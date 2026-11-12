// VoiceRequest.kt

package com.mochyy.mochymusic.playback.voice

import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import java.text.Normalizer

// what "hey google, play … on mochy" asked for, worked out from the words and the hints google sends beside them
sealed interface VoiceRequest {
    val shuffle: Boolean

    // "play music on mochy": nothing named, so carry on with what was playing
    data object Resume : VoiceRequest { override val shuffle = false }

    data class Liked(override val shuffle: Boolean = false) : VoiceRequest

    data class Downloads(override val shuffle: Boolean = false) : VoiceRequest

    data class Artist(val name: String, override val shuffle: Boolean = false) : VoiceRequest

    data class Album(val title: String, val artist: String?, override val shuffle: Boolean = false) : VoiceRequest

    // one of your own playlists by name first, then one youtube has
    data class Playlist(val name: String, override val shuffle: Boolean = false) : VoiceRequest

    // a song, or a genre, played with its radio after it
    data class Song(val text: String, override val shuffle: Boolean = false) : VoiceRequest

    // anything google couldn't place: whatever search's top result is
    data class Top(val text: String, override val shuffle: Boolean = false) : VoiceRequest
}

object VoiceSearch {
    const val FocusArtist = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
    const val FocusAlbum = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE
    const val FocusSong = MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
    const val FocusPlaylist = MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE
    const val FocusGenre = MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE
    // what google sends when it couldn't tell what kind of thing was asked for
    const val FocusAny = "vnd.android.cursor.item/*"

    // a play-from-search reaches the session as an item with no id and the spoken words as its search query
    fun isRequest(item: MediaItem): Boolean = item.mediaId.isEmpty() && item.requestMetadata.searchQuery != null

    fun parse(query: String?, extras: Bundle?): VoiceRequest = parse(
        query = query,
        focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS),
        title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE),
        artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
        album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM),
        genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE),
        playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST),
    )

    // only the hints parse reads, copied off an assistant intent so they can travel to the service
    fun extrasOf(from: Bundle?): Bundle? {
        from ?: return null
        val out = Bundle()
        for (key in Keys) from.getString(key)?.let { out.putString(key, it) }
        return out.takeUnless { it.isEmpty }
    }

    fun parse(
        query: String?,
        focus: String? = null,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        playlist: String? = null,
    ): VoiceRequest {
        val heard = clean(query)
        // google usually keeps the word "shuffle" in what it heard
        val shuffle = ShuffleWords.containsMatchIn(heard)
        val text = heard.replace(ShuffleWords, "").trim()
        val songTitle = clean(title)
        val artistName = clean(artist)
        val albumTitle = clean(album)
        val genreName = clean(genre)
        val playlistName = clean(playlist)

        // "my liked songs" means yours, whatever kind of thing google took it for
        val own = playlistName.ifEmpty { text }
        if (LikedWords.matches(own)) return VoiceRequest.Liked(shuffle)
        if (DownloadWords.matches(own)) return VoiceRequest.Downloads(shuffle)
        if ((focus.isNullOrEmpty() || focus == FocusAny) && ResumeWords.matches(text)) return if (shuffle) VoiceRequest.Liked(shuffle = true) else VoiceRequest.Resume

        val byFocus = when (focus) {
            FocusArtist -> artistName.ifEmpty { text }.ifEmpty { null }?.let { VoiceRequest.Artist(it, shuffle) }
            FocusAlbum -> albumTitle.ifEmpty { text }.ifEmpty { null }?.let { VoiceRequest.Album(it, artistName.ifEmpty { null }, shuffle) }
            FocusPlaylist -> playlistName.ifEmpty { text }.ifEmpty { null }?.let { VoiceRequest.Playlist(it, shuffle) }
            FocusSong -> (if (songTitle.isNotEmpty()) joined(songTitle, artistName) else text).ifEmpty { null }?.let { VoiceRequest.Song(it, shuffle) }
            FocusGenre -> genreName.ifEmpty { text }.ifEmpty { null }?.let { VoiceRequest.Song(it, shuffle) }
            else -> null
        }
        if (byFocus != null) return byFocus

        return when {
            text.isNotEmpty() -> VoiceRequest.Top(text, shuffle)
            // no words, but the pieces came through on their own
            albumTitle.isNotEmpty() -> VoiceRequest.Album(albumTitle, artistName.ifEmpty { null }, shuffle)
            songTitle.isNotEmpty() -> VoiceRequest.Song(joined(songTitle, artistName), shuffle)
            artistName.isNotEmpty() -> VoiceRequest.Artist(artistName, shuffle)
            playlistName.isNotEmpty() -> VoiceRequest.Playlist(playlistName, shuffle)
            genreName.isNotEmpty() -> VoiceRequest.Song(genreName, shuffle)
            shuffle -> VoiceRequest.Liked(shuffle = true)
            else -> VoiceRequest.Resume
        }
    }

    // how well a name matches what was said: 3 the same words, 2 every spoken word in it, 1 close enough, 0 not it
    fun nameMatch(name: String, spoken: String): Int {
        val have = words(name)
        val said = words(spoken)
        if (have.isEmpty() || said.isEmpty()) return 0
        return when {
            have == said || have.joinToString("") == said.joinToString("") -> 3
            have.containsAll(said) -> 2
            said.containsAll(have) && have.size >= 2 -> 1
            else -> 0
        }
    }

    // what was heard without a leading "play" or a trailing "on mochy", which google sometimes leaves in
    private fun clean(text: String?): String {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return trimmed
        return trimmed.replace(OnMochy, "").replace(PlayWord, "").trim().replace(Spaces, " ")
    }

    private fun joined(vararg parts: String) = parts.filter { it.isNotEmpty() }.joinToString(" ")

    private fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Accents, "")
            .split(NotWord)
            .filter { it.isNotEmpty() && it !in Filler }

    private val Keys = listOf(
        MediaStore.EXTRA_MEDIA_FOCUS,
        MediaStore.EXTRA_MEDIA_TITLE,
        MediaStore.EXTRA_MEDIA_ARTIST,
        MediaStore.EXTRA_MEDIA_ALBUM,
        MediaStore.EXTRA_MEDIA_GENRE,
        MediaStore.EXTRA_MEDIA_PLAYLIST,
    )
    private val Filler = setOf("my", "the", "a", "playlist")
    private val Accents = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NotWord = Regex("[^\\p{L}\\p{N}]+")
    private val Spaces = Regex("\\s+")
    private val OnMochy = Regex("(^|\\s+)(on|in|from|with|using)\\s+mochy(\\s+music)?\\s*$", RegexOption.IGNORE_CASE)
    private val PlayWord = Regex("^play\\s+", RegexOption.IGNORE_CASE)
    private val ShuffleWords = Regex("^shuffle\\b\\s*|\\s*\\bon shuffle$|\\s*\\bshuffled$", RegexOption.IGNORE_CASE)
    private val LikedWords = Regex(
        "^(my\\s+)?((liked|favou?rite|loved)\\s+(songs|music|tracks)|likes|favou?rites|(songs|music)\\s+i\\s+(like|liked|love))$",
        RegexOption.IGNORE_CASE,
    )
    private val ResumeWords = Regex("^((some\\s+)?(music|songs)|something)$", RegexOption.IGNORE_CASE)
    private val DownloadWords = Regex(
        "^(my\\s+)?(downloads|downloaded\\s+(songs|music)|offline\\s+(songs|music))$",
        RegexOption.IGNORE_CASE,
    )
}
