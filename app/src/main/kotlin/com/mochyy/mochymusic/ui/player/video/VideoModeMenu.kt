package com.mochyy.mochymusic.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoModeWholeVideoKey
import com.mochyy.mochymusic.constants.VideoQualityKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.component.SheetActionCard
import com.mochyy.mochymusic.ui.component.SheetActionCardRow
import com.mochyy.mochymusic.ui.component.SheetHeaderGap
import com.mochyy.mochymusic.ui.player.PressScaleActionButton
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetClose
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetFloating
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetStack
import com.mochyy.mochymusic.ui.player.menu.LyricsOffsetSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsProviderSheet
import com.mochyy.mochymusic.ui.player.menu.LyricsTranslationSheet
import com.mochyy.mochymusic.ui.player.menu.MenuRow
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.player.menu.PlaybackSpeedSheet
import com.mochyy.mochymusic.ui.player.menu.SleepTimerSheet
import com.mochyy.mochymusic.ui.player.menu.rememberMenuSheetStack
import com.mochyy.mochymusic.ui.screens.settings.videoSizeName
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberEnumPreference

// the chevron's menu in the full video mode: a card floating over the video, and every sheet it opens floats the same way
@Composable
internal fun VideoModeMenu(
    session: YouTubeVideoSession?,
    onDismiss: () -> Unit,
    onExitVideoMode: () -> Unit,
    onOpenPane: (VideoPane) -> Unit,
    onAddToPlaylist: () -> Unit,
    onSongInfo: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val (quality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    val (wholeVideo, setWholeVideo) = rememberPreference(VideoModeWholeVideoKey, defaultValue = false)
    var showQuality by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var showOffset by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    // what to do once the card has slid away, so leaving the mode or opening the pane doesn't cut its exit short
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val stack = rememberMenuSheetStack()

    CompositionLocalProvider(
        LocalMenuSheetFloating provides true,
        LocalMenuSheetStack provides stack,
    ) {
        MenuSheetSurface(
            onDismiss = {
                onDismiss()
                afterClose?.invoke()
            },
        ) { _ ->
            val close = LocalMenuSheetClose.current
            fun closeThen(action: () -> Unit) {
                afterClose = action
                close()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 6.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = track.thumbnailUrl?.resize(240, 240),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            color = Color(0xFFB3B3B3),
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    PressScaleActionButton(
                        icon = if (track.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                        tint = Color.White,
                        containerColor = Color.White.copy(alpha = if (track.liked) 0.3f else 0.15f),
                        onClick = connection::toggleLike,
                    )
                }

                Spacer(modifier = Modifier.height(SheetHeaderGap))

                SheetActionCardRow {
                    SheetActionCard(
                        icon = R.drawable.fullscreen_exit,
                        title = stringResource(R.string.video_mode_exit),
                        onClick = { closeThen(onExitVideoMode) },
                        modifier = Modifier.weight(1f),
                    )
                    SheetActionCard(
                        icon = R.drawable.playlist_add,
                        title = stringResource(R.string.add_to_playlist),
                        onClick = { closeThen(onAddToPlaylist) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                MenuRow(
                    icon = R.drawable.info,
                    title = stringResource(R.string.video_mode_song_info),
                    onClick = { closeThen(onSongInfo) },
                )

                Spacer(modifier = Modifier.height(16.dp))
                MenuRow(
                    icon = R.drawable.tune,
                    title = stringResource(R.string.video_quality),
                    onClick = { showQuality = true },
                    trailing = { TrailingValue(qualityLabel(quality, session)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.fullscreen_exit,
                    title = stringResource(R.string.video_mode_whole_video),
                    onClick = { setWholeVideo(!wholeVideo) },
                    trailing = { AppSwitch(checked = wholeVideo, onCheckedChange = setWholeVideo) },
                )

                Spacer(modifier = Modifier.height(16.dp))
                MenuRow(
                    icon = R.drawable.lyrics,
                    title = stringResource(R.string.video_mode_lyrics),
                    onClick = { closeThen { onOpenPane(VideoPane.LYRICS) } },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.queue_music,
                    title = stringResource(R.string.video_mode_queue),
                    onClick = { closeThen { onOpenPane(VideoPane.QUEUE) } },
                )

                Spacer(modifier = Modifier.height(16.dp))
                MenuRow(
                    icon = R.drawable.lyrics,
                    title = stringResource(R.string.video_mode_lyrics_provider),
                    onClick = { showProvider = true },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.fast_forward,
                    title = stringResource(R.string.video_mode_lyrics_offset),
                    onClick = { showOffset = true },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.translate,
                    title = stringResource(R.string.video_mode_lyrics_translation),
                    onClick = { showTranslation = true },
                )

                Spacer(modifier = Modifier.height(16.dp))
                MenuRow(
                    icon = R.drawable.speed,
                    title = stringResource(R.string.video_mode_speed),
                    onClick = { showSpeed = true },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.sleep_timer,
                    title = stringResource(R.string.sleep_timer),
                    onClick = { showSleep = true },
                )
            }
        }

        if (showQuality) VideoQualitySheet(session = session, onDismiss = { showQuality = false })
        if (showProvider) LyricsProviderSheet(onDismiss = { showProvider = false })
        if (showOffset) LyricsOffsetSheet(onDismiss = { showOffset = false })
        if (showTranslation) LyricsTranslationSheet(onDismiss = { showTranslation = false })
        if (showSpeed) PlaybackSpeedSheet(onDismiss = { showSpeed = false })
        if (showSleep) SleepTimerSheet(onDismiss = { showSleep = false })
    }
}

@Composable
private fun TrailingValue(text: String) {
    Text(
        text = text,
        color = Color(0xFF9A9A9A),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

// what the quality reads as: "Auto · 720p" while auto is playing a size, otherwise the size itself
@Composable
internal fun qualityLabel(quality: VideoQuality, session: YouTubeVideoSession?): String {
    if (quality != VideoQuality.AUTO) return videoSizeName(quality.shortSide)
    val playing = session?.playingShortSide ?: 0
    return if (playing > 0) {
        stringResource(R.string.video_quality_auto_now, videoSizeName(playing))
    } else {
        stringResource(R.string.video_quality_auto)
    }
}

// auto, then every size this video really comes in (all of them until it has loaded)
@Composable
internal fun VideoQualitySheet(session: YouTubeVideoSession?, onDismiss: () -> Unit) {
    val (quality, setQuality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    val sizes = session?.sizes.orEmpty()
    val options = remember(sizes) {
        listOf(VideoQuality.AUTO) + VideoQuality.entries.filter {
            it != VideoQuality.AUTO && (sizes.isEmpty() || it.shortSide in sizes)
        }
    }
    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val close = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.video_quality),
                color = Color.White,
                fontFamily = InterFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            for (option in options) {
                QualityRow(
                    title = if (option == VideoQuality.AUTO) {
                        qualityLabel(VideoQuality.AUTO, session)
                    } else {
                        videoSizeName(option.shortSide)
                    },
                    description = when {
                        option == VideoQuality.AUTO -> stringResource(R.string.video_quality_auto_desc)
                        option == quality -> stringResource(R.string.video_quality_fixed_desc)
                        option.shortSide > 1080 -> stringResource(R.string.video_quality_sharp_desc)
                        else -> null
                    },
                    selected = option == quality,
                    onClick = {
                        setQuality(option)
                        close()
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (sizes.isEmpty()) {
                Text(
                    text = stringResource(R.string.video_quality_not_loaded),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun QualityRow(title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
