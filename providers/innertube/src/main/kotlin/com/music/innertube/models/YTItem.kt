package com.music.innertube.models

import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import kotlinx.serialization.Serializable

@Serializable
sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

@Serializable
data class Artist(
    val name: String,
    val id: String?,
)

@Serializable
data class Album(
    val name: String,
    val id: String,
)

@Serializable
data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val musicVideoType: String? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val historyRemoveToken: String? = null
) : YTItem() {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

@Serializable
data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val description: String? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

@Serializable
data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

@Serializable
data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it is SongItem && it.isVideoSong }
    } else {
        this
    }

// youtube music doesn't mark shorts; in its video search every real short was a fan upload of 1:16 or less, and no other video was under 2:30
private const val SHORT_MAX_SECONDS = 90

private val SHORTS_HASHTAG = Regex("(?i)#shorts?\\b")

// a youtube short, told by its #shorts tag or by being a fan upload barely a minute long; songs and official videos never count
val SongItem.isLikelyShort: Boolean
    get() = isVideoSong && (SHORTS_HASHTAG.containsMatchIn(title) ||
        (musicVideoType == MUSIC_VIDEO_TYPE_UGC && duration != null && duration <= SHORT_MAX_SECONDS))

fun <T : YTItem> List<T>.filterYoutubeShorts(enabled: Boolean = false) =
    if (enabled) {
        filterNot { (it is PlaylistItem && it.id.startsWith("SS")) || (it is SongItem && it.isLikelyShort) }
    } else {
        this
    }

// words that give away music made with ai; "suno" alone is hindi for "listen", so only "suno ai" counts
private val AI_GENERATED_PATTERN = Regex(
    "(?i)\\b(ai[\\s-]*(covers?|generated|music|remix|version|voice|vocals?)|suno\\s*ai|udio|(made|created|generated|produced)\\s*(with|by|using)\\s*ai)\\b|[(\\[]\\s*ai\\s*[)\\]]"
)

// whether a title or a name says it was made with ai
fun isAiGeneratedText(text: String?): Boolean = text != null && AI_GENERATED_PATTERN.containsMatchIn(text)

// the title, and the artist, album or playlist owner's name too: most ai channels say so in their name, not in every title
fun YTItem.looksAiGenerated(): Boolean = isAiGeneratedText(title) || when (this) {
    is SongItem -> artists.any { isAiGeneratedText(it.name) } || isAiGeneratedText(album?.name)
    is AlbumItem -> artists.orEmpty().any { isAiGeneratedText(it.name) }
    is PlaylistItem -> isAiGeneratedText(author?.name)
    else -> false
}

fun <T : YTItem> List<T>.filterAiGenerated(enabled: Boolean = false) =
    if (enabled) {
        filterNot { it.looksAiGenerated() }
    } else {
        this
    }
