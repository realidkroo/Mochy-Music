// LyricsShareSession.kt
//
// The lyrics half of a song share: which lines are picked, the style, and how each word looks.
// The song share (share/song) holds everything else, the background and the card included.

package com.mochyy.mochymusic.ui.player.share

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextStyle
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.LyricsLineInsets

internal data class ShareSong(val title: String, val artist: String, val thumbnailUrl: String?)

/** Each style holds as many words as still read well on the picture. */
internal enum class ShareStyle(val maxWords: Int) {
    Card(25),
    Cinematic(40),
}

/** The most any style holds; lines that would go past it can't be picked at all. */
internal val ShareWordLimit = ShareStyle.entries.maxOf { it.maxWords }

internal sealed interface ShareBackground {
    /** soft colour clouds taken from the cover, drifting slowly, over the cover blurred */
    data object FromMusic : ShareBackground

    /** a picture from the phone; [opacity] fades it toward black so the words stay readable */
    data class Picture(val uri: Uri, val blur: Boolean = true, val opacity: Float = 0.85f) : ShareBackground

    data class Solid(val color: Color) : ShareBackground
}

/** How one word is drawn in the Cinematic style. */
internal data class WordLook(val glow: Boolean = false, val caps: Boolean = false, val big: Boolean = false) {
    val plain: Boolean get() = !glow && !caps && !big
}

/** A word's place: which lyric line, and which word in it. */
internal data class WordKey(val line: Int, val word: Int)

/**
 * The line held down to start sharing, as it looks in the lyrics, so its flying copy can start out
 * identical to it. [bounds] reads the row's place live: the lyrics keep scrolling with the song,
 * and closing the share flies the line back to wherever it is by then.
 */
internal class ShareFlightSource(
    /** the row in the lyrics page, so the page can hide its own copy while this one is out */
    val lyricIndex: Int,
    val text: String,
    val style: TextStyle,
    val alignment: LyricsAlignment,
    val insets: LyricsLineInsets,
    /** how big and how bright the line rests in the lyrics right now */
    val scale: Float,
    val alpha: Float,
    val bounds: () -> Rect?,
)

@Stable
internal class LyricsShareSession(
    val song: ShareSong,
    /** every lyric line of the song, in order */
    val lines: List<String>,
    // the same lines with their timing, so a video can flow through the picked ones
    val entries: List<LyricsEntry>,
    /** the line that was held down to open the share */
    val held: Int,
    val flightSource: ShareFlightSource?,
) {
    /** words before each line, so any run of lines is counted in one subtraction */
    private val wordsBefore = IntArray(lines.size + 1).also { sums ->
        lines.forEachIndexed { i, line -> sums[i + 1] = sums[i] + wordsOf(line).size }
    }

    /** The picked lines. Always one unbroken run, so a share reads as a real passage of the song. */
    var range by mutableStateOf<IntRange?>(held..held)
        private set

    /** Where the last change started, so the rows it touched can light up one after another from there. */
    var sweepFrom by mutableIntStateOf(held)
        private set

    var style by mutableStateOf(ShareStyle.Card)

    /** colours taken from the cover, strongest first; empty until the cover has been read */
    var palette by mutableStateOf<List<Color>>(emptyList())

    /** Cinematic: the song's name under the words instead of above them */
    var headerBelow by mutableStateOf(false)

    val looks = mutableStateMapOf<WordKey, WordLook>()

    // what "Select" does once the lines are picked: the song share moves on to the styles
    var onLinesPicked: (PopupSheetState) -> Unit = {}

    /** the held line's row in the picker, so the flying copy knows where to land */
    var flightTarget by mutableStateOf<LayoutCoordinates?>(null)

    val choreography = ShareChoreography(held = held, hasFlight = flightSource != null, lineCount = lines.size)

    val chosen: List<Pair<Int, String>> by derivedStateOf { range?.map { it to lines[it] }.orEmpty() }
    val lineCount: Int by derivedStateOf { range?.let { it.last - it.first + 1 } ?: 0 }
    val wordCount: Int by derivedStateOf { range?.let(::wordsIn) ?: 0 }

    /** True once the run can't grow: a neighbour is there but won't fit, and the other side is too or has no more lines. */
    val atLimit: Boolean by derivedStateOf {
        val r = range ?: return@derivedStateOf false
        val above = r.first - 1
        val below = r.last + 1
        val blockedAbove = above in lines.indices && !canPick(above)
        val blockedBelow = below in lines.indices && !canPick(below)
        (blockedAbove || blockedBelow) &&
            (blockedAbove || above !in lines.indices) &&
            (blockedBelow || below !in lines.indices)
    }

    private fun wordsIn(lines: IntRange): Int = wordsBefore[lines.last + 1] - wordsBefore[lines.first]

    fun isPicked(line: Int): Boolean = range?.contains(line) == true

    fun fits(style: ShareStyle): Boolean = wordCount <= style.maxWords

    /** Picked, but past what a card holds: only Cinematic can show this line. */
    fun pastCard(line: Int): Boolean {
        val r = range ?: return false
        return line in r && wordsIn(r.first..line) > ShareStyle.Card.maxWords
    }

    /**
     * Whether tapping this line would work. Picking a line away from the run picks every line in
     * between too, so the whole stretch has to fit.
     */
    fun canPick(line: Int): Boolean {
        if (line !in lines.indices) return false
        val r = range ?: return true
        if (line in r) return true
        return wordsIn(minOf(r.first, line)..maxOf(r.last, line)) <= ShareWordLimit
    }

    /**
     * Picks or unpicks a line and hands back whether anything changed. A line past a gap fills the
     * gap. Unpicking a line in the middle drops it and the shorter side with it, since the run
     * can't have a hole in it.
     */
    fun toggle(line: Int): Boolean {
        if (!canPick(line)) return false
        val r = range
        sweepFrom = when {
            r == null || line in r -> line
            line < r.first -> r.first
            else -> r.last
        }
        range = when {
            r == null -> line..line
            line !in r -> minOf(r.first, line)..maxOf(r.last, line)
            r.first == r.last -> null
            line == r.first -> (r.first + 1)..r.last
            line == r.last -> r.first..(r.last - 1)
            line - r.first < r.last - line -> (line + 1)..r.last
            else -> r.first..(line - 1)
        }
        return true
    }

    /** How much of the lyrics page's own copy of a line should show while the share is open. */
    fun lyricsLineAlpha(lyricIndex: Int): Float =
        if (flightSource?.lyricIndex == lyricIndex) choreography.sourceAlpha() else 1f

    fun look(key: WordKey): WordLook = looks[key] ?: WordLook()

    fun setLook(key: WordKey, look: WordLook) {
        if (look.plain) looks.remove(key) else looks[key] = look
    }
}

internal fun wordsOf(line: String): List<String> = line.split(' ', '\t').filter { it.isNotEmpty() }
