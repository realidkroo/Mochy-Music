// LibraryArtistsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

/**
 * The library's artists, or with a [shelf] the Local or Cached tab's: only the artists of that
 * tab's songs, each with how many of its songs are there.
 */
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val artists by (shelf?.let(viewModel::shelf)?.artists ?: viewModel.artists).collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalContext.current.resources

    LibraryEntityListScreen(
        title = stringResource(R.string.lib_artist),
        subtitle = stringResource(
            when (shelf) {
                LibraryShelfKind.Local -> R.string.lib_artists_local_subtitle
                LibraryShelfKind.Cached -> R.string.lib_artists_cached_subtitle
                null -> R.string.lib_artists_all_subtitle
            },
        ),
        searchPlaceholder = stringResource(R.string.lib_artists_search),
        items = artists,
        idOf = { it.id },
        nameOf = { it.artist.name },
        subtitleOf = {
            val count = resources.getQuantityString(R.plurals.lib_song_count, it.songCount, it.songCount)
            when (shelf) {
                LibraryShelfKind.Local -> resources.getString(R.string.lib_count_on_device, count)
                LibraryShelfKind.Cached -> resources.getString(R.string.lib_count_cached, count)
                null -> count
            }
        },
        thumbnailOf = { it.artist.thumbnailUrl },
        roundThumbnails = true,
        onClick = { artist -> navController.navigate("artist/${artist.id}") },
        onLongClick = { artist ->
            menuState.show {
                ArtistMenu(
                    originalArtist = artist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
