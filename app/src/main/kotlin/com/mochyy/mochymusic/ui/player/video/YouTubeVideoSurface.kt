package com.mochyy.mochymusic.ui.player.video

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.player.LetterboxStabilizer
import com.mochyy.mochymusic.ui.player.SongVideo
import com.mochyy.mochymusic.ui.player.VideoLetterbox
import com.mochyy.mochymusic.ui.player.videoCropTransform
import com.mochyy.mochymusic.utils.YTPlayerUtils
import com.mochyy.mochymusic.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// frames are read at this size for bars and edge colours: a 0.7% step per line, and cheap to copy
private const val SampleSize = 144
private const val SampleIntervalMs = 300L
private const val BarZoomMs = 700

// how often the song's place is handed to the video
private const val SyncIntervalMs = 250L

// a refused link is fetched again this many times, a little further apart each time
private const val MaxRefreshes = 3

// how wide a strip at each side of the picture its edge colour is taken from
private const val EdgeBand = 0.12f

// the colours at the left and right edges of the picture, for the light round it
@Immutable
data class VideoAmbient(val left: Color, val right: Color)

@Composable
fun rememberYouTubeVideoSession(videoId: String): YouTubeVideoSession {
    val context = LocalContext.current
    val session = remember(videoId) { YouTubeVideoSessions.acquire(context, videoId) }
    DisposableEffect(session) { onDispose { YouTubeVideoSessions.release(session) } }
    return session
}

// keeps a session loaded (fresh links when the old ones stop working) and in step with the song; autoCap is the most auto may pick
@Composable
fun DriveYouTubeVideo(
    session: YouTubeVideoSession,
    video: SongVideo,
    isPlaying: Boolean,
    autoCap: Int,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val (quality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    // nothing to show with the app in the background, so the picture stops there too
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running by rememberUpdatedState(isPlaying && lifecycle.isAtLeast(Lifecycle.State.STARTED))
    val positionAt by rememberUpdatedState(video.positionAt)
    var streams by remember(video.videoId) { mutableStateOf(video.streams) }
    LaunchedEffect(video.streams) { if (!video.streams.isExpired) streams = video.streams }

    fun target(songMs: Long): Long? {
        val duration = streams.durationMs
        return positionAt?.invoke(songMs) ?: if (duration > 0L) songMs % duration else null
    }

    LaunchedEffect(session, streams) {
        session.load(streams, quality, autoCap, target(playerConnection.player.currentPosition) ?: 0L)
    }
    LaunchedEffect(session, quality, autoCap) { session.setQuality(quality, autoCap) }

    LaunchedEffect(session) {
        snapshotFlow { session.failures }.filter { it in 1..MaxRefreshes }.collectLatest { count ->
            delay(1_000L * count * count)
            YTPlayerUtils.resolveVideoStreams(session.videoId).getOrNull()?.let { streams = it }
        }
    }

    LaunchedEffect(session) {
        while (isActive) {
            val player = playerConnection.player
            session.sync(target(player.currentPosition), player.playbackParameters.speed, running)
            delay(SyncIntervalMs)
        }
    }
}

// the session's picture, centre-cropped past any black bars baked into the video; fades in once the first frame is up
@Composable
fun YouTubeVideoSurface(
    session: YouTubeVideoSession,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    // 0 zooms the picture to fill the view, 1 shows all of it with room round it; anything between eases from one to the other
    fit: () -> Float = { 0f },
    // the most of the picture filling may cut away; a picture that would lose more (upright, square, 4:3) sits whole-ish with room round it instead
    fillMaxLoss: Float = 1f,
    glassState: GlassState? = null,
    glassActive: () -> Boolean = { true },
    keepScreenOn: Boolean = false,
    onAmbient: ((VideoAmbient) -> Unit)? = null,
    // the flagship's moving backdrop, fed four tones from the same frames the bars are read from
    onFrameColors: ((List<Color>) -> Unit)? = null,
) {
    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val playing by rememberUpdatedState(isPlaying)
    val currentOnAmbient by rememberUpdatedState(onAmbient)
    val currentOnFrameColors by rememberUpdatedState(onFrameColors)

    // the view is stretched over the frame and then scaled as a whole, so the texture itself stays the untouched frame the sampling reads
    val textureView = remember(session) {
        TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            pivotX = 0f
            pivotY = 0f
            surfaceTextureListener = VideoTexture(session)
        }
    }
    val videoFrame = remember(textureView) {
        FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = true
            isEnabled = false
            isClickable = false
            isFocusable = false
            addView(textureView)
        }
    }

    // how much of the frame, per side, is black bar the view zooms past
    val barInsetX = remember(session) { Animatable(0f) }
    val barInsetY = remember(session) { Animatable(0f) }

    LaunchedEffect(session) {
        snapshotFlow { session.hasFrame }.first { it }
        val sample = Bitmap.createBitmap(SampleSize, SampleSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SampleSize * SampleSize)
        val vertical = LetterboxStabilizer()
        val horizontal = LetterboxStabilizer()
        val zoomSpec = tween<Float>(BarZoomMs, easing = FastOutSlowInEasing)
        var read = false
        while (isActive) {
            delay(SampleIntervalMs)
            // a paused frame never changes, so it's read once and then left alone
            if ((read && !playing) || !textureView.isAvailable) continue
            if (runCatching { textureView.getBitmap(sample) }.isFailure) continue
            sample.getPixels(pixels, 0, SampleSize, 0, 0, SampleSize, SampleSize)
            read = true

            val reading = VideoLetterbox.analyze(pixels, SampleSize, SampleSize)
            val now = SystemClock.uptimeMillis()
            if (vertical.offer(reading.vertical, now)) launch { barInsetY.animateTo(vertical.inset, zoomSpec) }
            if (horizontal.offer(reading.horizontal, now)) launch { barInsetX.animateTo(horizontal.inset, zoomSpec) }
            currentOnAmbient?.invoke(edgeColors(pixels, SampleSize, horizontal.inset, vertical.inset))
            currentOnFrameColors?.invoke(
                com.mochyy.mochymusic.ui.player.flagship.ambientTones(pixels, SampleSize, SampleSize, horizontal.inset, vertical.inset)
            )
        }
    }

    val currentFit by rememberUpdatedState(fit)
    LaunchedEffect(textureView) {
        snapshotFlow {
            val t = currentFit().coerceIn(0f, 1f)
            fun transform(maxLoss: Float) = videoCropTransform(
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                videoAspect = session.aspect,
                insetX = barInsetX.value,
                insetY = barInsetY.value,
                maxLoss = maxLoss,
            )
            val filled = transform(fillMaxLoss)
            if (t == 0f) filled else filled.lerpTo(transform(0f), t)
        }.collect { transform ->
            textureView.scaleX = transform.scaleX
            textureView.scaleY = transform.scaleY
            textureView.translationX = transform.translationX
            textureView.translationY = transform.translationY
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (session.hasFrame) 1f else 0f,
        animationSpec = tween(400),
        label = "youtubeVideoAlpha",
    )

    // a new video brings a new view, the old one goes with its own session
    key(videoFrame) {
        AndroidView(
            factory = { videoFrame },
            // a view anywhere in the window asking for it keeps the screen awake, without touching the app's own keep-awake setting
            update = { it.keepScreenOn = keepScreenOn },
            modifier = modifier
                .onSizeChanged { viewSize = it }
                .graphicsLayer { this.alpha = alpha }
                .then(if (glassState != null) Modifier.glassRoot(glassState, isActive = glassActive) else Modifier),
        )
    }
}

