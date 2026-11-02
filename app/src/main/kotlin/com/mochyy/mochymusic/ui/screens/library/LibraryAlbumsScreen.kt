// LibraryAlbumsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryImportedViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

/**
 * The library's albums, or with a [shelf] the Local or Cached tab's: only the albums one of that
 * tab's songs is from. Those can include an imported file's album, which is only a tag and has no
 * page - opening one plays its songs instead. With a [source], the albums one service's songs come from.
 */
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    if (shelf != null) {
        ShelfAlbumsScreen(navController, shelf, modifier, pureBlack)
        return
    }
    if (source != null) {
        ImportedAlbumsScreen(navController, source, modifier, pureBlack)
        return
    }

    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()
    val entries = remember(albums) { albums.toGridEntries() }

    LibraryGridScreen(
        title = stringResource(R.string.albums),
        subtitle = stringResource(R.string.library_albums_subtitle),
        searchPlaceholder = stringResource(R.string.library_albums_search),
        entries = entries,
        onOpen = { navController.navigate("album/${it.id}") },
        // Both buttons open the newest album rather than building a queue across every album:
        // this screen only loads album metadata, so a real "play everything" would mean fetching
        // every album's tracklist up front just to make one button work.
        onPlay = { albums.firstOrNull()?.let { navController.navigate("album/${it.id}") } },
        onShuffle = { albums.randomOrNull()?.let { navController.navigate("album/${it.id}") } },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}

@Composable
private fun ImportedAlbumsScreen(
    navController: NavController,
    source: ImportSource,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val viewModel: LibraryImportedViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()
    val entries = remember(albums) { albums.toGridEntries() }

    LibraryGridScreen(
        title = stringResource(R.string.albums),
        subtitle = stringResource(R.string.library_imported_albums_subtitle, source.label),
        searchPlaceholder = stringResource(R.string.library_albums_search),
        entries = entries,
        onOpen = { navController.navigate("album/${it.id}") },
        // same as the library's albums: only album details are loaded here, so both open one
        onPlay = { albums.firstOrNull()?.let { navController.navigate("album/${it.id}") } },
        onShuffle = { albums.randomOrNull()?.let { navController.navigate("album/${it.id}") } },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}

private fun List<Album>.toGridEntries() = map {
    LibraryGridEntry(
        id = it.id,
        title = it.title,
        subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
        thumbnailUrl = it.album.thumbnailUrl,
        sharedElementKey = "album-${it.id}",
    )
}

@Composable
private fun ShelfAlbumsScreen(
    navController: NavController,
    shelf: LibraryShelfKind,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val albums by viewModel.shelf(shelf).albums.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return

    val entries = remember(albums) {
        albums.map {
            LibraryGridEntry(
                id = it.key,
                title = it.title,
                subtitle = it.artists,
                thumbnailUrl = it.thumbnailUrl,
                sharedElementKey = it.albumId?.let { id -> "album-$id" },
            )
        }
    }

    fun playAll(shuffle: Boolean) {
        val songs = albums.flatMap { it.songs }
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(ListQueue(title = shelf.label, items = ordered.map { it.toMediaItem() }))
    }

    LibraryGridScreen(
        title = "Albums",
        subtitle = when (shelf) {
            LibraryShelfKind.Local -> "Albums with music on this device."
            LibraryShelfKind.Cached -> "Albums with songs in your cache."
        },
        searchPlaceholder = "Find an album listed here",
        entries = entries,
        onOpen = { entry ->
            val album = albums.firstOrNull { it.key == entry.id } ?: return@LibraryGridScreen
            if (album.albumId != null) {
                navController.navigate("album/${album.albumId}")
            } else {
                playerConnection.playQueue(
                    ListQueue(title = album.title, items = album.songs.map { it.toMediaItem() }),
                )
            }
        },
        // Every album here has its songs on hand, so these play them all for real: album by
        // album, or every album's songs here shuffled together.
        onPlay = { playAll(shuffle = false) },
        onShuffle = { playAll(shuffle = true) },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
