// FfmpegFallbackRenderer.kt

// it lives in media3's package because ffmpeg's decoder class is only reachable from in here
package androidx.media3.decoder.ffmpeg

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.TraceUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

private const val BufferCount = 16
private const val DefaultInputBufferSize = 960 * 6

typealias FfmpegDecoder = SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>

/** media3's ffmpeg audio renderer rebuilt, so the sink hears which file a song comes from before its decoder starts (the stock one is final) */
@UnstableApi
open class FfmpegFallbackRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
) : DecoderAudioRenderer<FfmpegDecoder>(eventHandler, eventListener, audioSink) {

    /** the file this decoder is about to read, called on the playback thread before the decoder is built */
    protected open fun onSourceFormat(format: Format) {}

    override fun getName() = "FfmpegFallbackRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        val mime = format.sampleMimeType ?: return C.FORMAT_UNSUPPORTED_TYPE
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(mime)) return C.FORMAT_UNSUPPORTED_TYPE
        if (!FfmpegLibrary.supportsFormat(mime)) return C.FORMAT_UNSUPPORTED_SUBTYPE
        if (!sinkTakes(format, C.ENCODING_PCM_16BIT) && !sinkTakes(format, C.ENCODING_PCM_FLOAT)) return C.FORMAT_UNSUPPORTED_SUBTYPE
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return C.FORMAT_UNSUPPORTED_DRM
        return C.FORMAT_HANDLED
    }

    override fun supportsMixedMimeTypeAdaptation() = RendererCapabilities.ADAPTIVE_NOT_SEAMLESS

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FfmpegDecoder {
        // first, since audio pass answers the float question below by whether this file is lossless and deep
        onSourceFormat(format)
        TraceUtil.beginSection("createFfmpegAudioDecoder")
        try {
            val inputSize = format.maxInputSize.takeIf { it != Format.NO_VALUE } ?: DefaultInputBufferSize
            return FfmpegAudioDecoder(format, BufferCount, BufferCount, inputSize, shouldOutputFloat(format))
        } finally {
            TraceUtil.endSection()
        }
    }

    override fun getOutputFormat(decoder: FfmpegDecoder): Format {
        val ffmpeg = decoder as FfmpegAudioDecoder
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(ffmpeg.channelCount)
            .setSampleRate(ffmpeg.sampleRate)
            .setPcmEncoding(ffmpeg.encoding)
            .build()
    }

    // float keeps a 24-bit alac whole, 16 bits would cut it down; media3's own renderer leaves ac-3 at 16 bits and so do we
    private fun shouldOutputFloat(format: Format): Boolean {
        if (!sinkTakes(format, C.ENCODING_PCM_16BIT)) return true
        val floatSupport = getSinkFormatSupport(Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate))
        return floatSupport == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY && format.sampleMimeType != MimeTypes.AUDIO_AC3
    }

    private fun sinkTakes(format: Format, encoding: Int) =
        sinkSupportsFormat(Util.getPcmFormat(encoding, format.channelCount, format.sampleRate))
}
