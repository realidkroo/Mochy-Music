// what a module page that took one of mochy's pages over knows about it: the playlist's, album's, artist's or library's name, picture, editorial banner, bio and items

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.modules.security.SecretGuard
import com.mochyy.mochymusic.utils.ArtistHeader
import com.mochyy.mochymusic.viewmodels.AlbumViewModel
import com.mochyy.mochymusic.viewmodels.ArtistViewModel
import com.mochyy.mochymusic.viewmodels.AutoPlaylistViewModel
import com.mochyy.mochymusic.viewmodels.CachePlaylistViewModel
import com.mochyy.mochymusic.viewmodels.HistoryViewModel
import com.mochyy.mochymusic.viewmodels.LocalPlaylistViewModel
import com.mochyy.mochymusic.viewmodels.OnlinePlaylistViewModel
import com.mochyy.mochymusic.viewmodels.TopPlaylistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** rich artist details exposed to takeover pages and artist banner/bio/shelf blocks */
@Immutable
class ArtistSubject(
    val id: String,
    val name: String,
    val header: ArtistHeader?,
    val bio: String?,
    val monthlyListeners: String?,
    val subscribers: String?,
    val albums: List<AlbumItem>,
    val playlists: List<PlaylistItem>,
    val similarArtists: List<ArtistItem>,
    val liked: Boolean,
    val subscribed: Boolean?,
    val showSubscribe: Boolean,
    val onToggleLike: () -> Unit,
    val onToggleSubscribe: () -> Unit,
)

/** the page's thing: its words and picture, its songs, optional artist/playlist/album details, and route arguments */
@Immutable
class PageSubject(
    val title: String,
    val subtitle: String,
    val cover: String?,
    val songs: List<MediaMetadata>,
    val args: Map<String, String>,
    val artist: ArtistSubject? = null,
    val playlistEntity: Playlist? = null,
    val albumEntity: Album? = null,
) {
    /** fills subject placeholders ({title}, {subtitle}, {count}, {bio}, {listeners}, {subscribers}), now-playing fields, route args and safe module settings ({$key}) */
    fun fill(
        text: String,
        values: Map<String, String> = emptyMap(),
        playing: MediaMetadata? = null,
    ): String {
        if ('{' !in text) return text
        var filled = text
            .replace("{title}", title, ignoreCase = true)
            .replace("{subtitle}", subtitle, ignoreCase = true)
            .replace("{count}", songs.size.toString(), ignoreCase = true)
            .replace("{bio}", artist?.bio.orEmpty(), ignoreCase = true)
            .replace("{listeners}", artist?.monthlyListeners.orEmpty(), ignoreCase = true)
            .replace("{subscribers}", artist?.subscribers.orEmpty(), ignoreCase = true)
            .replace("{nowTitle}", playing?.title.orEmpty(), ignoreCase = true)
            .replace("{nowArtist}", playing?.artists?.joinToString(", ") { it.name }.orEmpty(), ignoreCase = true)
            .replace("{nowAlbum}", playing?.album?.title.orEmpty(), ignoreCase = true)
        args.forEach { (key, value) -> filled = filled.replace("{$key}", value) }
        if (values.isNotEmpty() && "{\$" in filled) {
            values.forEach { (key, value) ->
                if (!SecretGuard.looksLikeSecretProbe(key)) {
                    filled = filled.replace("{\$$key}", value, ignoreCase = true)
                }
            }
        }
        return SecretGuard.redact(filled)
    }

    companion object {
        val None = PageSubject("", "", null, emptyList(), emptyMap())
    }
}

/** the subject for a page taking over [name], loaded the way mochy's own page for it loads */
@Composable
internal fun rememberPageSubject(name: String, entry: NavBackStackEntry): PageSubject {
    val args = remember(entry) {
        val bundle = entry.arguments ?: return@remember emptyMap()
        bundle.keySet().filter { !it.startsWith("android-support") }.mapNotNull { key -> bundle.getString(key)?.let { key to it } }.toMap()
    }
    return when (name) {
        "local_playlist" -> localPlaylist(args)
        "album" -> album(args)
        "online_playlist" -> onlinePlaylist(args)
        "artist" -> artist(args)
        "auto_playlist" -> autoPlaylist(args)
        "top_playlist" -> topPlaylist(args)
        "cache_playlist" -> cachePlaylist(args)
        "history" -> historySubject(args)
        "library", "library/songs" -> librarySubject(args)
        else -> PageSubject("", "", null, emptyList(), args)
    }
}

