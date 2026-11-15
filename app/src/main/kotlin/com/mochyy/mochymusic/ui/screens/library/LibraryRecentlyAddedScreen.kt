// LibraryRecentlyAddedScreen.kt
//
// Songs only - every library song, or with a shelf the Local or Cached tab's songs. The underlying
// query orders by when each song entered the library (a local one: when it was downloaded or
// imported; a cached one: when it was last played), which is the one timeline shared across the library; albums/artists/playlists carry no
// comparable timestamp, so a combined "recently added" feed would need new queries rather than a
// merge of what exists.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

@Composable
fun LibraryRecentlyAddedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val songs by (shelf?.let(viewModel::shelf)?.songs ?: viewModel.songs).collectAsStateWithLifecycle()
    val recentlyAdded = stringResource(R.string.library_recently_added)
    val queueTitle = shelf?.label ?: recentlyAdded
    val playerConnection = LocalPlayerConnection.current ?: return

    val entries = remember(songs) {
        songs.map {
            LibraryGridEntry(
                id = it.id,
                title = it.title,
                subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
                thumbnailUrl = it.song.thumbnailUrl,
            )
        }
    }

    fun play(startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = queueTitle,
                items = ordered.map { it.toMediaItem() },
                startIndex = if (shuffle) 0 else startIndex,
            ),
        )
    }

    LibraryGridScreen(
        title = stringResource(R.string.lib_recent_title),
        subtitle = stringResource(R.string.lib_recent_subtitle),
        searchPlaceholder = stringResource(R.string.lib_recent_search),
        entries = entries,
        onOpen = { entry -> play(songs.indexOfFirst { it.id == entry.id }.coerceAtLeast(0), shuffle = false) },
        onPlay = { play(0, shuffle = false) },
        onShuffle = { play(0, shuffle = true) },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
