// SongSharePreview.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.innertube.YouTube
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlayer
import com.mochyy.mochymusic.ui.player.CanvasTexture
import com.mochyy.mochymusic.ui.player.CanvasVideoSession
import com.mochyy.mochymusic.ui.player.share.ShareHeight
import com.mochyy.mochymusic.ui.player.share.ShareRegions
import com.mochyy.mochymusic.ui.player.share.ShareWidth
import com.mochyy.mochymusic.utils.VideoTrack
import okhttp3.OkHttpClient
import kotlin.math.max
import kotlin.math.min

// whether this page is the one in front: a page pushed over it stops its picture moving until it's back on top
@Composable
internal fun PopupSheetState.rememberIsFront(): Boolean {
    val depth = remember { frames.size }
    return frames.count { !it.closing } <= depth
}

// the 360 x 640 picture shrunk to fit the box: laid out at a smaller density instead of scaled, so the card's glass lines up by plain positions
@Composable
internal fun SharePictureFit(modifier: Modifier, corner: Dp = 26.dp, fill: Boolean = false, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.then(if (fill) Modifier.clipToBounds() else Modifier), contentAlignment = Alignment.Center) {
        // fill crops the picture to the box, for the small tiles; otherwise it fits whole
        val k = if (fill) max(maxWidth / ShareWidth, maxHeight / ShareHeight) else min(maxWidth / ShareWidth, maxHeight / ShareHeight)
        val density = LocalDensity.current
        // a moving picture repaints only its own box, not the whole sheet with it
        Box(Modifier.requiredSize(ShareWidth * k, ShareHeight * k).clip(RoundedCornerShape(corner)).damageBoundary()) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * k, fontScale = 1f)) { content() }
        }
    }
}

// the editor's live picture: drag or pinch to move the background, tap the card to style it; only the front page's picture moves
@Composable
internal fun SongSharePreview(
    session: SongShareSession,
    live: Boolean,
    modifier: Modifier = Modifier,
    onCardTap: (() -> Unit)? = null,
    movable: Boolean = true,
    regions: ShareRegions? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val resting = remember(session) { { session.windowStartMs } }
    SharePictureFit(modifier) {
        Box {
            SongSharePicture(
                session = session,
                positionMs = if (live) session.playheadMs else resting,
                layers = ShareLayers.Everything,
                moving = live && session.isVideo,
                reportsCard = true,
                onCardTap = onCardTap,
                regions = regions,
                video = { ShareVideoLayer(session, playing = live && session.previewPlays) },
                modifier = if (movable) {
                    Modifier.pointerInput(session) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            session.moveBackground(zoom, pan.x / size.width, pan.y / size.height)
                        }
                    }
                } else {
                    Modifier
                },
            )
            overlay?.invoke(this)
        }
    }
}

// the background video under the preview, started over each time the song's window loops
@Composable
internal fun ShareVideoLayer(
    session: SongShareSession,
    playing: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    source: ShareVideoSource? = (session.background as? SongBackground.Video)?.source,
) {
    val loops = session.preview?.loops ?: 0
    when (source) {
        null -> Unit
        ShareVideoSource.AnimatedCover -> session.animatedCover.found?.let { url ->
            CanvasArtworkPlayer(primaryUrl = url, fallbackUrl = null, isPlaying = playing, modifier = modifier)
        }
        ShareVideoSource.MusicVideo -> session.musicVideo.found?.let { music ->
            val track = music.trackFor(PreviewVideoShortSide) ?: return@let
            val start = music.videoMsAt(session.windowStartMs).coerceAtLeast(0L)
            val item = remember(track.url, start, session.windowLengthMs) {
                clippedItem(MediaItem.Builder().setUri(track.url).setMimeType(MimeTypes.VIDEO_MP4), start, session.windowLengthMs)
            }
            LoopingVideo(item, music.streams.headers, playing, restartKey = loops, modifier = modifier)
        }
        is ShareVideoSource.Own -> {
            val item = remember(source) { clippedItem(MediaItem.Builder().setUri(source.uri), source.startMs, source.lengthMs) }
            LoopingVideo(item, emptyMap(), playing, restartKey = loops, modifier = modifier)
        }
    }
}

// the preview keeps to a light picture; the export reads the sharpest one
private const val PreviewVideoShortSide = 720

internal fun clippedItem(builder: MediaItem.Builder, startMs: Long, lengthMs: Long): MediaItem =
    builder
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0L))
                .setEndPositionMs(startMs.coerceAtLeast(0L) + lengthMs)
                .build(),
        )
        .build()

// the music video's sharpest h264 picture up to maxShortSide, which every phone decodes in hardware; any codec when it has no h264
internal fun ShareMusicVideo.trackFor(maxShortSide: Int): VideoTrack? {
    val fitting = streams.tracks.filter { it.shortSide in 1..maxShortSide }
    val h264 = fitting.filter { it.codecs.startsWith("avc1") && it.mimeType.contains("mp4") }
    return (h264.ifEmpty { fitting }).maxByOrNull { it.shortSide * 1000 + it.fps.coerceAtMost(60) }
        ?: streams.tracks.minByOrNull { it.shortSide }
}

// a clip that loops on the canvas thread, cropped to fill, so loading and releasing it never stall the sheet
@Composable
private fun LoopingVideo(item: MediaItem, headers: Map<String, String>, playing: Boolean, restartKey: Int, modifier: Modifier) {
    val context = LocalContext.current
    val factory = remember(headers) {
        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(YouTube.proxy).build())
            .setDefaultRequestProperties(headers)
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
    }
    val frame = remember {
        AspectRatioFrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }
    val texture = remember { CanvasTexture() }
    val currentPlaying by rememberUpdatedState(playing)
    val player = remember(factory) { CanvasVideoSession(context, factory, currentPlaying).also { texture.session = it } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    frame.post { frame.setAspectRatio(videoSize.width.toFloat() / videoSize.height) }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, item) { player.load(item, currentPlaying) }
    LaunchedEffect(player, playing) { player.setPlaying(playing) }
    LaunchedEffect(player, restartKey) { if (restartKey > 0) player.restart() }
    AndroidView(
        factory = { viewContext ->
            frame.apply {
                if (childCount == 0) {
                    addView(
                        TextureView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            surfaceTextureListener = texture
                        },
                    )
                }
            }
        },
        modifier = modifier,
    )
}
