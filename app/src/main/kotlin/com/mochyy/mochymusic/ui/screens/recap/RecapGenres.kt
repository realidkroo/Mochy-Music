// RecapGenres.kt
//
// Nothing in the library knows a song's genre - YouTube Music never says. The Stats page borrows
// iTunes' primary genre for each *artist* (ArtistGenres), asked with the artist's name only, never
// with anything about what or how much was played, and weighs the shares locally by listening time.

package com.mochyy.mochymusic.ui.screens.recap

import android.content.Context
import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.utils.ArtistGenres

@Immutable
data class GenreShare(val name: String, val fraction: Float, val playTimeMs: Long)

internal object RecapGenres {
    /** Genre shares of [artists]' listening time, biggest first; null when none could be named. */
    suspend fun shares(context: Context, artists: List<RecapArtist>): List<GenreShare>? {
        val known = ArtistGenres.lookUp(context, artists.map { it.name })
        val totals = LinkedHashMap<String, Long>()
        for (artist in artists) {
            val genre = known[ArtistGenres.keyOf(artist.name)] ?: continue
            totals[genre] = (totals[genre] ?: 0L) + artist.playTimeMs
        }
        val sum = totals.values.sum().takeIf { it > 0 } ?: return null
        return totals.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { GenreShare(it.key, it.value.toFloat() / sum, it.value) }
    }
}
