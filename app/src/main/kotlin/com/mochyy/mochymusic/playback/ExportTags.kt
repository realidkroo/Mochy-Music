// ExportTags.kt

package com.mochyy.mochymusic.playback

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

// a song's details written into its exported file, so other players and mochy's own import know what it is
internal class ExportTags(
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val lyrics: String?,
    val cover: ExportCover?,
)

internal class ExportCover(val jpeg: ByteArray, val width: Int, val height: Int)

// writes tags into the two kinds of file the export already rebuilds: ogg opus and flac, both carry vorbis comments
internal object ExportTagWriter {
    private const val FlacCommentBlock = 4
    private const val FlacPictureBlock = 6
    private const val FlacPaddingBlock = 1
    private const val MaxHeaderBytes = 64 * 1024 * 1024

    private val OggCrc = IntArray(256) { index ->
        var crc = index shl 24
        repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        crc
    }

    // copies an ogg opus file with its comment packet swapped for tags; a file laid out any other way is copied as it is
    fun copyOpus(input: InputStream, out: OutputStream, tags: ExportTags) {
        val data = DataInputStream(input)
        val read = ArrayList<OggPage>()
        fun passThrough() {
            read.forEach { it.writeTo(out) }
            data.copyTo(out, 256 * 1024)
        }

        val first = readPage(data)?.also { read += it }
        if (first == null || !first.body.startsWith("OpusHead") || first.lacing.size != 1) return passThrough()
        // the comment packet can span pages, but it always ends one, and the audio starts on the next
        var commentPages = 0
        while (true) {
            val page = readPage(data)?.also { read += it } ?: return passThrough()
            commentPages++
            val ends = page.lacing.withIndex().filter { it.value < 255 }
            if (ends.isEmpty()) continue
            if (ends.size != 1 || ends.single().index != page.lacing.lastIndex) return passThrough()
            break
        }
        if (!read[1].body.startsWith("OpusTags")) return passThrough()

        first.writeTo(out)
        val packet = "OpusTags".toByteArray(Charsets.US_ASCII) + vorbisComments(tags)
        val newPages = packetPages(packet, serial = first.serial, firstSequence = first.sequence + 1)
        newPages.forEach { out.write(it) }
        // every page after the tags moves by however many pages the tags grew or shrank by
        val shift = newPages.size - commentPages
        while (true) {
            val page = readPage(data) ?: break
            if (shift != 0) page.renumber(page.sequence + shift)
            page.writeTo(out)
        }
    }

    // a flac header ("fLaC" and its metadata blocks) with its comments and picture swapped for tags, or null when it isn't one
    fun retagFlacHeader(header: ByteArray, tags: ExportTags): ByteArray? {
        if (header.size < 8 || !header.startsWith("fLaC")) return null
        val kept = ArrayList<Pair<Int, ByteArray>>()
        var at = 4
        while (at + 4 <= header.size) {
            val type = header[at].toInt() and 0x7F
            val last = header[at].toInt() and 0x80 != 0
            val length = (header[at + 1].toInt() and 0xFF shl 16) or (header[at + 2].toInt() and 0xFF shl 8) or (header[at + 3].toInt() and 0xFF)
            if (at + 4 + length > header.size) return null
            if (type != FlacCommentBlock && type != FlacPictureBlock) kept += type to header.copyOfRange(at + 4, at + 4 + length)
            at += 4 + length
            if (last) break
        }
        if (kept.isEmpty()) return null
        val blocks = kept + (FlacCommentBlock to vorbisComments(tags)) + listOfNotNull(tags.cover?.let { FlacPictureBlock to flacPicture(it) })
        // padding stays last, where taggers expect to grow into it
        val ordered = blocks.filter { it.first != FlacPaddingBlock } + blocks.filter { it.first == FlacPaddingBlock }
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        ordered.forEachIndexed { index, (type, body) ->
            out.write(if (index == ordered.lastIndex) type or 0x80 else type)
            out.write(body.size ushr 16 and 0xFF)
            out.write(body.size ushr 8 and 0xFF)
            out.write(body.size and 0xFF)
            out.write(body)
        }
        return out.toByteArray()
    }

    // copies a flac file with its header retagged; anything that doesn't read as flac is copied as it is
    fun copyFlac(input: InputStream, out: OutputStream, tags: ExportTags) {
        val data = DataInputStream(input)
        val header = ByteArrayOutputStream()
        fun passThrough() {
            header.writeTo(out)
            data.copyTo(out, 256 * 1024)
        }
        val marker = ByteArray(4)
        if (!data.readInto(marker, header) || !marker.startsWith("fLaC")) return passThrough()
        while (true) {
            val blockHeader = ByteArray(4)
            if (!data.readInto(blockHeader, header)) return passThrough()
            val length = (blockHeader[1].toInt() and 0xFF shl 16) or (blockHeader[2].toInt() and 0xFF shl 8) or (blockHeader[3].toInt() and 0xFF)
            if (header.size() + length > MaxHeaderBytes || !data.readInto(ByteArray(length), header)) return passThrough()
            if (blockHeader[0].toInt() and 0x80 != 0) break
        }
        val retagged = retagFlacHeader(header.toByteArray(), tags) ?: return passThrough()
        out.write(retagged)
        data.copyTo(out, 256 * 1024)
    }

