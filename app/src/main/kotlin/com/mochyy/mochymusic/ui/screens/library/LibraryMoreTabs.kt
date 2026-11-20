// LibraryMoreTabs.kt
//
// The Library's History and Genres tabs: the last few days of what you played, and the genres of
// everything you keep or play, each with a way into its full page.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.EventWithSong
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.screens.historyDayHeading
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.ui.screens.search.SearchLoadingDots
import com.mochyy.mochymusic.viewmodels.LocalGenre
import com.mochyy.mochymusic.viewmodels.localGenreRoute
import java.time.LocalDate

private const val HistoryDays = 3
private const val HistoryPerDay = 8

/** the last few days of plays, a handful each, under a card that opens all of History */
fun LazyListScope.libraryHistoryTab(
    events: Map<LocalDate, List<EventWithSong>>?,
    nowPlaying: State<MediaMetadata?>?,
    isPlaying: State<Boolean>?,
    player: PlayerConnection?,
    navController: NavController,
) {
    if (events == null) {
        item(key = "history_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (events.isEmpty()) {
        item(key = "history_empty") { LibraryEmptyState(text = stringResource(R.string.lib_history_empty)) }
        return
    }
    val days = events.entries.take(HistoryDays).map { (day, plays) -> day to plays.take(HistoryPerDay) }
    // a tap plays what this tab shows, from that song on, like History does with all of it
    val shown = days.flatMap { (_, plays) -> plays.map { it.song } }
    item(key = "history_all") {
        LibraryWideCard(
            title = stringResource(R.string.lib_history_all),
            count = null,
            covers = shown.take(4).map { it.song.thumbnailUrl },
            onClick = { navController.navigate("history") },
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
        Spacer(Modifier.height(8.dp))
    }
    days.forEach { (day, plays) ->
        item(key = "history_day_$day", contentType = "historyDay") {
            Spacer(Modifier.height(14.dp))
            LibrarySectionTitle(historyDayHeading(day))
            Spacer(Modifier.height(6.dp))
        }
        items(plays, key = { "history_${it.event.id}" }, contentType = { "historyRow" }) { play ->
            val song = play.song
            val active = song.id == nowPlaying?.value?.id
            LibraryListRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = active,
                isPlaying = active && isPlaying?.value == true,
                songId = song.id,
                onClick = {
                    if (active) {
                        player?.togglePlayPause()
                    } else {
                        player?.playQueue(
                            ListQueue(items = shown.map { it.toMediaItem() }, startIndex = shown.indexOf(song).coerceAtLeast(0)),
                        )
                    }
                },
                onLongClick = { navController.navigate("history") },
            )
        }
    }
}

/** the genres of your songs (tags first, the artist's genre otherwise), one row each like a playlist, each opening its own page */
fun LazyListScope.libraryGenresTab(genres: List<LocalGenre>?, navController: NavController) {
    if (genres == null) {
        item(key = "genres_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (genres.isEmpty()) {
        item(key = "genres_empty") { LibraryEmptyState(text = stringResource(R.string.lib_genres_local_empty)) }
        return
    }
    item(key = "genres_top") { Spacer(Modifier.height(4.dp)) }
    items(genres, key = { "genre_${it.name.lowercase()}" }, contentType = { "genreRow" }) { genre ->
        val open = { navController.navigate(localGenreRoute(genre.name)) }
        LibraryListRow(
            title = genre.name,
            subtitle = pluralStringResource(R.plurals.n_song, genre.songs.size, genre.songs.size),
            thumbnailUrl = genre.songs.firstNotNullOfOrNull { it.song.thumbnailUrl?.takeIf(String::isNotEmpty) },
            onClick = open,
            onLongClick = open,
        )
    }
}
