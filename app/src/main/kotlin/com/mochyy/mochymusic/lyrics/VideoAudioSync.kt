// VideoAudioSync.kt

package com.mochyy.mochymusic.lyrics

import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.mochyy.mochymusic.playback.audio.StreamPcmDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

// listens to a song and its music video side by side and works out where the song sits in the video; lives in the music service, whose data source can read either
@UnstableApi
class VideoAudioSync(
    private val dataSourceFactory: () -> DataSource.Factory,
    // streams opened only to be listened to, so the service doesn't report their failures as the user's playback failing
    private val listeningOnly: MutableSet<String>,
) {
    // a recording to read: listeningOnly when it isn't the one playing
    class Source(val uri: Uri, val key: String, val listeningOnly: Boolean)

    private class Found(val map: SongVideoMap?, val atMs: Long)

    // its own quiet thread: listening must never compete with decoding the song that's playing
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "video-audio-sync").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true }
    }.asCoroutineDispatcher()
    private val prints = LruCache<String, AudioPrints>(6)
    private val found = LruCache<String, Found>(24)
    // one recording at a time, so two requests for the same pair share the work instead of doubling it
    private val listening = Mutex()

    // null when the two don't share a recording (a live version, a cover) or one of them couldn't be read
    suspend fun match(song: Source, video: Source): SongVideoMap? {
        val key = "${song.key}>${video.key}"
        known(key)?.let { return it.map }
        return withContext(dispatcher) {
            listening.withLock {
                known(key)?.let { return@withLock it.map }
                val songPrints = printsOf(song) ?: return@withLock null
                val videoPrints = printsOf(video) ?: return@withLock null
                val map = AudioMatcher.match(songPrints, videoPrints)
                android.util.Log.i("VideoAudioSync", "${song.key} in ${video.key}: ${map ?: "no match"}")
                found.put(key, Found(map, SystemClock.elapsedRealtime()))
                map
            }
        }
    }

    // a match is kept for good; no match is asked again after a while, in case the song's lyrics or video changed
    private fun known(key: String): Found? =
        found.get(key)?.takeIf { it.map != null || SystemClock.elapsedRealtime() - it.atMs < MissRetryMs }

    private suspend fun printsOf(source: Source): AudioPrints? {
        prints.get(source.key)?.let { return it }
        if (source.listeningOnly) listeningOnly += source.key
        return try {
            val printer = AudioPrinter()
            withTimeoutOrNull(ListenTimeoutMs) {
                StreamPcmDecoder.decode(dataSourceFactory(), source.uri, source.key, MaxRecordingUs) { pcm, channels, sampleRate, presentationUs ->
                    printer.feed(pcm, channels, sampleRate, presentationUs)
                }
            } ?: return null
            printer.finish()?.also { prints.put(source.key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("VideoAudioSync", "Couldn't listen to ${source.key}", e)
            null
        } finally {
            if (source.listeningOnly) listeningOnly -= source.key
        }
    }

    private companion object {
        // the first 90 seconds is enough to line up any intro or cut without downloading two whole 20-minute streams
        const val MaxRecordingUs = 90 * 1_000_000L
        const val ListenTimeoutMs = 60_000L
        const val MissRetryMs = 10 * 60_000L
    }
}
