package com.mochyy.mochymusic.ui.player.flagship

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.component.rememberCoverBlurAvailable
import kotlin.math.roundToInt

// the frost starts above the title row and runs past the screen's bottom so it never ends in view
private const val FrostTop = 0.50f
private const val FrostTitle = 0.565f
private const val FrostBottom = 1.03f

// 70% of peak blur by the middle of the title (the shader grows as t^2, so t = sqrt(0.7)), full strength right below it and held from there down
private const val FrostFull = FrostTop + (FrostTitle - FrostTop) / 0.83666f
private const val FullAt = (FrostFull - FrostTop) / (FrostBottom - FrostTop)

private val FrostRadius = 40.dp

// one pass growing from sharp to 70% at the title's middle and full frost just under it, then held all the way down
private val ProgressiveMask = Brush.verticalGradient(
    0f to Color.Transparent,
    0.04f to Color.Black,
    1f to Color.Black,
)

// one even blur (low quality, android 12) can only fade in, which reads much shorter than the growing one, so it starts higher and is full by the title's top
private const val FlatTop = 0.44f
private const val FlatFull = 0.545f
private const val FlatFullAt = (FlatFull - FlatTop) / (FrostBottom - FlatTop)

// smoothstep from clear to full, so the fade has no visible start or end line
private val FlatMask = Brush.verticalGradient(
    *Array(9) { i ->
        val t = i / 8f
        t * FlatFullAt to Color.Black.copy(alpha = t * t * (3f - 2f * t))
    },
    1f to Color.Black,
)

// the frost over the cover fading into its blurred copy: starts above the title, 70% at the title's middle, full from just under it down
@Composable
fun CoverFrost(
    glassState: GlassState,
    progressProvider: () -> Float,
    maxHeight: Dp,
    // gone while a page is up
    fadeProvider: () -> Float = { 1f },
    // how far the controls (and with them the cover's bottom) were moved, px down; read while placing only
    offsetProvider: () -> Float = { 0f },
) {
    val drawn by remember { derivedStateOf { progressProvider() > 0.5f && fadeProvider() > 0f } }
    if (!drawn || !rememberCoverBlurAvailable()) return
    val smooth = Build.VERSION.SDK_INT >= 33 && LocalBlurQuality.current == BlurQuality.HIGH
    val radiusPx = with(LocalDensity.current) { FrostRadius.toPx() }
    val top = if (smooth) FrostTop else FlatTop
    val currentProgress by rememberUpdatedState(progressProvider)
    val currentFade by rememberUpdatedState(fadeProvider)
    GlassPillBackground(
        state = glassState,
        blurRadius = { radiusPx },
        tileMode = android.graphics.Shader.TileMode.CLAMP,
        mask = if (smooth) ProgressiveMask else FlatMask,
        progressive = if (smooth) BlurDirection.TopToBottom else null,
        progressiveRamp = FullAt,
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight * (FrostBottom - top))
            .offset { IntOffset(0, (maxHeight.toPx() * top + offsetProvider()).roundToInt()) }
            // the blur is one draw, so its fade-in is laid on that draw instead of a second offscreen pass
            .graphicsLayer {
                alpha = ((currentProgress() - 0.5f) / 0.45f).coerceIn(0f, 1f) * currentFade().coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    )
}
