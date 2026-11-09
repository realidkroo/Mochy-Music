// AutoDownloads.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.AutoDownloadPlaylistsKey
import com.mochyy.mochymusic.db.Converters
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime

// playlists, and liked songs, whose new songs download by themselves; each keeps where it started counting, so the songs already there aren't fetched
object AutoDownloads {
    // liked songs aren't a playlist row, so they go under the id the liked page uses
    const val LikedId = "liked"

    // each switched-on list with its mark: the last entry's id for a playlist, the time liking started counting for liked songs
    fun marks(prefs: Preferences): Map<String, Long> = parse(prefs[AutoDownloadPlaylistsKey])

    suspend fun setEnabled(context: Context, database: MusicDatabase, playlistId: String, enabled: Boolean) {
        val mark = if (!enabled) null else withContext(Dispatchers.IO) {
            if (playlistId == LikedId) {
                // likedDate is stored through this converter, so the mark has to go through it too or it's off by the time zone
                Converters().dateToTimestamp(LocalDateTime.now()) ?: 0L
            } else {
                database.lastPlaylistEntryId(playlistId) ?: 0L
            }
        }
        context.dataStore.edit { prefs ->
            val all = parse(prefs[AutoDownloadPlaylistsKey]).toMutableMap()
            if (mark == null) all.remove(playlistId) else all[playlistId] = mark
            prefs[AutoDownloadPlaylistsKey] = JSONObject(all as Map<*, *>).toString()
        }
    }

    // whether [playlistId] downloads its new songs, for its menu's switch
    @Composable
    fun rememberEnabled(playlistId: String): Boolean {
        val (stored) = rememberPreference(AutoDownloadPlaylistsKey, defaultValue = "")
        return remember(stored, playlistId) { playlistId in parse(stored) }
    }

    private fun parse(stored: String?): Map<String, Long> {
        if (stored.isNullOrBlank()) return emptyMap()
        val json = runCatching { JSONObject(stored) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.optLong(it) }
    }
}
