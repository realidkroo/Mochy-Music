// TransferExport.kt

package com.mochyy.mochymusic.importer.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.database.DatabaseProvider
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.StoragePlaces
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** packs the whole library into one zip: database, play history, settings, the home algorithm, downloads and local copies, everything but logins */
@Singleton
class TransferExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val databaseProvider: DatabaseProvider,
    private val downloadUtil: DownloadUtil,
) {
    private val running = Mutex()

    /** writes the zip to target, onProgress goes from 0 to 1, the failure message is ready to show */
    suspend fun export(target: Uri, onProgress: (Float) -> Unit): Result<TransferManifest> = withContext(Dispatchers.IO) {
        if (!running.tryLock()) return@withContext Result.failure(TransferException("An export is already running."))
        val work = File(context.cacheDir, "transfer_export").apply { deleteRecursively(); mkdirs() }
        try {
            runCatching { withDownloadsPaused { pack(target, work, onProgress) } }
                .onSuccess { markExported(context) }
                .recoverCatching { throw if (it is TransferException) it else TransferException("Couldn't export. Make sure there's free space and try again.", it) }
        } finally {
            work.deleteRecursively()
            running.unlock()
        }
    }

    private suspend fun pack(target: Uri, work: File, onProgress: (Float) -> Unit): TransferManifest {
        onProgress(0f)
        val library = File(work, "library.db")
        val live = database.openHelper.writableDatabase
        snapshot(File(requireNotNull(live.path)), library, begin = live::beginTransaction, end = live::endTransaction)

        val media = File(work, "media.db")
        val liveMedia = databaseProvider.writableDatabase
        val hasMedia = runCatching {
            snapshot(File(liveMedia.path), media, begin = liveMedia::beginTransaction, end = liveMedia::endTransaction)
            dropStreamingCacheIndex(media)
        }.isSuccess

        val settings = TransferArchive.preferencesToJson(context.dataStore.data.first(), skip = TransferArchive.LoginKeys)
        val prefs = TransferArchive.PrefsToCarry.mapValues { (_, fileName) ->
            TransferArchive.sharedPreferencesToJson(context.getSharedPreferences(fileName, Context.MODE_PRIVATE))
        }
        val localSongs = describeLocalSongs(library)
        val files = carriedFiles()

        val totalBytes = library.length() + (if (hasMedia) media.length() else 0L) + files.sumOf { it.second.length() }
        val manifest = readManifest(library, totalBytes, localSongs = localSongs.length())

        // "wt" empties a file picked twice, some providers like drive only know "w"
        val stream = runCatching { context.contentResolver.openOutputStream(target, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(target, "w") }.getOrNull()
            ?: throw TransferException("Couldn't open the file you picked. Try picking another folder.")
        var written = 0L
        val report: (Long) -> Unit = { bytes ->
            written += bytes
            if (totalBytes > 0) onProgress((written.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f))
        }
        ZipOutputStream(stream.buffered(256 * 1024)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putText(TransferArchive.ManifestEntry, manifest.toJson().toString())
            zip.putText(TransferArchive.SettingsEntry, settings.toString())
            prefs.forEach { (name, json) -> zip.putText("${TransferArchive.PrefsPrefix}$name.json", json.toString()) }
            zip.putText(TransferArchive.LocalSongsEntry, localSongs.toString())
            zip.putFile(TransferArchive.LibraryEntry, library, report)
            if (hasMedia) zip.putFile(TransferArchive.MediaEntry, media, report)
            for ((relative, file) in files) {
                // songs are already squeezed, so they go in as they are and the export stays quick
                zip.setLevel(if (relative.startsWith("download/") || relative.startsWith("shared_audio/")) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED)
                zip.putFile(TransferArchive.FilesPrefix + relative, file, report)
            }
        }
        onProgress(1f)
        return manifest
    }

    // downloads keep writing into the download folder, so they wait while it's copied and carry on after
    private suspend fun <T> withDownloadsPaused(block: suspend () -> T): T {
        val manager = downloadUtil.downloadManager
        val wasPaused = withContext(Dispatchers.Main) { manager.downloadsPaused.also { manager.pauseDownloads() } }
        if (!wasPaused) delay(400)
        try {
            return block()
        } finally {
            if (!wasPaused) withContext(Dispatchers.Main) { manager.resumeDownloads() }
        }
    }

    // copies a live database while holding its write lock, so nothing lands halfway, then folds its wal into one plain file
    private fun snapshot(source: File, target: File, begin: () -> Unit, end: () -> Unit) {
        val targetWal = File(target.path + "-wal")
        target.delete()
        targetWal.delete()
        begin()
        try {
            source.copyTo(target, overwrite = true)
            File(source.path + "-wal").takeIf { it.exists() }?.copyTo(targetWal, overwrite = true)
        } finally {
            end()
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            foldIntoOneFile(copy)
            copy.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) { "snapshot of ${source.name} failed its check" }
            }
        }
        listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
    }

    // the streaming cache stays behind, so its tables in media3's database go too
    private fun dropStreamingCacheIndex(media: File) {
        val uids = StoragePlaces.dir(context, StoredKind.SongCache).listFiles { file -> file.name.endsWith(".uid") }
            ?.map { it.name.removeSuffix(".uid") }.orEmpty()
        if (uids.isEmpty()) return
        SQLiteDatabase.openDatabase(media.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            for (uid in uids) {
                if (!uid.all { it.isLetterOrDigit() }) continue
                runCatching { db.execSQL("DROP TABLE IF EXISTS ExoPlayerCacheIndex$uid") }
                runCatching { db.execSQL("DROP TABLE IF EXISTS ExoPlayerCacheFileMetadata$uid") }
                runCatching { db.execSQL("DELETE FROM ExoPlayerVersions WHERE instance_uid = ?", arrayOf(uid)) }
            }
        }
    }

    // every local song with where its file is, picked files also get their media store address while this app can still read them
    private fun describeLocalSongs(library: File): JSONArray {
        val out = JSONArray()
        SQLiteDatabase.openDatabase(library.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id, localUri FROM song WHERE localUri IS NOT NULL", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val uri = cursor.getString(1) ?: continue
                    val entry = JSONObject().put("id", id).put("uri", uri)
                    val parsed = Uri.parse(uri)
                    if (parsed.scheme == "content" && parsed.authority != MediaStore.AUTHORITY) {
                        mediaStoreUri(parsed)?.let { entry.put("mediaUri", it.toString()) }
                        describeFile(parsed)?.let { (name, size) -> entry.put("name", name).put("size", size) }
                    }
                    out.put(entry)
                }
            }
        }
        return out
    }

    private fun mediaStoreUri(document: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { MediaStore.getMediaUri(context, document) }.getOrNull()
    }

    private fun describeFile(document: Uri): Pair<String, Long>? = runCatching {
        context.contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(0) ?: return@use null
            name to (if (cursor.isNull(1)) -1L else cursor.getLong(1))
        }
    }.getOrNull()

    // everything under the files folder that carries over, as zip-style relative paths
    private fun carriedFiles(): List<Pair<String, File>> {
        val root = context.filesDir
        val onPhone = root.listFiles().orEmpty()
            .filter { TransferArchive.carriesFile(it.name) }
            .flatMap { top -> top.walkTopDown().filter { it.isFile }.toList() }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
        // downloads kept on a memory card go in under the same name, so the other phone finds them where it looks
        val downloads = StoragePlaces.dir(context, StoredKind.Downloads)
        if (downloads.parentFile == root) return onPhone
        val onCard = downloads.walkTopDown().filter { it.isFile }
            .map { "${StoredKind.Downloads.folder}/${it.relativeTo(downloads).invariantSeparatorsPath}" to it }
        return onPhone + onCard
    }

    private fun readManifest(library: File, totalBytes: Long, localSongs: Int): TransferManifest =
        SQLiteDatabase.openDatabase(library.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun count(sql: String) = runCatching { db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrDefault(0)
            TransferManifest(
                formatVersion = TransferArchive.FormatVersion,
                createdAt = System.currentTimeMillis(),
                appId = context.packageName,
                appVersion = BuildConfig.VERSION_NAME,
                dataDir = context.dataDir.path,
                databaseVersion = db.version,
                songs = count("SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL OR liked = 1"),
                playlists = count("SELECT COUNT(*) FROM playlist"),
                plays = count("SELECT COUNT(*) FROM event"),
                localSongs = localSongs,
                bytes = totalBytes,
            )
        }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File, report: (Long) -> Unit) {
        // a cache file can be swapped out while the export runs, one that's gone is skipped instead of failing everything
        val input = runCatching { file.inputStream() }.getOrNull() ?: return
        input.use {
            putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
            it.copyInto(this, report)
            closeEntry()
        }
    }

    private fun InputStream.copyInto(out: ZipOutputStream, report: (Long) -> Unit) {
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            report(read.toLong())
        }
    }

    companion object {
        private const val StatePrefs = "transfer_state"
        private const val ExportedAtKey = "exported_at"

        /** when this phone last finished an export, 0 if it never did */
        fun lastExportedAt(context: Context): Long =
            context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE).getLong(ExportedAtKey, 0L)

        private fun markExported(context: Context) {
            context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE).edit()
                .putLong(ExportedAtKey, System.currentTimeMillis()).apply()
        }
    }
}

/** a transfer failure whose message is already friendly enough to show as it is */
class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)
