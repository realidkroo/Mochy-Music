// TvHomeScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.ui.screens.recap.RecapAvailability
import com.mochyy.mochymusic.ui.screens.recap.RecapRepository
import com.mochyy.mochymusic.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

// home from the concept: a big picture across the top with the recap's call, then rows of music under it
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeScreen(profile: TvProfile, viewModel: HomeViewModel = hiltViewModel()) {
    val actions = LocalTvActions.current
    val database = LocalDatabase.current
    val homePage by viewModel.homePage.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val recap by produceState<RecapAvailability.Ready?>(null, database) {
        value = runCatching { RecapRepository.recapFor(database, LocalDate.now()) as? RecapAvailability.Ready }.getOrNull()
    }
    val regulars = keepListening?.takeIf { it.isNotEmpty() } ?: quickPicks.orEmpty()
    val sections = homePage?.sections.orEmpty()

    // the backdrop follows the focused card after a short pause, so racing along a row doesn't load every picture
    var focusedArt by remember { mutableStateOf<String?>(null) }
    var pendingArt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingArt) {
        delay(350)
        if (pendingArt != null) focusedArt = pendingArt
    }
    val recapArt = recap?.stats?.topArtists?.firstOrNull()?.thumbnailUrl
    val backdrop = focusedArt ?: recapArt ?: regulars.firstOrNull()?.thumbnailUrl ?: sections.firstOrNull()?.items?.firstOrNull()?.thumbnail

    val listState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // the call to action takes the remote first, like the concept's "View"
        delay(50)
        runCatching { heroFocus.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.68f
        TvHomeBackdrop(
            url = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight + 60.dp)
                // sinks away as the rows scroll up, read while drawing so scrolling doesn't recompose it
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex > 0) 1f else listState.firstVisibleItemScrollOffset / size.height.coerceAtLeast(1f)
                    alpha = (1f - scrolled * 1.4f).coerceIn(0f, 1f)
                    translationY = -scrolled * size.height * 0.3f
                },
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "hero", contentType = "hero") {
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        Column(Modifier.align(Alignment.BottomStart).padding(start = TvSpace.Start, bottom = 30.dp)) {
                            if (recap != null) {
                                Text(stringResource(R.string.tv_recap_headline), style = TvText.Hero)
                                Spacer(Modifier.height(12.dp))
                                TvPillButton(
                                    stringResource(R.string.tv_view),
                                    onClick = { actions.navController.navigate(TvRoutes.Recap) },
                                    icon = R.drawable.arrow_forward,
                                    modifier = Modifier.focusRequester(heroFocus),
                                )
                            } else {
                                Text(stringResource(R.string.tv_hello_again, profile.name), style = TvText.Hero)
                                Spacer(Modifier.height(12.dp))
                                TvPillButton(
                                    stringResource(R.string.tv_play_something),
                                    onClick = { regulars.firstOrNull()?.let(actions::open) ?: sections.firstOrNull()?.items?.firstOrNull()?.let(actions::open) },
                                    icon = R.drawable.play,
                                    modifier = Modifier.focusRequester(heroFocus),
                                )
                            }
                        }
                    }
                }
                if (regulars.isNotEmpty()) {
                    item(key = "regulars", contentType = "row") {
                        TvLocalShelf(stringResource(R.string.tv_regular_music), regulars, onFocused = { pendingArt = it.thumbnailUrl })
                    }
                }
                itemsIndexed(sections, key = { index, section -> "section_$index${section.title}" }, contentType = { _, _ -> "row" }) { _, section ->
                    TvShelf(
                        title = section.title,
                        items = section.items,
                        modifier = Modifier.padding(top = 22.dp),
                        onFocused = { pendingArt = it.thumbnail },
                    )
                }
            }
        }
    }
}

// the big picture behind home's header: anchored right, melting into the black on the left and at the bottom
@Composable
internal fun TvHomeBackdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        Crossfade(targetState = url, animationSpec = tween(600), label = "tvBackdrop", modifier = Modifier.fillMaxSize()) { shown ->
            Box(Modifier.fillMaxSize()) {
                TvBackdropImage(shown, Modifier.fillMaxHeight().fillMaxWidth(0.82f).align(Alignment.TopEnd), alignment = Alignment.TopCenter)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val left = Brush.horizontalGradient(0f to Color.Black, 0.22f to Color.Black.copy(alpha = 0.85f), 0.55f to Color.Transparent)
                    val bottom = Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black)
                    val top = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.2f to Color.Transparent)
                    onDrawBehind {
                        drawRect(left)
                        drawRect(bottom)
                        drawRect(top)
                    }
                },
        )
    }
}

// a row of library things (songs, albums, artists, playlists)
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvLocalShelf(title: String, items: List<LocalItem>, onFocused: ((LocalItem) -> Unit)? = null) {
    val actions = LocalTvActions.current
    Column {
        TvShelfTitle(title)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowPivot) {
            LazyRow(
                contentPadding = PaddingValues(start = TvSpace.Start, end = TvSpace.End),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                    TvItemCard(
                        title = item.title,
                        subtitle = item.tvSubtitle(),
                        thumbnailUrl = item.thumbnailUrl,
                        round = item is Artist,
                        onFocused = onFocused?.let { { it(item) } },
                        onLongClick = (item as? Song)?.let { song -> { actions.openMenu(song.tvRef()) } },
                        onClick = { actions.open(item) },
                    )
                }
            }
        }
    }
}
