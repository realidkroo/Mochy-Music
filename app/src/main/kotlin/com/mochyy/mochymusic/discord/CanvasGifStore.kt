// CanvasGifStore.kt

package com.mochyy.mochymusic.discord

import android.content.Context
import java.io.File
import java.security.MessageDigest

private fun hashName(key: String, ext: String): String =
    MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32) + ext

// the canvas gifs we made, kept in the cache folder so they count in the storage page and android can clean them
internal object CanvasGifStore {
    class Stats(val bytes: Long, val count: Int)

    private const val Folder = "canvas_gifs"
    // oldest ones go first when the folder gets bigger than this
    private const val MaxBytes = 100L * 1024 * 1024

    private fun folder(context: Context) = File(context.cacheDir, Folder).apply { mkdirs() }

    fun read(context: Context, key: String): ByteArray? {
        val file = File(folder(context), hashName(key, ".gif")).takeIf { it.isFile } ?: return null
        // touching it keeps the songs you play a lot from being trimmed
        file.setLastModified(System.currentTimeMillis())
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun write(context: Context, key: String, bytes: ByteArray) {
        runCatching { File(folder(context), hashName(key, ".gif")).writeBytes(bytes) }
        trim(context)
    }

    fun stats(context: Context): Stats {
        val files = folder(context).listFiles { f -> f.isFile && f.name.endsWith(".gif") }.orEmpty()
        return Stats(files.sumOf { it.length() }, files.size)
    }

    // clears the videos too, they only exist to make the gifs
    fun clear(context: Context) {
        folder(context).listFiles()?.forEach { it.delete() }
        CanvasVideoCache.clear(context)
    }

    private fun trim(context: Context) {
        val files = folder(context).listFiles().orEmpty().sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (total > MaxBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }
}

// the videos the gifs are cut from. kept so a retry, a lost upload or a restart doesnt download or convert them again
internal object CanvasVideoCache {
    private const val Folder = "canvas_videos"
    private const val MaxBytes = 80L * 1024 * 1024

    private fun folder(context: Context) = File(context.cacheDir, Folder).apply { mkdirs() }

    fun fileFor(context: Context, url: String): File = File(folder(context), hashName(url, ".mp4"))

    fun clear(context: Context) {
        folder(context).listFiles()?.forEach { it.delete() }
    }

    // .part files are downloads in progress, those stay out of it
    fun trim(context: Context) {
        val files = folder(context).listFiles { f -> f.isFile && f.name.endsWith(".mp4") }.orEmpty()
            .sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (total > MaxBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }
}