@Composable
private fun localPlaylist(args: Map<String, String>): PageSubject {
    val viewModel: LocalPlaylistViewModel = hiltViewModel()
    val playlist by viewModel.playlist.collectAsState()
    val rows by viewModel.playlistSongs.collectAsState()
    val songs = rememberMetadata(rows) { list -> list.map { it.song.toMediaMetadata() } }
    val count = playlist?.songCount ?: rows.size
    val subtitle = pluralStringResource(R.plurals.n_song, count, count)
    return remember(playlist, subtitle, songs, args) {
        PageSubject(
            title = playlist?.playlist?.name.orEmpty(),
            subtitle = subtitle,
            cover = playlist?.playlist?.thumbnailUrl ?: playlist?.thumbnails?.firstOrNull(),
            songs = songs,
            args = args,
            playlistEntity = playlist,
        )
    }
}

@Composable
private fun album(args: Map<String, String>): PageSubject {
    val viewModel: AlbumViewModel = hiltViewModel()
    val album by viewModel.albumWithSongs.collectAsState()
    val songs = rememberMetadata(album?.songs.orEmpty()) { list -> list.map { it.toMediaMetadata() } }
    return remember(album, songs, args) {
        val artists = album?.artists?.joinToString(", ") { it.name }.orEmpty()
        val year = album?.album?.year?.toString()
        val entity = album?.let { Album(it.album, it.artists) }
        PageSubject(
            title = album?.album?.title.orEmpty(),
            subtitle = listOfNotNull(artists.ifBlank { null }, year).joinToString(" · "),
            cover = album?.album?.thumbnailUrl,
            songs = songs,
            args = args,
            albumEntity = entity,
        )
    }
}

@Composable
private fun onlinePlaylist(args: Map<String, String>): PageSubject {
    val viewModel: OnlinePlaylistViewModel = hiltViewModel()
    val playlist by viewModel.playlist.collectAsState()
    val items by viewModel.playlistSongs.collectAsState()
    val songs = rememberMetadata(items) { list -> list.map(SongItem::toMediaMetadata) }
    return remember(playlist, songs, args) {
        val subtitle = listOfNotNull(playlist?.author?.name, playlist?.songCountText).joinToString(" · ")
        PageSubject(playlist?.title.orEmpty(), subtitle, playlist?.thumbnail, songs, args)
    }
}

@Composable
private fun artist(args: Map<String, String>): PageSubject {
    val viewModel: ArtistViewModel = hiltViewModel()
    val page by viewModel.page.collectAsState()
    val header by viewModel.header.collectAsState()
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val top = remember(page) {
        page?.sections?.firstOrNull { section -> section.items.any { it is SongItem } }?.items?.filterIsInstance<SongItem>().orEmpty()
    }
    val albums = remember(page) {
        page?.sections.orEmpty().flatMap { it.items.filterIsInstance<AlbumItem>() }.distinctBy { it.browseId }
    }
    val playlists = remember(page) {
        page?.sections.orEmpty().flatMap { it.items.filterIsInstance<PlaylistItem>() }.distinctBy { it.id }
    }
    val similar = remember(page) {
        page?.sections.orEmpty().flatMap { it.items.filterIsInstance<ArtistItem>() }.distinctBy { it.id }
    }
    val songs = rememberMetadata(top) { list -> list.map(SongItem::toMediaMetadata) }
    return remember(page, header, libraryArtist, subscribed, songs, albums, playlists, similar, args) {
        val name = header?.name ?: page?.artist?.title ?: libraryArtist?.artist?.name.orEmpty()
        val artistSubject = ArtistSubject(
            id = viewModel.artistId,
            name = name,
            header = header,
            bio = page?.description,
            monthlyListeners = page?.monthlyListenerCount,
            subscribers = page?.subscriberCountText,
            albums = albums,
            playlists = playlists,
            similarArtists = similar,
            liked = libraryArtist?.artist?.bookmarkedAt != null,
            subscribed = subscribed,
            showSubscribe = page?.artist?.channelId != null && subscribed != null,
            onToggleLike = viewModel::toggleLike,
            onToggleSubscribe = viewModel::toggleSubscribe,
        )
        PageSubject(
            title = name,
            subtitle = page?.monthlyListenerCount ?: page?.subscriberCountText.orEmpty(),
            cover = header?.bannerUrl ?: header?.avatarUrl ?: page?.artist?.thumbnail,
            songs = songs,
            args = args,
            artist = artistSubject,
        )
    }
}

