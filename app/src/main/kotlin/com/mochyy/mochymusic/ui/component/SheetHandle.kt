// SheetHandle.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest

// the bar end to end with its round caps, how thick it is, and the arrow it bends into
private val BarLength = 120.dp
private val BarThickness = 5.dp
private val ArrowLength = 44.dp
private val ArrowDepth = 10.dp

// tall enough for the arrow's point and caps, so nothing of it is cut when the box is clipped
private val GrabberHeight = 16.dp

private val GrabberColor = Color.White.copy(alpha = 0.7f)

// the same bend the player's handle does, a quick spring with no wobble
private val BendSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

// how dense the fade is from the sheet's top edge down to the bottom of the handle row, eased so it never bands
private val FadeStops = floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)
private val FadeAlphas = floatArrayOf(0.94f, 0.8f, 0.5f, 0.18f, 0f)

// a sheet's handle row: its fade, with the bar on top; the caller adds the height and the drag
@Composable
fun SheetHandleRow(
    bend: () -> Float,
    fade: (() -> Color)?,
    modifier: Modifier = Modifier,
    barOffset: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fade != null) Modifier.sheetTopFade(fade) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SheetGrabber(bend, Modifier.offset(y = barOffset))
    }
}

// the bar alone; bend() is read in draw, so a drag never recomposes it
@Composable
fun SheetGrabber(bend: () -> Float, modifier: Modifier = Modifier, color: Color = GrabberColor) {
    Spacer(
        modifier
            .size(BarLength, GrabberHeight)
            .drawWithCache {
                val path = Path()
                val stroke = Stroke(BarThickness.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val barHalf = (BarLength - BarThickness).toPx() / 2f
                val arrowHalf = (ArrowLength - BarThickness).toPx() / 2f
                val depth = ArrowDepth.toPx()
                onDrawBehind { drawBendingBar(path, bend(), barHalf, arrowHalf, depth, stroke, color) }
            },
    )
}

// a bar that folds into an arrow pointing down as bend goes 0 to 1, centred in the box; the player's handle draws with it too
internal fun DrawScope.drawBendingBar(
    path: Path,
    bend: Float,
    barHalf: Float,
    arrowHalf: Float,
    depthPx: Float,
    stroke: Stroke,
    color: Color,
) {
    val half = lerp(barHalf, arrowHalf, bend)
    val depth = depthPx * bend
    val cx = size.width / 2f
    val cy = size.height / 2f
    path.reset()
    path.moveTo(cx - half, cy - depth / 2f)
    path.lineTo(cx, cy + depth / 2f)
    path.lineTo(cx + half, cy - depth / 2f)
    drawPath(path = path, color = color, style = stroke)
}

// springs the bar into the arrow while arrow() holds and back flat after; arrow() is watched outside composition
@Composable
fun rememberSheetGrabberBend(arrow: () -> Boolean): Animatable<Float, AnimationVector1D> {
    val bend = remember { Animatable(0f) }
    val currentArrow by rememberUpdatedState(arrow)
    LaunchedEffect(bend) {
        snapshotFlow { currentArrow() }.collectLatest { on -> bend.animateTo(if (on) 1f else 0f, BendSpring) }
    }
    return bend
}

// the soft fade behind the bar, so rows scrolled up under it melt into the sheet instead of ending at a hard line
fun Modifier.sheetTopFade(color: () -> Color): Modifier = drawWithCache {
    var brushColor = Color.Unspecified
    var brush: Brush? = null
    onDrawBehind {
        val surface = color()
        // the colour only moves while the sheet recedes behind another, so the brush is rebuilt only then
        var current = brush
        if (current == null || surface != brushColor) {
            current = Brush.verticalGradient(
                *Array(FadeStops.size) { i -> FadeStops[i] to surface.copy(alpha = surface.alpha * FadeAlphas[i]) },
            )
            brush = current
            brushColor = surface
        }
        drawRect(current)
    }
}

// how tall a sheet's handle row is, and whether the scroller at the top of its content has made room for it inside itself
@Stable
class SheetHandleRoom(val height: Dp) {
    internal var takers by mutableIntStateOf(0)
}

val LocalSheetHandleRoom = staticCompositionLocalOf<SheetHandleRoom?> { null }

// for the scroller at the very top of a sheet: pad by this inside the scroll and its rows slide up under the handle's fade
@Composable
fun sheetHandleRoom(): Dp {
    val room = LocalSheetHandleRoom.current ?: return 0.dp
    DisposableEffect(room) {
        room.takers++
        onDispose { room.takers-- }
    }
    return room.height
}

// once a scroller inside has made room for the handle, the content reaches up to the sheet's top edge under the handle row
fun Modifier.underSheetHandle(room: SheetHandleRoom): Modifier = layout { measurable, constraints ->
    // read here, in layout, so a page taking or giving back the room only moves things, it never recomposes
    val lift = if (room.takers > 0) room.height.roundToPx() else 0
    if (lift == 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + lift else Constraints.Infinity
    val placeable = measurable.measure(
        constraints.copy(minHeight = (constraints.minHeight + lift).coerceAtMost(maxHeight), maxHeight = maxHeight),
    )
    layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
}
