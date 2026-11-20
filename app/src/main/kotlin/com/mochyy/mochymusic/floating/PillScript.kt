// PillScript.kt

package com.mochyy.mochymusic.floating

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.WordTimestamp
import com.mochyy.mochymusic.ui.player.LineReading
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.MinInterludeGapMs
import com.mochyy.mochymusic.ui.player.alignmentFor
import com.mochyy.mochymusic.ui.player.lyricsReadRtl
import com.mochyy.mochymusic.ui.player.splitBackingVocal
import com.mochyy.mochymusic.ui.player.usesAgentAlignment

// after the last line the pill waits this long before the song's title comes back
private const val OutroHoldMs = 1_500L

// a break's dots light one by one, this many of them
internal const val PillDots = 3

// the bubble shows two rows, and one more when lines come with a reading or a translation under them
internal const val BaseSlots = 2
private const val ReadingSlots = 3

internal enum class PillRowKind { LEAD, BACKING, READING, TRANSLATION, INTRO, TITLE, ARTIST }

// one row the pill can show: a sung line, its backing vocal, reading or translation, the intro's dots, or the song's title and artist
@Immutable
internal class PillRow(
    val kind: PillRowKind,
    val text: String,
    // timed words, null when the row is only line-synced or isn't lyrics at all
    val words: List<WordTimestamp>?,
    val startMs: Long,
    val endMs: Long,
    // the same side the lyrics page gives the line, so a duet's singers keep theirs and everything else starts at the reading side
    val side: LyricsAlignment,
    // rows of the same sung line share this, so the whole line lights up together
    val line: Int = -1,
    // the last row of a line that's followed by a long break, so it turns into the break's dots
    val hostsBreak: Boolean = false,
) {
    val sweeps: Boolean get() = !words.isNullOrEmpty()
    val small: Boolean get() = kind != PillRowKind.LEAD && kind != PillRowKind.TITLE && kind != PillRowKind.INTRO

    // the song's title or artist, which has no time of its own
    val isInfo: Boolean get() = kind == PillRowKind.TITLE || kind == PillRowKind.ARTIST
}

// what goes under each line besides its backing vocals, by line index, from the same settings the lyrics page reads
@Immutable
internal class PillReadings(
    val romanized: List<LineReading?>,
    val translations: List<String?>,
    // the romanisation is sung as the line and the original goes under it
    val romanizedAsMain: Boolean,
)

// from timeMs on, this row sits in the top slot; a break also says when its dots run, so they can light up through it
@Immutable
internal class PillStop(
    val timeMs: Long,
    val top: Int,
    val breakFromMs: Long = -1L,
    val breakToMs: Long = -1L,
) {
    val isBreak: Boolean get() = breakFromMs >= 0L
}

