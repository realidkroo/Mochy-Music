// LocalArtwork.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** covers taken out of local audio files, kept in the app's files folder because android and phone cleaners empty the cache folder */
object LocalArtwork {
    private const val FOLDER = "local_artwork"

    /** saves bytes as songId's cover and returns the file's uri, or null if it couldn't be written */
    fun save(context: Context, songId: String, bytes: ByteArray): String? = runCatching {
        val folder = File(context.filesDir, FOLDER).apply { mkdirs() }
        val file = File(folder, "${songId.replace(UnsafeInName, "_")}.jpg")
        // write-then-rename, so a crash mid-write never leaves a broken picture behind
        val temp = File(folder, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.writeBytes(bytes)
            temp.delete()
        }
        Uri.fromFile(file).toString()
    }.getOrNull()

    /** true for a cover file on this phone that is gone, or that sits where it could vanish */
    fun needsRepair(context: Context, thumbnailUrl: String?): Boolean {
        val uri = thumbnailUrl?.toUri() ?: return false
        if (uri.scheme != "file") return false
        val path = uri.path ?: return true
        return !File(path).exists() || path.startsWith(context.cacheDir.path)
    }

    /** fixes covers of local songs: moves ones still in the cache folder and re-reads the file for missing ones, includeMissing also retries songs that never had a cover, returns how many it fixed */
    suspend fun repair(context: Context, database: MusicDatabase, includeMissing: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            val songs = database.localSongsByRowIdAsc().first().map { it.song }
            var fixed = 0
            for (song in songs) {
                val current = song.thumbnailUrl
                val wanted = when {
                    needsRepair(context, current) -> true
                    includeMissing && current == null -> true
                    else -> false
                }
                if (!wanted) continue
                val newUrl = try {
                    moveOutOfCache(context, song.id, current) ?: readFromFile(context, song.id, song.localUri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't repair the cover of ${song.id}")
                    null
                }
                // a cover that's gone for good is cleared so the song shows the placeholder, and playing it tries the file again
                if (newUrl != current && (newUrl != null || current != null)) {
                    database.query { update(song.copy(thumbnailUrl = newUrl)) }
                    if (newUrl != null) fixed++
                }
            }
            fixed
        }

    /** a cover still in the cache folder, copied out before android clears it */
    private fun moveOutOfCache(context: Context, songId: String, thumbnailUrl: String?): String? {
        val path = thumbnailUrl?.toUri()?.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val saved = save(context, songId, file.readBytes()) ?: return null
        file.delete()
        return saved
    }

    private fun readFromFile(context: Context, songId: String, localUri: String?): String? {
        val uri = localUri?.toUri() ?: return null
        val bytes = extractAudioMetadata(context, uri).embeddedArtwork ?: return null
        return save(context, songId, bytes)
    }

    private val UnsafeInName = Regex("[^A-Za-z0-9_.-]")
}
