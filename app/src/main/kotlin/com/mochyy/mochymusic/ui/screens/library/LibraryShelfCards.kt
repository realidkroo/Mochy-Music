// LibraryShelfCards.kt
//
// What the Local and Cached tabs have in common: the songs they count, and the cards built from
// them - Songs, Playlist, Albums, Artist, then Recently added and Liked. Each tab adds its own
// pieces around these: Local its import cards, Cached the list of every cached song.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.component.LikedCover
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind
import com.mochyy.mochymusic.viewmodels.ShelfAlbum

/** What a Local or Cached tab counts. Only collected while the tab is open: the album grouping isn't free. */
@Immutable
class ShelfTabData(
    val kind: LibraryShelfKind,
    val songs: List<Song>,
    val liked: List<Song>,
    val albums: List<ShelfAlbum>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
) {
    /** The files imported from the phone, as opposed to downloads. Only the Local tab has any. */
    val files: List<Song> = songs.filter { it.song.isLocal }
}

/**
 * Null until the shelf's songs have been read once, so the tab waits a moment instead of flashing
 * its empty state. Collected with the lifecycle: watching the cache means looking at it every
 * couple of seconds, which has no business going on while the app is in the background.
 */
@Composable
fun collectShelfTabData(viewModel: LibraryHomeViewModel, kind: LibraryShelfKind): ShelfTabData? {
    val shelf = viewModel.shelf(kind)
    val loaded by shelf.isLoaded.collectAsStateWithLifecycle()
    val songs by shelf.songs.collectAsStateWithLifecycle()
    val liked by shelf.liked.collectAsStateWithLifecycle()
    val albums by shelf.albums.collectAsStateWithLifecycle()
    val artists by shelf.artists.collectAsStateWithLifecycle()
    val playlists by shelf.playlists.collectAsStateWithLifecycle()
    return remember(kind, loaded, songs, liked, albums, artists, playlists) {
        if (loaded) ShelfTabData(kind, songs, liked, albums, artists, playlists) else null
    }
}

/**
 * The four category cards, Recently added and Liked, each counting only the shelf's songs and
 * opening that shelf's own page. [spaceAbove] keeps the gap under whatever the tab puts first.
 */
internal fun LazyListScope.libraryShelfCards(
    data: ShelfTabData,
    navController: NavController,
    spaceAbove: Dp,
) {
    val key = data.kind.key

    item(key = "${key}_categories") {
        Spacer(modifier = Modifier.height(spaceAbove))
        LibraryCardGrid(
            listOf(
                LibraryCardEntry(stringResource(R.string.songs), data.songs.size, data.songs.take(4).map { it.song.thumbnailUrl }, {
                    navController.navigate("auto_playlist/$key")
                }),
                LibraryCardEntry(stringResource(R.string.lib_playlist), data.playlists.size, data.playlists.take(2).flatMap { it.thumbnails }, {
                    navController.navigate("library/$key/playlists")
                }),
                LibraryCardEntry(stringResource(R.string.albums), data.albums.size, data.albums.take(4).map { it.thumbnailUrl }, {
                    navController.navigate("library/$key/albums")
                }),
                LibraryCardEntry(
                    stringResource(R.string.lib_artist),
                    data.artists.size,
                    data.artists.take(4).map { it.artist.thumbnailUrl },
                    { navController.navigate("library/$key/artists") },
                    roundCovers = true,
                ),
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    item(key = "${key}_recently_added") {
        LibraryWideCard(
            title = stringResource(R.string.library_recently_added),
            count = data.songs.size,
            covers = data.songs.take(4).map { it.song.thumbnailUrl },
            onClick = { navController.navigate("library/$key/added") },
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    // only the liked songs on this shelf; every liked song is on the All tab
    if (data.liked.isNotEmpty()) {
        item(key = "${key}_liked") {
            LibraryWideCard(
                title = stringResource(R.string.liked),
                count = data.liked.size,
                covers = data.liked.take(4).map { it.song.thumbnailUrl },
                onClick = { navController.navigate("auto_playlist/${key}_liked") },
                sharedElementKey = "playlist-${key}_liked",
                art = { LikedCover(it) },
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
