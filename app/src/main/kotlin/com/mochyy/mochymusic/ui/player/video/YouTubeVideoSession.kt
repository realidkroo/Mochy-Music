package com.mochyy.mochymusic.ui.player.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.utils.VideoStreams
import com.mochyy.mochymusic.utils.VideoTrack
import com.mochyy.mochymusic.utils.followAppProxy
import okhttp3.OkHttpClient
import kotlin.math.abs

// further off than this and the picture jumps to the song instead of catching up
private const val HardSyncMs = 1_200L

// this close counts as in step, so the picture plays at the song's own speed
private const val SettledMs = 40L

// a gap of this many ms is closed by playing about 4% faster or slower
private const val CatchUpMs = 2_500f
private const val MaxNudge = 0.12f

// a seek that hasn't finished loading by now is forgotten, so the next tick may try again
private const val SeekGiveUpMs = 9_000L

// how far ahead of the song a seek may aim to make up for the time the picture takes to load
private const val MaxLeadMs = 3_000L

// how long a session waits for someone else to pick it up after its last view goes, before letting go of the player
private const val LingerMs = 1_500L

// a small buffer: quick to start, light on memory, still enough to ride out a short slowdown
private const val MinBufferMs = 12_000
private const val MaxBufferMs = 40_000
private const val BufferToStartMs = 800
private const val BufferToResumeMs = 1_600

private val ManifestUri: Uri = Uri.parse("https://www.youtube.com/mochy-video.mpd")

// the youtube video's own thread: building, loading and letting go of a player never lands on a frame
private object YouTubeVideoThread {
    val handler: Handler by lazy { Handler(HandlerThread("YouTubeVideo").apply { start() }.looper) }
}

// one http client for every video, through the same proxy as the music
private object YouTubeVideoHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followAppProxy()
            .build()
    }
}

// one youtube video playing without sound, kept in step with the song; the main thread only reads its state and posts to it
@Stable
class YouTubeVideoSession internal constructor(context: Context, val videoId: String) {
    // the first picture is up, so the view can fade in
    var hasFrame by mutableStateOf(false)
        private set

    // it should be playing but is waiting on the network, so a loader shows
    var isStalled by mutableStateOf(false)
        private set

    // the short side of the picture playing right now, for "Auto (720p)"
    var playingShortSide by mutableIntStateOf(0)
        private set

    var aspect by mutableFloatStateOf(0f)
        private set

    // counts up each time the links stop working, so whoever owns the video can fetch new ones
    var failures by mutableIntStateOf(0)
        private set

    // the short sides this video comes in, biggest first
    var sizes by mutableStateOf(emptyList<Int>())
        private set

    // what's loaded, read on the main thread to tell a fresh load from a repeat
    var loaded: VideoStreams? = null
        private set

    private val appContext = context.applicationContext
    private val handler = YouTubeVideoThread.handler
    private val main = Handler(Looper.getMainLooper())

