// BackupCloud.kt

package com.mochyy.mochymusic.backup

import java.io.File

/** the clouds a backup can go to, by the name saved with the account */
enum class CloudKind(val label: String) {
    Dropbox("Dropbox"),
    OneDrive("OneDrive"),
    WebDav("WebDAV"),
}

/** one backup zip sitting in the cloud */
data class CloudBackupFile(
    // what the cloud needs to fetch or delete it again: a dropbox id, a onedrive item id or the file name on a webdav server
    val id: String,
    val name: String,
    val bytes: Long,
    val modifiedAt: Long,
) {
    /** the phone it came from, read back out of the name the backup was given */
    val device: String get() = BackupNames.deviceOf(name)
}

/** what every cloud does with backups, always inside mochy's own folder there */
interface BackupCloud {
    suspend fun upload(file: File, name: String, onProgress: (Float) -> Unit)
    suspend fun list(): List<CloudBackupFile>
    suspend fun download(backup: CloudBackupFile, into: File, onProgress: (Float) -> Unit)
    suspend fun delete(backup: CloudBackupFile)
}

/** the kinds of trouble a cloud can run into, each with its own message on screen */
enum class CloudProblem { Offline, SignedOut, WrongLogin, BadAddress, NotFound, NotWebDav, Full, NoRoom, Busy, Denied, Expired }

class CloudException(val problem: CloudProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** how backups are named: mochy-backup-<phone>-<date>-<time>.zip, so each phone keeps and trims only its own */
object BackupNames {
    private const val Prefix = "mochy-backup-"
    private const val Suffix = ".zip"
    private val Stamp = Regex("-\\d{8}-\\d{6}$")

    /** this phone's model as a safe file-name piece, like redmi-note-11-pro */
    fun thisDevice(model: String): String =
        model.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "phone" }

    fun newName(device: String, stamp: String): String = "$Prefix$device-$stamp$Suffix"

    /** true for any mochy backup, whichever phone made it */
    fun isBackup(name: String): Boolean = name.startsWith(Prefix) && name.endsWith(Suffix) && Stamp.containsMatchIn(name.removeSuffix(Suffix))

    /** true for a backup this phone made, the only ones it ever deletes */
    fun isFrom(name: String, device: String): Boolean = isBackup(name) && deviceOf(name) == device

    fun deviceOf(name: String): String = name.removePrefix(Prefix).removeSuffix(Suffix).replace(Stamp, "")
}
