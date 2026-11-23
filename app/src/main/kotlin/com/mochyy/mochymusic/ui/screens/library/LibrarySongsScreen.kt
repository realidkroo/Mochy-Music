// LibrarySongsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackBones
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedGroupHeader
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedNoResults
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedPlaylistPage
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// a letter heading, or a song with its line already written ("artist • 3:41")
private sealed interface SongRow {
    val key: String

    @Immutable
    class Heading(override val key: String, val text: String) : SongRow

    @Immutable
    class Track(val song: Song, val subtitle: String) : SongRow {
        override val key = "s_${song.id}"
    }
}

// the rows a to z under their letters, the songs in that same order for play and shuffle, and the row each letter on the rail starts at
private class SongLayout(
    val rows: List<SongRow>,
    val ordered: List<Song>,
    val sections: List<IndexSection>,
    val firstRowOf: Map<String, Int>,
)

private fun songLayout(songs: List<Song>, query: String): SongLayout {
    val q = query.trim()
    val visible = if (q.isEmpty()) {
        songs
    } else {
        songs.filter { song -> song.song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) } }
    }
    val rows = ArrayList<SongRow>(visible.size + 30)
    val ordered = ArrayList<Song>(visible.size)
    val sections = ArrayList<IndexSection>()
    val firstRowOf = HashMap<String, Int>()
    indexSections(visible) { it.song.title }.forEach { (section, entries) ->
        sections += section
        firstRowOf[section.key] = rows.size
        rows += SongRow.Heading("h_${section.key}", section.label)
        entries.forEach { song ->
            val subtitle = listOfNotNull(
                song.artists.joinToString { it.name }.ifBlank { null },
                song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
            ).joinToString(" • ")
            rows += SongRow.Track(song, subtitle)
            ordered += song
        }
    }
    return SongLayout(rows, ordered, sections, firstRowOf)
}

// every song in the library, a to z, laid out like Recently added
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val title = stringResource(R.string.lib_all_songs_title)
    val nowPlaying = playerConnection.mediaMetadata.collectAsState()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsState()
    val currentQueue by playerConnection.queueTitle.collectAsState()

    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val glide = rememberIndexGlide(listState, landing = indexLandingUnderBar())
    // sorting thousands of titles waits off the main thread, so the page opens straight away
    val layout by produceState<SongLayout?>(null, songs, query.text) {
        value = withContext(Dispatchers.Default) { songLayout(songs, query.text) }
    }
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0) } }
    val isThisQueue = currentQueue == title

    fun play(ordered: List<Song>, start: Song?, shuffle: Boolean) {
        if (ordered.isEmpty()) return
        val queue = if (shuffle) ordered.shuffled() else ordered
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = queue.map { it.toMediaItem() },
                startIndex = start?.let { queue.indexOf(it).coerceAtLeast(0) } ?: 0,
            ),
        )
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = songs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = stringResource(
            R.string.lib_stats_in_library_time,
            pluralStringResource(R.plurals.n_song, songs.size, songs.size),
            listeningTime(totalSeconds),
        ),
        lazyListState = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying.value,
        onPlayClick = {
            if (isThisQueue) playerConnection.togglePlayPause() else play(layout?.ordered ?: songs, null, shuffle = false)
        },
        onShuffleClick = { play(layout?.ordered ?: songs, null, shuffle = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        overlay = {
            val shown = layout
            if (shown != null && shown.sections.size > 1 && query.text.isBlank()) {
                LibraryIndexRail(
                    sections = shown.sections,
                    // +1 for the header above the rows
                    onSectionSelected = { key -> shown.firstRowOf[key]?.let { row -> glide.glideTo(row + 1) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        val shown = layout
        if (shown == null) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@GeneratedPlaylistPage
        }
        if (shown.rows.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (query.text.isNotBlank()) GeneratedNoResults(query.text) else LibraryEmptyState()
            }
        }
        items(shown.rows, key = { it.key }, contentType = { if (it is SongRow.Heading) "heading" else "track" }) { row ->
            when (row) {
                is SongRow.Heading -> GeneratedGroupHeader(row.text)
                // left queues the song, right takes it out of the library (it's already in it)
                is SongRow.Track -> SongSwipeBox(
                    library = { librarySwipeAction(row.song.song) },
                    queue = { queueSwipeAction { row.song.toMediaItem() } },
                ) {
                    val active = row.song.id == nowPlaying.value?.id
                    PlaylistTrackRow(
                        thumbnailUrl = row.song.song.thumbnailUrl,
                        songId = row.song.id,
                        title = row.song.song.title,
                        subtitle = row.subtitle,
                        isActive = active,
                        isPlaying = active && isPlaying.value,
                        onClick = {
                            if (active) playerConnection.togglePlayPause() else play(shown.ordered, row.song, shuffle = false)
                        },
                        onLongClick = {
                            menuState.show { SongMenu(originalSong = row.song, navController = navController, onDismiss = menuState::dismiss) }
                        },
                        onMenuClick = {
                            menuState.show { SongMenu(originalSong = row.song, navController = navController, onDismiss = menuState::dismiss) }
                        },
                    )
                }
            }
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(50.dp)) }
    }
}
