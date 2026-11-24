package com.mochyy.mochymusic.ui.theme

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontMetadataParserTest {
    @Test
    fun parsesNameWeightItalicAndVariableAxes() {
        val sfnt = buildSfnt(
            mapOf(
                "OS/2" to buildOs2(weight = 700, italic = true),
                "fvar" to buildFvar(
                    listOf(
                        Axis("wght", 200f, 400f, 800f),
                        Axis("slnt", -10f, 0f, 0f),
                    ),
                ),
                "name" to buildNameTable(mapOf(1 to "Sample Font", 16 to "Sample Typographic")),
            ),
        )

        val meta = FontMetadataParser.parseBytes(sfnt)
        assertEquals("Sample Typographic", meta.familyName)
        assertEquals(700, meta.weight)
        assertTrue(meta.isItalic)
        assertEquals(200f, meta.variableWeightMin!!, 0.01f)
        assertEquals(800f, meta.variableWeightMax!!, 0.01f)
        assertEquals(-10f, meta.variableSlant!!, 0.01f)
        assertTrue(meta.isVariable)
    }

    @Test
    fun ignoresTruncatedOrGarbageInputSafely() {
        val meta = FontMetadataParser.parseBytes(byteArrayOf(0, 1, 0, 0, 0, 5))
        assertNull(meta.familyName)
        assertEquals(400, meta.weight)
        assertFalse(meta.isItalic)
        assertFalse(meta.isVariable)
    }

    private data class Axis(val tag: String, val min: Float, val def: Float, val max: Float)

    private fun buildSfnt(tables: Map<String, ByteArray>): ByteArray {
        val headerSize = 12 + tables.size * 16
        val body = ByteArrayOutputStream()
        val entries = ArrayList<Triple<String, Int, Int>>()
        var offset = headerSize
        for ((tag, data) in tables) {
            entries.add(Triple(tag, offset, data.size))
            body.write(data)
            val pad = (4 - (data.size % 4)) % 4
            repeat(pad) { body.write(0) }
            offset += data.size + pad
        }
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeInt(0x00010000)
        dos.writeShort(entries.size)
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)
        for ((tag, off, len) in entries) {
            dos.write(tag.toByteArray(StandardCharsets.ISO_8859_1), 0, 4)
            dos.writeInt(0)
            dos.writeInt(off)
            dos.writeInt(len)
        }
        dos.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun buildOs2(weight: Int, italic: Boolean): ByteArray {
        val out = ByteArray(64)
        out[4] = (weight shr 8).toByte()
        out[5] = (weight and 0xFF).toByte()
        val sel = if (italic) 1 else 0
        out[62] = (sel shr 8).toByte()
        out[63] = (sel and 0xFF).toByte()
        return out
    }

    private fun buildFvar(axes: List<Axis>): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(16)
        dos.writeShort(2)
        dos.writeShort(axes.size)
        dos.writeShort(20)
        dos.writeShort(0)
        dos.writeShort(0)
        for (axis in axes) {
            dos.write(axis.tag.toByteArray(StandardCharsets.ISO_8859_1), 0, 4)
            dos.writeInt((axis.min * 65536f).toInt())
            dos.writeInt((axis.def * 65536f).toInt())
            dos.writeInt((axis.max * 65536f).toInt())
            dos.writeShort(0)
            dos.writeShort(256)
        }
        return out.toByteArray()
    }

    private fun buildNameTable(names: Map<Int, String>): ByteArray {
        val strings = ByteArrayOutputStream()
        val records = ArrayList<Triple<Int, Int, Int>>()
        for ((id, text) in names) {
            val encoded = text.toByteArray(StandardCharsets.UTF_16BE)
            records.add(Triple(id, strings.size(), encoded.size))
            strings.write(encoded)
        }
        val storageOffset = 6 + records.size * 12
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeShort(0)
        dos.writeShort(records.size)
        dos.writeShort(storageOffset)
        for ((id, off, len) in records) {
            dos.writeShort(3)
            dos.writeShort(1)
            dos.writeShort(0x0409)
            dos.writeShort(id)
            dos.writeShort(len)
            dos.writeShort(off)
        }
        dos.write(strings.toByteArray())
        return out.toByteArray()
    }
}
