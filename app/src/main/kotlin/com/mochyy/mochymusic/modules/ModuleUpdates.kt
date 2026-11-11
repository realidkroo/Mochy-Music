// where each module was added from on the web, and whether a newer copy waits there; looked at now and then so github's limits are never hit

package com.mochyy.mochymusic.modules

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mochyy.mochymusic.modules.web.WebModuleManifest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

/** where mochy can look for a newer copy of a module */
sealed interface ModuleSource {
    /** a github project's release zip, with the tag and file stamp it was added at */
    data class GitHub(val owner: String, val repo: String, val tag: String, val stamp: String) : ModuleSource

    /** a service's manifest.json, with a fingerprint of the one that was added */
    data class Link(val base: String, val fingerprint: String) : ModuleSource

    /** what to check again to get the newer copy */
    val link: String
        get() = when (this) {
            is GitHub -> "https://github.com/$owner/$repo"
            is Link -> base
        }

    /** where it comes from, the way the module's page shows it */
    val label: String
        get() = when (this) {
            is GitHub -> "github.com/$owner/$repo"
            is Link -> base.substringAfter("://")
        }
}

/** a newer copy waiting: its tag or version, null when the author changed it without saying which */
data class ModuleUpdate(val version: String?)

internal class ModuleUpdates(private val prefs: SharedPreferences, private val client: OkHttpClient) {

    fun source(id: String): ModuleSource? {
        val o = prefs.getString(SourceKey + id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return when (o.optString("kind")) {
            "github" -> ModuleSource.GitHub(o.optString("owner"), o.optString("repo"), o.optString("tag"), o.optString("stamp"))
            "link" -> ModuleSource.Link(o.optString("base"), o.optString("fingerprint"))
            else -> null
        }
    }

    fun update(id: String): ModuleUpdate? {
        val o = prefs.getString(UpdateKey + id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return ModuleUpdate(o.optString("version").takeIf { it.isNotBlank() })
    }

    fun checkedAt(id: String): Long? = prefs.getLong(CheckedKey + id, 0L).takeIf { it > 0L }

    /** what a module was just added or updated from; a zip from the phone has no source and stops being checked */
    fun installed(id: String, source: ModuleSource?) {
        prefs.edit {
            if (source == null) remove(SourceKey + id) else putString(SourceKey + id, toJson(source).toString())
            remove(UpdateKey + id)
            putLong(CheckedKey + id, System.currentTimeMillis())
        }
    }

    fun forget(id: String) {
        prefs.edit {
            remove(SourceKey + id)
            remove(UpdateKey + id)
            remove(CheckedKey + id)
        }
    }

    /** asks the module's source whether it changed; [force] skips the wait between checks. blocking, so off the main thread */
    fun check(id: String, installedVersion: String, force: Boolean, preferDebug: Boolean) {
        val source = source(id) ?: return
        val now = System.currentTimeMillis()
        if (!force && now - (checkedAt(id) ?: 0L) < CheckEveryMs) return
        // written first, so a source that's down or a rate limit isn't asked again on every visit
        prefs.edit { putLong(CheckedKey + id, now) }
        val found = when (source) {
            is ModuleSource.GitHub -> newerRelease(source, preferDebug) ?: return
            is ModuleSource.Link -> newerManifest(source, installedVersion) ?: return
        }
        prefs.edit {
            val update = found.getOrNull()
            if (update == null) remove(UpdateKey + id) else putString(UpdateKey + id, JSONObject().put("version", update.version.orEmpty()).toString())
        }
    }

    // a result of null means the source couldn't be read, so whatever was known before stays
    private fun newerRelease(source: ModuleSource.GitHub, preferDebug: Boolean): Result<ModuleUpdate?>? {
        val link = GitHubReleases.Link(source.owner, source.repo, tag = null)
        val release = (GitHubReleases.release(client, link, preferDebug) as? GitHubReleases.Found.Newest)?.release ?: return null
        val changed = release.tag != source.tag || release.stamp != source.stamp
        return Result.success(if (changed) ModuleUpdate(release.tag) else null)
    }

    private fun newerManifest(source: ModuleSource.Link, installedVersion: String): Result<ModuleUpdate?>? {
        val text = runCatching {
            client.newCall(Request.Builder().url("${source.base}/manifest.json").header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        }.getOrNull() ?: return null
        if (fingerprint(text) == source.fingerprint) return Result.success(null)
        val manifest = WebModuleManifest.parse(text) ?: return null
        return Result.success(ModuleUpdate(manifest.version.takeIf { it.isNotBlank() && it != installedVersion }))
    }

    private fun toJson(source: ModuleSource): JSONObject = when (source) {
        is ModuleSource.GitHub -> JSONObject().put("kind", "github").put("owner", source.owner).put("repo", source.repo)
            .put("tag", source.tag).put("stamp", source.stamp)
        is ModuleSource.Link -> JSONObject().put("kind", "link").put("base", source.base).put("fingerprint", source.fingerprint)
    }

    companion object {
        private const val SourceKey = "source_"
        private const val UpdateKey = "update_"
        private const val CheckedKey = "update_checked_"
        // github lets an app without a key ask 60 times an hour, so each module is looked at a few times a day at most
        private const val CheckEveryMs = 6L * 60 * 60 * 1000

        /** a short hash of a manifest's text, so any change to it counts, even one that forgot to bump the version */
        fun fingerprint(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.trim().toByteArray()).take(10).joinToString("") { "%02x".format(it) }
    }
}
