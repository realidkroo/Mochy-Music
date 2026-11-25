// MixerAudioProcessor.kt

package com.mochyy.mochymusic.eq.mixer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** the mixer in one player's chain: the native engine for this player's rate and channels, or a straight wire while the mixer is off */
@UnstableApi
@Suppress("DEPRECATION")
class MixerAudioProcessor(private val store: MixerStore) : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0
    private var handle = 0L

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    // the native side reads memory directly, so the rare buffer that isn't direct is copied into this first
    private var directCopy: ByteBuffer = EMPTY_BUFFER
    private var frameCopy: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..MaxChannels) {
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (channels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        // off: no engine is kept, the song goes through untouched and the library never loads
        if (!store.active) {
            if (handle != 0L) release()
            val out = replaceOutputBuffer(bytes)
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (handle == 0L) {
            handle = MixerNative.create(sampleRate, channels)
            if (handle == 0L) {
                val out = replaceOutputBuffer(bytes)
                out.put(inputBuffer)
                out.flip()
                return
            }
        }
        val frames = bytes / (2 * channels)
        val source = if (inputBuffer.isDirect) inputBuffer else copyToDirect(inputBuffer)
        val out = replaceOutputBuffer(frames * 2 * channels)
        MixerNative.process(handle, source, source.position(), out, frames)
        out.position(0)
        out.limit(frames * 2 * channels)
        inputBuffer.position(inputBuffer.limit())
    }

    /** an encoded frame on its way to the decoder, for a module that reads the stream itself (an object decoder); playback thread only */
    fun encodedFrame(data: ByteBuffer, timeUs: Long) {
        if (handle == 0L || !store.wantsBitstream) return
        val size = data.remaining()
        if (size <= 0) return
        val source = if (data.isDirect) data else {
            if (frameCopy.capacity() < size) frameCopy = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            frameCopy.clear()
            frameCopy.put(data.duplicate())
            frameCopy.flip()
            frameCopy
        }
        MixerNative.bitstream(handle, source, source.position(), size, timeUs)
    }

    private fun copyToDirect(input: ByteBuffer): ByteBuffer {
        val size = input.remaining()
        if (directCopy.capacity() < size) directCopy = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        directCopy.clear()
        directCopy.put(input.duplicate())
        directCopy.flip()
        return directCopy
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        val format = pendingFormat
        if (format == AudioProcessor.AudioFormat.NOT_SET) return
        if (format.sampleRate != sampleRate || format.channelCount != channels) {
            release()
            sampleRate = format.sampleRate
            channels = format.channelCount
        }
        // a seek or a new song: every delay line and reverb tail starts silent
        if (handle != 0L) MixerNative.reset(handle)
    }

    override fun reset() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        release()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channels = 0
    }

    private fun release() {
        if (handle != 0L) MixerNative.destroy(handle)
        handle = 0L
    }

    companion object {
        /** up to 7.1.4; the engine's lanes stop there */
        const val MaxChannels = 12
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
