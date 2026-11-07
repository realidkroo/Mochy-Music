// TvPlayerHost.kt

package com.mochyy.mochymusic.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.constants.VideoModeReturnStyleKey
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.component.BottomSheetState
import com.mochyy.mochymusic.ui.player.BottomSheetPlayer
import com.mochyy.mochymusic.ui.player.TvPlayerCommand
import com.mochyy.mochymusic.ui.player.TvPlayerRemote
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay

// the phone's own player, every style it has on a tv, opened full screen from the pill and run from the remote
@Composable
internal fun TvPlayerHost(
    state: BottomSheetState,
    player: PlayerConnection,
    navController: NavController,
) {
    BottomSheetPlayer(state = state, navController = navController, pureBlack = true)
    if (state.isExpandedOrExpanding) {
        TvRemoteLayer(player = player, onClose = state::collapseSoft)
    }
}

// sits over the open player: ok plays or pauses, left and right seek, up or down brings the control strip
@Composable
private fun TvRemoteLayer(player: PlayerConnection, onClose: () -> Unit) {
    val layerFocus = remember { FocusRequester() }
    val stripFocus = remember { FocusRequester() }
    var stripShown by remember { mutableStateOf(false) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { layerFocus.requestFocus() }
    // the strip steps aside once the remote has been still for a while
    LaunchedEffect(stripShown, lastKeyAt) {
        if (!stripShown) return@LaunchedEffect
        delay(StripHideMs)
        stripShown = false
        layerFocus.requestFocus()
    }
    BackHandler(enabled = stripShown) {
        stripShown = false
        layerFocus.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(layerFocus)
            .onPreviewKeyEvent {
                lastKeyAt = System.currentTimeMillis()
                false
            }
            .onKeyEvent { event ->
                if (stripShown || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        player.player.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.player.currentPosition - SeekStepMs).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        player.seekTo(player.player.currentPosition + SeekStepMs)
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        stripShown = true
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AnimatedVisibility(
            visible = stripShown,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LaunchedEffect(Unit) { runCatching { stripFocus.requestFocus() } }
            TvControlStrip(
                player = player,
                firstFocus = stripFocus,
                onClose = onClose,
                onLeaveTop = {
                    stripShown = false
                    layerFocus.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun TvControlStrip(
    player: PlayerConnection,
    firstFocus: FocusRequester,
    onClose: () -> Unit,
    onLeaveTop: () -> Unit,
) {
    val isPlaying by player.isPlaying.collectAsState()
    val song by player.currentSong.collectAsState(initial = null)
    var style by rememberEnumPreference(PlayerStyleKey, PlayerStyle.FLAGSHIP)
    var videoReturn by rememberPreference(VideoModeReturnStyleKey, "")
    val tvStyles = remember { PlayerStyle.entries.filter { it.availableOnTv } }

    // video mode remembers where it was opened from, so leaving it goes back there
    fun pick(next: PlayerStyle) {
        if (next == PlayerStyle.VIDEO && style != PlayerStyle.VIDEO) videoReturn = style.name
        style = next
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(top = 48.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // up from the first row hands the remote back to the player, down from the last goes nowhere
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onLeaveTop()
                    true
                } else {
                    false
                }
            },
        ) {
            TvIconButton(R.drawable.skip_previous, onClick = player::seekToPrevious, filled = true)
            TvIconButton(
                if (isPlaying) R.drawable.pause else R.drawable.play,
                onClick = { player.player.togglePlayPause() },
                size = 44.dp,
                filled = true,
                modifier = Modifier.focusRequester(firstFocus),
            )
            TvIconButton(R.drawable.skip_next, onClick = player::seekToNext, filled = true)
            Spacer(Modifier.padding(horizontal = 6.dp))
            TvIconButton(if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border, onClick = player::toggleLike, filled = true)
            TvIconButton(R.drawable.lyrics, onClick = { TvPlayerRemote.send(TvPlayerCommand.Lyrics) }, filled = true)
            TvIconButton(R.drawable.queue_music, onClick = { TvPlayerRemote.send(TvPlayerCommand.Queue) }, filled = true)
            TvIconButton(R.drawable.close, onClick = onClose, filled = true)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown },
        ) {
            tvStyles.forEach { option ->
                val lit = option == style
                Text(
                    text = option.selectorName,
                    style = TvText.Sub.copy(color = if (lit) Color.Black else Color.White),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (lit) Color.White else Color.White.copy(alpha = 0.12f))
                        .tvClickable(shape = CircleShape, focusedScale = 1.06f) { pick(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val SeekStepMs = 10_000L
private const val StripHideMs = 5_000L
