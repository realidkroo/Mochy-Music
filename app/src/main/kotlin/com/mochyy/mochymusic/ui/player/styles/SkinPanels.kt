// a module player's panels and drawn shapes: boxes with a fill, a gradient or frosted glass behind their blocks, and shapes from svg path data

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.GradientKind
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import kotlin.math.min

/** a panel: frosted glass, a fill or a gradient behind its blocks, a border, round corners, its blocks in a column or a row */
@Composable
internal fun SkinBox(block: SkinBlock.Box, context: SkinContext, modifier: Modifier, inRow: Boolean) {
    val shape = RoundedCornerShape(block.corner)
    val colors = block.fill.map(context::color)
    val sized = modifier
        .then(block.width?.let { Modifier.width(it) } ?: if (inRow) Modifier else Modifier.fillMaxWidth())
        .then(block.height?.let { Modifier.height(it) } ?: Modifier)
        .then(if (block.shadow) Modifier.shadow(16.dp, shape) else Modifier)
        .clip(shape)
    val glass = context.glass.takeIf { block.glass != null }
    Box(sized) {
        if (glass != null) {
            val radius = with(LocalDensity.current) { (block.glass ?: 0f).dp.toPx() }
            // the blur reads past the box's edges and the clip trims it, so nothing bright smears in from an edge
            GlassPillBackground(
                state = glass,
                blurRadius = { radius },
                tint = colors.singleOrNull() ?: Color.Transparent,
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = true,
                modifier = Modifier.matchParentSize(),
            )
            if (colors.size > 1) Box(Modifier.matchParentSize().background(gradient(colors, block.gradient)))
        } else if (colors.isNotEmpty() || block.glass != null) {
            // where there's no glass to be had (the small preview, a tones background) a frosted box still reads as a panel
            val fill = colors.ifEmpty { listOf(GlassStandIn) }
            Box(Modifier.matchParentSize().background(gradient(fill, block.gradient)))
        }
        block.stroke?.let { Box(Modifier.matchParentSize().border(it, context.color(block.strokeColor), shape)) }
        val inner = Modifier
            .then(if (block.height != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(block.padding)
        if (block.row) {
            Row(inner, horizontalArrangement = Arrangement.spacedBy(block.gap), verticalAlignment = Alignment.CenterVertically) {
                RowBlocks(block.blocks, context)
            }
        } else {
            Column(inner, verticalArrangement = Arrangement.spacedBy(block.gap), horizontalAlignment = context.layout.align.horizontal) {
                ColumnBlocks(block.blocks, context)
            }
        }
    }
}

private fun gradient(colors: List<Color>, kind: GradientKind): Brush {
    if (colors.size == 1) return Brush.linearGradient(listOf(colors[0], colors[0]))
    return when (kind) {
        GradientKind.Vertical -> Brush.verticalGradient(colors)
        GradientKind.Horizontal -> Brush.horizontalGradient(colors)
        GradientKind.Diagonal -> Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)
        GradientKind.Radial -> Brush.radialGradient(colors)
    }
}

/** a shape from svg path data, scaled from its view box into its own box, filled, outlined or both */
@Composable
internal fun SkinVector(block: SkinBlock.Vector, context: SkinContext, modifier: Modifier) {
    // parsed once per shape; a path the parser can't read draws nothing instead of breaking the player
    val path = remember(block.data) { runCatching { PathParser().parsePathString(block.data).toPath() }.getOrNull() }
    val fill = block.fill.map(context::color)
    val outline = block.strokeColor?.let(context::color)
    val width = block.width ?: (block.height * (block.viewWidth / block.viewHeight))
    Spacer(
        modifier
            .size(width, block.height)
            .drawBehind {
                path ?: return@drawBehind
                val sx = size.width / block.viewWidth
                val sy = size.height / block.viewHeight
                scale(sx, sy, pivot = Offset.Zero) {
                    if (fill.isNotEmpty()) {
                        val brush = if (fill.size == 1) Brush.linearGradient(listOf(fill[0], fill[0])) else Brush.verticalGradient(fill, 0f, block.viewHeight)
                        drawPath(path, brush)
                    }
                    if (outline != null && block.stroke != null) {
                        // the stroke is given in dp on screen, so it's drawn thinner in the view box's own units
                        val strokeWidth = block.stroke.toPx() / min(sx, sy).coerceAtLeast(0.001f)
                        drawPath(path, outline, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            },
    )
}

private val GlassStandIn = Color.White.copy(alpha = 0.10f)
