// TransferExport.kt

package com.mochyy.mochymusic.importer.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.database.DatabaseProvider
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.di.ApplicationScope
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.StoragePlaces
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** live status of a user-data export, held on the singleton exporter so leaving settings never cancels it */
sealed interface TransferExportStatus {
    data object Idle : TransferExportStatus
    data class Working(val progress: Float?) : TransferExportStatus
    data object Done : TransferExportStatus
    data class Failed(val message: String) : TransferExportStatus
}

/** packs the whole library into one zip: database, play history, settings, the home algorithm, downloads and local copies, everything but logins */
@Singleton
class TransferExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val database: MusicDatabase,
    private val databaseProvider: DatabaseProvider,
    private val downloadUtil: DownloadUtil,
) {
    private val running = Mutex()
    private val _status = MutableStateFlow<TransferExportStatus>(TransferExportStatus.Idle)
    val status: StateFlow<TransferExportStatus> = _status.asStateFlow()

    /** runs the export on the application scope so navigating away from Storage Settings never cancels it or leaves a 0-byte zip */
    fun startExport(target: Uri) {
        if (_status.value is TransferExportStatus.Working) return
        _status.value = TransferExportStatus.Working(0f)
        applicationScope.launch {
            export(target) { _status.value = TransferExportStatus.Working(it) }
                .onSuccess { _status.value = TransferExportStatus.Done }
                .onFailure {
                    Timber.e(it, "User data export failed")
                    _status.value = TransferExportStatus.Failed(it.message ?: "Couldn't export. Try again.")
                }
        }
    }

    /** writes the zip to target, onProgress goes from 0 to 1, the failure message is ready to show */
    suspend fun export(target: Uri, onProgress: (Float) -> Unit): Result<TransferManifest> =
        exclusive { work -> withDownloadsPaused { pack(work, withSongs = true, onProgress) { openTarget(target) } } }
            .onSuccess { markExported(context) }
            .onFailure {
                // remove the empty 0-byte placeholder file that ACTION_CREATE_DOCUMENT created so users aren't left with a 0 MB zip
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            }

    /** the same zip without a single song, for cloud backups: library, stats, recap, settings and pictures, so it stays small */
    suspend fun exportBackup(target: File, onProgress: (Float) -> Unit): Result<TransferManifest> =
        exclusive { work -> pack(work, withSongs = false, onProgress) { target.outputStream() } }

    // one export at a time, in a scratch folder that's always cleaned up, failing with a message ready to show
    private suspend fun exclusive(block: suspend (work: File) -> TransferManifest): Result<TransferManifest> = withContext(Dispatchers.IO) {
        if (!running.tryLock()) return@withContext Result.failure(TransferException("An export is already running."))
        val work = File(context.cacheDir, "transfer_export").apply { deleteRecursively(); mkdirs() }
        try {
            runCatching { block(work) }
                .recoverCatching { throw if (it is TransferException) it else TransferException("Couldn't export. Make sure there's free space and try again.", it) }
        } finally {
            work.deleteRecursively()
            running.unlock()
        }
    }

    // "wt" empties a file picked twice, some providers like drive only know "w", and a raw pfd covers quirky OEM SAF providers
    private fun openTarget(target: Uri): OutputStream =
        runCatching { context.contentResolver.openOutputStream(target, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(target, "w") }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(target, "w")?.let { ParcelFileDescriptor.AutoCloseOutputStream(it) }
            }.getOrNull()
            ?: throw TransferException("Couldn't open the file you picked. Try picking another folder.")

    private suspend fun pack(work: File, withSongs: Boolean, onProgress: (Float) -> Unit, open: () -> OutputStream): TransferManifest {
        onProgress(0f)
        val library = File(work, "library.db")
        val live = database.openHelper.writableDatabase
        snapshotDatabase(
            source = File(requireNotNull(live.path)),
            target = library,
            begin = live::beginTransaction,
            end = live::endTransaction,
            vacuumInto = { path -> live.execSQL("VACUUM INTO ?", arrayOf(path)) },
            checkpoint = { live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } },
        )

        // media3's database is the list of downloads, which only means something with the songs beside it
        val media = File(work, "media.db")
        val liveMedia = databaseProvider.writableDatabase
        val hasMedia = withSongs && runCatching {
            snapshotDatabase(
                source = File(liveMedia.path),
                target = media,
                begin = liveMedia::beginTransaction,
                end = liveMedia::endTransaction,
                vacuumInto = { path -> liveMedia.execSQL("VACUUM INTO ?", arrayOf(path)) },
                checkpoint = { liveMedia.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } },
            )
            dropStreamingCacheIndex(media)
        }.isSuccess

        val settings = TransferArchive.preferencesToJson(context.dataStore.data.first(), skip = TransferArchive.LoginKeys)
        val prefs = TransferArchive.PrefsToCarry.mapValues { (_, fileName) ->
            TransferArchive.sharedPreferencesToJson(context.getSharedPreferences(fileName, Context.MODE_PRIVATE))
        }
        val localSongs = describeLocalSongs(library)
        val files = carriedFiles(withSongs)

        val totalBytes = library.length() + (if (hasMedia) media.length() else 0L) + files.sumOf { it.second.length() }
        val manifest = readManifest(library, totalBytes, localSongs = localSongs.length())

        val stream = open()
        val counting = CountingOutputStream(stream)
        val buffered = BufferedOutputStream(counting, 256 * 1024)
        // reuse one copy buffer across every entry instead of allocating 128 KB per file
        val copyBuffer = ByteArray(128 * 1024)
        var written = 0L
        var lastReported = 0f
        val report: (Long) -> Unit = { bytes ->
            written += bytes
            if (totalBytes > 0) {
                val p = (written.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                if (p - lastReported >= 0.01f || p >= 1f) {
                    lastReported = p
                    onProgress(p)
                }
            }
        }
        ZipOutputStream(buffered).use { zip ->
            var currentLevel = Deflater.BEST_SPEED
            zip.setLevel(currentLevel)
            zip.putText(TransferArchive.ManifestEntry, manifest.toJson().toString())
            zip.putText(TransferArchive.SettingsEntry, settings.toString())
            prefs.forEach { (name, json) -> zip.putText("${TransferArchive.PrefsPrefix}$name.json", json.toString()) }
            zip.putText(TransferArchive.LocalSongsEntry, localSongs.toString())
            zip.putFile(TransferArchive.LibraryEntry, library, copyBuffer, report)
            if (hasMedia) zip.putFile(TransferArchive.MediaEntry, media, copyBuffer, report)
            for ((relative, file) in files) {
                // songs are already squeezed, so they go in as they are and the export stays quick
                val wantedLevel = if (relative.substringBefore('/') in TransferArchive.SongFolders) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED
                if (wantedLevel != currentLevel) {
                    zip.setLevel(wantedLevel)
                    currentLevel = wantedLevel
                }
                zip.putFile(TransferArchive.FilesPrefix + relative, file, copyBuffer, report)
            }
            zip.finish()
            buffered.flush()
            // force FUSE / SAF providers to commit buffered bytes to disk before closing the descriptor
            if (stream is FileOutputStream) runCatching { stream.fd.sync() }
        }
        if (counting.bytesWritten <= 0L) {
            throw TransferException("Export produced an empty file. Try picking another folder.")
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
    private fun carriedFiles(withSongs: Boolean): List<Pair<String, File>> {
        val root = context.filesDir
        val downloads = if (withSongs) StoragePlaces.dir(context, StoredKind.Downloads) else null
        val downloadsOnExternal = downloads != null && downloads.parentFile != root
        val onPhone = root.listFiles().orEmpty()
            .filter {
                TransferArchive.carriesFile(it.name) &&
                    (withSongs || it.name !in TransferArchive.SongFolders) &&
                    // skip any leftover phone download/ folder when active downloads live on an SD card so zip never gets duplicate entries
                    (!downloadsOnExternal || it.name != StoredKind.Downloads.folder)
            }
            .flatMap { top -> top.walkTopDown().filter { it.isFile }.toList() }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
        if (!downloadsOnExternal) return onPhone
        // downloads kept on a memory card go in under the same name, so the other phone finds them where it looks
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

    private fun ZipOutputStream.putFile(name: String, file: File, buffer: ByteArray, report: (Long) -> Unit) {
        // a cache file can be swapped out while the export runs, one that's gone is skipped instead of failing everything
        val input = runCatching { file.inputStream() }.getOrNull() ?: return
        input.use {
            putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
            it.copyInto(this, buffer, report)
            closeEntry()
        }
    }

    private fun InputStream.copyInto(out: ZipOutputStream, buffer: ByteArray, report: (Long) -> Unit) {
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            report(read.toLong())
        }
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
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
