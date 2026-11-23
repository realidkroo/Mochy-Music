package com.mochyy.mochymusic.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.mochyy.mochymusic.eq.audio.LoudnessAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioNormalizationPolicyTest {

    @Test
    fun `negative gain attenuates linearly in sink without boost`() {
        val fx = AudioFxService()
        fx.setTrackNormalizationDb(-6f)
        assertEquals(0.5012f, fx.trackNormalizationLinear, 0.01f)
        assertEquals(0f, fx.trackNormalizationBoostDb, 0.001f)
    }

    @Test
    fun `positive gain boosts cleanly in processor without sink attenuation`() {
        val fx = AudioFxService()
        fx.setTrackNormalizationDb(2.5f)
        assertEquals(1f, fx.trackNormalizationLinear, 0.001f)
        assertEquals(2.5f, fx.trackNormalizationBoostDb, 0.001f)
    }

    @Test
    fun `extreme and invalid gains are clamped or reset safely`() {
        val fx = AudioFxService()
        fx.setTrackNormalizationDb(12f)
        assertEquals(3f, fx.trackNormalizationBoostDb, 0.001f)

        fx.setTrackNormalizationDb(-40f)
        assertEquals(0.1778f, fx.trackNormalizationLinear, 0.01f)
        assertEquals(0f, fx.trackNormalizationBoostDb, 0.001f)

        fx.setTrackNormalizationDb(Float.NaN)
        assertEquals(1f, fx.trackNormalizationLinear, 0.001f)
        assertEquals(0f, fx.trackNormalizationBoostDb, 0.001f)
    }

    @Test
    fun `loudness processor applies in-app boost when normalization is on and loudness fx is off`() {
        val fx = AudioFxService().apply {
            normalize = true
            setTrackNormalizationDb(3f)
        }
        val processor = LoudnessAudioProcessor(fx)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val frames = 128
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        val sample: Short = 4000
        repeat(frames) {
            input.putShort(sample)
            input.putShort(sample)
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.getOutput()
        assertEquals(frames * 4, out.remaining())

        // +3 dB is ~1.4125x -> ~5650
        var lastL = 0
        repeat(frames) {
            lastL = out.getShort().toInt()
            out.getShort()
        }
        assertTrue("Expected boosted sample around 5650, got $lastL", abs(lastL - 5650) < 120)
    }

    @Test
    fun `loudness processor passes audio untouched when normalization is disabled`() {
        val fx = AudioFxService().apply {
            normalize = false
            setTrackNormalizationDb(3f)
        }
        val processor = LoudnessAudioProcessor(fx)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        val expected = shortArrayOf(1200, -1200, 3400, -3400, 5600, -5600, 7800, -7800)
        expected.forEach { input.putShort(it) }
        input.flip()

        processor.queueInput(input)
        val out = processor.getOutput()
        expected.forEach { exp ->
            assertEquals(exp, out.getShort())
        }
    }
}
