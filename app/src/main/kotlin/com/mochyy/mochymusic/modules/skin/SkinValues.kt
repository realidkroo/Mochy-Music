// reading one value out of a manifest the forgiving way: numbers written as text still count, and anything odd reads as missing

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.float(name: String): Float? =
    (this[name] as? JsonPrimitive)?.let { it.floatOrNull ?: it.contentOrNull?.trim()?.toFloatOrNull() }?.takeIf { it.isFinite() }

internal fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.trim()?.lowercase()?.toBooleanStrictOrNull() }

/** a shader's own float uniforms by name, leaving out the ones mochy fills in itself */
internal fun shaderUniforms(element: JsonElement?, reserved: Set<String>): Map<String, Float> =
    (element as? JsonObject).orEmpty().entries
        .filter { (name, _) -> UniformName.matches(name) && name !in reserved }
        .mapNotNull { (name, value) -> (value as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() }?.let { name to it } }
        .take(MaxUniforms)
        .toMap()

/** one of the style's tones by name, "$key" for a colour from a setting, or a fixed "#RRGGBB" */
internal fun skinColor(raw: String?): SkinColor? = when (raw?.trim()?.lowercase()) {
    null, "" -> null
    "ink", "text" -> SkinColor.Ink
    "inksoft", "textsoft" -> SkinColor.InkSoft
    "played" -> SkinColor.Played
    "remaining" -> SkinColor.Remaining
    "chip" -> SkinColor.Chip
    "cover" -> SkinColor.Cover
    "accent" -> SkinColor.Accent
    else -> if (raw.trim().startsWith("$")) {
        // "$glow" or "$glow|#FFFFFF": the setting's colour, and what to use while it isn't one
        val (key, fallback) = raw.trim().removePrefix("$").split('|', limit = 2).let { it[0] to it.getOrNull(1) }
        key.takeIf { it.isNotBlank() }?.let { SkinColor.Setting(it.take(60), skinColor(fallback) ?: SkinColor.Ink) }
    } else {
        parseHexColor(raw)?.let(SkinColor::Fixed)
    }
}

internal fun skinWeight(raw: String?): FontWeight? = when (raw?.trim()?.lowercase()) {
    "thin" -> FontWeight.Thin
    "light" -> FontWeight.Light
    "normal", "regular" -> FontWeight.Normal
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    "black" -> FontWeight.Black
    else -> null
}

internal fun skinAlign(raw: String?): SkinAlign? = when (raw?.trim()?.lowercase()) {
    "start", "left" -> SkinAlign.Start
    "center", "centre" -> SkinAlign.Center
    "end", "right" -> SkinAlign.End
    else -> null
}

/** "#RRGGBB" or "#AARRGGBB", the way skins and the accent colour write a colour */
fun parseHexColor(raw: String?): Color? {
    val digits = raw?.trim()?.removePrefix("#") ?: return null
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

private val UniformName = Regex("[A-Za-z_][A-Za-z0-9_]{0,40}")
private const val MaxUniforms = 24
