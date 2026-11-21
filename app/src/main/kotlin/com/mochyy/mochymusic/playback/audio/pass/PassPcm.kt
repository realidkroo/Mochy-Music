// PassPcm.kt

package com.mochyy.mochymusic.playback.audio.pass

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong

/** decoder pcm into the dac's integer container: only the packing changes, never a sample's value, no gain, no dither */
internal object PassPcm {

    /** what the passthrough takes from a decoder: little-endian integers, and float, which is how 24-bit flac comes out */
    val Encodings = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        else -> 4
    }

    /** how many bits the song really has: the file's own depth when the extractor knew it, else what the decoder handed over */
    fun sourceBits(encoding: Int, fileEncoding: Int?): Int = when (fileEncoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> when (encoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_32BIT -> 32
            else -> 24
        }
    }

    /** packs every whole frame of input into output; false when a float sample wasn't a whole step of the song's depth, so it wasn't lossless to begin with */
    fun convert(
        input: ByteBuffer,
        encoding: Int,
        channels: Int,
        sourceBits: Int,
        outBits: Int,
        output: ByteBuffer,
        muted: Boolean,
    ): Boolean {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val inBytes = bytesPerSample(encoding)
        val samples = minOf(input.remaining() / (inBytes * channels), output.remaining() / (outBits / 8 * channels)) * channels
        // float holds a whole step of the song's depth exactly: 24 bits fit a float's mantissa
        val floatScale = (1L shl (minOf(sourceBits, 24) - 1)).toDouble()
        val floatShift = 32 - minOf(sourceBits, 24)
        var exact = true
        repeat(samples) {
            // every sample is lined up at the top of 32 bits, so one writer serves every container
            val sample = when (encoding) {
                C.ENCODING_PCM_16BIT -> input.short.toInt() shl 16
                C.ENCODING_PCM_24BIT -> {
                    val low = input.get().toInt() and 0xff
                    val mid = input.get().toInt() and 0xff
                    val high = input.get().toInt()
                    ((high shl 16) or (mid shl 8) or low) shl 8
                }
                C.ENCODING_PCM_32BIT -> input.int
                else -> {
                    val value = input.float.toDouble()
                    if (!value.isFinite()) {
                        0
                    } else {
                        val step = (value * floatScale).roundToLong().coerceIn(-floatScale.toLong(), floatScale.toLong() - 1)
                        if (exact && abs(value * floatScale - step) > 1e-3) exact = false
                        step.toInt() shl floatShift
                    }
                }
            }
            put(output, if (muted) 0 else sample, outBits)
        }
        return exact
    }

    private fun put(output: ByteBuffer, sample: Int, bits: Int) {
        when (bits) {
            16 -> output.putShort((sample shr 16).toShort())
            24 -> {
                output.put((sample shr 8).toByte())
                output.put((sample shr 16).toByte())
                output.put((sample shr 24).toByte())
            }
            else -> output.putInt(sample)
        }
    }
}
