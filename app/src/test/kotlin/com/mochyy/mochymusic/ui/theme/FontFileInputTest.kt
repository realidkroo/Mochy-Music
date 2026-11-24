package com.mochyy.mochymusic.ui.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontFileInputTest {
    @Test
    fun acceptsTtfExtensionRegardlessOfCase() {
        assertTrue(isTtfName("MyFont.TTF"))
        assertFalse(isTtfName("MyFont.ttf.exe"))
        assertFalse(isTtfName("MyFont.otf"))
    }

    @Test
    fun acceptsTtfAndOtfExtensionsForSupportedFonts() {
        assertTrue(isSupportedFontName("MyFont.ttf"))
        assertTrue(isSupportedFontName("MyFont.OTF"))
        assertFalse(isSupportedFontName("MyFont.woff2"))
    }

    @Test
    fun readsTrueTypeSfntVersion() {
        assertTrue(hasTrueTypeHeader(byteArrayOf(0, 1, 0, 0).inputStream()))
        assertFalse(hasTrueTypeHeader("OTTO".byteInputStream()))
        assertFalse(hasTrueTypeHeader("ttcf".byteInputStream()))
        assertFalse(hasTrueTypeHeader(byteArrayOf(0, 1).inputStream()))
    }

    @Test
    fun readsSupportedTtfAndOtfHeaders() {
        assertTrue(hasSupportedFontHeader(byteArrayOf(0, 1, 0, 0).inputStream()))
        assertTrue(hasSupportedFontHeader("OTTO".byteInputStream()))
        assertTrue(hasSupportedFontHeader("true".byteInputStream()))
        assertFalse(hasSupportedFontHeader("ttcf".byteInputStream()))
        assertFalse(hasSupportedFontHeader("PK\u0003\u0004".byteInputStream()))
    }

    @Test
    fun copiesBytesAtTheLimitWhenLengthIsUnknown() {
        val bytes = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()
        ByteArrayInputStream(bytes).copyFontTo(output, maxBytes = 3)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test(expected = FontTooLargeException::class)
    fun rejectsStreamThatExceedsTheLimit() {
        byteArrayOf(1, 2, 3, 4).inputStream().copyFontTo(ByteArrayOutputStream(), maxBytes = 3)
    }
}
