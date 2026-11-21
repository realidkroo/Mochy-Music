// what a module player can read about the music beyond the song: the sung line and word and how far along they are, the time, and what's next in the queue (docs/wiki, player info)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.modules.skin.SkinLyricField
import com.mochyy.mochymusic.modules.skin.SkinPlayerField
import com.mochyy.mochymusic.modules.skin.SkinQueueField
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.flow.MutableStateFlow

/** where the lyrics are on this frame: worked out once per frame for every block, shader and clock that reads them, and only read in draw, layout or a derived state */
@Stable
internal class LyricClock(val lines: List<LyricsEntry>, private val position: LivePosition) {
    private var at = Long.MIN_VALUE
    private var line = -1
    private var lineProgress = 0f
    private var word = -1
    private var wordProgress = 0f
    private var singing = false

    /** the sung line's index: -1 before the first, the line count once the last is over */
    fun line(): Int = update().line

    /** 0 at the start of the sung line, 1 at its end */
    fun lineProgress(): Float = update().lineProgress

    /** the sung word's index in its line, -1 when the line has no word timing */
    fun word(): Int = update().word

    /** how far through the sung word, 0 to 1; a line without word timing reads its own progress */
    fun wordProgress(): Float = update().wordProgress

    /** a line is being sung, not a gap or the bit before the first */
    fun singing(): Boolean = update().singing

    private fun update(): LyricClock {
        val now = position.positionMs()
        if (now == at) return this
        at = now
        val index = LyricsUtils.findCurrentLineIndex(lines, now)
        line = index
        val entry = lines.getOrNull(index)
        if (entry == null) {
            lineProgress = if (index >= lines.size && lines.isNotEmpty()) 1f else 0f
            word = -1
            wordProgress = lineProgress
            singing = false
            return this
        }
        val words = entry.words.orEmpty()
        val start = entry.time
        // a line ends where its last word does, else where the next one starts, else a few seconds on
        val end = words.lastOrNull()?.let { (it.endTime * 1000).toLong() } ?: lines.getOrNull(index + 1)?.time ?: (start + LoneLineMs)
        lineProgress = ((now - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
        singing = now in start..end
        word = -1
        wordProgress = lineProgress
        for (i in words.indices) {
            val w = words[i]
            val wordStart = (w.startTime * 1000).toLong()
            if (now < wordStart) break
            word = i
            val wordEnd = (w.endTime * 1000).toLong()
            wordProgress = ((now - wordStart).toFloat() / (wordEnd - wordStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
        }
        return this
    }

    companion object {
        private const val LoneLineMs = 5000L
    }
}

/** the clock for a player's lyrics, one per set of lines */
@Composable
internal fun rememberLyricClock(lines: List<LyricsEntry>, position: LivePosition): LyricClock =
    remember(lines, position) { LyricClock(lines, position) }

/** [text] with the lyrics, time and queue placeholders filled in; each part is only followed when the text names it, and only changes the words when it moves */
@Composable
internal fun rememberPlayerFields(text: String, context: SkinContext): String {
    val lyric = SkinLyricField.containsMatchIn(text)
    val player = SkinPlayerField.containsMatchIn(text)
    if (!lyric && !player) return text
    var filled = text
    if (lyric) filled = fillLyrics(filled, context)
    if (player) filled = fillPlayer(filled, context)
    return filled
}

@Composable
private fun fillLyrics(text: String, context: SkinContext): String {
    val clock = context.lyricClock
    val lines = clock.lines
    // the position moves every frame, the words only when the sung line (or word) changes
    val line by remember(clock) { derivedStateOf { clock.line() } }
    val wantsWord = text.contains("{word}", ignoreCase = true)
    val word by remember(clock, wantsWord) { derivedStateOf { if (wantsWord) clock.word() else -1 } }
    val entry = lines.getOrNull(line)
    val translation = remember(entry) { entry?.translatedTextFlow ?: NoTranslation }.collectAsState().value
    return text
        .replace("{line}", entry?.text.orEmpty(), ignoreCase = true)
        .replace("{nextLine}", lines.getOrNull(line + 1)?.text.orEmpty(), ignoreCase = true)
        .replace("{prevLine}", lines.getOrNull(line - 1)?.text.orEmpty(), ignoreCase = true)
        .replace("{translation}", translation.orEmpty(), ignoreCase = true)
        .replace("{word}", entry?.words?.getOrNull(word)?.text?.trim().orEmpty(), ignoreCase = true)
}

@Composable
private fun fillPlayer(text: String, context: SkinContext): String {
    var filled = text
    if (TimeField.containsMatchIn(filled)) {
        val position = context.position
        val duration = context.duration
        // whole seconds, so the words change once a second and not every frame, and skipped for queue-only labels
        val second by remember(position) { derivedStateOf { position.positionMs() / 1000L } }
        filled = filled
            .replace("{elapsed}", makeTimeString(second * 1000L), ignoreCase = true)
            .replace("{remaining}", "-" + makeTimeString((duration - second * 1000L).coerceAtLeast(0L)), ignoreCase = true)
            .replace("{length}", makeTimeString(duration), ignoreCase = true)
            .replace("{percent}", (if (duration > 0) (second * 100_000L / duration).coerceIn(0L, 100L) else 0L).toString(), ignoreCase = true)
    }
    if (SkinQueueField.containsMatchIn(filled)) {
        val queue = context.queue ?: return filled
        val state = queue.state
        val at = state.currentIndex + 1
        val size = state.items.size
        val left = (size - at).coerceAtLeast(0)
        filled = SkinQueueField.replace(filled) { match ->
            val key = match.groupValues[1].lowercase()
            when (key) {
                "queue" -> if (size > 0 && at > 0) "$at / $size" else ""
                "queueposition" -> at.coerceAtLeast(0).toString()
                "queuesize" -> size.toString()
                "queueleft" -> left.toString()
                "queuetitle" -> state.title
                "prev" -> queue.itemAt(-1)?.title.orEmpty()
                "prevartist" -> queue.itemAt(-1)?.artist.orEmpty()
                "prevlength" -> queue.itemAt(-1)?.duration?.takeIf { it > 0L }?.let(::makeTimeString).orEmpty()
                else -> fillNextToken(key, queue)
            }
        }
    }
    return filled
}

private fun fillNextToken(key: String, queue: SkinQueue): String {
    val body = key.removePrefix("next")
    val field = when {
        body.endsWith("artist") -> "artist"
        body.endsWith("length") -> "length"
        else -> "title"
    }
    val offset = body.removeSuffix("artist").removeSuffix("length").toIntOrNull() ?: 1
    val item = queue.itemAt(offset) ?: return ""
    return when (field) {
        "artist" -> item.artist
        "length" -> if (item.duration > 0L) makeTimeString(item.duration) else ""
        else -> item.title
    }
}

private val NoTranslation = MutableStateFlow<String?>(null)
// android's regex engine refuses a bare closing brace, so it's escaped or the player crashes on open
private val TimeField = Regex("""\{(elapsed|remaining|length|percent)\}""", RegexOption.IGNORE_CASE)
