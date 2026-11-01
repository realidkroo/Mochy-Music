// a page a look module brings, built from its blocks: words, pictures, web links, and songs, playlists, albums and artists to open

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.music.innertube.models.WatchEndpoint
import java.net.URLEncoder

const val ModulePageRoutePattern = "module_page/{id}/{page}"
fun modulePageRoute(id: String, page: String) = "module_page/${Uri.encode(id)}/${Uri.encode(page)}"

@Composable
fun ModulePageScreen(navController: NavController, id: String, pageId: String, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == id }
    val skin = remember(entry) { viewModel.skinOf(id) }
    // a page only shows while its module is on, the same as everything else a module adds
    val page = skin?.pages?.firstOrNull { it.id == pageId }?.takeIf { entry?.enabled == true }

    SubSettingsScaffold(
        title = page?.title ?: entry?.module?.info?.name.orEmpty(),
        backLabel = entry?.module?.info?.name,
        onBack = { navController.navigateUp() },
    ) {
        if (skin == null || page == null) {
            Text(
                stringResource(R.string.module_page_missing),
                fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = LabColors.Secondary,
            )
            return@SubSettingsScaffold
        }
        page.blocks.forEach { block ->
            PageBlockView(block, skin, navController)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun PageBlockView(block: PageBlock, skin: ModuleSkin, navController: NavController) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    when (block) {
        is PageBlock.Heading -> Text(
            block.text, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        is PageBlock.Paragraph -> Text(
            block.text, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = LabColors.Secondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        is PageBlock.Picture -> AsyncImage(
            model = skin.resolve(block.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(block.height).clip(RoundedCornerShape(block.corner)),
        )
        is PageBlock.Button -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .clickable { context.openDevUrl(block.link) },
            contentAlignment = Alignment.Center,
        ) {
            Text(block.text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Black)
        }
        PageBlock.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        is PageBlock.Space -> Spacer(Modifier.height(block.size))
        is PageBlock.Song -> PageRow(R.drawable.play, block.title, block.artist) {
            connection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = block.id)))
        }
        is PageBlock.Playlist -> PageRow(R.drawable.queue_music, block.title, block.subtitle) {
            navController.navigate("online_playlist/${block.id}")
        }
        is PageBlock.Album -> PageRow(R.drawable.album, block.title, block.subtitle) {
            navController.navigate("album/${block.id}")
        }
        is PageBlock.Artist -> PageRow(R.drawable.person, block.title, null) {
            navController.navigate("artist/${block.id}")
        }
        is PageBlock.Search -> PageRow(R.drawable.search_filled, block.title, block.query.takeIf { it != block.title }) {
            navController.navigate("search/${URLEncoder.encode(block.query, "UTF-8")}")
        }
    }
}

/** a row to tap: something to play or open, with what it is on the left */
@Composable
private fun PageRow(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(LabColors.Row),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(painterResource(R.drawable.navigate_next), null, tint = LabColors.Secondary, modifier = Modifier.size(18.dp))
    }
}
