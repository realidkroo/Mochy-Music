package com.mochyy.mochymusic.ui.player.bedside

import android.content.Context
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

private val DotSize = 6.dp
private val DotGap = 8.dp
// how far a pull down past a tile's first page (or a drag off a button) goes before the player closes
internal val PullClose = 28.dp
private val FlingClose = 900.dp
// past a page's own slop and a 45 degree slant, but short of the open sheet's, which would take the drag otherwise
private const val StraySlopScale = 1.6f
// a page is saved once the swiping has stopped this long, so a quick run through them doesn't bounce back
private const val PageSaveDelayMs = 600L
// a fifth of a page turns it: the default wants half, which sideways is most of the screen's long side
private const val PageTurnShare = 0.2f

// a short drag or a light flick turns the page, settling quick and without a bounce
@Composable
internal fun bedsidePagerFling(state: PagerState): TargetedFlingBehavior = PagerDefaults.flingBehavior(
    state = state,
    snapPositionalThreshold = PageTurnShare,
    snapAnimationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
)

// keeps a pager on the page it was left on: saved once it settles, and followed when changed from somewhere else
@Composable
internal fun RememberPage(pager: PagerState, saved: Int, onSave: (Int) -> Unit) {
    LaunchedEffect(saved) {
        val target = saved.coerceIn(0, pager.pageCount - 1)
        if (pager.settledPage != target && !pager.isScrollInProgress) pager.scrollToPage(target)
    }
    val currentOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collectLatest { page ->
            delay(PageSaveDelayMs)
            currentOnSave(page)
        }
    }
}

// a few small dots, the one in view bright, only out while someone's touching
@Composable
internal fun PageDots(count: Int, position: () -> Float, vertical: Boolean, rest: BedsideRest, modifier: Modifier) {
    val length = DotSize * count + DotGap * (count - 1)
    Spacer(
        modifier
            .size(if (vertical) DpSize(DotSize, length) else DpSize(length, DotSize))
            .drawBehind {
                val shown = rest.dots.value
                if (shown <= 0.01f) return@drawBehind
                val radius = DotSize.toPx() / 2f
                val step = DotSize.toPx() + DotGap.toPx()
                val at = position()
                for (i in 0 until count) {
                    val near = (1f - abs(at - i)).coerceIn(0f, 1f)
                    val along = radius + i * step
                    drawCircle(
                        color = Color.White.copy(alpha = shown * (0.3f + 0.7f * near)),
                        radius = radius,
                        center = if (vertical) Offset(radius, along) else Offset(along, radius),
                    )
                }
            },
    )
}

// the little bar that closes the player when tapped, a lock while the phone is locked; it fades with the chrome but always takes the first tap
@Composable
internal fun BedsideHandle(locked: Boolean, rest: BedsideRest, onClose: () -> Unit, modifier: Modifier) {
    val label = stringResource(if (locked) R.string.bedside_unlock_to_close else R.string.bedside_close)
    Box(
        modifier = modifier
            .size(72.dp, 36.dp)
            .bedsideChrome(rest)
            .semantics { contentDescription = label }
            .pointerInput(onClose) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Icon(painterResource(R.drawable.lock), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        } else {
            Spacer(
                Modifier
                    .size(40.dp, 4.dp)
                    .drawBehind { drawRoundRect(Color.White.copy(alpha = 0.45f), size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height / 2f)) },
            )
        }
    }
}

// a double tap plays or pauses and a hold opens the editor
internal fun Modifier.bedsideTaps(interactive: () -> Boolean, onToggle: () -> Unit, onEdit: () -> Unit): Modifier =
    pointerInput(interactive, onToggle, onEdit) {
        whileStyleInteractive(interactive) {
            detectTapGestures(onDoubleTap = { onToggle() }, onLongPress = { onEdit() })
        }
    }

// a drag nobody under it took (one that began on a button) is still the bedside's and closes it pulled down; it waits past the pages' slop and gives way to them, so a slanted swipe still turns a page
internal fun Modifier.catchStrayDrags(interactive: () -> Boolean, onClose: () -> Unit): Modifier =
    pointerInput(interactive, onClose) {
        val closePx = PullClose.toPx()
        whileStyleInteractive(interactive) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop * StraySlopScale
                var total = Offset.Zero
                var claimed = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (!claimed && change.isConsumed)) break
                    total += change.positionChange()
                    if (!claimed && total.getDistance() > slop) claimed = true
                    if (claimed) change.consume()
                }
                if (claimed && total.y > closePx && total.y > abs(total.x)) onClose()
            }
        }
    }

// pulling down past a pager's first page closes the player; kept here rather than left to the sheet so over the lock screen it can ask for an unlock first
internal class PullToClose(
    private val pager: PagerState,
    private val onClose: () -> Unit,
    private val closePx: Float,
    private val flingPx: Float,
) : NestedScrollConnection {
    private var fresh = true
    private var startedAtTop = false
    private var pulled = 0f
    private var closed = false

    private fun atTop() = pager.currentPage == 0 && abs(pager.currentPageOffsetFraction) < 0.01f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // only a pull that began on the first page closes, not one that scrolled back to it
        if (fresh && source == NestedScrollSource.UserInput) {
            fresh = false
            startedAtTop = atTop()
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !startedAtTop || available.y <= 0f) return Offset.Zero
        pulled += available.y
        if (!closed && pulled > closePx) {
            closed = true
            onClose()
        }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!closed && startedAtTop && pulled > 0f && available.y > flingPx) {
            closed = true
            onClose()
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        fresh = true
        pulled = 0f
        closed = false
        return Velocity.Zero
    }
}

@Composable
internal fun rememberPullToClose(pager: PagerState, onClose: () -> Unit): PullToClose {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.runtime.remember(pager, onClose, density) {
        with(density) { PullToClose(pager, onClose, PullClose.toPx(), FlingClose.toPx()) }
    }
}

// over the lock screen, everything in the menu that leaves the bedside asks for an unlock first
internal fun StyleActions.unlockedFirst(context: Context) = StyleActions(
    onOtherMenu = { context.unlockThen(onOtherMenu) },
    onEditPlayer = { context.unlockThen(onEditPlayer) },
    onLyrics = onLyrics?.let { open -> { context.unlockThen(open) } },
    onExitVideoMode = onExitVideoMode,
    onOpenArtist = { id -> context.unlockThen { onOpenArtist(id) } },
    onOpenAlbum = { id -> context.unlockThen { onOpenAlbum(id) } },
)

// the system bars as they fall on the bedside: along the top and bottom upright, down the sides once it's turned
internal class BedsideInsets(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

@Composable
internal fun bedsideInsets(landscape: Boolean): BedsideInsets {
    val status = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (landscape) BedsideInsets(start = status, top = 0.dp, end = nav, bottom = 0.dp)
    else BedsideInsets(start = 0.dp, top = status, end = 0.dp, bottom = nav)
}

// a card in the style picker shows the bedside, it doesn't run it
internal val SwallowTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
