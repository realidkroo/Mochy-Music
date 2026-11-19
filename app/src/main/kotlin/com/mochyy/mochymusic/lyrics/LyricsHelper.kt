// LyricsHelper.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import android.util.LruCache
import com.mochyy.mochymusic.constants.LyricsProviderOrderKey
import com.mochyy.mochymusic.constants.PreferredLyricsProvider
import com.mochyy.mochymusic.constants.PreferredLyricsProviderKey
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.NetworkConnectivityObserver
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private val bestCache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    /**
     * Picks the best lyrics for [mediaMetadata] out of the enabled providers, in preference order.
     *
     * Providers are tried in order and each result is *graded* rather than taken on faith. The old
     * acceptance test was `contains("<") && contains(">") && contains(":")`, which is true of any
     * LRC file with an emoticon in it, and which said nothing at all about what language came
     * back — so a Chinese translation of an English song scored the same as the real thing and won
     * on provider order alone. Grading compares three things, in this order of importance:
     *
     *  1. **Original script.** Decided from evidence about the song; see [pickBest].
     *  2. **Sync quality.** Word > line > plain. This is what "flowing lyrics" means.
     *  3. **Voice tags.** Duet attribution, which drives left/right layout.
     *  4. **Provider order.** Only breaks ties.
     *
     * All enabled providers are queried, since the script comparison needs the full candidate set.
     */
    /**
     * @param forceRefresh skip the in-memory result cache and re-query every provider. Required by
     *   "refetch lyrics": that path deletes the stored row and asks again, and without this it
     *   would be handed back the very result it was trying to discard.
     */
    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        forceRefresh: Boolean = false,
    ): LyricsWithProvider {
        if (forceRefresh) {
            bestCache.remove(mediaMetadata.id)
        } else {
            bestCache.get(mediaMetadata.id)?.let { return it }
        }

        // An imported file's own lyrics, from its tags: they're the file's, and they're there
        // offline. Read before anything goes online, so offline mode still has them.
        // Off the caller's thread: the player asks from the main one, and this reads the file.
        mediaMetadata.localUri?.let { uri ->
            withContext(Dispatchers.IO) { readEmbeddedLyrics(context, android.net.Uri.parse(uri)) }?.let { lyrics ->
                return LyricsWithProvider(lyrics, EmbeddedLyrics.Provider).also { bestCache.put(mediaMetadata.id, it) }
            }
        }

        // offline mode counts as no network: lyrics already saved for the song still show
        val isNetworkAvailable = !com.mochyy.mochymusic.utils.OfflineMode.isOn && try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {

            true
        }

        if (!isNetworkAvailable) {
            // not "not found": nothing was asked, and saying so would be saved as the answer
            return LyricsWithProvider(LYRICS_NOT_FOUND, NoConnectionProvider)
        }

        val providers = resolveLyricsProviders()
        // YouTube's auto captions only count when nothing else has lyrics (see [eligible]), so
        // they're only asked for then: asked every time, they cost every song YouTube requests
        // whose answer was nearly always thrown away.
        val (lastResort, regular) = providers.withIndex()
            .partition { it.value.name == YouTubeCaptionsLyricsProvider.name }
        var candidates = fetchIndexed(mediaMetadata, regular)
        // picking parses every candidate's lyrics, so it runs off the main thread the player calls from
        var best = withContext(Dispatchers.Default) { pickBest(mediaMetadata, candidates, providers.size) }
        if (best == null && lastResort.isNotEmpty()) {
            candidates = candidates + fetchIndexed(mediaMetadata, lastResort)
            best = withContext(Dispatchers.Default) { pickBest(mediaMetadata, candidates, providers.size) }
        }
        if (best == null) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        val result = LyricsWithProvider(best.lyrics, best.providerName)
        bestCache.put(mediaMetadata.id, result)
        return result
    }

    /**
     * One provider's answer for [mediaMetadata], graded. Used by the source picker so it can show
     * which providers actually have lyrics for this song before the user picks one.
     */
    data class ProviderCandidate(
        val providerName: String,
        val lyrics: String,
        val order: Int,
        val sync: LyricsUtils.SyncKind,
        val script: LyricsUtils.Script,
        /** For Latin-script lyrics, the language when it can be told; see [LyricsUtils.latinLanguage]. */
        val language: String? = null,
    ) {
        /** Carries real left/right voice tags, which is what lays a duet out on both sides. */
        val hasDuet: Boolean get() = LyricsUtils.hasDistinctVoices(lyrics)
    }

    /**
     * Asks every enabled provider at once. They used to be asked one after another, so a song
     * waited on the sum of eight network round trips (each with its own timeouts) before anything
     * was shown, and switching source felt like nothing happened.
     */
    suspend fun fetchCandidates(
        mediaMetadata: MediaMetadata,
        providers: List<LyricsProvider>? = null,
    ): List<ProviderCandidate> =
        fetchIndexed(mediaMetadata, (providers ?: resolveLyricsProviders()).withIndex().toList())

    /**
     * [providers] asked at once, each keeping its place in the full order. Graded on a background
     * thread: grading parses every answer, and the player asks from the main thread.
     */
    private suspend fun fetchIndexed(
        mediaMetadata: MediaMetadata,
        providers: List<IndexedValue<LyricsProvider>>,
    ): List<ProviderCandidate> = coroutineScope {
        providers.map { (order, provider) ->
            async(Dispatchers.Default) { fetchCandidate(mediaMetadata, provider, order) }
        }.awaitAll().filterNotNull()
    }

    /** The enabled providers, in the user's preferred order. */
    suspend fun orderedProviders(): List<LyricsProvider> = resolveLyricsProviders()

    fun isProviderEnabled(provider: LyricsProvider): Boolean = provider.isEnabled(context)

    /** Asks one provider. Null when it is switched off, has nothing, fails or times out. */
    suspend fun fetchCandidate(
        mediaMetadata: MediaMetadata,
        provider: LyricsProvider,
        order: Int,
    ): ProviderCandidate? {
        if (!provider.isEnabled(context) || com.mochyy.mochymusic.utils.OfflineMode.isOn) return null
        val lyrics = try {
            withTimeoutOrNull(ProviderTimeoutMs) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration,
                    mediaMetadata.album?.title,
                ).onFailure { reportException(it) }.getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            null
        }
        if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND) return null
        return ProviderCandidate(
            providerName = provider.name,
            lyrics = lyrics,
            order = order,
            sync = LyricsUtils.syncKind(lyrics),
            script = LyricsUtils.lyricsScript(lyrics),
            language = LyricsUtils.latinLanguage(lyrics),
        ).takeIf { it.sync != LyricsUtils.SyncKind.NONE }
    }

    /** Remembers a source the user picked by hand, so the next lookup returns it. */
    fun rememberChoice(mediaId: String, choice: LyricsWithProvider) {
        bestCache.put(mediaId, choice)
    }

    /**
     * Picks the lyrics most likely to be the song as sung.
     *
     * Language comes first, and it is decided from evidence about the SONG rather than from the
     * candidates alone:
     *
     *  1. A non-Latin title, artist or album ("夜に駆ける", "아이유") says what script the song is in.
     *  2. Otherwise, YouTube's own lyrics or captions for this exact video, when they are in a
     *     non-Latin script. They come from the video itself, not from a title search, so they
     *     cannot be a different song or a translation fetched by mistake.
     *  3. Otherwise, the language most sources agree on - by language, not just script, so an
     *     Indonesian song's English translation counts against it. When most of the lyrics found
     *     are in one language, the one or two in another lose to all of them, however well timed:
     *     a translation or a different song is wrong however finely it is synced.
     *  4. Otherwise (no clear majority) the old rule, narrowed: a non-Latin result beats a Latin
     *     one (the Latin one being a translation or romanisation) only when it is Japanese or
     *     Korean, or when two providers agree on it. A lone Chinese result for a song with a Latin
     *     title is far more often a mismatched search hit than the original, and it used to win
     *     outright, which is how English and Indonesian songs ended up showing Chinese lyrics.
     *
     * Then, among lyrics in the right language, sync quality (word > line > plain), then duet
     * tags, then provider order.
     *
     * Not every source gets a say, though: the Chinese services and YouTube's auto captions only
     * do when the better sources have nothing - see [eligible]. Null when nothing is left to pick
     * from.
     */
    private fun pickBest(
        mediaMetadata: MediaMetadata,
        candidates: List<ProviderCandidate>,
        providerCount: Int,
    ): ProviderCandidate? {
        val metadataScript = LyricsUtils.dominantScript(
            listOfNotNull(
                mediaMetadata.title,
                mediaMetadata.artists.joinToString(" ") { it.name },
                mediaMetadata.album?.title,
            ).joinToString(" ")
        )
        val referenceScript = (
            metadataScript.takeIf(::isNative)
                ?: candidates
                    .filter { it.providerName in LanguageReferenceProviders }
                    .map { it.script }
                    .firstOrNull(::isNative)
            )?.let { script ->
            // All-kanji names ("米津玄師") read as Han, but so does a Japanese song's credit line.
            // Chinese sources don't put kana into Chinese songs, so when any result has kana, the
            // song is Japanese. Without this, a Chinese translation scored as an exact match and
            // beat the Japanese original, along with its duet tags.
            if (script == LyricsUtils.Script.HAN && candidates.any { it.script == LyricsUtils.Script.KANA }) {
                LyricsUtils.Script.KANA
            } else {
                script
            }
        }

        val chineseSong = isChineseSong(referenceScript, candidates)
        val hindiSong = referenceScript == LyricsUtils.Script.DEVANAGARI ||
            candidates.any { it.script == LyricsUtils.Script.DEVANAGARI || it.language == "hi-Latn" }
        val songLanguage = songLanguage(referenceScript, candidates)
        val pool = eligible(candidates, chineseSong, songLanguage)
        if (pool.isEmpty()) return null

        // what most of the sources say the song is in, asked only when the song itself doesn't say
        val languages = languageKeys(pool)
        val majority = if (referenceScript == null) majorityLanguage(languages, minVotes = 2) else null

        val trustedNative: Set<LyricsUtils.Script> = if (referenceScript != null || majority != null) {
            emptySet()
        } else {
            pool.map { it.script }
                .filter(::isNative)
                .groupingBy { it }
                .eachCount()
                .filter { (script, count) ->
                    count >= 2 || script == LyricsUtils.Script.KANA || script == LyricsUtils.Script.HANGUL
                }
                .keys
        }

        return pool.withIndex().maxByOrNull { (index, c) ->
            // hindi songs come back in devanagari from youtube and in romanised hindi from apple and musixmatch; both are the real song
            val hindiMatch = hindiSong && (
                c.script == LyricsUtils.Script.DEVANAGARI ||
                    c.language == "hi-Latn" ||
                    (c.script == LyricsUtils.Script.LATIN && c.language == null)
                )
            val scriptScore = when {
                hindiMatch -> 3
                referenceScript != null -> when {
                    c.script == referenceScript -> 3
                    sameLanguageFamily(c.script, referenceScript) -> 2
                    c.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                majority != null -> when {
                    languages[index] == majority -> 3
                    c.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                trustedNative.isNotEmpty() -> when {
                    c.script in trustedNative -> 2
                    c.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                // Nothing points at a non-Latin original: an untrusted non-Latin result is the
                // odd one out, not the preferred one.
                isNative(c.script) -> 0
                else -> 1
            }
            // Syllable-split payloads are word timings smeared across single letters; they
            // render as gibberish word-by-word, so demote them to the level of line-synced.
            val syncScore = if (c.sync == LyricsUtils.SyncKind.WORD && LyricsUtils.isSyllableSplit(c.lyrics)) {
                LyricsUtils.SyncKind.LINE.ordinal
            } else {
                c.sync.ordinal
            }
            // Duet/voice tagging is strictly extra information: it is what lets the screen put
            // one singer left and the answering singer right. Only BetterLyrics (from TTML's
            // ttm:agent) and Paxsenix emit it — YouLyPlus never does. Without this term an
            // agent-less result that ties on script and sync would win purely on provider order
            // and the song would silently render flush-left with no way to tell why.
            // Real two-sided voices count most; tags on every line with a single voice still beat
            // none, as before.
            val duetScore = when {
                c.hasDuet -> 2
                LyricsUtils.hasVoiceTags(c.lyrics) -> 1
                else -> 0
            }

            // for hindi songs betterlyrics and lyricsplus beat youtube music whenever they have the song
            val preferredBonus = if (hindiMatch && c.providerName in PreferredHindiProviders) SyncWeight * 3 else 0

            // The Chinese services are the strongest sources for Chinese songs, so for one they win
            // a tie outright, and among themselves the user's order decides
            val orderScore = if (chineseSong && c.providerName in ChineseSources) {
                providerCount * 2 - c.order
            } else {
                providerCount - c.order
            }

            scriptScore * ScriptWeight +
                preferredBonus +
                syncScore * SyncWeight +
                duetScore * DuetWeight +
                orderScore
        }?.value
    }

    /**
     * What each candidate's lyrics are compared by: the language for Latin-script lyrics whose
     * language could be told, the script for everything else. Latin lyrics whose language
     * couldn't be told (too short, say) are taken to be in the Latin language most of the others
     * are in, rather than counting as a language of their own.
     */
    private fun languageKeys(candidates: List<ProviderCandidate>): List<String> {
        val keys = candidates.map { c ->
            if (c.script == LyricsUtils.Script.LATIN) {
                c.language?.let { LatinPrefix + it } ?: LyricsUtils.Script.LATIN.name
            } else {
                c.script.name
            }
        }
        val commonLatin = keys.filter { it.startsWith(LatinPrefix) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return keys.map { if (it == LyricsUtils.Script.LATIN.name && commonLatin != null) commonLatin else it }
    }

    /**
     * The language of [keys] there are clearly the most of: at least [minVotes], and more than
     * any other language. Null on a tie. Lyrics with no letters to tell by don't vote.
     */
    private fun majorityLanguage(keys: List<String>, minVotes: Int): String? {
        val ranked = keys.filter { it != LyricsUtils.Script.UNKNOWN.name }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        return top.key.takeIf { top.value >= minVotes && top.value > runnerUp }
    }

    /**
     * Whether the song is Chinese, which decides how far the Chinese services are believed. The
     * song's own evidence decides when there is some ([referenceScript]: its title, artist or
     * album, or YouTube's lyrics for this video). Without any, the other sources do: when Chinese
     * is what most of the ones that aren't Chinese services came back in - and none in kana, since
     * kana makes kanji Japanese - the song is Chinese, and the Chinese services are believed along
     * with them. They don't vote themselves: they answer in Chinese for songs that aren't.
     */
    private fun isChineseSong(
        referenceScript: LyricsUtils.Script?,
        candidates: List<ProviderCandidate>,
    ): Boolean =
        if (referenceScript != null) {
            referenceScript == LyricsUtils.Script.HAN
        } else {
            val others = candidates.filter { it.providerName !in ChineseSources }
            candidates.none { it.script == LyricsUtils.Script.KANA } &&
                majorityLanguage(languageKeys(others), minVotes = 1) == LyricsUtils.Script.HAN.name
        }

    /**
     * Which candidates are picked from, in tiers - a tier is only heard when every tier above it
     * came back empty:
     *
     *  1. Every source but the Chinese services (KuGou, NetEase, QQ Music) and YouTube's auto
     *     captions; the Chinese services too for a Chinese song, since that is what they are good at.
     *     For any other song, their word-timed lyrics join in too when they're in the song's own
     *     language ([songLanguage]). They're often the only word timing there is for a Japanese,
     *     Korean or English song, and kept out they lost to anyone's line-timed lyrics. Their usual
     *     mistakes - a Chinese translation, another song with a similar name in another language -
     *     don't pass that check.
     *  2. The Chinese services for any other song, and then only lyrics of theirs that aren't
     *     Chinese. They answer songs in every language with Chinese translations, or a different
     *     song with a similar name, often enough that Chinese from them is not believed for a song
     *     that isn't Chinese. The source picker still lists them, for anyone who wants them anyway.
     *  3. YouTube's auto captions: speech recognition, so lyrics only when there are no lyrics.
     */
    private fun eligible(
        candidates: List<ProviderCandidate>,
        chineseSong: Boolean,
        songLanguage: String?,
    ): List<ProviderCandidate> {
        val (chinese, rest) = candidates.partition { it.providerName in ChineseSources }
        val (captions, main) = rest.partition { it.providerName == YouTubeCaptionsLyricsProvider.name }
        val wordTimedOriginals = chinese.filter { c ->
            c.sync == LyricsUtils.SyncKind.WORD && c.script != LyricsUtils.Script.HAN &&
                songLanguage != null && languageKey(c, songLanguage) == songLanguage
        }
        return (if (chineseSong) main + chinese else main + wordTimedOriginals)
            .ifEmpty { chinese.filter { it.script != LyricsUtils.Script.HAN } }
            .ifEmpty { captions }
    }

    /**
     * What language the song is sung in, as a language key (see [languageKeys]), from evidence
     * that isn't the Chinese services': the song's own title, artist or album script, or YouTube's
     * lyrics for the video ([referenceScript]), or else what most of the other sources came back
     * in. Null when nothing says.
     */
    private fun songLanguage(referenceScript: LyricsUtils.Script?, candidates: List<ProviderCandidate>): String? {
        if (referenceScript != null) return referenceScript.name
        val others = candidates.filter { it.providerName !in ChineseSources && it.providerName != YouTubeCaptionsLyricsProvider.name }
        return majorityLanguage(languageKeys(others), minVotes = 1)
    }

    /**
     * [candidate]'s language key, read the way [songLanguage] is: kanji and kana count as one
     * family (a kanji-only title is still a Japanese song), and Latin lyrics whose language
     * couldn't be told take the song's, when the song is in a Latin language.
     */
    private fun languageKey(candidate: ProviderCandidate, songLanguage: String): String = when {
        candidate.script == LyricsUtils.Script.LATIN ->
            candidate.language?.let { LatinPrefix + it }
                ?: songLanguage.takeIf { it.startsWith(LatinPrefix) || it == LyricsUtils.Script.LATIN.name }
                ?: LyricsUtils.Script.LATIN.name
        candidate.script == LyricsUtils.Script.KANA && songLanguage == LyricsUtils.Script.HAN.name -> songLanguage
        else -> candidate.script.name
    }

    private fun isNative(script: LyricsUtils.Script) =
        script != LyricsUtils.Script.LATIN && script != LyricsUtils.Script.UNKNOWN

    /** Kanji-only Japanese titles read as Han; Japanese lyrics read as kana. Same song either way. */
    private fun sameLanguageFamily(a: LyricsUtils.Script, b: LyricsUtils.Script): Boolean {
        val cjk = setOf(LyricsUtils.Script.HAN, LyricsUtils.Script.KANA)
        return a in cjk && b in cjk
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        // offline mode counts as no network: lyrics already saved for the song still show
        val isNetworkAvailable = !com.mochyy.mochymusic.utils.OfflineMode.isOn && try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {

            true
        }

        if (!isNetworkAvailable) {

            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {

                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3

        /**
         * The provider of a lookup that couldn't go online (offline mode, or no network). Its
         * "not found" isn't an answer: saved, it would stay the song's lyrics after the network
         * came back, so callers don't save it.
         */
        const val NoConnectionProvider = "NoConnection"

        /**
         * One hung provider must not hold the whole lookup hostage. Kept above the providers' own
         * HTTP timeouts: Apple Music (BetterLyrics) alone allows 10s to connect plus 15s for the
         * request, and it is the main source of duet tags. A 15s cap here cut it off on slow
         * days, so songs like duets lost their left/right layout to an untagged copy.
         */
        private const val ProviderTimeoutMs = 35_000L

        /**
         * Providers whose answer comes from this exact video rather than a title search, so their
         * language is the song's language.
         */
        private val LanguageReferenceProviders = setOf("YouTubeMusic", "YouTubeSubtitle")
        private val PreferredHindiProviders = setOf("BetterLyrics", "YouLyPlus")

        /** Chinese services: trusted first-hand only for Chinese songs; see [eligible]. */
        private val ChineseSources = setOf(
            KuGouLyricsProvider.name,
            NetEaseLyricsProvider.name,
            QQMusicLyricsProvider.name,
        )

        /** Marks a language key as a Latin-script language: "latin:en", "latin:id". */
        private const val LatinPrefix = "latin:"

        // Spread far enough apart that a script match always outranks any amount of sync
        // quality, and sync quality always outranks provider order.
        private const val DuetWeight = 100
        private const val SyncWeight = 1_000
        private const val ScriptWeight = 10_000
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
