// the stream and phone words a module player's text can show: codec, rate, bitrate, bits, size, lossy or lossless, the output, the volume and how busy mochy is (docs/wiki, text blocks)

package com.mochyy.mochymusic.ui.player.styles

import android.content.Context
import android.media.AudioManager
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.modules.skin.SkinAudioField
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.StreamDetails
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale

/** the placeholders' words by lowercase name, changing only when one of them does */
@Stable
internal class SkinAudio(initial: Map<String, String> = emptyMap()) {
    var fields by mutableStateOf(initial)
        internal set

    // a value mochy doesn't know yet shows as a dash, so the layout never jumps around an empty gap
    fun fill(text: String): String = SkinAudioField.replace(text) { fields[it.groupValues[1].lowercase()] ?: Unknown }
}

/** null unless the layout shows a stream placeholder; the volume and cpu only tick while the player can be seen */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberSkinAudio(needed: Boolean, preview: Boolean): SkinAudio? {
    if (!needed) return null
    // the style picker's card is still, so it shows a believable made-up stream
    if (preview) return remember { SkinAudio(PreviewFields) }
    val audio = remember { SkinAudio() }
    val connection = LocalPlayerConnection.current ?: return audio
    val android = LocalContext.current
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    LaunchedEffect(connection) {
        val service = connection.service
        val stream = connection.mediaMetadata
            .map { it?.id to (it?.duration ?: 0) * 1_000L }
            .distinctUntilChanged()
            .flatMapLatest { (id, durationMs) -> if (id == null) flowOf(emptyMap()) else streamFlow(service, connection, id, durationMs, android) }
        val device = service.audioOutputs.devices.map { deviceName(it, android) }.distinctUntilChanged()
        val live = snapshotFlow { showing() && !obscured.value }
            .distinctUntilChanged()
            .flatMapLatest { shown -> if (shown) liveFlow(android) else emptyFlow() }
            .onStart { emit(emptyMap()) }
        combine(stream, device, live) { words, output, now -> words + now + ("device" to output) }
            .collect { audio.fields = it }
    }
    return audio
}

// everything the stream info sheet knows about this song, boiled down to short words
@OptIn(ExperimentalCoroutinesApi::class)
private fun streamFlow(service: MusicService, connection: PlayerConnection, id: String, durationMs: Long, android: Context): Flow<Map<String, String>> {
    val details = service.playerFlow.flatMapLatest { player -> player?.let(service::inspectorOf)?.details ?: flowOf(StreamDetails()) }
    return combine(connection.streamFacts(id), details, connection.currentFormat, service.playingFrom.map { it[id] }) { facts, all, format, from ->
        // gapless reads the next song early, so details filed under another song don't count
        val mine = all.takeIf { it.mediaId == null || it.mediaId == id } ?: StreamDetails()
        // youtube's record only describes youtube's stream
        val youtube = format?.takeIf { from?.providerId == SourceModules.YouTubeId }
        streamWords(facts, mine, youtube, durationMs, android)
    }.distinctUntilChanged()
}

private fun streamWords(facts: StreamFacts?, details: StreamDetails, youtube: FormatEntity?, durationMs: Long, android: Context): Map<String, String> {
    val codec = facts?.codec ?: StreamFacts.codecOf(details.sampleMime, details.codecs)
    val rate = details.sampleRate ?: facts?.sampleRate ?: youtube?.sampleRate?.takeIf { it > 0 }
    val size = details.fileSizeBytes ?: youtube?.contentLength?.takeIf { it > 0 }
    // a flac file says nothing of its bitrate, so its size over its length stands in
    val bitrate = details.bitrate ?: youtube?.bitrate?.takeIf { it > 0 }
        ?: if (size != null && durationMs > 0) (size * 8_000 / durationMs).toInt() else null
    val bits = details.sourceBitDepth ?: pcmBits(details.decodedEncoding)
    return buildMap {
        codec?.let { put("codec", it) }
        rate?.let { put("khz", kiloHertz(it)) }
        bitrate?.let { put("kbps", ((it + 500) / 1000).toString()) }
        bits?.let { put("bits", it.toString()) }
        size?.let { put("size", String.format(Locale.US, "%.1f", it / 1_048_576.0)) }
        codec?.let { put("quality", android.getString(if (it in Lossless) R.string.skin_audio_lossless else R.string.skin_audio_lossy)) }
    }
}

// the phone's media volume and mochy's share of the cpu, once a second, off the main thread since the volume is a binder call
private fun liveFlow(android: Context): Flow<Map<String, String>> = flow {
    val manager = android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    var lastCpu = Process.getElapsedCpuTime()
    var lastWall = SystemClock.elapsedRealtime()
    var cpu = 0
    while (true) {
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        emit(mapOf("volume" to volume.toString(), "cpu" to cpu.toString()))
        delay(LiveTickMs)
        val nowCpu = Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        cpu = ((nowCpu - lastCpu) * 100 / ((nowWall - lastWall).coerceAtLeast(1) * cores)).toInt().coerceIn(0, 100)
        lastCpu = nowCpu
        lastWall = nowWall
    }
}.distinctUntilChanged().flowOn(Dispatchers.Default)

private fun deviceName(devices: List<AudioOutputs.Device>, android: Context): String {
    val active = devices.firstOrNull { it.isActive }
    active?.name?.takeIf { it.isNotBlank() }?.let { return it }
    return android.getString(
        when (active?.kind) {
            null, AudioOutputs.Kind.PHONE -> R.string.skin_output_speaker
            AudioOutputs.Kind.WIRED -> R.string.skin_output_wired
            AudioOutputs.Kind.USB -> R.string.flagship_output_usb
            AudioOutputs.Kind.BLUETOOTH -> R.string.flagship_output_bluetooth
            AudioOutputs.Kind.HDMI -> R.string.flagship_output_hdmi
        },
    )
}

// "44.1" or "48", the way a spec sheet writes it
private fun kiloHertz(rate: Int): String =
    if (rate % 1000 == 0) (rate / 1000).toString() else String.format(Locale.US, "%.1f", rate / 1000.0)

private fun pcmBits(encoding: Int?): Int? = when (encoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT -> 16
    C.ENCODING_PCM_24BIT -> 24
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
    else -> null
}

private const val Unknown = "–"
private const val LiveTickMs = 1_000L
private val Lossless = setOf("FLAC", "ALAC", "PCM")
private val PreviewFields = mapOf(
    "codec" to "MP3", "khz" to "44.1", "kbps" to "320", "bits" to "16", "size" to "7.4",
    "quality" to "Lossy", "device" to "Speaker", "volume" to "60", "cpu" to "3",
)
