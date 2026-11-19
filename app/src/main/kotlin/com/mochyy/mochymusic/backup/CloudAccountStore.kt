// CloudAccountStore.kt

package com.mochyy.mochymusic.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** the cloud backups go to, with whatever it needs to let mochy in */
data class CloudAccount(
    val kind: CloudKind,
    // shown on the backup page: the account's email or name, or the webdav server's address
    val name: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastBackupAt: Long = 0L,
    val lastBackupBytes: Long = 0L,
    // why the last scheduled backup didn't go through, so the page can say so; null once one works again
    val lastProblem: CloudProblem? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name)
        .put("name", name)
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("expiresAt", expiresAt)
        .put("serverUrl", serverUrl)
        .put("username", username)
        .put("password", password)
        .put("lastBackupAt", lastBackupAt)
        .put("lastBackupBytes", lastBackupBytes)
        .put("lastProblem", lastProblem?.name.orEmpty())

    companion object {
        fun fromJson(json: JSONObject): CloudAccount? {
            val kind = CloudKind.entries.find { it.name == json.optString("kind") } ?: return null
            return CloudAccount(
                kind = kind,
                name = json.optString("name"),
                accessToken = json.optString("accessToken"),
                refreshToken = json.optString("refreshToken"),
                expiresAt = json.optLong("expiresAt"),
                serverUrl = json.optString("serverUrl"),
                username = json.optString("username"),
                password = json.optString("password"),
                lastBackupAt = json.optLong("lastBackupAt"),
                lastBackupBytes = json.optLong("lastBackupBytes"),
                lastProblem = CloudProblem.entries.find { it.name == json.optString("lastProblem") },
            )
        }
    }
}

/** keeps the account in no_backup, so it never rides along in android's backup, mochy's export or a cloud backup itself */
@Singleton
class CloudAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.noBackupFilesDir, FileName)
    private val writing = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _account = MutableStateFlow<CloudAccount?>(null)
    val account: StateFlow<CloudAccount?> = _account.asStateFlow()

    init {
        // read off the main thread, the page shows "not connected" for the moment it takes
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            _account.value = read()
            loaded.complete(Unit)
        }
    }

    suspend fun current(): CloudAccount? {
        loaded.await()
        return _account.value
    }

    /** null signs out */
    suspend fun save(account: CloudAccount?) = withContext(Dispatchers.IO) {
        loaded.await()
        writing.withLock { write(account) }
    }

    /** changes the saved account in place, for new tokens and the last backup's stamp; does nothing once signed out */
    suspend fun update(change: (CloudAccount) -> CloudAccount) = withContext(Dispatchers.IO) {
        loaded.await()
        writing.withLock { _account.value?.let { write(change(it)) } }
    }

    private fun read(): CloudAccount? = runCatching {
        if (!file.exists()) return null
        CloudAccount.fromJson(JSONObject(file.readText()))
    }.onFailure { Timber.tag(Tag).w(it, "Couldn't read the backup account") }.getOrNull()

    // written beside and renamed over, so a phone dying mid-write never leaves half an account
    private fun write(account: CloudAccount?) {
        if (account == null) {
            file.delete()
        } else {
            val temp = File(file.path + ".tmp")
            temp.writeText(account.toJson().toString())
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
        _account.value = account
    }

    private companion object {
        const val Tag = "CloudBackup"
        const val FileName = "cloud_backup_account.json"
    }
}
