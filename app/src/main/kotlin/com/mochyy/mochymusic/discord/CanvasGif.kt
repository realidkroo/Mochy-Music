// CanvasGif.kt

package com.mochyy.mochymusic.discord

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applecanvas.AppleMusicCanvasProvider
import com.mochyy.mochymusic.canvas.SpotifyCanvasProvider
import com.mochyy.mochymusic.constants.SpotifyCanvasKey
import com.mochyy.mochymusic.discord.CanvasGifProgress.Stage
import com.mochyy.mochymusic.ui.player.normalizeCanvasArtistName
import com.mochyy.mochymusic.ui.player.normalizeCanvasSongTitle
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.exp
import kotlin.math.roundToInt

// how far the gif being made is, for the bar on the discord page; each stage owns a rough slice, so the percent is a guess
internal object CanvasGifProgress {
    enum class Stage(@StringRes val label: Int, val from: Float, val to: Float) {
        FIND(R.string.discord_canvas_stage_find, 0f, 0.05f),
        DOWNLOAD(R.string.discord_canvas_stage_download, 0.05f, 0.25f),
        FRAMES(R.string.discord_canvas_stage_frames, 0.25f, 0.75f),
        ENCODE(R.string.discord_canvas_stage_encode, 0.75f, 0.90f),
        UPLOAD(R.string.discord_canvas_stage_upload, 0.90f, 0.99f),
    }

    class Snapshot(
        val title: String,
        val source: String,
        val stage: Stage,
        val fraction: Float,
        val etaSec: Int?,
        val finished: Boolean = false,
        val failed: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()
    private var startedAt = 0L

    @Synchronized
    fun begin(title: String, source: String) {
        startedAt = SystemClock.elapsedRealtime()
        _state.value = Snapshot(title, source, Stage.FIND, 0f, null)
    }

    // within is how far that stage is, 0 to 1. the bar never goes back even if two workers report out of order
    @Synchronized
    fun update(stage: Stage, within: Float) {
        val old = _state.value ?: return
        if (old.finished || old.failed) return
        val f = stage.from + (stage.to - stage.from) * within.coerceIn(0f, 1f)
        val frac = maxOf(old.fraction, f)
        val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
        // too early the guess is all over the place, so we wait a bit before showing a time
        val eta = if (frac >= 0.1f && elapsed >= 4f) (elapsed * (1f - frac) / frac).roundToInt() else null
        _state.value = Snapshot(old.title, old.source, stage, frac, eta)
    }

    @Synchronized
    fun done() {
        val old = _state.value ?: return
        _state.value = Snapshot(old.title, old.source, old.stage, 1f, 0, finished = true)
        hideLater(startedAt, 8_000)
    }

    @Synchronized
    fun failed() {
        val old = _state.value ?: return
        _state.value = Snapshot(old.title, old.source, old.stage, old.fraction, null, failed = true)
        hideLater(startedAt, 15_000)
    }

    @Synchronized
    fun clear() {
        _state.value = null
    }

    // only hides its own run, a newer song may already be using the bar
    private fun hideLater(token: Long, ms: Long) {
        scope.launch {
            delay(ms)
            synchronized(this@CanvasGifProgress) { if (startedAt == token) _state.value = null }
        }
    }
}

// one worker for the whole app that a skip never stops; the newest song goes first and the oldest waiting ones drop off
private object CanvasQueue {
    class Task(val work: suspend () -> Unit, val onDrop: () -> Unit)

    private const val MaxWaiting = 3
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val waiting = java.util.ArrayDeque<Task>()
    private var busy = false

