// what a module's player draws behind its blocks: one of mochy's backgrounds, colours, a picture or its own shader, with more layers stacked on top

package com.mochyy.mochymusic.ui.player.styles

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.player.rememberFallbackBlurredImage
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundContent
import com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop
import com.mochyy.mochymusic.ui.player.flagship.FollowPicture
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientClock
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors

@Composable
internal fun BoxScope.SkinBackdrop(context: SkinContext) {
    val layout = context.layout
    // captured for the frosted boxes when one asks; the boxes sit in the blocks above, never inside what they read
    val capture = context.glass?.let { Modifier.glassRoot(it) } ?: Modifier
    Box(Modifier.fillMaxSize().then(capture)) {
        SkinBackgroundFill(layout.background, context, Modifier.fillMaxSize())
        SkinLayers(context)
        if (layout.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = layout.dim)))
    }
}

/** the layers over the first background, each in its own layer so its loop only moves that layer */
@Composable
internal fun SkinLayers(context: SkinContext) {
    context.layout.layers.forEach { layer ->
        Box(Modifier.fillMaxSize().skinLayerMotion(layer.motion, layer.opacity, context.pulse)) {
            SkinBackgroundFill(layer.background, context, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SkinBackgroundFill(background: SkinBackground, context: SkinContext, modifier: Modifier) {
    when (background) {
        is SkinBackground.Player -> if (background.style == PlayerBackgroundStyle.AMBIENT) {
            AmbientSkinBackdrop(context, modifier)
        } else {
            val showing = rememberStyleShowing()
            val obscured = LocalAppContentObscured.current
            val running by remember { derivedStateOf { !context.preview && showing() && !obscured.value } }
            PlayerBackgroundContent(style = background.style, thumbnailUrl = context.track.artwork, pureBlack = false, modifier = modifier, animate = running)
        }
        is SkinBackground.Fill -> {
            val colors = background.colors.map(context::color)
            val fillModifier = if (colors.size == 1) {
                Modifier.background(colors.first())
            } else {
                val gradient = remember(colors) { Brush.verticalGradient(colors) }
                Modifier.background(gradient)
            }
            Box(modifier.then(fillModifier))
        }
        is SkinBackground.Picture -> SkinPictureBackdrop(background, context, modifier)
        is SkinBackground.CoverBlur -> SkinCoverBlurBackdrop(background, context, modifier)
        is SkinBackground.Shader -> SkinShaderSurface(background.program, context, modifier) {
            SkinBackgroundFill(background.fallback, context, modifier)
        }
        // the fallback stays under the video, so it's there until the first frame and for songs with nothing moving
        is SkinBackground.Live -> {
            SkinBackgroundFill(background.fallback, context, modifier)
            SkinCoverMedia(background.media, context, fit = false, rides = false, modifier = modifier)
        }
        SkinBackground.Tones -> Unit
    }
}

// a picture, blurred once when it asks; a little bigger than the screen so the blur's clamped edges never show as a glow
@Composable
private fun SkinPictureBackdrop(background: SkinBackground.Picture, context: SkinContext, modifier: Modifier) {
    val disableBlur by rememberPreference(DisableBlurKey, defaultValue = false)
    val wantBlur = !disableBlur && background.blur > 0.5f
    val nativeBlur = wantBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val rawModel = context.skin.resolve(background.image)
    val model = if (wantBlur && !nativeBlur) rememberFallbackBlurredImage(rawModel, background.blur) else rawModel
    Box(modifier) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(
                if (!nativeBlur) Modifier else Modifier
                    .graphicsLayer {
                        scaleX = 1.1f
                        scaleY = 1.1f
                    }
                    .blur(background.blur.dp),
            ),
        )
        // a gif or webp moves unless it's blurred, since a blur over a moving picture is worked out again on every frame
        if (!wantBlur && rawModel != null && isMovingPicture(rawModel)) MovingPictureOverlay(rawModel, context, ContentScale.Crop, null, Modifier.fillMaxSize())
        if (background.edgeBlur) {
            Box(Modifier.fillMaxSize().background(SkinPictureEdgeVignette))
        }
    }
}

private val SkinPictureEdgeVignette = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.45f),
    0.28f to Color.Transparent,
    0.68f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.65f),
)

// the song's cover art blurred at a custom radius with a dim veil
@Composable
private fun SkinCoverBlurBackdrop(background: SkinBackground.CoverBlur, context: SkinContext, modifier: Modifier) {
    val disableBlur by rememberPreference(DisableBlurKey, defaultValue = false)
    val wantBlur = !disableBlur && background.blur > 0.5f
    val nativeBlur = wantBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val rawModel = if (wantBlur) context.track.artwork?.resize(256, 256) else context.track.artwork
    val model = if (wantBlur && !nativeBlur) rememberFallbackBlurredImage(rawModel, background.blur) else rawModel
    Box(modifier.background(Color.Black)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(
                if (!nativeBlur) Modifier else Modifier
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }
                    .blur(background.blur.dp),
            ),
        )
        if (background.dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim)))
        }
    }
}

// the flagship's drifting colours, whose clock rests while the style can't be seen instead of running under the mini player
@Composable
private fun AmbientSkinBackdrop(context: SkinContext, modifier: Modifier) {
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    val running by remember { derivedStateOf { !context.preview && showing() && !obscured.value } }
    val colors = rememberAmbientColors()
    FollowPicture(colors, context.track.artwork)
    AmbientBackdrop(colors = colors, time = rememberAmbientClock(running = running), modifier = modifier)
}
