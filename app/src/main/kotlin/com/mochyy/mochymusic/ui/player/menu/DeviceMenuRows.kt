// TvMenuRows.kt

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.link.LinkSongs
import com.mochyy.mochymusic.link.phone.LiveTv
import com.mochyy.mochymusic.link.phone.PhoneLinkHost
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.TopToaster
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhoneLinkEntryPoint {
    fun phoneLinkHost(): PhoneLinkHost
}

// the phone's link to its tvs, for composables that live outside hilt's view models
@Composable
internal fun rememberPhoneLinkHost(): PhoneLinkHost {
    val context = LocalContext.current.applicationContext
    return remember { EntryPointAccessors.fromApplication(context, PhoneLinkEntryPoint::class.java).phoneLinkHost() }
}

// the player menu's tv rows: play the queue on a linked tv that's open, line the song up there, or work it as a remote
@Composable
internal fun TvMenuRows(onDismiss: () -> Unit) {
    val host = rememberPhoneLinkHost()
    val shown by host.menuShown.collectAsState()
    val live by host.live.collectAsState()
    val player = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tvs = remember(live) { host.liveTvs() }
    var remoteFor by remember { mutableStateOf<LiveTv?>(null) }
    if (!shown || tvs.isEmpty()) return

    tvs.forEach { tv ->
        Spacer(Modifier.height(8.dp))
        MenuRow(
            icon = R.drawable.cast_connected,
            title = stringResource(R.string.tv_menu_play_on, tv.name),
            onClick = {
                scope.launch { sendQueueToTv(context, host, tv, player) }
                onDismiss()
            },
        )
        Spacer(Modifier.height(8.dp))
        MenuRow(
            icon = R.drawable.playlist_play,
            title = stringResource(R.string.tv_menu_play_next_on, tv.name),
            enabled = player.mediaMetadata.value != null,
            onClick = {
                val song = player.mediaMetadata.value ?: return@MenuRow
                scope.launch {
                    val sent = host.tellTv(tv, "/next", JSONObject().put("songs", LinkSongs.listToJson(listOf(song))))
                    TopToaster.show(context.getString(if (sent != null) R.string.tv_menu_next_sent else R.string.tv_menu_unreachable, tv.name))
                }
                onDismiss()
            },
        )
        Spacer(Modifier.height(8.dp))
        MenuRow(icon = R.drawable.tune, title = stringResource(R.string.tv_menu_control, tv.name), onClick = { remoteFor = tv })
    }

    remoteFor?.let { tv -> TvRemoteDialog(host, tv, onDismiss = { remoteFor = null }) }
}

// the queue from the playing song on, at the same spot, goes to the tv and the phone goes quiet
internal suspend fun sendQueueToTv(context: android.content.Context, host: PhoneLinkHost, tv: LiveTv, player: com.mochyy.mochymusic.playback.PlayerConnection) {
    val exo = player.player
    val from = exo.currentMediaItemIndex.coerceAtLeast(0)
    val songs = (from until minOf(exo.mediaItemCount, from + MaxSentSongs)).mapNotNull { exo.getMediaItemAt(it).metadata }
    if (songs.isEmpty()) return
    val sent = host.tellTv(tv, "/play", JSONObject().put("songs", LinkSongs.listToJson(songs)).put("index", 0).put("position", exo.currentPosition))
    if (sent != null) player.pause()
    TopToaster.show(context.getString(if (sent != null) R.string.tv_menu_sent else R.string.tv_menu_unreachable, tv.name))
}

// a small remote for the tv: what's on, play and skip, and the tv's player look
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvRemoteDialog(host: PhoneLinkHost, tv: LiveTv, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf<JSONObject?>(null) }
    var reachable by remember { mutableStateOf(true) }
    // what the tv plays changes on its own, so the remote checks in every second while it's open
    LaunchedEffect(tv) {
        while (true) {
            val answer = host.tellTv(tv, "/now")
            reachable = answer != null
            if (answer != null) now = answer
            delay(1_000)
        }
    }
    fun send(path: String, body: JSONObject) {
        scope.launch {
            host.tellTv(tv, path, body)
            host.tellTv(tv, "/now")?.let { now = it }
        }
    }
    val song = now?.optJSONObject("song")?.let(LinkSongs::fromJson)
    val playing = now?.optBoolean("playing") == true
    val style = now?.optString("style").orEmpty()
    val styles = now?.optJSONArray("styles")

    DefaultDialog(onDismiss = onDismiss, title = { Text(stringResource(R.string.tv_menu_remote_title, tv.name)) }) {
        if (!reachable) {
            Text(stringResource(R.string.tv_menu_unreachable, tv.name), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = song?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song?.title ?: stringResource(R.string.tv_menu_nothing_on), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artists?.joinToString { it.name }.orEmpty(), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { send("/control", JSONObject().put("action", "previous")) }) {
                Icon(painterResource(R.drawable.skip_previous), contentDescription = null)
            }
            IconButton(onClick = { send("/control", JSONObject().put("action", "toggle")) }) {
                Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), contentDescription = null)
            }
            IconButton(onClick = { send("/control", JSONObject().put("action", "next")) }) {
                Icon(painterResource(R.drawable.skip_next), contentDescription = null)
            }
            IconButton(onClick = { send("/control", JSONObject().put("action", "open")) }) {
                Icon(painterResource(R.drawable.fullscreen), contentDescription = null)
            }
        }
        if (styles != null && styles.length() > 0) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.tv_menu_tv_look), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (index in 0 until styles.length()) {
                    val option = styles.optJSONObject(index) ?: continue
                    val lit = option.optString("name") == style
                    Text(
                        option.optString("label"),
                        color = if (lit) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (lit) Color.White else Color.White.copy(alpha = 0.12f))
                            .clickable { send("/style", JSONObject().put("style", option.optString("name"))) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

// a queue sent to the tv stops here, a radio would go on for ever
private const val MaxSentSongs = 200
