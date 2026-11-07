// storage → export downloaded songs: copies them as plain files into a mochy-preserve folder, sorted by playlist or by album

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.ExportChoice
import com.mochyy.mochymusic.playback.ExportLayout
import com.mochyy.mochymusic.playback.ExportStep
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.viewmodels.StorageViewModel

// what the export will do, shared by the options and the button under them
@Stable
private class ExportOptions {
    var layout by mutableStateOf(ExportLayout.Playlists)
    var covers by mutableStateOf(true)
    var lyrics by mutableStateOf(true)

    fun choice() = ExportChoice(layout, covers, lyrics)
}

fun PopupSheetState.showExportDownloadsSheet(viewModel: StorageViewModel) {
    val options = ExportOptions()
    // a finished or failed export from before shouldn't greet the next one
    viewModel.clearExport()
    show(
        buttonBar = {
            val step by viewModel.exportStep.collectAsState()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
                if (tree != null) viewModel.export(tree, options.choice())
            }
            val working = step as? ExportStep.Working
            SheetPillButton(
                text = when {
                    working == null -> stringResource(R.string.storage_export_pick)
                    working.total == 0 -> stringResource(R.string.storage_export_starting)
                    else -> stringResource(R.string.storage_export_progress, working.done + 1, working.total)
                },
                enabled = working == null,
                onClick = { picker.launch(null) },
            )
        },
    ) {
        ExportSheetContent(viewModel, options)
    }
}

@Composable
private fun ExportSheetContent(viewModel: StorageViewModel, options: ExportOptions) {
    val step by viewModel.exportStep.collectAsState()
    val locked = step is ExportStep.Working
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        SheetLabel(stringResource(R.string.storage_export_sheet_title))
        Text(
            stringResource(R.string.storage_export_note),
            style = storageText(13f, FontWeight.Medium, StorageMuted, -0.1f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutCard(
                icon = R.drawable.queue_music,
                title = stringResource(R.string.storage_export_layout_playlists),
                detail = stringResource(R.string.storage_export_layout_playlists_detail),
                selected = options.layout == ExportLayout.Playlists,
                enabled = !locked,
                onClick = { options.layout = ExportLayout.Playlists },
            )
            LayoutCard(
                icon = R.drawable.album,
                title = stringResource(R.string.storage_export_layout_albums),
                detail = stringResource(R.string.storage_export_layout_albums_detail),
                selected = options.layout == ExportLayout.Albums,
                enabled = !locked,
                onClick = { options.layout = ExportLayout.Albums },
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MenuRowSurface)
                .padding(vertical = 4.dp),
        ) {
            SwitchRow(R.drawable.image, stringResource(R.string.storage_export_covers), options.covers, !locked) { options.covers = it }
            SwitchRow(R.drawable.lyrics, stringResource(R.string.storage_export_lyrics), options.lyrics, !locked) { options.lyrics = it }
        }
        ExportStatus(step)
    }
}

@Composable
private fun ExportStatus(step: ExportStep) {
    val (text, color) = when (step) {
        is ExportStep.Done -> buildString {
            append(pluralStringResource(R.plurals.storage_export_done, step.songs, step.songs))
            if (step.skipped > 0) append(". ").append(pluralStringResource(R.plurals.storage_export_skipped, step.skipped, step.skipped))
        } to Color.White
        is ExportStep.Failed -> step.message to StorageDanger
        else -> return
    }
    Text(text, style = storageText(13f, FontWeight.SemiBold, color, -0.1f), modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp))
}

@Composable
private fun LayoutCard(icon: Int, title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .border(1.5.dp, if (selected) Color.White.copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = storageText(16f, FontWeight.SemiBold, tracking = -0.3f))
            Text(detail, style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
        }
    }
}

@Composable
private fun SwitchRow(icon: Int, label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(label, style = storageText(15f, FontWeight.SemiBold, tracking = -0.3f), modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RowIcon(icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
