package com.mochyy.mochymusic.ui.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel
import java.time.LocalDate

/** experimental hub: import from other services, sync a youtube account, tunemymusic csv, and backup */
@Composable
fun ImportSyncScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.importProgress.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val signedIn = rememberYouTubeSignedIn()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) viewModel.export(uri)
        }

    SubSettingsScaffold(
        title = stringResource(R.string.import_sync_title),
        onBack = { navController.navigateUp() },
    ) {
        if (progress.isRunning || progress.isDone) {
            val resync = progress.resync
            ImportInfoCard {
                Text(
                    text =
                        when {
                            resync != null && progress.isRunning -> {
                                stringResource(R.string.import_sync_progress_resyncing, resync.playlistName)
                            }

                            progress.isRunning -> {
                                stringResource(
                                    R.string.import_sync_progress_importing_count,
                                    progress.processedTracks,
                                    progress.totalTracks,
                                )
                            }

                            progress.cancelled -> {
                                stringResource(R.string.import_sync_progress_stopped)
                            }

                            resync != null && resync.failed.size < resync.playlists -> {
                                val countText =
                                    if (resync.added == 1) {
                                        stringResource(R.string.import_sync_progress_new_songs_single)
                                    } else {
                                        stringResource(R.string.import_sync_progress_new_songs_multiple, resync.added)
                                    }
                                if (resync.playlists == 1) {
                                    stringResource(R.string.import_sync_progress_resynced_single, resync.playlistName, countText)
                                } else {
                                    stringResource(R.string.import_sync_progress_resynced_multiple, resync.playlists, countText)
                                }
                            }

                            progress.error != null -> {
                                stringResource(R.string.import_sync_progress_failed)
                            }

                            else -> {
                                stringResource(R.string.import_sync_progress_finished_count, progress.matchedTracks, progress.totalTracks)
                            }
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progress.isRunning && progress.totalTracks > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.processedTracks.toFloat() / progress.totalTracks },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items =
                    listOf(
                        SettingsItem(
                            title = {
                                Text(
                                    stringResource(
                                        if (progress.isRunning) R.string.import_sync_see_progress else R.string.import_sync_see_results,
                                    ),
                                )
                            },
                            icon = painterResource(R.drawable.playlist_play),
                            iconShape = CircleShape,
                            onClick = { navController.navigate(ImportProgressRoute) { launchSingleTop = true } },
                        ),
                    ),
            )
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_sync_import_title)) },
                        highlightKey = "Import",
                        descriptionText = stringResource(R.string.import_sync_import_desc),
                        detail = stringResource(R.string.import_sync_import_detail),
                        icon = painterResource(R.drawable.playlist_add),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ImportProvidersRoute) },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_sync_sync_title)) },
                        highlightKey = "Sync",
                        descriptionText =
                            if (signedIn) {
                                stringResource(R.string.import_sync_sync_desc_connected)
                            } else {
                                stringResource(R.string.import_sync_sync_desc_disconnected)
                            },
                        icon = painterResource(R.drawable.sync),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(YouTubeSyncRoute) },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.tv_link_title)) },
                        highlightKey = "Mochy on your TV",
                        descriptionText = stringResource(R.string.tv_link_row_detail),
                        icon = painterResource(R.drawable.smartphone),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(com.mochyy.mochymusic.ui.screens.settings.tvlink.TvLinkRoute) },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_sync_tunemymusic_title)) },
                        highlightKey = "TuneMyMusic CSV",
                        descriptionText = stringResource(R.string.import_sync_tunemymusic_desc),
                        detail = stringResource(R.string.import_sync_tunemymusic_detail),
                        icon = painterResource(R.drawable.web_link),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.import_sync_backup_title),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_sync_export_csv_title)) },
                        highlightKey = "Export to Mochy CSV",
                        descriptionText = stringResource(R.string.import_sync_export_csv_desc),
                        detail = stringResource(R.string.import_sync_export_csv_detail),
                        icon = painterResource(R.drawable.backup),
                        iconShape = CircleShape,
                        onClick = { exportLauncher.launch("mochy-library-${LocalDate.now()}.csv") },
                    ),
                ),
        )
    }

    exportMessage?.let { message ->
        val working = message == "Exporting…"
        DefaultDialog(
            onDismiss = { if (!working) viewModel.clearExportMessage() },
            buttons = {
                if (!working) TextButton(onClick = viewModel::clearExportMessage) { Text(stringResource(R.string.import_sync_btn_ok)) }
            },
        ) {
            Text(if (working) stringResource(R.string.import_sync_exporting) else message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
