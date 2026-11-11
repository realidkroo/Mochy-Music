// SongEditContent.kt

package com.mochyy.mochymusic.ui.menu.songedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.AudioLabStore
import com.mochyy.mochymusic.eq.EqPresets
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.songedit.SongEdits
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.component.bleedHorizontally
import com.mochyy.mochymusic.ui.player.menu.LineSlider
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel
import java.util.Locale
import kotlin.math.roundToInt

// the popup sheet pads its content by this much, the cover row and the artist row reach past it to the edges
private val SheetSidePadding = 20.dp
private val SectionGap = 22.dp
private val LabelGap = 10.dp

private const val MaxTitleLength = 200
private const val OffsetStepMs = 100
private const val SpeedStep = 0.05f

// the body of the song edit sheet, top to bottom as in the mock; scrolls on its own while ok stays pinned under it
@Composable
internal fun SongEditContent(state: SongEditState, audioLab: AudioLabStore) {
    var showVideoPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_song),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        SongCoverCarousel(state = state, modifier = Modifier.bleedHorizontally(SheetSidePadding))

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_name))
        Spacer(Modifier.height(LabelGap))
        SheetTextField(
            value = state.title,
            onValueChange = { state.title = it.take(MaxTitleLength) },
            placeholder = stringResource(R.string.song_edit_name_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(SectionGap))
        LyricsSection(state)

        Spacer(Modifier.height(SectionGap))
        OffsetSection(state)

        Spacer(Modifier.height(SectionGap))
        SpeedSection(state)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_eq))
        Spacer(Modifier.height(LabelGap))
        EqPresetSelect(state, audioLab)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_artist))
        Spacer(Modifier.height(LabelGap))
        SongArtistPicker(state = state, sidePadding = SheetSidePadding)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_more))
        Spacer(Modifier.height(LabelGap))
        MoreDetails(state)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_video))
        Spacer(Modifier.height(LabelGap))
        VideoRow(songId = state.songId, onClick = { showVideoPicker = true })
    }

    if (showVideoPicker) {
        VideoPickerSheet(
            songId = state.songId,
            title = state.title,
            artist = state.artists.joinToString { it.name },
            onDismiss = { showVideoPicker = false },
        )
    }
}

// "custom lyrics": a dashed box to bring in a lyrics file, then a choice of what the lyrics sources found
@Composable
private fun LyricsSection(state: SongEditState) {
    SheetLabel(stringResource(R.string.song_edit_lyrics))
    Spacer(Modifier.height(LabelGap))
    LyricsFileBox(
        picked = state.pickedLyrics,
        onPicked = { state.pickedLyrics = it },
        onRemove = { state.pickedLyrics = null },
    )

    // a file of your own wins, so the sources only matter without one
    if (state.pickedLyrics == null) {
        Spacer(Modifier.height(10.dp))
        LyricsSourceSelect(state)
    }
}

@Composable
private fun LyricsSourceSelect(state: SongEditState) {
    val viewModel: LyricsMenuViewModel = hiltViewModel()
    val sources by viewModel.sources.collectAsState()
    LaunchedEffect(state.songId) { viewModel.checkSources(state.metadata) }

    val found = remember(sources) {
        sources.values
            .filterIsInstance<LyricsMenuViewModel.SourceState.Available>()
            .map { it.candidate }
            .sortedBy { it.order }
    }
    val word = stringResource(R.string.song_edit_lyrics_word)
    val line = stringResource(R.string.song_edit_lyrics_line)
    val plain = stringResource(R.string.song_edit_lyrics_plain)
    val keep = stringResource(R.string.song_edit_lyrics_keep)
    val format = stringResource(R.string.song_edit_lyrics_source)
    val options = listOf(keep) + found.map { candidate ->
        val kind = when (candidate.sync) {
            LyricsUtils.SyncKind.WORD -> word
            LyricsUtils.SyncKind.LINE -> line
            else -> plain
        }
        String.format(format, LyricsProviderRegistry.getDisplayName(candidate.providerName), kind)
    }
    val selected = state.lyricsSource?.let { pick -> found.indexOfFirst { it.providerName == pick.providerName } + 1 } ?: 0

    LabSelect(
        options = options,
        selectedIndex = selected.coerceAtLeast(0),
        onSelect = { index -> state.lyricsSource = found.getOrNull(index - 1) },
        contentDescription = stringResource(R.string.song_edit_lyrics_source_desc),
    )
}

