// AlbumScreen.kt

package com.mochyy.mochymusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.LocalSyncUtils
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.LocalAlbumRadio
import com.mochyy.mochymusic.ui.component.HomeCardHighlight
import com.mochyy.mochymusic.ui.component.HomeCardSpacing
import com.mochyy.mochymusic.ui.component.HomeCoverCard
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.detail.CreatorUi
import com.mochyy.mochymusic.ui.component.detail.FeaturedArtistsRow
import com.mochyy.mochymusic.ui.component.detail.PlaylistDetailScaffold
import com.mochyy.mochymusic.ui.component.detail.PlaylistEndOfLine
import com.mochyy.mochymusic.ui.component.detail.PlaylistHeader
import com.mochyy.mochymusic.ui.component.detail.PlaylistInset
import com.mochyy.mochymusic.ui.component.detail.PlaylistSectionTitle
import com.mochyy.mochymusic.ui.component.detail.PlaylistStatsStyle
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackBones
import com.mochyy.mochymusic.ui.component.detail.PlaylistModeBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSearchBar
import com.mochyy.mochymusic.ui.component.detail.PlaylistSelectTick
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.detail.featuredArtistsOf
import com.mochyy.mochymusic.ui.component.detail.playlistCoverHeight
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.component.rememberRevealSeenState
import com.mochyy.mochymusic.ui.component.revealOnAppear
import com.mochyy.mochymusic.ui.menu.AlbumMenu
import com.mochyy.mochymusic.ui.menu.SelectionSongMenu
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.menu.YouTubeAlbumMenu
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.AlbumViewModel
import com.music.innertube.models.AlbumItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val moreByArtist by viewModel.moreByArtist.collectAsState()
    val description by viewModel.description.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val filteredSongs = remember(albumWithSongs, hideExplicit, hideVideoSongs) {
        var songs = albumWithSongs?.songs ?: emptyList()
        if (hideExplicit) songs = songs.filter { !it.song.explicit }
        if (hideVideoSongs) songs = songs.filter { !it.song.isVideo }
        songs
    }

    // the album's own song search, from the round button beside shuffle
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val shownSongs = remember(filteredSongs, isSearching, query.text) {
        val typed = query.text.trim()
        if (!isSearching || typed.isEmpty()) {
            filteredSongs
        } else {
            filteredSongs.filter { song ->
                song.song.title.contains(typed, ignoreCase = true) ||
                    song.artists.any { it.name.contains(typed, ignoreCase = true) }
            }
        }
    }
    val closeSearch = {
        isSearching = false
        query = TextFieldValue()
    }
    if (isSearching) {
        BackHandler(onBack = closeSearch)
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
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) selection.remove(songId)
        }
    }

    val representativeSongId = filteredSongs.firstOrNull()?.id
    val format by remember(representativeSongId) {
        representativeSongId?.let { database.format(it) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val album = albumWithSongs
    val totalSeconds = remember(filteredSongs) { filteredSongs.sumOf { it.song.duration } }
    val featuredArtists = remember(filteredSongs) {
        featuredArtistsOf(filteredSongs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val mainArtistName = album?.artists?.firstOrNull()?.name

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        val current = album ?: return
        playerConnection.service.getAutomix(playlistId)
        playerConnection.playQueue(
            if (shuffled) {
                LocalAlbumRadio(current.copy(songs = current.songs.shuffled()))
            } else {
                LocalAlbumRadio(current, startIndex = startIndex)
            }
        )
    }

    PlaylistDetailScaffold(
        sharedElementKey = "album-${viewModel.albumId}",
        coverUrl = album?.album?.thumbnailUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        onMoreClick = album?.let { current ->
            {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = Album(current.album, current.artists),
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        },
        topBarOverride = if (isSearching) {
            {
                PlaylistSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.search_in_album),
                    focusRequester = focusRequester,
                    onClose = closeSearch,
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
                    if (selectAll) selection.addAll(filteredSongs.map { it.id })
                },
                onMore = {
                    menuState.show {
                        SelectionSongMenu(
                            songSelection = selection.mapNotNull { id -> filteredSongs.find { it.id == id } },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
        } else null,
    ) {
        if (album == null || album.songs.isEmpty()) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                if (loadFailed) AlbumRetry(onRetry = viewModel::retry) else PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) item(key = "album_header", contentType = "header") {
            // read here, not at the top of the screen: a track change then only redraws the header
            val isThisAlbum = mediaMetadata?.album?.id == album.album.id
            PlaylistHeader(
                onSearchClick = { isSearching = true },
                title = album.album.title,
                creators = album.artists.map { CreatorUi(name = it.name, avatarUrl = it.thumbnailUrl, artistId = it.id) },
                // the description takes the stats' place, and the stats move under it
                description = description?.takeIf { it.isNotBlank() },
                stats = albumStats(
                    year = album.album.year,
                    songCount = filteredSongs.size,
                    totalSeconds = totalSeconds,
                    explicit = album.album.explicit,
                    format = format?.let { f ->
                        val codec = f.codecs.substringBefore('.').uppercase().ifBlank { null }
                        listOfNotNull(codec, (f.bitrate / 1000).takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" ")
                    },
                ),
                isPlaying = isThisAlbum && isPlaying,
                onPlayClick = {
                    when {
                        isThisAlbum && isPlaying -> playerConnection.player.pause()
                        isThisAlbum -> playerConnection.player.play()
                        else -> play()
                    }
                },
                onShuffleClick = { play(shuffled = true) },
                isSaved = album.album.bookmarkedAt != null,
                onSaveClick = { database.query { update(album.album.toggleLike()) } },
                onCreatorClick = { creator -> creator.artistId?.let { navController.navigate("artist/$it") } },
            )
        }

        itemsIndexed(
            items = shownSongs,
            key = { _, song -> song.id },
            contentType = { _, _ -> "track" },
        ) { position, song ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(song.id) else selection.remove(song.id)
            }
            SongSwipeBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = "${song.artists.joinToString { it.name }} • ${makeTimeString(song.song.duration * 1000L)}",
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${song.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            // the index in the whole album, which hidden songs may have shifted
                            play(startIndex = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
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
                            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                        }
                    },
                    selected = inSelectMode && song.id in selection,
                    liked = song.song.liked,
                    onLikeClick = {
                        val updated = song.song.toggleLike()
                        database.query { update(updated) }
                        syncUtils.likeSong(updated)
                    },
                    trailing = if (inSelectMode) {
                        { PlaylistSelectTick(checked = song.id in selection) }
                    } else null,
                )
            }
        }

        // while searching only the matching songs show, as on a playlist
        if (isSearching) {
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(50.dp))
            }
            return@PlaylistDetailScaffold
        }

        item(key = "end_of_line") {
            PlaylistEndOfLine(songCount = filteredSongs.size, totalSeconds = totalSeconds)
        }

        if (featuredArtists.isNotEmpty()) {
            item(key = "featured_artists") {
                FeaturedArtistsRow(
                    artists = featuredArtists,
                    onArtistClick = { navController.navigate("artist/${it.id}") },
                    modifier = Modifier
                        .padding(top = 25.dp)
                        .revealOnAppear(key = "featured_artists", seenState = revealSeen),
                )
            }
        }

        albumShelf(
            key = "more_by_artist",
            title = if (mainArtistName != null) "More by $mainArtistName" else "More by this artist",
            albums = moreByArtist,
            onTitleClick = album.artists.firstOrNull()?.id?.let { id -> { navController.navigate("artist/$id") } },
            navController = navController,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                }
            },
        )
        albumShelf(
            key = "other_versions",
            title = "Other versions",
            albums = otherVersions,
            navController = navController,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                }
            },
        )
        albumShelf(
            key = "releases_for_you",
            title = "Releases for you",
            albums = releasesForYou,
            navController = navController,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                }
            },
        )

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

