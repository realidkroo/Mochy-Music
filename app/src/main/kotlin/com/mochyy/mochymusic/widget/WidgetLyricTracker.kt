// WidgetLyricTracker.kt

package com.mochyy.mochymusic.widget

import android.content.Context
import android.os.SystemClock
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.car.CarLyricLines
import com.mochyy.mochymusic.car.CarLyricsSource
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// which lyric line the now playing widgets show, read like the car's so it matches the player; main thread only, so nothing is locked
class WidgetLyricTracker(
    private val context: Context,
    private val source: CarLyricsSource,
    private val scope: () -> CoroutineScope,
    // called when something worth redrawing arrived: a song's lyrics, or the answer that it has none
    private val onLoaded: () -> Unit,
) {
    data class Cue(val current: String, val next: String)

    private var songId: String? = null
    private var lines: CarLyricLines? = null

    // whether a lookup for this song finished at least once, so "loading" can turn into "not available"
    private var answered = false
    private var loadJob: Job? = null
    private var retryAt = 0L

    // how long until the line changes again, as of the last cue; the service sleeps until then
    var wakeInMs: Long = Long.MAX_VALUE
        private set

    // a song's lines are parsed once, this only looks up the line for a position, cheap enough for every tick
    fun cue(metadata: MediaMetadata?, positionMs: Long): Cue {
        wakeInMs = Long.MAX_VALUE
        if (metadata == null) {
            reset(null)
            return Cue(context.getString(R.string.widget_np_lyrics_unavailable), "")
        }
        if (metadata.id != songId) reset(metadata.id)

        if (lines == null && loadJob?.isActive != true && SystemClock.elapsedRealtime() >= retryAt) {
            load(metadata)
        }

        val found = lines
        return when {
            found == null && !answered -> Cue(context.getString(R.string.widget_np_lyrics_loading), "")
            found == null -> Cue(context.getString(R.string.widget_np_lyrics_unavailable), "")
            // plain lyrics have no timing to follow, so their first lines shown all song long would only be wrong
            !found.synced -> Cue(context.getString(R.string.widget_np_lyrics_not_synced), "")
            else -> followed(found, positionMs)
        }
    }

    private fun followed(found: CarLyricLines, positionMs: Long): Cue {
        val index = found.lineAt(positionMs)
        val position = positionMs + found.offsetMs

        // before the first line, after the last one, or in a gap between two lines: a note instead of a blank
        val line = found.lines.getOrNull(index)?.text?.trim().orEmpty().ifBlank { Note }
        val upcoming = found.nextSungAfter(index)

        // a line becomes current LineLeadInMs before its own time, so that is when to wake, not at the time itself
        val following = found.lines.getOrNull(index + 1)
        if (following != null) {
            wakeInMs = (following.time - position - LyricsUtils.LineLeadInMs).coerceAtLeast(MinWakeMs)
        }
        return Cue(line, upcoming?.text?.trim().orEmpty())
    }

    private fun load(metadata: MediaMetadata) {
        val id = metadata.id
        loadJob = scope().launch {
            val result = runCatching { source.load(metadata) }.getOrNull()
            // the song may have changed while the lyrics were on their way; then they belong to nobody here
            if (songId != id) return@launch
            val firstAnswer = !answered
            lines = result
            answered = true
            // "not found" is saved and answers from the database from then on; offline saves nothing, so a retry lets lyrics appear later
            if (result == null) retryAt = SystemClock.elapsedRealtime() + RetryMs
            if (result != null || firstAnswer) onLoaded()
        }
    }

    private fun reset(newId: String?) {
        loadJob?.cancel()
        loadJob = null
        songId = newId
        lines = null
        answered = false
        retryAt = 0L
    }

    private companion object {
        const val Note = "♪"
        const val RetryMs = 20_000L
        const val MinWakeMs = 120L
    }
}
