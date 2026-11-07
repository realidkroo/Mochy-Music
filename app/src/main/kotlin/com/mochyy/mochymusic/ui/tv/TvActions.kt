// TvActions.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import java.net.URLEncoder

// a song as the tv's menu needs it, whether it came from the library or from youtube
@Immutable
internal data class TvSongRef(
    val id: String,
    val title: String,
    val artists: List<Pair<String, String?>>,
    val albumId: String?,
    val thumbnailUrl: String?,
    val metadata: MediaMetadata,
    val mediaItem: () -> MediaItem,
)

internal fun Song.tvRef() = TvSongRef(
    id = id,
    title = song.title,
    artists = artists.map { it.name to it.id },
    albumId = song.albumId ?: album?.id,
    thumbnailUrl = song.thumbnailUrl,
    metadata = toMediaMetadata(),
    mediaItem = { toMediaItem() },
)

internal fun SongItem.tvRef() = TvSongRef(
    id = id,
    title = title,
    artists = artists.map { it.name to it.id },
    albumId = album?.id,
    thumbnailUrl = thumbnail,
    metadata = toMediaMetadata(),
    mediaItem = { toMediaItem() },
)

// what every tv page does with what it shows: songs play, everything else opens its own page
internal class TvActions(
    val navController: NavController,
    private val player: () -> PlayerConnection?,
    val database: MusicDatabase,
    val openMenu: (TvSongRef) -> Unit,
    val openPlayer: () -> Unit,
) {
    fun open(item: YTItem) {
        when (item) {
            is SongItem -> player()?.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
            is AlbumItem -> navController.navigate("album/${item.browseId}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
        }
    }

    fun open(item: LocalItem) {
        when (item) {
            is Song -> player()?.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
            is Album -> navController.navigate("album/${item.id}")
            is Artist -> navController.navigate("artist/${item.id}")
            is Playlist -> navController.navigate("local_playlist/${item.id}")
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, title: String? = null, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        player()?.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = if (shuffled) 0 else startIndex))
    }

    fun playItems(songs: List<SongItem>, startIndex: Int = 0, title: String? = null, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        player()?.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = if (shuffled) 0 else startIndex))
    }

    fun playNext(song: TvSongRef) = player()?.playNext(song.mediaItem())

    fun addToQueue(song: TvSongRef) = player()?.addToQueue(song.mediaItem())

    // saves the song first when it only came from youtube, so the like has a row to land on
    fun toggleLike(song: TvSongRef) {
        database.query {
            val saved = getSongByIdBlocking(song.id)?.song
            if (saved == null) {
                insert(song.metadata)
                getSongByIdBlocking(song.id)?.song?.let { update(it.toggleLike()) }
            } else {
                update(saved.toggleLike())
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        navController.navigate("${TvRoutes.Search}?q=${URLEncoder.encode(query, "UTF-8")}") { launchSingleTop = true }
    }
}

internal val LocalTvActions = staticCompositionLocalOf<TvActions> { error("No TvActions provided") }
