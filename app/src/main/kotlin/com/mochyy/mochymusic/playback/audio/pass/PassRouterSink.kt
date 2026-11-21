// PassRouterSink.kt

package com.mochyy.mochymusic.playback.audio.pass

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import timber.log.Timber
import java.nio.ByteBuffer

/** sends each song either the normal way or to audio pass's exclusive output, and hands over between them without a song starting under another */
@UnstableApi
class PassRouterSink(
    private val normal: AudioSink,
    private val exclusive: PassSink,
    private val pass: AudioPass,
) : AudioSink {

    /** the file the playing song was decoded from, set by the audio renderer just before its decoder starts */
    @Volatile var sourceFormat: Format? = null

    private var active: AudioSink = normal

    // the sink that played the last song and is playing out what it holds before the next one writes
    private var retiring: AudioSink? = null
    private var pendingConfigure = false
    // captured when configure runs so gapless pre-buffering of the next song never alters the playing song's route
    private var configuredSource: Format? = null
    private var lastFormat: Format? = null
    private var lastBufferSize = 0
    private var lastChannels: IntArray? = null
    private var decidedAt = -1
    private var playing = false

    override fun getFormatSupport(format: Format): Int {
        // a hi-res song is asked for as float while it could go exclusive: 24 bits fit a float whole
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_FLOAT && pass.wantsFloat(sourceFormat)) {
            return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
        return normal.getFormatSupport(format)
    }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        lastFormat = inputFormat
        lastBufferSize = specifiedBufferSize
        lastChannels = outputChannels
        configuredSource = sourceFormat
        route(inputFormat, fresh = true)
    }

    /** decides where this format goes now; a new song always configures its sink, a mid-song check only when the answer changed */
    private fun route(format: Format, fresh: Boolean) {
        decidedAt = pass.generation
        val identity = lastChannels?.let { map -> map.indices.all { map[it] == it } } ?: true
        val target = if (identity && exclusive.canTake(format, configuredSource)) exclusive else normal
        if (target !== active) {
            // a handover still going the other way is cut short: that sink is needed again
            if (retiring === target) target.reset()
            // wait until the old sink finishes and lets go of the dac before configuring the new one, or mixer/usb setup collides
            retiring = active
            active = target
            pendingConfigure = true
        } else if (fresh) {
            if (retiring != null || (active !== exclusive && pass.holdsNormal())) {
                pendingConfigure = true
            } else {
                pendingConfigure = false
                configureSink(target, format)
            }
        }
    }

    private fun configureSink(target: AudioSink, format: Format) {
        if (target === exclusive) exclusive.configure(format, configuredSource) else normal.configure(format, lastBufferSize, lastChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // an effect, the switch or the dac changed mid-song: the song carries on wherever it may go now
        if (decidedAt != pass.generation && retiring == null && !pendingConfigure) lastFormat?.let { route(it, fresh = false) }
        if (!prepareActive()) return false
        if (active !== exclusive) {
            return active.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        return try {
            exclusive.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        } catch (failure: PassFailure) {
            fallBack(failure)
            false
        }
    }

    // waits for any retiring sink and usb driver hand-back, then configures and starts the new active sink cleanly
    private fun prepareActive(): Boolean {
        if (!finishRetiring()) return false
        // straight after the direct driver, android hasn't got the dac back yet
        if (active !== exclusive && pass.holdsNormal()) return false
        if (pendingConfigure) {
            val format = lastFormat ?: return true
            pendingConfigure = false
            configureSink(active, format)
            if (playing) {
                try {
                    active.play()
                } catch (failure: PassFailure) {
                    fallBack(failure)
                    return false
                }
            }
        }
        return true
    }

    // true once the old sink has played out and let go of the dac; one that fails while finishing (unplugged, taken over) has nothing left to wait for, and must not stop the next song
    private fun finishRetiring(): Boolean {
        val old = retiring ?: return true
        val ended = try {
            old.playToEndOfStream()
            old.isEnded
        } catch (failure: PassFailure) {
            Timber.tag("AudioPass").i("the old output failed while finishing: ${failure.message}")
            true
        }
        if (!ended) return false
        runCatching { old.reset() }
        retiring = null
        if (!playing || pendingConfigure) return true
        try {
            active.play()
        } catch (failure: PassFailure) {
            fallBack(failure)
        }
        return true
    }

    /** the exclusive output failed: the song goes on the normal way from this buffer, with no error shown */
    private fun fallBack(failure: PassFailure) {
        val format = lastFormat ?: return
        Timber.tag("AudioPass").i("exclusive output stopped: ${failure.message}")
        pass.failed(format, configuredSource, failure.message ?: "")
        exclusive.reset()
        active = normal
        retiring = null
        decidedAt = pass.generation
        // after direct usb lets go, wait until android sees the dac again before opening the normal track
        if (pass.holdsNormal()) {
            pendingConfigure = true
        } else {
            pendingConfigure = false
            normal.configure(format, lastBufferSize, lastChannels)
            if (playing) normal.play()
        }
    }

    override fun playToEndOfStream() {
        if (!prepareActive()) return
        try {
            active.playToEndOfStream()
        } catch (failure: PassFailure) {
            fallBack(failure)
        }
    }

    override fun isEnded(): Boolean = retiring == null && !pendingConfigure && active.isEnded

    override fun hasPendingData(): Boolean = retiring?.hasPendingData() == true || pendingConfigure || active.hasPendingData()

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        if (retiring != null) retiring!!.getCurrentPositionUs(sourceEnded)
        else if (pendingConfigure) AudioSink.CURRENT_POSITION_NOT_SET
        else active.getCurrentPositionUs(sourceEnded)

    override fun play() {
        playing = true
        val old = retiring
        if (old != null) {
            runCatching { old.play() }
        } else if (!pendingConfigure) {
            try {
                active.play()
            } catch (failure: PassFailure) {
                fallBack(failure)
            }
        }
    }

    override fun pause() {
        playing = false
        retiring?.pause()
        active.pause()
    }

    override fun flush() {
        // a seek: whatever was handing over is dropped with the rest
        retiring?.let {
            it.flush()
            it.reset()
        }
        retiring = null
        active.flush()
    }

    override fun reset() {
        retiring = null
        pendingConfigure = false
        configuredSource = null
        normal.reset()
        exclusive.reset()
        active = normal
    }

    override fun release() {
        normal.release()
        exclusive.release()
    }

    override fun handleDiscontinuity() = active.handleDiscontinuity()

    override fun getAudioTrackBufferSizeUs(): Long = active.audioTrackBufferSizeUs

    // the rest reaches both, so whichever plays next is already set up
    override fun setListener(listener: AudioSink.Listener) {
        normal.setListener(listener)
        exclusive.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) = normal.setPlayerId(playerId)
    override fun setClock(clock: Clock) = normal.setClock(clock)
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = normal.setPlaybackParameters(playbackParameters)
    override fun getPlaybackParameters(): PlaybackParameters = normal.playbackParameters
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        normal.skipSilenceEnabled = skipSilenceEnabled
        exclusive.skipSilenceEnabled = skipSilenceEnabled
    }
    override fun getSkipSilenceEnabled(): Boolean = normal.skipSilenceEnabled
    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        normal.setAudioAttributes(audioAttributes)
        exclusive.setAudioAttributes(audioAttributes)
    }
    override fun getAudioAttributes(): AudioAttributes? = normal.audioAttributes
    override fun setAudioSessionId(audioSessionId: Int) = normal.setAudioSessionId(audioSessionId)
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = normal.setAuxEffectInfo(auxEffectInfo)
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = normal.setPreferredDevice(audioDeviceInfo)
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = normal.setOutputStreamOffsetUs(outputStreamOffsetUs)
    override fun enableTunnelingV21() = normal.enableTunnelingV21()
    override fun disableTunneling() = normal.disableTunneling()
    override fun setOffloadMode(offloadMode: Int) = normal.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = normal.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun getFormatOffloadSupport(format: Format) = normal.getFormatOffloadSupport(format)
    override fun setVolume(volume: Float) {
        normal.setVolume(volume)
        exclusive.setVolume(volume)
    }
}
