// LibraryScaffold.kt
//
// The page frame every Library screen shares (collapsing title, search pill, the list below) and
// the search that runs inside it.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.search.SearchField
import com.mochyy.mochymusic.ui.screens.search.SearchGlassTopBar
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.ui.screens.search.searchTopBarHeight
import com.mochyy.mochymusic.viewmodels.ShelfAlbum
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as InnertubeArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

/**
 * Shared page frame for every Library screen: collapsing title/subtitle, a pinned search pill, and
 * the caller's content below.
 *
 * Every Library screen carries its own search that only covers that screen's contents, which is
 * why the field lives in the scaffold rather than in one global place.
 */
@Composable
fun LibraryScaffold(
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    listState: LazyListState,
    glassState: GlassState,
    collapseProvider: () -> Float,
    pureBlack: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack)),
    ) {
        // Only the list is recorded. The top bar draws this recording as its glass, so recording
        // the bar too made the node draw itself: a native stack overflow the moment a scroll
        // settled with the title collapsed. The bar stays a sibling, as on Search.
        // Recorded while scrolling too: the bar's blur stays up through a scroll instead of
        // dropping out and coming back when the list settles.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { collapseProvider() > 0.01f }),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = searchTopBarHeight(
                        withTitle = true,
                        titleBlockHeight = if (subtitle != null) LibraryTitleBlockHeight else 62.dp,
                    ),
                    bottom = bottomInset + 24.dp,
                ),
                content = content,
            )
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = collapseProvider,
            pureBlack = pureBlack,
            title = title,
            subtitle = subtitle,
            titleBlockHeight = if (subtitle != null) LibraryTitleBlockHeight else 62.dp,
            trailing = trailing,
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = {},
                placeholder = searchPlaceholder,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search within the library
// ---------------------------------------------------------------------------------------------

internal fun androidx.compose.foundation.lazy.LazyListScope.librarySearchResults(
    results: List<YTItem>,
    query: String,
    onOpen: (YTItem) -> Unit,
) {
    if (results.isEmpty()) {
        item(key = "no_results") {
            Text(
                text = stringResource(R.string.lib_no_results, query),
                color = SearchColors.Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding, vertical = 24.dp),
            )
        }
        return
    }
    items(results, key = { it.id }) { item ->
        val subtitle = when (item) {
            is SongItem -> item.artists.joinToString { it.name }
            is AlbumItem -> stringResource(R.string.lib_album)
            is ArtistItem -> stringResource(R.string.lib_artist)
            is PlaylistItem -> stringResource(R.string.lib_playlist)
        }
        LibraryListRow(
            title = item.title,
            subtitle = subtitle,
            thumbnailUrl = item.thumbnail,
            onClick = { onOpen(item) },
            onLongClick = { onOpen(item) },
            round = item is ArtistItem,
            songId = (item as? SongItem)?.id,
            // albums and playlists open growing out of their cover, as from Home
            sharedElementKey = com.mochyy.mochymusic.ui.component.coverTransitionKey(item),
        )
    }
}

/**
 * Opens what a search result or pinned tile stands for. [librarySongs] is where a song result is
 * looked up first: a file imported from the device has no YouTube id to start a radio from, so it
 * plays from its file instead - starting a radio with it would fail on the spot.
 */
internal fun openLibraryItem(
    item: YTItem,
    navController: NavController,
    playerConnection: com.mochyy.mochymusic.playback.PlayerConnection?,
    librarySongs: List<Song> = emptyList(),
) {
    when (item) {
        is SongItem -> {
            val file = librarySongs.firstOrNull { it.id == item.id }?.takeIf { it.song.isLocal }
            if (file != null) {
                playerConnection?.playQueue(ListQueue(title = file.title, items = listOf(file.toMediaItem())))
            } else {
                playerConnection?.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
            }
        }
        is AlbumItem -> navController.navigate("album/${item.browseId}")
        is ArtistItem -> navController.navigate("artist/${item.id}")
        // the Library's own playlists have local ids; only YouTube ones open as online playlists
        is PlaylistItem -> navController.navigate(
            if (item.id.startsWith("LP")) "local_playlist/${item.id}" else "online_playlist/${item.id}"
        )
    }
}

internal fun searchLibrary(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<Album>,
    playlists: List<Playlist>,
): List<YTItem> {
    val q = query.lowercase()
    val results = ArrayList<YTItem>()
    songs.filter { it.song.title.lowercase().contains(q) }.take(8).forEach {
        results += SongItem(
            id = it.id,
            title = it.title,
            artists = it.artists.map { a -> InnertubeArtist(name = a.name, id = a.id) },
            thumbnail = it.song.thumbnailUrl ?: "",
            explicit = it.song.explicit,
        )
    }
    artists.filter { it.artist.name.lowercase().contains(q) }.take(6).forEach {
        results += ArtistItem(
            id = it.id,
            title = it.title,
            thumbnail = it.artist.thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    albums.filter { it.album.title.lowercase().contains(q) }.take(6).forEach {
        results += AlbumItem(
            browseId = it.id,
            playlistId = it.album.playlistId ?: "",
            title = it.title,
            artists = it.artists.map { a -> InnertubeArtist(name = a.name, id = a.id) },
            thumbnail = it.album.thumbnailUrl ?: "",
            explicit = it.album.explicit,
        )
    }
    playlists.filter { it.playlist.name.lowercase().contains(q) }.take(6).forEach {
        results += PlaylistItem(
            id = it.id,
            title = it.title,
            author = null,
            songCountText = null,
            thumbnail = it.thumbnails.firstOrNull(),
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    return results
}

/**
 * The library search, over one shelf's things only (the Local or Cached tab). An album whose only
 * trace is a file's tag has no page to open, so it isn't a result of its own - its songs are.
 */
internal fun searchShelf(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<ShelfAlbum>,
    playlists: List<Playlist>,
): List<YTItem> {
    val q = query.lowercase()
    val albumHits = albums
        .filter { it.albumId != null && it.title.lowercase().contains(q) }
        .take(6)
        .map {
            AlbumItem(
                browseId = it.albumId!!,
                playlistId = "",
                title = it.title,
                artists = null,
                thumbnail = it.thumbnailUrl ?: "",
            )
        }
    // the same order as the Library's search: songs, artists, albums, playlists
    return searchLibrary(query, songs, artists, emptyList(), emptyList()) +
        albumHits +
        searchLibrary(query, emptyList(), emptyList(), emptyList(), playlists)
}
