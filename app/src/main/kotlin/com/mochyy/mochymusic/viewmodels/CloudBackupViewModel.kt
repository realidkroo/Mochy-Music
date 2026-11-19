// CloudBackupViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.backup.CloudAccount
import com.mochyy.mochymusic.backup.CloudBackupFile
import com.mochyy.mochymusic.backup.CloudBackupManager
import com.mochyy.mochymusic.backup.CloudBackupSchedule
import com.mochyy.mochymusic.backup.CloudBackupStep
import com.mochyy.mochymusic.backup.CloudKind
import com.mochyy.mochymusic.backup.CloudLogin
import com.mochyy.mochymusic.backup.CloudProblem
import com.mochyy.mochymusic.backup.CloudSignIn
import com.mochyy.mochymusic.constants.CloudBackupAutoKey
import com.mochyy.mochymusic.constants.CloudBackupEveryMinutesKey
import com.mochyy.mochymusic.importer.transfer.TransferRestore
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/** what the restore sheet has to show: still asking the cloud, the backups it found, or why it couldn't */
sealed interface CloudBackupList {
    data object Loading : CloudBackupList
    data class Found(val backups: List<CloudBackupFile>) : CloudBackupList
    data class Failed(val message: String) : CloudBackupList
}

/** the backup page under integrations: which cloud, back up now, the schedule, and restoring */
@OptIn(FlowPreview::class)
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: CloudBackupManager,
    private val signIn: CloudSignIn,
) : ViewModel() {
    val account: StateFlow<CloudAccount?> = manager.account
    val step: StateFlow<CloudBackupStep> = manager.step
    val login: StateFlow<CloudLogin> = signIn.login

    private val _backups = MutableStateFlow<CloudBackupList>(CloudBackupList.Loading)
    val backups: StateFlow<CloudBackupList> = _backups.asStateFlow()

    init {
        // the switch, the slider and the account all decide the schedule; a drag along the slider settles first
        viewModelScope.launch {
            combine(context.dataStore.data, account) { prefs, account ->
                val on = (prefs[CloudBackupAutoKey] ?: false) && account != null
                on to (prefs[CloudBackupEveryMinutesKey] ?: CloudBackupSchedule.DefaultEveryMinutes)
            }
                .distinctUntilChanged()
                // the first value is what's already scheduled, only changes after it need workmanager
                .drop(1)
                .debounce(600)
                .collect { (on, everyMinutes) -> CloudBackupSchedule.apply(context, on, everyMinutes) }
        }
    }

    fun isConfigured(kind: CloudKind): Boolean = kind == CloudKind.WebDav || signIn.isConfigured(kind)

    suspend fun loginUri(kind: CloudKind): Uri = signIn.loginUri(kind)

    fun cancelWaiting() = signIn.cancelWaiting()

    fun dismissLoginProblem() = signIn.dismissProblem()

    fun messageOf(problem: CloudProblem): String = manager.messageOf(problem)

    /** null when the server took it, otherwise what to tell the user */
    suspend fun connectWebDav(address: String, username: String, password: String): String? =
        manager.connectWebDav(address, username, password)?.let(manager::messageOf)

    fun disconnect() {
        viewModelScope.launch { manager.disconnect() }
    }

    // a scheduled backup already running shows its own progress here, so a tap meanwhile does nothing
    fun backUpNow() {
        if (step.value != CloudBackupStep.Idle) return
        viewModelScope.launch { manager.backUpNow() }
    }

    fun acknowledge() = manager.acknowledge()

    fun loadBackups() {
        _backups.value = CloudBackupList.Loading
        viewModelScope.launch {
            _backups.value = manager.backups().fold(
                onSuccess = { CloudBackupList.Found(it) },
                onFailure = { CloudBackupList.Failed(manager.messageOf(it)) },
            )
        }
    }

    /** downloads and unpacks backup, then restarts mochy so the next start swaps it in */
    fun restore(backup: CloudBackupFile) {
        viewModelScope.launch {
            if (!manager.restore(backup)) return@launch
            // a beat on the finished state, so the restart doesn't look like a crash
            delay(1_200)
            TransferRestore.restartToFinish(context)
        }
    }
}
