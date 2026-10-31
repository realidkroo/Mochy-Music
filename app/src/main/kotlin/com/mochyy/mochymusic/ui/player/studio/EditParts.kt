package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import com.mochyy.mochymusic.ui.player.HeldPart
import com.mochyy.mochymusic.ui.player.PlayerLayoutTweaks
import kotlin.math.roundToInt

/** 0..1 eased in and out, for the stretches of a move that run off one spring */
internal fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The editor's hold on the live flagship's parts. In the tiles they stay exactly where they are on
 * the player; opening a tile moves them: the cover editor slides the rest away, and the middle part
 * is a camera move - a dolly in on the middle part, everything else moving by its depth (the cover
 * behind drifts up and away slower, the transport in front flies out faster).
 */
@Stable
class StudioParts(
    val studio: PlayerStudioState,
    val anchors: StudioAnchors,
) {
    internal var width = 1f
    internal var height = 1f
    internal var statusTop = 0f
    internal var density: Density = Density(1f)
    internal var fullCover = false
    internal var barStyle = PlayerBottomBarStyle.STACK

    fun layout(): TileLayout = tileLayout(width, height, density, anchors, fullCover, barStyle)

    /** the middle part's pieces, lifted off the player while it's being edited */
    internal var pieces: StudioPieces? = null

    /** where holding a tile has moved the controls and how tall it's pulled the card */
    internal var tweaks: PlayerLayoutTweaks? = null

    /**
     * The middle part is a camera move over a still of the player (the editor draws it), so the
     * live parts don't move for it; only the cover editor moves them.
     */
    internal fun zoom(): Float = 0f

    /** where the middle part is brought to, as the camera closes in */
    private fun zoomTarget() = Offset(width / 2f, height * 0.5f)

    /**
     * the middle part as a whole: the camera only closes in a little, so it keeps its place and
     * comes slightly forward (and toward the middle). slid away for the cover editor
     */
    internal fun middleTransform(): PartTransform {
        val z = zoom()
        val g = anchors.middle
        val target = zoomTarget()
        val dx = (target.x - g.center.x) * z * 0.35f
        val dy = (target.y - g.center.y) * z * 0.35f + studio.cover.value * (height - g.top + offPx())
        return PartTransform(1f + 0.06f * z, dx, dy)
    }

    /** the cover, further back: it grows a touch less and drifts a little up */
    internal fun coverTransform(): PartTransform {
        val z = zoom()
        val u = studio.menu.value
        return PartTransform(1f + 0.035f * z + 0.025f * u, 0f, -height * (0.035f * z + 0.03f * u))
    }

    /** the transport, nearest the camera: it grows a touch more and drifts a little down */
    internal fun transportTransform(): PartTransform {
        val z = zoom()
        return PartTransform(1f + 0.05f * z, 0f, height * 0.03f * z + awayBelow(anchors.transport.value.top))
    }

    /** how far down a part whose top is at [top] slides to leave the screen for the cover editor */
    internal fun awayBelow(top: Float): Float = studio.cover.value * (height - top + offPx())

    private fun offPx() = with(density) { 60.dp.toPx() }

    /** where [rect] (a piece of the middle part) is drawn, the middle part moved as a whole about its centre */
    internal fun inMiddle(rect: Rect): Rect {
        val t = middleTransform()
        val g = anchors.middle
        val cx = g.center.x + t.dx + t.scale * (rect.center.x - g.center.x)
        val cy = g.center.y + t.dy + t.scale * (rect.center.y - g.center.y)
        val hw = rect.width * t.scale / 2f
        val hh = rect.height * t.scale / 2f
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }
}

@Composable
fun rememberStudioParts(studio: PlayerStudioState, anchors: StudioAnchors): StudioParts =
    remember(studio, anchors) { StudioParts(studio, anchors) }

/**
 * The layer on the cover art. In the tiles it stays put; in the cover editor it slides aside as
 * the styles are swiped through; for the middle part it drifts back with the camera.
 */
