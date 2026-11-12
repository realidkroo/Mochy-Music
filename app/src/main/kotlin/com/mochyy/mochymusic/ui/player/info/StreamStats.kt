// StreamStats.kt

package com.mochyy.mochymusic.ui.player.info

import androidx.media3.common.C
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.playback.PlayingFrom
import com.mochyy.mochymusic.playback.StreamDetails
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.playback.StreamInspector
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.playback.audio.SincResampler
import java.util.Locale
import kotlin.math.log2
import kotlin.math.roundToInt

/** one card of stats for nerds: a title and only the lines that have a real value */
data class StatCard(val title: String, val lines: List<Pair<String, String>>)

/** what the effects are doing, read once per sheet frame from audio lab and the settings */
data class EffectsSnapshot(
    val equalizerBands: Int? = null,
    val spatial: Boolean = false,
    val mono: Boolean = false,
    val reverb: Boolean = false,
    val loudness: Boolean = false,
    val volumeCurve: Boolean = false,
    val normalize: Boolean = false,
    val skipSilence: Boolean = false,
    val haptics: Boolean = false,
    val transitions: String? = null,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val hiResWanted: Boolean = false,
    val floatAllowed: Boolean = false,
)

data class StatsInput(
    val mediaId: String,
    val details: StreamDetails,
    val facts: StreamFacts?,
    /** youtube's record of its stream; null when something else plays the song */
    val youtubeFormat: FormatEntity?,
    val from: PlayingFrom?,
    val durationMs: Long,
    val playerVolume: Float,
    val decoderHardware: Boolean?,
    val device: AudioOutputs.Device?,
    val mixer: AudioOutputs.Mixer?,
    /** the phone's own mixer rate, which the speaker and the headphone jack run at */
    val systemMixerRate: Int?,
    val castingTo: String?,
    val effects: EffectsSnapshot,
    /** passthrough's own line, already worded: straight to the dac, or why not */
    val passthrough: Pair<String, String>? = null,
)

/** format, decoder, resampler, dsp, output: the song's road to the speaker, nothing guessed */
fun buildStatCards(input: StatsInput): List<StatCard> = listOf(
    formatCard(input),
    decoderCard(input),
    resamplerCard(input),
    dspCard(input),
    outputCard(input),
).filter { it.lines.isNotEmpty() }

private fun formatCard(input: StatsInput): StatCard {
    val d = input.details
    val codec = input.facts?.codec ?: StreamFacts.codecOf(d.sampleMime, d.codecs)
    val lossless = codec in LosslessCodecs
    val sampleRate = d.sampleRate ?: input.facts?.sampleRate ?: input.youtubeFormat?.sampleRate
    val fileSize = d.fileSizeBytes ?: input.youtubeFormat?.contentLength?.takeIf { it > 0 }
    val statedBitrate = d.bitrate ?: input.youtubeFormat?.bitrate?.takeIf { it > 0 }
    // a flac file says nothing of its bitrate: its size over its length does
    val averageBitrate = if (statedBitrate == null && fileSize != null && input.durationMs > 0) {
        (fileSize * 8_000 / input.durationMs).toInt()
    } else null
    return StatCard(
        title = codec?.let(::formatTitle) ?: "Format",
        lines = listOfNotNull(
            d.sourceBitDepth?.takeIf { lossless }?.let { "Bit depth" to "$it-bit" },
            sampleRate?.let { "Sample rate" to kHz(it) },
            statedBitrate?.let { "Bitrate" to kbps(it) } ?: averageBitrate?.let { "Bitrate" to "~${kbps(it)} average" },
            d.channels?.let { "Channel type" to channelName(it) },
            containerName(d.containerMime)?.let { "Container" to it },
            "Media id" to input.mediaId,
            input.from?.matchedId?.let { "Matched as" to "${input.from.name} #$it" },
            "Volume" to "${(input.playerVolume * 100).roundToInt()}%",
            input.youtubeFormat?.loudnessDb?.let { "Loudness" to String.format(Locale.US, "%+.1f dB", it) },
            fileSize?.let { "File size" to megabytes(it) },
        ),
    )
}

private fun decoderCard(input: StatsInput): StatCard {
    val d = input.details
    return StatCard(
        title = "Decoder",
        lines = listOfNotNull(
            d.decoderName?.let { "Decoder name" to it },
            input.decoderHardware?.let { "Runs on" to if (it) "Hardware" else "Software" },
            d.sampleMime?.let { "Format" to it },
            d.codecs?.takeIf { it.isNotBlank() }?.let { "Codec" to it },
            d.decodedEncoding?.let { "Output" to pcmName(it) },
            d.decodedSampleRate?.let { "Output rate" to kHz(it) },
            d.decodedChannels?.let { "Channels" to channelName(it) },
            d.decoderInitMs?.takeIf { it > 0 }?.let { "Started in" to "$it ms" },
        ),
    )
}

