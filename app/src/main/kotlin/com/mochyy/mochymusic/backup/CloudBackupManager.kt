// CloudBackupManager.kt

package com.mochyy.mochymusic.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CloudBackupKeepKey
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.importer.transfer.TransferArchive
import com.mochyy.mochymusic.importer.transfer.TransferException
import com.mochyy.mochymusic.importer.transfer.TransferExporter
import com.mochyy.mochymusic.importer.transfer.TransferImporter
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** where a backup or a restore stands, for the backup page; progress runs from 0 to 1 */
sealed interface CloudBackupStep {
    data object Idle : CloudBackupStep
    data class Packing(val progress: Float) : CloudBackupStep
    data class Uploading(val progress: Float) : CloudBackupStep
    data class Downloading(val progress: Float) : CloudBackupStep
    data class Unpacking(val progress: Float) : CloudBackupStep
    data object BackedUp : CloudBackupStep
    data object Restored : CloudBackupStep
    data class Failed(val message: String) : CloudBackupStep
}

/** how a scheduled run went, so the worker knows whether to try again soon */
enum class BackupOutcome { Done, Skipped, Retry, Stopped }

/** packs the library without songs, sends it to the connected cloud, trims old backups and brings one back */
@Singleton
class CloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: CloudAccountStore,
    private val exporter: TransferExporter,
    private val importer: TransferImporter,
) {
    private val busy = Mutex()
    private val device = BackupNames.thisDevice(Build.MODEL)

    private val _step = MutableStateFlow<CloudBackupStep>(CloudBackupStep.Idle)
    val step: StateFlow<CloudBackupStep> = _step.asStateFlow()

    val account: StateFlow<CloudAccount?> get() = accounts.account

    /** the page's button: always backs up, and says how it went */
    suspend fun backUpNow(): BackupOutcome = backUp(automatic = false)

    /** the scheduled run: quiet, and skipped when nothing a backup holds has changed since the last one */
    suspend fun backUpOnSchedule(): BackupOutcome = backUp(automatic = true)

    /** every backup in the cloud, from any phone, newest first */
    suspend fun backups(): Result<List<CloudBackupFile>> = runCatching {
        val account = accounts.current() ?: throw CloudException(CloudProblem.SignedOut)
        cloudFor(account).list().sortedByDescending { it.modifiedAt }
    }

    /** downloads and unpacks backup; once it's true the app has to restart to swap it in */
    suspend fun restore(backup: CloudBackupFile): Boolean {
        if (!busy.tryLock()) return false
        val zip = File(workDir(), "restore.zip")
        try {
            val account = accounts.current() ?: throw CloudException(CloudProblem.SignedOut)
            _step.value = CloudBackupStep.Downloading(0f)
            cloudFor(account).download(backup, zip) { _step.value = CloudBackupStep.Downloading(whole(it)) }
            _step.value = CloudBackupStep.Unpacking(0f)
            importer.stage(Uri.fromFile(zip)) { _step.value = CloudBackupStep.Unpacking(whole(it)) }.getOrThrow()
            _step.value = CloudBackupStep.Restored
            return true
        } catch (e: CancellationException) {
            _step.value = CloudBackupStep.Idle
            throw e
        } catch (e: Exception) {
            Timber.tag(Tag).w(e, "Restore from the cloud failed")
            _step.value = CloudBackupStep.Failed(messageOf(e))
            return false
        } finally {
            zip.delete()
            busy.unlock()
        }
    }

    /** checks a webdav server and saves it as the backup cloud; null when it worked, otherwise what went wrong */
    suspend fun connectWebDav(address: String, username: String, password: String): CloudProblem? {
        val url = WebDavCloud.addressOf(address) ?: return CloudProblem.BadAddress
        try {
            WebDavCloud.connect(url, username, password)
        } catch (e: CloudException) {
            return e.problem
        } catch (e: IOException) {
            Timber.tag(Tag).w(e, "Couldn't reach the webdav server")
            return CloudProblem.Offline
        }
        forgetOldLogin()
        accounts.save(CloudAccount(kind = CloudKind.WebDav, name = url.host, serverUrl = url.toString(), username = username, password = password))
        return null
    }

    /** signs out of the backup cloud here; the backups already up there stay */
    suspend fun disconnect() {
        forgetOldLogin()
        accounts.save(null)
    }

    /** the page showed a finished backup or a problem, so it goes back to rest */
    fun acknowledge() {
        val now = _step.value
        if (now is CloudBackupStep.BackedUp || now is CloudBackupStep.Failed) _step.value = CloudBackupStep.Idle
    }

    fun messageOf(error: Throwable): String = when (error) {
        is CloudException -> messageOf(error.problem)
        // the importer's own messages already say what to do, like updating mochy first
        is TransferException -> error.message ?: messageOf(CloudProblem.Busy)
        is IOException -> messageOf(CloudProblem.Offline)
        else -> messageOf(CloudProblem.Busy)
    }

    fun messageOf(problem: CloudProblem): String = context.getString(
        when (problem) {
            CloudProblem.Offline -> R.string.cloud_problem_offline
            CloudProblem.SignedOut -> R.string.cloud_problem_signed_out
            CloudProblem.WrongLogin -> R.string.cloud_problem_wrong_login
            CloudProblem.BadAddress -> R.string.cloud_problem_bad_address
            CloudProblem.NotFound -> R.string.cloud_problem_not_found
            CloudProblem.NotWebDav -> R.string.cloud_problem_not_webdav
            CloudProblem.Full -> R.string.cloud_problem_full
            CloudProblem.NoRoom -> R.string.cloud_problem_no_room
            CloudProblem.Busy -> R.string.cloud_problem_busy
            CloudProblem.Denied -> R.string.cloud_problem_denied
            CloudProblem.Expired -> R.string.cloud_problem_expired
        },
    )

    private suspend fun backUp(automatic: Boolean): BackupOutcome {
        if (!busy.tryLock()) return BackupOutcome.Skipped
        val zip = File(workDir(), BackupNames.newName(device, LocalDateTime.now().format(StampFormat)))
        try {
            val account = accounts.current() ?: return BackupOutcome.Stopped
            if (automatic && withContext(Dispatchers.IO) { lastChangeAt() } < account.lastBackupAt) return BackupOutcome.Skipped
            // stamped with when packing began, so a song played during a long upload still counts as new next time
            val packedAt = System.currentTimeMillis()
            _step.value = CloudBackupStep.Packing(0f)
            exporter.exportBackup(zip) { _step.value = CloudBackupStep.Packing(whole(it)) }.getOrThrow()
            val cloud = cloudFor(account)
            _step.value = CloudBackupStep.Uploading(0f)
            cloud.upload(zip, zip.name) { _step.value = CloudBackupStep.Uploading(whole(it)) }
            accounts.update { it.copy(lastBackupAt = packedAt, lastBackupBytes = zip.length(), lastProblem = null) }
            trimOld(cloud, keep = context.dataStore.data.first()[CloudBackupKeepKey] ?: CloudBackupSchedule.DefaultKeep)
            _step.value = if (automatic) CloudBackupStep.Idle else CloudBackupStep.BackedUp
            return BackupOutcome.Done
        } catch (e: CancellationException) {
            _step.value = CloudBackupStep.Idle
            throw e
        } catch (e: Exception) {
            Timber.tag(Tag).w(e, "Backup to the cloud failed")
            val problem = when (e) {
                is CloudException -> e.problem
                is TransferException -> CloudProblem.NoRoom
                is IOException -> CloudProblem.Offline
                else -> CloudProblem.Busy
            }
            // a scheduled run fails quietly, so the page tells about it the next time it opens instead
            if (automatic) accounts.update { it.copy(lastProblem = problem) }
            _step.value = if (automatic) CloudBackupStep.Idle else CloudBackupStep.Failed(messageOf(e))
            return if (problem == CloudProblem.SignedOut || problem == CloudProblem.WrongLogin) BackupOutcome.Stopped else BackupOutcome.Retry
        } finally {
            zip.delete()
            busy.unlock()
        }
    }

    // only this phone's own backups, oldest past keep go; a hiccup here never fails the backup that just worked
    private suspend fun trimOld(cloud: BackupCloud, keep: Int) {
        runCatching {
            cloud.list()
                .filter { BackupNames.isFrom(it.name, device) }
                .sortedByDescending { it.name }
                .drop(keep.coerceAtLeast(1))
                .forEach { runCatching { cloud.delete(it) } }
        }.onFailure { Timber.tag(Tag).w(it, "Couldn't trim old backups") }
    }

    // the newest change to anything a backup holds, cheap enough to check before every scheduled run
    private fun lastChangeAt(): Long {
        val database = context.getDatabasePath(InternalDatabase.DB_NAME)
        val watched = listOf(database, File(database.path + "-wal")) +
            File(context.filesDir, "datastore").listFiles().orEmpty() +
            TransferArchive.PrefsToCarry.values.map { File(context.dataDir, "shared_prefs/$it.xml") } +
            context.filesDir.listFiles().orEmpty().filter { TransferArchive.carriesFile(it.name) && it.name !in TransferArchive.SongFolders }
        return watched.maxOfOrNull { it.lastModified() } ?: 0L
    }

    private fun cloudFor(account: CloudAccount): BackupCloud = when (account.kind) {
        CloudKind.Dropbox -> DropboxCloud(OAuthSession(OAuthApp.Dropbox, accounts))
        CloudKind.OneDrive -> OneDriveCloud(OAuthSession(OAuthApp.OneDrive, accounts))
        CloudKind.WebDav -> WebDavCloud(account)
    }

    // a browser login being replaced is told to forget its token, so it doesn't linger on dropbox's side
    private suspend fun forgetOldLogin() {
        val old = accounts.current() ?: return
        OAuthApp.of(old.kind)?.revoke(old.accessToken)
    }

    private fun workDir(): File = File(context.cacheDir, "cloud_backup").apply { mkdirs() }

    // whole percents only, so the page redraws a hundred times a backup instead of once per buffer
    private fun whole(progress: Float): Float = (progress * 100).toInt() / 100f

    private companion object {
        const val Tag = "CloudBackup"
        val StampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
