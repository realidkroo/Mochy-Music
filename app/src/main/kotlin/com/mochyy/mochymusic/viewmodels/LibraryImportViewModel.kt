// LibraryImportViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.importer.DeviceMusicScanner
import com.mochyy.mochymusic.importer.DeviceSong
import com.mochyy.mochymusic.importer.FolderScan
import com.mochyy.mochymusic.importer.ImportHistory
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.LocalArtwork
import com.mochyy.mochymusic.importer.LocalFile
import com.mochyy.mochymusic.importer.LocalFolders
import com.mochyy.mochymusic.importer.LocalImportService
import com.mochyy.mochymusic.importer.LocalMissingFiles
import com.mochyy.mochymusic.importer.LocalSongRepair
import androidx.media3.exoplayer.offline.DownloadService
import com.mochyy.mochymusic.models.TransferBatch
import com.mochyy.mochymusic.models.TransferState
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.ui.component.TopToaster
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** A storage scan for the Add music sheet. */
sealed interface DeviceScan {
    data object Idle : DeviceScan
    data object Scanning : DeviceScan
    data class Found(val songs: List<DeviceSong>) : DeviceScan
}

/** What a re-scan did: whether an import started, and whether it already told the user something worth more than "no new songs". */
data class Rescan(val started: Boolean, val alreadyTold: Boolean)

// a scan's longer notes (the folder cap, skipped formats) stay up long enough to read
private const val LongNoticeMs = 5_000L

/**
 * Backs the Library's import side: the progress of downloads and local imports, the storage scan
 * behind Add music, and the Manage sheets' undo and delete actions. Results are told with a toast
 * at the top, since the sheet that asked has usually closed by then.
 */
