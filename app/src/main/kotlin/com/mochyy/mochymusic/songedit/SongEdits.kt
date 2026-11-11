// SongEdits.kt

package com.mochyy.mochymusic.songedit

import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SongEdit
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// every hand edit kept in memory, so queue items and the player can wear them without a database read on the main thread
object SongEdits {
    private val _all = MutableStateFlow<Map<String, SongEdit>>(emptyMap())
    val all: StateFlow<Map<String, SongEdit>> = _all.asStateFlow()

    private var following: Job? = null

    // the music service starts this once; the table is small, so the whole of it is kept
    @Synchronized
    fun follow(database: MusicDatabase, scope: CoroutineScope) {
        if (following?.isActive == true) return
        following = scope.launch(Dispatchers.IO) {
            database.songEdits().collect { edits -> _all.value = edits.associateBy { it.songId } }
        }
    }

    fun of(songId: String?): SongEdit? = songId?.let { _all.value[it] }

    fun applyTo(metadata: MediaMetadata): MediaMetadata = of(metadata.id)?.applyTo(metadata) ?: metadata
}
