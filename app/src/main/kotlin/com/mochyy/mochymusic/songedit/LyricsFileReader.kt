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

    private val TtmlLine = Regex("<p\\b[^>]*\\bbegin=\"([^\"]+)\"[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val Tag = Regex("<[^>]+>")
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

    // each timed <p> becomes one lrc line; word timings inside it are dropped, the line timing is what plays
    private fun fromTtml(raw: String): String = TtmlLine.findAll(raw).mapNotNull { match ->
        val ms = ttmlTime(match.groupValues[1]) ?: return@mapNotNull null
        val text = match.groupValues[2]
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Tag, "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty()) null else "${lrcTime(ms)}$text"
    }.joinToString("\n")

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
