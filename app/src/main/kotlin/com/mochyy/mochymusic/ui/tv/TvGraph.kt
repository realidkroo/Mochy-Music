// TvGraph.kt

package com.mochyy.mochymusic.ui.tv

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mochyy.mochymusic.link.tv.TvProfile

internal object TvRoutes {
    const val Home = "tv_home"
    const val Search = "tv_search"
    const val Library = "tv_library"
    const val Phone = "tv_phone"
    const val Recap = "tv_recap"
    const val Browse = "tv_browse"
    const val Songs = "tv_songs"
    const val Grid = "tv_grid"
}

// the tv's own pages; album, artist and playlist take the phone's route names so links from the player land here
internal fun NavGraphBuilder.tvGraph(profile: TvProfile) {
    composable(TvRoutes.Home) { TvHomeScreen(profile) }
    composable(
        "${TvRoutes.Search}?q={q}",
        arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { TvSearchScreen() }
    composable(TvRoutes.Library) { TvLibraryScreen() }
    composable(TvRoutes.Phone) { TvPhoneScreen(profile) }
    composable(TvRoutes.Recap) { TvRecapScreen() }
    composable(
        "${TvRoutes.Browse}/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType },
            navArgument("params") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { TvBrowseScreen() }
    composable("${TvRoutes.Songs}/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { TvSongsScreen() }
    composable("${TvRoutes.Grid}/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
        TvGridScreen(entry.arguments?.getString("kind").orEmpty())
    }
    composable("album/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) { TvAlbumScreen() }
    composable("artist/{artistId}", arguments = listOf(navArgument("artistId") { type = NavType.StringType })) { TvArtistScreen() }
    composable("online_playlist/{playlistId}", arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) {
        TvOnlinePlaylistScreen()
    }
    composable("local_playlist/{playlistId}", arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) {
        TvLocalPlaylistScreen()
    }
}
