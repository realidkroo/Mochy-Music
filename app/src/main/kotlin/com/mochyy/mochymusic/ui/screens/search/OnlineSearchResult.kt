// OnlineSearchResult.kt

package com.mochyy.mochymusic.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.os.Build
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PauseSearchHistoryKey
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.RoundedHighlight
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.menu.YouTubeAlbumMenu
import com.mochyy.mochymusic.ui.menu.YouTubeArtistMenu
import com.mochyy.mochymusic.ui.menu.YouTubePlaylistMenu
import com.mochyy.mochymusic.ui.menu.YouTubeSongMenu
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder
import com.mochyy.mochymusic.ui.component.navigateToTab

private val SearchCategories = listOf(
    "All" to null,
    "Music" to FILTER_SONG,
    "Artist" to FILTER_ARTIST,
    "Video" to FILTER_VIDEO,
    "Album" to FILTER_ALBUM,
    "Playlist" to FILTER_COMMUNITY_PLAYLIST,
    "Featured" to FILTER_FEATURED_PLAYLIST,
)

@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val summary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf { searchFilter?.value?.let { viewModel.viewStateMap[it] } }
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.query, TextRange(viewModel.query.length)))
    }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val progressProvider = remember(collapse) { { collapse.value } }

    val selectedIndex = remember(searchFilter) {
        SearchCategories.indexOfFirst { it.second?.value == searchFilter?.value }.coerceAtLeast(0)
    }

    LaunchedEffect(searchFilter) { listState.scrollToItem(0) }

    LaunchedEffect(listState, itemsPage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            if (nearEnd && itemsPage?.continuation != null) viewModel.loadMore()
        }
    }

    BackHandler { navController.navigateUp() }

    val submit: (String) -> Unit = remember(navController, pauseSearchHistory) {
        { raw ->
            val text = raw.trim()
            if (text.isNotEmpty() && text != viewModel.query) {
                focusManager.clearFocus()
                navController.navigate("search/${URLEncoder.encode(text, "UTF-8")}") {
                    popUpTo("search/${URLEncoder.encode(viewModel.query, "UTF-8")}") {
                        inclusive = true
                    }
                }
                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query { insert(SearchHistory(query = text)) }
                    }
                }
            }
        }
    }

    val onItemClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                }
            }

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    val onItemLongClick: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(item, navController, menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(item, navController, menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(item, menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    val top = remember(summary, viewModel.query) {
        summary?.let { pickTopResult(it.summaries.flatMap { section -> section.items }, viewModel.query) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // recorded while scrolling too, so the bar's blur never drops out mid-scroll
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f })
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = searchTopBarHeight(withTitle = true, extra = 46.dp),
                    bottom = bottomInset + 24.dp,
                ),
            ) {
                if (searchFilter == null) {

                    val page = summary
                    if (page == null) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else {
                        if (top != null) {
                            item(key = "top_header") {
                                SearchSectionHeader(title = "Top Results", ruleAbove = false)
                            }
                            item(key = "top_card") {
                                TopResultCard(
                                    item = top,
                                    isActive = mediaMetadata?.id == top.id,
                                    isPlaying = isPlaying,
                                    onClick = { onItemClick(top) },
                                    onLongClick = { onItemLongClick(top) },
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                            }
                        }

                        var renderedSection = false
                        page.summaries.forEachIndexed { index, section ->
                            val rows = section.items.filter { it.id != top?.id }
                            if (rows.isNotEmpty()) {
                                // a row's own rule already closes the previous section
                                val ruleAbove = !renderedSection
                                renderedSection = true
                                item(key = "summary_header_${index}_${section.title}") {
                                    SearchSectionHeader(
                                        title = if (index == 0) "Exact matches" else section.title,
                                        ruleAbove = ruleAbove,
                                    )
                                }
                                items(
                                    items = rows,
                                    key = { "summary_${index}_${it.id}" },
                                    contentType = { "resultRow" },
                                ) { item ->
                                    ResultRow(
                                        item = item,
                                        isActive = mediaMetadata?.id == item.id,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                        onMenu = { onItemLongClick(item) },
                                    )
                                }
                                item(key = "summary_gap_$index") {
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    val page = itemsPage
                    if (page == null) {
                        item(key = "filter_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else if (page.items.isEmpty()) {
                        item(key = "filter_empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No results",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = SearchColors.Secondary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = page.items,
                            key = { it.id },
                            contentType = { "resultRow" },
                        ) { item ->
                            ResultRow(
                                item = item,
                                isActive = mediaMetadata?.id == item.id,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                onMenu = { onItemLongClick(item) },
                            )
                        }
                    }
                }
            }
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = stringResource(R.string.search),
            trailing = {
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navController.navigateToTab("settings") })
            },
            below = {
                SearchCategoryRow(
                    categories = SearchCategories.map { it.first },
                    selectedIndex = selectedIndex,
                    onSelect = { index -> viewModel.filter.value = SearchCategories[index].second },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = submit,
                placeholder = "Search for any tracks, albums, lyrics...",
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = SearchColors.Secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { navController.navigateUp() },
                    )
                },
            )
        }
    }
}

private fun pickTopResult(items: List<YTItem>, query: String): YTItem? {
    if (items.isEmpty()) return null
    val normalised = query.trim().lowercase()

    items.filterIsInstance<ArtistItem>()
        .firstOrNull { it.title.trim().lowercase() == normalised }
        ?.let { return it }

    val songs = items.filterIsInstance<SongItem>()
    songs.firstOrNull { !it.isVideoSong }?.let { return it }

    return songs.firstOrNull() ?: items.first()
}

@Composable
private fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kind = when (item) {
        is SongItem -> if (item.isVideoSong) "Video" else "Song"
        is AlbumItem -> "Album"
        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist"
    }
    val detail = when (item) {
        is SongItem -> listOfNotNull(
            kind,
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            kind,
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.year?.toString(),
        ).joinToString("  •  ")

        is ArtistItem -> kind
        is PlaylistItem -> listOfNotNull(kind, item.author?.name?.let { "by $it" }, item.songCountText)
            .joinToString("  •  ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(SearchColors.Tile)
            .searchCardBorder(16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {

        if (item.thumbnail != null) {
            AsyncImage(
                model = item.thumbnail?.resize(256, 256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 0.55f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = com.mochyy.mochymusic.ui.component.BlurEffectCache.get(
                                70f,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Black.copy(alpha = 0.62f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
        SearchArtwork(
            url = item.thumbnail,
            size = 108.dp,
            circle = item is ArtistItem,
            corner = 8.dp,
            sharedElementKey = com.mochyy.mochymusic.ui.component.coverTransitionKey(item),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item !is ArtistItem) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActive && isPlaying) R.drawable.ic_untitled_pause else R.drawable.play
                        ),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        }
    }
}

// no play state here: rows only tint the active title, so a play or pause leaves every row alone
@Composable
private fun ResultRow(
    item: YTItem,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> listOfNotNull(
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            "Album",
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
        ).joinToString("  •  ")

        is ArtistItem -> "Artist"
        is PlaylistItem -> listOfNotNull("Playlist", item.author?.name).joinToString("  •  ")
    }

    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = null,
                    indication = ResultRowHighlight,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                url = item.thumbnail,
                size = 46.dp,
                circle = item is ArtistItem,
                sharedElementKey = com.mochyy.mochymusic.ui.component.coverTransitionKey(item),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),

                    color = if (isActive) Color(0xFF7FD1FF) else SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SearchOverflowDots(onClick = onMenu)
        }
    }

    Column {
        // songs slide to queue or save to the library; Settings > Gesture sets which side does which
        if (item is SongItem) {
            SongSwipeBox(
                library = { librarySwipeAction(item) },
                queue = { queueSwipeAction { item.toMediaItem() } },
            ) {
                row()
            }
        } else {
            row()
        }
        SearchRule()
    }
}

// pressing a result lights a pill from one side of the screen to the other
private val ResultRowHighlight = RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp)

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
