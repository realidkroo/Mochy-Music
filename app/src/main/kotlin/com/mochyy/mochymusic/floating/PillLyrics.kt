// PillLyrics.kt

package com.mochyy.mochymusic.floating

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
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
import com.mochyy.mochymusic.modules.skin.ActiveFloating
import com.mochyy.mochymusic.modules.skin.ActiveSkin
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
import com.mochyy.mochymusic.ui.theme.LookFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

// room between the bubble's edge and the text, where a long line fades out
private val SlotInset = 14.dp

private val DotRadius = 3.dp
private val DotGap = 5.dp

// the letter rise is sized for the 32sp lyrics page, this keeps it just as lively at bubble size
private const val PillMotionScale = 0.62f

// a long line slides along so the sweep stays about this far into the bubble
private const val SweepKeep = 0.62f
private const val SlideFollowSec = 0.28f

// a long line with no timed words rests this long at each end of its time and glides to its end in between
private const val GlideRestMs = 700L

// a title or artist too long for the bubble rests this long, then reads along to its end at this pace, and back
private const val BrowseRestMs = 2_500L
private val BrowsePerSecond = 36.dp

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

// the line coming up waits this much smaller than the one being sung, and grows to full size as it takes the top
private const val NextScale = 0.84f
private val NextSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 280f)

// how a row that isn't being swept is coloured: sung dark, a soft companion of the sung line, or grey and waiting its turn
private enum class RowLook { SUNG, SOFT, WAITING }

// one song's rows with their text already measured
@Immutable
internal class PreparedScript(val script: PillScript, val layouts: List<TextLayoutResult>)

private fun rowStyle(font: FontFamily, small: Boolean, baseSize: Float? = null, customWeight: FontWeight? = null) = TextStyle(
    fontFamily = font,
    fontWeight = customWeight ?: if (small) FontWeight.SemiBold else FontWeight.Bold,
    fontSize = (baseSize?.let { if (small) (it - 2f).coerceAtLeast(10f) else it } ?: if (small) 13f else 15f).sp,
    lineHeight = (baseSize?.let { if (small) (it + 2f) else (it + 4f) } ?: if (small) 17f else 19f).sp,
    letterSpacing = (-0.03).em,
).withLetterMotionFeatures(true)

