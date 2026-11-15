// Mp4FlacRemux.kt

package com.mochyy.mochymusic.playback

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.util.Collections

// lossless songs from some sources arrive as flac inside an mp4, which players open as an m4a; this writes the same frames out as a plain .flac, nothing re-encoded
internal object Mp4FlacRemux {
    // a moov bigger than this isn't an audio download's, so it's not read into memory
    private const val MaxMoovBytes = 16L * 1024 * 1024

    // what was done with a box: read through, stepped over, or the walk found what it came for
    private enum class Step { Read, Skip, Stop }

    // flac's own header (the marker and its metadata blocks) when this mp4 holds flac, else null
    fun flacHeader(spans: List<File>): ByteArray? = runCatching {
        var header: ByteArray? = null
        walk(spans) { type, size, body ->
            if (type == "moov" && size in 1..MaxMoovBytes) {
                header = metadataBlocks(body.readExactly(size.toInt()))?.let { "fLaC".toByteArray(Charsets.US_ASCII) + it }
                Step.Stop
            } else {
                Step.Skip
            }
        }
        header
    }.getOrNull()

    // the header, then every mdat's frames in file order, which is the order they play in
    fun write(spans: List<File>, header: ByteArray, out: OutputStream) {
        out.write(header)
        walk(spans) { type, size, body ->
            if (type == "mdat") {
                body.copyExactly(size, out)
                Step.Read
            } else {
                Step.Skip
            }
        }
    }

    // the dfLa box inside the flac sample entry holds flac's metadata blocks as they'd sit in a .flac file
    private fun metadataBlocks(moov: ByteArray): ByteArray? {
        val tag = "dfLa".toByteArray(Charsets.US_ASCII)
        for (at in 4..moov.size - 4) {
            if ((0 until 4).any { moov[at + it] != tag[it] }) continue
            val size = readInt(moov, at - 4)
            // a box header, then four bytes of version and flags before the blocks
            val start = at + 8
            val end = at - 4 + size
            if (size < 12 || end > moov.size || start >= end) continue
            return moov.copyOfRange(start, end)
        }
        return null
    }

    // reads the top-level boxes one after another, handing each to the block
    private fun walk(spans: List<File>, onBox: (type: String, size: Long, body: DataInputStream) -> Step) {
        val streams = spans.map { it.inputStream().buffered(256 * 1024) }
        DataInputStream(SequenceInputStream(Collections.enumeration(streams))).use { input ->
            while (true) {
                val small = try {
                    input.readInt().toLong() and 0xFFFFFFFFL
                } catch (_: EOFException) {
                    return
                }
                val type = String(input.readExactly(4), Charsets.US_ASCII)
                var headerBytes = 8L
                val size = when (small) {
                    1L -> input.readLong().also { headerBytes = 16L }
                    // a box that runs to the end of the file
                    0L -> Long.MAX_VALUE
                    else -> small
                }
                val body = if (size == Long.MAX_VALUE) Long.MAX_VALUE else size - headerBytes
                if (body < 0) return
                when (onBox(type, body, input)) {
                    Step.Stop -> return
                    Step.Read -> Unit
                    Step.Skip -> if (body == Long.MAX_VALUE) return else input.skipExactly(body)
                }
            }
        }
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var read = 0
        while (read < count) {
            val got = read(bytes, read, count - read)
            if (got < 0) throw EOFException()
            read += got
        }
        return bytes
    }

    private fun InputStream.skipExactly(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                if (read() < 0) throw EOFException()
                left--
            }
        }
    }

    // a box that runs to the end of the file copies everything left
    private fun InputStream.copyExactly(count: Long, out: OutputStream) {
        val buffer = ByteArray(256 * 1024)
        var left = count
        while (left > 0) {
            val got = read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (got < 0) {
                if (count == Long.MAX_VALUE) return
                throw EOFException()
            }
            out.write(buffer, 0, got)
            left -= got
        }
    }
}