private fun resamplerCard(input: StatsInput): StatCard {
    val d = input.details
    val effects = input.effects
    if (d.resampledFrom != null && d.resampledTo != null) return mochyResamplerCard(d, effects)
    val inRate = d.decodedSampleRate ?: d.sampleRate ?: return StatCard("Resampler", emptyList())
    val outRate = input.mixer?.sampleRate ?: input.systemMixerRate?.takeIf { input.device?.kind in FixedRateKinds && input.castingTo == null }
    val timeStretch = effects.speed != 1f || effects.pitch != 1f
    val resampling = outRate != null && outRate != inRate
    val type = when {
        input.castingTo != null -> "The cast device's own"
        timeStretch -> "Sonic time-stretch, then Android's mixer"
        outRate == null -> "Android's mixer, as needed"
        resampling -> "Android's mixer"
        else -> "None, it plays at its own rate"
    }
    return StatCard(
        title = "Resampler",
        lines = listOfNotNull(
            "Type" to type,
            outRate?.let { "I/O rate" to if (resampling) "${kHz(inRate)} → ${kHz(it)}" else "${kHz(inRate)} in and out" },
            // the filter has to stop at the lower rate's nyquist, half of it
            outRate?.takeIf { resampling }?.let { "Cutoff" to "~${kHz(minOf(inRate, it) / 2)}" },
            line("Quality", "Untouched".takeIf { !resampling && outRate != null && !timeStretch }),
            effects.speed.takeIf { it != 1f }?.let { "Speed" to String.format(Locale.US, "%.2f×", it) },
            effects.pitch.takeIf { it != 1f }?.let { "Pitch" to String.format(Locale.US, "%+.1f semitones", 12 * log2(it)) },
        ),
    )
}

// mochy's own filter did the work, so android's mixer is handed the rate it already runs at
private fun mochyResamplerCard(d: StreamDetails, effects: EffectsSnapshot): StatCard = StatCard(
    title = "Resampler",
    lines = listOfNotNull(
        "Type" to "Mochy hi-fi, windowed sinc (Kaiser)",
        "I/O rate" to "${kHz(d.resampledFrom!!)} → ${kHz(d.resampledTo!!)}",
        d.resamplerPassbandHz?.let { "Passband" to "flat to ${kHz(it.toInt())}" },
        "Cutoff" to kHz(minOf(d.resampledFrom, d.resampledTo) / 2),
        "Quality" to "−${SincResampler.StopbandDb.toInt()} dB stopband, 32-bit float",
        d.resamplerTaps?.let { "Taps" to "$it per sample" },
        "Android's mixer" to "Not resampling, the rates match",
        effects.speed.takeIf { it != 1f }?.let { "Speed" to String.format(Locale.US, "%.2f×", it) },
    ),
)

private fun dspCard(input: StatsInput): StatCard {
    val d = input.details
    val e = input.effects
    val chain = listOfNotNull(
        e.equalizerBands?.let { "Equalizer ($it bands)" },
        "Spatial".takeIf { e.spatial },
        "Mono".takeIf { e.mono },
        "Reverb".takeIf { e.reverb },
        "Loudness".takeIf { e.loudness },
        "Volume curve".takeIf { e.volumeCurve },
        e.transitions,
        "Skip silence".takeIf { e.skipSilence },
        "Haptics".takeIf { e.haptics },
    )
    val floatPath = d.outputEncoding == C.ENCODING_PCM_FLOAT
    return StatCard(
        title = "DSP",
        lines = listOfNotNull(
            d.outputEncoding?.let { "PCM format" to pcmName(it) },
            d.outputSampleRate?.let { "Sample rate" to kHz(it) },
            "Effects" to chain.joinToString(", ").ifEmpty { "None, the sound is untouched" },
            line("Dither", "TPDF, to 16-bit".takeIf { d.dithered }),
            line("Normalization", "On".takeIf { e.normalize }),
            when {
                floatPath -> "Hi-res path" to "32-bit float, straight through"
                e.hiResWanted && !e.floatAllowed -> "Hi-res path" to "Paused while an effect is on"
                else -> null
            },
        ),
    )
}

