// LostLikes.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** brings back likes a sync took off by mistake, from every record the app keeps of a like */
object LostLikes {
    // sqlite on older phones takes at most 999 values in one query
    private const val Chunk = 500

    suspend fun count(context: Context, database: MusicDatabase): Int = withContext(Dispatchers.IO) {
        missing(database, wanted(context, database)).size
    }

    // only on this phone, youtube is left alone; returns how many came back
    suspend fun bringBack(context: Context, database: MusicDatabase): Int = withContext(Dispatchers.IO) {
        YouTubeLikesMemory.exclusive {
            val dates = wanted(context, database)
            val songs = missing(database, dates)
            if (songs.isNotEmpty()) {
                val now = LocalDateTime.now()
                database.withTransaction {
                    songs.forEach { update(it.copy(liked = true, likedDate = dates[it.id] ?: now)) }
                }
            }
            YouTubeLikesMemory.forget(context, songs.map { it.id })
            songs.size
        }
    }

    // each later record knows the like date better, so it wins over the one before
    private fun wanted(context: Context, database: MusicDatabase): Map<String, LocalDateTime?> {
        val wanted = HashMap<String, LocalDateTime?>()
        // an import doesn't keep the date each song was liked on the other app, the day it ran is the closest we have
        database.likesFromImports().forEach { wanted[it.songId] = it.importedAt }
        SyncUndo.likedBefore(context).forEach { (id, date) -> if (date != null || id !in wanted) wanted[id] = date }
        YouTubeLikesMemory.removed(context).forEach { (id, date) -> if (date != null || id !in wanted) wanted[id] = date }
        return wanted
    }

    private fun missing(database: MusicDatabase, wanted: Map<String, LocalDateTime?>): List<SongEntity> =
        wanted.keys.chunked(Chunk).flatMap { database.unlikedSongEntities(it) }
}
