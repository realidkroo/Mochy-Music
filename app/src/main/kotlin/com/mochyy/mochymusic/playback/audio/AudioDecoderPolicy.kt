// AudioDecoderPolicy.kt

package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import java.util.Locale

/**
 * Decides which sources actually benefit from 32-bit float decoder output and avoids vendor decoders
 * known to distort when configured for float or lossless playback (such as Samsung's c2.sec.flac.decoder).
 */
@UnstableApi
object AudioDecoderPolicy {

    /**
     * Exact bits per sample of a lossless/PCM [format], or null when the format is lossy or its bit
     * depth isn't carried in the container headers.
     */
    fun sourceBitDepth(format: Format?): Int? {
        if (format == null) return null
        bitDepthFromPcmEncoding(format.pcmEncoding)?.let { return it }
        return when (format.sampleMimeType) {
            MimeTypes.AUDIO_FLAC -> flacBitDepth(format.initializationData)
            MimeTypes.AUDIO_ALAC -> alacBitDepth(format.initializationData)
            else -> null
        }
    }

    /**
     * True only when asking the decoder for 32-bit float actually preserves precision beyond 16-bit:
     * >16-bit PCM/lossless sources (20/24/32-bit FLAC, ALAC, WAV), or lossless codecs whose depth
     * couldn't be read from container headers.
     *
     * 16-bit lossless files are already bit-exact as 16-bit PCM, and lossy codecs carry no 24-bit
     * integer depth — requesting float for those triggers vendor decoder distortion (notably on
     * Samsung's built-in decoders) and unclamped inter-sample peaks without any fidelity gain.
     */
    fun shouldRequestFloat(format: Format?): Boolean {
        if (format == null) return false
        val depth = sourceBitDepth(format)
        if (depth != null) return depth > 16
        return format.sampleMimeType == MimeTypes.AUDIO_FLAC || format.sampleMimeType == MimeTypes.AUDIO_ALAC
    }

    fun isSamsungVendorDecoder(codecName: String): Boolean {
        val lower = codecName.lowercase(Locale.US)
        return lower.startsWith("c2.sec.") || lower.startsWith("omx.sec.")
    }

    /**
     * For FLAC and ALAC, prefers standard AOSP / FFmpeg decoders over Samsung's vendor decoders
     * (`c2.sec.*` / `OMX.SEC.*`), which can output distorted audio on 16-bit and some 24-bit files
     * when hi-res float output is enabled.
     */
    fun selectDecoders(
        mimeType: String,
        decoders: List<MediaCodecInfo>,
        hasFfmpegFallback: Boolean,
    ): List<MediaCodecInfo> = selectDecodersByName(mimeType, decoders, hasFfmpegFallback) { it.name }

    internal fun <T> selectDecodersByName(
        mimeType: String,
        decoders: List<T>,
        hasFfmpegFallback: Boolean,
        nameOf: (T) -> String,
    ): List<T> {
        if (mimeType != MimeTypes.AUDIO_FLAC && mimeType != MimeTypes.AUDIO_ALAC) return decoders
        val (samsung, others) = decoders.partition { isSamsungVendorDecoder(nameOf(it)) }
        if (samsung.isEmpty()) return decoders
        return when {
            // Drop Samsung's vendor FLAC/ALAC decoder when another decoder or bundled FFmpeg can take it,
            // so unsupported sample rates or 32-bit FLAC fall cleanly to FFmpeg rather than back to c2.sec.*
            others.isNotEmpty() && hasFfmpegFallback -> others
            others.isNotEmpty() -> others + samsung
            hasFfmpegFallback -> emptyList()
            else -> decoders
        }
    }

    private fun bitDepthFromPcmEncoding(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    // STREAMINFO layout: bytes 0..9 block/frame sizes, bytes 10..12 top 20 bits sampleRate,
    // next 3 bits (channels - 1), next 5 bits (bitsPerSample - 1) spanning low 1 bit of byte 12 and top 4 bits of byte 13
    internal fun flacBitDepth(initializationData: List<ByteArray>): Int? {
        val csd = initializationData.firstOrNull() ?: return null
        val streamInfoOffset = when {
            csd.size >= 42 &&
                csd[0] == 'f'.code.toByte() &&
                csd[1] == 'L'.code.toByte() &&
                csd[2] == 'a'.code.toByte() &&
                csd[3] == 'C'.code.toByte() &&
                (csd[4].toInt() and 0x7F) == 0 -> 8
            csd.size == 34 -> 0
            else -> return null
        }
        val b12 = csd[streamInfoOffset + 12].toInt() and 0xFF
        val b13 = csd[streamInfoOffset + 13].toInt() and 0xFF
        val bitsPerSample = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
        return bitsPerSample.takeIf { it in 4..32 }
    }

    // ALACSpecificConfig (24 bytes): [0..3] frameLength, [4] compatibleVersion, [5] bitDepth
    // Some containers (e.g. MKV) prepend the 12-byte full-box header (size + "alac" + version/flags)
    internal fun alacBitDepth(initializationData: List<ByteArray>): Int? {
        val csd = initializationData.firstOrNull() ?: return null
        val base = when {
            csd.size >= 36 &&
                csd[4] == 'a'.code.toByte() &&
                csd[5] == 'l'.code.toByte() &&
                csd[6] == 'a'.code.toByte() &&
                csd[7] == 'c'.code.toByte() -> 12
            csd.size >= 24 -> 0
            else -> return null
        }
        val bitDepth = csd[base + 5].toInt() and 0xFF
        return bitDepth.takeIf { it in ValidAlacBitDepths }
    }

    private val ValidAlacBitDepths = setOf(16, 20, 24, 32)
}
