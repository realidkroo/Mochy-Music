// SongShareExport.kt

package com.mochyy.mochymusic.ui.player.share.song.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Size
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.music.innertube.YouTube
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.ui.player.canvasMimeType
import com.mochyy.mochymusic.ui.player.share.ShareWidth
import com.mochyy.mochymusic.ui.player.share.song.MaxCardBlur
import com.mochyy.mochymusic.ui.player.share.song.ShareLayers
import com.mochyy.mochymusic.ui.player.share.song.ShareVideoSource
import com.mochyy.mochymusic.ui.player.share.song.SongBackground
import com.mochyy.mochymusic.ui.player.share.song.SongSharePicture
import com.mochyy.mochymusic.ui.player.share.song.SongShareSession
import com.mochyy.mochymusic.ui.player.share.song.cardCornerOf
import com.mochyy.mochymusic.ui.player.share.song.clippedItem
import com.mochyy.mochymusic.ui.player.share.song.found
import com.mochyy.mochymusic.ui.player.share.song.freshMusicVideo
import com.mochyy.mochymusic.ui.player.share.song.trackFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ExportDir = "song_share"

private const val Tag = "SongShare"

// how many still frames the picture gets to settle into place before the first real one
private const val WarmUpFrames = 3

// stories are 9:16; the short side is the quality
internal enum class ShareQuality(val width: Int) {
    UltraHd(2160),
    FullHd(1080);

    val height: Int get() = width * 16 / 9
}

// the app's own theme, handed to the export so its lyrics use the same fonts as the lyrics page
internal class ShareTheme(val colors: ColorScheme, val typography: Typography)

// one export in flight: how far it got, and a flag the gl thread checks so a cancel never leaves it waiting on the main thread
internal class ShareExportJob {
    @Volatile var cancelled = false
    @Volatile var progress = 0f
}

// the picture drawn at export size: 360 picture units across the full width, in the app's theme, at full blur quality
@Composable
private fun ExportPicture(
    session: SongShareSession,
    theme: ShareTheme,
    widthPx: Int,
    positionMs: () -> Long,
    layers: ShareLayers,
    moving: Boolean,
) {
    MaterialTheme(colorScheme = theme.colors, typography = theme.typography) {
        CompositionLocalProvider(
            LocalDensity provides Density(widthPx / ShareWidth.value, fontScale = 1f),
            LocalContentColor provides Color.White,
            LocalBlurQuality provides BlurQuality.HIGH,
        ) {
            // the card reports where it sits in this very layout, so the blur laid under it on a video matches it exactly; the editor's card can be a different height
            SongSharePicture(session = session, positionMs = positionMs, layers = layers, moving = moving, reportsCard = true)
        }
    }
}

