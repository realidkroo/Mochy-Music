// NavigationBuilder.kt

package com.mochyy.mochymusic.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mochyy.mochymusic.db.entities.FormatEntity
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable as navigationComposable
import androidx.navigation.compose.dialog
import com.mochyy.mochymusic.ui.utils.motionEnter
import com.mochyy.mochymusic.ui.utils.motionExit
import androidx.navigation.navArgument
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.constants.DarkModeKey
import com.mochyy.mochymusic.ui.theme.ForceDarkTheme
import com.mochyy.mochymusic.constants.PlaylistSortType
import com.mochyy.mochymusic.constants.PureBlackKey
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.ui.component.ContainerCloseEasing
import com.mochyy.mochymusic.ui.component.ContainerCloseMillis
import com.mochyy.mochymusic.ui.component.ContainerOpenDamping
import com.mochyy.mochymusic.ui.component.ContainerOpenStiffness
import com.mochyy.mochymusic.ui.component.LocalNavAnimatedContentScope
import com.mochyy.mochymusic.ui.component.NavigationTitle
import com.mochyy.mochymusic.ui.screens.artist.ArtistAlbumsScreen
import com.mochyy.mochymusic.ui.screens.artist.ArtistItemsScreen
import com.mochyy.mochymusic.ui.screens.artist.ArtistScreen
import com.mochyy.mochymusic.ui.screens.artist.ArtistSongsScreen
import com.mochyy.mochymusic.ui.screens.playlist.AutoPlaylistScreen
import com.mochyy.mochymusic.ui.screens.playlist.CachePlaylistScreen
import com.mochyy.mochymusic.ui.screens.playlist.LocalPlaylistScreen
import com.mochyy.mochymusic.ui.screens.playlist.OnlinePlaylistScreen
import com.mochyy.mochymusic.ui.screens.playlist.TopPlaylistScreen
import com.mochyy.mochymusic.ui.screens.search.GenreScreen
import com.mochyy.mochymusic.ui.screens.search.OnlineSearchResult
import com.mochyy.mochymusic.ui.screens.search.SearchScreen
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.screens.settings.ProfileScreen
import com.mochyy.mochymusic.ui.screens.library.LibraryAlbumsScreen
import com.mochyy.mochymusic.ui.screens.SectionDetailScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.viewmodels.HomeViewModel
import com.mochyy.mochymusic.ui.screens.library.LibraryArtistsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

private val CoverRoutes = listOf("album/", "local_playlist/", "online_playlist/", "auto_playlist/")

private fun String?.opensFromCover(): Boolean = this != null && CoverRoutes.any { startsWith(it) }

// Home moves on the card's clocks: the open spring going out, the close ease coming back. the
// tiny threshold keeps the spring from snapping the last 1% of the scale at the end.
private val HomeRecedeSpring = spring(
    dampingRatio = ContainerOpenDamping,
    stiffness = ContainerOpenStiffness,
    visibilityThreshold = 0.001f,
)
private val HomeReturnEase = tween<Float>(ContainerCloseMillis, easing = ContainerCloseEasing)

/** Library screens recede like Home when a cover opens from them, and come back on the same clock. */
private fun libraryExitTo(route: String?): androidx.compose.animation.ExitTransition? =
    if (route.opensFromCover()) {
        scaleOut(HomeRecedeSpring, targetScale = 0.92f) + fadeOut(HomeRecedeSpring, targetAlpha = 0.45f)
    } else {
        null
    }

/** coming back from a cover page: the same clock as Home; anything else keeps the default */
private fun coverReturnFrom(route: String?): androidx.compose.animation.EnterTransition? =
    if (route.opensFromCover()) {
        scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
    } else {
        null
    }

/**
 * a screen an album or playlist can be opened from (the Library's pages, Search, an artist,
 * Home's section pages): it hands its transition scope to its covers, so the page grows out of
 * the one tapped, and it sinks back while the page is open, exactly as Home does
 */
private fun NavGraphBuilder.coverSourceComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    exitTransition = { libraryExitTo(targetState.destination.route) },
    popEnterTransition = { coverReturnFrom(initialState.destination.route) },
) { entry ->
    CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
        content(entry)
    }
}

private fun libraryReturnFrom(route: String?): androidx.compose.animation.EnterTransition =
    if (route.opensFromCover()) {
        scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
    } else {
        fadeIn(tween(150))
    }

private typealias NavEnter =
    androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition?
