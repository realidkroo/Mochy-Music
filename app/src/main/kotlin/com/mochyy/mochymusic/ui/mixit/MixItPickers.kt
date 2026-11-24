// MixItPickers.kt

package com.mochyy.mochymusic.ui.mixit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.mixit.MixItBeatMatchMode
import com.mochyy.mochymusic.mixit.MixItEffect
import com.mochyy.mochymusic.mixit.MixItFallbackMode
import com.mochyy.mochymusic.mixit.MixItLimits
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.mixit.MixItPreset
import com.mochyy.mochymusic.mixit.TempoMatchSummary
import com.mochyy.mochymusic.mixit.TempoSyncDirection
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stacked Effects Picker Sheet (matching Screen 1 of the Mix It concept).
 * Pushes cleanly over the Transition Editor via Mochy's [PopupSheetState].
 */
internal fun PopupSheetState.showMixItEffectsPicker(
    isOutgoing: Boolean,
    songTitle: String,
    currentEffect: MixItEffect,
    onSelect: (MixItEffect) -> Unit,
) {
    val options = if (isOutgoing) MixItEffect.outgoingOptions else MixItEffect.incomingOptions
    var selected by mutableStateOf(currentEffect)
    var handle: com.mochyy.mochymusic.ui.component.PopupSheetHandle? = null
    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
        buttonBar = {
            MixItGoldActionButton(
                text = stringResource(R.string.mixit_select),
                onClick = {
                    onSelect(selected)
                    handle?.dismiss()
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.mixit_effects_title),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = (if (isOutgoing) "Song 1 · " else "Song 2 · ") + songTitle,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MixItColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { fx ->
                    val isSelected = selected == fx
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) MixItColors.GoldSoft else MixItColors.CardSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MixItColors.Gold else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { selected = fx }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = fx.label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (isSelected) MixItColors.GoldBright else Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MixItColors.Gold),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = MixItColors.OnGold,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Yellow "Mix" Sound Stems Sheet — tactile sliders for reducing/shaping Voice, Bass & Drums, and
 * Instruments independently on Song 1 and Song 2 during the transition.
 */
internal fun PopupSheetState.showMixItStemMixer(
    initialDraft: PlaylistTransition,
    outSong: Song,
    inSong: Song,
    startOnIncoming: Boolean,
    onApply: (PlaylistTransition) -> Unit,
) {
    var working by mutableStateOf(initialDraft)
    var tab by mutableIntStateOf(if (startOnIncoming) 1 else 0)
    var handle: com.mochyy.mochymusic.ui.component.PopupSheetHandle? = null
    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
        buttonBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SheetPillButton(
                    text = stringResource(R.string.mixit_reset_levels),
                    onClick = {
                        working = if (tab == 0) {
                            working.copy(outVoiceLevel = 1f, outBassLevel = 1f, outInstrumentLevel = 1f)
                        } else {
                            working.copy(inVoiceLevel = 1f, inBassLevel = 1f, inInstrumentLevel = 1f)
                        }
                        onApply(working)
                    },
                    modifier = Modifier.weight(1f),
                )
                MixItGoldActionButton(
                    text = stringResource(R.string.done),
                    onClick = {
                        onApply(working)
                        handle?.dismiss()
                    },
                    modifier = Modifier.weight(1.25f),
                )
            }
        },
    ) {
        val voice = if (tab == 0) working.outVoiceLevel else working.inVoiceLevel
        val bass = if (tab == 0) working.outBassLevel else working.inBassLevel
        val inst = if (tab == 0) working.outInstrumentLevel else working.inInstrumentLevel
        val activeSong = if (tab == 0) outSong else inSong

        fun updateLevels(v: Float = voice, b: Float = bass, i: Float = inst) {
            working = if (tab == 0) {
                working.copy(outVoiceLevel = v, outBassLevel = b, outInstrumentLevel = i, presetName = null)
            } else {
                working.copy(inVoiceLevel = v, inBassLevel = b, inInstrumentLevel = i, presetName = null)
            }
            onApply(working)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.mixit_mix_title),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))

            // Song 1 / Song 2 Segmented Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MixItColors.CardSurface)
                    .padding(4.dp),
            ) {
                listOf(
                    0 to (stringResource(R.string.mixit_mix_song1) to outSong.song.thumbnailUrl),
                    1 to (stringResource(R.string.mixit_mix_song2) to inSong.song.thumbnailUrl),
                ).forEach { (idx, pair) ->
                    val selected = tab == idx
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MixItColors.Gold else Color.Transparent)
                            .clickable { tab = idx }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                    ) {
                        AsyncImage(
                            model = pair.second?.resize(100, 100),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.25f)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pair.first,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) MixItColors.OnGold else Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = activeSong.song.title,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = MixItColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Quick 1-Tap Stem Mix Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                data class QuickStem(val label: String, val v: Float, val b: Float, val i: Float)
                val chips = listOf(
                    QuickStem("Full", 1f, 1f, 1f),
                    QuickStem("No Voice", 0f, 1f, 1f),
                    QuickStem("No Bass", 1f, 0f, 1f),
                    QuickStem("Vocals", 1f, 0.1f, 0.12f),
                )
                chips.forEach { chip ->
                    val matches = abs(voice - chip.v) < 0.06f && abs(bass - chip.b) < 0.06f && abs(inst - chip.i) < 0.06f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (matches) MixItColors.GoldSoft else MixItColors.CardSurface)
                            .border(
                                1.dp,
                                if (matches) MixItColors.Gold else MixItColors.Hairline,
                                CircleShape,
                            )
                            .clickable { updateLevels(chip.v, chip.b, chip.i) }
                            .padding(vertical = 7.dp),
                    ) {
                        Text(
                            text = chip.label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (matches) MixItColors.GoldBright else Color.White.copy(alpha = 0.84f),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tactile Voice / Bass / Instruments Sliders Card
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MixItColors.CardSurface)
                    .padding(14.dp),
            ) {
                StemSliderRow(
                    title = stringResource(R.string.mixit_stem_voice),
                    value = voice,
                    onValueChange = { updateLevels(v = it) },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MixItColors.Hairline))
                StemSliderRow(
                    title = stringResource(R.string.mixit_stem_bass),
                    value = bass,
                    onValueChange = { updateLevels(b = it) },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MixItColors.Hairline))
                StemSliderRow(
                    title = stringResource(R.string.mixit_stem_instruments),
                    value = inst,
                    onValueChange = { updateLevels(i = it) },
                )
            }

            Spacer(Modifier.height(12.dp))

            // Clean Bass Swap Toggle (compact, zero wordy fluff)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.CardSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.mixit_bass_swap),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = working.bassSwap,
                    onCheckedChange = {
                        working = working.copy(bassSwap = it)
                        onApply(working)
                    },
                )
            }
        }
    }
}

