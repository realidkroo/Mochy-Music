// StoragePlaces.kt

package com.mochyy.mochymusic.playback

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.mochyy.mochymusic.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// the two big folders mochy can keep on another drive, named after the folder they use on any drive
enum class StoredKind(val folder: String) {
    Downloads("download"),
    SongCache("exoplayer"),
}

// a place mochy can save onto: the phone, its own folder on a memory card, or a folder the user picked
data class StoragePlace(
    // mochy's own folder there, the kind's folder goes inside it
    val root: File,
    val label: String,
    val isPhone: Boolean,
    val total: Long,
    val free: Long,
    // picked with the folder picker, which needs all files access unless it's one of mochy's own
    val isFolder: Boolean = false,
)

// where downloads and the song cache live, read before hilt opens them, so a move only needs a restart
object StoragePlaces {
    private const val Tag = "StoragePlaces"
    private const val LeftoversName = "storage_leftovers.json"
    private const val PickedFolderName = "Mochy"
    private const val ExternalStorageAuthority = "com.android.externalstorage.documents"

    private fun markerOf(context: Context, kind: StoredKind) = File(context.noBackupFilesDir, "storage_place_${kind.folder}.txt")

    // the folder [kind] lives in right now; a memory card that's been taken out, or a folder mochy may no longer reach, falls back to the phone so the app still opens
    fun dir(context: Context, kind: StoredKind): File {
        val phone = File(context.filesDir, kind.folder)
        val root = chosenRoot(context, kind) ?: return phone
        if (Environment.getExternalStorageState(root) != Environment.MEDIA_MOUNTED) {
            Log.w(Tag, "${kind.folder} lives on a drive that isn't there, using the phone for now")
            return phone
        }
        if (needsFolderAccess(context, root)) {
            Log.w(Tag, "${kind.folder} lives in a folder mochy lost access to, using the phone for now")
            return phone
        }
        return File(root, kind.folder)
    }

    // the place [kind] lives in, or the phone when its drive isn't there
    fun placeOf(context: Context, kind: StoredKind): StoragePlace {
        val all = places(context)
        val root = dir(context, kind).parentFile
        return all.firstOrNull { it.root == root } ?: root?.let { folderPlace(context, it) } ?: all.first()
    }

    // the folder mochy saves into for one the user picked: its own "Mochy" folder inside, since the player's cache deletes any file it doesn't know in its folder
    fun rootForPicked(picked: File): File = if (picked.name.equals(PickedFolderName, ignoreCase = true)) picked else File(picked, PickedFolderName)

    // a picked folder described like a drive: where it is, and that drive's room
    fun folderPlace(context: Context, root: File): StoragePlace {
        var existing: File? = root
        while (existing != null && !existing.exists()) existing = existing.parentFile
        val stat = runCatching { StatFs((existing ?: root).path) }.getOrNull()
        return StoragePlace(root, readablePath(context, root), isPhone = false, stat?.totalBytes ?: 0L, stat?.availableBytes ?: 0L, isFolder = true)
    }

