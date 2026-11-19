// LibraryCollectionPages.kt
//
// The library's albums, artists and playlists on the shared collection page. Play and shuffle there
// reach every song across all of them, gathered off the main thread so a big library never stalls.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.menu.AlbumMenu
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.ui.menu.PlaylistMenu
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// gathers the songs off the main thread and plays them as one queue; shuffled, it's every song mixed, not the lists in a new order
private fun CoroutineScope.playGathered(player: PlayerConnection?, title: String, shuffle: Boolean, gather: suspend () -> List<Song>) {
    if (player == null) return
    launch {
        val items = withContext(Dispatchers.IO) {
            val songs = gather().distinctBy { it.id }
            (if (shuffle) songs.shuffled() else songs).map { it.toMediaItem() }
        }
        if (items.isNotEmpty()) player.playQueue(ListQueue(title = title, items = items))
    }
}

@Composable
internal fun LibraryAlbumsPage(navController: NavController) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.albums)

    LibraryCollectionPage(
        title = title,
        pageKey = "albums",
        items = albums,
        toEntry = { album ->
            CollectionEntry(
                id = album.id,
                title = album.title,
                subtitle = album.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = album.album.thumbnailUrl,
                sharedElementKey = "album-${album.id}",
            )
        },
        countText = { pluralStringResource(R.plurals.n_album, it, it) },
        searchPlaceholder = stringResource(R.string.library_albums_search),
        onOpen = { album, _ -> navController.navigate("album/${album.id}") },
        onMenu = { album ->
            menuState.show { AlbumMenu(originalAlbum = album, navController = navController, onDismiss = menuState::dismiss) }
        },
        onPlay = { ordered, shuffle ->
            // an album only knows its tracks once its page has been opened; before that, the library's songs from it stand in
            val library = songs
            scope.playGathered(player, title, shuffle) {
                val byAlbum = library.groupBy { it.song.albumId }
                ordered.flatMap { album -> database.albumSongs(album.id).first().ifEmpty { byAlbum[album.id].orEmpty() } }
            }
        },
        navController = navController,
    )
}

@Composable
internal fun LibraryArtistsPage(navController: NavController) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val artists by viewModel.artists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val resources = LocalContext.current.resources
    val player = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.artists)

    LibraryCollectionPage(
        title = title,
        pageKey = "artists",
        items = artists,
        toEntry = { artist ->
            CollectionEntry(
                id = artist.id,
                title = artist.artist.name,
                subtitle = resources.getQuantityString(R.plurals.lib_song_count, artist.songCount, artist.songCount),
                thumbnailUrl = artist.artist.thumbnailUrl,
            )
        },
        countText = { pluralStringResource(R.plurals.n_artist, it, it) },
        searchPlaceholder = stringResource(R.string.lib_artists_search),
        onOpen = { artist, _ -> navController.navigate("artist/${artist.id}") },
        onMenu = { artist ->
            menuState.show { ArtistMenu(originalArtist = artist, coroutineScope = scope, onDismiss = menuState::dismiss) }
        },
        onPlay = { ordered, shuffle ->
            // the library's songs by each artist, in the order the artists are shown
            val library = songs
            scope.playGathered(player, title, shuffle) {
                val byArtist = library.flatMap { song -> song.artists.map { it.id to song } }.groupBy({ it.first }, { it.second })
                ordered.flatMap { byArtist[it.id].orEmpty() }
            }
        },
        navController = navController,
        roundCovers = true,
    )
}

@Composable
internal fun LibraryPlaylistsPage(navController: NavController) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsState()
    val resources = LocalContext.current.resources
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.playlists)

    LibraryCollectionPage(
        title = title,
        pageKey = "playlists",
        items = playlists,
        toEntry = { playlist ->
            CollectionEntry(
                id = playlist.id,
                title = playlist.playlist.name,
                subtitle = resources.getQuantityString(R.plurals.lib_song_count, playlist.songCount, playlist.songCount),
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                // the same key Home's cards use, so the playlist opens growing out of its cover
                sharedElementKey = "playlist-${playlist.id}",
            )
        },
        countText = { pluralStringResource(R.plurals.n_playlist, it, it) },
        searchPlaceholder = stringResource(R.string.lib_playlists_search),
        onOpen = { playlist, _ -> navController.navigate("local_playlist/${playlist.id}") },
        onMenu = { playlist ->
            menuState.show {
                PlaylistMenu(playlist = playlist, coroutineScope = scope, onDismiss = menuState::dismiss, navController = navController)
            }
        },
        onPlay = { ordered, shuffle ->
            scope.playGathered(player, title, shuffle) { ordered.flatMap { list -> database.playlistSongs(list.id).first().map { it.song } } }
        },
        navController = navController,
    )
}