    // the vorbis comment body opus and flac share; one artist field joined the way the export names files
    private fun vorbisComments(tags: ExportTags): ByteArray {
        val fields = buildList {
            add("TITLE=${tags.title}")
            tags.artist?.let { add("ARTIST=$it") }
            tags.album?.let { add("ALBUM=$it") }
            tags.albumArtist?.let { add("ALBUMARTIST=$it") }
            tags.year?.let { add("DATE=$it") }
            tags.lyrics?.let { add("LYRICS=$it") }
            tags.cover?.let { add("METADATA_BLOCK_PICTURE=" + Base64.encodeToString(flacPicture(it), Base64.NO_WRAP)) }
        }
        val out = ByteArrayOutputStream()
        val vendor = "Mochy".toByteArray(Charsets.UTF_8)
        out.le32(vendor.size)
        out.write(vendor)
        out.le32(fields.size)
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            out.le32(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    // flac's picture block, also what opus carries base64'd: a front cover jpeg
    private fun flacPicture(cover: ExportCover): ByteArray {
        val out = ByteArrayOutputStream()
        val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
        out.be32(3)
        out.be32(mime.size)
        out.write(mime)
        out.be32(0)
        out.be32(cover.width)
        out.be32(cover.height)
        out.be32(24)
        out.be32(0)
        out.be32(cover.jpeg.size)
        out.write(cover.jpeg)
        return out.toByteArray()
    }

    private class OggPage(val header: ByteArray, val body: ByteArray) {
        val lacing: List<Int> get() = (0 until (header[26].toInt() and 0xFF)).map { header[27 + it].toInt() and 0xFF }
        val serial: Int get() = le32(header, 14)
        val sequence: Int get() = le32(header, 18)

        fun renumber(sequence: Int) {
            putLe32(header, 18, sequence)
            putLe32(header, 22, 0)
            putLe32(header, 22, oggCrc(header, body))
        }

        fun writeTo(out: OutputStream) {
            out.write(header)
            out.write(body)
        }
    }

    // null at the end of the file, and for a page cut short, which a whole download never has
    private fun readPage(input: DataInputStream): OggPage? = try {
        val fixed = ByteArray(27)
        input.readFully(fixed)
        if (!fixed.startsWith("OggS")) {
            null
        } else {
            val segments = fixed[26].toInt() and 0xFF
            val header = fixed.copyOf(27 + segments)
            input.readFully(header, 27, segments)
            val body = ByteArray((0 until segments).sumOf { header[27 + it].toInt() and 0xFF })
            input.readFully(body)
            OggPage(header, body)
        }
    } catch (_: EOFException) {
        null
    }

    // one packet laid out as pages: 255 segments of up to 255 bytes each, the last page carries the packet's end
    private fun packetPages(packet: ByteArray, serial: Int, firstSequence: Int): List<ByteArray> {
        val lacing = ArrayList<Int>()
        var left = packet.size
        while (left >= 255) {
            lacing += 255
            left -= 255
        }
        lacing += left
        val groups = lacing.chunked(255)
        var offset = 0
        return groups.mapIndexed { index, group ->
            val bodySize = group.sum()
            val header = ByteArray(27 + group.size)
            "OggS".toByteArray(Charsets.US_ASCII).copyInto(header)
            header[5] = if (index > 0) 1 else 0
            // a page where no packet ends has no position
            val granule = if (index == groups.lastIndex) 0L else -1L
            for (i in 0 until 8) header[6 + i] = (granule ushr (8 * i)).toByte()
            putLe32(header, 14, serial)
            putLe32(header, 18, firstSequence + index)
            header[26] = group.size.toByte()
            group.forEachIndexed { i, value -> header[27 + i] = value.toByte() }
            val body = packet.copyOfRange(offset, offset + bodySize)
            offset += bodySize
            putLe32(header, 22, oggCrc(header, body))
            header + body
        }
    }

    // ogg's own crc: polynomial 0x04c11db7, nothing reflected, over the page with its crc field zeroed
    private fun oggCrc(header: ByteArray, body: ByteArray): Int {
        var crc = 0
        for (byte in header) crc = (crc shl 8) xor OggCrc[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        for (byte in body) crc = (crc shl 8) xor OggCrc[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    // reads exactly bytes.size bytes, keeping a copy in seen; false at the end of the file
    private fun DataInputStream.readInto(bytes: ByteArray, seen: ByteArrayOutputStream): Boolean {
        var read = 0
        while (read < bytes.size) {
            val got = read(bytes, read, bytes.size - read)
            if (got < 0) {
                seen.write(bytes, 0, read)
                return false
            }
            read += got
        }
        seen.write(bytes)
        return true
    }

    private fun ByteArray.startsWith(text: String): Boolean =
        size >= text.length && text.indices.all { this[it] == text[it].code.toByte() }

    private fun le32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() and 0xFF shl 8) or
            (bytes[at + 2].toInt() and 0xFF shl 16) or (bytes[at + 3].toInt() and 0xFF shl 24)

    private fun putLe32(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = (value ushr (8 * i)).toByte()
    }

    private fun ByteArrayOutputStream.le32(value: Int) {
        for (i in 0 until 4) write(value ushr (8 * i) and 0xFF)
    }

    private fun ByteArrayOutputStream.be32(value: Int) {
        for (i in 3 downTo 0) write(value ushr (8 * i) and 0xFF)
    }
}
