package com.mochyy.mochymusic.modules.security

import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// watches for modules trying to sneak yt cookies, webdav pass, tokens, etc. out of the app
internal object SecretGuard {

    const val LolText = "lol"
    const val LolJson = """{"error":"lol","message":"lol nice try"}"""

    private val secretsBySlot = ConcurrentHashMap<String, Set<String>>()
    private val hostsBySlot = ConcurrentHashMap<String, String>()

    // mochy's own store backend is off-limits to modules
    private val alwaysBlockedHosts = setOf("mochy-module-feed.mochyy.workers.dev")

    fun register(slot: String, vararg values: String?) {
        val needles = buildSet {
            values.forEach { raw ->
                val clean = raw?.trim().orEmpty()
                if (clean.length < MinSecretLen) return@forEach
                addVariants(clean)
                // yt cookies bundle auth pairs (even a single SAPISID=... pair); watch each value too
                if ('=' in clean) {
                    clean.split(';').forEach { part ->
                        val name = part.substringBefore('=').trim()
                        val value = part.substringAfter('=', "").trim()
                        if (name in CookieNames && value.length >= MinSecretLen) addVariants(value)
                    }
                }
            }
        }
        if (needles.isEmpty()) secretsBySlot.remove(slot) else secretsBySlot[slot] = needles
    }

    fun registerBlockedUrl(slot: String, url: String?) {
        val host = url?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { URI(it).host?.lowercase()?.trimEnd('.') }.getOrNull()
        }
        if (host.isNullOrBlank()) hostsBySlot.remove(slot) else hostsBySlot[slot] = host
    }

    fun clear(slot: String) {
        secretsBySlot.remove(slot)
        hostsBySlot.remove(slot)
    }

    fun isBlockedHost(host: String): Boolean {
        val clean = host.lowercase().trimEnd('.')
        if (clean in alwaysBlockedHosts) return true
        return hostsBySlot.values.any { blocked -> clean == blocked || clean.endsWith(".$blocked") }
    }

    fun containsSecret(text: String?): Boolean {
        if (text.isNullOrEmpty() || secretsBySlot.isEmpty()) return false
        for (set in secretsBySlot.values) {
            for (needle in set) {
                if (text.contains(needle)) return true
            }
        }
        return false
    }

    fun containsSecret(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty() || secretsBySlot.isEmpty()) return false
        return containsSecret(String(bytes, Charsets.UTF_8))
    }

    // scrub anything sensitive before it hits logcat
    fun redact(text: String): String {
        if (text.isEmpty() || secretsBySlot.isEmpty()) return text
        var out = text
        for (set in secretsBySlot.values) {
            for (needle in set) {
                if (needle in out) out = out.replace(needle, LolText)
            }
        }
        return out
    }

    // catches shady modules poking storage keys for mochy's creds
    fun looksLikeSecretProbe(key: String): Boolean {
        val lower = key.lowercase().trim()
        if (lower.contains("..") || lower.contains('/') || lower.contains('\\')) return true
        return ProbePatterns.any { it in lower }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun MutableSet<String>.addVariants(secret: String) {
        add(secret)
        runCatching { URLEncoder.encode(secret, "UTF-8") }.getOrNull()?.takeIf { it != secret && it.length >= MinSecretLen }?.let(::add)
        // strip trailing '=' so both padded and unpadded base64 smuggling get caught
        runCatching { Base64.encode(secret.toByteArray(Charsets.UTF_8)).trimEnd('=') }
            .getOrNull()?.takeIf { it.length >= MinSecretLen }?.let(::add)
    }

    private const val MinSecretLen = 8

    private val CookieNames = setOf(
        "SAPISID", "PAPISID", "SID", "HSID", "SSID", "APISID", "LOGIN_INFO",
        "__Secure-3PSID", "__Secure-1PSID", "__Secure-3PAPISID", "__Secure-1PAPISID",
        "__Secure-3PSIDCC", "__Secure-1PSIDCC",
    )

    private val ProbePatterns = listOf(
        "innertube", "sapisid", "papisid", "webdav", "cloud_backup", "mochy_secrets",
        "vault_k", "secretvault", "secretpreferences", "settings.preferences",
        "discordaccess", "discordrefresh", "lastfmsession", "librefmsession",
        "openrouter", "deeplapi", "proxypassword", "turnstilebypass",
        "module_browser", "shared_prefs", "no_backup",
    )
}
