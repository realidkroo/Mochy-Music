// SongCoverFiles.kt

package com.mochyy.mochymusic.songedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// pictures people pick as a song's cover, copied into the app's own files because the photo picker's link stops working later
object SongCoverFiles {
    private const val Folder = "song_covers"
    private const val Size = 1200

    // crops the picture to a square, shrinks it and returns its file:// link, or null when it won't load
    suspend fun save(context: Context, songId: String, picked: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(picked)
                .size(Size, Size)
                .scale(Scale.FILL)
                .allowHardware(false)
                .build()
            val decoded = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
            val file = File(folder(context), "${safe(songId)}.${System.currentTimeMillis()}.webp")
            write(centerCropSquare(decoded), file)
            Uri.fromFile(file).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // drops the song's older pictures, keeping only the one its cover points at now
    fun deleteStale(context: Context, songId: String, keepUrl: String?) {
        val keep = keepUrl?.substringAfterLast('/')
        val prefix = "${safe(songId)}."
        folder(context).listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) && file.name != keep) file.delete()
        }
    }

    private fun folder(context: Context) = File(context.filesDir, Folder).apply { mkdirs() }

    // ids are file safe already, this just keeps a stray slash from making a folder
    private fun safe(songId: String) = songId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    @Suppress("DEPRECATION")
    private fun write(bitmap: Bitmap, file: File) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        file.outputStream().buffered().use { bitmap.compress(format, 92, it) }
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, Size, Size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }
}
