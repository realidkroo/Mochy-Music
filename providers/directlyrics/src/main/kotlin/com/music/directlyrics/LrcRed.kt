package com.music.directlyrics

import com.music.directlyrics.models.LrcRedSearchResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.math.roundToInt

/** lrc.red finds the song by name and hands back apple-style ttml, with word or syllable timing, singers and background vocals when it has them */
object LrcRed {

    suspend fun getTtml(title: String, artist: String, duration: Int): Result<String> = runCatching {
        val isrc = findTrack(title, artist, duration) ?: throw IllegalStateException("No matching lrc.red track")
        lyricsHttp.get("$BaseUrl/s/$isrc.ttml").bodyAsText()
    }

    // a long joined artist list can knock the song out of the results, so the title is tried alone before giving up
    private suspend fun findTrack(title: String, artist: String, duration: Int): String? =
        search("$title $artist").bestMatch(title, artist, duration)
            ?: search(title).bestMatch(title, artist, duration)

    private suspend fun search(query: String): List<TrackCandidate<String>> =
        lyricsHttp.get("$BaseUrl/search.json") {
            parameter("q", query)
        }.body<LrcRedSearchResponse>().hits.map { hit ->
            TrackCandidate(
                titles = listOf(hit.title),
                artists = listOf(hit.artist),
                durationSec = hit.duration.roundToInt(),
                ref = hit.isrc,
            )
        }

    const val BaseUrl = "https://lrc.red"
}
