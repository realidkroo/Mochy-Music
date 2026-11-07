// a github project's link turned into the module zip on its newest release, the one with mochyymodule in its file name

package com.mochyy.mochymusic.modules

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal object GitHubReleases {

    /** a project, and the release [tag] when the link named one; null tag means the newest */
    data class Link(val owner: String, val repo: String, val tag: String?)

    sealed interface Found {
        /** the zip in the cache, and what the add sheet shows it came from */
        data class Zip(val file: File, val from: String) : Found
        data object NoRelease : Found
        data object NoModule : Found
        data object Unreachable : Found
    }

    /** github.com/owner/repo, with /releases, /releases/latest, /releases/tag/v1 or /tree/… after it; a file link (/blob/, /raw/) stays a plain link */
    fun parse(link: String): Link? {
        val trimmed = link.trim().removeSuffix("/")
        val rest = trimmed.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (!rest.startsWith("github.com/", ignoreCase = true)) return null
        val parts = rest.substringAfter('/').substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (!Name.matches(owner) || !Name.matches(repo)) return null
        if (parts.getOrNull(2) in FileViews) return null
        val tag = if (parts.getOrNull(2) == "releases" && parts.getOrNull(3) == "tag") parts.getOrNull(4) else null
        return Link(owner, repo, tag)
    }

    /** the release's module zip, downloaded into the cache; blocking, so off the main thread */
    fun download(client: OkHttpClient, cacheDir: File, link: Link, preferDebug: Boolean): Found {
        val api = "https://api.github.com/repos/${link.owner}/${link.repo}/releases/" + (link.tag?.let { "tags/$it" } ?: "latest")
        val release = try {
            client.newCall(apiRequest(api)).execute().use { response ->
                // latest skips drafts and pre-releases, so a project with only those reads as having none
                if (response.code == 404) return Found.NoRelease
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                JSONObject(response.body.string())
            }
        } catch (e: Exception) {
            return Found.Unreachable
        }
        val asset = pickAsset(release, preferDebug) ?: return Found.NoModule
        val folder = File(cacheDir, "github_modules").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(folder, asset.first.replace(Regex("[^A-Za-z0-9._-]"), "-"))
        return try {
            client.newCall(Request.Builder().url(asset.second).header("User-Agent", "Mochy").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                copyCapped(response.body.byteStream(), target)
            }
            val tag = release.optString("tag_name").takeIf { it.isNotBlank() }
            Found.Zip(target, "github.com/${link.owner}/${link.repo}" + (tag?.let { " · $it" } ?: ""))
        } catch (e: Exception) {
            target.delete()
            Found.Unreachable
        }
    }

    // the zips with mochyymodule in their name; with a debug and a release one side by side, the one signed like this mochy
    private fun pickAsset(release: JSONObject, preferDebug: Boolean): Pair<String, String>? {
        val assets = release.optJSONArray("assets") ?: return null
        val zips = (0 until assets.length()).mapNotNull { i ->
            val asset = assets.optJSONObject(i) ?: return@mapNotNull null
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            val fits = name.contains(Marker, ignoreCase = true) && name.endsWith(".zip", ignoreCase = true) && url.startsWith("https://")
            if (fits && asset.optLong("size") <= MaxZipBytes) name to url else null
        }
        if (zips.size <= 1) return zips.firstOrNull()
        return zips.firstOrNull { it.first.contains("debug", ignoreCase = true) == preferDebug } ?: zips.first()
    }

    private fun apiRequest(url: String) = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Mochy")
        .build()

    // a release asset can be anything, so stop reading once it's past what a module zip could ever be
    private fun copyCapped(input: java.io.InputStream, file: File) {
        var written = 0L
        val buffer = ByteArray(16 * 1024)
        file.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                written += read
                if (written > MaxZipBytes) throw IOException("too big")
                out.write(buffer, 0, read)
            }
        }
    }

    /** what a release file's name has to have in it for mochy to take it */
    const val Marker = "mochyymodule"
    private const val MaxZipBytes = 40L * 1024 * 1024
    private val Name = Regex("[A-Za-z0-9._-]{1,100}")
    private val FileViews = setOf("blob", "raw", "issues", "pulls", "actions", "wiki")
}
