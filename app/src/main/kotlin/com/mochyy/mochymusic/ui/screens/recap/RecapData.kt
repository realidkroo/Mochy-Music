// RecapData.kt
//
// Everything the monthly recap and the Stats page show, worked out from the play history alone
// (the `event` table: one row per play of 30 s or more). Nothing here leaves the device.

package com.mochyy.mochymusic.ui.screens.recap

import android.content.Context
import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.RecapEventRow
import com.mochyy.mochymusic.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** The recap opens once a month has this many different songs in it. */
internal const val RecapMinSongs = 10

/** ...and not before this day of the month: on the 1st and 2nd the previous month is shown instead. */
internal const val RecapFirstDay = 3

@Immutable
data class StatsRange(val kind: Kind, val year: Int, val month: Int = 1) {
    enum class Kind { Month, Year }

    val start: LocalDate get() = if (kind == Kind.Month) LocalDate.of(year, month, 1) else LocalDate.of(year, 1, 1)
    val endExclusive: LocalDate get() = if (kind == Kind.Month) start.plusMonths(1) else start.plusYears(1)

    val monthName: String get() = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** What the recap calls the period: "October", or "2026" for a whole year. */
    val label: String get() = if (kind == Kind.Month) monthName else "$year"

    fun previous(): StatsRange =
        if (kind == Kind.Month) monthOf(start.minusMonths(1)) else StatsRange(Kind.Year, year - 1)

    /** Days in the month, or 12 for a year: how many buckets [ListeningStats.bucketMs] has. */
    val bucketCount: Int get() = if (kind == Kind.Month) start.lengthOfMonth() else 12

    companion object {
        fun monthOf(date: LocalDate) = StatsRange(Kind.Month, date.year, date.monthValue)
        fun yearOf(date: LocalDate) = StatsRange(Kind.Year, date.year)
    }
}

@Immutable
data class RecapSong(
    val id: String,
    val title: String,
    val artistName: String,
    val artistThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
)

@Immutable
data class RecapArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
    /** The song by this artist played the most in the period. */
    val topSong: RecapSong?,
)

// the one day a single song got played the most, if it's at least a few times
@Immutable
data class RepeatDay(val song: RecapSong, val date: LocalDate, val plays: Int)

// when and how steadily the listening happened, for the habits card
@Immutable
data class ListeningHabits(
    val activeDays: Int,
    val elapsedDays: Int,
    val longestStreak: Int,
    val biggestDay: LocalDate?,
    val biggestDayMs: Long,
    val longestSessionMs: Long,
    val longestSessionStart: LocalDateTime?,
)

@Immutable
class ListeningStats(
    val range: StatsRange,
    val totalMs: Long,
    val plays: Int,
    val songCount: Int,
    val artistCount: Int,
    val albumCount: Int,
    /** Most played first (by plays, then time), up to [MaxSongs]. */
    val topSongs: List<RecapSong>,
    /** Most listened first (by time), up to [MaxArtists]. */
    val topArtists: List<RecapArtist>,
    /** Listening time per weekday, Monday first. */
    val weekdayMs: List<Long>,
    /** Listening time per hour of the day, 0..23. */
    val hourMs: List<Long>,
    /** Listening time per day of the month, or per month of the year. */
    val bucketMs: List<Long>,
    val habits: ListeningHabits? = null,
    // songs never played before this period, best first, with how many there were and how long they played
    val newSongs: List<RecapSong> = emptyList(),
    val newSongCount: Int = 0,
    val newMs: Long = 0L,
    val repeat: RepeatDay? = null,
    // the period before this one, null when nothing was played then; partial means only up to the same day
    val previousTotalMs: Long? = null,
    val previousIsPartial: Boolean = false,
) {
    val isEmpty: Boolean get() = plays == 0

    companion object {
        const val MaxSongs = 20
        const val MaxArtists = 25
    }
}

/** What the recap card's barcode leads to once it's scanned. */
sealed interface RecapAvailability {
    data class Ready(val stats: ListeningStats, val monthProgressPercent: Int) : RecapAvailability

    /** Not unlocked yet; [comeBackText] finishes "come back later ...". */
    class NotYet(val comeBackText: (Context) -> String) : RecapAvailability
}

internal object RecapRepository {

