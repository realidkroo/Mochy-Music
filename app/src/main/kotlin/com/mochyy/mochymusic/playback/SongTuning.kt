// SongTuning.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

// speed and pitch kept for single songs, from the speed sheet's "only this song" tick; every other song plays at audio lab's
@Singleton
class SongTuning @Inject constructor(@ApplicationContext context: Context) {
    data class Tuning(val speed: Float, val pitch: Float)

    private val prefs = context.getSharedPreferences("song_tuning", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(read())
    val all: StateFlow<Map<String, Tuning>> = _all.asStateFlow()

    fun of(mediaId: String?): Tuning? = mediaId?.let { _all.value[it] }

    fun set(mediaId: String, tuning: Tuning) {
        _all.update { it + (mediaId to tuning) }
        prefs.edit { putString(mediaId, "${tuning.speed},${tuning.pitch}") }
    }

    fun clear(mediaId: String) {
        _all.update { it - mediaId }
        prefs.edit { remove(mediaId) }
    }

    private fun read(): Map<String, Tuning> = prefs.all.mapNotNull { (id, value) ->
        val parts = (value as? String)?.split(',') ?: return@mapNotNull null
        val speed = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val pitch = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        id to Tuning(speed, pitch)
    }.toMap()
}
