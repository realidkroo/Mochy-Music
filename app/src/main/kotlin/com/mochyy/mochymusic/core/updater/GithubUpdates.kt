package com.mochyy.mochymusic.core.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mochyy.mochymusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val GithubOwner = "realidkroo"
const val GithubRepo = "Mochy-Music"

const val GithubProfileUrl = "https://github.com/$GithubOwner"
const val GithubRepoUrl = "https://github.com/$GithubOwner/$GithubRepo"
const val InstagramUrl = "https://www.instagram.com/furfurrfoxx/"
// Threads names follow the Instagram one
const val ThreadsUrl = "https://www.threads.net/@furfurrfoxx"
// dev spaces hides the discord row while this is blank
const val DiscordUrl = "https://discord.gg/NTmqVphbRY"

private const val ReleasesApi =
    "https://api.github.com/repos/$GithubOwner/$GithubRepo/releases?per_page=20"

data class GithubRelease(
    val title: String,
    val tag: String,
    val version: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    // marked as a prerelease on github, which is what makes it a nightly
    val isNightly: Boolean = false,
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

// a release is x.y.z, a nightly adds a fourth number counting up from the release it was built on (8.3.2.1, 8.3.2.2, then 8.3.3)
data class FullVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val buildNumber: Int = 0,
    val nightly: Int = 0,
) : Comparable<FullVersion> {
    override fun compareTo(other: FullVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (nightly != other.nightly) return nightly.compareTo(other.nightly)
        return buildNumber.compareTo(other.buildNumber)
    }

    // what people read: 8.3.2, 8.3.2.4 for a nightly
    val name: String get() = if (nightly > 0) "$major.$minor.$patch.$nightly" else "$major.$minor.$patch"
}

private val VersionRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?""")
private val BuildNumberRegex = Regex("""(?:build#|\(#|#|\()(\d+)\)?""", RegexOption.IGNORE_CASE)

fun parseFullVersion(text: String): FullVersion {
    val version = VersionRegex.find(text)?.groupValues
    val buildNumber = BuildNumberRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return FullVersion(
        major = version?.get(1)?.toIntOrNull() ?: 0,
        minor = version?.get(2)?.toIntOrNull() ?: 0,
        patch = version?.getOrNull(3)?.toIntOrNull() ?: 0,
        buildNumber = buildNumber,
        nightly = version?.getOrNull(4)?.toIntOrNull() ?: 0,
    )
}

private const val CacheTtlMillis = 15L * 60L * 1000L

// older releases in this repo are the app under its old id, so "downgrade" to one would add a second app instead
private val FirstReleaseWithThisId = FullVersion(major = 8, minor = 2, patch = 3)

/** A release with the version it was parsed to, so it can be compared without parsing again. */
private class ParsedRelease(val version: FullVersion, val release: GithubRelease)

private val cacheLock = Any()
private var cachedAt = 0L
private var cachedResult: Result<List<ParsedRelease>>? = null
private var inFlight: kotlinx.coroutines.Deferred<Result<List<ParsedRelease>>>? = null

private val updateScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
)

/** Every published release, asked for once and shared for [CacheTtlMillis] unless [force]d. */
private suspend fun getReleases(force: Boolean): Result<List<ParsedRelease>> {
    val deferred = synchronized(cacheLock) {
        if (!force) {
            val cached = cachedResult
            if (cached != null && System.currentTimeMillis() - cachedAt < CacheTtlMillis) {
                return cached
            }
        }
        inFlight ?: updateScope.async { fetchReleases() }.also { inFlight = it }
    }

    val result = deferred.await()
    synchronized(cacheLock) {
        if (inFlight === deferred) inFlight = null
        if (result.isSuccess) {
            cachedResult = result
            cachedAt = System.currentTimeMillis()
        }
    }
    return result
}

// release only sees releases; nightly sees the prereleases and the releases, and takes the newest of all
private fun ParsedRelease.inChannel(channel: UpdateChannel) = channel == UpdateChannel.Nightly || !release.isNightly

/** The newest release on [channel], whatever is installed. */
suspend fun getLatestRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> =
    getReleases(force).map { list -> list.filter { it.inChannel(channel) }.maxByOrNull { it.version }?.release }

/**
 * What "Downgrade to" offers, null when there's none: on release, the newest release before the
 * installed x.y.z (an older build of the same version doesn't count); on nightly, the nightly before
 * this one. A nightly never steps down to a release, that can break the library.
 */
