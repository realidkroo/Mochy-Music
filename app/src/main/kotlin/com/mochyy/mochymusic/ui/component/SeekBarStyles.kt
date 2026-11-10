package com.mochyy.mochymusic.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import kotlin.math.sin

/**
 * Seek bar appearance, chosen per player style.
 *
 * The wavy variants are drawn as a [Path] rather than an AGSL shader, so unlike the lyrics glow
 * they animate identically on every supported Android version.
 */
/** [displayName] is the plain English name; the UI shows [labelRes], which follows the app language. */
enum class SeekBarStyle(val displayName: String, @StringRes val labelRes: Int) {
    DEFAULT("Default", R.string.pc_style_default),
    M3_EXPRESSIVE("Material 3 Expressive 1", R.string.pc_seek_m3_expressive_1),
    M3_EXPRESSIVE_LINE("Material 3 Expressive slider 2", R.string.pc_seek_m3_expressive_2),
    PLAIN("plain", R.string.pc_seek_plain),
}

private const val WaveSpeedMillis = 1400
private val WaveLength = 22.dp
private val WaveAmplitude = 3.5.dp

/** One pass of the loading sweep across the bar. */
private const val LoadingSweepMillis = 1500

/** The sweep's length, as a share of the bar. */
private const val LoadingSweepLength = 0.34f

/** How bright the sweep gets at its middle. */
private const val LoadingSweepPeak = 0.7f

/** Eases in and out of each pass, so the light speeds across the middle and slows at the ends. */
private val LoadingSweepEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/**
 * Draws the seek bar for [style].
 *
 * [fraction] is playback position in 0..1. [active] lifts the bar while the user is dragging,
 * matching the existing default track's behaviour.
 */
@Composable
fun SeekBarTrack(
    style: SeekBarStyle,
    fraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    animateWave: Boolean = true,
    active: Boolean = false,
    /** The track is still loading: a light sweeps along the bar instead of the wave moving. */
    loading: Boolean = false,
) {
    SeekBarTrack(
        style = style,
        fractionProvider = { fraction },
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        modifier = modifier,
        animateWave = animateWave,
        active = active,
        loading = loading,
    )
}

/**
 * Same bar, but [fractionProvider] is read while drawing, so a moving position redraws the bar
 * without recomposing whatever holds it. [clockRunning] false holds the wave and sweep still
 * (the player is collapsed and the bar can't be seen).
 */
@Composable
fun SeekBarTrack(
    style: SeekBarStyle,
    fractionProvider: () -> Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    animateWave: Boolean = true,
    active: Boolean = false,
    loading: Boolean = false,
    clockRunning: Boolean = true,
    // how far the bar has opened under the finger, 0 to 1; without it the bar springs open on its own while active
    expandProvider: (() -> Float)? = null,
) {
    val wavy = style == SeekBarStyle.M3_EXPRESSIVE || style == SeekBarStyle.M3_EXPRESSIVE_LINE
    val ownExpand = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = SeekBarExpandSpring,
        label = "seekExpand",
    )
    val expand = expandProvider ?: { ownExpand.value }

    // The wave flattens out when playback is paused, the user grabs the bar, or the track is still
    // loading, so the shape always reflects whether audio is actually moving.
    val waveScale by animateFloatAsState(
        targetValue = if (animateWave && !active && !loading) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "seekWaveScale",
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (loading) 1f else 0f,
        animationSpec = tween(durationMillis = if (loading) 320 else 220),
        label = "seekLoading",
    )

    // One clock for the wave and the loading sweep, running only while one of them is on screen.
    // This used to be an infinite transition that never stopped, so the bar - and the frame loop
    // with it - redrew every frame even with the music paused and the bar flat.
    val needsClock = (wavy && waveScale > 0f) || (wavy && animateWave && !active && !loading) ||
        loading || loadingAlpha > 0f
    val clockMs = remember { mutableLongStateOf(0L) }
    val wavePath = remember { Path() }
    LaunchedEffect(needsClock, clockRunning) {
        if (!needsClock || !clockRunning) return@LaunchedEffect
        // the clock is absolute time, so catch up now instead of showing the old phase for a frame
        clockMs.longValue = System.nanoTime() / 1_000_000L
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }

    // fixed room for the fully open bar (and its spring's overshoot), so opening it only redraws and never moves anything
    val height: Dp = when (style) {
        SeekBarStyle.DEFAULT -> 16.dp
        SeekBarStyle.M3_EXPRESSIVE, SeekBarStyle.M3_EXPRESSIVE_LINE -> 20.dp
        SeekBarStyle.PLAIN -> 16.dp
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val f = fractionProvider().coerceIn(0f, 1f)
        val open = expand()
        val phase = if (wavy && waveScale > 0f) {
            (clockMs.longValue % WaveSpeedMillis) / WaveSpeedMillis.toFloat()
        } else {
            0f
        }
        when (style) {
            SeekBarStyle.DEFAULT -> drawDefaultTrack(f, open, activeColor, inactiveColor)
            SeekBarStyle.PLAIN -> drawPlainTrack(f, open, activeColor, inactiveColor)
            SeekBarStyle.M3_EXPRESSIVE -> drawWavyTrack(
                path = wavePath,
                fraction = f,
                open = open,
                phase = phase,
                waveScale = waveScale,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumb = ThumbShape.ROUND,
            )
            SeekBarStyle.M3_EXPRESSIVE_LINE -> drawWavyTrack(
                path = wavePath,
                fraction = f,
                open = open,
                phase = phase,
                waveScale = waveScale,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumb = ThumbShape.LINE,
            )
        }
        if (loadingAlpha > 0.005f) {
            val t = (clockMs.longValue % LoadingSweepMillis) / LoadingSweepMillis.toFloat()
            drawLoadingSweep(
                progress = LoadingSweepEasing.transform(t),
                color = activeColor,
                alpha = loadingAlpha,
                thickness = if (style == SeekBarStyle.DEFAULT) defaultBarThickness(open) else null,
            )
        }
    }
}

