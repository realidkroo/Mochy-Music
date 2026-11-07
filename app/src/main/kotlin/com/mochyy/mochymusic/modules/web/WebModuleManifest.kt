// what a web module says about itself in its manifest.json, see docs/notes/modules.md for the whole format

package com.mochyy.mochymusic.modules.web

import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.modules.skin.GlassChange
import com.mochyy.mochymusic.modules.skin.SkinParser
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
    // a square picture for its row and a wide one over its page, a path in the zip or a link
    val icon: String? = null,
    val banner: String? = null,
    // plain names ("search", "stream", "lyrics", "metadata"), some services write objects with a name instead
    val resources: List<JsonElement> = emptyList(),
    val look: Map<String, JsonPrimitive> = emptyMap(),
    // the module's own settings: people change them on its page, a service gets each one with every call, and an option can carry a look
    val settings: List<Setting> = emptyList(),
    // only read from a zip: the service the module plays from, since a file has no link of its own
    val url: String? = null,
    // only read from a zip: the apk inside it holding the module's own code, which mochy runs only when it's signed with mochy's key
    val code: String? = null,
    // a skin: a player of its own, a font and a glass shader from the zip, pages, rows in the player's menu; read as raw json so one bad part can't sink the whole manifest
    val player: JsonElement? = null,
    val font: JsonElement? = null,
    val pages: JsonElement? = null,
    val actions: JsonElement? = null,
    val glass: JsonElement? = null,
) {
    @Serializable
    data class Setting(
        val key: String = "",
        val label: String? = null,
        // select, text or toggle; left out, it's worked out from the rest
        val type: String? = null,
        val default: JsonElement? = null,
        // each option is a plain value or an object with a value, a label, a description and a look
        val options: List<JsonElement> = emptyList(),
        val description: String? = null,
        val hint: String? = null,
        // a toggle's look while it's on
        val look: Map<String, JsonPrimitive> = emptyMap(),
        // a toggle's change to the module's glass while it's on
        val glass: JsonElement? = null,
    ) {
        val defaultValue: String? get() = default.queryValue()

        val values: List<String> get() = options.mapNotNull { optionValue(it) }

        val kind: FieldKind
            get() = when (type?.trim()?.lowercase()) {
                "select", "list", "choice" -> FieldKind.Select
                "toggle", "switch", "bool", "boolean" -> FieldKind.Toggle
                "text", "string", "input" -> FieldKind.Text
                else -> when {
                    options.isNotEmpty() -> FieldKind.Select
                    defaultValue?.lowercase() in setOf("true", "false") -> FieldKind.Toggle
                    else -> FieldKind.Text
                }
            }

        // what the page shows: a select starts on its default or its first option, a toggle on off
        fun toField(): ModuleField = ModuleField(
            id = key,
            label = label?.takeIf { it.isNotBlank() } ?: key.replaceFirstChar { it.uppercase() },
            kind = kind,
            options = options.mapNotNull { option ->
                val value = optionValue(option) ?: return@mapNotNull null
                val o = option as? JsonObject
                StreamOption(value, o?.text("label") ?: value, o?.text("description").orEmpty())
            },
            default = defaultValue ?: when (kind) {
                FieldKind.Select -> values.firstOrNull().orEmpty()
                FieldKind.Toggle -> "false"
                FieldKind.Text -> ""
            },
            hint = hint.orEmpty(),
            description = description.orEmpty(),
        )

        /** the look this setting puts on at [value]: a picked option's, or a toggle's own while it's on */
        fun lookAt(value: String?): Map<String, String> = when (kind) {
            FieldKind.Toggle -> if (value?.lowercase() == "true") look.asText() else emptyMap()
            FieldKind.Select -> options.firstOrNull { optionValue(it) == value }
                ?.let { ((it as? JsonObject)?.get("look") as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() }
                .orEmpty()
            FieldKind.Text -> emptyMap()
        }

        /** what this setting changes about the module's glass at [value]: a picked option's, or a toggle's own while it's on */
        fun glassAt(value: String?): GlassChange? = when (kind) {
            FieldKind.Toggle -> (glass as? JsonObject)?.takeIf { value?.lowercase() == "true" }
            FieldKind.Select -> options.firstOrNull { optionValue(it) == value }?.let { (it as? JsonObject)?.get("glass") as? JsonObject }
            FieldKind.Text -> null
        }?.let(SkinParser::glassChange)

        // whether any of its values changes the look, which makes the module a look
        val changesLook: Boolean
            get() = look.isNotEmpty() || glass != null ||
                options.any { option -> (option as? JsonObject)?.let { it["look"] is JsonObject || it["glass"] is JsonObject } == true }

        private fun optionValue(option: JsonElement): String? = (option as? JsonObject)?.get("value").queryValue() ?: option.queryValue()
    }

    fun setting(key: String): Setting? = settings.firstOrNull { it.key.equals(key, ignoreCase = true) }

    val resourceNames: Set<String>
        get() = resources.mapNotNull { element ->
            ((element as? JsonPrimitive) ?: ((element as? JsonObject)?.get("name") as? JsonPrimitive))?.contentOrNull?.lowercase()
        }.toSet()

    // anything that changes how mochy looks or what it shows: settings, a player, a font, pages, menu rows
    val changesLook: Boolean
        get() = look.isNotEmpty() || player != null || font != null || pages != null || actions != null || glass != null || settings.any { it.changesLook }

    // a manifest that lists nothing is an audio source, that's what most services of this kind are
    val playsSongs: Boolean get() = (resourceNames.isEmpty() && !changesLook) || "search" in resourceNames

    val hasLyrics: Boolean get() = "lyrics" in resourceNames

    val hasMetadata: Boolean get() = "metadata" in resourceNames

    // the look as plain text, values that aren't text or numbers dropped
    val lookValues: Map<String, String> get() = look.asText()

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
        private val ModuleKeys = listOf("resources", "look", "player", "font", "pages", "actions", "glass")

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

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun Map<String, JsonPrimitive>.asText(): Map<String, String> = mapNotNull { (key, value) -> value.contentOrNull?.let { key to it } }.toMap()
