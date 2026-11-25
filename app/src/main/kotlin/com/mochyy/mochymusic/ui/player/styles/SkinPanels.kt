// a module player's panels and drawn shapes: boxes with a fill, a gradient or frosted glass behind their blocks, and shapes from svg path data

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawWithCache
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
import com.mochyy.mochymusic.modules.skin.SkinBlurDirection
import com.mochyy.mochymusic.modules.skin.SkinGlassConfig
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import kotlin.math.min

/** a standalone frosted or progressive blur band over the player's backdrop */
@Composable
internal fun SkinBlurBlock(block: SkinBlock.Blur, context: SkinContext, modifier: Modifier) {
    val shape = RoundedCornerShape(block.corner)
    val colors = block.colors.map(context::color)
    val sized = modifier
        .then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
        .height(block.height)
        .clip(shape)
    val glass = context.glass
    Box(sized) {
        if (glass != null) {
            val radiusPx = with(LocalDensity.current) { block.radius.dp.toPx() }
            val tint = colors.singleOrNull() ?: Color.Transparent
            when (block.direction) {
                SkinBlurDirection.TopToBottom -> ProgressiveGlassBackground(
                    state = glass,
                    maxBlurRadius = { radiusPx },
                    tint = tint,
                    direction = BlurDirection.TopToBottom,
                    steps = block.steps,
                    fallbackBlur = true,
                    modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.BottomToTop -> ProgressiveGlassBackground(
                    state = glass,
                    maxBlurRadius = { radiusPx },
                    tint = tint,
                    direction = BlurDirection.BottomToTop,
                    steps = block.steps,
                    fallbackBlur = true,
                    modifier = Modifier.matchParentSize(),
                )
                SkinBlurDirection.Radial -> {
                    val radialMask = remember {
                        Brush.radialGradient(0f to Color.Black, 0.72f to Color.Black, 1f to Color.Transparent)
                    }
                    GlassPillBackground(
                        state = glass,
                        blurRadius = { radiusPx },
                        tint = tint,
                        mask = radialMask,
                        tileMode = android.graphics.Shader.TileMode.CLAMP,
                        bleed = block.bleed,
                        fallbackBlur = true,
                        fallbackMaskMode = 3,
                        fallbackMaskParam = 0.72f,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                null -> GlassPillBackground(
                    state = glass,
                    blurRadius = { radiusPx },
                    tint = tint,
                    tileMode = android.graphics.Shader.TileMode.CLAMP,
                    bleed = block.bleed,
                    fallbackBlur = true,
                    modifier = Modifier.matchParentSize(),
                )
            }
            if (colors.size > 1) Box(Modifier.matchParentSize().background(Brush.verticalGradient(colors)))
        } else {
            val fill = colors.ifEmpty { listOf(GlassStandIn) }
            Box(Modifier.matchParentSize().background(if (fill.size == 1) androidx.compose.ui.graphics.SolidColor(fill[0]) else Brush.verticalGradient(fill)))
        }
    }
}

/** a panel: frosted glass, progressive blur, a fill or a gradient behind its blocks, a border, round corners, its blocks in a column or a row */
@Composable
internal fun SkinBox(block: SkinBlock.Box, context: SkinContext, modifier: Modifier, inRow: Boolean) {
    val shape = RoundedCornerShape(block.corner)
    val colors = block.fill.map(context::color)
    val target = block.target
    val run = if (target != null) rememberTargetRunner(context) else null
    val interactive = LocalStyleInteractive.current
    val sized = modifier
        .then(block.width?.let { Modifier.width(it) } ?: if (inRow) Modifier else Modifier.fillMaxWidth())
        .then(block.height?.let { Modifier.height(it) } ?: Modifier)
        .then(if (block.shadow) Modifier.shadow(16.dp, shape) else Modifier)
        .clip(shape)
        .then(if (target != null && run != null) Modifier.clickable { if (!context.preview && interactive()) run(target) } else Modifier)
    val cfg = block.glassConfig ?: block.glass?.let { SkinGlassConfig(blur = it) }
    val glass = context.glass.takeIf { cfg != null }
    Box(sized) {
        if (glass != null && cfg != null) {
            val radius = with(LocalDensity.current) { cfg.blur.dp.toPx() }
            val tint = cfg.tint?.let(context::color) ?: colors.singleOrNull() ?: Color.Transparent
            val progressive = when (cfg.progressive) {
                SkinBlurDirection.TopToBottom -> BlurDirection.TopToBottom
                SkinBlurDirection.BottomToTop -> BlurDirection.BottomToTop
                else -> null
            }
            val radialMask = remember(cfg.progressive) {
                if (cfg.progressive == SkinBlurDirection.Radial) {
                    Brush.radialGradient(0f to Color.Black, 0.72f to Color.Black, 1f to Color.Transparent)
                } else null
            }
            GlassPillBackground(
                state = glass,
                blurRadius = { radius },
                tint = tint,
                mask = radialMask,
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = cfg.bleed,
                progressive = progressive,
                progressiveRamp = cfg.ramp,
                fallbackBlur = true,
                fallbackMaskMode = if (cfg.progressive == SkinBlurDirection.Radial) 3 else 0,
                fallbackMaskParam = 0.72f,
                modifier = Modifier.matchParentSize(),
            )
            cfg.shader?.let { prog ->
                SkinShaderSurface(prog, context, Modifier.matchParentSize()) {}
            }
            if (colors.size > 1) Box(Modifier.matchParentSize().background(gradient(colors, block.gradient)))
            if (cfg.outline && block.stroke == null) {
                Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.22f), shape))
            }
        } else if (colors.isNotEmpty() || cfg != null) {
            // where there's no glass to be had (the small preview, a tones background) a frosted box still reads as a panel
            val fill = colors.ifEmpty { listOf(cfg?.tint?.let(context::color) ?: GlassStandIn) }
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
    if (colors.size == 1) return androidx.compose.ui.graphics.SolidColor(colors[0])
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
    val fillBrush = remember(fill, block.viewHeight) {
        when {
            fill.isEmpty() -> null
            fill.size == 1 -> androidx.compose.ui.graphics.SolidColor(fill[0])
            else -> Brush.verticalGradient(fill, 0f, block.viewHeight)
        }
    }
    val outline = block.strokeColor?.let(context::color)
    val width = block.width ?: (block.height * (block.viewWidth / block.viewHeight))
    Spacer(
        modifier
            .size(width, block.height)
            .drawWithCache {
                val sx = size.width / block.viewWidth
                val sy = size.height / block.viewHeight
                val strokeStyle = if (outline != null && block.stroke != null) {
                    val strokeWidth = block.stroke.toPx() / min(sx, sy).coerceAtLeast(0.001f)
                    Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                } else null
                onDrawBehind {
                    path ?: return@onDrawBehind
                    scale(sx, sy, pivot = Offset.Zero) {
                        fillBrush?.let { drawPath(path, it) }
                        if (outline != null && strokeStyle != null) {
                            drawPath(path, outline, style = strokeStyle)
                        }
                    }
                }
            },
    )
}

private val GlassStandIn = Color.White.copy(alpha = 0.10f)
