// one bus's page: what it listens to, where it sends, and its chain of effects

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.BusFocus
import com.mochyy.mochymusic.eq.mixer.MixBus
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.eq.mixer.MixerState
import com.mochyy.mochymusic.eq.mixer.MixerStore
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.screens.audiolab.AudioLabPage
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabPanel
import com.mochyy.mochymusic.ui.screens.audiolab.LabSectionLabel
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabTitle
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.MixerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BusScreen(
    navController: NavController,
    busId: Int,
    viewModel: MixerViewModel = hiltViewModel(),
) {
    val store = viewModel.store
    val state = viewModel.state.collectAsState()
    val bus by remember(busId) { derivedStateOf { state.value.bus(busId) } }
    val current = bus ?: return
    val label = busLabel(current)
    val accent = busColor(current)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheets = LocalZoomOutOverlayState.current
    val unreadable = stringResource(R.string.mixer_script_unreadable)
    val openScript = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val script = viewModel.readScript(uri)
            if (script == null) {
                Toast.makeText(context, unreadable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val kind = store.script(script.first).kind(script.second)
            store.addSlot(busId, kind, script = script.first, scriptName = script.second)
        }
    }
    val slotIds by remember(busId) { derivedStateOf { state.value.bus(busId)?.slots?.map { it.uid }.orEmpty() } }

    AudioLabPage(
        title = LabTitle(label, stringResource(R.string.mixer_bus_title), firstSize = 40.sp, secondSize = 72.sp, firstDim = true),
        onBack = { navController.navigateUp() },
    ) {
        if (!current.isMaster) {
            BusSettingsCard(store, state, current)
            Spacer(Modifier.height(14.dp))
        }
        LabSectionLabel(stringResource(R.string.mixer_effects), Modifier.padding(horizontal = 6.dp)) {
            LabTextAction(stringResource(R.string.mixer_add_effect), R.drawable.add, enabled = slotIds.size < MixerStore.MaxSlots) {
                sheets.showAddEffectSheet(
                    store.addable(),
                    AddEffectActions(
                        onPick = { kind -> store.addSlot(busId, kind) },
                        onNewScript = {
                            val name = context.getString(R.string.mixer_new_script)
                            store.addSlot(busId, store.script(NewScriptTemplate).kind(name), script = NewScriptTemplate, scriptName = name)?.let { uid ->
                                navController.navigate("audio_liveprog/$uid")
                            }
                        },
                        onOpenScript = { openScript.launch(arrayOf("*/*")) },
                    ),
                )
            }
        }
        if (slotIds.isEmpty()) {
            LabCard { Text(stringResource(R.string.mixer_no_effects), fontFamily = InterFontFamily, fontSize = 14.sp, color = LabColors.Secondary, modifier = Modifier.padding(6.dp)) }
        }
        slotIds.forEachIndexed { index, uid ->
            key(uid) {
                SlotCard(
                    uid = uid,
                    state = state,
                    store = store,
                    position = index,
                    count = slotIds.size,
                    accent = accent,
                    onEditScript = { navController.navigate("audio_liveprog/$uid") },
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun BusSettingsCard(store: MixerStore, state: State<MixerState>, bus: MixBus) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val focusOptions = BusFocus.entries
    val others by remember(bus.id) { derivedStateOf { state.value.buses.filter { it.id != bus.id } } }
    LabCard(padding = 14.dp) {
        LabToggleRow(
            title = stringResource(R.string.mixer_bus_input),
            subtitle = stringResource(R.string.mixer_bus_input_sub),
            checked = bus.songIn,
            onCheckedChange = { store.setSongIn(bus.id, it) },
        )
        Spacer(Modifier.height(8.dp))
        LabSectionLabel(stringResource(R.string.mixer_bus_focus), Modifier.padding(horizontal = 6.dp))
        LabSelect(
            options = focusOptions.map { stringResource(focusLabel(it)) },
            selectedIndex = focusOptions.indexOf(bus.focus),
            onSelect = { store.setFocus(bus.id, focusOptions[it]) },
            contentDescription = stringResource(R.string.mixer_bus_focus),
        )
        if (bus.focus.surroundOnly) {
            Text(stringResource(R.string.mixer_focus_surround_note), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        LabSectionLabel(stringResource(R.string.mixer_routing), Modifier.padding(horizontal = 6.dp))
        LabPanel {
            SendSlider(store, bus, MixerNative.Master, stringResource(R.string.mixer_master))
            for (other in others) {
                if (!state.value.canSend(bus.id, other.id) && (bus.sends[other.id] ?: 0f) <= 0f) continue
                SendSlider(store, bus, other.id, busLabel(other))
            }
        }
        Spacer(Modifier.height(12.dp))
        LabSectionLabel(stringResource(R.string.mixer_bus_colour), Modifier.padding(horizontal = 6.dp))
        LabChips(
            chips = (0 until MixerStore.BusColors).map { LabChip(it.toString(), "", dot = LabColors.node(it)) },
            selectedKey = bus.color.toString(),
            onSelect = { store.setBusColor(bus.id, it.toInt()) },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            LabTextAction(stringResource(R.string.mixer_bus_name_hint), R.drawable.edit) { renaming = true }
            Spacer(Modifier.weight(1f))
            LabTextAction(stringResource(R.string.mixer_remove_bus), R.drawable.delete, enabled = state.value.buses.size > 1) { store.removeBus(bus.id) }
        }
    }
    if (renaming) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.mixer_bus_name_hint)) },
            initialTextFieldValue = TextFieldValue(bus.name),
            isInputValid = { true },
            onDone = { store.renameBus(bus.id, it) },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun SendSlider(store: MixerStore, bus: MixBus, to: Int, label: String) {
    val level = bus.sends[to] ?: 0f
    LabSlider(
        label = stringResource(R.string.mixer_send_to, label),
        valueText = "${(level * 100).roundToInt()}%",
        value = level,
        onValueChange = { store.setSend(bus.id, to, it) },
        valueRange = 0f..1f,
    )
}

/** what a bus is called on its strip and page: its own name, or its number */
@Composable
fun busLabel(bus: MixBus): String = when {
    bus.isMaster -> stringResource(R.string.mixer_master)
    bus.name.isNotBlank() -> bus.name
    else -> stringResource(R.string.mixer_bus_name, bus.id + 1)
}

fun busColor(bus: MixBus) = if (bus.isMaster) MasterColor else LabColors.node(bus.color)

val MasterColor = androidx.compose.ui.graphics.Color(0xFFF2F2F7)

fun focusLabel(focus: BusFocus): Int = when (focus) {
    BusFocus.All -> R.string.mixer_focus_all
    BusFocus.Mid -> R.string.mixer_focus_mid
    BusFocus.Side -> R.string.mixer_focus_side
    BusFocus.Left -> R.string.mixer_focus_left
    BusFocus.Right -> R.string.mixer_focus_right
    BusFocus.Centre -> R.string.mixer_focus_centre
    BusFocus.Lfe -> R.string.mixer_focus_lfe
    BusFocus.Rear -> R.string.mixer_focus_rear
    BusFocus.Surround -> R.string.mixer_focus_surround
    BusFocus.TopFront -> R.string.mixer_focus_top_front
    BusFocus.TopRear -> R.string.mixer_focus_top_rear
}
