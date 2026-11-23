// CastMediaProxy.kt

package com.mochyy.mochymusic.cast

import com.mochyy.mochymusic.utils.AppProxy
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Tiny LAN HTTP/1.1 media proxy started only while casting.
 *
 * Solves four separate reasons Samsung and other DLNA TVs (and web-header-gated streams on
 * Chromecast) fail with "format not supported":
 *  1. Serves plain HTTP on the LAN with a clean extension (.m4a/.mp3), avoiding TV HTTPS/TLS
 *     failures and http-get/https scheme mismatches.
 *  2. Answers HTTP HEAD with Content-Length, Accept-Ranges and DLNA transfer/contentFeatures
 *     headers without ever sending HEAD to googlevideo (which answers 403 to HEAD).
 *  3. Attaches YouTube's Origin/Referer/User-Agent headers upstream so googlevideo doesn't 403.
 *  4. Remuxes DASH fragmented MP4 (itag 140) into a plain faststart M4A so TV DLNA parsers play
 *     and seek cleanly.
 */
class CastMediaProxy(
    private val cacheDir: File,
    private val httpClient: OkHttpClient = AppProxy.sharedClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    data class ServedTrack(
        val url: String,
        val mimeType: String,
        val contentLength: Long,
        val dlnaFeatures: String,
    )

    private sealed interface Entry {
        val token: String
        val mimeType: String
        val dlnaFeatures: String

        data class LocalFile(
            override val token: String,
            override val mimeType: String,
            override val dlnaFeatures: String,
            val file: File,
        ) : Entry

        data class RemoteStream(
            override val token: String,
            override val mimeType: String,
            override val dlnaFeatures: String,
            val upstreamUrl: String,
            val headers: Map<String, String>,
            @Volatile var contentLength: Long,
        ) : Entry
    }

    @Volatile private var serverSocket: ServerSocket? = null
    private var workers: ExecutorService? = null
    @Volatile private var currentEntry: Entry? = null
    private val ownedFiles = ArrayList<File>()
    private val fileLock = Any()

    val port: Int get() = serverSocket?.localPort ?: -1

    @Synchronized
    fun ensureStarted(): Int {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val server = ServerSocket(0)
        serverSocket = server
        val pool = Executors.newFixedThreadPool(MaxConnections) { r ->
            Thread(r, "CastProxy-conn").apply { isDaemon = true }
        }
        workers = pool
        Thread({ acceptLoop(server, pool) }, "CastProxy-accept").apply { isDaemon = true }.start()
        Timber.d("CastMediaProxy listening on port ${server.localPort}")
        return server.localPort
    }

    @Synchronized
    fun stop() {
        currentEntry = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        workers?.shutdownNow()
        workers = null
        deleteOwnedFiles()
    }

    /**
     * Prepares [upstreamUrl] to be served on the LAN to [receiverHost].
     * When [remuxMp4] is true and the stream is an MP4 under [MaxBufferBytes], downloads and
     * remuxes fragmented DASH MP4 into a faststart .m4a file; otherwise streams through on the fly.
     */
    suspend fun prepare(
        receiverHost: String,
        upstreamUrl: String,
        mimeType: String,
        headers: Map<String, String>,
        contentLength: Long,
        dlnaFeatures: String,
        remuxMp4: Boolean,
    ): ServedTrack? = withContext(Dispatchers.IO) {
        val localIp = localAddressFor(receiverHost) ?: return@withContext null
        val listeningPort = ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        val cleanMime = mimeType.substringBefore(';').trim().ifBlank { "audio/mp4" }
        val ext = extensionFor(cleanMime)

        val isMp4 = cleanMime.equals("audio/mp4", ignoreCase = true) ||
            cleanMime.equals("audio/m4a", ignoreCase = true) ||
            cleanMime.equals("audio/x-m4a", ignoreCase = true)

        if (remuxMp4 && isMp4 && (contentLength <= 0L || contentLength <= MaxBufferBytes)) {
            val preparedFile = runCatching { downloadAndRemux(upstreamUrl, headers, token) }
                .onFailure { Timber.w(it, "CastMediaProxy remux failed, falling back to live stream proxy") }
                .getOrNull()
            if (preparedFile != null && preparedFile.length() > 0L) {
                val entry = Entry.LocalFile(token, cleanMime, dlnaFeatures, preparedFile)
                replaceEntry(entry, keep = preparedFile)
                val url = "http://$localIp:$listeningPort/media/$token.$ext"
                return@withContext ServedTrack(url, cleanMime, preparedFile.length(), dlnaFeatures)
            }
        }

        val resolvedLength = if (contentLength > 0L) contentLength else probeLength(upstreamUrl, headers)
        val entry = Entry.RemoteStream(
            token = token,
            mimeType = cleanMime,
            dlnaFeatures = dlnaFeatures,
            upstreamUrl = upstreamUrl,
            headers = headers,
            contentLength = resolvedLength,
        )
        replaceEntry(entry, keep = null)
        val url = "http://$localIp:$listeningPort/media/$token.$ext"
        ServedTrack(url, cleanMime, resolvedLength, dlnaFeatures)
    }

    /** Serves an already-present local file over LAN HTTP (also useful for tests). */
    fun serveLocalFile(
        localIp: String,
        file: File,
        mimeType: String,
        dlnaFeatures: String = DefaultDlnaFeatures,
    ): ServedTrack {
        val listeningPort = ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        val ext = extensionFor(mimeType)
        currentEntry = Entry.LocalFile(token, mimeType, dlnaFeatures, file)
        return ServedTrack(
            url = "http://$localIp:$listeningPort/media/$token.$ext",
            mimeType = mimeType,
            contentLength = file.length(),
            dlnaFeatures = dlnaFeatures,
        )
    }

    private fun downloadAndRemux(url: String, headers: Map<String, String>, token: String): File? {
        cacheDir.mkdirs()
        val raw = File(cacheDir, "cast_raw_$token.m4a")
        val remuxed = File(cacheDir, "cast_play_$token.m4a")
        trackFile(raw)
        trackFile(remuxed)

        val request = buildUpstreamRequest(url, headers, rangeHeader = null)
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Timber.w("CastMediaProxy upstream returned ${resp.code}")
                raw.delete()
                remuxed.delete()
                return null
            }
            val body = resp.body
            val declaredLen = body.contentLength()
            if (declaredLen > MaxBufferBytes) {
                raw.delete()
                remuxed.delete()
                return null
            }
            var total = 0L
            FileOutputStream(raw).use { out ->
                val buf = ByteArray(64 * 1024)
                val input = body.byteStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MaxBufferBytes) {
                        out.close()
                        raw.delete()
                        remuxed.delete()
                        return null
                    }
                    out.write(buf, 0, n)
                }
            }
        }

        val didRemux = runCatching { Fmp4Remuxer.remuxIfFragmented(raw, remuxed) }
            .onFailure { Timber.w(it, "Fmp4Remuxer failed") }
            .getOrDefault(false)

        return if (didRemux && remuxed.length() > 0L) {
            raw.delete()
            remuxed
        } else {
            remuxed.delete()
            raw
        }
    }

    private fun probeLength(url: String, headers: Map<String, String>): Long {
        // googlevideo rejects HEAD with 403, so probe with a 1-byte ranged GET instead.
        return runCatching {
            val req = buildUpstreamRequest(url, headers, rangeHeader = "bytes=0-0")
            httpClient.newCall(req).execute().use { resp ->
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange?.substringAfter('/', "")?.trim()?.toLongOrNull()
                if (totalFromRange != null && totalFromRange > 0L) return@use totalFromRange
                if (resp.code == 200) resp.body.contentLength().takeIf { it > 0L } ?: -1L else -1L
            }
        }.getOrDefault(-1L)
    }

    private fun buildUpstreamRequest(url: String, headers: Map<String, String>, rangeHeader: String?): Request {
        val builder = Request.Builder().get().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (rangeHeader != null) builder.header("Range", rangeHeader)
        YouTube.cookie?.let { cookie ->
            if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                builder.header("Cookie", cookie)
            }
        }
        return builder.build()
    }

    private fun acceptLoop(server: ServerSocket, pool: ExecutorService) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                Timber.d(e, "CastMediaProxy accept ended")
                continue
            }
            try {
                pool.execute { serve(client) }
            } catch (_: RejectedExecutionException) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.use { conn ->
            runCatching {
                conn.soTimeout = ReadTimeoutMs
                val input = BufferedInputStream(conn.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val rawPath = parts[1].substringBefore('?')
                val reqHeaders = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) reqHeaders[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }

                val out = conn.getOutputStream().buffered(64 * 1024)
                if (method == "OPTIONS") {
                    writeResponseHead(out, 200, "text/plain", 0L, null, DefaultDlnaFeatures)
                    out.flush()
                    return
                }
                if (method != "GET" && method != "HEAD") {
                    writeSimpleStatus(out, 405, "Method Not Allowed")
                    return
                }

                val entry = currentEntry
                val reqToken = rawPath.removePrefix("/media/").substringBefore('.')
                if (entry == null || reqToken != entry.token) {
                    writeSimpleStatus(out, 404, "Not Found")
                    return
                }

                when (entry) {
                    is Entry.LocalFile -> serveLocal(entry, method, reqHeaders["range"], out)
                    is Entry.RemoteStream -> serveRemote(entry, method, reqHeaders["range"], out)
                }
                out.flush()
            }.onFailure {
                if (it !is SocketException) Timber.d(it, "CastMediaProxy client connection closed")
            }
        }
    }

    private fun serveLocal(entry: Entry.LocalFile, method: String, rangeHeader: String?, out: OutputStream) {
        val file = entry.file
        if (!file.exists()) {
            writeSimpleStatus(out, 404, "Not Found")
            return
        }
        val total = file.length()
        val range = parseRange(rangeHeader, total)
        if (rangeHeader != null && range == null) {
            writeUnsatisfiable(out, total)
            return
        }
        if (range != null) {
            val (start, end) = range
            if (start >= total || start > end) {
                writeUnsatisfiable(out, total)
                return
            }
            val length = end - start + 1L
            writeResponseHead(
                out = out,
                status = 206,
                contentType = entry.mimeType,
                contentLength = length,
                contentRange = "bytes $start-$end/$total",
                dlnaFeatures = entry.dlnaFeatures,
            )
            if (method == "HEAD") return
            streamFileSlice(file, start, length, out)
        } else {
            writeResponseHead(
                out = out,
                status = 200,
                contentType = entry.mimeType,
                contentLength = total,
                contentRange = null,
                dlnaFeatures = entry.dlnaFeatures,
            )
            if (method == "HEAD") return
            streamFileSlice(file, 0L, total, out)
        }
    }

    private fun streamFileSlice(file: File, start: Long, length: Long, out: OutputStream) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
        }
    }

    private fun serveRemote(entry: Entry.RemoteStream, method: String, rangeHeader: String?, out: OutputStream) {
        val knownTotal = entry.contentLength
        if (method == "HEAD") {
            val total = if (knownTotal > 0L) knownTotal else probeLength(entry.upstreamUrl, entry.headers).also {
                if (it > 0L) entry.contentLength = it
            }
            writeResponseHead(
                out = out,
                status = 200,
                contentType = entry.mimeType,
                contentLength = total,
                contentRange = null,
                dlnaFeatures = entry.dlnaFeatures,
            )
            return
        }

        val req = buildUpstreamRequest(entry.upstreamUrl, entry.headers, rangeHeader)
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                writeSimpleStatus(out, 502, "Bad Gateway")
                return
            }
            val body = resp.body
            val contentRange = resp.header("Content-Range")
            contentRange?.substringAfter('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let {
                entry.contentLength = it
            }
            val len = body.contentLength()
            writeResponseHead(
                out = out,
                status = resp.code,
                contentType = entry.mimeType,
                contentLength = len,
                contentRange = contentRange,
                dlnaFeatures = entry.dlnaFeatures,
            )
            val buf = ByteArray(64 * 1024)
            val input = body.byteStream()
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
    }

    private fun writeResponseHead(
        out: OutputStream,
        status: Int,
        contentType: String,
        contentLength: Long,
        contentRange: String?,
        dlnaFeatures: String,
    ) {
        val reason = when (status) {
            200 -> "OK"
            206 -> "Partial Content"
            else -> "Error"
        }
        val sb = StringBuilder()
            .append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            .append("Content-Type: ").append(contentType).append("\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("transferMode.dlna.org: Streaming\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
            .append("Connection: close\r\n")
        if (dlnaFeatures.isNotBlank() && dlnaFeatures != "*") {
            sb.append("contentFeatures.dlna.org: ").append(dlnaFeatures).append("\r\n")
        }
        if (contentLength >= 0L) sb.append("Content-Length: ").append(contentLength).append("\r\n")
        if (contentRange != null) sb.append("Content-Range: ").append(contentRange).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun writeSimpleStatus(out: OutputStream, status: Int, reason: String) {
        val head = "HTTP/1.1 $status $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeUnsatisfiable(out: OutputStream, total: Long) {
        val head = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > MaxHeaderLine) return null
            sb.append(b.toChar())
        }
    }

    private fun trackFile(file: File) {
        synchronized(fileLock) { ownedFiles.add(file) }
    }

    private fun replaceEntry(entry: Entry, keep: File?) {
        currentEntry = entry
        synchronized(fileLock) {
            val iter = ownedFiles.iterator()
            while (iter.hasNext()) {
                val f = iter.next()
                if (f != keep) {
                    runCatching { f.delete() }
                    iter.remove()
                }
            }
        }
    }

    private fun deleteOwnedFiles() {
        synchronized(fileLock) {
            ownedFiles.forEach { runCatching { it.delete() } }
            ownedFiles.clear()
        }
    }

    companion object {
        const val DefaultDlnaFeatures = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        private const val MaxConnections = 8
        private const val ReadTimeoutMs = 15_000
        private const val MaxHeaderLine = 16 * 1024
        private const val MaxBufferBytes = 64L * 1024L * 1024L

        fun extensionFor(mimeType: String): String = when (mimeType.substringBefore(';').trim().lowercase()) {
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/aac" -> "aac"
            "audio/flac" -> "flac"
            "audio/ogg" -> "ogg"
            "audio/webm" -> "webm"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "m4a"
        }

        /** Finds the local IPv4 address on the interface the OS routes to [targetHost]. */
        fun localAddressFor(targetHost: String): String? {
            runCatching {
                DatagramSocket().use { sock ->
                    sock.connect(InetAddress.getByName(targetHost), 80)
                    val addr = sock.localAddress
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isAnyLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
            return runCatching {
                NetworkInterface.getNetworkInterfaces()?.asSequence()
                    ?.filter { it.isUp && !it.isLoopback }
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.hostAddress
            }.getOrNull()
        }

        internal fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
            val trimmed = header?.trim() ?: return null
            if (!trimmed.startsWith("bytes=", ignoreCase = true) || total <= 0L) return null
            val first = trimmed.substring(6).substringBefore(',').trim()
            val dash = first.indexOf('-')
            if (dash < 0) return null
            val startStr = first.substring(0, dash).trim()
            val endStr = first.substring(dash + 1).trim()
            if (startStr.isEmpty()) {
                val suffix = endStr.toLongOrNull()?.takeIf { it > 0L } ?: return null
                return (total - suffix).coerceAtLeast(0L) to (total - 1L)
            }
            val start = startStr.toLongOrNull() ?: return null
            val end = if (endStr.isEmpty()) {
                total - 1L
            } else {
                endStr.toLongOrNull()?.coerceAtMost(total - 1L) ?: return null
            }
            return start to end
        }
    }
}
