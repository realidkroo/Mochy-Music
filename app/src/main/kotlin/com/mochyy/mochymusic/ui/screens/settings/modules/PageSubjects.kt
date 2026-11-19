// what a module page that took one of mochy's pages over knows about it: the playlist's, album's or artist's name, picture and songs, read from the same view model mochy's own page uses

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.viewmodels.AlbumViewModel
import com.mochyy.mochymusic.viewmodels.ArtistViewModel
import com.mochyy.mochymusic.viewmodels.LocalPlaylistViewModel
import com.mochyy.mochymusic.viewmodels.OnlinePlaylistViewModel
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** the page's thing: its words and picture, its songs, and the route's own arguments by name */
@Immutable
class PageSubject(
    val title: String,
    val subtitle: String,
    val cover: String?,
    val songs: List<MediaMetadata>,
    val args: Map<String, String>,
) {
    /** "{title}", "{subtitle}", "{count}" and every route argument ("{playlistId}") filled into a page's words */
    fun fill(text: String): String {
        if ('{' !in text) return text
        var filled = text
            .replace("{title}", title, ignoreCase = true)
            .replace("{subtitle}", subtitle, ignoreCase = true)
            .replace("{count}", songs.size.toString(), ignoreCase = true)
        args.forEach { (key, value) -> filled = filled.replace("{$key}", value) }
        return filled
    }

    companion object {
        val None = PageSubject("", "", null, emptyList(), emptyMap())
    }
}

/** the subject for a page taking over [name], loaded the way mochy's own page for it loads; pages without one only get the route's arguments */
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
        PageSubject(playlist?.playlist?.name.orEmpty(), subtitle, playlist?.playlist?.thumbnailUrl ?: playlist?.thumbnails?.firstOrNull(), songs, args)
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
        PageSubject(album?.album?.title.orEmpty(), listOfNotNull(artists.ifBlank { null }, year).joinToString(" · "), album?.album?.thumbnailUrl, songs, args)
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
    // the artist's first shelf of songs, the one their own page opens with
    val top = remember(page) { page?.sections?.firstOrNull { section -> section.items.any { it is SongItem } }?.items?.filterIsInstance<SongItem>().orEmpty() }
    val songs = rememberMetadata(top) { list -> list.map(SongItem::toMediaMetadata) }
    return remember(page, songs, args) {
        PageSubject(page?.artist?.title.orEmpty(), page?.monthlyListenerCount ?: page?.subscriberCountText.orEmpty(), page?.artist?.thumbnail, songs, args)
    }
}

// a playlist can hold thousands of songs, so they're turned into the player's shape off the main thread
@Composable
private fun <T> rememberMetadata(rows: List<T>, convert: (List<T>) -> List<MediaMetadata>): List<MediaMetadata> =
    produceState(initialValue = emptyList(), rows) { value = withContext(Dispatchers.Default) { convert(rows) } }.value
