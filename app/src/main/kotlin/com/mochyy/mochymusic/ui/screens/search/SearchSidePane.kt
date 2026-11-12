package com.mochyy.mochymusic.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.OfflineModeKey
import com.mochyy.mochymusic.constants.PauseSearchHistoryKey
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.navigateToTab
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.OnlineSearchSuggestionViewModel
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

// the search pages on its side: the field and its history at the left, the categories or the results beside it
fun showsSearchPane(currentRoute: String?): Boolean =
    currentRoute == Screens.Search.route || currentRoute?.startsWith("search/") == true

// the pane's width for a window this wide: a phone's column, never more than a third and a bit of a tablet
fun searchPaneWidth(windowWidth: Dp): Dp = (windowWidth * 0.4f).coerceIn(300.dp, 420.dp)

// the field, then what's been searched before or, while typing, the suggestions and the top matches
@Composable
fun SearchSidePane(
    navController: NavController,
    currentRoute: String?,
    // read in here, so typing recomposes the pane and not the app around it
    query: State<TextFieldValue>,
    onQueryChange: (TextFieldValue) -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val query by query
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val offlineMode by rememberPreference(OfflineModeKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    val suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
    val suggestionState by suggestionViewModel.viewState.collectAsState()
    LaunchedEffect(query.text) { suggestionViewModel.query.value = query.text }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)

    // a search replaces the one beside it instead of stacking, so back goes to the categories
    val commit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            focusManager.clearFocus()
            // offline the library matches under the field are the whole search
            if (!offlineMode) {
                when (val parsed = YouTubeUrlParser.parse(text)) {
                    is YouTubeUrlParser.ParsedUrl.Video ->
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))
                    is YouTubeUrlParser.ParsedUrl.Artist ->
                        navController.navigate("artist/${parsed.id}")
                    null -> navController.navigate("search/${URLEncoder.encode(text, "UTF-8")}") {
                        popUpTo(Screens.Search.route)
                        launchSingleTop = true
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

    // two taps on the Search tab put the cursor in this field, keyboard up
    val searchEntry = remember(currentRoute) {
        runCatching { navController.getBackStackEntry(Screens.Search.route) }.getOrNull()
    }
    val focusAsked = remember(searchEntry) {
        searchEntry?.savedStateHandle?.getStateFlow(SearchFocusKey, false)
    }?.collectAsState()
    LaunchedEffect(focusAsked?.value) {
        if (focusAsked?.value != true) return@LaunchedEffect
        searchEntry?.savedStateHandle?.set(SearchFocusKey, false)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Box(modifier = modifier.width(searchPaneWidth(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)).fillMaxHeight()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SearchColors.page(pureBlack))
                .glassRoot(glassState, isActive = { collapse.value > 0.01f }),
        ) {
            SearchActiveContent(
                listState = listState,
                query = query.text,
                state = suggestionState,
                onFill = { text -> onQueryChange(TextFieldValue(text, TextRange(text.length))) },
                onSubmit = commit,
                onDeleteHistory = { entry ->
                    coroutineScope.launch(Dispatchers.IO) { database.query { delete(entry) } }
                },
                navController = navController,
                topPadding = searchTopBarHeight(withTitle = true),
            )
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = { collapse.value },
            pureBlack = pureBlack,
            title = stringResource(R.string.search),
            trailing = {
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navController.navigateToTab("settings") })
            },
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = commit,
                placeholder = stringResource(R.string.search_tracks_placeholder),
                focused = true,
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = SearchColors.Secondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = RemoteFocus,
                                ) { onQueryChange(TextFieldValue("")) },
                        )
                    }
                } else {
                    null
                },
                focusRequester = focusRequester,
            )
        }

        // a hairline between the pane and the page beside it, like the settings list's
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(0.8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}
