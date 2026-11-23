package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.mochyy.mochymusic.eq.AudioFxService
import com.mochyy.mochymusic.eq.audio.ReplayGainTags
import com.mochyy.mochymusic.eq.audio.VolumeCurve
import java.nio.ByteBuffer

/**
 * Tells the Crossmix renderer where in its song each buffer sits. Media3 1.7 gives audio
 * processors no timestamps, but every buffer passes through here with one, and the stream offset
 * that turns it into a position in the song arrives here too. The processor runs inside
 * [handleBuffer], so the timestamp it's given is always the one for the audio it's about to see.
 */
@UnstableApi
class CrossmixClockSink(
    delegate: AudioSink,
    private val processor: CrossmixTransitionAudioProcessor,
    private val audioFx: AudioFxService? = null,
    /** this player's song's ReplayGain, applied as part of its volume */
    private val replayGain: ReplayGainTags? = null,
    /** the visualizer's ear: every buffer, with its time, before the processors touch it */
    private val spectrum: AudioSpectrum? = null,
) : ForwardingAudioSink(delegate) {

    private var lastTimestampUs = Long.MIN_VALUE

    // Audio FX's volume curve shapes every volume the player is given (the slider, ducking, fades)
    private var requestedVolume = 1f
    private var appliedCurve: VolumeCurve? = null
    private var appliedReplayGain = 1f
    private var appliedTrackNormalization = 1f

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // a buffer the sink couldn't take yet comes back with the same timestamp, part-consumed;
        // stamping it again would pull the clock back by whatever was already processed
        if (presentationTimeUs != lastTimestampUs) {
            lastTimestampUs = presentationTimeUs
            processor.onInputTimestamp(presentationTimeUs)
            spectrum?.onBuffer(buffer, presentationTimeUs)
        }
        // the curve or the song's normalization changed: the player won't send its (unchanged)
        // volume again
        if (audioFx != null && (
                audioFx.volumeCurve !== appliedCurve ||
                    replayGainFactor() != appliedReplayGain ||
                    trackNormalizationFactor() != appliedTrackNormalization
                )
        ) applyVolume()
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun setVolume(volume: Float) {
        requestedVolume = volume
        applyVolume()
    }

    private fun applyVolume() {
        val curve = audioFx?.volumeCurve
        appliedCurve = curve
        appliedReplayGain = replayGainFactor()
        appliedTrackNormalization = trackNormalizationFactor()
        super.setVolume((curve?.gain(requestedVolume) ?: requestedVolume) * appliedReplayGain * appliedTrackNormalization)
    }

    private fun replayGainFactor(): Float {
        val fx = audioFx ?: return 1f
        return replayGain?.linear(fx.replayGain, fx.normalize) ?: 1f
    }

    private fun trackNormalizationFactor(): Float {
        val fx = audioFx ?: return 1f
        if (!fx.normalize) return 1f
        // a file with its own ReplayGain tags uses those instead of YouTube's loudness metadata
        if (replayGain?.present == true) return 1f
        return fx.trackNormalizationLinear.coerceIn(0.03f, 1f)
    }

    override fun configure(inputFormat: androidx.media3.common.Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        spectrum?.configure(inputFormat)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun flush() {
        lastTimestampUs = Long.MIN_VALUE
        spectrum?.flush()
        super.flush()
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        processor.onStreamOffset(outputStreamOffsetUs)
        spectrum?.onStreamOffset(outputStreamOffsetUs)
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
}
