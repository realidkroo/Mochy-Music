// TvSongMenu.kt

package com.mochyy.mochymusic.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.PhoneFiles

// everything a song can do, as a short list in the middle of the screen the remote walks down
@Composable
internal fun TvSongMenu(song: TvSongRef, onDismiss: () -> Unit) {
    val actions = LocalTvActions.current
    val link = LocalTvLink.current
    val database = LocalDatabase.current
    val saved by remember(song.id) { database.song(song.id) }.collectAsState(initial = null)
    val phoneFiles by PhoneFiles.files.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)

    val entries = buildList {
        add(Triple(R.drawable.playlist_play, stringResource(R.string.play_next)) { actions.playNext(song) })
        add(Triple(R.drawable.queue_music, stringResource(R.string.add_to_queue)) { actions.addToQueue(song) })
        val liked = saved?.song?.liked == true
        add(Triple(if (liked) R.drawable.favorite else R.drawable.favorite_border, stringResource(if (liked) R.string.tv_unlike else R.string.tv_like)) { actions.toggleLike(song) })
        song.albumId?.let { id -> add(Triple(R.drawable.album, stringResource(R.string.tv_go_to_album)) { actions.navController.navigate("album/$id") }) }
        song.artists.firstOrNull { it.second != null }?.let { (name, id) ->
            add(Triple(R.drawable.artist, stringResource(R.string.tv_go_to_artist, name)) { actions.navController.navigate("artist/$id") })
        }
        // a song the linked phone keeps can be copied here, so it plays even when the phone is away
        if (song.id in phoneFiles && song.id !in link.sync.onTv()) {
            add(Triple(R.drawable.download, stringResource(R.string.tv_copy_to_tv)) { link.sync.transfer(listOf(song.id)) })
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(320.dp)
                .tvPopIn()
                .clip(RoundedCornerShape(26.dp))
                .background(TvColors.Card)
                .focusGroup()
                .padding(12.dp),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvCover(song.thumbnailUrl, 40.dp, Modifier.size(40.dp), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(song.title, style = TvText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artists.joinToString { it.first }, style = TvText.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
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
