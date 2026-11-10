// LocalPlaylistScreen.kt

package com.mochyy.mochymusic.ui.screens.playlist

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.PlaylistEditLockKey
import com.mochyy.mochymusic.constants.PlaylistSongSortDescendingKey
import com.mochyy.mochymusic.constants.PlaylistSongSortType
import com.mochyy.mochymusic.constants.PlaylistSongSortTypeKey
import com.mochyy.mochymusic.constants.PlaylistSortType
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.db.entities.PlaylistEvent
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.extensions.move
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.playlistcover.PlaylistCoverStore
import com.mochyy.mochymusic.ui.component.BoneColor
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.DraggableScrollbar
import com.mochyy.mochymusic.ui.component.EmptyPlaceholder
import com.mochyy.mochymusic.ui.component.HomeCardHighlight
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.component.detail.CreatorUi
import com.mochyy.mochymusic.ui.component.detail.FeaturedArtistsRow
import com.mochyy.mochymusic.ui.component.detail.PlaylistDetailScaffold
import com.mochyy.mochymusic.ui.component.detail.PlaylistEndOfLine
import com.mochyy.mochymusic.ui.component.detail.PlaylistHeader
import com.mochyy.mochymusic.ui.component.detail.PlaylistModeBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSearchBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSectionTitle
import com.mochyy.mochymusic.ui.component.detail.PlaylistSelectTick
import com.mochyy.mochymusic.ui.component.detail.PlaylistSortControl
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackBones
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.detail.featuredArtistsOf
import com.mochyy.mochymusic.ui.component.detail.playlistCoverHeight
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.component.rememberRevealSeenState
import com.mochyy.mochymusic.ui.component.removeFromPlaylistSwipeAction
import com.mochyy.mochymusic.ui.component.revealOnAppear
import com.mochyy.mochymusic.ui.component.showEditPlaylistSheet
import com.mochyy.mochymusic.ui.menu.LocalPlaylistMenu
import com.mochyy.mochymusic.ui.menu.SelectionSongMenu
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.LocalPlaylistViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val mutableSongs = remember { mutableStateListOf<com.mochyy.mochymusic.db.entities.PlaylistSong>() }
    val playlistLength = remember(songs) { songs.fastSumBy { it.song.song.duration } }
    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    var showSortDialog by remember { mutableStateOf(false) }
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<Int>, Int>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    val editable: Boolean = playlist?.playlist?.isEditable == true
    // unlocking from the menu turns the rows into drag handles; the bar's tick locks them again
    val isEditingOrder = sortType == PlaylistSongSortType.CUSTOM && !locked && !isSearching && editable && !inSelectMode

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    } else if (isEditingOrder) {
        BackHandler { locked = true }
    }

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist?.playlist?.name.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.song.id, false)
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist?.playlist?.name.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                            // Its generated or picked cover is no use to anyone now.
                            PlaylistCoverStore.deleteStale(context.applicationContext, viewModel.playlistId, null)
                        }
                        navController.popBackStack()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    // the header is the only item above the songs, and only while not searching
    val headerItems by rememberUpdatedState(if (isSearching) 0 else 1)
    val lazyListState = rememberLazyListState()
    // each new search shows its matches from the first, not from wherever the last one was scrolled (#128)
    LaunchedEffect(query.text) {
        if (isSearching) lazyListState.scrollToItem(0)
    }
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }
                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                database.transaction {
                    move(viewModel.playlistId, from, to)
                }

                if (viewModel.playlist.value
                        ?.playlist
                        ?.browseId != null
                ) {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val successorIndex = if (from > to) to else to + 1
                        val successorSetVideoId = playlistSongMap.getOrNull(successorIndex)?.setVideoId

                        playlistSongMap.getOrNull(from)?.setVideoId?.let { setVideoId ->
                            YouTube.moveSongPlaylist(
                                viewModel.playlist.value
                                    ?.playlist
                                    ?.browseId!!,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val coverUrl =
        remember(playlist, songs) {
            playlist?.thumbnails?.firstOrNull()
                ?: songs
                    .firstOrNull {
                        !it.song.song.thumbnailUrl
                            .isNullOrEmpty()
                    }?.song
                    ?.song
                    ?.thumbnailUrl
        }
    val featuredArtists =
        remember(songs) {
            featuredArtistsOf(songs.flatMap { it.song.artists }, id = { it.id }, name = { it.name })
        }
    val creatorAvatar = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }

    fun play(
        items: List<com.mochyy.mochymusic.db.entities.PlaylistSong>,
        startIndex: Int = 0,
    ) {
        val current = playlist ?: return
        playerConnection.playQueue(
            ListQueue(
                title = current.playlist.name,
                items = items.map { it.song.toMediaItem() },
                startIndex = startIndex,
            ),
        )
        database.query { insert(PlaylistEvent(playlistId = current.id, timestamp = LocalDateTime.now())) }
    }

    val openPlaylistMenu: (() -> Unit)? =
        playlist?.let { currentPlaylist ->
            {
                menuState.show {
                    LocalPlaylistMenu(
                        playlist = currentPlaylist,
                        songs = songs,
                        context = context,
                        downloadState = downloadState,
                        // Over the menu, not instead of it: Done comes back to it.
                        onEdit = {
                            val top = songs.minWithOrNull(compareBy({ it.map.position }, { it.map.id }))?.song
                            menuState.showEditPlaylistSheet(
                                playlist = currentPlaylist.playlist,
                                database = database,
                                context = context,
                                topSongId = top?.id,
                                topSongArtworkUrl = top?.song?.thumbnailUrl,
                            )
                        },
                        onSync = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val playlistPage =
                                    YouTube
                                        .playlist(currentPlaylist.playlist.browseId!!)
                                        .completed()
                                        .getOrNull() ?: return@launch
                                database.transaction {
                                    clearPlaylist(currentPlaylist.id)
                                    playlistPage.songs
                                        .map(SongItem::toMediaMetadata)
                                        .onEach(::insert)
                                        .mapIndexed { position, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = currentPlaylist.id,
                                                position = position,
                                                setVideoId = song.setVideoId,
                                            )
                                        }.forEach(::insert)
                                }
                            }
                            coroutineScope.launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                            }
                        },
                        onDelete = { showDeletePlaylistDialog = true },
                        onDownload = {
                            when (downloadState) {
                                Download.STATE_COMPLETED -> {
                                    showRemoveDownloadDialog = true
                                }

                                Download.STATE_DOWNLOADING -> {
                                    songs.forEach { song ->
                                        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.song.id, false)
                                    }
                                }

                                else -> {
                                    songs.forEach { song ->
                                        val downloadRequest =
                                            DownloadRequest
                                                .Builder(song.song.id, song.song.id.toUri())
                                                .setCustomCacheKey(song.song.id)
                                                .setData(
                                                    song.song.song.title
                                                        .toByteArray(),
                                                ).build()
                                        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
                                    }
                                }
                            }
                        },
                        onQueue = {
                            playerConnection.addToQueue(items = songs.map { it.song.toMediaItem() })
                        },
                        onDismiss = { menuState.dismiss() },
                        locked = locked,
                        onToggleLock = {
                            locked = !locked
                            // dragging only means something in your own order
                            if (!locked) onSortTypeChange(PlaylistSongSortType.CUSTOM)
                        },
                        onShowSortDialog = { showSortDialog = true },
                        onSearch = { isSearching = true },
                        onPlayNext = {
                            playerConnection.playNext(songs.map { it.song.toMediaItem() })
                        },
                        navController = navController,
                    )
                }
            }
        }

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlistId}",
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
                .asPaddingValues(),
        onMoreClick = openPlaylistMenu,
        topBarOverride =
            if (isSearching) {
                {
                    PlaylistSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_in_playlist),
                        focusRequester = focusRequester,
                        onClose = {
                            isSearching = false
                            query = TextFieldValue()
                        },
                    )
                }
            } else {
                null
            },
        modeBar =
            when {
                inSelectMode -> {
                    PlaylistModeBar.Select(
                        count = selection.size,
                        allSelected = selection.size == songs.size && selection.isNotEmpty(),
                        onToggleAll = {
                            val selectAll = selection.size != songs.size
                            selection.clear()
                            if (selectAll) selection.addAll(songs.map { it.map.id })
                        },
                        onMore = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.song },
                                    songPosition = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.map },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                        onClose = onExitSelectionMode,
                    )
                }

                isEditingOrder -> {
                    PlaylistModeBar.EditOrder(
                        onMore = openPlaylistMenu,
                        onDone = { locked = true },
                    )
                }

                else -> {
                    null
                }
            },
        overlay = {
            DraggableScrollbar(
                modifier =
                    Modifier
                        .padding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues())
                        .align(Alignment.CenterEnd),
                scrollState = lazyListState,
                headerItems = headerItems,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                        .align(Alignment.BottomCenter),
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (songs.isEmpty() && currentPlaylist.playlist.remoteSongCount == 0) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                    modifier = Modifier.animateItem(),
                )
            }
        } else if (!isSearching) {
            item(key = "playlist_header") {
                val isThisQueue = queueTitle == currentPlaylist.playlist.name
                val savedRemote = currentPlaylist.playlist.browseId != null && !currentPlaylist.playlist.isEditable
                PlaylistHeader(
                    onSearchClick = { isSearching = true },
                    title = currentPlaylist.playlist.name,
                    creators =
                        if (accountName.isNotBlank()) {
                            listOf(
                                CreatorUi(name = accountName, avatarUrl = creatorAvatar),
                            )
                        } else {
                            emptyList()
                        },
                    stats =
                        playlistStats(
                            context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size),
                            playlistLength,
                        ),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) playerConnection.togglePlayPause() else play(songs)
                    },
                    onShuffleClick = { play(songs.shuffled()) },
                    isSaved = if (savedRemote) currentPlaylist.playlist.bookmarkedAt != null else null,
                    onSaveClick = {
                        database.query { update(currentPlaylist.playlist.toggleLike()) }
                    },
                    sortControl = {
                        PlaylistSortControl(
                            current = sortType,
                            descending = sortDescending,
                            options = PlaylistSongSortType.entries,
                            label = { stringResource(playlistSortLabel(it)) },
                            onSelect = onSortTypeChange,
                            onToggleDirection = { onSortDescendingChange(!sortDescending) },
                            showDirection = { it != PlaylistSongSortType.CUSTOM },
                        )
                    },
                )
            }
        }

        itemsIndexed(
            items = if (isSearching) filteredSongs else mutableSongs,
            key = { _, song -> song.map.id },
            contentType = { _, _ -> "track" },
        ) { index, song ->
            ReorderableItem(
                state = reorderableState,
                key = song.map.id,
            ) {
                val currentItem by rememberUpdatedState(song)

                fun deleteFromPlaylist() {
                    database.transaction {
                        coroutineScope.launch {
                            playlist?.playlist?.browseId?.let { browseId ->
                                val setVideoId = getSetVideoId(currentItem.map.songId)
                                setVideoId?.setVideoId?.let { setVideoIdValue ->
                                    YouTube.removeFromPlaylist(browseId, currentItem.map.songId, setVideoIdValue)
                                }
                            }
                        }
                        move(currentItem.map.playlistId, currentItem.map.position, Int.MAX_VALUE)
                        delete(currentItem.map.copy(position = Int.MAX_VALUE))
                    }
                }

                // takes the song out, with a few seconds to put it back exactly where it was
                fun removeWithUndo() {
                    val removed = currentItem
                    deleteFromPlaylist()
                    val browseId = playlist?.playlist?.browseId
                    val playlistName = playlist?.playlist?.name ?: context.getString(R.string.playlist_fallback_name)
                    com.mochyy.mochymusic.ui.component.TopToaster.show(
                        text = context.getString(R.string.playlist_removed_song_from, removed.song.song.title, playlistName),
                        thumbnail = removed.song.song.thumbnailUrl,
                        iconRes = R.drawable.playlist_add,
                        actionLabel = context.getString(R.string.undo),
                        onAction = {
                            coroutineScope.launch {
                                database.transaction {
                                    insert(removed.map.copy(position = Int.MAX_VALUE))
                                    move(removed.map.playlistId, Int.MAX_VALUE, removed.map.position)
                                }
                                // a synced playlist gets it back on YouTube too (at the end there)
                                browseId?.let { launch(Dispatchers.IO) { YouTube.addToPlaylist(it, removed.map.songId) } }
                            }
                        },
                    )
                }

                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) selection.add(song.map.id) else selection.remove(Integer.valueOf(song.map.id))
                }

                val content: @Composable () -> Unit = {
                    PlaylistTrackRow(
                        thumbnailUrl = song.song.song.thumbnailUrl,
                        songId = song.song.id,
                        title = song.song.song.title,
                        subtitle = "${song.song.artists.joinToString { it.name }} • ${makeTimeString(song.song.song.duration * 1000L)}",
                        isActive = song.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier =
                            Modifier.revealOnAppear(
                                key = "track_${song.map.id}",
                                seenState = revealSeen,
                                delayMillis = minOf(index, 8) * 28,
                            ),
                        onClick = {
                            if (inSelectMode) {
                                onCheckedChange(!selection.contains(song.map.id))
                            } else if (song.song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                play(songs, startIndex = songs.indexOfFirst { it.map.id == song.map.id })
                            }
                        },
                        onLongClick = {
                            if (!inSelectMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                inSelectMode = true
                                onCheckedChange(true)
                            }
                        },
                        onMenuClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song.song,
                                    playlistSong = song,
                                    playlistBrowseId = playlist?.playlist?.browseId,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        selected = inSelectMode && selection.contains(song.map.id),
                        trailing =
                            when {
                                inSelectMode -> {
                                    { PlaylistSelectTick(checked = selection.contains(song.map.id)) }
                                }

                                // only the handle while reordering, so a tap can't open a menu mid-drag
                                isEditingOrder -> {
                                    {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = stringResource(R.string.playlist_drag_to_move),
                                                tint = Color.White.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    null
                                }
                            },
                    )
                }

                SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    enabled = !inSelectMode,
                    // in your own playlist the library action takes the song out; a saved one you
                    // can't edit adds it to your library instead. Settings > Gesture sets the side.
                    library = {
                        if (editable) {
                            removeFromPlaylistSwipeAction { removeWithUndo() }
                        } else {
                            librarySwipeAction(song.song.song)
                        }
                    },
                    queue = { queueSwipeAction { song.song.toMediaItem() } },
                ) {
                    content()
                }
            }
        }

        if (!isSearching && editable) {
            item(key = "add_music_row") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                menuState.showAddMusicSheet(
                                    playlistId = viewModel.playlistId,
                                    startInLibrary = songs.isEmpty(),
                                )
                            })
                            .padding(start = 36.dp, end = 24.dp, top = 9.dp, bottom = 9.dp)
                            .animateItem(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(39.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(BoneColor),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_music),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 11.dp),
                    )
                }
            }
        }

        if (!isSearching && songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = playlistLength)
            }
            if (featuredArtists.isNotEmpty()) {
                item(key = "featured_artists") {
                    FeaturedArtistsRow(
                        artists = featuredArtists,
                        onArtistClick = { navController.navigate("artist/${it.id}") },
                        modifier = Modifier.padding(top = 25.dp),
                    )
                }
            }
        }

        if (!isSearching) {
            item(key = "related_playlists") {
                val allPlaylists by database.playlists(PlaylistSortType.CREATE_DATE, true).collectAsState(initial = emptyList())
                val relatedPlaylists =
                    remember(allPlaylists, playlist) {
                        allPlaylists.filter { it.id != playlist?.id }.shuffled().take(5)
                    }

                if (relatedPlaylists.isNotEmpty()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp),
                    ) {
                        PlaylistSectionTitle(title = stringResource(R.string.playlist_related))
                        Spacer(Modifier.height(9.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(relatedPlaylists, key = { it.id }) { related ->
                                // no clip on the whole card: its rounded corner cut into the song count
                                // under the cover. The press shows Home's highlight around it instead.
                                Column(
                                    modifier =
                                        Modifier
                                            .width(137.dp)
                                            .clickable(interactionSource = null, indication = HomeCardHighlight) {
                                                navController.navigate("local_playlist/${related.id}")
                                            },
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(BoneColor),
                                    ) {
                                        val thumbUrl = related.playlist.thumbnailUrl ?: related.thumbnails.firstOrNull()
                                        if (thumbUrl != null) {
                                            AsyncImage(
                                                model = thumbUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.music_note),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier =
                                                    Modifier
                                                        .size(44.dp)
                                                        .align(Alignment.Center),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = related.playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.92f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.n_song, related.songCount, related.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.playlist_sort_order)) },
            text = {
                Column {
                    PlaylistSongSortType.entries.forEach { type ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                        showSortDialog = false
                                    }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = sortType == type,
                                onClick = {
                                    if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                    showSortDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text =
                                    stringResource(playlistSortLabel(type)) +
                                        if (sortType ==
                                            type
                                        ) {
                                            (
                                                if (sortDescending) {
                                                    stringResource(
                                                        R.string.playlist_sort_desc,
                                                    )
                                                } else {
                                                    stringResource(R.string.playlist_sort_asc)
                                                }
                                            )
                                        } else {
                                            ""
                                        },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun playlistSortLabel(type: PlaylistSongSortType): Int =
    when (type) {
        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
        PlaylistSongSortType.NAME -> R.string.sort_by_name
        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
    }
