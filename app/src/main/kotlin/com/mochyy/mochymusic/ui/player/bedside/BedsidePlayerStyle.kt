package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BedsideClockFace
import com.mochyy.mochymusic.constants.BedsideClockFaceKey
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
import com.mochyy.mochymusic.utils.rememberEnumPreference

private val ButtonSize = 40.dp
private val EdgeGap = 18.dp

// a quiet clock for the nightstand: the time first, the song one swipe up, the night look settling in once it's left alone
@Composable
fun BedsidePlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val live = !preview
    val (face, onFace) = rememberEnumPreference(BedsideClockFaceKey, BedsideClockFace.BIG)
    val night = rememberBedsideNight(live)
    val tones = rememberStyleTones()
    val time = rememberBedsideTime(live)
    val cover = rememberCoverPainter(rememberPlayingTrack().thumbnailUrl)
    val (rotated, setRotated) = rememberStyleRotation(PlayerStyle.BEDSIDE)
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
            night = night,
            tones = tones,
            cover = cover,
            actions = actions,
            rotated = rotated,
            onRotate = setRotated,
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
    night: BedsideNight,
    tones: StyleTones,
    cover: Painter,
    actions: StyleActions,
    rotated: Boolean,
    onRotate: (Boolean) -> Unit,
    live: Boolean,
) {
    val context = LocalContext.current
    val sheet = LocalPlayerBottomSheetState.current
    val connection = LocalPlayerConnection.current
    val interactive = LocalStyleInteractive.current
    val rest = night.rest
    val ink = night.ink
    val close = remember(context, sheet) { { context.unlockThen { sheet?.collapseSoft() } } }
    val gated = remember(actions, context) { actions.unlockedFirst(context) }
    val toggle = remember(connection) { { connection?.togglePlayPause(); Unit } }
    var menuOpen by remember { mutableStateOf(false) }
    // a menu left open doesn't stay lit all night
    LaunchedEffect(rest) { snapshotFlow { rest.resting }.collect { if (it) menuOpen = false } }
    val sections = rememberPagerState { 2 }
    val pull = rememberPullToClose(sections, close)
    val insets = bedsideInsets(landscape)
    Box(
        Modifier
            .fillMaxSize()
            .then(if (live) Modifier.watchTouches(rest).catchStrayDrags(interactive, close) else Modifier),
    ) {
        VerticalPager(
            state = sections,
            flingBehavior = bedsidePagerFling(sections),
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
        TopButtons(night.locked, tones, rest, insets, onClose = close, onMenu = { menuOpen = !menuOpen })
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
        LockScreenPrompt(Modifier.align(Alignment.BottomCenter).padding(bottom = insets.bottom + 24.dp, start = 24.dp, end = 24.dp))
        LockedBackHandler(night.locked, close)
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
    RememberPage(pager, face.ordinal) { onFace(faces[it]) }
    Box(modifier) {
        HorizontalPager(
            state = pager,
            flingBehavior = bedsidePagerFling(pager),
            modifier = Modifier.fillMaxSize(),
            key = { faces[it].name },
        ) { page ->
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
