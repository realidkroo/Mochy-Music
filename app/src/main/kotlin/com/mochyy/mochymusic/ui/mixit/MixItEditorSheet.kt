// MixItEditorSheet.kt

package com.mochyy.mochymusic.ui.mixit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.mixit.MixItBeatMatchMode
import com.mochyy.mochymusic.mixit.MixItEffect
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Opens the Mix It Transition Editor bottom sheet for one consecutive song pair ([outSong] -> [inSong]).
 */
internal fun PopupSheetState.showMixItTransitionEditor(
    playlistId: String,
    outSong: Song,
    inSong: Song,
    existing: PlaylistTransition?,
    onSaved: () -> Unit = {},
    onReset: () -> Unit = {},
) {
    var handle: PopupSheetHandle? = null
    // Shared holder so the pinned bottom Reset/Save bar talks directly to the live editor state
    val actions = EditorActions()

    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
        buttonBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SheetPillButton(
                    text = stringResource(R.string.mixit_reset),
                    onClick = { actions.onReset?.invoke() },
                    modifier = Modifier.weight(1f),
                )
                MixItGoldActionButton(
                    text = stringResource(R.string.mixit_save),
                    onClick = { actions.onSave?.invoke() },
                    modifier = Modifier.weight(1.35f),
                )
            }
        },
    ) {
        MixItEditorContent(
            sheetState = this@showMixItTransitionEditor,
            sheetHandle = handle,
            playlistId = playlistId,
            outSong = outSong,
            inSong = inSong,
            existing = existing,
            actions = actions,
            onSaved = {
                onSaved()
                handle?.dismiss()
            },
            onResetDone = {
                onReset()
                handle?.dismiss()
            },
        )
    }
}

private class EditorActions {
    var onSave: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
}

