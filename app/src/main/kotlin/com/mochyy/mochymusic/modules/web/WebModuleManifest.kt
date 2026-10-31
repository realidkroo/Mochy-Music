// what a web module says about itself in its manifest.json, see docs/notes/modules.md for the whole format

package com.mochyy.mochymusic.modules.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

@Serializable
internal data class WebModuleManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val author: String? = null,
    // plain names ("search", "stream", "lyrics"), some services write objects with a name instead
    val resources: List<JsonElement> = emptyList(),
    val look: Map<String, JsonPrimitive> = emptyMap(),
    // the service's own settings, sent on every call; mochy has no form for them so each goes as its default
    val settings: List<Setting> = emptyList(),
) {
    @Serializable
    data class Setting(
        val key: String = "",
        val default: JsonElement? = null,
        // each option is a plain value or an object with a value, services write both
        val options: List<JsonElement> = emptyList(),
    ) {
        val defaultValue: String? get() = default.queryValue()

        val values: List<String> get() = options.mapNotNull { (it as? JsonObject)?.get("value").queryValue() ?: it.queryValue() }
    }

    fun setting(key: String): Setting? = settings.firstOrNull { it.key.equals(key, ignoreCase = true) }

    val resourceNames: Set<String>
        get() = resources.mapNotNull { element ->
            ((element as? JsonPrimitive) ?: ((element as? JsonObject)?.get("name") as? JsonPrimitive))?.contentOrNull?.lowercase()
        }.toSet()

    // a manifest that lists nothing is an audio source, that's what most services of this kind are
    val playsSongs: Boolean get() = (resourceNames.isEmpty() && look.isEmpty()) || "search" in resourceNames

    val hasLyrics: Boolean get() = "lyrics" in resourceNames

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): WebModuleManifest? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            ?.takeIf { it.name.isNotBlank() || it.id.isNotBlank() }

        // people paste the manifest's own link as often as the service's, both end up as the service's base
        fun baseOf(link: String): String? {
            var text = link.trim()
            if (text.isEmpty()) return null
            if (!text.contains("://")) text = "https://$text"
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
            val path = uri.path.orEmpty().replace(Regex("/[^/]*\\.json$"), "").trimEnd('/')
            return URI(uri.scheme, uri.userInfo, uri.host, uri.port, path, uri.query, null).toString().trimEnd('/')
        }

        // the service's own id under a web prefix, so it can never take the place of a built-in module
        fun idFor(base: String, manifest: WebModuleManifest): String {
            val own = manifest.id.ifBlank { runCatching { URI(base).host }.getOrNull().orEmpty() }
            return "web." + own.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        }
    }
}

// a json value as it goes in a query string: 320 stays 320, never 320.0; null and objects have no such form
private fun JsonElement?.queryValue(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