    suspend fun load(db: MusicDatabase, range: StatsRange): ListeningStats = withContext(Dispatchers.IO) {
        val from = range.start.toStoredMillis()
        val events = db.recapEvents(from, range.endExclusive.toStoredMillis())
        val ids = events.mapTo(LinkedHashSet()) { it.songId }.toList()
        val songs = ids.chunked(500).flatMap { db.getSongsByIds(it) }.associateBy { it.id }
        val playedBefore = ids.chunked(500).flatMapTo(HashSet()) { db.songsPlayedBefore(it, from) }
        val today = LocalDate.now()
        val previous = range.previous()
        // a period still running is held against the same stretch of the one before, or every month would look like a slump
        val running = !today.isBefore(range.start) && today.isBefore(range.endExclusive)
        val previousEnd = if (running) minOf(previous.start.plusDays(ChronoUnit.DAYS.between(range.start, today) + 1), range.start) else range.start
        val previousTotal = db.listeningTotal(previous.start.toStoredMillis(), previousEnd.toStoredMillis()).takeIf { it > 0 }
        withContext(Dispatchers.Default) {
            aggregate(range, events, songs, playedBefore, previousTotal, running, today)
        }
    }

    /**
     * The month the recap is about today, or why there isn't one yet. [force] (Experimental) opens
     * it anyway with whatever has been played: this month, else last month, else this year.
     */
    suspend fun recapFor(db: MusicDatabase, today: LocalDate, force: Boolean = false): RecapAvailability {
        val normal = recapByTheRules(db, today)
        if (!force || normal is RecapAvailability.Ready) return normal
        val candidates = listOf(StatsRange.monthOf(today), StatsRange.monthOf(today.minusMonths(1)), StatsRange.yearOf(today))
        for (range in candidates) {
            val stats = load(db, range)
            if (stats.songCount > 0) return RecapAvailability.Ready(stats, progressOf(range, today))
        }
        return RecapAvailability.NotYet { context -> context.getString(R.string.recap_not_yet_single) }
    }

    private fun progressOf(range: StatsRange, today: LocalDate): Int = when {
        range.kind == StatsRange.Kind.Year && range.year == today.year ->
            (today.dayOfYear * 100f / today.lengthOfYear()).roundToInt().coerceIn(1, 100)
        range.kind == StatsRange.Kind.Month && range.year == today.year && range.month == today.monthValue ->
            (today.dayOfMonth * 100f / today.lengthOfMonth()).roundToInt().coerceIn(1, 100)
        else -> 100
    }

    private suspend fun recapByTheRules(db: MusicDatabase, today: LocalDate): RecapAvailability {
        val thisMonth = StatsRange.monthOf(today)
        if (today.dayOfMonth < RecapFirstDay) {
            val previous = StatsRange.monthOf(today.minusMonths(1))
            val stats = load(db, previous)
            if (stats.songCount >= RecapMinSongs) return RecapAvailability.Ready(stats, 100)
            val unlock = today.withDayOfMonth(RecapFirstDay)
            return RecapAvailability.NotYet { context ->
                context.getString(
                    R.string.recap_not_yet_date,
                    unlock.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    RecapFirstDay,
                    RecapMinSongs,
                )
            }
        }
        val stats = load(db, thisMonth)
        if (stats.songCount >= RecapMinSongs) {
            val percent = (today.dayOfMonth * 100f / today.lengthOfMonth()).roundToInt().coerceIn(1, 100)
            return RecapAvailability.Ready(stats, percent)
        }
        val missing = RecapMinSongs - stats.songCount
        return RecapAvailability.NotYet { context ->
            val word = if (missing == 1) context.getString(R.string.recap_song_singular) else context.getString(R.string.recap_song_plural)
            context.getString(R.string.recap_not_yet_missing, missing, word)
        }
    }

    /** First day anything was played, for the Stats timeline and as a fallback join date. */
    suspend fun firstPlayDate(db: MusicDatabase): LocalDate? = withContext(Dispatchers.IO) {
        db.firstEventTimestamp()?.let { storedMillisToDateTime(it).toLocalDate() }
    }

