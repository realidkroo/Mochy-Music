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
import com.mochyy.mochymusic.utils.LanHttpClient
import timber.log.Timber
import java.io.IOException
import java.net.URL
import kotlin.math.abs

/** a dlna / upnp renderer (a smart tv, a network speaker) driven with its own soap calls, polled for where it is (docs/notes/cast.md) */
class DlnaRenderer(
    private val avTransportUrl: String,
    private val renderingControlUrl: String?,
    private val connectionManagerUrl: String? = null,
) : RemoteRenderer {

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var polledAt = 0L
    // a stop we asked for isn't the song ending
    @Volatile private var sawPlaying = false
    @Volatile private var stopping = false
    @Volatile private var seekTargetMs = -1L
    @Volatile private var seekGuardUntil = 0L
    @Volatile private var sinkProtocols: List<String> = emptyList()
    private var failedPolls = 0

    val host: String by lazy { runCatching { URL(avTransportUrl).host }.getOrDefault("") }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        // a renderer that doesn't answer this isn't there, so connecting fails here rather than on the first song
        transportInfo()
        connectionManagerUrl?.let { url ->
            runCatching {
                val reply = soap(url, ConnectionManager, "GetProtocolInfo", emptyList())
                val sink = tag(reply, "Sink").orEmpty()
                if (sink.isNotBlank()) {
                    sinkProtocols = sink.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    Timber.d("DLNA sink advertised ${sinkProtocols.size} protocols")
                }
            }
        }
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

    /** Picks the MIME type and DLNA feature string this renderer prefers for [mimeType]. */
    fun negotiateProfile(mimeType: String): Pair<String, String> =
        negotiateDlnaProfile(mimeType, sinkProtocols)

    override suspend fun load(media: RemoteMedia, startMs: Long) = withContext(Dispatchers.IO) {
        stopping = true
        seekTargetMs = -1L
        seekGuardUntil = 0L
        runCatching { soap(avTransportUrl, AvTransport, "Stop", listOf("InstanceID" to "0")) }
        sawPlaying = false
        polledAt = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                playing = true,
                buffering = true,
                finished = false,
                failed = false,
                positionMs = startMs,
                durationMs = media.durationMs,
            )
        }
        try {
            try {
                soap(
                    avTransportUrl, AvTransport, "SetAVTransportURI",
                    listOf("InstanceID" to "0", "CurrentURI" to media.url, "CurrentURIMetaData" to didl(media)),
                )
            } catch (e: IOException) {
                // Some strict TVs reject DIDL-Lite fields (like albumArtURI or profile tags); retry bare URI once.
                Timber.d(e, "SetAVTransportURI with DIDL failed, retrying without metadata")
                soap(
                    avTransportUrl, AvTransport, "SetAVTransportURI",
                    listOf("InstanceID" to "0", "CurrentURI" to media.url, "CurrentURIMetaData" to ""),
                )
            }
            soap(avTransportUrl, AvTransport, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
            polledAt = SystemClock.elapsedRealtime()
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
                if (_state.value.playing && !_state.value.buffering) {
                    runCatching { seekNow(startMs) }
                    return@withContext
                }
            }
        }
    }

    override suspend fun play() = withContext(Dispatchers.IO) {
        polledAt = SystemClock.elapsedRealtime()
        _state.update { it.copy(playing = true) }
        soap(avTransportUrl, AvTransport, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
        Unit
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        val current = positionNow()
        polledAt = SystemClock.elapsedRealtime()
        _state.update { it.copy(playing = false, positionMs = current) }
        soap(avTransportUrl, AvTransport, "Pause", listOf("InstanceID" to "0"))
        Unit
    }

    override suspend fun seek(positionMs: Long) {
        val now = SystemClock.elapsedRealtime()
        seekTargetMs = positionMs
        seekGuardUntil = now + SeekGuardMs
        polledAt = now
        _state.update { it.copy(positionMs = positionMs) }
        withContext(Dispatchers.IO) {
            soap(avTransportUrl, AvTransport, "Seek", listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to clock(positionMs)))
        }
    }

    private fun seekNow(positionMs: Long) {
        val now = SystemClock.elapsedRealtime()
        seekTargetMs = positionMs
        seekGuardUntil = now + SeekGuardMs
        polledAt = now
        _state.update { it.copy(positionMs = positionMs) }
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
        if (stopping) return
        try {
            val (transport, status) = transportInfo()
            val position = soap(avTransportUrl, AvTransport, "GetPositionInfo", listOf("InstanceID" to "0"))
            failedPolls = 0
            if (stopping) return

            val at = tag(position, "RelTime")?.let(::parseClock)
            val length = tag(position, "TrackDuration")?.let(::parseClock)?.takeIf { it > 0 }
            val playing = transport == "PLAYING"
            val transitioning = transport == "TRANSITIONING"
            val errored = status.equals("ERROR_OCCURRED", ignoreCase = true)
            if (playing) sawPlaying = true

            val nowMs = SystemClock.elapsedRealtime()
            val keepSeekTarget = at != null && seekTargetMs >= 0L && nowMs < seekGuardUntil && abs(at - seekTargetMs) > SeekLandedToleranceMs
            if (at != null && seekTargetMs >= 0L && abs(at - seekTargetMs) <= SeekLandedToleranceMs) {
                seekTargetMs = -1L
            }
            if (!keepSeekTarget) polledAt = nowMs

            _state.update { now ->
                now.copy(
                    playing = !errored && (playing || transitioning),
                    buffering = !errored && transitioning,
                    positionMs = when {
                        keepSeekTarget -> now.positionMs
                        at != null -> at
                        else -> now.positionMs
                    },
                    durationMs = length ?: now.durationMs,
                    // once a song has played, the renderer stopping on its own means it reached the end
                    finished = !stopping && !errored && sawPlaying && (transport == "STOPPED" || transport == "NO_MEDIA_PRESENT"),
                    failed = now.failed || errored,
                )
            }
        } catch (e: IOException) {
            // a few missed answers in a row: the tv went off or left the network
            if (++failedPolls >= LostAfterPolls) _state.update { it.copy(lost = true, playing = false, buffering = false) }
        }
    }

    private fun transportInfo(): Pair<String, String> {
        val reply = soap(avTransportUrl, AvTransport, "GetTransportInfo", listOf("InstanceID" to "0"))
        val state = tag(reply, "CurrentTransportState") ?: ""
        val status = tag(reply, "CurrentTransportStatus") ?: ""
        return state to status
    }

    private fun soap(controlUrl: String, service: String, action: String, args: List<Pair<String, String>>): String {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(service).append("\">")
            args.forEach { (name, value) -> append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>') }
            append("</u:").append(action).append("></s:Body></s:Envelope>")
        }.toByteArray(Charsets.UTF_8)
        LanHttpClient.execute(
            method = "POST",
            url = controlUrl,
            headers = mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPACTION" to "\"$service#$action\"",
            ),
            body = body,
            connectTimeoutMs = TimeoutMs,
            readTimeoutMs = TimeoutMs,
        ).use { response ->
            val text = response.body.string(maxBytes = 512 * 1024)
            if (response.code >= 400) throw IOException("$action answered ${response.code}: ${text.take(200)}")
            return text
        }
    }

    private companion object {
        const val AvTransport = "urn:schemas-upnp-org:service:AVTransport:1"
        const val RenderingControl = "urn:schemas-upnp-org:service:RenderingControl:1"
        const val ConnectionManager = "urn:schemas-upnp-org:service:ConnectionManager:1"
        const val PollMs = 1000L
        const val TimeoutMs = 4000
        const val LostAfterPolls = 4
        const val SeekWorthMs = 1500L
        const val StartSeekTries = 8
        const val SeekGuardMs = 2500L
        const val SeekLandedToleranceMs = 2500L
    }
}

