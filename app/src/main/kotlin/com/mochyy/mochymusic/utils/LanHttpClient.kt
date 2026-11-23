// LanHttpClient.kt

package com.mochyy.mochymusic.utils

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal HTTP/1.1 client strictly restricted to private LAN addresses (RFC1918 IPv4, IPv6 ULA /
 * link-local). Used exclusively for local-network protocols that cannot use public CA TLS
 * (UPnP/DLNA casting and Phone↔TV Link), allowing the app's Network Security Config to forbid
 * cleartext HTTP everywhere else.
 *
 * Never follows redirects, validates every resolved IP before opening the socket (preventing DNS
 * rebinding and SSRF to localhost / cloud metadata / public internet), and caps header counts.
 */
object LanHttpClient {
    private const val MaxHeaderLines = 64
    private const val MaxHeaderLineBytes = 16 * 1024
    private const val DefaultMaxBodyBytes = 8 * 1024 * 1024

    class Body(
        private val stream: InputStream,
        val contentLength: Long,
    ) : Closeable {
        fun byteStream(): InputStream = stream

        fun bytes(maxBytes: Int = DefaultMaxBodyBytes): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IOException("LAN HTTP response exceeded $maxBytes bytes")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        fun string(maxBytes: Int = DefaultMaxBodyBytes): String = String(bytes(maxBytes), Charsets.UTF_8)

        override fun close() {
            runCatching { stream.close() }
        }
    }

    class Response(
        val code: Int,
        val headers: Map<String, String>,
        val body: Body,
        private val socket: Socket,
    ) : Closeable {
        val isSuccessful: Boolean get() = code in 200..299

        fun header(name: String): String? = headers[name.lowercase()]

        override fun close() {
            runCatching { body.close() }
            runCatching { socket.close() }
        }
    }

    data class ValidatedTarget(
        val scheme: String,
        val host: String,
        val port: Int,
        val address: InetAddress,
        val pathAndQuery: String,
    )

