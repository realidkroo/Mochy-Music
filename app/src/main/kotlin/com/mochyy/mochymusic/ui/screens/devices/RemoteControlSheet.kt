// RemoteControlSheet.kt

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.remote.RemoteNow
import com.mochyy.mochymusic.link.remote.RemoteQueueItem
import com.mochyy.mochymusic.link.remote.RemoteTarget
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.menu.AddToPlaylistDialog
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.makeTimeString
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// the phone as a remote for the device it steers: what's on there, play and skip, the queue, and its settings
fun PopupSheetState.showRemoteControl(link: DevicesLink) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(stringResource(R.string.remote_stop), Modifier.weight(1f)) {
                    link.remote.stop()
                    sheet?.dismiss()
                }
                SheetButton(stringResource(R.string.tv_done), Modifier.weight(1f), bright = true) { sheet?.dismiss() }
            }
        },
    ) {
        RemoteControlContent(link, onClose = { sheet?.dismiss() })
    }
}

@Composable
private fun ColumnScope.RemoteControlContent(link: DevicesLink, onClose: () -> Unit) {
    val target by link.remote.target.collectAsState()
    val steered = target
    if (steered == null) {
        // stopped from somewhere else, or the device was unlinked
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val now by link.remote.now.collectAsState()
    val reachable by link.remote.reachable.collectAsState()
    var saving by remember { mutableStateOf<MediaMetadata?>(null) }
    DisposableEffect(Unit) {
        link.remote.watch(true)
        onDispose { link.remote.watch(false) }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        RemoteHeader(steered, now, reachable)
        Spacer(Modifier.height(16.dp))
        NowPlayingCard(
            now = now,
            onLike = link.remote::like,
            onSave = { now?.song?.let { saving = it } },
        )
        Spacer(Modifier.height(16.dp))
        SeekBar(now, onSeek = link.remote::seek)
        Spacer(Modifier.height(8.dp))
        Transport(
            playing = now?.playing == true,
            enabled = reachable,
            onPrevious = link.remote::previous,
            onToggle = link.remote::toggle,
            onNext = link.remote::next,
        )
        HandOverRow(steered, onHandOver = link.remote::handOver)
        val upNext = now?.upNext.orEmpty()
        if (upNext.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            GroupLabel(stringResource(R.string.remote_up_next).uppercase())
            Card {
                upNext.take(MaxUpNextShown).forEach { item ->
                    UpNextRow(item, onPlay = { link.remote.jump(item.index) }, onRemove = { link.remote.remove(item.index) })
                }
            }
        }
        RemoteSettingsSection(link, steered)
        Spacer(Modifier.height(12.dp))
    }

    SaveToPlaylist(song = saving, onDone = { added ->
        saving = null
        // a tv shows this phone's playlists, so it pulls them again right away
        if (added) link.remote.syncLibrary()
    })
}

@Composable
private fun RemoteHeader(target: RemoteTarget, now: RemoteNow?, reachable: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Faint), contentAlignment = Alignment.Center) {
            Icon(target.kind.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                target.name,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.02).em,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    when {
                        !reachable -> R.string.remote_unreachable
                        now == null -> R.string.remote_connecting
                        now.playing -> R.string.remote_playing
                        else -> R.string.remote_paused
                    },
                ),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = if (reachable) Muted else Color(0xFFFF8A80),
            )
        }
    }
}