private typealias NavExit =
    androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition?

/**
 * Every page of this graph goes through here rather than straight to navigation's composable(), so
 * its own transitions answer Settings > Appearance > Animation (#10): off, the page swaps in place.
 * Same parameters and defaults as navigation's (a pop transition falls back to the push one, and a
 * transition that isn't given, or gives null, falls back to the NavHost's).
 */
private fun NavGraphBuilder.composable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    enterTransition: NavEnter? = null,
    exitTransition: NavExit? = null,
    popEnterTransition: NavEnter? = enterTransition,
    popExitTransition: NavExit? = exitTransition,
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = navigationComposable(
    route = route,
    arguments = arguments,
    enterTransition = { motionEnter { enterTransition?.invoke(this) } },
    exitTransition = { motionExit { exitTransition?.invoke(this) } },
    popEnterTransition = { motionEnter { popEnterTransition?.invoke(this) } },
    popExitTransition = { motionExit { popExitTransition?.invoke(this) } },
    content = content,
)

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState
) {
    composable(
        route = Screens.Home.route,
        // opening a cover: Home sinks back and dims while the card grows toward you, then comes
        // forward again on the way back, in step with the card so a back swipe scrubs both
        exitTransition = {
            if (targetState.destination.route.opensFromCover()) {
                scaleOut(HomeRecedeSpring, targetScale = 0.92f) + fadeOut(HomeRecedeSpring, targetAlpha = 0.45f)
            } else {
                null
            }
        },
        popEnterTransition = {
            if (initialState.destination.route.opensFromCover()) {
                scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
            } else {
                fadeIn(tween(150))
            }
        },
    ) {

        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
        }
    }

    coverSourceComposable(
        route = "section_detail/{sectionId}",
        arguments = listOf(
            navArgument("sectionId") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionId = backStackEntry.arguments?.getString("sectionId") ?: return@coverSourceComposable

        val homeViewModel: HomeViewModel = hiltViewModel(navController.getBackStackEntry(Screens.Home.route))
        SectionDetailScreen(
            navController = navController,
            sectionId = sectionId,
            homeViewModel = homeViewModel
        )
    }

    coverSourceComposable("history") {
        HistoryScreen(navController = navController)
    }

    composable(
        route = "coming_soon/{sectionTitle}",
        arguments = listOf(
            navArgument("sectionTitle") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionTitle = backStackEntry.arguments?.getString("sectionTitle") ?: return@composable
        ComingSoonScreen(navController = navController, sectionTitle = sectionTitle)
    }

    coverSourceComposable(Screens.Search.route) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }
        SearchScreen(
            navController = navController,
            pureBlack = pureBlack
        )
    }

    composable(
        route = "library",
        // a playlist opening from its cover: the Library sinks back while it grows, as Home does
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            com.mochyy.mochymusic.ui.screens.library.LibraryHomeScreen(
                navController = navController,
                pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme },
            )
        }
    }

    coverSourceComposable("library/songs") {
        com.mochyy.mochymusic.ui.screens.library.LibrarySongsScreen(navController = navController)
    }

    composable(
        route = "library/playlists",
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) {
        // the scope a playlist's cover needs to grow into its page, as on Home
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            com.mochyy.mochymusic.ui.screens.library.LibraryPlaylistsScreen(navController = navController)
        }
    }

    coverSourceComposable("library/added") {
        com.mochyy.mochymusic.ui.screens.library.LibraryRecentlyAddedScreen(navController = navController)
    }

    // The Library's Local and Cached tabs open these: the same pages again, counting only that
    // tab's songs. "library/local/albums", "library/cached/albums" and so on.
    com.mochyy.mochymusic.viewmodels.LibraryShelfKind.entries.forEach { shelf ->
        coverSourceComposable("library/${shelf.key}/added") {
            com.mochyy.mochymusic.ui.screens.library.LibraryRecentlyAddedScreen(navController = navController, shelf = shelf)
        }

        coverSourceComposable("library/${shelf.key}/albums") {
            com.mochyy.mochymusic.ui.screens.library.LibraryAlbumsScreen(navController = navController, shelf = shelf)
        }

        coverSourceComposable("library/${shelf.key}/artists") {
            com.mochyy.mochymusic.ui.screens.library.LibraryArtistsScreen(navController = navController, shelf = shelf)
        }

        composable(
            route = "library/${shelf.key}/playlists",
            exitTransition = { libraryExitTo(targetState.destination.route) },
            popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
        ) {
            CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                com.mochyy.mochymusic.ui.screens.library.LibraryPlaylistsScreen(navController = navController, shelf = shelf)
            }
        }
    }

    // Everything one service brought in, whatever playlist it went into: Imported from Spotify.
    coverSourceComposable(
        route = "library/imported/{source}",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
    ) {
        com.mochyy.mochymusic.ui.screens.library.LibraryImportedScreen(navController = navController)
    }

    // The Imported tab's Playlists cards: the playlists one service brought in.
    composable(
        route = "library/imported/{source}/playlists",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            com.mochyy.mochymusic.ui.screens.library.LibraryPlaylistsScreen(
                navController = navController,
                source = com.mochyy.mochymusic.importer.ImportSource.fromKey(entry.arguments?.getString("source")),
            )
        }
    }

    // the Imported tab's Albums cards: the albums one service's songs come from
    coverSourceComposable(
        route = "library/imported/{source}/albums",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
    ) {
        com.mochyy.mochymusic.ui.screens.library.LibraryAlbumsScreen(
            navController = navController,
            source = com.mochyy.mochymusic.importer.ImportSource.fromKey(it.arguments?.getString("source")),
        )
    }

    composable(Screens.Settings.route) {
        ProfileScreen(navController = navController)
    }

    composable("stats") {
        com.mochyy.mochymusic.ui.screens.recap.StatsScreen(navController = navController)
    }

    composable("cipher_settings") {
        com.mochyy.mochymusic.ui.screens.settings.CipherSettingsScreen(navController = navController)
    }

    composable("playback_diagnostics") {
        com.mochyy.mochymusic.ui.screens.settings.PlaybackDiagnosticsScreen(navController = navController)
    }

    composable("mochy_settings") {
        com.mochyy.mochymusic.ui.screens.settings.MochySettingsScreen(navController = navController)
    }

    // app behaviour kept general's route, so old links and search results still land on it
    composable("general_settings") {
        com.mochyy.mochymusic.ui.screens.settings.AppBehaviourSettingsScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute) {
        com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute) {
        com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordScreen(navController = navController)
    }

    composable(
        route = com.mochyy.mochymusic.ui.screens.settings.integrations.ScrobbleServiceRoute,
        arguments = listOf(navArgument("site") { type = NavType.StringType }),
    ) { entry ->
        val site = com.mochyy.mochymusic.scrobble.ScrobbleSite.entries
            .find { it.name == entry.arguments?.getString("site") }
            ?: com.mochyy.mochymusic.scrobble.ScrobbleSite.LASTFM
        com.mochyy.mochymusic.ui.screens.settings.integrations.ScrobbleServiceScreen(navController = navController, site = site)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.ProxySettingsRoute) {
        com.mochyy.mochymusic.ui.screens.settings.ProxySettingsScreen(navController = navController)
    }

    composable("crossmix_settings") {
        com.mochyy.mochymusic.ui.screens.settings.CrossmixSettingsScreen(navController = navController)
    }

    composable(
        route = "developer_space?person={person}",
        arguments = listOf(navArgument("person") { type = NavType.IntType; defaultValue = 0 }),
    ) { entry ->
        val person = entry.arguments?.getInt("person") ?: 0
        com.mochyy.mochymusic.ui.screens.settings.devspace.DeveloperSpaceScreen(
            navController = navController,
            initialPage = person,
        )
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundRoute) {
        com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.storage.StorageRoute) {
        com.mochyy.mochymusic.ui.screens.settings.storage.StorageScreen(navController = navController)
    }

    composable("experimental_settings") {
        com.mochyy.mochymusic.ui.screens.settings.ExperimentalSettingsScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProvidersRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProvidersScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportTuneMyMusicRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.TuneMyMusicImportScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProgressRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProgressScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncScreen(navController = navController)
    }

    composable(
        route = com.mochyy.mochymusic.ui.screens.settings.importsync.AccountImportRoutePattern,
        arguments = listOf(
            navArgument("service") { type = NavType.StringType },
            // what a Re-sync opened the page for, and the services Re-sync all goes on to
            navArgument("resync") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("then") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.AccountImportScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeLoginRoute) {
        com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeLoginScreen(navController = navController)
    }

    composable("equalizer") {
        com.mochyy.mochymusic.ui.screens.equalizer.EqualizerScreen(navController = navController)
    }

    composable("gesture_settings") {
        com.mochyy.mochymusic.ui.screens.gesture.GestureScreen(navController = navController)
    }

    composable("customisation_settings") {
        com.mochyy.mochymusic.ui.screens.gesture.CustomisationScreen(navController = navController)
    }

    composable("audio_lab") {
        com.mochyy.mochymusic.ui.screens.audiolab.AudioLabScreen(navController = navController)
    }

    composable("audio_fx") {
        com.mochyy.mochymusic.ui.screens.audiolab.AudioFxScreen(navController = navController)
    }

    composable("audio_quality") {
        com.mochyy.mochymusic.ui.screens.audiolab.AudioQualityScreen(navController = navController)
    }

    composable("audio_pass") {
        com.mochyy.mochymusic.ui.screens.audiolab.AudioPassScreen(navController = navController)
    }

    composable(com.mochyy.mochymusic.ui.screens.settings.modules.ModulesRoute) {
        com.mochyy.mochymusic.ui.screens.settings.modules.ModulesScreen(navController = navController)
    }

    composable(
        route = com.mochyy.mochymusic.ui.screens.settings.modules.ModuleRoutePattern,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { entry ->
        com.mochyy.mochymusic.ui.screens.settings.modules.ModuleScreen(
            navController = navController,
            id = entry.arguments?.getString("id").orEmpty(),
        )
    }

    composable(
        route = com.mochyy.mochymusic.ui.screens.settings.modules.ModulePageRoutePattern,
        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("page") { type = NavType.StringType }),
    ) { entry ->
        com.mochyy.mochymusic.ui.screens.settings.modules.ModulePageScreen(
            navController = navController,
            id = entry.arguments?.getString("id").orEmpty(),
            pageId = entry.arguments?.getString("page").orEmpty(),
        )
    }

    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }

    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            when {
                targetState.destination.route.opensFromCover() -> libraryExitTo(targetState.destination.route)
                targetState.destination.route?.startsWith("search/") == true -> fadeOut(tween(200))
                else -> fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            when {
                initialState.destination.route.opensFromCover() -> coverReturnFrom(initialState.destination.route)
                initialState.destination.route?.startsWith("search/") == true -> fadeIn(tween(250))
                else -> fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            OnlineSearchResult(navController)
        }
    }

    composable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),

        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AlbumScreen(navController, scrollBehavior)
        }
    }

    coverSourceComposable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    // "similar to <artist>": the artists, playlists and songs around one artist, from its page
    coverSourceComposable(
        route = "artist/{artistId}/similar",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.mochyy.mochymusic.ui.screens.artist.ArtistSimilarScreen(navController)
    }

    coverSourceComposable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            OnlinePlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "genre/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        GenreScreen(
            navController = navController,
            pureBlack = remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            },
        )
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    coverSourceComposable("library/albums") {
        com.mochyy.mochymusic.ui.screens.library.LibraryAlbumsScreen(navController = navController)
    }

    coverSourceComposable("library/artists") {
        com.mochyy.mochymusic.ui.screens.library.LibraryArtistsScreen(navController = navController)
    }
}

internal data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val embeddedArtwork: ByteArray?,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
)

internal fun extractAudioMetadata(
    context: android.content.Context,
    uri: Uri,
): LocalAudioMetadata {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown"
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            ?: "Unknown Artist"
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            ?: context.contentResolver.getType(uri)
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull()
        val sampleRate = try {

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                retriever.extractMetadata(38)?.toIntOrNull()
            } else null
        } catch (_: Exception) { null }
        val embeddedArtwork = try { retriever.embeddedPicture } catch (_: Exception) { null }
        // track and disc tags often read "3/12", the part before the slash is the number
        fun number(key: Int) = retriever.extractMetadata(key)?.substringBefore('/')?.trim()?.toIntOrNull()
        LocalAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = (durationMs / 1000).toInt(),
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            embeddedArtwork = embeddedArtwork,
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            trackNumber = number(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
            discNumber = number(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
        )
    } catch (e: Exception) {

        LocalAudioMetadata(
            title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown",
            artist = "Unknown Artist",
            album = null,
            durationSeconds = 0,
            mimeType = context.contentResolver.getType(uri),
            bitrate = null,
            sampleRate = null,
            embeddedArtwork = null,
        )
    } finally {
        retriever.release()
    }
}
