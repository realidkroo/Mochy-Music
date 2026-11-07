// PageWipe.kt
//
// The switch between two pages that Home's hero and the player's cover share: a feathered wipe
// with a little drift and blur, driven by how far each page is from settled. Both call sites hand
// it a page offset (0 on screen, +1 one page to the right) and it does the rest.

package com.mochyy.mochymusic.ui.component

import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import com.mochyy.mochymusic.ui.utils.AppMotion

// how far a page actually travels while it crossfades: a little drift reads as sliding,
// a full-width slide is what made two covers look stacked on each other mid-swipe
private const val PageMediaTravel = 0.18f
private const val PageTextTravel = 0.42f
// the wipe: feather width as a share of the page, how far the outgoing fade trails the incoming
// edge (in feathers), and the blur a page has at a full page away
private const val PageWipeFeather = 0.55f
private const val PageWipeTrail = 0.25f
private val PageSwitchBlur = 18.dp
// how far right of the page the wipe's circle is centred, in widths, when fully curved (a swipe),
// and the floor the curve eases down to for a straight edge
private const val PageWipeCurveReach = 0.6f
private const val PageWipeFlat = 0.04f

/** how far from settled a page has to be before the wipe touches it, and where it's hidden outright */
private const val PageWipeSettled = 0.001f
private const val PageWipeHidden = 0.999f

// in the wide layout the card beside the one in front sits back this dark, and its media drifts this far inside it
private const val PeekDim = 0.5f
private const val PeekMediaTravel = 0.12f

// a feathered wipe sweeps across from the side the next page comes in on: the incoming page goes
// 0 -> 100% behind the edge, the outgoing one 100 -> 0% just trailing it, so the seam only dims a
// touch and never shows black. both are blurred by how far they are from settling, so the new
// cover sharpens into place while the old one smears out.
// the edge is the rim of a circle growing in from the right: so far off it's a straight line when
// the page advances by itself ([curve] 0), close enough to bow round under your finger when you
// swipe ([curve] 1).
// pages are pinned to the viewport while this runs ([pageMediaDrift] does the sliding inside them):
// a page's own border on screen showed as a hard line wherever it crossed the feather.
//
// a settled page draws itself straight. while the wipe has something to do to it, the page is only
// recorded, and [PageWipeCopy] beside it draws that recording masked and blurred: from a half-size
// copy once the blur is wide enough to hide the difference, and only across the part of the page
// the mask leaves. both pages used to render the whole page offscreen at full size every frame of
// the switch, blur all of it and erase most of it again.
//
// draw the left-hand page of a pair first: the right-hand one is the one with the circle.
@Composable
internal fun PageWipe(
    pageOffset: () -> Float,
    curve: () -> Float,
    blur: Boolean,
    modifier: Modifier = Modifier,
    // the wide layout's cards sit side by side and slide, the next one peeking in, so there's nothing to wipe
    peek: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (peek) {
        PeekPage(pageOffset = pageOffset, modifier = modifier, content = content)
        return
    }
    val blurPx = if (blur) with(LocalDensity.current) { PageSwitchBlur.toPx() } else 0f
    val pageLayer = rememberGraphicsLayer()
    val sourceEdge = remember { PageWipeEdge() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val d = pageOffset()
                translationX = -d * size.width
                // this close to a whole page away, an incoming page is entirely past the edge and
                // an outgoing one sits under the settled page in front. drawn plainly there (the
                // wipe had already let go of it), the next cover flashed over the page for the
                // first frames of every advance, and the old one at the end of a swipe back
                alpha = if (abs(d) >= PageWipeHidden) 0f else 1f
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawWithContent {
                    if (sourceEdge.update(pageOffset(), size, curve(), blurPx)) {
                        pageLayer.record { this@drawWithContent.drawContent() }
                    } else {
                        drawContent()
                    }
                },
            content = content,
        )
        // a sibling of the recorded page, never inside it, so the recording can't contain itself
        PageWipeCopy(pageLayer = pageLayer, pageOffset = pageOffset, curve = curve, blurPx = blurPx)
    }
}

// a card of the wide hero: clipped to itself, darker the further it is from the front, read while drawing so a slide never recomposes it
@Composable
private fun PeekPage(pageOffset: () -> Float, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithContent {
                drawContent()
                val d = abs(pageOffset()).coerceAtMost(1f)
                if (d > PageWipeSettled) drawRect(Color.Black.copy(alpha = PeekDim * d))
            },
        content = content,
    )
}

/** where the wipe's edge is on one page, worked out again each frame it moves */
private class PageWipeEdge {
    var incoming = false
    var centerX = 0f
    var centerY = 0f
    /** incoming: opaque inside this radius. outgoing: gone inside it */
    var inner = 0f
    /** incoming: gone outside this radius. outgoing: untouched outside it */
    var outer = 0f
    var blur = 0f

    /**
     * the edge for a page [d] pages from settled on a page of [size]. true while the wipe changes
     * anything about the page: it erases some of it, or blurs it by more than half a pixel. outside
     * that the page as it is looks exactly the same, so it's drawn plainly.
     */
    fun update(d: Float, size: Size, curve: Float, blurPx: Float): Boolean {
        val a = abs(d)
        if (a < PageWipeSettled || a >= PageWipeHidden) return false
        val w = size.width
        val feather = w * PageWipeFeather
        // both pages of the pair agree on where the edge is: progress 0 -> 1 as the right-hand
        // page takes over, whichever way the swipe runs
        val progress = if (d > 0f) 1f - d else -d
        val off = w * PageWipeCurveReach / curve.coerceIn(PageWipeFlat, 1f)
        centerX = w + off
        centerY = size.height / 2f
        // from the incoming page's feather just touching the right edge, to it being opaque over
        // the far corner
        val from = off - feather
        val to = hypot(centerX, centerY)
        val r = from + (to - from) * progress
        incoming = d > 0f
        if (incoming) {
            // opaque inside the circle, gone a feather outside it
            inner = r
            outer = r + feather
        } else {
            // its fade trails the incoming one's by a quarter feather
            inner = r - feather * PageWipeTrail
            outer = inner + feather
        }
        blur = a * blurPx
        // the farthest point from the centre is the left corners, the nearest the right edge's middle
        val erases = if (incoming) inner < to else outer > off
        return erases || blur > 0.5f
    }
}