    /** True only for RFC1918 IPv4 (10/8, 172.16/12, 192.168/16) and IPv6 ULA (fc00::/7) / link-local (fe80::/10). */
    fun isPrivateLanAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        // Unwrap IPv4-mapped IPv6 addresses (::ffff:a.b.c.d)
        val ipv4 = when {
            address is Inet4Address && bytes.size == 4 -> bytes
            address is Inet6Address && bytes.size == 16 &&
                (0 until 10).all { bytes[it] == 0.toByte() } &&
                bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() -> bytes.copyOfRange(12, 16)
            else -> null
        }
        if (ipv4 != null) {
            val b0 = ipv4[0].toInt() and 0xFF
            val b1 = ipv4[1].toInt() and 0xFF
            return when {
                b0 == 10 -> true // 10.0.0.0/8
                b0 == 172 && b1 in 16..31 -> true // 172.16.0.0/12
                b0 == 192 && b1 == 168 -> true // 192.168.0.0/16
                else -> false // blocks 127/8, 169.254/16 (cloud metadata), 100.64/10 (CGNAT), public IPs
            }
        }
        if (address is Inet6Address && bytes.size == 16) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val isUla = (b0 and 0xFE) == 0xFC // fc00::/7
            val isLinkLocal = b0 == 0xFE && (b1 and 0xC0) == 0x80 // fe80::/10
            return isUla || isLinkLocal
        }
        return false
    }

    /** True for private LAN peers or loopback (used by inbound local servers). */
    fun isLocalOrLoopbackAddress(address: InetAddress?): Boolean {
        if (address == null) return false
        if (address.isLoopbackAddress) return true
        val bytes = address.address
        if (address is Inet6Address && bytes.size == 16 &&
            (0 until 10).all { bytes[it] == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() &&
            (bytes[12].toInt() and 0xFF) == 127
        ) {
            return true
        }
        return isPrivateLanAddress(address)
    }

    /**
     * Validates that [rawUrl] is an http(s) URL whose host resolves exclusively to a private LAN
     * address (and, when [expectedHostIp] is provided, matches that exact IP).
     */
    fun validateLanUrl(
        rawUrl: String,
        expectedHostIp: String? = null,
        allowLoopback: Boolean = false,
    ): ValidatedTarget {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: throw IOException("Invalid LAN URL")
        val scheme = uri.scheme?.lowercase() ?: throw IOException("Missing URL scheme")
        if (scheme != "http" && scheme != "https") throw IOException("Unsupported scheme: $scheme")
        if (!uri.rawUserInfo.isNullOrEmpty()) throw IOException("Userinfo is not permitted in LAN URLs")
        val rawHost = uri.host?.trim()?.removePrefix("[")?.removeSuffix("]")?.substringBefore('%')
            ?.takeIf { it.isNotEmpty() } ?: throw IOException("Missing host")
        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        if (port !in 1..65535) throw IOException("Invalid port: $port")

        val resolved = InetAddress.getAllByName(rawHost)
        if (resolved.isEmpty()) throw IOException("Host did not resolve")
        for (addr in resolved) {
            val allowed = isPrivateLanAddress(addr) || (allowLoopback && addr.isLoopbackAddress)
            if (!allowed) {
                throw IOException("Refused non-LAN destination address")
            }
        }
        if (expectedHostIp != null) {
            val expectedBytes = InetAddress.getByName(expectedHostIp.substringBefore('%')).address
            if (resolved.any { !it.address.contentEquals(expectedBytes) }) {
                throw IOException("Destination IP does not match expected LAN peer")
            }
        }
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return ValidatedTarget(
            scheme = scheme,
            host = rawHost,
            port = port,
            address = resolved.first(),
            pathAndQuery = path + query,
        )
    }

    fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        connectTimeoutMs: Int = 4_000,
        readTimeoutMs: Int = 30_000,
        expectedHostIp: String? = null,
        allowLoopback: Boolean = false,
    ): Response {
        val target = validateLanUrl(url, expectedHostIp = expectedHostIp, allowLoopback = allowLoopback)
        val socket = openSocket(target, connectTimeoutMs, readTimeoutMs)
        try {
            val hostHeader = buildHostHeader(target.host, target.port, target.scheme)
            val requestHead = StringBuilder()
                .append(sanitizeToken(method.uppercase())).append(' ')
                .append(sanitizePath(target.pathAndQuery))
                .append(" HTTP/1.1\r\n")
                .append("Host: ").append(hostHeader).append("\r\n")
                .append("Connection: close\r\n")
            var hasContentLength = false
            for ((rawName, rawValue) in headers) {
                val name = sanitizeHeaderPart(rawName)
                val value = sanitizeHeaderPart(rawValue)
                if (name.isEmpty()) continue
                if (name.equals("Host", ignoreCase = true) || name.equals("Connection", ignoreCase = true)) continue
                if (name.equals("Content-Length", ignoreCase = true)) hasContentLength = true
                requestHead.append(name).append(": ").append(value).append("\r\n")
            }
            if (body != null && !hasContentLength) {
                requestHead.append("Content-Length: ").append(body.size).append("\r\n")
            }
            requestHead.append("\r\n")

            val out = socket.getOutputStream()
            out.write(requestHead.toString().toByteArray(Charsets.US_ASCII))
            if (body != null && body.isNotEmpty()) out.write(body)
            out.flush()

            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val statusLine = readHeaderLine(input) ?: throw IOException("Empty HTTP response")
            val statusParts = statusLine.split(' ', limit = 3)
            val code = statusParts.getOrNull(1)?.toIntOrNull()
                ?: throw IOException("Malformed HTTP status line")

            val respHeaders = LinkedHashMap<String, String>()
            var count = 0
            while (true) {
                val line = readHeaderLine(input) ?: break
                if (line.isEmpty()) break
                if (++count > MaxHeaderLines) throw IOException("Too many HTTP response headers")
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    respHeaders[key] = value
                }
            }

            val chunked = respHeaders["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
            val contentLength = respHeaders["content-length"]?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
            val bodyStream: InputStream = when {
                method.equals("HEAD", ignoreCase = true) || code == 204 || code == 304 -> EmptyInputStream
                chunked -> ChunkedInputStream(input)
                contentLength >= 0L -> BoundedInputStream(input, contentLength)
                else -> input
            }
            return Response(code, respHeaders, Body(bodyStream, contentLength), socket)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun openSocket(target: ValidatedTarget, connectTimeoutMs: Int, readTimeoutMs: Int): Socket {
        val raw = Socket()
        raw.connect(InetSocketAddress(target.address, target.port), connectTimeoutMs)
        raw.soTimeout = readTimeoutMs
        if (target.scheme != "https") return raw
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, target.host, target.port, true) as SSLSocket
            ssl.soTimeout = readTimeoutMs
            ssl.startHandshake()
            ssl
        } catch (t: Throwable) {
            runCatching { raw.close() }
            throw t
        }
    }

    private fun buildHostHeader(host: String, port: Int, scheme: String): String {
        val cleanHost = sanitizeHeaderPart(host)
        val formatted = if (':' in cleanHost) "[$cleanHost]" else cleanHost
        val defaultPort = if (scheme == "https") 443 else 80
        return if (port == defaultPort) formatted else "$formatted:$port"
    }

    private fun sanitizeToken(text: String): String = text.filter { it in 'A'..'Z' || it in 'a'..'z' }

    private fun sanitizePath(text: String): String {
        if (text.any { it == '\r' || it == '\n' || it == ' ' }) throw IOException("Invalid characters in request target")
        return text
    }

    private fun sanitizeHeaderPart(text: String): String = text.replace("\r", "").replace("\n", "").trim()

    private fun readHeaderLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length >= MaxHeaderLineBytes) throw IOException("HTTP header line too long")
            sb.append(b.toChar())
        }
    }

    private object EmptyInputStream : InputStream() {
        override fun read(): Int = -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = -1
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            val b = delegate.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }

        override fun close() = delegate.close()
    }

    private class ChunkedInputStream(private val delegate: InputStream) : InputStream() {
        private var chunkRemaining = 0
        private var eof = false

        override fun read(): Int {
            val single = ByteArray(1)
            val n = read(single, 0, 1)
            return if (n <= 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (eof) return -1
            if (len == 0) return 0
            while (chunkRemaining == 0) {
                val sizeLine = readLine() ?: throw EOFException("Unexpected EOF in chunked stream")
                val hex = sizeLine.substringBefore(';').trim()
                val nextSize = hex.toIntOrNull(16)
                    ?: throw IOException("Invalid chunk size: $hex")
                if (nextSize < 0) throw IOException("Negative chunk size")
                if (nextSize == 0) {
                    // consume trailing headers until empty line
                    while (true) {
                        val trailer = readLine() ?: break
                        if (trailer.isEmpty()) break
                    }
                    eof = true
                    return -1
                }
                chunkRemaining = nextSize
            }
            val toRead = minOf(len, chunkRemaining)
            val n = delegate.read(b, off, toRead)
            if (n < 0) throw EOFException("Unexpected EOF mid-chunk")
            chunkRemaining -= n
            if (chunkRemaining == 0) {
                // consume trailing CRLF after chunk body
                readLine()
            }
            return n
        }

        private fun readLine(): String? {
            val sb = StringBuilder()
            while (true) {
                val c = delegate.read()
                if (c < 0) return if (sb.isEmpty()) null else sb.toString()
                if (c == '\n'.code) return sb.toString().trimEnd('\r')
                if (sb.length >= 1024) throw IOException("Chunk header line too long")
                sb.append(c.toChar())
            }
        }

        override fun close() = delegate.close()
    }
}
