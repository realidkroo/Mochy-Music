package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BedsideInfoTilePageKey
import com.mochyy.mochymusic.constants.BedsideMusicTilePageKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.player.LocalSongCarousel
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.RotatingFrame
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.StyleCircle
import com.mochyy.mochymusic.ui.player.styles.StyleMenu
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.rememberStyleRotation
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTones
import com.mochyy.mochymusic.ui.player.styles.styleSongSwipe
import com.mochyy.mochymusic.utils.rememberPreference

private val MenuButton = 34.dp
private val EdgeGap = 16.dp
// the music tile's share of the long side
private const val MusicShare = 0.54f

// the nightstand as two tiles: the music on one, the words or a clock on the other, each scrolled through on its own
@Composable
fun BedsideTilesStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val live = !preview
    val night = rememberBedsideNight(live)
    val tones = rememberStyleTones()
    val time = rememberBedsideTime(live)
    val (rotated, setRotated) = rememberStyleRotation(PlayerStyle.BEDSIDE_TILES)
    RotatingFrame(
        rotated = rotated,
        tones = tones,
        background = { Spacer(Modifier.fillMaxSize().background(Color.Black)) },
        modifier = modifier.then(if (preview) SwallowTouches else Modifier),
    ) { landscape ->
        TilesLayout(landscape, time, night, tones, actions, rotated, setRotated, live)
    }
}

// the tiles side by side when there's width for it, one over the other upright, and the chrome over them
@Composable
private fun TilesLayout(
    landscape: Boolean,
    time: BedsideTime,
    night: BedsideNight,
    tones: StyleTones,
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
    val close = remember(context, sheet) { { context.unlockThen { sheet?.collapseSoft() } } }
    val gated = remember(actions, context) { actions.unlockedFirst(context) }
    val toggle = remember(connection) { { connection?.togglePlayPause(); Unit } }
    var menuOpen by remember { mutableStateOf(false) }
    // a menu left open doesn't stay lit all night
    LaunchedEffect(rest) { snapshotFlow { rest.resting }.collect { if (it) menuOpen = false } }
    val (musicSaved, onMusicSaved) = rememberPreference(BedsideMusicTilePageKey, 0)
    val (infoSaved, onInfoSaved) = rememberPreference(BedsideInfoTilePageKey, LyricsPage)
    val music = rememberPagerState(initialPage = musicSaved.coerceIn(0, MusicPages - 1)) { MusicPages }
    val info = rememberPagerState(initialPage = infoSaved.coerceIn(0, InfoPages - 1)) { InfoPages }
    if (live) {
        RememberPage(music, musicSaved, onMusicSaved)
        RememberPage(info, infoSaved, onInfoSaved)
    }
    val insets = bedsideInsets(landscape)
    val taps = if (live) Modifier.bedsideTaps(interactive, toggle, gated.onEditPlayer) else Modifier
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (live) Modifier.watchTouches(rest).catchStrayDrags(interactive, close) else Modifier),
    ) {
        val wide = maxWidth > maxHeight
        val drift = Modifier.graphicsLayer {
            val offset = rest.drift.value
            translationX = offset.x
            translationY = offset.y
        }
        // each tile keeps clear of the system bars on its own outer edges only
        val musicPad = if (wide) BedsideInsets(insets.start, insets.top, 0.dp, insets.bottom) else BedsideInsets(insets.start, insets.top, insets.end, 0.dp)
        val infoPad = if (wide) BedsideInsets(0.dp, insets.top, insets.end, insets.bottom) else BedsideInsets(insets.start, 0.dp, insets.end, insets.bottom)
        val musicTile: @Composable (Modifier) -> Unit = { tileModifier ->
            MusicTile(music, night, live, wide, musicPad, close, taps, tileModifier)
        }
        val infoTile: @Composable (Modifier) -> Unit = { tileModifier ->
            InfoTile(info, time, night, live, infoPad, close, taps, tileModifier)
        }
        if (wide) {
            Row(Modifier.fillMaxSize().then(drift)) {
                musicTile(Modifier.weight(MusicShare).fillMaxHeight())
                infoTile(Modifier.weight(1f - MusicShare).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().then(drift)) {
                musicTile(Modifier.weight(0.5f).fillMaxWidth())
                infoTile(Modifier.weight(0.5f).fillMaxWidth())
            }
        }
        if (!live) return@BoxWithConstraints
        // sideways the handle and the menu sit over the info tile, as in the mockups; upright they sit along the top
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .then(if (wide) Modifier.fillMaxWidth(1f - MusicShare) else Modifier.fillMaxWidth())
                .padding(top = insets.top, end = insets.end),
        ) {
            BedsideHandle(night.locked, rest, close, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
            MenuButton(rest, tones) { menuOpen = !menuOpen }
        }
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
            buttonSize = MenuButton,
            showButton = false,
        )
        LockScreenPrompt(Modifier.align(Alignment.Center).padding(24.dp))
        LockedBackHandler(night.locked, close)
    }
}

