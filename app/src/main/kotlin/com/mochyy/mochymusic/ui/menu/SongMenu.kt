// SongMenu.kt

package com.mochyy.mochymusic.ui.menu

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.importer.ImportedVersions
import com.mochyy.mochymusic.importer.TrackMatch
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.LocalSyncUtils
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.ListItemHeight
import com.mochyy.mochymusic.constants.ListThumbnailSize
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.db.entities.PlaylistSong
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.ListDialog
import com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState
import com.mochyy.mochymusic.ui.component.Material3MenuGroup
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.NewAction
import com.mochyy.mochymusic.ui.component.NewActionGrid
import com.mochyy.mochymusic.ui.component.SheetActionCard
import com.mochyy.mochymusic.ui.component.SheetActionCardRow
import com.mochyy.mochymusic.ui.component.SheetDownloadCard
import com.mochyy.mochymusic.ui.component.SheetHeaderGap
import com.mochyy.mochymusic.ui.component.SheetHeader
import com.mochyy.mochymusic.ui.component.SheetHeaderButton
import com.mochyy.mochymusic.ui.component.SongListItem
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.utils.listItemShape
import com.mochyy.mochymusic.ui.utils.ShowMediaInfo
import com.mochyy.mochymusic.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val downloadUtil = LocalDownloadUtil.current

    val download by remember(downloadUtil, originalSong.id) {
        downloadUtil.getDownload(originalSong.id)
    }.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val cacheViewModel = hiltViewModel<CachePlaylistViewModel>()

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    val isPinned by database.speedDialDao.isPinned(song.id).collectAsState(initial = false)

    // the track an import brought this song in as: offers "Choose different version", and makes
    // Refetch search for that track again rather than only refresh this song's details
    val importMatch by produceState<TrackMatch?>(initialValue = null, song.id) {
        value = ImportedVersions.trackFor(context, database, song.id)
    }
    val menuState = LocalMenuState.current

    val orderedArtists by produceState(initialValue = emptyList<ArtistEntity>(), song) {
        withContext(Dispatchers.IO) {
            val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
            val sorted = artistMaps.mapNotNull { map ->
                song.artists.firstOrNull { it.id == map.artistId }
            }
            value = sorted
        }
    }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> = Saver(
        save = { it.text },
        restore = { text -> TextFieldValue(text, TextRange(text.length)) }
    )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.artists.firstOrNull()?.name.orEmpty()))
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields = listOf(
                stringResource(R.string.song_title) to titleField,
                stringResource(R.string.artist_name) to artistField
            ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) titleField = newValue
                else artistField = newValue
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    database.query {
                        update(song.song.copy(title = newTitle))
                        val artist = song.artists.firstOrNull()
                        if (artist != null) {
                            update(artist.copy(name = newArtist))
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            listOf(song.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        topSongId = song.id,
        topSongArtworkUrl = song.song.thumbnailUrl,
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(listOf(song)) { song ->
                SongListItem(song = song, isSwipeable = false)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = song.artists.distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    SheetHeader(
        thumbnailUrl = song.song.thumbnailUrl,
        title = song.song.title,
        subtitle = song.artists.joinToString(", ") { it.name },
        trailing = {
            SheetHeaderButton(
                icon = if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (song.song.liked) MaterialTheme.colorScheme.error else Color.White,
                onClick = {
                    val s = song.song.toggleLike()
                    database.query {
                        update(s)
                    }
                    syncUtils.likeSong(s)
                },
            )
        },
    )

    Spacer(Modifier.height(SheetHeaderGap))

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            // Download and Play next are what people open a song's menu for, so they get the two
            // big cards. Then the song-menu mock's four rows - add to playlist, share, album,
            // artist - and the rest.
            SheetActionCardRow {
                SheetDownloadCard(
                    downloadState = download?.state,
                    onDownload = {
                        val downloadRequest = DownloadRequest
                            .Builder(song.id, song.id.toUri())
                            .setCustomCacheKey(song.id)
                            .setData(song.song.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                    },
                    onRemove = {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetActionCard(
                    icon = R.drawable.playlist_play,
                    title = stringResource(R.string.play_next),
                    onClick = {
                        onDismiss()
                        playerConnection.playNext(song.toMediaItem())
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_an_playlist)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        onClick = { showChoosePlaylistDialog = true }
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.share)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            com.mochyy.mochymusic.ui.player.share.song.SongShare.open(song.toMediaMetadata())
                        }
                    ),
                    if (song.song.albumId != null) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.view_album)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                navController.navigate("album/${song.song.albumId}")
                            }
                        )
                    } else null,
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.view_artist)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.artist),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (song.artists.size == 1) {
                                navController.navigate("artist/${song.artists[0].id}")
                                onDismiss()
                            } else {
                                showSelectArtistDialog = true
                            }
                        }
                    ),
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.edit)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                            )
                        },
                        onClick = { showEditDialog = true }
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.play)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = song.song.title,
                                    items = listOf(song.toMediaItem()),
                                )
                            )
                        }
                    ),
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOfNotNull(

                    if (true) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.start_radio)) },
                            description = { Text(text = stringResource(R.string.start_radio_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            }
                        )
                    } else null,
                    if (true) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(song.toMediaItem())
                            }
                        )
                    } else null
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = stringResource(if (isPinned) R.string.unpin_from_speed_dial else R.string.pin_to_speed_dial)
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(song.id)
                                    } else {
                                        database.pinToSpeedDial(
                                            SpeedDialItem(
                                                id = song.id,
                                                title = song.song.title,
                                                subtitle = song.artists.joinToString(", ") { it.name },
                                                thumbnailUrl = song.song.thumbnailUrl,
                                                type = "SONG",
                                                explicit = song.song.explicit
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (song.song.inLibrary == null) R.string.add_to_library
                                        else R.string.remove_from_library
                                    )
                                )
                            },
                            description = { Text(text = stringResource(R.string.add_to_library_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (song.song.inLibrary == null) R.drawable.library_add
                                        else R.drawable.library_add_check
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val currentSong = song.song
                                val isInLibrary = currentSong.inLibrary != null
                                val token =
                                    if (isInLibrary) currentSong.libraryRemoveToken else currentSong.libraryAddToken

                                token?.let {
                                    coroutineScope.launch {
                                        YouTube.feedback(listOf(it))
                                    }
                                }

                                database.query {
                                    update(song.song.toggleLibrary())
                                }
                            }
                        )
                    )
                    if (event != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    database.query {
                                        delete(event)
                                    }
                                }
                            )
                        )
                    }
                    if (playlistSong != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_playlist)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    database.transaction {
                                        coroutineScope.launch {
                                            playlistBrowseId?.let { playlistId ->
                                                if (playlistSong.map.setVideoId != null) {
                                                    YouTube.removeFromPlaylist(
                                                        playlistId,
                                                        playlistSong.map.songId,
                                                        playlistSong.map.setVideoId
                                                    )
                                                }
                                            }
                                        }
                                        move(
                                            playlistSong.map.playlistId,
                                            playlistSong.map.position,
                                            Int.MAX_VALUE
                                        )
                                        delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }
                    if (isFromCache) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_cache)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    cacheViewModel.removeSongFromCache(song.id)
                                }
                            )
                        )
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    importMatch?.let { match ->
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.menu_choose_version)) },
                                description = { Text(text = stringResource(R.string.menu_choose_version_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.tune),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuState.showChooseVersionSheet(match, onChosen = onDismiss)
                                }
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.refetch)) },
                            description = {
                                Text(
                                    text = if (importMatch != null) stringResource(R.string.pc_refetch_import_desc)
                                    else stringResource(R.string.refetch_desc)
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                                )
                            },
                            onClick = {
                                refetchIconDegree -= 360
                                val match = importMatch
                                if (match != null) {
                                    refetchImported(context, database, match)
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        YouTube.queue(listOf(song.id)).onSuccess {
                                            val newSong = it.firstOrNull()
                                            if (newSong != null) {
                                                database.transaction {
                                                    update(song, newSong.toMediaMetadata())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    )
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                bottomSheetPageState.show {
                                    ShowMediaInfo(song.id)
                                }
                            }
                        )
                    )
                }
            )
        }
    }
}

/**
 * Refetch on an imported song: searches for the track it was imported as again, and says what came
 * of it. It used to only refresh this song's own details, so a wrong version stayed wrong and the
 * button looked like it did nothing.
 */
private fun refetchImported(context: android.content.Context, database: com.mochyy.mochymusic.db.MusicDatabase, match: TrackMatch) {
    ImportedVersions.scope.launch {
        val switched = ImportedVersions.refetch(context.applicationContext, database, match)
        if (switched != null) {
            TopToaster.show(text = context.getString(R.string.pc_closer_match, switched.title), thumbnail = switched.thumbnail, iconRes = R.drawable.sync)
        } else {
            TopToaster.show(
                text = context.getString(R.string.pc_closest_match),
                iconRes = R.drawable.sync,
                durationMs = 4000L,
            )
        }
    }
}
