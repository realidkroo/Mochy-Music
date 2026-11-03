// MiniLyricsTicker.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.RubyToken
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.AppMotion

private val TickerFontSize = 17.sp
private val TickerLineHeight = 22.sp

/** The letter motion is sized for 32sp lines; scaled down to match. */
private const val TickerMotionScale = 17f / 32f

/** Soft edges where the line runs past the card. */
private val TickerEdgeFade = 18.dp

/**
 * How far into the card the sweep is kept while a long line slides. Past the middle, so the reader
 * still sees a little of what's coming.
 */
private const val SweepKeepFraction = 0.62f

/** Critically damped follow time of the sideways slide: it trails the sweep, never jumps. */
private const val SlideFollowSec = 0.32f

/** Interlude dots at card size. */
private const val DotsScale = 0.5f

/** The dots row is 56dp tall with its dots inset by the lyric margin; this undoes that inset. */
private val DotsRowInset = LyricsHeaderArtX

private val LineChangeSpring = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val LineFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
/** The same feel as [LineChangeSpring], for a line growing or shrinking as it moves. */
private val LineScaleSpring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)

/** The words of a line not sung yet. */
private const val UnsungAlpha = 0.45f

/** A line leaving the top of a tall card shrinks back a little as it goes. */
private const val LeavingScale = 0.92f

/** Lines grow and shrink from their left end, the end they're read from. */
private val LineStart = TransformOrigin(0f, 0.5f)

/** A tall card's other changes (a seek, a break) roll this share of a row instead of a whole one. */
private const val TallRollShare = 0.35f

/**
 * Where a tall card's pieces sit, shared between its row and the list under it, so that the next
 * line can rise into the row from exactly where it was showing in the list.
 */
@Stable
private class TickerStack {
    /** how tall each line's reading and translation are under the row, px */
    val readingsHeights = mutableStateMapOf<TickerSlot, Int>()

    /** how far down the list of next lines sits, px: it glides to make room for the readings */
    val listTop = Animatable(0f)

    fun readingsOf(slot: TickerSlot): Int = readingsHeights[slot] ?: 0
}

/** The first line at or after [from] with words in it: the one a tall card lists first. */
private fun firstShownFrom(lines: List<LyricsEntry>, from: Int): Int {
    var i = from.coerceAtLeast(0)
    while (i < lines.size && lines[i].text.isBlank()) i++
    return i
}

/**
 * A plain step forward: [to] is the line a tall card was already listing first under [from], so it
 * can rise from there. A seek or a skip has no such place to come from.
 */
private fun risesFromList(lines: List<LyricsEntry>, from: TickerSlot, to: TickerSlot): Boolean =
    to.kind == TickerSlot.LINE &&
        (from.kind == TickerSlot.LINE || from.kind == TickerSlot.GAP) &&
        to.index == firstShownFrom(lines, from.index + 1)

/** What the card is showing. Equal slots are the same content, so nothing re-animates. */
@Immutable
internal data class TickerSlot(val kind: Int, val index: Int) {
    companion object {
        const val EMPTY = 0
        const val LINE = 1
        const val GAP = 2
        const val END = 3
        /** The lyrics are still being looked up. */
        const val LOADING = 4
    }
}

internal fun tickerSlot(lines: List<LyricsEntry>, position: Long): TickerSlot {
    if (lines.isEmpty()) return TickerSlot(TickerSlot.EMPTY, -1)
    val index = LyricsUtils.findCurrentLineIndex(lines, position)
    if (index >= lines.size) return TickerSlot(TickerSlot.END, lines.lastIndex)
    if (index < 0) {
        return if (lines.first().time >= MinInterludeGapMs) {
            TickerSlot(TickerSlot.GAP, -1)
        } else {
            TickerSlot(TickerSlot.LINE, 0)
        }
    }
    // A long instrumental gap after this line reads as dots, like on the lyrics page.
    val next = lines.getOrNull(index + 1)
    if (next != null) {
        val end = LyricsUtils.lineEndMs(lines, index)
        if (next.time - end >= MinInterludeGapMs && position > end) {
            return TickerSlot(TickerSlot.GAP, index)
        }
    }
    return TickerSlot(TickerSlot.LINE, index)
}

