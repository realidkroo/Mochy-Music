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

// the artist page from the concept: their photo across the page, who they are and what's new on the left, their music on the right
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
    val songSection = remember(sections) { sections.firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is SongItem } } }
    val topSongs = remember(songSection) { songSection?.items?.filterIsInstance<SongItem>().orEmpty() }
    val similarSection = remember(sections) { sections.lastOrNull { s -> s.items.isNotEmpty() && s.items.all { it is ArtistItem } } }
    // everything else the artist has: albums, singles, playlists, videos, appearances
    val moreSections = remember(sections) { sections.filter { it !== songSection && it !== similarSection && it.items.isNotEmpty() } }
    val name = header?.name ?: page?.artist?.title.orEmpty()
    val seen = remember { HashSet<Any>() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(page != null) { if (page != null) runCatching { playFocus.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        TvHomeBackdrop(header?.bannerUrl ?: page?.artist?.thumbnail, Modifier.fillMaxWidth().height(pageHeight * 0.86f))
        Row(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
                LazyColumn(
                    modifier = Modifier.width(pageWidth * 0.42f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = TvSpace.Start, top = pageHeight * 0.26f, bottom = 40.dp),
                ) {
                    item(key = "who") {
                        Column {
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
                        }
                    }
                    release?.let { latest ->
                        item(key = "release") {
                            TvReleaseCard(
                                label = latest.label,
                                title = latest.title,
                                subtitle = latest.subtitle,
                                thumbnailUrl = latest.thumbnailUrl,
                                modifier = Modifier.padding(top = 18.dp, end = 10.dp).tvReveal("release", seen),
                                onClick = { latest.album?.let(actions::open) ?: latest.song?.let(actions::open) },
                            )
                        }
                    }
                    similarSection?.let { similar ->
                        item(key = "similar") {
                            TvShelf(
                                title = stringResource(R.string.tv_similar_artists),
                                items = similar.items,
                                contentPadding = PaddingValues(end = 10.dp, top = 10.dp, bottom = 10.dp),
                                modifier = Modifier.padding(top = 18.dp).tvReveal("similar", seen, delayMillis = 80),
                                titleInset = 0.dp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(30.dp))
            CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = pageHeight * 0.5f, end = TvSpace.End, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(topSongs.take(MaxTopSongs), key = { "song_${it.id}" }, contentType = { "song" }) { song ->
                        TvSongRow(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnailUrl = song.thumbnail,
                            isCurrent = false,
                            onMenu = { actions.openMenu(song.tvRef()) },
                            onClick = { actions.playItems(topSongs, topSongs.indexOf(song), name) },
                        )
                    }
                    items(moreSections, key = { "section_${it.title}" }, contentType = { "section" }) { section ->
                        TvShelf(
                            title = section.title,
                            items = section.items,
                            contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp, end = 10.dp),
                            modifier = Modifier.padding(top = 16.dp).tvReveal(section.title, seen),
                            titleInset = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

// the concept's "new release" card: the newest thing out, big enough to read from the sofa
@Composable
private fun TvReleaseCard(label: String, title: String, subtitle: String, thumbnailUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .tvClickable(shape = shape, focusedScale = 1.03f, onClick = onClick)
            .clip(shape)
            .background(Color(0xFF3A3A3A).copy(alpha = 0.85f))
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

// the artist's best known songs at the top of the right side, the rest of their music in rows under them
private const val MaxTopSongs = 6
