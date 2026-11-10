// SongSwipeBox.kt

package com.mochyy.mochymusic.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.SwipeLeftActionKey
import com.mochyy.mochymusic.constants.SwipeRightActionKey
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// what sliding a song row can do, picked per side in Settings > Customisation
enum class SongSwipeAction(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val color: Color,
    @StringRes val pillLabel: Int,
) {
    Library(R.string.swipe_action_library, R.drawable.library_add, SwipeSaveColor, R.string.add_to_library),
    Queue(R.string.add_to_queue, R.drawable.queue_music, SwipeQueueColor, R.string.add_to_queue),
    Download(R.string.action_download, R.drawable.download, SwipeDownloadColor, R.string.action_download),
    PlayNext(R.string.play_next, R.drawable.playlist_play, SwipePlayNextColor, R.string.play_next),
    AddToPlaylist(R.string.add_to_playlist, R.drawable.playlist_add, SwipePlaylistColor, R.string.add_to_playlist),
    Details(R.string.swipe_action_details, R.drawable.info, SwipeDetailsColor, R.string.details),
}

val DefaultSwipeRight = SongSwipeAction.Library
val DefaultSwipeLeft = SongSwipeAction.Queue

@Composable
fun SongSwipeBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slab: Boolean = true,
    library: @Composable () -> SwipeAction,
    queue: @Composable () -> SwipeAction,
    content: @Composable () -> Unit,
) {
    var playlistTarget by remember { mutableStateOf<MediaItem?>(null) }

    if (playlistTarget != null) {
        val target = playlistTarget!!
        val database = LocalDatabase.current
        val scope = rememberCoroutineScope()
        AddToPlaylistDialog(
            isVisible = true,
            onGetSong = { playlist ->
                target.metadata?.let { meta ->
                    database.withTransaction { insert(meta) }
                }
                scope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { browseId ->
                        YouTube.addToPlaylist(browseId, target.mediaId)
                    }
                }
                listOf(target.mediaId)
            },
            onDismiss = { playlistTarget = null },
            topSongId = target.mediaId,
            topSongArtworkUrl = target.mediaMetadata.artworkUri?.toString(),
        )
    }

    SwipeActionsBox(
        modifier = modifier,
        enabled = enabled,
        slab = slab,
        start = {
            chosenSwipeAction(
                key = SwipeRightActionKey,
                default = DefaultSwipeRight,
                library = library,
                queue = queue,
                onOpenPlaylistDialog = { playlistTarget = it },
            )
        },
        end = {
            chosenSwipeAction(
                key = SwipeLeftActionKey,
                default = DefaultSwipeLeft,
                library = library,
                queue = queue,
                onOpenPlaylistDialog = { playlistTarget = it },
            )
        },
        content = content,
    )
}

@Composable
private fun chosenSwipeAction(
    key: Preferences.Key<String>,
    default: SongSwipeAction,
    library: @Composable () -> SwipeAction,
    queue: @Composable () -> SwipeAction,
    onOpenPlaylistDialog: (MediaItem) -> Unit,
): SwipeAction {
    val (chosen) = rememberEnumPreference(key, defaultValue = default)
    return when (chosen) {
        SongSwipeAction.Library -> library()
        SongSwipeAction.Queue -> queue()
        SongSwipeAction.Download -> downloadSwipeAction(queue().mediaItem)
        SongSwipeAction.PlayNext -> playNextSwipeAction(queue().mediaItem)
        SongSwipeAction.AddToPlaylist -> addToPlaylistSwipeAction(queue().mediaItem, onOpenPlaylistDialog)
        SongSwipeAction.Details -> detailsSwipeAction(queue().mediaItem)
    }
}
