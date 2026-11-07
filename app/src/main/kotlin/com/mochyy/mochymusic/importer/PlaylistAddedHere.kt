// PlaylistAddedHere.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

// for playlists that mirror youtube: the songs added in mochy (newest first) and what youtube had last time, so a sync keeps one and follows the other
object PlaylistAddedHere {
    private const val Prefs = "playlist_added_here"
    private const val MaxRemembered = 300

    fun add(context: Context, playlistId: String, songIds: List<String>) {
        val before = added(context, playlistId)
        val now = (songIds + before).distinct().take(MaxRemembered)
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit { putString(addedKey(playlistId), JSONArray(now).toString()) }
    }

    // newest first
    fun added(context: Context, playlistId: String): List<String> {
        val text = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(addedKey(playlistId), null) ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    // what youtube's copy held at the last sync; null before the first one
    fun remoteSeen(context: Context, playlistId: String): Set<String>? =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getStringSet(seenKey(playlistId), null)

    // after a sync: youtube's list, and the added songs that are still in the playlist
    fun remember(context: Context, playlistId: String, remoteIds: List<String>, stillAdded: List<String>) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit {
            putStringSet(seenKey(playlistId), remoteIds.toSet())
            putString(addedKey(playlistId), JSONArray(stillAdded.take(MaxRemembered)).toString())
        }
    }

    private fun addedKey(playlistId: String) = "$playlistId:added"
    private fun seenKey(playlistId: String) = "$playlistId:seen"
}