    // a folder from the folder picker as a path mochy can write to with plain files, or null for one that isn't on the phone or a card
    fun folderFromTree(context: Context, tree: android.net.Uri): File? {
        if (tree.authority != ExternalStorageAuthority) return null
        val documentId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volumeId = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "").trim('/')
        val volumeRoot = if (volumeId.equals("primary", ignoreCase = true)) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()
        } else {
            cardRoot(context, volumeId)
        } ?: return null
        return if (relative.isEmpty()) volumeRoot else File(volumeRoot, relative)
    }

    // whether mochy needs all files access (or the old storage permission) to write in [folder]: not for its own folders
    fun needsFolderAccess(context: Context, folder: File): Boolean = !isOwnFolder(context, folder) && !hasFolderAccess(context)

    // all files access on android 11+, the storage permission before it
    fun hasFolderAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    private fun isOwnFolder(context: Context, folder: File): Boolean =
        (listOf(context.filesDir) + context.getExternalFilesDirs(null).filterNotNull()).any { own ->
            folder.path == own.path || folder.path.startsWith(own.path + File.separator)
        }

    // a memory card's root by its id, from where android keeps mochy's own folder on it
    private fun cardRoot(context: Context, volumeId: String): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService<StorageManager>()?.storageVolumes
                ?.firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
                ?.directory?.let { return it }
        }
        return context.getExternalFilesDirs(null).filterNotNull()
            .map { it.path.substringBefore("/Android/") }
            .firstOrNull { it.endsWith("/$volumeId", ignoreCase = true) }
            ?.let(::File)
    }

    // "Music/Mochy" on the phone, "card name/Music/Mochy" on a card
    private fun readablePath(context: Context, folder: File): String {
        @Suppress("DEPRECATION")
        val phoneRoot = Environment.getExternalStorageDirectory().path
        if (folder.path.startsWith(phoneRoot)) return folder.path.removePrefix(phoneRoot).trim('/').ifEmpty { folder.name }
        val volume = runCatching { context.getSystemService<StorageManager>()?.getStorageVolume(folder) }.getOrNull()
        val name = runCatching { volume?.getDescription(context) }.getOrNull() ?: context.getString(R.string.storage_place_card)
        val cardPath = folder.path.split('/').drop(3).joinToString("/")
        return if (cardPath.isEmpty()) name else "$name/$cardPath"
    }

    // the phone first, then every memory card or drive android gives mochy a folder on
    fun places(context: Context): List<StoragePlace> {
        val storage = context.getSystemService<StorageManager>()
        val phone = describe(context.filesDir, context.getString(R.string.storage_place_phone), isPhone = true, storage = null, context = context)
        val drives = context.getExternalFilesDirs(null).drop(1).filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .map { describe(it, null, isPhone = false, storage = storage, context = context) }
        return listOf(phone) + drives
    }

    private fun describe(root: File, label: String?, isPhone: Boolean, storage: StorageManager?, context: Context): StoragePlace {
        val stat = runCatching { StatFs(root.path) }.getOrNull()
        val name = label
            ?: runCatching { storage?.getStorageVolume(root)?.getDescription(context) }.getOrNull()
            ?: context.getString(R.string.storage_place_card)
        return StoragePlace(root, name, isPhone, stat?.totalBytes ?: 0L, stat?.availableBytes ?: 0L)
    }

    private fun chosenRoot(context: Context, kind: StoredKind): File? {
        val marker = markerOf(context, kind)
        if (!marker.exists()) return null
        val path = runCatching { marker.readText().trim() }.getOrNull()
        return path?.takeIf { it.isNotEmpty() }?.let(::File)
    }

    // remembers where [kind] lives from the next start on, and that the old folder can go once that start is here
    fun choose(context: Context, kind: StoredKind, place: StoragePlace, oldDir: File) {
        val marker = markerOf(context, kind)
        if (place.isPhone) marker.delete() else marker.writeText(place.root.path)
        if (oldDir != File(place.root, kind.folder)) addLeftover(context, kind, oldDir)
    }

    // an import brings its downloads back onto the phone, so whatever lived on a card is left behind
    fun resetToPhone(context: Context, kind: StoredKind) {
        val root = chosenRoot(context, kind) ?: return
        markerOf(context, kind).delete()
        addLeftover(context, kind, File(root, kind.folder))
    }

    private fun addLeftover(context: Context, kind: StoredKind, dir: File) {
        val file = File(context.noBackupFilesDir, LeftoversName)
        val list = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        list.put(JSONObject().put("kind", kind.name).put("path", dir.path))
        file.writeText(list.toString())
    }

    // called first thing in App.onCreate: clears the folders a move left behind, on a thread of its own since a big cache takes a while
    fun cleanUpLeftovers(app: Application) {
        val file = File(app.noBackupFilesDir, LeftoversName)
        if (!file.exists()) return
        val list = runCatching { JSONArray(file.readText()) }.getOrNull()
        file.delete()
        list ?: return
        // read now, before anything can change them, so a folder in use is never the one deleted
        val inUse = StoredKind.entries.map { dir(app, it) }.toSet()
        Thread {
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                val kind = runCatching { StoredKind.valueOf(entry.getString("kind")) }.getOrNull() ?: continue
                val dir = File(entry.optString("path"))
                if (dir in inUse || !dir.exists()) continue
                runCatching {
                    // the cache's index lives in media3's database, so it goes through simplecache to take its tables too
                    if (kind == StoredKind.SongCache) {
                        // if its tables can't be reached the files still go, a few orphaned rows cost nothing
                        runCatching { StandaloneDatabaseProvider(app).use { SimpleCache.delete(dir, it) } }
                        if (dir.exists()) dir.deleteRecursively()
                    } else {
                        dir.deleteRecursively()
                    }
                }.onFailure { Log.w(Tag, "Couldn't clear the old ${kind.folder} folder", it) }
            }
        }.apply { name = "StorageLeftovers" }.start()
    }
}
