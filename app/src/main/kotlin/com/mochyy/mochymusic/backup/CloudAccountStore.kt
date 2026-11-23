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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val found = read()
            _account.value = found
            syncGuard(found)
            loaded.complete(Unit)
        }
    }

    suspend fun current(): CloudAccount? {
        loaded.await()
        return _account.value
    }

    suspend fun save(account: CloudAccount?) = withContext(Dispatchers.IO) {
        loaded.await()
        writing.withLock { write(account) }
    }

    suspend fun update(change: (CloudAccount) -> CloudAccount) = withContext(Dispatchers.IO) {
        loaded.await()
        writing.withLock { _account.value?.let { write(change(it)) } }
    }

    private fun read(): CloudAccount? = runCatching {
        if (!file.exists()) return null
        val raw = file.readText().trim()
        val opened = com.mochyy.mochymusic.utils.SecretVault.open(raw, com.mochyy.mochymusic.utils.SecretVault.CloudBackupDomain) ?: return null
        val account = CloudAccount.fromJson(JSONObject(opened)) ?: return null
        // upgrade old plaintext webdav/cloud creds straight into the vault
        if (com.mochyy.mochymusic.utils.SecretVault.needsUpgrade(raw)) writeSealed(account)
        account
    }.onFailure { Timber.tag(Tag).w(it, "Couldn't read the backup account") }.getOrNull()

    private fun write(account: CloudAccount?) {
        if (account == null) {
            file.delete()
        } else {
            writeSealed(account)
        }
        _account.value = account
        syncGuard(account)
    }

    private fun writeSealed(account: CloudAccount) {
        val sealed = com.mochyy.mochymusic.utils.SecretVault.seal(account.toJson().toString(), com.mochyy.mochymusic.utils.SecretVault.CloudBackupDomain)
        val temp = File(file.path + ".tmp")
        temp.writeText(sealed)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun syncGuard(account: CloudAccount?) {
        if (account == null) {
            com.mochyy.mochymusic.modules.security.SecretGuard.clear("cloud_backup")
            com.mochyy.mochymusic.modules.security.SecretGuard.registerBlockedUrl("cloud_webdav", null)
        } else {
            com.mochyy.mochymusic.modules.security.SecretGuard.register("cloud_backup", account.password, account.accessToken, account.refreshToken)
            com.mochyy.mochymusic.modules.security.SecretGuard.registerBlockedUrl("cloud_webdav", account.serverUrl)
        }
    }

    private companion object {
        const val Tag = "CloudBackup"
        const val FileName = "cloud_backup_account.json"
    }
}
