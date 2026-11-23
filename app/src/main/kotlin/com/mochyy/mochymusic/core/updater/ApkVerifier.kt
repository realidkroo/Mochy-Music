// ApkVerifier.kt

package com.mochyy.mochymusic.core.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Security checks for Mochy's self-updater:
 *  - Restricts APK downloads and every HTTP redirect hop to HTTPS on official GitHub release hosts.
 *  - Stores downloaded APKs strictly inside private app cache (`cacheDir/updates`), preventing
 *    world-writable external-storage TOCTOU APK swaps.
 *  - Sanitizes release asset names to prevent path traversal.
 *  - Verifies package name AND signing certificate SHA-256 digest against the installed Mochy APK
 *    before prompting Android's package installer.
 */
object ApkVerifier {
    const val MaxApkBytes = 250L * 1024 * 1024
    private const val MaxRedirects = 5
    private const val UpdatesFolderName = "updates"

    private val GithubReleaseHosts = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "codeload.github.com",
    )

    fun updatesDir(context: Context): File = File(context.cacheDir, UpdatesFolderName).apply { mkdirs() }

    /** Sanitizes a remote release filename and resolves it strictly inside [updatesDir]. */
    fun safeApkFile(context: Context, rawName: String?): File {
        val dir = updatesDir(context)
        return safeApkFileIn(dir, rawName)
    }

    internal fun safeApkFileIn(dir: File, rawName: String?): File {
        val base = rawName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
            .take(120)
        val withExt = when {
            base.isEmpty() -> "mochy-update.apk"
            base.endsWith(".apk", ignoreCase = true) -> base
            else -> "$base.apk"
        }
        val file = File(dir, withExt)
        val root = dir.canonicalPath + File.separator
        check(file.canonicalPath.startsWith(root)) { "Refused update filename outside updates dir" }
        return file
    }

    /** True only for HTTPS URLs pointing at Mochy's official GitHub release hosts. */
    fun isAllowedUpdateUrl(url: String, allowNightlyLink: Boolean = false): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        val host = parsed.host.lowercase().trimEnd('.')
        val repoPrefix = "/$GithubOwner/$GithubRepo/".lowercase()
        if (host == "github.com") {
            return parsed.encodedPath.lowercase().startsWith("${repoPrefix}releases/")
        }
        if (host in GithubReleaseHosts) return true
        if (allowNightlyLink) {
            if (host == "nightly.link") {
                return parsed.encodedPath.lowercase().startsWith(repoPrefix)
            }
            if (host.endsWith(".actions.githubusercontent.com") || host.endsWith(".blob.core.windows.net")) {
                return true
            }
        }
        return false
    }

    fun isSafeExternalWebUrl(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        return parsed.isHttps
    }

    /** Opens an HTTPS connection following redirects manually while validating every hop. */
    fun openVerifiedConnection(
        startUrl: String,
        allowNightlyLink: Boolean = false,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 30_000,
    ): HttpURLConnection {
        var current = startUrl.trim()
        for (hop in 0..MaxRedirects) {
            if (!isAllowedUpdateUrl(current, allowNightlyLink)) {
                throw SecurityException("Blocked untrusted update URL: $current")
            }
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "mochy-android")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without Location header")
                current = URL(URL(current), location).toString()
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw IOException("Update server returned HTTP $code")
            }
            return conn
        }
        throw IOException("Too many redirects while downloading update")
    }

    /**
     * Verifies [apk] lives inside private [updatesDir], matches this app's packageName, and is
     * signed by the exact same signing certificate(s) as the currently installed app.
     */
    fun verifyUpdateApk(context: Context, apk: File): Boolean {
        if (!apk.isFile || apk.length() <= 0L || apk.length() > MaxApkBytes) {
            Timber.w("Update APK missing or invalid size")
            return false
        }
        val expectedRoot = runCatching { updatesDir(context).canonicalPath + File.separator }.getOrNull() ?: return false
        val actualPath = runCatching { apk.canonicalPath }.getOrNull() ?: return false
        if (!actualPath.startsWith(expectedRoot)) {
            Timber.w("Refused to install update APK outside private updates directory")
            return false
        }

        val pm = context.packageManager
        val installedDigests = installedSigningDigests(pm, context.packageName)
        if (installedDigests.isEmpty()) {
            Timber.w("Could not read installed app signing certificate")
            return false
        }

        val archiveInfo = readArchivePackageInfo(pm, apk.absolutePath) ?: run {
            Timber.w("Failed to parse downloaded update APK")
            return false
        }
        if (archiveInfo.packageName != context.packageName) {
            Timber.w("Update APK packageName mismatch: ${archiveInfo.packageName}")
            return false
        }
        val archiveDigests = signingDigestsOf(archiveInfo)
        if (archiveDigests.isEmpty() || archiveDigests != installedDigests) {
            Timber.w("Update APK signing certificate does not match installed app")
            return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun installedSigningDigests(pm: PackageManager, packageName: String): Set<String> = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        signingDigestsOf(info)
    }.getOrDefault(emptySet())

    @Suppress("DEPRECATION")
    private fun readArchivePackageInfo(pm: PackageManager, apkPath: String): PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        pm.getPackageArchiveInfo(apkPath, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingDigestsOf(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return sigs.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
