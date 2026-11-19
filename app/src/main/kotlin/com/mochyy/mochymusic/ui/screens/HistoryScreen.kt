package com.mochyy.mochymusic.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.detail.PlaylistEndOfLine
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.component.rememberRevealSeenState
import com.mochyy.mochymusic.ui.component.revealOnAppear
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedGroupHeader
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedNoResults
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedPlaylistPage
import com.mochyy.mochymusic.ui.screens.playlist.generatedStats
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.viewmodels.HistoryViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// every day of history is headed by its own date in the app's language, "19 November 2026"
@Composable
internal fun historyDayHeading(day: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    return remember(day, format) { day.format(format) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val loadedEvents by historyViewModel.events.collectAsState()
    val eventsMap = loadedEvents.orEmpty()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val title = stringResource(R.string.history)
    var query by remember { mutableStateOf(TextFieldValue()) }
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    // the whole history in the order it's shown, which is what a tap plays from
    val allSongs = remember(eventsMap) { eventsMap.values.flatten().map { it.song } }
    val totalSeconds = remember(allSongs) { allSongs.sumOf { it.song.duration } }
    val groups = remember(eventsMap, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) {
            eventsMap.toList()
        } else {
            eventsMap.mapValues { (_, events) ->
                events.filter { event ->
                    event.song.song.title.contains(q, ignoreCase = true) ||
                        event.song.artists.any { it.name.contains(q, ignoreCase = true) }
                }
            }.filterValues { it.isNotEmpty() }.toList()
        }
    }
    val isThisQueue = queueTitle == title

    fun playFrom(song: Song?, shuffled: Boolean = false) {
        if (allSongs.isEmpty()) return
        val items = if (shuffled) allSongs.shuffled() else allSongs
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = items.map { it.toMediaItem() },
                startIndex = song?.let { items.indexOf(it).coerceAtLeast(0) } ?: 0,
            )
        )
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = allSongs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = generatedStats(allSongs.size, totalSeconds),
        lazyListState = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else playFrom(null) },
        onShuffleClick = { playFrom(null, shuffled = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Find something you played",
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
    ) {
        if (groups.isEmpty() && query.text.isNotBlank()) {
            item(key = "no_results") { GeneratedNoResults(query.text) }
        }

        groups.forEach { (day, events) ->
            item(key = "header_$day", contentType = "group_header") {
                GeneratedGroupHeader(historyDayHeading(day), modifier = Modifier.animateItem())
            }

            items(items = events, key = { it.event.id }, contentType = { "track" }) { eventWithSong ->
                val song = eventWithSong.song
                SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    library = { librarySwipeAction(song.song) },
                    queue = { queueSwipeAction { song.toMediaItem() } },
                ) {
                    val active = song.id == mediaMetadata?.id
                    PlaylistTrackRow(
                        thumbnailUrl = song.song.thumbnailUrl,
                        songId = song.id,
                        title = song.song.title,
                        subtitle = "${song.artists.joinToString { it.name }} • ${makeTimeString(song.song.duration * 1000L)}",
                        isActive = active,
                        isPlaying = active && isPlaying,
                        modifier = Modifier.revealOnAppear(key = "event_${eventWithSong.event.id}", seenState = revealSeen),
                        onClick = {
                            if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause() else playFrom(song)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(originalSong = song, event = eventWithSong.event, navController = navController, onDismiss = menuState::dismiss)
                            }
                        },
                        onMenuClick = {
                            menuState.show {
                                SongMenu(originalSong = song, event = eventWithSong.event, navController = navController, onDismiss = menuState::dismiss)
                            }
                        },
                    )
                }
            }
        }

        if (query.text.isBlank() && allSongs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = allSongs.size, totalSeconds = totalSeconds)
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
