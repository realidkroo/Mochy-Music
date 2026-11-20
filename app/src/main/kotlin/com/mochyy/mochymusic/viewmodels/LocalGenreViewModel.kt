// LocalGenreViewModel.kt
//
// The Library's Genres tab and each genre's page. Files on your phone go by the genre in their tags
// (a song tagged "Rock; Pop" sits in both); everything else goes by its artist's genre, the same
// answers Stats uses, so a genre Stats shows always has a page here.

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.importer.GenreSeparator
import com.mochyy.mochymusic.utils.ArtistGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LocalGenre(val name: String, val songs: List<Song>)

fun localGenreRoute(name: String) = "local_genre/${Uri.encode(name)}"

private fun Song.tagGenres(): List<String> = song.genre.orEmpty().split(GenreSeparator).map { it.trim() }.filter { it.isNotEmpty() }

// the tag wins when there is one, otherwise each of its artists' genres
private fun Song.genres(artistGenres: Map<String, String>): List<String> =
    tagGenres().ifEmpty { artists.mapNotNull { artistGenres[ArtistGenres.keyOf(it.name)] }.distinct() }

// one genre per name whatever its letter case, spelled the way it was first seen, a to z
private fun groupByGenre(songs: List<Song>, artistGenres: Map<String, String>): List<LocalGenre> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
    songs.forEach { song ->
        song.genres(artistGenres).forEach { genre ->
            groups.getOrPut(genre.lowercase()) { genre to mutableListOf() }.second += song
        }
    }
    return groups.values.map { (name, list) -> LocalGenre(name, list) }.sortedBy { it.name.lowercase() }
}

// the artists behind untagged songs that nobody has asked itunes about yet, the ones with the most songs first
private fun unknownArtists(songs: List<Song>, artistGenres: Map<String, String>): List<String> =
    songs.asSequence()
        .filter { it.tagGenres().isEmpty() }
        .flatMap { it.artists.asSequence().map { artist -> artist.name } }
        .filter { ArtistGenres.keyOf(it).isNotEmpty() && ArtistGenres.keyOf(it) !in artistGenres }
        .groupingBy { ArtistGenres.keyOf(it) }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

private fun songsWithGenres(database: MusicDatabase): Flow<Pair<List<Song>, Map<String, String>>> =
    combine(database.songsForGenres(), ArtistGenres.known.filterNotNull()) { songs, known -> songs to known }

@HiltViewModel
class LocalGenresViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    // null while it's first read; nothing is read while the tab isn't open
    val genres: StateFlow<List<LocalGenre>?> = songsWithGenres(database)
        .map { (songs, known) -> groupByGenre(songs, known) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // a few new artists per visit, the same gentle pace stats keeps; the list fills in as answers come back
        viewModelScope.launch(Dispatchers.Default) {
            val known = ArtistGenres.load(context)
            val songs = database.songsForGenres().first()
            val missing = unknownArtists(songs, known)
            if (missing.isNotEmpty()) runCatching { ArtistGenres.lookUp(context, missing) }
        }
    }
}

@HiltViewModel
class LocalGenreViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val name: String = savedStateHandle.get<String>("name").orEmpty()

    val songs: StateFlow<List<Song>?> = songsWithGenres(database)
        .map { (songs, known) -> songs.filter { song -> song.genres(known).any { it.equals(name, ignoreCase = true) } } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { ArtistGenres.load(context) }
    }
}
