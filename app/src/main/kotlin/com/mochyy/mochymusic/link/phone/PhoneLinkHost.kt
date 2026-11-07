// PhoneLinkHost.kt

package com.mochyy.mochymusic.link.phone

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.constants.UsernameKey
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.MusicDatabaseVersion
import com.mochyy.mochymusic.di.ApplicationScope
import com.mochyy.mochymusic.link.LinkAdvert
import com.mochyy.mochymusic.link.LinkHttpServer
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// a tv this phone is paired with; the key is what the tv shows on every call
@Immutable
data class PairedTv(val id: String, val name: String, val key: String, val pairedAt: Long, val lastSeenAt: Long)

// the phone's half of the tv link: remembers paired tvs and keeps a small server up so they can sync whenever they like
@Singleton
class PhoneLinkHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val internalDatabase: InternalDatabase,
    private val downloadUtil: DownloadUtil,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
    private val _tvs = MutableStateFlow(readTvs())
    val tvs: StateFlow<List<PairedTv>> = _tvs.asStateFlow()

    private val server = LinkHttpServer("PhoneLink", ::route)
    private var advert: LinkAdvert? = null

    // goes up whenever the library changes, so a tv only pulls a fresh copy when there is one
    private val revision = AtomicLong(System.currentTimeMillis())
    private var watchingLibrary = false

    private val library = PhoneLibraryRoutes(context, database, internalDatabase, downloadUtil)

    val isRunning: Boolean get() = server.isRunning

    // keeps the server up while any tv is paired, quiet otherwise
    @Synchronized
    fun refresh() {
        if (_tvs.value.isEmpty()) {
            stop()
            return
        }
        if (server.isRunning) return
        watchLibrary()
        val port = server.start(LinkWire.PhonePort)
        advert = LinkAdvert(
            context,
            LinkWire.PhoneServiceType,
            "Mochy ${LinkIdentity.name(context)}",
            port,
            mapOf(LinkWire.AttrId to LinkIdentity.id(context), LinkWire.AttrName to LinkIdentity.name(context)),
        )
    }

    @Synchronized
    fun stop() {
        advert?.close()
        advert = null
        server.stop()
    }

    // asks the tv to take this phone, with the code its screen shows; the key made here is shared by both from now on
    suspend fun pair(tv: LinkPeer, code: String): Result<PairedTv> = withContext(Dispatchers.IO) {
        runCatching {
            val port = server.start(LinkWire.PhonePort)
            val key = LinkWire.newKey()
            val profile = context.dataStore.data.first()
            val body = JSONObject()
                .put("code", code.trim())
                .put("phoneId", LinkIdentity.id(context))
                .put("phoneName", LinkIdentity.name(context))
                .put("userName", profile[UsernameKey].orEmpty())
                .put("hasAvatar", !profile[ProfilePicUriKey].isNullOrBlank())
                .put("port", port)
                .put("key", key)
                .put("protocol", LinkWire.ProtocolVersion)
            LinkWire.postJson("${LinkWire.baseUrl(tv.host, tv.port)}/pair", null, body.toString()).use { response ->
                when (response.code) {
                    200 -> Unit
                    403 -> throw PairException(PairException.Reason.WrongCode)
                    else -> throw PairException(PairException.Reason.Failed)
                }
                val reply = JSONObject(response.body.string())
                val paired = PairedTv(
                    id = reply.optString("tvId", tv.id),
                    name = reply.optString("tvName", tv.name),
                    key = key,
                    pairedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                )
                saveTvs(_tvs.value.filterNot { it.id == paired.id } + paired)
                paired
            }
        }.onFailure {
            if (_tvs.value.isEmpty()) stop()
            Timber.tag("Link").w(it, "pairing with ${tv.name} failed")
        }.also { refresh() }
    }

    fun unlink(tvId: String) {
        saveTvs(_tvs.value.filterNot { it.id == tvId })
        refresh()
        if (_tvs.value.isEmpty()) PhoneLinkService.stop(context)
    }

    private fun route(request: LinkRequest): LinkResponse {
        val tv = _tvs.value.firstOrNull { it.key == request.key }
            ?: return LinkResponse.status(401, "not paired")
        seen(tv)
        return when {
            request.path == "/hello" -> LinkResponse.json(hello().toString())
            request.path == "/avatar" -> avatar()
            request.path == "/library" -> library.snapshot()
            request.path == "/files" -> LinkResponse.json(library.files().toString())
            request.path.startsWith("/file/") -> library.file(request, request.path.removePrefix("/file/"))
            request.path == "/changes" && request.method == "POST" -> {
                library.applyChanges(JSONObject(request.bodyText()))
                revision.incrementAndGet()
                LinkResponse.json("{}")
            }
            request.path == "/unlink" && request.method == "POST" -> {
                unlink(tv.id)
                LinkResponse.json("{}")
            }
            else -> LinkResponse.status(404)
        }
    }

    private fun hello(): JSONObject {
        val profile = runBlocking { context.dataStore.data.first() }
        return JSONObject()
            .put("id", LinkIdentity.id(context))
            .put("name", LinkIdentity.name(context))
            .put("user", profile[UsernameKey].orEmpty())
            .put("protocol", LinkWire.ProtocolVersion)
            .put("dbVersion", MusicDatabaseVersion)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("rev", revision.get())
    }

    private fun avatar(): LinkResponse {
        val path = runBlocking { context.dataStore.data.first()[ProfilePicUriKey] }.orEmpty()
        val file = File(path.removePrefix("file://")).takeIf { path.isNotBlank() && it.isFile }
            ?: return LinkResponse.status(404)
        return LinkResponse.bytes(file.readBytes(), "image/jpeg")
    }

    // only the tables a tv shows count, a new lyric or format alone isn't worth a whole sync
    private fun watchLibrary() {
        if (watchingLibrary) return
        watchingLibrary = true
        internalDatabase.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                arrayOf("song", "artist", "album", "playlist", "playlist_song_map", "event"),
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    revision.incrementAndGet()
                }
            },
        )
    }

    // written at most once a minute, a tv calls every few seconds while it's open
    private fun seen(tv: PairedTv) {
        val now = System.currentTimeMillis()
        if (now - tv.lastSeenAt < 60_000) return
        saveTvs(_tvs.value.map { if (it.id == tv.id) it.copy(lastSeenAt = now) else it })
    }

    private fun readTvs(): List<PairedTv> {
        val array = runCatching { JSONArray(prefs.getString(TvsKey, null) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            PairedTv(
                id = json.optString("id"),
                name = json.optString("name"),
                key = json.optString("key").ifBlank { return@mapNotNull null },
                pairedAt = json.optLong("pairedAt"),
                lastSeenAt = json.optLong("lastSeenAt"),
            )
        }
    }

    private fun saveTvs(list: List<PairedTv>) {
        _tvs.value = list
        val array = JSONArray(
            list.map {
                JSONObject().put("id", it.id).put("name", it.name).put("key", it.key)
                    .put("pairedAt", it.pairedAt).put("lastSeenAt", it.lastSeenAt)
            },
        )
        prefs.edit { putString(TvsKey, array.toString()) }
    }

    // the background switch lives here too, so the boot receiver can read it without the settings store
    var staysReachable: Boolean
        get() = prefs.getBoolean(StayKey, true)
        set(value) {
            prefs.edit { putBoolean(StayKey, value) }
            if (value) PhoneLinkService.start(context) else PhoneLinkService.stop(context)
        }

    companion object {
        private const val Prefs = "mochy_link_tvs"
        private const val TvsKey = "tvs"
        private const val StayKey = "stay_reachable"

        // read without hilt by the boot receiver
        fun hasPairedTvs(context: Context): Boolean {
            val saved = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(TvsKey, null)
            return !saved.isNullOrBlank() && saved != "[]"
        }

        fun staysReachable(context: Context): Boolean =
            context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(StayKey, true)
    }
}

// why a pairing didn't go through, so the phone can say the right thing
class PairException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { WrongCode, Failed }
}
