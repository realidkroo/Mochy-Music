// LyricsAutoSync.kt

package com.mochyy.mochymusic.lyrics

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.mochyy.mochymusic.playback.audio.StreamPcmDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Lyrics auto offset: listens to the song, finds where it's being sung, and moves the lyrics so
 * each line lands on its singing (see [VocalActivityAnalyzer] and [LyricsAligner]). Only for
 * lyrics timed by line or by word; plain text has no timing to move.
 *
 * It runs here, in the music service, rather than in the sheet that asked: the service's data
 * source is the one that can read any song (streamed, cached, downloaded or on the phone), and a
 * sync that's under way still finishes and saves when the sheet is closed.
 */
@UnstableApi
class LyricsAutoSync(
    private val dataSourceFactory: () -> DataSource.Factory,
    private val saveOffset: (songId: String, offsetMs: Int) -> Unit,
    /** Told about a finished sync nobody was watching, so it can say so another way. */
    private val onUnwatchedResult: (State) -> Unit,
) {
    sealed interface State {
        val songId: String

        /** Decoding the song; [progress] 0..1. */
        data class Listening(override val songId: String, val progress: Float) : State

        /** Lining the lyrics up with what was heard. */
        data class Matching(override val songId: String) : State

        /**
         * Saved. [sure] is false for a best guess worth checking by ear. [driftMs] is how far the
         * timing wanders over the song when it does, which one offset can't fully fix.
         */
        data class Synced(
            override val songId: String,
            val offsetMs: Int,
            val previousOffsetMs: Int,
            val sure: Boolean,
            val driftMs: Int?,
        ) : State

        data class Failed(override val songId: String, val message: String) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    // its own quiet thread: listening must never compete with decoding the song that's playing
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lyrics-auto-sync").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null

    fun release() {
        job?.cancel()
        scope.coroutineContext[Job]?.cancel()
        dispatcher.close()
        heard.clear()
    }

    // what the last few songs sounded like, so trying again (after picking other lyrics) is instant
    private val heard = object : LinkedHashMap<String, VocalActivity>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VocalActivity>?) = size > 3
    }

    /**
     * Syncs [lines] for [songId], reading the song from [uri] under [cacheKey]. [durationMs] only
     * drives the progress shown; [previousOffsetMs] is kept for Undo.
     */
    fun start(
        songId: String,
        uri: Uri,
        cacheKey: String,
        durationMs: Long,
        lines: List<LyricsEntry>,
        previousOffsetMs: Int,
    ) {
        job?.cancel()
        _state.value = State.Listening(songId, 0f)
        job = scope.launch {
            val result = try {
                withTimeoutOrNull(TimeoutMs) { sync(songId, uri, cacheKey, durationMs, lines, previousOffsetMs) }
                    ?: State.Failed(songId, "Listening took too long. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LyricsAutoSync", "Auto offset for $songId failed", e)
                State.Failed(songId, "Couldn't listen to this song. Check your connection and try again.")
            }
            _state.value = result
            if (_state.subscriptionCount.value == 0) onUnwatchedResult(result)
        }
    }

    private suspend fun sync(
        songId: String,
        uri: Uri,
        cacheKey: String,
        durationMs: Long,
        lines: List<LyricsEntry>,
        previousOffsetMs: Int,
    ): State {
        val vocals = synchronized(heard) { heard[cacheKey] } ?: listen(songId, uri, cacheKey, durationMs)
            ?: return State.Failed(songId, "Too little of the song came through to hear the singing.")
        synchronized(heard) { heard[cacheKey] = vocals }

        _state.value = State.Matching(songId)
        val alignment = LyricsAligner.align(lines, vocals, MaxOffsetMs)
        if (alignment == null || alignment.confidence < MinConfidence) {
            return State.Failed(songId, "Couldn't match the singing to these lyrics. They may be for another version, or you can set it by hand.")
        }
        android.util.Log.i(
            "LyricsAutoSync",
            "$songId: offset ${alignment.offsetMs} ms, confidence ${"%.2f".format(alignment.confidence)}, " +
                "${alignment.strongLines}/${alignment.totalLines} lines clear, drift ${alignment.driftMs}",
        )
        saveOffset(songId, alignment.offsetMs)
        return State.Synced(
            songId = songId,
            offsetMs = alignment.offsetMs,
            previousOffsetMs = previousOffsetMs,
            sure = alignment.confidence >= SureConfidence,
            driftMs = alignment.driftMs,
        )
    }

    private suspend fun listen(songId: String, uri: Uri, cacheKey: String, durationMs: Long): VocalActivity? {
        val analyzer = VocalActivityAnalyzer()
        var shown = 0f
        StreamPcmDecoder.decode(dataSourceFactory(), uri, cacheKey, MaxSongUs) { pcm, channels, sampleRate, presentationUs ->
            analyzer.feed(pcm, channels, sampleRate, presentationUs)
            if (durationMs > 0) {
                val progress = (presentationUs / 1_000f / durationMs).coerceIn(0f, 1f)
                // a step at a time: the sheet doesn't need to redraw for every decoded block
                if (abs(progress - shown) >= 0.01f) {
                    shown = progress
                    _state.value = State.Listening(songId, progress)
                }
            }
        }
        return analyzer.finish()
    }

    private companion object {
        /** The same range the offset slider has. */
        const val MaxOffsetMs = 10_000
        const val MaxSongUs = 15 * 60_000_000L
        const val TimeoutMs = 120_000L
        /** Below this the match is too unsure to save at all. */
        const val MinConfidence = 0.3f
        /** From here it's a confident match rather than a best guess. */
        const val SureConfidence = 0.55f
    }
}
