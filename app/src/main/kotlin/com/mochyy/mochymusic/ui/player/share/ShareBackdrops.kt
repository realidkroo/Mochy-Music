// ShareBackdrops.kt
//
// What sits behind the words on a lyrics share picture: soft clouds made from the cover, your own
// picture, or a plain colour - and which ink reads on each.

package com.mochyy.mochymusic.ui.player.share

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.component.setAgslInputShader
import com.mochyy.mochymusic.ui.component.setAgslUniform
import com.mochyy.mochymusic.ui.player.flagship.downscaledLayer
import com.mochyy.mochymusic.ui.utils.SoftBlurTransformation
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** White on dark backgrounds, near-black on light plain colours. */
internal fun contentColorOn(background: ShareBackground): Color = when (background) {
    is ShareBackground.Solid -> if (background.color.luminance() > 0.55f) Color(0xFF111111) else Color.White
    else -> Color.White
}

/** Any background: the background pager shows each choice with this. */
@Composable
internal fun BackdropOf(
    background: ShareBackground,
    thumbnailUrl: String?,
    palette: List<Color>,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    when (background) {
        is ShareBackground.Solid -> Box(modifier.fillMaxSize().background(background.color))
        is ShareBackground.Picture -> PictureBackdrop(background, modifier)
        ShareBackground.FromMusic -> MusicBackdrop(thumbnailUrl, palette, animate, modifier)
    }
}

@Composable
private fun PictureBackdrop(bg: ShareBackground.Picture, modifier: Modifier) {
    val context = LocalContext.current
    // blurred as a small bitmap once, so it looks the same on every Android version and records
    // into the saved picture like any other image
    val request = remember(bg.uri, bg.blur) {
        ImageRequest.Builder(context)
            .data(bg.uri)
            .apply { if (bg.blur) size(220).transformations(SoftBlurTransformation(width = 160, radius = 3)) else size(1440) }
            .build()
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bg.opacity },
        )
    }
}

/**
 * Made from the song: its cover blurred right down, with soft clouds of the cover's own colours
 * drifting over it. The drift only runs while [animate] is on (the editors), the saved picture
 * keeps whatever moment it was shared at.
 */
@Composable
internal fun MusicBackdrop(thumbnailUrl: String?, palette: List<Color>, animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl?.resize(544, 544))
            .size(192)
            .transformations(SoftBlurTransformation())
            .build()
    }
    val drift = if (animate) {
        rememberInfiniteTransition(label = "shareDrift").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
            label = "shareDriftPhase",
        )
    } else {
        null
    }
    val base = palette.firstOrNull() ?: Color(0xFF2A2A2E)
    Box(modifier.fillMaxSize().background(base)) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.75f },
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawMusicClouds(palette, drift?.value ?: MusicCloudsRestPhase) },
        )
    }
}

// where the clouds sit when nothing makes them drift
internal const val MusicCloudsRestPhase = 0.6f

private val MusicCloudsScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.38f)))

// the cover's colours as soft clouds, placed by phase (radians, one lap is 2 pi)
internal fun DrawScope.drawMusicClouds(palette: List<Color>, phase: Float) {
    val count = minOf(palette.size, 5)
    val minDim = size.minDimension
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val color = palette[i]
        val angle = phase * (0.7f + i * 0.19f) + i * 1.3f
        val center = Offset(
            w * (0.3f + 0.2f * (i % 3)) + w * 0.22f * cos(angle),
            h * (0.2f + 0.16f * (i % 4)) + h * 0.12f * sin(angle),
        )
        val radius = minDim * (0.7f + 0.1f * (i % 2))
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0f)), center, radius),
            radius,
            center,
        )
    }
    // a touch darker toward the bottom, where the words usually end
    drawRect(MusicCloudsScrim)
}

// compiled once for every share liquid-cover backdrop; uniforms are set right before each draw
private val sharedLiquidCoverShader: Any? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createAgslShader(LiquidCoverAgsl) else null
}

