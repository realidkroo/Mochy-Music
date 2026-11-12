// VoicePicker.kt

package com.mochyy.mochymusic.playback.voice

import android.content.Context
import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.pages.ArtistPage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playback.queues.Queue
import com.mochyy.mochymusic.playback.queues.YouTubePlaylistQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// turns a voice request into a queue mochy already plays, with the songs found before anything that's playing stops
class VoicePicker(
    private val context: Context,
    private val database: MusicDatabase,
) {
    private val hideExplicit: Boolean get() = context.dataStore.get(HideExplicitKey, false)
    private val hideVideos: Boolean get() = context.dataStore.get(HideVideoSongsKey, false)

    // null when nothing matched; youtube trouble is thrown only when the library had nothing either, so the car can say which it was
    suspend fun queueFor(request: VoiceRequest): Queue? = withContext(Dispatchers.IO) {
        when (request) {
            VoiceRequest.Resume -> startingPoint()
            is VoiceRequest.Liked -> listQueue(
                context.getString(R.string.liked_songs),
                database.likedSongs(SongSortType.CREATE_DATE, descending = true).first(),
                request.shuffle,
            )
            is VoiceRequest.Downloads -> listQueue(
                context.getString(R.string.downloaded_songs),
                database.downloadedSongs(SongSortType.CREATE_DATE, descending = true).first(),
                request.shuffle,
            )
            else -> ownPlaylist(request) ?: onlineOrLibrary(request)
        }
    }

    // "play music" with nothing queued yet: the same quick picks the home screen opens with
    private suspend fun startingPoint(): Queue? {
        val monthAgo = System.currentTimeMillis() - MonthMs
        val picks = database.mostPlayedSongs(fromTimeStamp = monthAgo, limit = QuickPicks).first()
            .ifEmpty { database.recentlyPlayedSongs(limit = QuickPicks).first() }
            .ifEmpty { database.likedSongs(SongSortType.CREATE_DATE, descending = true).first() }
        return listQueue(context.getString(R.string.quick_picks), picks, shuffle = false)
    }

    // a playlist of your own goes first, but a top result only takes it when the name matches word for word
    private suspend fun ownPlaylist(request: VoiceRequest): Queue? {
        val (name, least) = when (request) {
            is VoiceRequest.Playlist -> request.name to 1
            is VoiceRequest.Top -> request.text to 3
            else -> return null
        }
        val best = database.playlistsByCreateDateAsc().first()
            .map { it to VoiceSearch.nameMatch(it.playlist.name, name) }
            .filter { it.second >= least }
            .maxByOrNull { it.second }
            ?.first ?: return null
        val songs = database.playlistSongs(best.id).first().map { it.song }
        if (songs.isNotEmpty()) return listQueue(best.playlist.name, songs, request.shuffle)
        // a youtube playlist saved without its songs plays straight from youtube
        val browseId = best.playlist.browseId ?: return null
        return youtubePlaylist(browseId, best.playlist.name, request.shuffle)
    }

    private suspend fun onlineOrLibrary(request: VoiceRequest): Queue? {
        val found = try {
            online(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return library(request) ?: throw e
        }
        return found ?: library(request)
    }

    private suspend fun online(request: VoiceRequest): Queue? = when (request) {
        is VoiceRequest.Artist -> artist(request.name, request.shuffle) ?: song(request.name)
        is VoiceRequest.Album -> album(request.title, request.artist, request.shuffle)
            ?: song(listOfNotNull(request.title, request.artist).joinToString(" "))
        is VoiceRequest.Playlist -> foundPlaylist(request.name, request.shuffle)
        is VoiceRequest.Song -> song(request.text)
        is VoiceRequest.Top -> top(request.text, request.shuffle)
        else -> null
    }

    // the songs already in your library, for when youtube can't be reached or has nothing
    private suspend fun library(request: VoiceRequest): Queue? = when (request) {
        is VoiceRequest.Artist -> database.searchArtists(request.name).first()
            .maxByOrNull { VoiceSearch.nameMatch(it.artist.name, request.name) }
            ?.let { listQueue(it.artist.name, database.artistSongsByCreateDateAsc(it.id).first(), request.shuffle) }
        is VoiceRequest.Album -> database.searchAlbums(request.title).first()
            .maxByOrNull { VoiceSearch.nameMatch(it.album.title, request.title) }
            ?.let { listQueue(it.album.title, database.albumSongs(it.id).first(), request.shuffle) }
        is VoiceRequest.Song -> librarySongs(request.text)
        is VoiceRequest.Top -> librarySongs(request.text)
        else -> null
    }

    private suspend fun librarySongs(text: String): Queue? {
        val songs = database.searchSongs(text).first()
        return listQueue(songs.firstOrNull()?.song?.title ?: text, songs, shuffle = false)
    }

    private suspend fun artist(name: String, shuffle: Boolean): Queue? {
        val found = YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow().items.filterIsInstance<ArtistItem>()
        val best = found.firstOrNull { VoiceSearch.nameMatch(it.title, name) >= 3 } ?: found.firstOrNull() ?: return null
        return artistQueue(best, shuffle)
    }

    // the artist's top songs, like the play button on their page, or their radio when the page has none
    private suspend fun artistQueue(artist: ArtistItem, shuffle: Boolean): Queue? {
        val page = YouTube.artist(artist.id).getOrThrow()
        songsQueue(page.artist.title, topSongs(page), shuffle)?.let { return it }
        val endpoint = (if (shuffle) page.artist.shuffleEndpoint else null)
            ?: page.artist.radioEndpoint
            ?: page.artist.shuffleEndpoint
            ?: return null
        return YouTubeQueue(endpoint)
    }

    // the page's songs shelf, then the whole list behind its more button when that answers in time
    private suspend fun topSongs(page: ArtistPage): List<SongItem> {
        val section = page.sections.firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null } ?: return emptyList()
        val shown = section.items.filterIsInstance<SongItem>()
        val more = section.moreEndpoint ?: return shown
        val all = withTimeoutOrNull(MoreSongsTimeoutMs) { YouTube.artistItems(more).getOrNull() }
            ?.items?.filterIsInstance<SongItem>()
        return all?.takeIf { it.isNotEmpty() } ?: shown
    }

    private suspend fun album(title: String, artist: String?, shuffle: Boolean): Queue? {
        val query = listOfNotNull(title, artist).joinToString(" ")
        val found = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow().items.filterIsInstance<AlbumItem>()
        val best = found.firstOrNull { VoiceSearch.nameMatch(it.title, title) >= 3 } ?: found.firstOrNull() ?: return null
        return albumQueue(best, shuffle)
    }

    private suspend fun albumQueue(album: AlbumItem, shuffle: Boolean): Queue? {
        val page = YouTube.album(album.browseId).getOrThrow()
        return songsQueue(page.album.title, page.songs, shuffle)
    }

    private suspend fun foundPlaylist(name: String, shuffle: Boolean): Queue? {
        val filters = listOf(YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
        val item = filters.firstNotNullOfOrNull { filter ->
            YouTube.search(name, filter).getOrThrow().items.filterIsInstance<PlaylistItem>().firstOrNull()
        } ?: return null
        return youtubePlaylist(item.id, item.title, shuffle)
    }

    // loaded here so a playlist that turns out empty never stops what's playing; the rest pages in as it plays, like on the playlist's page
    private suspend fun youtubePlaylist(id: String, title: String, shuffle: Boolean): Queue? {
        val page = YouTube.playlist(id).getOrThrow()
        val songs = page.songs.filterExplicit(hideExplicit).filterVideoSongs(hideVideos)
        if (songs.isEmpty()) return null
        return YouTubePlaylistQueue(
            playlistId = id,
            playlistTitle = page.playlist.title.ifBlank { title },
            initialSongs = if (shuffle) songs.shuffled() else songs,
            initialContinuation = page.songsContinuation,
        )
    }

    // the best song for the words, playing right away with its radio after it, like a song tapped in search
    private suspend fun song(text: String): Queue? {
        val found = YouTube.search(text, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items
            .filterIsInstance<SongItem>()
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideos)
        return found.firstOrNull()?.let(::radio)
    }

    // whatever search puts on top, played the way tapping it would
    private suspend fun top(text: String, shuffle: Boolean): Queue? {
        val summary = YouTube.searchSummary(text).getOrThrow()
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideos)
        val candidates = summary.summaries.firstOrNull()?.items.orEmpty().take(TopCandidates)
        for (item in candidates) {
            val queue = when (item) {
                is SongItem -> radio(item)
                is AlbumItem -> albumQueue(item, shuffle)
                is ArtistItem -> artistQueue(item, shuffle)
                is PlaylistItem -> youtubePlaylist(item.id, item.title, shuffle)
                else -> null
            }
            if (queue != null) return queue
        }
        return song(text)
    }

    private fun radio(song: SongItem): Queue =
        YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata())

    private fun songsQueue(title: String, songs: List<SongItem>, shuffle: Boolean): Queue? =
        itemsQueue(title, songs.filterExplicit(hideExplicit).filterVideoSongs(hideVideos).map { it.toMediaItem() }, shuffle)

    private fun listQueue(title: String, songs: List<Song>, shuffle: Boolean): Queue? =
        itemsQueue(title, songs.map { it.toMediaItem() }, shuffle)

    private fun itemsQueue(title: String, items: List<MediaItem>, shuffle: Boolean): Queue? {
        if (items.isEmpty()) return null
        return ListQueue(title = title, items = if (shuffle) items.shuffled() else items)
    }

    private companion object {
        const val MonthMs = 30L * 24 * 60 * 60 * 1000
        const val QuickPicks = 20
        const val TopCandidates = 3
        const val MoreSongsTimeoutMs = 8_000L
    }
}
