package com.mochyy.mochymusic.modules.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

enum class ScriptVerdict { Clean, Warn, Blocked }

enum class FindingKind {
    SecretAccess,
    LocalNetwork,
    CodeEscape,
    ZipUnsafe,
    DynamicEval,
    Obfuscated,
    PlainHttp,
}

data class ScriptFinding(
    val kind: FindingKind,
    val blocked: Boolean,
    val file: String,
    val detail: String,
)

data class ScriptScanResult(
    val verdict: ScriptVerdict,
    val scriptNames: List<String>,
    val findings: List<ScriptFinding>,
)

// scans script zips, raw js, and links before they ever reach quickjs
internal object ScriptSecurityScanner {

    fun scanBytes(fileName: String, bytes: ByteArray): ScriptScanResult {
        if (bytes.size > MaxZipBytes) {
            return ScriptScanResult(
                ScriptVerdict.Blocked,
                emptyList(),
                listOf(ScriptFinding(FindingKind.ZipUnsafe, true, fileName, "file exceeds 25 MB limit")),
            )
        }
        return if (looksLikeZip(bytes)) scanZip(fileName, bytes) else scanSingleScript(fileName, bytes)
    }

    fun scanLink(rawLink: String): ScriptScanResult {
        val link = rawLink.trim()
        if (!ModuleUrlGuard.isSafeRemoteUrl(link)) {
            return ScriptScanResult(
                ScriptVerdict.Blocked,
                listOf(link),
                listOf(ScriptFinding(FindingKind.LocalNetwork, true, link, "points to localhost, local network, or blocked host")),
            )
        }
        val findings = mutableListOf<ScriptFinding>()
        if (link.startsWith("http://", ignoreCase = true)) {
            findings += ScriptFinding(FindingKind.PlainHttp, false, link, "uses unencrypted http://")
        }
        if (SecretGuard.containsSecret(link)) {
            findings += ScriptFinding(FindingKind.SecretAccess, true, link, "carries a Mochy secret")
        }
        return resultOf(listOf(link.substringAfter("://").take(60)), findings)
    }