    fun add(task: Task) {
        var dropped: Task? = null
        var start = false
        synchronized(lock) {
            waiting.addLast(task)
            if (waiting.size > MaxWaiting) dropped = waiting.pollFirst()
            if (!busy) {
                busy = true
                start = true
            }
        }
        dropped?.onDrop?.invoke()
        if (start) {
            scope.launch {
                while (true) {
                    val next = synchronized(lock) {
                        val t = waiting.pollLast()
                        if (t == null) busy = false
                        t
                    } ?: break
                    // one bad song must not kill the worker
                    runCatching { next.work() }
                }
            }
        }
    }
}

// discord can't play video, so the canvas gets cut into a looping gif once per song, and the still artwork shows until it's ready
internal class CanvasGif(
    appContext: Context,
    private val onReady: () -> Unit,
) {
    private val context = appContext.applicationContext
    private val made = ConcurrentHashMap<String, PictureHosts.Hosted>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val working = ConcurrentHashMap.newKeySet<String>()

    fun urlFor(title: String, artist: String, durationSec: Int?, source: PresencePicture.CanvasSource, fps: Int, size: Int): String? {
        val key = "$title|$artist|$source|$fps|$size"
        val now = System.currentTimeMillis()
        made[key]?.let { if (now < it.usableUntilMs) return it.url }
        if (failedAt[key]?.let { now - it < RetryAfterFailureMs } == true) return null
        if (working.add(key)) {
            CanvasQueue.add(
                CanvasQueue.Task(
                    work = { make(key, title, artist, durationSec, source, fps, size) },
                    onDrop = { working.remove(key) },
                ),
            )
        }
        return null
    }

    private suspend fun make(key: String, title: String, artist: String, durationSec: Int?, source: PresencePicture.CanvasSource, fps: Int, size: Int) {
        CanvasGifProgress.begin(title, sourceName(source))
        try {
            Timber.tag(LogTag).i("making a gif for $title - $artist ($source, $fps fps, $size px)")
            // the gif is kept on the phone, after a restart or when the host deleted it we only upload it again
            val storeKey = "$key|$MaxFrames"
            val gif = withContext(Dispatchers.IO) { CanvasGifStore.read(context, storeKey) } ?: run {
                CanvasGifProgress.update(Stage.FIND, 0f)
                val video = CanvasVideo.find(context, title, artist, durationSec, source)
                    ?: error("no canvas or animated cover for this song")
                CanvasGifProgress.update(Stage.FIND, 1f)
                render(video, fps, size).also { withContext(Dispatchers.IO) { CanvasGifStore.write(context, storeKey, it) } }
            }
            made[key] = upload(gif)
            CanvasGifProgress.done()
            onReady()
        } catch (e: CancellationException) {
            CanvasGifProgress.clear()
            throw e
        } catch (e: Exception) {
            Timber.tag(LogTag).w(e, "couldn't make the canvas gif")
            failedAt[key] = System.currentTimeMillis()
            CanvasGifProgress.failed()
        } finally {
            working.remove(key)
        }
    }

    private fun sourceName(s: PresencePicture.CanvasSource) = when (s) {
        PresencePicture.CanvasSource.SPOTIFY -> "Spotify Canvas"
        PresencePicture.CanvasSource.APPLE -> "Apple Music Animated Cover"
        PresencePicture.CanvasSource.EITHER -> "Spotify Canvas or Apple Music Animated Cover"
    }

    // the host doesnt tell us how far the upload is, so the bar just creeps up while we wait
    private suspend fun upload(gif: ByteArray): PictureHosts.Hosted = coroutineScope {
        val t0 = SystemClock.elapsedRealtime()
        CanvasGifProgress.update(Stage.UPLOAD, 0f)
        val ticker = launch {
            while (true) {
                delay(300)
                val t = (SystemClock.elapsedRealtime() - t0) / 1000f
                CanvasGifProgress.update(Stage.UPLOAD, 1f - exp(-t / 6f))
            }
        }
        try {
            withContext(Dispatchers.IO) { PictureHosts.upload(gif, "canvas.gif", "image/gif") }
        } finally {
            ticker.cancel()
        }
    }

    // the master playlist lists the same video in many sizes, the smallest one that is still 480 wide is plenty for a small picture
    private fun pickVariant(masterUrl: String): String {
        val text = runCatching {
            DiscordHttp.client.newCall(Request.Builder().url(masterUrl).build()).execute().use { r -> r.body?.string().orEmpty() }
        }.getOrDefault("")
        // already a playlist of one size
        if (!text.contains("#EXT-X-STREAM-INF")) return masterUrl
        val lines = text.lines()
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        for ((i, line) in lines.withIndex()) {
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            val width = Regex("RESOLUTION=(\\d+)x\\d+").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // h264 plays on every phone, hevc not always
            val score = (if (width >= 480) 100_000 - width else width) + (if (line.contains("avc1")) 1_000_000 else 0)
            if (score > bestScore) {
                bestScore = score
                best = uri
            }
        }
        val chosen = best ?: return masterUrl
        return masterUrl.toHttpUrl().resolve(chosen)?.toString() ?: chosen
    }

    // media3's transformer reads the stream and writes it out as one mp4 file, without the audio
    private suspend fun hlsToMp4(playlistUrl: String, out: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            val transformer = Transformer.Builder(context).addListener(listener).build()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(playlistUrl)).setRemoveAudio(true).build()
            transformer.start(item, out.absolutePath)
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
        }
    }

    // plain mp4 (spotify), written to a .part file first so a half download never looks finished
    private fun download(url: String, out: File) {
        DiscordHttp.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "canvas download failed: HTTP ${r.code}" }
            val body = r.body!!
            val total = body.contentLength()
            var got = 0L
            body.byteStream().use { input ->
                out.outputStream().use { sink ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        got += n
                        if (total > 0) CanvasGifProgress.update(Stage.DOWNLOAD, got.toFloat() / total)
                    }
                }
            }
        }
    }

    // video is fetched once and kept on disk, a retry, a replay or a restart skips this whole part
    private suspend fun fetchVideo(videoUrl: String): File {
        val keep = CanvasVideoCache.fileFor(context, videoUrl)
        if (keep.isFile && keep.length() > 0) {
            keep.setLastModified(System.currentTimeMillis())
            CanvasGifProgress.update(Stage.DOWNLOAD, 1f)
            return keep
        }
        val part = File(keep.path + ".part")
        try {
            part.delete()
            if (videoUrl.contains(".m3u8", ignoreCase = true)) {
                // apple's animated covers are hls streams, media3 turns one into a plain mp4 we can read frames from
                val playlist = pickVariant(videoUrl)
                coroutineScope {
                    // transformer gives no progress for a stream, so this one creeps up by time too
                    val t0 = SystemClock.elapsedRealtime()
                    val ticker = launch {
                        while (true) {
                            delay(400)
                            val t = (SystemClock.elapsedRealtime() - t0) / 1000f
                            CanvasGifProgress.update(Stage.DOWNLOAD, 1f - exp(-t / 15f))
                        }
                    }
                    try {
                        withTimeout(HlsTimeoutMs) { hlsToMp4(playlist, part) }
                    } finally {
                        ticker.cancel()
                    }
                }
            } else {
                download(videoUrl, part)
            }
            check(part.length() > 0) { "empty video" }
            keep.delete()
            check(part.renameTo(keep)) { "couldn't keep the video" }
        } finally {
            part.delete()
        }
        CanvasVideoCache.trim(context)
        return keep
    }

    private fun frameAt(r: MediaMetadataRetriever, atUs: Long, dw: Int, dh: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST, dw, dh)
        } else {
            r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }

    private suspend fun render(videoUrl: String, maxFps: Int, size: Int): ByteArray = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val file = fetchVideo(videoUrl)
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("canvas has no length")
            // use every frame the video has, only a video faster than the picked fps gets thinned out
            val sourceFrames = if (Build.VERSION.SDK_INT >= 28) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()?.takeIf { it > 0 }
            } else null
            val sourceFps = sourceFrames?.let { it * 1000f / ms } ?: 30f
            val count = (ms * minOf(sourceFps, maxFps.toFloat()) / 1000f).roundToInt().coerceIn(MinFrames, MaxFrames)
            // ask for frames already small, decoding them at full size is most of the wait
            val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: size
            val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: size
            val k = size.toFloat() / minOf(vw, vh)
            val dw = (vw * k).roundToInt().coerceAtLeast(size)
            val dh = (vh * k).roundToInt().coerceAtLeast(size)
            // as many workers as the phone can feed, each has its own retriever (not thread safe) and takes every nth frame
            val workers = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 6)
            val slots = arrayOfNulls<IntArray>(count)
            val cut = AtomicInteger(0)
            coroutineScope {
                (0 until workers).map { w ->
                    async {
                        val r = MediaMetadataRetriever()
                        try {
                            r.setDataSource(file.absolutePath)
                            for (i in w until count step workers) {
                                // microseconds, frame i sits exactly at i * (length / count)
                                val raw = frameAt(r, ms * 1000L * i / count, dw, dh)
                                if (raw != null) slots[i] = squarePixels(raw, size)
                                CanvasGifProgress.update(Stage.FRAMES, cut.incrementAndGet().toFloat() / count)
                            }
                        } finally {
                            runCatching { r.release() }
                        }
                    }
                }.awaitAll()
            }
            val frames = slots.filterNotNull()
            check(frames.size >= MinFrames) { "couldn't read enough frames" }
            // video changes colours between frames, so the palette comes from a bit of every frame
            val sample = IntArray(frames.sumOf { it.size / 32 })
            var at = 0
            for (f in frames) for (p in f.indices step 32) sample[at++] = f[p]
            val palette = SpinningArtwork.Palette.of(sample)
            // every frame goes to palette indexes once, the size retries below reuse them (before it redid all of it every retry)
            val indexed = arrayOfNulls<ByteArray>(frames.size)
            val mapped = AtomicInteger(0)
            coroutineScope {
                (0 until workers).map { w ->
                    async(Dispatchers.Default) {
                        // own palette per worker so the lookups dont wait on each other, same colours or we fall back to the shared one
                        val pal = if (w == 0) palette else SpinningArtwork.Palette.of(sample)
                            .takeIf { java.util.Objects.deepEquals(it.colors, palette.colors) } ?: palette
                        for (i in w until frames.size step workers) {
                            val f = frames[i]
                            val b = ByteArray(size * size)
                            synchronized(pal) { for (p in f.indices) b[p] = pal.indexOf(f[p]).toByte() }
                            indexed[i] = b
                            CanvasGifProgress.update(Stage.ENCODE, 0.6f * mapped.incrementAndGet() / frames.size)
                        }
                    }
                }.awaitAll()
            }
            val bytes = indexed.requireNoNulls()
            // a gif delay is whole centiseconds (30fps is 3.33) so they alternate, the total still matches the video
            val totalCentis = ms / 10.0
            fun encode(list: List<ByteArray>): ByteArray {
                val encoder = GifEncoder(size, size, palette.colors)
                var spent = 0
                for ((n, idx) in list.withIndex()) {
                    val end = ((n + 1) * totalCentis / list.size).roundToInt()
                    encoder.addFrame(idx, (end - spent).coerceAtLeast(MinFrameCentis))
                    spent = end
                    CanvasGifProgress.update(Stage.ENCODE, 0.6f + 0.4f * (n + 1) / list.size)
                }
                return encoder.finish()
            }
            // discord can turn a huge gif down, so when its too big every other frame goes, the length stays the same
            var step = 1
            var gif = encode(bytes.asList())
            while (gif.size > MaxGifBytes && step < 8) {
                step *= 2
                gif = encode(bytes.filterIndexed { i, _ -> i % step == 0 })
            }
            Timber.tag(LogTag).i("canvas gif ready: ${frames.size / step} of ${frames.size} frames, ${gif.size / 1024} KB")
            gif
        } finally {
            runCatching { retriever.release() }
        }
    }

    // the middle square of the frame, scaled to size
    private fun squarePixels(raw: Bitmap, size: Int): IntArray {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val side = minOf(raw.width, raw.height)
        val src = Rect((raw.width - side) / 2, (raw.height - side) / 2, (raw.width + side) / 2, (raw.height + side) / 2)
        Canvas(out).drawBitmap(raw, src, RectF(0f, 0f, size.toFloat(), size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(size * size)
        out.getPixels(pixels, 0, size, 0, 0, size, size)
        out.recycle()
        raw.recycle()
        return pixels
    }

    companion object {
        private const val LogTag = "CanvasGif"
        // gifs cant go faster than 2 centiseconds a frame (50fps)
        private const val MinFrameCentis = 2
        private const val MinFrames = 8
        // a longer video just gets fewer fps instead of a huge file
        private const val MaxFrames = 360
        // what discord still takes without trouble, as far as i know
        private const val MaxGifBytes = 4 * 1024 * 1024
        private const val RetryAfterFailureMs = 10 * 60_000L
        private const val HlsTimeoutMs = 120_000L
    }
}

// finds the video of a song's canvas or apple music animated cover, the status and the preview in the app both use it
internal object CanvasVideo {
    suspend fun find(context: Context, title: String, artist: String, durationSec: Int?, source: PresencePicture.CanvasSource): String? = coroutineScope {
        val song = normalizeCanvasSongTitle(title)
        val by = normalizeCanvasArtistName(artist)
        val named = song.length >= 2 && by.length >= 2

        // both sources are asked at the same time, the order below only decides who wins
        val spotify = if (source != PresencePicture.CanvasSource.APPLE) {
            async(Dispatchers.IO) { safe { spotifyUrl(context, title, artist, durationSec) } }
        } else null
        // apple's ones are hls streams, CanvasGif turns them into a file first
        val apple = if (source != PresencePicture.CanvasSource.SPOTIFY && named) {
            async(Dispatchers.IO) { safe { AppleMusicCanvasProvider.getBySongArtist(song, by, null, "us", durationSec)?.preferredAnimationUrl } }
        } else null

        val url = spotify?.await() ?: apple?.await()
        spotify?.cancel()
        apple?.cancel()
        Timber.tag("CanvasGif").i(if (url == null) "no video for $title ($source)" else "video found for $title ($source): $url")
        url
    }

    // spotify only answers once it's switched on and signed in on the canvas page
    private suspend fun spotifyUrl(context: Context, title: String, artist: String, durationSec: Int?): String? {
        if (!context.dataStore.get(SpotifyCanvasKey, false)) return null
        return SpotifyCanvasProvider.getBySongArtist(context, title, artist, durationSec)?.preferredAnimationUrl
    }

    // one source blowing up shouldnt take the other one down with it
    private suspend fun <T> safe(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
