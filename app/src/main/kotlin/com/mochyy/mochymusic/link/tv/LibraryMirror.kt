// LibraryMirror.kt

package com.mochyy.mochymusic.link.tv

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mochyy.mochymusic.link.LinkRows
import timber.log.Timber
import java.io.File

// turns the tv profile's library into the phone's: every table the phone sent replaces the tv's copy, in one go
internal object LibraryMirror {
    // the tv keeps its own searches, typed with a remote they're nothing like the phone's
    private val KeptOnTv = setOf("search_history")

    // copied first, so the maps and plays that point at them always find their row
    private val ParentsFirst = listOf("song", "artist", "album", "playlist")

    fun apply(
        live: SupportSQLiteDatabase,
        snapshot: File,
        copiedToTv: Map<String, String>,
        downloadedOnTv: Set<String>,
    ) {
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { phone ->
            val tables = (LinkRows.tablesOf(phone) intersect LinkRows.tablesOf(live)) - KeptOnTv
            val ordered = ParentsFirst.filter { it in tables } + (tables - ParentsFirst.toSet()).sorted()
            live.beginTransaction()
            try {
                // the old rows go before the new ones arrive, checked once at the end instead of row by row
                live.execSQL("PRAGMA defer_foreign_keys = ON")
                ordered.asReversed().forEach { live.execSQL("DELETE FROM `$it`") }
                ordered.forEach { copyTable(phone, live, it) }
                // a library restored from a backup can hold plays or playlist rows whose song is gone, and one of them failed the whole commit
                dropDangling(live)
                // the phone's own song files only open on the phone; a copy that came over to the tv takes their place
                live.execSQL("UPDATE song SET localUri = NULL WHERE localUri IS NOT NULL")
                copiedToTv.forEach { (id, path) ->
                    live.execSQL("UPDATE song SET localUri = ? WHERE id = ?", arrayOf<Any?>("file://$path", id))
                }
                // songs downloaded to the tv itself stay marked as downloaded even when the phone never had them
                val now = System.currentTimeMillis()
                downloadedOnTv.chunked(400).forEach { chunk ->
                    live.execSQL(
                        "UPDATE song SET dateDownload = COALESCE(dateDownload, ?) WHERE id IN (${LinkRows.placeholders(chunk.size)})",
                        (listOf<Any?>(now) + chunk).toTypedArray(),
                    )
                }
                live.setTransactionSuccessful()
            } finally {
                live.endTransaction()
            }
        }
    }

    // rows that point at a song, album or playlist that isn't there, as sqlite itself finds them, are left out
    private fun dropDangling(live: SupportSQLiteDatabase) {
        val dangling = live.query("PRAGMA foreign_key_check").use { cursor ->
            buildSet { while (cursor.moveToNext()) if (!cursor.isNull(1)) add(cursor.getString(0) to cursor.getLong(1)) }
        }
        if (dangling.isEmpty()) return
        Timber.tag("Link").i("left out ${dangling.size} rows from the phone that point at nothing")
        dangling.forEach { (table, rowId) -> live.execSQL("DELETE FROM `$table` WHERE rowid = ?", arrayOf<Any?>(rowId)) }
    }

    private fun copyTable(phone: SQLiteDatabase, live: SupportSQLiteDatabase, table: String) {
        val wanted = LinkRows.columnsOf(live, table)
        phone.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
            val columns = (0 until cursor.columnCount).filter { cursor.getColumnName(it) in wanted }
            if (columns.isEmpty()) return
            val values = ContentValues(columns.size)
            while (cursor.moveToNext()) {
                values.clear()
                for (index in columns) {
                    val name = cursor.getColumnName(index)
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                        Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(index))
                        Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(index))
                        else -> values.put(name, cursor.getString(index))
                    }
                }
                live.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        }
    }
}
