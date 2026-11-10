// TvHomeScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.HeroCarouselItem
import com.mochyy.mochymusic.ui.component.StarterSongItem
import com.mochyy.mochymusic.ui.component.extraHeroPicks
import com.mochyy.mochymusic.ui.component.heroCarouselItems
import com.mochyy.mochymusic.ui.screens.recap.RecapAvailability
import com.mochyy.mochymusic.ui.screens.recap.RecapRepository
import com.mochyy.mochymusic.viewmodels.HomeCategory
import com.mochyy.mochymusic.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

// one card in home's header: the month's recap, or one of the phone's own hero picks
private sealed interface TvHeroCard {
    val key: String
    val art: String?

    data class Recap(override val art: String?) : TvHeroCard {
        override val key = "recap"
    }

    data class Pick(val item: HeroCarouselItem) : TvHeroCard {
        override val key = "pick_${item.mediaId ?: item.mainText}"
        override val art = item.thumbnailUrl
    }
}

// a row on home, in the order the phone deals them out
@Immutable
private sealed interface TvHomeRow {
    val key: String
    val title: String

    data class Online(override val key: String, override val title: String, val items: List<YTItem>) : TvHomeRow
    data class Library(override val key: String, override val title: String, val items: List<LocalItem>) : TvHomeRow
}

// home from the concept, run by the phone's own picks: the hero cycles through what the phone's hero would show, the rows follow its order
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeScreen(profile: TvProfile, viewModel: HomeViewModel = hiltViewModel()) {
    val actions = LocalTvActions.current
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current
    // the view model learns what's played and skipped from the player, like on the phone
    LaunchedEffect(player) { player?.let(viewModel::attachPlayer) }

    val playing by remember(player) { player?.mediaMetadata ?: MutableStateFlow<MediaMetadata?>(null) }.collectAsState()
    val isPlaying by remember(player) { player?.isPlaying ?: MutableStateFlow(false) }.collectAsState()
    val lastPlayed by viewModel.lastPlayedSong.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val similar by viewModel.similarRecommendations.collectAsState()
    val onRepeat by viewModel.mostPlayedSongsForHome.collectAsState()
    val forgotten by viewModel.forgottenFavorites.collectAsState()
    val starterSongs by viewModel.starterSongs.collectAsState()
    val recap by produceState<RecapAvailability.Ready?>(null, database) {
        value = runCatching { RecapRepository.recapFor(database, LocalDate.now()) as? RecapAvailability.Ready }.getOrNull()
    }

    val heroCards = remember(playing, lastPlayed, keepListening, dailyDiscover, similar, onRepeat, forgotten, starterSongs, recap) {
        val picks = heroCarouselItems(
            lastPlayedSong = playing ?: lastPlayed,
            dailyDiscover = dailyDiscover,
            extras = extraHeroPicks(similar, onRepeat, forgotten),
            playlistPicks = emptyList(),
            keepListening = keepListening,
            starterItems = starterSongs.orEmpty().map { StarterSongItem(it.song, it.artistName, it.artistThumbnail) },
            showGacha = false,
        ).take(MaxHeroCards).map { TvHeroCard.Pick(it) }
        listOfNotNull(recap?.let { TvHeroCard.Recap(it.stats.topArtists.firstOrNull()?.thumbnailUrl) }) + picks
    }
    val rows = rememberHomeRows(viewModel, keepListening)

    var heroIndex by remember { mutableIntStateOf(0) }
    var heroTouchedAt by remember { mutableStateOf(0L) }
    val hero = heroCards.getOrNull(heroIndex % heroCards.size.coerceAtLeast(1))
    // the hero turns on its own, a little slower than the phone's since it's read from across the room
    LaunchedEffect(heroCards.size, heroTouchedAt) {
        if (heroCards.size < 2) return@LaunchedEffect
        while (true) {
            delay(HeroTurnMs)
            heroIndex = (heroIndex + 1) % heroCards.size
        }
    }

    // a focused card's picture takes the backdrop after a short pause, so racing along a row doesn't load every one
    var focusedArt by remember { mutableStateOf<String?>(null) }
    var pendingArt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingArt) {
        delay(350)
        focusedArt = pendingArt
    }
    val backdrop = focusedArt ?: hero?.art ?: rows.firstNotNullOfOrNull { row ->
        when (row) {
            is TvHomeRow.Online -> row.items.firstOrNull()?.thumbnail
            is TvHomeRow.Library -> row.items.firstOrNull()?.thumbnailUrl
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    val seen = remember { HashSet<Any>() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { heroFocus.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.66f
        TvHomeBackdrop(
            url = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight + 70.dp)
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
                        TvHeroText(
                            card = hero,
                            profileName = profile.name,
                            isPlaying = isPlaying && hero is TvHeroCard.Pick && hero.item.mediaId != null && hero.item.mediaId == playing?.id,
                            focus = heroFocus,
                            onFocused = {
                                pendingArt = null
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            onTurn = { step ->
                                if (heroCards.isNotEmpty()) {
                                    heroIndex = (heroIndex + step + heroCards.size) % heroCards.size
                                    heroTouchedAt = System.currentTimeMillis()
                                }
                            },
                            onPlay = {
                                when (val card = hero) {
                                    is TvHeroCard.Recap -> actions.navController.navigate(TvRoutes.Recap)
                                    is TvHeroCard.Pick -> player?.let { card.item.onPlay(it, actions.navController) }
                                    null -> Unit
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = TvSpace.Start, bottom = 26.dp),
                        )
                        if (heroCards.size > 1) {
                            TvHeroDots(count = heroCards.size, current = heroIndex % heroCards.size, modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSpace.End, bottom = 30.dp))
                        }
                    }
                }
                items(rows, key = { it.key }, contentType = { it::class.simpleName }) { row ->
                    Box(Modifier.padding(top = 14.dp).tvReveal(row.key, seen)) {
                        when (row) {
                            is TvHomeRow.Online -> TvShelf(title = row.title, items = row.items, onFocused = { pendingArt = it.thumbnail })
                            is TvHomeRow.Library -> TvLocalShelf(row.title, row.items, onFocused = { pendingArt = it.thumbnailUrl })
                        }
                    }
                }
            }
        }
    }
}

