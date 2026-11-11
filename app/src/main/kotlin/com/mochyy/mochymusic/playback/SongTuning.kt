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

// speed, pitch and an eq preset kept for single songs, from the speed sheet's "only this song" tick and the song edit sheet; every other song plays with audio lab's
@Singleton
class SongTuning @Inject constructor(@ApplicationContext context: Context) {
    data class Tuning(val speed: Float, val pitch: Float)

    private val prefs = context.getSharedPreferences("song_tuning", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(read())
    val all: StateFlow<Map<String, Tuning>> = _all.asStateFlow()

    // song id to the id of the eq preset it plays through, built in or saved in audio lab
    private val eqPrefs = context.getSharedPreferences("song_eq", Context.MODE_PRIVATE)
    private val _eqPresets = MutableStateFlow(readEq())
    val eqPresets: StateFlow<Map<String, String>> = _eqPresets.asStateFlow()

    fun of(mediaId: String?): Tuning? = mediaId?.let { _all.value[it] }

    fun set(mediaId: String, tuning: Tuning) {
        _all.update { it + (mediaId to tuning) }
        prefs.edit { putString(mediaId, "${tuning.speed},${tuning.pitch}") }
    }

    fun clear(mediaId: String) {
        _all.update { it - mediaId }
        prefs.edit { remove(mediaId) }
    }

    fun eqPresetOf(mediaId: String?): String? = mediaId?.let { _eqPresets.value[it] }

    // null puts the song back on audio lab's own curve
    fun setEqPreset(mediaId: String, presetId: String?) {
        if (presetId == null) {
            _eqPresets.update { it - mediaId }
            eqPrefs.edit { remove(mediaId) }
        } else {
            _eqPresets.update { it + (mediaId to presetId) }
            eqPrefs.edit { putString(mediaId, presetId) }
        }
    }

    private fun read(): Map<String, Tuning> = prefs.all.mapNotNull { (id, value) ->
        val parts = (value as? String)?.split(',') ?: return@mapNotNull null
        val speed = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val pitch = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        id to Tuning(speed, pitch)
    }.toMap()

    private fun readEq(): Map<String, String> =
        eqPrefs.all.mapNotNull { (id, value) -> (value as? String)?.let { id to it } }.toMap()
}
