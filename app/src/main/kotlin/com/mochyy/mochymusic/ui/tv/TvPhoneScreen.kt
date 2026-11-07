// TvPhoneScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.PhoneFiles
import com.mochyy.mochymusic.link.tv.PairTarget
import com.mochyy.mochymusic.link.tv.PhoneSyncState
import com.mochyy.mochymusic.link.tv.TvPairing
import com.mochyy.mochymusic.link.tv.TvPhoneSync
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.link.tv.TvProfiles
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import kotlinx.coroutines.flow.first

// the tv's side of the phone link, handed to every page that shows or starts it
internal class TvLink(val sync: TvPhoneSync, val pairing: TvPairing)

internal val LocalTvLink = staticCompositionLocalOf<TvLink> { error("No TvLink provided") }

@Composable
internal fun TvPhoneScreen(profile: TvProfile) {
    val profiles by TvProfiles.profiles.collectAsState()
    val current = profiles.firstOrNull { it.id == profile.id } ?: profile
    if (current.phone != null) TvPhoneLinked(current) else TvPhonePairing(current)
}

// "hello again": the phone's state in a big card on the dotted panel, and what came over from it on the right
@Composable
private fun TvPhoneLinked(profile: TvProfile, library: LibraryHomeViewModel = hiltViewModel()) {
    val link = LocalTvLink.current
    val actions = LocalTvActions.current
    val state by link.sync.state.collectAsState()
    val files by PhoneFiles.files.collectAsState()
    val liked by library.likedSongs.collectAsState()
    val playlists by library.playlists.collectAsState()
    val syncFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { syncFocus.requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .weight(0.49f)
                .clip(RoundedCornerShape(bottomEnd = 64.dp))
                .background(Color(0xFF141414))
                .tvDots(color = Color(0xFF2A2A2A)),
        ) {
            Column(Modifier.padding(start = TvSpace.Start, top = TvSpace.Top, end = 28.dp)) {
                Text(stringResource(R.string.tv_hello_again, profile.name), style = TvText.PageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(48.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF6A6A6A).copy(alpha = 0.6f))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.smartphone), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(profile.phone?.name.orEmpty(), style = TvText.Item.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(syncLine(state), style = TvText.Sub.copy(color = Color(0xFFE0E0E0), fontSize = 12.sp))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvPillButton(stringResource(R.string.tv_sync_now), onClick = link.sync::syncNow, icon = R.drawable.sync, bright = true, modifier = Modifier.focusRequester(syncFocus))
                        if (files.isNotEmpty()) {
                            TvPillButton(stringResource(R.string.tv_copy_all), onClick = { link.sync.transfer(files.keys - link.sync.onTv()) }, icon = R.drawable.download)
                        }
                        TvPillButton(stringResource(R.string.tv_unlink), onClick = link.sync::unlink)
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(0.51f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 54.dp, end = TvSpace.End, top = TvSpace.Top - 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvPhoneCard(stringResource(R.string.tv_from_phone, profile.phone?.name.orEmpty()), null, R.drawable.smartphone, slim = true) { link.sync.syncNow() }
            TvPhoneCard(stringResource(R.string.liked_songs), pluralStringResource(R.plurals.tv_song_count, liked.size, liked.size), R.drawable.favorite) {
                actions.navController.navigate("${TvRoutes.Songs}/liked")
            }
            TvPhoneCard(stringResource(R.string.tv_on_your_phone), pluralStringResource(R.plurals.tv_song_count, files.size, files.size), R.drawable.offline) {
                actions.navController.navigate("${TvRoutes.Songs}/phone")
            }
            TvPhoneCard(stringResource(R.string.playlists), playlists.size.toString(), R.drawable.queue_music) {
                actions.navController.navigate("${TvRoutes.Grid}/playlists")
            }
            TvPhoneCard(stringResource(R.string.history), null, R.drawable.history) {
                actions.navController.navigate("${TvRoutes.Songs}/history")
            }
        }
    }
}

@Composable
private fun syncLine(state: PhoneSyncState): String = when (state.phase) {
    PhoneSyncState.Phase.Looking -> stringResource(R.string.tv_sync_looking)
    PhoneSyncState.Phase.Syncing -> stringResource(R.string.tv_sync_syncing)
    PhoneSyncState.Phase.Away -> stringResource(R.string.tv_sync_away)
    PhoneSyncState.Phase.Unlinked -> stringResource(R.string.tv_sync_unlinked)
    PhoneSyncState.Phase.Failed -> stringResource(R.string.tv_sync_failed)
    PhoneSyncState.Phase.NoPhone -> stringResource(R.string.tv_sync_no_phone)
    PhoneSyncState.Phase.Synced -> if (state.transferring > 0) {
        pluralStringResource(R.plurals.tv_sync_copying, state.transferring, state.transferring)
    } else {
        val minutes = ((System.currentTimeMillis() - state.lastSyncAt) / 60_000L).toInt().coerceAtLeast(0)
        if (minutes < 1) stringResource(R.string.tv_sync_just_now) else pluralStringResource(R.plurals.tv_sync_minutes_ago, minutes, minutes)
    }
}

@Composable
private fun TvPhoneCard(title: String, count: String?, icon: Int, slim: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TvColors.Card)
            .tvClickable(shape = shape, focusedScale = 1.03f, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (slim) 14.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = Color(0xFFD9D9D9), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = TvText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!slim && count != null) {
            Spacer(Modifier.height(18.dp))
            Text(count, style = TvText.PageTitle.copy(fontSize = 22.sp))
        }
    }
}

// "sync from phone": the code to type and where to type it, while the tv waits on the wi-fi for this profile's phone
@Composable
private fun TvPhonePairing(profile: TvProfile) {
    val link = LocalTvLink.current
    DisposableEffect(profile.id) {
        link.pairing.open(PairTarget.Profile(profile.id))
        onDispose { link.pairing.close() }
    }
    LaunchedEffect(profile.id) {
        link.pairing.paired.first { it.id == profile.id }
        link.sync.resume()
    }
    Column(Modifier.fillMaxSize().padding(start = TvSpace.Start, top = TvSpace.Top, end = TvSpace.End)) {
        Text(stringResource(R.string.tv_sync_from_phone), style = TvText.PageTitle)
        Text(stringResource(R.string.tv_same_wifi), style = TvText.Body.copy(color = Color(0xFFA0A0A0)), modifier = Modifier.padding(start = 3.dp))
        Spacer(Modifier.height(36.dp))
        TvPairingSteps(link.pairing)
    }
}

// the code in big digits and the three steps on the phone, used here and in the welcome
@Composable
internal fun TvPairingSteps(pairing: TvPairing, modifier: Modifier = Modifier) {
    val code by pairing.code.collectAsState()
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            code?.code.orEmpty().forEach { digit ->
                Box(
                    Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TvColors.Card)
                        .border(1.dp, TvColors.PillEdge, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(digit.toString(), style = TvText.PageTitle.copy(fontSize = 28.sp))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        listOf(
            stringResource(R.string.tv_pair_step_open),
            stringResource(R.string.tv_pair_step_pick, code?.tvName.orEmpty()),
            stringResource(R.string.tv_pair_step_code),
        ).forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(TvColors.CardRaised), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = TvText.Sub.copy(color = Color.White))
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = TvText.Body.copy(fontSize = 14.sp, color = Color(0xFFCFCFCF)))
            }
        }
    }
}
