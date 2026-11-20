// LocalArtwork.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import androidx.core.net.toUri
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/** covers taken out of local audio files, kept in the app's files folder because android and phone cleaners empty the cache folder */
object LocalArtwork {
    private const val FOLDER = "local_artwork"
    // a cover past this on its long side is scaled down to it: still sharper than any phone shows it full screen, at a fraction of a 3000 px original's size
    private const val MaxCoverPx = 2000
    private const val CoverQuality = 92
    // left in the folder once the old per-song covers have been shared out and shrunk, so that only happens once
    private const val CompactedMarker = ".compacted-1"

    /** saves bytes as a cover and returns the file's uri, or null if it couldn't be written; songs with the same picture share one file */
    fun save(context: Context, bytes: ByteArray): String? = runCatching {
        val file = File(folder(context), "${hashOf(bytes)}.jpg")
        // an album's other songs usually brought this picture already
        if (!file.exists() || file.length() == 0L) writeAtomically(file, shrunk(bytes))
        Uri.fromFile(file).toString()
    }.getOrNull()

    /** the provider that hands covers to other apps (LocalArtworkProvider) */
    val Authority = "${BuildConfig.APPLICATION_ID}.localartwork"

    /** a cover link android auto, widgets and the lock screen can open: a file in the covers folder goes out through the cover provider, any other link as it is */
    fun shareableUri(thumbnailUrl: String?): Uri? {
        val uri = thumbnailUrl?.toUri() ?: return null
        if (uri.scheme != "file") return uri
        val file = File(uri.path ?: return uri)
        if (file.parentFile?.name != FOLDER) return uri
        return Uri.Builder().scheme("content").authority(Authority).appendPath(file.name).build()
    }

    /** the cover file a provider link names, or null for anything that isn't a plain file in the covers folder */
    fun fileFor(context: Context, name: String?): File? {
        if (name.isNullOrBlank() || '/' in name || name.startsWith(".")) return null
        val folder = folder(context)
        return File(folder, name).takeIf { it.parentFile == folder && it.isFile }
    }

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
                    moveOutOfCache(context, current) ?: readFromFile(context, song.localUri)
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

    /** deletes a cover once nothing shows it: songs share cover files, so it stays until its last song, album, playlist or artist has gone */
    suspend fun deleteIfUnused(context: Context, database: MusicDatabase, thumbnailUrl: String?): Unit = withContext(Dispatchers.IO) {
        val url = thumbnailUrl ?: return@withContext
        val file = ownFile(context, url) ?: return@withContext
        if (database.thumbnailUsers(url) == 0) file.delete()
    }

    /** once: the same picture saved for every song of an album becomes one file, and covers past MaxCoverPx shrink, without changing any song's link */
    suspend fun compact(context: Context): Unit = withContext(Dispatchers.IO) {
        val folder = folder(context)
        val marker = File(folder, CompactedMarker)
        if (marker.exists()) return@withContext
        val kept = HashMap<String, File>()
        val files = folder.listFiles { file -> file.isFile && !file.name.startsWith(".") && !file.name.endsWith(".tmp") }.orEmpty()
        for (file in files) {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            val hash = hashOf(bytes)
            val original = kept[hash]
            if (original == null) {
                kept[hash] = file
                val smaller = shrunk(bytes)
                if (smaller !== bytes) runCatching { writeAtomically(file, smaller) }
            } else {
                linkInPlace(original, file)
            }
        }
        runCatching { marker.createNewFile() }
    }

    /** a cover still in the cache folder, copied out before android clears it */
    private fun moveOutOfCache(context: Context, thumbnailUrl: String?): String? {
        val path = thumbnailUrl?.toUri()?.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val saved = save(context, file.readBytes()) ?: return null
        file.delete()
        return saved
    }

    private fun readFromFile(context: Context, localUri: String?): String? {
        val uri = localUri?.toUri() ?: return null
        val bytes = extractAudioMetadata(context, uri).embeddedArtwork ?: return null
        return save(context, bytes)
    }

    // the file behind a cover link when it's one of ours: in the covers folder, or the cache folder older versions used
    private fun ownFile(context: Context, thumbnailUrl: String): File? {
        val uri = thumbnailUrl.toUri()
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        return file.takeIf { it.startsWith(folder(context)) || it.startsWith(context.cacheDir) }
    }

    // the same picture under another song's name becomes a second name for one file, so every link to it keeps working; left as it is if that can't be done
    private fun linkInPlace(original: File, duplicate: File) {
        val temp = File(duplicate.parentFile, "${duplicate.name}.link.tmp")
        runCatching {
            temp.delete()
            Os.link(original.path, temp.path)
            if (!temp.renameTo(duplicate)) temp.delete()
        }.onFailure {
            temp.delete()
            Timber.w(it, "Couldn't share the cover ${duplicate.name}")
        }
    }

    // a picture past MaxCoverPx comes back as a smaller jpeg; a smaller, see-through or unreadable one comes back as it was
    private fun shrunk(bytes: ByteArray): ByteArray = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= MaxCoverPx) return@runCatching bytes
        var sample = 1
        while (longSide / (sample * 2) >= MaxCoverPx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching bytes
        try {
            // jpeg has no see-through parts, they'd turn black
            if (decoded.hasAlpha()) return@runCatching bytes
            val scale = MaxCoverPx.toFloat() / max(decoded.width, decoded.height)
            val sized = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            sized.compress(Bitmap.CompressFormat.JPEG, CoverQuality, out)
            if (sized !== decoded) sized.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() && it.size < bytes.size } ?: bytes
        } finally {
            decoded.recycle()
        }
    }.getOrDefault(bytes)

    private fun hashOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    // write-then-rename, so a crash mid-write never leaves a broken picture behind; the temp name is unique since an import and the player can save the same cover at once
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.writeBytes(bytes)
            temp.delete()
        }
    }

    private fun folder(context: Context) = File(context.filesDir, FOLDER).apply { mkdirs() }
}
