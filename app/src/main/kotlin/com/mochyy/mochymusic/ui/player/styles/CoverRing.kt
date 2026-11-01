package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** a circle in the ring's own pixels: the cover's, which the ring runs round */
@Immutable
data class RingCircle(val center: Offset, val radius: Float)

/**
 * The ring round a round cover: what the seek bar on it is doing ([expand]: 0 a thin line on the
 * cover's edge, 1 the full bar just outside it with the times at its ends), and the time a held
 * bar has been dragged to. Shared with whatever the bar pushes aside (the Poster's lyrics).
 */
@Stable
class CoverRingState internal constructor(private val scope: CoroutineScope) {
    val expand = Animatable(0f)
    var scrubMs by mutableLongStateOf(-1L)
    internal var held = false
    private var job: Job? = null

    /** grows the bar, and lets it settle back after [forMs] unless it's held by then */
    fun peek(forMs: Long) {
        job?.cancel()
        job = scope.launch {
            expand.animateTo(1f, ExpandSpring)
            delay(forMs)
            if (!held) expand.animateTo(0f, CollapseSpring)
        }
    }

    internal fun grab() {
        job?.cancel()
        held = true
        job = scope.launch { expand.animateTo(1f, ExpandSpring) }
    }

    internal fun letGo(afterMs: Long) {
        held = false
        peek(afterMs)
    }
}

@Composable
fun rememberCoverRingState(): CoverRingState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverRingState(scope) }
}

private val ExpandSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val CollapseSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 170f)

/** how long the bar stays out when playback starts */
private const val StartPeekMs = 3_000L
/** and after a touch lets go */
private const val ReleasePeekMs = 1_400L

/**
 * The seek bar and the visualizer round [circle], over the lower edge of a cover that runs off the
 * screen's sides (the Poster's). The bar lies thin on the cover's rim; touched, it moves out past
 * the rim and thickens, the elapsed time and the song's length riding its ends, and pushes the
 * visualizer's bars further out - drag along it to scrub, let go to play from there. It also comes
 * out on its own for a few seconds when the music starts.
 *
 * The visualizer's bars stand out from the rim, low notes at the left, a little see-through. They
 * follow the sound actually heard: the spectrum is read at the player's position every frame.
 */
