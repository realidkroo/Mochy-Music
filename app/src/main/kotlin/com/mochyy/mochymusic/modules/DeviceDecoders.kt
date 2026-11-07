// DeviceDecoders.kt

package com.mochyy.mochymusic.modules

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.mochyy.mochymusic.module.SourceStream
import java.util.concurrent.ConcurrentHashMap

/** whether a module's stream can be decoded here, by the phone or our ffmpeg; one nothing takes plays on in silence, so it's passed over (docs/notes/audio-output.md) */
@OptIn(UnstableApi::class)
object DeviceDecoders {
    private val known = ConcurrentHashMap<String, Boolean>()

    fun canPlay(stream: SourceStream): Boolean = canPlay(stream.codec, stream.sampleRateHz ?: 0)

    /** [codec] ("FLAC", "ALAC"...) at [sampleRateHz], or at any rate when 0 */
    fun canPlay(codec: String?, sampleRateHz: Int = 0): Boolean {
        val mimes = when (codec?.uppercase()) {
            "FLAC" -> listOf("audio/flac")
            "ALAC" -> listOf("audio/alac")
            // media3 plays atmos on a plain e-ac-3 decoder too, it just folds the height channels into the bed
            "DOLBY ATMOS" -> listOf("audio/eac3-joc", "audio/eac3")
            "E-AC-3" -> listOf("audio/eac3")
            "AC-3" -> listOf("audio/ac3")
            // AAC and the rest: every phone decodes them
            else -> return true
        }
        return mimes.any { mime -> known.getOrPut("$mime@$sampleRateHz") { hasDecoder(mime, sampleRateHz) || ffmpegDecodes(mime) } }
    }

    /** a decoder for [mime] that takes [sampleRateHz] (any rate when 0); true when it can't be told */
    private fun hasDecoder(mime: String, sampleRateHz: Int): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                (
                    sampleRateHz <= 0 ||
                        runCatching {
                            info.getCapabilitiesForType(mime).audioCapabilities?.isSampleRateSupported(sampleRateHz) != false
                        }.getOrDefault(true)
                    )
        }
    }.getOrDefault(true)

    // ffmpeg takes any sample rate, so only the codec is asked; false if its library didn't load
    private fun ffmpegDecodes(mime: String): Boolean =
        runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)
}