/**
 * The lyrics page, card-sized: the current line with the same karaoke sweep and letter motion. A
 * line too long for the card slides left as it's sung, keeping the sweep in view, and each new line
 * rolls up from below as the last one leaves. On a tall card, where the next line is already
 * showing underneath, it rises from there instead and grows into the row.
 *
 * Only the slot (which line, or a gap) is collected from the playback position, so the card
 * recomposes once per line, not on every position tick; the sweep itself runs in frame callbacks.
 */
@Composable
internal fun MiniLyricsTicker(
    lines: List<LyricsEntry>,
    /** Lyrics have been looked up (an empty [lines] then means there are none). */
    loaded: Boolean,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
    /**
     * How much taller than usual the card is, px (pulled in the player editor). The line keeps the
     * card's usual row at the top; under it go its reading, its translation and the lines after it.
     */
    growthProvider: () -> Float = { 0f },
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val tall by remember { derivedStateOf { growthProvider() > 0.5f } }
    val rowPx = with(LocalDensity.current) { PlayerCardHeight.roundToPx() }
    // frame-accurate, so the card's sweep and gap dots glide instead of stepping with the poller
    val positionProvider = remember(playerConnection) {
        PlaybackClock({ playerConnection.player }, { playerConnection.lyricsOffsetMs.value.toLong() })
    }
    val slot by remember(lines, playerConnection) {
        combine(playerConnection.progressState, playerConnection.lyricsOffsetMs) { progress, offsetMs ->
            tickerSlot(lines, progress.position + offsetMs)
        }.distinctUntilChanged()
    }.collectAsState(initial = tickerSlot(lines, positionProvider()))

    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val waveEnabled = motionStyle.shaded
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    // Still looking the lyrics up is its own state, so the dots hand over to the first line (or to
    // "No lyrics") with the same roll as any line change.
    val shown = if (!loaded && slot.kind == TickerSlot.EMPTY) TickerSlot(TickerSlot.LOADING, -1) else slot

    val density = LocalDensity.current
    val tuckPx = with(density) { ExtraLinesTuck.toPx() }
    val stepPx = with(density) { UpcomingLineHeight.toPx() }
    val stack = remember(lines) { TickerStack() }

    // The line gets the card's own row; a taller card hangs the line's reading and translation
    // under it and lists what comes next below those, as its own list that scrolls up a line at a
    // time (see TickerUpcoming). Clipped as a whole.
    Column(modifier = modifier.clipToBounds()) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                if (tall && risesFromList(lines, initialState, targetState)) {
                    // The next line was already showing under this one, small and dim. It rises from
                    // that spot, growing and brightening into the row, while this line and its
                    // readings lift away by the same amount: one scroll, nothing swapped out.
                    // Measured once, here, so the list gliding on can't pull it mid-flight.
                    val rise = (rowPx / 2f - tuckPx + stack.listTop.value + stepPx / 2f).roundToInt()
                    val synced = !lines[targetState.index].words.isNullOrEmpty()
                    val startAlpha = (NextLineAlpha / (if (synced) UnsungAlpha else 1f)).coerceAtMost(1f)
                    val startScale = UpcomingFontSize.value / TickerFontSize.value
                    (slideInVertically(LineChangeSpring) { rise } +
                        scaleIn(LineScaleSpring, startScale, LineStart) +
                        fadeIn(LineFadeSpring, startAlpha))
                        .togetherWith(
                            slideOutVertically(LineChangeSpring) { -rise } +
                                scaleOut(LineScaleSpring, LeavingScale, LineStart) +
                                fadeOut(LineFadeSpring)
                        )
                        .using(SizeTransform(clip = false))
                } else {
                    // Anything else: the old line rolls up and out, the new one up and in - the
                    // lyrics page's scroll, in a card. On a tall card only part of a row, so the
                    // new line doesn't sweep across the list under it on its way in.
                    val roll = if (tall) (rowPx * TallRollShare).roundToInt() else rowPx
                    (slideInVertically(LineChangeSpring) { minOf(it, roll) } + fadeIn(LineFadeSpring))
                        .togetherWith(slideOutVertically(LineChangeSpring) { -minOf(it, roll) } + fadeOut(LineFadeSpring))
                        .using(SizeTransform(clip = false))
                }
            },
            contentAlignment = Alignment.CenterStart,
            label = "miniLyricsLine",
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerCardHeight),
        ) { current ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (current.kind) {
                    TickerSlot.LINE, TickerSlot.END -> lines.getOrNull(current.index)?.let { entry ->
                        TickerLine(
                            entry = entry,
                            dim = current.kind == TickerSlot.END,
                            lineEndMs = LyricsUtils.lineEndMs(lines, current.index),
                            positionProvider = positionProvider,
                            motionStyle = motionStyle,
                            waveEnabled = waveEnabled,
                            highBloom = highBloom,
                            morph = morph,
                        )
                        // part of the line's own slot, so they arrive and leave with it
                        if (tall && current.kind == TickerSlot.LINE) {
                            TickerReadings(
                                entry = entry,
                                growthProvider = growthProvider,
                                onHeight = { stack.readingsHeights[current] = it },
                                modifier = Modifier.align(Alignment.TopStart).hangBelow(rowPx),
                            )
                        }
                    }

                    TickerSlot.GAP -> {
                        val startMs = if (current.index < 0) 0L else LyricsUtils.lineEndMs(lines, current.index)
                        val endMs = lines.getOrNull(current.index + 1)?.time ?: startMs
                        LyricsInterludeDots(
                            startMs = startMs,
                            endMs = endMs,
                            positionProvider = positionProvider,
                            accentColor = Color.White,
                            alignment = LyricsAlignment.START,
                            modifier = Modifier.graphicsLayer {
                                scaleX = DotsScale
                                scaleY = DotsScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = -DotsRowInset.toPx() * DotsScale
                            },
                        )
                    }

                    TickerSlot.LOADING -> LyricsLoadingDots(alignStart = true)

                    else -> Text(
                        text = "No lyrics for this song",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (tall) TickerUpcoming(lines, shown, stack, growthProvider)
    }
}

/** Lays this out [y] px further down without taking any room where it is, so it hangs under the row. */
private fun Modifier.hangBelow(y: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, y) }
}

