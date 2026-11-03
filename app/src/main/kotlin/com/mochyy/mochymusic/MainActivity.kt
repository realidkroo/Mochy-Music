// MainActivity.kt

package com.mochyy.mochymusic

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.mochyy.mochymusic.ui.component.LocalSharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppBarHeight
import com.mochyy.mochymusic.constants.DarkModeKey
import com.mochyy.mochymusic.ui.utils.defaultMotionEnter
import com.mochyy.mochymusic.ui.utils.defaultMotionExit
import com.mochyy.mochymusic.ui.theme.ForceDarkTheme
import com.mochyy.mochymusic.constants.DefaultOpenTabKey
import com.mochyy.mochymusic.constants.CropAlbumArtKey
import com.mochyy.mochymusic.constants.GridItemsSizeKey
import com.mochyy.mochymusic.constants.GridItemSize
import com.mochyy.mochymusic.constants.SwipeToSongKey
import com.mochyy.mochymusic.constants.DisableScreenshotKey
import com.mochyy.mochymusic.constants.DynamicThemeKey
import com.mochyy.mochymusic.constants.EnableHighRefreshRateKey
import com.mochyy.mochymusic.constants.MiniPlayerBottomSpacing
import com.mochyy.mochymusic.constants.MiniPlayerHeight
import com.mochyy.mochymusic.constants.NavigationBarAnimationSpec
import com.mochyy.mochymusic.constants.NavigationBarHeight
import com.mochyy.mochymusic.constants.SlimNavBarHeight
import com.mochyy.mochymusic.constants.SlimNavBarKey
import com.mochyy.mochymusic.constants.StopMusicOnTaskClearKey

import com.mochyy.mochymusic.core.updater.getAutoUpdateCheckSetting
import com.mochyy.mochymusic.core.updater.isNewerVersion
import com.mochyy.mochymusic.core.updater.saveUpdateAvailableState
import com.mochyy.mochymusic.core.updater.getUpdateNotificationsSetting
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.constants.PauseListenHistoryKey
import com.mochyy.mochymusic.constants.PauseSearchHistoryKey
import com.mochyy.mochymusic.constants.PureBlackKey
import com.mochyy.mochymusic.constants.SelectedThemeColorKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.MusicService.MusicBinder
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.AppNavigationBar
import com.mochyy.mochymusic.ui.component.onTabTapped
import com.mochyy.mochymusic.ui.component.AppNavigationRail
import com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.BottomSheetState
import com.mochyy.mochymusic.ui.component.rememberBottomSheetState
import com.mochyy.mochymusic.ui.component.shimmer.ShimmerTheme
import com.mochyy.mochymusic.ui.menu.YouTubeSongMenu
import com.mochyy.mochymusic.ui.player.BottomSheetPlayer
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.ui.screens.search.requestSearchFocus
import com.mochyy.mochymusic.ui.screens.navigationBuilder
import com.mochyy.mochymusic.ui.screens.DarkMode
import com.mochyy.mochymusic.ui.screens.NavigationTab
import com.mochyy.mochymusic.ui.theme.ColorSaver
import com.mochyy.mochymusic.ui.theme.DefaultThemeColor
import com.mochyy.mochymusic.ui.theme.MochyTheme
import com.mochyy.mochymusic.ui.theme.extractThemeColor
import com.mochyy.mochymusic.ui.utils.appBarScrollBehavior
import com.mochyy.mochymusic.ui.utils.resetHeightOffset
import com.mochyy.mochymusic.ui.utils.handOffToLaunchIntro
import com.mochyy.mochymusic.ui.launch.LaunchGate
import com.mochyy.mochymusic.ui.launch.LaunchIntroOverlay
import com.mochyy.mochymusic.ui.launch.LaunchIntroState
import com.mochyy.mochymusic.ui.launch.LocalLaunchIntro
import com.mochyy.mochymusic.ui.launch.launchRise
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.utils.reportException
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import com.mochyy.mochymusic.ui.screens.setup.SetupWizardContainer
import kotlinx.coroutines.Dispatchers
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

private const val BackgroundZoomOnExpand = true

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")

private const val SETUP_MARKER_FILE = "setup_wizard_completed"

private fun hasOnboardedOnThisInstall(context: android.content.Context): Boolean {
    if (runCatching { java.io.File(context.noBackupFilesDir, SETUP_MARKER_FILE).exists() }.getOrDefault(false)) {
        return true
    }

    if (isUpgradedInstall(context)) {
        markOnboardedOnThisInstall(context)
        return true
    }
    return false
}

/** True when this package has been updated in place at least once, i.e. it is not a fresh install. */
private fun isUpgradedInstall(context: android.content.Context): Boolean =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false)

