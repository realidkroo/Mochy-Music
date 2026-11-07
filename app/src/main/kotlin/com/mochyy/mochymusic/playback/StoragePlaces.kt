// StoragePlaces.kt

package com.mochyy.mochymusic.playback

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
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

// a drive mochy can save onto: the phone, or its own folder on a memory card
data class StoragePlace(
    // mochy's own folder on that drive, the kind's folder goes inside it
    val root: File,
    val label: String,
    val isPhone: Boolean,
    val total: Long,
    val free: Long,
)

// where downloads and the song cache live, read before hilt opens them, so a move only needs a restart
object StoragePlaces {
    private const val Tag = "StoragePlaces"
    private const val LeftoversName = "storage_leftovers.json"

    private fun markerOf(context: Context, kind: StoredKind) = File(context.noBackupFilesDir, "storage_place_${kind.folder}.txt")

    // the folder [kind] lives in right now; a memory card that's been taken out falls back to the phone so the app still opens
    fun dir(context: Context, kind: StoredKind): File {
        val phone = File(context.filesDir, kind.folder)
        val root = chosenRoot(context, kind) ?: return phone
        if (Environment.getExternalStorageState(root) != Environment.MEDIA_MOUNTED) {
            Log.w(Tag, "${kind.folder} lives on a drive that isn't there, using the phone for now")
            return phone
        }
        return File(root, kind.folder)
    }

    // the place [kind] lives in, or the phone when its drive isn't there
    fun placeOf(context: Context, kind: StoredKind): StoragePlace {
        val all = places(context)
        val root = dir(context, kind).parentFile
        return all.firstOrNull { it.root == root } ?: all.first()
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
