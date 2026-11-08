// TvPlayerHost.kt

package com.mochyy.mochymusic.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.mochyy.mochymusic.constants.TvFlagshipSquareKey
import com.mochyy.mochymusic.constants.VideoModeReturnStyleKey
import com.mochyy.mochymusic.ui.player.flagship.AnimatedPlayGlyph
import com.mochyy.mochymusic.ui.player.flagship.AnimatedSkipGlyph
import com.mochyy.mochymusic.ui.player.flagship.SkipCycleState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
        TvRemoteLayer(
            player = player,
            // an artist or album from the song's panel opens its page with the player put away
            onOpen = { route ->
                state.collapseSoft()
                navController.navigate(route)
            },
        )
    }
}

// which bar the remote has brought over the player, if any
private enum class TvPlayerBar { None, PlayingNext, Strip }

// sits over the open player: ok plays or pauses, left and right seek, up brings what's playing next, down the lyrics and looks
@Composable
private fun TvRemoteLayer(player: PlayerConnection, onOpen: (String) -> Unit) {
    val layerFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    var bar by remember { mutableStateOf(TvPlayerBar.None) }
    // the song's info panel; it takes the remote while it's open, the sheets made for touch couldn't be reached
    var info by remember { mutableStateOf<TvInfoStart?>(null) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    val hide: () -> Unit = {
        bar = TvPlayerBar.None
        runCatching { layerFocus.requestFocus() }
    }

    LaunchedEffect(Unit) { layerFocus.requestFocus() }
    // the player's own title steps aside while the control bar carries it
    LaunchedEffect(bar) { TvPlayerRemote.controlsShown = bar == TvPlayerBar.Strip }
    DisposableEffect(Unit) { onDispose { TvPlayerRemote.controlsShown = false } }
    // a bar steps aside once the remote has been still for a while
    LaunchedEffect(bar, lastKeyAt) {
        if (bar == TvPlayerBar.None || info != null) return@LaunchedEffect
        delay(BarHideMs)
        hide()
    }
    BackHandler(enabled = bar != TvPlayerBar.None) { hide() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(layerFocus)
            .onPreviewKeyEvent {
                lastKeyAt = System.currentTimeMillis()
                false
            }
            .onKeyEvent { event ->
                if (bar != TvPlayerBar.None || info != null || event.type != KeyEventType.KeyDown) return@onKeyEvent false
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
                    Key.DirectionUp -> {
                        bar = TvPlayerBar.PlayingNext
                        true
                    }
                    Key.DirectionDown -> {
                        bar = TvPlayerBar.Strip
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AnimatedVisibility(
            visible = bar == TvPlayerBar.PlayingNext,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            LaunchedEffect(Unit) { runCatching { barFocus.requestFocus() } }
            TvPlayingNext(player = player, firstFocus = barFocus, onLeave = hide)
        }
        AnimatedVisibility(
            visible = bar == TvPlayerBar.Strip,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LaunchedEffect(Unit) { runCatching { barFocus.requestFocus() } }
            TvControlStrip(
                player = player,
                firstFocus = barFocus,
                onLeaveTop = hide,
                onInfo = { start ->
                    bar = TvPlayerBar.None
                    info = start
                },
            )
        }
        info?.let { start ->
            TvSongInfoPanel(
                player = player,
                start = start,
                onOpen = onOpen,
                onDismiss = {
                    info = null
                    runCatching { layerFocus.requestFocus() }
                },
            )
        }
    }
}

// the queue as a row along the top: ok jumps to a song, its menu moves it earlier or later or takes it out
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvPlayingNext(player: PlayerConnection, firstFocus: FocusRequester, onLeave: () -> Unit) {
    val queue by player.queueSnapshot.collectAsState()
    val current = queue.items.getOrNull(queue.currentIndex)
    val upcoming = remember(queue) { queue.items.drop(queue.currentIndex + 1).take(MaxShownNext) }
    var menuFor by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)))
            .padding(top = 26.dp, bottom = 48.dp)
            // down hands the remote back to the player, up goes nowhere
            .onPreviewKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown || menuFor != null -> false
                    event.key == Key.DirectionDown -> {
                        onLeave()
                        true
                    }
                    event.key == Key.DirectionUp -> true
                    else -> false
                }
            },
    ) {
        Text(stringResource(R.string.tv_playing_next), style = TvText.Section, modifier = Modifier.padding(start = TvSpace.Start, bottom = 4.dp))
        if (upcoming.isEmpty()) {
            Text(
                stringResource(R.string.tv_queue_empty),
                style = TvText.Body,
                modifier = Modifier
                    .padding(start = TvSpace.Start, top = 8.dp)
                    .focusRequester(firstFocus)
                    .focusable(),
            )
            return@Column
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowPivot) {
            LazyRow(
                contentPadding = PaddingValues(start = TvSpace.Start, end = TvSpace.Start, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(upcoming, key = { _, item -> item.uid }, contentType = { _, _ -> "next" }) { index, item ->
                    Box(Modifier.animateItem()) {
                        TvItemCard(
                            title = item.title,
                            subtitle = item.artist,
                            thumbnailUrl = item.artworkUri?.toString(),
                            size = 92.dp,
                            coverModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onLongClick = { menuFor = item.uid },
                            onClick = { player.playQueueItem(item.uid) },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { uid ->
        val index = upcoming.indexOfFirst { it.uid == uid }
        if (index < 0) {
            menuFor = null
            return@let
        }
        val item = upcoming[index]
        TvQueueMenu(
            title = item.title,
            canMoveEarlier = index > 0,
            canMoveLater = index < upcoming.lastIndex,
            onPlay = { player.playQueueItem(uid) },
            // moving is "put it after": earlier lands it after the song two back, or right after the playing one
            onEarlier = { (upcoming.getOrNull(index - 2) ?: current)?.let { player.moveQueueItem(uid, it.uid) } },
            onLater = { upcoming.getOrNull(index + 1)?.let { player.moveQueueItem(uid, it.uid) } },
            onRemove = { player.removeQueueItems(listOf(uid)) },
            onDismiss = { menuFor = null },
        )
    }
}

// the few things a queued song can do, as a short list the remote walks down
@Composable
private fun TvQueueMenu(
    title: String,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onPlay: () -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)
    val entries = buildList {
        add(Triple(R.drawable.play, stringResource(R.string.tv_queue_play_now), onPlay))
        if (canMoveEarlier) add(Triple(R.drawable.arrow_back, stringResource(R.string.tv_queue_earlier), onEarlier))
        if (canMoveLater) add(Triple(R.drawable.arrow_forward, stringResource(R.string.tv_queue_later), onLater))
        add(Triple(R.drawable.close, stringResource(R.string.tv_queue_remove), onRemove))
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(280.dp)
                .tvPopIn()
                .clip(RoundedCornerShape(24.dp))
                .background(TvColors.Card)
                .focusGroup()
                .padding(12.dp),
        ) {
            Text(title, style = TvText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
            entries.forEachIndexed { index, (icon, label, run) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .tvClickable(shape = RoundedCornerShape(14.dp), look = TvFocusLook.Fill) {
                            run()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = TvText.Item)
                }
            }
        }
    }
}

// one look in the control bar: a player style, and for the flagship whether its cover is the square one
private data class TvLook(val style: PlayerStyle, val square: Boolean, val label: String)

// the control bar the remote brings up from below: the title slides in from the cover's corner, then the seek bar, the play buttons, and the looks
@Composable
private fun TvControlStrip(
    player: PlayerConnection,
    firstFocus: FocusRequester,
    onLeaveTop: () -> Unit,
    onInfo: (TvInfoStart) -> Unit,
) {
    var style by rememberEnumPreference(PlayerStyleKey, PlayerStyle.FLAGSHIP)
    var square by rememberPreference(TvFlagshipSquareKey, false)
    var videoReturn by rememberPreference(VideoModeReturnStyleKey, "")
    val squareLabel = stringResource(R.string.tv_flagship_square)
    val looks = remember(squareLabel) {
        PlayerStyle.entries.filter { it.availableOnTv }.flatMap { option ->
            if (option == PlayerStyle.FLAGSHIP) {
                listOf(TvLook(option, false, option.selectorName), TvLook(option, true, squareLabel))
            } else {
                listOf(TvLook(option, false, option.selectorName))
            }
        }
    }
    val song by player.mediaMetadata.collectAsState()

    // video mode remembers where it was opened from, so leaving it goes back there
    fun pick(look: TvLook) {
        if (look.style == PlayerStyle.VIDEO && style != PlayerStyle.VIDEO) videoReturn = style.name
        if (look.style == PlayerStyle.FLAGSHIP) square = look.square
        style = look.style
    }

    // the title comes in from the cover's corner a moment after the bar
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrive.animateTo(1f, tween(520, delayMillis = 60, easing = TvEase)) }

    Column(
        Modifier
            .fillMaxWidth()
            // dark enough at the bottom that the cover's own title fades away under the bar
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Black.copy(alpha = 0.8f), 0.55f to Color.Black.copy(alpha = 0.95f), 1f to Color.Black))
            .padding(start = TvSpace.Start, end = TvSpace.Start, top = 60.dp, bottom = 24.dp),
    ) {
        Column(
            Modifier.graphicsLayer {
                val p = arrive.value
                alpha = p
                translationX = (1f - p) * -36.dp.toPx()
                translationY = (1f - p) * 18.dp.toPx()
            },
        ) {
            Text(song?.title.orEmpty(), style = TvText.Hero.copy(fontSize = 24.sp, lineHeight = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song?.artists?.joinToString { it.name }.orEmpty(), style = TvText.Body.copy(fontSize = 13.sp, color = Color(0xFFBDBDBD)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        TvSeekBar(
            player = player,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onLeaveTop()
                    true
                } else {
                    false
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TvTransport(player, firstFocus)
            Spacer(Modifier.width(20.dp))
            TvPillButton(stringResource(R.string.tv_lyrics), onClick = { TvPlayerRemote.send(TvPlayerCommand.Lyrics) }, icon = R.drawable.lyrics)
            Spacer(Modifier.width(8.dp))
            TvPillButton(stringResource(R.string.tv_song_info), onClick = { onInfo(TvInfoStart.Song) }, icon = R.drawable.info)
            Spacer(Modifier.width(8.dp))
            TvPillButton(stringResource(R.string.tv_source), onClick = { onInfo(TvInfoStart.Source) }, icon = R.drawable.graphic_eq)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // nothing below the looks, down stays here
            modifier = Modifier.onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown },
        ) {
            looks.forEach { look ->
                val lit = look.style == style && (look.style != PlayerStyle.FLAGSHIP || look.square == square)
                TvPillButton(look.label, onClick = { pick(look) }, bright = lit)
            }
        }
    }
}

// previous, play and next as the main player's own running glyphs, no circles round them
@Composable
private fun TvTransport(player: PlayerConnection, playFocus: FocusRequester) {
    val isPlaying by player.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    val previous = remember { SkipCycleState() }
    val next = remember { SkipCycleState() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvGlyphButton(onClick = {
            scope.launch { previous.step() }
            player.seekToPrevious()
        }) { AnimatedSkipGlyph(cycle = previous, backwards = true, color = Color.White, width = 26.dp) }
        TvGlyphButton(modifier = Modifier.focusRequester(playFocus), onClick = { player.player.togglePlayPause() }) {
            AnimatedPlayGlyph(playing = isPlaying, color = Color.White, height = 28.dp)
        }
        TvGlyphButton(onClick = {
            scope.launch { next.step() }
            player.seekToNext()
        }) { AnimatedSkipGlyph(cycle = next, backwards = false, color = Color.White, width = 26.dp) }
    }
}

// a bare glyph the remote can land on: a soft rounded light behind it while focused, a little bigger
@Composable
private fun TvGlyphButton(modifier: Modifier = Modifier, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        modifier
            .size(54.dp)
            .tvClickable(shape = RoundedCornerShape(16.dp), look = TvFocusLook.Fill, focusedScale = 1.12f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

// the song's progress, a thin line that thickens under the remote; left and right on it seek ten seconds
@Composable
private fun TvSeekBar(player: PlayerConnection, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // read a few times a second only while the bar is up, and drawn without recomposing
    val position = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position.longValue = player.player.currentPosition.coerceAtLeast(0L)
            duration.longValue = player.player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }
    val thickness = animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "seekThickness")
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            player.seekTo((player.player.currentPosition - SeekStepMs).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            player.seekTo(player.player.currentPosition + SeekStepMs)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interaction)
                .drawBehind {
                    val line = (3.dp + 3.dp * thickness.value).toPx()
                    val y = size.height / 2
                    val fraction = if (duration.longValue > 0) (position.longValue.toFloat() / duration.longValue).coerceIn(0f, 1f) else 0f
                    drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), line, StrokeCap.Round)
                    drawLine(Color.White, Offset(0f, y), Offset(size.width * fraction, y), line, StrokeCap.Round)
                    if (thickness.value > 0.01f) {
                        drawCircle(Color.White, radius = 7.dp.toPx() * thickness.value, center = Offset(size.width * fraction, y))
                    }
                },
        )
        Row(Modifier.fillMaxWidth()) {
            TvTime(position)
            Spacer(Modifier.weight(1f))
            TvTime(duration)
        }
    }
}

// a time under the seek bar; it reads its own state so only these two little texts redraw as the song plays
@Composable
private fun TvTime(millis: androidx.compose.runtime.MutableLongState) {
    val seconds = millis.longValue / 1000
    Text("%d:%02d".format(seconds / 60, seconds % 60), style = TvText.Sub.copy(fontSize = 11.sp))
}

private const val SeekStepMs = 10_000L
private const val BarHideMs = 6_000L

// the shelf shows the next few, a radio's queue goes on much longer
private const val MaxShownNext = 30
