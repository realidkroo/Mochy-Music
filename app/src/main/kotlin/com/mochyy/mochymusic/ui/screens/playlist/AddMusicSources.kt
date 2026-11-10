// AddMusicSources.kt

package com.mochyy.mochymusic.ui.screens.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.isImportedFile
import com.mochyy.mochymusic.ui.menu.announceAddedToPlaylist
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/*
 * Where the "Add music" sheet's songs come from, and where they go: the search over the library
 * and YouTube, the suggestions drawn from the playlist's own songs, and saving the picks.
 */

/** How many of the playlist's songs the suggestions are drawn from. */
private const val SuggestionSeeds = 3

/** Past this many, suggestions drift too far from the playlist to be worth showing. */
private const val MaxSuggestions = 40

/** Library matches shown above the online results, so a short query can't bury them. */
private const val MaxLibraryHits = 20

/** Wait for a pause in typing before searching, so we don't fire a request per letter. */
private const val SearchDebounceMs = 300L

/** What a search turned up: library hits straight away, then YouTube's songs (null while loading). */
internal class SongSearchResults(
    /** What was searched for, which can trail the text field while the user is still typing. */
    val query: String,
    val library: List<Song>,
    val online: List<SongItem>?,
    val onlineFailed: Boolean,
)

/**
 * Searches as the user types: their library first, filtered here and shown at once, then
 * YouTube's songs underneath once they arrive.
 */
@Composable
internal fun rememberSongSearch(needle: String, library: List<Song>?): SongSearchResults? {
    val context = LocalContext.current
    val currentLibrary by rememberUpdatedState(library)
    var results by remember { mutableStateOf<SongSearchResults?>(null) }

    LaunchedEffect(needle) {
        if (needle.isEmpty()) {
            results = null
            return@LaunchedEffect
        }
        delay(SearchDebounceMs)

        val hits = withContext(Dispatchers.Default) {
            currentLibrary.orEmpty().filter { it.matches(needle) }.take(MaxLibraryHits)
        }
        results = SongSearchResults(query = needle, library = hits, online = null, onlineFailed = false)

        val found = withContext(Dispatchers.IO) {
            YouTube.search(needle, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                ?.filterIsInstance<SongItem>()
                ?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                ?.filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false))
        }
        // A song already listed under the library hits isn't worth a second row.
        val hitIds = hits.mapTo(HashSet()) { it.id }
        results = SongSearchResults(
            query = needle,
            library = hits,
            online = found.orEmpty().filter { it.id !in hitIds }.distinctBy { it.id },
            onlineFailed = found == null,
        )
    }
    return results
}

private fun Song.matches(needle: String): Boolean =
    song.title.contains(needle, ignoreCase = true) ||
        artists.any { it.name.contains(needle, ignoreCase = true) }

/**
 * Songs like the ones already in the playlist: songs like a few of them, taking turns between the
 * lists so one song's neighbours don't fill the top. Empty when the playlist is, or when YouTube
 * can't be reached.
 */
internal suspend fun loadPlaylistSuggestions(
    database: MusicDatabase,
    playlistId: String,
    hideExplicit: Boolean,
    hideVideos: Boolean,
): List<SongItem> = coroutineScope {
    val songs = database.playlistSongs(playlistId).first().map { it.song }
    if (songs.isEmpty()) return@coroutineScope emptyList()
    val haveIds = songs.mapTo(HashSet()) { it.id }
    // A song imported from the phone and its YouTube copy have different ids, so match by name too.
    val haveNames = songs.mapTo(HashSet()) { nameKey(it.song.title, it.artists.firstOrNull()?.name) }

    // The first song sets the playlist's mood; a couple of random others keep the mix from
    // leaning on one artist, and make each opening a little different.
    val seeds = listOf(songs.first()) + songs.drop(1).shuffled().take(SuggestionSeeds - 1)
    val lists = seeds.map { seed -> async { songsLike(seed) } }.awaitAll()

    interleave(lists)
        .filter { it.id !in haveIds && nameKey(it.title, it.artists.firstOrNull()?.name) !in haveNames }
        .distinctBy { it.id }
        .filterExplicit(hideExplicit)
        .filterVideoSongs(hideVideos)
        .take(MaxSuggestions)
}

/**
 * Songs like [seed], in the order the player's endless queue tries them. The song's radio comes
 * first because it's the one a signed-out session reliably gets: YouTube leaves out the "related"
 * list without an account. The artist's own songs are the last resort.
 */
private suspend fun songsLike(seed: Song): List<SongItem> {
    val videoId = if (isImportedFile(seed.id)) findOnYouTube(seed) ?: return emptyList() else seed.id

    YouTube.next(WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")).getOrNull()?.items
        ?.filter { it.id != videoId }
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.relatedEndpoint
        ?.let { YouTube.related(it).getOrNull()?.songs }
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    val artistId = seed.artists.firstOrNull { it.isYouTubeArtist }?.id ?: return emptyList()
    return YouTube.artist(artistId).getOrNull()?.sections.orEmpty()
        .flatMap { it.items }
        .filterIsInstance<SongItem>()
}

/** A song imported from the phone has no YouTube id: find its YouTube copy by title and artist. */
private suspend fun findOnYouTube(song: Song): String? {
    val query = listOfNotNull(song.song.title, song.artists.firstOrNull()?.name).joinToString(" ")
    return YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
        ?.filterIsInstance<SongItem>()
        ?.firstOrNull()
        ?.id
}

private fun nameKey(title: String, artist: String?): String =
    title.trim().lowercase() + "|" + artist.orEmpty().trim().lowercase()

/** First of each list, then the second of each, and so on. */
private fun <T> interleave(lists: List<List<T>>): List<T> {
    val longest = lists.maxOfOrNull { it.size } ?: 0
    val out = ArrayList<T>(lists.sumOf { it.size })
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let(out::add)
    }
    return out
}

/**
 * Saves any picked song the app hasn't seen (a search or suggestion result), then appends the
 * picks the playlist doesn't have yet, in the order they were ticked. A synced playlist is
 * told about them on YouTube too.
 */
internal suspend fun addPicksToPlaylist(database: MusicDatabase, playlistId: String, chosen: List<MediaMetadata>, context: android.content.Context? = null) {
    val playlist = database.playlist(playlistId).first() ?: return
    val already = database.playlistDuplicates(playlistId, chosen.map { it.id }).toSet()
    val fresh = chosen.filter { it.id !in already }
    if (fresh.isEmpty()) return
    val ids = fresh.map { it.id }

    database.withTransaction {
        // A playlist can only point at saved songs. For one already saved this does nothing.
        fresh.forEach { insert(it) }
        addSongToPlaylist(playlist, ids)
    }
    announceAddedToPlaylist(database, playlist, ids, context)

    playlist.playlist.browseId?.let { browseId ->
        ids.forEach { YouTube.addToPlaylist(browseId, it) }
    }
}
