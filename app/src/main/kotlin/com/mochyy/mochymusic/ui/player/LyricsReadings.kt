package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.AiProviderKey
import com.mochyy.mochymusic.constants.DeeplApiKey
import com.mochyy.mochymusic.constants.DeeplFormalityKey
import com.mochyy.mochymusic.constants.LyricsRomanizeBelarusianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeBulgarianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeChineseKey
import com.mochyy.mochymusic.constants.LyricsRomanizeHindiKey
import com.mochyy.mochymusic.constants.LyricsRomanizeJapaneseKey
import com.mochyy.mochymusic.constants.LyricsRomanizeKoreanKey
import com.mochyy.mochymusic.constants.LyricsRomanizeKyrgyzKey
import com.mochyy.mochymusic.constants.LyricsRomanizeMacedonianKey
import com.mochyy.mochymusic.constants.LyricsRomanizePunjabiKey
import com.mochyy.mochymusic.constants.LyricsRomanizeRussianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeSerbianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeUkrainianKey
import com.mochyy.mochymusic.constants.OpenRouterApiKey
import com.mochyy.mochymusic.constants.OpenRouterBaseUrlKey
import com.mochyy.mochymusic.constants.OpenRouterModelKey
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.constants.TranslateLyricsKey
import com.mochyy.mochymusic.constants.TranslateModeKey
import com.mochyy.mochymusic.constants.TranslateSourceLanguageKey
import com.mochyy.mochymusic.constants.YouTubeCaptionTranslationKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.RubyToken
import com.mochyy.mochymusic.lyrics.WordTimestamp
import com.mochyy.mochymusic.lyrics.YouTubeCaptionTranslation
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills [lines] with their readings and translations, the way the lyrics page shows them: the
 * romanisation (a sub-line, or ruby over Japanese and Chinese words) the settings ask for, and the
 * translation when it's switched on - from the database if one matches, otherwise translated now.
 * Each line carries them in its own flows ([LyricsEntry.romanizedTextFlow], [LyricsEntry.rubyFlow],
 * [LyricsEntry.translatedTextFlow]), so whoever draws the lines just collects those.
 *
 * Shared by the lyrics page and the player styles that perform the lyrics, so both read the same.
 */
