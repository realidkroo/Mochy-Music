// the module player's one clock: loop time and the music's levels worked out once a frame, plus the loops and entrances blocks put on with it

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.modules.skin.EnterKind
import com.mochyy.mochymusic.modules.skin.MotionKind
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinEnter
import com.mochyy.mochymusic.modules.skin.SkinMotion
import com.mochyy.mochymusic.playback.audio.AudioSpectrum
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** read these in draw or graphicsLayer only: they change every frame while the song plays */
@Stable
internal class SkinPulse {
    /** seconds of loop time, only moving while the song plays and the player can be seen */
    var time by mutableFloatStateOf(0f)
        internal set

    /** 0..1, eased: the low end, the middle, the top and all of it */
    var bass by mutableFloatStateOf(0f)
        internal set
    var mid by mutableFloatStateOf(0f)
        internal set
    var treble by mutableFloatStateOf(0f)
        internal set
    var level by mutableFloatStateOf(0f)
        internal set

    /** bumps on every frame the bands moved; a visualizer reads it to redraw */
    var frame by mutableLongStateOf(0L)
        internal set

    internal val bars = BarLevels()

    /** the bands low to high, eased, [AudioSpectrum.Bands] of them */
    val bands: FloatArray get() = bars.shown

    internal fun settle() {
        bars.clear()
        bass = 0f
        mid = 0f
        treble = 0f
        level = 0f
        frame++
    }
}

/** one clock for the whole player; it only runs when the layout has something that moves, and rests under the mini player */
@Composable
internal fun rememberSkinPulse(needed: Boolean, preview: Boolean): SkinPulse {
    val pulse = remember { SkinPulse() }
    val connection = LocalPlayerConnection.current
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    LaunchedEffect(needed, preview, connection, playing, showing) {
        if (!needed || preview || connection == null) {
            pulse.settle()
            return@LaunchedEffect
        }
        snapshotFlow { showing() && !obscured.value }.distinctUntilChanged().collectLatest { shown ->
            if (!shown) {
                pulse.settle()
                return@collectLatest
            }
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = now
                if (playing) pulse.time += dt
                val spectrum = connection.service.spectrumOf(connection.player)
                val heard = playing && spectrum != null &&
                    spectrum.read(connection.player.currentPosition, pulse.bars.target(AudioSpectrum.Bands))
                if (!heard) pulse.bars.silence()
                pulse.bars.ease(dt)
                val shown = pulse.bars.shown
                pulse.bass = average(shown, 0, 8)
                pulse.mid = average(shown, 8, 26)
                pulse.treble = average(shown, 26, shown.size)
                pulse.level = average(shown, 0, shown.size)
                pulse.frame = now
                // paused and settled: nothing left to move until play is pressed
                if (!playing && pulse.bars.settled()) break
            }
        }
    }
    return pulse
}

private fun average(values: FloatArray, from: Int, to: Int): Float {
    val end = minOf(to, values.size)
    if (end <= from) return 0f
    var sum = 0f
    for (i in from until end) sum += values[i]
    return sum / (end - from)
}

/** how far a block's entrance has come, 0 to 1; it plays again each time the player opens, or with each song when it asks */
@Composable
internal fun rememberEnterProgress(enter: SkinEnter?, preview: Boolean): () -> Float {
    if (enter == null || preview) return { 1f }
    val progress = remember { Animatable(0f) }
    val connection = LocalPlayerConnection.current
    val showing = rememberStyleShowing()
    LaunchedEffect(enter, connection) {
        val song = if (enter.eachSong && connection != null) connection.mediaMetadata.map { it?.id } else kotlinx.coroutines.flow.flowOf(null)
        combine(snapshotFlow { showing() }.distinctUntilChanged(), song.distinctUntilChanged()) { shown, id -> shown to id }
            .collectLatest { (shown, _) ->
                progress.snapTo(0f)
                if (!shown) return@collectLatest
                delay(enter.delayMs.toLong())
                progress.animateTo(1f, tween(enter.durationMs, easing = FastOutSlowInEasing))
            }
    }
    return remember(progress) { { progress.value } }
}

/** a styled block's loop, entrance, nudge, turn and see-through, all in one layer so they never recompose it */
internal fun Modifier.skinStyled(block: SkinBlock.Styled, pulse: SkinPulse, enter: () -> Float): Modifier = graphicsLayer {
    translationX = block.x.toPx()
    translationY = block.y.toPx()
    rotationZ = block.rotate
    alpha = block.opacity
    block.motion?.let { applyMotion(it, pulse) }
    block.enter?.let { applyEnter(it.kind, enter()) }
}

/** a background layer's loop and see-through */
internal fun Modifier.skinLayerMotion(motion: SkinMotion?, opacity: Float, pulse: SkinPulse): Modifier = graphicsLayer {
    alpha = opacity
    motion?.let { applyMotion(it, pulse) }
}

private fun GraphicsLayerScope.applyMotion(motion: SkinMotion, pulse: SkinPulse) {
    val t = pulse.time * motion.speed
    val a = motion.amount
    when (motion.kind) {
        MotionKind.Float -> translationY += sin(t * Tau / 3f) * 6.dp.toPx() * a
        MotionKind.Sway -> rotationZ += sin(t * Tau / 4f) * 4f * a
        MotionKind.Breathe -> (1f + 0.035f * a * sin(t * Tau / 4f)).let { scaleX *= it; scaleY *= it }
        MotionKind.Pulse -> alpha *= 1f - 0.35f * a * (0.5f + 0.5f * sin(t * Tau / 2f))
        MotionKind.Spin -> rotationZ += (t * 45f) % 360f
        MotionKind.Beat -> (1f + 0.08f * a * pulse.bass).let { scaleX *= it; scaleY *= it }
        MotionKind.Shake -> translationX += sin(t * Tau * 9f) * 2.dp.toPx() * a * pulse.bass
        MotionKind.Drift -> {
            // a little bigger than the screen, so the slow pan never shows an edge
            translationX += sin(t * Tau / 14f) * 12.dp.toPx() * a
            translationY += cos(t * Tau / 18f) * 10.dp.toPx() * a
            scaleX *= 1.08f + 0.04f * a
            scaleY *= 1.08f + 0.04f * a
        }
        MotionKind.Zoom -> (1.06f + 0.06f * a * (0.5f + 0.5f * sin(t * Tau / 16f))).let { scaleX *= it; scaleY *= it }
    }
}

private fun GraphicsLayerScope.applyEnter(kind: EnterKind, progress: Float) {
    if (progress >= 1f) return
    val rest = 1f - progress
    alpha *= progress
    when (kind) {
        EnterKind.Fade -> Unit
        EnterKind.Rise -> translationY += rest * 24.dp.toPx()
        EnterKind.Drop -> translationY -= rest * 24.dp.toPx()
        EnterKind.Zoom -> (0.85f + 0.15f * progress).let { scaleX *= it; scaleY *= it }
        EnterKind.SlideStart -> translationX -= rest * 40.dp.toPx()
        EnterKind.SlideEnd -> translationX += rest * 40.dp.toPx()
    }
}

private const val Tau = (2 * PI).toFloat()