fun Modifier.studioCoverLayer(parts: StudioParts?): Modifier {
    if (parts == null) return this
    return graphicsLayer {
        val studio = parts.studio
        if (!studio.editing) return@graphicsLayer
        val t = parts.coverTransform()
        scaleX = t.scale
        scaleY = t.scale
        translationX = (studio.coverCommitted - studio.coverPage.value) * parts.width * studio.cover.value
        translationY = t.dy
    }
}

/** the layer on one of the middle part's two pieces (the title row, the seek bar) */
fun Modifier.studioMiddleLayer(parts: StudioParts?, slot: (StudioAnchors) -> MutableState<Rect>): Modifier {
    if (parts == null) return this
    return graphicsLayer {
        val studio = parts.studio
        if (!studio.editing) return@graphicsLayer
        val rect = slot(parts.anchors).value
        if (rect.width <= 0f) return@graphicsLayer
        val to = parts.inMiddle(rect)
        scaleX = to.width / rect.width
        scaleY = scaleX
        translationX = to.center.x - rect.center.x
        translationY = to.center.y - rect.center.y
    }
}

fun Modifier.studioTransportLayer(parts: StudioParts?): Modifier {
    if (parts == null) return this
    return graphicsLayer {
        if (!parts.studio.editing) return@graphicsLayer
        val t = parts.transportTransform()
        scaleX = t.scale
        scaleY = t.scale
        translationY = t.dy
    }
}

/** the bottom bar (either style): it stays put in the tiles and slides away with the transport for the cover editor */
fun Modifier.studioBarLayer(parts: StudioParts?, slot: (StudioAnchors) -> MutableState<Rect>): Modifier {
    if (parts == null) return this
    return graphicsLayer {
        if (!parts.studio.editing) return@graphicsLayer
        translationY = parts.awayBelow(slot(parts.anchors).value.top)
    }
}

/**
 * The tiles' outlines and their tap targets, drawn inside the player sheet so they shrink and grow
 * with it on the way between the selector and the editor. While the editor is up nothing else on
 * the player takes a touch.
 *
 * A tap opens a tile. Holding one picks its part up: the controls then follow the finger up and
 * down (the cover zooming with them), and the card is pulled taller or shorter.
 */
@Composable
fun EditTiles(parts: StudioParts, modifier: Modifier = Modifier) {
    val studio = parts.studio
    val editing by remember(studio) { derivedStateOf { studio.editing } }
    if (!editing) return
    val tweaks = parts.tweaks
    val haptics = LocalHapticFeedback.current
    val controlsHold = remember(tweaks, haptics) { tweaks?.let { TileHold(it, HeldPart.CONTROLS, haptics) } }
    val cardHold = remember(tweaks, haptics) { tweaks?.let { TileHold(it, HeldPart.CARD, haptics) } }
    val holdGlow = animateFloatAsState(
        targetValue = if (tweaks?.held != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "tileHold",
    )
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                },
        )
        // while the middle part's pieces are lifted the editor draws the tiles itself, over its still
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (parts.pieces?.lifted != true) {
                drawTiles(parts)
                drawHoldMarks(parts, holdGlow.value)
            }
        }
        if (studio.stage == StudioStage.EDIT) {
            val hasBar by remember(parts) { derivedStateOf { parts.layout().barTile != null } }
            TileTarget({ parts.layout().coverTile }) { studio.go(StudioStage.COVER) }
            TileTarget({ parts.layout().middleTile }, controlsHold) { studio.go(StudioStage.MIDDLE) }
            // the seek bar's tile is the handle; the buttons follow it on their own, so theirs only opens its sheet
            TileTarget({ parts.layout().controlsTile }) { studio.openSheet(StudioSheet.CONTROLS) }
            if (hasBar) TileTarget({ parts.layout().barTile ?: Rect.Zero }, cardHold) { studio.go(StudioStage.BAR) }
        }
    }
}

