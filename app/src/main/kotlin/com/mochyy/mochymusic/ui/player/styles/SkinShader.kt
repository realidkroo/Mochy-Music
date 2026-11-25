// a module's own agsl drawn as a fill: its uniforms set at draw time from the shared clock, so the shader moves without recomposing anything

package com.mochyy.mochymusic.ui.player.styles

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinProgram
import com.mochyy.mochymusic.modules.skin.SkinShaderSource
import com.mochyy.mochymusic.ui.component.FallbackGlShaderBox
import com.mochyy.mochymusic.ui.component.TranspiledAgsl
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.setAgslColorUniform
import com.mochyy.mochymusic.ui.component.setAgslUniform

// the shader filling modifier's box, native agsl on android 13+ or gles 3.0 fallback on android 7-12
@Composable
internal fun SkinShaderSurface(source: SkinShaderSource, context: SkinContext, modifier: Modifier, fallback: @Composable () -> Unit) {
    val program = ActiveSkin.shader(context.skin.moduleId, source.file)
    if (program == null) {
        fallback()
        return
    }
    if (program.isNative && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderFill(program, source, context, modifier)
        return
    }
    val transpiled = program.shader as? TranspiledAgsl
    if (transpiled != null) {
        var failed by remember(program) { mutableStateOf(false) }
        if (!failed) {
            FallbackShaderFill(program, transpiled, source, context, modifier, onFailed = { failed = true })
            return
        }
    }
    fallback()
}

