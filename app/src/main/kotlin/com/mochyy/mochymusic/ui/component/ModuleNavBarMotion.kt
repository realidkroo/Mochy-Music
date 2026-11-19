// a look's nav bar in motion: its shader over the glass with a clock that rests when the bar can't be seen, and the keyframes its tabs play when pressed, lit and while lit

package com.mochyy.mochymusic.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.modules.skin.ActiveBar
import com.mochyy.mochymusic.modules.skin.SkinTimeline
import com.mochyy.mochymusic.ui.player.styles.PoseSum
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** the module's shader filling the bar over the glass, its uniforms set while drawing; its clock only runs when it asks for time and the bar can be seen */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun BarShader(active: ActiveBar, pill: NavPillState, tabCount: Int, accent: Color, pureBlack: Boolean, obscured: () -> Boolean) {
    val program = active.program ?: return
    val source = active.bar.shader ?: return
    val brush = remember(program) { ShaderBrush(program.shader.asAgslPaintShader()) }
    val time = rememberBarTime(running = "time" in program.declared, obscured = obscured)
    val chrome = if (pureBlack) Color.Black else Color(0xFF131313)
    Spacer(
        Modifier
            .fillMaxSize()
            // a bar that animates itself repaints only its own rectangle, never the app under it
            .damageBoundary()
            .drawBehind {
                val shader = program.shader
                val declared = program.declared
                if ("size" in declared) shader.setAgslUniform("size", size.width, size.height)
                if ("time" in declared) shader.setAgslUniform("time", time())
                if ("density" in declared) shader.setAgslUniform("density", density)
                if ("tabs" in declared) shader.setAgslUniform("tabs", tabCount.toFloat())
                if ("selected" in declared) shader.setAgslUniform("selected", (pill.start.value + pill.end.value) / 2f)
                if ("pillShown" in declared) shader.setAgslUniform("pillShown", pill.shown.value)
                if ("pillLift" in declared) shader.setAgslUniform("pillLift", pill.lift.value)
                if ("pressed" in declared) shader.setAgslUniform("pressed", pill.lift.value)
                if ("pill" in declared) {
                    val rect = pill.forGlass(size, NavRowPadding.toPx(), tabCount, this)?.rect
                    if (rect != null) shader.setAgslUniform("pill", rect.left, rect.top, rect.right, rect.bottom) else shader.setAgslUniform("pill", 0f, 0f, 0f, 0f)
                }
                if ("accent" in declared) shader.setAgslColorUniform("accent", accent.toArgb())
                if ("chrome" in declared) shader.setAgslColorUniform("chrome", chrome.toArgb())
                source.uniforms.forEach { (name, value) -> if (name in declared) shader.setAgslUniform(name, value) }
                drawRect(brush)
            },
    )
}

// seconds the bar has been showing with the app in front; it rests behind the player and in the background
@Composable
private fun rememberBarTime(running: Boolean, obscured: () -> Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hidden by rememberUpdatedState(obscured)
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { hidden() }.collectLatest { covered ->
                if (covered) return@collectLatest
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                        last = now
                    }
                }
            }
        }
    }
    return remember { { time.floatValue } }
}

/** keyframes a tab plays once: on a press with [play], or each time [trigger] turns non-null */
internal class OneShot(private val timeline: SkinTimeline?, private val elapsed: Animatable<Float, AnimationVector1D>, private val launch: (suspend () -> Unit) -> Unit) {
    fun play() {
        val t = timeline ?: return
        launch {
            val total = (t.delayMs + t.durationMs).toFloat()
            elapsed.snapTo(0f)
            elapsed.animateTo(total, tween(total.toInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }

    fun addTo(pose: PoseSum) {
        val t = timeline ?: return
        val at = elapsed.value
        if (at <= 0f || at >= t.delayMs + t.durationMs) return
        for (track in t.frames.tracks) pose.add(track.prop, track.valueAt(t.position(at), t.easing), 1f)
    }
}

@Composable
internal fun rememberOneShot(timeline: SkinTimeline?, trigger: Boolean?): OneShot {
    val elapsed = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shot = remember(timeline) { OneShot(timeline, elapsed) { block -> scope.launch { block() } } }
    // the select animation plays when the tab lights, never on the frame the bar first shows
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (trigger != null) shot.play()
    }
    return shot
}

/** a looping animation on the lit tab, its own clock running only while it's lit */
internal class Loop(private val timeline: SkinTimeline?, private val time: () -> Float) {
    fun addTo(pose: PoseSum) {
        val t = timeline ?: return
        val at = t.position(time() * 1000f)
        for (track in t.frames.tracks) pose.add(track.prop, track.valueAt(at, t.easing), 1f)
    }
}

@Composable
internal fun rememberLoop(timeline: SkinTimeline?, running: Boolean): Loop {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(timeline, running) {
        if (timeline == null || !running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return remember(timeline) { Loop(timeline) { time.floatValue } }
}