private fun com.mochyy.mochymusic.ui.player.VideoTransform.lerpTo(
    other: com.mochyy.mochymusic.ui.player.VideoTransform,
    t: Float,
) = com.mochyy.mochymusic.ui.player.VideoTransform(
    scaleX = scaleX + (other.scaleX - scaleX) * t,
    scaleY = scaleY + (other.scaleY - scaleY) * t,
    translationX = translationX + (other.translationX - translationX) * t,
    translationY = translationY + (other.translationY - translationY) * t,
)

// hands the view's texture to the session, and only frees it once the decoder has let go of it
private class VideoTexture(private val session: YouTubeVideoSession) : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        session.attach(texture)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        session.detach(texture) { texture.release() }
        return false
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}

// the average colour of a strip down each side of the picture, inside any bars
internal fun edgeColors(pixels: IntArray, size: Int, insetX: Float, insetY: Float): VideoAmbient {
    val top = (insetY * size).toInt().coerceIn(0, size / 2 - 1)
    val bottom = size - top
    val left = (insetX * size).toInt().coerceIn(0, size / 2 - 1)
    val right = size - left
    val band = ((right - left) * EdgeBand).toInt().coerceAtLeast(2)

    fun average(from: Int, to: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in top until bottom step 2) {
            val row = y * size
            for (x in from until to) {
                val c = pixels[row + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
        }
        if (count == 0) return Color.Black
        return Color(red = (r / count).toInt(), green = (g / count).toInt(), blue = (b / count).toInt())
    }
    return VideoAmbient(left = average(left, left + band), right = average(right - band, right))
}