@Composable
private fun MixItEditorContent(
    sheetState: PopupSheetState,
    sheetHandle: PopupSheetHandle?,
    playlistId: String,
    outSong: Song,
    inSong: Song,
    existing: PlaylistTransition?,
    actions: EditorActions,
    onSaved: () -> Unit,
    onResetDone: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    val outItem = remember(outSong.id) { outSong.toMediaItem() }
    val inItem = remember(inSong.id) { inSong.toMediaItem() }

    val fallbackOutDur = remember(outSong.song.duration) {
        (outSong.song.duration.takeIf { it > 0 }?.toLong()?.times(1_000L)) ?: 180_000L
    }
    val fallbackInDur = remember(inSong.song.duration) {
        (inSong.song.duration.takeIf { it > 0 }?.toLong()?.times(1_000L)) ?: 180_000L
    }

    // start from null and load disk/memory analysis inside LaunchedEffect so opening the sheet never blocks the main thread on disk io
    var outAnalysis by remember(outSong.id) { mutableStateOf<TrackAnalysis?>(null) }
    var inAnalysis by remember(inSong.id) { mutableStateOf<TrackAnalysis?>(null) }
    var analyzingOut by remember(outSong.id) { mutableStateOf(true) }
    var analyzingIn by remember(inSong.id) { mutableStateOf(true) }

    var outLyricsRaw by remember(outSong.id) { mutableStateOf<String?>(null) }
    var inLyricsRaw by remember(inSong.id) { mutableStateOf<String?>(null) }
    var outSyncedLines by remember(outSong.id) { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    var inSyncedLines by remember(inSong.id) { mutableStateOf<List<LyricsEntry>>(emptyList()) }

    val outDurationMs = remember(outAnalysis, fallbackOutDur) {
        maxOf(fallbackOutDur, outAnalysis?.analyzedMs ?: 0L).coerceAtLeast(15_000L)
    }
    val inDurationMs = remember(inAnalysis, fallbackInDur) {
        maxOf(fallbackInDur, inAnalysis?.analyzedMs ?: 0L).coerceAtLeast(15_000L)
    }

    var draft by remember(playlistId, outSong.id, inSong.id) {
        mutableStateOf(
            existing ?: MixItPlanner.defaultDraft(
                playlistId = playlistId,
                fromSongId = outSong.id,
                toSongId = inSong.id,
                outDurationMs = fallbackOutDur,
                inDurationMs = fallbackInDur,
                outAnalysis = outAnalysis,
                inAnalysis = inAnalysis,
            ),
        )
    }
    var userTouched by remember { mutableStateOf(existing != null) }
    var editPointMode by remember { mutableStateOf(false) }

    // Keep previewState as State so 60 Hz playhead ticks never recompose the entire editor sheet.
    val previewState = (playerConnection?.mixItPreviewState
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.mochyy.mochymusic.playback.MixItPreviewState()) })
        .collectAsState()

    // Stop live preview cleanly whenever the Transition Editor closes
    DisposableEffect(Unit) {
        onDispose {
            playerConnection?.stopMixItPreview()
        }
    }

    // run track analysis and lyrics independently so waveforms draw as soon as decoding finishes instead of waiting on slow lyrics
    LaunchedEffect(outSong.id, inSong.id) {
        val conn = playerConnection ?: run {
            analyzingOut = false
            analyzingIn = false
            return@LaunchedEffect
        }
        val outLyricsJob = async {
            val raw = conn.fetchLyricsForMixIt(outItem)
            val lines = withContext(Dispatchers.Default) { parseSyncedLines(raw) }
            outLyricsRaw = raw
            outSyncedLines = lines
            raw
        }
        val inLyricsJob = async {
            val raw = conn.fetchLyricsForMixIt(inItem)
            val lines = withContext(Dispatchers.Default) { parseSyncedLines(raw) }
            inLyricsRaw = raw
            inSyncedLines = lines
            raw
        }
        val outAnalyzeJob = async {
            val cached = withContext(Dispatchers.IO) { conn.cachedTrackAnalysis(outSong.id) }
            if (cached != null) outAnalysis = cached
            val result = if (cached?.complete == true && cached.waveform.isNotEmpty()) cached
            else conn.analyzeTrackForMixIt(outItem) ?: cached
            if (result != null) outAnalysis = result
            analyzingOut = false
            result
        }
        val inAnalyzeJob = async {
            val cached = withContext(Dispatchers.IO) { conn.cachedTrackAnalysis(inSong.id) }
            if (cached != null) inAnalysis = cached
            val result = if (cached?.complete == true && cached.waveform.isNotEmpty()) cached
            else conn.analyzeTrackForMixIt(inItem) ?: cached
            if (result != null) inAnalysis = result
            analyzingIn = false
            result
        }

        val oa = outAnalyzeJob.await()
        val ia = inAnalyzeJob.await()
        val outL = outLyricsJob.await()
        val inL = inLyricsJob.await()

        // upgrade an untouched default pair with musical cue points once analysis and lyrics have landed
        if (!userTouched && existing == null && (oa != null || ia != null)) {
            draft = MixItPlanner.defaultDraft(
                playlistId = playlistId,
                fromSongId = outSong.id,
                toSongId = inSong.id,
                outDurationMs = maxOf(fallbackOutDur, oa?.analyzedMs ?: 0L),
                inDurationMs = maxOf(fallbackInDur, ia?.analyzedMs ?: 0L),
                outAnalysis = oa,
                inAnalysis = ia,
                outLyrics = outL,
                inLyrics = inL,
            )
        }
    }

    // Wire Save & Reset handlers into the pinned bottom bar
    actions.onSave = {
        playerConnection?.stopMixItPreview()
        val toSave = draft.copy(updatedAt = System.currentTimeMillis())
        scope.launch {
            withContext(Dispatchers.IO) {
                database.upsert(toSave)
                database.setPlaylistMixItEnabled(playlistId, true)
            }
            playerConnection?.notifyMixItChanged(playlistId)
            TopToaster.show(
                text = context.getString(R.string.mixit_saved_toast),
                iconRes = R.drawable.ic_mix_it,
            )
            onSaved()
        }
    }
    actions.onReset = {
        playerConnection?.stopMixItPreview()
        scope.launch {
            withContext(Dispatchers.IO) {
                database.deletePlaylistTransition(playlistId, outSong.id, inSong.id)
            }
            playerConnection?.notifyMixItChanged(playlistId)
            TopToaster.show(
                text = context.getString(R.string.mixit_reset_toast),
                iconRes = R.drawable.ic_mix_it,
            )
            onResetDone()
        }
    }

    val beatMode = remember(draft.beatMatchMode) { MixItBeatMatchMode.fromName(draft.beatMatchMode) }
    val tempoSummary = remember(outAnalysis, inAnalysis, beatMode, draft.customSpeedRatio) {
        MixItPlanner.describeTempoMatch(outAnalysis, inAnalysis, beatMode, draft.customSpeedRatio)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        // ---- Top Header: "Transition Editor [BETA]" + Presets + Play/Stop Preview ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mixit_editor_title),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, MixItColors.Gold.copy(alpha = 0.7f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.mixit_beta),
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.8.sp,
                            color = MixItColors.Gold,
                        )
                    }
                    if (!draft.presetName.isNullOrBlank()) {
                        Text(
                            text = draft.presetName!!,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = MixItColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Presets pill button (matches Image 1 Screen 2!)
            val presetsInteraction = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressBounce(presetsInteraction)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(interactionSource = presetsInteraction, indication = null) {
                        playerConnection?.stopMixItPreview()
                        sheetState.showMixItPresetsPicker(
                            currentPresetName = draft.presetName,
                            onSmartAutoPick = {
                                userTouched = true
                                draft = MixItPlanner.defaultDraft(
                                    playlistId = playlistId,
                                    fromSongId = outSong.id,
                                    toSongId = inSong.id,
                                    outDurationMs = outDurationMs,
                                    inDurationMs = inDurationMs,
                                    outAnalysis = outAnalysis,
                                    inAnalysis = inAnalysis,
                                    outLyrics = outLyricsRaw,
                                    inLyrics = inLyricsRaw,
                                )
                            },
                            onSelectPreset = { preset ->
                                userTouched = true
                                val beatMs = outAnalysis?.beatMs?.toDouble()?.takeIf { it in 250.0..1_200.0 } ?: 500.0
                                draft = preset.applyTo(draft, beatMs)
                            },
                        )
                    }
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.mixit_presets),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = Color.White,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Circular Play / Stop Live Preview button (isolated so playhead ticks never recompose it)
            MixItPreviewPlayButton(
                previewState = previewState,
                onClick = {
                    val conn = playerConnection ?: return@MixItPreviewPlayButton
                    val st = previewState.value
                    if (st.isPlaying || st.isBuffering) {
                        conn.stopMixItPreview()
                    } else {
                        conn.startMixItPreview(
                            outItem = outItem,
                            inItem = inItem,
                            transition = draft,
                            outDurationMs = outDurationMs,
                            inDurationMs = inDurationMs,
                            outAnalysis = outAnalysis,
                            inAnalysis = inAnalysis,
                        )
                    }
                },
            )
        }

        // Optional preview stream error notice
        MixItPreviewErrorBanner(previewState = previewState)

        Spacer(Modifier.height(14.dp))

        // ---- Song 1 Header + Synced Lyrics Strip (Image 1 Screen 2 top!) ----
        TrackHeaderStrip(
            song = outSong,
            bpm = outAnalysis?.bpm?.takeIf { outAnalysis?.isBeatReliable == true },
            analyzing = analyzingOut,
            alignEnd = false,
            activeFx = MixItEffect.fromName(draft.outEffect),
        )
        if (outSyncedLines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SyncedLyricsSnippet(
                lines = outSyncedLines,
                atMsProvider = {
                    val st = previewState.value
                    if (st.isPlaying) st.outPositionMs else draft.outStartMs
                },
                alignEnd = false,
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- Smart Control Bar: Beat-Match Customize + Edit Point Toggle ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Visual Beat-Match pill (shows BPM sync direction directly, zero nasty descriptions!)
            val bmInteraction = remember { MutableInteractionSource() }
            val bmLabel = remember(tempoSummary) {
                if (tempoSummary.detail.isNotBlank()) tempoSummary.detail else tempoSummary.title
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressBounce(bmInteraction)
                    .weight(1f)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (tempoSummary.isLocked) MixItColors.GoldSoft else MixItColors.CardSurface)
                    .border(
                        width = 1.dp,
                        color = if (tempoSummary.isLocked) MixItColors.GoldBorder else MixItColors.Hairline,
                        shape = CircleShape,
                    )
                    .clickable(interactionSource = bmInteraction, indication = null) {
                        playerConnection?.stopMixItPreview()
                        sheetState.showMixItBeatMatchCustomizer(
                            initialDraft = draft,
                            outSong = outSong,
                            inSong = inSong,
                            outAnalysis = outAnalysis,
                            inAnalysis = inAnalysis,
                            onApply = { updated ->
                                userTouched = true
                                draft = updated
                            },
                        )
                    }
                    .padding(horizontal = 11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (tempoSummary.isLocked) MixItColors.Gold else MixItColors.TextMuted),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = bmLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (tempoSummary.isLocked) MixItColors.GoldBright else Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.tune),
                    contentDescription = null,
                    tint = if (tempoSummary.isLocked) MixItColors.Gold else MixItColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }

            // Edit Point toggle pill (isolated so color animation only recomposes this pill)
            EditPointTogglePill(
                editPointMode = editPointMode,
                onToggle = { editPointMode = !editPointMode },
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- THE DUAL WAVEFORM STUDIO DECK ----
        MixItWaveformDeck(
            draft = draft,
            outDurationMs = outDurationMs,
            inDurationMs = inDurationMs,
            outAnalysis = outAnalysis,
            inAnalysis = inAnalysis,
            analyzingOut = analyzingOut,
            analyzingIn = analyzingIn,
            editPointMode = editPointMode,
            isBeatMatched = tempoSummary.isLocked,
            playheadWindowProgressProvider = { previewState.value.windowProgress },
            onDraftChange = { updated ->
                userTouched = true
                draft = updated
            },
            onDragActiveChange = { dragging ->
                sheetHandle?.setLocked(dragging)
                val st = previewState.value
                if (dragging && (st.isPlaying || st.isBuffering)) {
                    playerConnection?.stopMixItPreview()
                }
            },
            onOpenOutEffects = {
                playerConnection?.stopMixItPreview()
                sheetState.showMixItEffectsPicker(
                    isOutgoing = true,
                    songTitle = outSong.song.title,
                    currentEffect = MixItEffect.fromName(draft.outEffect),
                    onSelect = { fx ->
                        userTouched = true
                        draft = draft.copy(outEffect = fx.name, presetName = null)
                    },
                )
            },
            onOpenInEffects = {
                playerConnection?.stopMixItPreview()
                sheetState.showMixItEffectsPicker(
                    isOutgoing = false,
                    songTitle = inSong.song.title,
                    currentEffect = MixItEffect.fromName(draft.inEffect),
                    onSelect = { fx ->
                        userTouched = true
                        draft = draft.copy(inEffect = fx.name, presetName = null)
                    },
                )
            },
            onOpenOutMix = {
                playerConnection?.stopMixItPreview()
                sheetState.showMixItStemMixer(
                    initialDraft = draft,
                    outSong = outSong,
                    inSong = inSong,
                    startOnIncoming = false,
                    onApply = { updated ->
                        userTouched = true
                        draft = updated
                    },
                )
            },
            onOpenInMix = {
                playerConnection?.stopMixItPreview()
                sheetState.showMixItStemMixer(
                    initialDraft = draft,
                    outSong = outSong,
                    inSong = inSong,
                    startOnIncoming = true,
                    onApply = { updated ->
                        userTouched = true
                        draft = updated
                    },
                )
            },
        )

        Spacer(Modifier.height(10.dp))

        // ---- Song 2 Header + Synced Lyrics Strip (mirrored cleanly like Image 1!) ----
        TrackHeaderStrip(
            song = inSong,
            bpm = inAnalysis?.bpm?.takeIf { inAnalysis?.isBeatReliable == true },
            analyzing = analyzingIn,
            alignEnd = true,
            activeFx = MixItEffect.fromName(draft.inEffect),
        )
        if (inSyncedLines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SyncedLyricsSnippet(
                lines = inSyncedLines,
                atMsProvider = {
                    val st = previewState.value
                    if (st.isPlaying) st.inPositionMs else draft.inStartMs
                },
                alignEnd = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Subtle helpful hint line at the bottom of the deck
        Text(
            text = if (editPointMode) {
                stringResource(R.string.mixit_hint_edit_point)
            } else {
                stringResource(R.string.mixit_hint_drag_waves)
            },
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            color = MixItColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackHeaderStrip(
    song: Song,
    bpm: Float?,
    analyzing: Boolean,
    alignEnd: Boolean,
    activeFx: MixItEffect,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!alignEnd) {
            TrackArtThumb(song.song.thumbnailUrl)
            Spacer(Modifier.width(9.dp))
        }

        Column(
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (alignEnd) {
                    BpmOrLoadingChip(bpm = bpm, analyzing = analyzing)
                }
                Text(
                    text = song.song.title,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!alignEnd) {
                    BpmOrLoadingChip(bpm = bpm, analyzing = analyzing)
                }
            }
            val artistText = song.artists.joinToString { it.name }.ifBlank { "" }
            val fxText = if (activeFx != MixItEffect.NONE) " · FX: ${activeFx.label}" else ""
            if (artistText.isNotBlank() || fxText.isNotBlank()) {
                Text(
                    text = artistText + fxText,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = MixItColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (alignEnd) {
            Spacer(Modifier.width(9.dp))
            TrackArtThumb(song.song.thumbnailUrl)
        }
    }
}

@Composable
private fun TrackArtThumb(url: String?) {
    AsyncImage(
        model = url?.resize(160, 160),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MixItColors.CardSurface),
    )
}

@Composable
private fun BpmOrLoadingChip(bpm: Float?, analyzing: Boolean) {
    when {
        bpm != null -> Text(
            text = "${bpm.roundToInt()} BPM",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = MixItColors.Gold,
            modifier = Modifier
                .clip(CircleShape)
                .background(MixItColors.Gold.copy(alpha = 0.13f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        analyzing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            CircularProgressIndicator(
                color = MixItColors.Gold,
                strokeWidth = 1.4.dp,
                modifier = Modifier.size(9.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.mixit_reading_audio),
                fontFamily = InterFontFamily,
                fontSize = 9.5.sp,
                color = MixItColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MixItPreviewPlayButton(
    previewState: androidx.compose.runtime.State<com.mochyy.mochymusic.playback.MixItPreviewState>,
    onClick: () -> Unit,
) {
    val isPlaying by remember(previewState) { androidx.compose.runtime.derivedStateOf { previewState.value.isPlaying } }
    val isBuffering by remember(previewState) { androidx.compose.runtime.derivedStateOf { previewState.value.isBuffering } }
    val playInteraction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(playInteraction)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isPlaying) MixItColors.Gold else Color.White)
            .clickable(interactionSource = playInteraction, indication = null, onClick = onClick),
    ) {
        when {
            isBuffering -> CircularProgressIndicator(
                color = MixItColors.OnGold,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            isPlaying -> Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MixItColors.OnGold),
            )
            else -> Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.mixit_preview),
                tint = MixItColors.OnGold,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MixItPreviewErrorBanner(
    previewState: androidx.compose.runtime.State<com.mochyy.mochymusic.playback.MixItPreviewState>,
) {
    val error by remember(previewState) { androidx.compose.runtime.derivedStateOf { previewState.value.error } }
    error?.let { err ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = err,
            fontFamily = InterFontFamily,
            fontSize = 11.5.sp,
            color = Color(0xFFFF8E96),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFF4D5A).copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EditPointTogglePill(
    editPointMode: Boolean,
    onToggle: () -> Unit,
) {
    val editBg by animateColorAsState(
        targetValue = if (editPointMode) MixItColors.Gold else MixItColors.Gold.copy(alpha = 0.22f),
        animationSpec = tween(180),
        label = "editPointBg",
    )
    val editFg by animateColorAsState(
        targetValue = if (editPointMode) MixItColors.OnGold else MixItColors.GoldBright,
        animationSpec = tween(180),
        label = "editPointFg",
    )
    val epInteraction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(epInteraction)
            .height(32.dp)
            .clip(CircleShape)
            .background(editBg)
            .border(1.dp, MixItColors.Gold.copy(alpha = if (editPointMode) 1f else 0.55f), CircleShape)
            .clickable(interactionSource = epInteraction, indication = null, onClick = onToggle)
            .padding(horizontal = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (editPointMode) MixItColors.OnGold else MixItColors.Gold),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.mixit_edit_point),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = editFg,
        )
    }
}

/**
 * 3-line synced lyrics preview starting directly at the Start Box timestamp ([atMsProvider]),
 * showing the line at the start of the yellow box first, followed by the next 2 upcoming lines.
 * Derived on the active line index so 60 Hz preview playback only recomposes when the line advances.
 */
@Composable
private fun SyncedLyricsSnippet(
    lines: List<LyricsEntry>,
    atMsProvider: () -> Long,
    alignEnd: Boolean,
) {
    if (lines.isEmpty()) return
    val currentAtMs by androidx.compose.runtime.rememberUpdatedState(atMsProvider)
    val startIdx by remember(lines) {
        androidx.compose.runtime.derivedStateOf {
            val atMs = currentAtMs()
            // Prefer the first line beginning at/inside the start box (within 400ms tolerance),
            // falling back to the active line being sung right at the start edge.
            val upcoming = lines.indexOfFirst { it.time >= atMs - 400L }
            when {
                upcoming >= 0 && lines[upcoming].time <= atMs + 2_500L -> upcoming
                else -> lines.indexOfLast { it.time <= atMs + 250L }.coerceIn(0, lines.lastIndex)
            }
        }
    }
    val line0 = lines.getOrNull(startIdx)
    val line1 = lines.getOrNull(startIdx + 1)
    val line2 = lines.getOrNull(startIdx + 2)

    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        if (line0 != null) LyricLineRow(line0, active = true, alignEnd = alignEnd)
        if (line1 != null) LyricLineRow(line1, active = false, alignEnd = alignEnd)
        if (line2 != null) LyricLineRow(line2, active = false, alignEnd = alignEnd)
    }
}

@Composable
private fun LyricLineRow(entry: LyricsEntry, active: Boolean, alignEnd: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (active && !alignEnd) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MixItColors.Gold),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = formatMixClock(entry.time),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MixItColors.Gold else MixItColors.TextMuted,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.text,
            fontFamily = InterFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Color.White else MixItColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (active && alignEnd) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MixItColors.Gold),
            )
        }
    }
}

private val SyncedStampRegex = Regex("""\[\d{1,2}:\d{2}""")

private fun parseSyncedLines(raw: String?): List<LyricsEntry> {
    if (raw.isNullOrBlank() || !SyncedStampRegex.containsMatchIn(raw)) return emptyList()
    return runCatching { LyricsUtils.parseLyrics(raw) }.getOrNull().orEmpty()
        .filter { it.text.isNotBlank() }
}
