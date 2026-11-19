// the card under the room: the switch, a chip for every sound, and exact controls for whichever one is picked

package com.mochyy.mochymusic.ui.screens.audiolab.stage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.stage.StageBox
import com.mochyy.mochymusic.eq.stage.StageListener
import com.mochyy.mochymusic.eq.stage.StageObject
import com.mochyy.mochymusic.eq.stage.StageSettings
import com.mochyy.mochymusic.eq.stage.StageSource
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabChip
import com.mochyy.mochymusic.ui.screens.audiolab.LabChips
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabPanel
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.screens.audiolab.pct
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.AudioLabViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private const val YouKey = "you"

@Composable
internal fun StageControlsCard(
    viewModel: AudioLabViewModel,
    stage: State<StageSettings>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val enabled by remember { derivedStateOf { stage.value.enabled } }
    LabCard(padding = 14.dp) {
        LabToggleRow(
            title = stringResource(R.string.stage_card_title),
            subtitle = stringResource(R.string.stage_toggle_sub),
            checked = enabled,
            onCheckedChange = { on -> viewModel.updateStage { it.copy(enabled = on) } },
        )
        Spacer(Modifier.height(10.dp))
        SourceChips(stage, selected, onSelect)
        Spacer(Modifier.height(10.dp))
        if (selected == ListenerKey) ListenerControls(viewModel, stage)
        else if (selected != null) ObjectControls(viewModel, stage, selected)
    }
}

@Composable
private fun SourceChips(stage: State<StageSettings>, selected: Int?, onSelect: (Int?) -> Unit) {
    val objects by remember { derivedStateOf { stage.value.objects.map { it.id to it.source } } }
    val names = StageSource.entries.associateWith { stringResource(sourceName(it)) }
    val labels = labelsOf(objects, names, stringResource(R.string.stage_source_numbered))
    val chips = objects.map { (id, source) -> LabChip(id.toString(), labels.getValue(id), sourceColor(source)) } +
        LabChip(YouKey, stringResource(R.string.stage_you), Color.White)
    LabChips(
        chips = chips,
        selectedKey = when (selected) {
            null -> null
            ListenerKey -> YouKey
            else -> selected.toString()
        },
        onSelect = { key -> onSelect(if (key == YouKey) ListenerKey else key.toIntOrNull()) },
    )
}

@Composable
private fun ObjectControls(viewModel: AudioLabViewModel, stage: State<StageSettings>, id: Int) {
    val target by remember(id) { derivedStateOf { stage.value.objects.firstOrNull { it.id == id } } }
    val box by remember { derivedStateOf { stage.value.box } }
    val removable by remember(id) {
        derivedStateOf { target?.let { t -> stage.value.objects.count { it.source == t.source } > 1 } == true }
    }
    val o = target ?: return
    fun change(transform: (StageObject) -> StageObject) = viewModel.updateStageObject(id, transform)

    LabPanel {
        LabSlider(stringResource(R.string.stage_axis_side), sideText(o.x, box), o.x, { v -> change { it.copy(x = v) } }, -1f..1f)
        LabSlider(stringResource(R.string.stage_axis_height), heightText(o.y, box), o.y, { v -> change { it.copy(y = v) } }, 0f..1f)
        LabSlider(stringResource(R.string.stage_axis_depth), depthText(o.z, box), o.z, { v -> change { it.copy(z = v) } }, -1f..1f)
        LabSlider(stringResource(R.string.stage_level), dbText(o.levelDb), o.levelDb,
            { v -> change { it.copy(levelDb = (v * 2).roundToInt() / 2f) } }, -24f..6f)
        if (o.source == StageSource.Ambience) {
            LabSlider(stringResource(R.string.stage_width), pct(o.width), o.width, { v -> change { it.copy(width = v) } }, 0f..1f)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StagePill(stringResource(R.string.stage_mute), o.muted) { change { it.copy(muted = !it.muted) } }
        StagePill(stringResource(R.string.stage_solo), o.solo) { change { it.copy(solo = !it.solo) } }
        Spacer(Modifier.weight(1f))
        if (removable) LabTextAction(stringResource(R.string.stage_remove), R.drawable.delete) { viewModel.removeStageObject(id) }
    }
}

@Composable
private fun ListenerControls(viewModel: AudioLabViewModel, stage: State<StageSettings>) {
    val listener by remember { derivedStateOf { stage.value.listener } }
    val box by remember { derivedStateOf { stage.value.box } }
    fun change(transform: (StageListener) -> StageListener) = viewModel.updateListener(transform)

    LabPanel {
        LabSlider(stringResource(R.string.stage_axis_side), sideText(listener.x, box), listener.x, { v -> change { it.copy(x = v) } }, -1f..1f)
        LabSlider(stringResource(R.string.stage_axis_height), heightText(listener.y, box), listener.y, { v -> change { it.copy(y = v) } }, 0f..1f)
        LabSlider(stringResource(R.string.stage_axis_depth), depthText(listener.z, box), listener.z, { v -> change { it.copy(z = v) } }, -1f..1f)
        LabSlider(stringResource(R.string.stage_facing), facingText(listener.yaw), listener.yaw,
            { v -> change { it.copy(yaw = v.roundToInt().toFloat()) } }, -180f..180f)
    }
}

/** a small on/off pill, white while on, like the chips */
@Composable
internal fun StagePill(label: String, on: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (on) Color.White else LabColors.Chip, tween(180), label = "pillBg")
    val foreground by animateColorAsState(if (on) Color.Black else Color.White, tween(180), label = "pillFg")
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = foreground)
    }
}

private fun meters(value: Float) = String.format("%.1f", value)

@Composable
private fun sideText(x: Float, box: StageBox): String {
    val m = x * box.width / 2
    return when {
        abs(m) < 0.05f -> stringResource(R.string.stage_middle)
        m < 0 -> stringResource(R.string.stage_meters_left, meters(-m))
        else -> stringResource(R.string.stage_meters_right, meters(m))
    }
}

@Composable
private fun heightText(y: Float, box: StageBox): String = stringResource(R.string.stage_meters_up, meters(y * box.height))

@Composable
private fun depthText(z: Float, box: StageBox): String {
    val m = z * box.depth / 2
    return when {
        abs(m) < 0.05f -> stringResource(R.string.stage_middle)
        m < 0 -> stringResource(R.string.stage_meters_back, meters(-m))
        else -> stringResource(R.string.stage_meters_front, meters(m))
    }
}

@Composable
private fun facingText(yaw: Float): String {
    val degrees = yaw.roundToInt()
    return when {
        degrees == 0 -> stringResource(R.string.stage_facing_ahead)
        degrees < 0 -> stringResource(R.string.stage_facing_left, -degrees)
        else -> stringResource(R.string.stage_facing_right, degrees)
    }
}

internal fun dbText(db: Float): String = (if (db > 0) "+" else if (db < 0) "−" else "") + String.format("%.1f dB", abs(db))
