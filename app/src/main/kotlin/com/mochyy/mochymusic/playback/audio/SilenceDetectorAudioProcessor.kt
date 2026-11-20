// SilenceDetectorAudioProcessor.kt

package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
@Suppress("DEPRECATION")
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
) : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    // what configure asked for, taken up at the next flush: until then the audio still arriving is in the last song's format
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    // kept and reused: getOutput hands outputBuffer back as empty, so sizing against it made a new direct buffer every time
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    var instantModeEnabled: Boolean = false

    @Volatile
    private var consecutiveSilentFrames: Long = 0

    @Volatile
    private var inSilence: Boolean = false

    private var notifiedThisSilence = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun detectSilence(inputBuffer: ByteBuffer) {

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                val sampleValue = abs(inputBuffer.getShort(sampleIndex).toInt())
                if (sampleValue > framePeak) {
                    framePeak = sampleValue
                }
            }

            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                val silentDurationUs = (consecutiveSilentFrames * 1_000_000L) / sampleRate
                if (silentDurationUs >= minSilenceDurationUs) {
                    inSilence = true
                    if (!notifiedThisSilence) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                clearSilenceState()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() {
        clearSilenceState()
    }

    fun isCurrentlySilent(): Boolean = inSilence

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        clearSilenceState()
        if (pendingFormat != AudioProcessor.AudioFormat.NOT_SET) {
            sampleRate = pendingFormat.sampleRate
            channelCount = pendingFormat.channelCount
            encoding = pendingFormat.encoding
        }
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
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

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
