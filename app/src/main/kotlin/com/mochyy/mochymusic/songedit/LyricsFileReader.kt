// LyricsFileReader.kt

package com.mochyy.mochymusic.songedit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.lyrics.LyricsUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

// a lyrics file someone picked, already turned into the text the lyrics table keeps
@Immutable
data class PickedLyrics(val fileName: String, val text: String, val lines: Int)

object LyricsFileReader {
    // a lyrics file is a few kilobytes; anything past this is the wrong file
    private const val MaxBytes = 1_000_000

    private val TtmlLine = Regex("<p\\b([^>]*)\\bbegin=\"([^\"]+)\"([^>]*)>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val Tag = Regex("<[^>]+>")
    private val AnyTag = Regex("<(/?)([a-zA-Z][\\w:.-]*)\\b([^>]*?)(/?)>")
    private val BeginAttr = Regex("\\bbegin=\"([^\"]+)\"")
    private val AgentAttr = Regex("ttm:agent=\"([^\"]+)\"")
    private val BackgroundRole = Regex("ttm:role=\"x-bg\"")
    private val SrtTime = Regex("(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->")

    // lrc and plain text stay as they are, ttml and srt become lrc lines; null when nothing in it reads as lyrics
    suspend fun read(context: Context, uri: Uri): PickedLyrics? = withContext(Dispatchers.IO) {
        try {
            val raw = readText(context, uri) ?: return@withContext null
            val lyrics = when {
                raw.contains("<tt", ignoreCase = true) -> fromTtml(raw)
                SrtTime.containsMatchIn(raw) -> fromSrt(raw)
                else -> raw.trim()
            }
            if (LyricsUtils.syncKind(lyrics) == LyricsUtils.SyncKind.NONE) return@withContext null
            PickedLyrics(fileName(context, uri), lyrics, LyricsUtils.parseLyrics(lyrics).size)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun readText(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val out = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                if (out.size() + read > MaxBytes) return null
                out.write(buffer, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name()).removePrefix("﻿")
    }

    private fun fileName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    // each timed <p> becomes an lrc line; timed <span>s inside it keep their word timing as a rich sync line, the singer's agent and a background part come along too
    private fun fromTtml(raw: String): String = TtmlLine.findAll(raw).flatMap { match ->
        val ms = ttmlTime(match.groupValues[2]) ?: return@flatMap emptySequence()
        val attrs = match.groupValues[1] + match.groupValues[3]
        val agent = AgentAttr.find(attrs)?.groupValues?.get(1)?.let { "{agent:$it}" }.orEmpty()
        val inner = match.groupValues[4]
        val words = timedWords(inner)
        if (words.isEmpty()) {
            val text = inner
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                .replace(Tag, "")
                .replace(Regex("\\s+"), " ")
                .trim()
            return@flatMap if (text.isEmpty()) emptySequence() else sequenceOf("${lrcTime(ms)}$agent$text")
        }
        val lead = words.filter { !it.background }
        val backing = words.filter { it.background }
        sequenceOf(
            lead.takeIf { it.isNotEmpty() }?.let { "${lrcTime(ms)}$agent${richSync(it)}" },
            // the background part sings as its own line, from its first word
            backing.takeIf { it.isNotEmpty() }?.let { "${lrcTime(it.first().beginMs)}$agent{bg}${richSync(it)}" },
        ).filterNotNull()
    }.joinToString("\n")

    private class TtmlWord(val text: String, val beginMs: Long, val background: Boolean)

    private fun richSync(words: List<TtmlWord>) = words.joinToString(" ") { "<${wordTime(it.beginMs)}>${it.text}" }

    // the words of one <p>, each from the innermost <span> with a begin time round it; a span marked x-bg (and everything inside it) is the background part
    private fun timedWords(inner: String): List<TtmlWord> {
        class Open(val beginMs: Long?, val background: Boolean, val text: StringBuilder = StringBuilder())
        val words = ArrayList<TtmlWord>()
        val open = ArrayList<Open>()
        var last = 0
        fun text(piece: String) {
            if (piece.isEmpty()) return
            open.lastOrNull { it.beginMs != null }?.text?.append(piece)
        }
        for (tag in AnyTag.findAll(inner)) {
            text(inner.substring(last, tag.range.first))
            last = tag.range.last + 1
            val closing = tag.groupValues[1] == "/"
            val name = tag.groupValues[2].lowercase()
            val selfClosing = tag.groupValues[4] == "/"
            when {
                name == "br" -> text(" ")
                name != "span" -> Unit
                closing -> {
                    val done = open.removeLastOrNull() ?: continue
                    val word = done.text.toString().replace(Regex("\\s+"), " ").trim()
                    if (done.beginMs != null && word.isNotEmpty()) words += TtmlWord(word, done.beginMs, done.background)
                }
                else -> {
                    val attrs = tag.groupValues[3]
                    val background = BackgroundRole.containsMatchIn(attrs) || open.lastOrNull()?.background == true
                    val span = Open(BeginAttr.find(attrs)?.groupValues?.get(1)?.let(::ttmlTime), background)
                    if (!selfClosing) open += span
                }
            }
        }
        text(inner.substring(last))
        return words
    }

    private fun wordTime(ms: Long): String =
        String.format(Locale.US, "%02d:%02d.%03d", ms / 60_000, (ms / 1000) % 60, ms % 1000)

    // ttml writes time as 1:02:03.5, 02:03.5, 123.5s or 123500ms
    private fun ttmlTime(value: String): Long? {
        val v = value.trim()
        return when {
            v.endsWith("ms") -> v.removeSuffix("ms").toDoubleOrNull()?.toLong()
            v.endsWith("s") -> v.removeSuffix("s").toDoubleOrNull()?.times(1000)?.toLong()
            else -> {
                val parts = v.split(':').map { it.toDoubleOrNull() ?: return null }
                parts.fold(0.0) { total, part -> total * 60 + part }.times(1000).toLong()
            }
        }
    }

    private fun fromSrt(raw: String): String = raw.replace("\r", "").split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        val timeIndex = lines.indexOfFirst { SrtTime.containsMatchIn(it) }
        if (timeIndex < 0) return@mapNotNull null
        val (h, m, s, frac) = SrtTime.find(lines[timeIndex])!!.destructured
        val ms = ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
        val text = lines.drop(timeIndex + 1).joinToString(" ").replace(Tag, "").trim()
        if (text.isEmpty()) null else "${lrcTime(ms)}$text"
    }.joinToString("\n")

    private fun lrcTime(ms: Long): String =
        String.format(Locale.US, "[%02d:%02d.%02d]", ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10)
}
