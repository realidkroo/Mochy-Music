// LyricsFileFormats.kt

package com.mochyy.mochymusic.songedit

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.Inflater

// the lyric files beyond ttml and srt: loose lrc, karaoke subtitles and the word-timed files of netease, qq music and kugou, all turned into lrc lines
internal object LyricsFileFormats {
    // kugou's .krc is zlib behind this xor key, the same in every file
    private val KrcKey = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
    private const val MaxTextBytes = 4_000_000

    private val AssDialogue = Regex("^Dialogue:\\s*[^,]*,(\\d+):(\\d{2}):(\\d{2})[.:](\\d{1,3}),[^,]*,(?:[^,]*,){6}(.*)$", RegexOption.MULTILINE)
    private val AssBreak = Regex("\\\\[Nnh]")
    private val AssOverride = Regex("\\{[^}]*\\}")
    private val AssKaraoke = Regex("\\\\[kK][fo]?(\\d+)")

    // netease: "[lineStart,length](wordStart,length,0)word", every time in milliseconds from the start
    private val YrcLine = Regex("^\\[(\\d+),\\d+\\](\\(\\d+,\\d+,-?\\d+\\).*)$", RegexOption.MULTILINE)
    private val YrcWord = Regex("\\((\\d+),\\d+,-?\\d+\\)(.*?)(?=\\(\\d+,\\d+,-?\\d+\\)|$)")

    // kugou: "[lineStart,length]<offset,length,0>word", each word timed from its line's start
    private val KrcLine = Regex("^\\[(\\d+),\\d+\\](<\\d+,\\d+,-?\\d+>.*)$", RegexOption.MULTILINE)
    private val KrcWord = Regex("<(\\d+),\\d+,-?\\d+>(.*?)(?=<\\d+,\\d+,-?\\d+>|$)")

    // qq music: "[lineStart,length]word(wordStart,length)", the time after each word
    private val QrcLine = Regex("^\\[(\\d+),\\d+\\](.*\\(\\d+,\\d+\\).*)$", RegexOption.MULTILINE)
    private val QrcWord = Regex("(.*?)\\((\\d+),\\d+\\)")
    private val QrcXml = Regex("LyricContent=\"([^\"]*)\"")