private fun outputCard(input: StatsInput): StatCard {
    val d = input.details
    val device = input.device
    val frameBytes = (channelCountOf(d.outputChannelConfig) ?: 2) * (bytesPerSample(d.outputEncoding) ?: 2)
    val bufferMs = d.outputBufferBytes?.let { bytes -> d.outputSampleRate?.takeIf { it > 0 }?.let { rate -> bytes * 1000L / (rate.toLong() * frameBytes) } }
    val mixer = input.mixer
    return StatCard(
        title = "Output",
        lines = listOfNotNull(
            input.castingTo?.let { "Casting to" to it },
            device?.let { "Device" to deviceName(it) },
            device?.let { "Route" to routeName(it.kind) },
            device?.takeIf { it.kind == AudioOutputs.Kind.USB || it.kind == AudioOutputs.Kind.HDMI }?.let { dev ->
                listOfNotNull(dev.maxSampleRate?.let(::kHz), dev.bitDepths.maxOrNull()?.let { "$it-bit" })
                    .takeIf { it.isNotEmpty() }?.let { "Takes up to" to it.joinToString(" · ") }
            },
            mixer?.let {
                "DAC mixer" to (if (it.bitPerfect) "Bit-perfect, " else "Locked to the song, ") +
                    listOfNotNull(kHz(it.sampleRate), StreamInspector.bitDepthOf(it.encoding)?.let { b -> "$b-bit" }).joinToString(" ")
            },
            input.passthrough,
            d.outputEncoding?.let { enc ->
                "Audio track" to listOfNotNull(pcmShort(enc), d.outputSampleRate?.let(::kHz), channelCountOf(d.outputChannelConfig)?.let(::channelName)).joinToString(" · ")
            },
            bufferMs?.takeIf { it > 0 }?.let { "Buffer" to "~$it ms" },
            line("Offload", "On".takeIf { d.offload }),
            line("Tunneling", "On".takeIf { d.tunneling }),
            d.underruns.takeIf { it > 0 }?.let { "Underruns" to "$it" },
        ),
    )
}

// a line with nothing to say isn't drawn at all, rather than drawn as "-"
private fun line(label: String, value: String?): Pair<String, String>? = value?.let { label to it }

private val LosslessCodecs = setOf("FLAC", "ALAC", "PCM")
private val FixedRateKinds = setOf(AudioOutputs.Kind.PHONE, AudioOutputs.Kind.WIRED)

// a dot and the codec like the mock (".flac" shown as .Flac), but acronyms said letter by letter stay in capitals
private fun formatTitle(codec: String): String =
    if (codec in setOf("FLAC", "OPUS", "VORBIS", "ALAC")) ".${codec.lowercase().replaceFirstChar { it.uppercase() }}" else ".$codec"

private fun kHz(rate: Int): String {
    val khz = rate / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.${if ((rate % 100) == 0) 1 else 2}f kHz", khz)
}

private fun kbps(bitsPerSecond: Int): String = String.format(Locale.getDefault(), "%,d kbps", (bitsPerSecond + 500) / 1000)

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

private fun channelName(count: Int): String = when (count) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1 surround"
    8 -> "7.1 surround"
    else -> "$count channels"
}

private fun containerName(mime: String?): String? = when (mime?.lowercase()) {
    null -> null
    "audio/webm", "video/webm" -> "WebM"
    "audio/mp4", "video/mp4" -> "MP4"
    "audio/flac" -> "FLAC"
    "audio/ogg" -> "Ogg"
    "audio/mpeg" -> "MP3"
    "audio/wav", "audio/x-wav" -> "WAV"
    "application/x-mpegurl", "application/vnd.apple.mpegurl" -> "HLS"
    "application/dash+xml" -> "DASH"
    else -> mime
}

private fun pcmName(encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_16BIT -> "16-bit integer PCM"
    C.ENCODING_PCM_24BIT -> "24-bit integer PCM"
    C.ENCODING_PCM_32BIT -> "32-bit integer PCM"
    C.ENCODING_PCM_FLOAT -> "32-bit float PCM"
    C.ENCODING_PCM_8BIT -> "8-bit PCM"
    else -> "Encoded, passed through"
}

private fun pcmShort(encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_FLOAT -> "32-bit float"
    else -> StreamInspector.bitDepthOf(encoding)?.let { "$it-bit" } ?: "Encoded"
}

private fun bytesPerSample(encoding: Int?): Int? = when (encoding) {
    C.ENCODING_PCM_16BIT -> 2
    C.ENCODING_PCM_24BIT -> 3
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
    else -> null
}

private fun channelCountOf(channelConfig: Int?): Int? = channelConfig?.let { Integer.bitCount(it).takeIf { n -> n > 0 } }

private fun routeName(kind: AudioOutputs.Kind): String = when (kind) {
    AudioOutputs.Kind.PHONE -> "Built-in speaker"
    AudioOutputs.Kind.WIRED -> "Wired"
    AudioOutputs.Kind.USB -> "USB audio"
    AudioOutputs.Kind.BLUETOOTH -> "Bluetooth"
    AudioOutputs.Kind.HDMI -> "HDMI"
}

internal fun deviceName(device: AudioOutputs.Device): String = device.name.ifBlank {
    when (device.kind) {
        AudioOutputs.Kind.PHONE -> "This phone"
        AudioOutputs.Kind.WIRED -> "Wired headphones"
        AudioOutputs.Kind.USB -> "USB DAC"
        AudioOutputs.Kind.BLUETOOTH -> "Bluetooth device"
        AudioOutputs.Kind.HDMI -> "HDMI"
    }
}
