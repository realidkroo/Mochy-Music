package com.mochyy.mochymusic.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontSelectionTest {
    private val look = FontFamily.Serif
    private val imported = FontFamily.Monospace
    private val bundled = FontFamily.SansSerif

    @Test
    fun defaultRetainsTheLookAndFallsBackToTheBundledFont() {
        assertSame(look, AppFontMode.DEFAULT.resolve(look, imported, bundled))
        assertSame(bundled, AppFontMode.DEFAULT.resolve(null, imported, bundled))
    }

    @Test
    fun explicitChoicesTakePriorityOverTheLook() {
        assertSame(FontFamily.Default, AppFontMode.SYSTEM.resolve(look, imported, bundled))
        assertSame(imported, AppFontMode.CUSTOM.resolve(look, imported, bundled))
    }

    @Test
    fun missingFileAndUnknownSavedModeUseTheDefaultPolicy() {
        assertSame(look, AppFontMode.CUSTOM.resolve(look, null, bundled))
        assertEquals(AppFontMode.DEFAULT, AppFontMode.fromStoredValue("future-mode"))
        assertEquals(AppFontMode.DEFAULT, AppFontMode.fromStoredValue(null))
    }
}
