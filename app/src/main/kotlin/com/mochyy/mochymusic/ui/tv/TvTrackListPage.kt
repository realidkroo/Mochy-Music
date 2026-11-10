// TvTrackListPage.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.containerTransformTarget
import kotlinx.coroutines.flow.MutableStateFlow

// one row of the album page, whatever list it came from
@Immutable
internal data class TvTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val ref: TvSongRef,
)

// the album and playlist page from the concept: the cover down the left melting into the dark, the songs on the right
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvTrackListPage(
    title: String,
    subtitle: String,
    coverUrl: String?,
    tracks: List<TvTrack>,
    loading: Boolean,
    onPlay: (index: Int, shuffled: Boolean) -> Unit,
    background: Color = Color(0xFF0C0E11),
    // the card it grew out of, so it can shrink back into exactly that spot on the way out
    transformKey: String? = null,
) {
    val actions = LocalTvActions.current
    val player = LocalPlayerConnection.current
    val playingId by remember(player) { player?.mediaMetadata ?: MutableStateFlow<MediaMetadata?>(null) }.collectAsState()
    val firstSong = remember { FocusRequester() }
    LaunchedEffect(tracks.isNotEmpty()) { if (tracks.isNotEmpty()) runCatching { firstSong.requestFocus() } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            .containerTransformTarget(transformKey, sourceCornerRadius = 16.dp),
    ) {
        val coverWidth = maxWidth * 0.47f
        // the cover, its right and bottom edges fading into the page so the words sit on the dark
        Box(Modifier.width(coverWidth).fillMaxHeight()) {
            TvBackdropImage(coverUrl, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val sideways = Brush.horizontalGradient(0.55f to Color.Transparent, 1f to background)
                        val down = Brush.verticalGradient(0.5f to Color.Transparent, 1f to background.copy(alpha = 0.95f))
                        onDrawBehind {
                            drawRect(sideways)
                            drawRect(down)
                        }
                    },
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = TvSpace.Start, bottom = 40.dp).width(coverWidth - TvSpace.Start)) {
            Text(title, style = TvText.Hero.copy(fontSize = 34.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = TvText.Sub.copy(fontSize = 12.sp, color = Color(0xFFBDBDBD)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvPillButton(stringResource(R.string.play), onClick = { onPlay(0, false) }, icon = R.drawable.play, bright = true)
                TvPillButton(stringResource(R.string.shuffle), onClick = { onPlay(0, true) }, icon = R.drawable.shuffle)
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .padding(start = coverWidth + 40.dp, end = TvSpace.End),
                contentPadding = PaddingValues(top = 130.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loading && tracks.isEmpty()) {
                    item { Text(stringResource(R.string.tv_loading), style = TvText.Body) }
                }
                itemsIndexed(tracks, key = { index, track -> "${track.id}#$index" }, contentType = { _, _ -> "track" }) { index, track ->
                    TvSongRow(
                        title = track.title,
                        subtitle = track.subtitle,
                        thumbnailUrl = track.thumbnailUrl,
                        isCurrent = playingId?.id == track.id,
                        modifier = if (index == 0) Modifier.focusRequester(firstSong) else Modifier,
                        onMenu = { actions.openMenu(track.ref) },
                        onClick = { onPlay(index, false) },
                    )
                }
                if (tracks.isNotEmpty()) {
                    item(key = "ledger", contentType = "ledger") { TvLedger(tracks) }
                }
            }
        }
    }
}

// the concept's end strip: how many songs, "end of line" spaced out, how long it all runs
@Composable
private fun TvLedger(tracks: List<TvTrack>) {
    val minutes = tracks.sumOf { it.durationSeconds.coerceAtLeast(0) } / 60
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, end = 38.dp)) {
        HorizontalDivider(color = Color(0xFF3A3A3A), thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.tv_ledger_songs, tracks.size, tracks.size).uppercase(), style = TvText.Ledger)
            Text(
                stringResource(R.string.tv_end_of_line).uppercase().toCharArray().joinToString(" "),
                style = TvText.Ledger.copy(fontSize = 8.sp),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(pluralStringResource(R.plurals.tv_ledger_minutes, minutes, minutes).uppercase(), style = TvText.Ledger)
        }
        HorizontalDivider(color = Color(0xFF3A3A3A), thickness = 0.5.dp)
    }
}
