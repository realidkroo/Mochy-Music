// the mixer page: regular scaffold, clean presets, channel strips, loudness and a two-pane tablet studio layout

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSectionLabel
import com.mochyy.mochymusic.ui.screens.audiolab.LabStage
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.screens.audiolab.animatedDim
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.MixerViewModel

@Composable
fun MixerScreen(
    navController: NavController,
    viewModel: MixerViewModel = hiltViewModel(),
) {
    val store = viewModel.store
    val stateHolder = viewModel.state.collectAsState()
    val state = stateHolder.value
    val presets by viewModel.presets.collectAsState()
    val meters = rememberMixerMeters(active = state.enabled)
    var saving by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBusId by rememberSaveable { mutableIntStateOf(state.buses.firstOrNull()?.id ?: MixerNative.Master) }
    val activeBus = state.bus(selectedBusId) ?: state.buses.firstOrNull() ?: state.master
    val dim = animatedDim(!state.enabled)

    SubSettingsScaffold(
        title = stringResource(R.string.mixer_card_title),
        backLabel = stringResource(R.string.mixer_back_audio_fx),
        onBack = { navController.navigateUp() },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tablet = maxWidth >= 740.dp
            val wideLoudness = maxWidth >= 520.dp

            if (tablet) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LabCard(Modifier.weight(0.85f).fillMaxHeight(), padding = 10.dp) {
                            LabToggleRow(
                                title = stringResource(R.string.mixer_switch),
                                subtitle = null,
                                checked = state.enabled,
                                onCheckedChange = store::setEnabled,
                                leadingDot = true,
                            )
                        }
                        LabCard(Modifier.weight(1.35f).fillMaxHeight(), padding = 12.dp) {
                            LabSectionLabel(stringResource(R.string.mixer_mixes), Modifier.padding(horizontal = 6.dp)) {
                                LabTextAction(stringResource(R.string.mixer_reset_mix)) { store.resetMix() }
                            }
                            LabChips(
                                chips = presets.map { LabChip(it.id, it.name) },
                                selectedKey = state.presetId,
                                onSelect = store::loadMix,
                                onLongPress = { editing = it },
                                trailing = { LabTextAction(stringResource(R.string.mixer_save_mix), R.drawable.add) { saving = true } },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = dim },
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1.05f)) {
                            LabCard(padding = 12.dp) {
                                LabSectionLabel(stringResource(R.string.mixer_buses), Modifier.padding(horizontal = 6.dp))
                                MixerStrips(
                                    state = state,
                                    meters = meters,
                                    store = store,
                                    selectedBusId = activeBus.id,
                                    stripWidth = 86.dp,
                                    onOpen = { id -> selectedBusId = id },
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            LoudnessCard(meters, wide = true)
                        }
                        Column(Modifier.weight(0.95f)) {
                            ChannelDetailPane(
                                busId = activeBus.id,
                                bus = activeBus,
                                state = stateHolder,
                                store = store,
                                viewModel = viewModel,
                                navController = navController,
                                showChannelHeader = true,
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    LabCard(padding = 10.dp) {
                        LabToggleRow(
                            title = stringResource(R.string.mixer_switch),
                            subtitle = null,
                            checked = state.enabled,
                            onCheckedChange = store::setEnabled,
                            leadingDot = true,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }) {
                        LabCard(padding = 14.dp) {
                            LabSectionLabel(stringResource(R.string.mixer_mixes), Modifier.padding(horizontal = 6.dp)) {
                                LabTextAction(stringResource(R.string.mixer_reset_mix)) { store.resetMix() }
                            }
                            LabChips(
                                chips = presets.map { LabChip(it.id, it.name) },
                                selectedKey = state.presetId,
                                onSelect = store::loadMix,
                                onLongPress = { editing = it },
                                trailing = { LabTextAction(stringResource(R.string.mixer_save_mix), R.drawable.add) { saving = true } },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        LabCard(padding = 12.dp) {
                            LabSectionLabel(stringResource(R.string.mixer_buses), Modifier.padding(horizontal = 6.dp))
                            MixerStrips(
                                state = state,
                                meters = meters,
                                store = store,
                                onOpen = { id -> navController.navigate("audio_mixer_bus/$id") },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        LoudnessCard(meters, wide = wideLoudness)
                    }
                }
            }
        }
    }

    if (saving) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.mixer_save_mix)) },
            placeholder = { Text(stringResource(R.string.mixer_save_mix_hint)) },
            onDone = store::saveMix,
            onDismiss = { saving = false },
        )
    }
    editing?.let { id -> presets.firstOrNull { it.id == id } }?.let { preset ->
        val id = preset.id
        TextFieldDialog(
            title = { Text(stringResource(R.string.mixer_rename_mix)) },
            initialTextFieldValue = TextFieldValue(preset.name),
            onDone = { store.renameMix(id, it) },
            onDismiss = { editing = null },
            extraContent = {
                LabTextAction(stringResource(R.string.mixer_delete_mix), R.drawable.delete) {
                    store.deleteMix(id)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun LoudnessCard(meters: MixerMeterState, wide: Boolean) {
    val reading by remember { derivedStateOf { meters.tick.longValue / 8 } }
    LabCard(padding = 14.dp) {
        LabSectionLabel(stringResource(R.string.mixer_loudness), Modifier.padding(horizontal = 6.dp)) {
            LabTextAction(stringResource(R.string.mixer_loudness_reset)) { meters.resetLoudness() }
        }
        val values = remember(reading) { meters.loudness.copyOf() }
        if (wide) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Reading(stringResource(R.string.mixer_lufs_momentary), lufsText(values[0]), "LUFS")
                Reading(stringResource(R.string.mixer_lufs_short), lufsText(values[1]), "LUFS")
                Reading(stringResource(R.string.mixer_lufs_integrated), lufsText(values[2]), "LUFS")
                Reading(stringResource(R.string.mixer_lufs_range), lufsText(values[3]), "LU")
                Reading(stringResource(R.string.mixer_true_peak), lufsText(values[4]), "dBTP")
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Reading(stringResource(R.string.mixer_lufs_momentary), lufsText(values[0]), "LUFS")
                Reading(stringResource(R.string.mixer_lufs_short), lufsText(values[1]), "LUFS")
                Reading(stringResource(R.string.mixer_lufs_integrated), lufsText(values[2]), "LUFS")
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Reading(stringResource(R.string.mixer_lufs_range), lufsText(values[3]), "LU")
                Reading(stringResource(R.string.mixer_true_peak), lufsText(values[4]), "dBTP")
            }
        }
        Spacer(Modifier.height(12.dp))
        LabStage(height = 92.dp) {
            Canvas(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 10.dp, vertical = 10.dp)) {
                meters.tick.longValue
                val h = size.height
                fun y(lufs: Float) = h * (1f - ((lufs + 36f) / 30f).coerceIn(0f, 1f))
                drawLine(LabColors.StageLine, Offset(0f, y(-14f)), Offset(size.width, y(-14f)), strokeWidth = 1.dp.toPx())
                val history = meters.history
                val path = Path()
                var started = false
                for (i in history.indices) {
                    val v = history[i]
                    if (v < -150f) continue
                    val x = i / (history.size - 1f) * size.width
                    if (!started) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                    started = true
                }
                if (started) drawPath(path, MasterColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun Reading(name: String, value: String, unit: String) {
    Column {
        Text(name, fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
        Row {
            Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LabColors.Primary)
            Spacer(Modifier.padding(start = 3.dp))
            Text(unit, fontFamily = InterFontFamily, fontSize = 10.sp, color = LabColors.Secondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
