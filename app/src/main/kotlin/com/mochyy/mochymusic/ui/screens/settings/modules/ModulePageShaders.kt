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
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.SkinProgram
import com.mochyy.mochymusic.modules.skin.SkinShaderSource
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
    private val compiled = ConcurrentHashMap<String, SkinProgram>()

    // a shader the file hasn't changed since compiling comes straight back, so reopening a page costs nothing
    suspend fun load(file: File): PageProgram = withContext(Dispatchers.Default) {
        if (!file.isFile) return@withContext PageProgram.Broken("${file.name} isn't in the module")
        if (file.length() > MaxShaderBytes) return@withContext PageProgram.Broken("${file.name} is bigger than ${MaxShaderBytes / 1024} KB")
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        compiled[key]?.let { return@withContext PageProgram.Ready(it) }
        try {
            val source = file.readText()
            val program = SkinProgram(createAgslShader(source), ActiveSkin.DeclaredUniform.findAll(source).map { it.groupValues[1] }.toSet())
            compiled[key] = program
            PageProgram.Ready(program)
        } catch (e: Exception) {
            PageProgram.Broken("${file.name}: " + (e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName))
        }
    }

    private const val MaxShaderBytes = 64 * 1024
}

/** the compiled shader for [source], null while it compiles */
@Composable
internal fun rememberPageProgram(skin: ModuleSkin, source: SkinShaderSource): PageProgram? {
    val state by produceState<PageProgram?>(null, skin, source.file) {
        value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PageProgram.Unsupported
        } else {
            skin.fontFile(source.file)?.let { PageShaderCache.load(it) } ?: PageProgram.Broken("${source.file} isn't in the module")
        }
    }
    return state
}

/** the shader filling [modifier]'s box with its clock, size, density, the accent and the last touch on it */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun PageShaderFill(program: SkinProgram, source: SkinShaderSource, modifier: Modifier, touchable: Boolean) {
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

// seconds the page has been showing; it only ticks while a shader reads it and nothing covers the page
@Composable
private fun rememberPageClock(running: Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    val obscured = LocalAppContentObscured.current
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
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
    return remember { { time.floatValue } }
}
