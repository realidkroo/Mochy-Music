// pure-kotlin 35 hz runner driving a signed code module's SurfaceBridge into two reused bitmaps with strict bounds and secret redaction

package com.mochyy.mochymusic.ui.player.styles

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mochyy.mochymusic.module.bridge.shared.SurfaceBridge
import com.mochyy.mochymusic.modules.security.SecretGuard
import timber.log.Timber
import java.io.File

/** standard arcade and fps key codes forwarded to a code module's SurfaceBridge */
internal object SurfaceKey {
    const val RIGHT = 0xae
    const val LEFT = 0xac
    const val UP = 0xad
    const val DOWN = 0xaf
    const val STRAFE_L = 0xa0
    const val STRAFE_R = 0xa1
    const val USE = 0xa2
    const val ACTION = 0xa3
    const val ESCAPE = 27
    const val ENTER = 13
    const val TAB = 9
    const val RUN = 0x80 + 0x36
    const val YES = 'y'.code
    const val NO = 'n'.code
}

internal sealed interface SurfaceState {
    data object Idle : SurfaceState
    data object Loading : SurfaceState
    data object Running : SurfaceState
    data object OtherAssetLoaded : SurfaceState
    data object EngineMissing : SurfaceState
    data class Failed(val message: String) : SurfaceState
}

/** owns the background thread that ticks a code module's SurfaceBridge and swaps double-buffered bitmaps without recomposing */
internal object SurfaceSession {
    private val lock = Object()
    private val heldKeys = mutableSetOf<Int>()

    private var bridge: SurfaceBridge? = null
    private var thread: Thread? = null
    private var requestedAsset: String? = null
    private var requestedDataDir: String? = null
    private var requestedExtras: List<String> = emptyList()
    private var paused = true
    private var stopped = false
    private var attachedCount = 0

    // two reusable bitmaps and one reusable pixel buffer: zero per-frame allocations
    private var buffers: Array<Bitmap>? = null
    private var images: Array<ImageBitmap>? = null
    private var pixelScratch: IntArray? = null
    private var frameW = 320
    private var frameH = 200
    @Volatile
    private var frontIndex = 0

    var state: SurfaceState by mutableStateOf(SurfaceState.Idle)
        private set

    /** bumped on every new frame; read only inside draw to avoid recomposing the player */
    var frameTick: Long by mutableLongStateOf(0L)
        private set

    fun currentFrame(): ImageBitmap? = images?.get(frontIndex)

    fun attach(surface: SurfaceBridge?, assetFile: File?, dataDir: File, extras: List<File>) {
        if (surface == null) {
            state = SurfaceState.EngineMissing
            return
        }
        synchronized(lock) {
            attachedCount++
            val assetPath = assetFile?.absolutePath.orEmpty()
            if (thread != null && !stopped) {
                if (requestedAsset != assetPath || bridge !== surface) {
                    state = SurfaceState.OtherAssetLoaded
                }
                lock.notifyAll()
                return
            }
            dataDir.mkdirs()
            bridge = surface
            requestedAsset = assetPath
            requestedDataDir = dataDir.absolutePath
            requestedExtras = extras.map { it.absolutePath }
            stopped = false
            state = SurfaceState.Loading
            val worker = Thread({ runLoop(surface) }, "MochyModuleSurface").apply {
                isDaemon = true
                start()
            }
            thread = worker
            lock.notifyAll()
        }
    }

    fun detach() {
        synchronized(lock) {
            attachedCount = (attachedCount - 1).coerceAtLeast(0)
            if (attachedCount == 0) {
                setPausedLocked(true)
            }
            lock.notifyAll()
        }
    }

    /** tears down the session when its code module is removed or replaced so its classloader can be collected */
    fun release(surface: SurfaceBridge?) {
        if (surface == null) return
        synchronized(lock) {
            if (bridge !== surface) return
            stopped = true
            attachedCount = 0
            setPausedLocked(true)
            bridge = null
            thread = null
            requestedAsset = null
            requestedDataDir = null
            requestedExtras = emptyList()
            buffers = null
            images = null
            pixelScratch = null
            state = SurfaceState.Idle
            lock.notifyAll()
        }
    }

    fun setPaused(shouldPause: Boolean) {
        synchronized(lock) {
            setPausedLocked(shouldPause || attachedCount == 0 || stopped)
            lock.notifyAll()
        }
    }

    fun setKey(keyCode: Int, pressed: Boolean) {
        val safeCode = keyCode and 0xFF
        val surface = synchronized(lock) {
            if (pressed) heldKeys += safeCode else heldKeys -= safeCode
            bridge
        } ?: return
        runCatching { surface.surfaceKey(safeCode, pressed) }
    }

