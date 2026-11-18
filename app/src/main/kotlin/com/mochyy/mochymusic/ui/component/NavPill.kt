// the lit tab's pill: one shape that stretches from tab to tab, lifts while a finger is on the bar and follows it across, drawn by mochy or by a look's glass

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.utils.smoothFrames
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// the pill sits this far in from the bar's top, bottom and both ends
private val NavHighlightInset = 8.dp

// room between a pill and where its neighbour's would be
private val NavHighlightGap = 4.dp

// half the gap is the row's padding and half is inside each slot, so the pill sits as far from the ends as from the top and bottom
internal val NavRowPadding = NavHighlightInset - NavHighlightGap / 2

// the front edge springs to the new tab faster than the back one, so it stretches toward the tab and settles into it instead of sliding over as a block
private val NavHighlightLead = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val NavHighlightTrail = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

// rising under a finger overshoots a touch, like a drop being picked up; settling back is softer
private val PillRise = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)
private val PillSettle = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

// after a plain tap the tab's own click picks the page; if nothing changes by then, the pill goes back to the lit tab
private const val TapSettleMs = 450L

/** what a look's glass hears about the pill, in the bar's own pixels, read while drawing */
class ChromePill(val rect: Rect, val shown: Float, val lift: Float)

/** where the pill is, in tabs: its back and front edges, how shown it is, and how lifted by a finger */
@Stable
internal class NavPillState(initial: Int) {
    val start = Animatable(initial.coerceAtLeast(0).toFloat())
    val end = Animatable(initial.coerceAtLeast(0).toFloat())
    val shown = Animatable(if (initial >= 0) 1f else 0f)
    val lift = Animatable(0f)

    /** the pill in the tab row's own pixels, null while hidden */
    fun rect(rowSize: Size, tabCount: Int, density: Density): Rect? {
        if (tabCount == 0 || shown.value <= 0f) return null
        val slot = rowSize.width / tabCount
        val halfGap = with(density) { NavHighlightGap.toPx() } / 2f
        val inset = with(density) { NavHighlightInset.toPx() }
        return Rect(start.value * slot + halfGap, inset, (end.value + 1f) * slot - halfGap, rowSize.height - inset)
    }

    /** the pill as a bar's glass sees it, for a row sitting [rowPadding] in from both of the bar's ends */
    fun forGlass(barSize: Size, rowPadding: Float, tabCount: Int, density: Density): ChromePill? {
        val rect = rect(Size(barSize.width - rowPadding * 2f, barSize.height), tabCount, density) ?: return null
        return ChromePill(rect.translate(rowPadding, 0f), shown.value, lift.value)
    }

    // the new tab's page is built on the frame the pill is moving, so its springs run on capped frames and glide through that instead of jumping
    suspend fun moveTo(target: Float) = smoothFrames {
        val movingRight = target > (start.value + end.value) / 2f
        launch { start.animateTo(target, if (movingRight) NavHighlightTrail else NavHighlightLead) }
        end.animateTo(target, if (movingRight) NavHighlightLead else NavHighlightTrail)
    }
}

/** the pill for a bar, following [selectedIndex]: it fades out when no tab is lit and back in where it belongs */
@Composable
internal fun rememberNavPill(selectedIndex: Int): NavPillState {
    val pill = remember { NavPillState(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) {
            smoothFrames { pill.shown.animateTo(0f, tween(200)) }
            return@LaunchedEffect
        }
        val target = selectedIndex.toFloat()
        // back from no lit tab, it fades in where it belongs rather than sliding over from the old one
        if (pill.shown.value == 0f) {
            pill.start.snapTo(target)
            pill.end.snapTo(target)
        }
        launch { smoothFrames { pill.shown.animateTo(1f, tween(200)) } }
        pill.moveTo(target)
    }
    return pill
}

/** mochy's own pill behind the lit tab, left out while [drawn] says the look's glass draws it; only read while drawing, so it never recomposes the bar */
internal fun Modifier.navHighlight(pill: NavPillState, tabCount: Int, drawn: () -> Boolean = { true }): Modifier = drawBehind {
    if (!drawn()) return@drawBehind
    val rect = pill.rect(size, tabCount, this) ?: return@drawBehind
    val lift = pill.lift.value
    // lifted, it swells a little past its slot and brightens, as if it rose off the bar
    val grow = lift * 3.dp.toPx()
    drawRoundRect(
        color = ChromeHighlightColor.copy(alpha = ChromeHighlightColor.alpha * (1f + 0.7f * lift)),
        topLeft = Offset(rect.left - grow, rect.top - grow),
        size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
        cornerRadius = CornerRadius(rect.height / 2f + grow),
        alpha = pill.shown.value,
    )
}

// a finger on the bar lifts the pill under it; a sideways drag takes over from the tabs and picks the tab it ends on, a plain tap stays the tab's own
private fun Modifier.navPillGesture(pill: NavPillState, tabCount: Int, selectedIndex: () -> Int, onPick: (Int) -> Unit): Modifier =
    pointerInput(pill, tabCount) {
        if (tabCount == 0) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slot = size.width.toFloat() / tabCount
                fun tabAt(x: Float) = (x / slot - 0.5f).coerceIn(0f, tabCount - 1f)
                launch { smoothFrames { pill.lift.animateTo(1f, PillRise) } }
                launch { pill.moveTo(tabAt(down.position.x)) }
                var dragging = false
                var travel = 0f
                var x = down.position.x
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    travel += change.positionChange().x
                    x = change.position.x
                    if (!dragging && abs(travel) > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        // taking the moves here cancels the tab's own press, so a drag never also taps the tab it started on
                        change.consume()
                        launch { pill.moveTo(tabAt(x)) }
                    }
                }
                launch { smoothFrames { pill.lift.animateTo(0f, PillSettle) } }
                val target = tabAt(x).roundToInt()
                launch { pill.moveTo(target.toFloat()) }
                if (dragging) {
                    onPick(target)
                } else {
                    launch {
                        delay(TapSettleMs)
                        val lit = selectedIndex()
                        if (lit >= 0 && lit != target) pill.moveTo(lit.toFloat())
                    }
                }
            }
        }
    }

/** lifts the pill under a finger and lets it be dragged across the tabs, picking the one it's let go on with [onPick] */
@Composable
internal fun Modifier.navPillDrag(pill: NavPillState, tabCount: Int, selectedIndex: Int, onPick: (Int) -> Unit): Modifier {
    val lit by rememberUpdatedState(selectedIndex)
    val pick by rememberUpdatedState(onPick)
    return navPillGesture(pill, tabCount, { lit }) { pick(it) }
}
