package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.player.MusicVolume
import com.mochyy.mochymusic.ui.player.smoothstepF
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// how tall the speaker's cone is; both ends of the volume line draw the same speaker at this size
internal val SpeakerHeight = 11.dp

// the quiet speaker's width with room for its slash, and the loud one's out to its third wave, in speaker heights
internal const val QuietSpeakerWidth = 0.72f
internal const val LoudSpeakerWidth = 1.32f

// where the waves curve round, their sizes, and how far round each one reaches either side, in speaker heights and degrees
private const val WaveCentreX = 0.34f
private val WaveRadii = floatArrayOf(0.44f, 0.68f, 0.92f)
private const val WaveHalfAngle = 36f
private const val LineWidth = 0.12f

// a wave left dark still shows faintly, so the speaker never looks broken at low volume
private const val WaveDim = 0.28f

// how far the echoes travel right before they're gone, in speaker heights
private const val EchoTravel = 0.5f

private val MuteDraw = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
private val MuteLift = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
private val EchoRun = tween<Float>(durationMillis = 560, easing = LinearOutSlowInEasing)

// the speakers' own motion: the slash over a muted one, and the loud one's waves echoing out as the volume goes up
@Stable
internal class SpeakerMotion(muted: Boolean) {
    val mute = Animatable(if (muted) 1f else 0f)

    // 0 when an echo has just started, 1 when it's done
    val echo = Animatable(1f)
}

@Composable
internal fun rememberSpeakerMotion(volume: MusicVolume): SpeakerMotion {
    val motion = remember(volume) { SpeakerMotion(volume.shown <= 0f) }
    LaunchedEffect(motion) {
        snapshotFlow { volume.shown <= 0f }.collectLatest { muted ->
            motion.mute.animateTo(if (muted) 1f else 0f, if (muted) MuteDraw else MuteLift)
        }
    }
    LaunchedEffect(motion) {
        var last = volume.shown
        snapshotFlow { volume.shown }.collect { now ->
            val up = now > last + 0.001f
            last = now
            // a volume key, or a drag reaching the top: not every step of a drag, or it would never stop echoing
            val wanted = up && (!volume.isDragging || now >= 1f)
            // holding a key keeps them coming, but each gets a little way out before the next starts
            if (wanted && motion.echo.value > 0.3f) {
                launch {
                    motion.echo.snapTo(0f)
                    motion.echo.animateTo(1f, EchoRun)
                }
            }
        }
    }
    return motion
}

// the speaker's body in a box h tall, its corners rounded: the square back and the cone opening to the right
internal fun speakerBody(h: Float): Path {
    val cy = h / 2f
    val back = 0.19f * h
    return roundedPolygon(
        listOf(
            Offset(0f, cy - back),
            Offset(0.25f * h, cy - back),
            Offset(0.58f * h, 0f),
            Offset(0.58f * h, h),
            Offset(0.25f * h, cy + back),
            Offset(0f, cy + back),
        ),
        radius = 0.09f * h,
    )
}

// the quiet end: the speaker alone, with a slash drawn over it top to bottom as it mutes
internal fun DrawScope.drawQuietSpeaker(body: Path, x: Float, cy: Float, h: Float, grow: Float, alpha: Float, mute: Float) {
    val width = QuietSpeakerWidth * h
    translate(x, cy - h / 2f) {
        scale(grow, grow, Offset(width / 2f, h / 2f)) {
            // the body steps back a little under the slash so the slash reads
            drawPath(body, Color.White, alpha = alpha * (1f - 0.45f * mute))
            if (mute > 0.002f) {
                val from = Offset(-0.02f * h, -0.06f * h)
                val to = Offset(0.7f * h, 1.06f * h)
                drawLine(
                    color = Color.White,
                    start = from,
                    end = from + (to - from) * mute,
                    strokeWidth = LineWidth * h,
                    cap = StrokeCap.Round,
                    alpha = alpha,
                )
            }
        }
    }
}

// the loud end: the speaker with three waves lit up to the level, and two echoes of the last one bouncing off to the right
internal fun DrawScope.drawLoudSpeaker(
    body: Path,
    stroke: Stroke,
    x: Float,
    cy: Float,
    h: Float,
    grow: Float,
    alpha: Float,
    level: Float,
    echo: Float,
) {
    val width = LoudSpeakerWidth * h
    // a quick pop as each echo leaves
    val pop = 1f + 0.12f * sin(PI.toFloat() * (echo / 0.35f).coerceIn(0f, 1f))
    translate(x, cy - h / 2f) {
        scale(grow * pop, grow * pop, Offset(width / 2f, h / 2f)) {
            drawPath(body, Color.White, alpha = alpha)
            val centre = Offset(WaveCentreX * h, h / 2f)
            var outer = 0
            for (i in WaveRadii.indices) {
                val lit = (level.coerceIn(0f, 1f) * WaveRadii.size - i).coerceIn(0f, 1f)
                if (lit > 0f) outer = i
                wave(centre, WaveRadii[i] * h, stroke, alpha * (WaveDim + (1f - WaveDim) * lit))
            }
            // the outermost lit wave, twice over, a little apart: each slides right and fades out
            if (echo < 1f) {
                for (k in 0..1) {
                    val t = ((echo - 0.2f * k) / 0.8f).coerceIn(0f, 1f)
                    if (t <= 0f || t >= 1f) continue
                    val shift = EchoTravel * h * smoothstepF(t)
                    wave(centre + Offset(shift, 0f), WaveRadii[outer] * h, stroke, alpha * 0.85f * (1f - t))
                }
            }
        }
    }
}

private fun DrawScope.wave(centre: Offset, radius: Float, stroke: Stroke, alpha: Float) {
    if (alpha <= 0.002f) return
    drawArc(
        color = Color.White,
        startAngle = -WaveHalfAngle,
        sweepAngle = WaveHalfAngle * 2f,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = stroke,
        alpha = alpha,
    )
}

// the wave's stroke at h tall, made once per size
internal fun waveStroke(h: Float): Stroke = Stroke(width = LineWidth * h, cap = StrokeCap.Round)

// a closed shape through points, each corner rounded off by radius (less where the sides are short)
private fun roundedPolygon(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    for (i in 0 until n) {
        val previous = points[(i - 1 + n) % n]
        val corner = points[i]
        val next = points[(i + 1) % n]
        val into = corner - previous
        val out = next - corner
        val r = minOf(radius, into.getDistance() / 2f, out.getDistance() / 2f)
        val a = corner - into / into.getDistance() * r
        val b = corner + out / out.getDistance() * r
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(corner.x, corner.y, b.x, b.y)
    }
    path.close()
    return path
}
