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
    // only read from a zip: the service the module plays from, since a file has no link of its own
    val url: String? = null,
    // only read from a zip: the apk inside it holding the module's own code, which mochy runs only when it's signed with mochy's key
    val code: String? = null,
    // a skin: a player of its own, a font from the zip, pages; read as raw json so one bad part can't sink the whole manifest
    val player: JsonElement? = null,
    val font: JsonElement? = null,
    val pages: JsonElement? = null,
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

    // anything that changes how mochy looks: settings, a player, a font, pages
    val changesLook: Boolean get() = look.isNotEmpty() || player != null || font != null || pages != null

    // a manifest that lists nothing is an audio source, that's what most services of this kind are
    val playsSongs: Boolean get() = (resourceNames.isEmpty() && !changesLook) || "search" in resourceNames

    val hasLyrics: Boolean get() = "lyrics" in resourceNames

    // the look as plain text, values that aren't text or numbers dropped
    val lookValues: Map<String, String> get() = look.mapNotNull { (key, value) -> value.contentOrNull?.let { key to it } }.toMap()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): WebModuleManifest? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            ?.takeIf { it.name.isNotBlank() || it.id.isNotBlank() }

        // a site's own manifest.json, the one for installing it to a home screen, has a start page and icons but asks for nothing a module does
        fun isWebsite(text: String): Boolean {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return false
            return ModuleKeys.none { it in root } && SiteKeys.any { it in root }
        }

        private val SiteKeys = listOf("start_url", "display", "short_name", "icons")
        private val ModuleKeys = listOf("resources", "look", "player", "font", "pages")

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
