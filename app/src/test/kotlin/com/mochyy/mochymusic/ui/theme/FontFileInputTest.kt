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
    fun readsTrueTypeSfntVersion() {
        assertTrue(hasTrueTypeHeader(byteArrayOf(0, 1, 0, 0).inputStream()))
        assertFalse(hasTrueTypeHeader("OTTO".byteInputStream()))
        assertFalse(hasTrueTypeHeader("ttcf".byteInputStream()))
        assertFalse(hasTrueTypeHeader(byteArrayOf(0, 1).inputStream()))
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
