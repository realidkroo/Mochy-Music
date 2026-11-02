// CanvasArtworkPlayer.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.mochyy.mochymusic.ui.utils.rememberSettled
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.mochyy.mochymusic.utils.followAppProxy
import com.music.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import java.util.Locale
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.ViewGroup
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

private object CanvasVideoCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                java.io.File(context.cacheDir, "canvas_video"),
                LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
                StandaloneDatabaseProvider(context),
            ).also { cache = it }
        }
}

/** how long a canvas has to stay before its video starts */
private const val CanvasSettleMs = 700L

// the playing canvas is read this small, this often, for the backdrop's colours
private const val CanvasSamplePx = 32
private const val CanvasSampleMs = 1_500L

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    blurRadiusPx: Float = 0f,
    // told when the first frame is up and when it's gone again, so a mirror of it knows when there's something to show
    onReadyChange: ((Boolean) -> Unit)? = null,
    // the flagship's moving backdrop, fed four tones from a tiny copy of the playing frame
    onFrameColors: ((List<androidx.compose.ui.graphics.Color>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    // like the video background: no decoder for each canvas skipped past, only once it has stayed
    val initial = rememberSettled(primary ?: fallback ?: return, CanvasSettleMs) ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    val currentOnReadyChange by rememberUpdatedState(onReadyChange)
    // nothing to show with the app in the background, and a looping canvas kept decoding there for nobody
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldPlay = isPlaying && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(isVideoReady) { currentOnReadyChange?.invoke(isVideoReady) }
    DisposableEffect(Unit) { onDispose { currentOnReadyChange?.invoke(false) } }

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .followAppProxy()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val clientParam = request.url.queryParameter("c")?.trim().orEmpty()
                    val isWeb =
                        clientParam.startsWith("WEB", ignoreCase = true) ||
                            clientParam.startsWith("WEB_REMIX", ignoreCase = true) ||
                            request.url.toString().contains("c=WEB", ignoreCase = true)

                    val userAgent =
                        when {
                            clientParam.startsWith("WEB", ignoreCase = true) ||
                                clientParam.startsWith("WEB_REMIX", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB

                            clientParam.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent

                            clientParam.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent

                            clientParam.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

                            else -> YouTubeClient.USER_AGENT_WEB
                        }

                    val builder = request.newBuilder().header("User-Agent", userAgent)
                    if (isWeb) {
                        builder.header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                        builder.header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                    }

                    chain.proceed(builder.build())
                }
                .build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            val upstreamFactory = DefaultDataSource.Factory(
                context,
                OkHttpDataSource.Factory(okHttpClient),
            )
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(CanvasVideoCache.get(context.applicationContext))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        }
    val aspectRatioFrameLayout = remember {
        AspectRatioFrameLayout(context).apply {
            // zoomed past the cover's box and centred inside the frame below, not by compose
            layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, android.view.Gravity.CENTER)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    // exactly the cover's size: given the zoomed view itself, compose shifted it left to centre it, and the cover's glass recorded it
    // from that shifted corner, so its mirror and blurred copy lost their right side (the cut on animated covers)
    val frame = remember {
        android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            clipChildren = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    // the texture outlives a session, so a new url's player draws into the same view
    val texture = remember { CanvasTexture() }
    val session = remember(initial) {
        CanvasVideoSession(context, mediaSourceFactory, shouldPlay).also { texture.session = it }
    }

    LaunchedEffect(shouldPlay) {
        session.setPlaying(shouldPlay)
    }

    DisposableEffect(session, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val next =
                        when (currentUrl) {
                            primary -> fallback
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        // callbacks land on the canvas thread, but the view only takes changes on the main one
                        aspectRatioFrameLayout.post {
                            aspectRatioFrameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
                        }
                    }
                }
            }
        session.addListener(listener)
        onDispose { session.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, session) {
        val normalized = currentUrl.trim()
        val mimeType = canvasMimeType(normalized, isFallback = fallback != null && currentUrl == fallback && currentUrl != primary)

        val mediaItem =
            MediaItem.Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        isVideoReady = false
        session.load(mediaItem, shouldPlay)
    }

    DisposableEffect(session) {
        onDispose {
            session.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha"
    )

    val currentOnFrameColors by rememberUpdatedState(onFrameColors)
    val sampling = onFrameColors != null
    LaunchedEffect(isVideoReady, sampling, shouldPlay) {
        if (!isVideoReady || !sampling) return@LaunchedEffect
        val sample = android.graphics.Bitmap.createBitmap(CanvasSamplePx, CanvasSamplePx, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(CanvasSamplePx * CanvasSamplePx)
        // a paused canvas never changes, so it's read once and left alone
        do {
            val view = aspectRatioFrameLayout.getChildAt(0) as? TextureView
            if (view != null && view.isAvailable && runCatching { view.getBitmap(sample) }.isSuccess) {
                sample.getPixels(pixels, 0, CanvasSamplePx, 0, 0, CanvasSamplePx, CanvasSamplePx)
                currentOnFrameColors?.invoke(
                    com.mochyy.mochymusic.ui.player.flagship.ambientTones(pixels, CanvasSamplePx, CanvasSamplePx)
                )
            }
            kotlinx.coroutines.delay(CanvasSampleMs)
        } while (shouldPlay)
    }

    AndroidView(
        factory = { viewContext ->
            if (aspectRatioFrameLayout.childCount == 0) {
                val textureView = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    surfaceTextureListener = texture
                }
                aspectRatioFrameLayout.addView(textureView)
            }
            frame.apply { if (childCount == 0) addView(aspectRatioFrameLayout) }
        },
        update = { view ->

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                view.setRenderEffect(
                    com.mochyy.mochymusic.ui.component.BlurEffectCache.getNative(
                        blurRadiusPx,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                )
            }
        },
        modifier = modifier.alpha(alpha),
    )
}

// whether a canvas link is an hls playlist or a plain mp4, so the song share's export reads it the same way the player does
internal fun canvasMimeType(url: String, isFallback: Boolean = false): String {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        lower.contains(".m3u8") || lower.split('?').first().endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        lower.contains(".mp4") -> MimeTypes.VIDEO_MP4
        isFallback -> MimeTypes.VIDEO_MP4
        else -> MimeTypes.APPLICATION_M3U8
    }
}

// one thread runs every canvas player, so building or releasing one never stalls the main thread mid-scroll
private object CanvasVideoThread {
    val handler: Handler by lazy { Handler(HandlerThread("CanvasVideo").apply { start() }.looper) }
}

// one canvas video's player, touched only on the canvas thread; the main thread just posts to it
internal class CanvasVideoSession(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
    playing: Boolean,
) {
    private val handler = CanvasVideoThread.handler
    private var player: ExoPlayer? = null
    private var surface: Surface? = null

    init {
        handler.post {
            player = ExoPlayer.Builder(context)
                .setLooper(handler.looper)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setMaxVideoSize(1280, 1280)
                        .setMaxVideoBitrate(2_500_000)
                        .setForceHighestSupportedBitrate(false)
                        .build()
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false,
                    )
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    playWhenReady = playing
                    surface?.let(::setVideoSurface)
                }
        }
    }

    fun setPlaying(playing: Boolean) = handler.post {
        player?.let { if (it.playWhenReady != playing) it.playWhenReady = playing }
    }

    fun addListener(listener: Player.Listener) = handler.post { player?.addListener(listener) }

    fun removeListener(listener: Player.Listener) = handler.post { player?.removeListener(listener) }

    fun load(mediaItem: MediaItem, playing: Boolean) = handler.post {
        player?.apply {
            stop()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = playing
        }
    }

    // back to the start of what's loaded, so a share's video starts over with its song
    fun restart() = handler.post { player?.seekTo(0L) }

    fun attach(texture: SurfaceTexture) = handler.post {
        surface?.release()
        surface = Surface(texture).also { player?.setVideoSurface(it) }
    }

    // waits here, off the main thread, until the decoder lets go of the texture, then hands it back to be freed
    fun detach(texture: SurfaceTexture) = handler.post {
        surface?.let { player?.clearVideoSurface(it) }
        surface?.release()
        surface = null
        mainHandler.post { texture.release() }
    }

    fun release() = handler.post {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    private companion object {
        val mainHandler = Handler(Looper.getMainLooper())
    }
}

// keeps the view's texture alive until the canvas thread is done with it, and gives it to whichever session is current
internal class CanvasTexture : TextureView.SurfaceTextureListener {
    private var available: SurfaceTexture? = null

    var session: CanvasVideoSession? = null
        set(value) {
            field = value
            available?.let { value?.attach(it) }
        }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        available = texture
        session?.attach(texture)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        available = null
        val current = session
        if (current == null) return true
        current.detach(texture)
        // false: the texture is freed after the decoder lets go of it, not while it still draws into it
        return false
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
