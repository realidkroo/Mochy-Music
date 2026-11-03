// DlnaRenderer.kt

package com.mochyy.mochymusic.cast

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** a dlna / upnp renderer (a smart tv, a network speaker) driven with its own soap calls, polled for where it is (docs/notes/cast.md) */
class DlnaRenderer(
    private val avTransportUrl: String,
    private val renderingControlUrl: String?,
) : RemoteRenderer {

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var polledAt = 0L
    // a stop we asked for isn't the song ending
    @Volatile private var sawPlaying = false
    @Volatile private var stopping = false
    private var failedPolls = 0

    override suspend fun connect() = withContext(Dispatchers.IO) {
        // a renderer that doesn't answer this isn't there, so connecting fails here rather than on the first song
        transportState()
        renderingControlUrl?.let { url ->
            runCatching {
                val reply = soap(url, RenderingControl, "GetVolume", listOf("InstanceID" to "0", "Channel" to "Master"))
                tag(reply, "CurrentVolume")?.toIntOrNull()?.let { v -> _state.update { it.copy(volume = v / 100f) } }
            }
        }
        io.launch {
            while (isActive) {
                poll()
                delay(PollMs)
            }
        }
        Unit
    }

    override suspend fun load(media: RemoteMedia, startMs: Long) = withContext(Dispatchers.IO) {
        stopping = true
        runCatching { soap(avTransportUrl, AvTransport, "Stop", listOf("InstanceID" to "0")) }
        sawPlaying = false
        _state.update { it.copy(buffering = true, finished = false, failed = false, positionMs = startMs, durationMs = media.durationMs) }
        try {
            soap(
                avTransportUrl, AvTransport, "SetAVTransportURI",
                listOf("InstanceID" to "0", "CurrentURI" to media.url, "CurrentURIMetaData" to didl(media)),
            )
            soap(avTransportUrl, AvTransport, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
        } catch (e: IOException) {
            Timber.w(e, "The renderer turned the song down")
            _state.update { it.copy(failed = true, buffering = false, playing = false) }
            return@withContext
        } finally {
            stopping = false
        }
        // a renderer only takes a seek once it's playing, so the start waits for the first poll that says so
        if (startMs > SeekWorthMs) {
            repeat(StartSeekTries) {
                delay(PollMs)
                if (_state.value.playing) {
                    runCatching { seekNow(startMs) }
                    return@withContext
                }
            }
        }
    }

    override suspend fun play() = withContext(Dispatchers.IO) {
        _state.update { it.copy(playing = true) }
        soap(avTransportUrl, AvTransport, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
        Unit
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        _state.update { it.copy(playing = false) }
        soap(avTransportUrl, AvTransport, "Pause", listOf("InstanceID" to "0"))
        Unit
    }

    override suspend fun seek(positionMs: Long) = withContext(Dispatchers.IO) { seekNow(positionMs) }

    private fun seekNow(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        polledAt = SystemClock.elapsedRealtime()
        soap(avTransportUrl, AvTransport, "Seek", listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to clock(positionMs)))
    }

    override suspend fun setVolume(volume: Float) = withContext(Dispatchers.IO) {
        val url = renderingControlUrl ?: return@withContext
        val level = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = level) }
        soap(url, RenderingControl, "SetVolume", listOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to (level * 100).toInt().toString()))
        Unit
    }

    override fun positionNow(): Long {
        val s = _state.value
        if (!s.playing || s.buffering) return s.positionMs
        val moved = s.positionMs + (SystemClock.elapsedRealtime() - polledAt)
        return if (s.durationMs > 0) moved.coerceAtMost(s.durationMs) else moved
    }

    override suspend fun close(stopPlayback: Boolean) {
        io.cancel()
        if (!stopPlayback) return
        withContext(Dispatchers.IO) {
            stopping = true
            runCatching { soap(avTransportUrl, AvTransport, "Stop", listOf("InstanceID" to "0")) }
        }
    }

    private fun poll() {
        try {
            val transport = transportState()
            val position = soap(avTransportUrl, AvTransport, "GetPositionInfo", listOf("InstanceID" to "0"))
            failedPolls = 0
            val at = tag(position, "RelTime")?.let(::parseClock)
            val length = tag(position, "TrackDuration")?.let(::parseClock)?.takeIf { it > 0 }
            val playing = transport == "PLAYING"
            if (playing) sawPlaying = true
            polledAt = SystemClock.elapsedRealtime()
            _state.update { now ->
                now.copy(
                    playing = playing || transport == "TRANSITIONING",
                    buffering = transport == "TRANSITIONING",
                    positionMs = at ?: now.positionMs,
                    durationMs = length ?: now.durationMs,
                    // once a song has played, the renderer stopping on its own means it reached the end
                    finished = !stopping && sawPlaying && (transport == "STOPPED" || transport == "NO_MEDIA_PRESENT"),
                )
            }
        } catch (e: IOException) {
            // a few missed answers in a row: the tv went off or left the network
            if (++failedPolls >= LostAfterPolls) _state.update { it.copy(lost = true, playing = false, buffering = false) }
        }
    }

    private fun transportState(): String {
        val reply = soap(avTransportUrl, AvTransport, "GetTransportInfo", listOf("InstanceID" to "0"))
        return tag(reply, "CurrentTransportState") ?: ""
    }

    private fun soap(controlUrl: String, service: String, action: String, args: List<Pair<String, String>>): String {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(service).append("\">")
            args.forEach { (name, value) -> append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>') }
            append("</u:").append(action).append("></s:Body></s:Envelope>")
        }.toByteArray(Charsets.UTF_8)
        val connection = URL(controlUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TimeoutMs
            connection.readTimeout = TimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            connection.setRequestProperty("SOAPACTION", "\"$service#$action\"")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val text = (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) throw IOException("$action answered $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val AvTransport = "urn:schemas-upnp-org:service:AVTransport:1"
        const val RenderingControl = "urn:schemas-upnp-org:service:RenderingControl:1"
        const val PollMs = 1000L
        const val TimeoutMs = 4000
        const val LostAfterPolls = 4
        const val SeekWorthMs = 1500L
        const val StartSeekTries = 8
    }
}

// what the renderer shows for the song, as a didl-lite item the way upnp describes media
private fun didl(media: RemoteMedia): String = buildString {
    append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
    append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
    append("<dc:title>").append(escape(media.title)).append("</dc:title>")
    append("<upnp:artist>").append(escape(media.artist)).append("</upnp:artist>")
    append("<dc:creator>").append(escape(media.artist)).append("</dc:creator>")
    media.album?.let { append("<upnp:album>").append(escape(it)).append("</upnp:album>") }
    media.artworkUrl?.let { append("<upnp:albumArtURI>").append(escape(it)).append("</upnp:albumArtURI>") }
    append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
    append("<res protocolInfo=\"http-get:*:").append(escape(media.mimeType)).append(":*\"")
    if (media.durationMs > 0) append(" duration=\"").append(clock(media.durationMs)).append(".000\"")
    append('>').append(escape(media.url)).append("</res>")
    append("</item></DIDL-Lite>")
}

private fun escape(text: String): String = buildString(text.length) {
    for (c in text) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
    }
}

// built once per tag name: the renderer is asked every second
private val TagPatterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()

// the first <name> in a soap answer, whatever namespace prefix the renderer gave it
private fun tag(xml: String, name: String): String? =
    TagPatterns.getOrPut(name) { Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>([^<]*)</(?:\\w+:)?$name>") }
        .find(xml)?.groupValues?.get(1)?.trim()
        ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")?.replace("&apos;", "'")?.replace("&amp;", "&")

// h:mm:ss, the way upnp writes a time
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
}

private fun parseClock(text: String): Long? {
    if (text.isBlank() || text == "NOT_IMPLEMENTED") return null
    val parts = text.split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toDoubleOrNull() ?: return null
    return ((hours * 3600 + minutes * 60) * 1000) + (seconds * 1000).toLong()
}
