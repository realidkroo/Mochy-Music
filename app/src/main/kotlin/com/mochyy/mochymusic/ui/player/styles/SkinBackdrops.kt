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
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.ui.component.glassRoot
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
            PlayerBackgroundContent(style = background.style, thumbnailUrl = context.track.artwork, pureBlack = false, modifier = modifier)
        }
        is SkinBackground.Fill -> {
            val colors = background.colors.map(context::color)
            Box(modifier.then(if (colors.size == 1) Modifier.background(colors.first()) else Modifier.background(Brush.verticalGradient(colors))))
        }
        is SkinBackground.Picture -> SkinPictureBackdrop(background, context, modifier)
        is SkinBackground.Shader -> SkinShaderSurface(background.program, context, modifier) {
            SkinBackgroundFill(background.fallback, context, modifier)
        }
        SkinBackground.Tones -> Unit
    }
}

// a picture, blurred once when it asks; a little bigger than the screen so the blur's clamped edges never show as a glow
@Composable
private fun SkinPictureBackdrop(background: SkinBackground.Picture, context: SkinContext, modifier: Modifier) {
    val blurred = background.blur > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    AsyncImage(
        model = context.skin.resolve(background.image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.then(
            if (!blurred) Modifier else Modifier
                .graphicsLayer {
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
                .blur(background.blur.dp),
        ),
    )
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