/** How bright the next line is under a tall card's current one; each line after it a little dimmer. */
private const val NextLineAlpha = 0.38f
private const val NextLineDimStep = 0.14f

/** Lines after the current one a tall card lists at most. The ones that don't fit stay hidden. */
private const val UpcomingLinesMax = 6

/** The first line after the current one is barely soft; each one further down a little softer. */
private val UpcomingBlurFirst = 0.6.dp
private val UpcomingBlurStep = 0.45.dp

/** The upcoming lines' size and line height; the height is also how far the list moves for each new line. */
private val UpcomingFontSize = 13.sp
private val UpcomingLineHeight = 17.sp

private val UpcomingScrollSpring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)

/**
 * A reading waits this long before fading in: on a new line, until the line has mostly risen into
 * the row, and on a reading that arrives late, until the list below has started making room.
 */
private const val ReadingSettleDelayMs = 140L
private val ReadingFade = tween<Float>(durationMillis = 260)

/**
 * The lines after the current one on a taller card, under its reading and translation, as many as
 * the card has room for, each dimmer and a little more blurred than the one above. When the song
 * moves on, the list scrolls up one line while the first of them rises into the card's row (see
 * MiniLyricsTicker), and the rest step up and brighten into the places they move to.
 */
@Composable
private fun TickerUpcoming(lines: List<LyricsEntry>, slot: TickerSlot, stack: TickerStack, growthProvider: () -> Float) {
    // during a break, the lines after it; at the end, nothing
    val firstUpcoming = when (slot.kind) {
        TickerSlot.LINE, TickerSlot.GAP -> slot.index + 1
        else -> return
    }
    val upcoming = remember(lines, firstUpcoming) {
        (firstUpcoming.coerceAtLeast(0) until lines.size)
            .filter { lines[it].text.isNotBlank() }
            .take(UpcomingLinesMax)
    }

    val stepPx = with(LocalDensity.current) { UpcomingLineHeight.toPx() }
    val currentFirst by rememberUpdatedState(firstUpcoming)
    val currentSlot by rememberUpdatedState(slot)
    LaunchedEffect(stack, stepPx) {
        var shownFrom = currentFirst
        stack.listTop.snapTo(stack.readingsOf(currentSlot).toFloat())
        // One driver for the list's place, so a new line and readings that arrive late never fight.
        // Moving on starts the list where it was, a line lower, and glides it up to sit under the
        // new readings; a seek back just shows the new spot.
        snapshotFlow { currentFirst to stack.readingsOf(currentSlot) }.collectLatest { (first, readings) ->
            when {
                first > shownFrom -> stack.listTop.snapTo(stack.listTop.value + stepPx)
                first < shownFrom -> stack.listTop.snapTo(readings.toFloat())
            }
            shownFrom = first
            stack.listTop.animateTo(readings.toFloat(), UpcomingScrollSpring)
        }
    }

    Box(modifier = Modifier.graphicsLayer { translationY = stack.listTop.value }) {
        CardExtraLines(growthProvider, startProvider = { stack.listTop.value }) {
            upcoming.forEachIndexed { i, index ->
                // keyed by the line, so a line stepping up keeps its text and brightens from where it was
                key(index) {
                    UpcomingLine(
                        text = lines[index].text,
                        alpha = NextLineAlpha * (1f - NextLineDimStep * i).coerceAtLeast(0.3f),
                        blur = UpcomingBlurFirst + UpcomingBlurStep * i,
                    )
                }
            }
        }
    }
}

