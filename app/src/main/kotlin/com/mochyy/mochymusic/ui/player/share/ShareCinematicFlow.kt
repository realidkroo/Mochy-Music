// ShareCinematicFlow.kt
//
// The picked lines in a video, on the card and in the Cinematic words alike: every word waits faded,
// then lights up left to right while it's sung, so the lines play along with the song. A picture
// keeps them all lit.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import com.mochyy.mochymusic.ui.player.sweepBloom
import com.mochyy.mochymusic.ui.player.layerBleed
import com.mochyy.mochymusic.ui.player.SweepHeadGeometry
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import kotlin.math.max
import kotlin.math.min

// how faded a word waits before it's sung
private const val WaitingAlpha = 0.3f

// room left round a lit word, so a glowing word's glow isn't cut off at its letters
private val GlowRoom = 18.dp

// when each word of a picked line is sung, in the lyrics' own time
internal class SungWord(val startMs: Long, val endMs: Long)

// what a video needs to sing the words: the lyrics' time right now, and every picked line's word times
@Immutable
internal class CinematicFlow(val positionMs: () -> Long, val words: Map<Int, List<SungWord>>)

// the provider's own word times when it timed every word, otherwise the line spread over its length by letters
internal fun cinematicTimings(session: LyricsShareSession): Map<Int, List<SungWord>> =
    session.chosen.associate { (line, text) ->
        val words = wordsOf(text)
        val entry = session.entries.getOrNull(line) ?: return@associate line to emptyList()
        val timed = entry.words?.filter { it.text.isNotBlank() }
        val times = if (timed != null && timed.size == words.size) {
            timed.map { SungWord((it.startTime * 1000).toLong(), (it.endTime * 1000).toLong()) }
        } else {
            val start = entry.time
            val end = timed?.lastOrNull()?.let { (it.endTime * 1000).toLong() }?.takeIf { it > start }
                ?: session.entries.getOrNull(line + 1)?.time?.takeIf { it > start }
                ?: (start + 3_000L)
            val letters = words.sumOf { it.length }.coerceAtLeast(1)
            var at = start.toDouble()
            words.map { word ->
                val span = (end - start) * word.length.toDouble() / letters
                SungWord(at.toLong(), (at + span).toLong()).also { at += span }
            }
        }
        line to times
    }

// how long the glow takes to come up on a word and to fade once it's sung
private const val GlowInMs = 120f
private const val GlowOutMs = 260f

