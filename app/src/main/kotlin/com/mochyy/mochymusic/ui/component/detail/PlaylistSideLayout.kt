package com.mochyy.mochymusic.ui.component.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.containerTransformTarget
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import kotlinx.coroutines.launch

// the cover's share of the width on its side, and the least and most it takes
private const val SideCoverShare = 0.46f
private val SideCoverMin = 280.dp
private val SideCoverMax = 560.dp

// the room the back button's row keeps above the first song
private val SideListTop = 62.dp

// the title block on its side sits on the cover at the left; the header item hands it here so the songs get the whole right side
@Stable
internal class PlaylistSideHeader {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal val LocalPlaylistSideHeader = staticCompositionLocalOf<PlaylistSideHeader?> { null }

// called by the header item on its side: keeps the item alive while it's scrolled away, so the block on the cover never goes stale
@Composable
internal fun PublishSideHeader(side: PlaylistSideHeader, body: @Composable () -> Unit) {
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(pinnable) {
        val handle = pinnable?.pin()
        onDispose { handle?.release() }
    }
    SideEffect { side.content = body }
    DisposableEffect(side) { onDispose { side.content = null } }
}

// a playlist on its side: the cover down the left with the title on it, fading into the page, the songs scrolling on the right
@Composable
internal fun PlaylistSideBySide(
    sharedElementKey: String?,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    framedCover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    topBarOverride: (@Composable () -> Unit)?,
    modeBar: PlaylistModeBar?,
    modifier: Modifier,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val side = remember { PlaylistSideHeader() }
    val glassState = remember { GlassState() }
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val statusTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val sides = WindowInsets.stableSystemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    // the song search bar sits over the songs; they start under its measured height while it's up
    val overrideBarHeightPx = remember { mutableIntStateOf(0) }
    val overrideBarHeight = with(density) { overrideBarHeightPx.intValue.toDp() }

    // turned sideways while scrolled down, the header item was never built: a visit to the top for two frames builds it
    // (and pins it, so it stays), then the list goes back where it was
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (side.content != null || lazyListState.firstVisibleItemIndex == 0) return@LaunchedEffect
        val index = lazyListState.firstVisibleItemIndex
        val offset = lazyListState.firstVisibleItemScrollOffset
        lazyListState.scrollToItem(0)
        withFrameNanos { }
        withFrameNanos { }
        lazyListState.scrollToItem(index, offset)
    }

    CompositionLocalProvider(
        LocalPlaylistSideHeader provides side,
        LocalPlaylistFramedCover provides framedCover,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .containerTransformTarget(sharedElementKey)
                .drawBehind { drawRect(background.value) }
                .then(modifier),
        ) {
            val coverWidth = (maxWidth * SideCoverShare).coerceIn(SideCoverMin, SideCoverMax)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { modeBar != null }),
            ) {
                Box(modifier = Modifier.width(coverWidth).fillMaxHeight()) {
                    SideCover(coverUrl = coverUrl, cover = cover, background = background)
                }
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        start = coverWidth,
                        top = contentPadding.calculateTopPadding() + statusTop + SideListTop + if (topBarOverride != null) overrideBarHeight else 0.dp,
                        end = contentPadding.calculateEndPadding(direction) + sides.calculateEndPadding(direction),
                        bottom = contentPadding.calculateBottomPadding().coerceAtLeast(bottomInset),
                    ),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }

            // the title, who made it and the buttons, on the cover just above the bottom bar
            SideHeaderHost(
                side = side,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    .padding(start = sides.calculateStartPadding(direction), bottom = bottomInset + 4.dp),
            )

            if (topBarOverride != null) {
                Box(
                    modifier = Modifier
                        .padding(start = coverWidth)
                        .onSizeChanged { overrideBarHeightPx.intValue = it.height },
                ) {
                    topBarOverride()
                }
            } else {
                PlaylistTopBar(
                    glassState = glassState,
                    // the cover never scrolls away on its side, so the bar stays open
                    morph = { 0f },
                    coverUrl = coverUrl,
                    cover = cover,
                    background = background,
                    onBackClick = onBackClick,
                    onBackLongClick = onBackLongClick,
                    onMoreClick = onMoreClick,
                    onMiniCoverClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
                    mode = modeBar,
                )
            }

            overlay()
        }
    }
}

// its own scope: the header item hands over a fresh block on every recomposition, and read in the page's scope that recomposed the cover and the list with it
@Composable
private fun SideHeaderHost(side: PlaylistSideHeader, modifier: Modifier) {
    Box(modifier = modifier) {
        side.content?.invoke()
    }
}

// the cover filling the left side, its right edge sinking into the page and its foot darkened under the title
@Composable
private fun SideCover(
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
) {
    val context = LocalContext.current
    // the card already holds the 544px copy in memory, so that shows at once while the sharp one loads
    val request = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl?.resize(1200, 1200))
            .placeholderMemoryCacheKey(coverUrl?.resize(544, 544))
            .build()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cover != null -> cover(Modifier.fillMaxSize())
            !coverUrl.isNullOrEmpty() -> AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val bg = background.value
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Transparent,
                        endY = size.height * 0.24f,
                    )
                    val foot = Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        0.78f to bg.copy(alpha = 0.72f),
                        1f to bg.copy(alpha = 0.94f),
                    )
                    // eased in and out, so the cover melts into the songs with no line where the fade starts
                    val edge = Brush.horizontalGradient(
                        0.55f to Color.Transparent,
                        0.75f to bg.copy(alpha = 0.35f),
                        0.9f to bg.copy(alpha = 0.82f),
                        1f to bg,
                    )
                    onDrawBehind {
                        drawRect(top)
                        drawRect(foot)
                        drawRect(edge)
                    }
                },
        )
    }
}
