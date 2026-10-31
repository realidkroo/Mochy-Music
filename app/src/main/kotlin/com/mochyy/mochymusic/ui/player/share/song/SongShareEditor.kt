// SongShareEditor.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.share.EditOutlines
import com.mochyy.mochymusic.ui.player.share.ShareHeight
import com.mochyy.mochymusic.ui.player.share.ShareMuted
import com.mochyy.mochymusic.ui.player.share.ShareRegions
import com.mochyy.mochymusic.ui.player.share.ShareTitle
import com.mochyy.mochymusic.ui.player.share.ShareWidth
import com.mochyy.mochymusic.ui.player.share.showWordEditor
import com.mochyy.mochymusic.ui.player.share.song.export.ShareApp
import com.mochyy.mochymusic.ui.player.share.song.export.takesSticker
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt

// the picture, the background that moves under your fingers, and, for a video, the part of the song it plays
internal fun PopupSheetState.showEditor(session: SongShareSession) {
    val popup = this
    show(buttonBar = { SheetPillButton(text = stringResource(R.string.song_share_share_button), onClick = { popup.showSendPage(session) }) }) {
        EditorContent(session, popup)
    }
}

@Composable
private fun ColumnScope.EditorContent(session: SongShareSession, popup: PopupSheetState) {
    val live = popup.rememberIsFront()
    PlaysThePreview(session, front = live)
    val pick = session.pick
    val cinematic = session.cinematic && pick != null
    // a video background plays a part of the song, so it gets the timeline; a still one is a picture
    val timeline = session.hasVideoBackground
    ShareTitle(
        stringResource(R.string.song_share_editor_title),
        stringResource(if (cinematic) R.string.song_share_editor_hint_words else R.string.song_share_editor_hint),
    )
    val regions = remember { ShareRegions() }
    val height = (LocalConfiguration.current.screenHeightDp * if (timeline) 0.47f else 0.58f).dp
    Box(Modifier.fillMaxWidth().height(height)) {
        SongSharePreview(
            session = session,
            live = live,
            modifier = Modifier.fillMaxSize(),
            onCardTap = if (cinematic) null else ({ popup.showCardStyle(session) }),
            regions = if (cinematic) regions else null,
            overlay = if (cinematic && pick != null) {
                {
                    EditOutlines(
                        regions = regions,
                        headerBelow = pick.headerBelow,
                        onWords = { popup.showWordEditor(pick) },
                        onMoveHeader = { pick.headerBelow = !pick.headerBelow },
                    )
                }
            } else {
                null
            },
        )
        EditBackgroundPill(Modifier.align(Alignment.TopEnd)) { popup.showBackgroundType(session) }
    }
    if (timeline) {
        Spacer(Modifier.height(16.dp))
        SongTimeline(session, live)
    }
    Spacer(Modifier.height(8.dp))
}

// the song plays while the editor is up, starts again each time it comes back to the front (the share page leaves it paused after handing off), and stops when you step back out of it
@Composable
private fun PlaysThePreview(session: SongShareSession, front: Boolean) {
    LaunchedEffect(front) { if (front) session.previewWanted = true }
    DisposableEffect(session) { onDispose { session.previewWanted = false } }
}

@Composable
private fun SongTimeline(session: SongShareSession, live: Boolean) {
    val lineStarts = remember(session.lyrics) { session.lyrics.found.orEmpty().map { it.time - session.lyricsOffsetMs } }
    ShareTimeline(
        totalMs = session.durationMs.coerceAtLeast(session.windowLengthMs),
        startMs = session.windowStartMs,
        lengthMs = session.windowLengthMs,
        onSettled = { start, length ->
            session.windowTouched = true
            session.setWindow(start, length)
        },
        playheadMs = { if (live) session.playheadMs() else null },
        marks = lineStarts,
        drawStrip = { timeToX -> drawSongStrip(lineStarts, session.durationMs, timeToX) },
    )
}

@Composable
private fun EditBackgroundPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        stringResource(R.string.song_share_edit_background),
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = (-0.2).sp,
        color = Color.White,
        modifier = modifier
            .padding(12.dp)
            .pressBounce(interaction)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// "Customize card": the card up close, its colour, how see-through it is, and how much it blurs what's behind it
internal fun PopupSheetState.showCardStyle(session: SongShareSession) {
    val popup = this
    show(buttonBar = { SheetPillButton(text = stringResource(R.string.song_share_ok), onClick = popup::dismiss) }) {
        val context = LocalContext.current
        val live = popup.rememberIsFront()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ShareTitle(stringResource(R.string.song_share_card_title))
            CardCloseUp(session, live)
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.song_share_card_colour))
            Spacer(Modifier.height(10.dp))
            CardColours(session)
            Spacer(Modifier.height(18.dp))
            LabCard(padding = 10.dp) {
                LabSlider(
                    label = stringResource(R.string.song_share_card_opacity),
                    valueText = "${(session.cardOpacity * 100).roundToInt()}%",
                    value = session.cardOpacity,
                    onValueChange = { session.cardOpacity = it },
                    valueRange = 0.2f..1f,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                LabSlider(
                    label = stringResource(R.string.song_share_card_blur),
                    valueText = "${(session.cardBlur * 100).roundToInt()}%",
                    value = session.cardBlur,
                    onValueChange = { session.cardBlur = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            // only true when the stories path is set up; without it every app gets the finished picture, blur and all
            if (ShareApp.entries.any { it.takesSticker(context) }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.song_share_card_blur_note),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = ShareMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.4).sp, color = Color.White)
}

// the card cut out of the real picture, background and blur included, so every change shows as it will look
@Composable
private fun CardCloseUp(session: SongShareSession, live: Boolean) {
    val bounds = session.cardBounds ?: return
    val resting = remember(session) { { session.windowStartMs } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        val cardWidth = ShareWidth * bounds.width
        val cardHeight = ShareHeight * bounds.height
        val k = minOf(maxWidth / cardWidth, maxHeight / cardHeight, 1.5f)
        val density = LocalDensity.current
        Box(Modifier.size(cardWidth * k, cardHeight * k).clip(RoundedCornerShape(cardCornerOf(session.kind) * k))) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * k, fontScale = 1f)) {
                SongSharePicture(
                    session = session,
                    positionMs = if (live) session.playheadMs else resting,
                    layers = ShareLayers.Everything,
                    moving = live && session.isVideo,
                    video = { ShareVideoLayer(session, playing = live && session.previewPlays) },
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset(x = -ShareWidth * bounds.left, y = -ShareHeight * bounds.top),
                )
            }
        }
    }
}

// the cover's colours, then near-black and white; white is left out under lyrics, which are always drawn white
@Composable
private fun CardColours(session: SongShareSession) {
    val colours = remember(session.palette, session.kind) {
        session.palette + listOfNotNull(Color(0xFF141414), Color.White.takeIf { session.kind == SongShareKind.MusicOnly })
    }
    val picked = session.cardTint
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        colours.forEach { colour ->
            val on = colour.toArgb() == picked.toArgb()
            Spacer(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colour)
                    .border(if (on) 3.dp else 1.dp, Color.White.copy(alpha = if (on) 0.95f else 0.18f), CircleShape)
                    .clickable { session.cardColor = colour },
            )
        }
    }
}
