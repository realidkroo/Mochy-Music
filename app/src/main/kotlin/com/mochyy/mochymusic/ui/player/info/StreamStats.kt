// StreamStats.kt

package com.mochyy.mochymusic.ui.player.info

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.C
import com.mochyy.mochymusic.R
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

private fun Context.t(@StringRes id: Int, vararg args: Any): String = getString(id, *args)

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
)

/** format, decoder, resampler, dsp, output: the song's road to the speaker, nothing guessed */
fun buildStatCards(context: Context, input: StatsInput): List<StatCard> = listOf(
    formatCard(context, input),
    decoderCard(context, input),
    resamplerCard(context, input),
    dspCard(context, input),
    outputCard(context, input),
).filter { it.lines.isNotEmpty() }

private fun formatCard(c: Context, input: StatsInput): StatCard {
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
        title = codec?.let(::formatTitle) ?: c.t(R.string.pc_stat_format),
        lines = listOfNotNull(
            d.sourceBitDepth?.takeIf { lossless }?.let { c.t(R.string.pc_stat_bit_depth) to "$it-bit" },
            sampleRate?.let { c.t(R.string.pc_stat_sample_rate) to kHz(it) },
            statedBitrate?.let { c.t(R.string.pc_stat_bitrate) to kbps(it) } ?: averageBitrate?.let { c.t(R.string.pc_stat_bitrate) to c.t(R.string.pc_stat_bitrate_average, kbps(it)) },
            d.channels?.let { c.t(R.string.pc_stat_channel_type) to channelName(c, it) },
            containerName(d.containerMime)?.let { c.t(R.string.pc_stat_container) to it },
            c.t(R.string.pc_stat_media_id) to input.mediaId,
            input.from?.matchedId?.let { c.t(R.string.pc_stat_matched_as) to "${input.from.name} #$it" },
            c.t(R.string.pc_stat_volume) to "${(input.playerVolume * 100).roundToInt()}%",
            input.youtubeFormat?.loudnessDb?.let { c.t(R.string.pc_stat_loudness) to String.format(Locale.US, "%+.1f dB", it) },
            fileSize?.let { c.t(R.string.pc_stat_file_size) to megabytes(it) },
        ),
    )
}

private fun decoderCard(c: Context, input: StatsInput): StatCard {
    val d = input.details
    return StatCard(
        title = c.t(R.string.pc_stat_decoder),
        lines = listOfNotNull(
            d.decoderName?.let { c.t(R.string.pc_stat_decoder_name) to it },
            input.decoderHardware?.let { c.t(R.string.pc_stat_runs_on) to c.t(if (it) R.string.pc_stat_hardware else R.string.pc_stat_software) },
            d.sampleMime?.let { c.t(R.string.pc_stat_format) to it },
            d.codecs?.takeIf { it.isNotBlank() }?.let { c.t(R.string.pc_stat_codec) to it },
            d.decodedEncoding?.let { c.t(R.string.pc_stat_output) to pcmName(c, it) },
            d.decodedSampleRate?.let { c.t(R.string.pc_stat_output_rate) to kHz(it) },
            d.decodedChannels?.let { c.t(R.string.pc_stat_channels) to channelName(c, it) },
            d.decoderInitMs?.takeIf { it > 0 }?.let { c.t(R.string.pc_stat_started_in) to "$it ms" },
        ),
    )
}

private fun resamplerCard(c: Context, input: StatsInput): StatCard {
    val d = input.details
    val effects = input.effects
    if (d.resampledFrom != null && d.resampledTo != null) return mochyResamplerCard(c, d, effects)
    val inRate = d.decodedSampleRate ?: d.sampleRate ?: return StatCard(c.t(R.string.pc_stat_resampler), emptyList())
    val outRate = input.mixer?.sampleRate ?: input.systemMixerRate?.takeIf { input.device?.kind in FixedRateKinds && input.castingTo == null }
    val timeStretch = effects.speed != 1f || effects.pitch != 1f
    val resampling = outRate != null && outRate != inRate
    val type = when {
        input.castingTo != null -> c.t(R.string.pc_stat_rs_cast)
        timeStretch -> c.t(R.string.pc_stat_rs_stretch)
        outRate == null -> c.t(R.string.pc_stat_rs_as_needed)
        resampling -> c.t(R.string.pc_stat_rs_android)
        else -> c.t(R.string.pc_stat_rs_none)
    }
    return StatCard(
        title = c.t(R.string.pc_stat_resampler),
        lines = listOfNotNull(
            c.t(R.string.pc_stat_type) to type,
            outRate?.let { c.t(R.string.pc_stat_io_rate) to if (resampling) "${kHz(inRate)} → ${kHz(it)}" else c.t(R.string.pc_stat_in_and_out, kHz(inRate)) },
            // the filter has to stop at the lower rate's nyquist, half of it
            outRate?.takeIf { resampling }?.let { c.t(R.string.pc_stat_cutoff) to "~${kHz(minOf(inRate, it) / 2)}" },
            line(c.t(R.string.pc_stat_quality), c.t(R.string.pc_stat_untouched).takeIf { !resampling && outRate != null && !timeStretch }),
            effects.speed.takeIf { it != 1f }?.let { c.t(R.string.pc_stat_speed) to String.format(Locale.US, "%.2f×", it) },
            effects.pitch.takeIf { it != 1f }?.let { c.t(R.string.pc_stat_pitch) to c.t(R.string.pc_stat_semitones, String.format(Locale.US, "%+.1f", 12 * log2(it))) },
        ),
    )
}