/**
 * Matches a stream's MIME type against a DLNA renderer's advertised Sink protocolInfo list.
 * Samsung TVs advertise `DLNA.ORG_PN=AAC_ISO_320` or `AAC_ISO` for `audio/mp4`; matching the exact
 * profile name the TV advertised avoids 714 / "format not supported" rejections.
 */
internal fun negotiateDlnaProfile(mimeType: String, sinkProtocols: List<String>): Pair<String, String> {
    val clean = mimeType.substringBefore(';').trim().lowercase().ifBlank { "audio/mp4" }
    val baseFlags = CastMediaProxy.DefaultDlnaFeatures
    val isMp4 = clean == "audio/mp4" || clean == "audio/m4a" || clean == "audio/x-m4a"

    if (sinkProtocols.isEmpty()) {
        return clean to baseFlags
    }

    fun findProfile(targetMime: String, pn: String? = null): String? = sinkProtocols.firstOrNull { entry ->
        val parts = entry.split(':')
        if (parts.size < 4) return@firstOrNull false
        val proto = parts[0].trim().lowercase()
        val mime = parts[2].trim().substringBefore(';').lowercase()
        val info = parts[3].trim()
        proto == "http-get" && mime == targetMime && (pn == null || info.contains("DLNA.ORG_PN=$pn", ignoreCase = true))
    }

    if (isMp4) {
        if (findProfile("audio/mp4", "AAC_ISO_320") != null) {
            return "audio/mp4" to "DLNA.ORG_PN=AAC_ISO_320;$baseFlags"
        }
        if (findProfile("audio/mp4", "AAC_ISO") != null) {
            return "audio/mp4" to "DLNA.ORG_PN=AAC_ISO;$baseFlags"
        }
        if (findProfile("audio/mp4") != null) {
            return "audio/mp4" to baseFlags
        }
        if (findProfile("audio/x-m4a") != null) {
            return "audio/x-m4a" to baseFlags
        }
        if (findProfile("audio/m4a") != null) {
            return "audio/m4a" to baseFlags
        }
        return "audio/mp4" to baseFlags
    }

    if (clean == "audio/mpeg" && findProfile("audio/mpeg", "MP3") != null) {
        return "audio/mpeg" to "DLNA.ORG_PN=MP3;$baseFlags"
    }

    return clean to baseFlags
}

// what the renderer shows for the song, as a didl-lite item the way upnp describes media
private fun didl(media: RemoteMedia): String = buildString {
    append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">")
    append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
    append("<dc:title>").append(escape(media.title)).append("</dc:title>")
    append("<upnp:artist>").append(escape(media.artist)).append("</upnp:artist>")
    append("<dc:creator>").append(escape(media.artist)).append("</dc:creator>")
    media.album?.let { append("<upnp:album>").append(escape(it)).append("</upnp:album>") }
    media.artworkUrl?.let { append("<upnp:albumArtURI dlna:profileID=\"JPEG_TN\">").append(escape(it)).append("</upnp:albumArtURI>") }
    append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
    val features = media.dlnaFeatures.ifBlank { "*" }
    append("<res protocolInfo=\"http-get:*:").append(escape(media.mimeType)).append(':').append(escape(features)).append('"')
    if (media.durationMs > 0) append(" duration=\"").append(clock(media.durationMs)).append(".000\"")
    if (media.contentLength > 0) append(" size=\"").append(media.contentLength).append('"')
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
