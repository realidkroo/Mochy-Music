// TransferArchive.kt

package com.mochyy.mochymusic.importer.transfer

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mochyy.mochymusic.constants.AccountChannelHandleKey
import com.mochyy.mochymusic.constants.AccountEmailKey
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.DataSyncIdKey
import com.mochyy.mochymusic.constants.DiscordAccessTokenKey
import com.mochyy.mochymusic.constants.DiscordLoginStateKey
import com.mochyy.mochymusic.constants.DiscordLoginVerifierKey
import com.mochyy.mochymusic.constants.DiscordProfileKey
import com.mochyy.mochymusic.constants.DiscordRefreshTokenKey
import com.mochyy.mochymusic.constants.DiscordTokenExpiresAtKey
import com.mochyy.mochymusic.constants.EnableDiscordRPCKey
import com.mochyy.mochymusic.constants.EnableLastFMScrobblingKey
import com.mochyy.mochymusic.constants.EnableLibreFmScrobblingKey
import com.mochyy.mochymusic.constants.InnerTubeCookieKey
import com.mochyy.mochymusic.constants.LastFMSessionKey
import com.mochyy.mochymusic.constants.LastFMUsernameKey
import com.mochyy.mochymusic.constants.LibreFmSessionKey
import com.mochyy.mochymusic.constants.LibreFmUsernameKey
import com.mochyy.mochymusic.constants.MonochromePlaybackApiTokenKey
import com.mochyy.mochymusic.constants.TurnstileBypassTokenKey
import com.mochyy.mochymusic.constants.VisitorDataKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** the layout of a transfer zip, written by the export and read back by the import, so both sides agree on every name */
internal object TransferArchive {
    const val Format = "mochy-transfer"
    const val FormatVersion = 1

    // the manifest is always the first entry, so the import can check the zip before unpacking gigabytes
    const val ManifestEntry = "manifest.json"
    const val LibraryEntry = "database/library.db"
    const val MediaEntry = "database/media.db"
    const val SettingsEntry = "settings/settings.json"
    const val PrefsPrefix = "settings/prefs/"
    const val LocalSongsEntry = "local_songs.json"
    const val FilesPrefix = "files/"

    // media3's own database, it holds the list of downloads and the index of the download folder
    const val MediaDatabaseName = "exoplayer_internal.db"

    // the app's own shared preferences worth keeping (audio lab, eq profiles, the home algorithm, hidden songs, source picks, ui scale),
    // by the name they travel under in the zip and the file name on this phone, so a renamed file still lands in the right place
    val PrefsToCarry = mapOf(
        "audio_lab" to "audio_lab",
        "nanosonic_eq_profiles" to "nanosonic_eq_profiles",
        "listening_signals" to "listening_signals",
        "not_recommended" to com.mochyy.mochymusic.utils.NotRecommended.PREFS_NAME,
        "source_modules" to "source_modules",
        "display" to "mochy_settings",
    )

    // sign-ins and tokens stay behind, the new app signs in again instead of carrying them around in a zip
    val LoginKeys: Set<String> = setOf(
        InnerTubeCookieKey, VisitorDataKey, DataSyncIdKey, AccountNameKey, AccountEmailKey, AccountChannelHandleKey,
        DiscordAccessTokenKey, DiscordRefreshTokenKey, DiscordTokenExpiresAtKey, DiscordLoginStateKey,
        DiscordLoginVerifierKey, DiscordProfileKey, EnableDiscordRPCKey,
        LastFMSessionKey, LastFMUsernameKey, EnableLastFMScrobblingKey,
        LibreFmSessionKey, LibreFmUsernameKey, EnableLibreFmScrobblingKey,
        MonochromePlaybackApiTokenKey, TurnstileBypassTokenKey,
    ).map { it.name }.toSet()

    // top-level things in the files folder that stay behind: the streaming cache, settings (sent as json),
    // caches, crash notes, the saved queue (java objects tied to class names) and folder picks android won't hand over
    private val FilesLeftBehind = setOf(
        "exoplayer", "datastore", "cipher_cache", "local_music_folders.txt", "last_crash.txt",
        "persistent_queue.data", "persistent_automix.data", "persistent_player_state.data",
    )

    /** true for a top-level name in the files folder that goes into the zip */
    fun carriesFile(name: String): Boolean = name !in FilesLeftBehind &&
        !name.startsWith("changelog_cache_") &&
        !name.startsWith("profileinstaller") &&
        !name.endsWith(".tmp")

    /** settings as typed json, so ints stay ints and sets stay sets on the way back in */
    fun preferencesToJson(preferences: Preferences, skip: Set<String>): JSONObject {
        val out = JSONObject()
        for ((key, value) in preferences.asMap()) {
            if (key.name in skip) continue
            out.put(key.name, typedValue(value) ?: continue)
        }
        return out
    }