    private fun aggregate(
        range: StatsRange,
        events: List<RecapEventRow>,
        songs: Map<String, Song>,
        playedBefore: Set<String>,
        previousTotalMs: Long?,
        previousIsPartial: Boolean,
        today: LocalDate,
    ): ListeningStats {
        class Acc(var ms: Long = 0L, var plays: Int = 0)

        val perSong = HashMap<String, Acc>()
        val weekday = LongArray(7)
        val hour = LongArray(24)
        val buckets = LongArray(range.bucketCount)
        var total = 0L
        for (e in events) {
            val ms = e.playTime.coerceAtLeast(0L)
            total += ms
            perSong.getOrPut(e.songId) { Acc() }.apply {
                this.ms += ms
                plays++
            }
            val at = storedMillisToDateTime(e.timestamp)
            weekday[at.dayOfWeek.value - 1] += ms
            hour[at.hour] += ms
            val bucket = if (range.kind == StatsRange.Kind.Month) at.dayOfMonth - 1 else at.monthValue - 1
            if (bucket in buckets.indices) buckets[bucket] += ms
        }

        fun recapSong(id: String, acc: Acc): RecapSong? {
            val song = songs[id] ?: return null
            return RecapSong(
                id = id,
                title = song.song.title,
                artistName = song.artists.joinToString(", ") { it.name }.ifBlank { song.song.albumName ?: "Unknown artist" },
                artistThumbnailUrl = song.artists.firstNotNullOfOrNull { it.thumbnailUrl },
                thumbnailUrl = song.song.thumbnailUrl,
                playTimeMs = acc.ms,
                plays = acc.plays,
            )
        }

        val songOrder = compareByDescending<RecapSong> { it.plays }.thenByDescending { it.playTimeMs }
        val allSongs = perSong.mapNotNull { (id, acc) -> recapSong(id, acc) }.sortedWith(songOrder)

        // one artist can come back under a few ids (a channel, a topic channel, a local tag), so
        // they're merged by name - otherwise the same person could take two places in the top 10
        class ArtistAcc(val id: String, val name: String, var thumb: String?) {
            var ms = 0L
            var plays = 0
            val songs = ArrayList<RecapSong>()
        }

        val perArtist = LinkedHashMap<String, ArtistAcc>()
        val albums = HashSet<String>()
        for (s in allSongs) {
            val song = songs[s.id] ?: continue
            song.song.albumId?.let { albums += it }
            song.artists.distinctBy { it.name.trim().lowercase() }.forEach { artist ->
                val key = artist.name.trim().lowercase()
                if (key.isEmpty()) return@forEach
                val acc = perArtist.getOrPut(key) { ArtistAcc(artist.id, artist.name.trim(), artist.thumbnailUrl) }
                if (acc.thumb == null) acc.thumb = artist.thumbnailUrl
                acc.ms += s.playTimeMs
                acc.plays += s.plays
                acc.songs += s
            }
        }
        val topArtists = perArtist.values
            .sortedByDescending { it.ms }
            .take(ListeningStats.MaxArtists)
            .map { a ->
                val top = a.songs.sortedWith(songOrder).firstOrNull()
                RecapArtist(
                    id = a.id,
                    name = a.name,
                    thumbnailUrl = a.thumb ?: top?.thumbnailUrl,
                    playTimeMs = a.ms,
                    plays = a.plays,
                    topSong = top,
                )
            }

        return ListeningStats(
            range = range,
            totalMs = total,
            plays = events.size,
            songCount = perSong.size,
            artistCount = perArtist.size,
            albumCount = albums.size,
            topSongs = allSongs.take(ListeningStats.MaxSongs),
            topArtists = topArtists,
            weekdayMs = weekday.toList(),
            hourMs = hour.toList(),
            bucketMs = buckets.toList(),
        ).withExtras(events, allSongs.associateBy { it.id }, playedBefore, previousTotalMs, previousIsPartial, today)
    }
}