/**
 * What holding a tile does: picks its part up, moves it with the finger, and puts it down where
 * it's let go. A tick as it meets an end, and as the controls pass where they usually sit.
 */
private class TileHold(
    private val tweaks: PlayerLayoutTweaks,
    private val part: HeldPart,
    private val haptics: HapticFeedback,
) {
    private var stopped = false
    private var side = 0

    /** the part can be moved on this player right now */
    fun available(): Boolean = when (part) {
        HeldPart.CONTROLS -> tweaks.controlsMovable
        HeldPart.CARD -> tweaks.cardGrowable
    }

    fun start() {
        tweaks.hold(part)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        stopped = false
        side = side()
    }

    fun drag(dy: Float) {
        val atEnd = when (part) {
            HeldPart.CONTROLS -> tweaks.dragControls(dy)
            HeldPart.CARD -> tweaks.dragCard(dy)
        }
        if (atEnd && !stopped) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        stopped = atEnd
        val now = side()
        if (now != 0 && side != 0 && now != side) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (now != 0) side = now
    }

    fun end() = tweaks.hold(null)

    /** which side of their usual place the controls are on (0 right at it); the card has no middle to pass */
    private fun side(): Int {
        if (part != HeldPart.CONTROLS) return 0
        val offset = tweaks.controlsOffset()
        return if (offset > 0.5f) 1 else if (offset < -0.5f) -1 else 0
    }
}

private const val Tapped = 1
private const val Cancelled = 2

/**
 * A tile's touch target, at [rect] (read while placing, so it follows a part being dragged without
 * recomposing). A tap opens it; with a [hold], holding it picks its part up instead.
 */
@Composable
private fun TileTarget(rect: () -> Rect, hold: TileHold? = null, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val r = rect()
                val placeable = measurable.measure(
                    Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0))
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(r.left.roundToInt(), r.top.roundToInt())
                }
            }
            .pointerInput(hold) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val holds = hold != null && hold.available()
                    // let go first: a tap. moved away first: a swipe, nothing to do here. Without a
                    // hold to pick up, a long press is still a tap.
                    val early = withTimeoutOrNull(if (holds) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE) {
                        var outcome = 0
                        while (outcome == 0) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            outcome = when {
                                change == null || change.isConsumed -> Cancelled
                                !change.pressed -> Tapped
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> Cancelled
                                else -> 0
                            }
                        }
                        outcome
                    }
                    when {
                        early == Tapped -> currentOnClick()
                        early == null && hold != null -> {
                            hold.start()
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val dy = change.positionChange().y
                                    if (dy != 0f) {
                                        change.consume()
                                        hold.drag(dy)
                                    }
                                }
                            } finally {
                                hold.end()
                            }
                        }
                    }
                }
            },
    )
}

internal fun DrawScope.drawTiles(parts: StudioParts) {
    val studio = parts.studio
    val e = studio.edit.value
    if (e <= 0.01f) return
    val l = parts.layout()
    val c = studio.cover.value
    val m = studio.middle.value
    val b = studio.bar.value
    val ink = PartTileInk
    val stroke = ink.stroke.toPx()
    val corner = TileCorner.toPx()
    val fade = (1f - c) * (1f - ease(m / 0.4f)) * (1f - ease(b / 0.4f))
    val a = e * fade
    if (a <= 0.01f) return

    // the cover's tile always fades out toward its bottom; the full-bleed one has no top edge at all
    val r = l.coverTile
    val brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = ink.top * a),
        0.62f to Color.White.copy(alpha = ink.bottom * 0.9f * a),
        1f to Color.Transparent,
        startY = r.top,
        endY = r.bottom,
    )
    if (l.fullCover) {
        val path = Path().apply {
            moveTo(r.left, r.top)
            lineTo(r.left, r.bottom)
            moveTo(r.right, r.top)
            lineTo(r.right, r.bottom)
        }
        drawPath(path, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))
    } else {
        drawRoundRect(brush, r.topLeft, r.size, CornerRadius(corner), style = Stroke(width = stroke))
    }
    drawTileOutline(l.middleTile, a, ink)
    drawTileOutline(l.controlsTile, a, ink)
    l.barTile?.let { drawTileOutline(it, a, ink) }
}