@Composable
fun LyricsReadings(
    lines: List<LyricsEntry>,
    songId: String?,
    database: MusicDatabase,
    /** the stored row, for a translation saved with it */
    lyricsEntity: com.mochyy.mochymusic.db.entities.LyricsEntity?,
) {
    val context = LocalContext.current
    // every language switch, in the order of RomanizeLanguageKeys; the list never changes, so reading them in a loop is safe
    val switches = RomanizeLanguageKeys.map { key -> rememberPreference(key, defaultValue = true).value }

    LaunchedEffect(lines, switches) {
        val allowed = RomanizeLanguageKeys.zip(switches).toMap()
        readSong(lines, allowed = { allowed[it] != false }) { index, reading ->
            val entry = lines[index]
            // the pieces first, so whoever sees the reading land finds its timing already there
            entry.romanizedWordsFlow.value = reading?.words
            entry.rubyFlow.value = reading?.ruby
            entry.romanizedTextFlow.value = reading?.text
        }
    }

    // Translations. One effect owns them: whenever the lyrics, the on/off toggle, or any
    // translation setting changes, it shows the stored translation if one matches and otherwise
    // translates. Before, translating only happened inside a collector of the button's trigger
    // that had captured the language at the moment the screen opened, so after switching from
    // English to Indonesian it went on translating into English; the "whole session" switch was
    // saved but never read; and "show original" only blanked the lines until the next database
    // emission put the stored translation back.
    val (translateLanguage) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (translateSourceLanguage) = rememberPreference(TranslateSourceLanguageKey, defaultValue = "auto")
    val (translateMode) = rememberPreference(TranslateModeKey, defaultValue = "line")
    val (translateSessionWide) = rememberPreference(TranslateLyricsKey, defaultValue = false)
    val translationOn by LyricsTranslationHelper.translationOn.collectAsState()

    val (aiProvider) = rememberPreference(AiProviderKey, defaultValue = "OpenRouter")
    val (openRouterKey) = rememberPreference(OpenRouterApiKey, defaultValue = "")
    val (openRouterBaseUrl) = rememberPreference(
        OpenRouterBaseUrlKey,
        defaultValue = "https://openrouter.ai/api/v1",
    )
    val (openRouterModel) = rememberPreference(OpenRouterModelKey, defaultValue = "")
    val (deeplKey) = rememberPreference(DeeplApiKey, defaultValue = "")
    val (deeplFormality) = rememberPreference(DeeplFormalityKey, defaultValue = "default")
    val (youtubeTranslations) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    val playerConnection = com.mochyy.mochymusic.LocalPlayerConnection.current

    LaunchedEffect(songId, translateSessionWide) {
        LyricsTranslationHelper.onSongChanged(songId, translateSessionWide)
    }

    // Read, not keyed: saving a translation writes this row, and restarting on that write would
    // cancel the translation that just finished.
    val latestLyricsEntity by androidx.compose.runtime.rememberUpdatedState(lyricsEntity)

    LaunchedEffect(
        lines, translationOn, translateLanguage, translateSourceLanguage, translateMode,
        aiProvider, openRouterKey, deeplKey, youtubeTranslations,
    ) {
        if (lines.isEmpty()) return@LaunchedEffect
        if (!translationOn) {
            lines.forEach { it.translatedTextFlow.value = null }
            return@LaunchedEffect
        }
        val target = translateLanguage.ifBlank { "en" }

        // a translation someone uploaded to the video beats any machine one, so it's tried first
        val metadata = playerConnection?.mediaMetadata?.value?.takeIf { it.id == songId }
        if (youtubeTranslations && metadata != null &&
            LyricsTranslationHelper.translateWithYouTubeCaptions(
                lyrics = lines,
                request = YouTubeCaptionTranslation.Request(
                    songId = metadata.id,
                    title = metadata.title,
                    artist = metadata.artists.joinToString { it.name },
                    isVideo = metadata.isVideoSong,
                    lines = lines,
                    targetLanguage = target,
                    matchAudio = { videoId -> playerConnection?.service?.matchSongToVideo(metadata.id, videoId) },
                ),
                lyricsEntity = latestLyricsEntity,
                mode = translateMode,
                database = database,
            )
        ) return@LaunchedEffect
        if (LyricsTranslationHelper.loadTranslationsFromDatabase(
                lyrics = lines,
                lyricsEntity = latestLyricsEntity,
                targetLanguage = if (hasAiKeyFor(aiProvider, openRouterKey, deeplKey)) {
                    target
                } else {
                    LyricsTranslationHelper.languageTag(translateSourceLanguage, target)
                },
                mode = translateMode,
            )
        ) return@LaunchedEffect

        // Google Translate needs no account, so it is used whenever the chosen AI provider has no
        // key. (This used to check for ANY key, so DeepL picked with only an OpenRouter key saved
        // went to DeepL and failed with "API key required".)
        if (!hasAiKeyFor(aiProvider, openRouterKey, deeplKey)) {
            LyricsTranslationHelper.translateWithGoogle(
                lyrics = lines,
                targetLanguage = target,
                mode = translateMode,
                sourceLanguage = translateSourceLanguage,
                scope = this,
                songId = songId.orEmpty(),
                database = database,
            )
        } else {
            LyricsTranslationHelper.translateLyrics(
                lyrics = lines,
                targetLanguage = target,
                apiKey = openRouterKey,
                baseUrl = openRouterBaseUrl,
                model = openRouterModel,
                mode = translateMode,
                scope = this,
                context = context,
                provider = aiProvider,
                deeplApiKey = deeplKey,
                deeplFormality = deeplFormality,
                songId = songId.orEmpty(),
                database = database,
            )
        }
    }

    LaunchedEffect(lines) {
        LyricsTranslationHelper.clearTranslationsTrigger.collect {
            lines.forEach { it.translatedTextFlow.value = null }
        }
    }
}

// one line's romanisation: the whole line read out, its timed pieces when the line has timing, and the ruby over japanese and chinese words
class LineReading(val text: String?, val words: List<WordTimestamp>?, val ruby: List<RubyToken>?)

// the switch for the language a song is sung in, or null when it's in nothing mochy can romanise
fun romanizeSwitchFor(lines: List<LyricsEntry>): Preferences.Key<Boolean>? {
    val body = lines.joinToString(" ") { it.text }
    return switchFor(body, LyricsUtils.romanizableScript(body))
}

private fun switchFor(body: String, script: LyricsUtils.Script): Preferences.Key<Boolean>? = when (script) {
    LyricsUtils.Script.KANA -> LyricsRomanizeJapaneseKey
    LyricsUtils.Script.HANGUL -> LyricsRomanizeKoreanKey
    LyricsUtils.Script.HAN -> LyricsRomanizeChineseKey
    LyricsUtils.Script.DEVANAGARI -> LyricsRomanizeHindiKey
    // the cyrillic languages share one alphabet, so the words tell them apart
    LyricsUtils.Script.CYRILLIC -> when {
        LyricsUtils.isUkrainian(body) -> LyricsRomanizeUkrainianKey
        LyricsUtils.isSerbian(body) -> LyricsRomanizeSerbianKey
        LyricsUtils.isBelarusian(body) -> LyricsRomanizeBelarusianKey
        LyricsUtils.isKyrgyz(body) -> LyricsRomanizeKyrgyzKey
        LyricsUtils.isMacedonian(body) -> LyricsRomanizeMacedonianKey
        LyricsUtils.isBulgarian(body) -> LyricsRomanizeBulgarianKey
        else -> LyricsRomanizeRussianKey
    }
    else -> null
}

