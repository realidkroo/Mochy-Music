package com.mochyy.mochymusic.ui.player.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.ui.player.styles.AlternatePlayerStyle
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.stableStatusBars

// a card a row above the one in view is gone: it fades as it slides up out of the way
private fun rowLeaving(row: Int, rowPosition: Float): Float = (1f - (rowPosition - row)).coerceIn(0f, 1f)

// the live card's own variants fade as the cards grow back into the player, or they'd slide down across it
private fun variantFade(column: Int, row: Int, studio: PlayerStudioState, e: Float): Float =
    if (column == studio.liveColumn && row != studio.liveRow) ease((e - 0.35f) / 0.5f) else 1f

/** the selector's geometry at progress [e] (0 the full player, 1 cards) on a [w] x [h] screen */
internal class CardGeometry(val e: Float, val w: Float, val h: Float, gapPx: Float) {
    val scale = 1f + (CardScale - 1f) * e
    val stride = w * scale + gapPx
    val centerY = h / 2f + (CardCenterY * h - h / 2f) * e
    val cardW = w * scale
    val cardH = h * scale
    // a column's variants sit one card and a gap apart, top to bottom
    val strideY = h * scale + gapPx
    fun centerX(page: Int, position: Float) = w / 2f + (page - position) * stride
    fun centerY(row: Int, rowPosition: Float) = centerY + (row - rowPosition) * strideY
    fun visible(page: Int, position: Float) = abs(centerX(page, position) - w / 2f) < w / 2f + cardW / 2f
    fun visibleY(row: Int, rowPosition: Float) = abs(centerY(row, rowPosition) - h / 2f) < h / 2f + cardH / 2f
}

/**
 * Applies the selector card transform to the live player sheet: the player shrinks into the card
 * of the style it's showing, which follows the selector's scroll. The backgrounds box (the cover
 * editor scrolled down) shrinks it the same way, into a box higher up.
 */
fun Modifier.studioSheetLayer(studio: PlayerStudioState): Modifier = graphicsLayer {
    // under the middle part's still, nothing of the live player needs drawing
    if (studio.sheetCovered) {
        alpha = 0f
        return@graphicsLayer
    }
    val e = studio.selector.value
    val b = studio.backdrop.value * studio.cover.value
    if (e <= 0.0005f && b <= 0.0005f) return@graphicsLayer
    val w = size.width
    val h = size.height
    val gap = CardGap.toPx()
    var scale = 1f
    var tx = 0f
    var ty = 0f
    var corner = 0f
    if (e > 0.0005f) {
        val g = CardGeometry(e, w, h, gap)
        scale = g.scale
        val column = studio.liveColumn
        val rowPosition = studio.rows(column).value
        tx = g.centerX(column, studio.page.value) - w / 2f
        ty = g.centerY(studio.liveRow, rowPosition) - h / 2f
        // scrolled past it, it fades away upward instead of hanging behind the header
        val leaving = rowLeaving(studio.liveRow, rowPosition)
        if (leaving < 1f) alpha = leaving
        corner = CardCorner.toPx() * e.coerceIn(0f, 1f)
    }
    if (b > 0.0005f) {
        val s = 1f + (BoxScale - 1f) * b
        scale *= s
        // the live box sits where the applied background is among the boxes being swiped through
        tx += (studio.backdropCommitted - studio.backdropPage.value) * (w * s + gap)
        ty += (BoxCenterY * h - h / 2f) * b
        corner = maxOf(corner, BoxCorner.toPx() * b.coerceIn(0f, 1f))
    }
    scaleX = scale
    scaleY = scale
    translationX = tx
    translationY = ty
    // the shape is in the layer's own (unscaled) units
    shape = RoundedCornerShape(corner.coerceAtLeast(0f) / scale.coerceAtLeast(0.01f))
    clip = true
}