@Composable
private fun FallbackShaderFill(
    program: SkinProgram,
    transpiled: TranspiledAgsl,
    source: SkinShaderSource,
    context: SkinContext,
    modifier: Modifier,
    onFailed: () -> Unit,
) {
    val connection = LocalPlayerConnection.current
    val declared = program.declared
    val needsClock = remember(declared) {
        declared.any { it in DynamicPlayerUniforms }
    }
    FallbackGlShaderBox(
        transpiled = transpiled,
        modifier = modifier,
        onFailed = { onFailed() },
    ) { out, _, _, _ ->
        val pulse = context.pulse
        if (needsClock) pulse.frame
        var sig = 1125899906842597L
        if ("time" in declared) {
            out.time = pulse.time
            sig = sig * 31L + out.time.toRawBits()
        }
        if ("progress" in declared) {
            out.progress = (context.position.positionMs().toFloat() / context.duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
            sig = sig * 31L + out.progress.toRawBits()
        }
        if ("level" in declared) {
            out.level = pulse.level
            sig = sig * 31L + out.level.toRawBits()
        }
        if ("bass" in declared) {
            out.bass = pulse.bass
            sig = sig * 31L + out.bass.toRawBits()
        }
        if ("mid" in declared) {
            out.mid = pulse.mid
            sig = sig * 31L + out.mid.toRawBits()
        }
        if ("treble" in declared) {
            out.treble = pulse.treble
            sig = sig * 31L + out.treble.toRawBits()
        }
        if ("playing" in declared) {
            out.playing = if (connection?.isPlaying?.value == true) 1f else 0f
            sig = sig * 31L + out.playing.toRawBits()
        }
        if ("played" in declared) {
            out.playedArgb = context.tones.played.toArgb()
            sig = sig * 31L + out.playedArgb
        }
        if ("remaining" in declared) {
            out.remainingArgb = context.tones.remaining.toArgb()
            sig = sig * 31L + out.remainingArgb
        }
        if ("cover" in declared) {
            out.coverArgb = context.cover.toArgb()
            sig = sig * 31L + out.coverArgb
        }
        if ("accent" in declared) {
            out.accentArgb = context.accent.toArgb()
            sig = sig * 31L + out.accentArgb
        }
        val clock = context.lyricClock
        if ("line" in declared) {
            out.line = clock.line().toFloat()
            sig = sig * 31L + out.line.toRawBits()
        }
        if ("lineCount" in declared) {
            out.lineCount = clock.lines.size.toFloat()
            sig = sig * 31L + out.lineCount.toRawBits()
        }
        if ("lineProgress" in declared) {
            out.lineProgress = clock.lineProgress()
            sig = sig * 31L + out.lineProgress.toRawBits()
        }
        if ("wordProgress" in declared) {
            out.wordProgress = clock.wordProgress()
            sig = sig * 31L + out.wordProgress.toRawBits()
        }
        if ("singing" in declared) {
            out.singing = if (clock.singing()) 1f else 0f
            sig = sig * 31L + out.singing.toRawBits()
        }
        if ("position" in declared) {
            out.position = context.position.positionMs() / 1000f
            sig = sig * 31L + out.position.toRawBits()
        }
        if ("duration" in declared) {
            out.duration = context.duration / 1000f
            sig = sig * 31L + out.duration.toRawBits()
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

private val DynamicPlayerUniforms = setOf(
    "time", "progress", "level", "bass", "mid", "treble",
    "line", "lineProgress", "wordProgress", "singing", "position",
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderFill(program: SkinProgram, source: SkinShaderSource, context: SkinContext, modifier: Modifier) {
    // one brush per compiled shader; the uniforms change under it every frame, the brush itself never does
    val brush = remember(program) { ShaderBrush(program.shader.asAgslPaintShader()) }
    val needsClock = remember(program.declared) { program.declared.any { it in DynamicPlayerUniforms } }
    val connection = LocalPlayerConnection.current
    Spacer(
        modifier
            // it redraws every frame the music moves, so it's kept to its own rectangle
            .damageBoundary()
            .drawBehind {
                setUniforms(program, source, context, playing = connection?.isPlaying?.value == true, needsClock = needsClock)
                drawRect(brush)
            },
    )
}

// mochy's uniforms first, then the module's own; only names the source declares, so a frame never throws for one it left out
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.setUniforms(
    program: SkinProgram,
    source: SkinShaderSource,
    context: SkinContext,
    playing: Boolean,
    needsClock: Boolean,
) {
    val shader = program.shader
    val declared = program.declared
    val pulse = context.pulse
    // subscribe to the frame clock only when this shader actually uses a time/audio/lyric-varying uniform
    if (needsClock) pulse.frame
    if ("size" in declared) shader.setAgslUniform("size", size.width, size.height)
    if ("time" in declared) shader.setAgslUniform("time", pulse.time)
    if ("progress" in declared) shader.setAgslUniform("progress", (context.position.positionMs().toFloat() / context.duration.coerceAtLeast(1L)).coerceIn(0f, 1f))
    if ("level" in declared) shader.setAgslUniform("level", pulse.level)
    if ("bass" in declared) shader.setAgslUniform("bass", pulse.bass)
    if ("mid" in declared) shader.setAgslUniform("mid", pulse.mid)
    if ("treble" in declared) shader.setAgslUniform("treble", pulse.treble)
    if ("playing" in declared) shader.setAgslUniform("playing", if (playing) 1f else 0f)
    if ("density" in declared) shader.setAgslUniform("density", density)
    if ("played" in declared) shader.setAgslColorUniform("played", context.tones.played.toArgb())
    if ("remaining" in declared) shader.setAgslColorUniform("remaining", context.tones.remaining.toArgb())
    if ("cover" in declared) shader.setAgslColorUniform("cover", context.cover.toArgb())
    if ("accent" in declared) shader.setAgslColorUniform("accent", context.accent.toArgb())
    setLyricUniforms(shader, declared, context)
    source.uniforms.forEach { (name, value) -> if (name in declared) shader.setAgslUniform(name, value) }
}

/** the lyrics, the position and the length, for any player shader that declares them; read while drawing, so they move without recomposing */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun setLyricUniforms(shader: Any, declared: Set<String>, context: SkinContext) {
    val clock = context.lyricClock
    if ("line" in declared) shader.setAgslUniform("line", clock.line().toFloat())
    if ("lineCount" in declared) shader.setAgslUniform("lineCount", clock.lines.size.toFloat())
    if ("lineProgress" in declared) shader.setAgslUniform("lineProgress", clock.lineProgress())
    if ("wordProgress" in declared) shader.setAgslUniform("wordProgress", clock.wordProgress())
    if ("singing" in declared) shader.setAgslUniform("singing", if (clock.singing()) 1f else 0f)
    if ("position" in declared) shader.setAgslUniform("position", context.position.positionMs() / 1000f)
    if ("duration" in declared) shader.setAgslUniform("duration", context.duration / 1000f)
}

/** a shader in a box among the blocks, its fallback colour where it can't run */
@Composable
internal fun SkinShaderBlock(block: SkinBlock.Shader, context: SkinContext, modifier: Modifier) {
    val box = modifier
        .then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
        .height(block.height)
        .clip(RoundedCornerShape(block.corner))
    SkinShaderSurface(block.program, context, box) {
        block.fallback?.let { Box(box.background(context.color(it))) }
    }
}
