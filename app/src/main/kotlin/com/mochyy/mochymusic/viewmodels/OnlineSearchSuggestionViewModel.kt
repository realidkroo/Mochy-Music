// OnlineSearchSuggestionViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.utils.YouTubeUrlParser
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else if (com.mochyy.mochymusic.utils.OfflineMode.isOn) {
                        // offline: the library is all there is to search, shown where YouTube's
                        // top results would be
                        combine(
                            database.searchHistory(query).map { it.take(3) },
                            database.searchSongs(query, OfflinePreviewSize),
                            database.searchAlbums(query, OfflinePreviewSize),
                            database.searchArtists(query, OfflinePreviewSize),
                            database.searchPlaylists(query, OfflinePreviewSize),
                        ) { history, songs, albums, artists, playlists ->
                            SearchSuggestionViewState(
                                history = history,
                                items = com.mochyy.mochymusic.ui.screens.library.searchLibrary(
                                    query, songs, artists, albums, playlists,
                                ),
                            )
                        }
                    } else {
                        val parsedUrl = YouTubeUrlParser.parse(query)
                        val parsedItem = if (parsedUrl != null) fetchParsedUrlItem(parsedUrl) else null

                        val result = if (parsedUrl != null) null else YouTube.searchSuggestions(query).getOrNull()
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

                        database
                            .searchHistory(query)
                            .map { it.take(3) }
                            .map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions =
                                    result
                                        ?.queries
                                        ?.filter { suggestionQuery ->
                                            history.none { it.query == suggestionQuery }
                                        }
                                        // a suggestion repeated twice gave two rows the same key and closed the app
                                        ?.distinct()
                                        .orEmpty(),
                                    items = listOfNotNull(parsedItem) +
                                    result
                                        ?.recommendedItems
                                        ?.distinctBy { it.id }
                                        ?.filter { it.id != parsedItem?.id }
                                        ?.filterExplicit(hideExplicit)
                                        ?.filterVideoSongs(hideVideoSongs)
                                        .orEmpty(),
                                    isFromLink = parsedUrl != null
                                )
                            }
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }

    private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? {
        println("[LINK_PARSE_DEBUG] Fetching metadata for: $parsedUrl")
        return try {
            val item = when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video -> {
                    YouTube.queue(listOf(parsedUrl.id)).getOrNull()?.firstOrNull()
                }

                is YouTubeUrlParser.ParsedUrl.Artist -> {
                    YouTube.artist(parsedUrl.id).getOrNull()?.artist
                }
            }
            println("[LINK_PARSE_DEBUG] Fetch successful: ${item?.id} (${item?.javaClass?.simpleName})")
            item
        } catch (e: Exception) {
            println("[LINK_PARSE_DEBUG] Fetch failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val isFromLink: Boolean = false,
)

/** how many of each kind offline search shows */
private const val OfflinePreviewSize = 8
