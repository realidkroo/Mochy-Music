// LyricsProviderRegistry.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.constants.PreferredLyricsProvider
import com.music.directlyrics.LrcRed

object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxSenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "NetEase"         to NetEaseLyricsProvider,
        "QQMusic"         to QQMusicLyricsProvider,
        "Deezer"          to DeezerLyricsProvider,
        "LrcRed"          to LrcRedLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
        "YouTubeCaptions" to YouTubeCaptionsLyricsProvider,
    )

    // lyrics from modules that are on, set by the module registry; a module switched off simply isn't in here
    @Volatile
    private var moduleProviders: Map<String, ModuleLyricsProvider> = emptyMap()

    fun setModuleProviders(providers: List<ModuleLyricsProvider>) {
        moduleProviders = providers.associateBy { it.name }
    }

    val providerNames: List<String> get() = moduleProviders.keys.toList() + providerMap.keys

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name] ?: moduleProviders[name]

    /** A saved order, with any source added since it was saved at the end, in default order. */
    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
        // a lyrics module someone just turned on goes first, they added it to be asked
        val newModules = moduleProviders.keys.filter { it !in saved }
        return newModules + saved + getDefaultProviderOrder().filter { it !in saved && it !in newModules }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    // NetEase, QQ Music and KuGou near the end: they are Chinese services, and LyricsHelper only
    // believes them first-hand for Chinese songs. NetEase leads them because it often has word
    // timing. YouTube's auto captions last of all - they are speech recognition.
    fun getDefaultProviderOrder(): List<String> = moduleProviders.keys.toList() + listOf(
        "BetterLyrics",
        "YouLyPlus",
        "Paxsenix",
        "LrcRed",
        "SimpMusic",
        "LrcLib",
        "Deezer",
        "YouTubeSubtitle",
        "YouTubeMusic",
        "NetEase",
        "QQMusic",
        "Kugou",
        "YouTubeCaptions",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB        -> "LrcLib"
        PreferredLyricsProvider.KUGOU         -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC     -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS     -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX      -> "Paxsenix"
    }

    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Apple Music"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "NetEase"         -> "NetEase"
        "QQMusic"         -> "QQ Music"
        "Deezer"          -> "Deezer"
        "LrcRed"          -> "lrc.red"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        "YouTubeCaptions" -> "YouTube Auto Captions"
        else              -> moduleProviders[name]?.displayName ?: name
    }

    /**
     * The open-source project behind a source, for the lyrics page's "Star on GitHub" button.
     * Only sources whose code is public: KuGou, NetEase, QQ Music, Deezer and YouTube are the
     * companies' own services, and Paxsenix hasn't published its server. SimpMusic's lyrics
     * server has no public repo of its own, so its star goes to the SimpMusic app.
     */
    fun getProject(name: String): LyricsProject? = when (name) {
        "YouLyPlus"    -> LyricsProject("LyricsPlus", "https://github.com/ibratabian17/LyricsPlus")
        "BetterLyrics" -> LyricsProject("Better Lyrics", "https://github.com/better-lyrics/better-lyrics")
        "LrcLib"       -> LyricsProject("LRCLIB", "https://github.com/tranxuanthang/lrclib")
        "SimpMusic"    -> LyricsProject("SimpMusic", "https://github.com/maxrave-dev/SimpMusic")
        else           -> null
    }

    // lrc.red lets anyone use its lyrics as long as the app links back to it wherever they show
    fun getWebsite(name: String): String? = when (name) {
        "LrcRed" -> LrcRed.BaseUrl
        else     -> null
    }
}

data class LyricsProject(val name: String, val repoUrl: String)
