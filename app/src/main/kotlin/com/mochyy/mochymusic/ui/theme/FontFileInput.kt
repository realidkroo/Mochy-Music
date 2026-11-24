package com.mochyy.mochymusic.ui.theme

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal const val MaxCustomFontBytes = 32L * 1024 * 1024

internal class FontTooLargeException : IOException()

internal fun isTtfName(name: String) = name.endsWith(".ttf", ignoreCase = true)

internal fun isSupportedFontName(name: String) =
    name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)

internal fun hasTrueTypeHeader(input: InputStream): Boolean =
    input.read() == 0 && input.read() == 1 && input.read() == 0 && input.read() == 0

// accept standard truetype, apple truetype, and opentype cff single-font files
internal fun hasSupportedFontHeader(input: InputStream): Boolean {
    val b0 = input.read()
    val b1 = input.read()
    val b2 = input.read()
    val b3 = input.read()
    if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return false
    val isTrueType = b0 == 0 && b1 == 1 && b2 == 0 && b3 == 0
    val isAppleTrueType = b0 == 't'.code && b1 == 'r'.code && b2 == 'u'.code && b3 == 'e'.code
    val isOpenTypeCff = b0 == 'O'.code && b1 == 'T'.code && b2 == 'T'.code && b3 == 'O'.code
    return isTrueType || isAppleTrueType || isOpenTypeCff
}

internal fun InputStream.copyFontTo(
    output: OutputStream,
    maxBytes: Long = MaxCustomFontBytes,
    checkCancellation: () -> Unit = {},
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = maxBytes
    while (true) {
        checkCancellation()
        val count = read(buffer)
        if (count < 0) return
        if (count > remaining) throw FontTooLargeException()
        output.write(buffer, 0, count)
        remaining -= count
    }
}
