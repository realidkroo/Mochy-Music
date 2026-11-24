// reads sfnt tables (name, os/2, head, fvar) from a ttf or otf file so we know its real name and supported styles

package com.mochyy.mochymusic.ui.theme

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

internal data class ParsedFontMetadata(
    val familyName: String? = null,
    val weight: Int = 400,
    val isItalic: Boolean = false,
    val variableWeightMin: Float? = null,
    val variableWeightMax: Float? = null,
    val variableItalic: Boolean = false,
    val variableSlant: Float? = null,
) {
    val isVariable: Boolean
        get() = variableWeightMin != null || variableItalic || variableSlant != null
}

internal object FontMetadataParser {
    private const val MaxTableCount = 128
    private const val MaxNameTableBytes = 256 * 1024
    private const val MaxFvarTableBytes = 64 * 1024

    fun parse(file: File): ParsedFontMetadata {
        if (!file.isFile || file.length() < 12L) return ParsedFontMetadata()
        return runCatching {
            RandomAccessFile(file, "r").use { raf -> parseRandomAccess(raf, file.length()) }
        }.getOrElse { ParsedFontMetadata() }
    }

    // kept bytes-based too so jvm unit tests can feed synthetic sfnt fixtures without temp files
    fun parseBytes(bytes: ByteArray): ParsedFontMetadata {
        if (bytes.size < 12) return ParsedFontMetadata()
        val numTables = readU16(bytes, 4)
        if (numTables <= 0 || numTables > MaxTableCount) return ParsedFontMetadata()
        if (12 + numTables * 16 > bytes.size) return ParsedFontMetadata()

        val tables = HashMap<String, Pair<Int, Int>>(numTables)
        for (i in 0 until numTables) {
            val base = 12 + i * 16
            val tag = String(bytes, base, 4, StandardCharsets.ISO_8859_1)
            val offset = readU32(bytes, base + 8)
            val length = readU32(bytes, base + 12)
            if (offset in 0..bytes.size && length > 0 && offset.toLong() + length.toLong() <= bytes.size.toLong()) {
                tables[tag] = offset to length
            }
        }
        return extractFromTables(tables) { offset, length -> bytes.copyOfRange(offset, offset + length) }
    }

    private fun parseRandomAccess(raf: RandomAccessFile, fileLength: Long): ParsedFontMetadata {
        val header = ByteArray(12)
        raf.readFully(header)
        val numTables = readU16(header, 4)
        if (numTables <= 0 || numTables > MaxTableCount) return ParsedFontMetadata()
        val dirSize = numTables * 16
        if (12L + dirSize > fileLength) return ParsedFontMetadata()

        val dir = ByteArray(dirSize)
        raf.readFully(dir)
        val tables = HashMap<String, Pair<Int, Int>>(numTables)
        for (i in 0 until numTables) {
            val base = i * 16
            val tag = String(dir, base, 4, StandardCharsets.ISO_8859_1)
            val offset = readU32(dir, base + 8)
            val length = readU32(dir, base + 12)
            if (offset >= 12 && length > 0 && offset.toLong() + length.toLong() <= fileLength) {
                tables[tag] = offset to length
            }
        }
        return extractFromTables(tables) { offset, length ->
            val buf = ByteArray(length)
            raf.seek(offset.toLong())
            raf.readFully(buf)
            buf
        }
    }

