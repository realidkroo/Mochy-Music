// TvCollections.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.link.PhoneFiles
import com.mochyy.mochymusic.playback.CachedSongs
import com.mochyy.mochymusic.playback.queues.LocalAlbumRadio
import com.mochyy.mochymusic.viewmodels.AlbumViewModel
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LocalPlaylistViewModel
import com.mochyy.mochymusic.viewmodels.OnlinePlaylistViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private fun Song.tvTrack() = TvTrack(
    id = id,
    title = song.title,
    subtitle = artists.joinToString { it.name },
    thumbnailUrl = song.thumbnailUrl,
    durationSeconds = song.duration,
    ref = tvRef(),
)

@Composable
internal fun TvAlbumScreen(viewModel: AlbumViewModel = hiltViewModel(), transformKey: String? = null) {
    val album by viewModel.albumWithSongs.collectAsState()
    val player = LocalPlayerConnection.current
    val current = album
    val tracks = remember(current) { current?.songs?.map { it.tvTrack() }.orEmpty() }
    val stats = current?.let {
        listOfNotNull(
            it.album.year?.toString(),
            pluralStringResource(R.plurals.tv_song_count, it.songs.size, it.songs.size),
        ).joinToString(" · ")
    }.orEmpty()
    TvTrackListPage(
        title = current?.album?.title.orEmpty(),
        subtitle = listOfNotNull(current?.artists?.joinToString { it.name }, stats.ifBlank { null }).joinToString("  "),
        coverUrl = current?.album?.thumbnailUrl,
        tracks = tracks,
        loading = current == null,
        transformKey = transformKey,
        onPlay = { index, shuffled ->
            val withSongs = current ?: return@TvTrackListPage
            // an album plays on into its radio when it ends, the same as on the phone
            player?.playQueue(if (shuffled) LocalAlbumRadio(withSongs.copy(songs = withSongs.songs.shuffled())) else LocalAlbumRadio(withSongs, startIndex = index))
        },
    )
}

@Composable
internal fun TvOnlinePlaylistScreen(viewModel: OnlinePlaylistViewModel = hiltViewModel(), transformKey: String? = null) {
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val actions = LocalTvActions.current
    val tracks = remember(songs) {
        songs.map { TvTrack(it.id, it.title, it.artists.joinToString { a -> a.name }, it.thumbnail, it.duration ?: 0, it.tvRef()) }
    }
    TvTrackListPage(
        title = playlist?.title.orEmpty(),
        subtitle = listOfNotNull(playlist?.author?.name, playlist?.songCountText).joinToString("  "),
        coverUrl = playlist?.thumbnail,
        tracks = tracks,
        loading = loading,
        transformKey = transformKey,
        onPlay = { index, shuffled -> actions.playItems(songs, index, playlist?.title, shuffled) },
    )
}

@Composable
internal fun TvLocalPlaylistScreen(viewModel: LocalPlaylistViewModel = hiltViewModel(), transformKey: String? = null) {
    val playlist by viewModel.playlist.collectAsState()
    val entries by viewModel.playlistSongs.collectAsState()
    val actions = LocalTvActions.current
    val songs = remember(entries) { entries.map { it.song } }
    val tracks = remember(songs) { songs.map { it.tvTrack() } }
    TvTrackListPage(
        title = playlist?.playlist?.name.orEmpty(),
        subtitle = pluralStringResource(R.plurals.tv_song_count, songs.size, songs.size),
        coverUrl = playlist?.thumbnails?.firstOrNull() ?: songs.firstOrNull()?.song?.thumbnailUrl,
        tracks = tracks,
        loading = playlist == null,
        transformKey = transformKey,
        onPlay = { index, shuffled -> actions.playSongs(songs, index, playlist?.playlist?.name, shuffled) },
    )
}

// the library's own lists on a tv: liked, all songs, downloads, files, the cache, what's on the phone, the history
internal enum class TvSongsKind(val key: String, val title: Int) {
    Liked("liked", R.string.liked_songs),
    Library("library", R.string.songs),
    Downloaded("downloaded", R.string.tv_downloaded_here),
    Local("local", R.string.tv_local_files),
    Cached("cached", R.string.tv_cached),
    Phone("phone", R.string.tv_on_your_phone),
    History("history", R.string.history),
    Imported("imported", R.string.tv_imported),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Liked
    }
}

@HiltViewModel
internal class TvSongsViewModel @Inject constructor(
    database: MusicDatabase,
    cachedSongs: CachedSongs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val key = savedStateHandle.get<String>("kind").orEmpty()
    val kind = TvSongsKind.of(key.substringBefore(':'))

    // "imported:spotify" lists what came in from one service
    val importSource: ImportSource? = ImportSource.fromKey(key.substringAfter(':', ""))

    val songs: StateFlow<List<Song>?> = when (kind) {
        TvSongsKind.Liked -> database.likedSongs(SongSortType.CREATE_DATE, true)
        TvSongsKind.Library -> database.songs(SongSortType.CREATE_DATE, true)
        TvSongsKind.Downloaded -> database.downloadedSongs(SongSortType.CREATE_DATE, true)
        TvSongsKind.Local -> database.localSongs(SongSortType.CREATE_DATE, true)
        TvSongsKind.Cached -> cachedSongs.songs
        // the phone's downloads keep their download date in the mirrored library, its files are its local songs
        TvSongsKind.Phone -> combine(
            database.downloadedSongs(SongSortType.CREATE_DATE, true),
            database.localSongs(SongSortType.CREATE_DATE, true),
            PhoneFiles.files,
        ) { downloaded, local, files -> (downloaded + local).distinctBy { it.id }.filter { it.id in files } }
        TvSongsKind.History -> database.events().map { events -> events.map { it.song }.distinctBy { it.id } }
        TvSongsKind.Imported -> database.importedSongsByNameAsc(importSource?.key.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
internal fun TvSongsScreen(viewModel: TvSongsViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsState()
    val actions = LocalTvActions.current
    val list = songs.orEmpty()
    val tracks = remember(list) { list.map { it.tvTrack() } }
    val title = viewModel.importSource?.label ?: stringResource(viewModel.kind.title)
    TvTrackListPage(
        title = title,
        subtitle = pluralStringResource(R.plurals.tv_song_count, list.size, list.size),
        coverUrl = list.firstOrNull()?.song?.thumbnailUrl,
        tracks = tracks,
        loading = songs == null,
        onPlay = { index, shuffled -> actions.playSongs(list, index, title, shuffled) },
    )
}

// albums, artists or playlists from the library as a wall of covers
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvGridScreen(kind: String, viewModel: LibraryHomeViewModel = hiltViewModel()) {
    val actions = LocalTvActions.current
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val (title, items) = when (kind) {
        "artists" -> stringResource(R.string.artists) to artists
        "playlists" -> stringResource(R.string.playlists) to playlists
        else -> stringResource(R.string.albums) to albums
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Text(title, style = TvText.PageTitle, modifier = Modifier.padding(start = TvSpace.Start, top = TvSpace.Top))
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TvCardSize),
                contentPadding = PaddingValues(start = TvSpace.Start, end = TvSpace.End, top = 20.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> kind }) { index, item: LocalItem ->
                    TvItemCard(
                        title = item.title,
                        subtitle = item.tvSubtitle(),
                        thumbnailUrl = item.thumbnailUrl,
                        round = kind == "artists",
                        transformKey = tvTransformKey(item),
                        coverModifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { actions.open(item) },
                    )
                }
            }
        }
    }
}
