package com.mochyy.mochymusic.ui.player.video

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// reads a playing video's frame on its own thread: TextureView.getBitmap made the main thread wait on a gpu readback every few hundred ms
internal object VideoFrameReader {
    private val handler: Handler by lazy { Handler(HandlerThread("VideoFrames").apply { start() }.looper) }

    // copies the decoder's latest frame into the bitmap, scaled to fit; false with no frame yet or the surface gone
    suspend fun read(surface: Surface, into: Bitmap): Boolean = suspendCancellableCoroutine { done ->
        handler.post {
            val asked = runCatching {
                PixelCopy.request(surface, into, { result -> done.resume(result == PixelCopy.SUCCESS) }, handler)
            }.isSuccess
            if (!asked) done.resume(false)
        }
    }
}

// one video's sampler: straight from the decoder's surface, or the old main-thread way on a phone where that keeps failing
internal class VideoFrameSampler(size: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private var direct = true
    private var misses = 0

    suspend fun read(surface: Surface?, view: TextureView?): Boolean {
        if (direct) {
            if (surface == null) return false
            if (VideoFrameReader.read(surface, bitmap)) {
                misses = 0
                return true
            }
            if (++misses < GiveUpAfter) return false
            direct = false
        }
        return view != null && view.isAvailable && runCatching { view.getBitmap(bitmap) }.isSuccess
    }

    private companion object {
        const val GiveUpAfter = 4
    }
}
