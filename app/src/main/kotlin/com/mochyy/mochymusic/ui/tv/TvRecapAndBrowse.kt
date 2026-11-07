// TvRecapAndBrowse.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.screens.recap.RecapAvailability
import com.mochyy.mochymusic.ui.screens.recap.RecapRepository
import com.mochyy.mochymusic.viewmodels.GenreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

// the month's recap on a tv: the top artist's picture, the time spent listening, and the songs played most
@Composable
internal fun TvRecapScreen() {
    val database = LocalDatabase.current
    val actions = LocalTvActions.current
    val loaded by produceState<Pair<RecapAvailability.Ready, List<Song>>?>(null, database) {
        val ready = runCatching { RecapRepository.recapFor(database, LocalDate.now()) as? RecapAvailability.Ready }.getOrNull() ?: return@produceState
        val songs = withContext(Dispatchers.IO) { ready.stats.topSongs.mapNotNull { database.getSongByIdBlocking(it.id) } }
        value = ready to songs
    }
    val stats = loaded?.first?.stats
    val songs = loaded?.second.orEmpty()
    val tracks = remember(songs) {
        songs.map { TvTrack(it.id, it.song.title, it.artists.joinToString { a -> a.name }, it.song.thumbnailUrl, it.song.duration, it.tvRef()) }
    }
    TvTrackListPage(
        title = stats?.let { stringResource(R.string.tv_recap_title, it.range.label) }.orEmpty(),
        subtitle = stats?.let { stringResource(R.string.tv_recap_stats, (it.totalMs / 60_000L).toInt(), it.plays) }.orEmpty(),
        coverUrl = stats?.topArtists?.firstOrNull()?.thumbnailUrl,
        tracks = tracks,
        loading = loaded == null,
        onPlay = { index, shuffled -> actions.playSongs(songs, index, stats?.range?.label, shuffled) },
    )
}

// a mood or genre from search: its rows of playlists and albums
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvBrowseScreen(viewModel: GenreViewModel = hiltViewModel()) {
    val result by viewModel.result.collectAsState()
    CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = TvSpace.Top, bottom = 48.dp)) {
            item(key = "title") {
                Text(
                    result?.title ?: stringResource(R.string.tv_loading),
                    style = TvText.PageTitle,
                    modifier = Modifier.padding(start = TvSpace.Start, bottom = 18.dp),
                )
            }
            itemsIndexed(result?.items.orEmpty(), key = { index, _ -> "browse_$index" }, contentType = { _, _ -> "row" }) { _, section ->
                TvShelf(title = section.title.orEmpty(), items = section.items, modifier = Modifier.padding(bottom = 22.dp))
            }
        }
    }
}