    fun addTurn(delta: Int) {
        val clamped = delta.coerceIn(-MaxTurnDelta, MaxTurnDelta)
        if (clamped == 0) return
        val surface = synchronized(lock) { bridge } ?: return
        runCatching { surface.surfaceTurn(clamped) }
    }

    private fun setPausedLocked(next: Boolean) {
        if (paused == next) return
        paused = next
        val surface = bridge ?: return
        runCatching {
            surface.surfaceSetPaused(next)
            // release held keys when the player hides so inputs never get stuck
            if (next && heldKeys.isNotEmpty()) {
                heldKeys.forEach { surface.surfaceKey(it, false) }
                heldKeys.clear()
            }
        }
    }

    private fun ensureBuffers(surface: SurfaceBridge): Array<Bitmap> {
        buffers?.let { return it }
        val dims = runCatching { surface.surfaceSize() }.getOrNull()
        val (w, h) = clampDimensions(dims?.getOrNull(0), dims?.getOrNull(1))
        frameW = w
        frameH = h
        pixelScratch = IntArray(frameW * frameH)
        val created = Array(2) { Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888) }
        buffers = created
        images = Array(2) { created[it].asImageBitmap() }
        return created
    }

    private fun runLoop(surface: SurfaceBridge) {
        val asset = requestedAsset.orEmpty()
        val dir = requestedDataDir ?: return
        val extras = requestedExtras.takeIf { it.isNotEmpty() }?.toTypedArray()
        val initCode = runCatching { surface.surfaceStart(asset, dir, extras) }.getOrElse {
            val clean = sanitizeError(it.message ?: it.javaClass.simpleName)
            Timber.tag(Tag).w(it, "Surface engine failed to start: %s", clean)
            state = SurfaceState.Failed(clean)
            return
        }
        when (initCode) {
            0 -> state = SurfaceState.Running
            1 -> {
                state = SurfaceState.OtherAssetLoaded
                return
            }
            else -> {
                val msg = sanitizeError(runCatching { surface.surfaceLastError() }.getOrNull())
                Timber.tag(Tag).w("Surface failed to boot: %s", msg)
                state = SurfaceState.Failed(msg)
                return
            }
        }
        val bitmaps = ensureBuffers(surface)
        val pixels = pixelScratch ?: return
        var lastSeq = 0L
        while (true) {
            synchronized(lock) {
                while (!stopped && (paused || attachedCount <= 0)) {
                    runCatching { lock.wait() }
                }
                if (stopped || bridge !== surface) return
            }
            val startNs = System.nanoTime()
            val ok = runCatching { surface.surfaceTick() }.getOrDefault(false)
            if (!ok) {
                if (stopped) return
                val msg = sanitizeError(runCatching { surface.surfaceLastError() }.getOrNull())
                state = SurfaceState.Failed(msg)
                return
            }
            val nextSeq = runCatching { surface.surfaceCopyFrame(pixels, lastSeq) }.getOrDefault(lastSeq)
            if (nextSeq != lastSeq && !stopped) {
                val back = 1 - frontIndex
                bitmaps[back].setPixels(pixels, 0, frameW, 0, 0, frameW, frameH)
                lastSeq = nextSeq
                frontIndex = back
                frameTick = nextSeq
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            val waitMs = (16L - elapsedMs).coerceAtLeast(1L)
            runCatching { Thread.sleep(waitMs) }
        }
    }

    /** clamps reported framebuffer size so a buggy or hostile module can never allocate gigabytes */
    internal fun clampDimensions(width: Int?, height: Int?): Pair<Int, Int> {
        val w = (width ?: 320).coerceIn(MinWidth, MaxWidth)
        val h = (height ?: 200).coerceIn(MinHeight, MaxHeight)
        return w to h
    }

    /** strips secrets and control characters from module error text before showing or logging it */
    internal fun sanitizeError(raw: String?): String {
        val redacted = SecretGuard.redact(raw.orEmpty())
        val cleaned = redacted.replace(ControlChars, " ").trim().take(MaxErrorChars)
        return cleaned.ifBlank { "engine error" }
    }

    private const val Tag = "ModuleSurface"
    private const val MinWidth = 64
    private const val MaxWidth = 640
    private const val MinHeight = 64
    private const val MaxHeight = 480
    private const val MaxTurnDelta = 1024
    private const val MaxErrorChars = 200
    private val ControlChars = Regex("[\\p{Cntrl}]+")
}
