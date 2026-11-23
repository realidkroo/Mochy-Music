// SongShareCards.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mochyy.mochymusic.ui.player.share.ShareKaraokeLineSpec
import com.mochyy.mochymusic.ui.player.share.ShareKaraokeText
import com.mochyy.mochymusic.ui.player.share.rememberShareKaraokeState
import com.mochyy.mochymusic.ui.player.share.waitingInkOf
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private val MusicCardWidth = 172.dp
private val LyricsCardWidth = 296.dp
private val CardLineGap = 4.dp

internal fun cardWidthOf(kind: SongShareKind): Dp = when (kind) {
    SongShareKind.MusicOnly -> MusicCardWidth
    SongShareKind.Lyrics -> LyricsCardWidth
}

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
                val done = ((positionMs() - session.windowStartMs).toFloat() / session.windowLengthMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                if (done > 0f) drawRoundRect(ink, size = Size(size.width * done, size.height), cornerRadius = radius)
            },
    )
}

// the card's lines, the same in a picture and a video: plain bold lines with their reading and translation under them;
// a video animates every line with the regular lyrics view's karaoke engine (AGSL zoom/bloom, word rise & swell)
@Composable
private fun CardLyrics(session: SongShareSession, ink: Color, sung: Boolean, positionMs: () -> Long) {
    val pick = session.pick
    val lines = session.lyrics.found.orEmpty()
    val start = session.windowStartMs + session.lyricsOffsetMs
    val end = session.windowEndMs + session.lyricsOffsetMs
    // the picked lines (with their real next-line timestamp from the song), or without a pick the ones sung in the share's moment
    val entriesWithNext = remember(pick?.range, lines, start, end) {
        if (pick != null) {
            pick.range?.mapNotNull { idx ->
                val entry = pick.entries.getOrNull(idx) ?: return@mapNotNull null
                entry to pick.entries.getOrNull(idx + 1)?.time
            }.orEmpty()
        } else {
            val allSung = lines.filter { it.text.isNotBlank() && !it.isBackground }
            val picked = linesInWindow(lines, start, end)
            picked.map { entry ->
                val fullIdx = allSung.indexOf(entry)
                entry to (if (fullIdx >= 0) allSung.getOrNull(fullIdx + 1)?.time else null)
            }
        }
    }
    val choice = session.readingChoice
    val readings = session.lineReadings
    val translations = session.lineTranslations
    val shown = remember(entriesWithNext, choice, readings, translations) {
        entriesWithNext.map { (entry, nextMs) -> session.cardLine(entry) to nextMs }
    }
    if (shown.isEmpty()) {
        Text(" ", style = stillText(StillTextSize, ink))
        return
    }
    val size = rememberCardTextSize(shown.map { it.first })
    val look = session.lyricsLook
    val lyricsPosition = remember(session, positionMs) { { positionMs() + session.lyricsOffsetMs } }
    val karaoke = if (sung) {
        val specs = remember(shown, end) {
            shown.map { (line, nextMs) ->
                ShareKaraokeLineSpec(line.entry.text.trim(), line.entry, nextMs ?: end)
            }
        }
        rememberShareKaraokeState(specs, lyricsPosition, look.motionStyle.risesWords)
    } else {
        null
    }
    val waitingInk = waitingInkOf(ink)
    Column(verticalArrangement = Arrangement.spacedBy(CardLineGap)) {
        shown.forEachIndexed { index, (line, _) ->
            key(index) {
                val text = line.entry.text.trim()
                val karaokeLine = karaoke?.lines?.getOrNull(index)
                Column {
                    if (karaokeLine != null) {
                        val waiting = remember(text, waitingInk) {
                            AnnotatedString(text, spanStyle = androidx.compose.ui.text.SpanStyle(color = waitingInk))
                        }
                        val lit = remember(text, ink) {
                            AnnotatedString(text, spanStyle = androidx.compose.ui.text.SpanStyle(color = ink))
                        }
                        ShareKaraokeText(
                            line = karaokeLine,
                            waitingText = waiting,
                            litText = lit,
                            waitingColor = waitingInk,
                            style = stillText(size, ink),
                            baseTextSizeSp = size,
                            motionStyle = look.motionStyle,
                            highBloom = look.highBloom,
                        )
                    } else {
                        Text(text, style = stillText(size, ink))
                    }
                    line.under.forEach { under ->
                        Text(
                            under,
                            style = underText(size, ink),
                            modifier = if (karaokeLine != null) {
                                Modifier.graphicsLayer { alpha = 0.6f + 0.4f * karaokeLine.activeZoom.floatValue }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
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
        val gapPx = with(density) { CardLineGap.toPx() }
        fun heightAt(size: Float) = lines.sumOf { line ->
            measurer.measure(line.entry.text.trim(), stillText(size, Color.White), constraints = constraints).size.height +
                line.under.sumOf { measurer.measure(it, underText(size, Color.White), constraints = constraints).size.height }
        } + gapPx * (lines.size - 1).coerceAtLeast(0)
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
