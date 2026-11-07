// TvLibraryScreen.kt

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.PhoneFiles
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel

private enum class TvLibraryTab(val label: Int) { All(R.string.tv_tab_all), Cached(R.string.tv_cached), Local(R.string.tv_local_and_downloaded), Imported(R.string.tv_imported) }

// one card in the library's grid: what it is, how many, a couple of its covers peeking out at the corner
@Immutable
private data class TvShelfCard(
    val key: String,
    val title: String,
    val count: String,
    val icon: Int,
    val covers: List<String?>,
    val route: String,
    val wide: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvLibraryScreen(viewModel: LibraryHomeViewModel = hiltViewModel()) {
    val actions = LocalTvActions.current
    var tab by rememberSaveable { mutableStateOf(TvLibraryTab.All) }
    val liked by viewModel.likedSongs.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val imported by viewModel.importedSources.collectAsState()
    val onDevice by viewModel.local.songs.collectAsState(initial = emptyList())
    val cached by viewModel.cached.songs.collectAsState(initial = emptyList())
    val phoneFiles by PhoneFiles.files.collectAsState()

    val songCount: @Composable (Int) -> String = { pluralStringResource(R.plurals.tv_song_count, it, it) }
    val cards = when (tab) {
        TvLibraryTab.All -> listOf(
            TvShelfCard("liked", stringResource(R.string.liked_songs), songCount(liked.size), R.drawable.favorite, liked.take(2).map { it.song.thumbnailUrl }, "${TvRoutes.Songs}/liked"),
            TvShelfCard("songs", stringResource(R.string.songs), songCount(songs.size), R.drawable.music_note, songs.take(2).map { it.song.thumbnailUrl }, "${TvRoutes.Songs}/library"),
            TvShelfCard("albums", stringResource(R.string.albums), albums.size.toString(), R.drawable.album, albums.take(2).map { it.thumbnailUrl }, "${TvRoutes.Grid}/albums"),
            TvShelfCard("artists", stringResource(R.string.artists), artists.size.toString(), R.drawable.artist, artists.take(2).map { it.thumbnailUrl }, "${TvRoutes.Grid}/artists"),
            TvShelfCard("playlists", stringResource(R.string.playlists), playlists.size.toString(), R.drawable.queue_music, playlists.take(2).map { it.thumbnails.firstOrNull() }, "${TvRoutes.Grid}/playlists", wide = true),
        )
        TvLibraryTab.Cached -> listOf(
            TvShelfCard("cached", stringResource(R.string.tv_cached), songCount(cached.size), R.drawable.cached, cached.take(2).map { it.song.thumbnailUrl }, "${TvRoutes.Songs}/cached", wide = true),
        )
        TvLibraryTab.Local -> buildList {
            val downloaded = onDevice.filter { !it.song.isLocal }
            val files = onDevice.filter { it.song.isLocal }
            add(TvShelfCard("downloaded", stringResource(R.string.tv_downloaded_here), songCount(downloaded.size), R.drawable.offline, downloaded.take(2).map { it.song.thumbnailUrl }, "${TvRoutes.Songs}/downloaded"))
            add(TvShelfCard("local", stringResource(R.string.tv_local_files), songCount(files.size), R.drawable.music_note, files.take(2).map { it.song.thumbnailUrl }, "${TvRoutes.Songs}/local"))
            if (phoneFiles.isNotEmpty()) {
                add(TvShelfCard("phone", stringResource(R.string.tv_on_your_phone), songCount(phoneFiles.size), R.drawable.smartphone, emptyList(), "${TvRoutes.Songs}/phone", wide = true))
            }
        }
        TvLibraryTab.Imported -> imported.map { card ->
            TvShelfCard("imported_${card.source.key}", card.source.label, songCount(card.songCount), R.drawable.library_music, card.covers.take(2), "${TvRoutes.Songs}/imported:${card.source.key}")
        }
    }

    Row(Modifier.fillMaxSize().padding(start = TvSpace.Start, end = TvSpace.End)) {
        Column(Modifier.width(250.dp).padding(top = TvSpace.Top)) {
            Text(stringResource(R.string.tv_library), style = TvText.PageTitle)
            Spacer(Modifier.height(18.dp))
            TvLibraryTab.entries.forEach { entry ->
                TvLibraryTabLabel(text = stringResource(entry.label), selected = entry == tab, onPick = { tab = entry })
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = TvSpace.Top + 64.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cards.isEmpty()) {
                    item(span = { GridItemSpan(2) }) { Text(stringResource(R.string.tv_nothing_here), style = TvText.Body) }
                }
                items(cards, key = { it.key }, span = { GridItemSpan(if (it.wide) 2 else 1) }) { card ->
                    TvLibraryCard(card) { actions.navController.navigate(card.route) }
                }
            }
        }
    }
}

// the tabs on the left: grey words, the picked one white on a dark pill with a little bar in front
@Composable
private fun TvLibraryTabLabel(text: String, selected: Boolean, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // moving onto a tab picks it, so the grid on the right changes as the remote goes down the list
    LaunchedEffect(focused) { if (focused) onPick() }
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1C1C1C) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(shape = RoundedCornerShape(12.dp), focusedScale = 1.03f, ring = false, onClick = onPick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.width(2.dp).height(16.dp).background(Color.White))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = TvText.PageTitle.copy(fontSize = 20.sp, color = if (selected || focused) Color.White else Color(0xFF6E6E6E)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvLibraryCard(card: TvShelfCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (card.wide) 132.dp else 100.dp)
            .clip(shape)
            .background(TvColors.Card)
            .tvClickable(shape = shape, focusedScale = 1.03f, onClick = onClick),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(card.icon), contentDescription = null, tint = Color(0xFFD9D9D9), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(card.title, style = TvText.Item.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            card.count,
            style = TvText.PageTitle.copy(fontSize = 22.sp),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp),
        )
        // two covers leaning against the corner, cut off by the card's edge like the concept
        Box(Modifier.align(Alignment.BottomEnd).size(width = 104.dp, height = 62.dp)) {
            card.covers.getOrNull(1)?.let {
                TvCover(it, 64.dp, Modifier.size(64.dp).offset(x = 6.dp, y = 8.dp).rotate(-8f), shape = RoundedCornerShape(6.dp))
            }
            TvCover(card.covers.getOrNull(0), 64.dp, Modifier.size(64.dp).offset(x = 40.dp, y = 6.dp).rotate(-2f), shape = RoundedCornerShape(6.dp))
        }
    }
}