    private fun extractFromTables(
        tables: Map<String, Pair<Int, Int>>,
        readSlice: (Int, Int) -> ByteArray,
    ): ParsedFontMetadata {
        var weight = 400
        var isItalic = false
        var hasOs2 = false

        tables["OS/2"]?.let { (off, len) ->
            if (len >= 64) {
                val data = readSlice(off, 64)
                val rawWeight = readU16(data, 4)
                if (rawWeight in 1..1000) weight = rawWeight.coerceIn(100, 900)
                val fsSelection = readU16(data, 62)
                isItalic = (fsSelection and 0x0001) != 0
                hasOs2 = true
            }
        }

        tables["head"]?.let { (off, len) ->
            if (len >= 46) {
                val data = readSlice(off, 46)
                val macStyle = readU16(data, 44)
                if (!hasOs2 && (macStyle and 0x0001) != 0) weight = 700
                if ((macStyle and 0x0002) != 0) isItalic = true
            }
        }

        var wghtMin: Float? = null
        var wghtMax: Float? = null
        var varItalic = false
        var varSlant: Float? = null

        tables["fvar"]?.let { (off, len) ->
            if (len in 16..MaxFvarTableBytes) {
                val data = readSlice(off, len)
                val axesOffset = readU16(data, 4)
                val axisCount = readU16(data, 8)
                val axisSize = readU16(data, 10)
                if (axisSize >= 20 && axisCount in 1..64 && axesOffset + axisCount * axisSize <= data.size) {
                    for (i in 0 until axisCount) {
                        val pos = axesOffset + i * axisSize
                        val tag = String(data, pos, 4, StandardCharsets.ISO_8859_1)
                        val minVal = readFixed1616(data, pos + 4)
                        val maxVal = readFixed1616(data, pos + 12)
                        when (tag) {
                            "wght" -> if (maxVal > minVal) {
                                wghtMin = minVal.coerceIn(100f, 900f)
                                wghtMax = maxVal.coerceIn(100f, 900f)
                            }
                            "ital" -> if (maxVal >= 0.5f) varItalic = true
                            "slnt" -> {
                                // negative slant tilts right (standard italic), positive tilts left
                                if (minVal <= -4f) varSlant = maxOf(minVal, -12f)
                                else if (maxVal >= 4f) varSlant = minOf(maxVal, 12f)
                            }
                        }
                    }
                }
            }
        }

        val familyName = tables["name"]?.let { (off, len) ->
            if (len in 6..MaxNameTableBytes) parseFamilyName(readSlice(off, len)) else null
        }

        return ParsedFontMetadata(
            familyName = familyName,
            weight = weight,
            isItalic = isItalic,
            variableWeightMin = wghtMin,
            variableWeightMax = wghtMax,
            variableItalic = varItalic,
            variableSlant = varSlant,
        )
    }

    private fun parseFamilyName(data: ByteArray): String? {
        val count = readU16(data, 2)
        val stringOffset = readU16(data, 4)
        if (count <= 0 || 6 + count * 12 > data.size || stringOffset >= data.size) return null

        var preferredTypographic: String? = null
        var preferredFamily: String? = null
        var fallbackFamily: String? = null

        for (i in 0 until count) {
            val pos = 6 + i * 12
            val platformId = readU16(data, pos)
            val languageId = readU16(data, pos + 4)
            val nameId = readU16(data, pos + 6)
            if (nameId != 1 && nameId != 16) continue
            val length = readU16(data, pos + 8)
            val offset = readU16(data, pos + 10)
            val start = stringOffset + offset
            if (length <= 0 || start < 0 || start + length > data.size) continue

            val decoded = when (platformId) {
                0, 3 -> String(data, start, length, StandardCharsets.UTF_16BE)
                1 -> String(data, start, length, StandardCharsets.ISO_8859_1)
                else -> continue
            }.cleanFontName()
            if (decoded.isEmpty()) continue

            val isEnglish = (platformId == 3 && languageId == 0x0409) || (platformId == 1 && languageId == 0) || platformId == 0
            if (nameId == 16) {
                if (isEnglish) preferredTypographic = decoded
                else if (preferredTypographic == null) preferredTypographic = decoded
            } else {
                if (isEnglish) preferredFamily = decoded
                else if (fallbackFamily == null) fallbackFamily = decoded
            }
        }
        return preferredTypographic ?: preferredFamily ?: fallbackFamily
    }

    private fun String.cleanFontName(): String =
        filterNot { it.isISOControl() }.trim().take(120)

    private fun readU16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 8) or (b[offset + 1].toInt() and 0xFF)

    private fun readU32(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun readFixed1616(b: ByteArray, offset: Int): Float = readU32(b, offset) / 65536f
}
