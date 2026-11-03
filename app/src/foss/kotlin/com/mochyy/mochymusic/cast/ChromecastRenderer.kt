// ChromecastRenderer.kt

package com.mochyy.mochymusic.cast

import android.annotation.SuppressLint
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** a chromecast, google tv or nest speaker driven over the cast protocol itself, no play services (docs/notes/cast.md) */
class ChromecastRenderer(private val host: String, private val port: Int) : RemoteRenderer {

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestIds = AtomicInteger(1)
    private val writeLock = Any()

    private var socket: SSLSocket? = null
    private var output: DataOutputStream? = null

    @Volatile private var transportId: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var mediaSessionId: Int? = null
    private val appReady = CompletableDeferred<String>()

    // when the last status came in, so the position can run on between them
    @Volatile private var statusAt = 0L

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(AnyCertificate), SecureRandom()) }
        val s = tls.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), ConnectTimeoutMs)
        s.startHandshake()
        socket = s
        output = DataOutputStream(BufferedOutputStream(s.outputStream))
        io.launch { readLoop(DataInputStream(BufferedInputStream(s.inputStream))) }

        send(ReceiverId, NsConnection, JSONObject().put("type", "CONNECT"))
        // the receiver hangs up on a sender that goes quiet for a few seconds
        io.launch {
            while (isActive) {
                runCatching { send(ReceiverId, NsHeartbeat, JSONObject().put("type", "PING")) }
                delay(HeartbeatMs)
            }
        }
        send(ReceiverId, NsReceiver, request("LAUNCH").put("appId", DefaultMediaReceiver))
        val transport = withTimeout(LaunchTimeoutMs) { appReady.await() }
        send(transport, NsConnection, JSONObject().put("type", "CONNECT"))
    }

    override suspend fun load(media: RemoteMedia, startMs: Long) = withContext(Dispatchers.IO) {
        val transport = transportId ?: return@withContext
        val metadata = JSONObject()
            .put("metadataType", MusicTrackMetadata)
            .put("title", media.title)
            .put("artist", media.artist)
            .apply {
                media.album?.let { put("albumName", it) }
                media.artworkUrl?.let { put("images", JSONArray().put(JSONObject().put("url", it))) }
            }
        val load = request("LOAD")
            .put("sessionId", sessionId)
            .put(
                "media",
                JSONObject()
                    .put("contentId", media.url)
                    .put("contentType", media.mimeType)
                    .put("streamType", "BUFFERED")
                    .put("metadata", metadata),
            )
            .put("autoplay", true)
            .put("currentTime", startMs / 1000.0)
        mediaSessionId = null
        _state.update { it.copy(buffering = true, finished = false, failed = false, positionMs = startMs, durationMs = media.durationMs) }
        statusAt = SystemClock.elapsedRealtime()
        send(transport, NsMedia, load)
    }

    override suspend fun play() = mediaCommand("PLAY")

    override suspend fun pause() = mediaCommand("PAUSE")

    override suspend fun seek(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        statusAt = SystemClock.elapsedRealtime()
        mediaCommand("SEEK") { put("currentTime", positionMs / 1000.0) }
    }

    override suspend fun setVolume(volume: Float) = withContext(Dispatchers.IO) {
        val level = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = level) }
        send(ReceiverId, NsReceiver, request("SET_VOLUME").put("volume", JSONObject().put("level", level.toDouble())))
    }

    override fun positionNow(): Long {
        val s = _state.value
        if (!s.playing || s.buffering) return s.positionMs
        val moved = s.positionMs + (SystemClock.elapsedRealtime() - statusAt)
        return if (s.durationMs > 0) moved.coerceAtMost(s.durationMs) else moved
    }

    override suspend fun close(stopPlayback: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (stopPlayback) sessionId?.let { send(ReceiverId, NsReceiver, request("STOP").put("sessionId", it)) }
                send(ReceiverId, NsConnection, JSONObject().put("type", "CLOSE"))
            }
            runCatching { socket?.close() }
        }
        io.cancel()
    }

    private suspend fun mediaCommand(type: String, extra: JSONObject.() -> Unit = {}) = withContext(Dispatchers.IO) {
        val transport = transportId ?: return@withContext
        val media = mediaSessionId ?: return@withContext
        send(transport, NsMedia, request(type).put("mediaSessionId", media).apply(extra))
    }

    private fun request(type: String) = JSONObject().put("type", type).put("requestId", requestIds.getAndIncrement())

    private fun send(destination: String, namespace: String, payload: JSONObject) {
        val bytes = CastMessage.encode(SenderId, destination, namespace, payload.toString())
        synchronized(writeLock) {
            val out = output ?: throw IOException("not connected")
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (true) {
                val length = input.readInt()
                if (length <= 0 || length > MaxMessageBytes) throw IOException("a cast message $length bytes long")
                val bytes = ByteArray(length)
                input.readFully(bytes)
                val message = CastMessage.decode(bytes) ?: continue
                runCatching { handle(message) }.onFailure { Timber.w(it, "Couldn't read a cast message") }
            }
        } catch (e: Exception) {
            Timber.d(e, "Chromecast connection ended")
            _state.update { it.copy(lost = true, playing = false, buffering = false) }
            if (!appReady.isCompleted) appReady.completeExceptionally(e)
        }
    }

    private fun handle(message: CastMessage) {
        val payload = JSONObject(message.payload)
        when (message.namespace) {
            NsHeartbeat -> if (payload.optString("type") == "PING") {
                send(message.source, NsHeartbeat, JSONObject().put("type", "PONG"))
            }
            NsReceiver -> if (payload.optString("type") == "RECEIVER_STATUS") onReceiverStatus(payload.getJSONObject("status"))
            NsMedia -> when (payload.optString("type")) {
                "MEDIA_STATUS" -> payload.optJSONArray("status")?.optJSONObject(0)?.let(::onMediaStatus)
                "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST" ->
                    _state.update { it.copy(failed = true, playing = false, buffering = false) }
            }
            NsConnection -> if (payload.optString("type") == "CLOSE" && message.source != ReceiverId) {
                // the media app hung up on us: someone else cast to it, or it was closed on the tv
                _state.update { it.copy(lost = true, playing = false, buffering = false) }
            }
        }
    }

    private fun onReceiverStatus(status: JSONObject) {
        status.optJSONObject("volume")?.optDouble("level")?.takeIf { !it.isNaN() }?.let { level ->
            _state.update { it.copy(volume = level.toFloat()) }
        }
        val apps = status.optJSONArray("applications")
        val ours = (0 until (apps?.length() ?: 0)).mapNotNull { apps?.optJSONObject(it) }
            .firstOrNull { it.optString("appId") == DefaultMediaReceiver }
        if (ours == null) {
            // another app took the screen after we had it
            if (transportId != null) _state.update { it.copy(lost = true, playing = false, buffering = false) }
            return
        }
        sessionId = ours.optString("sessionId")
        val transport = ours.optString("transportId")
        if (transportId == null && transport.isNotEmpty()) {
            transportId = transport
            appReady.complete(transport)
        }
    }

    private fun onMediaStatus(status: JSONObject) {
        mediaSessionId = status.optInt("mediaSessionId").takeIf { it != 0 } ?: mediaSessionId
        val playerState = status.optString("playerState")
        val idleReason = status.optString("idleReason")
        val seconds = status.optDouble("currentTime")
        val duration = status.optJSONObject("media")?.optDouble("duration")
        statusAt = SystemClock.elapsedRealtime()
        _state.update { now ->
            now.copy(
                playing = playerState == "PLAYING" || playerState == "BUFFERING",
                buffering = playerState == "BUFFERING",
                positionMs = if (seconds.isNaN()) now.positionMs else (seconds * 1000).toLong(),
                durationMs = duration?.takeIf { !it.isNaN() && it > 0 }?.let { (it * 1000).toLong() } ?: now.durationMs,
                finished = playerState == "IDLE" && idleReason == "FINISHED",
                failed = playerState == "IDLE" && idleReason == "ERROR",
            )
        }
    }

    private companion object {
        const val SenderId = "sender-0"
        const val ReceiverId = "receiver-0"
        const val NsConnection = "urn:x-cast:com.google.cast.tp.connection"
        const val NsHeartbeat = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NsReceiver = "urn:x-cast:com.google.cast.receiver"
        const val NsMedia = "urn:x-cast:com.google.cast.media"
        // google's default media receiver: plays any plain link with its title and cover on the tv
        const val DefaultMediaReceiver = "CC1AD845"
        const val MusicTrackMetadata = 3
        const val ConnectTimeoutMs = 5000
        const val LaunchTimeoutMs = 12_000L
        const val HeartbeatMs = 5000L
        const val MaxMessageBytes = 64 * 1024
    }
}

