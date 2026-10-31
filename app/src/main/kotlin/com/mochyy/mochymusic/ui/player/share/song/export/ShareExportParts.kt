// ShareExportParts.kt

package com.mochyy.mochymusic.ui.player.share.song.export

import android.media.MediaCodecList
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.MutableLongState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

// the song comes in and goes out softly instead of starting and stopping mid-note
private const val FadeInMs = 250L
private const val FadeOutMs = 700L

// what this phone's h264 encoders can take; videos encode on their side, so 9:16 is asked as 16:9
internal object ShareEncoders {
    private val cache = HashMap<Pair<ShareQuality, Int>, Boolean>()

    @Synchronized
    fun canEncode(quality: ShareQuality, fps: Int): Boolean = cache.getOrPut(quality to fps) {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, ignoreCase = true) } &&
                    runCatching {
                        val video = info.getCapabilitiesForType(MimeTypes.VIDEO_H264).videoCapabilities
                        video.areSizeAndRateSupported(quality.height, quality.width, fps.toDouble()) ||
                            video.areSizeAndRateSupported(quality.width, quality.height, fps.toDouble())
                    }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    // 4k when the phone can encode it at all, the smoothest frame rate it can manage at that size
    fun bestVideoQuality(): ShareQuality = if (canEncode(ShareQuality.UltraHd, 30)) ShareQuality.UltraHd else ShareQuality.FullHd

    fun frameRateFor(quality: ShareQuality): Int = if (canEncode(quality, 60)) 60 else 30

    fun bitrateFor(quality: ShareQuality, fps: Int): Int = when (quality) {
        ShareQuality.UltraHd -> if (fps >= 60) 60_000_000 else 45_000_000
        ShareQuality.FullHd -> if (fps >= 60) 20_000_000 else 14_000_000
    }
}

// the song through the app's own stream lookup and cache, every video through plain http
internal class ShareMediaSources(
    private val songId: String,
    private val song: MediaSource.Factory,
    private val web: MediaSource.Factory,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        song.setDrmSessionManagerProvider(drmSessionManagerProvider)
        web.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        song.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        web.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray = (song.supportedTypes.toSet() + web.supportedTypes.toSet()).toIntArray()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (mediaItem.mediaId == songId) song.createMediaSource(mediaItem) else web.createMediaSource(mediaItem)
}

// hands the gl thread each frame of the card, drawn on the main thread for exactly that moment
internal class ExportFrames(
    private val frames: OffscreenFrames,
    private val playhead: MutableLongState,
    private val startMs: Long,
    private val job: ShareExportJob,
    private val totalFrames: Long,
) {
    private val main = Handler(Looper.getMainLooper())
    private var lastPtsUs = -1L
    private var drawn = 0L
    private var failed = 0

    // called on the gl thread; waits in short steps so a cancel always gets through
    fun frameAt(presentationTimeUs: Long): FrameImage? {
        if (job.cancelled) return null
        val done = CountDownLatch(1)
        var result: FrameImage? = null
        main.post {
            frames.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                result = runCatching { draw(presentationTimeUs) }
                    .onFailure {
                        if (failed == 0) Log.e("SongShare", "Couldn't draw the share's frame at $presentationTimeUs us", it)
                        failed++
                    }
                    // the frame before stands in, so the card stays in the video instead of blinking out
                    .getOrElse { frames.lastFrame }
                done.countDown()
            }
        }
        while (!done.await(100, TimeUnit.MILLISECONDS)) {
            if (job.cancelled) return null
        }
        return result
    }

    fun summary(): String = "handed over $drawn frames, $failed failed to draw"

    private suspend fun draw(presentationTimeUs: Long): FrameImage {
        val stepMs = if (lastPtsUs < 0L) 0.0 else (presentationTimeUs - lastPtsUs) / 1000.0
        lastPtsUs = presentationTimeUs
        playhead.longValue = startMs + presentationTimeUs / 1000L
        frames.advance(stepMs)
        val frame = frames.capture()
        drawn++
        job.progress = min(1f, drawn.toFloat() / totalFrames)
        return frame
    }
}

// a short fade at both ends of the clip, so the song doesn't start or stop mid-note
internal class ShareAudioFade(private val lengthMs: Long) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var frame = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val sampleBytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = sampleBytes * channels.coerceAtLeast(1)
        val frames = input.remaining() / frameBytes
        val output = replaceOutputBuffer(frames * frameBytes)
        val fadeIn = (sampleRate * FadeInMs / 1000L).coerceAtLeast(1L)
        val fadeOut = (sampleRate * FadeOutMs / 1000L).coerceAtLeast(1L)
        val total = sampleRate * lengthMs / 1000L
        repeat(frames) {
            val gain = minOf(1f, frame.toFloat() / fadeIn, (total - frame).toFloat() / fadeOut).coerceIn(0f, 1f)
            repeat(channels) {
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(input.float * gain)
                } else {
                    output.putShort((input.short * gain).toInt().toShort())
                }
            }
            frame++
        }
        input.position(input.limit())
        output.flip()
    }

    override fun onFlush() {
        frame = 0L
    }
}