@Composable
private fun StemSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val pct = (value * 100f).roundToInt().coerceIn(0, 100)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pct == 0) "CUT" else "$pct%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (pct < 100) MixItColors.GoldBright else MixItColors.TextSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (pct < 100) MixItColors.GoldSoft else Color.White.copy(alpha = 0.06f))
                    .clickable { onValueChange(if (pct > 0) 0f else 1f) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Slider(
            value = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
            onValueChange = { onValueChange((it * 20f).roundToInt() / 20f) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MixItColors.Gold,
                activeTrackColor = MixItColors.Gold,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
            ),
        )
    }
}

/** Stacked 1-tap DJ Presets Picker pushed over the Transition Editor. */
internal fun PopupSheetState.showMixItPresetsPicker(
    currentPresetName: String?,
    onSmartAutoPick: () -> Unit,
    onSelectPreset: (MixItPreset) -> Unit,
) {
    var handle: com.mochyy.mochymusic.ui.component.PopupSheetHandle? = null
    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.mixit_presets_title),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.GoldSoft)
                    .border(1.dp, MixItColors.GoldBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        onSmartAutoPick()
                        handle?.dismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MixItColors.Gold),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sparks),
                        contentDescription = null,
                        tint = MixItColors.OnGold,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.mixit_smart_pick),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MixItColors.GoldBright,
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MixItPreset.entries.forEach { preset ->
                    val isCurrent = currentPresetName == preset.title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) MixItColors.CardSurfaceElevated else MixItColors.CardSurface)
                            .border(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) MixItColors.Gold else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                onSelectPreset(preset)
                                handle?.dismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = preset.title,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (isCurrent) MixItColors.GoldBright else Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${preset.durationBeats} beats",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MixItColors.Gold.copy(alpha = 0.9f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MixItColors.Gold.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual Beat-Matching Customizer — shows a live visual BPM sync diagram between Song 1 and Song 2
 * (direction arrow + animated dual beat-grid lock) with zero nasty verbose descriptions.
 */
