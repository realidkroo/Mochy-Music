// TvMochyScreen.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.TvControl
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.link.tv.TvProfiles
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.screens.donate.showDonateSheet
import com.mochyy.mochymusic.ui.screens.update.rememberUpdateState
import com.mochyy.mochymusic.ui.screens.update.showUpdateSheet

// one tile on the mochy page: a place to go, what it is, and a line on how it stands
@Immutable
private data class TvMochyTile(val key: String, val title: String, val detail: String, val icon: Int, val wide: Boolean = false, val onClick: () -> Unit)

// the rail's mochy page: only what a tv needs, your phone, who's listening and whether the phone may drive the tv
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvMochyScreen(profile: TvProfile) {
    val actions = LocalTvActions.current
    val profiles by TvProfiles.profiles.collectAsState()
    val current = profiles.firstOrNull { it.id == profile.id } ?: profile
    val phoneControl by TvControl.allowed.collectAsState()
    val popup = LocalZoomOutOverlayState.current
    val updateState by rememberUpdateState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val tiles = listOf(
        TvMochyTile(
            "phone",
            stringResource(R.string.tv_your_phone),
            current.phone?.name ?: stringResource(R.string.tv_mochy_link_phone),
            R.drawable.smartphone,
            wide = true,
        ) { actions.navController.navigate(TvRoutes.Phone) },
        TvMochyTile("profiles", stringResource(R.string.tv_whos_listening), current.name, R.drawable.person) { actions.switchProfile() },
        TvMochyTile(
            "control",
            stringResource(R.string.tv_mochy_phone_control),
            stringResource(if (phoneControl) R.string.tv_on else R.string.tv_off),
            R.drawable.sync,
        ) { TvControl.setAllowed(!phoneControl) },
        TvMochyTile("updates", stringResource(R.string.tv_mochy_updates), stringResource(R.string.tv_mochy_version, BuildConfig.VERSION_NAME), R.drawable.sync) {
            popup.showUpdateSheet(updateState)
        },
        TvMochyTile("support", stringResource(R.string.tv_mochy_support), stringResource(R.string.tv_mochy_support_detail), R.drawable.favorite) {
            popup.showDonateSheet()
        },
    )

    Row(Modifier.fillMaxSize().padding(start = TvSpace.Start, end = TvSpace.End)) {
        Column(Modifier.width(250.dp).padding(top = TvSpace.Top)) {
            Text(stringResource(R.string.tv_mochy), style = TvText.PageTitle)
            Text(stringResource(R.string.tv_mochy_version, BuildConfig.VERSION_NAME), style = TvText.Body.copy(fontSize = 13.sp))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvColumnPivot) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = TvSpace.Top + 6.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(tiles, key = { _, tile -> tile.key }, span = { _, tile -> GridItemSpan(if (tile.wide) 2 else 1) }) { index, tile ->
                    TvMochyTileCard(tile, if (index == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun TvMochyTileCard(tile: TvMochyTile, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(if (tile.wide) 104.dp else 92.dp)
            .tvClickable(shape = shape, focusedScale = 1.03f, onClick = tile.onClick)
            .clip(shape)
            .background(TvColors.Card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(TvColors.CardRaised), contentAlignment = Alignment.Center) {
                Icon(painterResource(tile.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(tile.title, style = TvText.Item.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            tile.detail,
            style = TvText.Sub.copy(fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        )
    }
}
