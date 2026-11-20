// LocalImportService.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.db.entities.ImportSessionSong
import com.mochyy.mochymusic.models.TransferBatch
import com.mochyy.mochymusic.models.TransferItem
import com.mochyy.mochymusic.models.TransferState
import com.mochyy.mochymusic.ui.component.TopToaster
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** imports audio files from the phone one at a time in the background, here and not in a screen so it keeps going when the sheet closes and the progress card can show it */
@Singleton
class LocalImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Timber.e(e, "Local import failed") },
    )
    private var job: Job? = null

    private val _batch = MutableStateFlow(TransferBatch())

    /** the import under way, or the last one until clearFinished */
    val batch: StateFlow<TransferBatch> = _batch.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    /** a file waiting to be imported, with the lyrics and cover found beside it. picked files need their read access kept to still play later */
    private class Pending(
        val uri: Uri,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val keepAccess: Boolean,
        val lyrics: Uri? = null,
        val cover: Uri? = null,
    )

    /** files chosen in the system file picker, lyrics and pictures picked with them go to their songs. false if an import is already running */
    fun importPicked(uris: List<Uri>): Boolean =
        start(uris.map { Pending(it, title = "", subtitle = null, artworkUrl = null, keepAccess = true) }) {
            val picked = LocalSidecars.pairPicked(context, uris)
            notePicked(picked)
            picked.files.map { it.toPending(keepAccess = true) }
        }

    // a big pick is better as a folder, since android drops the oldest single-file grants past its limit; otherwise says what was left out
    private fun notePicked(picked: FolderScan) {
        val held = runCatching { context.contentResolver.persistedUriPermissions.size }.getOrDefault(0)
        when {
            picked.files.size >= PickFolderHintAt || held + picked.files.size > GrantLimit * 9 / 10 -> TopToaster.show(
                text = context.getString(R.string.local_import_pick_folder_hint),
                iconRes = R.drawable.library_music,
                durationMs = 6_000L,
            )
            picked.unplayable > 0 -> TopToaster.show(
                text = context.resources.getQuantityString(R.plurals.local_import_skipped_formats, picked.unplayable, picked.unplayable),
                iconRes = R.drawable.library_music,
                durationMs = 5_000L,
            )
        }
    }

    /** songs found by a storage scan. false if an import is already running */
    fun importDeviceSongs(songs: List<DeviceSong>): Boolean =
        start(songs.map { Pending(it.uri, it.title, it.artist, it.artworkUri, keepAccess = false) })

    /** files found in a music folder the user picked. access comes with the folder, so none is taken per file */
    fun importFolderFiles(files: List<LocalFile>): Boolean =
        start(files.map { it.toPending(keepAccess = false) })

    /** forgets a finished import, so the progress card and sheet start clean next time */
    fun clearFinished() {
        if (!isRunning) _batch.value = TransferBatch()
    }

    // prepare runs before the import, off the main thread, for work like sorting picked files that has to read them first
    private fun start(picked: List<Pending>, prepare: (suspend () -> List<Pending>)? = null): Boolean {
        if (isRunning || picked.isEmpty()) return false
        _batch.value = TransferBatch(picked.distinctBy { it.uri }.map { it.toItem() })
        job = scope.launch {
            // the same file picked twice would come in twice
            val files = (prepare?.invoke() ?: picked).distinctBy { it.uri }
            if (prepare != null) _batch.value = TransferBatch(files.map { it.toItem() })
            if (files.isEmpty()) return@launch
            val session = database.insert(ImportSession(source = ImportSession.DEVICE_SOURCE, startedAt = LocalDateTime.now()))
            try {
                files.forEachIndexed { index, file -> importOne(index, file, session) }
            } finally {
                withContext(NonCancellable) {
                    database.deleteImportSessionIfEmpty(session)
                    // stopped part way: what never started didn't come in
                    _batch.update { batch ->
                        TransferBatch(batch.items.map { if (it.state == TransferState.Waiting) it.copy(state = TransferState.Failed) else it })
                    }
                }
            }
        }
        return true
    }

    private suspend fun importOne(index: Int, file: Pending, session: Long) {
        coroutineContext.ensureActive()
        setItem(index) { it.copy(state = TransferState.Running) }
        val songId = try {
            if (file.keepAccess) {
                context.contentResolver.takePersistableUriPermission(file.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            importLocalAudio(context, database, file.uri, file.lyrics, file.cover).also { id ->
                database.insert(ImportSessionSong(sessionId = session, songId = id, addedToLibrary = true, liked = false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Couldn't import ${file.uri}")
            null
        }
        val song = songId?.let { database.getSongById(it) }
        setItem(index) { item ->
            if (song == null) {
                item.copy(state = TransferState.Failed)
            } else {
                item.copy(
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                    artworkUrl = song.song.thumbnailUrl ?: item.artworkUrl,
                    state = TransferState.Done,
                    progress = 1f,
                    songId = song.id,
                )
            }
        }
    }

    private fun setItem(index: Int, change: (TransferItem) -> TransferItem) {
        _batch.update { batch ->
            TransferBatch(batch.items.mapIndexed { i, item -> if (i == index) change(item) else item })
        }
    }

    private fun Pending.toItem() = TransferItem(
        key = uri.toString(),
        title = title.ifEmpty { "Song" },
        subtitle = subtitle,
        artworkUrl = artworkUrl,
        state = TransferState.Waiting,
    )

    // a file only has its file name until it is read; the list shows that rather than nothing
    private fun LocalFile.toPending(keepAccess: Boolean) = Pending(
        uri = uri,
        title = name?.let(LocalFileNames::withoutExtension).orEmpty(),
        subtitle = null,
        artworkUrl = null,
        keepAccess = keepAccess,
        lyrics = lyrics,
        cover = cover,
    )

    private companion object {
        // from this many picked songs on, adding their folder is suggested instead
        const val PickFolderHintAt = 100

        // single files android keeps read access to for good: 512 from android 11, 128 before
        val GrantLimit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 512 else 128
    }
}