@Composable
fun CoverRing(
    state: CoverRingState,
    circle: RingCircle,
    visualizer: Boolean,
    /** the live player (not a selector card): it seeks, peeks and listens */
    live: Boolean,
    position: LivePosition,
    modifier: Modifier = Modifier,
    side: RingSide = RingSide.Bottom,
) {
    val connection = LocalPlayerConnection.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val labelStyle = remember {
        TextStyle(fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
    }
    val currentCircle by rememberUpdatedState(circle)
    val showing = rememberStyleShowing()
    val interactive = LocalStyleInteractive.current

    // out for a few seconds whenever the music starts: play pressed, or the next song begun. Not
    // while the player is collapsed, where nobody would see it.
    LaunchedEffect(connection, live) {
        if (!live || connection == null) return@LaunchedEffect
        var wasPlaying = connection.isPlaying.value
        var lastId = connection.mediaMetadata.value?.id
        combine(connection.isPlaying, connection.mediaMetadata.map { it?.id }.distinctUntilChanged()) { playing, id -> playing to id }
            .collect { (playing, id) ->
                val started = playing && (!wasPlaying || id != lastId)
                wasPlaying = playing
                lastId = id
                if (started && !state.held && showing()) state.peek(StartPeekMs)
            }
    }

    // the bars: what they show, eased frame by frame toward what's heard
    val bars = remember { BarLevels() }
    val frame = remember { mutableLongStateOf(0L) }
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    LaunchedEffect(visualizer, live, playing, connection, showing) {
        if (!visualizer || !live || connection == null) {
            bars.clear()
            frame.longValue++
            return@LaunchedEffect
        }
        // They run while any of the player is on screen, the close included: stopped the moment a
        // close began, they vanished with the poster still in view. Collapsed, they stop, and rise
        // from nothing at the next open.
        snapshotFlow { showing() }.collectLatest { shown ->
            if (!shown) {
                bars.clear()
                frame.longValue++
                return@collectLatest
            }
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = now
                val spectrum = connection.service.spectrumOf(connection.player)
                val heard = playing && spectrum != null &&
                    spectrum.read(connection.player.currentPosition, bars.target(bars.count))
                if (!heard) bars.silence()
                bars.ease(dt)
                frame.longValue = now
                // paused and settled: nothing left to move
                if (!playing && bars.settled()) break
            }
        }
    }

    Spacer(
        modifier = modifier
            .then(
                if (!live || connection == null) Modifier else Modifier.pointerInput(connection, interactive) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                        // nothing taken from the mini player, or from the sheet on its way down
                        if (!interactive()) return@awaitEachGesture
                        val c = currentCircle
                        val geometry = RingGeometry(c, Size(size.width.toFloat(), size.height.toFloat()), density, side)
                        if (!geometry.onBand(down.position)) return@awaitEachGesture
                        val duration = connection.player.duration.takeIf { it > 0 } ?: return@awaitEachGesture
                        state.grab()
                        // a swipe along the bar scrubs; one across it (down, to close the player) is left alone
                        var travel = Offset.Zero
                        var scrubbing = false
                        var lastTick = -1L
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!scrubbing) {
                                travel += change.positionChange()
                                if (travel.getDistance() < slop) continue
                                val along = geometry.alongness(change.position, travel)
                                if (along < 0.55f) {
                                    // not ours: settle back and let the player have it
                                    state.letGo(250L)
                                    return@awaitEachGesture
                                }
                                scrubbing = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                            val ms = (duration * geometry.fractionAt(change.position)).toLong()
                            state.scrubMs = ms
                            val tick = ms / 10_000L
                            if (tick != lastTick) {
                                if (lastTick >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastTick = tick
                            }
                        }
                        if (scrubbing && state.scrubMs >= 0L) {
                            connection.player.seekTo(state.scrubMs.coerceIn(0L, duration))
                        }
                        state.letGo(ReleasePeekMs)
                        scope.launch {
                            // the bar keeps the dragged time until the player has caught up with it
                            delay(400)
                            if (!state.held) state.scrubMs = -1L
                        }
                    }
                }
            )
            .drawBehind {
                frame.longValue
                val c = currentCircle
                val geometry = RingGeometry(c, size, density, side)
                val e = state.expand.value
                if (visualizer) {
                    drawBars(geometry, bars, e, preview = !live)
                }
                val duration = connection?.player?.duration?.takeIf { it > 0 } ?: 0L
                // the frame's one position, shared with everything else the style draws
                val at = when {
                    state.scrubMs >= 0L -> state.scrubMs
                    live -> position.positionMs()
                    else -> (duration * 0.38f).toLong()
                }
                drawSeekArc(geometry, e, if (duration > 0) at.toFloat() / duration else 0.38f, at, duration, measurer, labelStyle)
            },
    )
}

/** which part of the cover's rim is on screen: its bottom (upright) or its right side (sideways) */
enum class RingSide { Bottom, Right }

/**
 * Where everything on the ring sits. The bar runs along the rim that's on screen, from the left
 * (upright) or the top (sideways), its ends kept a margin in from the screen's edges (wider when
 * expanded, for the times beyond them); the visualizer covers that whole rim.
 */
private class RingGeometry(val circle: RingCircle, val size: Size, density: Float, val side: RingSide) {
    private val dp = density
    val center get() = circle.center
    val radius get() = circle.radius

    /** the bar's radius: on the cover at rest, past the rim when out */
    fun barRadius(e: Float) = radius + lerp(-15f, 22f, e) * dp

    /** the angle (degrees, 0 right, 90 down) where a circle of [r] crosses x, on its lower side */
    fun angleAtX(x: Float, r: Float): Float {
        val dx = (x - center.x).coerceIn(-r * 0.999f, r * 0.999f)
        return Math.toDegrees(atan2(sqrt(r * r - dx * dx), dx).toDouble()).toFloat()
    }