// "set lyric offset": the pill on the right shows it and puts it back to zero, the line under it slides from two minutes early to three late
@Composable
private fun OffsetSection(state: SongEditState) {
    val seconds = String.format(Locale.getDefault(), "%+.1f", state.lyricsOffsetMs / 1000f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.song_edit_offset), modifier = Modifier.weight(1f))
        ValuePill(
            text = stringResource(R.string.song_edit_offset_value, seconds),
            description = stringResource(R.string.song_edit_offset_reset),
            onClick = { state.lyricsOffsetMs = 0 },
        )
    }
    Spacer(Modifier.height(6.dp))
    LineSlider(
        value = (state.lyricsOffsetMs - OffsetMinMs).toFloat() / (OffsetMaxMs - OffsetMinMs),
        onValueChange = { fraction ->
            val raw = OffsetMinMs + fraction * (OffsetMaxMs - OffsetMinMs)
            state.lyricsOffsetMs = (raw / OffsetStepMs).roundToInt() * OffsetStepMs
        },
    )
    RangeLabels(stringResource(R.string.song_edit_offset_min), stringResource(R.string.song_edit_offset_max))
}

// "set playback speed": saved with the song, so it plays this fast every time; the pill puts it back to normal
@Composable
private fun SpeedSection(state: SongEditState) {
    val value = String.format(Locale.getDefault(), "%.2f", state.speed)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.song_edit_speed), modifier = Modifier.weight(1f))
        ValuePill(
            text = stringResource(R.string.song_edit_speed_value, value),
            description = null,
            onClick = { state.speed = 1f },
        )
    }
    Spacer(Modifier.height(6.dp))
    LineSlider(
        value = (state.speed - SpeedMin) / (SpeedMax - SpeedMin),
        onValueChange = { fraction ->
            val raw = SpeedMin + fraction * (SpeedMax - SpeedMin)
            state.speed = ((raw / SpeedStep).roundToInt() * SpeedStep).coerceIn(SpeedMin, SpeedMax)
        },
    )
    RangeLabels(stringResource(R.string.song_edit_speed_min), stringResource(R.string.song_edit_speed_max))
    Spacer(Modifier.height(6.dp))
    Text(text = stringResource(R.string.song_edit_speed_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
}

@Composable
private fun ValuePill(text: String, description: String?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClickLabel = description,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RangeLabels(start: String, end: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = start, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(text = end, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// "set equalizer preset": the song's own curve while it plays, or the same as every other song
@Composable
private fun EqPresetSelect(state: SongEditState, audioLab: AudioLabStore) {
    val saved by audioLab.presets.collectAsState()
    val none = stringResource(R.string.song_edit_eq_none)
    val presets = remember(saved) {
        EqPresets.BuiltIn.map { it.id to it.name } + saved.map { it.id to it.name }
    }
    LabSelect(
        options = listOf(none) + presets.map { it.second },
        selectedIndex = presets.indexOfFirst { it.first == state.eqPresetId } + 1,
        onSelect = { index -> state.eqPresetId = presets.getOrNull(index - 1)?.first },
        contentDescription = stringResource(R.string.song_edit_eq_desc),
    )
}

// "other music metadata": the album and the year
@Composable
private fun MoreDetails(state: SongEditState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(2f)) {
            Text(text = stringResource(R.string.song_edit_album), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SheetTextField(
                value = state.album,
                onValueChange = { state.album = it.take(MaxTitleLength) },
                placeholder = stringResource(R.string.song_edit_album_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.song_edit_year), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SheetTextField(
                value = state.year,
                onValueChange = { typed -> state.year = typed.filter(Char::isDigit).take(4) },
                placeholder = stringResource(R.string.song_edit_year_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        }
    }
}

// the song's video as it stands, tapping opens select video over the sheet
@Composable
private fun VideoRow(songId: String, onClick: () -> Unit) {
    val edits by SongEdits.all.collectAsState()
    val edit = edits[songId]
    val status = when {
        edit?.noVideo == true -> R.string.song_edit_video_none
        edit?.videoId != null -> R.string.song_edit_video_picked
        else -> R.string.song_edit_video_auto
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        SheetIcon(R.drawable.slow_motion_video)
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.video_pick_title),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(text = stringResource(status), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}