// mochy's own filter did the work, so android's mixer is handed the rate it already runs at
private fun mochyResamplerCard(c: Context, d: StreamDetails, effects: EffectsSnapshot): StatCard = StatCard(
    title = c.t(R.string.pc_stat_resampler),
    lines = listOfNotNull(
        c.t(R.string.pc_stat_type) to c.t(R.string.pc_stat_rs_mochy),
        c.t(R.string.pc_stat_io_rate) to "${kHz(d.resampledFrom!!)} → ${kHz(d.resampledTo!!)}",
        d.resamplerPassbandHz?.let { c.t(R.string.pc_stat_passband) to c.t(R.string.pc_stat_flat_to, kHz(it.toInt())) },
        c.t(R.string.pc_stat_cutoff) to kHz(minOf(d.resampledFrom, d.resampledTo) / 2),
        c.t(R.string.pc_stat_quality) to c.t(R.string.pc_stat_stopband, SincResampler.StopbandDb.toInt()),
        d.resamplerTaps?.let { c.t(R.string.pc_stat_taps) to c.t(R.string.pc_stat_per_sample, it) },
        c.t(R.string.pc_stat_android_mixer) to c.t(R.string.pc_stat_not_resampling),
        effects.speed.takeIf { it != 1f }?.let { c.t(R.string.pc_stat_speed) to String.format(Locale.US, "%.2f×", it) },
    ),
)

private fun dspCard(c: Context, input: StatsInput): StatCard {
    val d = input.details
    val e = input.effects
    val chain = listOfNotNull(
        e.equalizerBands?.let { c.t(R.string.pc_stat_eq_bands, it) },
        c.t(R.string.pc_stat_spatial).takeIf { e.spatial },
        c.t(R.string.pc_stat_mono).takeIf { e.mono },
        c.t(R.string.pc_stat_reverb).takeIf { e.reverb },
        c.t(R.string.pc_stat_loudness).takeIf { e.loudness },
        c.t(R.string.pc_stat_volume_curve).takeIf { e.volumeCurve },
        e.transitions,
        c.t(R.string.pc_stat_skip_silence).takeIf { e.skipSilence },
        c.t(R.string.pc_stat_haptics).takeIf { e.haptics },
    )
    val floatPath = d.outputEncoding == C.ENCODING_PCM_FLOAT
    return StatCard(
        title = c.t(R.string.pc_stat_dsp),
        lines = listOfNotNull(
            d.outputEncoding?.let { c.t(R.string.pc_stat_pcm_format) to pcmName(c, it) },
            d.outputSampleRate?.let { c.t(R.string.pc_stat_sample_rate) to kHz(it) },
            c.t(R.string.pc_stat_effects) to chain.joinToString(", ").ifEmpty { c.t(R.string.pc_stat_effects_none) },
            line(c.t(R.string.pc_stat_dither), c.t(R.string.pc_stat_dither_tpdf).takeIf { d.dithered }),
            line(c.t(R.string.pc_stat_normalization), c.t(R.string.pc_stat_on).takeIf { e.normalize }),
            when {
                floatPath -> c.t(R.string.pc_stat_hires_path) to c.t(R.string.pc_stat_hires_float)
                e.hiResWanted && !e.floatAllowed -> c.t(R.string.pc_stat_hires_path) to c.t(R.string.pc_stat_hires_paused)
                else -> null
            },
        ),
    )
}

