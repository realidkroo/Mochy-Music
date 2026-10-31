package com.mochyy.mochymusic.ui.player.bedside

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BedsideClockFace
import com.mochyy.mochymusic.constants.BedsideClockFaceKey
import com.mochyy.mochymusic.constants.BedsideKeepAwakeKey
import com.mochyy.mochymusic.constants.BedsideLockScreenKey
import com.mochyy.mochymusic.constants.BedsideNightTint
import com.mochyy.mochymusic.constants.BedsideNightTintKey
import com.mochyy.mochymusic.constants.BedsideScreenSaverKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.RotatingFrame
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.StyleCircle
import com.mochyy.mochymusic.ui.player.styles.StyleMenu
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.rememberStyleRotation
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTones
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

private val ButtonSize = 40.dp
private val EdgeGap = 18.dp
private val DotSize = 6.dp
private val DotGap = 8.dp
// how far a pull down past the clock (or a drag off a button) goes before the player closes
private val PullClose = 28.dp
private val FlingClose = 900.dp
// past a page's own slop and a 45 degree slant, but short of the open sheet's, which would take the drag otherwise
private const val StraySlopScale = 1.6f
// a face is saved once the swiping has stopped this long, so a quick run through them doesn't bounce back
private const val FaceSaveDelayMs = 600L

// a quiet clock for the nightstand: the time first, the song one swipe up, the night look settling in once it's left alone
@Composable
fun BedsidePlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val live = !preview
    val (face, onFace) = rememberEnumPreference(BedsideClockFaceKey, BedsideClockFace.BIG)
    val tintChoice by rememberEnumPreference(BedsideNightTintKey, BedsideNightTint.ORANGE)
    val saver by rememberPreference(BedsideScreenSaverKey, defaultValue = true)
    val keepAwake by rememberPreference(BedsideKeepAwakeKey, defaultValue = true)
    val overLockScreen by rememberPreference(BedsideLockScreenKey, defaultValue = true)
    val seed = LocalArtworkColor.current
    val coverHue = remember(seed) { coverTint(seed) }
    val tint = when (tintChoice) {
        BedsideNightTint.OFF -> null
        BedsideNightTint.ORANGE -> BedsideOrange
        BedsideNightTint.COVER -> coverHue
    }
    val rest = rememberBedsideRest(live, saver)
    val ink = rememberBedsideInk(rest, tint, saver)
    val tones = rememberStyleTones()
    val time = rememberBedsideTime(live)
    val cover = rememberCoverPainter(rememberPlayingTrack().thumbnailUrl)
    val (rotated, setRotated) = rememberStyleRotation(PlayerStyle.BEDSIDE)
    val locked = if (live) rememberPhoneLocked().value else false
    if (live) BedsideWindowFlags(keepAwake, overLockScreen && LockScreenSupported)
    RotatingFrame(
        rotated = rotated,
        tones = tones,
        // pure black, so an oled screen keeps every pixel off that isn't the time
        background = { Spacer(Modifier.fillMaxSize().background(Color.Black)) },
        modifier = modifier.then(if (preview) SwallowTouches else Modifier),
    ) { landscape ->
        BedsideLayout(
            landscape = landscape,
            face = face,
            onFace = onFace,
            time = time,
            ink = ink,
            rest = rest,
            tones = tones,
            cover = cover,
            actions = actions,
            rotated = rotated,
            onRotate = setRotated,
            locked = locked,
            live = live,
        )
    }
}