// a chromecast signs with its own device certificate, not one a public authority vouches for, so on the home network any certificate is taken
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private object AnyCertificate : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** the cast protocol's one message type, a small protobuf written and read by hand so no protobuf library is needed */
private class CastMessage(val source: String, val destination: String, val namespace: String, val payload: String) {
    companion object {
        fun encode(source: String, destination: String, namespace: String, payload: String): ByteArray {
            val out = ByteArrayOutputStream()
            // protocol version 0, castv2 1.0
            out.write(0x08); out.write(0)
            writeString(out, 0x12, source)
            writeString(out, 0x1A, destination)
            writeString(out, 0x22, namespace)
            // the payload is text
            out.write(0x28); out.write(0)
            writeString(out, 0x32, payload)
            return out.toByteArray()
        }

        fun decode(bytes: ByteArray): CastMessage? {
            var i = 0
            var source = ""
            var destination = ""
            var namespace = ""
            var payload: String? = null
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (i < bytes.size) {
                    val b = bytes[i++].toInt()
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) break
                    shift += 7
                }
                return result
            }
            while (i < bytes.size) {
                val tag = varint().toInt()
                when (tag and 7) {
                    0 -> varint()
                    1 -> i += 8
                    5 -> i += 4
                    2 -> {
                        val length = varint().toInt()
                        if (length < 0 || i + length > bytes.size) return null
                        val text = String(bytes, i, length, Charsets.UTF_8)
                        when (tag ushr 3) {
                            2 -> source = text
                            3 -> destination = text
                            4 -> namespace = text
                            6 -> payload = text
                        }
                        i += length
                    }
                    else -> return null
                }
            }
            return payload?.let { CastMessage(source, destination, namespace, it) }
        }

        private fun writeString(out: ByteArrayOutputStream, tag: Int, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            out.write(tag)
            var length = bytes.size
            while (length >= 0x80) {
                out.write((length and 0x7F) or 0x80)
                length = length ushr 7
            }
            out.write(length)
            out.write(bytes)
        }
    }
}