internal fun PopupSheetState.showMixItBeatMatchCustomizer(
    initialDraft: PlaylistTransition,
    outSong: Song,
    inSong: Song,
    outAnalysis: TrackAnalysis?,
    inAnalysis: TrackAnalysis?,
    onApply: (PlaylistTransition) -> Unit,
) {
    var working by mutableStateOf(initialDraft)
    var handle: com.mochyy.mochymusic.ui.component.PopupSheetHandle? = null
    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
        buttonBar = {
            MixItGoldActionButton(
                text = stringResource(R.string.done),
                onClick = {
                    onApply(working)
                    handle?.dismiss()
                },
            )
        },
    ) {
        val haptic = LocalHapticFeedback.current
        val mode = MixItBeatMatchMode.fromName(working.beatMatchMode)
        val summary = remember(outAnalysis, inAnalysis, mode, working.customSpeedRatio) {
            MixItPlanner.describeTempoMatch(outAnalysis, inAnalysis, mode, working.customSpeedRatio)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.mixit_beatmatch_title),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
                color = Color.White,
            )

            Spacer(Modifier.height(14.dp))

            // Visual Song 1 <==> Song 2 Beat-Match Diagram + Live Animated Beat Lock Strip!
            VisualBeatMatchStage(
                outSong = outSong,
                inSong = inSong,
                summary = summary,
                beatNudgeMs = working.beatNudgeMs,
            )

            Spacer(Modifier.height(14.dp))

            // Clean 4-way visual mode selector pills (no descriptions!)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val modeLabels = listOf(
                    MixItBeatMatchMode.AUTO to "Auto",
                    MixItBeatMatchMode.MATCH_OUT to "Song 1",
                    MixItBeatMatchMode.MATCH_IN to "Song 2",
                    MixItBeatMatchMode.OFF to "Off",
                )
                modeLabels.forEach { (option, shortTitle) ->
                    val selected = mode == option && working.customSpeedRatio == 0f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MixItColors.Gold else MixItColors.CardSurface)
                            .border(
                                width = 1.dp,
                                color = if (selected) MixItColors.Gold else MixItColors.Hairline,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                working = working.copy(beatMatchMode = option.name, customSpeedRatio = 0f)
                                onApply(working)
                            },
                    ) {
                        Text(
                            text = shortTitle,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selected) MixItColors.OnGold else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 1-Tap Align Beats Action Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MixItColors.GoldSoft)
                    .border(1.dp, MixItColors.GoldBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val (outAligned, inAligned) = MixItPlanner.autoAlignAtRegion(
                            working.outStartMs,
                            working.inStartMs,
                            outAnalysis,
                            inAnalysis,
                        )
                        working = working.copy(outStartMs = outAligned, inStartMs = inAligned, beatNudgeMs = 0)
                        onApply(working)
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mix_it),
                    contentDescription = null,
                    tint = MixItColors.Gold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.mixit_auto_align_beats),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MixItColors.GoldBright,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Compact Switch Card (title + switch only — clean, zero clutter!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.CardSurface)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mixit_snap_to_beat),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    AppSwitch(
                        checked = working.snapToBeat,
                        onCheckedChange = {
                            working = working.copy(snapToBeat = it)
                            onApply(working)
                        },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MixItColors.Hairline))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mixit_bass_swap),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    AppSwitch(
                        checked = working.bassSwap,
                        onCheckedChange = {
                            working = working.copy(bassSwap = it)
                            onApply(working)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fine Beat Nudge Stepper
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.CardSurface)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.mixit_beat_nudge),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (working.beatNudgeMs == 0) "0 ms" else "%+d ms".format(working.beatNudgeMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MixItColors.Gold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(-25, -10, 0, 10, 25).forEach { step ->
                        val label = if (step == 0) "0" else "%+d".format(step)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (step == 0 && working.beatNudgeMs == 0) MixItColors.GoldSoft
                                    else MixItColors.CardSurfaceElevated,
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = if (step == 0) 0 else (working.beatNudgeMs + step)
                                        .coerceIn(-MixItLimits.MaxNudgeMs, MixItLimits.MaxNudgeMs)
                                    working = working.copy(beatNudgeMs = next)
                                    onApply(working)
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = if (step == 0) MixItColors.GoldBright else Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Song 2 Manual Speed Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.CardSurface)
                    .padding(14.dp),
            ) {
                val activeRatio = if (working.customSpeedRatio > 0f) working.customSpeedRatio else summary.inSpeed
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.mixit_custom_tempo),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (working.customSpeedRatio == 0f) "Auto · ${(activeRatio * 100).roundToInt()}%"
                        else "${(working.customSpeedRatio * 100).roundToInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MixItColors.Gold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                working = working.copy(customSpeedRatio = 0f)
                                onApply(working)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Slider(
                    value = activeRatio.takeIf { it.isFinite() }?.coerceIn(0.80f, 1.20f) ?: 1f,
                    onValueChange = { v ->
                        val rounded = (v * 100f).roundToInt() / 100f
                        working = working.copy(customSpeedRatio = rounded)
                        onApply(working)
                    },
                    valueRange = 0.80f..1.20f,
                    colors = SliderDefaults.colors(
                        thumbColor = MixItColors.Gold,
                        activeTrackColor = MixItColors.Gold,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    ),
                )
            }
        }
    }
}

