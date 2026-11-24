package com.mochyy.mochymusic.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun defaultRolePassesBaseStyleThroughUntouched() {
        val base = TextStyle(fontFamily = bundled, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        val resolved = ResolvedRoleFont(
            fontId = BuiltinInterId,
            family = bundled,
            weight = RoleWeight.AUTO,
            italic = false,
            capabilities = FontCapabilities(supportsBold = true, supportsExtraBold = true, supportsLight = true),
            isDefaultInter = true,
        )
        assertTrue(resolved.isDefaultPassThrough)
        assertSame(base, resolved.applyTo(base))
    }

    @Test
    fun unsupportedCapabilitiesNeverApplyFakeBoldOrItalic() {
        val base = TextStyle(fontFamily = bundled, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal)
        val singleFace = ResolvedRoleFont(
            fontId = BuiltinBbhBartleId,
            family = imported,
            weight = RoleWeight.BOLD,
            italic = true,
            capabilities = FontCapabilities(),
        )
        val applied = singleFace.applyTo(base)
        assertSame(imported, applied.fontFamily)
        assertEquals(FontWeight.Normal, applied.fontWeight)
        assertEquals(FontStyle.Normal, applied.fontStyle)
    }

    @Test
    fun supportedBoldAndItalicApplyCleanly() {
        val headingBase = TextStyle(fontFamily = bundled, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        val bodyBase = TextStyle(fontFamily = bundled, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        val caps = FontCapabilities(supportsBold = true, supportsExtraBold = true, supportsItalic = true, supportsLight = true)
        val resolved = ResolvedRoleFont(
            fontId = BuiltinSystemId,
            family = look,
            weight = RoleWeight.BOLD,
            italic = true,
            capabilities = caps,
        )

        val appliedHeading = resolved.applyTo(headingBase)
        assertEquals(FontWeight.ExtraBold, appliedHeading.fontWeight)
        assertEquals(FontStyle.Italic, appliedHeading.fontStyle)

        val appliedBody = resolved.applyTo(bodyBase)
        assertEquals(FontWeight.Bold, appliedBody.fontWeight)
        assertEquals(FontStyle.Italic, appliedBody.fontStyle)
    }

    @Test
    fun customFontCapabilitiesReflectVariableAndGroupedStaticFaces() {
        val staticSingle = AppFonts.CustomFontRecord(
            id = "custom:1",
            displayName = "Display",
            familyKey = "display",
            faces = listOf(AppFonts.CustomFaceRecord("id1", "ttf", 400, false, null, null, false, null)),
        )
        assertFalse(staticSingle.capabilities().hasAnyStyle)

        val grouped = AppFonts.CustomFontRecord(
            id = "custom:2",
            displayName = "Outfit",
            familyKey = "outfit",
            faces = listOf(
                AppFonts.CustomFaceRecord("id1", "ttf", 400, false, null, null, false, null),
                AppFonts.CustomFaceRecord("id2", "ttf", 700, false, null, null, false, null),
                AppFonts.CustomFaceRecord("id3", "ttf", 400, true, null, null, false, null),
            ),
        )
        assertTrue(grouped.capabilities().supportsBold)
        assertTrue(grouped.capabilities().supportsItalic)
        assertFalse(grouped.capabilities().supportsLight)

        val variable = AppFonts.CustomFontRecord(
            id = "custom:3",
            displayName = "Geist",
            familyKey = "geist",
            faces = listOf(AppFonts.CustomFaceRecord("id1", "ttf", 400, false, 200f, 800f, false, -10f)),
        )
        assertTrue(variable.capabilities().supportsLight)
        assertTrue(variable.capabilities().supportsBold)
        assertTrue(variable.capabilities().supportsExtraBold)
        assertTrue(variable.capabilities().supportsItalic)
    }
}
