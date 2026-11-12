// TvProfiles.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.mochyy.mochymusic.db.InternalDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// the phone a profile mirrors, with what it takes to reach it again without asking anyone
@Immutable
data class LinkedPhone(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val key: String,
    val pairedAt: Long,
    val lastSyncAt: Long = 0L,
)

// one person on the tv: their own library database, and the phone it comes from when they linked one
@Immutable
data class TvProfile(
    val id: String,
    val name: String,
    val avatarPath: String?,
    val dbName: String,
    val phone: LinkedPhone?,
    val createdAt: Long,
    // the phone picture the avatar came from: null never asked, empty the phone has none, else the phone's stamp for it
    val phoneAvatar: String? = null,
)

// the tv's profiles, kept in plain shared preferences so they can be read before room opens anything
object TvProfiles {
    private const val Prefs = "tv_profiles"
    private const val ListKey = "profiles"
    private const val ActiveKey = "active"
    private const val PickedKey = "picked_before_restart"

    private val _profiles = MutableStateFlow<List<TvProfile>>(emptyList())
    val profiles: StateFlow<List<TvProfile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    // the database name this process opened, so a pick of another profile knows it has to start over
    @Volatile private var openedDbName: String? = null
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            _profiles.value = parse(prefs.getString(ListKey, null))
            _activeId.value = prefs.getString(ActiveKey, null)?.takeIf { id -> _profiles.value.any { it.id == id } }
            loaded = true
        }
    }

    // called by hilt while it builds the database, before anything else reads the library
    fun databaseName(context: Context): String {
        load(context)
        val name = active()?.dbName ?: InternalDatabase.DB_NAME
        openedDbName = name
        return name
    }

    fun active(): TvProfile? = _profiles.value.firstOrNull { it.id == _activeId.value }

    fun byId(id: String): TvProfile? = _profiles.value.firstOrNull { it.id == id }

    fun byPhone(phoneId: String): TvProfile? = _profiles.value.firstOrNull { it.phone?.id == phoneId }

    // picking a profile whose library isn't the one open means the app starts over into it
    fun needsRestartFor(id: String): Boolean {
        val profile = byId(id) ?: return false
        return profile.dbName != (openedDbName ?: InternalDatabase.DB_NAME)
    }

    // the first profile takes the database the tv already had, so nothing played before is lost
    fun create(context: Context, name: String, avatarPath: String?, phone: LinkedPhone?): TvProfile {
        load(context)
        val id = UUID.randomUUID().toString().take(8)
        val dbTaken = _profiles.value.any { it.dbName == InternalDatabase.DB_NAME }
        val profile = TvProfile(
            id = id,
            name = name.trim().ifBlank { "You" },
            avatarPath = avatarPath,
            dbName = if (dbTaken) "mochy_profile_$id.db" else InternalDatabase.DB_NAME,
            phone = phone,
            createdAt = System.currentTimeMillis(),
        )
        save(context, _profiles.value + profile)
        return profile
    }

    fun update(context: Context, id: String, change: (TvProfile) -> TvProfile) {
        load(context)
        save(context, _profiles.value.map { if (it.id == id) change(it) else it })
    }

    fun setActive(context: Context, id: String) {
        load(context)
        _activeId.value = id
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit(commit = true) { putString(ActiveKey, id) }
    }

    // a profile picked right before a restart shouldn't be asked "who's listening" again on the way back
    fun markPickedBeforeRestart(context: Context) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit(commit = true) { putBoolean(PickedKey, true) }
    }

    fun takePickedBeforeRestart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val picked = prefs.getBoolean(PickedKey, false)
        if (picked) prefs.edit(commit = true) { remove(PickedKey) }
        return picked
    }

    // removes a profile that isn't open, with its library and everything its phone sent over
    fun remove(context: Context, id: String) {
        load(context)
        val profile = byId(id) ?: return
        if (profile.id == _activeId.value) return
        save(context, _profiles.value.filterNot { it.id == id })
        listOf("", "-wal", "-shm", "-journal").forEach { context.getDatabasePath(profile.dbName + it).delete() }
        folder(context, id).deleteRecursively()
    }

    // where a profile keeps its avatar, its phone's file list and the songs copied from the phone
    fun folder(context: Context, id: String): File = File(context.filesDir, "tv_profiles/$id").apply { mkdirs() }

    private fun save(context: Context, list: List<TvProfile>) {
        _profiles.value = list
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit(commit = true) {
            putString(ListKey, JSONArray(list.map(::toJson)).toString())
        }
    }

    private fun parse(text: String?): List<TvProfile> {
        val array = runCatching { JSONArray(text ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val phone = json.optJSONObject("phone")?.let { p ->
                // sealed by this tv's vault; a key from another tv's backup can't be opened, so the phone pairs again
                val key = com.mochyy.mochymusic.utils.SecretVault.open(p.optString("key"))?.ifBlank { null } ?: return@let null
                LinkedPhone(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    host = p.optString("host"),
                    port = p.optInt("port"),
                    key = key,
                    pairedAt = p.optLong("pairedAt"),
                    lastSyncAt = p.optLong("lastSyncAt"),
                )
            }
            TvProfile(
                id = json.optString("id").ifBlank { return@mapNotNull null },
                name = json.optString("name"),
                avatarPath = json.optString("avatar").takeIf { it.isNotBlank() },
                dbName = json.optString("db").ifBlank { InternalDatabase.DB_NAME },
                phone = phone,
                createdAt = json.optLong("createdAt"),
                phoneAvatar = json.opt("phoneAvatar") as? String,
            )
        }
    }

    private fun toJson(profile: TvProfile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("avatar", profile.avatarPath.orEmpty())
        .put("db", profile.dbName)
        .put("createdAt", profile.createdAt)
        .put("phoneAvatar", profile.phoneAvatar)
        .apply {
            profile.phone?.let { phone ->
                put(
                    "phone",
                    JSONObject()
                        .put("id", phone.id)
                        .put("name", phone.name)
                        .put("host", phone.host)
                        .put("port", phone.port)
                        .put("key", com.mochyy.mochymusic.utils.SecretVault.seal(phone.key))
                        .put("pairedAt", phone.pairedAt)
                        .put("lastSyncAt", phone.lastSyncAt),
                )
            }
        }
}
