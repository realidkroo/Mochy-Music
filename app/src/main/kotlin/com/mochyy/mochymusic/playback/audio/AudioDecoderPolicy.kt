// AudioDecoderPolicy.kt

package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import java.util.Locale

/**
 * Decides which sources actually benefit from 32-bit float decoder output and avoids OEM vendor
 * decoders known to distort when configured for float or lossless playback (such as Samsung's
 * c2.sec.*, MediaTek's c2.mtk.*, Qualcomm's c2.qti.*, Exynos/Unisoc vendor FLAC/ALAC decoders).
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
     * couldn't be read from container headers (safe for FFmpeg).
     *
     * 16-bit lossless files are already bit-exact as 16-bit PCM, and lossy codecs carry no 24-bit
     * integer depth — requesting float for those triggers decoder distortion and unclamped
     * inter-sample peaks without any fidelity gain.
     */
    fun shouldRequestFloat(format: Format?): Boolean {
        if (format == null) return false
        val depth = sourceBitDepth(format)
        if (depth != null) return depth > 16
        return format.sampleMimeType == MimeTypes.AUDIO_FLAC || format.sampleMimeType == MimeTypes.AUDIO_ALAC
    }

    /**
     * Stricter variant for platform MediaCodec decoders: requests 32-bit float ONLY when container
     * metadata proves the source is genuinely >16-bit. Unknown-depth streams stay on 16-bit PCM
     * rather than risking 16-bit float distortion on platform/vendor decoders.
     */
    fun shouldRequestFloatForMediaCodec(format: Format?): Boolean {
        val depth = sourceBitDepth(format) ?: return false
        return depth > 16
    }

    fun isStandardSoftwareDecoder(codecName: String): Boolean {
        val lower = codecName.lowercase(Locale.US)
        return lower.startsWith("c2.android.") || lower.startsWith("omx.google.")
    }

    fun isSamsungVendorDecoder(codecName: String): Boolean {
        val lower = codecName.lowercase(Locale.US)
        return lower.startsWith("c2.sec.") || lower.startsWith("omx.sec.")
    }

    fun isVendorLosslessDecoder(codecName: String): Boolean = !isStandardSoftwareDecoder(codecName)

    /**
     * For FLAC and ALAC, prefers standard AOSP (`c2.android.*` / `OMX.google.*`) or bundled FFmpeg
     * decoders over OEM vendor decoders (`c2.sec.*`, `c2.mtk.*`, `c2.qti.*`, `OMX.MTK.*`, etc.),
     * which can distort 16-bit and some 24-bit lossless streams when 32-bit float output is enabled.
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
        val (standard, vendor) = decoders.partition { isStandardSoftwareDecoder(nameOf(it)) }
        if (vendor.isEmpty()) return decoders
        return when {
            // Drop OEM vendor FLAC/ALAC decoders whenever AOSP or bundled FFmpeg can decode the stream,
            // so unsupported sample rates, ALAC, or 32-bit FLAC fall cleanly to FFmpeg rather than a vendor codec
            standard.isNotEmpty() && hasFfmpegFallback -> standard
            standard.isNotEmpty() -> standard + vendor
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
        for (csd in initializationData) {
            parseFlacCsdBitDepth(csd)?.let { return it }
        }
        return null
    }

    private fun parseFlacCsdBitDepth(csd: ByteArray): Int? {
        if (csd.size >= 42 &&
            csd[0] == 'f'.code.toByte() &&
            csd[1] == 'L'.code.toByte() &&
            csd[2] == 'a'.code.toByte() &&
            csd[3] == 'C'.code.toByte()
        ) {
            var pos = 4
            while (pos + 4 <= csd.size) {
                val header = csd[pos].toInt() and 0xFF
                val isLast = (header and 0x80) != 0
                val blockType = header and 0x7F
                val blockLen = ((csd[pos + 1].toInt() and 0xFF) shl 16) or
                    ((csd[pos + 2].toInt() and 0xFF) shl 8) or
                    (csd[pos + 3].toInt() and 0xFF)
                val bodyStart = pos + 4
                if (blockType == 0 && blockLen >= 34 && bodyStart + 14 <= csd.size) {
                    return streamInfoBitDepth(csd, bodyStart)
                }
                if (isLast || blockLen < 0 || bodyStart + blockLen > csd.size) break
                pos = bodyStart + blockLen
            }
        }
        // 38-byte metadata block header (type 0 STREAMINFO, length 34) without "fLaC" magic
        if (csd.size >= 38 && (csd[0].toInt() and 0x7F) == 0) {
            val blockLen = ((csd[1].toInt() and 0xFF) shl 16) or
                ((csd[2].toInt() and 0xFF) shl 8) or
                (csd[3].toInt() and 0xFF)
            if (blockLen == 34) {
                return streamInfoBitDepth(csd, 4)
            }
        }
        // Raw 34-byte STREAMINFO body
        if (csd.size == 34) {
            return streamInfoBitDepth(csd, 0)
        }
        return null
    }

    private fun streamInfoBitDepth(csd: ByteArray, streamInfoOffset: Int): Int? {
        val b12 = csd[streamInfoOffset + 12].toInt() and 0xFF
        val b13 = csd[streamInfoOffset + 13].toInt() and 0xFF
        val bitsPerSample = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
        return bitsPerSample.takeIf { it in 4..32 }
    }

    // ALACSpecificConfig (24 bytes): [0..3] frameLength, [4] compatibleVersion (0), [5] bitDepth,
    // [6..8] pb/mb/kb, [9] channels, [10..19] frame/bitrate stats, [20..23] sampleRate.
    // Containers vary: raw 24B (MP4 BoxParser), 28B (4B version/flags + 24B config),
    // 36B (4B size + "alac" + 4B version/flags + 24B config, e.g. MKV), or nested QuickTime frma+alac cookies.
    internal fun alacBitDepth(initializationData: List<ByteArray>): Int? {
        for (csd in initializationData) {
            parseAlacCsdBitDepth(csd)?.let { return it }
        }
        return null
    }

    private fun parseAlacCsdBitDepth(csd: ByteArray): Int? {
        // Scan for boxed "alac" atom (4B "alac" + 4B version/flags + 24B ALACSpecificConfig).
        // Scan from the last matching "alac" backwards so nested QuickTime cookies (outer "alac" wrapping inner "alac") hit the inner config first.
        if (csd.size >= 32) {
            for (i in (csd.size - 32) downTo 0) {
                if (csd[i] == 'a'.code.toByte() &&
                    csd[i + 1] == 'l'.code.toByte() &&
                    csd[i + 2] == 'a'.code.toByte() &&
                    csd[i + 3] == 'c'.code.toByte()
                ) {
                    parseAlacConfigBitDepth(csd, i + 8)?.let { return it }
                }
            }
        }
        // 28-byte MP4/FFmpeg full-box body: 4-byte zero version/flags + 24-byte ALACSpecificConfig
        if (csd.size == 28 && csd[0] == 0.toByte() && csd[1] == 0.toByte() && csd[2] == 0.toByte() && csd[3] == 0.toByte()) {
            parseAlacConfigBitDepth(csd, 4)?.let { return it }
        }
        // Raw 24-byte ALACSpecificConfig
        if (csd.size >= 24) {
            parseAlacConfigBitDepth(csd, 0)?.let { return it }
        }
        return null
    }

    private fun parseAlacConfigBitDepth(csd: ByteArray, base: Int): Int? {
        if (base < 0 || base + 24 > csd.size) return null
        val compatibleVersion = csd[base + 4].toInt() and 0xFF
        if (compatibleVersion != 0) return null
        val bitDepth = csd[base + 5].toInt() and 0xFF
        return bitDepth.takeIf { it in ValidAlacBitDepths }
    }

    private val ValidAlacBitDepths = setOf(16, 20, 24, 32)
}
