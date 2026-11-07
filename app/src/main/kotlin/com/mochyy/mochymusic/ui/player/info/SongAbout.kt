// SongAbout.kt

package com.mochyy.mochymusic.ui.player.info

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.module.TrackMetadata
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.ModuleMetadata
import com.mochyy.mochymusic.ui.utils.resize
import com.music.innertube.YouTube
import com.music.musixmatch.Musixmatch
import com.music.musixmatch.MusixmatchSong
import com.music.genius.Genius
import com.music.genius.GeniusSong
import com.music.wikipedia.WikiSong
import com.music.wikipedia.Wikipedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.ConcurrentHashMap

/** everything the about sheet shows for one song, as each source said it; the facts shown are picked from them */
data class SongAbout(
    val artist: String? = null,
    val lengthSeconds: Int? = null,
    val libraryYear: Int? = null,
    val libraryAlbum: String? = null,
    val musixmatchDone: Boolean = false,
    val musixmatch: MusixmatchSong? = null,
    /** musixmatch's page (what the song is about) has been read, or there was none to read */
    val musixmatchPageDone: Boolean = false,
    val geniusDone: Boolean = false,
    val genius: GeniusSong? = null,
    val wikiDone: Boolean = false,
    val wiki: WikiSong? = null,
    val youtubeDone: Boolean = false,
    val youtubeDescription: String? = null,
    // what the song facts modules that are on said, laid together, and whose words the about text is
    val moduleDone: Boolean = false,
    val module: TrackMetadata? = null,
    val moduleName: String? = null,
    val artists: List<AboutArtist> = emptyList(),
    val links: List<StoreLink> = emptyList(),
) {
    /** every source that can fill in the facts has answered (or given up) */
    val factsDone: Boolean get() = musixmatchDone && geniusDone && wikiDone && moduleDone

    // each fact from the first source that has it: a module that's on, then musixmatch, wikipedia, genius, then the library
    val genre: String?
        get() = listOf(module?.genres, musixmatch?.genres, wiki?.genres, genius?.genres).firstOrNull { !it.isNullOrEmpty() }
            ?.take(2)?.joinToString(", ") { it.replaceFirstChar(Char::titlecase) }
    val released: String?
        get() = (module?.releaseDate ?: musixmatch?.releaseDate ?: wiki?.releaseDate ?: genius?.releaseDate)?.let(::readableDate) ?: libraryYear?.toString()
    val album: String? get() = libraryAlbum ?: module?.album ?: musixmatch?.album ?: genius?.album
    val writtenBy: String?
        get() = module?.writers.orEmpty().ifEmpty { genius?.writers.orEmpty() }.ifEmpty { wiki?.writers.orEmpty() }.joinToString(", ").ifBlank { null }
    val producedBy: String?
        get() = module?.producers.orEmpty().ifEmpty { genius?.producers.orEmpty() }.ifEmpty { wiki?.producers.orEmpty() }.joinToString(", ").ifBlank { null }
    val label: String? get() = module?.label ?: wiki?.label
}

data class AboutArtist(val id: String?, val name: String, val imageUrl: String?)

data class StoreLink(val service: AccountService, val url: String)

/** gathers [SongAbout] off the main thread: the library first, then every source side by side */
object SongAboutLoader {
    // kept for the run, so closing and opening the sheet again is instant
    private val cache = ConcurrentHashMap<String, SongAbout>()