/**
 * The current line's reading and translation, hung under it on a tall card. They fill in as the
 * lyrics' readings pass gets to the line; what isn't there takes no room. [onHeight] gets how tall
 * they are, which is where the list of next lines goes.
 */
@Composable
private fun TickerReadings(
    entry: LyricsEntry,
    growthProvider: () -> Float,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val romanized by entry.romanizedTextFlow.collectAsState()
    val ruby by entry.rubyFlow.collectAsState()
    val translated by entry.translatedTextFlow.collectAsState()
    // Japanese and Chinese carry their reading word by word over the text; in one line it's those
    // readings in order
    val reading = romanized ?: ruby?.takeIf { it.isNotEmpty() }?.let { rubyReading(entry.text, it) }
    CardExtraLines(growthProvider, modifier.onSizeChanged { onHeight(it.height) }) {
        reading?.let { SettlingLine(it, alpha = 0.62f, italic = true) }
        translated?.takeIf { it.isNotBlank() }?.let { SettlingLine(it, alpha = 0.82f) }
    }
}

/** A reading line that fades in a moment after it appears (see [ReadingSettleDelayMs]). */
@Composable
private fun SettlingLine(text: String, alpha: Float, italic: Boolean = false) {
    val settled = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(ReadingSettleDelayMs)
        settled.animateTo(1f, ReadingFade)
    }
    CardExtraLine(text, alpha, italic = italic, modifier = Modifier.graphicsLayer { this.alpha = settled.value })
}

/**
 * One line further down the lyrics. Blurred through a layer of its own, so the list scrolling
 * moves the finished picture rather than blurring it again each frame. Android 12+ only: on older
 * phones the line is just dimmer. A change of brightness (the line stepping up) eases in on an outer
 * layer, which fades that finished picture rather than blurring it again.
 */
@Composable
private fun UpcomingLine(text: String, alpha: Float, blur: Dp) {
    val shownAlpha = remember { Animatable(alpha) }
    LaunchedEffect(alpha) { shownAlpha.animateTo(alpha, UpcomingScrollSpring) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = UpcomingFontSize, lineHeight = UpcomingLineHeight),
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .graphicsLayer { this.alpha = shownAlpha.value }
            .blur(if (AppMotion.elementBlur) blur else 0.dp, BlurredEdgeTreatment.Unbounded),
    )
}

