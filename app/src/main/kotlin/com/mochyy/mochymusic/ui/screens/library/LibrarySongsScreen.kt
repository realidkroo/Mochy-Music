// LibrarySongsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel

// every song in the library, a to z
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val title = stringResource(R.string.lib_all_songs_title)

    fun play(ordered: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (ordered.isEmpty()) return
        val queue = if (shuffle) ordered.shuffled() else ordered
        playerConnection.playQueue(
            ListQueue(title = title, items = queue.map { it.toMediaItem() }, startIndex = if (shuffle) 0 else startIndex),
        )
    }

    LibraryCollectionPage(
        title = title,
        pageKey = "songs",
        items = songs,
        toEntry = { song ->
            CollectionEntry(
                id = song.id,
                title = song.song.title,
                subtitle = listOfNotNull(
                    song.artists.joinToString { it.name }.ifBlank { null },
                    song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                ).joinToString(" • "),
                thumbnailUrl = song.song.thumbnailUrl,
                songId = song.id,
            )
        },
        countText = { pluralStringResource(R.plurals.n_song, it, it) },
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        onOpen = { song, ordered ->
            if (song.id == playerConnection.mediaMetadata.value?.id) {
                playerConnection.togglePlayPause()
            } else {
                play(ordered, ordered.indexOf(song).coerceAtLeast(0), shuffle = false)
            }
        },
        onMenu = { song ->
            menuState.show {
                SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
            }
        },
        onPlay = { ordered, shuffle -> play(ordered, 0, shuffle) },
        navController = navController,
        secondsOf = { it.song.duration.coerceAtLeast(0) },
        // left queues the song, right takes it out of the library (it's already in it)
        rowWrapper = { song, row ->
            SongSwipeBox(
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
    )
}