    fun load(song: MediaMetadata, database: MusicDatabase, context: Context): Flow<SongAbout> = channelFlow {
        // here rather than at app start: it reads its saved key from disk, which belongs off the main thread
        Musixmatch.init(context)
        val cached = cache[song.id]
        if (cached != null && cached.factsDone && cached.youtubeDone && cached.musixmatchPageDone) {
            send(cached)
            return@channelFlow
        }
        val state = MutableStateFlow(cached ?: fromLibrary(song, database))
        send(state.value)
        fun publish(change: (SongAbout) -> SongAbout) {
            state.update(change)
            // a source that said nothing is kept as "still to ask": each remembers a real miss itself, a failed try gets another go
            cache[song.id] = state.value.let {
                it.copy(
                    musixmatchDone = it.musixmatch != null,
                    musixmatchPageDone = it.musixmatch?.pageRead == true,
                    geniusDone = it.genius != null,
                    wikiDone = it.wiki != null,
                    // with no song facts module on there's nothing to ask again
                    moduleDone = it.module != null || ModuleMetadata.sources.isEmpty(),
                )
            }
            trySend(state.value)
        }

        val artistName = song.artists.firstOrNull()?.name.orEmpty()
        launch {
            val description = if (song.id.startsWith("LOCAL_")) null
            else YouTube.getMediaInfo(song.id).getOrNull()?.description?.trim()?.takeIf { it.isNotEmpty() }
            publish { it.copy(youtubeDone = true, youtubeDescription = description) }
        }
        launch {
            val found = if (artistName.isBlank()) null else Musixmatch.song(song.title, artistName, song.duration.takeIf { d -> d > 0 })
            publish { about -> about.withMusixmatch(found, pageDone = found == null || found.pageRead).withLinks(song) }
            // the facts are in; the page with what the song is about takes a few seconds more in a hidden browser
            if (found != null && !found.pageRead) {
                val read = Musixmatch.readPage(found)
                publish { about -> about.withMusixmatch(read ?: found, pageDone = true).withLinks(song) }
            }
        }
        launch {
            val found = if (artistName.isBlank()) null else Genius.song(song.title, artistName)
            publish { about -> about.withGenius(found).withLinks(song) }
        }
        launch {
            val found = if (artistName.isBlank()) null else Wikipedia.song(song.title, artistName)
            publish { it.copy(wikiDone = true, wiki = found) }
        }
        launch {
            val track = TrackQuery(
                title = song.title,
                artists = song.artists.map { it.name },
                album = song.album?.title,
                durationMs = song.duration.takeIf { it > 0 }?.times(1000L),
            )
            val facts = ModuleMetadata.lookup(track)
            publish { it.copy(moduleDone = true, module = facts?.metadata, moduleName = facts?.aboutFrom) }
        }
        launch {
            val photos = artistPhotos(song, database)
            publish { about -> about.copy(artists = about.artists.map { a -> a.copy(imageUrl = photos[a.name] ?: a.imageUrl) }) }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fromLibrary(song: MediaMetadata, database: MusicDatabase): SongAbout {
        val stored = database.song(song.id).first()
        return SongAbout(
            artist = song.artists.joinToString(", ") { it.name }.ifBlank { null },
            lengthSeconds = song.duration.takeIf { it > 0 },
            libraryYear = stored?.song?.year ?: stored?.album?.year,
            libraryAlbum = song.album?.title ?: stored?.song?.albumName,
            artists = song.artists.map { AboutArtist(it.id, it.name, null) },
        ).withLinks(song)
    }

    private fun SongAbout.withMusixmatch(found: MusixmatchSong?, pageDone: Boolean): SongAbout {
        val byName = found?.artists.orEmpty().associateBy { it.name.lowercase() }
        return copy(
            musixmatchDone = true,
            musixmatchPageDone = pageDone,
            musixmatch = found,
            artists = artists.map { a -> if (a.imageUrl == null) a.copy(imageUrl = byName[a.name.lowercase()]?.imageUrl) else a },
        )
    }

    // genius names its artist "eill (jpn)": its photo stands in for the first artist when nothing else had one
    private fun SongAbout.withGenius(found: GeniusSong?): SongAbout {
        val photo = found?.artistImageUrl
        val lead = artists.firstOrNull()
        val sameArtist = lead != null && found != null &&
            found.artist.substringBefore(" (").trim().equals(lead.name.trim(), ignoreCase = true)
        return copy(
            geniusDone = true,
            genius = found,
            artists = if (sameArtist && lead!!.imageUrl == null && photo != null) listOf(lead.copy(imageUrl = photo)) + artists.drop(1) else artists,
        )
    }

    private fun SongAbout.withLinks(song: MediaMetadata): SongAbout = copy(
        links = linksFor(song, musixmatch?.spotifyId ?: genius?.spotifyId, musixmatch?.appleMusicId ?: genius?.appleMusicId),
    )

    // the library's photo when the artist is in it, otherwise their youtube music page's, in parallel
    private suspend fun artistPhotos(song: MediaMetadata, database: MusicDatabase): Map<String, String> = coroutineScope {
        song.artists.filter { !it.id.isNullOrBlank() }.map { artist ->
            async {
                val id = artist.id!!
                val url = runCatching {
                    database.artist(id).first()?.artist?.thumbnailUrl ?: YouTube.artist(id).getOrNull()?.artist?.thumbnail
                }.getOrNull()?.resize(240, 240)
                url?.let { artist.name to it }
            }
        }.mapNotNull { it.await() }.toMap()
    }

    // a store's own song page when a source knows its id, its search otherwise
    private fun linksFor(song: MediaMetadata, spotifyId: String?, appleMusicId: String?): List<StoreLink> {
        val query = URLEncoder.encode("${song.title} ${song.artists.firstOrNull()?.name.orEmpty()}".trim(), "UTF-8")
        return listOfNotNull(
            StoreLink(
                AccountService.SPOTIFY,
                spotifyId?.let { "https://open.spotify.com/track/$it" } ?: "https://open.spotify.com/search/$query",
            ),
            StoreLink(AccountService.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=${song.id}").takeIf { !song.id.startsWith("LOCAL_") },
            StoreLink(
                AccountService.APPLE_MUSIC,
                appleMusicId?.let { "https://music.apple.com/song/$it" } ?: "https://music.apple.com/search?term=$query",
            ),
            StoreLink(AccountService.TIDAL, "https://listen.tidal.com/search?q=$query"),
        )
    }

}

// "2014-03-17" as the phone writes dates, "2014-03" as a month and year, "2014" as it is
private fun readableDate(iso: String): String? = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.recoverCatching {
    YearMonth.parse(iso).format(DateTimeFormatter.ofPattern("MMM yyyy"))
}.getOrNull() ?: iso.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
