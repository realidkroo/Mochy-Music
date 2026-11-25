// one effect in a bus's chain: its switch, its picture and meters, presets, and every knob it has

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.FxKind
import com.mochyy.mochymusic.eq.mixer.KindOrigin
import com.mochyy.mochymusic.eq.mixer.KnobScale
import com.mochyy.mochymusic.eq.mixer.MixerLabels
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.eq.mixer.MixerState
import com.mochyy.mochymusic.eq.mixer.MixerStore
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSegmented
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** an engine-written label in the phone's language when mochy has it, a module's or script's own text as it came otherwise */
@Composable
fun fxText(english: String): String = MixerLabels.of(english)?.let { stringResource(it) } ?: english

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotCard(
    uid: Long,
    state: State<MixerState>,
    store: MixerStore,
    position: Int,
    count: Int,
    accent: Color,
    onEditScript: () -> Unit,
) {
    // only this slot's own changes recompose it, not a knob turned on the card above
    val slot by remember(uid) { derivedStateOf { state.value.slot(uid) } }
    val builtins by store.builtins.collectAsState()
    val current = slot ?: return
    val kind = remember(current.kind, current.script, builtins) { store.kindFor(current) }
    val problem = rememberSlotProblem(uid)

    LabCard(padding = 14.dp) {
        SlotHeader(
            position = position,
            name = kind?.let { fxText(it.name) } ?: stringResource(R.string.mixer_slot_missing),
            origin = (kind?.origin as? KindOrigin.Module)?.moduleName,
            on = !current.bypass,
            accent = accent,
            onToggle = { store.setBypass(uid, !it) },
            onUp = if (position > 0) ({ store.moveSlot(busIdOf(state.value, uid), position, position - 1) }) else null,
            onDown = if (position < count - 1) ({ store.moveSlot(busIdOf(state.value, uid), position, position + 1) }) else null,
            onRemove = { store.removeSlot(uid) },
        )
        if (kind == null) {
            SlotNote(stringResource(R.string.mixer_slot_missing_body))
            return@LabCard
        }
        problem.value?.let { code -> ProblemNote(code, onResume = { store.resume(uid); problem.value = null }) }
        if (kind.description.isNotBlank()) {
            Text(
                text = fxText(kind.description),
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = LabColors.Secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        val values = { kind.fit(slot?.values.orEmpty()) }
        if (kind.showsTail) {
            Spacer(Modifier.height(10.dp))
            DecayGraph(kind, current.script ?: kind.engineSource, values, onKnob = { i, v -> store.setValue(uid, i, v) }, accent)
        }
        if (kind.meters.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SlotMeters(uid, kind, accent)
        }
        if (kind.presets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val picked by remember(kind) { derivedStateOf { kind.presets.indexOfFirst { p -> kind.fit(p.values) == values() } } }
            LabChips(
                chips = kind.presets.mapIndexed { i, p -> LabChip(i.toString(), fxText(p.name)) },
                selectedKey = picked.takeIf { it >= 0 }?.toString(),
                onSelect = { key -> kind.presets.getOrNull(key.toInt())?.let { store.setValues(uid, it.values) } },
            )
        }
        Spacer(Modifier.height(12.dp))
        SlotKnobs(kind, values, accent) { i, v -> store.setValue(uid, i, v) }
        if (current.isScript) {
            Spacer(Modifier.height(10.dp))
            ScriptFooter(uid, onEditScript)
        }
    }
}

@Composable
private fun SlotHeader(
    position: Int,
    name: String,
    origin: String?,
    on: Boolean,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) accent else LabColors.Row), contentAlignment = Alignment.Center) {
            Text("${position + 1}", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) Color.Black else LabColors.Secondary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LabColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (origin != null) Text(origin, fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, maxLines = 1)
        }
        HeaderIcon(R.drawable.arrow_upward, stringResource(R.string.mixer_move_up), onUp)
        HeaderIcon(R.drawable.arrow_downward, stringResource(R.string.mixer_move_down), onDown)
        HeaderIcon(R.drawable.close, stringResource(R.string.mixer_remove_effect), onRemove)
        Spacer(Modifier.width(4.dp))
        AppSwitch(checked = on, onCheckedChange = onToggle)
    }
}

@Composable
private fun HeaderIcon(icon: Int, description: String, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = if (onClick != null) LabColors.Secondary else LabColors.Faint.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

/** dials for the continuous knobs, a picker or a switch for the rest, in the order the effect lists them */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotKnobs(kind: FxKind, values: () -> List<Float>, accent: Color, onChange: (Int, Float) -> Unit) {
    val dials = kind.knobs.withIndex().filter { (_, k) -> k.scale == KnobScale.Linear || k.scale == KnobScale.Log || k.scale == KnobScale.Int }
    val others = kind.knobs.withIndex().filter { (_, k) -> k.scale == KnobScale.Choice || k.scale == KnobScale.Toggle }
    for ((i, knob) in others) {
        val value = values().getOrElse(i) { knob.default }
        if (knob.scale == KnobScale.Toggle) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fxText(knob.name), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LabColors.Primary, modifier = Modifier.weight(1f))
                AppSwitch(checked = value > 0.5f, onCheckedChange = { onChange(i, if (it) 1f else 0f) })
            }
        } else {
            val labels = knob.choices.map { fxText(it) }
            Text(fxText(knob.name), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LabColors.Secondary, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp))
            // three or fewer fit side by side; longer lists unroll under a field
            if (labels.size in 2..3) {
                LabSegmented(labels, value.toInt().coerceIn(0, labels.lastIndex), { onChange(i, it.toFloat()) }, height = 40.dp)
            } else {
                LabSelect(labels, value.toInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0)), { onChange(i, it.toFloat()) }, contentDescription = fxText(knob.name))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    if (dials.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for ((i, knob) in dials) {
            FxKnobControl(knob = knob, label = fxText(knob.name), value = { values().getOrElse(i) { knob.default } }, onChange = { onChange(i, it) }, accent = accent)
        }
    }
}

