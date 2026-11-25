// a module page's own shaders: compiled off the main thread once per file, drawn with a clock that rests while the page is hidden, and told where a finger is

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.modules.security.ShaderSecurityGuard
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.SkinProgram
import com.mochyy.mochymusic.modules.skin.SkinShaderSource
import com.mochyy.mochymusic.ui.component.AgslTranspiler
import com.mochyy.mochymusic.ui.component.FallbackGlShaderBox
import com.mochyy.mochymusic.ui.component.GlFallbackEngine
import com.mochyy.mochymusic.ui.component.TranspiledAgsl
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.setAgslColorUniform
import com.mochyy.mochymusic.ui.component.setAgslUniform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** what loading a page's shader came to: the compiled program, or the compiler's own words for the module's page */
internal sealed interface PageProgram {
    data class Ready(val program: SkinProgram) : PageProgram
    data class Broken(val why: String) : PageProgram
    /** this phone has no runtime shaders, the page shows its fallback and that's no fault of the module */
    data object Unsupported : PageProgram
}

private object PageShaderCache {
    private val compiled = android.util.LruCache<String, SkinProgram>(MaxCachedShaders)

    // a shader the file hasn't changed since compiling comes straight back, so reopening a page costs nothing
    suspend fun load(file: File): PageProgram = withContext(Dispatchers.Default) {
        if (!file.isFile) return@withContext PageProgram.Broken("${file.name} isn't in the module")
        if (file.length() > MaxShaderBytes) return@withContext PageProgram.Broken("${file.name} is bigger than ${MaxShaderBytes / 1024} KB")
        val native = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val key = "${if (native) "agsl" else "gles"}:${file.path}:${file.lastModified()}:${file.length()}"
        compiled.get(key)?.let { return@withContext PageProgram.Ready(it) }
        try {
            val source = file.readText()
            ShaderSecurityGuard.validate(source)?.let { rejected ->
                return@withContext PageProgram.Broken("${file.name}: $rejected")
            }
            val program = if (native) {
                SkinProgram(
                    shader = createAgslShader(source),
                    declared = ActiveSkin.DeclaredUniform.findAll(source).map { it.groupValues[1] }.toSet(),
                    isNative = true,
                )
            } else {
                val transpiled = AgslTranspiler.transpile(source)
                    ?: return@withContext PageProgram.Broken("${file.name}: couldn't convert shader for this Android version")
                if (transpiled.shaderInputs.isNotEmpty()) {
                    return@withContext PageProgram.Broken("${file.name}: nested shader inputs need Android 13")
                }
                GlFallbackEngine.compileBlocking(transpiled.glsl)?.let { err ->
                    return@withContext PageProgram.Broken("${file.name}: $err")
                }
                SkinProgram(
                    shader = transpiled,
                    declared = transpiled.declaredUniforms,
                    isNative = false,
                )
            }
            compiled.put(key, program)
            PageProgram.Ready(program)
        } catch (e: Exception) {
            PageProgram.Broken("${file.name}: " + (e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName))
        }
    }

    private const val MaxShaderBytes = 64 * 1024
    private const val MaxCachedShaders = 32
}

// the compiled shader for source, null while it compiles; native on android 13+ and transpiled gles 3.0 below
@Composable
internal fun rememberPageProgram(skin: ModuleSkin, source: SkinShaderSource): PageProgram? {
    val state by produceState<PageProgram?>(null, skin, source.file) {
        value = skin.fontFile(source.file)?.let { PageShaderCache.load(it) } ?: PageProgram.Broken("${source.file} isn't in the module")
    }
    return state
}

// the shader filling modifier's box with its clock, size, density, the accent and the last touch on it
@Composable
internal fun PageShaderFill(
    program: SkinProgram,
    source: SkinShaderSource,
    modifier: Modifier,
    touchable: Boolean,
    onFailed: (() -> Unit)? = null,
) {
    if (program.isNative && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        NativePageShaderFill(program, source, modifier, touchable)
        return
    }
    val transpiled = program.shader as? TranspiledAgsl
    if (transpiled != null) {
        FallbackPageShaderFill(transpiled, program.declared, source, modifier, touchable, onFailed)
    } else {
        onFailed?.invoke()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun NativePageShaderFill(program: SkinProgram, source: SkinShaderSource, modifier: Modifier, touchable: Boolean) {
    // one brush per compiled shader; the uniforms change under it every frame, the brush itself never does
    val brush = remember(program) { ShaderBrush(program.shader.asAgslPaintShader()) }
    val time = rememberPageClock(running = "time" in program.declared)
    var touch by remember { mutableStateOf(Offset(-1f, -1f)) }
    var pressed by remember { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    Spacer(
        modifier
            // it can redraw every frame, so only its own rectangle repaints
            .damageBoundary()
            .then(
                if (!touchable) Modifier else Modifier.pointerInput(Unit) {
                    // the finger is only watched, never taken, so the page still scrolls under it
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touch = down.position
                        pressed = 1f
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { touch = it.position }
                        } while (event.changes.any { it.pressed })
                        pressed = 0f
                    }
                },
            )
            .drawBehind {
                val shader = program.shader
                val declared = program.declared
                if ("size" in declared) shader.setAgslUniform("size", size.width, size.height)
                if ("time" in declared) shader.setAgslUniform("time", time())
                if ("density" in declared) shader.setAgslUniform("density", density)
                if ("touch" in declared) shader.setAgslUniform("touch", touch.x, touch.y)
                if ("pressed" in declared) shader.setAgslUniform("pressed", pressed)
                if ("accent" in declared) shader.setAgslColorUniform("accent", accent)
                source.uniforms.forEach { (name, value) -> if (name in declared) shader.setAgslUniform(name, value) }
                drawRect(brush)
            },
    )
}

@Composable
private fun FallbackPageShaderFill(
    transpiled: TranspiledAgsl,
    declared: Set<String>,
    source: SkinShaderSource,
    modifier: Modifier,
    touchable: Boolean,
    onFailed: (() -> Unit)?,
) {
    val time = rememberPageClock(running = "time" in declared)
    var touch by remember { mutableStateOf(Offset(-1f, -1f)) }
    var pressed by remember { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val tracked = if (!touchable) modifier else modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            touch = down.position
            pressed = 1f
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { touch = it.position }
            } while (event.changes.any { it.pressed })
            pressed = 0f
        }
    }
    FallbackGlShaderBox(
        transpiled = transpiled,
        modifier = tracked,
        onFailed = { onFailed?.invoke() },
    ) { out, _, _, scale ->
        var sig = 1125899906842597L
        if ("time" in declared) {
            out.time = time()
            sig = sig * 31L + out.time.toRawBits()
        }
        if ("touch" in declared) {
            out.touchX = if (touch.x >= 0f) touch.x * scale else -1f
            out.touchY = if (touch.y >= 0f) touch.y * scale else -1f
            sig = sig * 31L + out.touchX.toRawBits()
            sig = sig * 31L + out.touchY.toRawBits()
        }
        if ("pressed" in declared) {
            out.pressed = pressed
            sig = sig * 31L + pressed.toRawBits()
        }
        if ("accent" in declared) {
            out.accentArgb = accent
            sig = sig * 31L + accent
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

// seconds the page has been showing; it only ticks while resumed, a shader reads it, and nothing covers the page
@Composable
private fun rememberPageClock(running: Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { obscured.value }.distinctUntilChanged().collectLatest { hidden ->
                if (hidden) return@collectLatest
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) time.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                        last = now
                    }
                }
            }
        }
    }
    return remember { { time.floatValue } }
}
