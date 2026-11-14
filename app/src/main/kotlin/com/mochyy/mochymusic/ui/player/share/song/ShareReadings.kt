// ShareReadings.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.constants.LyricsRomanizeAsMainKey
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.constants.TranslateSourceLanguageKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.lyrics.GoogleTranslateEngine
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.lyrics.YouTubeCaptionTranslation
import com.mochyy.mochymusic.ui.player.LineReading
import com.mochyy.mochymusic.ui.player.readSong
import com.mochyy.mochymusic.ui.player.romanizeSwitchFor
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// the share's own reading switches; they start the way the app has them and only ever change this share
@Immutable
internal data class ShareReadingChoice(val romanize: Boolean = true, val asMain: Boolean = false, val translate: Boolean = false)

// a lyric line as the share draws it: the line that sings, and what sits under it
@Immutable
internal class ShareLine(val entry: LyricsEntry, val under: List<String>)

// romanisation as main and translation as the lyrics page has them right now; romanisation waits for the song's language
internal fun startingReadingChoice(context: Context) = ShareReadingChoice(
    romanize = true,
    asMain = context.dataStore.get(LyricsRomanizeAsMainKey, false),
    translate = LyricsTranslationHelper.translationOn.value,
)

// the card's line: the original with its reading and translation under it, or the romanisation singing with the original under it
internal fun SongShareSession.cardLine(entry: LyricsEntry): ShareLine {
    val choice = readingChoice
    val reading = lineReadings[entry]?.takeIf { choice.romanize }
    val readingText = reading?.text
    val translation = lineTranslations[entry]?.takeIf { choice.translate }
    if (choice.asMain && readingText != null) {
        // the romanised pieces carry the timing, so the line still sings word by word in a video
        return ShareLine(entry.copy(text = readingText, words = reading?.words, syllables = null), listOfNotNull(entry.text.trim(), translation))
    }
    return ShareLine(entry, listOfNotNull(readingText, translation))
}

// the cinematic words stay the original, since each of them can be styled, so the reading always goes under them
internal fun SongShareSession.cinematicUnder(entry: LyricsEntry): List<String> = listOfNotNull(
    lineReadings[entry]?.text?.takeIf { readingChoice.romanize },
    lineTranslations[entry]?.takeIf { readingChoice.translate },
)

// works out the share's own readings and translation for its lines, so the lyrics page's switches and lines are left alone
@Composable
internal fun ShareLyricReadings(session: SongShareSession) {
    val lines = session.lyrics.found ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    LaunchedEffect(lines) {
        val switch = withContext(Dispatchers.Default) { romanizeSwitchFor(lines) }
        // romanised to start with when the lyrics page romanises this language
        if (!session.readingChoiceTouched) {
            session.readingChoice = session.readingChoice.copy(romanize = switch != null && context.dataStore.get(switch, true))
        }
        val found = HashMap<LyricsEntry, LineReading>()
        readSong(lines, allowed = { true }) { index, reading ->
            if (reading?.text != null) found[lines[index]] = reading
        }
        session.lineReadings = found
    }
    val (target) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (source) = rememberPreference(TranslateSourceLanguageKey, defaultValue = "auto")
    LaunchedEffect(lines, target, source) {
        session.lineTranslations = emptyMap()
        // only fetched once the switch is on, and kept after, so switching it off and on again is instant
        snapshotFlow { session.readingChoice.translate }.collectLatest { on ->
            if (on && session.lineTranslations.isEmpty()) {
                session.lineTranslations = translateForShare(lines, database, session.song.id, target.ifBlank { "en" }, source)
            }
        }
    }
}

// the lines' translation: the one the lyrics page already shows, the one saved with the song, or a fresh one just for the share
private suspend fun translateForShare(
    lines: List<LyricsEntry>,
    database: MusicDatabase,
    songId: String,
    target: String,
    source: String,
): Map<LyricsEntry, String> {
    val shown = lines.mapNotNull { entry -> entry.translatedTextFlow.value?.takeIf { it.isNotBlank() }?.let { entry to it } }.toMap()
    if (shown.isNotEmpty()) return shown
    val sung = lines.filter { it.text.isNotBlank() }
    if (sung.isEmpty()) return emptyMap()
    storedTranslation(sung, database, songId, target, source)?.let { return it }
    // google needs no account, and nothing is saved, so the share never changes what the lyrics page has stored
    val translated = GoogleTranslateEngine.translate(sung.map { it.text }, target, source.ifBlank { "auto" }) ?: return emptyMap()
    return sung.zip(translated).mapNotNull { (entry, text) ->
        // a line already in the target language comes back the same, and showing it twice only doubles it
        text.takeIf { it.isNotBlank() && !it.trim().equals(entry.text.trim(), ignoreCase = true) }?.let { entry to it }
    }.toMap()
}

// the translation saved with the song, when it's in the language asked for; one row per sung line
private suspend fun storedTranslation(
    sung: List<LyricsEntry>,
    database: MusicDatabase,
    songId: String,
    target: String,
    source: String,
): Map<LyricsEntry, String>? {
    val stored = withContext(Dispatchers.IO) { database.lyrics(songId).first() } ?: return null
    val text = stored.translatedLyrics.takeIf { it.isNotBlank() } ?: return null
    val tags = setOf(target, LyricsTranslationHelper.languageTag(source, target), YouTubeCaptionTranslation.storedLanguageTag(target))
    if (stored.translationLanguage !in tags) return null
    val rows = text.lines()
    return sung.mapIndexedNotNull { index, entry -> rows.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { entry to it } }.toMap()
}
