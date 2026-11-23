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
}
