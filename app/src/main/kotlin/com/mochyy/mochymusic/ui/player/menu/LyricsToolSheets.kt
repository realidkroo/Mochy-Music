// LyricsToolSheets.kt
// The three lyrics tools reachable from the player menu when it is opened on the lyrics page:
// an editor, a provider picker, and translation settings.
//
// These live behind `fromLyrics` in PlayerActionMenu rather than being always-on player actions,
// because every one of them acts on lyrics that are on screen. "Refetch lyrics" from a player with
// no lyrics view open is an action whose entire result is invisible.

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.constants.TranslateLyricsKey
import com.mochyy.mochymusic.constants.TranslateSourceLanguageKey
import com.mochyy.mochymusic.constants.YouTubeCaptionTranslationKey
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.YouTubeCaptionTranslation
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.menu.songedit.LyricsFileBox
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.mochyy.mochymusic.ui.component.RemoteFocus

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

/**
 * Line-by-line lyrics editor.
 *
 * Timestamps are shown in plain `m:ss` rather than the raw `[00:12.34]` tag they are stored as —
 * the bracketed form is a serialisation detail, and reading a screen of them to find the line you
 * want to fix is needlessly hard. The original millisecond value is kept alongside so saving
 * round-trips exactly; only the text is editable.
 *
 * Saving writes plain per-line LRC. Any word-level timings the provider supplied are dropped,
 * because there is no way to re-derive them from edited text — the edited song keeps line-level
 * sync and loses the karaoke sweep. That is a real trade and the reason the editor stores under
 * its own provider name, so a later refetch can replace it wholesale.
 */
