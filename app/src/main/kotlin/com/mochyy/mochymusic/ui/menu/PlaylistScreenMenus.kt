// PlaylistScreenMenus.kt

package com.mochyy.mochymusic.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.PlaylistSong
import com.mochyy.mochymusic.playback.AutoDownloads
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.Material3MenuGroup
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.SheetActionCard
import com.mochyy.mochymusic.ui.component.SheetActionCardRow
import com.mochyy.mochymusic.ui.component.SheetDownloadCard
import com.mochyy.mochymusic.ui.component.SheetSearchLauncher
import kotlinx.coroutines.launch

/**
 * The menu behind a playlist screen's "more" button, laid out as in the mock: a search that opens
 * the screen's own song search, Download and Edit playlist as two cards (with Re-sync as a third
 * for a playlist imported from a service), then Play next, Sort by, Edit order and Add to
 * playlist - and the rest of the options as they were.
 *
 * Sort by and Edit order are the screen's sort dialog and its lock on reordering; they used to be
 * "Sort order" and "Lock/Unlock editing" further down the list.
 */
@Composable
fun LocalPlaylistMenu(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    context: Context,
    downloadState: Int,
    onEdit: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
    locked: Boolean = true,
    onToggleLock: () -> Unit = {},
    onShowSortDialog: () -> Unit = {},
    onSearch: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    /** Lets Re-sync open a service's sign-in page; without it, it can only run the ones that need none. */
    navController: NavController? = null,
) {
    val isYouTubePlaylist = playlist.playlist.browseId != null
    val resync = rememberPlaylistResync(playlist.id, playlist.playlist.browseId, navController, onDismiss)
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val topSong = remember(songs) {
        songs.minWithOrNull(compareBy({ it.map.position }, { it.map.id }))?.song
    }

    // Adds every song of this playlist to another one. Opens over this menu, which comes back
    // forward behind it.
    AddToPlaylistDialog(
        isVisible = showAddToPlaylist,
        onGetSong = { songs.map { it.song.id } },
        onDismiss = { showAddToPlaylist = false },
        topSongId = topSong?.id,
        topSongArtworkUrl = topSong?.song?.thumbnailUrl,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The sheet only caps its height; a short screen or a large font scrolls instead of
            // cutting the last options off.
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
    ) {
        SheetSearchLauncher(
            placeholder = "Search any song on playlist",
            onClick = {
                onDismiss()
                onSearch()
            },
        )

        Spacer(Modifier.height(16.dp))

        SheetActionCardRow {
            // The screen's onDownload starts, cancels or removes, whichever fits the state.
            SheetDownloadCard(
                downloadState = downloadState,
                onDownload = {
                    onDownload()
                    onDismiss()
                },
                onRemove = {
                    onDownload()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
            // Over this menu rather than closing it: Done comes back here.
            SheetActionCard(
                icon = R.drawable.edit,
                title = "Edit playlist",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            resync.title?.let { title ->
                SheetActionCard(
                    icon = R.drawable.sync,
                    title = title,
                    onClick = resync::start,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        AutoDownloadRow(playlist.id)

        Spacer(Modifier.height(10.dp))

        Material3MenuGroup(
            items = listOfNotNull(
                Material3MenuItemData(
                    title = { Text("Play next") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.playlist_play),
                            contentDescription = null
                        )
                    },
                    onClick = if (songs.isNotEmpty()) {
                        {
                            onPlayNext()
                            onDismiss()
                        }
                    } else null,
                ),
                Material3MenuItemData(
                    title = { Text("Sort by") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.drag_handle),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onShowSortDialog()
                        onDismiss()
                    },
                ),
                if (playlist.playlist.isEditable) {
                    Material3MenuItemData(
                        title = { Text(if (locked) "Edit order" else "Lock order") },
                        icon = {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock_open else R.drawable.lock),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onToggleLock()
                            onDismiss()
                        },
                    )
                } else null,
                Material3MenuItemData(
                    title = { Text("Add to playlist") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.playlist_add),
                            contentDescription = null
                        )
                    },
                    onClick = if (songs.isNotEmpty()) {
                        { showAddToPlaylist = true }
                    } else null,
                ),
            )
        )

        Spacer(Modifier.height(12.dp))

        Material3MenuGroup(
            items = buildList {
                if (isYouTubePlaylist) {
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.action_sync)) },
                            description = { Text(stringResource(R.string.sync_playlist_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onSync()
                                onDismiss()
                            }
                        )
                    )
                }

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.add_to_queue)) },
                        description = { Text(stringResource(R.string.add_to_queue_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onQueue()
                            onDismiss()
                        }
                    )
                )

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.share)) },
                        description = { Text(stringResource(R.string.share_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            val shareText = if (isYouTubePlaylist) {
                                "https://music.youtube.com/playlist?list=${playlist.playlist.browseId}"
                            } else {
                                songs.joinToString("\n") { it.song.song.title }
                            }
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                            onDismiss()
                        }
                    )
                )

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.delete)) },
                        description = { Text(stringResource(R.string.delete_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    )
                )
            }
        )
    }
}

@Composable
fun AutoPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    /** opens the list's own song search, as on a playlist's menu */
    onSearch: (() -> Unit)? = null,
    // the list whose new songs can download by themselves (liked songs), or none
    autoDownloadId: String? = null,
) = QueueAndDownloadCards(downloadState, onQueue, onDownload, onDismiss, onSearch, autoDownloadId)

@Composable
fun TopPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) = QueueAndDownloadCards(downloadState, onQueue, onDownload, onDismiss)

@Composable
fun CachePlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) = QueueAndDownloadCards(downloadState, onQueue, onDownload, onDismiss)

/**
 * The menu of a playlist the app makes for you (auto, top, cache). Queueing it up and saving it
 * offline are the two things it offers, so both are big cards; with [onSearch], a search for its
 * songs leads, as on a playlist's menu.
 */
@Composable
private fun QueueAndDownloadCards(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onSearch: (() -> Unit)? = null,
    autoDownloadId: String? = null,
) {
    val bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    if (onSearch != null) {
        SheetSearchLauncher(
            placeholder = "Search any song here",
            onClick = {
                onDismiss()
                onSearch()
            },
        )
        Spacer(Modifier.height(16.dp))
    }
    SheetActionCardRow(
        modifier = Modifier.padding(bottom = if (autoDownloadId != null) 10.dp else bottom),
    ) {
        SheetActionCard(
            icon = R.drawable.queue_music,
            title = stringResource(R.string.add_to_queue),
            onClick = {
                onQueue()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
        // The screen's onDownload starts, cancels or removes, whichever fits the state.
        SheetDownloadCard(
            downloadState = downloadState,
            onDownload = {
                onDownload()
                onDismiss()
            },
            onRemove = {
                onDownload()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (autoDownloadId != null) {
        AutoDownloadRow(autoDownloadId)
        Spacer(Modifier.height(bottom))
    }
}

// "download new songs": songs added to this list from now on download by themselves
@Composable
private fun AutoDownloadRow(playlistId: String) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val enabled = AutoDownloads.rememberEnabled(playlistId)
    val toggle = { scope.launch { AutoDownloads.setEnabled(context, database, playlistId, !enabled) } }
    Material3MenuGroup(
        items = listOf(
            Material3MenuItemData(
                title = { Text(stringResource(R.string.auto_download_new_songs)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                onClick = { toggle() },
                trailingContent = { AppSwitch(checked = enabled, onCheckedChange = { toggle() }) },
            ),
        ),
    )
}
