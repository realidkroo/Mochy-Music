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
import androidx.compose.runtime.remember
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
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.setAgslColorUniform
import com.mochyy.mochymusic.ui.component.setAgslUniform

/** the shader filling [modifier]'s box, or [fallback] where shaders can't run or this one didn't compile */
@Composable
internal fun SkinShaderSurface(source: SkinShaderSource, context: SkinContext, modifier: Modifier, fallback: @Composable () -> Unit) {
    val program = ActiveSkin.shaders[source.file]
    if (program == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        fallback()
        return
    }
    ShaderFill(program, source, context, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderFill(program: SkinProgram, source: SkinShaderSource, context: SkinContext, modifier: Modifier) {
    // one brush per compiled shader; the uniforms change under it every frame, the brush itself never does
    val brush = remember(program) { ShaderBrush(program.shader.asAgslPaintShader()) }
    val connection = LocalPlayerConnection.current
    Spacer(
        modifier
            // it redraws every frame the music moves, so it's kept to its own rectangle
            .damageBoundary()
            .drawBehind {
                setUniforms(program, source, context, playing = connection?.isPlaying?.value == true)
                drawRect(brush)
            },
    )
}

// mochy's uniforms first, then the module's own; only names the source declares, so a frame never throws for one it left out
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.setUniforms(program: SkinProgram, source: SkinShaderSource, context: SkinContext, playing: Boolean) {
    val shader = program.shader
    val declared = program.declared
    val pulse = context.pulse
    // read so a new frame of the clock redraws this, and only this
    pulse.frame
    if ("size" in declared) shader.setAgslUniform("size", size.width, size.height)
    if ("time" in declared) shader.setAgslUniform("time", pulse.time)
    if ("progress" in declared) shader.setAgslUniform("progress", (context.position.positionMs().toFloat() / context.duration).coerceIn(0f, 1f))
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
    source.uniforms.forEach { (name, value) -> if (name in declared) shader.setAgslUniform(name, value) }
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
