// PillLyrics.kt

package com.mochyy.mochymusic.floating

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.player.KaraokeHead
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.WordWaitDrop
import com.mochyy.mochymusic.ui.player.advance
import com.mochyy.mochymusic.ui.player.isRtlLine
import com.mochyy.mochymusic.ui.player.karaokeHeadFor
import com.mochyy.mochymusic.ui.player.karaokeTimedRanges
import com.mochyy.mochymusic.ui.player.rememberWordMotionModifier
import com.mochyy.mochymusic.ui.player.sweepHeadX
import com.mochyy.mochymusic.ui.player.withLetterMotionFeatures
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

internal val BubbleColor = Color(0xFFE8EEF1)
private val Ink = Color(0xFF0D0D0E)

// a line not sung yet, the same grey whether it waits underneath or is about to be sung on top, so moving up never flashes
private val Waiting = Ink.copy(alpha = 0.42f)

// two rows fill the bubble, this far apart; the rest wait above and below it
private val RowPitch = 21.dp

// room between the bubble's edge and the text, where a long line fades out
private val SlotInset = 14.dp

private val DotRadius = 3.dp
private val DotGap = 5.dp

// the letter rise is sized for the 32sp lyrics page, this keeps it just as lively at bubble size
private const val PillMotionScale = 0.62f

// a long line slides along so the sweep stays about this far into the bubble
private const val SweepKeep = 0.62f
private const val SlideFollowSec = 0.28f

// the sweep's soft edge behind its head, as a share of the text size, and how far it reaches ahead
private const val SweepSoftEm = 0.55f
private val SweepLead = 2.dp

// asking for frames this long before the first word, so the sweep is already running when it starts
private const val FrameLeadMs = 48L

// the shortest sleep between line checks, so a clock that's a hair behind doesn't spin
private const val MinStepMs = 16L

// the row leaving goes first and the one coming in follows a moment later, so the lines cascade instead of moving as a block
private val LeadSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 260f)
private val TrailSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 150f)

// a row fades out over this much of a slot as it leaves the bubble
private const val EdgeFadeSlots = 0.7f

// one song's rows with their text already measured
@Immutable
internal class PreparedScript(val script: PillScript, val layouts: List<TextLayoutResult>)

private fun rowStyle(font: FontFamily, small: Boolean) = TextStyle(
    fontFamily = font,
    fontWeight = if (small) FontWeight.SemiBold else FontWeight.Bold,
    fontSize = if (small) 13.sp else 15.sp,
    lineHeight = if (small) 17.sp else 19.sp,
    letterSpacing = (-0.03).em,
).withLetterMotionFeatures(true)

// every row measured once, off the main thread, so a line change never lays text out mid-animation
private suspend fun prepare(script: PillScript, measurer: TextMeasurer, font: FontFamily): PreparedScript =
    withContext(Dispatchers.Default) {
        val big = rowStyle(font, small = false)
        val small = rowStyle(font, small = true)
        val layouts = script.rows.map { row ->
            measurer.measure(
                text = row.text,
                style = if (row.small) small else big,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
            )
        }
        PreparedScript(script, layouts)
    }

// the bubble's lyrics: the line being sung on top, what comes next under it, each sliding into its place as the song goes
@Composable
internal fun PillLyrics(state: PillState, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val song = state.song
    val script = state.script
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val font = InterFontFamily
    val prepared by produceState<PreparedScript?>(null, script, song?.id, font) {
        val shown = script ?: PillScript.info(song?.title.orEmpty(), song?.artists?.joinToString { it.name }.orEmpty())
        value = prepare(shown, TextMeasurer(resolver, density, direction, 0), font)
    }
    Crossfade(targetState = prepared, animationSpec = tween(320), modifier = modifier, label = "pillLyrics") { ready ->
        if (ready != null) PillRows(ready, state, width, height, appRtl = direction == LayoutDirection.Rtl)
    }
}

