package com.mochyy.mochymusic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

internal const val FollowGlobalFontId = ""
internal const val BuiltinInterId = "builtin:inter"
internal const val BuiltinSystemId = "builtin:system"
internal const val BuiltinSerifId = "builtin:serif"
internal const val BuiltinMonoId = "builtin:mono"
internal const val BuiltinBbhBartleId = "builtin:bbh_bartle"

internal enum class FontRole { HEADING, SUBHEADING, GLOBAL }

// auto leaves every text style untouched so default mode renders identically to stock mochy
internal enum class RoleWeight {
    AUTO, LIGHT, NORMAL, BOLD;

    companion object {
        fun fromStored(value: String?): RoleWeight = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

@Immutable
internal data class FontCapabilities(
    val supportsBold: Boolean = false,
    val supportsExtraBold: Boolean = false,
    val supportsItalic: Boolean = false,
    val supportsLight: Boolean = false,
) {
    val hasAnyStyle: Boolean get() = supportsBold || supportsItalic || supportsLight
}

@Immutable
internal data class RoleFontConfig(
    val fontId: String = FollowGlobalFontId,
    val weight: RoleWeight = RoleWeight.AUTO,
    val italic: Boolean? = null,
) {
    fun isDefaultFor(role: FontRole): Boolean {
        val defaultId = if (role == FontRole.GLOBAL) BuiltinInterId else FollowGlobalFontId
        return fontId == defaultId && weight == RoleWeight.AUTO && italic == null
    }
}

@Immutable
internal data class FontOption(
    val id: String,
    val name: String,
    val capabilities: FontCapabilities,
    val isCustom: Boolean = false,
    val isVariable: Boolean = false,
    val faceCount: Int = 1,
)

@Immutable
internal data class ResolvedRoleFont(
    val fontId: String,
    val family: FontFamily,
    val boldFamily: FontFamily? = null,
    val weight: RoleWeight = RoleWeight.AUTO,
    val italic: Boolean = false,
    val capabilities: FontCapabilities = FontCapabilities(),
    val isDefaultInter: Boolean = false,
) {
    val effectiveWeight: RoleWeight
        get() = when (weight) {
            RoleWeight.BOLD -> if (capabilities.supportsBold) RoleWeight.BOLD else RoleWeight.AUTO
            RoleWeight.LIGHT -> if (capabilities.supportsLight) RoleWeight.LIGHT else RoleWeight.AUTO
            RoleWeight.NORMAL -> RoleWeight.NORMAL
            RoleWeight.AUTO -> RoleWeight.AUTO
        }

    val effectiveItalic: Boolean
        get() = italic && capabilities.supportsItalic

    val isDefaultPassThrough: Boolean
        get() = isDefaultInter && effectiveWeight == RoleWeight.AUTO && !effectiveItalic

    // returns base verbatim when default so zero allocations happen and default ui stays pixel-identical
    fun applyTo(base: TextStyle): TextStyle {
        val chosenWeight = effectiveWeight
        val chosenItalic = effectiveItalic
        val targetFamily = if (chosenWeight == RoleWeight.BOLD && boldFamily != null) boldFamily else family

        if (chosenWeight == RoleWeight.AUTO && !chosenItalic) {
            return if (base.fontFamily === targetFamily) base else base.copy(fontFamily = targetFamily)
        }

        val resolvedWeight = when (chosenWeight) {
            RoleWeight.AUTO, RoleWeight.NORMAL -> base.fontWeight
            RoleWeight.LIGHT -> FontWeight.Light
            RoleWeight.BOLD -> {
                val baseWeight = base.fontWeight
                if (baseWeight != null && baseWeight >= FontWeight.Bold && capabilities.supportsExtraBold) {
                    FontWeight.ExtraBold
                } else {
                    FontWeight.Bold
                }
            }
        }
        val resolvedStyle = if (chosenItalic) FontStyle.Italic else base.fontStyle
        return base.copy(
            fontFamily = targetFamily,
            fontWeight = resolvedWeight,
            fontStyle = resolvedStyle,
        )
    }
}

@Immutable
internal data class ResolvedFonts(
    val heading: ResolvedRoleFont,
    val subheading: ResolvedRoleFont,
    val global: ResolvedRoleFont,
) {
    fun forRole(role: FontRole): ResolvedRoleFont = when (role) {
        FontRole.HEADING -> heading
        FontRole.SUBHEADING -> subheading
        FontRole.GLOBAL -> global
    }
}

// kept for legacy preference migration and existing unit tests
internal enum class AppFontMode {
    DEFAULT, SYSTEM, CUSTOM;

    fun resolve(look: FontFamily?, imported: FontFamily?, bundled: FontFamily): FontFamily =
        when (this) {
            DEFAULT -> look ?: bundled
            SYSTEM -> FontFamily.Default
            CUSTOM -> imported ?: look ?: bundled
        }

    companion object {
        fun fromStoredValue(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