// one picture of the share at the window's start; cropped to the card for a story sticker
internal suspend fun exportStill(
    context: Context,
    session: SongShareSession,
    theme: ShareTheme,
    quality: ShareQuality,
    layers: ShareLayers,
    cropToCard: Boolean = false,
): File {
    val host = checkNotNull(session.offscreenHost) { "The share isn't on screen" }
    val frames = OffscreenFrames(host, quality.width, quality.height) {
        ExportPicture(session, theme, quality.width, { session.windowStartMs }, layers, moving = false)
    }
    try {
        frames.warmUp(WarmUpFrames)
        val frame = frames.capture()
        // cinematic words have no card to crop to: the sticker is the whole picture, see-through round the words
        val bounds = session.cardBounds.takeIf { cropToCard && session.hasCard }
        val picture = if (bounds != null) {
            val left = (bounds.left * frame.width).toInt().coerceIn(0, frame.width - 1)
            val top = (bounds.top * frame.height).toInt().coerceIn(0, frame.height - 1)
            val right = (bounds.right * frame.width).toInt().coerceIn(left + 1, frame.width)
            val bottom = (bounds.bottom * frame.height).toInt().coerceIn(top + 1, frame.height)
            Bitmap.createBitmap(frame.bitmap, left, top, right - left, bottom - top)
        } else {
            Bitmap.createBitmap(frame.bitmap, 0, 0, frame.width, frame.height)
        }
        // written before the frames are let go: android hands back the frame itself when the crop is the whole of it, and letting go recycles it
        return withContext(Dispatchers.IO) {
            newExportFile(context, "png").also { file -> file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    } finally {
        frames.release()
    }
}

// the share as a video with the song playing: the background from its source, the card drawn by compose for every frame on top
internal suspend fun exportVideo(
    context: Context,
    session: SongShareSession,
    theme: ShareTheme,
    quality: ShareQuality,
    layers: ShareLayers,
    songSource: DataSource.Factory,
    job: ShareExportJob,
): File {
    val host = checkNotNull(session.offscreenHost) { "The share isn't on screen" }
    val fps = ShareEncoders.frameRateFor(quality)
    val width = quality.width
    val height = quality.height
    val video = (session.background as? SongBackground.Video)?.takeIf { layers.background }
    val background = video?.let { videoBackground(session, it) }
    // over a video the card is drawn alone and its blur is done on the video itself; otherwise compose draws all of it
    val composeLayers = when {
        background == null -> layers
        layers.card -> ShareLayers.CardOverVideo
        else -> null
    }
    val playhead = mutableLongStateOf(session.windowStartMs)
    val frames = composeLayers?.let { picked ->
        OffscreenFrames(host, width, height) { ExportPicture(session, theme, width, { playhead.longValue }, picked, moving = true) }
    }
    val totalFrames = (session.windowLengthMs * fps / 1000L).coerceAtLeast(1L)
    val source = frames?.let { ExportFrames(it, playhead, session.windowStartMs, job, totalFrames) }
    try {
        frames?.warmUp(WarmUpFrames)
        val foreground = source?.let { listOf(ForegroundEffect(it::frameAt)) }.orEmpty()
        val frost = if (background != null && layers.card && layers.glass) cardFrost(session, width, height) else null

        val carrier = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(Uri.fromFile(carrierImage(context))).setImageDurationMs(session.windowLengthMs).build(),
        )
            .setFrameRate(fps)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_STRETCH_TO_FIT)) +
                        if (background == null) foreground else emptyList(),
                ),
            )
            .build()
        val song = EditedMediaItem.Builder(clippedItem(session.song.toMediaItem().buildUpon(), session.windowStartMs, session.windowLengthMs))
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(ShareAudioFade(session.windowLengthMs)), emptyList()))
            .build()

        val sequences = buildList {
            add(EditedMediaItemSequence.Builder(carrier).build())
            if (background != null) {
                val moving = EditedMediaItem.Builder(background.item)
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), backgroundEffects(session, video!!.dim, width, height)))
                    .build()
                add(EditedMediaItemSequence.Builder(moving).setIsLooping(true).build())
            }
            add(EditedMediaItemSequence.Builder(song).build())
        }
        val composition = Composition.Builder(sequences)
            .apply {
                if (background != null) {
                    setVideoCompositorSettings(CarrierUnderVideo(Size(width, height)))
                    setEffects(Effects(emptyList(), listOfNotNull(frost?.let(::CardFrostEffect)) + foreground))
                }
            }
            .build()

        val out = newExportFile(context, "mp4")
        val sources = ShareMediaSources(
            songId = session.song.id,
            song = DefaultMediaSourceFactory(songSource),
            web = webSources(context, background?.headers.orEmpty()),
        )
        runTransformer(context, composition, out, sources, quality, fps, job, trackProgress = source == null)
        return out
    } finally {
        // kept in release builds, so a phone where the card goes missing can say why
        frames?.let { Log.i(Tag, "Share video ${width}x$height at $fps fps: ${it.summary()}, ${source?.summary()}") }
        frames?.release()
    }
}


private class VideoBackground(val item: MediaItem, val headers: Map<String, String>)

private suspend fun videoBackground(session: SongShareSession, video: SongBackground.Video): VideoBackground? =
    when (val source = video.source) {
        is ShareVideoSource.Own -> VideoBackground(clippedItem(MediaItem.Builder().setUri(source.uri), source.startMs, source.lengthMs), emptyMap())
        ShareVideoSource.AnimatedCover -> session.animatedCover.found?.let { url ->
            VideoBackground(MediaItem.Builder().setUri(url).setMimeType(canvasMimeType(url)).build(), emptyMap())
        }
        ShareVideoSource.MusicVideo -> session.musicVideo.found?.let { found ->
            val music = freshMusicVideo(found)
            val track = music.trackFor(1080) ?: return@let null
            val item = clippedItem(
                MediaItem.Builder().setUri(track.url).setMimeType(MimeTypes.VIDEO_MP4),
                music.videoMsAt(session.windowStartMs),
                session.windowLengthMs,
            )
            VideoBackground(item, music.streams.headers)
        }
    }

