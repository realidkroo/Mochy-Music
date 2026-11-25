// one channel's effects chain and channel settings, reused both as its own page and as the right pane on tablets

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabCollapsible
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabPanel
import com.mochyy.mochymusic.ui.screens.audiolab.LabSectionLabel
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
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

    SubSettingsScaffold(
        title = label,
        backLabel = stringResource(R.string.mixer_card_title),
        onBack = { navController.navigateUp() },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 720.dp && !current.isMaster) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(300.dp)) {
                        BusSettingsCard(store, state, current, collapsible = false)
                    }
                    Column(Modifier.weight(1f)) {
                        ChannelEffectsChain(
                            busId = busId,
                            bus = current,
                            state = state,
                            store = store,
                            viewModel = viewModel,
                            navController = navController,
                            showChannelHeader = false,
                        )
                    }
                }
            } else {
                ChannelDetailPane(
                    busId = busId,
                    bus = current,
                    state = state,
                    store = store,
                    viewModel = viewModel,
                    navController = navController,
                    showChannelHeader = false,
                )
            }
        }
    }
}

/** self-contained channel panel used on phone BusScreen and on the right half of tablet MixerScreen */
@Composable
internal fun ChannelDetailPane(
    busId: Int,
    bus: MixBus,
    state: State<MixerState>,
    store: MixerStore,
    viewModel: MixerViewModel,
    navController: NavController,
    showChannelHeader: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        ChannelEffectsChain(
            busId = busId,
            bus = bus,
            state = state,
            store = store,
            viewModel = viewModel,
            navController = navController,
            showChannelHeader = showChannelHeader,
        )
        if (!bus.isMaster) {
            BusSettingsCard(store, state, bus, collapsible = true)
        }
    }
}

@Composable
private fun ChannelEffectsChain(
    busId: Int,
    bus: MixBus,
    state: State<MixerState>,
    store: MixerStore,
    viewModel: MixerViewModel,
    navController: NavController,
    showChannelHeader: Boolean,
) {
    val accent = busColor(bus)
    val label = busLabel(bus)
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
    val openAddSheet: () -> Unit = {
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

    if (showChannelHeader) {
        LabCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = LabColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LabTextAction(
                    stringResource(R.string.mixer_add_effect),
                    R.drawable.add,
                    enabled = slotIds.size < MixerStore.MaxSlots,
                    onClick = openAddSheet,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    } else {
        LabSectionLabel(stringResource(R.string.mixer_effects), Modifier.padding(horizontal = 6.dp)) {
            LabTextAction(
                stringResource(R.string.mixer_add_effect),
                R.drawable.add,
                enabled = slotIds.size < MixerStore.MaxSlots,
                onClick = openAddSheet,
            )
        }
    }

    if (slotIds.isEmpty()) {
        val emptyShape = RoundedCornerShape(22.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(emptyShape)
                .background(LabColors.Card)
                .border(1.dp, LabColors.StageLine, emptyShape)
                .clickable(onClick = openAddSheet)
                .padding(vertical = 22.dp, horizontal = 16.dp),
        ) {
            Icon(painterResource(R.drawable.add), null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.mixer_add_effect),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = LabColors.Primary,
            )
        }
        Spacer(Modifier.height(14.dp))
    } else {
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
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BusSettingsCard(
    store: MixerStore,
    state: State<MixerState>,
    bus: MixBus,
    collapsible: Boolean,
) {
    var expanded by rememberSaveable(bus.id, collapsible) { mutableStateOf(!collapsible) }
    var renaming by rememberSaveable(bus.id) { mutableStateOf(false) }
    val focusOptions = BusFocus.entries
    val others by remember(bus.id) { derivedStateOf { state.value.buses.filter { it.id != bus.id } } }
    val focusText = stringResource(focusLabel(bus.focus))

    LabCard(padding = 14.dp) {
        if (collapsible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Icon(painterResource(R.drawable.tune), null, tint = busColor(bus), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.mixer_bus_settings),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = LabColors.Primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    focusText,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = LabColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                    null,
                    tint = LabColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            LabSectionLabel(stringResource(R.string.mixer_bus_settings), Modifier.padding(horizontal = 6.dp))
        }

        LabCollapsible(visible = expanded) {
            if (collapsible) Spacer(Modifier.height(8.dp))
            LabToggleRow(
                title = stringResource(R.string.mixer_bus_input),
                subtitle = null,
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

/** what a channel is called on its strip and page: its own name, or its number */
@Composable
fun busLabel(bus: MixBus): String = when {
    bus.isMaster -> stringResource(R.string.mixer_master)
    bus.name.isNotBlank() -> bus.name
    else -> stringResource(R.string.mixer_bus_name, bus.id + 1)
}

fun busColor(bus: MixBus) = if (bus.isMaster) MasterColor else LabColors.node(bus.color)

val MasterColor = Color(0xFFF2F2F7)

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
