// mochy → settings → integrations → backup: which cloud, back up now, the schedule, and bringing a backup back

package com.mochyy.mochymusic.ui.screens.settings.integrations.backup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.backup.CloudAccount
import com.mochyy.mochymusic.backup.CloudBackupStep
import com.mochyy.mochymusic.backup.CloudKind
import com.mochyy.mochymusic.backup.CloudLogin
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationPageSubtitle
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageDanger
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageMuted
import com.mochyy.mochymusic.ui.screens.settings.storage.formatSize
import com.mochyy.mochymusic.ui.screens.settings.storage.storageText
import com.mochyy.mochymusic.viewmodels.CloudBackupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BackupRoute = "integrations_settings/backup"

// onedrive stays off the page until its microsoft app is registered, the code behind it is ready
private val ShownClouds = listOf(CloudKind.Dropbox, CloudKind.WebDav)

@Composable
fun BackupScreen(
    navController: NavController,
    viewModel: CloudBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalZoomOutOverlayState.current
    val account by viewModel.account.collectAsState()
    val login by viewModel.login.collectAsState()
    val step by viewModel.step.collectAsState()

    LaunchedEffect(login) {
        (login as? CloudLogin.Failed)?.let {
            Toast.makeText(context, viewModel.messageOf(it.problem), Toast.LENGTH_LONG).show()
            viewModel.dismissLoginProblem()
        }
    }
    LaunchedEffect(step) {
        when (val now = step) {
            CloudBackupStep.BackedUp -> {
                Toast.makeText(context, context.getString(R.string.backup_done_toast, account?.kind?.label.orEmpty()), Toast.LENGTH_SHORT).show()
                viewModel.acknowledge()
            }
            is CloudBackupStep.Failed -> {
                Toast.makeText(context, now.message, Toast.LENGTH_LONG).show()
                viewModel.acknowledge()
            }
            else -> Unit
        }
    }
    // back from the browser without an answer, give the redirect a moment to land before the rows go back to sign in
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(1_500)
            viewModel.cancelWaiting()
        }
    }

    fun openWebDav() {
        var sheet: PopupSheetHandle? = null
        sheet = overlay.showWithHandle {
            WebDavSignInContent(
                viewModel = viewModel,
                current = account?.takeIf { it.kind == CloudKind.WebDav },
                onConnected = { host ->
                    sheet?.dismiss()
                    Toast.makeText(context, context.getString(R.string.backup_connected_toast, host), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    fun signIn(kind: CloudKind) {
        if (kind == CloudKind.WebDav) {
            openWebDav()
            return
        }
        scope.launch {
            val uri = viewModel.loginUri(kind)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                viewModel.cancelWaiting()
                Toast.makeText(context, context.getString(R.string.backup_no_browser), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openRestore() {
        viewModel.loadBackups()
        overlay.show { RestoreBackupContent(viewModel) }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.integration_backup_name),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.integrations),
    ) {
        IntegrationPageSubtitle(stringResource(R.string.backup_page_subtitle))
        CloudGroup(
            account = account,
            login = login,
            isConfigured = viewModel::isConfigured,
            onSignIn = ::signIn,
            onDisconnect = viewModel::disconnect,
        )
        Spacer(Modifier.height(16.dp))
        BackupsGroup(account = account, step = step, onBackUp = viewModel::backUpNow, onRestore = ::openRestore)
        account?.lastProblem?.takeIf { step == CloudBackupStep.Idle }?.let { problem ->
            Text(
                stringResource(R.string.backup_last_failed, viewModel.messageOf(problem)),
                style = storageText(13f, FontWeight.Medium, StorageDanger, -0.2f),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        BackupScheduleCards(connected = account != null)
        Spacer(Modifier.height(140.dp))
    }
}

/** a row per cloud, the connected one ticked, and a way to disconnect */
@Composable
private fun CloudGroup(
    account: CloudAccount?,
    login: CloudLogin,
    isConfigured: (CloudKind) -> Boolean,
    onSignIn: (CloudKind) -> Unit,
    onDisconnect: () -> Unit,
) {
    val clouds = ShownClouds.map { kind ->
        val connected = account?.kind == kind
        val configured = isConfigured(kind)
        SettingsItem(
            title = { Text(kind.label) },
            highlightKey = kind.label,
            searchText = kind.label,
            descriptionText = when {
                login is CloudLogin.InBrowser && login.kind == kind -> stringResource(R.string.backup_waiting_browser)
                login is CloudLogin.Finishing && login.kind == kind -> stringResource(R.string.backup_finishing_sign_in)
                connected && kind == CloudKind.WebDav -> stringResource(R.string.backup_webdav_connected, account?.name.orEmpty())
                connected -> stringResource(R.string.backup_signed_in_as, account?.name.orEmpty())
                !configured -> stringResource(R.string.backup_not_set_up)
                else -> stringResource(
                    when (kind) {
                        CloudKind.Dropbox -> R.string.backup_dropbox_desc
                        CloudKind.OneDrive -> R.string.backup_onedrive_desc
                        CloudKind.WebDav -> R.string.backup_webdav_desc
                    },
                )
            },
            icon = painterResource(if (kind == CloudKind.WebDav) R.drawable.storage else R.drawable.cloud),
            iconShape = CircleShape,
            enabled = configured,
            // tapping the connected one signs in again, to switch accounts or fix a login that stopped working
            onClick = { onSignIn(kind) },
            trailingContent = if (connected) {
                { Icon(painterResource(R.drawable.check), contentDescription = null) }
            } else {
                null
            },
        )
    }
    val disconnect = account?.let {
        SettingsItem(
            title = { Text(stringResource(R.string.backup_disconnect)) },
            descriptionText = stringResource(R.string.backup_disconnect_desc, it.kind.label),
            icon = painterResource(R.drawable.logout),
            iconShape = CircleShape,
            onClick = onDisconnect,
        )
    }
    SettingsGroup(
        title = stringResource(R.string.backup_group_where),
        style = SettingsGroupStyle.Grouped,
        items = clouds + listOfNotNull(disconnect),
    )
    // dropbox's app is still in development mode, so new sign-ins can stop; someone already connected keeps working
    if (account?.kind != CloudKind.Dropbox && isConfigured(CloudKind.Dropbox)) {
        Text(
            stringResource(R.string.backup_dropbox_notice),
            style = storageText(13f, FontWeight.Medium, StorageMuted, -0.2f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
    }
}

/** back up now, with how the last one went or how far this one is, and restore */
@Composable
private fun BackupsGroup(
    account: CloudAccount?,
    step: CloudBackupStep,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
) {
    val resting = step == CloudBackupStep.Idle
    val status = stepText(step) ?: when {
        account == null -> stringResource(R.string.backup_connect_first)
        account.lastBackupAt > 0 -> stringResource(
            R.string.backup_last,
            DateUtils.getRelativeTimeSpanString(account.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            formatSize(account.lastBackupBytes),
        )
        else -> stringResource(R.string.backup_never)
    }
    SettingsGroup(
        title = stringResource(R.string.backup_group_backups),
        style = SettingsGroupStyle.Grouped,
        items = listOf(
            SettingsItem(
                title = { Text(stringResource(R.string.backup_now)) },
                highlightKey = "Back up now",
                descriptionText = status,
                icon = painterResource(R.drawable.backup),
                iconShape = CircleShape,
                enabled = account != null && resting,
                onClick = onBackUp,
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.backup_restore_title)) },
                highlightKey = "Restore a backup",
                descriptionText = stringResource(R.string.backup_restore_desc),
                icon = painterResource(R.drawable.restore),
                iconShape = CircleShape,
                enabled = account != null && resting,
                onClick = onRestore,
            ),
        ),
    )
}

/** how far a backup or restore has got, or null while nothing is running */
@Composable
internal fun stepText(step: CloudBackupStep): String? = when (step) {
    is CloudBackupStep.Packing -> stringResource(R.string.backup_packing, percent(step.progress))
    is CloudBackupStep.Uploading -> stringResource(R.string.backup_uploading, percent(step.progress))
    is CloudBackupStep.Downloading -> stringResource(R.string.backup_downloading, percent(step.progress))
    is CloudBackupStep.Unpacking -> stringResource(R.string.backup_unpacking, percent(step.progress))
    CloudBackupStep.Restored -> stringResource(R.string.backup_restored)
    else -> null
}

private fun percent(progress: Float): Int = (progress * 100).toInt().coerceIn(0, 100)
