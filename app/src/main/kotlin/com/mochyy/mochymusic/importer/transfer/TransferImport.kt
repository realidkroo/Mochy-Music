// TransferImport.kt

package com.mochyy.mochymusic.importer.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mochyy.mochymusic.db.AllMigrations
import com.mochyy.mochymusic.db.MusicDatabaseVersion
import com.mochyy.mochymusic.playback.StoragePlaces
import com.mochyy.mochymusic.playback.StoredKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** reads a transfer zip, checks it, unpacks it next to the app's data and converts it for this app; TransferRestore swaps it in on the next start */
@Singleton
class TransferImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val running = Mutex()

    /** what's inside a zip, from its first entry only, failing with a message ready to show */
    suspend fun peek(source: Uri): Result<TransferManifest> = withContext(Dispatchers.IO) {
        runCatching { readManifest(source) }.friendly()
    }

    /** unpacks and converts source, onProgress goes from 0 to 1; once it succeeds the app restarts to finish */
    suspend fun stage(source: Uri, onProgress: (Float) -> Unit): Result<TransferManifest> = withContext(Dispatchers.IO) {
        if (!running.tryLock()) return@withContext Result.failure(TransferException("An import is already running."))
        try {
            runCatching { unpackAndConvert(source, onProgress) }
                .onFailure { TransferRestore.stagingDir(context).deleteRecursively() }
                .friendly()
        } finally {
            running.unlock()
        }
    }

    private fun unpackAndConvert(source: Uri, onProgress: (Float) -> Unit): TransferManifest {
        val manifest = readManifest(source)
        val needed = manifest.bytes + SpareBytes
        // if downloads are already kept on an SD card (or phone storage lacks room while a mounted SD card has plenty),
        // stage downloaded songs directly onto that SD card instead of forcing everything through internal storage
        val externalDownloadsRoot = chooseDownloadsRoot(needed)
        val downloadsStage = externalDownloadsRoot?.let { File(it, TransferRestore.DownloadsStagingName) }
        if (externalDownloadsRoot == null && context.dataDir.usableSpace < needed) {
            throw TransferException("Not enough free space. Mochy needs about ${needed / 1_000_000} MB free to import this.")
        }
        if (externalDownloadsRoot != null && context.dataDir.usableSpace < SpareBytes) {
            throw TransferException("Not enough free space on this phone for your library database.")
        }

        val staging = TransferRestore.stagingDir(context).apply { deleteRecursively(); mkdirs() }
        downloadsStage?.apply { deleteRecursively(); mkdirs() }
        try {
            unpack(source, staging, downloadsStage, total = manifest.bytes, onProgress)
        } catch (e: Throwable) {
            downloadsStage?.deleteRecursively()
            throw e
        }

        val library = File(staging, TransferArchive.LibraryEntry)
        if (!library.exists()) {
            downloadsStage?.deleteRecursively()
            throw TransferException("That export has no library inside. Export again from your old app.")
        }

        val moveToHere = pathMover(manifest)
        val missingLocal = convertLibrary(library, File(staging, TransferArchive.LocalSongsEntry), moveToHere)
        rewriteSettings(File(staging, TransferArchive.SettingsEntry), moveToHere)
        File(staging, TransferArchive.PrefsPrefix).listFiles().orEmpty().forEach { prefs ->
            if (prefs.name.removeSuffix(".json") in TransferArchive.PrefsToCarry) rewriteSettings(prefs, moveToHere) else prefs.delete()
        }

        val hasStagedExternalDownloads = downloadsStage != null && downloadsStage.listFiles()?.isNotEmpty() == true
        if (!hasStagedExternalDownloads) downloadsStage?.deleteRecursively()

        // written last: without it the next start throws the half-made folder away instead of using it
        File(staging, TransferRestore.ReadyMarker).writeText(
            JSONObject()
                .put("songs", manifest.songs)
                .put("playlists", manifest.playlists)
                .put("missingLocal", missingLocal)
                .apply {
                    if (hasStagedExternalDownloads) {
                        put("downloadsRoot", externalDownloadsRoot.path)
                    }
                }
                .toString()
        )
        onProgress(1f)
        return manifest
    }

    private fun chooseDownloadsRoot(neededBytes: Long): File? {
        val current = StoragePlaces.placeOf(context, StoredKind.Downloads)
        if (!current.isPhone && current.free >= neededBytes) return current.root
        if (context.dataDir.usableSpace >= neededBytes) return null
        return StoragePlaces.sdCards(context).firstOrNull { it.free >= neededBytes }?.root
    }

    private fun readManifest(source: Uri): TransferManifest {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw TransferException("Couldn't open that file. Try picking it again.")
        val json = ZipInputStream(stream.buffered()).use { zip ->
            val first = zip.nextEntry
            if (first?.name != TransferArchive.ManifestEntry) throw NotAnExport()
            JSONObject(readCappedBytes(zip, MaxManifestBytes).toString(Charsets.UTF_8))
        }
        val manifest = TransferManifest.fromJson(json) ?: throw NotAnExport()
        if (manifest.formatVersion > TransferArchive.FormatVersion || manifest.databaseVersion > MusicDatabaseVersion) {
            throw TransferException("This export comes from a newer Mochy. Update Mochy first, then import it again.")
        }
        // without a path room would fall back to an empty library, so refuse before touching anything
        if (!hasMigrationPath(manifest.databaseVersion, MusicDatabaseVersion)) {
            throw TransferException("This export is too old for Mochy to read. Update your old app, export again and import that.")
        }
        return manifest
    }

    private fun readCappedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw TransferException("This export contains an oversized metadata entry.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // copies the known entries into staging (routing files/download/* straight to an SD card when chosen), everything else in the zip is ignored
    private fun unpack(source: Uri, staging: File, downloadsStage: File?, total: Long, onProgress: (Float) -> Unit) {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw TransferException("Couldn't open that file. Try picking it again.")
        val root = staging.canonicalPath + File.separator
        val dlRoot = downloadsStage?.let { it.canonicalPath + File.separator }
        val dlPrefix = TransferArchive.FilesPrefix + StoredKind.Downloads.folder + "/"
        val maxTotalBytes = maxOf(total.coerceAtLeast(0L) + 512L * 1024 * 1024, 512L * 1024 * 1024)
        var copied = 0L
        var lastReported = 0f
        val buffer = ByteArray(128 * 1024)
        ZipInputStream(stream.buffered(256 * 1024)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !isWanted(entry.name)) continue
                val toExternalDownloads = downloadsStage != null && entry.name.startsWith(dlPrefix)
                val target = if (toExternalDownloads) {
                    File(downloadsStage, entry.name.removePrefix(dlPrefix))
                } else {
                    File(staging, entry.name)
                }
                val expectedRoot = if (toExternalDownloads) dlRoot else root
                // a crafted zip could point outside the folder with ../, this keeps every file inside it
                if (expectedRoot == null || !target.canonicalPath.startsWith(expectedRoot)) continue
                val isJsonMetadata = entry.name.endsWith(".json")
                var entryWritten = 0L
                target.parentFile?.mkdirs()
                target.outputStream().buffered(256 * 1024).use { out ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryWritten += read
                        copied += read
                        if (isJsonMetadata && entryWritten > MaxJsonEntryBytes) {
                            throw TransferException("This export contains an oversized settings file.")
                        }
                        if (copied > maxTotalBytes) {
                            throw TransferException("This export expanded past its declared size.")
                        }
                        out.write(buffer, 0, read)
                        if (total > 0) {
                            val p = (copied.toDouble() / total).toFloat().coerceIn(0f, 0.97f)
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                onProgress(p)
                            }
                        }
                    }
                }
                if (entry.time > 0) target.setLastModified(entry.time)
            }
        }
    }

    private fun isWanted(name: String): Boolean = when {
        name == TransferArchive.LibraryEntry || name == TransferArchive.MediaEntry -> true
        name == TransferArchive.SettingsEntry || name == TransferArchive.LocalSongsEntry -> true
        name.startsWith(TransferArchive.PrefsPrefix) -> name.endsWith(".json")
        name.startsWith(TransferArchive.FilesPrefix) -> TransferArchive.carriesFile(name.removePrefix(TransferArchive.FilesPrefix).substringBefore('/'))
        else -> false
    }

    // points saved file paths (covers, shared songs, the profile photo) from the old app's folder at this one
    private fun pathMover(manifest: TransferManifest): PathMover {
        val here = context.dataDir.path.trimEnd('/') + "/"
        val from = buildSet {
            if (manifest.dataDir.isNotBlank()) add(manifest.dataDir.trimEnd('/') + "/")
            if (manifest.appId.isNotBlank()) {
                add("/data/data/${manifest.appId}/")
                add("/data/user/0/${manifest.appId}/")
            }
        }.filter { it != here }
        return PathMover(from, here)
    }

    // fixes paths, relinks local songs to the files on this phone and leaves one plain file; returns how many local songs weren't found
    private fun convertLibrary(library: File, localSongs: File, mover: PathMover): Int {
        var missing = 0
        SQLiteDatabase.openDatabase(library.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                if (mover.from.isNotEmpty()) rewritePaths(db, mover)
                if (localSongs.exists()) missing = relinkLocalSongs(db, JSONArray(localSongs.readText()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            foldIntoOneFile(db)
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (!cursor.moveToFirst() || !cursor.getString(0).equals("ok", ignoreCase = true)) {
                    throw TransferException("The library in that export is damaged. Export again from your old app.")
                }
            }
        }
        listOf("-wal", "-shm", "-journal").forEach { File(library.path + it).delete() }
        return missing
    }

    // every text column of every table, so paths get fixed wherever a feature kept one
    private fun rewritePaths(db: SQLiteDatabase, mover: PathMover) {
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        for (table in tables) {
            val safeTable = table.replace("`", "``")
            val columns = db.rawQuery("PRAGMA table_info(`$safeTable`)", null).use { cursor ->
                val nameAt = cursor.getColumnIndexOrThrow("name")
                val typeAt = cursor.getColumnIndexOrThrow("type")
                buildList {
                    while (cursor.moveToNext()) {
                        val type = cursor.getString(typeAt).orEmpty().uppercase()
                        if (type.isEmpty() || "TEXT" in type || "CHAR" in type || "CLOB" in type) add(cursor.getString(nameAt))
                    }
                }
            }
            for (column in columns) {
                val safeColumn = column.replace("`", "``")
                for (old in mover.from) {
                    db.execSQL(
                        "UPDATE OR IGNORE `$safeTable` SET `$safeColumn` = REPLACE(`$safeColumn`, ?, ?) WHERE instr(`$safeColumn`, ?) > 0",
                        arrayOf(old, mover.to, old),
                    )
                }
            }
        }
    }

    // a picked file's access can't move to a new app, so its song points at the same file through the media store instead
    private fun relinkLocalSongs(db: SQLiteDatabase, songs: JSONArray): Int {
        var missing = 0
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optString("id")
            if (id.isBlank()) continue
            val uri = Uri.parse(song.optString("uri"))
            if (uri.scheme != "content" || uri.authority == MediaStore.AUTHORITY) continue
            val found = song.optString("mediaUri").takeIf { it.isNotBlank() }
                ?: findInMediaStore(song.optString("name"), song.optLong("size", -1L))
            if (found == null) {
                missing++
                continue
            }
            db.execSQL("UPDATE song SET localUri = ? WHERE id = ?", arrayOf(found, id))
        }
        return missing
    }

    // looks the file up by name and size across internal storage and mounted SD cards, null without audio permission or when it isn't on this phone
    private fun findInMediaStore(name: String, size: Long): String? {
        if (name.isBlank()) return null
        val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull().orEmpty()
            if (volumes.isNotEmpty()) volumes.map { MediaStore.Audio.Media.getContentUri(it) }
            else listOf(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
        val (selection, args) = if (size > 0) {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?" to arrayOf(name, size.toString())
        } else {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?" to arrayOf(name)
        }
        for (collection in collections) {
            val found = runCatching {
                context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), selection, args, null)?.use { cursor ->
                    if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()).toString() else null
                }
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    // drops any login that slipped in and moves saved paths, in settings.json and each prefs file alike
    private fun rewriteSettings(file: File, mover: PathMover) {
        if (!file.exists()) return
        val json = JSONObject(file.readText())
        TransferArchive.LoginKeys.forEach { json.remove(it) }
        file.writeText(TransferArchive.mapStrings(json, mover::move).toString())
    }

    private fun Result<TransferManifest>.friendly(): Result<TransferManifest> = recoverCatching { error ->
        throw when (error) {
            is TransferException -> error
            is NotAnExport -> TransferException("That isn't a Mochy export. Pick the .zip you exported from your old app.")
            else -> TransferException("Couldn't import that file. Make sure there's free space and try again.", error)
        }
    }

    private class NotAnExport : Exception()

    private class PathMover(val from: List<String>, val to: String) {
        fun move(value: String): String = from.fold(value) { text, old -> text.replace(old, to) }
    }

    companion object {
        private const val SpareBytes = 64L * 1024 * 1024
        private const val MaxManifestBytes = 2 * 1024 * 1024
        private const val MaxJsonEntryBytes = 16L * 1024 * 1024

        /** true when room can walk the old database up to this version without wiping it, picking migrations the way room does */
        fun hasMigrationPath(from: Int, to: Int): Boolean {
            if (from == to) return true
            if (from <= 0 || from > to) return false
            var at = from
            while (at < to) {
                at = AllMigrations.filter { it.startVersion == at && it.endVersion <= to }.maxOfOrNull { it.endVersion } ?: return false
            }
            return true
        }
    }
}