// the player selector: every style as a card, the live player one of them, the flagship's older layouts stacked under it
@Composable
fun PlayerSelectorOverlay(
    studio: PlayerStudioState,
    styles: List<PlayerStyle>,
    // a style index, and how fast the cards were sinking when a swipe down set it
    onSet: (Int, Float) -> Unit,
    /** the editor for the focused style (its index) */
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by remember(studio) { derivedStateOf { studio.selector.value > 0.0005f || studio.stage == StudioStage.SELECTOR } }
    if (!shown) return
    val active = studio.stage == StudioStage.SELECTOR
    if (active) BackHandler { studio.back() }

    val density = LocalDensity.current
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val previewActions = remember { StyleActions(onOtherMenu = {}, onEditPlayer = {}) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnSet by rememberUpdatedState(onSet)
    val columnCount = if (studio.columns.isEmpty()) styles.size else studio.columns.size
    val lastColumn = columnCount - 1
    val focusedColumn by remember(studio, columnCount) {
        derivedStateOf { studio.page.value.roundToInt().coerceIn(0, lastColumn) }
    }
    val focused by remember(studio, columnCount) {
        derivedStateOf {
            val column = focusedColumn
            val row = studio.rows(column).value.roundToInt().coerceIn(0, studio.rowCount(column) - 1)
            studio.styleAt(column, row).coerceIn(0, styles.lastIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val gapPx = with(density) { CardGap.toPx() }
        fun geometry() = CardGeometry(studio.selector.value, w, h, gapPx)
        // where the cards sit once the selector is up: the dots and the buttons hang under them
        val restingBottomPx = CardGeometry(1f, w, h, gapPx).let { it.centerY + it.cardH / 2f }
        // the band the cards own for taps: under the header, above the dots and the buttons
        val cardsTopPx = with(density) { (statusTop + 50.dp).toPx() }
        val cardsBottomPx = restingBottomPx + with(density) { 18.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!active) Modifier else Modifier.pointerInput(columnCount, w, h, cardsTopPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPointerInputChange(down)
                            val column = focusedColumn
                            val rows = studio.rows(column)
                            // a touch while the cards still glide catches them, and isn't a tap on whatever was passing under it
                            val caught = studio.page.settling || rows.settling
                            studio.page.stop()
                            rows.stop()
                            val startPage = studio.page.value
                            val startRow = rows.value
                            val lastRow = studio.rowCount(column) - 1
                            var dx = 0f
                            var dy = 0f
                            var mode = 0 // 0 undecided, 1 across the columns, 2 up and down
                            var released = false
                            val resting = CardGeometry(1f, w, h, gapPx)
                            val sinkRange = h * 0.9f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                val delta = change.positionChangeIgnoreConsumed()
                                dx += delta.x
                                dy += delta.y
                                tracker.addPointerInputChange(change)
                                if (mode == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                    mode = if (abs(dx) >= abs(dy)) 1 else 2
                                }
                                if (mode == 1) {
                                    change.consume()
                                    // past the ends it resists
                                    val raw = startPage - dx / resting.stride
                                    studio.page.value = when {
                                        raw < 0f -> raw * 0.3f
                                        raw > lastColumn -> lastColumn + (raw - lastColumn) * 0.3f
                                        else -> raw
                                    }
                                } else if (mode == 2) {
                                    change.consume()
                                    // the variants follow the finger; pulled down past the top one, the cards sink back toward the player
                                    val raw = startRow - dy / resting.strideY
                                    if (raw < 0f) {
                                        rows.value = 0f
                                        studio.selector.value = (1f + raw * resting.strideY / sinkRange).coerceIn(0f, 1f)
                                    } else {
                                        studio.selector.value = 1f
                                        rows.value = if (raw > lastRow) lastRow + (raw - lastRow) * 0.3f else raw
                                    }
                                }
                            }
                            val velocity = tracker.calculateVelocity()
                            // a column's rows can be moved while the page is caught between two columns, so it lands on one after
                            fun settlePage() {
                                if (abs(studio.page.value - column) > 0.0005f) studio.page.animateTo(column.toFloat())
                            }
                            when (mode) {
                                0 -> if (released) {
                                    if (caught) {
                                        studio.page.animateTo(studio.page.value.roundToInt().coerceIn(0, lastColumn).toFloat())
                                        rows.animateTo(rows.value.roundToInt().coerceIn(0, lastRow).toFloat())
                                        return@awaitEachGesture
                                    }
                                    val tap = down.position
                                    // the header, the dots and the buttons take their own taps, the cards behind them never see one
                                    if (tap.y < cardsTopPx || tap.y > cardsBottomPx) return@awaitEachGesture
                                    val position = studio.page.value
                                    val tappedColumn = (position + (tap.x - w / 2f) / resting.stride).roundToInt()
                                    if (tappedColumn !in 0..lastColumn) return@awaitEachGesture
                                    val rowPosition = studio.rows(tappedColumn).value
                                    val tappedRow = (rowPosition + (tap.y - resting.centerY) / resting.strideY).roundToInt()
                                    if (tappedRow !in 0 until studio.rowCount(tappedColumn)) return@awaitEachGesture
                                    val onCard = abs(tap.x - resting.centerX(tappedColumn, position)) < resting.cardW / 2f &&
                                        abs(tap.y - resting.centerY(tappedRow, rowPosition)) < resting.cardH / 2f
                                    // a card slid up out of the way has faded out, there's nothing there to tap
                                    if (!onCard || rowLeaving(tappedRow, rowPosition) < 0.5f) return@awaitEachGesture
                                    when {
                                        // a card at the side comes to the middle first, so a style never goes on by accident
                                        tappedColumn != focusedColumn -> studio.page.animateTo(tappedColumn.toFloat())
                                        // the variant peeking in comes up
                                        tappedRow != rowPosition.roundToInt() -> studio.rows(tappedColumn).animateTo(tappedRow.toFloat())
                                        // the card in view puts that style on: the player grows out of it
                                        else -> currentOnSet(studio.styleAt(tappedColumn, tappedRow), 0f)
                                    }
                                }
                                1 -> {
                                    val flick = (-velocity.x / resting.stride).coerceIn(-3f, 3f)
                                    val target = (studio.page.value + flick * 0.3f).roundToInt().coerceIn(0, lastColumn)
                                    studio.page.animateTo(target.toFloat(), velocity = flick)
                                }
                                2 -> if (studio.selector.value < 0.999f) {
                                    // the cards follow the finger down, so they keep its speed whichever way they go
                                    val sinkVelocity = -velocity.y / sinkRange
                                    val pulled = (1f - studio.selector.value) * sinkRange
                                    if (pulled > 120.dp.toPx() || velocity.y > 1_400f) {
                                        currentOnSet(studio.styleAt(column, 0), sinkVelocity)
                                    } else {
                                        studio.selector.animateTo(1f, velocity = sinkVelocity)
                                        settlePage()
                                    }
                                } else {
                                    // a quick flick moves on a variant, a slow one settles on the nearest; either way it keeps the finger's speed
                                    val rowVelocity = (-velocity.y / resting.strideY).coerceIn(-4f, 4f)
                                    val position = rows.value
                                    val target = when {
                                        rowVelocity > 0.35f -> maxOf(ceil(position), (position + rowVelocity * 0.25f).roundToInt().toFloat())
                                        rowVelocity < -0.35f -> minOf(floor(position), (position + rowVelocity * 0.25f).roundToInt().toFloat())
                                        else -> position.roundToInt().toFloat()
                                    }.coerceIn(0f, lastRow.toFloat())
                                    rows.animateTo(target, velocity = rowVelocity)
                                    settlePage()
                                }
                            }
                        }
                    }
                ),
        ) {
            // every style, as itself
            styles.forEachIndexed { index, style ->
                key(style) {
                    val column = studio.columnOf(index)
                    val row = studio.rowOf(index)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val g = geometry()
                                val position = studio.page.value
                                val rowPosition = studio.rows(column).value
                                // the live card is the player itself, except while a newly picked
                                // style is being built behind it: then its card covers it, and
                                // fades into it as it grows
                                val live = index == studio.livePage
                                val leaving = rowLeaving(row, rowPosition) * variantFade(column, row, studio, g.e)
                                val visible = g.visible(column, position) && g.visibleY(row, rowPosition) && leaving > 0f &&
                                    (!live || studio.handoff.value > 0.001f)
                                alpha = if (!visible) 0f else if (live) studio.handoff.value.coerceIn(0f, 1f) * leaving else leaving
                                scaleX = g.scale
                                scaleY = g.scale
                                translationX = g.centerX(column, position) - w / 2f
                                translationY = g.centerY(row, rowPosition) - h / 2f
                                // springs settle a hair past their ends: a corner must never go below zero
                                shape = RoundedCornerShape(CardCorner.toPx() * g.e.coerceIn(0f, 1f) / g.scale.coerceAtLeast(0.01f))
                                clip = true
                            }
                            .background(Color.Black),
                    ) {
                        // every card built, the live one too: picking a style hands over to its
                        // card, which mustn't be built in the frame it has to cover the player
                        when (style) {
                            PlayerStyle.FLAGSHIP -> FlagshipPreview(flagship = true)
                            PlayerStyle.CLASSIC -> FlagshipPreview()
                            PlayerStyle.TURNTABLE -> FlagshipPreview(turntable = true)
                            else -> AlternatePlayerStyle(style = style, actions = previewActions, preview = true, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // a hairline round every card, the live one included, and the focused column's variant dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val g = geometry()
                if (g.e < 0.02f) return@Canvas
                val e = g.e.coerceIn(0f, 1f)
                val stroke = 1.dp.toPx()
                val radius = CardCorner.toPx() * e
                val position = studio.page.value
                styles.indices.forEach { index ->
                    val column = studio.columnOf(index)
                    val row = studio.rowOf(index)
                    val rowPosition = studio.rows(column).value
                    if (!g.visible(column, position) || !g.visibleY(row, rowPosition)) return@forEach
                    val cx = g.centerX(column, position)
                    val cy = g.centerY(row, rowPosition)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f * e * rowLeaving(row, rowPosition) * variantFade(column, row, studio, e)),
                        topLeft = Offset(cx - g.cardW / 2f + stroke / 2f, cy - g.cardH / 2f + stroke / 2f),
                        size = Size(g.cardW - stroke, g.cardH - stroke),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(stroke),
                    )
                }
                val column = focusedColumn
                val count = studio.rowCount(column)
                if (count > 1) {
                    // they fade as a swipe leaves the column, and the one in view is white
                    val settled = (1f - abs(position - column) * 2f).coerceIn(0f, 1f)
                    val dot = 8.dp.toPx()
                    val step = 17.dp.toPx()
                    val x = (w - g.cardW) / 4f
                    // centred on the card's height, beside the card in view
                    val top = g.centerY - (count - 1) * step / 2f
                    val rowPosition = studio.rows(column).value.coerceIn(0f, (count - 1).toFloat())
                    repeat(count) { i ->
                        val near = (1f - abs(rowPosition - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = (0.32f + 0.6f * near) * settled * e),
                            radius = dot / 2f,
                            center = Offset(x, top + i * step),
                        )
                    }
                }
            }

            // the variant peeking in below fades into the dark under the buttons, so they always read
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val from = restingBottomPx + 2.dp.toPx()
                        val scrim = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                            startY = from,
                            endY = from + 64.dp.toPx(),
                        )
                        onDrawBehind {
                            val e = studio.selector.value.coerceIn(0f, 1f)
                            if (e > 0.01f) drawRect(scrim, topLeft = Offset(0f, from), alpha = e)
                        }
                    },
            )

            // header: back, and "Styles" with the style in view under it, sliding down from above
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 10.dp, start = 22.dp, end = 22.dp)
                    .height(40.dp)
                    .graphicsLayer {
                        val e = studio.selector.value.coerceIn(0f, 1f)
                        translationY = -(1f - e) * (statusTop.toPx() + 70.dp.toPx())
                        alpha = e
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = active) { studio.back() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_ios),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp).offset(x = 2.dp),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Styles", color = Color.White, style = studioText(StudioType.Title, FontWeight.Bold))
                    AnimatedContent(
                        targetState = styles.getOrNull(focused)?.selectorName.orEmpty(),
                        transitionSpec = {
                            (slideInVertically(tween(320)) { it } + fadeIn(tween(240))) togetherWith
                                (slideOutVertically(tween(280)) { -it } + fadeOut(tween(180)))
                        },
                        label = "selectorStyleName",
                    ) { name ->
                        Text(name, color = Color.White.copy(alpha = 0.6f), style = studioText(StudioType.Subtitle, FontWeight.Medium))
                    }
                }
            }

            // under the cards: the page dots and the buttons for the card in view
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            p.placeWithLayer(0, (restingBottomPx + 18.dp.toPx()).roundToInt()) {
                                val e = studio.selector.value.coerceIn(0f, 1f)
                                translationY = (1f - e) * 160.dp.toPx()
                                alpha = e
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.size(width = (columnCount * 11).dp, height = 5.dp)) {
                    val dot = 5.dp.toPx()
                    val step = 11.dp.toPx()
                    val position = studio.page.value.coerceIn(0f, lastColumn.toFloat())
                    val start = (size.width - (columnCount - 1) * step - dot) / 2f
                    repeat(columnCount) { i ->
                        // the dot in view is white, its neighbours fade in as a swipe reaches them
                        val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.32f + 0.68f * near),
                            radius = dot / 2f,
                            center = Offset(start + i * step + dot / 2f, size.height / 2f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                val style = styles.getOrNull(focused)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // edit stays in its place, only dimmed where there's nothing to edit, so set never moves under the finger
                    val editable = style?.editable == true
                    CardButton("Edit", enabled = active && editable, dimmed = !editable, onClick = { onEdit(focused) }, modifier = Modifier.weight(1f))
                    CardButton("Set", enabled = active, onClick = { currentOnSet(focused, 0f) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CardButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (dimmed) 0.06f else 0.15f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White.copy(alpha = if (dimmed) 0.35f else 1f), style = studioText(StudioType.Button))
    }
}
