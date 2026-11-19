// LocalGenreViewModel.kt
//
// The Library's Genres tab and each genre's page: the songs on your phone grouped by the genre in
// their tags. A song tagged "Rock; Pop" sits in both.

package com.mochyy.mochymusic.viewmodels

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.importer.GenreSeparator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class LocalGenre(val name: String, val songs: List<Song>)

fun localGenreRoute(name: String) = "local_genre/${Uri.encode(name)}"

private fun Song.genres(): List<String> = song.genre.orEmpty().split(GenreSeparator).map { it.trim() }.filter { it.isNotEmpty() }

// one genre per name whatever its letter case, spelled the way it was first seen, a to z
private fun groupByGenre(songs: List<Song>): List<LocalGenre> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
    songs.forEach { song ->
        song.genres().forEach { genre ->
            groups.getOrPut(genre.lowercase()) { genre to mutableListOf() }.second += song
        }
    }
    return groups.values.map { (name, list) -> LocalGenre(name, list) }.sortedBy { it.name.lowercase() }
}

@HiltViewModel
class LocalGenresViewModel @Inject constructor(database: MusicDatabase) : ViewModel() {
    // null while it's first read; nothing is read while the tab isn't open
    val genres: StateFlow<List<LocalGenre>?> = database.localSongsWithGenre()
        .map(::groupByGenre)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@HiltViewModel
class LocalGenreViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val name: String = savedStateHandle.get<String>("name").orEmpty()

    val songs: StateFlow<List<Song>?> = database.localSongsWithGenre()
        .map { songs -> songs.filter { song -> song.genres().any { it.equals(name, ignoreCase = true) } } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
