// PendingYouTubeLikes.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import java.io.File

/**
 * Local like/unlike changes that YouTube doesn't know about yet (from imports, offline taps, or failed requests).
 * Sync flushes these to YouTube BEFORE reconciling YouTube's remote liked list so local changes are never overwritten.
 *
 * Line format:
 *   - `videoId` or `+videoId` -> pending LIKE
 *   - `-videoId` -> pending UNLIKE
 */
object PendingYouTubeLikes {
    private const val FILE_NAME = "yt_pending_likes.txt"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Returns pending liked video IDs (backwards-compatible helper). */
    fun read(context: Context): Set<String> = synchronized(lock) {
        readStatesLocked(context).filterValues { it }.keys
    }

    /** Returns all pending like/unlike state changes: videoId -> desired liked state. */
    fun readStates(context: Context): Map<String, Boolean> = synchronized(lock) {
        readStatesLocked(context)
    }

    fun set(context: Context, id: String, liked: Boolean) {
        if (id.isBlank()) return
        synchronized(lock) {
            val current = readStatesLocked(context).toMutableMap()
            current[id] = liked
            writeLocked(context, current)
        }
    }

    fun add(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val current = readStatesLocked(context).toMutableMap()
            ids.forEach { if (it.isNotBlank()) current[it] = true }
            writeLocked(context, current)
        }
    }

    fun remove(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val current = readStatesLocked(context).toMutableMap()
            ids.forEach { current.remove(it) }
            writeLocked(context, current)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
        }
    }

    private fun readStatesLocked(context: Context): Map<String, Boolean> = runCatching {
        val target = file(context)
        if (!target.exists()) return@runCatching emptyMap()
        val out = LinkedHashMap<String, Boolean>()
        target.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("-") -> {
                    val id = line.substring(1).trim()
                    if (id.isNotEmpty()) out[id] = false
                }
                line.startsWith("+") -> {
                    val id = line.substring(1).trim()
                    if (id.isNotEmpty()) out[id] = true
                }
                else -> out[line] = true
            }
        }
        out
    }.getOrNull().orEmpty()

    private fun writeLocked(context: Context, states: Map<String, Boolean>) {
        runCatching {
            val target = file(context)
            if (states.isEmpty()) {
                target.delete()
                return
            }
            val encoded = states.entries.joinToString("\n") { (id, liked) ->
                if (liked) "+$id" else "-$id"
            }
            // write-then-rename so a crash mid-write never leaves half a list
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            temp.writeText(encoded)
            if (!temp.renameTo(target)) {
                target.writeText(encoded)
                temp.delete()
            }
        }
    }
}
