// CoverGradientBackdrop.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import com.mochyy.mochymusic.ui.player.BackdropBlurTransformation
import com.mochyy.mochymusic.ui.player.LocalCrossfadeShown
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.isActive
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.component.setAgslUniform
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.setAgslInputShader
import coil3.toBitmap
import coil3.imageLoader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import com.mochyy.mochymusic.ui.player.flagship.downscaledLayer
import kotlin.math.floor

// one warp shader for every backdrop copy: each draw sets its own numbers first, and a song change compiles nothing
private val sharedWarpShader: Any? by lazy {
    if (android.os.Build.VERSION.SDK_INT >= 33) createAgslShader(DirectWarpShaderSource) else null
}

// soft 48px liquid warp looks the same at 30 steps a second while cutting gpu work and refresh wakeups by 4x
private const val WarpStepsPerSecond = 30f
private const val WarpWakeMillis = 30L

// null below android 13, where the warp can't run; passing null to a backdrop turns the warp off
@Composable
fun rememberWarpShader(): Any? = sharedWarpShader

@Composable
fun CoverGradientBackdrop(
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    shader: Any? = rememberWarpShader(),
    timeProvider: (() -> Float)? = null,
    /**
     * Called once the picture has loaded - or has failed to, either way there is nothing more to
     * wait for. A caller fading this backdrop in waits for it: until then it draws nothing.
     */
    onLoaded: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentOnLoaded by androidx.compose.runtime.rememberUpdatedState(onLoaded)
    val isShown by androidx.compose.runtime.rememberUpdatedState(LocalCrossfadeShown.current)

    val ownTime = remember { mutableFloatStateOf(0f) }
    val runOwnClock = timeProvider == null
    LaunchedEffect(animate, runOwnClock) {
        if (!animate || !runOwnClock) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->

                ownTime.floatValue = ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)
            }
            kotlinx.coroutines.delay(WarpWakeMillis)
        }
    }
    val currentTime by androidx.compose.runtime.rememberUpdatedState(timeProvider ?: { ownTime.floatValue })
    val step by remember { derivedStateOf { floor(currentTime() * WarpStepsPerSecond) } }

    val request = remember(context, thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl?.resize(48, 48))
            .allowHardware(false)
            .transformations(BackdropBlurTransformation(radiusPx = 4))
            .crossfade(false)
            .size(CoilSize(48, 48))
            .build()
    }

    Box(modifier = modifier) {
        if (shader != null && android.os.Build.VERSION.SDK_INT >= 33) {
            // one downscaled pass over the 48px picture: a sixteenth of the pixels at 30 hz looks identical and saves huge gpu power
            val bitmap by produceState<android.graphics.Bitmap?>(null, request) {
                value = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
                currentOnLoaded?.invoke()
            }
            val directWarp = checkNotNull(sharedWarpShader)
            Spacer(
                modifier = Modifier
                    .requiredSize(width, height)
                    .downscaledLayer()
                    .drawWithCache {
                        val picture = bitmap
                        val brush = ShaderBrush(directWarp.asAgslPaintShader())
                        val input = picture?.let {
                            android.graphics.BitmapShader(
                                it,
                                android.graphics.Shader.TileMode.CLAMP,
                                android.graphics.Shader.TileMode.CLAMP,
                            ).apply {
                                filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
                                // ContentScale.FillBounds: the picture stretched over the whole box.
                                setLocalMatrix(
                                    android.graphics.Matrix().apply {
                                        setScale(size.width / it.width, size.height / it.height)
                                    }
                                )
                            }
                        }
                        onDrawBehind {
                            // a crossfade copy at zero opacity draws nothing, and doesn't follow the clock either
                            if (input == null || !isShown()) return@onDrawBehind
                            directWarp.setAgslInputShader("image", input)
                            directWarp.setAgslUniform("resolution", size.width, size.height)
                            directWarp.setAgslUniform("time", step / WarpStepsPerSecond)
                            directWarp.setAgslUniform("zoom", BackdropZoom)
                            drawRect(brush = brush)
                        }
                    }
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                onSuccess = { currentOnLoaded?.invoke() },
                onError = { currentOnLoaded?.invoke() },
                modifier = Modifier
                    .requiredSize(width, height)
                    // A plain transform: it never needed the Offscreen layer it used to force.
                    .graphicsLayer {
                        scaleX = BackdropZoom
                        scaleY = BackdropZoom
                    }
            )
        }

        Box(
            modifier = Modifier
                .requiredSize(width, height)
                .background(Color.Black.copy(alpha = 0.22f))
        )
    }
}

/** How far the backdrop is zoomed past its box, so the warp's edges stay off screen. */
private const val BackdropZoom = 1.6f

/**
 * The liquid warp, drawn straight onto the screen: [image] is the picture
 * stretched over [resolution], and [zoom] (about the centre) is the scale the layer version applied
 * after warping. Every visible pixel samples inside the picture, so its edges never come into it.
 */
private const val DirectWarpShaderSource = """
uniform float2 resolution;
uniform float time;
uniform float zoom;
uniform shader image;

half4 main(float2 fragCoord) {
    float2 centre = resolution * 0.5;
    float2 p = centre + (fragCoord - centre) / zoom;
    float2 uv = p / resolution;
    float t = time * 0.5;

    float2 warpOffset = float2(
        sin(uv.y * 3.0 + t) * 0.05 + cos(uv.x * 2.0 - t * 0.6) * 0.04,
        cos(uv.x * 3.0 + t * 0.7) * 0.05 + sin(uv.y * 2.5 + t * 0.9) * 0.04
    );

    return image.eval(p + warpOffset * resolution);
}
"""