@HiltViewModel
class LibraryImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val localImport: LocalImportService,
    private val history: ImportHistory,
    downloadUtil: DownloadUtil,
) : ViewModel() {

    val downloads: StateFlow<TransferBatch> = downloadUtil.batch

    val localImports: StateFlow<TransferBatch> = localImport.batch

    private val _scan = MutableStateFlow<DeviceScan>(DeviceScan.Idle)
    val scan: StateFlow<DeviceScan> = _scan.asStateFlow()

    /** Looks for songs on the phone that aren't in the library yet. */
    fun scanDevice() {
        if (_scan.value == DeviceScan.Scanning) return
        _scan.value = DeviceScan.Scanning
        viewModelScope.launch {
            val known = withContext(Dispatchers.IO) { knownLocalUris(withMediaStore = true) }
            _scan.value = DeviceScan.Found(DeviceMusicScanner.scan(context, known))
        }
    }

    /**
     * Every file already in the library. With [withMediaStore], songs that came in through a
     * picked folder also count by their MediaStore uri, so the phone-wide scan doesn't offer them
     * a second time.
     */
    private fun knownLocalUris(withMediaStore: Boolean): Set<String> {
        val uris = database.localUris()
        if (!withMediaStore) return uris.toSet()
        return uris.toHashSet().apply {
            uris.filter { it.startsWith("content://") && !it.startsWith("content://media/") }.forEach { uri ->
                LocalFolders.mediaStoreUri(context, Uri.parse(uri))?.let { add(it.toString()) }
            }
        }
    }

    /**
     * A music folder the user just picked: kept for later re-scans, and its songs that aren't in
     * the library yet are imported now. Returns false when there was nothing to start.
     */
    suspend fun addFolder(tree: Uri): Boolean {
        val scanned = withContext(Dispatchers.IO) {
            runCatching { LocalFolders.add(context, tree) }.getOrNull() ?: return@withContext null
            LocalFolders.scan(context, tree)
        }
        if (scanned == null) {
            TopToaster.show("Couldn't keep access to that folder. Try picking it again.", iconRes = R.drawable.library_music)
            return false
        }
        // picking a folder again also brings the lyrics and covers beside its songs to the ones already in
        reportRepaired(LocalSongRepair.repair(context, database, scanned.files))
        val files = withContext(Dispatchers.IO) { LocalFolders.onlyNew(context, scanned.files, knownLocalUris(withMediaStore = false)) }
        val started = startFolderImport(files, emptyMessage = "Every song in that folder is already in your library")
        reportScan(scanned, missing = emptyList())
        return started
    }

    /**
     * Re-scan: brings back covers that went missing, gives songs in the saved music folders the
     * lyrics and covers sitting beside them, imports new files and offers to drop songs whose file is gone.
     */
    suspend fun refreshLocal(): Rescan {
        val (fixedCovers, scanned) = withContext(Dispatchers.IO) {
            val fixed = LocalArtwork.repair(context, database, includeMissing = true)
            fixed to LocalFolders.scanSaved(context)
        }
        if (fixedCovers > 0) {
            TopToaster.show(if (fixedCovers == 1) "Brought back 1 cover" else "Brought back $fixedCovers covers", iconRes = R.drawable.library_music)
        }
        reportRepaired(LocalSongRepair.repair(context, database, scanned.files))
        val (files, missing) = withContext(Dispatchers.IO) {
            val songs = database.localSongsByRowIdAsc().first().map { it.song }
            LocalFolders.onlyNew(context, scanned.files, knownLocalUris(withMediaStore = false)) to LocalMissingFiles.find(songs, scanned)
        }
        val started = files.isNotEmpty() && startFolderImport(files, emptyMessage = null)
        return Rescan(started = started, alreadyTold = reportScan(scanned, missing))
    }

    private fun reportRepaired(count: Int) {
        if (count == 0) return
        TopToaster.show(context.resources.getQuantityString(R.plurals.lib_local_repaired, count, count), iconRes = R.drawable.library_music)
    }

    // the one thing from a scan worth saying, most useful first, since a newer toast covers the last; true when something was said
    private fun reportScan(scan: FolderScan, missing: List<SongEntity>): Boolean {
        val resources = context.resources
        when {
            missing.isNotEmpty() -> TopToaster.show(
                text = resources.getQuantityString(R.plurals.local_missing_files, missing.size, missing.size),
                iconRes = R.drawable.library_music,
                actionLabel = context.getString(R.string.local_missing_remove),
                onAction = { removeLocalSongs(missing.map { it.id }) },
            )
            scan.truncated -> TopToaster.show(
                text = context.getString(R.string.local_import_folder_cap, LocalFolders.MAX_FILES),
                iconRes = R.drawable.library_music,
                durationMs = LongNoticeMs,
            )
            scan.unplayable > 0 -> TopToaster.show(
                text = resources.getQuantityString(R.plurals.local_import_skipped_formats, scan.unplayable, scan.unplayable),
                iconRes = R.drawable.library_music,
                durationMs = LongNoticeMs,
            )
            else -> return false
        }
        return true
    }

    /** True if Mochy has music folders the user picked. */
    fun hasFolders(): Boolean = LocalFolders.read(context).isNotEmpty()

    private fun startFolderImport(files: List<LocalFile>, emptyMessage: String?): Boolean {
        if (files.isEmpty()) {
            emptyMessage?.let { TopToaster.show(it, iconRes = R.drawable.library_music) }
            return false
        }
        if (!localImport.importFolderFiles(files)) {
            TopToaster.show("An import is still running. Try again once it's done.", iconRes = R.drawable.library_music)
            return false
        }
        return true
    }

    fun importPicked(uris: List<Uri>): Boolean = localImport.importPicked(uris)

    fun importDeviceSongs(songs: List<DeviceSong>): Boolean = localImport.importDeviceSongs(songs)

    fun clearLocalImport() = localImport.clearFinished()

    /** Stops every download that hasn't finished. The ones already done stay in the library. */
    fun cancelAllDownloads() {
        val running = downloads.value.items.filter { it.state == TransferState.Waiting || it.state == TransferState.Running }
        running.forEach { item ->
            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, item.key, false)
        }
        TopToaster.show(
            if (running.size == 1) "Cancelled 1 download" else "Cancelled ${running.size} downloads",
            iconRes = R.drawable.library_music,
        )
    }

    // ---- Manage imports ----

    fun deleteAllImports() = report { "Removed ${songs(history.deleteAllImports())} you imported" }

    fun undoLatestImport() = report {
        history.undoLatestImport()?.let { "Undid your last import: ${songs(it)} removed" } ?: "There's no import to undo"
    }

    fun deleteImportsFrom(source: ImportSource) = report {
        "Removed ${songs(history.deleteImportsFrom(source))} imported from ${source.label}"
    }

    // ---- Manage local ----

    fun undoLatestLocalImport() = report {
        history.undoLatestLocalImport()?.let { "Undid your last local import: ${songs(it)} removed" }
            ?: "There's no local import to undo"
    }

    fun removeLocalSongs(songIds: Collection<String>) = report {
        history.removeLocalSongs(songIds)
        "Removed ${songs(songIds.size)}. The files are still on your phone."
    }

    /**
     * Fills the playlist "Create playlist" just made with a finished batch. The create sheet saves
     * the playlist on a background thread and doesn't wait for it, so this waits for it to exist.
     */
    fun fillPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = withTimeoutOrNull(5_000) { database.playlist(playlistId).filterNotNull().first() } ?: return@launch
            database.withTransaction { addSongToPlaylist(playlist, songIds) }
        }
    }

    private fun report(action: suspend () -> String) {
        viewModelScope.launch {
            val message = action()
            TopToaster.show(message, iconRes = R.drawable.library_music)
        }
    }

    private fun songs(count: Int) = if (count == 1) "1 song" else "$count songs"
}