// where a row sits in the bubble for a given slot: 0 is the top row, 1 the bottom one, fractions while it moves
@Immutable
private class RowGeometry(
    val slotLeft: Float,
    val slotWidth: Float,
    val height: Float,
    val pitch: Float,
    val mirrored: Boolean,
    val appRtl: Boolean,
) {
    fun centerY(pos: Float): Float = height / 2f + (pos - 0.5f) * pitch

    fun sideX(side: LyricsAlignment, room: Float): Float = when (side) {
        LyricsAlignment.START -> if (appRtl) room else 0f
        LyricsAlignment.END -> if (appRtl) 0f else room
        LyricsAlignment.CENTER -> room / 2f
    }

    // a singer keeps their side; otherwise the top row starts at the reading side and the bottom row answers from the other
    fun alignedX(side: LyricsAlignment?, pos: Float, width: Float): Float {
        val room = slotWidth - width
        if (side != null) return sideX(side, room)
        val top = sideX(if (mirrored) LyricsAlignment.END else LyricsAlignment.START, room)
        val bottom = sideX(if (mirrored) LyricsAlignment.START else LyricsAlignment.END, room)
        return top + (bottom - top) * pos.coerceIn(0f, 1f)
    }
}

private fun rowAlpha(pos: Float): Float = when {
    pos < 0f -> (1f + pos / EdgeFadeSlots).coerceIn(0f, 1f)
    pos > 1f -> (1f - (pos - 1f) / EdgeFadeSlots).coerceIn(0f, 1f)
    else -> 1f
}