    /** the same where it crosses y, on its right side */
    fun angleAtY(y: Float, r: Float): Float {
        val dy = (y - center.y).coerceIn(-r * 0.999f, r * 0.999f)
        return Math.toDegrees(atan2(dy, sqrt(r * r - dy * dy)).toDouble()).toFloat()
    }

    /** where a circle of [r] crosses into the screen [from] its first edge and [to] its last */
    fun span(r: Float, from: Float, to: Float): Pair<Float, Float> = when (side) {
        RingSide.Bottom -> angleAtX(from, r) to angleAtX(size.width - to, r)
        RingSide.Right -> angleAtY(from, r) to angleAtY(size.height - to, r)
    }

    /** the bar's ends: the song's start, then its end */
    fun barEnds(e: Float): Pair<Float, Float> =
        span(barRadius(e), lerp(26f, 64f, e) * dp, lerp(26f, 52f, e) * dp)

    /** the visualizer's: nearly the whole rim that shows */
    fun barsSpan(): Pair<Float, Float> = when (side) {
        RingSide.Bottom -> span(radius, 14f * dp, 10f * dp)
        RingSide.Right -> span(radius, 10f * dp, 10f * dp)
    }

    /** a touch near enough the bar to take it */
    fun onBand(p: Offset): Boolean {
        val d = hypot(p.x - center.x, p.y - center.y)
        if (d < radius - 46f * dp || d > radius + 50f * dp) return false
        val (start, end) = barEnds(0f)
        val a = angleOf(p)
        return a in (minOf(start, end) - 4f)..(maxOf(start, end) + 4f)
    }

    fun angleOf(p: Offset) = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()

    /** how much of [move] runs along the circle at [p] rather than across it, 0..1 */
    fun alongness(p: Offset, move: Offset): Float {
        val rx = p.x - center.x
        val ry = p.y - center.y
        val rl = hypot(rx, ry).coerceAtLeast(1f)
        val ml = move.getDistance().coerceAtLeast(1f)
        val across = abs((move.x * rx + move.y * ry) / (rl * ml))
        return sqrt((1f - across * across).coerceAtLeast(0f))
    }

    /** how far along the bar [p] is, 0 at its start */
    fun fractionAt(p: Offset): Float {
        val (start, end) = barEnds(1f)
        return ((angleOf(p) - start) / (end - start)).coerceIn(0f, 1f)
    }
}

/** the bars' levels: what the sound says, and what's drawn, easing toward it */
internal class BarLevels {
    var count = 28
        private set
    private var targets = FloatArray(count)
    var shown = FloatArray(count)
        private set
    // the loudest bar lately, falling slowly, so quiet songs fill the bars too
    private var ceiling = 0.6f

    fun resize(n: Int) {
        if (n == count) return
        count = n
        targets = FloatArray(n)
        shown = FloatArray(n)
    }

    fun target(n: Int): FloatArray {
        resize(n)
        return targets
    }

    fun silence() = java.util.Arrays.fill(targets, 0f)

    fun clear() {
        silence()
        java.util.Arrays.fill(shown, 0f)
    }

    fun settled() = shown.all { it < 0.002f }

    fun ease(dt: Float) {
        var loudest = 0f
        for (v in targets) loudest = maxOf(loudest, v)
        ceiling = if (loudest > ceiling) loudest else maxOf(0.35f, ceiling - dt * 0.12f)
        val floor = ceiling - Range
        val rise = 1f - exp(-dt / 0.045f)
        val fall = 1f - exp(-dt / 0.17f)
        for (i in 0 until count) {
            val level = ((targets[i] - floor) / Range).coerceIn(0f, 1f)
            val shaped = level * level * (3f - 2f * level)
            val current = shown[i]
            shown[i] = current + (shaped - current) * (if (shaped > current) rise else fall)
        }
    }

    private companion object {
        /** how much of the bands, below the loudest, the bars span */
        const val Range = 0.62f
    }
}

