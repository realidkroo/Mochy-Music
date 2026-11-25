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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.modules.skin.ActiveBar
import com.mochyy.mochymusic.modules.skin.SkinProgram
import com.mochyy.mochymusic.modules.skin.SkinShaderSource
import com.mochyy.mochymusic.modules.skin.SkinTimeline
import com.mochyy.mochymusic.ui.player.styles.PoseSum
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// the module's shader filling the bar over the glass, native agsl on android 13+ or gles 3.0 fallback on older android
@Composable
internal fun BarShader(active: ActiveBar, pill: NavPillState, tabCount: Int, accent: Color, pureBlack: Boolean, obscured: () -> Boolean) {
    val program = active.program ?: return
    val source = active.bar.shader ?: return
    val chrome = if (pureBlack) Color.Black else Color(0xFF131313)
    if (program.isNative && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        NativeBarShader(program, source, pill, tabCount, accent, chrome, obscured)
        return
    }
    val transpiled = program.shader as? TranspiledAgsl ?: return
    var failed by remember(program) { mutableStateOf(false) }
    if (!failed) {
        FallbackBarShader(transpiled, program.declared, source, pill, tabCount, accent, chrome, obscured, onFailed = { failed = true })
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun NativeBarShader(
    program: SkinProgram,
    source: SkinShaderSource,
    pill: NavPillState,
    tabCount: Int,
    accent: Color,
    chrome: Color,
    obscured: () -> Boolean,
) {
    val brush = remember(program) { ShaderBrush(program.shader.asAgslPaintShader()) }
    val time = rememberBarTime(running = "time" in program.declared, obscured = obscured)
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

@Composable
private fun FallbackBarShader(
    transpiled: TranspiledAgsl,
    declared: Set<String>,
    source: SkinShaderSource,
    pill: NavPillState,
    tabCount: Int,
    accent: Color,
    chrome: Color,
    obscured: () -> Boolean,
    onFailed: () -> Unit,
) {
    val time = rememberBarTime(running = "time" in declared, obscured = obscured)
    val realDensity = LocalDensity.current
    val navPadPx = with(realDensity) { NavRowPadding.toPx() }
    val accentArgb = accent.toArgb()
    val chromeArgb = chrome.toArgb()
    FallbackGlShaderBox(
        transpiled = transpiled,
        modifier = Modifier.fillMaxSize(),
        onFailed = { onFailed() },
    ) { out, scaledSize, _, scale ->
        var sig = 1125899906842597L
        if ("time" in declared) {
            out.time = time()
            sig = sig * 31L + out.time.toRawBits()
        }
        if ("tabs" in declared) {
            out.tabs = tabCount.toFloat()
            sig = sig * 31L + tabCount
        }
        if ("selected" in declared) {
            out.selected = (pill.start.value + pill.end.value) / 2f
            sig = sig * 31L + out.selected.toRawBits()
        }
        if ("pillShown" in declared) {
            out.pillShown = pill.shown.value
            sig = sig * 31L + out.pillShown.toRawBits()
        }
        if ("pillLift" in declared || "pressed" in declared) {
            val lift = pill.lift.value
            out.pillLift = lift
            out.pressed = lift
            sig = sig * 31L + lift.toRawBits()
        }
        if ("pill" in declared) {
            val unscaled = if (scale > 0f) Size(scaledSize.width / scale, scaledSize.height / scale) else scaledSize
            val rect = pill.forGlass(unscaled, navPadPx, tabCount, realDensity)?.rect
            if (rect != null) {
                out.pillLeft = rect.left * scale
                out.pillTop = rect.top * scale
                out.pillRight = rect.right * scale
                out.pillBottom = rect.bottom * scale
            } else {
                out.pillLeft = 0f; out.pillTop = 0f; out.pillRight = 0f; out.pillBottom = 0f
            }
            sig = sig * 31L + out.pillLeft.toRawBits()
            sig = sig * 31L + out.pillTop.toRawBits()
            sig = sig * 31L + out.pillRight.toRawBits()
            sig = sig * 31L + out.pillBottom.toRawBits()
        }
        if ("accent" in declared) {
            out.accentArgb = accentArgb
            sig = sig * 31L + accentArgb
        }
        if ("chrome" in declared) {
            out.chromeArgb = chromeArgb
            sig = sig * 31L + chromeArgb
        }
        out.custom = source.uniforms
        if (source.uniforms.isNotEmpty()) {
            source.uniforms.forEach { (k, v) ->
                if (k in declared) sig = sig * 31L + v.toRawBits()
            }
        }
        sig
    }
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val obscured = com.mochyy.mochymusic.LocalAppContentObscured.current
    LaunchedEffect(timeline, running, lifecycle) {
        if (timeline == null || !running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { obscured.value }.collectLatest { covered ->
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
    return remember(timeline) { Loop(timeline) { time.floatValue } }
}
