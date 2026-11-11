// CoverSources.kt

package com.mochyy.mochymusic.songedit

import androidx.compose.runtime.Immutable
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.ModuleMetadata
import com.mochyy.mochymusic.ui.player.normalizeCanvasArtistName
import com.mochyy.mochymusic.ui.player.normalizeCanvasSongTitle
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

// one cover the edit sheet can offer, with the name of where it came from
@Immutable
data class CoverOption(val source: String, val url: String)

// looks a song's cover up everywhere at once: youtube music, apple music, deezer and any switched-on module that knows song info
object CoverSources {
    // a few per source is enough to catch the single and the album versions without a wall of the same picture
    private const val PerSource = 3
    private const val ModuleBudgetMs = 12_000L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // each source sends its covers the moment it answers, so the carousel fills in as they come; one that fails just adds nothing
    fun search(title: String, artist: String, album: String?, durationMs: Long?): Flow<CoverOption> = channelFlow {
        if (title.isBlank()) return@channelFlow
        val match = Match(title, artist)
        launch { youTubeMusic(title, artist, match).forEach { send(it) } }
        launch { appleMusic(title, artist, match).forEach { send(it) } }
        launch { deezer(title, artist, match).forEach { send(it) } }
        val query = TrackQuery(title, listOf(artist).filter { it.isNotBlank() }, album, durationMs?.takeIf { it > 0 })
        for (module in ModuleMetadata.sources) {
            launch {
                val cover = withTimeoutOrNull(ModuleBudgetMs) { safely { module.metadata(query)?.coverUrl } }
                if (!cover.isNullOrBlank()) send(CoverOption(module.info.name, cover))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun youTubeMusic(title: String, artist: String, match: Match): List<CoverOption> {
        val songs = YouTube.search("$title $artist".trim(), YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .filterNot { it.isVideoSong }
        return songs
            .sortedByDescending { match.score(it.title, it.artists.joinToString { a -> a.name }) }
            .map { it.thumbnail.resize(1200, 1200) }
            .distinct()
            .take(PerSource)
            .map { CoverOption("YouTube Music", it) }
    }

    private suspend fun appleMusic(title: String, artist: String, match: Match): List<CoverOption> = safely {
        val term = encode("$title $artist".trim())
        // the phone's own store first: a local release often only lives there
        val country = Locale.getDefault().country.lowercase().takeIf { it.length == 2 } ?: "us"
        val body = get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=15&country=$country")
            ?: return@safely emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return@safely emptyList()
        results.objects()
            .sortedByDescending { match.score(it.optString("trackName"), it.optString("artistName")) }
            .distinctBy { it.optLong("collectionId") }
            .mapNotNull { it.optString("artworkUrl100").takeIf(String::isNotBlank) }
            // the same artwork at full size; apple keeps the 100px one in the link and serves any size asked for
            .map { it.replace("100x100bb", "1200x1200bb") }
            .take(PerSource)
            .map { CoverOption("Apple Music", it) }
    } ?: emptyList()

    private suspend fun deezer(title: String, artist: String, match: Match): List<CoverOption> = safely {
        // the field search is tighter; a plain one catches titles the fields miss
        val strict = if (artist.isNotBlank()) deezerSearch("artist:\"$artist\" track:\"$title\"") else emptyList()
        val tracks = strict.ifEmpty { deezerSearch("$title $artist".trim()) }
        tracks
            .sortedByDescending { match.score(it.optString("title"), it.optJSONObject("artist")?.optString("name").orEmpty()) }
            .mapNotNull { it.optJSONObject("album") }
            .distinctBy { it.optLong("id") }
            .mapNotNull { it.optString("cover_xl").takeIf(String::isNotBlank) }
            .take(PerSource)
            .map { CoverOption("Deezer", it) }
    } ?: emptyList()

    private fun deezerSearch(query: String): List<JSONObject> {
        val body = get("https://api.deezer.com/search?q=${encode(query)}&limit=15") ?: return emptyList()
        return JSONObject(body).optJSONArray("data")?.objects().orEmpty()
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8.name())

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    // a source that breaks (blocked, changed, offline) is left out instead of taking the others down
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag("CoverSources").w(e, "A cover source failed")
        null
    }

    // how close a result is to the song: same title and artist first, then either one
    private class Match(title: String, artist: String) {
        private val title = normalizeCanvasSongTitle(title)
        private val artist = normalizeCanvasArtistName(artist)

        fun score(candidateTitle: String, candidateArtist: String): Int {
            val titleHit = close(normalizeCanvasSongTitle(candidateTitle), title)
            val artistHit = close(normalizeCanvasArtistName(candidateArtist), artist)
            return (if (titleHit) 2 else 0) + (if (artistHit) 1 else 0)
        }

        private fun close(a: String, b: String) =
            a.isNotEmpty() && b.isNotEmpty() && (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true))
    }
}
