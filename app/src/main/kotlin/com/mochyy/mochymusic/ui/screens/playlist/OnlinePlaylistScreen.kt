// OnlinePlaylistScreen.kt

package com.mochyy.mochymusic.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.queues.YouTubePlaylistQueue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.HomeCardHighlight
import com.mochyy.mochymusic.ui.component.HomeCardSpacing
import com.mochyy.mochymusic.ui.component.HomeCoverCard
import com.mochyy.mochymusic.ui.component.coverTransitionKey
import com.mochyy.mochymusic.ui.component.detail.PlaylistInset
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.component.detail.CreatorUi
import com.mochyy.mochymusic.ui.component.detail.featuredArtistsOf
import com.mochyy.mochymusic.ui.component.detail.FeaturedArtistsRow
import com.mochyy.mochymusic.ui.component.detail.PlaylistDetailScaffold
import com.mochyy.mochymusic.ui.component.detail.PlaylistEndOfLine
import com.mochyy.mochymusic.ui.component.detail.PlaylistHeader
import com.mochyy.mochymusic.ui.component.detail.PlaylistSearchBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSectionTitle
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackBones
import com.mochyy.mochymusic.ui.component.detail.PlaylistModeBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSelectTick
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.rememberRevealSeenState
import com.mochyy.mochymusic.ui.component.revealOnAppear
import com.mochyy.mochymusic.ui.menu.YouTubeAlbumMenu
import com.mochyy.mochymusic.ui.menu.YouTubeArtistMenu
import com.mochyy.mochymusic.ui.menu.YouTubePlaylistMenu
import com.mochyy.mochymusic.ui.menu.YouTubeSelectionSongMenu
import com.mochyy.mochymusic.ui.menu.YouTubeSongMenu
import com.mochyy.mochymusic.ui.menu.toggleSavedPlaylist
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.viewmodels.OnlinePlaylistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val downloadUtil = LocalDownloadUtil.current

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs.mapIndexed { i, s -> i to s }
        else songs.mapIndexed { i, s -> i to s }.filter {
            it.second.title.contains(query.text, true) ||
                    it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }
    // each new search shows its matches from the first, not from wherever the last one was scrolled (#128)
    LaunchedEffect(query.text) {
        if (isSearching) lazyListState.scrollToItem(0)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val coverUrl = playlist?.thumbnail ?: songs.firstOrNull { !it.thumbnail.isNullOrEmpty() }?.thumbnail
    val totalSeconds = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlistId}",
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        onMoreClick = playlist?.let { currentPlaylist ->
            {
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = currentPlaylist,
                        songs = songs,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                        onImportedPlaylist = { playlistId ->
                            navController.navigate("local_playlist/$playlistId")
                        },
                    )
                }
            }
        },
        topBarOverride = if (isSearching) {
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
        } else null,
        modeBar = if (inSelectMode) {
            PlaylistModeBar.Select(
                count = selection.size,
                allSelected = selection.size == filteredSongs.size && selection.isNotEmpty(),
                onToggleAll = {
                    val selectAll = selection.size != filteredSongs.size
                    selection.clear()
                    if (selectAll) selection.addAll(filteredSongs.map { it.second.id })
                },
                onMore = {
                    menuState.show {
                        YouTubeSelectionSongMenu(
                            songSelection = filteredSongs.filter { it.second.id in selection }.map { it.second },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
        } else null,
        overlay = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null || songs.isEmpty()) {
            if (isLoading) {
                item(key = "loading_header") {
                    Spacer(Modifier.height(com.mochyy.mochymusic.ui.component.detail.playlistCoverHeight() * 0.975f))
                }
                item(key = "loading_tracks") {
                    PlaylistTrackBones()
                }
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) {
            item(key = "playlist_header") {
                // read here, not at the top of the screen: a new queue then only redraws the header
                val isThisQueue = queueTitle == currentPlaylist.title
                val isEditableRemote = currentPlaylist.id == "LM" || currentPlaylist.isEditable
                PlaylistHeader(
                    onSearchClick = { isSearching = true },
                    title = currentPlaylist.title,
                    creators = listOfNotNull(
                        currentPlaylist.author?.let { CreatorUi(name = it.name, artistId = it.id) }
                    ),
                    stats = playlistStats(context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size), totalSeconds),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = songs,
                                    initialContinuation = viewModel.continuation
                                )
                            )
                        }
                    },
                    onShuffleClick = {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = currentPlaylist.id,
                                playlistTitle = currentPlaylist.title,
                                initialSongs = songs.shuffled(),
                                initialContinuation = viewModel.continuation
                            )
                        )
                    },
                    isSaved = if (isEditableRemote) null else dbPlaylist?.playlist?.bookmarkedAt != null,
                    onSaveClick = {
                        database.toggleSavedPlaylist(currentPlaylist, dbPlaylist, songs, coroutineScope)
                    },
                )
            }
        }

        itemsIndexed(
            items = filteredSongs,
            key = { _, (index, song) -> "${index}_${song.id}" },
            contentType = { _, _ -> "track" },
        ) { position, (_, songItem) ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(songItem.id) else selection.remove(songItem.id)
            }

            SongSwipeBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                library = { librarySwipeAction(songItem) },
                queue = { queueSwipeAction { songItem.toMediaItem() } },
            ) {
                val active = mediaMetadata?.id == songItem.id
                PlaylistTrackRow(
                    thumbnailUrl = songItem.thumbnail,
                    songId = songItem.id,
                    title = songItem.title,
                    subtitle = "${songItem.artists.joinToString { it.name }} • ${makeTimeString(songItem.duration?.times(1000L))}",
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${songItem.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(songItem.id !in selection)
                        } else if (songItem.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = filteredSongs.map { it.second },
                                    initialContinuation = viewModel.continuation,
                                    startIndex = position
                                )
                            )
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
                            YouTubeSongMenu(songItem, navController, menuState::dismiss)
                        }
                    },
                    selected = inSelectMode && songItem.id in selection,
                    trailing = if (inSelectMode) {
                        { PlaylistSelectTick(checked = songItem.id in selection) }
                    } else null,
                )
            }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                PlaylistTrackBones(rows = 2)
            }
        }

        if (!isSearching) {
            if (!isLoadingMore && viewModel.continuation == null) {
                item(key = "end_of_line") {
                    PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
                }
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

            if (relatedItems.isNotEmpty()) {
                item(key = "related_title") {
                    PlaylistSectionTitle(
                        title = "Related Playlists",
                        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                    )
                }

                // Home's cards on the page's own inset: the same corners, type and spacing as
                // every other row of covers, and each one grows into its page like from Home
                item(key = "related_items") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PlaylistInset),
                        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(relatedItems.distinctBy { it.id }, key = { it.id }) { item ->
                            HomeCoverCard(
                                title = item.title,
                                subtitle = when (item) {
                                    is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
                                    is AlbumItem -> listOfNotNull(
                                        item.artists?.joinToString { it.name }?.ifBlank { null },
                                        item.year?.toString(),
                                    ).joinToString(" · ").ifBlank { null }
                                    is SongItem -> item.artists.joinToString { it.name }
                                    is ArtistItem -> null
                                },
                                thumbnailUrl = item.thumbnail,
                                circular = item is ArtistItem,
                                sharedElementKey = coverTransitionKey(item),
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = HomeCardHighlight,
                                        onClick = {
                                            when (item) {
                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                is AlbumItem -> navController.navigate("album/${item.browseId}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                is SongItem -> playerConnection.playQueue(
                                                    YouTubeQueue(WatchEndpoint(videoId = item.id))
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                when (item) {
                                                    is PlaylistItem -> YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                        onImportedPlaylist = { playlistId ->
                                                            navController.navigate("local_playlist/$playlistId")
                                                        }
                                                    )
                                                    is SongItem -> YouTubeSongMenu(
                                                        song = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is AlbumItem -> YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is ArtistItem -> YouTubeArtistMenu(
                                                        artist = item,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

/** "21 songs · 1h 23m" */
internal fun playlistStats(songCountText: String, totalSeconds: Int): String {
    if (totalSeconds <= 0) return songCountText
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val duration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$songCountText · $duration"
}