/**
 * Visual Song 1 ⇄ Song 2 Beat-Match Stage showing artwork, BPM, directional sync arrow, and
 * animated dual-track beat grid pulses that visually lock together when beat-matching is active.
 */
@Composable
private fun VisualBeatMatchStage(
    outSong: Song,
    inSong: Song,
    summary: TempoMatchSummary,
    beatNudgeMs: Int,
) {
    val pulse = rememberInfiniteTransition(label = "beatLockPulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "beatPhase",
    )

    val song1Adapts = summary.direction == TempoSyncDirection.SONG1_TO_SONG2
    val song2Adapts = summary.direction == TempoSyncDirection.SONG2_TO_SONG1
    val outBpmText = summary.outBpm?.let { "${it.roundToInt()} BPM" } ?: "-- BPM"
    val inBpmText = summary.inBpm?.let { "${it.roundToInt()} BPM" } ?: "-- BPM"
    val syncedText = summary.syncedBpm?.let { "${it.roundToInt()} BPM" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MixItColors.DeckBackground)
            .border(
                1.dp,
                if (summary.isLocked) MixItColors.Gold.copy(alpha = 0.5f) else MixItColors.Hairline,
                RoundedCornerShape(18.dp),
            )
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Song 1 Tile
            VisualTrackSyncTile(
                artUrl = outSong.song.thumbnailUrl,
                title = outSong.song.title,
                bpm = outBpmText,
                targetBpm = if (song1Adapts) syncedText else null,
                isLeader = song2Adapts || summary.direction == TempoSyncDirection.NATURAL_LOCK,
                isAdapting = song1Adapts,
                modifier = Modifier.weight(1f),
            )

            // Center Directional Sync Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp),
            ) {
                val arrowSymbol = when (summary.direction) {
                    TempoSyncDirection.SONG2_TO_SONG1 -> "──▶"
                    TempoSyncDirection.SONG1_TO_SONG2 -> "◀──"
                    TempoSyncDirection.NATURAL_LOCK -> "══"
                    TempoSyncDirection.GAP -> "· ·"
                    TempoSyncDirection.NONE -> "—"
                }
                Text(
                    text = arrowSymbol,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (summary.isLocked) MixItColors.Gold else MixItColors.TextMuted,
                )
                Spacer(Modifier.height(3.dp))
                val pctTag = when {
                    song2Adapts && abs(summary.inSpeed - 1f) > 0.004f -> {
                        val p = ((summary.inSpeed - 1f) * 100f).roundToInt()
                        if (p >= 0) "+$p%" else "$p%"
                    }
                    song1Adapts && abs(summary.outSpeed - 1f) > 0.004f -> {
                        val p = ((summary.outSpeed - 1f) * 100f).roundToInt()
                        if (p >= 0) "+$p%" else "$p%"
                    }
                    summary.direction == TempoSyncDirection.NATURAL_LOCK -> "LOCK"
                    summary.direction == TempoSyncDirection.GAP -> "FREE"
                    else -> "OFF"
                }
                Text(
                    text = pctTag,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    color = if (summary.isLocked) MixItColors.OnGold else MixItColors.TextSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (summary.isLocked) MixItColors.Gold else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Song 2 Tile
            VisualTrackSyncTile(
                artUrl = inSong.song.thumbnailUrl,
                title = inSong.song.title,
                bpm = inBpmText,
                targetBpm = if (song2Adapts) syncedText else null,
                isLeader = song1Adapts || summary.direction == TempoSyncDirection.NATURAL_LOCK,
                isAdapting = song2Adapts,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Live Dual Beat-Grid Lock Strip: beats line up vertically when synced, drift when off, shift with nudge!
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            val beats = 8
            val spacing = size.width / beats
            val nudgeOffsetPx = (beatNudgeMs / 500f) * spacing
            val driftRatio = if (
                summary.isLocked ||
                summary.outBpm == null ||
                summary.inBpm == null ||
                !summary.outBpm.isFinite() ||
                !summary.inBpm.isFinite() ||
                summary.outBpm <= 1f ||
                summary.inBpm <= 1f
            ) {
                1f
            } else {
                (summary.inBpm / summary.outBpm).takeIf { it.isFinite() }?.coerceIn(0.65f, 1.45f) ?: 1f
            }

            val topY1 = 4.dp.toPx()
            val topY2 = size.height / 2f - 2.dp.toPx()
            val botY1 = size.height / 2f + 2.dp.toPx()
            val botY2 = size.height - 4.dp.toPx()

            for (i in -1..(beats + 1)) {
                val x1 = ((i + phase) * spacing)
                if (x1 in 0f..size.width) {
                    val isBar = (i % 4 == 0)
                    drawLine(
                        color = if (isBar) MixItColors.Gold else MixItColors.Track1Wave.copy(alpha = 0.7f),
                        start = Offset(x1, topY1),
                        end = Offset(x1, topY2),
                        strokeWidth = if (isBar) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val x2 = ((i + phase) * spacing * driftRatio) + nudgeOffsetPx
                if (x2 in 0f..size.width) {
                    val isBar = (i % 4 == 0)
                    val lockedCol = if (summary.isLocked) {
                        if (isBar) MixItColors.GoldBright else MixItColors.Gold.copy(alpha = 0.7f)
                    } else {
                        MixItColors.Track2Wave.copy(alpha = 0.5f)
                    }
                    drawLine(
                        color = lockedCol,
                        start = Offset(x2, botY1),
                        end = Offset(x2, botY2),
                        strokeWidth = if (isBar) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualTrackSyncTile(
    artUrl: String?,
    title: String,
    bpm: String,
    targetBpm: String?,
    isLeader: Boolean,
    isAdapting: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLeader || isAdapting) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.025f))
            .border(
                width = if (isAdapting) 1.2.dp else 1.dp,
                color = when {
                    isAdapting -> MixItColors.Gold
                    isLeader -> MixItColors.Gold.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(8.dp),
    ) {
        AsyncImage(
            model = artUrl?.resize(140, 140),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MixItColors.CardSurface),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (targetBpm != null && targetBpm != bpm) "$bpm → $targetBpm" else bpm,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (isAdapting || isLeader) MixItColors.GoldBright else MixItColors.TextSecondary,
            maxLines = 1,
        )
    }
}

/**
 * Playlist-level Mix It Settings Sheet: toggle Mix It on/off, pick unconfigured fallback, or reset all.
 */
internal fun PopupSheetState.showMixItPlaylistOptions(
    mixItEnabled: Boolean,
    currentFallback: MixItFallbackMode,
    customCount: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectFallback: (MixItFallbackMode) -> Unit,
    onClearAll: () -> Unit,
) {
    var enabledState by mutableStateOf(mixItEnabled)
    var fallbackState by mutableStateOf(currentFallback)
    var confirmClear by mutableStateOf(false)
    var handle: com.mochyy.mochymusic.ui.component.PopupSheetHandle? = null
    handle = showWithHandle(
        surface = MixItColors.SheetSurface,
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.done),
                onClick = { handle?.dismiss() },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.mixit_playlist_settings_title),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MixItColors.CardSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.mixit_enable_for_playlist),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = enabledState,
                    onCheckedChange = {
                        enabledState = it
                        onToggleEnabled(it)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionMiniLabel(stringResource(R.string.mixit_unconfigured_heading))
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MixItFallbackMode.entries.forEach { option ->
                    val selected = fallbackState == option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MixItColors.GoldSoft else MixItColors.CardSurface)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MixItColors.Gold else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                fallbackState = option
                                onSelectFallback(option)
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = option.label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = if (selected) MixItColors.GoldBright else Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MixItColors.Gold,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (customCount > 0) {
                Spacer(Modifier.height(16.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFF4D5A).copy(alpha = if (confirmClear) 0.24f else 0.12f))
                        .border(1.dp, Color(0xFFFF4D5A).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable {
                            if (confirmClear) {
                                onClearAll()
                                handle?.dismiss()
                            } else {
                                confirmClear = true
                            }
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        text = if (confirmClear) {
                            stringResource(R.string.mixit_clear_all_confirm, customCount)
                        } else {
                            stringResource(R.string.mixit_clear_all, customCount)
                        },
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color(0xFFFF7A84),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionMiniLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = MixItColors.TextSecondary,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
internal fun MixItGoldActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(if (enabled) MixItColors.Gold else MixItColors.Gold.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MixItColors.OnGold,
            maxLines = 1,
        )
    }
}
