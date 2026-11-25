package com.mochyy.mochymusic.crossmix

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.mochyy.mochymusic.playback.audio.StreamPcmDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** analyses tracks for crossmix and keeps them on disk, a failed decode is never cached since an expired stream url may work next time */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class CrossmixAnalysisRepository(
    private val context: Context,
    private val dataSourceFactory: () -> DataSource.Factory,
) {
    // bounded memory cache because every analysis is already persisted on disk
    private val memory = android.util.LruCache<String, TrackAnalysis>(16)
    // lightweight BPM-only cache for large Mix It playlists so scrolling 1000+ songs never deserializes waveforms
    private val bpmCache = android.util.LruCache<String, Int>(2048)
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysis?>>()
    private val failures = ConcurrentHashMap<String, String>()
    private val cacheDir = File(context.cacheDir, "crossmix-analysis-v2").also { it.mkdirs() }

    // two quiet low-priority threads so Song 1 & Song 2 decode in parallel for Mix It without competing with playback
    private val dispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "crossmix-analysis").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // stops the background analysis thread when the service destroys
    fun release() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        dispatcher.close()
        memory.evictAll()
        bpmCache.evictAll()
    }

    // keeps a few nearby track analyses hot in ram and drops older ones that are already saved on disk
    fun trimMemory() {
        memory.trimToSize(4)
        bpmCache.trimToSize(256)
        failures.clear()
    }

    /** wholeTrack is the song that's playing, whose ending we mix out of; otherwise only the first HeadMs are read, the next song is entered early */
    suspend fun analyze(item: MediaItem, wholeTrack: Boolean): TrackAnalysis? {
        val uri = item.localConfiguration?.uri ?: return null
        val mediaId = item.mediaId.ifBlank { uri.toString() }
        val cacheKey = item.localConfiguration?.customCacheKey?.takeIf { it.isNotBlank() } ?: mediaId
        usable(memory.get(mediaId), wholeTrack)?.let { return it }

        val key = "$mediaId|$wholeTrack"
        // start only once it's in the map, so a quick finish can't remove itself first and leave a stale result for later callers
        val job = inFlight.getOrPut(key) {
            scope.async(start = CoroutineStart.LAZY) {
                val progress = Progress()
                val timeoutMs = if (wholeTrack) 60_000L else 40_000L
                try {
                    usable(read(mediaId), wholeTrack)?.also { memory.put(mediaId, it); failures.remove(mediaId) }
                        ?: (
                            withTimeoutOrNull(timeoutMs) { decodeAndAnalyze(uri, cacheKey, wholeTrack, progress) }
                                ?: null.also {
                                    failures[mediaId] = "timed out after ${timeoutMs / 1_000} s with " +
                                        "${progress.decodedUs / 1_000_000} s of audio decoded"
                                }
                            )?.also { analysis ->
                            failures.remove(mediaId)
                            android.util.Log.i(
                                "Crossmix",
                                "analysed $mediaId: ${analysis.analyzedMs / 1_000} s of audio in " +
                                    "${(System.currentTimeMillis() - progress.startedAt) / 1_000} s, " +
                                    "bpm ${analysis.bpm}, ${analysis.beatsMs.size} beats, complete=${analysis.complete}",
                            )
                            // a whole-track result answers head requests too; never overwrite it with less
                            if (memory.get(mediaId)?.complete != true || analysis.complete) {
                                memory.put(mediaId, analysis)
                                bpmCache.put(mediaId, reliableBpmOf(analysis) ?: NoBpm)
                                write(mediaId, analysis)
                            }
                        }
                } catch (error: Throwable) {
                    failures[mediaId] = error.message ?: error.javaClass.simpleName
                    null
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        job.start()
        return job.await()
    }

    /** what's already known in memory, without decoding or disk IO */
    fun cached(mediaId: String): TrackAnalysis? = memory.get(mediaId)

    /** checks memory, then disk cache without triggering a new decode */
    fun cachedFromDisk(mediaId: String): TrackAnalysis? =
        memory.get(mediaId) ?: read(mediaId)?.also {
            memory.put(mediaId, it)
            bpmCache.put(mediaId, reliableBpmOf(it) ?: NoBpm)
        }

    /** lightweight BPM lookup for playlist rows: avoids deserializing waveform/energy arrays or thrashing the 16-entry analysis cache */
    fun cachedReliableBpm(mediaId: String): Int? {
        memory.get(mediaId)?.let { analysis ->
            val bpm = reliableBpmOf(analysis)
            bpmCache.put(mediaId, bpm ?: NoBpm)
            return bpm
        }
        bpmCache.get(mediaId)?.let { return it.takeIf { v -> v > 0 } }
        val bpm = readReliableBpmHeader(mediaId)
        bpmCache.put(mediaId, bpm ?: NoBpm)
        return bpm
    }

    private fun reliableBpmOf(analysis: TrackAnalysis): Int? =
        analysis.takeIf { it.isBeatReliable }?.bpm?.takeIf { it.isFinite() }?.roundToInt()

    private fun readReliableBpmHeader(mediaId: String): Int? = runCatching {
        val file = cacheFile(mediaId)
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(file.inputStream(), 64)).use { input ->
            if (input.readInt() != CacheVersion) return null
            input.readLong() // analyzedMs
            input.readBoolean() // complete
            val bpm = input.readFloat().takeUnless { it.isNaN() } ?: return null
            val bpmConfidence = input.readFloat()
            val beatsCount = input.readInt()
            if (bpm.isFinite() && bpmConfidence >= 0.35f && beatsCount >= 16) bpm.roundToInt() else null
        }
    }.getOrNull()

    /** why the last analysis of mediaId came back empty, if it did */
    fun lastFailure(mediaId: String): String? = failures[mediaId]

    private class Progress {
        val startedAt = System.currentTimeMillis()
        @Volatile var decodedUs = 0L
    }

    private fun usable(analysis: TrackAnalysis?, wholeTrack: Boolean): TrackAnalysis? =
        analysis?.takeIf { !wholeTrack || it.complete }

    private suspend fun decodeAndAnalyze(uri: Uri, mediaId: String, wholeTrack: Boolean, progress: Progress): TrackAnalysis {
        val analyzer = StreamingTrackAnalyzer()
        val limitUs = if (wholeTrack) MaxTrackMs * 1_000L else HeadMs * 1_000L
        val resampler = MonoResampler()
        val outcome = StreamPcmDecoder.decode(dataSourceFactory(), uri, mediaId, limitUs) { pcm, channels, sampleRate, presentationUs ->
            resampler.push(pcm, channels, sampleRate, analyzer)
            progress.decodedUs = presentationUs
        }
        val lastPresentationUs = outcome.lastPresentationUs
        val durationUs = outcome.durationUs
        val wholeStream = outcome.reachedEnd && (durationUs <= 0L || lastPresentationUs >= durationUs - 3_000_000L)
        val complete = wholeStream || lastPresentationUs >= MaxTrackMs * 1_000L
        if (wholeTrack && !complete) {
            error("stream ended at ${lastPresentationUs / 1_000_000} s of ${durationUs / 1_000_000} s")
        }
        return analyzer.finish(complete = wholeTrack && complete)
            ?: error("only ${lastPresentationUs / 1_000_000} s of audio came through")
    }

    private fun read(mediaId: String): TrackAnalysis? = runCatching {
        val file = cacheFile(mediaId)
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != CacheVersion) return null
            TrackAnalysis(
                analyzedMs = input.readLong(),
                complete = input.readBoolean(),
                bpm = input.readFloat().takeUnless { it.isNaN() },
                bpmConfidence = input.readFloat(),
                beatsMs = input.readLongArray(),
                barBeats = input.readIntArray(),
                sectionBars = input.readIntArray(),
                key = input.readUTF().ifBlank { null },
                keyConfidence = input.readFloat(),
                leadingSilenceEndMs = input.readLong(),
                trailingSilenceStartMs = input.readLong(),
                energy = input.readFloatArray(),
                bass = input.readFloatArray(),
                brightness = input.readFloatArray(),
                vibe = Vibe(
                    energy = input.readFloat(),
                    danceability = input.readFloat(),
                    brightness = input.readFloat(),
                    bassWeight = input.readFloat(),
                    minor = input.readBoolean(),
                ),
                vocal = input.readFloatArray(),
                waveform = input.readFloatArray(),
            ).takeIf { analysis -> analysis.barBeats.all { it in analysis.beatsMs.indices } }
        }
    }.getOrNull()

    private fun write(mediaId: String, analysis: TrackAnalysis) = runCatching {
        val target = cacheFile(mediaId)
        val temp = File(target.parentFile, target.name + ".tmp")
        DataOutputStream(BufferedOutputStream(temp.outputStream())).use { out ->
            out.writeInt(CacheVersion)
            out.writeLong(analysis.analyzedMs)
            out.writeBoolean(analysis.complete)
            out.writeFloat(analysis.bpm ?: Float.NaN)
            out.writeFloat(analysis.bpmConfidence)
            out.writeLongArray(analysis.beatsMs)
            out.writeIntArray(analysis.barBeats)
            out.writeIntArray(analysis.sectionBars)
            out.writeUTF(analysis.key.orEmpty())
            out.writeFloat(analysis.keyConfidence)
            out.writeLong(analysis.leadingSilenceEndMs)
            out.writeLong(analysis.trailingSilenceStartMs)
            out.writeFloatArray(analysis.energy)
            out.writeFloatArray(analysis.bass)
            out.writeFloatArray(analysis.brightness)
            out.writeFloat(analysis.vibe.energy)
            out.writeFloat(analysis.vibe.danceability)
            out.writeFloat(analysis.vibe.brightness)
            out.writeFloat(analysis.vibe.bassWeight)
            out.writeBoolean(analysis.vibe.minor)
            out.writeFloatArray(analysis.vocal)
            out.writeFloatArray(analysis.waveform)
        }
        // written whole, then swapped in: a crash mid-write can't leave a half file to misread
        temp.renameTo(target)
    }

    private fun cacheFile(mediaId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(mediaId.toByteArray())
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val b = digest[i].toInt() and 0xFF
            hex[i * 2] = HexChars[b ushr 4]
            hex[i * 2 + 1] = HexChars[b and 0x0F]
        }
        return File(cacheDir, String(hex))
    }

    private fun DataInputStream.readLongArray(): LongArray = LongArray(readInt().coerceIn(0, MaxCachedArray)) { readLong() }
    private fun DataInputStream.readFloatArray(): FloatArray = FloatArray(readInt().coerceIn(0, MaxCachedArray)) { readFloat() }
    private fun DataInputStream.readIntArray(): IntArray = IntArray(readInt().coerceIn(0, MaxCachedArray)) { readInt() }

    private fun DataOutputStream.writeLongArray(values: LongArray) {
        writeInt(values.size)
        values.forEach(::writeLong)
    }

    private fun DataOutputStream.writeFloatArray(values: FloatArray) {
        writeInt(values.size)
        values.forEach(::writeFloat)
    }

    private fun DataOutputStream.writeIntArray(values: IntArray) {
        writeInt(values.size)
        values.forEach(::writeInt)
    }

    private companion object {
        // 3: one-chunk streams. 4: voice curve. 5: bars & sections. 6: 8 Hz waveform. 7: 32 Hz high-contrast waveform for Mix It
        const val CacheVersion = 7
        const val MaxCachedArray = 200_000
        const val NoBpm = -1
        val HexChars = "0123456789abcdef".toCharArray()
        /** the next song: two minutes of possible entry points plus room for the blend after one */
        const val HeadMs = 150_000L
        const val MaxTrackMs = 15 * 60_000L
    }
}

/** downmixes pcm 16 to mono and resamples it to the analyser's rate, carrying phase between calls */
private class MonoResampler {
    private val out = FloatArray(8_192)
    private var count = 0
    private var position = 0.0
    private var previous = 0f

    fun push(pcm: java.nio.ShortBuffer, channels: Int, inputRate: Int, analyzer: StreamingTrackAnalyzer) {
        val step = inputRate.toDouble() / StreamingTrackAnalyzer.SampleRate
        val frames = pcm.remaining() / channels
        for (frame in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += pcm.get() / 32_768f
            val current = sum / channels
            // emit every output sample that falls between the previous input sample and this one
            while (position <= 1.0) {
                out[count++] = previous + (current - previous) * position.toFloat()
                if (count == out.size) { analyzer.feed(out, count); count = 0 }
                position += step
            }
            position -= 1.0
            previous = current
        }
        if (count > 0) { analyzer.feed(out, count); count = 0 }
    }
}
