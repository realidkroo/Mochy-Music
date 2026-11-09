// StorageViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import coil3.SingletonImageLoader
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.playback.CachedSongs
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.DownloadExporter
import com.mochyy.mochymusic.playback.ExportChoice
import com.mochyy.mochymusic.playback.ExportStep
import com.mochyy.mochymusic.playback.MoveStep
import com.mochyy.mochymusic.playback.StorageMover
import com.mochyy.mochymusic.playback.StoragePlace
import com.mochyy.mochymusic.playback.StoragePlaces
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** one drive mochy keeps things on, for its own overall card */
data class DriveUsage(
    val label: String,
    val isPhone: Boolean,
    val total: Long,
    val free: Long,
    /** what mochy has on this drive */
    val mochy: Long,
) {
    val othersUsed: Long get() = (total - free - mochy).coerceAtLeast(0L)
}

/** How much room Mochy takes, in the four parts the Storage page shows, and every drive it sits on. */
data class StorageUsage(
    /** the phone first, then any memory card holding downloads or the cache */
    val drives: List<DriveUsage>,
    /** songs streamed and kept for next time, cover pictures, and other throwaway files */
    val cache: Long,
    /** downloaded songs and the covers saved for local files */
    val downloads: Long,
    val database: Long,
    /** the app itself and its settings, everything not counted above */
    val appData: Long,
) {
    val mochy: Long get() = cache + downloads + database + appData
    val phone: DriveUsage get() = drives.first()
}

/** A finished download, with what it costs on the phone. */
data class DownloadedSong(
    val song: Song,
    val bytes: Long,
    /** when the download finished, ms since 1970 */
    val downloadedAt: Long,
)

/** the drives one kind of folder could go to, where it is now, and how much a move would copy */
data class PlaceChoice(
    val places: List<StoragePlace>,
    val current: StoragePlace,
    val bytesToMove: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val cachedSongs: CachedSongs,
    private val downloadUtil: DownloadUtil,
    private val mover: StorageMover,
    private val exporter: DownloadExporter,
) : ViewModel() {

    val moveStep: StateFlow<MoveStep> = mover.step
    val exportStep: StateFlow<ExportStep> = exporter.step

    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage.asStateFlow()

    /** True while something is being deleted, so the buttons can't be tapped twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Every finished download, newest first; the sheet sorts it however it likes. */
    val downloads: StateFlow<List<DownloadedSong>?> = downloadUtil.downloads
        .map { all -> all.values.filter { it.state == Download.STATE_COMPLETED } }
        .distinctUntilChanged { old, new -> old.map { it.request.id } == new.map { it.request.id } }
        .mapLatest { done ->
            val byId = done.associateBy { it.request.id }
            val songs = if (byId.isEmpty()) emptyList() else database.getSongsByIds(byId.keys.toList())
            songs.mapNotNull { song ->
                val download = byId[song.id] ?: return@mapNotNull null
                DownloadedSong(song, download.bytesDownloaded.coerceAtLeast(0L), download.updateTimeMs)
            }.sortedByDescending { it.downloadedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Walks Mochy's folders again. The page calls it on open and whenever the downloads change. */
    fun measure() {
        viewModelScope.launch { _usage.value = withContext(Dispatchers.IO) { readUsage() } }
    }

    /** Songs kept from streaming and every cover picture. Downloads and the library stay. */
    fun clearCaches() = runBusy {
        cachedSongs.clear().join()
        SingletonImageLoader.get(context).let { loader ->
            loader.memoryCache?.clear()
            withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        }
    }

    fun removeAllDownloads() = runBusy {
        DownloadService.sendRemoveAllDownloads(context, ExoDownloadService::class.java, false)
        // the service deletes in the background; give it a moment before measuring again
        delay(1_200)
    }

    fun removeDownloads(songIds: Collection<String>) = runBusy {
        songIds.forEach { DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, it, false) }
        delay(800)
    }

    /** the drives on offer for [kind], read off the main thread since it measures the folder */
    suspend fun placeChoice(kind: StoredKind): PlaceChoice = withContext(Dispatchers.IO) {
        PlaceChoice(StoragePlaces.places(context), StoragePlaces.placeOf(context, kind), mover.bytesToMove(kind))
    }

    // a folder from the folder picker as the place mochy would save into, or null when it isn't on the phone or a card
    suspend fun pickedFolder(tree: android.net.Uri): StoragePlace? = withContext(Dispatchers.IO) {
        StoragePlaces.folderFromTree(context, tree)?.let { StoragePlaces.folderPlace(context, StoragePlaces.rootForPicked(it)) }
    }

    fun needsFolderAccess(place: StoragePlace): Boolean = StoragePlaces.needsFolderAccess(context, place.root)

    fun move(kind: StoredKind, to: StoragePlace) = mover.move(kind, to)

    fun clearMoveFailure() = mover.clearFailure()

    fun export(tree: android.net.Uri, choice: ExportChoice) = exporter.start(tree, choice)

    fun clearExport() = exporter.clear()

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _usage.value = withContext(Dispatchers.IO) { readUsage() }
                _busy.value = false
            }
        }
    }

    private fun readUsage(): StorageUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val dataDir = context.dataDir
        val songCacheDir = StoragePlaces.dir(context, StoredKind.SongCache)
        val downloadDir = StoragePlaces.dir(context, StoredKind.Downloads)
        val localArtwork = context.filesDir.resolve("local_artwork")
        val databases = context.getDatabasePath("x").parentFile

        val songCache = sizeOf(songCacheDir)
        val downloadedSongs = sizeOf(downloadDir)
        val cache = songCache + sizeOf(context.cacheDir) + sizeOf(context.codeCacheDir)
        val downloads = downloadedSongs + sizeOf(localArtwork)
        val db = sizeOf(databases)
        // only what sits in the data folder comes off it before the rest is counted, a folder on a card was never in it
        val onCards = listOf(songCacheDir to songCache, downloadDir to downloadedSongs).filter { (dir, _) -> dir.parentFile != context.filesDir }
        val rest = (sizeOf(dataDir) - (cache + downloads - onCards.sumOf { it.second }) - db).coerceAtLeast(0L)
        val apk = context.applicationInfo.run {
            (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        }
        val total = cache + downloads + db + rest + apk
        val phone = DriveUsage(
            label = context.getString(R.string.storage_place_phone),
            isPhone = true,
            total = stat.totalBytes,
            free = stat.availableBytes,
            mochy = total - onCards.sumOf { it.second },
        )
        // a card holding both downloads and the cache gets one card on the page, not two
        val cards = StoredKind.entries
            .map { StoragePlaces.placeOf(context, it) }
            .filter { !it.isPhone }
            .distinctBy { it.root }
            .map { place ->
                DriveUsage(
                    label = place.label,
                    isPhone = false,
                    total = place.total,
                    free = place.free,
                    mochy = onCards.filter { (dir, _) -> dir.parentFile == place.root }.sumOf { it.second },
                )
            }
        return StorageUsage(
            drives = listOf(phone) + cards,
            cache = cache,
            downloads = downloads,
            database = db,
            appData = rest + apk,
        )
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        return runCatching { file.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }
}
