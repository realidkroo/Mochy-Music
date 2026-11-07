// TvShell.kt

package com.mochyy.mochymusic.ui.tv

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@OptIn(ExperimentalMaterial3Api::class)
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
        var menuFor by remember { mutableStateOf<TvSongRef?>(null) }
        val actions = remember(navController, player) {
            TvActions(navController, { player }, database, openMenu = { menuFor = it }, openPlayer = openPlayer)
        }
        val obscured = remember(sheet) { derivedStateOf { sheet.value > 0.dp } }
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
                            .graphicsLayer {
                                val open = (sheet.value / sheet.expandedBound).coerceIn(0f, 1f)
                                alpha = 1f - open
                                // one cached layer while the player slides over, nothing at all once it's covered
                                if (open > 0.001f && open < 0.999f) compositingStrategy = CompositingStrategy.Offscreen
                            },
                    ) {
                        CompositionLocalProvider(LocalAppContentObscured provides obscured) {
                            NavHost(
                                navController = navController,
                                startDestination = TvRoutes.Home,
                                enterTransition = { fadeIn(tween(220)) },
                                exitTransition = { fadeOut(tween(160)) },
                            ) {
                                // the phone's pages stay reachable for anything the player opens (an equalizer, a lyrics source)
                                navigationBuilder(navController, scrollBehavior, activity, snackbarHostState)
                                tvGraph(profile = profile)
                            }
                        }
                        TvChromeLayer(
                            player = player,
                            profile = profile,
                            selected = tabOf(currentRoute),
                            onOpenPlayer = openPlayer,
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
                        TvPlayerHost(state = sheet, player = player, navController = navController)
                    }
                    menuFor?.let { song -> TvSongMenu(song = song, onDismiss = { menuFor = null }) }
                }
            }
        }

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
    selected: TvTab?,
    onOpenPlayer: () -> Unit,
    onSwitchProfile: () -> Unit,
    onTab: (TvTab) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TvLogo(Modifier.align(Alignment.TopStart).padding(start = TvSpace.Start, top = TvSpace.ChromeTop + 4.dp))
        if (player != null) {
            TvNowPlayingPill(player, onOpenPlayer, Modifier.align(Alignment.TopCenter).padding(top = TvSpace.ChromeTop - 4.dp))
        }
        TvAvatar(profile.avatarPath, onSwitchProfile, Modifier.align(Alignment.TopEnd).padding(end = TvSpace.Start, top = TvSpace.ChromeTop + 3.dp))
        TvRail(selected, onTab, Modifier.align(Alignment.CenterEnd).padding(end = 32.dp))
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

// waits two frames so the freshly mounted player has laid out before the spring starts
private fun CoroutineScope.launchOpen(open: () -> Unit) {
    launch {
        withFrameNanos { }
        withFrameNanos { }
        open()
    }
}

private const val PlayerUnmountMs = 30_000L
