package com.mochyy.mochymusic.modules.security

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.SourceStream
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// keeps module http/streams off localhost, lan, file://, and mochy's own secrets
internal object ModuleUrlGuard {

    private const val Tag = "ModuleGuard"
    private const val MaxRedirects = 5
    private const val MaxRequestBodyBytes = 2 * 1024 * 1024
    private const val MaxResponseBodyBytes = 10 * 1024 * 1024
    private const val RateWindowMs = 10_000L
    private const val MaxRequestsPerWindow = 60

    private val AllowedMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
    private val RedirectCodes = setOf(301, 302, 303, 307, 308)
    private val rateWindows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun lolResponse(): HttpResponse = HttpResponse(
        code = 403,
        body = SecretGuard.LolJson.toByteArray(Charsets.UTF_8),
        headers = mapOf("content-type" to "application/json", "x-mochy-guard" to SecretGuard.LolText),
    )

    // blocks file://, content://, localhost, lan ips, embedded creds, and mochy's own hosts
    fun isSafeRemoteUrl(raw: String?, checkBlockedHosts: Boolean = true): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 4096) return false
        if (text.any { it.code < 0x20 || it.code == 0x7F }) return false
        val uri = runCatching { URI(text) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        if (!uri.rawUserInfo.isNullOrEmpty()) return false
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return false
        return isSafeHostName(host, checkBlockedHosts)
    }

    fun isSafeHostName(rawHost: String, checkBlockedHosts: Boolean = true): Boolean {
        val host = rawHost.trim().lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (host.isEmpty()) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa")) {
            return false
        }
        if (checkBlockedHosts && SecretGuard.isBlockedHost(host)) return false
        parseIpLiteral(host)?.let { if (isBlockedAddress(it)) return false }
        // catch sneaky octal/hex/decimal ipv4 encodings like 0x7f000001 or 2130706433
        if (looksLikeSneakyIp(host)) return false
        return true
    }

    fun isBlockedAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isMulticastAddress) {
            return true
        }
        val bytes = addr.address
        if (addr is Inet4Address && bytes.size == 4) {
            return isBlockedIpv4(bytes)
        }
        if (addr is Inet6Address && bytes.size == 16) {
            // ipv4-mapped ::ffff:a.b.c.d
            val mapped = (0..9).all { bytes[it] == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
            if (mapped) return isBlockedIpv4(bytes.copyOfRange(12, 16))
            // ula fc00::/7
            val b0 = bytes[0].toInt() and 0xFF
            if ((b0 and 0xFE) == 0xFC) return true
            // link-local fe80::/10
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
        }
        return false
    }

    private fun isBlockedIpv4(b: ByteArray): Boolean {
        val a = b[0].toInt() and 0xFF
        val c = b[1].toInt() and 0xFF
        return when {
            a == 0 -> true // 0.0.0.0/8
            a == 10 -> true // 10.0.0.0/8
            a == 127 -> true // loopback
            a == 100 && c in 64..127 -> true // cgnat 100.64.0.0/10
            a == 169 && c == 254 -> true // link-local / cloud metadata
            a == 172 && c in 16..31 -> true // 172.16.0.0/12
            a == 192 && c == 168 -> true // 192.168.0.0/16
            a == 198 && (c == 18 || c == 19) -> true // benchmark
            a >= 224 -> true // multicast / reserved / broadcast
            else -> false
        }
    }

    // parse plain ipv4/ipv6 literals without touching dns
    private fun parseIpLiteral(host: String): InetAddress? {
        val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host)
        if (ipv4 != null) {
            val parts = ipv4.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            if (parts.any { it !in 0..255 }) return null
            return runCatching { InetAddress.getByAddress(ByteArray(4) { parts[it].toByte() }) }.getOrNull()
        }
        if (':' in host) {
            return runCatching { InetAddress.getByName(host) }.getOrNull()
        }
        return null
    }

    // rejects 2130706433, 0x7f.0.0.1, 0177.0.0.1 etc. so nobody slips past with weird ip math
    private fun looksLikeSneakyIp(host: String): Boolean {
        if (host.all { it.isDigit() }) return true
        if (host.startsWith("0x", ignoreCase = true) && host.drop(2).all { it.isDigit() || it in 'a'..'f' }) return true
        val parts = host.split('.')
        if (parts.size in 2..4 && parts.all { p ->
                p.isNotEmpty() && (p.all { it.isDigit() } || (p.startsWith("0x", true) && p.drop(2).all { it.isDigit() || it in 'a'..'f' }))
            }
        ) {
            // standard dotted ipv4 was already checked by parseIpLiteral; anything else with leading zeros / hex / <4 octets is sketchy
            if (parts.size != 4 || parts.any { it.startsWith("0x", true) || (it.length > 1 && it.startsWith("0")) }) return true
        }
        return false
    }

    // checks resolved ips too so dns rebinding like 127.0.0.1.nip.io gets slammed shut
    val SafeModuleDns: Dns = Dns { hostname ->
        if (!isSafeHostName(hostname)) throw UnknownHostException("lol blocked host: $hostname")
        val resolved = Dns.SYSTEM.lookup(hostname)
        if (resolved.isEmpty() || resolved.any(::isBlockedAddress)) {
            throw UnknownHostException("lol blocked address for $hostname")
        }
        resolved
    }

    fun buildSandboxedClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .dns(SafeModuleDns)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    // runs a module's http call inside the sandbox; any cred leak or ssrf attempt gets greeted with lol
    fun executeSandboxed(client: OkHttpClient, moduleId: String, request: HttpRequest): HttpResponse {
        if (!allowRate(moduleId)) {
            Timber.tag(Tag).w("rate limited module %s", moduleId)
            return HttpResponse(429, """{"error":"slow down"}""".toByteArray(), mapOf("content-type" to "application/json"))
        }
        if (!isSafeRemoteUrl(request.url)) {
            Timber.tag(Tag).w("blocked unsafe url from %s", moduleId)
            return lolResponse()
        }
        val bodyBytes = request.body
        if (bodyBytes != null && bodyBytes.size > MaxRequestBodyBytes) {
            return HttpResponse(413, """{"error":"body too big"}""".toByteArray(), mapOf("content-type" to "application/json"))
        }
        if (SecretGuard.containsSecret(request.url) || SecretGuard.containsSecret(bodyBytes) ||
            request.headers.any { (k, v) -> SecretGuard.containsSecret(k) || SecretGuard.containsSecret(v) }
        ) {
            Timber.tag(Tag).w("caught module %s trying to exfil a secret, greeting with lol", moduleId)
            return lolResponse()
        }

        val method = request.method.uppercase().takeIf { it in AllowedMethods } ?: "GET"
        var currentUrl = request.url.trim()
        var currentMethod = method
        var currentBody = if (currentMethod == "GET" || currentMethod == "HEAD") null else bodyBytes
        var currentHeaders = ModuleSanitizer.sanitizeHeaders(request.headers)

        for (hop in 0..MaxRedirects) {
            val parsed = currentUrl.toHttpUrlOrNull() ?: return lolResponse()
            if (!isSafeRemoteUrl(parsed.toString())) return lolResponse()

            val builder = Request.Builder().url(parsed)
            currentHeaders.forEach { (k, v) -> builder.header(k, v) }
            val bodiless = currentMethod == "GET" || currentMethod == "HEAD"
            val reqBody = if (bodiless) null else (currentBody ?: ByteArray(0)).toRequestBody(request.contentType?.toMediaTypeOrNull())
            builder.method(currentMethod, reqBody)

            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: UnknownHostException) {
                if (e.message?.contains("lol blocked") == true) return lolResponse()
                throw e
            }

            response.use { res ->
                if (res.code in RedirectCodes) {
                    val location = res.header("Location") ?: return HttpResponse(res.code, ByteArray(0), headerMap(res))
                    val next = parsed.resolve(location) ?: return lolResponse()
                    if (!isSafeRemoteUrl(next.toString()) || SecretGuard.containsSecret(next.toString())) return lolResponse()
                    if (hop == MaxRedirects) throw IOException("too many redirects")
                    // drop auth headers if a redirect jumps to another host
                    if (!next.host.equals(parsed.host, ignoreCase = true)) {
                        currentHeaders = currentHeaders.filterKeys { !it.equals("authorization", true) && !it.equals("cookie", true) }
                    }
                    if (res.code == 303 || ((res.code == 301 || res.code == 302) && currentMethod == "POST")) {
                        currentMethod = "GET"
                        currentBody = null
                    }
                    currentUrl = next.toString()
                    return@use
                }
                val data = readCapped(res.body.byteStream(), MaxResponseBodyBytes)
                return HttpResponse(res.code, data, headerMap(res))
            }
        }
        throw IOException("too many redirects")
    }

    // safe text fetch for manifests/updates with ssrf check, redirect checks, and size cap
    fun fetchCappedText(client: OkHttpClient, url: String, maxBytes: Int): String? {
        if (!isSafeRemoteUrl(url)) return null
        var current = url.trim()
        for (hop in 0..MaxRedirects) {
            val parsed = current.toHttpUrlOrNull() ?: return null
            if (!isSafeRemoteUrl(parsed.toString())) return null
            val req = Request.Builder().url(parsed).header("Accept", "application/json").build()
            val text = client.newCall(req).execute().use { res ->
                if (res.code in RedirectCodes) {
                    val loc = res.header("Location") ?: return null
                    val next = parsed.resolve(loc) ?: return null
                    if (!isSafeRemoteUrl(next.toString())) return null
                    current = next.toString()
                    return@use null
                }
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                readCapped(res.body.byteStream(), maxBytes).decodeToString()
            }
            if (text != null) return text
        }
        return null
    }

    // strips shady stream urls/headers before exoplayer or downloads ever touch them
    fun sanitizeStream(stream: SourceStream): SourceStream? {
        if (!isSafeRemoteUrl(stream.url)) {
            Timber.tag(Tag).w("rejected unsafe stream url from %s", stream.sourceLabel)
            return null
        }
        if (SecretGuard.containsSecret(stream.url)) {
            Timber.tag(Tag).w("blocked secret leak in stream url from %s", stream.sourceLabel)
            return null
        }
        return stream.copy(headers = ModuleSanitizer.sanitizeHeaders(stream.headers))
    }

    private fun readCapped(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("response too big")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun headerMap(res: okhttp3.Response): Map<String, String> =
        res.headers.associate { (name, value) -> name.lowercase() to value }

    private fun allowRate(moduleId: String): Boolean {
        val now = System.currentTimeMillis()
        val queue = rateWindows.computeIfAbsent(moduleId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty()) {
                val oldest = queue.peekFirst() ?: break
                if (now - oldest <= RateWindowMs) break
                queue.removeFirst()
            }
            if (queue.size >= MaxRequestsPerWindow) return false
            queue.addLast(now)
            return true
        }
    }
}