// every line's reading in one pass off the main thread, each handed to onLine as it's done; a language that isn't allowed gets null for every line
suspend fun readSong(
    lines: List<LyricsEntry>,
    allowed: (Preferences.Key<Boolean>) -> Boolean,
    onLine: (index: Int, reading: LineReading?) -> Unit,
) = withContext(Dispatchers.Default) {
    // the whole song decides the language: a kanji line alone looks chinese, and an english verse alone looks like nothing
    val body = lines.joinToString(" ") { it.text }
    val script = LyricsUtils.romanizableScript(body)
    val switch = switchFor(body, script)
    if (switch == null || !allowed(switch)) {
        lines.indices.forEach { onLine(it, null) }
        return@withContext
    }
    val punjabi = LyricsUtils.isPunjabi(body) && allowed(LyricsRomanizePunjabiKey)

    suspend fun romanizeOne(text: String): String? = when {
        punjabi -> LyricsUtils.romanizePunjabi(text)
        script == LyricsUtils.Script.KANA -> LyricsUtils.romanizeJapanese(text)
        script == LyricsUtils.Script.HANGUL -> LyricsUtils.romanizeKorean(text)
        script == LyricsUtils.Script.HAN -> LyricsUtils.romanizeChinese(text)
        script == LyricsUtils.Script.DEVANAGARI -> LyricsUtils.romanizeHindi(text)
        script == LyricsUtils.Script.CYRILLIC -> LyricsUtils.romanizeCyrillic(text)
        else -> null
    }

    // one line after another, never a coroutine each: sixty kuromoji runs at once stalled the player opening a japanese song
    lines.forEachIndexed { index, entry ->
        if (entry.text.isBlank()) {
            onLine(index, null)
        } else {
            // japanese and chinese get ruby over each word like furigana; alphabets only get a line underneath
            val ruby = LyricsUtils.rubyFor(entry.text, script)
            // the whole line's reading too, ruby or not: it's what the line says when the romanisation is shown as the line itself
            val romanized = if (ruby != null) LyricsUtils.readingLine(entry.text, ruby) else romanizeOne(entry.text)
            val reading = romanized?.takeIf { it.isNotBlank() && it != entry.text }
            val pieces = entry.syllables ?: entry.words
            val words = if (reading != null && !pieces.isNullOrEmpty()) {
                LyricsUtils.romanizedWords(entry.text, pieces, ruby) { romanizeOne(it) }
            } else {
                null
            }
            onLine(index, LineReading(reading, words, ruby))
        }
    }
}

internal fun hasAiKeyFor(provider: String, openRouterKey: String, deeplKey: String): Boolean =
    if (provider == "DeepL") deeplKey.isNotBlank() else openRouterKey.isNotBlank()

// every language the readings can be switched off for; the style page's one switch sets them all
val RomanizeLanguageKeys = listOf(
    LyricsRomanizeJapaneseKey,
    LyricsRomanizeKoreanKey,
    LyricsRomanizeChineseKey,
    LyricsRomanizeRussianKey,
    LyricsRomanizeUkrainianKey,
    LyricsRomanizeSerbianKey,
    LyricsRomanizeBulgarianKey,
    LyricsRomanizeBelarusianKey,
    LyricsRomanizeKyrgyzKey,
    LyricsRomanizeMacedonianKey,
    LyricsRomanizeHindiKey,
    LyricsRomanizePunjabiKey,
)

// a line as the settings want it drawn: the original with its readings, or the romanisation as the line with the original underneath
@androidx.compose.runtime.Immutable
class ShownLyric(
    val entry: LyricsEntry,
    val subLine: String?,
    val ruby: List<com.mochyy.mochymusic.lyrics.RubyToken>?,
    val translation: String?,
)

// what to draw for [entry] right now; the same on the lyrics page, the card, the video mode and a share, so they all agree
@Composable
fun rememberShownLyric(entry: LyricsEntry): ShownLyric {
    val (asMain) = rememberPreference(com.mochyy.mochymusic.constants.LyricsRomanizeAsMainKey, defaultValue = false)
    val romanized by entry.romanizedTextFlow.collectAsState()
    val romanizedWords by entry.romanizedWordsFlow.collectAsState()
    val ruby by entry.rubyFlow.collectAsState()
    val translated by entry.translatedTextFlow.collectAsState()
    val translation = translated?.takeIf { it.isNotBlank() }
    return remember(entry, asMain, romanized, romanizedWords, ruby, translation) {
        val reading = romanized?.takeIf { it.isNotBlank() }
        if (asMain && reading != null) {
            // the romanised pieces carry the timing, so the line sings letter by letter the same way; without them it rises whole
            ShownLyric(entry.copy(text = reading, words = romanizedWords, syllables = null), subLine = entry.text, ruby = null, translation = translation)
        } else {
            // ruby carries the reading over the words, so a line under it would only say it twice
            ShownLyric(entry, subLine = if (ruby.isNullOrEmpty()) reading else null, ruby = ruby, translation = translation)
        }
    }
}