// one song's lyrics laid out for the floating pill, built once off the main thread and only read after that
@Immutable
internal class PillScript(
    val rows: List<PillRow>,
    val stops: List<PillStop>,
    // how many rows the bubble shows at once
    val slots: Int,
) {
    // false when the bubble only has the song's title to show
    val sung: Boolean get() = rows.any { it.kind == PillRowKind.LEAD }

    // the stop that holds at positionMs
    fun stopAt(positionMs: Long): Int {
        var low = 0
        var high = stops.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (stops[mid].timeMs <= positionMs) low = mid else high = mid - 1
        }
        return low
    }

    // how many of a break's dots are lit at positionMs, or -1 outside a break; the first one lights straight away
    fun dotsLit(stop: Int, positionMs: Long): Int {
        val s = stops[stop]
        if (!s.isBreak) return -1
        val span = (s.breakToMs - s.breakFromMs).coerceAtLeast(1L)
        val through = ((positionMs - s.breakFromMs) * PillDots / span).toInt()
        return (through + 1).coerceIn(1, PillDots)
    }

    // the next moment after positionMs where the pill changes anything: the next stop, or a break's next dot
    fun nextChangeMs(stop: Int, positionMs: Long): Long {
        val next = stops.getOrNull(stop + 1)?.timeMs ?: Long.MAX_VALUE
        val s = stops[stop]
        if (!s.isBreak) return next
        val span = (s.breakToMs - s.breakFromMs).coerceAtLeast(1L)
        for (dot in 1 until PillDots) {
            val at = s.breakFromMs + span * dot / PillDots
            if (at > positionMs) return minOf(at, next)
        }
        return next
    }

    companion object {
        // no timed lyrics to follow: the bubble shows the song's title over its artist
        fun info(title: String, artist: String): PillScript = PillScript(
            rows = infoRows(title, artist, line = 0),
            stops = listOf(PillStop(0L, 0)),
            slots = BaseSlots,
        )

        // the saved lyrics as lines, empty when there are none
        fun lines(raw: String?): List<LyricsEntry> = raw
            ?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
            ?.let { runCatching { LyricsUtils.parseLyricsCached(it) }.getOrNull() }
            .orEmpty()

        fun build(lines: List<LyricsEntry>, title: String, artist: String, appRtl: Boolean, readings: PillReadings? = null): PillScript {
            if (lines.none { it.text.isNotBlank() } || LyricsUtils.isUnsynced(lines)) return info(title, artist)
            val mirrored = lyricsReadRtl(lines) != appRtl
            val groups = groupLines(lines, usesAgentAlignment(lines), mirrored, readings)
            if (groups.isEmpty()) return info(title, artist)
            return layOut(groups, title, artist)
        }
    }
}

// a sung line with what goes under it: its reading, the backing vocals that answer it, and its translation
private class LineGroup(val lead: PillRow) {
    var reading: PillRow? = null
    val backing = ArrayList<PillRow>(1)
    var translation: PillRow? = null
    val startMs: Long get() = lead.startMs
    val endMs: Long get() = backing.fold(lead.endMs) { end, row -> maxOf(end, row.endMs) }

    fun rows(): List<PillRow> = buildList {
        add(lead)
        reading?.let(::add)
        addAll(backing)
        translation?.let(::add)
    }
}

private fun infoRows(title: String, artist: String, line: Int) = listOf(
    PillRow(PillRowKind.TITLE, title, null, 0L, 0L, LyricsAlignment.START, line),
    PillRow(PillRowKind.ARTIST, artist, null, 0L, 0L, LyricsAlignment.START, line),
)

// lead lines in order, each with its reading, its backing vocals (bracketed parts and the background lines under it) and its translation
private fun groupLines(lines: List<LyricsEntry>, duet: Boolean, mirrored: Boolean, readings: PillReadings?): List<LineGroup> {
    val groups = ArrayList<LineGroup>(lines.size)
    for (index in lines.indices) {
        val entry = lines[index]
        if (entry.text.isBlank()) continue
        val endMs = LyricsUtils.lineEndMs(lines, index)
        val owner = groups.lastOrNull()
        if (entry.isBackground && owner != null) {
            // a background line answers the line before it, so it sits small on that singer's side
            val parts = splitBackingVocal(entry.text, entry.words)
            val text = parts.backing?.takeIf { parts.lead.isBlank() } ?: entry.text
            val words = if (parts.lead.isBlank()) parts.backingWords else entry.words
            owner.backing += PillRow(PillRowKind.BACKING, text, words, entry.time, endMs, owner.lead.side)
            continue
        }
        val side = alignmentFor(entry, useAgentAlignment = duet, mirrored = mirrored)
        val reading = readings?.romanized?.getOrNull(index)?.takeIf { !it.text.isNullOrBlank() }
        val asMain = reading != null && readings?.romanizedAsMain == true
        // with the romanisation as the line, it's sung in its own timed pieces and the original goes under it
        val text = if (asMain) reading?.text.orEmpty() else entry.text
        val words = if (asMain) reading?.words else entry.words
        val parts = splitBackingVocal(text, words)
        val group = when {
            parts.backing == null -> LineGroup(PillRow(PillRowKind.LEAD, text, words, entry.time, endMs, side))
            // a line that's all brackets is still the line being sung
            parts.lead.isBlank() -> LineGroup(PillRow(PillRowKind.LEAD, parts.backing, parts.backingWords, entry.time, endMs, side))
            else -> LineGroup(PillRow(PillRowKind.LEAD, parts.lead, parts.leadWords, entry.time, endMs, side)).also {
                val backingWords = parts.backingWords
                val backingStart = backingWords?.firstOrNull()?.let { word -> (word.startTime * 1000).toLong() } ?: entry.time
                val backingEnd = backingWords?.lastOrNull()?.let { word -> (word.endTime * 1000).toLong() } ?: endMs
                it.backing += PillRow(PillRowKind.BACKING, parts.backing, backingWords, backingStart, backingEnd, side)
            }
        }
        if (reading != null) {
            group.reading = if (asMain) {
                PillRow(PillRowKind.READING, entry.text, entry.words, entry.time, endMs, side)
            } else {
                PillRow(PillRowKind.READING, reading.text.orEmpty(), reading.words, entry.time, endMs, side)
            }
        }
        group.translation = readings?.translations?.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?.let { PillRow(PillRowKind.TRANSLATION, it, null, entry.time, endMs, side) }
        groups += group
    }
    return groups
}

