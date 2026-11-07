// TvSearchScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.pages.SearchSummary
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.viewmodels.MoodAndGenresViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
internal class TvSearchViewModel @Inject constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val history: StateFlow<List<String>> = database.searchHistory()
        .map { rows: List<SearchHistory> -> rows.map { it.query }.distinct().take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = MutableStateFlow<List<SearchSummary>?>(null)
    val results: StateFlow<List<SearchSummary>?> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val initialQuery: String = savedStateHandle.get<String>("q")?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()

    init {
        if (initialQuery.isNotBlank()) search(initialQuery)
    }

    fun search(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        _searching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = text)) }
            _results.value = YouTube.searchSummary(text).getOrNull()?.summaries.orEmpty()
            _searching.value = false
        }
    }

    fun clear() {
        _results.value = null
    }
}

// search from the concept: the field and past searches on the left, moods and genres (or the results) on the right
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSearchScreen(
    viewModel: TvSearchViewModel = hiltViewModel(),
    moods: MoodAndGenresViewModel = hiltViewModel(),
) {
    val actions = LocalTvActions.current
    val history by viewModel.history.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val genres by moods.moodAndGenres.collectAsState()
    var query by remember { mutableStateOf(viewModel.initialQuery) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(start = TvSpace.Start, end = TvSpace.End)) {
        Column(Modifier.width(380.dp).fillMaxHeight().padding(top = TvSpace.Top)) {
            Text(stringResource(R.string.search), style = TvText.PageTitle)
            Spacer(Modifier.height(14.dp))
            TvSearchField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) viewModel.clear()
                },
                onSearch = { viewModel.search(query) },
                modifier = Modifier.focusRequester(fieldFocus),
            )
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.history), style = TvText.Body)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(history, key = { it }) { past ->
                    Text(
                        past,
                        style = TvText.Item.copy(color = Color(0xFFCFCFCF)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvClickable(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f) {
                                query = past
                                viewModel.search(past)
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val shown = results
            CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
                when {
                    searching -> Text(stringResource(R.string.tv_loading), style = TvText.Body, modifier = Modifier.padding(top = TvSpace.Top + 50.dp))
                    shown != null -> TvSearchResults(shown)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = TvSpace.Top + 50.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val all = genres.orEmpty().flatMap { it.items }
                        items(all, key = { "${it.endpoint.browseId}|${it.endpoint.params}|${it.title}" }) { genre ->
                            TvGenreCard(genre.title, Color(genre.stripeColor)) {
                                val params = genre.endpoint.params?.let { "?params=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                                actions.navController.navigate("${TvRoutes.Browse}/${genre.endpoint.browseId}$params")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TvText.Item.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (focused) Color(0xFF3A3A3A) else Color(0xFF2E2E2E))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(stringResource(R.string.tv_search_hint), style = TvText.Item.copy(color = TvColors.Muted))
                inner()
            }
        },
    )
}

@Composable
private fun TvGenreCard(title: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = 0.55f).compositeOverDark())
            .tvClickable(shape = RoundedCornerShape(22.dp), focusedScale = 1.04f, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, style = TvText.Item, modifier = Modifier.align(Alignment.BottomStart))
    }
}

private fun Color.compositeOverDark(): Color = androidx.compose.ui.graphics.lerp(Color(0xFF6F6F6F), this, 0.55f)

@Composable
private fun TvSearchResults(results: List<SearchSummary>) {
    val actions = LocalTvActions.current
    LazyColumn(contentPadding = PaddingValues(top = TvSpace.Top + 50.dp, bottom = 48.dp)) {
        if (results.isEmpty()) {
            item { Text(stringResource(R.string.tv_nothing_found), style = TvText.Body) }
        }
        results.forEachIndexed { index, summary ->
            val songs = summary.items.filterIsInstance<SongItem>()
            item(key = "title_$index") { Text(summary.title, style = TvText.Section, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
            if (songs.size == summary.items.size) {
                items(songs, key = { "song_${index}_${it.id}" }) { song ->
                    TvSongRow(
                        title = song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.thumbnail,
                        isCurrent = false,
                        onMenu = { actions.openMenu(song.tvRef()) },
                        onClick = { actions.open(song) },
                    )
                }
            } else {
                item(key = "row_$index") {
                    TvShelf(title = "", items = summary.items, contentPadding = PaddingValues(end = 8.dp))
                }
            }
        }
    }
}