// the stats past the basics: habits, new finds, the song on repeat and last period, all from the same plays
private fun ListeningStats.withExtras(
    events: List<RecapEventRow>,
    songsById: Map<String, RecapSong>,
    playedBefore: Set<String>,
    previousTotalMs: Long?,
    previousIsPartial: Boolean,
    today: LocalDate,
): ListeningStats {
    if (events.isEmpty()) return this
    val dayMs = HashMap<LocalDate, Long>()
    val daySongPlays = HashMap<LocalDate, HashMap<String, Int>>()
    val sorted = events.sortedBy { it.timestamp }
    for (e in sorted) {
        val day = storedMillisToDateTime(e.timestamp).toLocalDate()
        dayMs[day] = (dayMs[day] ?: 0L) + e.playTime.coerceAtLeast(0L)
        val counts = daySongPlays.getOrPut(day) { HashMap() }
        counts[e.songId] = (counts[e.songId] ?: 0) + 1
    }

    val habits = habitsOf(sorted, dayMs, today)

    // a song counts as new when this period holds its very first play; songsById is already best first
    val newSongs = songsById.values.filter { it.id !in playedBefore }

    var repeat: RepeatDay? = null
    for ((day, counts) in daySongPlays) {
        for ((id, count) in counts) {
            if (count < 3 || count <= (repeat?.plays ?: 0)) continue
            val song = songsById[id] ?: continue
            repeat = RepeatDay(song, day, count)
        }
    }

    return ListeningStats(
        range = range,
        totalMs = totalMs,
        plays = plays,
        songCount = songCount,
        artistCount = artistCount,
        albumCount = albumCount,
        topSongs = topSongs,
        topArtists = topArtists,
        weekdayMs = weekdayMs,
        hourMs = hourMs,
        bucketMs = bucketMs,
        habits = habits,
        newSongs = newSongs.take(10),
        newSongCount = newSongs.size,
        newMs = newSongs.sumOf { it.playTimeMs },
        repeat = repeat,
        previousTotalMs = previousTotalMs,
        previousIsPartial = previousIsPartial,
    )
}

// plays closer than this (on top of the song itself) count as one sitting
private const val SessionGapMs = 10 * 60_000L

private fun ListeningStats.habitsOf(sorted: List<RecapEventRow>, dayMs: Map<LocalDate, Long>, today: LocalDate): ListeningHabits {
    val lastDay = minOf(range.endExclusive.minusDays(1), maxOf(today, range.start))
    val elapsed = (lastDay.toEpochDay() - range.start.toEpochDay() + 1).toInt().coerceAtLeast(1)

    var streak = 0
    var best = 0
    var previous: LocalDate? = null
    for (day in dayMs.keys.sorted()) {
        streak = if (previous != null && day == previous.plusDays(1)) streak + 1 else 1
        best = maxOf(best, streak)
        previous = day
    }

    // the stored time may be the start or the end of a play, so the gap allows for the longer of the two songs
    var sessionMs = 0L
    var sessionStart = sorted.first().timestamp
    var longestMs = 0L
    var longestStart = sessionStart
    for ((i, e) in sorted.withIndex()) {
        if (i > 0) {
            val prev = sorted[i - 1]
            val gap = e.timestamp - prev.timestamp
            if (gap > maxOf(prev.playTime, e.playTime) + SessionGapMs) {
                sessionMs = 0L
                sessionStart = e.timestamp
            }
        }
        sessionMs += e.playTime.coerceAtLeast(0L)
        if (sessionMs > longestMs) {
            longestMs = sessionMs
            longestStart = sessionStart
        }
    }

    val biggest = dayMs.maxByOrNull { it.value }
    return ListeningHabits(
        activeDays = dayMs.count { it.value > 0 },
        elapsedDays = elapsed,
        longestStreak = best,
        biggestDay = biggest?.key,
        biggestDayMs = biggest?.value ?: 0L,
        longestSessionMs = longestMs,
        longestSessionStart = storedMillisToDateTime(longestStart),
    )
}

// The event table stores LocalDateTime.now() as if it were UTC (see Converters), so the wall clock
// of a play comes back by reading the millis as UTC - never through the device's zone.
internal fun LocalDate.toStoredMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

internal fun storedMillisToDateTime(millis: Long): LocalDateTime =
    LocalDateTime.ofEpochSecond(Math.floorDiv(millis, 1000L), 0, ZoneOffset.UTC)

internal fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

/** "12h 05m" style, the way the recap and Stats spell listening time. */
internal fun formatHoursMinutes(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

/** Hours with one decimal ("3.4"), for the receipt and the Stats rows. */
internal fun formatHoursDecimal(ms: Long): String {
    val tenths = (ms / 360_000L).coerceAtLeast(0L)
    return "${tenths / 10}.${tenths % 10}"
}

/** Morning / afternoon / evening / night, for the hour a peak falls in. */
internal fun timeOfDayName(hour: Int): String = when (hour) {
    in 5..11 -> "mornings"
    in 12..16 -> "afternoons"
    in 17..21 -> "evenings"
    else -> "nights"
}