// the rows in reading order and the stops between them: each line takes the top slot as it starts, a long break turns the line before it into dots
private fun layOut(groups: List<LineGroup>, title: String, artist: String): PillScript {
    val rows = ArrayList<PillRow>(groups.size * 3 + 3)
    val leadRow = IntArray(groups.size)
    val intro = groups.first().startMs >= MinInterludeGapMs
    if (intro) rows += PillRow(PillRowKind.INTRO, "", null, 0L, groups.first().startMs, LyricsAlignment.START)
    for ((k, group) in groups.withIndex()) {
        val breakAfter = groups.getOrNull(k + 1)?.let { it.startMs - group.endMs >= MinInterludeGapMs } ?: false
        leadRow[k] = rows.size
        val own = group.rows()
        own.forEachIndexed { i, row -> rows += row.placed(line = k, hostsBreak = breakAfter && i == own.lastIndex) }
    }
    val titleRow = rows.size
    rows += infoRows(title, artist, line = groups.size)

    val stops = ArrayList<PillStop>(groups.size * 2 + 2)
    stops += if (intro) {
        PillStop(0L, 0, breakFromMs = 0L, breakToMs = groups.first().startMs)
    } else {
        PillStop(0L, leadRow[0])
    }
    for ((k, group) in groups.withIndex()) {
        stops.addInOrder(PillStop((group.startMs - LyricsUtils.LineLeadInMs).coerceAtLeast(0L), leadRow[k]))
        val next = groups.getOrNull(k + 1) ?: continue
        if (next.startMs - group.endMs >= MinInterludeGapMs) {
            stops.addInOrder(PillStop(group.endMs, leadRow[k + 1] - 1, breakFromMs = group.endMs, breakToMs = next.startMs))
        }
    }
    stops.addInOrder(PillStop(groups.maxOf { it.endMs } + OutroHoldMs, titleRow))
    val slots = if (groups.any { it.reading != null || it.translation != null }) ReadingSlots else BaseSlots
    return PillScript(rows, stops, slots)
}

private fun PillRow.placed(line: Int, hostsBreak: Boolean) =
    PillRow(kind, text, words, startMs, endMs, side, line, hostsBreak)

// lines that overlap can hand over before the earlier one's stop; a later stop at the same moment or before simply wins
private fun MutableList<PillStop>.addInOrder(stop: PillStop) {
    while (isNotEmpty() && last().timeMs >= stop.timeMs && size > 1) removeAt(lastIndex)
    if (isNotEmpty() && last().timeMs >= stop.timeMs) {
        set(lastIndex, PillStop(last().timeMs, stop.top, stop.breakFromMs, stop.breakToMs))
        return
    }
    add(stop)
}