// Apple Music-style moving & liquid-warping blurred cover backdrop.
// Rendered at a quarter size (1/16th pixels) for smooth 60fps preview & 4K export, with an automatic
// Canvas fallback for Android < 13 and software export frames.
@Composable
internal fun LiquidCoverBackdrop(
    coverBlur: ImageBitmap?,
    palette: List<Color>,
    dim: Float,
    timeSec: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shader = sharedLiquidCoverShader
    val base = palette.firstOrNull() ?: Color(0xFF1E1E24)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .downscaledLayer()
            .drawWithCache {
                val bmp = coverBlur?.asAndroidBitmap()
                val agslBrush = if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ShaderBrush(shader.asAgslPaintShader())
                } else {
                    null
                }
                val inputShader = if (bmp != null && agslBrush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        filterMode = BitmapShader.FILTER_MODE_LINEAR
                        setLocalMatrix(
                            Matrix().apply {
                                setScale(size.width / bmp.width.coerceAtLeast(1), size.height / bmp.height.coerceAtLeast(1))
                            },
                        )
                    }
                } else {
                    null
                }
                onDrawBehind {
                    val t = timeSec()
                    val clampedDim = dim.coerceIn(0.15f, 1f)
                    if (shader != null && agslBrush != null && inputShader != null &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        drawContext.canvas.nativeCanvas.isHardwareAccelerated
                    ) {
                        shader.setAgslInputShader("image", inputShader)
                        shader.setAgslUniform("resolution", size.width, size.height)
                        shader.setAgslUniform("time", t)
                        shader.setAgslUniform("dim", clampedDim)
                        drawRect(brush = agslBrush)
                    } else {
                        // Software & pre-Android-13 fallback: gently rotating/breathing blurred cover + flowing colour pools
                        drawRect(Color.Black)
                        val phase = t * (2f * PI.toFloat() / 24f)
                        if (coverBlur != null) {
                            val angleDeg = 20f * sin(phase) + 9f * cos(phase * 2f + 0.8f)
                            val zoom = 1.65f + 0.12f * sin(phase + 0.5f)
                            val dx = size.width * 0.08f * sin(phase * 1.5f)
                            val dy = size.height * 0.08f * cos(phase * 1.3f)
                            withTransform({
                                translate(dx, dy)
                                rotate(angleDeg, center)
                                scale(zoom, zoom, center)
                            }) {
                                drawImage(
                                    image = coverBlur,
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                                    alpha = clampedDim,
                                )
                            }
                        } else {
                            drawRect(base.copy(alpha = clampedDim))
                        }
                        if (palette.isNotEmpty()) {
                            drawMusicClouds(palette, phase)
                        } else {
                            drawRect(MusicCloudsScrim)
                        }
                    }
                }
            },
    )
}

// Two counter-flowing liquid-warped layers of the blurred album cover, slowly swirling and morphing
// like Apple Music's animated backdrop, with gentle saturation lift, dim, vignette, and 8-bit dither.
private const val LiquidCoverAgsl = """
uniform float2 resolution;
uniform float time;
uniform float dim;
uniform shader image;

const float TAU = 6.2831853;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(resolution, float2(1.0));
    float t = time * TAU / 24.0;

    float2 c = uv - 0.5;
    float aspect = resolution.x / max(resolution.y, 1.0);
    float2 p = c;
    p.x *= min(aspect, 1.0);
    p.y *= min(1.0 / max(aspect, 0.001), 1.0);

    float angle = 0.42 * sin(t) + 0.18 * cos(t * 2.0 + 0.8);
    float scale = 1.48 + 0.10 * sin(t + 0.5);
    float ca = cos(angle);
    float sa = sin(angle);
    p = float2(p.x * ca - p.y * sa, p.x * sa + p.y * ca) / scale;

    float2 w1 = float2(
        sin(p.y * 5.2 + t * 2.0) * 0.085 + cos(p.x * 4.4 - t) * 0.065,
        cos(p.x * 5.0 + t * 2.0) * 0.085 + sin(p.y * 4.2 + t) * 0.065
    );
    float2 q = p + w1;
    float2 w2 = float2(
        sin((q.x + q.y) * 7.5 - t * 3.0) * 0.042,
        cos((q.x - q.y) * 7.0 + t * 3.0) * 0.042
    );

    float2 uv1 = clamp(q + w2 + 0.5, float2(0.02), float2(0.98));
    float2 q2 = float2(-p.y, p.x) * 0.85 - w1 * 0.85;
    float2 uv2 = clamp(q2 + 0.5, float2(0.02), float2(0.98));

    half4 col1 = image.eval(uv1 * resolution);
    half4 col2 = image.eval(uv2 * resolution);
    half3 col = mix(col1.rgb, col2.rgb, 0.36);

    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = clamp(mix(half3(luma), col, 1.24), half3(0.0), half3(1.0));
    col *= half(dim);
    col *= half(1.0 - 0.18 * smoothstep(0.45, 1.0, uv.y));

    float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += half((grain - 0.5) / 255.0);
    return half4(col, 1.0);
}
"""