private fun outputCard(c: Context, input: StatsInput): StatCard {
    val d = input.details
    val device = input.device
    val frameBytes = (channelCountOf(d.outputChannelConfig) ?: 2) * (bytesPerSample(d.outputEncoding) ?: 2)
    val bufferMs = d.outputBufferBytes?.let { bytes -> d.outputSampleRate?.takeIf { it > 0 }?.let { rate -> bytes * 1000L / (rate.toLong() * frameBytes) } }
    val mixer = input.mixer
    return StatCard(
        title = c.t(R.string.pc_stat_output),
        lines = listOfNotNull(
            input.castingTo?.let { c.t(R.string.pc_stat_casting_to) to it },
            device?.let { c.t(R.string.pc_stat_device) to deviceName(c, it) },
            device?.let { c.t(R.string.pc_stat_route) to routeName(c, it.kind) },
            device?.takeIf { it.kind == AudioOutputs.Kind.USB || it.kind == AudioOutputs.Kind.HDMI }?.let { dev ->
                listOfNotNull(dev.maxSampleRate?.let(::kHz), dev.bitDepths.maxOrNull()?.let { "$it-bit" })
                    .takeIf { it.isNotEmpty() }?.let { c.t(R.string.pc_stat_takes_up_to) to it.joinToString(" · ") }
            },
            mixer?.let {
                c.t(R.string.pc_stat_dac_mixer) to c.t(
                    if (it.bitPerfect) R.string.pc_stat_mixer_bitperfect else R.string.pc_stat_mixer_locked,
                    listOfNotNull(kHz(it.sampleRate), StreamInspector.bitDepthOf(it.encoding)?.let { b -> "$b-bit" }).joinToString(" "),
                )
            },
            d.outputEncoding?.let { enc ->
                c.t(R.string.pc_stat_audio_track) to listOfNotNull(pcmShort(c, enc), d.outputSampleRate?.let(::kHz), channelCountOf(d.outputChannelConfig)?.let { n -> channelName(c, n) }).joinToString(" · ")
            },
            bufferMs?.takeIf { it > 0 }?.let { c.t(R.string.pc_stat_buffer) to "~$it ms" },
            line(c.t(R.string.pc_stat_offload), c.t(R.string.pc_stat_on).takeIf { d.offload }),
            line(c.t(R.string.pc_stat_tunneling), c.t(R.string.pc_stat_on).takeIf { d.tunneling }),
            d.underruns.takeIf { it > 0 }?.let { c.t(R.string.pc_stat_underruns) to "$it" },
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

private fun channelName(c: Context, count: Int): String = when (count) {
    1 -> c.t(R.string.pc_stat_mono)
    2 -> c.t(R.string.pc_stat_stereo)
    6 -> c.t(R.string.pc_stat_surround_51)
    8 -> c.t(R.string.pc_stat_surround_71)
    else -> c.t(R.string.pc_stat_n_channels, count)
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

private fun pcmName(c: Context, encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_16BIT -> c.t(R.string.pc_stat_pcm_16)
    C.ENCODING_PCM_24BIT -> c.t(R.string.pc_stat_pcm_24)
    C.ENCODING_PCM_32BIT -> c.t(R.string.pc_stat_pcm_32)
    C.ENCODING_PCM_FLOAT -> c.t(R.string.pc_stat_pcm_float)
    C.ENCODING_PCM_8BIT -> c.t(R.string.pc_stat_pcm_8)
    else -> c.t(R.string.pc_stat_pcm_encoded)
}

private fun pcmShort(c: Context, encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_FLOAT -> c.t(R.string.pc_stat_float_32)
    else -> StreamInspector.bitDepthOf(encoding)?.let { "$it-bit" } ?: c.t(R.string.pc_stat_encoded)
}

private fun bytesPerSample(encoding: Int?): Int? = when (encoding) {
    C.ENCODING_PCM_16BIT -> 2
    C.ENCODING_PCM_24BIT -> 3
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
    else -> null
}

private fun channelCountOf(channelConfig: Int?): Int? = channelConfig?.let { Integer.bitCount(it).takeIf { n -> n > 0 } }

private fun routeName(c: Context, kind: AudioOutputs.Kind): String = when (kind) {
    AudioOutputs.Kind.PHONE -> c.t(R.string.pc_stat_route_speaker)
    AudioOutputs.Kind.WIRED -> c.t(R.string.pc_stat_route_wired)
    AudioOutputs.Kind.USB -> c.t(R.string.pc_stat_route_usb)
    AudioOutputs.Kind.BLUETOOTH -> c.t(R.string.pc_stat_bluetooth)
    AudioOutputs.Kind.HDMI -> "HDMI"
}

internal fun deviceName(c: Context, device: AudioOutputs.Device): String = device.name.ifBlank {
    when (device.kind) {
        AudioOutputs.Kind.PHONE -> c.t(R.string.pc_stat_dev_phone)
        AudioOutputs.Kind.WIRED -> c.t(R.string.pc_stat_dev_wired)
        AudioOutputs.Kind.USB -> c.t(R.string.pc_stat_dev_usb)
        AudioOutputs.Kind.BLUETOOTH -> c.t(R.string.pc_stat_dev_bluetooth)
        AudioOutputs.Kind.HDMI -> "HDMI"
    }
}
