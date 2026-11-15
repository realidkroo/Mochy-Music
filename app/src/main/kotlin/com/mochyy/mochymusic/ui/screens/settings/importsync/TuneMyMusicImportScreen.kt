package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel

private const val TUNEMYMUSIC_URL = "https://www.tunemymusic.com/transfer"

@Composable
fun TuneMyMusicImportScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val csvPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.loadFile(uri, sourceLabel = "TuneMyMusic")
        }

    SubSettingsScaffold(
        title = stringResource(R.string.import_sync_tunemymusic_title),
        onBack = { navController.navigateUp() },
    ) {
        ImportInfoCard {
            Text(
                stringResource(R.string.tunemymusic_info_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Step(1, stringResource(R.string.tunemymusic_step_1))
                Step(2, stringResource(R.string.tunemymusic_step_2))
                Step(3, stringResource(R.string.tunemymusic_step_3))
                Step(4, stringResource(R.string.tunemymusic_step_4))
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.tunemymusic_open_site)) },
                        descriptionText = stringResource(R.string.tunemymusic_open_site_desc),
                        icon = painterResource(R.drawable.web_link),
                        iconShape = CircleShape,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TUNEMYMUSIC_URL)))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, context.getString(R.string.tunemymusic_no_browser), Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.tunemymusic_choose_file)) },
                        descriptionText = stringResource(R.string.tunemymusic_choose_file_desc),
                        icon = painterResource(R.drawable.playlist_add),
                        iconShape = CircleShape,
                        onClick = {
                            csvPicker.launch(
                                arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"),
                            )
                        },
                    ),
                ),
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