@Composable
private fun autoPlaylist(args: Map<String, String>): PageSubject {
    val viewModel: AutoPlaylistViewModel = hiltViewModel()
    val content by viewModel.content.collectAsState()
    val rows = content?.songs.orEmpty()
    val songs = rememberMetadata(rows) { list -> list.map { it.toMediaMetadata() } }
    val title = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.module_page_auto_liked)
        "downloaded" -> stringResource(R.string.module_page_auto_downloaded)
        "cached" -> stringResource(R.string.module_page_auto_cached)
        else -> viewModel.playlist.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    val subtitle = pluralStringResource(R.plurals.n_song, songs.size, songs.size)
    return remember(title, subtitle, songs, args) {
        PageSubject(title, subtitle, songs.firstOrNull()?.thumbnailUrl, songs, args)
    }
}

@Composable
private fun topPlaylist(args: Map<String, String>): PageSubject {
    val viewModel: TopPlaylistViewModel = hiltViewModel()
    val rows by viewModel.topSongs.collectAsState()
    val songs = rememberMetadata(rows) { list -> list.map { it.toMediaMetadata() } }
    val title = stringResource(R.string.module_page_auto_top)
    val subtitle = pluralStringResource(R.plurals.n_song, songs.size, songs.size)
    return remember(title, subtitle, songs, args) {
        PageSubject(title, subtitle, songs.firstOrNull()?.thumbnailUrl, songs, args)
    }
}

@Composable
private fun cachePlaylist(args: Map<String, String>): PageSubject {
    val viewModel: CachePlaylistViewModel = hiltViewModel()
    val rows by viewModel.cachedSongs.collectAsState()
    val songs = rememberMetadata(rows) { list -> list.map { it.toMediaMetadata() } }
    val title = stringResource(R.string.module_page_auto_cached)
    val subtitle = pluralStringResource(R.plurals.n_song, songs.size, songs.size)
    return remember(title, subtitle, songs, args) {
        PageSubject(title, subtitle, songs.firstOrNull()?.thumbnailUrl, songs, args)
    }
}

@Composable
private fun historySubject(args: Map<String, String>): PageSubject {
    val viewModel: HistoryViewModel = hiltViewModel()
    val events by viewModel.events.collectAsState()
    val flat = remember(events) { events?.values?.flatten().orEmpty().map { it.song }.distinctBy { it.id } }
    val songs = rememberMetadata(flat) { list -> list.map { it.toMediaMetadata() } }
    val title = stringResource(R.string.history)
    val subtitle = pluralStringResource(R.plurals.n_song, songs.size, songs.size)
    return remember(title, subtitle, songs, args) {
        PageSubject(title, subtitle, songs.firstOrNull()?.thumbnailUrl, songs, args)
    }
}

@Composable
private fun librarySubject(args: Map<String, String>): PageSubject {
    val database = LocalDatabase.current
    val rows by remember(database) { database.songs(SongSortType.CREATE_DATE, true) }.collectAsState(initial = emptyList())
    val songs = rememberMetadata(rows) { list -> list.map { it.toMediaMetadata() } }
    val title = stringResource(R.string.filter_library)
    val subtitle = pluralStringResource(R.plurals.n_song, songs.size, songs.size)
    return remember(title, subtitle, songs, args) {
        PageSubject(title, subtitle, songs.firstOrNull()?.thumbnailUrl, songs, args)
    }
}

// a playlist can hold thousands of songs, so they're turned into the player's shape off the main thread
@Composable
private fun <T> rememberMetadata(rows: List<T>, convert: (List<T>) -> List<MediaMetadata>): List<MediaMetadata> =
    produceState(initialValue = emptyList(), rows) { value = withContext(Dispatchers.Default) { convert(rows) } }.value
