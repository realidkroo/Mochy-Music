// TvLinkToast.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.PhoneSyncState
import kotlinx.coroutines.delay

// what the banner says: the phone came back, or the library is now the phone's
private enum class TvLinkNews { Connected, Synced }

// the tv's side of "your tv and phone are in step": a small card under the avatar when the phone connects and when a sync lands
@Composable
internal fun TvLinkToast(modifier: Modifier = Modifier) {
    val link = LocalTvLink.current
    val state by link.sync.state.collectAsState()
    var news by remember { mutableStateOf<TvLinkNews?>(null) }
    var previous by remember { mutableStateOf(state.phase) }

    LaunchedEffect(state.phase) {
        val before = previous
        previous = state.phase
        news = when {
            // looking or away and now talking: the phone is back
            (before == PhoneSyncState.Phase.Looking || before == PhoneSyncState.Phase.Away) &&
                (state.phase == PhoneSyncState.Phase.Syncing || state.phase == PhoneSyncState.Phase.Synced) -> TvLinkNews.Connected
            before == PhoneSyncState.Phase.Syncing && state.phase == PhoneSyncState.Phase.Synced -> TvLinkNews.Synced
            else -> return@LaunchedEffect
        }
        delay(ShownMs)
        news = null
    }

    AnimatedVisibility(
        visible = news != null,
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(420, easing = TvEase)) { it / 2 },
        exit = fadeOut(tween(220)) + slideOutHorizontally(tween(300)) { it / 2 },
        modifier = modifier,
    ) {
        val shown = news ?: TvLinkNews.Synced
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(TvColors.Pill)
                .border(1.dp, TvColors.PillEdge, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF5BD68A).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.smartphone), contentDescription = null, tint = Color(0xFF5BD68A), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(if (shown == TvLinkNews.Connected) R.string.tv_toast_connected else R.string.tv_toast_synced, state.phoneName),
                    style = TvText.Item,
                )
                Text(
                    stringResource(if (shown == TvLinkNews.Connected) R.string.tv_toast_connected_detail else R.string.tv_toast_synced_detail),
                    style = TvText.Sub,
                )
            }
        }
    }
}

private const val ShownMs = 4_000L
