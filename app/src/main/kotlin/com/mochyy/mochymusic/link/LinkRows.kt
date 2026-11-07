// LinkRows.kt

package com.mochyy.mochymusic.link

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

// database rows as plain json and back, by column name, so two app versions with slightly different tables still line up
internal object LinkRows {
    fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndex("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    fun read(db: SupportSQLiteDatabase, sql: String, args: Array<Any?> = emptyArray()): JSONArray =
        db.query(sql, args).use(::toJson)

    fun toJson(cursor: Cursor): JSONArray {
        val out = JSONArray()
        while (cursor.moveToNext()) {
            val row = JSONObject()
            for (i in 0 until cursor.columnCount) {
                when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> row.put(cursor.getColumnName(i), JSONObject.NULL)
                    Cursor.FIELD_TYPE_INTEGER -> row.put(cursor.getColumnName(i), cursor.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> row.put(cursor.getColumnName(i), cursor.getDouble(i))
                    Cursor.FIELD_TYPE_STRING -> row.put(cursor.getColumnName(i), cursor.getString(i))
                    // nothing the link carries is stored as a blob
                    else -> Unit
                }
            }
            out.put(row)
        }
        return out
    }

    // writes json rows into a table, keeping only the columns this side has; skip lists columns the caller fills itself
    fun write(db: SupportSQLiteDatabase, table: String, rows: JSONArray?, conflict: Int, skip: Set<String> = emptySet()) {
        rows ?: return
        val columns = columnsOf(db, table)
        if (columns.isEmpty()) return
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val values = ContentValues()
            for (name in row.keys()) {
                if (name !in columns || name in skip) continue
                when (val value = row.opt(name)) {
                    null, JSONObject.NULL -> values.putNull(name)
                    is Int -> values.put(name, value.toLong())
                    is Long -> values.put(name, value)
                    is Double -> values.put(name, value)
                    is Boolean -> values.put(name, if (value) 1L else 0L)
                    else -> values.put(name, value.toString())
                }
            }
            if (values.size() > 0) runCatching { db.insert(table, conflict, values) }
        }
    }

    // every real table in a database, without sqlite's and room's own bookkeeping
    fun tablesOf(db: SQLiteDatabase): Set<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot(::isBookkeeping).toSet()

    fun tablesOf(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot(::isBookkeeping).toSet()

    private fun isBookkeeping(name: String) =
        name.startsWith("sqlite_") || name.startsWith("room_") || name == "android_metadata"

    // a list of ids as "?, ?, ?" for an IN (...) clause
    fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")
}
