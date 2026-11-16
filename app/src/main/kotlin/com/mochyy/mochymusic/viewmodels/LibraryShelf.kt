// LibraryShelf.kt
//
// The Library's Local and Cached tabs each show one slice of the library, picked out by its songs.
// The liked songs, albums, artists and playlists on those tabs are all worked out from the songs,
// the same way for both, so the two tabs and the pages they open always count alike.

package com.mochyy.mochymusic.viewmodels

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which slice. [key] is its part of the routes ("library/cached/albums") and of the song lists'
 * names ("auto_playlist/local_liked"); [label] names the queue its songs play in.
 */
enum class LibraryShelfKind(val key: String, val label: String) {
    /** Downloads and files imported from the phone. */
    Local("local", "Local & downloaded"),

    /** Songs the player kept whole in its cache while streaming them. */
    Cached("cached", "Cached"),
}

/**
 * One slice of the library and everything that comes from its songs. [refreshArtists], when
 * given, runs while [artists] is being watched, for artists nothing else fetches pictures for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryShelf internal constructor(
    source: Flow<List<Song>>,
    libraryPlaylists: StateFlow<List<Playlist>>,
    database: MusicDatabase,
    scope: CoroutineScope,
    refreshArtists: (suspend (List<Artist>) -> Unit)? = null,
) {
    private val loaded = MutableStateFlow(false)

    /** False until the songs have been read once, so a tab can wait instead of flashing "empty". */
    val isLoaded: StateFlow<Boolean> = loaded.asStateFlow()

    /** The shelf's songs, newest first. Every count on its tab comes from this list and nothing else. */
    val songs: StateFlow<List<Song>> = source
        .onEach { loaded.value = true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Only the liked songs that are on the shelf; every liked song is what Liked songs is for. */
    val liked: StateFlow<List<Song>> = songs
        .map { list -> list.filter { it.song.liked } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The albums the songs belong to, built from the songs themselves rather than the album table:
     * a downloaded or cached song that was never added to the library has no album in the
     * Library's list, and an imported file only has its album as a tag. See [ShelfAlbum].
     */
    val albums: StateFlow<List<ShelfAlbum>> = songs
        .map { groupShelfAlbums(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The songs' artists, each with how many of its songs are on the shelf, the artist with the
     * newest song first. Same rule as the Library's own artist list: only artists that have a page
     * to open (a YouTube channel, or one made for an imported file).
     */
    val artists: StateFlow<List<Artist>> = songs
        .map { list ->
            list.flatMap { it.artists }
                .filter { it.isYouTubeArtist || it.isLocal }
                .groupBy { it.id }
                .map { (_, entries) -> Artist(artist = entries.first(), songCount = entries.size) }
        }
        .flowOn(Dispatchers.Default)
        .withArtistRefresh(refreshArtists)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The library's playlists that have at least one of the songs in them. Re-checked whenever the
     * songs change, and whenever a playlist does - [libraryPlaylists] re-emits when a song is added
     * to or taken out of one.
     */
    val playlists: StateFlow<List<Playlist>> = combine(
        libraryPlaylists,
        songs.map { list -> list.map { it.id } }.distinctUntilChanged(),
    ) { lists, ids -> lists to ids }
        .mapLatest { (lists, ids) ->
            if (lists.isEmpty() || ids.isEmpty()) return@mapLatest emptyList<Playlist>()
            // under the 999 bound parameters older SQLite builds allow in one statement
            val containing = ids.chunked(900)
                .flatMap { database.playlistIdsContainingSongs(it) }
                .toSet()
            lists.filter { it.id in containing }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Runs [refresh] beside the list rather than in its way: the list still updates at once, and a
 * slow fetch only means the next pass starts from the newest list, never a backlog of old ones.
 * Stops with the list, when nobody watches it any more.
 */
private fun Flow<List<Artist>>.withArtistRefresh(refresh: (suspend (List<Artist>) -> Unit)?): Flow<List<Artist>> {
    if (refresh == null) return this
    val upstream = this
    return channelFlow {
        val newest = MutableStateFlow<List<Artist>?>(null)
        launch(Dispatchers.IO) { newest.filterNotNull().collect { refresh(it) } }
        upstream.collect { list ->
            send(list)
            newest.value = list
        }
    }
}

/**
 * An album on the Local or Cached tab. [albumId] is null for an imported file whose album is only
 * a tag - there is no album page to open for it, so opening it plays its [songs] instead.
 */
@Immutable
data class ShelfAlbum(
    val key: String,
    val albumId: String?,
    val title: String,
    val artists: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

private fun groupShelfAlbums(songs: List<Song>): List<ShelfAlbum> =
    songs
        .groupBy { song ->
            song.album?.id
                ?: song.song.albumId
                ?: song.song.albumName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "tag:$it" }
        }
        .mapNotNull { (key, group) ->
            if (key == null) return@mapNotNull null
            val first = group.first()
            ShelfAlbum(
                key = key,
                albumId = first.album?.id ?: first.song.albumId,
                title = first.album?.title ?: first.song.albumName?.trim() ?: return@mapNotNull null,
                artists = group.flatMap { it.artists }.map { it.name }.distinct().joinToString().ifBlank { null },
                thumbnailUrl = first.album?.thumbnailUrl ?: group.firstNotNullOfOrNull { it.song.thumbnailUrl },
                songs = group,
            )
        }