/**
 * the wipe's picture of a page: the recording, masked by the edge (DstOut, so it has a buffer of its
 * own), then blurred. the mask goes on before the blur, as it did when the page's own layer did both.
 * columns the mask clears completely are never drawn, and the mask itself only covers the feather.
 */
@Composable
private fun PageWipeCopy(
    pageLayer: GraphicsLayer,
    pageOffset: () -> Float,
    curve: () -> Float,
    blurPx: Float,
) {
    val edge = remember { PageWipeEdge() }
    DownscaledBlur(
        radius = {
            val a = abs(pageOffset())
            // the wipe's soft edge is an element blur, it stays crisp with element blur off
            if (a < PageWipeSettled || a >= PageWipeHidden || !AppMotion.elementBlur) 0f else BlurEffectCache.effectiveRadius(a * blurPx)
        },
        tileMode = Shader.TileMode.CLAMP,
        mask = null,
        offscreen = { abs(pageOffset()) in PageWipeSettled..PageWipeHidden },
        modifier = Modifier.fillMaxSize(),
    ) { fullSize ->
        if (!edge.update(pageOffset(), fullSize, curve(), blurPx)) return@DownscaledBlur
        val w = fullSize.width
        val h = fullSize.height
        val halfH = h / 2f
        // the first column a circle of radius [radius] covers from top to bottom
        fun fullyInside(radius: Float): Float =
            if (radius > halfH) min(w, edge.centerX - sqrt(radius * radius - halfH * halfH)) else w
        val left: Float
        val right: Float
        val maskLeft: Float
        val maskRight: Float
        if (edge.incoming) {
            // nothing of it left of the outer circle; solid where the inner one spans the height
            left = (edge.centerX - edge.outer).coerceAtLeast(0f)
            right = w
            maskLeft = left
            maskRight = fullyInside(edge.inner)
        } else {
            // gone where the inner circle spans the height; untouched left of the outer one
            left = 0f
            right = fullyInside(edge.inner)
            maskLeft = (edge.centerX - edge.outer).coerceAtLeast(0f)
            maskRight = right
        }
        if (right <= left) return@DownscaledBlur
        clipRect(left, 0f, right, h) { drawLayer(pageLayer) }
        if (maskRight > maskLeft) {
            val center = Offset(edge.centerX, edge.centerY)
            val stops = if (edge.incoming) {
                featherStops(edge.inner / edge.outer, inside = Color.Transparent, outside = Color.Black)
            } else {
                featherStops(edge.inner / edge.outer, inside = Color.Black, outside = Color.Transparent)
            }
            drawRect(
                brush = Brush.radialGradient(*stops, center = center, radius = edge.outer),
                topLeft = Offset(maskLeft, 0f),
                size = Size(maskRight - maskLeft, h),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}

// smoothstep across the feather instead of a straight ramp: a linear one has a visible crease
// where it meets the flat part on either end
private fun featherStops(start: Float, inside: Color, outside: Color): Array<Pair<Float, Color>> {
    val s = start.coerceIn(0f, 0.999f)
    return Array(FeatherEase.size) { i ->
        val t = i / (FeatherEase.size - 1f)
        (s + (1f - s) * t) to lerp(inside, outside, FeatherEase[i])
    }
}

private val FeatherEase = floatArrayOf(0f, 0.104f, 0.352f, 0.648f, 0.896f, 1f)

// the slide itself, inside a pinned page: the media drifts the way it used to, zoomed by just
// enough to keep its edges off screen, so it settles from a slight push-in
internal fun Modifier.pageMediaDrift(pageOffset: () -> Float, peek: Boolean = false): Modifier = if (peek) peekMediaDrift(pageOffset) else graphicsLayer {
    val d = pageOffset().coerceIn(-1f, 1f)
    val scale = 1f + 2f * PageMediaTravel * abs(d)
    scaleX = scale
    scaleY = scale
    translationX = d * size.width * PageMediaTravel
}

// text drifts further than the cover and is gone by half way, so the outgoing and incoming
// titles never sit on top of each other
internal fun Modifier.pageTextDrift(pageOffset: () -> Float, peek: Boolean = false): Modifier = graphicsLayer {
    val d = pageOffset()
    if (peek) {
        // the peeking card keeps a faint hint of its title, the way the concept shows it
        alpha = (1f - abs(d) * 0.75f).coerceIn(0f, 1f)
        return@graphicsLayer
    }
    translationX = d * size.width * (PageTextTravel + PageMediaTravel)
    alpha = (1f - abs(d) * 1.9f).coerceIn(0f, 1f)
}

// the wide hero's media lags a little behind its card as it slides, zoomed just enough to keep its edges inside the card
private fun Modifier.peekMediaDrift(pageOffset: () -> Float): Modifier = graphicsLayer {
    val d = pageOffset().coerceIn(-1f, 1f)
    val scale = 1f + 2f * PeekMediaTravel * abs(d)
    scaleX = scale
    scaleY = scale
    translationX = -d * size.width * PeekMediaTravel
}
