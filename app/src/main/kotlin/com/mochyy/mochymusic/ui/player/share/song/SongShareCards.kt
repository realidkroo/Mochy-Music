// SongShareCards.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.player.share.MochyMark
import com.mochyy.mochymusic.ui.player.share.SweptText
import com.mochyy.mochymusic.ui.player.share.timedPieces
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private val MusicCardWidth = 172.dp
private val LyricsCardWidth = 296.dp

// a still lyrics card shows at most this many lines of its moment
private const val StillLines = 5

// the picked lines on a picture: this size, stepping down to the smallest when there are too many for the room
private const val StillTextSize = 19f
private const val SmallestStillText = 12f

// the most room the lyrics take on the card, so even 25 one-word lines keep the card inside the picture
private val LyricsRoom = 400.dp

// the card's lyrics column: the card less its padding on both sides
private val LyricsColumnWidth = LyricsCardWidth - 36.dp

internal fun cardCornerOf(kind: SongShareKind): Dp = when (kind) {
    SongShareKind.MusicOnly -> 22.dp
    SongShareKind.Lyrics -> 24.dp
}

private fun cardText(size: Float, weight: FontWeight, color: Color, tracking: Float = -0.4f) =
    TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

// the tall card: the cover on top, the song under it, and a line that fills as the clip plays
@Composable
internal fun MusicOnlyCard(session: SongShareSession, positionMs: () -> Long, moving: Boolean) {
    val ink = session.cardInk
    Column(Modifier.width(MusicCardWidth).padding(14.dp)) {
        CardCover(session.art.cover, ink, corner = 14.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(12.dp))
        Text(session.share.title, style = cardText(15f, FontWeight.Bold, ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            session.share.artist,
            style = cardText(12f, FontWeight.SemiBold, ink.copy(alpha = 0.72f), -0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (moving) {
            Spacer(Modifier.height(10.dp))
            ClipProgress(session, positionMs, ink)
        }
        Spacer(Modifier.height(12.dp))
        MochyMark(ink, size = 12f)
    }
}

// the wide card: the song on top, then its lyrics, each line sung as the song plays in a video
@Composable
internal fun LyricsCard(session: SongShareSession, positionMs: () -> Long, moving: Boolean) {
    val ink = session.cardInk
    Column(Modifier.width(LyricsCardWidth).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardCover(session.art.cover, ink, corner = 10.dp, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(session.share.title, style = cardText(15f, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    session.share.artist,
                    style = cardText(12f, FontWeight.SemiBold, ink.copy(alpha = 0.72f), -0.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        CardLyrics(session, ink, sung = moving && session.lyricsSynced, positionMs)
        Spacer(Modifier.height(16.dp))
        MochyMark(ink, size = 12f)
    }
}

@Composable
private fun CardCover(cover: ImageBitmap?, ink: Color, corner: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(corner)
    if (cover != null) {
        Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        Spacer(modifier.clip(shape).background(ink.copy(alpha = 0.15f)))
    }
}

// how far into its clip the share is, read while drawing so the line moves without recomposing the card
@Composable
private fun ClipProgress(session: SongShareSession, positionMs: () -> Long, ink: Color) {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(ink.copy(alpha = 0.22f), cornerRadius = radius)
                val done = ((positionMs() - session.windowStartMs).toFloat() / session.windowLengthMs).coerceIn(0f, 1f)
                if (done > 0f) drawRoundRect(ink, size = Size(size.width * done, size.height), cornerRadius = radius)
            },
    )
}

// the card's lines, the same in a picture and a video: plain bold lines with their reading and translation under them; a video lights each word as it's sung
@Composable
private fun CardLyrics(session: SongShareSession, ink: Color, sung: Boolean, positionMs: () -> Long) {
    val pick = session.pick
    val lines = session.lyrics.found.orEmpty()
    val start = session.windowStartMs + session.lyricsOffsetMs
    val end = session.windowEndMs + session.lyricsOffsetMs
    // the picked lines, or without a pick the ones sung in the share's moment
    val entries = remember(pick?.range, lines, start, end) {
        pick?.let { p -> p.range?.mapNotNull { p.entries.getOrNull(it) }.orEmpty() } ?: linesInWindow(lines, start, end)
    }
    val choice = session.readingChoice
    val readings = session.lineReadings
    val translations = session.lineTranslations
    val shown = remember(entries, choice, readings, translations) { entries.map { session.cardLine(it) } }
    if (shown.isEmpty()) {
        Text(" ", style = stillText(StillTextSize, ink))
        return
    }
    val size = rememberCardTextSize(shown)
    val lyricsPosition = remember(session, positionMs) { { positionMs() + session.lyricsOffsetMs } }
    Column {
        shown.forEachIndexed { index, line ->
            key(index) {
                val text = line.entry.text.trim()
                if (sung) {
                    val next = entries.getOrNull(index + 1)?.time ?: end
                    val timing = remember(line.entry, text, next) { timedPieces(line.entry, text, next) }
                    SweptText({ AnnotatedString(text) }, ink, stillText(size, ink), timing.ranges, timing.times, lyricsPosition)
                } else {
                    Text(text, style = stillText(size, ink))
                }
                line.under.forEach { Text(it, style = underText(size, ink)) }
            }
        }
    }
}

private fun stillText(size: Float, ink: Color) = cardText(size, FontWeight.Bold, ink, -0.7f).copy(lineHeight = (size * 25f / 19f).sp)

// a reading or a translation under a card line: smaller, softer and not bold, so the line stays what you read first
private fun underText(size: Float, ink: Color) =
    cardText(size * UnderShare, FontWeight.SemiBold, ink.copy(alpha = ink.alpha * UnderAlpha), -0.2f).copy(lineHeight = (size * UnderShare * 1.3f).sp)

private const val UnderShare = 0.6f
private const val UnderAlpha = 0.72f

// the usual size, or a step smaller at a time until the lines and what's under them fit the card's room
@Composable
private fun rememberCardTextSize(lines: List<ShareLine>): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(lines, density) {
        val constraints = Constraints(maxWidth = with(density) { LyricsColumnWidth.roundToPx() })
        val room = with(density) { LyricsRoom.toPx() }
        fun heightAt(size: Float) = lines.sumOf { line ->
            measurer.measure(line.entry.text.trim(), stillText(size, Color.White), constraints = constraints).size.height +
                line.under.sumOf { measurer.measure(it, underText(size, Color.White), constraints = constraints).size.height }
        }
        var size = StillTextSize
        while (size > SmallestStillText && heightAt(size) > room) size -= 1f
        size
    }
}

internal fun linesInWindow(lines: List<LyricsEntry>, startMs: Long, endMs: Long): List<LyricsEntry> {
    val sung = lines.filter { it.text.isNotBlank() && !it.isBackground }
    if (sung.isEmpty()) return emptyList()
    val first = LyricsUtils.findCurrentLineIndex(sung, startMs).coerceIn(0, sung.lastIndex)
    val picked = sung.drop(first).takeWhile { it.time < endMs }.take(StillLines)
    return picked.ifEmpty { listOf(sung[first]) }
}