// also called by the import once it has swapped the old library in, so onboarding doesn't show again
internal fun markOnboardedOnThisInstall(context: android.content.Context) {
    runCatching { java.io.File(context.noBackupFilesDir, SETUP_MARKER_FILE).createNewFile() }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.mochyy.mochymusic.i18n.AppLanguage.wrapActivityContext(newBase))
    }
    companion object {
        private const val ACTION_SEARCH = "com.mochyy.mochymusic.action.SEARCH"
        private const val ACTION_LIBRARY = "com.mochyy.mochymusic.action.LIBRARY"
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    /** Bumped when launched from an update notification; the composition opens the version sheet. */
    private var openUpdateRequests by mutableStateOf(0)

    /** A fresh launch from the launcher plays the launch intro (ui/launch/LaunchIntro.kt). */
    private var introRequested = false

    // keeps the app's animation speed following android's own animation scale while we're open
    private var systemScaleObserver: android.database.ContentObserver? = null
    private var introPlays = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    connectTo(service)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")

                    lifecycleScope.launch {
                        delay(500)
                        try {
                            connectTo(service)
                        } catch (e2: Exception) {
                            Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    // each return to the app binds again, release the connection it replaces or its listener and poller keep running
    private fun connectTo(binder: MusicBinder) {
        val previous = playerConnection
        playerConnection = PlayerConnection(this, binder, database, lifecycleScope).also {
            // the retry above can land after onStop, so it starts resting if the app is already hidden
            it.isUiVisible.value = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        previous?.dispose()
    }

    override fun onStart() {
        super.onStart()
        playerConnection?.isUiVisible?.value = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        playerConnection?.isUiVisible?.value = false
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        systemScaleObserver?.let { contentResolver.unregisterContentObserver(it) }
        systemScaleObserver = null
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
            unbindService(serviceConnection)
        }
        // the service outlives us, so take our listener off its player or a recreate leaves the old one firing
        playerConnection?.dispose()
        playerConnection = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == com.mochyy.mochymusic.ui.screens.update.ActionOpenUpdate) {
            openUpdateRequests++
            return
        }
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().handOffToLaunchIntro { introPlays }
        super.onCreate(savedInstanceState)
        introRequested = savedInstanceState == null && intent?.action == Intent.ACTION_MAIN
        if (introRequested) LaunchGate.begin() else LaunchGate.skip()
        // kept in step with the icon in use, in case it was switched some way other than customisation
        com.mochyy.mochymusic.utils.AppIcons.syncSplash(this)
        // the first start after an import says so once the intro is out of the way
        com.mochyy.mochymusic.importer.transfer.TransferRestore.takeRestoredNote(this)?.let { note ->
            lifecycleScope.launch {
                LaunchGate.awaitIntro()
                com.mochyy.mochymusic.ui.component.TopToaster.show(note, iconRes = R.drawable.backup, durationMs = 5000L)
            }
        }
        if (savedInstanceState == null && intent?.action == com.mochyy.mochymusic.ui.screens.update.ActionOpenUpdate) {
            openUpdateRequests++
        }

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        systemScaleObserver = com.mochyy.mochymusic.ui.utils.AppMotion.followSystemScale(this)
        moveOffOldLyricsShaderSwitch()
        followLookSettings()

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // our own animation scale goes into the window's recomposer, so the customize speed and the off switch reach every animation
        val motionRecomposer = window.decorView.createLifecycleAwareWindowRecomposer(
            com.mochyy.mochymusic.ui.utils.AppMotion.durationScale,
            lifecycle,
        )
        setContent(parent = motionRecomposer) {
            mochyApp(
                playerConnection = playerConnection,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
            )
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    @Composable
    private fun mochyApp(
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            if (getAutoUpdateCheckSetting(context)) {

                delay(2000L)
                // Same cached lookup the version sheet and home prompt use (this app's own
                // releases - the old checker here queried another project's repo). Launch-time
                // only: nothing about updates runs in the background.
                val release = com.mochyy.mochymusic.core.updater.getLatestRelease().getOrNull()
                val isAvailable = release != null &&
                    com.mochyy.mochymusic.core.updater.isNewerThanInstalled(release.version)
                saveUpdateAvailableState(context, isAvailable)

                if (release != null && isAvailable && getUpdateNotificationsSetting(context)) {
                    // At most three reminders per release (now, +2 weeks, +3 months).
                    com.mochyy.mochymusic.ui.screens.update.maybeNotifyUpdate(context, release)
                }
            }
        }

        // "Keep screen on" (the player's menu, or a chevron button): the phone doesn't sleep while
        // Mochy is on screen. A window flag only holds while the app is in front, which is the point.
        val keepScreenOn by rememberPreference(com.mochyy.mochymusic.constants.KeepScreenOn, defaultValue = false)
        val rootView = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(keepScreenOn) {
            rootView.keepScreenOn = keepScreenOn
            onDispose { rootView.keepScreenOn = false }
        }

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    layoutParams.preferredDisplayModeId = highRefreshModeId()
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 = modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                        ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                    params.preferredDisplayModeId = highRefreshModeId()
                } else {
                    params.preferredRefreshRate = 60f
                    params.preferredDisplayModeId = 0
                }
                window.attributes = params
            }
        }

        val useDarkTheme = ForceDarkTheme

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        // Pure black only makes sense on top of the dark scheme; forcing it in light mode
        // paints a black background under light-mode colours and the app looks dark on a
        // phone set to light.
        val pureBlack = pureBlackEnabled && useDarkTheme

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        LaunchedEffect(selectedThemeColor) {
            if (!enableDynamicTheme) {
                themeColor = selectedThemeColor
            }
        }

        // the launch intro waits for this, so the whole app doesn't recompose for a new colour
        // scheme halfway through it
        var themeSettled by remember { mutableStateOf(false) }

        LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!enableDynamicTheme || playerConnection == null) {
                themeColor = selectedThemeColor
                if (!enableDynamicTheme) themeSettled = true
                return@LaunchedEffect
            }

            playerConnection.service.currentMediaMetadata.collectLatest { song ->
                if (song?.thumbnailUrl != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val result = imageLoader.execute(
                                ImageRequest.Builder(this@MainActivity)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .build()
                            )
                            themeColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
                        } catch (e: Exception) {

                            themeColor = selectedThemeColor
                        }
                    }
                } else {
                    themeColor = selectedThemeColor
                }
                themeSettled = true
            }
        }

        MochyTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
                val glassState = remember { GlassState() }
                val hideAppChrome = remember { mutableStateOf(false) }
                val detailAccentColor = remember { mutableStateOf<Color?>(null) }
                val focusManager = LocalFocusManager.current
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout

                @OptIn(ExperimentalLayoutApi::class)
                val windowsInsets = WindowInsets.systemBarsIgnoringVisibility
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val navigationItems = remember {
                        Screens.MainScreens
                }
                val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)

                val defaultOpenTab = remember {
                    dataStore[DefaultOpenTabKey].toEnum<NavigationTab>(defaultValue = NavigationTab.HOME)
                }
                val tabOpenedFromShortcut = remember {
                    when (intent?.action) {
                        ACTION_SEARCH -> NavigationTab.SEARCH
                        ACTION_LIBRARY -> NavigationTab.SEARCH
                        else -> null
                    }
                }

                // the intro flies the splash mark into Home's logo, so it plays only when Home is first
                val launchIntro = remember {
                    val plays = introRequested && (tabOpenedFromShortcut ?: defaultOpenTab) == NavigationTab.HOME
                    introPlays = plays
                    if (introRequested && !plays) LaunchGate.skip()
                    // an anime icon's splash shows the icon itself, so the intro bounces out of it; every other launch starts from the bare mark
                    val icon = com.mochyy.mochymusic.utils.AppIcons.launchedFrom(intent)?.takeIf(com.mochyy.mochymusic.utils.AppIcons::splashShows)
                    LaunchIntroState(plays, icon)
                }

                val topLevelScreens = remember {
                    listOf(
                        Screens.Home.route,
                        "settings",
                    )
                }

                val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue())
                }

                val onSearch: (String) -> Unit = remember {
                    { searchQuery ->
                        if (searchQuery.isNotEmpty()) {
                            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

                            if (dataStore[PauseSearchHistoryKey] != true) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.query {
                                        insert(SearchHistory(query = searchQuery))
                                    }
                                }
                            }
                        }
                    }
                }

                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                // the tab lit in the bar is read off the back stack every time, never remembered:
                // a page belongs to the tab page under it, however it was reached
                val backStack by navController.currentBackStack.collectAsState()
                val selectedTabRoute by remember(navigationItems) {
                    derivedStateOf { com.mochyy.mochymusic.ui.component.owningTab(backStack, navigationItems) }
                }
                val tabOwnership = remember(navigationItems) {
                    com.mochyy.mochymusic.ui.component.TabOwnership(navigationItems)
                }
                // every page's tab is noted while it's on the stack, so the slide still knows it
                // after a tab switch has saved the page away
                SideEffect { tabOwnership.update(backStack) }

                val inSearchScreen by remember {
                    derivedStateOf {
                        currentRoute?.startsWith("search/") == true
                    }
                }
                val navigationItemRoutes = remember(navigationItems) {
                    navigationItems.map { it.route }.toSet()
                }

                val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes) {
                    currentRoute == null ||
                        navigationItemRoutes.contains(currentRoute) ||
                        currentRoute!!.startsWith("search/") ||

                        currentRoute!!.startsWith("genre/") ||
                        currentRoute!!.startsWith("local_playlist/") ||
                        currentRoute!!.startsWith("online_playlist/") ||
                        currentRoute!!.startsWith("auto_playlist/") ||
                        // the bar stays while a page from the Library, an album or an artist is open
                        currentRoute!!.startsWith("library/") ||
                        currentRoute!!.startsWith("album/") ||
                        currentRoute!!.startsWith("artist/") ||

                        currentRoute in SubSettingsRoutes
                }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                val showRail = isLandscape && !inSearchScreen

                val navPadding = if (shouldShowNavigationBar && !showRail) {
                    if (slimNav) SlimNavBarHeight else NavigationBarHeight
                } else {
                    0.dp
                }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = bottomInset +
                        (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                        MiniPlayerHeight,
                    expandedBound = maxHeight,
                )

                PlayerSystemBarsSync(playerBottomSheetState)

                val playerAwareWindowInsets = remember(
                    bottomInset,
                    shouldShowNavigationBar,
                    playerBottomSheetState.isDismissed,
                    showRail,
                ) {
                    var bottom = bottomInset
                    if (shouldShowNavigationBar && !showRail) {
                        bottom += NavigationBarHeight
                    }
                    if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                }
                val topAppBarScrollBehavior = appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery = withContext(Dispatchers.IO) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")
                            if (!rawQuery.isNullOrEmpty()) {
                                try {
                                    URLDecoder.decode(rawQuery, "UTF-8")
                                } catch (e: IllegalArgumentException) {
                                    rawQuery
                                }
                            } else {
                                ""
                            }
                        }
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length)
                            )
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(playerConnection) {
                    val player = playerConnection?.player ?: return@LaunchedEffect
                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }

                DisposableEffect(playerConnection, playerBottomSheetState) {
                    val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem != null &&
                                playerBottomSheetState.isDismissed
                            ) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings" &&

                        currentRoute != Screens.Search.route &&
                        currentRoute != Screens.Home.route
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { intent ->
                        handleDeepLinkIntent(intent, navController)
                    }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitleRes = remember(navBackStackEntry) {
                    when (navBackStackEntry?.destination?.route) {
                        Screens.Home.route -> R.string.music
                        Screens.Search.route -> R.string.search
                        else -> null
                    }
                }

                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by remember(database) { database.eventCount() }.collectAsState(initial = 0)
                val showHistoryButton = remember(pauseListenHistory, eventCount) {
                    !(pauseListenHistory && eventCount == 0)
                }

                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

                val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
                val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
                val blurQuality by rememberEnumPreference(
                    com.mochyy.mochymusic.constants.BlurQualityKey,
                    com.mochyy.mochymusic.ui.component.DefaultBlurQuality,
                )
                val swipeToSong by rememberPreference(SwipeToSongKey, false)

                val setupCompleted by rememberPreference(com.mochyy.mochymusic.constants.SetupCompletedKey, false)
                var forceShowSetup by remember { mutableStateOf(false) }
                // Seeded once per process: a restored backup can say setup is done on an
                // install that has never actually shown the wizard. Kept as state because
                // finishing the wizard writes the marker and must dismiss it right away.
                var onboardedHere by remember { mutableStateOf(hasOnboardedOnThisInstall(context)) }


                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState provides playerBottomSheetState,

                    LocalPlayerConnection provides playerConnection,
                    LocalGlassState provides glassState,
                    LocalHideAppChrome provides hideAppChrome,
                    LocalDetailAccentColor provides detailAccentColor,
                    LocalArtworkColor provides themeColor,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalCropAlbumArt provides cropAlbumArt,
                    LocalGridItemSize provides gridItemSize,
                    LocalSwipeToSong provides swipeToSong,
                    LocalLaunchIntro provides launchIntro,
                    com.mochyy.mochymusic.ui.component.LocalBlurQuality provides blurQuality,
                ) {
                    com.mochyy.mochymusic.ui.component.PopupSheetHost(
                        states = listOf(
                            com.mochyy.mochymusic.ui.component.LocalMenuState.current,
                            com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState.current,
                            com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState.current,
                        ),
                    ) {
                    com.mochyy.mochymusic.ui.screens.update.UpdateNotificationOpener(
                        requests = openUpdateRequests,
                        onOpenDevSpace = { personIndex ->
                            navController.navigate("developer_space?person=$personIndex")
                        },
                    )
                    com.mochyy.mochymusic.ui.screens.donate.DonatePromptScheduler(
                        enabled = setupCompleted && onboardedHere && !forceShowSetup,
                    )
                        SetupWizardContainer(
                            isVisible = !setupCompleted || !onboardedHere || forceShowSetup,
                            onSetupCompleted = { username, uri ->
                                markOnboardedOnThisInstall(context)
                                onboardedHere = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val savedUriStr = uri?.let { com.mochyy.mochymusic.ui.screens.profile.ProfileStore.copyPhoto(context, it) }.orEmpty()

                                    dataStore.updateData { prefs ->
                                        prefs.toMutablePreferences().apply {
                                            set(com.mochyy.mochymusic.constants.SetupCompletedKey, true)
                                            set(com.mochyy.mochymusic.constants.UsernameKey, username)
                                            if (savedUriStr.isNotEmpty()) {
                                                set(com.mochyy.mochymusic.constants.ProfilePicUriKey, savedUriStr)
                                            }
                                        }
                                    }
                                }
                                forceShowSetup = false
                            }
                        ) {
                            Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                AnimatedVisibility(
                                    visible = shouldShowTopBar,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                                ) {
                                    Row {
                                        val profilePicStr by rememberPreference(com.mochyy.mochymusic.constants.ProfilePicUriKey, "")
                                        TopAppBar(
                                            navigationIcon = {},
                                            title = {
                                                Text(
                                                    text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 24.sp
                                                    ),
                                                )
                                            },
                                            actions = {
                                                if (showHistoryButton) {
                                                    IconButton(onClick = { navController.navigate("history") }) {
                                                        if (profilePicStr.isNotEmpty()) {
                                                            coil3.compose.AsyncImage(
                                                                model = if (profilePicStr.startsWith("file://")) profilePicStr else "file://$profilePicStr",
                                                                contentDescription = "Profile",
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Icon(
                                                                painter = painterResource(R.drawable.music_history),
                                                                contentDescription = stringResource(R.string.history)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            scrollBehavior = topAppBarScrollBehavior,
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.windowInsetsPadding(
                                                if (showRail) {
                                                    WindowInsets(left = NavigationBarHeight)
                                                        .add(cutoutInsets.only(WindowInsetsSides.Start))
                                                } else {
                                                    cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                                }
                                            )
                                        )
                                    }
                                }
                            },
                            bottomBar = {
                                val onNavItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        navController.onTabTapped(screen, isSelected) {
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        }
                                    }
                                }

                                // Two quick taps on Search open its field, ready to type. (A hold used
                                // to open song recognition, whose page is gone: it crashed the app.)
                                val onSearchDoubleTap: () -> Unit = remember(navController) {
                                    { navController.requestSearchFocus() }
                                }

                                val navBarTotalHeight = bottomInset + NavigationBarHeight

                                if (!showRail && currentRoute != "wrapped" && currentRoute != "update") {

                                    val detailAccent = LocalDetailAccentColor.current.value
                                    val navScrimTint by androidx.compose.animation.animateColorAsState(
                                        targetValue = detailAccent ?: Color.Black,
                                        animationSpec = tween(500),
                                        label = "navScrimTint"
                                    )

                                    Box {

                                        if (!hideAppChrome.value) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .launchRise(order = 3)
                                                .fillMaxWidth()
                                                .height(bottomInset + navPadding + 40.dp)
                                                // the tint fades over 500 ms, read it while drawing so the bar doesn't recompose
                                                .drawBehind {
                                                    val tint = navScrimTint
                                                    drawRect(
                                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                tint.copy(alpha = 0.5f),
                                                                tint.copy(alpha = 0.9f),
                                                                tint
                                                            )
                                                        )
                                                    )
                                                }
                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    translationY = if (navBarHeightPx == 0f) {
                                                        totalHeightPx
                                                    } else {
                                                        val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                        val slideOffset = totalHeightPx * progress
                                                        val hideOffset = totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                        slideOffset + hideOffset
                                                    }
                                                }
                                        )

                                        }

                                        if (!hideAppChrome.value) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                            modifier = Modifier.launchRise(order = 3),
                                        )

                                        AppNavigationBar(
                                            navigationItems = navigationItems,
                                            currentRoute = currentRoute,
                                            selectedTabRoute = selectedTabRoute,
                                            onItemClick = onNavItemClick,
                                            pureBlack = pureBlack,
                                            slimNav = slimNav,
                                            onSearchDoubleTap = onSearchDoubleTap,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .launchRise(order = 3)
                                                .height(bottomInset + navPadding)

                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)

                                                    translationY = if (navBarHeightPx == 0f) {
                                                        totalHeightPx
                                                    } else {
                                                        val slideOffset = totalHeightPx * progress
                                                        val hideOffset = totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                        slideOffset + hideOffset
                                                    }

                                                    alpha = (1f - progress / 0.6f).coerceIn(0f, 1f)
                                                }
                                        )
                                        }
                                    }
                                } else {
                                    if (currentRoute != "wrapped" && currentRoute != "update") {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack
                                        )
                                    }

                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                val onRailItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        navController.onTabTapped(screen, isSelected) {
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        }
                                    }
                                }

                                val onRailSearchDoubleTap: () -> Unit = remember(navController) {
                                    { navController.requestSearchFocus() }
                                }

                                if (showRail && currentRoute != "wrapped" && currentRoute != "update") {
                                    AppNavigationRail(
                                        navigationItems = navigationItems,
                                        currentRoute = currentRoute,
                                        selectedTabRoute = selectedTabRoute,
                                        onItemClick = onRailItemClick,
                                        pureBlack = pureBlack,
                                        onSearchDoubleTap = onRailSearchDoubleTap
                                    )
                                }
                                Box(
                                    Modifier
                                        .weight(1f)

                                        .graphicsLayer {
                                            val p = if (BackgroundZoomOnExpand) {
                                                playerBottomSheetState.progress.coerceIn(0f, 1f)
                                            } else 0f
                                            if (p > 0.001f) {
                                                val scale = 1f - 0.06f * p
                                                scaleX = scale
                                                scaleY = scale

                                                alpha = ((1f - p) / 0.25f).coerceIn(0f, 1f)
                                                // While the player slides over it the app behind
                                                // doesn't change, but the zoom made it redraw in
                                                // full every frame - every image, every text glyph
                                                // re-rasterised at the new scale, its own glass
                                                // blurs. Kept in a layer, it is drawn once and the
                                                // zoom and fade move that. Not once the player is
                                                // fully open: at alpha 0 it isn't drawn at all.
                                                if (p < 0.999f) {
                                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                                }
                                            }

                                        }
                                ) {

                                    // from the moment the player starts to open, not only once it's
                                    // all the way up. While the sheet moves, the app behind is kept
                                    // in one cached layer (above); anything still animating in it -
                                    // the hero's video, the gacha's drift, the playing bars - made
                                    // that whole layer render again on each of its frames, all
                                    // through the open and the close
                                    val appContentObscured = remember(playerBottomSheetState) {
                                        derivedStateOf { playerBottomSheetState.progress > 0.001f }
                                    }
                                    SharedTransitionLayout {
                                    CompositionLocalProvider(
                                        LocalSharedTransitionScope provides this,
                                        LocalAppContentObscured provides appContentObscured,
                                    ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.SEARCH -> Screens.Search
                                            else -> Screens.Home
                                        }.route,

                                        // each page slides by the tab it belongs to, not by its own route: an
                                        // album open in the Library moves like the Library when you switch tabs
                                        enterTransition = {
                                            defaultMotionEnter {
                                                val stack = navController.currentBackStack.value
                                                val currentRouteIndex = tabOwnership.indexOf(targetState, stack)
                                                val previousRouteIndex = tabOwnership.indexOf(initialState, stack)

                                                if (currentRouteIndex == -1 || currentRouteIndex >= previousRouteIndex)
                                                    slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                                else
                                                    slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                            }
                                        },

                                        exitTransition = {
                                            defaultMotionExit {
                                                val stack = navController.currentBackStack.value
                                                val currentRouteIndex = tabOwnership.indexOf(initialState, stack)
                                                val targetRouteIndex = tabOwnership.indexOf(targetState, stack)

                                                if (targetRouteIndex == -1 || targetRouteIndex >= currentRouteIndex)
                                                    slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                                else
                                                    slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                            }
                                        },

                                        popEnterTransition = {
                                            defaultMotionEnter {
                                                val stack = navController.currentBackStack.value
                                                val currentRouteIndex = tabOwnership.indexOf(targetState, stack)
                                                val previousRouteIndex = tabOwnership.indexOf(initialState, stack)

                                                if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                                    slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                                else
                                                    slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                            }
                                        },

                                        popExitTransition = {
                                            defaultMotionExit {
                                                val stack = navController.currentBackStack.value
                                                val currentRouteIndex = tabOwnership.indexOf(initialState, stack)
                                                val targetRouteIndex = tabOwnership.indexOf(targetState, stack)

                                                if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                                    slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                                else
                                                    slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                            }
                                        },
                                        modifier = Modifier
                                            .glassRoot(glassState, isActive = { true })
                                    ) {
                                        navigationBuilder(
                                            navController = navController,
                                            scrollBehavior = topAppBarScrollBehavior,
                                            activity = this@MainActivity,
                                            snackbarHostState = snackbarHostState
                                        )
                                    }
                                    }
                                    }
                                }
                            }
                        }
                    }
                    }

                    // action feedback ("Added to Queue", "Undo"...) drops in over the sheets too
                    com.mochyy.mochymusic.ui.component.TopToastHost()

                    // "share song" from any menu opens here, over the app and the player
                    com.mochyy.mochymusic.ui.player.share.song.SongShareHost()

                    HomeUpdatePrompt(currentRoute = currentRoute)

                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LaunchIntroOverlay(
                        state = launchIntro,
                        essentialsReady = playerConnection != null && themeSettled,
                    )

                }
            }
        }
    }

    // fastest mode at the current screen size; android 15+ needs it too, left alone it kept lyrics and the turntable at 60hz on 120hz phones
    @Suppress("DEPRECATION")
    private fun highRefreshModeId(): Int {
        return try {
            val display = window.windowManager.defaultDisplay
            val current = display.mode
            display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.modeId ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // reads the sheet state here, so its flips don't recompose the whole app root
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PlayerSystemBarsSync(state: BottomSheetState) {
        // The open player hides the navigation bar: gone once the sheet sits at the top,
        // back as soon as it heads down - not whenever it stops sitting exactly at the top
        // (a resize it is catching up with), which brought the bar back over the open player.
        val playerOpen = state.isExpanded
        val playerHeadingDown = !state.isExpandedOrExpanding
        // the status bar goes too when customize's top bar is off, on every player style
        val (statusBarShown) = rememberPreference(com.mochyy.mochymusic.constants.PlayerStatusBarKey, defaultValue = com.mochyy.mochymusic.constants.PlayerStatusBarDefault)
        // the full video mode fills the screen sideways, a status bar down one side of it would cut into the picture
        val (playerStyle) = com.mochyy.mochymusic.utils.rememberEnumPreference(
            com.mochyy.mochymusic.constants.PlayerStyleKey,
            com.mochyy.mochymusic.constants.PlayerStyle.FLAGSHIP,
        )
        // and the bedside is a clock on the nightstand, a row of notifications over it would only glow in the dark
        val hideStatusBar = !statusBarShown || playerStyle == com.mochyy.mochymusic.constants.PlayerStyle.VIDEO || playerStyle.isBedside
        // customize's bottom bar brings the navigation bar back, except where it would cut into the video or glow by the bed
        val (navBarShown) = rememberPreference(com.mochyy.mochymusic.constants.PlayerNavBarKey, defaultValue = com.mochyy.mochymusic.constants.PlayerNavBarDefault)
        val hideNavBar = !navBarShown || playerStyle == com.mochyy.mochymusic.constants.PlayerStyle.VIDEO || playerStyle.isBedside
        // Back in the app (from Home, the recents, a dialog) it's hidden again: with
        // gesture navigation a swipe from the bottom brings it back for good (see
        // playerNavBarBehavior).
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(playerOpen, playerHeadingDown, windowFocused, hideNavBar) {
            val controller = WindowCompat.getInsetsController(window, window.decorView.rootView)
            // one behaviour for both bars, set only here: the ipod used to set its own and broke the back swipe on gesture navigation
            if (playerOpen && hideNavBar) {
                controller.systemBarsBehavior = window.playerNavBarBehavior()
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else if (playerHeadingDown || !hideNavBar) {
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
        // the status bar leaves during the last stretch of the open, so its slide runs with the player's instead of after it lands
        val statusBarAway by remember(state) {
            derivedStateOf { state.isExpanded || (state.isExpandedOrExpanding && state.progress > StatusBarLeavesAt) }
        }
        LaunchedEffect(statusBarAway, hideStatusBar, windowFocused) {
            val controller = WindowCompat.getInsetsController(window, window.decorView.rootView)
            if (statusBarAway && hideStatusBar) {
                controller.systemBarsBehavior = window.playerNavBarBehavior()
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        // set before the bar ever hides and left alone after, so hiding it never moves the window off the camera strip
        val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        LaunchedEffect(hideStatusBar, portrait) {
            window.drawUnderCutout(hideStatusBar && portrait)
        }
        // A bar a swipe brought back while the player stays open (Samsung's swipe-from-
        // bottom gestures) goes away again after a moment, as the transient one does.
        val navBarsShown = WindowInsets.areNavigationBarsVisible
        LaunchedEffect(playerOpen, navBarsShown, hideNavBar) {
            if (!playerOpen || !navBarsShown || !hideNavBar) return@LaunchedEffect
            delay(2_500)
            WindowCompat.getInsetsController(window, window.decorView.rootView)
                .hide(WindowInsetsCompat.Type.navigationBars())
        }
        // same for a status bar pulled down over a player that hides it
        val statusBarsShown = WindowInsets.areStatusBarsVisible
        LaunchedEffect(playerOpen, statusBarsShown, hideStatusBar) {
            if (!playerOpen || !statusBarsShown || !hideStatusBar) return@LaunchedEffect
            delay(2_500)
            WindowCompat.getInsetsController(window, window.decorView.rootView)
                .hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // copies the animation, element blur and cover rounding settings where draw code and the animation scale read them, the moment they're saved
    private fun followLookSettings() {
        lifecycleScope.launch {
            dataStore.data
                .map { prefs ->
                    LookSettings(
                        animations = prefs[com.mochyy.mochymusic.constants.EnableElementBlurAnimatorKey] ?: true,
                        speed = prefs[com.mochyy.mochymusic.constants.AnimationSpeedKey] ?: 1f,
                        elementBlur = prefs[com.mochyy.mochymusic.constants.ElementBlurKey] ?: true,
                        coverRadius = prefs[com.mochyy.mochymusic.constants.ThumbnailCornerRadiusKey] ?: 25f,
                    )
                }
                .distinctUntilChanged()
                .collect { look ->
                    com.mochyy.mochymusic.ui.utils.AppMotion.enabled = look.animations
                    com.mochyy.mochymusic.ui.utils.AppMotion.speed = look.speed
                    com.mochyy.mochymusic.ui.utils.AppMotion.elementBlur = look.elementBlur
                    val r = look.coverRadius.coerceIn(0f, 45f)
                    com.mochyy.mochymusic.ui.component.HomeCardCornerRadius = r.dp
                    com.mochyy.mochymusic.constants.GridThumbnailCornerRadius = (r * 0.6f).coerceIn(0f, 28f).dp
                    com.mochyy.mochymusic.constants.ThumbnailCornerRadius = (r * 0.35f).coerceIn(0f, 16f).dp
                }
        }
    }

    private data class LookSettings(val animations: Boolean, val speed: Float, val elementBlur: Boolean, val coverRadius: Float)

    // people who had the old lyrics shader switch off land on the matching shaderless lyric style, once
    private fun moveOffOldLyricsShaderSwitch() {
        if (dataStore[com.mochyy.mochymusic.constants.LyricsWaveAnimationKey] != false) return
        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                if (prefs[com.mochyy.mochymusic.constants.LyricsWaveAnimationKey] != false) return@edit
                val stored = prefs[com.mochyy.mochymusic.constants.LyricsMotionStyleKey]
                val style = com.mochyy.mochymusic.constants.LyricsMotionStyle.entries.firstOrNull { it.name == stored }
                    ?: com.mochyy.mochymusic.constants.LyricsMotionStyle.MOTION
                prefs[com.mochyy.mochymusic.constants.LyricsMotionStyleKey] = style.withoutShader.name
                prefs.remove(com.mochyy.mochymusic.constants.LyricsWaveAnimationKey)
            }
        }
    }

    /** Audio files shared to Mochy from another app or a file manager are imported as local songs. */
    private fun importSharedAudio(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false
        val uris = if (intent.action == Intent.ACTION_SEND) {
            listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java))
        } else {
            androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java).orEmpty()
        }
        if (uris.isEmpty()) return false
        // handled once: the activity being recreated would otherwise import them again
        intent.action = null
        intent.removeExtra(Intent.EXTRA_STREAM)
        lifecycle.coroutineScope.launch {
            var done = 0
            for (uri in uris) {
                try {
                    com.mochyy.mochymusic.importer.importSharedAudio(applicationContext, database, uri)
                    done++
                } catch (e: Exception) {
                    reportException(e)
                }
            }
            com.mochyy.mochymusic.ui.component.TopToaster.show(
                if (done == 0) "Couldn't import that" else "Imported $done song${if (done != 1) "s" else ""} to Mochy",
            )
        }
        return true
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        if (importSharedAudio(intent)) return
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                withContext(Dispatchers.Main) {
                                    navController.navigate("album/$browseId")
                                }
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                }
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(videoId), playlistId).onSuccess { queue ->
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                        queue.firstOrNull()?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(null, playlistId).onSuccess { queue ->
                            val firstItem = queue.firstOrNull()
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                        firstItem?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val SubSettingsRoutes = setOf(
    "experimental_settings",
    "cipher_settings",
    "playback_diagnostics",
    "mochy_settings",
    "import_sync",
    "import_providers",
    "import_tunemymusic",
    "import_progress",
    "youtube_sync",
    "stats",
    "developer_space",
    "developer_playground",
    "storage_settings",
    "gesture_settings",
    "customisation_settings",
    // Audio Lab keeps the bar, as in its designs
    "audio_lab",
    "equalizer",
    "audio_fx",
    "audio_quality",
    "crossmix_settings",
    "modules",
    "module/{id}",
)

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalGlassState = staticCompositionLocalOf<GlassState?> { null }

/**
 * True while the app's screens are entirely covered (the player fully open). Animations that run
 * on their own forever - a playing indicator's bars, Home's hero video and gacha drift - wait it
 * out instead of drawing frames nobody can see. Only provided around the app's screens; the player
 * itself never sees it set.
 */
val LocalAppContentObscured = staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> { AppContentNeverObscured }

private object AppContentNeverObscured : androidx.compose.runtime.State<Boolean> {
    override val value: Boolean get() = false
}

val LocalHideAppChrome = staticCompositionLocalOf { mutableStateOf(false) }

private const val UpdateSnoozeWindowMillis = 24L * 60L * 60L * 1000L

@Composable
private fun HomeUpdatePrompt(currentRoute: String?) {
    if (currentRoute != Screens.Home.route) return
    val (noticeHidden) = rememberPreference(com.mochyy.mochymusic.constants.HideUpdateNoticeKey, defaultValue = false)
    if (noticeHidden) return

    val updateState by com.mochyy.mochymusic.ui.screens.update.rememberUpdateState()
    val (snoozedAt, setSnoozedAt) = rememberPreference(
        com.mochyy.mochymusic.constants.UpdatePromptSnoozedAtKey,
        defaultValue = 0L,
    )
    val (userName) = rememberPreference(com.mochyy.mochymusic.constants.UsernameKey, "")

    var dismissed by rememberSaveable { mutableStateOf(false) }

    val release = (updateState as? com.mochyy.mochymusic.core.updater.UpdateState.Available)?.release
    val snoozeExpired = System.currentTimeMillis() - snoozedAt > UpdateSnoozeWindowMillis

    if (release != null && snoozeExpired && !dismissed) {
        com.mochyy.mochymusic.ui.screens.update.UpdatePromptSheet(
            release = release,
            userName = userName,
            onSnooze = {
                setSnoozedAt(System.currentTimeMillis())
                dismissed = true
            },
            onDismiss = { dismissed = true },
        )
    }
}
val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalCropAlbumArt = compositionLocalOf { false }
val LocalGridItemSize = compositionLocalOf { GridItemSize.BIG }
val LocalSwipeToSong = compositionLocalOf { false }
val LocalIsPlayerExpanded = compositionLocalOf { false }

/**
 * Colour extracted from the current track's artwork.
 *
 * Exposed so Material 3 surfaces in the player can be seeded from the music rather than the device
 * wallpaper - which is the whole point of the M3 player styles.
 */
val LocalArtworkColor = androidx.compose.runtime.compositionLocalOf { DefaultThemeColor }

val LocalDetailAccentColor = staticCompositionLocalOf<androidx.compose.runtime.MutableState<Color?>> {
    androidx.compose.runtime.mutableStateOf(null)
}

/**
 * How the navigation bar hidden under the open player comes back. The transient mode (a swipe
 * shows it over the player for a moment) is the nicer one, but with gesture navigation the system
 * turns the back gesture off while a bar is hidden that way: the first back swipe only brought the
 * bar in, and for many the open player looked impossible to close. The default mode keeps the
 * back gesture working with the bar hidden, so gesture navigation gets that one; buttons (a bar
 * you can tap) keep the transient mode. Gesture navigation is told apart by the bar having
 * nothing tappable in it - known for certain from Android 11, where the hidden bar's insets can
 * be read.
 */
// how far open the player is when its status bar starts sliding away
private const val StatusBarLeavesAt = 0.82f

// with the status bar hidden android 9 to 14 paints a notched phone's camera strip black unless the window may draw there
private fun android.view.Window.drawUnderCutout(on: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val mode = if (on) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }
    if (attributes.layoutInDisplayCutoutMode == mode) return
    attributes = attributes.apply { layoutInDisplayCutoutMode = mode }
}

private fun android.view.Window.playerNavBarBehavior(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val tappable = androidx.core.view.ViewCompat.getRootWindowInsets(decorView)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.tappableElement())
        if (tappable != null && tappable.left == 0 && tappable.right == 0 && tappable.bottom == 0) {
            return WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
    return WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