@Composable
fun LyricsEditorSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsMenuViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    val parsed = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            LyricsUtils.parseLyricsCached(raw)
        }
    }

    // Editable copy, seeded once per song so typing is not undone by the database flow re-emitting.
    val drafts = remember(parsed) { mutableStateListOf<String>().apply { addAll(parsed.map { it.text }) } }

    MenuSheetSurface(onDismiss = onDismiss) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Edit lyrics",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (parsed.isNotEmpty()) {
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                val id = mediaMetadata?.id
                                if (id != null) {
                                    viewModel.saveLyrics(
                                        songId = id,
                                        lyrics = buildLrc(parsed.map { it.time }, drafts),
                                    )
                                }
                                closeSheet()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (parsed.isEmpty()) {
                Text(
                    text = "No lyrics to edit for this song.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MenuRowSurface)
                        .padding(14.dp)
                ) {
                    itemsIndexed(parsed, key = { index, _ -> index }) { index, entry ->
                        Column(modifier = Modifier.padding(bottom = 14.dp)) {
                            Text(
                                text = formatTimestamp(entry.time),
                                color = Color(0xFF8A8A8A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = drafts.getOrElse(index) { entry.text },
                                onValueChange = { if (index < drafts.size) drafts[index] = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `83_400` -> `"1:23"`. The stored form is a bracketed LRC tag; this is the readable one. */
internal fun formatTimestamp(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Reassembles edited text against its original timings as plain per-line LRC. */
internal fun buildLrc(times: List<Long>, texts: List<String>): String = buildString {
    for (index in times.indices) {
        val text = texts.getOrNull(index).orEmpty()
        if (text.isBlank()) continue
        val ms = times[index].coerceAtLeast(0L)
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000
        val centis = (ms % 1000) / 10
        append('[')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append('.')
        append(centis.toString().padStart(2, '0'))
        append(']')
        append(text.trim())
        append('\n')
    }
}

// ---------------------------------------------------------------------------------------------
// Provider picker
// ---------------------------------------------------------------------------------------------

/**
 * Picks which source this song's lyrics come from.
 *
 * Every source is asked as soon as the sheet opens, and each row says what it found: checking,
 * word / line synced, plain, not found, or switched off. Tapping a source that has lyrics applies
 * the copy already fetched, so the switch is instant. This used to move the tapped source to the
 * front of the global order and search again, but provider order only breaks ties in the grading,
 * so the same result usually came straight back and the pick appeared to do nothing.
 */
@Composable
fun LyricsProviderSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsMenuViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val sources by viewModel.sources.collectAsState()

    androidx.compose.runtime.LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(viewModel::checkSources)
    }

    // Sources with lyrics first, in the order they would be preferred; then the rest.
    val names = remember(sources) {
        val rank = { state: LyricsMenuViewModel.SourceState? ->
            when (state) {
                is LyricsMenuViewModel.SourceState.Available -> 0
                LyricsMenuViewModel.SourceState.Checking, null -> 1
                LyricsMenuViewModel.SourceState.Unavailable -> 2
                LyricsMenuViewModel.SourceState.Off -> 3
            }
        }
        LyricsProviderRegistry.providerNames.sortedWith(
            compareBy<String> { rank(sources[it]) }
                .thenBy { (sources[it] as? LyricsMenuViewModel.SourceState.Available)?.candidate?.order ?: Int.MAX_VALUE }
        )
    }
    val active = lyricsEntity?.provider
    val addedText = stringResource(R.string.lyrics_file_added)

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Lyrics Provider",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // your own file sits above every source and, like tapping one, takes over straight away
            LyricsFileBox(
                picked = null,
                onPicked = { file ->
                    mediaMetadata?.let { viewModel.saveLyrics(it.id, file.text) }
                    TopToaster.show(text = addedText, iconRes = R.drawable.lyrics)
                    closeSheet()
                },
            )
            Spacer(modifier = Modifier.height(14.dp))

            // A left-aligned list, not centred pills. These are named options being chosen from,
            // so they read down a common left edge like every other menu in the app; centred pills
            // made a list of eight providers into eight floating buttons with no scan line.
            names.forEach { name ->
                val state = sources[name]
                val available = state as? LyricsMenuViewModel.SourceState.Available
                val isActive = name == active
                val status = when {
                    isActive -> "In use"
                    available != null -> when (available.candidate.sync) {
                        LyricsUtils.SyncKind.WORD -> "Word synced"
                        LyricsUtils.SyncKind.LINE -> "Line synced"
                        else -> "Plain"
                    } + if (available.candidate.hasDuet) " · duet" else ""
                    state == LyricsMenuViewModel.SourceState.Unavailable -> "Not found"
                    state == LyricsMenuViewModel.SourceState.Off -> "Off"
                    else -> "Checking…"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MenuRowSurface)
                        .clickable(
                            enabled = available != null && !isActive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = RemoteFocus,
                        ) {
                            val metadata = mediaMetadata
                            if (available != null && metadata != null) {
                                viewModel.useSource(metadata, available.candidate)
                                closeSheet()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    // Availability dot: green has lyrics, grey is still checking, red found nothing.
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    available != null -> Color(0xFF4CD964)
                                    state == LyricsMenuViewModel.SourceState.Unavailable -> Color(0xFFFF5A5F)
                                    else -> Color(0xFF6A6A6A)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = LyricsProviderRegistry.getDisplayName(name),
                        color = Color.White.copy(alpha = if (available != null) 1f else 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = status,
                        color = if (isActive) Color.White else Color(0xFF9A9A9A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Translation settings
// ---------------------------------------------------------------------------------------------

/**
 * Every language Google Translate accepts.
 *
 * Kept as a full list rather than a handful of popular picks: which language someone wants their
 * lyrics in is not something a shortlist can guess, and a missing language is a dead end with no
 * workaround. Presented in its own overlay because a list this long cannot sit inline.
 */
private val TranslationTargets = listOf(
    "af" to "Afrikaans", "sq" to "Albanian", "am" to "Amharic", "ar" to "Arabic",
    "hy" to "Armenian", "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian",
    "bn" to "Bengali", "bs" to "Bosnian", "bg" to "Bulgarian", "ca" to "Catalan",
    "ceb" to "Cebuano", "ny" to "Chichewa", "zh-CN" to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)", "co" to "Corsican", "hr" to "Croatian",
    "cs" to "Czech", "da" to "Danish", "nl" to "Dutch", "en" to "English",
    "eo" to "Esperanto", "et" to "Estonian", "tl" to "Filipino", "fi" to "Finnish",
    "fr" to "French", "fy" to "Frisian", "gl" to "Galician", "ka" to "Georgian",
    "de" to "German", "el" to "Greek", "gu" to "Gujarati", "ht" to "Haitian Creole",
    "ha" to "Hausa", "haw" to "Hawaiian", "iw" to "Hebrew", "hi" to "Hindi",
    "hmn" to "Hmong", "hu" to "Hungarian", "is" to "Icelandic", "ig" to "Igbo",
    "id" to "Indonesian", "ga" to "Irish", "it" to "Italian", "ja" to "Japanese",
    "jw" to "Javanese", "kn" to "Kannada", "kk" to "Kazakh", "km" to "Khmer",
    "ko" to "Korean", "ku" to "Kurdish", "ky" to "Kyrgyz", "lo" to "Lao",
    "la" to "Latin", "lv" to "Latvian", "lt" to "Lithuanian", "lb" to "Luxembourgish",
    "mk" to "Macedonian", "mg" to "Malagasy", "ms" to "Malay", "ml" to "Malayalam",
    "mt" to "Maltese", "mi" to "Maori", "mr" to "Marathi", "mn" to "Mongolian",
    "my" to "Myanmar", "ne" to "Nepali", "no" to "Norwegian", "ps" to "Pashto",
    "fa" to "Persian", "pl" to "Polish", "pt" to "Portuguese", "pa" to "Punjabi",
    "ro" to "Romanian", "ru" to "Russian", "sm" to "Samoan", "gd" to "Scots Gaelic",
    "sr" to "Serbian", "st" to "Sesotho", "sn" to "Shona", "sd" to "Sindhi",
    "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian", "so" to "Somali",
    "es" to "Spanish", "su" to "Sundanese", "sw" to "Swahili", "sv" to "Swedish",
    "tg" to "Tajik", "ta" to "Tamil", "te" to "Telugu", "th" to "Thai",
    "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu", "uz" to "Uzbek",
    "vi" to "Vietnamese", "cy" to "Welsh", "xh" to "Xhosa", "yi" to "Yiddish",
    "yo" to "Yoruba", "zu" to "Zulu",
)

@Composable
fun LyricsTranslationSheet(onDismiss: () -> Unit) {
    // Same default as the lyrics screen. The sheet used to default to "" while the screen
    // defaulted to "en", so the two disagreed about what language was selected.
    val (target, setTarget) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (source, setSource) = rememberPreference(TranslateSourceLanguageKey, defaultValue = "auto")
    val (sessionWide, setSessionWide) = rememberPreference(TranslateLyricsKey, defaultValue = false)
    var picking by remember { mutableStateOf(false) }
    var pickingSource by remember { mutableStateOf(false) }

    val status by LyricsTranslationHelper.status.collectAsState()
    val translationOn by LyricsTranslationHelper.translationOn.collectAsState()
    val isTranslating = status is LyricsTranslationHelper.TranslationStatus.Translating
    val youtubeLanguages = rememberYouTubeTranslationLanguages()

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Text(text = "Lyrics translation", color = Color(0xFF9A9A9A), fontSize = 12.sp)
            Text(
                text = "Translate from",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Detected by default. Detection guesses wrong on short or mixed-language lyrics, and
            // then nothing could be done about it, so the source can be set by hand too.
            PillButton(
                label = if (source.isBlank() || source == "auto") {
                    "Detect automatically"
                } else {
                    TranslationTargets.firstOrNull { it.first == source }?.second ?: source
                },
                onClick = { pickingSource = true },
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "To", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(
                label = TranslationTargets.firstOrNull { it.first == target }?.second ?: "unselected",
                onClick = { picking = true },
                trailing = if (target in youtubeLanguages) ({ YouTubeTick() }) else null,
            )
            AnimatedVisibility(visible = youtubeLanguages.isNotEmpty(), enter = fadeIn() + expandVertically()) {
                Text(
                    text = "A green tick means the video has its own captions in that language, so they're used as " +
                        "the translation. You can turn this off in Settings > General.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "And", color = Color(0xFF9A9A9A), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(MenuRowSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                SheetIcon(R.drawable.translate)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Translate this whole session",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = sessionWide, onCheckedChange = setSessionWide)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // The action lives here rather than on the lyrics page: this is where the target
            // language is chosen, so pressing "translate" is only meaningful once you have set it.
            // Disabled until a target exists, since translating into nothing is not an action.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (target.isBlank() || isTranslating) {
                            MenuRowSurface
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        }
                    )
                    .clickable(
                        // Still tappable while translating, so a slow or stuck translation can be
                        // switched off instead of locking the button.
                        enabled = target.isNotBlank(),
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                    ) {
                        if (translationOn) {
                            LyricsTranslationHelper.triggerClearTranslations()
                        } else {
                            LyricsTranslationHelper.triggerManualTranslation()
                        }
                        // Close so the result is actually visible — the lyrics are behind this
                        // sheet, and the loading dots appear on the lines themselves.
                        closeSheet()
                    }
            ) {
                Text(
                    text = when {
                        isTranslating -> "Translating… (tap to stop)"
                        translationOn -> "Show original"
                        target.isBlank() -> "Pick a language first"
                        else -> "Translate!"
                    },
                    color = Color.White.copy(alpha = if (target.isBlank() || isTranslating) 0.45f else 1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // A failed translation used to leave the lines quietly untranslated with no reason.
            (status as? LyricsTranslationHelper.TranslationStatus.Error)?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Couldn't translate: ${error.message}",
                    color = Color(0xFFFF8A8A),
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (picking) {
        LanguagePickerSheet(
            title = "Translate to",
            selected = target,
            marked = youtubeLanguages,
            // The picker closes itself (animated) after a pick; onDismiss then drops it. While a
            // translation is showing, the lyrics screen re-translates into the new language.
            onPick = { setTarget(it) },
            onDismiss = { picking = false },
        )
    }
    if (pickingSource) {
        LanguagePickerSheet(
            title = "Translate from",
            selected = source.ifBlank { "auto" },
            options = listOf("auto" to "Detect automatically") + TranslationTargets,
            onPick = { setSource(it) },
            onDismiss = { pickingSource = false },
        )
    }
}

// the languages the playing song's video has its own uploaded captions in, asked once when the sheet opens
@Composable
private fun rememberYouTubeTranslationLanguages(): Set<String> {
    val playerConnection = LocalPlayerConnection.current
    val (enabled) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    var languages by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(enabled) {
        languages = emptySet()
        if (!enabled || playerConnection == null) return@LaunchedEffect
        val metadata = playerConnection.mediaMetadata.value ?: return@LaunchedEffect
        val raw = playerConnection.currentLyrics.first()?.lyrics
            ?.takeIf { it.isNotBlank() && it != LYRICS_NOT_FOUND } ?: return@LaunchedEffect
        val lines = withContext(Dispatchers.Default) { LyricsUtils.parseLyricsCached(raw) }
        languages = YouTubeCaptionTranslation.availableTargets(
            songId = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString { it.name },
            isVideo = metadata.isVideoSong,
            lines = lines,
            targets = TranslationTargets.map { it.first },
        )
    }
    return languages
}

// the green tick on the right of a language's pill when the video has captions in it
@Composable
private fun YouTubeTick() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(YouTubeTranslationGreen),
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = "Captions on YouTube",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val YouTubeTranslationGreen = Color(0xFF93F58A)

/**
 * The full language list, in its own overlay on top of the translation sheet.
 *
 * Filterable, because a hundred-odd entries is more than anyone wants to scroll through to reach
 * "Vietnamese".
 */
@Composable
private fun LanguagePickerSheet(
    title: String,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<String, String>> = TranslationTargets,
    marked: Set<String> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) {
            options
        } else {
            options.filter { it.second.contains(query, ignoreCase = true) }
        }
    }

    MenuSheetSurface(onDismiss = onDismiss) { _ ->
        val closeSheet = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MenuRowSurface)
                    .padding(horizontal = 16.dp)
            ) {
                if (query.isEmpty()) {
                    Text(text = "Search language", color = Color(0xFF8A8A8A), fontSize = 13.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(filtered, key = { _, item -> item.first }) { _, (code, label) ->
                    val isSelected = code == selected
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.20f) else MenuRowSurface
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = RemoteFocus,
                            ) {
                                onPick(code)
                                closeSheet()
                            }
                            .padding(start = 18.dp, end = 10.dp)
                            .height(46.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 36.dp),
                        )
                        if (code in marked) {
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) { YouTubeTick() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) Color.White.copy(alpha = 0.20f) else MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 9.dp)) { trailing() }
        }
    }
}