    // everything from here on is only touched on the video thread
    private var player: ExoPlayer? = null
    // every view showing this video, oldest first; the newest gets the picture, and when it goes the one before takes it back
    private val textures = ArrayList<SurfaceTexture>()
    private val surfaces = HashMap<SurfaceTexture, Surface>()
    private var owner: SurfaceTexture? = null
    private var streams: VideoStreams? = null
    private var quality = VideoQuality.AUTO
    private var autoCap = 1080
    // the size a whole-file video was loaded at; 0 when the player picks sizes itself
    private var wholeFileSide = 0
    // h264 or the vp9/av1 family past 1080p, whichever the loaded source was built from
    private var loadedFamily: List<VideoTrack>? = null
    private var seekStartedAt = 0L
    private var seekLatencyMs = 600.0
    private var speed = 1f
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && seekStartedAt != 0L) {
                // learn how long a seek takes on this connection, so the next one aims that far ahead
                val took = SystemClock.elapsedRealtime() - seekStartedAt
                seekLatencyMs = seekLatencyMs * 0.6 + took * 0.4
                seekStartedAt = 0L
            }
            publishStall()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publishStall()

        override fun onRenderedFirstFrame() {
            main.post { hasFrame = true }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            val side = minOf(videoSize.width, videoSize.height)
            main.post {
                aspect = ratio
                playingShortSide = side
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            main.post { failures += 1 }
        }
    }

    fun load(streams: VideoStreams, quality: VideoQuality, autoCap: Int, startMs: Long) {
        if (loaded == streams) return
        loaded = streams
        sizes = streams.sizes
        handler.post {
            if (released) return@post
            this.streams = streams
            this.quality = quality
            this.autoCap = autoCap
            val p = player ?: buildPlayer().also { player = it }
            applyQuality(p)
            seekStartedAt = SystemClock.elapsedRealtime()
            p.setMediaSource(mediaSourceFor(streams), startMs.coerceAtLeast(0L))
            p.prepare()
        }
    }

    fun setQuality(quality: VideoQuality, autoCap: Int) = handler.post {
        if (released || (this.quality == quality && this.autoCap == autoCap)) return@post
        this.quality = quality
        this.autoCap = autoCap
        val p = player ?: return@post
        applyQuality(p)
        val s = streams ?: return@post
        // a whole-file video can't change size by itself, nor can a manifest change codec: load the other one right where the picture is
        val otherFamily = s.tracksFor(capShortSide()) !== loadedFamily
        if (otherFamily || (wholeFileSide != 0 && s.trackFor(capShortSide())?.shortSide != wholeFileSide)) {
            seekStartedAt = SystemClock.elapsedRealtime()
            p.setMediaSource(mediaSourceFor(s), p.currentPosition)
            p.prepare()
        }
    }

    // where the song says the picture should be (null: wherever it is), read on the main thread just now
    fun sync(targetMs: Long?, songSpeed: Float, playing: Boolean) {
        val sampledAt = SystemClock.elapsedRealtime()
        handler.post { syncNow(targetMs, songSpeed, playing, sampledAt) }
    }

    fun attach(texture: SurfaceTexture) = handler.post {
        if (released || texture in textures) return@post
        textures.add(texture)
        surfaces[texture] = Surface(texture)
        showOn(texture)
    }

    // waits here, off the main thread, until the decoder lets go of the texture, then hands it back to be freed
    fun detach(texture: SurfaceTexture, onFreed: () -> Unit) = handler.post {
        textures.remove(texture)
        val surface = surfaces.remove(texture)
        if (owner === texture) {
            val next = textures.lastOrNull()
            if (next != null) showOn(next) else {
                surface?.let { player?.clearVideoSurface(it) }
                owner = null
            }
        }
        surface?.release()
        main.post(onFreed)
    }

    private fun showOn(texture: SurfaceTexture) {
        owner = texture
        surfaces[texture]?.let { player?.setVideoSurface(it) }
    }

    internal fun release() = handler.post {
        released = true
        player?.removeListener(listener)
        player?.release()
        player = null
        surfaces.values.forEach { it.release() }
        surfaces.clear()
        textures.clear()
        owner = null
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(appContext)
            .setLooper(handler.looper)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(MinBufferMs, MaxBufferMs, BufferToStartMs, BufferToResumeMs)
                    .build()
            )
            .build()
            .apply {
                volume = 0f
                // a video shorter than the song starts over rather than freezing on its last frame
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                owner?.let { surfaces[it] }?.let(::setVideoSurface)
                addListener(listener)
            }

    private fun capShortSide() = if (quality == VideoQuality.AUTO) autoCap else quality.shortSide

    private fun applyQuality(p: ExoPlayer) {
        val s = streams ?: return
        val cap = capShortSide()
        val landscape = s.tracks.lastOrNull()?.let { it.width >= it.height } ?: true
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(if (landscape) Int.MAX_VALUE else cap, if (landscape) cap else Int.MAX_VALUE)
            // a picked size stays put; auto is free to drop when the connection slows and climb back after
            .setForceHighestSupportedBitrate(quality != VideoQuality.AUTO)
            .build()
    }

    private fun mediaSourceFor(s: VideoStreams): MediaSource {
        val dataSource = OkHttpDataSource.Factory(YouTubeVideoHttp.client).setDefaultRequestProperties(s.headers)
        val family = s.tracksFor(capShortSide())
        loadedFamily = family
        val manifest = VideoDash.manifest(family, s.durationMs)?.let { xml ->
            runCatching { DashManifestParser().parse(ManifestUri, xml.byteInputStream()) }.getOrNull()
        }
        if (manifest != null) {
            wholeFileSide = 0
            val item = MediaItem.Builder().setUri(ManifestUri).setMimeType(MimeTypes.APPLICATION_MPD).build()
            return DashMediaSource.Factory(dataSource).createMediaSource(manifest, item)
        }
        // youtube only serves this one whole: one size, picked by the quality setting
        val track = s.trackFor(capShortSide()) ?: s.tracks.first()
        wholeFileSide = track.shortSide
        return ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(track.url))
    }

    private fun syncNow(targetMs: Long?, songSpeed: Float, playing: Boolean, sampledAt: Long) {
        val p = player ?: return
        if (p.playWhenReady != playing) p.playWhenReady = playing
        val duration = p.duration
        if (targetMs == null || duration <= 0L) {
            setSpeed(p, songSpeed)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val expected = targetMs + if (playing) ((now - sampledAt) * songSpeed).toLong() else 0L
        // the song's own intro or outro, past either end of the video: it just keeps playing
        if (expected !in 0..duration) {
            setSpeed(p, songSpeed)
            return
        }
        // waiting for data after a seek or a slowdown: seeking again now throws away what's on its way, which is what froze the picture
        if (p.playbackState == Player.STATE_BUFFERING) {
            if (seekStartedAt != 0L && now - seekStartedAt > SeekGiveUpMs) seekStartedAt = 0L
            return
        }
        val drift = p.currentPosition - expected
        if (abs(drift) > HardSyncMs) {
            // aimed ahead by however long the last seeks took to load, so it lands in step instead of behind
            val lead = if (playing) (seekLatencyMs * songSpeed).toLong().coerceIn(0L, MaxLeadMs) else 0L
            seekStartedAt = now
            p.seekTo((expected + lead).coerceAtMost(duration - 1L))
            setSpeed(p, songSpeed)
            return
        }
        // a small gap closes by playing a touch faster or slower: no seek, no stall
        val nudge = if (!playing || abs(drift) < SettledMs) 0f else (-drift / CatchUpMs).coerceIn(-MaxNudge, MaxNudge)
        setSpeed(p, songSpeed * (1f + nudge))
    }

    private fun setSpeed(p: ExoPlayer, value: Float) {
        if (abs(value - speed) < 0.004f) return
        speed = value
        p.setPlaybackSpeed(value)
    }

    private fun publishStall() {
        val p = player ?: return
        val stalled = p.playWhenReady && p.playbackState == Player.STATE_BUFFERING
        main.post { isStalled = stalled }
    }
}

