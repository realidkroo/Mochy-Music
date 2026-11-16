// EmbeddedLyrics.kt
//
// Lyrics an audio file carries in its own tags: ID3 in MP3s (USLT, synced SYLT, or a TXXX
// "LYRICS"), Vorbis comments in FLAC, Opus and Ogg, and the ©lyr atom in M4A. Imported files carry their lyrics
// this way often enough, and these are there offline. Only the tags are read, never the audio.

package com.mochyy.mochymusic.lyrics

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EmbeddedLyrics {

    /** What [LyricsEntity.provider][com.mochyy.mochymusic.db.entities.LyricsEntity] says for these. */
    const val Provider = "Embedded"

    /** Random access to a file's bytes. */
    interface Source {
        val size: Long

        /** Up to [length] bytes from [position]: fewer at the end of the file, none past it. */
        fun read(position: Long, length: Int): ByteArray
    }

    /** The lyrics in [source]: synced ones over plain text. Null when it has none. */
    fun find(source: Source): String? {
        val head = source.read(0, 12)
        return when {
            head.startsWith("ID3") -> id3(source) ?: id3TagEnd(source)?.let { end ->
                // a FLAC can carry an ID3 tag in front of its own
                if (source.read(end, 4).startsWith("fLaC")) flac(source, end) else null
            }
            head.startsWith("fLaC") -> flac(source, 0)
            head.startsWith("OggS") -> ogg(source)
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(source)
            else -> null
        }
    }

    // ---- ID3v2 (MP3) --------------------------------------------------------------------------

    private fun id3TagEnd(source: Source): Long? {
        val header = source.read(0, 10)
        if (header.size < 10) return null
        val footer = header[3].toInt() == 4 && header[5].toInt() and 0x10 != 0
        return 10L + syncsafe(header, 6) + if (footer) 10 else 0
    }

    private fun id3(source: Source): String? {
        val header = source.read(0, 10)
        if (header.size < 10) return null
        val major = header[3].toInt()
        if (major != 3 && major != 4) return null
        val flags = header[5].toInt()
        val tagSize = syncsafe(header, 6)
        if (tagSize <= 0) return null
        // Frames are walked straight off the file, so cover art - often most of the tag, and
        // megabytes of it - is skipped rather than read. A v2.3 tag written unsynchronised is the
        // exception: it has to be undone as a whole before its frames can be found.
        val tag: Source = if (major == 3 && flags and 0x80 != 0) {
            if (tagSize > MaxTagBytes) return null
            BytesSource(deUnsync(source.read(10, tagSize)))
        } else {
            SliceSource(source, 10, tagSize.toLong())
        }

        var pos = 0L
        if (flags and 0x40 != 0) {
            // the extended header: v2.4 counts itself in its size, v2.3 doesn't
            val extended = tag.read(0, 4)
            if (extended.size < 4) return null
            pos = if (major == 4) syncsafe(extended, 0).toLong() else int32(extended, 0).toLong() + 4
        }
        val found = ArrayList<String>()
        while (pos + 10 <= tag.size) {
            val frameHeader = tag.read(pos, 10)
            if (frameHeader.size < 10 || frameHeader[0].toInt() == 0) break // padding
            val id = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
            val frameSize = if (major == 4) syncsafe(frameHeader, 4) else int32(frameHeader, 4)
            val frameFlags = (frameHeader[8].toInt() and 0xFF shl 8) or (frameHeader[9].toInt() and 0xFF)
            val start = pos + 10
            if (frameSize <= 0 || start + frameSize > tag.size) break
            pos = start + frameSize

            val compressedOrEncrypted =
                if (major == 4) frameFlags and 0x000C != 0 else frameFlags and 0x00C0 != 0
            if (compressedOrEncrypted || id !in LyricFrames || frameSize > MaxTagBytes) continue
            var skip = 0
            if (major == 4 && frameFlags and 0x0040 != 0 || major == 3 && frameFlags and 0x0020 != 0) skip += 1 // group id
            if (major == 4 && frameFlags and 0x0001 != 0) skip += 4 // data length indicator
            if (skip >= frameSize) continue
            var body = tag.read(start + skip, frameSize - skip)
            if (major == 4 && frameFlags and 0x0002 != 0) body = deUnsync(body)

            when (id) {
                "SYLT" -> sylt(body)
                "USLT" -> uslt(body)
                else -> txxx(body)
            }?.let(found::add)
        }
        return pick(found)
    }

    private class BytesSource(private val bytes: ByteArray) : Source {
        override val size: Long = bytes.size.toLong()

        override fun read(position: Long, length: Int): ByteArray {
            if (position < 0 || position >= size || length <= 0) return ByteArray(0)
            return bytes.copyOfRange(position.toInt(), minOf(size, position + length).toInt())
        }
    }

    /** [length] bytes of [base] from [offset], read as if they were a file of their own. */
    private class SliceSource(private val base: Source, private val offset: Long, length: Long) : Source {
        override val size: Long = minOf(length, (base.size - offset).coerceAtLeast(0))

        override fun read(position: Long, length: Int): ByteArray {
            if (position < 0 || position >= size || length <= 0) return ByteArray(0)
            return base.read(offset + position, minOf(length.toLong(), size - position).toInt())
        }
    }

    /** unsynchronised lyrics: encoding, language, a description, then the text */
    private fun uslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val charset = charsetOf(body[0].toInt()) ?: return null
        val textStart = terminatorEnd(body, 4, body[0].toInt())
        return decode(body, textStart, body.size, charset)
    }

    /** a user text frame; lyrics when its description says so */
    private fun txxx(body: ByteArray): String? {
        if (body.size < 2) return null
        val encoding = body[0].toInt()
        val charset = charsetOf(encoding) ?: return null
        val descriptionEnd = terminatorEnd(body, 1, encoding)
        val description = decode(body, 1, descriptionEnd, charset)?.trim()?.uppercase() ?: return null
        if (description !in LyricKeys) return null
        return decode(body, descriptionEnd, body.size, charset)
    }

    /**
     * Synced lyrics, as LRC. Only timed in milliseconds is read; frame-timed ones are rare and
     * can't be turned into times without the audio. Entries that start a new line with a line
     * break are joined into lines; without any, each entry is a line.
     */
    private fun sylt(body: ByteArray): String? {
        if (body.size < 7) return null
        val encoding = body[0].toInt()
        val charset = charsetOf(encoding) ?: return null
        if (body[4].toInt() != 2) return null
        var pos = terminatorEnd(body, 6, encoding)
        val entries = ArrayList<Pair<Long, String>>()
        while (pos < body.size) {
            val textEnd = terminatorEnd(body, pos, encoding)
            if (textEnd + 4 > body.size) break
            val text = decode(body, pos, textEnd, charset) ?: ""
            entries += int32(body, textEnd).toLong() to text
            pos = textEnd + 4
        }
        if (entries.isEmpty()) return null
        val breaksMarkLines = entries.any { (_, text) -> text.startsWith('\n') || text.startsWith('\r') }
        val lines = ArrayList<Pair<Long, StringBuilder>>()
        for ((time, text) in entries) {
            val startsLine = !breaksMarkLines || lines.isEmpty() || text.startsWith('\n') || text.startsWith('\r')
            if (startsLine) lines += time to StringBuilder(text.trim()) else lines.last().second.append(text)
        }
        return lines
            .filter { (_, text) -> text.isNotBlank() }
            .joinToString("\n") { (time, text) -> "${lrcTime(time)}${text.toString().trim()}" }
            .takeIf { it.isNotBlank() }
    }

    // ---- FLAC ---------------------------------------------------------------------------------

    private fun flac(source: Source, start: Long): String? {
        var pos = start + 4
        repeat(MaxFlacBlocks) {
            val header = source.read(pos, 4)
            if (header.size < 4) return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = (header[1].toInt() and 0xFF shl 16) or (header[2].toInt() and 0xFF shl 8) or (header[3].toInt() and 0xFF)
            if (type == VorbisCommentBlock) {
                return if (length in 1..MaxTagBytes) vorbisComments(source.read(pos + 4, length)) else null
            }
            if (last) return null
            pos += 4 + length
        }
        return null
    }

    private fun vorbisComments(block: ByteArray): String? {
        var pos = 0
        if (pos + 4 > block.size) return null
        pos += 4 + le32(block, pos) // vendor string
        if (pos + 4 > block.size) return null
        val count = le32(block, pos)
        pos += 4
        val found = ArrayList<String>()
        for (i in 0 until count) {
            if (pos + 4 > block.size) break
            val length = le32(block, pos)
            pos += 4
            if (length < 0 || pos + length > block.size) break
            val comment = String(block, pos, length, Charsets.UTF_8)
            pos += length
            val eq = comment.indexOf('=')
            if (eq > 0 && comment.substring(0, eq).trim().uppercase() in LyricKeys) found += comment.substring(eq + 1)
        }
        return pick(found)
    }

    // ---- Ogg (Opus, Vorbis) -------------------------------------------------------------------

    // an ogg stream's second packet holds the same vorbis comments flac uses, after "OpusTags" or "\u0003vorbis"
    private fun ogg(source: Source): String? {
        val packet = oggPacket(source, index = 1) ?: return null
        val start = when {
            packet.startsWith("OpusTags") -> 8
            packet.size > 7 && packet[0].toInt() == 3 && String(packet, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> 7
            else -> return null
        }
        return vorbisComments(packet.copyOfRange(start, packet.size))
    }

    // one whole packet, read page by page, since a comment packet with a cover inside runs across many pages
    private fun oggPacket(source: Source, index: Int): ByteArray? {
        var pos = 0L
        var packetIndex = 0
        val packet = ByteArrayOutputStream()
        repeat(MaxOggPages) {
            val header = source.read(pos, 27)
            if (header.size < 27 || !header.startsWith("OggS")) return null
            val segments = header[26].toInt() and 0xFF
            val lacing = source.read(pos + 27, segments)
            if (lacing.size < segments) return null
            var body = pos + 27 + segments
            for (value in lacing) {
                val length = value.toInt() and 0xFF
                if (packetIndex == index) {
                    if (packet.size() + length > MaxTagBytes) return null
                    packet.write(source.read(body, length))
                }
                body += length
                // a segment shorter than 255 bytes closes its packet
                if (length < 255) {
                    if (packetIndex == index) return packet.toByteArray()
                    packetIndex++
                }
            }
            pos = body
        }
        return null
    }

    // ---- MP4 / M4A ----------------------------------------------------------------------------

    private fun mp4(source: Source): String? {
        val moov = atom(source, 0, source.size, "moov") ?: return null
        val udta = atom(source, moov.first, moov.second, "udta") ?: return null
        val meta = atom(source, udta.first, udta.second, "meta") ?: return null
        // meta is a full box (version and flags before its children), except in QuickTime's files
        val ilst = atom(source, meta.first + 4, meta.second, "ilst")
            ?: atom(source, meta.first, meta.second, "ilst")
            ?: return null
        val lyrics = atom(source, ilst.first, ilst.second, "©lyr") ?: return null
        val data = atom(source, lyrics.first, lyrics.second, "data") ?: return null
        // data: a type and a locale, then the text
        val length = data.second - data.first - 8
        if (length <= 0 || length > MaxTagBytes) return null
        return pick(listOf(String(source.read(data.first + 8, length.toInt()), Charsets.UTF_8)))
    }

    /** The body of the first [type] atom between [from] and [to]: its start and its end. */
    private fun atom(source: Source, from: Long, to: Long, type: String): Pair<Long, Long>? {
        var pos = from
        while (pos + 8 <= to) {
            val header = source.read(pos, 16)
            if (header.size < 8) return null
            var size = int32(header, 0).toLong() and 0xFFFFFFFFL
            var headerSize = 8
            when (size) {
                1L -> {
                    if (header.size < 16) return null
                    size = (int32(header, 8).toLong() and 0xFFFFFFFFL shl 32) or (int32(header, 12).toLong() and 0xFFFFFFFFL)
                    headerSize = 16
                }
                0L -> size = to - pos
            }
            if (size < headerSize) return null
            if (String(header, 4, 4, Charsets.ISO_8859_1) == type) return (pos + headerSize) to minOf(pos + size, to)
            pos += size
        }
        return null
    }

    // ---- shared -------------------------------------------------------------------------------

    /** Synced over plain; nothing that is only whitespace. */
    private fun pick(found: List<String>): String? {
        val texts = found.map { it.replace("\r\n", "\n").replace('\r', '\n').trim('\u0000', ' ', '\n') }
            .filter { it.isNotBlank() }
        return texts.firstOrNull { LrcTimestamp.containsMatchIn(it) } ?: texts.firstOrNull()
    }

    private fun lrcTime(ms: Long): String =
        "[%02d:%02d.%02d]".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60, ms % 1000 / 10)

    private fun charsetOf(encoding: Int): Charset? = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> null
    }

    /** Just past the string starting at [from]: its null terminator is two bytes in UTF-16. */
    private fun terminatorEnd(bytes: ByteArray, from: Int, encoding: Int): Int {
        if (encoding == 1 || encoding == 2) {
            var i = from
            while (i + 1 < bytes.size) {
                if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i + 2
                i += 2
            }
        } else {
            for (i in from until bytes.size) if (bytes[i].toInt() == 0) return i + 1
        }
        return bytes.size
    }

    private fun decode(bytes: ByteArray, from: Int, to: Int, charset: Charset): String? =
        if (from >= to) null else String(bytes, from, to - from, charset).trimEnd('\u0000')

    /** ID3's unsynchronisation: every 0xFF 0x00 was written for a 0xFF. */
    private fun deUnsync(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            out.write(bytes[i].toInt())
            if (bytes[i].toInt() and 0xFF == 0xFF && i + 1 < bytes.size && bytes[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun syncsafe(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0x7F shl 21) or (b[at + 1].toInt() and 0x7F shl 14) or
            (b[at + 2].toInt() and 0x7F shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF shl 24) or (b[at + 1].toInt() and 0xFF shl 16) or
            (b[at + 2].toInt() and 0xFF shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8) or
            (b[at + 2].toInt() and 0xFF shl 16) or (b[at + 3].toInt() and 0xFF shl 24)

    private fun ByteArray.startsWith(text: String): Boolean =
        size >= text.length && text.indices.all { this[it] == text[it].code.toByte() }

    private val LyricFrames = setOf("USLT", "SYLT", "TXXX")
    private val LyricKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYNCEDLYRICS")
    // closing brackets escaped: Android's regex engine is stricter than desktop Java's
    private val LrcTimestamp = Regex("""\[\d{1,2}:\d{2}([.:]\d{1,3})?\]""")
    private const val VorbisCommentBlock = 4
    private const val MaxFlacBlocks = 64

    // a comment packet with a big cover spans a few hundred pages at most
    private const val MaxOggPages = 1024

    /** Tags carry cover art too, which can run to megabytes; past this something's wrong. */
    private const val MaxTagBytes = 16 * 1024 * 1024
}