    private fun scanZip(zipName: String, bytes: ByteArray): ScriptScanResult {
        val findings = mutableListOf<ScriptFinding>()
        val scripts = mutableListOf<String>()
        var entries = 0
        var totalUncompressed = 0L

        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MaxEntries) {
                        findings += ScriptFinding(FindingKind.ZipUnsafe, true, zipName, "zip has way too many entries")
                        break
                    }
                    val name = entry.name.trimStart('/')
                    if (entry.isDirectory || name.startsWith("__MACOSX/")) continue
                    // check traversal BEFORE skipping dotfiles, since ".." starts with "."
                    if ('\\' in name || '\u0000' in name || name.split('/').any { it == ".." }) {
                        findings += ScriptFinding(FindingKind.ZipUnsafe, true, name, "path traversal inside zip")
                        continue
                    }
                    if (name.split('/').any { it.startsWith(".") }) continue
                    val content = readEntryCapped(zip, minOf(MaxEntryBytes, MaxTotalUncompressed - totalUncompressed))
                    if (content == null) {
                        findings += ScriptFinding(FindingKind.ZipUnsafe, true, name, "zip entry is suspiciously huge")
                        break
                    }
                    totalUncompressed += content.size
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in ScriptExtensions) {
                        scripts += name.substringAfterLast('/')
                        inspectSource(name, content.decodeToString(), findings)
                    } else if (ext == "json") {
                        inspectManifestJson(name, content.decodeToString(), findings)
                    }
                }
            }
        }.onFailure {
            findings += ScriptFinding(FindingKind.ZipUnsafe, true, zipName, "corrupted zip archive")
        }

        return resultOf(scripts.distinct().take(20), findings)
    }

    private fun scanSingleScript(name: String, bytes: ByteArray): ScriptScanResult {
        val findings = mutableListOf<ScriptFinding>()
        inspectSource(name, bytes.decodeToString(), findings)
        return resultOf(listOf(name), findings)
    }

    private fun inspectSource(file: String, source: String, findings: MutableList<ScriptFinding>) {
        for ((pattern, label) in BlockedSecretRules) {
            if (pattern.containsMatchIn(source)) {
                findings += ScriptFinding(FindingKind.SecretAccess, true, file, label)
            }
        }
        for ((pattern, label) in BlockedLocalRules) {
            if (pattern.containsMatchIn(source)) {
                findings += ScriptFinding(FindingKind.LocalNetwork, true, file, label)
            }
        }
        for ((pattern, label) in BlockedEscapeRules) {
            if (pattern.containsMatchIn(source)) {
                findings += ScriptFinding(FindingKind.CodeEscape, true, file, label)
            }
        }
        for ((pattern, label) in WarnEvalRules) {
            if (pattern.containsMatchIn(source)) {
                findings += ScriptFinding(FindingKind.DynamicEval, false, file, label)
            }
        }
        if (ObfuscationHex.findAll(source).take(25).count() >= 25 || ObfuscationFromCharCode.containsMatchIn(source)) {
            findings += ScriptFinding(FindingKind.Obfuscated, false, file, "heavily obfuscated strings")
        }
        if (PlainHttpUrl.containsMatchIn(source)) {
            findings += ScriptFinding(FindingKind.PlainHttp, false, file, "talks to plain http:// URLs")
        }
    }

    private fun inspectManifestJson(file: String, json: String, findings: MutableList<ScriptFinding>) {
        for ((pattern, label) in BlockedLocalRules) {
            if (pattern.containsMatchIn(json)) {
                findings += ScriptFinding(FindingKind.LocalNetwork, true, file, label)
            }
        }
        for ((pattern, label) in BlockedSecretRules) {
            if (pattern.containsMatchIn(json)) {
                findings += ScriptFinding(FindingKind.SecretAccess, true, file, label)
            }
        }
    }

    private fun resultOf(scripts: List<String>, findings: List<ScriptFinding>): ScriptScanResult {
        val unique = findings.distinctBy { it.kind to it.file }
        val verdict = when {
            unique.any { it.blocked } -> ScriptVerdict.Blocked
            unique.isNotEmpty() -> ScriptVerdict.Warn
            else -> ScriptVerdict.Clean
        }
        return ScriptScanResult(verdict, scripts, unique)
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun readEntryCapped(zip: ZipInputStream, limit: Long): ByteArray? {
        if (limit <= 0) return null
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) return out.toByteArray()
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
    }

    private const val MaxEntries = 500
    private const val MaxZipBytes = 25 * 1024 * 1024
    private const val MaxEntryBytes = 5L * 1024 * 1024
    private const val MaxTotalUncompressed = 25L * 1024 * 1024
    private val ScriptExtensions = setOf("js", "mjs", "cjs")

    // stuff a legit music script has zero damn business touching
    private val BlockedSecretRules = listOf(
        Regex("""innerTubeCookie|SAPISID|PAPISID|__Secure-3PSID""", RegexOption.IGNORE_CASE) to "references YouTube login cookies",
        Regex("""cloud_backup_account|\.vault_k|mochy_secrets|SecretVault|SecretPreferences""", RegexOption.IGNORE_CASE) to "targets Mochy secret storage",
        Regex("""webdav[_-]?(pass|pwd|password|user|auth)|proxyPassword|discordAccessToken|discordRefreshToken""", RegexOption.IGNORE_CASE) to "targets saved account passwords/tokens",
        Regex("""settings\.preferences_pb|/data/data/|/data/user/|shared_prefs|no_backup""", RegexOption.IGNORE_CASE) to "probes private app files",
        Regex("""mochy-module-feed\.mochyy\.workers\.dev""", RegexOption.IGNORE_CASE) to "targets Mochy's own module store API",
    )

    private val BlockedLocalRules = listOf(
        Regex("""\bfile://|\bcontent://""", RegexOption.IGNORE_CASE) to "uses local file:// or content:// URIs",
        Regex("""https?://(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1]|169\.254\.169\.254)\b""", RegexOption.IGNORE_CASE) to "probes localhost or local metadata",
    )

    private val BlockedEscapeRules = listOf(
        Regex("""Runtime\.getRuntime|ProcessBuilder|DexClassLoader|PathClassLoader|CookieManager|java\.lang\.Runtime""") to "tries to touch Android/JVM internals",
    )

    private val WarnEvalRules = listOf(
        Regex("""\beval\s*\(""") to "runs dynamic eval()",
        Regex("""\bnew\s+Function\s*\(""") to "builds code dynamically with Function()",
    )

    private val ObfuscationHex = Regex("""\\x[0-9a-fA-F]{2}""")
    private val ObfuscationFromCharCode = Regex("""String\.fromCharCode\s*\(([^)]{40,})\)""")
    private val PlainHttpUrl = Regex("""http://(?!(localhost|127\.0\.0\.1))""", RegexOption.IGNORE_CASE)
}
