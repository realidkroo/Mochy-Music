// TvArtistScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.viewmodels.ArtistViewModel

// the artist page from the concept: their photo across the page, avatar and name on the left, their top songs on the right
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvArtistScreen(viewModel: ArtistViewModel = hiltViewModel()) {
    val actions = LocalTvActions.current
    val player = LocalPlayerConnection.current
    val page by viewModel.page.collectAsState()
    val header by viewModel.header.collectAsState()
    val release by viewModel.release.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val sections = page?.sections.orEmpty()
    val topSongs = remember(sections) { sections.firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is SongItem } }?.items?.filterIsInstance<SongItem>().orEmpty() }
    val similar = remember(sections) { sections.lastOrNull { s -> s.items.isNotEmpty() && s.items.all { it is ArtistItem } }?.items.orEmpty() }
    val name = header?.name ?: page?.artist?.title.orEmpty()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(page != null) { if (page != null) runCatching { playFocus.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        TvHomeBackdrop(header?.bannerUrl ?: page?.artist?.thumbnail, Modifier.fillMaxWidth().height(maxHeight * 0.82f))
        Column(Modifier.padding(start = TvSpace.Start, top = maxHeight * 0.3f).width(maxWidth * 0.36f)) {
            TvCover(header?.avatarUrl ?: page?.artist?.thumbnail, 104.dp, Modifier.size(104.dp), shape = CircleShape)
            Spacer(Modifier.height(10.dp))
            Text(name, style = TvText.Hero.copy(fontSize = 30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(
                    R.drawable.play,
                    onClick = { page?.artist?.playEndpoint?.let { player?.playQueue(YouTubeQueue(it)) } ?: actions.playItems(topSongs) },
                    filled = true,
                    modifier = Modifier.focusRequester(playFocus),
                )
                TvIconButton(
                    R.drawable.shuffle,
                    onClick = { page?.artist?.shuffleEndpoint?.let { player?.playQueue(YouTubeQueue(it)) } ?: actions.playItems(topSongs, shuffled = true) },
                    filled = true,
                )
                TvPillButton(
                    stringResource(if (subscribed == true) R.string.tv_following else R.string.tv_follow),
                    onClick = viewModel::toggleSubscribe,
                    bright = subscribed != true,
                )
            }
            release?.let { latest ->
                Spacer(Modifier.height(18.dp))
                TvReleaseCard(
                    label = latest.label,
                    title = latest.title,
                    subtitle = latest.subtitle,
                    thumbnailUrl = latest.thumbnailUrl,
                    onClick = { latest.album?.let(actions::open) ?: latest.song?.let(actions::open) },
                )
            }
        }
        if (similar.isNotEmpty()) {
            TvShelf(
                title = stringResource(R.string.tv_similar_artists),
                items = similar,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 18.dp).width(maxWidth * 0.5f),
            )
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyColumn(
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().width(maxWidth * 0.42f).padding(end = TvSpace.End),
                contentPadding = PaddingValues(top = maxHeight * 0.66f, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(topSongs, key = { it.id }, contentType = { "song" }) { song ->
                    TvSongRow(
                        title = song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.thumbnail,
                        isCurrent = false,
                        onMenu = { actions.openMenu(song.tvRef()) },
                        onClick = { actions.playItems(topSongs, topSongs.indexOf(song), name) },
                    )
                }
            }
        }
    }
}

// the concept's "new release" card: the newest thing out, big enough to read from the sofa
@Composable
private fun TvReleaseCard(label: String, title: String, subtitle: String, thumbnailUrl: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(Color(0xFF3A3A3A).copy(alpha = 0.85f))
            .tvClickable(shape = shape, focusedScale = 1.03f, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvCover(thumbnailUrl, 72.dp, Modifier.size(72.dp), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = TvText.Sub.copy(color = Color.White))
            Spacer(Modifier.height(4.dp))
            Text(title, style = TvText.Item.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = TvText.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
