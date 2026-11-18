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

/** analyses tracks for crossmix and keeps them on disk, a failed decode is never cached since an expired stream url may work next time */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class CrossmixAnalysisRepository(
    private val context: Context,
    private val dataSourceFactory: () -> DataSource.Factory,
) {
    private val memory = ConcurrentHashMap<String, TrackAnalysis>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysis?>>()
    private val failures = ConcurrentHashMap<String, String>()
    private val cacheDir = File(context.cacheDir, "crossmix-analysis-v2").also { it.mkdirs() }

    // one quiet thread: analysis must never compete with decoding the song that's playing
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "crossmix-analysis").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** wholeTrack is the song that's playing, whose ending we mix out of; otherwise only the first HeadMs are read, the next song is entered early */
    suspend fun analyze(item: MediaItem, wholeTrack: Boolean): TrackAnalysis? {
        val uri = item.localConfiguration?.uri ?: return null
        val mediaId = item.mediaId.ifBlank { uri.toString() }
        val cacheKey = item.localConfiguration?.customCacheKey?.takeIf { it.isNotBlank() } ?: mediaId
        usable(memory[mediaId], wholeTrack)?.let { return it }

        val key = "$mediaId|$wholeTrack"
        // start only once it's in the map, so a quick finish can't remove itself first and leave a stale result for later callers
        val job = inFlight.getOrPut(key) {
            scope.async(start = CoroutineStart.LAZY) {
                val progress = Progress()
                val timeoutMs = if (wholeTrack) 60_000L else 40_000L
                try {
                    usable(read(mediaId), wholeTrack)?.also { memory[mediaId] = it; failures.remove(mediaId) }
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
                            if (memory[mediaId]?.complete != true || analysis.complete) {
                                memory[mediaId] = analysis
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

    /** what's already known, without decoding anything */
    fun cached(mediaId: String): TrackAnalysis? = memory[mediaId]

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
        }
        // written whole, then swapped in: a crash mid-write can't leave a half file to misread
        temp.renameTo(target)
    }

    private fun cacheFile(mediaId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(mediaId.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) })
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
        // 3: analyses cut short by a one-chunk stream were stored as whole ones. 4: the voice curve. 5: bars from chords and drums, and section starts
        const val CacheVersion = 5
        const val MaxCachedArray = 200_000
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
