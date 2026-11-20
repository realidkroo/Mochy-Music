package com.mochyy.mochymusic.ui.theme

import androidx.compose.ui.text.font.FontFamily

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
