// LibraryCachedTab.kt
//
// The Library's Cached tab: songs the player kept whole while streaming them, so they play again
// without a connection. The same cards as Local, counting only what is cached, then Manage cache,
// then every cached song in one list, most recently played first.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction

/**
 * [nowPlayingId] and [isPlaying] are read inside each row, so a song starting or pausing only
 * redraws the rows, not the whole tab.
 */
internal fun LazyListScope.libraryCachedTab(
    data: ShelfTabData,
    nowPlayingId: () -> String?,
    isPlaying: () -> Boolean,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    onManage: () -> Unit,
    navController: NavController,
) {
    if (data.songs.isEmpty()) {
        item(key = "cached_empty") {
            LibraryEmptyState(text = stringResource(R.string.lib_cached_empty))
        }
    } else {
        libraryShelfCards(data, navController, spaceAbove = 0.dp)
    }

    // above the list rather than under it: with hundreds of songs cached it would be a long way down
    item(key = "cached_manage") {
        Spacer(modifier = Modifier.height(12.dp))
        LibraryManagePill(text = stringResource(R.string.lib_manage_cache_pill), onClick = onManage)
    }

    if (data.songs.isEmpty()) return

    item(key = "cached_songs_header") {
        Spacer(modifier = Modifier.height(24.dp))
        LibraryCapsLabel(stringResource(R.string.lib_cached_all_music))
        Spacer(modifier = Modifier.height(8.dp))
    }

    items(data.songs, key = { "cached_song_${it.id}" }, contentType = { "cached_song" }) { song ->
        // left queues the song, right adds it to the library or takes it out, as on Songs
        SongSwipeBox(
            slab = false,
            library = { librarySwipeAction(song.song) },
            queue = { queueSwipeAction { song.toMediaItem() } },
        ) {
            val active = nowPlayingId() == song.id
            LibraryListRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = active,
                isPlaying = active && isPlaying(),
                songId = song.id,
                onClick = { onPlay(song) },
                onLongClick = { onMenu(song) },
            )
        }
    }
}
