// TvShell.kt

package com.mochyy.mochymusic.ui.tv

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalGlassState
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.LocalSyncUtils
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.BlurQualityKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.link.tv.TvControl
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetHost
import com.mochyy.mochymusic.ui.component.rememberBottomSheetState
import com.mochyy.mochymusic.ui.component.shimmer.ShimmerTheme
import com.mochyy.mochymusic.ui.screens.navigationBuilder
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.valentinilk.shimmer.LocalShimmerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the tv app once a profile is picked: its pages, the chrome around them, and the player over everything
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun TvShell(
    player: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    profile: TvProfile,
    onSwitchProfile: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity

    BoxWithConstraints(Modifier.fillMaxSize().background(TvColors.Ground)) {
        // the player is either all the way open or out of sight, a tv has no mini player at the bottom
        val sheet = rememberBottomSheetState(dismissedBound = 0.dp, collapsedBound = 0.dp, expandedBound = maxHeight)
        var playerMounted by remember { mutableStateOf(false) }
        // mounted unseen first, so the heavy first frame of a style never lands inside the open spring
        val openPlayer: () -> Unit = {
            if (player?.mediaMetadata?.value != null) {
                playerMounted = true
                scope.launchOpen { sheet.expandSoft() }
            }
        }
        // the pill's own spot, so the full player can grow out of exactly where it was sitting; only
        // caught while the pill is actually at rest, never mid fade or mid swipe
        var pillBounds by remember { mutableStateOf<Rect?>(null) }
        var menuFor by remember { mutableStateOf<TvSongRef?>(null) }
        val actions = remember(navController, player) {
            TvActions(navController, { player }, database, openMenu = { menuFor = it }, openPlayer = openPlayer, switchProfile = onSwitchProfile)
        }
        val obscured = remember(sheet) { derivedStateOf { sheet.value > 0.dp } }
        // the app remembers what had the remote, so closing the player puts it straight back there
        val appFocus = remember { FocusRequester() }
        // back on a tab's own page takes the remote to the rail first, since the rail sits at the far right
        val railFocus = remember { FocusRequester() }
        var railHasFocus by remember { mutableStateOf(false) }
        val onTabRoot = currentRoute in TvTab.entries.map { it.route } || currentRoute?.startsWith("${TvRoutes.Search}?") == true

        var playerWasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(sheet.isDismissed) {
            if (sheet.isDismissed && playerWasOpen) runCatching { appFocus.requestFocus() }
            playerWasOpen = !sheet.isDismissed
        }
        val blurQuality by rememberEnumPreference(BlurQualityKey, BlurQuality.LOW)
        val glassState = remember { GlassState() }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val snackbarHostState = remember { SnackbarHostState() }

        CompositionLocalProvider(
            LocalDatabase provides database,
            LocalContentColor provides Color.White,
            LocalPlayerBottomSheetState provides sheet,
            LocalPlayerConnection provides player,
            LocalGlassState provides glassState,
            LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
            LocalDownloadUtil provides downloadUtil,
            LocalSyncUtils provides syncUtils,
            LocalShimmerTheme provides ShimmerTheme,
            // a tv's gpu is closer to an old phone's than a new one: the cheap blur unless someone picks the other
            LocalBlurQuality provides blurQuality,
            LocalTvActions provides actions,
        ) {
            PopupSheetHost(states = listOf(LocalMenuState.current, LocalBottomSheetPageState.current, LocalZoomOutOverlayState.current)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event -> handleMediaKey(event.key, event.type, player) },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(appFocus)
                            .focusRestorer()
                            .focusGroup()
                            .graphicsLayer {
                                val open = (sheet.value / sheet.expandedBound).coerceIn(0f, 1f)
                                alpha = 1f - open
                                // one cached layer while the player slides over, nothing at all once it's covered
                                if (open > 0.001f && open < 0.999f) compositingStrategy = CompositingStrategy.Offscreen
                            },
                    ) {
                        CompositionLocalProvider(LocalAppContentObscured provides obscured) {
                            // a playlist or album card grows into its own page the same way the phone's cards do
                            androidx.compose.animation.SharedTransitionLayout {
                                CompositionLocalProvider(com.mochyy.mochymusic.ui.component.LocalSharedTransitionScope provides this) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = TvRoutes.Home,
                                        enterTransition = { fadeIn(tween(320, easing = TvEase)) + scaleIn(tween(420, easing = TvEase), initialScale = 0.96f) },
                                        exitTransition = { fadeOut(tween(180)) },
                                        popEnterTransition = { fadeIn(tween(320, easing = TvEase)) + scaleIn(tween(420, easing = TvEase), initialScale = 1.03f) },
                                        popExitTransition = { fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.97f) },
                                    ) {
                                        // the phone's pages stay reachable for anything the player opens (an equalizer, a lyrics source)
                                        navigationBuilder(navController, scrollBehavior, activity, snackbarHostState)
                                        tvGraph(profile = profile)
                                    }
                                }
                            }
                        }
                        // registered after the nav host's own, so on a tab's page it wins over going back a page
                        BackHandler(enabled = onTabRoot && !railHasFocus && sheet.isDismissed && menuFor == null) {
                            runCatching { railFocus.requestFocus() }
                        }
                        TvChromeLayer(
                            player = player,
                            profile = profile,
                            railFocus = railFocus,
                            onRailFocus = { railHasFocus = it },
                            selected = tabOf(currentRoute),
                            onOpenPlayer = openPlayer,
                            onPillPlaced = { if (sheet.isDismissed) pillBounds = it },
                            onSwitchProfile = onSwitchProfile,
                            onTab = { tab ->
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                    if (playerMounted && player != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .growFromPill(progress = { sheet.value / sheet.expandedBound }, pill = { pillBounds }),
                        ) {
                            TvPlayerHost(state = sheet, player = player, navController = navController)
                        }
                    }
                    menuFor?.let { song -> TvSongMenu(song = song, onDismiss = { menuFor = null }) }
                }
            }
        }

        // a song sent from the phone opens the player, so the room sees what's on
        LaunchedEffect(Unit) { TvControl.openPlayer.collect { openPlayer() } }

        // a closed player drops its pages after a while, a tv has little memory to spare
        LaunchedEffect(sheet.isDismissed) {
            if (sheet.isDismissed && playerMounted) {
                delay(PlayerUnmountMs)
                if (sheet.isDismissed) playerMounted = false
            }
        }
    }
}

