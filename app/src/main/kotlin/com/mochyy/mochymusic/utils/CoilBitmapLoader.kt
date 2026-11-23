// CoilBitmapLoader.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import timber.log.Timber

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap =
        createBitmap(64, 64)

    private fun Bitmap.copyIfNeeded(): Bitmap {
        return if (isRecycled) {
            createFallbackBitmap()
        } else {
            try {
                copy(Bitmap.Config.ARGB_8888, false) ?: createFallbackBitmap()
            } catch (e: Exception) {
                createFallbackBitmap()
            }
        }
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                // cap notification artwork to 512px instead of decoding multi-megapixel embedded covers whole
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= MaxArtworkPx && bounds.outHeight / (sample * 2) >= MaxArtworkPx) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                if (bitmap == null) createFallbackBitmap()
                else bitmap.copyIfNeeded().also { if (it !== bitmap) bitmap.recycle() }
            } catch (e: Exception) {
                Timber.tag("CoilBitmapLoader").w(e, "Failed to decode bitmap data")
                createFallbackBitmap()
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(MaxArtworkPx, MaxArtworkPx)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()

            val result = context.imageLoader.execute(request)

            when (result) {
                is ErrorResult -> {
                    createFallbackBitmap()
                }
                is SuccessResult -> {
                    try {
                        val bitmap = result.image.toBitmap()
                        bitmap.copyIfNeeded()
                    } catch (e: Exception) {
                        Timber.tag("CoilBitmapLoader").w(e, "Failed to convert image to bitmap")
                        createFallbackBitmap()
                    }
                }
            }
        }

    private companion object {
        const val MaxArtworkPx = 512
    }
}
