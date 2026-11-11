// FrostedGlass.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.ChromeGlassStyle
import com.mochyy.mochymusic.constants.ChromeGlassStyleKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

/*
 * The floating chrome, the nav bar and the mini player, drawn to the Figma: #131313 at 78% over
 * the blurred app, a 2px white hairline at 5% just inside the edge, and a faint shadow under it.
 * Both bars use what's here, so they can't drift apart.
 */

/** How much of the chrome colour covers the blurred app. */
private const val FrostTintAlpha = 0.78f

/** The hairline is 2 real pixels on every screen, not 2dp: any thicker and it reads as a border. */
private const val ChromeOutlineWidthPx = 2f
private val ChromeOutlineColor = Color.White.copy(alpha = 0.05f)
private val ChromeOutlineStroke = Stroke(ChromeOutlineWidthPx)

/** Lifts the chrome off a light page without looking like a dark halo on a black one. */
val ChromeShadow = Shadow(radius = 6.3.dp, color = Color.Black.copy(alpha = 0.12f), spread = 2.dp)

/** The lit tab's pill in the nav bar, and the round press behind the mini player's buttons. */
val ChromeHighlightColor = Color.White.copy(alpha = 0.1f)

/**
 * A faint grain over the glass. A dark blur only has a few shades to work with, so without it the
 * glass shows bands; this breaks them up and is too faint to see as grain.
 */
private const val FrostGrainAlpha = 0.035f
private const val FrostGrainTileSize = 64

/** A small tile of grain, half lighter and half darker pixels, repeated over the glass. Made once. */
private val FrostGrainBrush: ShaderBrush by lazy {
    val random = java.util.Random(0x6A55)
    val pixels = IntArray(FrostGrainTileSize * FrostGrainTileSize) {
        val alpha = random.nextInt(256) shl 24
        if (random.nextBoolean()) alpha or 0xFFFFFF else alpha
    }
    val tile = android.graphics.Bitmap.createBitmap(
        pixels, FrostGrainTileSize, FrostGrainTileSize, android.graphics.Bitmap.Config.ARGB_8888,
    )
    ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The frosted glass behind the nav bar and the mini player.
 *
 * The blur reads a margin of the app around the glass (see `bleed` on [GlassPillBackground]). It
 * used to see only what was right under the glass and repeat the edge row past it, so anything
 * bright touching an edge was smeared across the whole bar: streaks while scrolling, and a flash
 * of glow each time a cover or a title crossed in. The parent has to clip it to its shape.
 */
@Composable
fun FrostedChromeGlass(
    state: GlassState,
    pureBlack: Boolean,
    accentWash: (() -> Color)?,
    modifier: Modifier = Modifier,
    // which bar this is, so a look's glass shader can draw each one its own way
    surface: ChromeSurface = ChromeSurface.NavBar,
) {
    // a look module can swap the frost for a glass shader of its own; android 13+ runs it, older phones and blur switched off keep the frost
    val (style) = rememberEnumPreference(ChromeGlassStyleKey, defaultValue = ChromeGlassStyle.FROSTED)
    val (blurOff) = rememberPreference(DisableBlurKey, defaultValue = false)
    val moduleGlass = com.mochyy.mochymusic.modules.skin.ActiveSkin.glass
    if (style == ChromeGlassStyle.MODULE && moduleGlass != null && !blurOff && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ModuleChromeGlass(state, moduleGlass, pureBlack, accentWash, surface, modifier)
        return
    }
    val color = if (pureBlack) Color.Black else GlassChromeColor
    // the grain only matters on the finer, high quality glass
    val grain = LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.HIGH
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (grain) drawRect(brush = FrostGrainBrush, alpha = FrostGrainAlpha)
        }
    ) {
        GlassPillBackground(
            state = state,
            blurRadius = { GlassChromeBlurRadius },
            tint = color.copy(alpha = FrostTintAlpha),
            foundationColor = color,
            accentWash = accentWash,
            // Only reaches the margin's outer edge now, too far out to show.
            tileMode = android.graphics.Shader.TileMode.CLAMP,
            bleed = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** the bars that share the chrome glass; [kind] is the number a look's glass shader gets for each */
enum class ChromeSurface(val kind: Float) { NavBar(0f), MiniPlayer(1f), WideBar(2f) }

/**
 * The chrome's hairline round a rounded rect, kept just inside it so the parent's clip can't
 * shave half of it off. [alpha] fades it, for the mini player as it opens.
 */
fun DrawScope.drawChromeOutline(
    topLeft: Offset,
    size: Size,
    cornerRadius: Float,
    alpha: Float = 1f,
) {
    val half = ChromeOutlineWidthPx / 2f
    drawRoundRect(
        color = ChromeOutlineColor,
        topLeft = Offset(topLeft.x + half, topLeft.y + half),
        size = Size(size.width - ChromeOutlineWidthPx, size.height - ChromeOutlineWidthPx),
        cornerRadius = CornerRadius((cornerRadius - half).coerceAtLeast(0f)),
        style = ChromeOutlineStroke,
        alpha = alpha,
    )
}