/** "2016 · 14 songs · 52m · Explicit · OPUS 160 kbps" */
private fun albumStats(year: Int?, songCount: Int, totalSeconds: Int, explicit: Boolean, format: String?): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return listOfNotNull(
        year?.toString(),
        "$songCount ${if (songCount == 1) "song" else "songs"}",
        (if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m").takeIf { totalSeconds > 0 },
        "Explicit".takeIf { explicit },
        format?.ifBlank { null },
    ).joinToString(" · ")
}

/** a titled row of Home's square cards: more by the artist, other versions, releases for you */
private fun LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<AlbumItem>,
    navController: NavController,
    onLongClick: (AlbumItem) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    val distinct = albums.distinctBy { it.id }
    if (distinct.isEmpty()) return
    item(key = "${key}_title") {
        PlaylistSectionTitle(
            title = title,
            onClick = onTitleClick,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PlaylistInset),
            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = distinct, key = { it.id }) { item ->
                HomeCoverCard(
                    title = item.title,
                    subtitle = listOfNotNull(
                        item.artists?.joinToString { it.name }?.ifBlank { null },
                        item.year?.toString(),
                    ).joinToString(" · ").ifBlank { null },
                    thumbnailUrl = item.thumbnail,
                    sharedElementKey = "album-${item.id}",
                    modifier = Modifier.combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = { navController.navigate("album/${item.id}") },
                        onLongClick = { onLongClick(item) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AlbumRetry(onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Couldn't load this album. Tap to try again",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
