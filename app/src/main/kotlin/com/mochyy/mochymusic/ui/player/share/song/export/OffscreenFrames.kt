// OffscreenFrames.kt

package com.mochyy.mochymusic.ui.player.share.song.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlin.coroutines.resume

private const val Tag = "SongShare"

// how many turns the main thread gets for the composition to ask for its next frame
private const val SettleYields = 6

// how finely a frame is checked for anything drawn at all: about every 10 picture units
private const val CheckColumns = 36
private const val CheckRows = 64

// a drawn frame: premultiplied, top row first; the bitmap can be wider than the picture when the gpu pads its rows
internal class FrameImage(val bitmap: Bitmap, val width: Int, val height: Int)

// draws a composable off screen, one frame at a time, on a clock moved by hand: every animation in it lands exactly on the frame's time, however long the frame took to draw
internal class OffscreenFrames(
    private val host: ViewGroup,
    val width: Int,
    val height: Int,
    content: @Composable () -> Unit,
) {
    private val clock = BroadcastFrameClock()
    private val main = Handler(Looper.getMainLooper())
    val scope = CoroutineScope(Dispatchers.Main.immediate + clock + SupervisorJob())
    private val recomposer = Recomposer(scope.coroutineContext)
    private var frameNanos = 1_000_000_000L

    // the gpu draws blur and shaders like the screen does; some phones hand back an empty frame from it, and those draw in software instead
    private var onGpu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private var gpuFrame: Bitmap? = null

    // two buffers taking turns, so a software frame that fails halfway never spoils the last good one
    private val softwareFrames = arrayOfNulls<Bitmap>(2)
    private var softwareTurn = 0

    // stands in for a frame that couldn't be drawn, so the card never drops out of a video
    var lastFrame: FrameImage? = null
        private set

    // how the frames went, for the log once an export is done
    private var gpuFrames = 0
    private var softwareFrameCount = 0
    private var stoodIn = 0
    private var softwareFailed = false

    private val view = ComposeView(host.context).apply {
        setParentCompositionContext(recomposer)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        host.addView(view, ViewGroup.LayoutParams(width, height))
        layOut()
    }

    // moves the clock on by frameMs and lets the composition, its animations and its effects all catch up
    suspend fun advance(frameMs: Double) {
        frameNanos += (frameMs * 1_000_000.0).toLong()
        Snapshot.sendApplyNotifications()
        var turns = 0
        while (!clock.hasAwaiters && turns < SettleYields) {
            nextTurn()
            turns++
        }
        if (clock.hasAwaiters) clock.sendFrame(frameNanos)
        Snapshot.sendApplyNotifications()
        nextTurn()
    }

    // a few still frames for the picture to settle into place; every one is checked, so a phone whose gpu hands back empty frames is caught here
    suspend fun warmUp(frames: Int) {
        repeat(frames) {
            advance(0.0)
            capture()
        }
    }

    // the composable as it looks right now; a frame that comes out empty or broken is drawn again in software, and if even that fails the last good one stands in
    suspend fun capture(): FrameImage {
        layOut()
        if (onGpu && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            captureOnGpu()?.let { return it }
            onGpu = false
        }
        captureInSoftware()?.let { return it }
        stoodIn++
        return checkNotNull(lastFrame) { "The share's picture came out empty" }
    }

    // one line for the log, so a phone where the card goes missing shows which way its frames were drawn
    fun summary(): String = "gpu frames $gpuFrames, software frames $softwareFrameCount, stood in for $stoodIn, now on ${if (onGpu) "gpu" else "software"}"

    fun release() {
        gpuFrame?.recycle()
        gpuFrame = null
        softwareFrames.forEach { it?.recycle() }
        softwareFrames.fill(null)
        lastFrame = null
        view.disposeComposition()
        host.removeView(view)
        recomposer.cancel()
        scope.cancel()
    }

    private fun layOut() {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    // one trip round the main thread's queue, so anything the frame posted there has run before the picture is taken
    private suspend fun nextTurn() {
        yield()
        suspendCancellableCoroutine { continuation -> main.post { continuation.resume(Unit) } }
    }

    // the same way compose takes a picture of a layer: the view drawn into a picture that the gpu turns into a bitmap, then copied out for the encoder; null when that fails or comes back empty
    @RequiresApi(Build.VERSION_CODES.P)
    private fun captureOnGpu(): FrameImage? {
        val copy = runCatching {
            val drawn = Bitmap.createBitmap(ViewPicture(view, width, height))
            try {
                checkNotNull(drawn.copy(Bitmap.Config.ARGB_8888, false)) { "The gpu frame couldn't be read back" }
            } finally {
                drawn.recycle()
            }
        }.onFailure { Log.w(Tag, "Drawing the share on the gpu failed, using software", it) }.getOrNull() ?: return null
        val frame = FrameImage(copy, width, height)
        if (frame.isBlank()) {
            Log.w(Tag, "The gpu frame came back empty after $gpuFrames good ones, drawing the share in software instead")
            copy.recycle()
            return null
        }
        // the frame before has been handed to the encoder by now; its memory goes straight back
        gpuFrame?.recycle()
        gpuFrame = copy
        gpuFrames++
        lastFrame = frame
        return frame
    }

    private fun captureInSoftware(): FrameImage? {
        val bitmap = softwareFrames[softwareTurn]
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { softwareFrames[softwareTurn] = it }
        val frame = runCatching {
            bitmap.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(bitmap))
            FrameImage(bitmap, width, height)
        }.onFailure {
            // logged once; a draw that fails keeps failing, and 600 copies of it would bury everything else
            if (!softwareFailed) Log.w(Tag, "Drawing the share in software failed", it)
            softwareFailed = true
        }.getOrNull()
        if (frame == null || frame.isBlank()) return null
        softwareTurn = 1 - softwareTurn
        softwareFrameCount++
        lastFrame = frame
        return frame
    }
}

// a picture that draws the view each time it's drawn, so the gpu renders it fresh with blur and shaders included
@RequiresApi(Build.VERSION_CODES.P)
private class ViewPicture(private val view: View, private val pictureWidth: Int, private val pictureHeight: Int) : Picture() {
    override fun beginRecording(width: Int, height: Int): Canvas = Canvas()

    override fun endRecording() = Unit

    override fun getWidth(): Int = pictureWidth

    override fun getHeight(): Int = pictureHeight

    override fun requiresHardwareAcceleration(): Boolean = true

    override fun draw(canvas: Canvas) {
        view.draw(canvas)
    }
}

// nothing drawn anywhere on a fine grid across the frame: the card, the words or the background always land on some of it
private fun FrameImage.isBlank(): Boolean {
    for (row in 0 until CheckRows) {
        val y = (row * height + height / 2) / CheckRows
        for (column in 0 until CheckColumns) {
            val x = (column * width + width / 2) / CheckColumns
            if (Color.alpha(bitmap.getPixel(x, y)) != 0) return false
        }
    }
    return true
}