    /** shared preferences as typed json, the same shape as the settings */
    fun sharedPreferencesToJson(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            out.put(key, typedValue(value ?: continue) ?: continue)
        }
        return out
    }

    /** puts typed json back into settings, skipping logins in case an older zip still has them */
    fun jsonIntoPreferences(json: JSONObject, into: MutablePreferences) {
        for (name in json.keys()) {
            if (name in LoginKeys) continue
            val entry = json.optJSONObject(name) ?: continue
            val value = entry.opt("v") ?: continue
            when (entry.optString("t")) {
                "b" -> into[booleanPreferencesKey(name)] = value as? Boolean ?: continue
                "i" -> into[intPreferencesKey(name)] = (value as? Number)?.toInt() ?: continue
                "l" -> into[longPreferencesKey(name)] = (value as? Number)?.toLong() ?: continue
                "f" -> into[floatPreferencesKey(name)] = (value as? Number)?.toFloat() ?: continue
                "d" -> into[doublePreferencesKey(name)] = (value as? Number)?.toDouble() ?: continue
                "s" -> into[stringPreferencesKey(name)] = value as? String ?: continue
                "ss" -> into[stringSetPreferencesKey(name)] = (value as? JSONArray)?.strings() ?: continue
                "bytes" -> into[byteArrayPreferencesKey(name)] = Base64.decode(value as? String ?: continue, Base64.NO_WRAP)
            }
        }
    }

    /** puts typed json back into shared preferences */
    fun jsonIntoSharedPreferences(json: JSONObject, into: SharedPreferences.Editor) {
        for (name in json.keys()) {
            val entry = json.optJSONObject(name) ?: continue
            val value = entry.opt("v") ?: continue
            when (entry.optString("t")) {
                "b" -> into.putBoolean(name, value as? Boolean ?: continue)
                "i" -> into.putInt(name, (value as? Number)?.toInt() ?: continue)
                "l" -> into.putLong(name, (value as? Number)?.toLong() ?: continue)
                "f", "d" -> into.putFloat(name, (value as? Number)?.toFloat() ?: continue)
                "s" -> into.putString(name, value as? String ?: continue)
                "ss" -> into.putStringSet(name, (value as? JSONArray)?.strings() ?: continue)
            }
        }
    }

    /** runs every text value in typed json through change, used to point saved file paths at the new app */
    fun mapStrings(json: JSONObject, change: (String) -> String): JSONObject {
        for (name in json.keys()) {
            val entry = json.optJSONObject(name) ?: continue
            when (entry.optString("t")) {
                "s" -> entry.put("v", change(entry.optString("v")))
                "ss" -> entry.put("v", JSONArray(entry.optJSONArray("v")?.strings().orEmpty().map(change)))
            }
        }
        return json
    }

    private fun typedValue(value: Any): JSONObject? {
        val (type, encoded) = when (value) {
            is Boolean -> "b" to value
            is Int -> "i" to value
            is Long -> "l" to value
            is Float -> "f" to value.toDouble()
            is Double -> "d" to value
            is String -> "s" to value
            is Set<*> -> "ss" to JSONArray(value.filterIsInstance<String>())
            is ByteArray -> "bytes" to Base64.encodeToString(value, Base64.NO_WRAP)
            else -> return null
        }
        return JSONObject().put("t", type).put("v", encoded)
    }

    private fun JSONArray.strings(): Set<String> = (0 until length()).mapNotNull { optString(it, null) }.toSet()
}

/** turns a copied wal-mode database into one plain file, so it can be zipped or moved on its own without losing the newest changes */
internal fun foldIntoOneFile(db: SQLiteDatabase) {
    val mode = db.rawQuery("PRAGMA journal_mode=DELETE", null).use { if (it.moveToFirst()) it.getString(0) else null }
    if (mode.equals("delete", ignoreCase = true)) return
    // android kept it in wal mode, so push everything from the wal into the main file instead
    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
        check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "couldn't fold the wal into ${db.path}" }
    }
}

// copies a live database while holding its write lock, so nothing lands halfway, then folds its wal into one plain file
internal fun snapshotDatabase(source: File, target: File, begin: () -> Unit, end: () -> Unit) {
    val targetWal = File(target.path + "-wal")
    target.delete()
    targetWal.delete()
    begin()
    try {
        source.copyTo(target, overwrite = true)
        File(source.path + "-wal").takeIf { it.exists() }?.copyTo(targetWal, overwrite = true)
    } finally {
        end()
    }
    SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
        foldIntoOneFile(copy)
        copy.rawQuery("PRAGMA quick_check", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) { "snapshot of ${source.name} failed its check" }
        }
    }
    listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
}

/** what the manifest says about a zip, enough to check it and to tell the user what's inside */
data class TransferManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appId: String,
    val appVersion: String,
    val dataDir: String,
    val databaseVersion: Int,
    val songs: Int,
    val playlists: Int,
    val plays: Int,
    val localSongs: Int,
    val bytes: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("format", TransferArchive.Format)
        .put("version", formatVersion)
        .put("createdAt", createdAt)
        .put("appId", appId)
        .put("appVersion", appVersion)
        .put("dataDir", dataDir)
        .put("databaseVersion", databaseVersion)
        .put("songs", songs)
        .put("playlists", playlists)
        .put("plays", plays)
        .put("localSongs", localSongs)
        .put("bytes", bytes)

    companion object {
        /** null when the json isn't a transfer manifest at all */
        fun fromJson(json: JSONObject): TransferManifest? {
            if (json.optString("format") != TransferArchive.Format) return null
            return TransferManifest(
                formatVersion = json.optInt("version"),
                createdAt = json.optLong("createdAt"),
                appId = json.optString("appId"),
                appVersion = json.optString("appVersion"),
                dataDir = json.optString("dataDir"),
                databaseVersion = json.optInt("databaseVersion"),
                songs = json.optInt("songs"),
                playlists = json.optInt("playlists"),
                plays = json.optInt("plays"),
                localSongs = json.optInt("localSongs"),
                bytes = json.optLong("bytes"),
            )
        }
    }
}