/** the effect's own readings, like a compressor's gain reduction, as bars that move in the draw phase */
@Composable
private fun SlotMeters(uid: Long, kind: FxKind, accent: Color) {
    val readings = remember(uid) { FloatArray(1 + MixerNative.MaxMeters) }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(uid) {
        MixerNative.metering(true)
        try {
            while (isActive) {
                MixerNative.slotReadings(uid, readings)
                tick.longValue++
                delay(33)
            }
        } finally {
            MixerNative.metering(false)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kind.meters.take(MixerNative.MaxMeters).forEachIndexed { i, meter ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fxText(meter.name), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, modifier = Modifier.width(84.dp))
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    tick.longValue
                    val v = readings[1 + i]
                    val span = meter.max - meter.min
                    // a reduction meter fills from the right, the way a compressor's needle drops
                    val fraction = if (span == 0f) 0f else ((meter.max - v) / span).coerceIn(0f, 1f)
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(LabColors.StageLine, cornerRadius = r)
                    drawRoundRect(accent, Offset(size.width * (1f - fraction), 0f), Size(size.width * fraction, size.height), cornerRadius = r)
                }
                Spacer(Modifier.width(8.dp))
                MeterValue(tick, readings, 1 + i, meter.unit)
            }
        }
    }
}

@Composable
private fun MeterValue(tick: androidx.compose.runtime.MutableLongState, readings: FloatArray, index: Int, unit: String) {
    // the number only needs a few updates a second, the bar beside it moves every frame
    val bucket by remember { derivedStateOf { tick.longValue / 6 } }
    val text = remember(bucket, unit) { "%.1f %s".format(readings[index], unit).trim() }
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = LabColors.Primary,
        modifier = Modifier.width(58.dp),
    )
}

@Composable
private fun ScriptFooter(uid: Long, onEdit: () -> Unit) {
    var showConsole by remember { mutableStateOf(false) }
    var console by remember { mutableStateOf("") }
    LaunchedEffect(uid, showConsole) {
        if (!showConsole) return@LaunchedEffect
        while (isActive) {
            val fresh = MixerNative.console(uid)
            if (fresh.isNotEmpty()) console = (console + fresh).takeLast(4000)
            delay(400)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LabTextAction(stringResource(if (showConsole) R.string.mixer_console_hide else R.string.mixer_console_show)) { showConsole = !showConsole }
        Spacer(Modifier.weight(1f))
        LabTextAction(stringResource(R.string.mixer_edit_script), R.drawable.edit, onClick = onEdit)
    }
    if (showConsole) {
        Text(
            text = console.ifEmpty { stringResource(R.string.mixer_console_empty) },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = LabColors.Secondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LabColors.Stage)
                .padding(10.dp),
        )
    }
}

@Composable
private fun SlotNote(text: String) {
    Text(text, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 17.sp, color = LabColors.Secondary, modifier = Modifier.padding(6.dp))
}

@Composable
private fun ProblemNote(code: String, onResume: () -> Unit) {
    val text = when {
        code.startsWith("compile:") -> stringResource(R.string.mixer_problem_compile, code.removePrefix("compile:"))
        code == "too_slow" -> stringResource(R.string.mixer_problem_slow)
        code == "bad_numbers" -> stringResource(R.string.mixer_problem_numbers)
        code == "missing_sample" -> stringResource(R.string.mixer_problem_no_sample)
        code == "missing_module" -> stringResource(R.string.mixer_slot_missing_body)
        code == "no_memory" -> stringResource(R.string.mixer_problem_memory)
        else -> stringResource(R.string.mixer_problem_other)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33F26B6B))
            .padding(12.dp),
    ) {
        Text(text, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 17.sp, color = LabColors.Primary)
        if (code == "too_slow") {
            Spacer(Modifier.height(6.dp))
            LabTextAction(stringResource(R.string.mixer_problem_resume), onClick = onResume)
        }
    }
}

/** why the engine couldn't run this slot, checked every second while the card is shown */
@Composable
private fun rememberSlotProblem(uid: Long): androidx.compose.runtime.MutableState<String?> {
    val problem = remember(uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        while (isActive) {
            problem.value = MixerNative.slotProblem(uid)
            delay(1000)
        }
    }
    return problem
}

private fun busIdOf(state: MixerState, uid: Long): Int =
    (state.buses + state.master).firstOrNull { b -> b.slots.any { it.uid == uid } }?.id ?: 0
