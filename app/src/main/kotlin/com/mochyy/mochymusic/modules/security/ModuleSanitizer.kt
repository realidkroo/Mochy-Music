package com.mochyy.mochymusic.modules.security

import java.io.File

// keeps module ids, file paths, nav args, and headers from escaping their lane
internal object ModuleSanitizer {

    private val UnsafeChars = Regex("[^a-z0-9._-]")
    private val ValidId = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$")
    private val ValidNavArg = Regex("^[A-Za-z0-9._~:+=-]{1,160}$")

    private val ReservedIds = setOf(
        "youtube", "settings", "source_modules", "module_browser", "module_looks", "default",
    )

    // never lets "." or ".." slip into a folder or prefs name
    fun safeSegment(raw: String): String {
        val cleaned = raw.trim().lowercase()
            .replace(UnsafeChars, "-")
            .replace(Regex("\\.{2,}"), "-")
            .trimStart('.')
            .trimEnd('.')
        return cleaned.take(80).ifBlank { "module" }
    }

    // builds a path under [root] and refuses anything that climbs out
    fun safeChild(root: File, vararg parts: String): File? {
        if (parts.isEmpty()) return null
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return null
        var cur = base
        for (part in parts) {
            if (part.isBlank() || part == "." || part == ".." || '/' in part || '\\' in part || '\u0000' in part) return null
            cur = File(cur, part)
        }
        val resolved = runCatching { cur.canonicalFile }.getOrNull() ?: return null
        val prefix = base.path.trimEnd(File.separatorChar) + File.separatorChar
        return resolved.takeIf { it.path.startsWith(prefix) }
    }

    fun isValidModuleId(id: String): Boolean {
        val trimmed = id.trim()
        if (!ValidId.matches(trimmed)) return false
        if (".." in trimmed) return false
        return trimmed.lowercase() !in ReservedIds
    }

    // blocks ../ and ?query tricks when a module page navigates to an album/artist/playlist
    fun safeNavArg(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || ".." in trimmed) return null
        return trimmed.takeIf { ValidNavArg.matches(it) }
    }

    // drops crlf injection, forbidden headers, and any secret smuggling
    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        if (headers.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((rawName, rawValue) in headers) {
            if (out.size >= MaxHeaders) break
            val name = rawName.trim()
            val value = rawValue.trim()
            if (name.isEmpty() || name.length > MaxHeaderName || value.length > MaxHeaderValue) continue
            if (hasControlChars(name) || hasControlChars(value)) continue
            if (name.lowercase() in ForbiddenHeaders) continue
            if (SecretGuard.containsSecret(name) || SecretGuard.containsSecret(value)) continue
            out[name] = value
        }
        return out
    }

    private fun hasControlChars(text: String): Boolean = text.any { it.code < 0x20 || it.code == 0x7F }

    private const val MaxHeaders = 32
    private const val MaxHeaderName = 128
    private const val MaxHeaderValue = 4096

    private val ForbiddenHeaders = setOf(
        "host", "proxy-authorization", "x-mochy-sign", "x-mochy-time", "transfer-encoding", "content-length",
    )
}
