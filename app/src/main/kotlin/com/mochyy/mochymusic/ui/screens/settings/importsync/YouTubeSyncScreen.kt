package com.mochyy.mochymusic.ui.screens.settings.importsync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AccountEmailKey
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.YtExcludeImportsKey
import com.mochyy.mochymusic.constants.YtPullHistoryKey
import com.mochyy.mochymusic.constants.YtReportPlaysKey
import com.mochyy.mochymusic.constants.YtSyncNewPlaylistsKey
import com.mochyy.mochymusic.constants.YtmSyncKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel

@Composable
fun YouTubeSyncScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val signedIn = rememberYouTubeSignedIn()
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val (autoSync, onAutoSyncChange) = rememberPreference(YtmSyncKey, true)
    val (syncNewPlaylists, onSyncNewPlaylistsChange) = rememberPreference(YtSyncNewPlaylistsKey, false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, true)
    val (pullHistory, onPullHistoryChange) = rememberPreference(YtPullHistoryKey, false)
    val (excludeImports, onExcludeImportsChange) = rememberPreference(YtExcludeImportsKey, false)
    val state by viewModel.youTubeState.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmUpload by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    SubSettingsScaffold(
        title = stringResource(R.string.import_sync_sync_title),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = stringResource(R.string.yt_sync_account_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    if (signedIn) {
                        SettingsItem(
                            title = { Text(accountName.ifBlank { stringResource(R.string.yt_sync_signed_in) }) },
                            descriptionText = accountEmail.ifBlank { "YouTube Music" },
                            icon = painterResource(R.drawable.account),
                            iconShape = CircleShape,
                            trailingContent = {
                                TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.yt_sync_sign_out)) }
                            },
                        )
                    } else {
                        SettingsItem(
                            title = { Text(stringResource(R.string.yt_sync_sign_in_title)) },
                            descriptionText = stringResource(R.string.yt_sync_sign_in_desc),
                            icon = painterResource(R.drawable.login),
                            iconShape = CircleShape,
                            onClick = { navController.navigate(YouTubeLoginRoute) },
                        )
                    },
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.yt_sync_keep_in_sync_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    toggleItem(
                        title = stringResource(R.string.yt_sync_auto_sync_title),
                        description = stringResource(R.string.yt_sync_auto_sync_desc),
                        icon = R.drawable.sync,
                        checked = autoSync,
                        enabled = signedIn,
                        onChange = onAutoSyncChange,
                    ),
                    toggleItem(
                        title = stringResource(R.string.yt_sync_new_playlists_title),
                        description = stringResource(R.string.yt_sync_new_playlists_desc),
                        icon = R.drawable.playlist_add,
                        checked = syncNewPlaylists,
                        enabled = signedIn,
                        onChange = onSyncNewPlaylistsChange,
                    ),
                    toggleItem(
                        title = stringResource(R.string.yt_sync_report_plays_title),
                        description = stringResource(R.string.yt_sync_report_plays_desc),
                        icon = R.drawable.history,
                        checked = reportPlays,
                        enabled = signedIn,
                        onChange = onReportPlaysChange,
                    ),
                    toggleItem(
                        title = stringResource(R.string.yt_sync_pull_history_title),
                        description = stringResource(R.string.yt_sync_pull_history_desc),
                        icon = R.drawable.history,
                        checked = pullHistory,
                        enabled = signedIn,
                        onChange = onPullHistoryChange,
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.yt_sync_other_apps_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    toggleItem(
                        title = stringResource(R.string.yt_sync_exclude_title),
                        description = stringResource(R.string.yt_sync_exclude_desc),
                        icon = R.drawable.cloud,
                        checked = excludeImports,
                        enabled = true,
                        onChange = onExcludeImportsChange,
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.yt_sync_now_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync_now_title)) },
                        descriptionText =
                            if (pullHistory) {
                                stringResource(
                                    R.string.yt_sync_now_desc_history,
                                )
                            } else {
                                stringResource(R.string.yt_sync_now_desc_no_history)
                            },
                        icon = painterResource(R.drawable.refresh),
                        iconShape = CircleShape,
                        enabled = signedIn && !state.running,
                        onClick = viewModel::syncNow,
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync_pull_history_now_title)) },
                        descriptionText = stringResource(R.string.yt_sync_pull_history_now_desc),
                        icon = painterResource(R.drawable.history),
                        iconShape = CircleShape,
                        enabled = signedIn && !state.running,
                        onClick = viewModel::pullHistoryNow,
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync_undo_title)) },
                        descriptionText = stringResource(R.string.yt_sync_undo_desc),
                        icon = painterResource(R.drawable.undo),
                        iconShape = CircleShape,
                        enabled = signedIn && state.canUndo && !state.running,
                        onClick = { confirmUndo = true },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync_upload_playlists_title)) },
                        descriptionText = stringResource(R.string.yt_sync_upload_playlists_desc),
                        detail = stringResource(R.string.yt_sync_upload_playlists_detail),
                        icon = painterResource(R.drawable.cloud),
                        iconShape = CircleShape,
                        enabled = signedIn && !state.running,
                        onClick = { confirmUpload = true },
                    ),
                ),
        )

        if (state.running || state.message != null) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                if (state.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(state.label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::clearYouTubeMessage) { Text(stringResource(R.string.import_sync_btn_ok)) }
                }
            }
        }

        if (!signedIn) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.yt_sync_no_account_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmSignOut) {
        DefaultDialog(
            onDismiss = { confirmSignOut = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.yt_sync_dialog_sign_out_title)) },
            buttons = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.yt_sync_cancel)) }
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.yt_sync_sign_out)) }
            },
        ) {
            Text(
                stringResource(R.string.yt_sync_dialog_sign_out_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmUndo) {
        DefaultDialog(
            onDismiss = { confirmUndo = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.yt_sync_undo_dialog_title)) },
            buttons = {
                TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.yt_sync_cancel)) }
                TextButton(onClick = {
                    confirmUndo = false
                    viewModel.undoLastSync(withYouTube = false)
                }) { Text(stringResource(R.string.yt_sync_undo_phone)) }
                TextButton(
                    enabled = signedIn,
                    onClick = {
                        confirmUndo = false
                        viewModel.undoLastSync(withYouTube = true)
                    },
                ) { Text(stringResource(R.string.yt_sync_undo_both)) }
            },
        ) {
            Text(
                stringResource(R.string.yt_sync_undo_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.yt_sync_undo_both_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmUpload) {
        DefaultDialog(
            onDismiss = { confirmUpload = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.yt_sync_dialog_upload_title)) },
            buttons = {
                TextButton(onClick = { confirmUpload = false }) { Text(stringResource(R.string.yt_sync_cancel)) }
                TextButton(onClick = {
                    confirmUpload = false
                    viewModel.uploadLocalPlaylists()
                }) { Text(stringResource(R.string.yt_sync_dialog_upload_create)) }
            },
        ) {
            Text(
                stringResource(R.string.yt_sync_dialog_upload_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = title,
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)