@Composable
private fun NowPlayingCard(now: RemoteNow?, onLike: () -> Unit, onSave: () -> Unit) {
    val song = now?.song
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Faint),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song?.title ?: stringResource(R.string.tv_menu_nothing_on),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song?.artists?.joinToString { it.name }.orEmpty(),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song != null) {
            IconButton(onClick = onLike) {
                Icon(
                    painterResource(if (now.liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = stringResource(R.string.remote_like),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onSave) {
                Icon(painterResource(R.drawable.playlist_add), contentDescription = stringResource(R.string.remote_add_to_playlist), tint = Color.White)
            }
        }
    }
}

// the song's place on the device, moved on every frame from its last answer; tap or drag to seek there
@Composable
private fun SeekBar(now: RemoteNow?, onSeek: (Long) -> Unit) {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playing = now?.playing == true
    LaunchedEffect(playing) {
        while (playing) withFrameMillis { clock.longValue = System.currentTimeMillis() }
    }
    val latest by rememberUpdatedState(now)
    val dragging = remember { mutableStateOf(false) }
    val dragFraction = remember { mutableFloatStateOf(0f) }
    val fraction: State<Float> = remember {
        derivedStateOf {
            val shown = latest ?: return@derivedStateOf 0f
            if (dragging.value) return@derivedStateOf dragFraction.floatValue
            if (shown.duration <= 0) 0f else (shown.positionAt(clock.longValue).toFloat() / shown.duration).coerceIn(0f, 1f)
        }
    }
    // the labels only change once a second, so only they recompose that often
    val elapsedSeconds by remember { derivedStateOf { ((latest?.duration ?: 0L) * fraction.value / 1000f).toLong() } }

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val shown = latest ?: return@detectTapGestures
                    if (shown.duration > 0) onSeek((shown.duration * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging.value = true
                        dragFraction.floatValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = { finishDrag(latest, dragging, dragFraction, onSeek) },
                    onDragCancel = { dragging.value = false },
                    onHorizontalDrag = { change, _ ->
                        dragFraction.floatValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            }
            .drawBehind {
                val height = 4.dp.toPx()
                val top = (size.height - height) / 2
                val radius = CornerRadius(height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), Size(size.width, height), radius)
                drawRoundRect(Color.White, Offset(0f, top), Size(size.width * fraction.value, height), radius)
            },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(makeTimeString(elapsedSeconds * 1000), fontFamily = InterFontFamily, fontSize = 11.sp, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(makeTimeString(now?.duration?.takeIf { it > 0 }), fontFamily = InterFontFamily, fontSize = 11.sp, color = Muted)
    }
}

private fun finishDrag(now: RemoteNow?, dragging: androidx.compose.runtime.MutableState<Boolean>, fraction: MutableFloatState, onSeek: (Long) -> Unit) {
    dragging.value = false
    val shown = now ?: return
    if (shown.duration > 0) onSeek((shown.duration * fraction.floatValue).toLong())
}

@Composable
private fun Transport(playing: Boolean, enabled: Boolean, onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(painterResource(R.drawable.skip_previous), contentDescription = stringResource(R.string.remote_previous), tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
                .clickable(enabled = enabled, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (playing) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(R.string.remote_play_pause),
                tint = Color.Black,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(painterResource(R.drawable.skip_next), contentDescription = stringResource(R.string.remote_next), tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

// when this phone has its own song loaded, one tap moves it over to the device
@Composable
private fun HandOverRow(target: RemoteTarget, onHandOver: () -> Unit) {
    val player = LocalPlayerConnection.current ?: return
    val local by player.mediaMetadata.collectAsState()
    val song = local ?: return
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .clickable(onClick = onHandOver)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Faint),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.remote_hand_over, target.name), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.title, fontFamily = InterFontFamily, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UpNextRow(item: RemoteQueueItem, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Faint),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.song.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.song.artists.joinToString { it.name }, fontFamily = InterFontFamily, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.remote_remove_from_queue), tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

// the song on the device goes into one of this phone's playlists, and the caller hears whether it went in
@Composable
private fun SaveToPlaylist(song: MediaMetadata?, onDone: (Boolean) -> Unit) {
    val database = LocalDatabase.current
    var added by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AddToPlaylistDialog(
        isVisible = song != null,
        onGetSong = { playlist ->
            val picked = song ?: return@AddToPlaylistDialog emptyList()
            database.withTransaction { insert(picked) }
            playlist.playlist.browseId?.let { browseId ->
                scope.launch(Dispatchers.IO) { runCatching { YouTube.addToPlaylist(browseId, picked.id) } }
            }
            added = true
            listOf(picked.id)
        },
        onDismiss = {
            onDone(added)
            added = false
        },
    )
}

private const val MaxUpNextShown = 8