// the two sections stacked a swipe apart, with the dots, the chevron and the menu over them
@Composable
private fun BedsideLayout(
    landscape: Boolean,
    face: BedsideClockFace,
    onFace: (BedsideClockFace) -> Unit,
    time: BedsideTime,
    ink: BedsideInk,
    rest: BedsideRest,
    tones: StyleTones,
    cover: Painter,
    actions: StyleActions,
    rotated: Boolean,
    onRotate: (Boolean) -> Unit,
    locked: Boolean,
    live: Boolean,
) {
    val context = LocalContext.current
    val sheet = LocalPlayerBottomSheetState.current
    val connection = LocalPlayerConnection.current
    val interactive = LocalStyleInteractive.current
    val density = LocalDensity.current
    val close = remember(context, sheet) { { context.unlockThen { sheet?.collapseSoft() } } }
    val gated = remember(actions, context) { actions.unlockedFirst(context) }
    val toggle = remember(connection) { { connection?.togglePlayPause(); Unit } }
    var menuOpen by remember { mutableStateOf(false) }
    // a menu left open doesn't stay lit all night
    LaunchedEffect(rest) { snapshotFlow { rest.resting }.collect { if (it) menuOpen = false } }
    val sections = rememberPagerState { 2 }
    val pull = remember(sections, close, density) {
        with(density) { PullToClose(sections, close, PullClose.toPx(), FlingClose.toPx()) }
    }
    val insets = bedsideInsets(landscape)
    Box(
        Modifier
            .fillMaxSize()
            .then(if (live) Modifier.watchTouches(rest).catchStrayDrags(interactive, close) else Modifier),
    ) {
        VerticalPager(
            state = sections,
            // the song page is built up front so the first swipe to it doesn't stutter; a picker card only needs the clock
            beyondViewportPageCount = if (live) 1 else 0,
            userScrollEnabled = live,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pull)
                .graphicsLayer {
                    val drift = rest.drift.value
                    translationX = drift.x
                    translationY = drift.y
                },
        ) { page ->
            val pageModifier = Modifier
                .fillMaxSize()
                .padding(start = insets.start, top = insets.top, end = insets.end, bottom = insets.bottom)
                .then(if (live) Modifier.bedsideTaps(interactive, toggle, gated.onEditPlayer) else Modifier)
            if (page == 0) {
                BedsideClockSection(face, onFace, time, ink, rest, cover, live, pageModifier)
            } else {
                BedsideNowPlaying(
                    ink = ink,
                    rest = rest,
                    tones = tones,
                    time = time,
                    visible = { sections.currentPage == 1 || sections.isScrollInProgress },
                    preview = !live,
                    modifier = pageModifier,
                )
            }
        }
        if (!live) return@Box
        PageDots(
            count = 2,
            position = { sections.currentPage + sections.currentPageOffsetFraction },
            vertical = true,
            rest = rest,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = insets.end + 12.dp),
        )
        TopButtons(locked, tones, rest, insets, onClose = close, onMenu = { menuOpen = !menuOpen })
        StyleMenu(
            open = menuOpen,
            onOpenChange = { menuOpen = it },
            tones = tones,
            actions = gated,
            rotated = rotated,
            onRotate = onRotate,
            atBottom = false,
            end = insets.end + EdgeGap,
            edge = insets.top + EdgeGap,
            buttonSize = ButtonSize,
            showButton = false,
        )
        LockedBackHandler(locked, close)
    }
}

// the clock section: the faces side by side, a sideways swipe from one to the next
@Composable
private fun BedsideClockSection(
    face: BedsideClockFace,
    onFace: (BedsideClockFace) -> Unit,
    time: BedsideTime,
    ink: BedsideInk,
    rest: BedsideRest,
    cover: Painter,
    live: Boolean,
    modifier: Modifier,
) {
    if (!live) {
        BedsideClockFaceView(face, time, ink, cover, modifier)
        return
    }
    val faces = BedsideClockFace.entries
    val pager = rememberPagerState(initialPage = face.ordinal) { faces.size }
    // picked in the sheet, or saved late by the settings file: the pager catches up
    LaunchedEffect(face) {
        if (pager.settledPage != face.ordinal && !pager.isScrollInProgress) pager.animateScrollToPage(face.ordinal)
    }
    val currentOnFace by rememberUpdatedState(onFace)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collectLatest { page ->
            delay(FaceSaveDelayMs)
            currentOnFace(faces[page])
        }
    }
    Box(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { faces[it].name }) { page ->
            BedsideClockFaceView(faces[page], time, ink, cover, Modifier.fillMaxSize())
        }
        PageDots(
            count = faces.size,
            position = { pager.currentPage + pager.currentPageOffsetFraction },
            vertical = false,
            rest = rest,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        )
    }
}