/** the outline laid over a held tile's, so the part a finger has picked up stands out */
private val HeldTileInk = TileInk(stroke = 5.dp, top = 1f, bottom = 0.85f)

private val ChevronWidth = 16.dp
private val ChevronHeight = 6.dp
private val ChevronGap = 16.dp

/**
 * While a part is held: its tiles brighten, and chevrons say which way it can still go (dimmed at
 * an end). The controls get them above and below the whole block, the card above and inside its top.
 */
private fun DrawScope.drawHoldMarks(parts: StudioParts, glow: Float) {
    val tweaks = parts.tweaks ?: return
    val a = glow * parts.studio.edit.value
    if (a <= 0.01f) return
    val l = parts.layout()
    val gap = ChevronGap.toPx()
    fun dim(can: Boolean) = if (can) a else a * 0.35f
    when (tweaks.lastHeld) {
        HeldPart.CONTROLS -> {
            drawTileOutline(l.middleTile, a * 0.5f, HeldTileInk)
            drawTileOutline(l.controlsTile, a * 0.5f, HeldTileInk)
            // both on the seek bar's tile, the part the finger moves: above it, and inside its bottom between the two times
            drawChevron(Offset(l.middleTile.center.x, l.middleTile.top - gap), up = true, alpha = dim(tweaks.canMoveControls(up = true)))
            drawChevron(Offset(l.middleTile.center.x, l.middleTile.bottom - gap), up = false, alpha = dim(tweaks.canMoveControls(up = false)))
        }
        HeldPart.CARD -> l.barTile?.let { bar ->
            drawTileOutline(bar, a * 0.5f, HeldTileInk)
            drawChevron(Offset(bar.center.x, bar.top - gap), up = true, alpha = dim(tweaks.canGrowCard(taller = true)))
            drawChevron(Offset(bar.center.x, bar.top + gap), up = false, alpha = dim(tweaks.canGrowCard(taller = false)))
        }
        null -> Unit
    }
}

/** a small open chevron centred on [center], pointing up or down */
private fun DrawScope.drawChevron(center: Offset, up: Boolean, alpha: Float) {
    if (alpha <= 0.01f) return
    val half = ChevronWidth.toPx() / 2f
    val rise = ChevronHeight.toPx() / 2f
    val tip = if (up) -rise else rise
    val path = Path().apply {
        moveTo(center.x - half, center.y - tip)
        lineTo(center.x, center.y + tip)
        lineTo(center.x + half, center.y - tip)
    }
    drawPath(
        path,
        Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** how a tile's outline is drawn: its width, and how bright it is along its top and its bottom */
internal class TileInk(val stroke: Dp, val top: Float, val bottom: Float)

/** the tiles round the player's own parts, on the editor's first page: bold enough to read over any cover */
internal val PartTileInk = TileInk(stroke = 5.dp, top = 0.82f, bottom = 0.58f)

/** the tiles inside a part's own page */
internal val PageTileInk = TileInk(stroke = TileStroke, top = 0.6f, bottom = 0.38f)

/** a tile's outline: bold, a touch brighter along the top */
internal fun DrawScope.drawTileOutline(rect: Rect, alpha: Float, ink: TileInk = PageTileInk) {
    if (alpha <= 0.01f) return
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = ink.top * alpha),
            1f to Color.White.copy(alpha = ink.bottom * alpha),
            startY = rect.top,
            endY = rect.bottom,
        ),
        rect.topLeft,
        rect.size,
        CornerRadius(TileCorner.toPx().coerceAtMost(rect.height / 2f)),
        style = Stroke(width = ink.stroke.toPx()),
    )
}

internal fun lerpRect(a: Rect, b: Rect, t: Float): Rect = Rect(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t,
    a.bottom + (b.bottom - a.bottom) * t,
)
