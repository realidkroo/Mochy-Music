// SpeedDialSheet.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.YouTubeAlbumRadio
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TilesPerPage = 9
private const val TilesPerRow = 3

// how many times the highlight hops round the page before it lands on the pick
private const val PickHops = 9

private val SheetInset = 24.dp
private val PageGap = 12.dp
private val PageShape = RoundedCornerShape(26.dp)
private val PagePadding = 14.dp
private val TileGap = 10.dp
private val AvatarSize = 52.dp

private val OwnerStyle: TextStyle
    get() = HomeTitleStyle.copy(fontSize = 34.sp, lineHeight = 38.sp)
private val EmptySlot = Color.White.copy(alpha = 0.04f)

/** your speed dial in a sheet: nine tiles a page, sliding on to the next page when there are more */
@Composable
fun SpeedDialSheet(
    items: StateFlow<List<YTItem>>,
    pinnedItems: StateFlow<List<YTItem>>,
    ownerName: String?,
    avatarUrl: String?,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val tiles by items.collectAsState()
    val pinned by pinnedItems.collectAsState()
    val pinnedIds = remember(pinned) { pinned.mapTo(HashSet()) { it.id } }
    val pages = remember(tiles) { tiles.chunked(TilesPerPage) }
    val pagerState = rememberPagerState { pages.size }

    val nowPlaying by playerConnection.mediaMetadata.collectAsState()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsState()

    // the random pick, read only in the tiles' layers so the hops don't recompose the grid
    val playable = remember(tiles) { tiles.filter { it.canPlayRightAway() } }
    var picking by remember { mutableStateOf(false) }
    val pickedId = remember { mutableStateOf<String?>(null) }
    val pickPop = remember { Animatable(1f) }

    fun pickRandom() {
        if (picking || playable.isEmpty()) return
        picking = true
        scope.launch {
            try {
                val winner = playable.random()
                val page = tiles.indexOfFirst { it.id == winner.id } / TilesPerPage
                pagerState.animateScrollToPage(page)
                // the highlight hops round the page, slowing down, then settles on the pick
                val onPage = pages.getOrNull(page).orEmpty()
                repeat(PickHops) { hop ->
                    pickedId.value = onPage.filter { it.id != pickedId.value }.randomOrNull()?.id ?: winner.id
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    delay(45L + hop * 20L)
                }
                pickedId.value = winner.id
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                pickPop.snapTo(1f)
                pickPop.animateTo(1.1f, tween(120))
                pickPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
                playSpeedDialItem(winner, playerConnection)
                onDismiss()
            } finally {
                picking = false
                pickedId.value = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        SpeedDialHeader(
            ownerName = ownerName,
            avatarUrl = avatarUrl,
            pill = when {
                playable.isEmpty() -> null
                picking -> HeroPillKind.Picking
                else -> HeroPillKind.Random
            },
            onRandom = ::pickRandom,
            modifier = Modifier.padding(horizontal = SheetInset),
        )
        Spacer(Modifier.height(20.dp))
        HorizontalPager(
            state = pagerState,
            // the next page peeks in at the right, so it's clear there's more to slide to
            contentPadding = PaddingValues(horizontal = SheetInset),
            pageSpacing = PageGap,
            userScrollEnabled = !picking,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            // the list can shrink under an open sheet (an unpin), a frame before the pager hears of it
            val pageTiles = pages.getOrNull(page) ?: return@HorizontalPager
            SpeedDialPage(
                tiles = pageTiles,
                pinnedIds = pinnedIds,
                nowPlayingId = nowPlaying?.id,
                isPlaying = isPlaying,
                pickedId = pickedId,
                pickPop = { pickPop.value },
                onOpen = onOpen,
                onMenu = onMenu,
            )
        }
    }
}

@Composable
private fun SpeedDialHeader(
    ownerName: String?,
    avatarUrl: String?,
    pill: HeroPillKind?,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(BoneColor)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(BoneColor)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = ownerName?.let { stringResource(R.string.speed_dial_owner, it) }
                    ?: stringResource(R.string.speed_dial_owner_you),
                style = OwnerStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.speed_dial_label),
                style = HomeLabelStyle,
                color = Color.White.copy(alpha = 0.69f),
            )
        }
        if (pill != null) {
            HeroPill(kind = pill, onClick = onRandom, enabled = pill == HeroPillKind.Random)
        }
    }
}

// one card of up to nine tiles; a short last page keeps empty slots so every card is the same size
@Composable
private fun SpeedDialPage(
    tiles: List<YTItem>,
    pinnedIds: Set<String>,
    nowPlayingId: String?,
    isPlaying: State<Boolean>,
    pickedId: MutableState<String?>,
    pickPop: () -> Float,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TileGap),
        modifier = Modifier
            .fillMaxWidth()
            .clip(PageShape)
            .background(PopupSheetRowSurface)
            .padding(PagePadding)
    ) {
        repeat(TilesPerPage / TilesPerRow) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                repeat(TilesPerRow) { column ->
                    val item = tiles.getOrNull(row * TilesPerRow + column)
                    if (item == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(SpeedDialTileShape)
                                .background(EmptySlot)
                        )
                    } else {
                        val active = item.id == nowPlayingId
                        SpeedDialGridItem(
                            item = item,
                            isPinned = item.id in pinnedIds,
                            isActive = active,
                            isPlaying = { active && isPlaying.value },
                            onClick = { onOpen(item) },
                            onLongClick = { onMenu(item) },
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    val picked = pickedId.value ?: return@graphicsLayer
                                    val chosen = picked == item.id
                                    alpha = if (chosen) 1f else 0.35f
                                    val scale = if (chosen) pickPop() else 1f
                                    scaleX = scale
                                    scaleY = scale
                                    // one dim pass per draw instead of an offscreen copy of each tile
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                },
                        )
                    }
                }
            }
        }
    }
}

// random only starts what can play straight away; the library's own playlists have to be loaded first
private fun YTItem.canPlayRightAway(): Boolean = when (this) {
    is SongItem -> true
    is AlbumItem -> playlistId.isNotBlank()
    is PlaylistItem -> !id.startsWith("LP") && id != "liked"
    is ArtistItem -> radioEndpoint != null || shuffleEndpoint != null
}

private fun playSpeedDialItem(item: YTItem, playerConnection: PlayerConnection) {
    when (item) {
        is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
        is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(item.playlistId))
        is PlaylistItem -> playerConnection.playQueue(YouTubeQueue(item.playEndpoint ?: WatchEndpoint(playlistId = item.id)))
        is ArtistItem -> (item.radioEndpoint ?: item.shuffleEndpoint)?.let { playerConnection.playQueue(YouTubeQueue(it)) }
    }
}
