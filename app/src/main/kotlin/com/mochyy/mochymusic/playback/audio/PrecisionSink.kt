// PrecisionSink.kt

package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.mochyy.mochymusic.playback.StreamInspector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** decides per song what reaches the track: float or dithered 16-bit, at the song's rate or the output's (docs/notes/audio-output.md) */
@UnstableApi
class PrecisionSink(
    delegate: AudioSink,
    private val outputs: AudioOutputs,
    private val inspector: StreamInspector?,
) : ForwardingAudioSink(delegate) {

    /** the file format being decoded, set by the audio renderer before it asks the sink about float */
    @Volatile
    var sourceFormat: Format? = null

    private var inputFormat: Format? = null
    private var bufferSize = 0
    private var outputChannels: IntArray? = null
    private var configuredAllowed = false
    private var configuredTarget: Int? = null
    private var configuredCap: Int? = null
    private var handedFormat: Format? = null

    /** true while buffers are reworked here instead of going straight down */
    private var processing = false
    private var outEncoding = C.ENCODING_PCM_16BIT
    private var resampler: SincResampler? = null

    // a reworked buffer the sink hasn't taken all of yet, and the decoder buffer it came from
    private var pending: ByteBuffer? = null
    private var pendingSource: ByteBuffer? = null

    // scratch kept between buffers, so the playback thread doesn't allocate for every one
    private var planes = Array(2) { FloatArray(0) }
    private var resampled = Array(2) { FloatArray(0) }
    private var out: ByteBuffer? = null
    private var dither = 0x2545F491

    // the decoder is asked for float only when float would make it all the way to the track AND the source actually has >16-bit depth;
    // 16-bit lossless and lossy sources stay 16-bit from the decoder so vendor decoders (like samsung's) don't distort on float
    override fun getFormatSupport(format: Format): Int {
        if (format.pcmEncoding == C.ENCODING_PCM_FLOAT && MimeTypes.AUDIO_RAW == format.sampleMimeType &&
            (!outputs.floatAllowed || format.channelCount > 2 || !AudioDecoderPolicy.shouldRequestFloat(sourceFormat))
        ) {
            return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
        return super.getFormatSupport(format)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        this.inputFormat = inputFormat
        this.bufferSize = specifiedBufferSize
        this.outputChannels = outputChannels
        handedFormat = null
        inspector?.onSinkConfigured(inputFormat)
        applyConfiguration()
    }

    private fun applyConfiguration() {
        val format = inputFormat ?: return
        configuredAllowed = outputs.floatAllowed
        configuredTarget = outputs.fixedOutputRate
        configuredCap = outputs.effectRateCap
        val encoding = format.pcmEncoding
        val workable = MimeTypes.AUDIO_RAW == format.sampleMimeType && encoding in Workable && format.channelCount > 0
        val hiRes = workable && Util.isEncodingHighResolutionPcm(encoding)
        val capped = configuredCap?.takeIf { format.sampleRate > it }
        val target = listOfNotNull(configuredTarget, capped).minOrNull()?.takeIf { workable && it != format.sampleRate }
        val nextResampler = target?.let {
            resampler?.takeIf { r -> r.inRate == format.sampleRate && r.outRate == it && r.channels == format.channelCount }
                ?: SincResampler.create(format.sampleRate, it, format.channelCount)
        }
        val reworked = nextResampler != null || hiRes
        val nextEncoding = when {
            !reworked -> encoding
            configuredAllowed -> C.ENCODING_PCM_FLOAT
            else -> C.ENCODING_PCM_16BIT
        }
        val handed = if (reworked) {
            format.buildUpon()
                .setPcmEncoding(nextEncoding)
                .setSampleRate(nextResampler?.outRate ?: format.sampleRate)
                .build()
        } else format
        inspector?.onProcessing(nextResampler, dithered = reworked && nextEncoding == C.ENCODING_PCM_16BIT)
        // the same decision again (an effect switched on while a 16-bit song plays): nothing to reopen
        if (handed == handedFormat && nextResampler === resampler) return

        processing = reworked
        outEncoding = nextEncoding
        if (nextResampler !== resampler) nextResampler?.reset()
        resampler = nextResampler
        pending = null
        pendingSource = null
        handedFormat = handed
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
            outputs.prepareMixer(handed.sampleRate, outputChannels?.size ?: format.channelCount, handed.pcmEncoding)
        }
        super.configure(handed, bufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // an effect or the output changed mid-song: the sink drains the old track and reopens, a moment's gap
        if (pending == null && inputFormat != null &&
            (configuredAllowed != outputs.floatAllowed || configuredTarget != outputs.fixedOutputRate || configuredCap != outputs.effectRateCap)
        ) {
            applyConfiguration()
        }
        if (!processing) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        if (pending == null || pendingSource !== buffer) {
            pending = rework(buffer, inputFormat!!)
            pendingSource = buffer
            buffer.position(buffer.limit())
        }
        val reworked = pending!!
        // the resampler can hold a short buffer back whole while its filter fills
        val done = !reworked.hasRemaining() || super.handleBuffer(reworked, presentationTimeUs, encodedAccessUnitCount)
        if (done) {
            pending = null
            pendingSource = null
        }
        return done
    }

    override fun playToEndOfStream() {
        pending?.let { reworked ->
            if (!reworked.hasRemaining() || super.handleBuffer(reworked, C.TIME_UNSET, 1)) {
                pending = null
                pendingSource = null
            } else {
                return
            }
        }
        super.playToEndOfStream()
    }

    override fun isEnded(): Boolean = pending == null && super.isEnded()

    override fun flush() {
        pending = null
        pendingSource = null
        resampler?.reset()
        super.flush()
    }

    override fun reset() {
        pending = null
        pendingSource = null
        resampler?.reset()
        super.reset()
    }

    // decoder pcm to float planes, through the resampler when there is one, then float or dithered 16-bit
    private fun rework(source: ByteBuffer, format: Format): ByteBuffer {
        val channels = format.channelCount
        val input = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = bytesPerSample(format.pcmEncoding) * channels
        val frames = input.remaining() / frameBytes
        ensurePlanes(channels, frames)
        readPlanes(input, format.pcmEncoding, channels, frames)

        val r = resampler
        var data = planes
        var count = frames
        if (r != null) {
            ensureResampled(channels, r.maxOutputFrames(frames))
            count = r.process(planes, frames, resampled)
            data = resampled
        }

        val bytes = count * channels * if (outEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val target = out?.takeIf { it.capacity() >= bytes }
            ?: ByteBuffer.allocateDirect(bytes + bytes / 4).order(ByteOrder.LITTLE_ENDIAN).also { out = it }
        target.clear()
        if (outEncoding == C.ENCODING_PCM_FLOAT) {
            for (i in 0 until count) for (c in 0 until channels) target.putFloat(clampFloat(data[c][i]))
        } else {
            for (i in 0 until count) for (c in 0 until channels) target.putShort(ditherTo16(data[c][i]))
        }
        target.flip()
        return target
    }

    private fun readPlanes(input: ByteBuffer, encoding: Int, channels: Int, frames: Int) {
        val start = input.position()
        when (encoding) {
            C.ENCODING_PCM_16BIT -> for (i in 0 until frames) for (c in 0 until channels) {
                planes[c][i] = input.getShort(start + (i * channels + c) * 2) / 32768f
            }
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> for (i in 0 until frames) for (c in 0 until channels) {
                planes[c][i] = java.lang.Short.reverseBytes(input.getShort(start + (i * channels + c) * 2)) / 32768f
            }
            C.ENCODING_PCM_24BIT -> for (i in 0 until frames) for (c in 0 until channels) {
                val at = start + (i * channels + c) * 3
                val value = (input.get(at).toInt() and 0xFF) or ((input.get(at + 1).toInt() and 0xFF) shl 8) or (input.get(at + 2).toInt() shl 16)
                planes[c][i] = value / 8388608f
            }
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> for (i in 0 until frames) for (c in 0 until channels) {
                val at = start + (i * channels + c) * 3
                val value = (input.get(at).toInt() shl 16) or ((input.get(at + 1).toInt() and 0xFF) shl 8) or (input.get(at + 2).toInt() and 0xFF)
                planes[c][i] = value / 8388608f
            }
            C.ENCODING_PCM_32BIT -> for (i in 0 until frames) for (c in 0 until channels) {
                planes[c][i] = (input.getInt(start + (i * channels + c) * 4) / 2147483648.0).toFloat()
            }
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> for (i in 0 until frames) for (c in 0 until channels) {
                planes[c][i] = (Integer.reverseBytes(input.getInt(start + (i * channels + c) * 4)) / 2147483648.0).toFloat()
            }
            else -> for (i in 0 until frames) for (c in 0 until channels) {
                planes[c][i] = input.getFloat(start + (i * channels + c) * 4)
            }
        }
    }

    // keeps valid 24-bit samples bit-exact while stopping non-finite values or resampler peaks above 1.0 from distorting the track
    private fun clampFloat(sample: Float): Float = when {
        !sample.isFinite() -> 0f
        sample > 1f -> 1f
        sample < -1f -> -1f
        else -> sample
    }

    // tpdf dither: two random halves of a step added before rounding, soft-clamped first so dolby float peaks above 1.0 never crackle
    private fun ditherTo16(sample: Float): Short {
        dither = dither xor (dither shl 13)
        dither = dither xor (dither ushr 17)
        dither = dither xor (dither shl 5)
        val a = (dither and 0xFFFF) / 65536f
        val b = ((dither ushr 16) and 0xFFFF) / 65536f
        val clamped = when {
            !sample.isFinite() -> 0f
            sample > 0.98f -> 0.98f + 0.02f * kotlin.math.tanh((sample - 0.98f) / 0.02f)
            sample < -0.98f -> -0.98f + 0.02f * kotlin.math.tanh((sample + 0.98f) / 0.02f)
            else -> sample
        }
        val value = (clamped * 32768f + a - b).roundToInt()
        return value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun ensurePlanes(channels: Int, frames: Int) {
        if (planes.size != channels || planes[0].size < frames) planes = Array(channels) { FloatArray(frames + frames / 4) }
    }

    private fun ensureResampled(channels: Int, frames: Int) {
        if (resampled.size != channels || resampled[0].size < frames) resampled = Array(channels) { FloatArray(frames + frames / 4) }
    }

    private companion object {
        val Workable = setOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN,
            C.ENCODING_PCM_FLOAT,
        )

        fun bytesPerSample(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            else -> 4
        }
    }
}
