package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.player.PlaybackClock
import com.mochyy.mochymusic.ui.player.styles.BarLevels
import com.mochyy.mochymusic.ui.player.styles.rememberStyleShowing
import com.mochyy.mochymusic.ui.player.video.FollowingLyrics
import com.mochyy.mochymusic.ui.player.video.rememberVideoLyricLook
import com.mochyy.mochymusic.ui.player.video.rememberVideoLyrics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import kotlin.math.sin

// the info tile's pages, top to bottom: the lyrics, the stacked clock, the dial, the small clock over the bars, and the dial over the bars
internal const val InfoPages = 5
internal const val LyricsPage = 0

private val BarWidth = 14.dp
private val BarGap = 20.dp
private val BarRest = 14.dp

// one page of the info tile; a picker card shows the clock in place of the lyrics, which would load a whole song's words for a still card
@Composable
internal fun InfoTilePage(
    page: Int,
    time: BedsideTime,
    ink: BedsideInk,
    live: Boolean,
    visible: () -> Boolean,
    padding: BedsideInsets,
    modifier: Modifier,
) {
    when {
        page == LyricsPage && live -> LyricsTile(ink, padding, modifier)
        page == 2 -> AnalogTile(time, ink, modifier)
        page == 3 -> BarsTile(time, ink, live, visible, padding, modifier)
        page == 4 -> DialBarsTile(time, ink, live, visible, padding, modifier)
        else -> StackedTile(time, ink, modifier)
    }
}

// the song's words following the music, the video mode's own list; the tile turns pages, so the list itself doesn't scroll
@Composable
private fun LyricsTile(ink: BedsideInk, padding: BedsideInsets, modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val lyrics = rememberVideoLyrics()
    val look = rememberVideoLyricLook()
    val clock = remember(connection) {
        PlaybackClock({ connection?.player }, { connection?.lyricsOffsetMs?.value?.toLong() ?: 0L })
    }
    BoxWithConstraints(modifier) {
        FollowingLyrics(
            lyrics = lyrics,
            look = look,
            restingOffset = maxHeight * 0.3f,
            bottomPadding = maxHeight * 0.6f,
            positionProvider = clock,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = padding.top + 28.dp, end = padding.end + 30.dp),
            userScroll = false,
            tint = ink::ink,
        )
    }
}

// the day over the hours over the minutes, as big as the tile allows
@Composable
private fun StackedTile(time: BedsideTime, ink: BedsideInk, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val line = minOf(maxHeight * 0.3f, maxWidth * 0.36f)
        val style = clockStyle(line, FontWeight.ExtraBold, lineHeight = 0.86f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(time.shortDate.lowercase(Locale.getDefault()), style = labelStyle(line * 0.17f, FontWeight.Bold), color = ink.main)
            Spacer(Modifier.height(line * 0.1f))
            FlipDigits(time.hours.padStart(2, '0'), style, ink.main)
            FlipDigits(time.minutes, style, ink.main)
        }
    }
}

@Composable
private fun AnalogTile(time: BedsideTime, ink: BedsideInk, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        AnalogDial(time, ink, Modifier.size(minOf(maxHeight * 0.76f, maxWidth * 0.72f)), ring = true)
    }
}

// a small clock in the top corner and bars along the bottom moving with the music
@Composable
private fun BarsTile(time: BedsideTime, ink: BedsideInk, live: Boolean, visible: () -> Boolean, padding: BedsideInsets, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val digits = smallClockSize()
        Box(Modifier.align(Alignment.TopEnd).smallClockCorner(padding)) {
            FlipDigits("${time.hours}:${time.minutes}", clockStyle(digits, FontWeight.ExtraBold), ink.main)
        }
        BedsideBars(
            ink = ink,
            live = live,
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.4f)
                .padding(horizontal = 12.dp),
        )
    }
}

// the ring dial small in the top corner, in the digits' place on the page before, so the clock holds still as the page turns
@Composable
private fun DialBarsTile(time: BedsideTime, ink: BedsideInk, live: Boolean, visible: () -> Boolean, padding: BedsideInsets, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        AnalogDial(
            time,
            ink,
            Modifier
                .align(Alignment.TopEnd)
                .smallClockCorner(padding)
                .size(smallClockSize() * DialToDigits),
            ring = true,
        )
        BedsideBars(
            ink = ink,
            live = live,
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.4f)
                .padding(horizontal = 12.dp),
        )
    }
}

// a round dial looks smaller than numbers of the same height, so it's set a little taller than the digits
private const val DialToDigits = 1.2f

// how tall the small clock over the bars is, the digits and the dial alike
private fun BoxWithConstraintsScope.smallClockSize(): Dp = minOf(maxHeight * 0.17f, maxWidth * 0.15f)

// where the small clock sits over the bars, the same on both bar pages
private fun Modifier.smallClockCorner(padding: BedsideInsets): Modifier =
    padding(top = padding.top + 30.dp, end = padding.end + 34.dp)

// bars rising from under the tile's edge with the music, low notes on the left; they only run while the page is in view and the song moves them
@Composable
private fun BedsideBars(ink: BedsideInk, live: Boolean, visible: () -> Boolean, modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val bars = remember { BarLevels() }
    val frame = remember { mutableLongStateOf(0L) }
    val showing = rememberStyleShowing()
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    LaunchedEffect(live, playing, connection) {
        if (!live || connection == null) return@LaunchedEffect
        snapshotFlow { showing() && visible() }.distinctUntilChanged().collectLatest { shown ->
            if (!shown) return@collectLatest
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = now
                val spectrum = connection.service.spectrumOf(connection.player)
                val heard = playing && spectrum != null && spectrum.read(connection.player.currentPosition, bars.target(bars.count))
                if (!heard) bars.silence()
                bars.ease(dt)
                frame.longValue = now
                // paused and settled: nothing left to move
                if (!playing && bars.settled()) break
            }
        }
    }
    Spacer(
        modifier
            .damageBoundary()
            .drawBehind {
                frame.longValue
                val width = BarWidth.toPx()
                val gap = BarGap.toPx()
                val count = ((size.width + gap) / (width + gap)).toInt().coerceIn(4, 24)
                bars.resize(count)
                val start = (size.width - (count * width + (count - 1) * gap)) / 2f
                val rest = BarRest.toPx()
                val colour = ink.ink()
                for (i in 0 until count) {
                    val level = if (live) bars.shown[i] else 0.35f + 0.3f * sin(i * 0.9f) * sin(i * 0.37f + 1f)
                    val height = rest + (size.height - rest) * level
                    // only the rounded top shows, the rest runs off the bottom of the tile
                    drawRoundRect(
                        color = colour,
                        topLeft = Offset(start + i * (width + gap), size.height - height),
                        size = Size(width, height + width),
                        cornerRadius = CornerRadius(width / 2f),
                    )
                }
            },
    )
}
