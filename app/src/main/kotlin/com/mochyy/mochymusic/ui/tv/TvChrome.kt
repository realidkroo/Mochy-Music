// TvChrome.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.playback.PlayerConnection

// the mochy mark at the top left, as on every concept page
@Composable
internal fun TvLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_mochy_mark),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier.size(22.dp),
    )
}

// the profile's picture at the top right; picking it goes back to who's listening
@Composable
internal fun TvAvatar(path: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .tvClickable(shape = CircleShape, focusedScale = 1.15f, onClick = onClick)
            .clip(CircleShape)
            .background(TvColors.CardRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = "file://$path", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp))
        } else {
            Icon(painterResource(R.drawable.person), contentDescription = null, tint = TvColors.Muted, modifier = Modifier.size(14.dp))
        }
    }
}

// the now playing pill: gone while nothing plays, just the cover, title and play state at rest, every control once the remote is on it
@Composable
internal fun TvNowPlayingPill(
    player: PlayerConnection,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata by player.mediaMetadata.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val song by player.currentSong.collectAsState(initial = null)
    val shuffle by player.shuffleModeEnabled.collectAsState()
    var focusedInside by remember { mutableStateOf(false) }
    val current = metadata

    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(TvColors.Pill)
                .border(1.dp, TvColors.PillEdge, CircleShape)
                .onFocusChanged { focusedInside = it.hasFocus }
                .focusGroup()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = focusedInside, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(R.drawable.shuffle, onClick = { player.player.shuffleModeEnabled = !shuffle }, size = 26.dp, tint = if (shuffle) Color.White else TvColors.Muted)
                    TvIconButton(R.drawable.skip_previous, onClick = player::seekToPrevious, size = 26.dp)
                    TvIconButton(if (isPlaying) R.drawable.pause else R.drawable.play, onClick = { player.player.togglePlayPause() }, size = 26.dp)
                    TvIconButton(R.drawable.skip_next, onClick = player::seekToNext, size = 26.dp)
                    TvIconButton(R.drawable.repeat, onClick = { player.player.toggleRepeatMode() }, size = 26.dp)
                    Spacer(Modifier.width(18.dp))
                }
            }
            // the cover and title open the full player
            Row(
                Modifier
                    .tvClickable(shape = CircleShape, look = TvFocusLook.Fill, onClick = onOpenPlayer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvCover(url = current?.thumbnailUrl, size = 22.dp, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.widthIn(max = 150.dp)) {
                    Text(current?.title.orEmpty(), style = TvText.Sub.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current?.artists?.joinToString { it.name }.orEmpty(), style = TvText.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // at rest the pill still says whether the music is going
                if (!focusedInside) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (isPlaying) R.string.tv_playing else R.string.tv_paused),
                        tint = TvColors.Muted,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            AnimatedVisibility(visible = focusedInside, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(18.dp))
                    TvIconButton(
                        if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                        onClick = player::toggleLike,
                        size = 26.dp,
                    )
                }
            }
        }
    }
}

// the four places, down the right edge like the concept
internal enum class TvTab(val route: String, val icon: Any, val label: Int) {
    Home(TvRoutes.Home, R.raw.home, R.string.home),
    Search(TvRoutes.Search, R.raw.search, R.string.search),
    Library(TvRoutes.Library, R.raw.lib, R.string.your_library),
    Mochy(TvRoutes.Mochy, R.drawable.nav_mochy_outline, R.string.tv_mochy),
}

@Composable
internal fun TvRail(selected: TvTab?, onSelect: (TvTab) -> Unit, focus: FocusRequester, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(TvColors.Pill)
            .border(1.dp, TvColors.PillEdge, RoundedCornerShape(26.dp))
            .focusGroup()
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvTab.entries.forEach { tab ->
            val lit = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Box(
                Modifier
                    .size(width = 34.dp, height = 44.dp)
                    .then(if (tab == (selected ?: TvTab.Home)) Modifier.focusRequester(focus) else Modifier)
                    .tvClickable(shape = RoundedCornerShape(17.dp), look = TvFocusLook.Invert, interactionSource = interaction) { onSelect(tab) }
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        when {
                            focused -> Color.White
                            lit -> TvColors.CardRaised
                            else -> Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = rememberAsyncImagePainter(tab.icon),
                    contentDescription = stringResource(tab.label),
                    tint = when {
                        focused -> Color.Black
                        lit -> Color.White
                        else -> TvColors.Muted
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// lets the shell know which tab a page belongs to, read off the route
internal fun tabOf(route: String?): TvTab? = when {
    route == null -> null
    route.startsWith(TvRoutes.Home) || route.startsWith(TvRoutes.Recap) -> TvTab.Home
    route.startsWith(TvRoutes.Search) || route.startsWith(TvRoutes.Browse) -> TvTab.Search
    route.startsWith(TvRoutes.Library) || route.startsWith(TvRoutes.Songs) || route.startsWith("local_playlist") -> TvTab.Library
    route.startsWith(TvRoutes.Mochy) || route.startsWith(TvRoutes.Phone) -> TvTab.Mochy
    else -> null
}
