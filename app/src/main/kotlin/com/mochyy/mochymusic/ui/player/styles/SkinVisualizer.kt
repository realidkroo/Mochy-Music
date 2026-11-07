// a module player's visualizer: bars, a mirror, a wave, dots or a ring, drawn from the shared clock's bands so they follow what's heard

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.VisualizerStyle
import com.mochyy.mochymusic.ui.component.damageBoundary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun SkinVisualizer(block: SkinBlock.Visualizer, context: SkinContext, modifier: Modifier) {
    val color = context.color(block.color)
    // the bands spread over this visualizer's own number of bars, reused every frame
    val levels = remember(block.count) { FloatArray(block.count) }
    val path = remember { Path() }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(block.height)
            // it moves every frame the music does, so only its own rectangle repaints; nothing draws outside it
            .damageBoundary()
            .drawBehind {
                context.pulse.frame
                if (context.preview) previewLevels(levels) else spread(context.pulse.bands, levels)
                when (block.style) {
                    VisualizerStyle.Bars -> drawBars(levels, color, block.fill, block.round, mirrored = false)
                    VisualizerStyle.Mirror -> drawBars(levels, color, block.fill, block.round, mirrored = true)
                    VisualizerStyle.Wave -> drawWave(levels, color, path)
                    VisualizerStyle.Dots -> drawDots(levels, color, block.fill)
                    VisualizerStyle.Ring -> drawRing(levels, color, block.fill)
                }
            },
    )
}

// each bar the average of the bands under it, so 12 bars still show the whole range
private fun spread(bands: FloatArray, out: FloatArray) {
    val n = out.size
    for (i in 0 until n) {
        val from = i * bands.size / n
        val to = ((i + 1) * bands.size / n).coerceAtLeast(from + 1)
        var sum = 0f
        for (b in from until to) sum += bands[b]
        out[i] = sum / (to - from)
    }
}

// the style picker's card is still, so it gets a gentle made-up shape instead of flat bars
private fun previewLevels(out: FloatArray) {
    for (i in out.indices) out[i] = 0.3f + 0.25f * sin(i * 0.9f) * sin(i * 0.37f + 1f)
}

private fun DrawScope.drawBars(levels: FloatArray, color: Color, fill: Float, round: Boolean, mirrored: Boolean) {
    val slot = size.width / levels.size
    val width = (slot * fill).coerceAtLeast(1.dp.toPx())
    val corner = if (round) CornerRadius(width / 2f) else CornerRadius.Zero
    for (i in levels.indices) {
        val x = i * slot + (slot - width) / 2f
        val height = (levels[i] * size.height).coerceAtLeast(if (round) width else 1.dp.toPx())
        val top = if (mirrored) (size.height - height) / 2f else size.height - height
        drawRoundRect(color, Offset(x, top), Size(width, height), corner)
    }
}

// a smooth line through the levels, with a soft fill under it
private fun DrawScope.drawWave(levels: FloatArray, color: Color, path: Path) {
    val stroke = 2.5.dp.toPx()
    val usable = size.height - stroke
    fun y(i: Int) = size.height - stroke / 2f - levels[i] * usable
    val step = size.width / (levels.size - 1).coerceAtLeast(1)
    path.reset()
    path.moveTo(0f, y(0))
    for (i in 1 until levels.size) {
        val x0 = (i - 1) * step
        val x1 = i * step
        val mid = (x0 + x1) / 2f
        path.cubicTo(mid, y(i - 1), mid, y(i), x1, y(i))
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color.copy(alpha = color.alpha * 0.18f))
}

// one dot per band, riding up its column as it gets louder
private fun DrawScope.drawDots(levels: FloatArray, color: Color, fill: Float) {
    val slot = size.width / levels.size
    val radius = min(slot * fill, size.height) / 2f
    for (i in levels.indices) {
        val y = size.height - radius - levels[i] * (size.height - radius * 2f)
        drawCircle(color, radius, Offset(i * slot + slot / 2f, y))
    }
}

// bars standing out from a circle, low notes from the top going round; put it in a stack over a round cover
private fun DrawScope.drawRing(levels: FloatArray, color: Color, fill: Float) {
    val outer = min(size.width, size.height) / 2f
    val inner = outer * 0.62f
    val reach = outer - inner
    val center = Offset(size.width / 2f, size.height / 2f)
    val gap = (2 * PI * inner / levels.size).toFloat()
    val stroke = (gap * fill).coerceIn(1.dp.toPx(), 12.dp.toPx())
    for (i in levels.indices) {
        val angle = (i.toFloat() / levels.size * 2 * PI - PI / 2).toFloat()
        val direction = Offset(cos(angle), sin(angle))
        val length = (levels[i] * reach).coerceAtLeast(stroke)
        drawLine(color, center + direction * inner, center + direction * (inner + length), stroke, StrokeCap.Round)
    }
}
