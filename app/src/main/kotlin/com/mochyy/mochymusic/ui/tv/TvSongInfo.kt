// TvSongInfo.kt

package com.mochyy.mochymusic.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.player.info.AboutSource
import com.mochyy.mochymusic.ui.player.info.SongAbout
import com.mochyy.mochymusic.ui.player.info.SongAboutLoader
import com.mochyy.mochymusic.ui.player.info.textFor

// which part of the panel the remote lands on first
internal enum class TvInfoStart { Song, Source }

// the song's info on a tv: a drawer from the right where every line is a stop for the remote, artists and album open their pages
@Composable
internal fun TvSongInfoPanel(
    player: PlayerConnection,
    start: TvInfoStart,
    onOpen: (route: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val song by player.mediaMetadata.collectAsState()
    val current = song ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val about by remember(current.id) { SongAboutLoader.load(current, database, context.applicationContext) }
        .collectAsState(initial = SongAbout(artist = current.artists.joinToString { it.name }))
    val playingFrom by player.service.playingFrom.collectAsState()
    val facts by remember(current.id) { player.streamFacts(current.id) }.collectAsState(initial = null)
    val format by remember(player) { player.currentFormat }.collectAsState(initial = null)
    val firstFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    var aboutOpen by remember { mutableStateOf(false) }
    LaunchedEffect(start) { runCatching { (if (start == TvInfoStart.Source) sourceFocus else firstFocus).requestFocus() } }
    BackHandler(onBack = onDismiss)

    val aboutText = AboutSource.entries.firstNotNullOfOrNull { textFor(about, it) }
    val from = playingFrom[current.id]
    val sourceLine = listOfNotNull(from?.name, from?.quality, facts?.describe(format)).distinct().joinToString(" · ")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(0.35f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.7f))),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(400.dp)
                .tvPopIn()
                // the drawer melts in from its own left edge instead of ending the scrim behind it in a flat opaque line
                .background(Brush.horizontalGradient(0f to Color.Transparent, 0.2f to TvColors.Pill, 1f to TvColors.Pill))
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvCover(current.thumbnailUrl, 64.dp, Modifier.size(64.dp), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.title, style = TvText.Item.copy(fontSize = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(current.artists.joinToString { it.name }, style = TvText.Sub.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))

            var firstUsed = false
            fun firstModifier(): Modifier = if (!firstUsed) {
                firstUsed = true
                Modifier.focusRequester(firstFocus)
            } else {
                Modifier
            }

            // what the song is about, a few lines; ok shows the whole of it
            if (aboutText != null) {
                TvInfoLabel(stringResource(R.string.tv_info_about))
                Text(
                    aboutText,
                    style = TvText.Body.copy(fontSize = 13.sp, color = Color(0xFFD6D6D6)),
                    maxLines = if (aboutOpen) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = firstModifier()
                        .fillMaxWidth()
                        .animateContentSize()
                        .tvClickable(shape = RoundedCornerShape(12.dp), look = TvFocusLook.Fill) { aboutOpen = !aboutOpen }
                        .padding(8.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            TvInfoLabel(stringResource(R.string.tv_info_go_to))
            current.artists.filter { !it.id.isNullOrBlank() }.forEach { artist ->
                TvInfoRow(
                    icon = R.drawable.artist,
                    text = artist.name,
                    modifier = firstModifier(),
                    onClick = { onOpen("artist/${artist.id}") },
                )
            }
            current.album?.let { album ->
                TvInfoRow(
                    icon = R.drawable.album,
                    text = album.title,
                    modifier = firstModifier(),
                    onClick = { onOpen("album/${album.id}") },
                )
            }
            Spacer(Modifier.height(14.dp))

            // where the song is coming from right now and in what format, a stop of its own so the remote can rest on it
            TvInfoLabel(stringResource(R.string.tv_info_source))
            TvInfoRow(
                icon = R.drawable.graphic_eq,
                text = sourceLine.ifBlank { stringResource(R.string.tv_info_source_unknown) },
                modifier = Modifier.focusRequester(sourceFocus).then(firstModifier()),
                onClick = {},
            )
            Spacer(Modifier.height(20.dp))
            TvInfoRow(icon = R.drawable.close, text = stringResource(R.string.tv_info_close), onClick = onDismiss)
        }
    }
}

@Composable
private fun TvInfoLabel(text: String) {
    Text(text, style = TvText.Section.copy(fontSize = 12.sp, color = TvColors.Muted), modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
}

@Composable
private fun TvInfoRow(icon: Int, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(14.dp), look = TvFocusLook.Fill, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(TvColors.CardRaised), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = TvText.Item, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
