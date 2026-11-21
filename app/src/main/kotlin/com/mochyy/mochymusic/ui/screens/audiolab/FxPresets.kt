// the presets on top of audio fx: every effect saved under a name, picked with a tap, and kept up to date as you change things

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.AudioLabViewModel

@Composable
internal fun FxPresetsSection(viewModel: AudioLabViewModel) {
    val presets by viewModel.fxPresets.collectAsState()
    val pickedId by viewModel.fxPresetId.collectAsState()
    val sheets = LocalZoomOutOverlayState.current

    fun edit(id: String) {
        val preset = presets.firstOrNull { it.id == id } ?: return
        sheets.showEditPresetSheet(
            place = FxPresetPlace,
            name = preset.name,
            takenNames = presets.map { it.name },
            onRename = { viewModel.renameFxPreset(id, it) },
            onDelete = { viewModel.deleteFxPreset(id) },
        )
    }

    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.fx_presets),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        pickedId?.let { id -> LabTextAction(stringResource(R.string.edit), R.drawable.edit) { edit(id) } }
    }
    Spacer(Modifier.height(10.dp))
    LabChips(
        chips = presets.map { LabChip(it.id, it.name) },
        selectedKey = pickedId,
        // tapping the picked one again lets go of it, so you can try things without changing it
        onSelect = { id -> if (id == pickedId) viewModel.releaseFxPreset() else viewModel.applyFxPreset(id) },
        onLongPress = ::edit,
        leading = {
            AddPresetButton {
                sheets.showNewPresetSheet(FxPresetPlace, presets.map { it.name }, viewModel::saveFxPreset)
            }
        },
    )
}

// the round plus in front of the chips, the same height so the row reads as one
@Composable
private fun AddPresetButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(LabColors.Chip)
            .clickable(remember { MutableInteractionSource() }, RemoteFocus, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painterResource(R.drawable.add),
            contentDescription = stringResource(R.string.preset_sheet_new),
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