@Composable
private fun PillRows(prepared: PreparedScript, state: PillState, width: Dp, height: Dp, appRtl: Boolean) {
    val script = prepared.script
    var top by remember(script) { mutableIntStateOf(script.stops[script.stopAt(state.lyricsClock())].top) }
    var dotsLit by remember(script) { mutableIntStateOf(-1) }

    // wakes only when the next line, break or dot is due, so nothing runs between lines
    LaunchedEffect(script, state.playing, state.seekTick) {
        while (true) {
            val position = state.lyricsClock()
            val stop = script.stopAt(position)
            top = script.stops[stop].top
            dotsLit = script.dotsLit(stop, position)
            if (!state.playing) break
            val next = script.nextChangeMs(stop, position)
            if (next == Long.MAX_VALUE) break
            delay(((next - position) / state.speed()).toLong().coerceAtLeast(MinStepMs))
        }
    }

    val lead = remember(script) { Animatable(top.toFloat()) }
    val trail = remember(script) { Animatable(top.toFloat()) }
    LaunchedEffect(script) {
        snapshotFlow { top }.collectLatest { target ->
            val goal = target.toFloat()
            // a seek far away jumps most of the way, so only the last stretch glides
            if (abs(goal - lead.value) > 2.5f) {
                val near = goal - sign(goal - lead.value) * 1.5f
                lead.snapTo(near)
                trail.snapTo(near)
            }
            coroutineScope {
                launch { lead.animateTo(goal, LeadSpring) }
                launch { trail.animateTo(goal, TrailSpring) }
            }
        }
    }

    val density = LocalDensity.current
    val geometry = remember(density, width, height, script.mirrored, appRtl) {
        with(density) {
            val inset = SlotInset.toPx()
            RowGeometry(
                slotLeft = inset,
                slotWidth = (width.toPx() - inset * 2f).coerceAtLeast(0f),
                height = height.toPx(),
                pitch = RowPitch.toPx(),
                mirrored = script.mirrored,
                appRtl = appRtl,
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithCache {
                // the fog sits in the inset, so a long line fades at the bubble's edge instead of being cut
                val fog = geometry.slotLeft
                val left = Brush.horizontalGradient(0f to BubbleColor, 1f to BubbleColor.copy(alpha = 0f), startX = 0f, endX = fog)
                val right = Brush.horizontalGradient(0f to BubbleColor.copy(alpha = 0f), 1f to BubbleColor, startX = size.width - fog, endX = size.width)
                onDrawWithContent {
                    drawContent()
                    drawRect(left, size = Size(fog, size.height))
                    drawRect(right, topLeft = Offset(size.width - fog, 0f), size = Size(fog, size.height))
                }
            },
    ) {
        val first = (top - 1).coerceAtLeast(0)
        val last = (top + 2).coerceAtMost(script.rows.lastIndex)
        for (index in first..last) {
            key(index) {
                val row = script.rows[index]
                val showsDots = row.kind == PillRowKind.INTRO ||
                    (row.hostsBreak && (index < top || (index == top && dotsLit >= 0)))
                val live = row.sweeps && !showsDots &&
                    (index == top || (index == top + 1 && row.kind == PillRowKind.BACKING))
                // the row leaving follows the fast spring, the rows moving in lean on the slower one
                val weight = ((index - top + 1) / 2f).coerceIn(0f, 1f)
                PillLyricRow(
                    row = row,
                    layout = prepared.layouts[index],
                    position = { index - (lead.value + (trail.value - lead.value) * weight) },
                    live = live,
                    waiting = row.sweeps && index > top,
                    showsDots = showsDots,
                    dotsLit = if (index < top) PillDots else dotsLit,
                    geometry = geometry,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun PillLyricRow(
    row: PillRow,
    layout: TextLayoutResult,
    position: () -> Float,
    live: Boolean,
    waiting: Boolean,
    showsDots: Boolean,
    dotsLit: Int,
    geometry: RowGeometry,
    state: PillState,
) {
    val slide = remember(row) { mutableFloatStateOf(0f) }
    val dots = animateFloatAsState(if (showsDots) 1f else 0f, tween(320), label = "dots")
    val lit = animateFloatAsState(dotsLit.coerceAtLeast(0).toFloat(), tween(420), label = "dotsLit")
    val rtlText = remember(layout) { row.text.isNotEmpty() && layout.isRtlLine(0) }
    val width = layout.size.width
    val height = layout.size.height
    val textX = { pos: Float ->
        val room = geometry.slotWidth - width
        when {
            room >= 0f -> geometry.alignedX(row.side, pos, width.toFloat())
            // too long for the bubble: the reading end stays in view and slides along with the sweep
            rtlText -> room + slide.floatValue
            else -> -slide.floatValue
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                val pos = position()
                translationX = geometry.slotLeft + textX(pos)
                translationY = geometry.centerY(pos) - height / 2f
                alpha = rowAlpha(pos)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(minOf(width, constraints.maxWidth), minOf(height, constraints.maxHeight)) { placeable.place(0, 0) }
            },
    ) {
        if (live) {
            SweepText(row, layout, state, slide, geometry.slotWidth, rtlText)
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val pos = position()
                        val d = dots.value
                        if (d < 1f) drawRestingText(layout, pos, waiting, alpha = 1f - d)
                        if (d > 0f) {
                            val dotsWidth = (DotRadius.toPx() * 2f) * PillDots + DotGap.toPx() * (PillDots - 1)
                            val x = geometry.alignedX(row.side, pos, dotsWidth) - textX(pos)
                            drawDots(x, lit.value, alpha = d)
                        }
                    },
            )
        }
    }
}

// a line that isn't being sung: grey and resting a little low while it waits, dark once it's had its turn
private fun DrawScope.drawRestingText(layout: TextLayoutResult, pos: Float, waiting: Boolean, alpha: Float) {
    if (waiting) {
        translate(top = WordWaitDrop.toPx() * PillMotionScale) { drawText(layout, color = Waiting, alpha = alpha) }
        return
    }
    drawText(layout, color = lerp(Ink, Waiting, pos.coerceIn(0f, 1f)), alpha = alpha)
}

// a break's dots, lighting one by one as it goes
private fun DrawScope.drawDots(x: Float, lit: Float, alpha: Float) {
    val radius = DotRadius.toPx()
    val step = radius * 2f + DotGap.toPx()
    for (i in 0 until PillDots) {
        val on = (lit - i).coerceIn(0f, 1f)
        drawCircle(
            color = lerp(Waiting, Ink, on),
            radius = radius * (0.8f + 0.2f * on),
            center = Offset(x + radius + i * step, size.height / 2f),
            alpha = alpha,
        )
    }
}

// the line being sung: the colour sweeps through it word by word and each letter rises as it's reached, all drawn without a shader
@Composable
private fun SweepText(
    row: PillRow,
    layout: TextLayoutResult,
    state: PillState,
    slide: MutableFloatState,
    slotWidth: Float,
    rtlText: Boolean,
) {
    val head = remember(row) { karaokeHeadFor(row.text) }
    val timed = remember(row) { karaokeTimedRanges(row.text, row.words.orEmpty()) }
    val holder = remember(layout) { arrayOf<TextLayoutResult?>(layout) }
    val overflow = (layout.size.width - slotWidth).coerceAtLeast(0f)
    val playing = state.playing

    // frames only while the line is being sung or still settling; paused or done, it stops asking
    LaunchedEffect(head, playing, state.seekTick) {
        val firstWordMs = timed.firstOrNull()?.let { (it.startTime * 1000).toLong() } ?: row.startMs
        var lastFrame = -1L
        while (true) {
            if (playing) {
                val wait = firstWordMs - state.lyricsClock() - FrameLeadMs
                if (wait > 0L) {
                    delay((wait / state.speed()).toLong().coerceAtLeast(1L))
                    lastFrame = -1L
                    continue
                }
            }
            val moving = withFrameNanos { now ->
                val dt = if (lastFrame < 0L) 0.016f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.064f)
                lastFrame = now
                val position = state.lyricsClock()
                val moved = head.advance(timed, position / 1000.0, dt, wordMotion = true)
                val slid = overflow > 0f && slide.follow(slideTarget(head, layout, row.text.length, slotWidth, overflow, rtlText), dt)
                moved || slid || (playing && position < row.endMs)
            }
            if (!moving) break
        }
    }

    val motion = rememberWordMotionModifier(
        layoutHolder = holder,
        head = head,
        riseAmplitudeProvider = { 1f },
        fakeGlowProvider = { 0f },
        motionScale = PillMotionScale,
    )
    val brush = remember(layout) { SweepBrush() }
    Spacer(
        Modifier
            .fillMaxSize()
            .then(motion)
            .drawBehind {
                val h = head.offset.floatValue
                val length = row.text.length
                when {
                    h <= 0f -> drawText(layout, color = Waiting)
                    h >= length -> drawText(layout, color = Ink)
                    else -> {
                        val x = layout.sweepHeadX(h, h.toInt().coerceIn(0, length), 0, length, rtlText)
                        val soft = layout.layoutInput.style.fontSize.toPx() * SweepSoftEm
                        drawText(layout, brush = brush.at(x, soft, SweepLead.toPx(), rtlText))
                    }
                }
            },
    )
}

// how far a long line should have slid for the sweep to sit [SweepKeep] into the bubble
private fun slideTarget(head: KaraokeHead, layout: TextLayoutResult, length: Int, slotWidth: Float, overflow: Float, rtl: Boolean): Float {
    val h = head.offset.floatValue
    if (h <= 0f || length == 0) return 0f
    val x = layout.sweepHeadX(h, h.toInt().coerceIn(0, length), 0, length, rtl)
    val read = if (rtl) layout.size.width - x else x
    return (read - slotWidth * SweepKeep).coerceIn(0f, overflow)
}

// eases toward target and says whether it's still on its way
private fun MutableFloatState.follow(target: Float, dt: Float): Boolean {
    val now = floatValue
    if (abs(target - now) < 0.5f) {
        if (now != target) floatValue = target
        return false
    }
    floatValue = now + (target - now) * (1f - exp(-dt / SlideFollowSec))
    return true
}

// the sung colour up to the head, fading into the waiting grey just past it; one gradient for the whole line, made again only when the head moves
private class SweepBrush {
    private var headX = Float.NaN
    private var rtl = false
    private var brush: Brush = Brush.horizontalGradient(listOf(Ink, Waiting))

    fun at(x: Float, soft: Float, lead: Float, rtl: Boolean): Brush {
        if (x == headX && rtl == this.rtl) return brush
        headX = x
        this.rtl = rtl
        val flow = if (rtl) -1f else 1f
        brush = Brush.horizontalGradient(
            0f to Ink,
            1f to Waiting,
            startX = x - soft * flow,
            endX = x + lead * flow,
        )
        return brush
    }
}