// the music tile: the cover page and the card page, a vertical swipe apart; a sideways swipe skips the song
@Composable
private fun MusicTile(
    pager: PagerState,
    night: BedsideNight,
    live: Boolean,
    wide: Boolean,
    padding: BedsideInsets,
    close: () -> Unit,
    taps: Modifier,
    modifier: Modifier,
) {
    val connection = LocalPlayerConnection.current
    val track = rememberPlayingTrack()
    val carousel = if (live) LocalSongCarousel.current else null
    // both music pages show the song's line, so it ticks whenever the player is open
    val progress = rememberBedsideProgress { live }
    val pull = rememberPullToClose(pager, close)
    Box(modifier) {
        VerticalPager(
            state = pager,
            flingBehavior = bedsidePagerFling(pager),
            beyondViewportPageCount = if (live) 1 else 0,
            userScrollEnabled = live,
            modifier = Modifier.fillMaxSize().nestedScroll(pull),
        ) { page ->
            MusicTilePage(
                page = page,
                track = track,
                carousel = carousel,
                progress = progress,
                ink = night.ink,
                landscape = wide,
                padding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .then(taps)
                    .styleSongSwipe(LocalStyleInteractive.current, carousel) { connection?.player },
            )
        }
        if (live) {
            PageDots(
                count = MusicPages,
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                vertical = true,
                rest = night.rest,
                modifier = if (wide) {
                    Modifier.align(Alignment.CenterStart).padding(start = padding.start + 10.dp)
                } else {
                    Modifier.align(Alignment.CenterEnd).padding(end = padding.end + 10.dp)
                },
            )
        }
    }
}

// the info tile: the lyrics, the stacked clock, the dial and the bars, a vertical swipe apart
@Composable
private fun InfoTile(
    pager: PagerState,
    time: BedsideTime,
    night: BedsideNight,
    live: Boolean,
    padding: BedsideInsets,
    close: () -> Unit,
    taps: Modifier,
    modifier: Modifier,
) {
    val pull = rememberPullToClose(pager, close)
    Box(modifier) {
        VerticalPager(
            state = pager,
            flingBehavior = bedsidePagerFling(pager),
            beyondViewportPageCount = if (live) 1 else 0,
            userScrollEnabled = live,
            modifier = Modifier.fillMaxSize().nestedScroll(pull),
        ) { page ->
            InfoTilePage(
                page = page,
                time = time,
                ink = night.ink,
                live = live,
                visible = { pager.currentPage == page || pager.isScrollInProgress },
                padding = padding,
                modifier = Modifier.fillMaxSize().then(taps),
            )
        }
        if (live) {
            PageDots(
                count = InfoPages,
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                vertical = true,
                rest = night.rest,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = padding.end + 10.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.MenuButton(rest: BedsideRest, tones: StyleTones, onClick: () -> Unit) {
    val label = stringResource(R.string.bedside_more)
    StyleCircle(
        icon = R.drawable.more_horiz,
        tones = tones,
        size = MenuButton,
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = EdgeGap, end = EdgeGap)
            .bedsideChrome(rest)
            .semantics { contentDescription = label },
    )
}