/**
 * A soft light running along the bar while the track loads. It fades in at its leading edge and
 * out at its tail, so it reads as light passing through the bar rather than a block moving on it.
 */
private fun DrawScope.drawLoadingSweep(progress: Float, color: Color, alpha: Float, thickness: Dp?) {
    val y = size.height / 2f
    val stroke = thickness?.toPx() ?: 4.dp.toPx()
    val inset = if (thickness != null) stroke / 2f else 0f
    val length = size.width * LoadingSweepLength
    val centre = -length / 2f + (size.width + length) * progress
    val from = centre - length / 2f
    val to = centre + length / 2f
    val start = from.coerceAtLeast(inset)
    val end = to.coerceAtMost(size.width - inset)
    if (end <= start) return
    drawLine(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = color.alpha * LoadingSweepPeak * alpha),
            1f to Color.Transparent,
            startX = from,
            endX = to,
        ),
        start = Offset(start, y),
        end = Offset(end, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private enum class ThumbShape { ROUND, LINE }

// one spring for the bar opening under the finger and everything that moves with it
val SeekBarExpandSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)

// the default bar is 7dp at rest and 14dp held
private fun defaultBarThickness(open: Float): Dp = (7f + 7f * open).dp

// thin bars get a touch thicker and their thumb a touch bigger while held
private fun heldStroke(open: Float): Dp = (4f + 2f * open).dp

private fun DrawScope.drawDefaultTrack(fraction: Float, open: Float, activeColor: Color, inactiveColor: Color) {
    val h = defaultBarThickness(open).toPx()
    val y = size.height / 2f
    drawLine(
        color = inactiveColor,
        start = Offset(h / 2f, y),
        end = Offset(size.width - h / 2f, y),
        strokeWidth = h,
        cap = StrokeCap.Round,
    )
    val end = (h / 2f + (size.width - h) * fraction).coerceAtLeast(h / 2f)
    drawLine(
        color = activeColor,
        start = Offset(h / 2f, y),
        end = Offset(end, y),
        strokeWidth = h,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPlainTrack(fraction: Float, open: Float, activeColor: Color, inactiveColor: Color) {
    val y = size.height / 2f
    val stroke = heldStroke(open).toPx()
    val thumbRadius = (7f + 1f * open).dp.toPx()
    drawLine(
        color = inactiveColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    val x = size.width * fraction
    drawLine(
        color = activeColor,
        start = Offset(0f, y),
        end = Offset(x, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawCircle(color = activeColor, radius = thumbRadius, center = Offset(x, y))
}

/**
 * Material 3's expressive slider: the played portion ripples, the rest stays flat, and the thumb
 * sits on the boundary between them.
 */
private fun DrawScope.drawWavyTrack(
    path: Path,
    fraction: Float,
    open: Float,
    phase: Float,
    waveScale: Float,
    activeColor: Color,
    inactiveColor: Color,
    thumb: ThumbShape,
) {
    val y = size.height / 2f
    val stroke = heldStroke(open).toPx()
    val wavelength = WaveLength.toPx()
    val amplitude = WaveAmplitude.toPx() * waveScale

    val thumbGap = when (thumb) {
        ThumbShape.ROUND -> 9.dp.toPx()
        ThumbShape.LINE -> 7.dp.toPx()
    }
    val usable = (size.width - thumbGap).coerceAtLeast(0f)
    val x = usable * fraction

    if (x > 0.5f) {
        // one path for every frame; reset keeps its memory instead of allocating a new one
        path.reset()
        path.apply {
            moveTo(0f, y)
            var px = 0f
            // 2dp steps: fine enough that the curve reads as smooth, coarse enough to stay cheap
            // when this redraws every frame.
            val step = 2.dp.toPx()
            while (px < x) {
                val t = px / wavelength + phase * 2f
                lineTo(px, y + sin(t * 2f * Math.PI.toFloat()) * amplitude)
                px += step
            }
            lineTo(x, y + sin((x / wavelength + phase * 2f) * 2f * Math.PI.toFloat()) * amplitude)
        }
        drawPath(path = path, color = activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }

    if (x < size.width) {
        drawLine(
            color = inactiveColor,
            start = Offset((x + thumbGap).coerceAtMost(size.width), y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    when (thumb) {
        ThumbShape.ROUND -> drawCircle(color = activeColor, radius = (7f + 2f * open).dp.toPx(), center = Offset(x, y))
        ThumbShape.LINE -> drawLine(
            color = activeColor,
            start = Offset(x, y - size.height / 2.4f),
            end = Offset(x, y + size.height / 2.4f),
            strokeWidth = (4f + 1f * open).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
