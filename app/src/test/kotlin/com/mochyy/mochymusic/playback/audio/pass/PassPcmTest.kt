package com.mochyy.mochymusic.playback.audio.pass

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** checks pcm container packing for audio pass, including non-finite decoder samples */
class PassPcmTest {

    @Test
    fun `float non-finite samples turn into silence instead of crashing`() {
        val input = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        input.putFloat(Float.NaN)
        input.putFloat(Float.POSITIVE_INFINITY)
        input.putFloat(Float.NEGATIVE_INFINITY)
        input.putFloat(0.5f)
        input.flip()

        val output = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        val exact = PassPcm.convert(input, C.ENCODING_PCM_FLOAT, channels = 2, sourceBits = 24, outBits = 32, output = output, muted = false)
        output.flip()

        assertTrue(exact)
        assertEquals(0, output.int)
        assertEquals(0, output.int)
        assertEquals(0, output.int)
        assertEquals(0x40000000, output.int)
    }

    @Test
    fun `float packing flags non-integer depth steps as not bit-exact`() {
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        input.putFloat(0.1234567f)
        input.putFloat(-0.7654321f)
        input.flip()

        val output = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        val exact = PassPcm.convert(input, C.ENCODING_PCM_FLOAT, channels = 2, sourceBits = 16, outBits = 16, output = output, muted = false)
        assertFalse(exact)
    }

    @Test
    fun `16-bit pcm packs cleanly into 24-bit and mutes to zero when asked`() {
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(0x1234)
        input.putShort((-0x1234).toShort())
        input.flip()

        val output = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(PassPcm.convert(input, C.ENCODING_PCM_16BIT, channels = 2, sourceBits = 16, outBits = 24, output = output, muted = false))
        output.flip()
        assertEquals(0x00, output.get().toInt() and 0xff)
        assertEquals(0x34, output.get().toInt() and 0xff)
        assertEquals(0x12, output.get().toInt() and 0xff)

        input.rewind()
        output.clear()
        assertTrue(PassPcm.convert(input, C.ENCODING_PCM_16BIT, channels = 2, sourceBits = 16, outBits = 24, output = output, muted = true))
        output.flip()
        repeat(6) { assertEquals(0, output.get().toInt()) }
    }
}