/** [text] read out: each run with a reading replaced by it, the rest (Latin words, say) kept as it is. */
private fun rubyReading(text: String, ruby: List<RubyToken>): String = buildString {
    var at = 0
    ruby.sortedBy { it.start }.forEach { token ->
        if (token.start > at) append(text, at, token.start.coerceAtMost(text.length))
        if (isNotEmpty() && !last().isWhitespace()) append(' ')
        append(token.text)
        at = maxOf(at, token.end)
    }
    if (at < text.length) append(text, at, text.length)
}.trim()

@Composable
private fun TickerLine(
    entry: LyricsEntry,
    dim: Boolean,
    lineEndMs: Long,
    positionProvider: () -> Long,
    motionStyle: LyricsMotionStyle,
    waveEnabled: Boolean,
    highBloom: Boolean,
    morph: CardMorphState,
) {
    val parts = remember(entry.text, entry.words) { splitBackingVocal(entry.text, entry.words) }
    val text = parts.lead.ifBlank { entry.text }
    val words = parts.leadWords
    val karaoke = !dim && !words.isNullOrEmpty() && text.isNotEmpty()

    val probe = remember(entry) { SweepProbe() }
    val viewport = remember { mutableFloatStateOf(0f) }
    val scroll = remember(entry) { mutableFloatStateOf(0f) }

    // The sideways slide: follows the sweep for a word-synced line, the clock for a line-synced
    // one. A critically damped follow, so a sweep that jumps (a seek) still glides.
    LaunchedEffect(entry, karaoke) {
        var value = 0f
        var velocity = 0f
        var lastMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                val dt = if (lastMs < 0L) 0.016f else ((frameMs - lastMs) / 1000f).coerceIn(0f, 0.064f)
                lastMs = frameMs
                val overflow = (probe.textWidth - viewport.floatValue).coerceAtLeast(0f)
                val target = when {
                    overflow <= 0f -> 0f
                    karaoke -> (probe.headX - viewport.floatValue * SweepKeepFraction).coerceIn(0f, overflow)
                    else -> {
                        val span = (lineEndMs - entry.time).coerceAtLeast(1L).toFloat()
                        val through = (positionProvider() - entry.time) / span
                        overflow * smoothstepF((through - 0.1f) / 0.75f)
                    }
                }
                val omega = 1f / SlideFollowSec
                val y = value - target
                val e = kotlin.math.exp(-omega * dt)
                val tmp = (velocity + omega * y) * dt
                value = target + (y + tmp) * e
                velocity = (velocity - omega * tmp) * e
                if (kotlin.math.abs(value - target) < 0.05f && kotlin.math.abs(velocity) < 1f) {
                    value = target
                    velocity = 0f
                }
                if (value != scroll.floatValue) scroll.floatValue = value
                morph.cardLyricScroll = value
            }
        }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport.floatValue = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade only the edge the line actually runs past, and only as far as it does.
                val overflow = (probe.textWidth - size.width).coerceAtLeast(0f)
                if (overflow <= 0f) return@drawWithContent
                val fade = TickerEdgeFade.toPx()
                val left = (scroll.floatValue / fade).coerceIn(0f, 1f)
                val right = ((overflow - scroll.floatValue) / fade).coerceIn(0f, 1f)
                val edge = (fade / size.width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 1f - left),
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - right),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -scroll.floatValue },
        ) {
            if (karaoke) {
                KaraokeSweepText(
                    text = text,
                    words = words!!,
                    positionProvider = positionProvider,
                    waveAmplitudeProvider = { if (waveEnabled) 1f else 0f },
                    riseAmplitudeProvider = { 1f },
                    baseColor = { Color.White.copy(alpha = UnsungAlpha) },
                    highlightColor = Color.White,
                    fontSize = TickerFontSize,
                    lineHeight = TickerLineHeight,
                    highBloom = highBloom,
                    motionStyle = motionStyle,
                    singleLine = true,
                    motionScale = TickerMotionScale,
                    probe = probe,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = TickerFontSize,
                        lineHeight = TickerLineHeight,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (dim) 0.55f else 1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { probe.textWidth = it.size.width.toFloat() },
                )
            }
        }
    }
}
