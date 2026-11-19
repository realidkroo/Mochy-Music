// HistoryViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.pages.HistoryPage
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.HistorySource
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    var historySource = MutableStateFlow(HistorySource.LOCAL)

    val historyPage = MutableStateFlow<HistoryPage?>(null)

    val events =
        context.dataStore.data
            .map { it[HideVideoSongsKey] ?: false }
            .distinctUntilChanged()
            .flatMapLatest { hideVideoSongs ->
                database
                    .events()
                    .map { events ->
                        events
                            .filter { !hideVideoSongs || !it.song.song.isVideo }
                            // one group per day, newest first, each headed by its own date
                            .groupBy { it.event.timestamp.toLocalDate() }
                            .toSortedMap(compareByDescending { it })
                            .mapValues { entry ->
                                entry.value.distinctBy { it.song.id }
                            }
                    }
            // null until the first read, so a page can tell loading from never played
            }.flowOn(Dispatchers.Default).stateIn<Map<LocalDate, List<com.mochyy.mochymusic.db.entities.EventWithSong>>?>(viewModelScope, SharingStarted.Lazily, null)

    init {
        fetchRemoteHistory()
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.musicHistory().onSuccess {
                historyPage.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}