// one session per video, kept a moment after its last view goes, so the player turning into the full video mode keeps the same picture going
object YouTubeVideoSessions {
    private class Entry(val session: YouTubeVideoSession) {
        var users = 0
        var pendingRelease: Runnable? = null
    }

    private val entries = HashMap<String, Entry>()
    private val main = Handler(Looper.getMainLooper())

    fun acquire(context: Context, videoId: String): YouTubeVideoSession {
        // only one video plays at a time: one left waiting for a hand-over that didn't come goes as another starts
        entries.entries.removeAll { (id, other) ->
            val idle = id != videoId && other.users == 0
            if (idle) {
                other.pendingRelease?.let(main::removeCallbacks)
                other.session.release()
            }
            idle
        }
        val entry = entries.getOrPut(videoId) { Entry(YouTubeVideoSession(context, videoId)) }
        entry.pendingRelease?.let(main::removeCallbacks)
        entry.pendingRelease = null
        entry.users += 1
        return entry.session
    }

    fun release(session: YouTubeVideoSession) {
        val entry = entries[session.videoId]?.takeIf { it.session === session } ?: return
        entry.users -= 1
        if (entry.users > 0) return
        val drop = Runnable {
            if (entry.users == 0 && entries[session.videoId] === entry) {
                entries.remove(session.videoId)
                session.release()
            }
        }
        entry.pendingRelease = drop
        main.postDelayed(drop, LingerMs)
    }
}
