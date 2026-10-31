// SongSharePages.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.share.BackdropOf
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.ShareMuted
import com.mochyy.mochymusic.ui.player.share.ShareStyle
import com.mochyy.mochymusic.ui.player.share.ShareTitle
import com.mochyy.mochymusic.ui.player.share.shake
import com.mochyy.mochymusic.ui.player.share.showLinePicker
import com.mochyy.mochymusic.ui.player.share.song.export.copySongLink
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize

// from a menu: the song on a card, its lyrics, or just the link
internal fun PopupSheetState.showKindPage(session: SongShareSession): PopupSheetHandle {
    val popup = this
    return showWithHandle(buttonBar = { GoBackButton(popup::dismiss) }) {
        val context = LocalContext.current
        ShareTitle(stringResource(R.string.song_share_music_title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            ChoiceTile(
                title = stringResource(R.string.song_share_music_only),
                subtitle = stringResource(R.string.song_share_music_only_hint),
                onClick = {
                    session.kind = SongShareKind.MusicOnly
                    popup.showEditor(session)
                },
            ) { TilePicture(session, SongShareKind.MusicOnly) }
            val lyrics = session.lyrics
            ChoiceTile(
                title = stringResource(R.string.song_share_lyrics),
                subtitle = stringResource(
                    when {
                        lyrics is Fetched.Looking -> R.string.song_share_lyrics_looking
                        lyrics is Fetched.Missing -> R.string.song_share_lyrics_missing
                        session.lyricsSynced -> R.string.song_share_lyrics_synced
                        else -> R.string.song_share_lyrics_unsynced
                    },
                ),
                enabled = lyrics is Fetched.Found,
                onClick = { session.startLyrics(popup) },
            ) { TilePicture(session, SongShareKind.Lyrics, ShareStyle.Card) }
            ChoiceTile(
                title = stringResource(R.string.song_share_type_link),
                subtitle = stringResource(if (session.link != null) R.string.song_share_type_link_hint else R.string.song_share_type_link_missing),
                enabled = session.link != null,
                onClick = {
                    if (copySongLink(context, session)) {
                        TopToaster.show(context.getString(R.string.song_share_link_copied), thumbnail = session.song.thumbnailUrl)
                    }
                    popup.dismissAll()
                },
            ) {
                Icon(
                    painterResource(R.drawable.link),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
        }
    }
}

// lyrics from a menu: the line picker opens on the line at the share's moment, like holding it on the lyrics page
private fun SongShareSession.startLyrics(popup: PopupSheetState) {
    val sung = lyrics.found?.filter { it.text.isNotBlank() }.orEmpty()
    if (sung.isEmpty()) return
    val chosen = pick ?: LyricsShareSession(
        song = share,
        lines = sung.map { it.text.trim() },
        entries = sung,
        held = LyricsUtils.findCurrentLineIndex(sung, windowStartMs + lyricsOffsetMs).coerceIn(0, sung.lastIndex),
        flightSource = null,
    ).also { fresh ->
        fresh.palette = palette
        fresh.onLinesPicked = { next -> choseLines(next) }
    }
    pick = chosen
    kind = SongShareKind.Lyrics
    popup.showLinePicker(chosen)
}

// after the lines: on a card, or the cinematic words straight on the picture; a style that can't hold that many words shakes
internal fun PopupSheetState.showStylePage(session: SongShareSession) {
    val popup = this
    show(buttonBar = { GoBackButton(popup::dismiss) }) {
        val pick = session.pick ?: return@show
        ShareTitle(stringResource(R.string.song_share_style_title), stringResource(R.string.song_share_style_subtitle))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            ShareStyle.entries.forEach { style ->
                val fits = pick.fits(style)
                ChoiceTile(
                    title = stringResource(if (style == ShareStyle.Card) R.string.song_share_style_card else R.string.song_share_style_cinematic),
                    subtitle = if (fits) stringResource(R.string.song_share_style_fits, style.maxWords) else stringResource(R.string.song_share_style_too_many),
                    enabled = fits,
                    selected = pick.style == style,
                    onClick = {
                        pick.style = style
                        popup.showEditor(session)
                    },
                ) { TilePicture(session, SongShareKind.Lyrics, style) }
            }
        }
    }
}

// "Edit background" asks this first: a moving background, or a still one
internal fun PopupSheetState.showBackgroundType(session: SongShareSession) {
    val popup = this
    lateinit var typePage: PopupSheetHandle
    typePage = showWithHandle(buttonBar = { GoBackButton(popup::dismiss) }) {
        ShareTitle(stringResource(R.string.song_share_type_title), stringResource(R.string.song_share_type_subtitle))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            ChoiceTile(
                title = stringResource(R.string.song_share_type_video),
                subtitle = stringResource(R.string.song_share_type_video_hint),
                selected = session.hasVideoBackground,
                onClick = { popup.showBackgroundPager(session, video = true, typePage) },
            ) {
                AsyncImage(
                    model = session.song.thumbnailUrl?.resize(544, 544),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                )
            }
            ChoiceTile(
                title = stringResource(R.string.song_share_type_still),
                subtitle = stringResource(R.string.song_share_type_still_hint),
                selected = !session.hasVideoBackground,
                onClick = { popup.showBackgroundPager(session, video = false, typePage) },
            ) { BackdropOf(session.lastStill, session.song.thumbnailUrl, session.palette, animate = false) }
        }
    }
}

@Composable
internal fun GoBackButton(onClick: () -> Unit) {
    SheetPillButton(text = stringResource(R.string.song_share_go_back), onClick = onClick)
}

// the picture as it stands, small and still, cropped to fill a tile
@Composable
private fun TilePicture(session: SongShareSession, kind: SongShareKind, style: ShareStyle? = null) {
    SharePictureFit(Modifier.fillMaxSize(), corner = 0.dp, fill = true) {
        SongSharePicture(
            session = session,
            positionMs = { session.windowStartMs },
            layers = ShareLayers.Everything,
            moving = false,
            kind = kind,
            style = style,
        )
    }
}

// a big tile like the mock: a picture peeking up from the bottom on the left, the choice on the right; one that can't be picked shakes
@Composable
internal fun ChoiceTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    preview: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(136.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
                translationX = shake.value
            }
            .clip(shape)
            .background(MenuRowSurface)
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White.copy(alpha = 0.7f) else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null) {
                if (enabled) {
                    onClick()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.shake(shake)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .padding(start = 18.dp, top = 18.dp)
                .width(118.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            content = preview,
        )
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 16.dp)) {
            Text(
                title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                letterSpacing = (-0.7).sp,
                color = Color.White,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ShareMuted)
            }
        }
    }
}
