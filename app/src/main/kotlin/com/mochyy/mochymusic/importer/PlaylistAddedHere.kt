// PlaylistAddedHere.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

// for playlists that mirror youtube: tracks pending local additions/removals that haven't been confirmed on youtube yet,
// plus what youtube had last time, so two-way sync keeps offline changes and mirrors remote additions/removals cleanly
object PlaylistAddedHere {
    private const val Prefs = "playlist_added_here"
    private const val MaxRemembered = 300

    fun add(context: Context, playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val before = added(context, playlistId)
        val now = (songIds + before).distinct().take(MaxRemembered)
        val removals = pendingRemovals(context, playlistId) - songIds.toSet()
        prefs.edit {
            putString(addedKey(playlistId), JSONArray(now).toString())
            if (removals.isEmpty()) remove(removedKey(playlistId)) else putStringSet(removedKey(playlistId), removals)
        }
    }

    /** Marks previously pending local additions as confirmed on YouTube so future remote deletions are respected. */
    fun confirmAdded(context: Context, playlistId: String, songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        val confirmed = songIds.toSet()
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val remaining = added(context, playlistId).filterNot { it in confirmed }
        val seen = remoteSeen(context, playlistId)?.let { it + confirmed }
        prefs.edit {
            putString(addedKey(playlistId), JSONArray(remaining).toString())
            if (seen != null) putStringSet(seenKey(playlistId), seen)
        }
    }

    /** Records local removal(s) of songs from a synced playlist so sync pushes them if offline / failed. */
    fun addRemoval(context: Context, playlistId: String, songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        val ids = songIds.toSet()
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val remainingAdds = added(context, playlistId).filterNot { it in ids }
        val removals = (pendingRemovals(context, playlistId) + ids)
        prefs.edit {
            putString(addedKey(playlistId), JSONArray(remainingAdds).toString())
            putStringSet(removedKey(playlistId), removals)
        }
    }

    /** Clears pending removals once YouTube has confirmed them. */
    fun confirmRemoved(context: Context, playlistId: String, songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        val ids = songIds.toSet()
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val remaining = pendingRemovals(context, playlistId) - ids
        val seen = remoteSeen(context, playlistId)?.let { it - ids }
        prefs.edit {
            if (remaining.isEmpty()) remove(removedKey(playlistId)) else putStringSet(removedKey(playlistId), remaining)
            if (seen != null) putStringSet(seenKey(playlistId), seen)
        }
    }

    // newest first: songs added locally that have NOT yet been confirmed on YouTube
    fun added(context: Context, playlistId: String): List<String> {
        val text = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(addedKey(playlistId), null) ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    // songs removed locally that have NOT yet been confirmed removed on YouTube
    fun pendingRemovals(context: Context, playlistId: String): Set<String> =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getStringSet(removedKey(playlistId), null)?.toSet().orEmpty()

    // what youtube's copy held at the last sync; null before the first one
    fun remoteSeen(context: Context, playlistId: String): Set<String>? =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getStringSet(seenKey(playlistId), null)?.toSet()

    // after a sync: youtube's list, and only the still-unpushed local additions (anything already in remoteIds is cleared!)
    fun remember(context: Context, playlistId: String, remoteIds: List<String>, stillAdded: List<String>, stillRemoved: Set<String> = emptySet()) {
        val remoteSet = remoteIds.toSet()
        val unconfirmedAdds = stillAdded.filterNot { it in remoteSet }.take(MaxRemembered)
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit {
            putStringSet(seenKey(playlistId), remoteSet)
            putString(addedKey(playlistId), JSONArray(unconfirmedAdds).toString())
            if (stillRemoved.isEmpty()) remove(removedKey(playlistId)) else putStringSet(removedKey(playlistId), stillRemoved)
        }
    }

    fun clearPlaylist(context: Context, playlistId: String) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit {
            remove(addedKey(playlistId))
            remove(seenKey(playlistId))
            remove(removedKey(playlistId))
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun addedKey(playlistId: String) = "$playlistId:added"
    private fun seenKey(playlistId: String) = "$playlistId:seen"
    private fun removedKey(playlistId: String) = "$playlistId:removed"
}
