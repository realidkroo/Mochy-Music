// the restore sheet: every backup in the cloud, newest first, pick one and mochy swaps it in and restarts

package com.mochyy.mochymusic.ui.screens.settings.integrations.backup

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.backup.CloudBackupFile
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.settings.storage.formatSize
import com.mochyy.mochymusic.viewmodels.CloudBackupList
import com.mochyy.mochymusic.viewmodels.CloudBackupViewModel

private val ProblemColor = Color(0xFFFF8A80)
private val RowShape = RoundedCornerShape(14.dp)

@Composable
internal fun RestoreBackupContent(viewModel: CloudBackupViewModel) {
    val list by viewModel.backups.collectAsState()
    val step by viewModel.step.collectAsState()
    var picked by remember { mutableStateOf<CloudBackupFile?>(null) }
    val progress = stepText(step)

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(stringResource(R.string.backup_restore_title), color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.backup_restore_body), color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(20.dp))

        val current = list
        when {
            // a restore that's running owns the sheet until mochy restarts
            progress != null -> Notice(progress)
            current is CloudBackupList.Loading -> Notice(stringResource(R.string.backup_restore_loading))
            current is CloudBackupList.Failed -> {
                Notice(current.message, color = ProblemColor)
                Spacer(Modifier.height(18.dp))
                SheetPillButton(text = stringResource(R.string.backup_try_again), onClick = viewModel::loadBackups)
            }
            current is CloudBackupList.Found && current.backups.isEmpty() -> Notice(stringResource(R.string.backup_restore_empty))
            current is CloudBackupList.Found -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    current.backups.forEach { backup ->
                        BackupRow(backup, isPicked = picked == backup, onClick = { picked = backup })
                    }
                }
                Spacer(Modifier.height(20.dp))
                SheetPillButton(
                    text = stringResource(R.string.backup_restore_this),
                    onClick = { picked?.let(viewModel::restore) },
                    enabled = picked != null,
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String, color: Color = SearchColors.Secondary) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}

// when it was made on top, which phone and how big underneath, ticked once picked
@Composable
private fun BackupRow(backup: CloudBackupFile, isPicked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val made = remember(backup.modifiedAt) {
        DateUtils.formatDateTime(
            context,
            backup.modifiedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(Color.White.copy(alpha = if (isPicked) 0.14f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(made, color = SearchColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.backup_restore_item, backup.device, formatSize(backup.bytes)),
                color = SearchColors.Secondary,
                fontSize = 12.sp,
            )
        }
        if (isPicked) {
            Icon(painterResource(R.drawable.check), contentDescription = null, tint = SearchColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}
