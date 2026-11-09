// UploadedPictures.kt

package com.mochyy.mochymusic.discord

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// pictures from the phone (a local song's cover, one the user picked) put on a free host for discord, each made once and reused until its host lets it go
internal class UploadedPictures(
    private val context: Context,
    private val scope: CoroutineScope,
    // a picture is up: send the status again so it shows
    private val onReady: () -> Unit,
) {
    private val made = ConcurrentHashMap<String, PictureHosts.Hosted>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val working = ConcurrentHashMap.newKeySet<String>()

    // the picture's web address once it's up; null meanwhile, while it's made in the background
    fun urlFor(source: String): String? {
        val now = System.currentTimeMillis()
        made[source]?.let { if (now < it.usableUntilMs) return it.url }
        if (failedAt[source]?.let { now - it < RetryAfterFailureMs } == true) return null
        if (working.add(source)) {
            scope.launch {
                try {
                    val picture = loadLocalPicture(context, source, Size) ?: error("couldn't read $source")
                    val (bytes, name, mime) = encode(picture)
                    made[source] = PictureHosts.upload(bytes, name, mime)
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(LogTag).w(e, "couldn't put a picture up for discord")
                    failedAt[source] = System.currentTimeMillis()
                } finally {
                    working.remove(source)
                }
            }
        }
        return null
    }

    // a photo goes as a jpeg, a picture with see-through parts (a logo) keeps them as a png
    private suspend fun encode(picture: Bitmap): Triple<ByteArray, String, String> = withContext(Dispatchers.Default) {
        val out = ByteArrayOutputStream()
        if (picture.hasAlpha()) {
            picture.compress(Bitmap.CompressFormat.PNG, 100, out)
            Triple(out.toByteArray(), "mochy.png", "image/png")
        } else {
            picture.compress(Bitmap.CompressFormat.JPEG, 90, out)
            Triple(out.toByteArray(), "mochy.jpg", "image/jpeg")
        }
    }

    companion object {
        private const val LogTag = "UploadedPictures"
        // discord shows the big picture at up to 240px, so this stays sharp on dense screens
        private const val Size = 512
        private const val RetryAfterFailureMs = 10 * 60_000L
    }
}

// a picture on the phone (a file, a content uri, a path) as a square software bitmap, filled and cropped to [size]
internal suspend fun loadLocalPicture(context: Context, source: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
    val data: Any = if (source.startsWith("/")) File(source) else Uri.parse(source)
    val request = ImageRequest.Builder(context)
        .data(data)
        .size(size, size)
        .scale(Scale.FILL)
        .allowHardware(false)
        .build()
    val image = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
    if (image.width == image.height) return@withContext image
    val side = minOf(image.width, image.height)
    Bitmap.createBitmap(image, (image.width - side) / 2, (image.height - side) / 2, side, side)
}

// keeps a picked picture in mochy's own folder (the picker's link stops working later) and gives back its file uri, or null if it can't be read
internal suspend fun savePresencePicture(context: Context, picked: Uri, slot: String): String? {
    val picture = loadLocalPicture(context, picked.toString(), 512) ?: return null
    return withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "discord").apply { mkdirs() }
        val png = picture.hasAlpha()
        val file = File(folder, "custom_${slot}_${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
        runCatching {
            file.outputStream().buffered().use { out ->
                picture.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 92, out)
            }
        }.getOrElse { return@withContext null }
        // the one it replaces is no use any more
        folder.listFiles()?.forEach { if (it.name.startsWith("custom_${slot}_") && it != file) it.delete() }
        Uri.fromFile(file).toString()
    }
}
