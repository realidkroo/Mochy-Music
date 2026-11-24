// the page blocks that show something live: the cover and songs of what the page took over, buttons that play them or go back to mochy's page, and the module's player with its sub-styles for an editor page

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.PageControl
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.player.styles.AlternatePlayerStyle
import com.mochyy.mochymusic.ui.player.styles.LocalModuleStyle
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.utils.rememberEnumPreference
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
internal fun PageLiveBlock(block: PageBlock, page: PageContext) {
    when (block) {
        is PageBlock.Cover -> PageCover(block, page.subject.cover)
        is PageBlock.Control -> PageControlButton(block, page)
        is PageBlock.Player -> PagePlayer(block, page)
        PageBlock.Styles -> PageStyles(page)
        else -> Unit
    }
}

@Composable
private fun PageCover(block: PageBlock.Cover, cover: String?) {
    cover ?: return
    val shape = if (block.circle) CircleShape else RoundedCornerShape(block.corner)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = (if (block.circle) Modifier.size(block.height) else Modifier.fillMaxWidth().height(block.height)).clip(shape).background(LabColors.Card),
        )
    }
}

/** the songs, a screenful first and more on a tap, so a long playlist never builds every row on the frame the page opens */
@Composable
private fun PageSongs(block: PageBlock.Songs, subject: PageSubject) {
    val connection = LocalPlayerConnection.current
    val playing by (connection?.mediaMetadata ?: NoSong).collectAsState()
    val songs = subject.songs.take(block.limit)
    var shown by rememberSaveable { mutableIntStateOf(FirstRows) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        songs.take(shown).forEachIndexed { index, song ->
            SongRow(index, song, block, active = song.id == playing?.id) {
                connection?.playQueue(ListQueue(title = subject.title.ifBlank { null }, items = songs.map { it.toMediaItem() }, startIndex = index))
            }
        }
        if (songs.size > shown) {
            PillButton(text = stringResource(R.string.module_page_more_songs), filled = false, onClick = { shown += MoreRows })
        }
    }
}

@Composable
private fun SongRow(index: Int, song: MediaMetadata, block: PageBlock.Songs, active: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LabColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (block.numbers) {
            Text(
                (index + 1).toString(), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (active) accent else LabColors.Secondary, modifier = Modifier.width(28.dp),
            )
        }
        if (block.covers) {
            AsyncImage(
                model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(block.corner)).background(LabColors.Row),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (active) accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artists.joinToString(", ") { it.name }, fontFamily = InterFontFamily, fontSize = 12.sp,
                color = LabColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.duration > 0) {
            Text(makeTimeString(song.duration * 1000L), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary)
        }
    }
}

/** play, shuffle, mochy's own page, or the module's player; a button with nothing to do here isn't shown */
@Composable
private fun PageControlButton(block: PageBlock.Control, page: PageContext) {
    val connection = LocalPlayerConnection.current
    var playerStyle by rememberEnumPreference(PlayerStyleKey, PlayerStyle.FLAGSHIP)
    val songs = page.subject.songs
    val label = block.text.ifBlank {
        stringResource(
            when (block.kind) {
                PageControl.Play -> R.string.module_page_play
                PageControl.Shuffle -> R.string.module_page_shuffle
                PageControl.Original -> R.string.module_page_original
                PageControl.UsePlayer -> R.string.module_page_use_player
            },
        )
    }
    val action: (() -> Unit)? = when (block.kind) {
        PageControl.Play -> songs.takeIf { it.isNotEmpty() && connection != null }?.let { list ->
            { connection?.playQueue(ListQueue(title = page.subject.title.ifBlank { null }, items = list.map { it.toMediaItem() })) }
        }
        PageControl.Shuffle -> songs.takeIf { it.isNotEmpty() && connection != null }?.let { list ->
            {
                connection?.player?.shuffleModeEnabled = true
                connection?.playQueue(ListQueue(title = page.subject.title.ifBlank { null }, items = list.map { it.toMediaItem() }, startIndex = list.indices.random()))
            }
        }
        PageControl.Original -> page.onOriginal
        PageControl.UsePlayer -> ActiveSkin.styles.firstOrNull { it.skin.moduleId == page.entry.id }?.let {
            {
                ActiveSkin.pick("${page.entry.id}/")
                playerStyle = PlayerStyle.MODULE
            }
        }
    }
    action ?: return
    PillButton(text = label, filled = block.filled, onClick = action)
}

/** the module's player as it looks right now, the whole screen's layout shrunk into the box, the way the style picker shows it */
@Composable
private fun PagePlayer(block: PageBlock.Player, page: PageContext) {
    val mine = ActiveSkin.styles.filter { it.skin.moduleId == page.entry.id }
    val style = ActiveSkin.style?.takeIf { it.skin.moduleId == page.entry.id } ?: mine.firstOrNull()
    if (style == null) {
        Text(
            stringResource(R.string.module_page_no_player), fontFamily = InterFontFamily, fontSize = 13.sp,
            lineHeight = 18.sp, color = LabColors.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val actions = remember { StyleActions(onOtherMenu = {}, onEditPlayer = {}) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(block.height),
        contentAlignment = Alignment.Center,
    ) {
        val scale = (maxHeight / screenHeight).coerceAtMost(maxWidth / screenWidth)
        Box(
            Modifier
                .size(screenWidth * scale, screenHeight * scale)
                .clip(RoundedCornerShape(block.corner))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // laid out at the screen's own size, then shrunk, so the preview is the real layout and not a squeezed one
            Box(Modifier.requiredSize(screenWidth, screenHeight).graphicsLayer { scaleX = scale; scaleY = scale }) {
                CompositionLocalProvider(LocalModuleStyle provides style) {
                    AlternatePlayerStyle(style = PlayerStyle.MODULE, actions = actions, preview = true, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** the module's players and their sub-styles as chips; tapping one makes it the player */
@Composable
private fun PageStyles(page: PageContext) {
    val mine = ActiveSkin.styles.filter { it.skin.moduleId == page.entry.id }
    if (mine.isEmpty()) return
    var playerStyle by rememberEnumPreference(PlayerStyleKey, PlayerStyle.FLAGSHIP)
    val picked = ActiveSkin.style?.key?.takeIf { playerStyle == PlayerStyle.MODULE }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        mine.forEach { style ->
            val lit = style.key == picked
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (lit) Color.White else LabColors.Card)
                    .clickable {
                        ActiveSkin.pick(style.key)
                        playerStyle = PlayerStyle.MODULE
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(style.title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (lit) Color.Black else Color.White, maxLines = 1)
            }
        }
    }
}

private val NoSong = MutableStateFlow<MediaMetadata?>(null)
private const val FirstRows = 30
private const val MoreRows = 50
