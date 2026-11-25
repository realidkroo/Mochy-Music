package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** verifies bit-depth detection, float-request gating, and Samsung vendor FLAC/ALAC decoder filtering */
@UnstableApi
class AudioDecoderPolicyTest {

    // builds a minimal 42-byte fLaC + STREAMINFO header with the requested bitsPerSample (4..32)
    private fun flacCsd(bitsPerSample: Int, sampleRate: Int = 44_100, channels: Int = 2): ByteArray {
        val csd = ByteArray(42)
        csd[0] = 'f'.code.toByte()
        csd[1] = 'L'.code.toByte()
        csd[2] = 'a'.code.toByte()
        csd[3] = 'C'.code.toByte()
        csd[4] = 0x80.toByte() // last-metadata-block flag, block type 0 (STREAMINFO)
        csd[7] = 34 // STREAMINFO length
        // bytes 18..21 = offset 8 + 10..13: 20-bit sampleRate | 3-bit (channels - 1) | 5-bit (bitsPerSample - 1) | top 4 bits totalSamples
        val bps = (bitsPerSample - 1) and 0x1F
        val ch = (channels - 1) and 0x07
        csd[18] = ((sampleRate ushr 12) and 0xFF).toByte()
        csd[19] = ((sampleRate ushr 4) and 0xFF).toByte()
        csd[20] = (((sampleRate and 0x0F) shl 4) or (ch shl 1) or (bps ushr 4)).toByte()
        csd[21] = ((bps and 0x0F) shl 4).toByte()
        return csd
    }

    private fun alacCsd(bitDepth: Int, withAtomHeader: Boolean = false): ByteArray {
        val base = if (withAtomHeader) 12 else 0
        val csd = ByteArray(base + 24)
        if (withAtomHeader) {
            csd[4] = 'a'.code.toByte()
            csd[5] = 'l'.code.toByte()
            csd[6] = 'a'.code.toByte()
            csd[7] = 'c'.code.toByte()
        }
        csd[base + 5] = bitDepth.toByte()
        return csd
    }

    @Test
    fun `16-bit flac does not request float from decoder`() {
        val plainFlac = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .build()
        assertEquals(16, AudioDecoderPolicy.sourceBitDepth(plainFlac))
        assertFalse(AudioDecoderPolicy.shouldRequestFloat(plainFlac))

        // DASH/MP4-packaged FLAC carries bit depth in dfLa initializationData with pcmEncoding = NO_VALUE
        val mp4Flac16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .setInitializationData(listOf(flacCsd(16)))
            .build()
        assertEquals(16, AudioDecoderPolicy.sourceBitDepth(mp4Flac16))
        assertFalse(AudioDecoderPolicy.shouldRequestFloat(mp4Flac16))
    }

    @Test
    fun `24-bit and 32-bit flac request float from decoder`() {
        val plainFlac24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setPcmEncoding(C.ENCODING_PCM_24BIT)
            .setSampleRate(96_000)
            .setChannelCount(2)
            .build()
        assertEquals(24, AudioDecoderPolicy.sourceBitDepth(plainFlac24))
        assertTrue(AudioDecoderPolicy.shouldRequestFloat(plainFlac24))

        val mp4Flac24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(96_000)
            .setChannelCount(2)
            .setInitializationData(listOf(flacCsd(24, sampleRate = 96_000)))
            .build()
        assertEquals(24, AudioDecoderPolicy.sourceBitDepth(mp4Flac24))
        assertTrue(AudioDecoderPolicy.shouldRequestFloat(mp4Flac24))

        val flac32 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setPcmEncoding(C.ENCODING_PCM_32BIT)
            .setSampleRate(192_000)
            .setChannelCount(2)
            .build()
        assertEquals(32, AudioDecoderPolicy.sourceBitDepth(flac32))
        assertTrue(AudioDecoderPolicy.shouldRequestFloat(flac32))
    }