// the words over the hero: the little "because you like" line, the big title, and play; left and right turn the cards
@Composable
private fun TvHeroText(
    card: TvHeroCard?,
    profileName: String,
    isPlaying: Boolean,
    focus: FocusRequester,
    onFocused: () -> Unit,
    onTurn: (Int) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 460.dp)) {
        AnimatedContent(
            targetState = card,
            contentKey = { it?.key },
            transitionSpec = {
                (fadeIn(tween(420, easing = TvEase)) + slideInVertically(tween(420, easing = TvEase)) { it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 8 })
            },
            label = "tvHeroText",
        ) { shown ->
            Column {
                when (shown) {
                    is TvHeroCard.Recap -> Text(stringResource(R.string.tv_recap_headline), style = TvText.Hero)
                    is TvHeroCard.Pick -> {
                        val label = shown.item.label(isPlaying)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label.text, style = TvText.Sub.copy(color = Color(0xFFD0D0D0), fontSize = 12.sp))
                            label.subject?.let { subject ->
                                Spacer(Modifier.width(6.dp))
                                label.coverUrl?.let {
                                    TvCover(it, 16.dp, Modifier.size(16.dp), shape = if (label.roundCover) CircleShape else RoundedCornerShape(4.dp))
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(subject, style = TvText.Sub.copy(color = Color.White, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(shown.item.mainText, style = TvText.Hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(shown.item.subText, style = TvText.Body.copy(color = Color(0xFFCFCFCF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    null -> Text(stringResource(R.string.tv_hello_again, profileName), style = TvText.Hero)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TvPillButton(
            text = stringResource(
                when {
                    card is TvHeroCard.Recap -> R.string.tv_view
                    card is TvHeroCard.Pick && card.item.opensPage -> R.string.tv_view
                    else -> R.string.play
                },
            ),
            icon = if (card is TvHeroCard.Pick && !card.item.opensPage) R.drawable.play else R.drawable.arrow_forward,
            bright = true,
            onClick = onPlay,
            modifier = Modifier
                .focusRequester(focus)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onTurn(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            onTurn(1)
                            true
                        }
                        else -> false
                    }
                },
        )
    }
}

// little dots under the hero, the lit one stretched, so it's clear there are more cards a press away
@Composable
private fun TvHeroDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val lit = index == current
            Box(
                Modifier
                    .height(5.dp)
                    .width(if (lit) 16.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (lit) Color.White else Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

// home's rows in the phone's order: history first, the recommendation rows dealt in between, youtube's shelves last
@Composable
private fun rememberHomeRows(viewModel: HomeViewModel, keepListening: List<LocalItem>?): List<TvHomeRow> {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongsForHome.collectAsState()
    val categories by viewModel.homeCategories.collectAsState()
    val community by viewModel.communityPlaylists.collectAsState()
    val speedDial by viewModel.speedDialItems.collectAsState()
    val history by viewModel.recentHistorySongs.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val forgotten by viewModel.forgottenFavorites.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val allTimeHits by viewModel.allTimeHits.collectAsState()

    val regular = stringResource(R.string.tv_regular_music)
    val recentTitle = stringResource(R.string.recently_played)
    val mostTitle = stringResource(R.string.home_most_played)
    val communityTitle = stringResource(R.string.from_the_community)
    val speedTitle = stringResource(R.string.speed_dial)
    val historyTitle = stringResource(R.string.home_history)
    val playlistsTitle = stringResource(R.string.home_your_playlists)
    val forgottenTitle = stringResource(R.string.dont_forget_these_songs)
    val hitsTitle = stringResource(R.string.all_time_hits)
    val labels = mapOf(
        HomeCategory.Kind.BecauseYouLike to stringResource(R.string.home_because_you_like),
        HomeCategory.Kind.MoreLikeArtist to stringResource(R.string.home_more_like),
        HomeCategory.Kind.YourGenre to stringResource(R.string.home_because_you_listen_to),
        HomeCategory.Kind.NewGenre to stringResource(R.string.home_try_something_new),
    )
    val newFromArtists = stringResource(R.string.home_new_from_your_artists)

    return remember(keepListening, recentlyPlayed, mostPlayed, categories, community, speedDial, history, accountPlaylists, localPlaylists, forgotten, homePage, allTimeHits) {
        buildList {
            val pending = categories.orEmpty().toMutableList()
            fun addCategories(count: Int = 1) = repeat(count) {
                val category = pending.removeFirstOrNull() ?: return@repeat
                val title = when (category.kind) {
                    HomeCategory.Kind.NewFromArtists -> newFromArtists
                    else -> labels[category.kind]?.let { "$it · ${category.title}" } ?: category.title
                }
                if (category.items.isNotEmpty()) add(TvHomeRow.Online("category_${category.id}", title, category.items))
            }
            fun addLocal(key: String, title: String, items: List<LocalItem>?) {
                if (!items.isNullOrEmpty()) add(TvHomeRow.Library(key, title, items))
            }
            fun addOnline(key: String, title: String, items: List<YTItem>?) {
                if (!items.isNullOrEmpty()) add(TvHomeRow.Online(key, title, items))
            }

            addLocal("regulars", regular, keepListening)
            addLocal("recently_played", recentTitle, recentlyPlayed)
            addLocal("most_played", mostTitle, mostPlayed)
            addCategories()
            addOnline("community", communityTitle, community?.map { it.playlist })
            addOnline("speed_dial", speedTitle, speedDial)
            addLocal("history", historyTitle, history)
            addCategories(2)
            addLocal("local_playlists", playlistsTitle, localPlaylists?.filter { it.songCount > 0 })
            addOnline("account_playlists", playlistsTitle, accountPlaylists)
            addCategories()
            addLocal("forgotten", forgottenTitle, forgotten)
            addCategories(pending.size)
            val sections = homePage?.sections.orEmpty()
            sections.firstOrNull()?.let { addOnline("home_0_${it.title}", it.title, it.items) }
            addOnline("all_time_hits", hitsTitle, allTimeHits)
            sections.drop(1).forEachIndexed { index, section -> addOnline("home_${index + 1}_${section.title}", section.title, section.items) }
        }.distinctBy { it.key }
    }
}

// the big picture behind home's and the artist page's header: anchored right, its own left and bottom edges melting away so no line ever shows where it starts
@Composable
internal fun TvHomeBackdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        Crossfade(targetState = url, animationSpec = tween(700, easing = TvEase), label = "tvBackdrop", modifier = Modifier.fillMaxSize()) { shown ->
            Box(Modifier.fillMaxSize()) {
                TvBackdropImage(
                    shown,
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.82f)
                        .align(Alignment.TopEnd)
                        // its own layer, so the masks below cut the picture alone; it never changes once loaded
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.Black), blendMode = BlendMode.DstIn)
                            drawRect(Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        },
                    alignment = Alignment.TopCenter,
                )
            }
        }
        // a little shade where the words sit, and under the chrome
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val left = Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.45f to Color.Transparent)
                    val top = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 0.2f to Color.Transparent)
                    onDrawBehind {
                        drawRect(left)
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
                contentPadding = PaddingValues(start = TvSpace.Start, end = TvSpace.End, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                    TvItemCard(
                        title = item.title,
                        subtitle = item.tvSubtitle(),
                        thumbnailUrl = item.thumbnailUrl,
                        round = item is Artist,
                        transformKey = tvTransformKey(item),
                        onFocused = onFocused?.let { { it(item) } },
                        onLongClick = (item as? Song)?.let { song -> { actions.openMenu(song.tvRef()) } },
                        onClick = { actions.open(item) },
                    )
                }
            }
        }
    }
}

// the hero shows this many picks at most, more than that and nobody waits for the last one
private const val MaxHeroCards = 8
private const val HeroTurnMs = 9_000L
