// TvKit.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.roundToInt

// the concept's colours: black ground, near-black cards, greys for anything that isn't the point
internal object TvColors {
    val Ground = Color.Black
    val Card = Color(0xFF1B1B1B)
    val CardRaised = Color(0xFF262626)
    val Pill = Color(0xFF151515)
    val PillEdge = Color(0xFF2A2A2A)
    val Placeholder = Color(0xFF6E6E6E)
    val Muted = Color(0xFF8E8E8E)
    val Faint = Color(0xFF5C5C5C)
    val Ring = Color.White
    val Dot = Color(0xFF2E2E2E)
}

// laid out for a 960 x 540 dp tv: margins from the concept, the right side kept clear for the rail
internal object TvSpace {
    val Start = 48.dp
    val End = 92.dp
    val Top = 88.dp
    val ChromeTop = 28.dp
}

internal object TvText {
    private val tight = (-0.04).em

    val PageTitle: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = tight, color = Color.White)

    val Hero: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = tight, lineHeight = 36.sp, color = Color.White)

    val Section: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.02).em, color = Color(0xFFBDBDBD))

    val Item: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.02).em, color = Color.White)

    val Sub: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TvColors.Muted)

    val Body: TextStyle
        get() = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.02).em, color = TvColors.Muted)

    // the album page's "120 songs · end of line · 21 minutes" strip
    val Ledger: TextStyle
        get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 0.05.em, color = TvColors.Muted)
}

// a focused thing on a tv grows a little and gets a white ring, the only way to see where the remote is
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.05f,
    ring: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // read while drawing, so a focus change only redraws this one item
    val lift = animateFloatAsState(if (focused) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 900f), label = "tvFocus")
    val ringWidth = with(LocalDensity.current) { 2.dp.toPx() }
    return this
        .graphicsLayer {
            val scale = 1f + (focusedScale - 1f) * lift.value
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            val amount = lift.value
            if (ring && amount > 0.01f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), TvColors.Ring.copy(alpha = amount), style = Stroke(ringWidth))
            }
        }
        .then(if (onFocused != null) Modifier.onFocusChanged { if (it.isFocused) onFocused() } else Modifier)
        // the menu key is the remote's long press, every song keeps its menu one button away
        .then(
            if (onLongClick != null) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                        onLongClick()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
        )
        .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
}

// a cover asked for at the size it's drawn, so a wall of art doesn't decode full-size images on a tv's small gpu
@Composable
internal fun TvCover(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val px = with(LocalDensity.current) { size.toPx().roundToInt() }.coerceAtLeast(64)
    Box(modifier.clip(shape).drawWithCache { onDrawBehind { drawRect(TvColors.Placeholder.copy(alpha = 0.35f)) } }) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url.resize(px, px)).size(px).build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

// a big picture for a backdrop, capped at a size the tv can scale up without spending memory on detail nobody sees
@Composable
internal fun TvBackdropImage(url: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    if (url == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url.resize(BackdropPx, BackdropPx)).size(BackdropPx).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        modifier = modifier,
    )
}

private const val BackdropPx = 900

// the welcome and phone pages' dot grid, built once per size and drawn as one path
internal fun Modifier.tvDots(spacing: Dp = 16.dp, color: Color = TvColors.Dot): Modifier = drawWithCache {
    val step = spacing.toPx()
    val radius = 1.dp.toPx()
    val dots = Path()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            dots.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), radius))
            x += step
        }
        y += step
    }
    onDrawBehind { drawPath(dots, color) }
}

// vertical pages scroll so the focused row sits a third of the way down, never jammed against an edge
@OptIn(ExperimentalFoundationApi::class)
internal val TvColumnPivot = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val keyline = containerSize * 0.3f
        if (size > containerSize - keyline) return offset
        return offset - keyline
    }
}

// rows keep the focused card near their start, so the next ones are always in sight
@OptIn(ExperimentalFoundationApi::class)
internal val TvRowPivot = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val keyline = containerSize * 0.12f
        if (offset >= keyline && offset + size <= containerSize * 0.8f) return 0f
        return offset - keyline
    }
}