    @Test
    fun `alac bit depth is parsed for both raw and boxed config`() {
        val alac16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setInitializationData(listOf(alacCsd(16)))
            .build()
        assertEquals(16, AudioDecoderPolicy.sourceBitDepth(alac16))
        assertFalse(AudioDecoderPolicy.shouldRequestFloat(alac16))

        val alac24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setInitializationData(listOf(alacCsd(24, withAtomHeader = true)))
            .build()
        assertEquals(24, AudioDecoderPolicy.sourceBitDepth(alac24))
        assertTrue(AudioDecoderPolicy.shouldRequestFloat(alac24))

        val alacUnknown = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_ALAC).build()
        assertNull(AudioDecoderPolicy.sourceBitDepth(alacUnknown))
        assertTrue(AudioDecoderPolicy.shouldRequestFloat(alacUnknown))
    }

    @Test
    fun `lossy formats do not request float from decoder`() {
        for (mime in listOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS, MimeTypes.AUDIO_E_AC3)) {
            val format = Format.Builder().setSampleMimeType(mime).setSampleRate(48_000).setChannelCount(2).build()
            assertNull(AudioDecoderPolicy.sourceBitDepth(format))
            assertFalse("expected $mime to stay on 16-bit", AudioDecoderPolicy.shouldRequestFloat(format))
        }
    }

    @Test
    fun `samsung vendor decoders are bypassed for flac and alac when alternatives exist`() {
        val samsungFlac = listOf("c2.sec.flac.decoder", "c2.android.flac.decoder")
        assertEquals(
            listOf("c2.android.flac.decoder"),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_FLAC, samsungFlac, hasFfmpegFallback = true) { it },
        )
        assertEquals(
            listOf("c2.android.flac.decoder", "c2.sec.flac.decoder"),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_FLAC, samsungFlac, hasFfmpegFallback = false) { it },
        )

        val samsungOnlyAlac = listOf("c2.sec.alac.decoder")
        assertEquals(
            emptyList<String>(),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_ALAC, samsungOnlyAlac, hasFfmpegFallback = true) { it },
        )
        assertEquals(
            samsungOnlyAlac,
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_ALAC, samsungOnlyAlac, hasFfmpegFallback = false) { it },
        )

        val nonSamsung = listOf("c2.android.flac.decoder")
        assertEquals(
            nonSamsung,
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_FLAC, nonSamsung, hasFfmpegFallback = true) { it },
        )

        // lossy / dolby codecs are untouched (they never request float anyway)
        val samsungAac = listOf("c2.sec.aac.decoder", "c2.android.aac.decoder")
        assertEquals(
            samsungAac,
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_AAC, samsungAac, hasFfmpegFallback = true) { it },
        )
    }

    @Test
    fun `mediatek qualcomm and other vendor lossless decoders are bypassed for flac and alac`() {
        val mtkOnlyAlac = listOf("c2.mtk.alac.decoder", "OMX.MTK.AUDIO.DECODER.ALAC")
        assertEquals(
            emptyList<String>(),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_ALAC, mtkOnlyAlac, hasFfmpegFallback = true) { it },
        )
        assertEquals(
            mtkOnlyAlac,
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_ALAC, mtkOnlyAlac, hasFfmpegFallback = false) { it },
        )

        val qtiFlac = listOf("c2.qti.flac.decoder", "c2.android.flac.decoder")
        assertEquals(
            listOf("c2.android.flac.decoder"),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_FLAC, qtiFlac, hasFfmpegFallback = true) { it },
        )
        assertEquals(
            listOf("c2.android.flac.decoder", "c2.qti.flac.decoder"),
            AudioDecoderPolicy.selectDecodersByName(MimeTypes.AUDIO_FLAC, qtiFlac, hasFfmpegFallback = false) { it },
        )
    }

    @Test
    fun `28-byte and nested quicktime alac csd plus 38-byte and multi-block flac csd are parsed accurately`() {
        // 28-byte MP4/FFmpeg full-box body: 4 zero bytes (version/flags) + 24-byte ALACSpecificConfig
        val alac28Bit16 = ByteArray(28).apply {
            this[4 + 2] = 0x10 // frameLength = 4096
            this[4 + 4] = 0 // compatibleVersion
            this[4 + 5] = 16 // bitDepth
            this[4 + 9] = 2 // stereo
        }
        val alac28Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setInitializationData(listOf(alac28Bit16))
            .build()
        assertEquals(16, AudioDecoderPolicy.sourceBitDepth(alac28Format))
        assertFalse(AudioDecoderPolicy.shouldRequestFloat(alac28Format))
        assertFalse(AudioDecoderPolicy.shouldRequestFloatForMediaCodec(alac28Format))

        // Nested QuickTime cookie with "frma" prefix before "alac" atom
        val nestedAlac24 = ByteArray(48).apply {
            this[4] = 'f'.code.toByte(); this[5] = 'r'.code.toByte(); this[6] = 'm'.code.toByte(); this[7] = 'a'.code.toByte()
            this[16] = 'a'.code.toByte(); this[17] = 'l'.code.toByte(); this[18] = 'a'.code.toByte(); this[19] = 'c'.code.toByte()
            this[16 + 8 + 5] = 24
        }
        val nestedFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setInitializationData(listOf(nestedAlac24))
            .build()
        assertEquals(24, AudioDecoderPolicy.sourceBitDepth(nestedFormat))
        assertTrue(AudioDecoderPolicy.shouldRequestFloatForMediaCodec(nestedFormat))

        // 38-byte FLAC block header + STREAMINFO without leading "fLaC"
        val flac42 = flacCsd(16)
        val flac38 = flac42.copyOfRange(4, 42)
        val flac38Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setInitializationData(listOf(flac38))
            .build()
        assertEquals(16, AudioDecoderPolicy.sourceBitDepth(flac38Format))
        assertFalse(AudioDecoderPolicy.shouldRequestFloat(flac38Format))

        // Unknown-depth FLAC never requests float from platform MediaCodec
        val unknownFlac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).build()
        assertNull(AudioDecoderPolicy.sourceBitDepth(unknownFlac))
        assertFalse(AudioDecoderPolicy.shouldRequestFloatForMediaCodec(unknownFlac))
    }
}