// the chevron that closes (a lock while the phone is locked) and the ••• for the menu, both fading with the chrome
@Composable
private fun BoxScope.TopButtons(
    locked: Boolean,
    tones: StyleTones,
    rest: BedsideRest,
    insets: BedsideInsets,
    onClose: () -> Unit,
    onMenu: () -> Unit,
) {
    val closeLabel = stringResource(if (locked) R.string.bedside_unlock_to_close else R.string.bedside_close)
    val menuLabel = stringResource(R.string.bedside_more)
    StyleCircle(
        icon = if (locked) R.drawable.lock else R.drawable.expand_more,
        tones = tones,
        size = ButtonSize,
        onClick = onClose,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = insets.start + EdgeGap, top = insets.top + EdgeGap)
            .bedsideChrome(rest)
            .semantics { contentDescription = closeLabel },
    )
    StyleCircle(
        icon = R.drawable.more_horiz,
        tones = tones,
        size = ButtonSize,
        onClick = onMenu,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = insets.end + EdgeGap, top = insets.top + EdgeGap)
            .bedsideChrome(rest)
            .semantics { contentDescription = menuLabel },
    )
}

// a few small dots, the one in view bright, only out while someone's touching
@Composable
private fun PageDots(count: Int, position: () -> Float, vertical: Boolean, rest: BedsideRest, modifier: Modifier) {
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

// a double tap plays or pauses and a hold opens the editor, on either section
private fun Modifier.bedsideTaps(interactive: () -> Boolean, onToggle: () -> Unit, onEdit: () -> Unit): Modifier =
    pointerInput(interactive, onToggle, onEdit) {
        whileStyleInteractive(interactive) {
            detectTapGestures(onDoubleTap = { onToggle() }, onLongPress = { onEdit() })
        }
    }

// a drag nobody under it took (one that began on a button) is still the bedside's and closes it pulled down; it waits past the pages' slop and gives way to them, so a slanted swipe still turns a page
private fun Modifier.catchStrayDrags(interactive: () -> Boolean, onClose: () -> Unit): Modifier =
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

// pulling down past the clock closes the player; kept here rather than left to the sheet so over the lock screen it can ask for an unlock first
private class PullToClose(
    private val sections: PagerState,
    private val onClose: () -> Unit,
    private val closePx: Float,
    private val flingPx: Float,
) : NestedScrollConnection {
    private var fresh = true
    private var startedAtTop = false
    private var pulled = 0f
    private var closed = false

    private fun atTop() = sections.currentPage == 0 && abs(sections.currentPageOffsetFraction) < 0.01f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // only a pull that began on the clock closes, not one that scrolled back to it from the song
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

// over the lock screen, everything in the menu that leaves the bedside asks for an unlock first
private fun StyleActions.unlockedFirst(context: Context) = StyleActions(
    onOtherMenu = { context.unlockThen(onOtherMenu) },
    onEditPlayer = { context.unlockThen(onEditPlayer) },
    onLyrics = onLyrics?.let { open -> { context.unlockThen(open) } },
    onExitVideoMode = onExitVideoMode,
    onOpenArtist = { id -> context.unlockThen { onOpenArtist(id) } },
    onOpenAlbum = { id -> context.unlockThen { onOpenAlbum(id) } },
)

// the system bars as they fall on the bedside: along the top and bottom upright, down the sides once it's turned
private class BedsideInsets(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

@Composable
private fun bedsideInsets(landscape: Boolean): BedsideInsets {
    val status = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (landscape) BedsideInsets(start = status, top = 0.dp, end = nav, bottom = 0.dp)
    else BedsideInsets(start = 0.dp, top = status, end = 0.dp, bottom = nav)
}

// a card in the style picker shows the clock, it doesn't run it
private val SwallowTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
