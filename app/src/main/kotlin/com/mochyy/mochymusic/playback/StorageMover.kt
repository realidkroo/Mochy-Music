// StorageMover.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import android.util.Log
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.transfer.TransferRestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// how a move is going, for the storage page and the place sheet
sealed interface MoveStep {
    data object Idle : MoveStep
    data class Working(val kind: StoredKind, val progress: Float) : MoveStep
    data class Failed(val kind: StoredKind, val message: String) : MoveStep
}

// moves downloads (copied, every song kept) or the song cache (started fresh) to another drive, then restarts so the player opens the new folder
@Singleton
class StorageMover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadUtil: DownloadUtil,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _step = MutableStateFlow<MoveStep>(MoveStep.Idle)
    val step: StateFlow<MoveStep> = _step.asStateFlow()

    // what moving [kind] there would need, so the sheet can say it won't fit before anyone taps
    fun bytesToMove(kind: StoredKind): Long =
        if (kind == StoredKind.Downloads) sizeOf(StoragePlaces.dir(context, kind)) else 0L

    fun move(kind: StoredKind, to: StoragePlace) {
        if (_step.value is MoveStep.Working) return
        _step.value = MoveStep.Working(kind, 0f)
        scope.launch {
            val from = StoragePlaces.dir(context, kind)
            runCatching {
                if (!to.isPhone) checkWritable(to.root)
                if (kind == StoredKind.Downloads) copyDownloads(from, to) else File(to.root, kind.folder).mkdirs()
                StoragePlaces.choose(context, kind, to, oldDir = from)
            }.onSuccess {
                _step.value = MoveStep.Working(kind, 1f)
                // a beat on the full bar, so the restart doesn't look like a crash
                delay(600)
                withContext(Dispatchers.Main) { TransferRestore.restartToFinish(context) }
            }.onFailure {
                Log.e("StorageMover", "Couldn't move ${kind.folder}", it)
                File(to.root, "${kind.folder}.moving").deleteRecursively()
                _step.value = MoveStep.Failed(kind, context.getString(R.string.storage_move_failed))
            }
        }
    }

    fun clearFailure() {
        if (_step.value is MoveStep.Failed) _step.value = MoveStep.Idle
    }

    // copied next to where it goes and renamed in one step, so a move cut short never leaves a half folder in use
    private suspend fun copyDownloads(from: File, to: StoragePlace) {
        val manager = downloadUtil.downloadManager
        val wasPaused = withContext(Dispatchers.Main) { manager.downloadsPaused.also { manager.pauseDownloads() } }
        if (!wasPaused) delay(400)
        var moved = false
        try {
            val staging = File(to.root, "${StoredKind.Downloads.folder}.moving")
            val target = File(to.root, StoredKind.Downloads.folder)
            staging.deleteRecursively()
            val files = from.walkTopDown().filter { it.isFile }.toList()
            val total = files.sumOf { it.length() }.coerceAtLeast(1L)
            if (total > to.free) throw IOException("not enough room")
            var copied = 0L
            var shown = 0f
            for (file in files) {
                val out = File(staging, file.relativeTo(from).path)
                out.parentFile?.mkdirs()
                file.inputStream().use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val progress = (copied.toFloat() / total).coerceAtMost(0.99f)
                            // a step every half percent is plenty for a bar, and spares the page thousands of updates
                            if (progress - shown >= 0.005f) {
                                shown = progress
                                _step.value = MoveStep.Working(StoredKind.Downloads, progress)
                            }
                        }
                    }
                }
                if (out.length() != file.length()) throw IOException("${file.name} came out a different size")
            }
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("couldn't put the copied folder in place")
            moved = true
        } finally {
            // after a move they stay paused until the restart, or they'd write into the folder that's about to go
            if (!moved && !wasPaused) withContext(Dispatchers.Main) { manager.resumeDownloads() }
        }
    }

    // writes and removes a small file first, so a folder android won't let mochy write in fails here instead of after the restart
    private fun checkWritable(root: File) {
        if (!root.exists() && !root.mkdirs()) throw IOException("couldn't make ${root.path}")
        val probe = File(root, ".mochy-write-check")
        probe.writeText("")
        if (!probe.delete()) throw IOException("couldn't tidy up in ${root.path}")
    }

    private fun sizeOf(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}
