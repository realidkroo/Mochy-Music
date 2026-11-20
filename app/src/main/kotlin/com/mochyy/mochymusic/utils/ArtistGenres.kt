// each artist's genre as itunes names it, kept on disk and shared by stats and the library's genres tab so both agree

package com.mochyy.mochymusic.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object ArtistGenres {
    // the old stats-only name, kept so nobody's earlier answers get asked again
    private const val CacheFileName = "recap_artist_genres.json"

    // itunes allows about 20 searches a minute, so the rest wait for the next visit
    private const val MaxLookupsPerCall = 12

    private val mutex = Mutex()
    private val cache = MutableStateFlow<Map<String, String>?>(null)

    // null until the file is first read; only real answers are kept, a miss is asked again next session
    val known: StateFlow<Map<String, String>?> = cache.asStateFlow()

    fun keyOf(name: String) = name.trim().lowercase()

    suspend fun load(context: Context): Map<String, String> = withContext(Dispatchers.IO) { mutex.withLock { loaded(context) } }

    // asks itunes about the artists it doesn't know yet, in the order given, and returns everything known after
    suspend fun lookUp(context: Context, artistNames: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val known = HashMap(loaded(context))
            var asked = 0
            for (name in artistNames) {
                val key = keyOf(name)
                if (key.isEmpty() || key in known) continue
                if (asked >= MaxLookupsPerCall) break
                if (asked > 0) delay(220)
                asked++
                known[key] = ListeningGenres.itunesGenre(name) ?: continue
            }
            if (known.size != cache.value?.size) {
                writeFile(context, known)
                cache.value = known
            }
            known
        }
    }

    private fun loaded(context: Context): Map<String, String> = cache.value ?: readFile(context).also { cache.value = it }

    private fun readFile(context: Context): Map<String, String> = try {
        val file = File(context.filesDir, CacheFileName)
        if (!file.exists()) {
            emptyMap()
        } else {
            val json = JSONObject(file.readText())
            HashMap<String, String>().apply { json.keys().forEach { k -> json.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } } }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun writeFile(context: Context, map: Map<String, String>) {
        runCatching {
            val json = JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            File(context.filesDir, CacheFileName).writeText(json.toString())
        }
    }
}
