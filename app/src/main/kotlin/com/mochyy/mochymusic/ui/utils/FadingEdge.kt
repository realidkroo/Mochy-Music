// FadingEdge.kt

package com.mochyy.mochymusic.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

fun Modifier.fadingEdge(
    left: Dp? = null,
    top: Dp? = null,
    right: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            drawRect(
                brush =
                Brush.verticalGradient(
                    colors =
                    listOf(
                        Color.Transparent,
                        Color.Black,
                    ),
                    startY = 0f,
                    endY = top.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            drawRect(
                brush =
                Brush.verticalGradient(
                    colors =
                    listOf(
                        Color.Black,
                        Color.Transparent,
                    ),
                    startY = size.height - bottom.toPx(),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (left != null) {
            drawRect(
                brush =
                Brush.horizontalGradient(
                    colors =
                    listOf(
                        Color.Black,
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = left.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right != null) {
            drawRect(
                brush =
                Brush.horizontalGradient(
                    colors =
                    listOf(
                        Color.Transparent,
                        Color.Black,
                    ),
                    startX = size.width - right.toPx(),
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.fadingEdge(
    horizontal: Dp? = null,
    vertical: Dp? = null,
) = fadingEdge(
    left = horizontal,
    right = horizontal,
    top = vertical,
    bottom = vertical,
)

/** fades a sideways row out at whichever edge still has more to show (0 to 1 each), so it reads as going on instead of cut off; no extra layer while neither shows */
fun Modifier.scrollEdgeFade(width: Dp, left: () -> Float, right: () -> Float) = graphicsLayer {
    compositingStrategy = if (left() > 0f || right() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}.drawWithContent {
    drawContent()
    val edge = width.toPx().coerceAtMost(size.width / 2f)
    val l = left()
    val r = right()
    if (l > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black.copy(alpha = 1f - l), 1f to Color.Black, startX = 0f, endX = edge),
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
    if (r > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - r), startX = size.width - edge, endX = size.width),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** a scrolling row's edges fade in over their first stretch of scroll, so they don't pop */
fun Modifier.scrollEdgeFade(state: ScrollState, width: Dp) = composed {
    val edge = with(LocalDensity.current) { width.toPx() }
    scrollEdgeFade(width, left = { (state.value / edge).coerceIn(0f, 1f) }, right = { ((state.maxValue - state.value) / edge).coerceIn(0f, 1f) })
}

/** the same for a lazy row, which only knows whether it can go further each way */
fun Modifier.scrollEdgeFade(state: LazyListState, width: Dp) = composed {
    val edge = with(LocalDensity.current) { width.toPx() }
    scrollEdgeFade(
        width,
        left = { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / edge).coerceIn(0f, 1f) },
        right = { if (state.canScrollForward) 1f else 0f },
    )
}
