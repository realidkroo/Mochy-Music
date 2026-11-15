// PillScript.kt

package com.mochyy.mochymusic.floating

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.WordTimestamp
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

internal enum class PillRowKind { LEAD, BACKING, INTRO, TITLE, ARTIST }

// one row the pill can show: a sung line, its backing vocal, the intro's dots, or the song's title and artist
@Immutable
internal class PillRow(
    val kind: PillRowKind,
    val text: String,
    // timed words, null when the row is only line-synced or isn't lyrics at all
    val words: List<WordTimestamp>?,
    val startMs: Long,
    val endMs: Long,
    // the singer's side in a duet; null means the pill staggers it, top row at the start and bottom row at the end
    val side: LyricsAlignment?,
    // the last row of a line that's followed by a long break, so it turns into the break's dots
    val hostsBreak: Boolean = false,
) {
    val sweeps: Boolean get() = !words.isNullOrEmpty()
    val small: Boolean get() = kind == PillRowKind.BACKING || kind == PillRowKind.ARTIST
}

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
    // the lyrics read right to left, so the stagger swaps sides
    val mirrored: Boolean,
) {
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
            rows = infoRows(title, artist),
            stops = listOf(PillStop(0L, 0)),
            mirrored = false,
        )

        fun build(raw: String?, title: String, artist: String, appRtl: Boolean): PillScript {
            val lines = raw
                ?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
                ?.let { runCatching { LyricsUtils.parseLyricsCached(it) }.getOrNull() }
                .orEmpty()
            if (lines.none { it.text.isNotBlank() } || LyricsUtils.isUnsynced(lines)) return info(title, artist)
            val mirrored = lyricsReadRtl(lines) != appRtl
            val groups = groupLines(lines, usesAgentAlignment(lines), mirrored)
            if (groups.isEmpty()) return info(title, artist)
            return layOut(groups, title, artist, mirrored)
        }
    }
}

// a sung line with the backing vocals that answer it
private class LineGroup(val lead: PillRow, val backing: MutableList<PillRow> = ArrayList(1)) {
    val startMs: Long get() = lead.startMs
    val endMs: Long get() = backing.fold(lead.endMs) { end, row -> maxOf(end, row.endMs) }
}

private fun infoRows(title: String, artist: String) = listOf(
    PillRow(PillRowKind.TITLE, title, null, 0L, 0L, side = null),
    PillRow(PillRowKind.ARTIST, artist, null, 0L, 0L, side = null),
)

// lead lines in order, each with its backing vocals: bracketed parts of the line, and background lines tucked under the line they answer
private fun groupLines(lines: List<LyricsEntry>, duet: Boolean, mirrored: Boolean): List<LineGroup> {
    val groups = ArrayList<LineGroup>(lines.size)
    for (index in lines.indices) {
        val entry = lines[index]
        if (entry.text.isBlank()) continue
        val endMs = LyricsUtils.lineEndMs(lines, index)
        val side = if (duet) alignmentFor(entry, useAgentAlignment = true, mirrored = mirrored) else null
        val parts = splitBackingVocal(entry.text, entry.words)
        val owner = groups.lastOrNull()
        if (entry.isBackground && owner != null) {
            val text = parts.backing?.takeIf { parts.lead.isBlank() } ?: entry.text
            val words = if (parts.lead.isBlank()) parts.backingWords else entry.words
            owner.backing += PillRow(PillRowKind.BACKING, text, words, entry.time, endMs, side)
            continue
        }
        // a line that's all brackets is still the line being sung
        if (parts.backing == null || parts.lead.isBlank()) {
            val text = parts.backing?.takeIf { parts.lead.isBlank() } ?: entry.text
            val words = if (parts.backing != null) parts.backingWords else entry.words
            groups += LineGroup(PillRow(PillRowKind.LEAD, text, words, entry.time, endMs, side))
            continue
        }
        val group = LineGroup(PillRow(PillRowKind.LEAD, parts.lead, parts.leadWords, entry.time, endMs, side))
        val backingWords = parts.backingWords
        val backingStart = backingWords?.firstOrNull()?.let { (it.startTime * 1000).toLong() } ?: entry.time
        val backingEnd = backingWords?.lastOrNull()?.let { (it.endTime * 1000).toLong() } ?: endMs
        group.backing += PillRow(PillRowKind.BACKING, parts.backing, backingWords, backingStart, backingEnd, side)
        groups += group
    }
    return groups
}

// the rows in reading order and the stops between them: each line takes the top slot as it starts, a long break turns the line before it into dots
private fun layOut(groups: List<LineGroup>, title: String, artist: String, mirrored: Boolean): PillScript {
    val rows = ArrayList<PillRow>(groups.size * 2 + 3)
    val leadRow = IntArray(groups.size)
    val intro = groups.first().startMs >= MinInterludeGapMs
    if (intro) rows += PillRow(PillRowKind.INTRO, "", null, 0L, groups.first().startMs, side = null)
    for ((k, group) in groups.withIndex()) {
        val breakAfter = groups.getOrNull(k + 1)?.let { it.startMs - group.endMs >= MinInterludeGapMs } ?: false
        leadRow[k] = rows.size
        val last = group.backing.lastIndex
        rows += group.lead.withBreak(breakAfter && last < 0)
        group.backing.forEachIndexed { i, row -> rows += row.withBreak(breakAfter && i == last) }
    }
    val titleRow = rows.size
    rows += infoRows(title, artist)

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
    return PillScript(rows, stops, mirrored)
}

private fun PillRow.withBreak(hosts: Boolean) =
    if (hosts == hostsBreak) this else PillRow(kind, text, words, startMs, endMs, side, hosts)

// lines that overlap can hand over before the earlier one's stop; a later stop at the same moment or before simply wins
private fun MutableList<PillStop>.addInOrder(stop: PillStop) {
    while (isNotEmpty() && last().timeMs >= stop.timeMs && size > 1) removeAt(lastIndex)
    if (isNotEmpty() && last().timeMs >= stop.timeMs) {
        set(lastIndex, PillStop(last().timeMs, stop.top, stop.breakFromMs, stop.breakToMs))
        return
    }
    add(stop)
}