    // lrc stamps the line regex can't read: [0:12], [00:12], [00:12.5] or [00:12:50]
    private val LrcStamp = Regex("([\\[<])(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?([\\]>])")
    private val CanonicalStamp = Regex("\\d{2}:\\d{2}\\.\\d{2,3}")
    private val LrcOffset = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*\\]\\s*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    private class Piece(val text: String, val startMs: Long)

    fun convert(raw: String): String {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        // qq music's own files keep the lyrics escaped inside an xml attribute
        val qrc = QrcXml.find(text)?.groupValues?.get(1)?.let(::unescapeXml)
        return convertText(qrc ?: text)
    }

    private fun convertText(text: String): String = when {
        AssDialogue.containsMatchIn(text) -> fromAss(text)
        YrcLine.containsMatchIn(text) -> fromYrc(text)
        KrcLine.containsMatchIn(text) -> fromKrc(text)
        QrcLine.containsMatchIn(text) -> fromQrc(text)
        else -> normalizeLrc(text)
    }

    // text files come in utf-8, utf-16 from windows, or gbk from older chinese players
    fun decodeText(bytes: ByteArray): String {
        fun startsWith(vararg marker: Int) = bytes.size >= marker.size && marker.indices.all { bytes[it] == marker[it].toByte() }
        val text = when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> strict(bytes, Charsets.UTF_8)
                ?: charset("GB18030")?.let { strict(bytes, it) }
                ?: String(bytes, charset("windows-1252") ?: Charsets.ISO_8859_1)
        }
        return text.removePrefix("﻿")
    }

    // the text inside a kugou .krc, or null when these bytes aren't one
    fun decodeKrc(bytes: ByteArray): String? {
        if (bytes.size < 5 || String(bytes, 0, 4, Charsets.ISO_8859_1) != "krc1") return null
        val body = ByteArray(bytes.size - 4) { (bytes[it + 4].toInt() xor KrcKey[it % KrcKey.size]).toByte() }
        val inflater = Inflater()
        return try {
            inflater.setInput(body)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, read)
                if (out.size() > MaxTextBytes) return null
            }
            decodeText(out.toByteArray())
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    // a line's text with no word timing, and karaoke {\k} syllables as a word-timed line
    private fun fromAss(raw: String): String = AssDialogue.findAll(raw).mapNotNull { match ->
        val (h, m, s, frac, text) = match.destructured
        val start = ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + fraction(frac)
        val clean = text.replace(AssBreak, " ")
        val pieces = ArrayList<Piece>()
        var cursor = start
        var syllable = start
        var karaoke = false
        var last = 0
        for (block in AssOverride.findAll(clean)) {
            pieces += Piece(clean.substring(last, block.range.first), syllable)
            last = block.range.last + 1
            AssKaraoke.find(block.value)?.let {
                karaoke = true
                syllable = cursor
                cursor += it.groupValues[1].toLong() * 10
            }
        }
        pieces += Piece(clean.substring(last), syllable)
        if (karaoke) richLine(start, pieces) else plainLine(start, pieces.joinToString("") { it.text })
    }.joinToString("\n")

    private fun fromYrc(raw: String): String = YrcLine.findAll(raw).mapNotNull { line ->
        val words = YrcWord.findAll(line.groupValues[2]).map { Piece(it.groupValues[2], it.groupValues[1].toLong()) }.toList()
        richLine(line.groupValues[1].toLong(), words)
    }.joinToString("\n")

    private fun fromKrc(raw: String): String = KrcLine.findAll(raw).mapNotNull { line ->
        val start = line.groupValues[1].toLong()
        val words = KrcWord.findAll(line.groupValues[2]).map { Piece(it.groupValues[2], start + it.groupValues[1].toLong()) }.toList()
        richLine(start, words)
    }.joinToString("\n")

    private fun fromQrc(raw: String): String = QrcLine.findAll(raw).mapNotNull { line ->
        val words = QrcWord.findAll(line.groupValues[2]).map { Piece(it.groupValues[1], it.groupValues[2].toLong()) }.toList()
        richLine(line.groupValues[1].toLong(), words)
    }.joinToString("\n")

    // rewrites only the stamps the parser would skip, and folds an [offset:] tag into every time
    private fun normalizeLrc(raw: String): String {
        val offset = LrcOffset.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val text = if (offset != 0L) raw.replace(LrcOffset, "") else raw
        return text.replace(LrcStamp) { match ->
            val (open, minutes, seconds, frac, close) = match.destructured
            val body = match.value.substring(1, match.value.length - 1)
            if ((open == "[") != (close == "]") || (offset == 0L && CanonicalStamp.matches(body))) return@replace match.value
            // a positive offset shows the lyrics sooner
            val ms = maxOf(0L, minutes.toLong() * 60_000 + seconds.toLong() * 1000 + fraction(frac) - offset)
            if (open == "[") LyricsFileReader.lrcTime(ms) else "<${LyricsFileReader.wordTime(ms)}>"
        }
    }

    // words keep their own spaces, so "<t>Ever <t>after" reads "Ever after" and "<t>我<t>爱" reads "我爱"
    private fun richLine(lineMs: Long, pieces: List<Piece>): String? {
        val words = ArrayList<Piece>()
        for (piece in pieces) {
            if (piece.text.isEmpty()) continue
            val previous = words.lastOrNull()
            when {
                piece.text.isNotBlank() -> words += piece
                // a timed gap between words joins the word before it
                previous != null -> words[words.lastIndex] = Piece(previous.text + piece.text, previous.startMs)
            }
        }
        if (words.isEmpty()) return null
        return LyricsFileReader.lrcTime(lineMs) + words.joinToString("") { "<${LyricsFileReader.wordTime(it.startMs)}>${it.text}" }
    }

    private fun plainLine(ms: Long, text: String): String? =
        text.trim().takeIf { it.isNotEmpty() }?.let { LyricsFileReader.lrcTime(ms) + it }

    // "5" is half a second, "05" is 50 ms, "050" is 50 ms
    private fun fraction(digits: String): Long = digits.padEnd(3, '0').take(3).toLongOrNull() ?: 0L

    private fun unescapeXml(text: String): String = text
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.let { code -> String(Character.toChars(code)) } ?: it.value }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.let { code -> String(Character.toChars(code)) } ?: it.value }
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun strict(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun charset(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()
}
