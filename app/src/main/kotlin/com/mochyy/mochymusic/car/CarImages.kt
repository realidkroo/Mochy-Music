// CarImages.kt

package com.mochyy.mochymusic.car

import android.content.Context
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.RoundedCornersTransformation
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// covers for the car screen; the car can't fetch our links itself, so each one goes over as a small rounded bitmap, kept for the session so a list comes back instantly
class CarImages(private val context: Context) {
    private val covers = LruCache<String, CarIcon>(CachedCovers)
    private val loads = Semaphore(ParallelLoads)

    // a cover, or the plain note when there's none or it won't load
    suspend fun cover(url: String?): CarIcon {
        if (url.isNullOrBlank()) return placeholder
        covers.get(url)?.let { return it }
        val icon = loads.withPermit { withContext(Dispatchers.IO) { load(url) } } ?: return placeholder
        covers.put(url, icon)
        return icon
    }

    // every cover of a list at once, a few at a time, in the list's order
    suspend fun covers(urls: List<String?>): List<CarIcon> = coroutineScope {
        urls.map { async { cover(it) } }.awaitAll()
    }

    fun icon(@DrawableRes res: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(context, res)).build()

    val placeholder: CarIcon by lazy { icon(R.drawable.music_note) }

    private suspend fun load(url: String): CarIcon? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url.resize(CoverPx, CoverPx))
            .size(Size(CoverPx, CoverPx))
            // the car draws a tile's picture exactly as sent, so the corners are rounded here; a wide video thumbnail is cut square like the rest
            .transformations(RoundedCornersTransformation(CornerPx))
            .allowHardware(false)
            .build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }.getOrNull()

    private companion object {
        // sharp on the car's large tiles; fewer are kept than before so the bigger covers take no more memory
        const val CoverPx = 320
        const val CornerPx = 36f
        const val CachedCovers = 80
        const val ParallelLoads = 6
    }
}