// every row measured once, off the main thread, so a line change never lays text out mid-animation
private suspend fun prepare(script: PillScript, measurer: TextMeasurer, font: FontFamily, baseSize: Float?, customWeight: FontWeight?): PreparedScript =
    withContext(Dispatchers.Default) {
        val big = rowStyle(font, small = false, baseSize = baseSize, customWeight = customWeight)
        val small = rowStyle(font, small = true, baseSize = baseSize, customWeight = customWeight)
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
internal fun PillLyrics(
    state: PillState,
    width: Dp,
    activeFloating: ActiveFloating? = null,
    transparentEdge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val song = state.song
    val script = state.script
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val skin = activeFloating?.floating
    val font = activeFloating?.skin?.moduleId?.let(ActiveSkin::playerFont) ?: LookFont.family ?: InterFontFamily
    val baseSize = skin?.fontSize
    val customWeight = skin?.weight
    val prepared by produceState<PreparedScript?>(null, script, song?.id, font, baseSize, customWeight) {
        val shown = script ?: PillScript.info(song?.title.orEmpty(), song?.artists?.joinToString { it.name }.orEmpty())
        value = prepare(shown, TextMeasurer(resolver, density, direction, 0), font, baseSize, customWeight)
    }
    Crossfade(targetState = prepared, animationSpec = tween(320), modifier = modifier, label = "pillLyrics") { ready ->
        if (ready != null) PillRows(ready, state, width, appRtl = direction == LayoutDirection.Rtl, transparentEdge = transparentEdge)
    }
}

// where a row sits in the bubble for a given slot: 0 is the top row, the last slot the bottom one, fractions while it moves
@Immutable
private class RowGeometry(
    val slotLeft: Float,
    val slotWidth: Float,
    val topPad: Float,
    val pitch: Float,
    val slots: Int,
    val appRtl: Boolean,
) {
    // rows hang from the top, so a bubble growing a row taller only uncovers the one underneath
    fun centerY(pos: Float): Float = topPad + (pos + 0.5f) * pitch

    // a row keeps its side the whole way up, the same side the lyrics page gives it
    fun alignedX(side: LyricsAlignment, width: Float): Float {
        val room = slotWidth - width
        return when (side) {
            LyricsAlignment.START -> if (appRtl) room else 0f
            LyricsAlignment.END -> if (appRtl) 0f else room
            LyricsAlignment.CENTER -> room / 2f
        }
    }

    fun alpha(pos: Float): Float {
        val last = (slots - 1).toFloat()
        return when {
            pos < 0f -> (1f + pos / EdgeFadeSlots).coerceIn(0f, 1f)
            pos > last -> (1f - (pos - last) / EdgeFadeSlots).coerceIn(0f, 1f)
            else -> 1f
        }
    }
}

@Composable
private fun PillRows(prepared: PreparedScript, state: PillState, width: Dp, appRtl: Boolean, transparentEdge: Boolean = false) {
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
    val tint = LocalPillTint.current
    val geometry = remember(density, width, script.slots, appRtl) {
        with(density) {
            val inset = SlotInset.toPx()
            val pitch = PillMetrics.RowPitch.toPx()
            RowGeometry(
                slotLeft = inset,
                slotWidth = (width.toPx() - inset * 2f).coerceAtLeast(0f),
                topPad = (PillMetrics.BubbleHeight.toPx() - pitch * BaseSlots) / 2f,
                pitch = pitch,
                slots = script.slots,
                appRtl = appRtl,
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (transparentEdge) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val fog = geometry.slotLeft
                            val mask = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                (fog / size.width.coerceAtLeast(1f)).coerceIn(0.01f, 0.2f) to Color.Black,
                                (1f - fog / size.width.coerceAtLeast(1f)).coerceIn(0.8f, 0.99f) to Color.Black,
                                1f to Color.Transparent,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(mask, blendMode = BlendMode.DstIn)
                            }
                        }
                } else {
                    Modifier.drawWithCache {
                        // the fog sits in the inset, so a long line fades at the bubble's edge instead of being cut
                        val fog = geometry.slotLeft
                        val bubble = tint.bubble
                        val left = Brush.horizontalGradient(0f to bubble, 1f to bubble.copy(alpha = 0f), startX = 0f, endX = fog)
                        val right = Brush.horizontalGradient(0f to bubble.copy(alpha = 0f), 1f to bubble, startX = size.width - fog, endX = size.width)
                        onDrawWithContent {
                            drawContent()
                            drawRect(left, size = Size(fog, size.height))
                            drawRect(right, topLeft = Offset(size.width - fog, 0f), size = Size(fog, size.height))
                        }
                    }
                },
            ),
    ) {
        val first = (top - 1).coerceAtLeast(0)
        val last = (top + script.slots).coerceAtMost(script.rows.lastIndex)
        val currentLine = script.rows[top].line
        for (index in first..last) {
            key(index) {
                val row = script.rows[index]
                val showsDots = row.kind == PillRowKind.INTRO ||
                    (row.hostsBreak && (index < top || (index == top && dotsLit >= 0)))
                // the line being sung is every row of it that's in view: the lead, its reading, its backing vocals
                val inLine = index >= top && index - top < script.slots && row.line == currentLine
                val look = when {
                    index > top && !inLine -> RowLook.WAITING
                    row.sweeps || row.kind == PillRowKind.LEAD || row.kind == PillRowKind.TITLE -> RowLook.SUNG
                    else -> RowLook.SOFT
                }
                // the row leaving follows the fast spring, the rows moving in lean on the slower one
                val weight = ((index - top + 1) / 2f).coerceIn(0f, 1f)
                PillLyricRow(
                    row = row,
                    layout = prepared.layouts[index],
                    position = { index - (lead.value + (trail.value - lead.value) * weight) },
                    current = inLine,
                    live = row.sweeps && !showsDots && inLine,
                    look = look,
                    showsDots = showsDots,
                    dotsLit = if (index < top) PillDots else dotsLit,
                    geometry = geometry,
                    tint = tint,
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
    current: Boolean,
    live: Boolean,
    look: RowLook,
    showsDots: Boolean,
    dotsLit: Int,
    geometry: RowGeometry,
    tint: PillTint,
    state: PillState,
) {
    val width = layout.size.width
    val height = layout.size.height
    // a line too long for the bubble with no timed words to follow slides along by itself, or its end never shows
    val overflow = (width - geometry.slotWidth).coerceAtLeast(0f)
    val glides = overflow > 0f && !row.sweeps
    val slide = remember(row, overflow) {
        mutableFloatStateOf(if (glides && !row.isInfo) glideAt(row, state.lyricsClock(), overflow) else 0f)
    }
    if (glides) {
        val browsePx = with(LocalDensity.current) { BrowsePerSecond.toPx() }
        LaunchedEffect(row, overflow, current) {
            snapshotFlow { state.playing to state.seekTick }.collectLatest { (playing, _) ->
                when {
                    !row.isInfo -> glide(row, slide, overflow, state, playing)
                    current && playing -> browse(slide, overflow, browsePx)
                }
            }
        }
    }
    val dots = animateFloatAsState(if (showsDots) 1f else 0f, tween(320), label = "dots")
    val lit = animateFloatAsState(dotsLit.coerceAtLeast(0).toFloat(), tween(420), label = "dotsLit")
    // a row that stops waiting eases into its colour instead of flicking
    val waiting = animateFloatAsState(if (look == RowLook.WAITING) 1f else 0f, tween(300), label = "rowWaiting")
    val rtlText = remember(layout) { row.text.isNotEmpty() && layout.isRtlLine(0) }
    val next = animateFloatAsState(
        if (look == RowLook.WAITING && (row.kind == PillRowKind.LEAD || row.kind == PillRowKind.TITLE)) 1f else 0f,
        NextSpring,
        label = "rowNext",
    )
    // it shrinks toward its own side, so its edge stays lined up with the line above
    val pivot = remember(layout, geometry) {
        when {
            geometry.slotWidth < width -> if (rtlText) 1f else 0f
            row.side == LyricsAlignment.CENTER -> 0.5f
            (row.side == LyricsAlignment.END) != geometry.appRtl -> 1f
            else -> 0f
        }
    }
    val textX = {
        val room = geometry.slotWidth - width
        when {
            room >= 0f -> geometry.alignedX(row.side, width.toFloat())
            // too long for the bubble: the reading end stays in view and slides along with the sweep
            rtlText -> room + slide.floatValue
            else -> -slide.floatValue
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                val pos = position()
                translationX = geometry.slotLeft + textX()
                translationY = geometry.centerY(pos) - height / 2f
                alpha = geometry.alpha(pos)
                val scale = 1f - (1f - NextScale) * next.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivot, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(minOf(width, constraints.maxWidth), minOf(height, constraints.maxHeight)) { placeable.place(0, 0) }
            },
    ) {
        if (live) {
            SweepText(row, layout, state, tint, slide, geometry.slotWidth, rtlText)
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val d = dots.value
                        if (d < 1f) drawRestingText(layout, restingColor(tint, look, position(), waiting.value), drop = row.sweeps, waiting.value, alpha = 1f - d)
                        if (d > 0f) {
                            val dotsWidth = (DotRadius.toPx() * 2f) * PillDots + DotGap.toPx() * (PillDots - 1)
                            val x = geometry.alignedX(row.side, dotsWidth) - textX()
                            drawDots(tint, x, lit.value, alpha = d)
                        }
                    },
            )
        }
    }
}

// a sung row darkens as it climbs to the top, a soft row keeps its quiet grey, and both fade toward the waiting grey while they wait
private fun restingColor(tint: PillTint, look: RowLook, pos: Float, waiting: Float): Color {
    val settled = if (look == RowLook.SOFT) tint.soft else lerp(tint.ink, tint.waiting, pos.coerceIn(0f, 1f))
    return lerp(settled, tint.waiting, waiting)
}

// a line that isn't being swept; one that will be sweeps rests a little low while it waits, where its letters rise from
private fun DrawScope.drawRestingText(layout: TextLayoutResult, color: Color, drop: Boolean, waiting: Float, alpha: Float) {
    if (drop && waiting > 0f) {
        translate(top = WordWaitDrop.toPx() * PillMotionScale * waiting) { drawText(layout, color = color, alpha = alpha) }
        return
    }
    drawText(layout, color = color, alpha = alpha)
}

// a break's dots, lighting one by one as it goes
private fun DrawScope.drawDots(tint: PillTint, x: Float, lit: Float, alpha: Float) {
    val radius = DotRadius.toPx()
    val step = radius * 2f + DotGap.toPx()
    for (i in 0 until PillDots) {
        val on = (lit - i).coerceIn(0f, 1f)
        drawCircle(
            color = lerp(tint.waiting, tint.ink, on),
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
    tint: PillTint,
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
                    h <= 0f -> drawText(layout, color = tint.waiting)
                    h >= length -> drawText(layout, color = tint.ink)
                    else -> {
                        val x = layout.sweepHeadX(h, h.toInt().coerceIn(0, length), 0, length, rtlText)
                        val soft = layout.layoutInput.style.fontSize.toPx() * SweepSoftEm
                        // the alpha is set every time: left out, the text keeps whatever alpha its last draw had, and a line that started grey swept at a fraction of its strength
                        drawText(layout, brush = brush.at(x, soft, SweepLead.toPx(), rtlText, tint.ink, tint.waiting), alpha = 1f)
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

// the rest at each end of a line's time, shorter for a quick line so it still has room to glide
private fun glideRest(row: PillRow): Long = minOf(GlideRestMs, (row.endMs - row.startMs) / 6)

// how far a long line with no timed words has slid at positionMs: none while it rests at the start, all of it by the rest at the end
private fun glideAt(row: PillRow, positionMs: Long, overflow: Float): Float {
    val rest = glideRest(row)
    val span = (row.endMs - row.startMs - rest * 2).coerceAtLeast(1L)
    val through = ((positionMs - row.startMs - rest).toFloat() / span).coerceIn(0f, 1f)
    return overflow * through * through * (3f - 2f * through)
}

// keeps a long line where the clock says it should be; it sleeps until its glide starts and stops asking for frames once it's done
private suspend fun glide(row: PillRow, slide: MutableFloatState, overflow: Float, state: PillState, playing: Boolean) {
    val from = row.startMs + glideRest(row)
    val to = row.endMs - glideRest(row)
    var lastFrame = -1L
    while (true) {
        val position = state.lyricsClock()
        if (playing && position < from && slide.floatValue == glideAt(row, position, overflow)) {
            delay(((from - position) / state.speed()).toLong().coerceAtLeast(MinStepMs))
            lastFrame = -1L
            continue
        }
        val moving = withFrameNanos { now ->
            val dt = if (lastFrame < 0L) 0.016f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.064f)
            lastFrame = now
            val at = state.lyricsClock()
            // a seek eases over to the new spot instead of jumping there
            val settling = slide.follow(glideAt(row, at, overflow), dt)
            settling || (playing && at in from until to)
        }
        if (!moving && (!playing || state.lyricsClock() >= to)) break
    }
}

// a title too long for the bubble rests, reads along to its end, rests, and goes back, for as long as the song plays
private suspend fun browse(slide: MutableFloatState, overflow: Float, pxPerSecond: Float) {
    val spec = tween<Float>((overflow / pxPerSecond * 1000f).roundToInt().coerceAtLeast(400), easing = FastOutSlowInEasing)
    while (true) {
        delay(BrowseRestMs)
        animate(slide.floatValue, overflow, animationSpec = spec) { value, _ -> slide.floatValue = value }
        delay(BrowseRestMs)
        animate(slide.floatValue, 0f, animationSpec = spec) { value, _ -> slide.floatValue = value }
    }
}

// the sung colour up to the head, fading into the waiting grey just past it; one gradient for the whole line, made again only when the head moves
private class SweepBrush {
    private var headX = Float.NaN
    private var rtl = false
    private var sung = Color.Unspecified
    private var waiting = Color.Unspecified
    private var brush: Brush = Brush.horizontalGradient(listOf(Color.Black, Color.Gray))

    fun at(x: Float, soft: Float, lead: Float, rtl: Boolean, sung: Color, waiting: Color): Brush {
        if (x == headX && rtl == this.rtl && sung == this.sung && waiting == this.waiting) return brush
        headX = x
        this.rtl = rtl
        this.sung = sung
        this.waiting = waiting
        val flow = if (rtl) -1f else 1f
        brush = Brush.horizontalGradient(
            0f to sung,
            1f to waiting,
            startX = x - soft * flow,
            endX = x + lead * flow,
        )
        return brush
    }
}
