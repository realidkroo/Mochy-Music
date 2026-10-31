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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.LyricsGlowLine
import com.mochyy.mochymusic.ui.player.alignmentFor
import com.mochyy.mochymusic.ui.player.lineLook
import com.mochyy.mochymusic.ui.player.lyricAnchorIndex
import com.mochyy.mochymusic.ui.player.lyricsLineInsets
import com.mochyy.mochymusic.ui.player.restingLineStyle
import com.mochyy.mochymusic.ui.player.share.MochyMark
import com.mochyy.mochymusic.ui.player.usesAgentAlignment
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val MusicCardWidth = 172.dp
private val LyricsCardWidth = 296.dp

// how small the lyrics page's lines are drawn to fit on the card
private const val LineScale = 0.56f

// how tall the badge's bars stand on a still picture
private val RestingBars = floatArrayOf(0.55f, 0.9f, 0.4f)

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
    val lines = session.lyrics.found.orEmpty()
    val lyricsPosition = remember(session, positionMs) { { positionMs() + session.lyricsOffsetMs } }
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
            Spacer(Modifier.width(8.dp))
            PlayingBadge(positionMs, moving, ink)
        }
        Spacer(Modifier.height(14.dp))
        if (moving && session.lyricsSynced) {
            SungLyrics(session, lines, lyricsPosition)
        } else {
            StillLyrics(session, lines, ink)
        }
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

// three little bars in a circle, dancing with the clip in a video and resting on a picture
@Composable
private fun PlayingBadge(positionMs: () -> Long, moving: Boolean, ink: Color) {
    Spacer(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.16f))
            .drawBehind {
                val barWidth = size.width * 0.1f
                val gap = size.width * 0.08f
                val left = (size.width - barWidth * 3 - gap * 2) / 2f
                val t = if (moving) positionMs().toFloat() else 0f
                for (i in 0..2) {
                    val swing = if (moving) abs(sin(t / (310f + i * 70f) * PI.toFloat() + i)) else RestingBars[i]
                    val height = size.height * (0.18f + 0.3f * swing)
                    drawRoundRect(
                        ink,
                        topLeft = Offset(left + i * (barWidth + gap), (size.height - height) / 2f),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            },
    )
}

// the picked lines as plain bold text for a picture, every one of them; without a pick, the lines sung in the share's moment
@Composable
private fun StillLyrics(session: SongShareSession, lines: List<LyricsEntry>, ink: Color) {
    val pick = session.pick
    val start = session.windowStartMs + session.lyricsOffsetMs
    val end = session.windowEndMs + session.lyricsOffsetMs
    val text = if (pick != null) {
        pick.chosen.joinToString("\n") { it.second }
    } else {
        remember(lines, start, end) { linesInWindow(lines, start, end).joinToString("\n") { it.text.trim() } }
    }
    val size = rememberStillTextSize(text)
    Text(
        text.ifBlank { " " },
        style = stillText(size, ink),
        maxLines = if (pick != null) Int.MAX_VALUE else StillLines + 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun stillText(size: Float, ink: Color) = cardText(size, FontWeight.Bold, ink, -0.7f).copy(lineHeight = (size * 25f / 19f).sp)

// the usual size, or a step smaller at a time until the lines fit the card's room
@Composable
private fun rememberStillTextSize(text: String): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, density) {
        val width = with(density) { LyricsColumnWidth.roundToPx() }
        val room = with(density) { LyricsRoom.toPx() }
        fun heightAt(size: Float) = measurer.measure(text, stillText(size, Color.White), constraints = Constraints(maxWidth = width)).size.height
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

// the picked lines, every one of them on the card at once like the picture, each sung the way the lyrics page sings it: no list scrolling past
@Composable
private fun SungLyrics(session: SongShareSession, lines: List<LyricsEntry>, positionMs: () -> Long) {
    val look = session.lyricsLook
    val pick = session.pick
    val start = session.windowStartMs + session.lyricsOffsetMs
    val end = session.windowEndMs + session.lyricsOffsetMs
    val sung = remember(pick?.chosen, lines, start, end) {
        pick?.range?.mapNotNull { pick.entries.getOrNull(it) } ?: linesInWindow(lines, start, end)
    }
    if (sung.isEmpty()) {
        StillLyrics(session, lines, Color.White)
        return
    }
    val useAgentAlignment = remember(sung) { usesAgentAlignment(sung) }
    // the last line stays lit until the clip ends, instead of every line going dim once it's sung
    val current by remember(sung) { derivedStateOf { LyricsUtils.findCurrentLineIndex(sung, positionMs()).coerceAtMost(sung.lastIndex) } }
    val anchor by remember(sung) { derivedStateOf { lyricAnchorIndex(sung, positionMs()).coerceAtMost(sung.lastIndex) } }
    val density = LocalDensity.current
    val scale = rememberLineScale(sung, useAgentAlignment)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * scale, fontScale = 1f),
        LocalContentColor provides Color.White,
    ) {
        Column(Modifier.fillMaxWidth()) {
            sung.forEachIndexed { index, entry ->
                key(index) {
                    SungLine(entry, index, current = { current }, anchor = { anchor }, positionMs, look, alignmentFor(entry, useAgentAlignment))
                }
            }
        }
    }
}

// how small the page's lines are drawn on the card: the usual size, or smaller when the picked lines would run past the card's room
@Composable
private fun rememberLineScale(sung: List<LyricsEntry>, useAgentAlignment: Boolean): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.headlineSmall
    return remember(sung, useAgentAlignment, density, base) {
        // everything here is in the page's own size, before the card shrinks it
        val column = LyricsColumnWidth / LineScale
        val style = restingLineStyle(base, active = true, letterMotion = false)
        val height = sung.sumOf { entry ->
            val insets = lyricsLineInsets(alignmentFor(entry, useAgentAlignment))
            val width = with(density) { (column - insets.start - insets.end - insets.padH * 2).roundToPx() }.coerceAtLeast(1)
            val text = measurer.measure(entry.text, style, constraints = Constraints(maxWidth = width)).size.height
            text + with(density) { (insets.top * 2 + insets.padV * 2).roundToPx() }
        }
        val room = with(density) { (LyricsRoom / LineScale).toPx() }
        LineScale * minOf(1f, room / height.coerceAtLeast(1))
    }
}

@Composable
private fun SungLine(
    entry: LyricsEntry,
    index: Int,
    current: () -> Int,
    anchor: () -> Int,
    positionMs: () -> Long,
    look: ShareLyricsLook,
    alignment: LyricsAlignment,
) {
    // worked out per line, so a line change only recomposes the lines whose look changes
    val lineLook by remember(index) {
        derivedStateOf { lineLook(index, current(), anchor(), unsynced = false, interlude = false) }
    }
    LyricsGlowLine(
        entry = entry,
        state = lineLook.state,
        // every picked line stays sharp on the card, so the whole passage can be read
        blurStage = 0,
        positionProvider = positionMs,
        accentColor = Color.White,
        onClick = {},
        alignment = alignment,
        waveEnabled = look.motionStyle.shaded,
        highBloom = look.highBloom,
        motionStyle = look.motionStyle,
    )
}