private fun DrawScope.drawBars(g: RingGeometry, bars: BarLevels, e: Float, preview: Boolean) {
    val r = g.radius
    // the whole visible rim, nearly edge to edge
    val (start, end) = g.barsSpan()
    val spanRad = Math.toRadians(abs(start - end).toDouble()).toFloat()
    val count = ((r * spanRad) / 21.dp.toPx()).toInt().coerceIn(12, 40)
    bars.resize(count)
    val thickness = 5.dp.toPx()
    val gap = (13f + 34f * e).dp.toPx()
    val minLength = 4.dp.toPx()
    val maxLength = 50.dp.toPx()
    val alpha = 0.55f * (1f - 0.5f * e)
    val color = Color.White.copy(alpha = alpha)
    for (i in 0 until count) {
        val t = if (count == 1) 0f else i / (count - 1f)
        val degrees = start + (end - start) * t
        val rad = degrees * (PI.toFloat() / 180f)
        val dir = Offset(cos(rad), sin(rad))
        val level = if (preview) 0.35f + 0.3f * sin(i * 0.9f) * sin(i * 0.37f + 1f) else bars.shown[i]
        // upright, the left end sits over the start of the words: its bars stay a little shorter
        val taper = if (g.side == RingSide.Bottom) 0.65f + 0.35f * t.coerceAtMost(0.3f) / 0.3f else 1f
        val length = minLength + (maxLength - minLength) * level * taper
        val inner = g.center + dir * (r + gap)
        drawLine(color, inner, inner + dir * length, strokeWidth = thickness, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSeekArc(
    g: RingGeometry,
    e: Float,
    fraction: Float,
    positionMs: Long,
    durationMs: Long,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val r = g.barRadius(e)
    val (start, end) = g.barEnds(e)
    val sweep = end - start
    val stroke = lerp(3f, 7f, e).dp.toPx()
    val topLeft = g.center - Offset(r, r)
    val arcSize = Size(r * 2f, r * 2f)
    // on the cover it's only a hint (a third as bright); out past the rim, the full bar
    val ink = lerp(0.3f, 1f, e)
    drawArc(
        color = Color.White.copy(alpha = 0.38f * ink),
        startAngle = start, sweepAngle = sweep, useCenter = false,
        topLeft = topLeft, size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val played = sweep * fraction.coerceIn(0f, 1f)
    drawArc(
        color = Color.White.copy(alpha = 0.96f * ink),
        startAngle = start, sweepAngle = if (abs(played) < 0.01f) 0.01f * kotlin.math.sign(sweep) else played, useCenter = false,
        topLeft = topLeft, size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val shown = ((e - 0.3f) / 0.7f).coerceIn(0f, 1f)
    if (shown <= 0.001f || durationMs <= 0L) return
    val toward = kotlin.math.sign(sweep)
    ringLabel(makeTimeString(positionMs), g, start, r, outward = -toward, shown, measurer, labelStyle)
    ringLabel(makeTimeString(durationMs), g, end, r, outward = toward, shown, measurer, labelStyle)
}

/** a time just past one end of the bar, turned along it, reading the right way up */
private fun DrawScope.ringLabel(
    text: String,
    g: RingGeometry,
    endDegrees: Float,
    r: Float,
    /** which way round, in degrees, is away from the bar */
    outward: Float,
    shown: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    // measured at full colour and faded as it's drawn: a fading colour in the style missed the measurer's cache every frame of the bar coming out
    val layout = measurer.measure(text, style)
    // clear of the round cap by a little, sliding in from further out as it appears
    val gapPx = (layout.size.width / 2f) + (8f + 10f * (1f - shown)).dp.toPx()
    val gapDegrees = Math.toDegrees((gapPx / r).toDouble()).toFloat()
    val degrees = endDegrees + outward * gapDegrees
    val rad = degrees * (PI.toFloat() / 180f)
    val at = g.center + Offset(cos(rad), sin(rad)) * r
    val s = 0.86f + 0.14f * shown
    // along the rim, turned whichever way keeps it the right way up
    var turn = degrees - 90f
    while (turn > 90f) turn -= 180f
    while (turn < -90f) turn += 180f
    rotate(turn, at) {
        scale(s, s, at) {
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f), alpha = shown)
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
