package com.mochyy.mochymusic.ui.theme

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal const val MaxCustomFontBytes = 32L * 1024 * 1024

internal class FontTooLargeException : IOException()

internal fun isTtfName(name: String) = name.endsWith(".ttf", ignoreCase = true)

internal fun hasTrueTypeHeader(input: InputStream): Boolean =
    input.read() == 0 && input.read() == 1 && input.read() == 0 && input.read() == 0

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
