package com.mochyy.mochymusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.eq.AudioFxService
import com.mochyy.mochymusic.eq.stage.StageNative
import com.mochyy.mochymusic.eq.stage.StageParams
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 3d sims: hands the song to the native room (docs/notes/stage.md); a surround song changes the channel count, so the room only takes one over from the next song */
@UnstableApi
@Suppress("DEPRECATION")
class StageAudioProcessor(private val service: AudioFxService) : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0
    private var handle = 0L

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    // a direct copy of the input for the rare buffer that isn't direct, since the native side reads memory directly
    private var directCopy: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var pushed: StageParams? = null
    // true while the room only passes the song through, so it can be skipped without the native call
    private var idle = true

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || !StageNative.loaded) return notSet()
        return when (inputAudioFormat.channelCount) {
            2 -> {
                pendingFormat = inputAudioFormat
                inputAudioFormat
            }
            in 3..8 -> if (service.stage.enabled) {
                pendingFormat = inputAudioFormat
                AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
            } else notSet()
            else -> notSet()
        }
    }

    private fun notSet(): AudioProcessor.AudioFormat {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (channels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val params = service.stage
        // skip allocating the c++ room engine (2-7 mb) while 3d stage is off and already settled dry
        if (channels == 2 && idle && !params.enabled) {
            if (handle != 0L) release()
            val out = replaceOutputBuffer(bytes)
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (handle == 0L) {
            handle = StageNative.create(sampleRate, channels)
            pushed = null
            if (handle == 0L) {
                inputBuffer.position(inputBuffer.limit())
                return
            }
        }
        if (params !== pushed) {
            StageNative.setParams(handle, params.values)
            pushed = params
        }
        val frames = bytes / (2 * channels)
        val source = if (inputBuffer.isDirect) inputBuffer else copyToDirect(inputBuffer)
        val out = replaceOutputBuffer(frames * 4)
        idle = StageNative.process(handle, source, source.position(), out, frames)
        out.position(0)
        out.limit(frames * 4)
        inputBuffer.position(inputBuffer.limit())
        if (!idle) StageNative.levels(handle, service.stageLevels)
        else if (!params.enabled && channels == 2) release()
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
        // only spin up the native room engine when stage is enabled or decoding surround
        if (service.stage.enabled || channels > 2) {
            if (handle == 0L) handle = StageNative.create(sampleRate, channels)
            else StageNative.clear(handle)
        } else if (handle != 0L) {
            release()
        }
        pushed = null
        idle = true
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
        if (handle != 0L) StageNative.destroy(handle)
        handle = 0L
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