@Composable
private fun TvChromeLayer(
    player: PlayerConnection?,
    profile: TvProfile,
    railFocus: FocusRequester,
    onRailFocus: (Boolean) -> Unit,
    selected: TvTab?,
    onOpenPlayer: () -> Unit,
    onPillPlaced: (Rect) -> Unit,
    onSwitchProfile: () -> Unit,
    onTab: (TvTab) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val top = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent, endY = 92.dp.toPx())
                    val right = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f), startX = size.width - 130.dp.toPx(), endX = size.width)
                    onDrawBehind {
                        drawRect(top, size = androidx.compose.ui.geometry.Size(size.width, 92.dp.toPx()))
                        drawRect(right, topLeft = androidx.compose.ui.geometry.Offset(size.width - 130.dp.toPx(), 0f))
                    }
                },
        )
        TvLogo(Modifier.align(Alignment.TopStart).padding(start = TvSpace.Start, top = TvSpace.ChromeTop + 4.dp))
        if (player != null) {
            TvNowPlayingPill(
                player,
                onOpenPlayer,
                Modifier.align(Alignment.TopCenter).padding(top = TvSpace.ChromeTop - 4.dp),
                onPlaced = onPillPlaced,
            )
        }
        TvAvatar(profile.avatarPath, onSwitchProfile, Modifier.align(Alignment.TopEnd).padding(end = TvSpace.Start, top = TvSpace.ChromeTop + 3.dp))
        // "connected to" and "synced with" the phone, under the avatar for a few seconds
        TvLinkToast(Modifier.align(Alignment.TopEnd).padding(end = TvSpace.Start, top = TvSpace.ChromeTop + 40.dp))
        TvRail(
            selected,
            onTab,
            focus = railFocus,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .onFocusChanged { onRailFocus(it.hasFocus) },
        )
    }
}

// the remote's media keys work on every page, the player doesn't have to be open
private fun handleMediaKey(key: Key, type: KeyEventType, player: PlayerConnection?): Boolean {
    if (player == null || type != KeyEventType.KeyDown) return false
    when (key) {
        Key.MediaPlayPause -> player.player.togglePlayPause()
        Key.MediaPlay -> player.play()
        Key.MediaPause -> player.pause()
        Key.MediaNext -> player.seekToNext()
        Key.MediaPrevious -> player.seekToPrevious()
        Key.MediaFastForward -> player.seekTo(player.player.currentPosition + 10_000L)
        Key.MediaRewind -> player.seekTo((player.player.currentPosition - 10_000L).coerceAtLeast(0L))
        else -> return false
    }
    return true
}

// how much of the grow the content spends fading in, so it never shows squashed inside the tiny starting pill
private const val PillGrowFadeShare = 0.35f

// the full player scales and clips down to exactly the pill's own rect at rest, and grows back out to the
// whole screen as [progress] goes from 0 to 1 - the same pill the remote just pressed, not a generic slide-up
private fun Modifier.growFromPill(progress: () -> Float, pill: () -> Rect?): Modifier = graphicsLayer {
    val source = pill() ?: return@graphicsLayer
    val t = progress().coerceIn(0f, 1f)
    if (t >= 0.999f) return@graphicsLayer
    val screenCenter = Offset(size.width / 2f, size.height / 2f)
    val pillCenter = source.center
    scaleX = lerp(source.width / size.width, 1f, t)
    scaleY = lerp(source.height / size.height, 1f, t)
    translationX = lerp(pillCenter.x - screenCenter.x, 0f, t)
    translationY = lerp(pillCenter.y - screenCenter.y, 0f, t)
    clip = true
    shape = RoundedCornerShape(lerp(source.height / 2f, 0f, t).toDp())
    alpha = (t / PillGrowFadeShare).coerceIn(0f, 1f)
}

// waits two frames so the freshly mounted player has laid out before the spring starts
private fun CoroutineScope.launchOpen(open: () -> Unit) {
    launch {
        withFrameNanos { }
        withFrameNanos { }
        open()
    }
}

private const val PlayerUnmountMs = 30_000L
