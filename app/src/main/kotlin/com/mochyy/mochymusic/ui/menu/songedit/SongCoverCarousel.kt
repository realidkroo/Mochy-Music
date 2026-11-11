// SongCoverCarousel.kt

package com.mochyy.mochymusic.ui.menu.songedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.songedit.CoverOption
import com.mochyy.mochymusic.songedit.CoverPick
import com.mochyy.mochymusic.songedit.CoverSources
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val CustomPage = 0
private const val CurrentPage = 1
private const val FixedPages = 2

// how long a typed title or artist has to stay put before the covers are looked up again
private const val RetypeSettleMs = 900L

// the edit sheet's cover row: your own picture, the cover it has now in the middle, then every cover the sources found; the one resting in the middle is the pick
@Composable
internal fun SongCoverCarousel(state: SongEditState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateListOf<CoverOption>() }
    var searching by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pagerState = rememberPagerState(initialPage = CurrentPage) { FixedPages + found.size }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    val pickPicture = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // a song whose name was wrong gets better covers once it's fixed, so the search follows what's typed
    val query by remember {
        derivedStateOf { state.title.trim() to state.artists.joinToString(" ") { it.name } }
    }
    val searchedOnce = remember { booleanArrayOf(false) }
    LaunchedEffect(query) {
        if (searchedOnce[0]) {
            delay(RetypeSettleMs)
            if (pagerState.currentPage >= FixedPages) pagerState.scrollToPage(CurrentPage)
            found.clear()
        }
        searchedOnce[0] = true
        searching = true
        val seen = mutableSetOf(state.currentCover)
        val durationMs = state.metadata.duration.takeIf { it > 0 }?.times(1000L)
        CoverSources.search(query.first, query.second, state.album.ifBlank { null }, durationMs).collect { option ->
            if (seen.add(option.url)) found.add(option)
        }
        searching = false
    }

    LaunchedEffect(picked) {
        if (picked != null) pagerState.animateScrollToPage(CustomPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to picked }.collect { (page, uri) ->
            state.cover = when (page) {
                CustomPage -> uri?.let(CoverPick::Picture)
                CurrentPage -> null
                else -> found.getOrNull(page - FixedPages)?.let { CoverPick.Link(it.url) }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // a bit over half the width, so the next cover peeks in on any phone
            val card = (maxWidth * 0.55f).coerceIn(150.dp, 240.dp)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(card),
                pageSpacing = 14.dp,
                contentPadding = PaddingValues(horizontal = (maxWidth - card) / 2),
                key = { page -> if (page < FixedPages) "fixed$page" else found.getOrNull(page - FixedPages)?.url ?: page },
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val option = found.getOrNull(page - FixedPages)
                CoverPage(
                    page = page,
                    size = card,
                    pagerState = pagerState,
                    label = when (page) {
                        CustomPage -> stringResource(R.string.song_edit_cover_custom)
                        CurrentPage -> stringResource(R.string.song_edit_cover_current)
                        else -> option?.source.orEmpty()
                    },
                    image = when (page) {
                        CustomPage -> picked
                        CurrentPage -> state.currentCover
                        else -> option?.url
                    },
                    isCustom = page == CustomPage,
                    onClick = {
                        when {
                            pagerState.currentPage != page -> scope.launch { pagerState.animateScrollToPage(page) }
                            page == CustomPage -> pickPicture()
                        }
                    },
                )
            }
        }
        if (searching) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.song_edit_cover_looking),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
    }
}

// one cover with where it's from under it; the ones beside the middle sit back a little, read in the layer so a swipe never recomposes
@Composable
private fun CoverPage(
    page: Int,
    size: Dp,
    pagerState: PagerState,
    label: String,
    image: Any?,
    isCustom: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            val scale = 1f - 0.08f * distance
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.4f * distance
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(28.dp))
                .background(MenuRowSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = onClick,
                ),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCustom) {
                // the mock's round plus: the way in to the photo picker, still there over a picked picture to swap it
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(size * 0.3f)
                        .clip(CircleShape)
                        .background(if (image != null) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = stringResource(R.string.song_edit_cover_pick),
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.12f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(size),
        )
    }
}