// cropped to fill, moved and zoomed the way the editor shows it, and dimmed toward black
private fun backgroundEffects(session: SongShareSession, dim: Float, width: Int, height: Int) = listOf(
    Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
    // gl's y points up, the editor's points down
    MatrixTransformation {
        Matrix().apply {
            setScale(session.bgZoom, session.bgZoom)
            postTranslate(2f * session.bgOffsetX, -2f * session.bgOffsetY)
        }
    },
    RgbMatrix { _, _ -> floatArrayOf(dim, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 1f) },
)

private fun cardFrost(session: SongShareSession, width: Int, height: Int): CardFrost? {
    val bounds = session.cardBounds?.takeIf { session.hasCard } ?: return null
    val unit = width / ShareWidth.value
    return CardFrost(
        left = bounds.left * width,
        top = bounds.top * height,
        right = bounds.right * width,
        bottom = bounds.bottom * height,
        cornerPx = cardCornerOf(session.kind).value * unit,
        radiusPx = session.cardBlur * MaxCardBlur.value * unit,
    ).takeIf { it.radiusPx >= 0.5f }
}

// the 60 fps clock under a video: a black picture nobody sees, there so the output has a frame for every 60th of a second whatever the video's own rate
private class CarrierUnderVideo(private val size: Size) : VideoCompositorSettings {
    private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
    private val shown = StaticOverlaySettings.Builder().build()

    override fun getOutputSize(inputSizes: List<Size>): Size = size

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings = if (inputId == 0) hidden else shown
}

private fun carrierImage(context: Context): File {
    val file = File(exportDir(context), "carrier.png")
    if (!file.exists()) {
        val black = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        file.outputStream().use { black.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return file
}

private fun exportDir(context: Context): File = File(context.cacheDir, ExportDir).apply { mkdirs() }

// a fresh file for this export; earlier exports are cleared so the cache never fills up with old videos
private fun newExportFile(context: Context, extension: String): File {
    val dir = exportDir(context)
    dir.listFiles()?.filter { it.name != "carrier.png" && System.currentTimeMillis() - it.lastModified() > 60_000L }?.forEach { it.delete() }
    return File(dir, "mochy-${System.currentTimeMillis()}.$extension")
}

private fun webSources(context: Context, headers: Map<String, String>): MediaSource.Factory {
    val http = OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(YouTube.proxy).build()).setDefaultRequestProperties(headers)
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
}

private suspend fun runTransformer(
    context: Context,
    composition: Composition,
    out: File,
    sources: MediaSource.Factory,
    quality: ShareQuality,
    fps: Int,
    job: ShareExportJob,
    trackProgress: Boolean,
) = coroutineScope {
    var transformer: Transformer? = null
    val progress = if (trackProgress) {
        launch {
            val holder = ProgressHolder()
            while (true) {
                delay(150)
                val current = transformer ?: continue
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) job.progress = holder.progress / 100f
            }
        }
    } else {
        null
    }
    try {
        suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    continuation.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    continuation.resumeWithException(exportException)
                }
            }
            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(ShareEncoders.bitrateFor(quality, fps)).build())
                .setEnableFallback(true)
                .build()
            val loaders = DefaultAssetLoaderFactory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                Clock.DEFAULT,
                sources,
                DataSourceBitmapLoader(context),
            )
            val built = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoders)
                .setAssetLoaderFactory(loaders)
                .addListener(listener)
                .build()
            transformer = built
            built.start(composition, out.path)
            continuation.invokeOnCancellation {
                // the gl thread lets go first, so the cancel never waits on a frame the main thread is about to be asked for
                job.cancelled = true
                Handler(Looper.getMainLooper()).post { built.cancel() }
                out.delete()
            }
        }
    } finally {
        progress?.cancel()
    }
}