suspend fun getPreviousRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> {
    val installed = parseFullVersion(BuildConfig.VERSION_NAME).copy(buildNumber = 0)
    return getReleases(force).map { list ->
        when {
            channel == UpdateChannel.Release && installedIsNightly -> null
            channel == UpdateChannel.Release -> list.filter { !it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
            !installedIsNightly -> null
            else -> list.filter { it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
        }?.maxByOrNull { it.version }?.release
    }
}

private suspend fun fetchReleases(): Result<List<ParsedRelease>> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(ReleasesApi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "mochy-android")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(json)
        val appArch = runCatching { BuildConfig.ARCHITECTURE }.getOrDefault("universal")

        buildList {
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue

                val tag = release.optString("tag_name").orEmpty()
                val title = release.optString("name").orEmpty().ifBlank { tag }
                val fullVer = parseFullVersion("$title $tag")

                if (fullVer.major == 0 && fullVer.minor == 0 && fullVer.patch == 0) continue
                if (fullVer.copy(buildNumber = 0) < FirstReleaseWithThisId) continue

                val displayVersion = if (fullVer.buildNumber > 0) "${fullVer.name} (${fullVer.buildNumber})" else fullVer.name

                val assets = release.optJSONArray("assets")
                var selectedUrl: String? = null
                var selectedName: String? = null
                var selectedSize = 0L

                var universalUrl: String? = null
                var universalName: String? = null
                var universalSize = 0L

                var fallbackUrl: String? = null
                var fallbackName: String? = null
                var fallbackSize = 0L

                if (assets != null) {
                    for (a in 0 until assets.length()) {
                        val asset = assets.getJSONObject(a)
                        val name = asset.optString("name").orEmpty()
                        if (!name.endsWith(".apk", ignoreCase = true)) continue
                        val url = asset.optString("browser_download_url").orEmpty()
                        val size = asset.optLong("size", 0L)

                        if (appArch.isNotBlank() && appArch != "universal" && name.contains(appArch, ignoreCase = true)) {
                            selectedUrl = url
                            selectedName = name
                            selectedSize = size
                        } else if (name.contains("universal", ignoreCase = true)) {
                            universalUrl = url
                            universalName = name
                            universalSize = size
                        } else if (fallbackUrl == null) {
                            fallbackUrl = url
                            fallbackName = name
                            fallbackSize = size
                        }
                    }
                }

                val apkUrl = selectedUrl ?: universalUrl ?: fallbackUrl
                val apkName = selectedName ?: universalName ?: fallbackName
                val apkSize = when {
                    selectedUrl != null -> selectedSize
                    universalUrl != null -> universalSize
                    else -> fallbackSize
                }

                add(
                    ParsedRelease(
                        version = fullVer,
                        release = GithubRelease(
                            title = title,
                            tag = tag,
                            version = displayVersion,
                            body = release.optString("body").orEmpty(),
                            htmlUrl = release.optString("html_url").orEmpty().ifBlank { "$GithubRepoUrl/releases" },
                            apkUrl = apkUrl,
                            apkName = apkName,
                            apkSizeBytes = apkSize,
                            publishedAt = release.optString("published_at").orEmpty(),
                            isNightly = release.optBoolean("prerelease", false),
                        ),
                    ),
                )
            }
        }
    }
}

fun isNewerThanInstalled(candidateVersionText: String): Boolean {
    val candidate = parseFullVersion(candidateVersionText)
    val installed = parseFullVersion(BuildConfig.VERSION_NAME)
    return candidate > installed
}

suspend fun downloadApk(
    context: Context,
    release: GithubRelease,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val url = release.apkUrl ?: error("This release has no APK attached")
        val target = apkFileFor(context, release)

        if (isDownloaded(context, release)) {
            onProgress(1f)
            return@runCatching target
        }
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mochy-android")
        }
        val total = release.apkSizeBytes.takeIf { it > 0 }
            ?: connection.contentLengthLong.takeIf { it > 0 }
            ?: -1L

        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        target
    }
}

fun apkFileFor(context: Context, release: GithubRelease): File =
    File(context.cacheDir, release.apkName ?: "mochy-update.apk")

fun isDownloaded(context: Context, release: GithubRelease): Boolean {
    val f = apkFileFor(context, release)
    if (!f.exists() || f.length() <= 0L) return false
    return release.apkSizeBytes <= 0L || f.length() == release.apkSizeBytes
}

fun canInstallPackages(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

fun requestInstallPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun installApk(context: Context, apk: File): Boolean {
    if (!canInstallPackages(context)) {
        requestInstallPermission(context)
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