// the head of the sweep right now: inside the word being sung, or just past the last one sung while its glow fades; null when nothing shines
private fun sweepHead(layout: TextLayoutResult?, ranges: List<IntRange>, times: List<SungWord>, now: Long, bleed: Float): SweepHeadGeometry? {
    layout ?: return null
    val length = layout.layoutInput.text.length
    var pick = -1
    var glow = 0f
    for (i in times.indices) {
        val word = times[i]
        val range = ranges.getOrNull(i) ?: continue
        if (range.isEmpty() || range.last >= length) continue
        if (now < word.startMs) break
        val shine = if (now < word.endMs) ((now - word.startMs) / GlowInMs).coerceIn(0f, 1f) else (1f - (now - word.endMs) / GlowOutMs).coerceIn(0f, 1f)
        if (shine > 0f) {
            pick = i
            glow = shine
        }
    }
    if (pick < 0) return null
    val range = ranges[pick]
    val word = times[pick]
    val first = layout.getBoundingBox(range.first)
    val last = layout.getBoundingBox(range.last)
    val sung = ((now - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val lineIndex = layout.getLineForOffset(range.first)
    return SweepHeadGeometry(
        headX = first.left + (last.right - first.left) * sung + bleed,
        lineTop = layout.getLineTop(lineIndex) + bleed,
        lineBottom = layout.getLineBottom(lineIndex) + bleed,
        lineStart = layout.getLineLeft(lineIndex) + bleed,
        charWidth = ((last.right - first.left) / (range.last - range.first + 1).coerceAtLeast(1)).coerceAtLeast(1f),
        glow = glow,
    )
}

// where each word sits in the line as it's drawn, capitals included
private fun wordRanges(session: LyricsShareSession, line: Int, text: String): List<IntRange> {
    var at = 0
    return wordsOf(text).mapIndexed { i, word ->
        if (i > 0) at += 1
        val shown = if (session.look(WordKey(line, i)).caps) word.uppercase() else word
        (at until at + shown.length).also { at += shown.length }
    }
}

// one picked cinematic line, its words styled as picked, lit as it's sung
@Composable
internal fun SungLine(
    session: LyricsShareSession,
    line: Int,
    text: String,
    ink: Color,
    textSize: Float,
    style: TextStyle,
    flow: CinematicFlow,
) {
    SweptText(
        text = { color -> cinematicLine(session, line, text, color, textSize) },
        ink = ink,
        style = style,
        ranges = wordRanges(session, line, text),
        times = flow.words[line].orEmpty(),
        positionMs = flow.positionMs,
    )
}

// where each sung piece of a line sits in its text, and when it's sung
internal class TimedPieces(val ranges: List<IntRange>, val times: List<SungWord>)

private val WordRun = Regex("\\S+")

// the line's own timed pieces when every one of them is found in the text, otherwise its words spread over the line by letters
internal fun timedPieces(entry: LyricsEntry, text: String, nextLineMs: Long?): TimedPieces {
    val pieces = (entry.syllables ?: entry.words)?.filter { it.text.isNotBlank() }.orEmpty()
    var at = 0
    val found = pieces.mapNotNull { piece ->
        val word = piece.text.trim()
        val start = text.indexOf(word, at).takeIf { it >= 0 } ?: return@mapNotNull null
        at = start + word.length
        start until at
    }
    if (pieces.isNotEmpty() && found.size == pieces.size) {
        return TimedPieces(found, pieces.map { SungWord((it.startTime * 1000).toLong(), (it.endTime * 1000).toLong()) })
    }
    val words = WordRun.findAll(text).map { it.range }.toList()
    val start = entry.time
    val end = pieces.lastOrNull()?.let { (it.endTime * 1000).toLong() }?.takeIf { it > start }
        ?: nextLineMs?.takeIf { it > start }
        ?: (start + 3_000L)
    val letters = words.sumOf { it.last - it.first + 1 }.coerceAtLeast(1)
    var time = start.toDouble()
    val times = words.map { word ->
        val span = (end - start) * (word.last - word.first + 1).toDouble() / letters
        SungWord(time.toLong(), (time + span).toLong()).also { time += span }
    }
    return TimedPieces(words, times)
}

// a line drawn faded, with a lit copy on top that shows only as far as the song has got and glows round the word being sung
@Composable
internal fun SweptText(
    text: (ink: Color) -> AnnotatedString,
    ink: Color,
    style: TextStyle,
    ranges: List<IntRange>,
    times: List<SungWord>,
    positionMs: () -> Long,
) {
    val waitingInk = ink.copy(alpha = ink.alpha * WaitingAlpha)
    // read only while drawing, so a new layout never recomposes the line
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    val lit = remember { Path() }
    Box {
        Text(text(waitingInk), style = style.copy(color = waitingInk))
        Text(
            text(ink),
            style = style,
            onTextLayout = { laidOut[0] = it },
            // the lyrics page's glow round the word being sung, on a layer with room for it to spill
            modifier = Modifier
                .layerBleed(GlowRoom)
                .sweepBloom(highBloom = true) { sweepHead(laidOut[0], ranges, times, positionMs(), GlowRoom.toPx()) }
                .padding(GlowRoom)
                .drawWithContent {
                    val layout = laidOut[0] ?: return@drawWithContent
                    val now = positionMs()
                    val room = GlowRoom.toPx()
                    val length = layout.layoutInput.text.length
                    lit.reset()
                    var any = false
                    for (i in ranges.indices) {
                        val range = ranges[i]
                        val word = times.getOrNull(i) ?: continue
                        if (range.isEmpty() || range.last >= length) continue
                        val sung = ((now - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        if (sung <= 0f) continue
                        val first = layout.getBoundingBox(range.first)
                        val last = layout.getBoundingBox(range.last)
                        val top = min(first.top, last.top) - room
                        val bottom = max(first.bottom, last.bottom) + room
                        if (first.top == last.top) {
                            val right = first.left + (last.right - first.left) * sung
                            lit.addRect(Rect(first.left - room, top, if (sung >= 1f) right + room else right, bottom))
                        } else {
                            // a word too long for its line lights both lines at once
                            lit.addRect(Rect(0f, top, size.width, bottom))
                        }
                        any = true
                    }
                    if (any) clipPath(lit) { this@drawWithContent.drawContent() }
                },
        )
    }
}
