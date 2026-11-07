// TransferRestore.kt

package com.mochyy.mochymusic.importer.transfer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.markOnboardedOnThisInstall
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.StoragePlaces
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import kotlin.system.exitProcess

/** finishes an import on the first start after it was unpacked, before room, media3 or the settings open anything */
object TransferRestore {
    private const val Tag = "TransferRestore"
    private const val StagingName = "transfer_staging"
    private const val NoteName = "transfer_restored.json"
    internal const val ReadyMarker = "ready.json"

    /** where an unpacked import waits for the next start, next to the data so moving it in is a rename */
    fun stagingDir(context: Context): File = File(context.dataDir, StagingName)

    /** called first thing in App.onCreate, swaps the unpacked import in if one is waiting */
    fun applyPending(app: Application) {
        // the crash screen runs in its own process and must never touch an import
        if (!isMainProcess(app)) return
        val staging = stagingDir(app)
        if (!staging.exists()) return
        val ready = File(staging, ReadyMarker)
        if (!ready.exists()) {
            // the import was closed halfway through unpacking, so there's nothing whole to use
            staging.deleteRecursively()
            return
        }
        runCatching { swapIn(app, staging, JSONObject(ready.readText())) }
            .onFailure { Log.e(Tag, "Couldn't finish the import", it) }
        staging.deleteRecursively()
    }

    /** stops playback and starts the app again, so the next start can swap the import in */
    fun restartToFinish(context: Context) {
        context.stopService(Intent(context, MusicService::class.java))
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        exitProcess(0)
    }

    /** the welcome line for the first screen after an import, handed out once */
    fun takeRestoredNote(context: Context): String? {
        val file = File(context.noBackupFilesDir, NoteName)
        if (!file.exists()) return null
        val note = runCatching { JSONObject(file.readText()) }.getOrNull()
        file.delete()
        note ?: return null
        val missing = note.optInt("missingLocal")
        return if (missing > 0) {
            "Welcome back! Your library is here, but $missing local ${if (missing == 1) "song wasn't" else "songs weren't"} found on this phone."
        } else {
            "Welcome back! Your whole library is here."
        }
    }

    // files first and the library last, so a stop halfway leaves an app that still starts and can import again
    private fun swapIn(context: Context, staging: File, ready: JSONObject) {
        moveFiles(context, File(staging, TransferArchive.FilesPrefix))
        // the import's downloads land on the phone, so a card that held the old ones is let go
        if (File(context.filesDir, StoredKind.Downloads.folder).exists()) StoragePlaces.resetToPhone(context, StoredKind.Downloads)
        File(staging, TransferArchive.MediaEntry).takeIf { it.exists() }?.let { media ->
            // the streaming cache's index lived in the old media database, so the cache starts over with the new one
            StoragePlaces.dir(context, StoredKind.SongCache).deleteRecursively()
            replaceDatabase(context.getDatabasePath(TransferArchive.MediaDatabaseName), media)
        }
        restorePrefs(context, File(staging, TransferArchive.PrefsPrefix))
        File(staging, TransferArchive.SettingsEntry).takeIf { it.exists() }?.let { settings ->
            val json = JSONObject(settings.readText())
            runBlocking { context.dataStore.edit { TransferArchive.jsonIntoPreferences(json, it) } }
        }
        replaceDatabase(context.getDatabasePath(InternalDatabase.DB_NAME), File(staging, TransferArchive.LibraryEntry))
        markOnboardedOnThisInstall(context)
        File(context.noBackupFilesDir, NoteName).writeText(ready.toString())
    }

    private fun moveFiles(context: Context, from: File) {
        for (item in from.listFiles().orEmpty()) {
            val target = File(context.filesDir, item.name)
            target.deleteRecursively()
            if (!item.renameTo(target)) {
                item.copyRecursively(target, overwrite = true)
            }
        }
    }

    private fun replaceDatabase(target: File, staged: File) {
        if (!staged.exists()) return
        target.parentFile?.mkdirs()
        // a leftover wal from the empty database would be replayed onto the imported one and break it
        listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        if (!staged.renameTo(target)) {
            staged.copyTo(target, overwrite = true)
        }
    }

    // merged into what's there, so anything set during onboarding that the old app never had stays
    private fun restorePrefs(context: Context, folder: File) {
        for (file in folder.listFiles().orEmpty()) {
            val fileName = TransferArchive.PrefsToCarry[file.name.removeSuffix(".json")] ?: continue
            val editor = context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit()
            TransferArchive.jsonIntoSharedPreferences(JSONObject(file.readText()), editor)
            editor.commit()
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() }.getOrNull()
        }
        return name.isNullOrEmpty() || name == context.packageName
    }
}
