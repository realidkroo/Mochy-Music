// YouTubeLikesMemory.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

/** what the liked songs sync keeps between runs: the likes youtube listed last time, and every like the sync took off */
object YouTubeLikesMemory {
    private const val FILE_NAME = "yt_likes_memory.json"
    // enough for a whole library taken off by one bad sync, oldest ones drop first
    private const val MaxRemoved = 20_000
    // more unlikes than this in one sync, and over a tenth of the list, is youtube sending a short list rather than real unlikes
    private const val MaxUnlikesAtOnce = 20
    private val json = Json { ignoreUnknownKeys = true }
    private val fileLock = Any()
    private val runLock = Mutex()

    @Serializable
    private data class Memory(
        val seen: List<String>? = null,
        val removed: Map<String, String?> = emptyMap(),
    )

    // the likes youtube surely dropped, or null when this list can't tell: cut short, or a mass drop that's a youtube hiccup
    fun droppedOnYouTube(likedHere: List<String>, seen: Set<String>?, remote: Set<String>, wholeList: Boolean, keep: Set<String>): Set<String>? {
        if (!wholeList) return null
        // before the first full read nothing counts as listed, so the first sync after an update takes nothing off
        if (seen == null) return emptySet()
        // a song youtube never listed was liked here (an import, a file, a like that never went up), so it stays
        val dropped = likedHere.filterTo(LinkedHashSet()) { it in seen && it !in remote && it !in keep }
        if (dropped.isNotEmpty() && remote.isEmpty()) return null
        if (dropped.size > MaxUnlikesAtOnce && dropped.size * 10 > seen.size) return null
        return dropped
    }

    // one liked sync or restore at a time, two at once would each write back a stale list
    suspend fun <T> exclusive(block: suspend () -> T): T = runLock.withLock { block() }

    // what youtube's liked list held at the last full read; null before the first one
    fun seen(context: Context): Set<String>? = synchronized(fileLock) { read(context).seen?.toSet() }

    fun rememberSeen(context: Context, ids: Set<String>) = synchronized(fileLock) {
        write(context, read(context).copy(seen = ids.toList()))
    }

    // with the like date each song had, so bringing it back puts it where it was
    fun recordRemoved(context: Context, songs: Map<String, LocalDateTime?>) {
        if (songs.isEmpty()) return
        synchronized(fileLock) {
            val memory = read(context)
            val removed = (memory.removed - songs.keys) + songs.mapValues { it.value?.toString() }
            write(context, memory.copy(removed = removed.entries.toList().takeLast(MaxRemoved).associate { it.key to it.value }))
        }
    }

    fun removed(context: Context): Map<String, LocalDateTime?> = synchronized(fileLock) {
        read(context).removed.mapValues { (_, date) -> date?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    }

    // after a like comes back by hand it's a like made here, so the next sync can't count it as one youtube dropped
    fun forget(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(fileLock) {
            val memory = read(context)
            val gone = ids.toSet()
            write(context, memory.copy(seen = memory.seen?.filterNot { it in gone }, removed = memory.removed - gone))
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): Memory = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Memory>(it) }
    }.getOrNull() ?: Memory()

    private fun write(context: Context, memory: Memory) {
        runCatching {
            // write-then-rename so a crash mid-write never leaves half a list
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            temp.writeText(json.encodeToString(memory))
            if (!temp.renameTo(file(context))) {
                file(context).writeText(json.encodeToString(memory))
                temp.delete()
            }
        }
    }
}
