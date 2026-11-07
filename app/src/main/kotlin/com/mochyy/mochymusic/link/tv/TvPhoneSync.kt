// TvPhoneSync.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.link.LinkRows
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.link.PhoneFile
import com.mochyy.mochymusic.link.PhoneFiles
import com.mochyy.mochymusic.link.discoverPeers
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.ExoDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

// where the tv's link with the active profile's phone stands, for the phone page and the profile picker
@Immutable
data class PhoneSyncState(
    val phase: Phase = Phase.NoPhone,
    val phoneName: String = "",
    val lastSyncAt: Long = 0L,
    val transferring: Int = 0,
) {
    enum class Phase { NoPhone, Looking, Syncing, Synced, Away, Unlinked, Failed }
}

// keeps the active profile's library in step with its phone while the tv app is open
@Singleton
class TvPhoneSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val internalDatabase: InternalDatabase,
    private val downloadUtil: DownloadUtil,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncing = Mutex()
    private var loop: Job? = null
    private var lastRevision: Long? = null

    private val _state = MutableStateFlow(PhoneSyncState())
    val state: StateFlow<PhoneSyncState> = _state.asStateFlow()

    // starts checking on the phone, called while the tv app is in front
    fun resume() {
        if (loop?.isActive == true) return
        val profile = TvProfiles.active()
        val phone = profile?.phone
        if (profile == null || phone == null) {
            PhoneFiles.clear()
            _state.value = PhoneSyncState()
            return
        }
        // the last list the phone sent shows which songs live on it even before it answers
        PhoneFiles.update(readFiles(profile.id))
        _state.update { it.copy(phoneName = phone.name, lastSyncAt = phone.lastSyncAt) }
        loop = scope.launch {
            var first = true
            while (isActive) {
                check(forceSync = first)
                first = false
                delay(HeartbeatMs)
            }
        }
    }

    fun pause() {
        loop?.cancel()
        loop = null
    }

    fun syncNow() {
        scope.launch { check(forceSync = true) }
    }

    // says goodbye to the phone when it's around, then keeps the library on the tv as the profile's own
    fun unlink() {
        val profile = TvProfiles.active() ?: return
        val phone = profile.phone ?: return
        pause()
        scope.launch {
            runCatching { LinkWire.postJson("${LinkWire.baseUrl(phone.host, phone.port)}/unlink", phone.key, "{}").close() }
            TvProfiles.update(context, profile.id) { it.copy(phone = null) }
            PhoneFiles.clear()
            _state.value = PhoneSyncState()
        }
    }

    // one heartbeat: say hello, and pull the library again when it changed and the last pull is old enough
    private suspend fun check(forceSync: Boolean) {
        val profile = TvProfiles.active() ?: return
        val phone = profile.phone ?: return
        if (_state.value.phase != PhoneSyncState.Phase.Synced && _state.value.phase != PhoneSyncState.Phase.Syncing) {
            _state.update { it.copy(phase = PhoneSyncState.Phase.Looking) }
        }
        val hello = reach(profile.id, phone) ?: return
        val revision = hello.optLong("rev")
        val stale = System.currentTimeMillis() - (TvProfiles.byId(profile.id)?.phone?.lastSyncAt ?: 0L) > AutoSyncGapMs
        if (forceSync || (revision != lastRevision && stale)) {
            sync(profile.id)
            lastRevision = revision
        } else if (_state.value.phase == PhoneSyncState.Phase.Looking || _state.value.phase == PhoneSyncState.Phase.Away) {
            _state.update { it.copy(phase = PhoneSyncState.Phase.Synced) }
        }
    }

    // the phone at its last address, or wherever nsd says it went; null when it's away
    private suspend fun reach(profileId: String, phone: LinkedPhone): JSONObject? {
        hello(phone)?.let { return it }
        val found = withTimeoutOrNull(LookForMs) {
            discoverPeers(context, LinkWire.PhoneServiceType).first { peers -> peers.any { it.id == phone.id } }
                .first { it.id == phone.id }
        }
        if (found != null) {
            val moved = phone.copy(host = found.host, port = found.port)
            TvProfiles.update(context, profileId) { it.copy(phone = moved) }
            hello(moved)?.let { return it }
        }
        if (_state.value.phase != PhoneSyncState.Phase.Unlinked) {
            PhoneFiles.lost()
            _state.update { it.copy(phase = PhoneSyncState.Phase.Away) }
        }
        return null
    }

    private fun hello(phone: LinkedPhone): JSONObject? {
        val base = LinkWire.baseUrl(phone.host, phone.port)
        return try {
            LinkWire.get("$base/hello", phone.key).use { response ->
                when (response.code) {
                    200 -> JSONObject(response.body.string()).also { PhoneFiles.reachedAt(base, phone.key) }
                    401 -> {
                        // the phone unlinked this tv on its side; the library stays here as the profile's own
                        TvProfiles.active()?.let { profile -> TvProfiles.update(context, profile.id) { it.copy(phone = null) } }
                        PhoneFiles.clear()
                        _state.update { it.copy(phase = PhoneSyncState.Phase.Unlinked) }
                        pause()
                        null
                    }
                    else -> null
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    // sends what happened on the tv, then takes the phone's whole library and its list of song files
    private suspend fun sync(profileId: String) = syncing.withLock {
        val phone = TvProfiles.byId(profileId)?.phone ?: return@withLock
        val base = LinkWire.baseUrl(phone.host, phone.port)
        _state.update { it.copy(phase = PhoneSyncState.Phase.Syncing) }
        val result = runCatching {
            pushChanges(profileId, base, phone.key)
            val snapshot = pullLibrary(base, phone.key)
            try {
                LibraryMirror.apply(
                    live = database.openHelper.writableDatabase,
                    snapshot = snapshot,
                    copiedToTv = readCopied(profileId),
                    downloadedOnTv = downloadedOnTv(),
                )
            } finally {
                snapshot.delete()
            }
            // written past room, so every open screen is told by hand
            internalDatabase.invalidationTracker.refreshVersionsAsync()
            saveBaseline(profileId)
            pullFiles(profileId, base, phone.key)
            if (TvProfiles.byId(profileId)?.avatarPath == null) fetchAvatar(profileId, phone)
        }
        result.onSuccess {
            val now = System.currentTimeMillis()
            TvProfiles.update(context, profileId) { profile -> profile.copy(phone = profile.phone?.copy(lastSyncAt = now)) }
            _state.update { it.copy(phase = PhoneSyncState.Phase.Synced, lastSyncAt = now) }
        }.onFailure {
            Timber.tag("Link").w(it, "sync with ${phone.name} failed")
            _state.update { state -> state.copy(phase = if (it is IOException) PhoneSyncState.Phase.Away else PhoneSyncState.Phase.Failed) }
        }
    }

    // plays since the last sync, and likes or library changes made on the tv, so the phone's own copy has them too
    private fun pushChanges(profileId: String, base: String, key: String) {
        val db = database.openHelper.readableDatabase
        val baseline = readBaseline(profileId)
        val plays = LinkRows.read(db, "SELECT * FROM event WHERE id > ?", arrayOf(baseline.lastEventId))
        val liked = idSet(db, "SELECT id FROM song WHERE liked = 1")
        val inLibrary = idSet(db, "SELECT id FROM song WHERE inLibrary IS NOT NULL")
        val newlyLiked = liked - baseline.liked
        val unliked = baseline.liked - liked
        val added = inLibrary - baseline.library
        val removed = baseline.library - inLibrary
        val playedIds = (0 until plays.length()).mapNotNull { plays.optJSONObject(it)?.optString("songId") }
        val songIds = (playedIds + newlyLiked + added).toSet()
        if (plays.length() == 0 && newlyLiked.isEmpty() && unliked.isEmpty() && added.isEmpty() && removed.isEmpty()) return
        val body = JSONObject()
            .put("rows", rowsFor(db, songIds))
            .put("plays", plays)
            .put("liked", JSONArray(newlyLiked))
            .put("unliked", JSONArray(unliked))
            .put("added", JSONArray(added))
            .put("removed", JSONArray(removed))
        LinkWire.postJson("$base/changes", key, body.toString()).use { response ->
            if (!response.isSuccessful) throw IOException("phone turned the changes away (${response.code})")
        }
    }

    // the song rows a change points at, with their artists and albums, so the phone can show them properly
    private fun rowsFor(db: androidx.sqlite.db.SupportSQLiteDatabase, songIds: Set<String>): JSONObject {
        val rows = JSONObject()
        val tables = mapOf(
            "song" to "SELECT * FROM song WHERE id IN (%s)",
            "song_artist_map" to "SELECT * FROM song_artist_map WHERE songId IN (%s)",
            "artist" to "SELECT * FROM artist WHERE id IN (SELECT artistId FROM song_artist_map WHERE songId IN (%s))",
            "song_album_map" to "SELECT * FROM song_album_map WHERE songId IN (%s)",
            "album" to "SELECT * FROM album WHERE id IN (SELECT albumId FROM song_album_map WHERE songId IN (%s))",
        )
        tables.forEach { (table, sql) ->
            val out = JSONArray()
            songIds.chunked(400).forEach { chunk ->
                val found = LinkRows.read(db, sql.format(LinkRows.placeholders(chunk.size)), chunk.toTypedArray())
                for (i in 0 until found.length()) out.put(found.get(i))
            }
            rows.put(table, out)
        }
        return rows
    }

    private fun pullLibrary(base: String, key: String): File {
        val target = File(context.cacheDir, "link_library_${System.nanoTime()}.db")
        LinkWire.get("$base/library", key).use { response ->
            if (!response.isSuccessful) throw IOException("phone didn't send its library (${response.code})")
            GZIPInputStream(response.body.byteStream(), 64 * 1024).use { input ->
                target.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
        }
        return target
    }

    private fun pullFiles(profileId: String, base: String, key: String) {
        val array = LinkWire.get("$base/files", key).use { response ->
            if (!response.isSuccessful) return
            JSONArray(response.body.string())
        }
        File(TvProfiles.folder(context, profileId), FilesName).writeText(array.toString())
        PhoneFiles.update(parseFiles(array))
    }

    private fun fetchAvatar(profileId: String, phone: LinkedPhone) {
        runCatching {
            LinkWire.get("${LinkWire.baseUrl(phone.host, phone.port)}/avatar", phone.key).use { response ->
                if (!response.isSuccessful) return
                // a fresh name each time, so the image cache never hands back an old picture
                val file = File(TvProfiles.folder(context, profileId), "avatar_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { response.body.byteStream().copyTo(it) }
                TvProfiles.update(context, profileId) { it.copy(avatarPath = file.absolutePath) }
            }
        }
    }

    // called right after pairing, while the phone is certainly awake
    fun fetchAvatarAfterPairing(profileId: String) {
        val phone = TvProfiles.byId(profileId)?.phone ?: return
        scope.launch { fetchAvatar(profileId, phone) }
    }

    // copies songs the phone has onto the tv: its downloads join the tv's downloads, its own files land in the profile's folder
    fun transfer(songIds: Collection<String>) {
        val profile = TvProfiles.active() ?: return
        val files = PhoneFiles.files.value
        val wanted = songIds.mapNotNull { files[it] }
        if (wanted.isEmpty()) return
        _state.update { it.copy(transferring = it.transferring + wanted.size) }
        scope.launch {
            for (file in wanted) {
                runCatching { if (file.isLocalFile) copyLocalFile(profile.id, file) else queueDownload(file.id) }
                    .onFailure { Timber.tag("Link").w(it, "couldn't copy ${file.id}") }
                _state.update { it.copy(transferring = (it.transferring - 1).coerceAtLeast(0)) }
            }
        }
    }

    private suspend fun queueDownload(songId: String) {
        val title = withContext(Dispatchers.IO) { database.getSongByIdBlocking(songId)?.song?.title }.orEmpty()
        val request = DownloadRequest.Builder(songId, songId.toUri())
            .setCustomCacheKey(songId)
            .setData(title.toByteArray())
            .build()
        withContext(Dispatchers.Main) { DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false) }
    }

    private fun copyLocalFile(profileId: String, file: PhoneFile) {
        val url = PhoneFiles.streamUrl(file.id) ?: throw IOException("phone is away")
        val extension = when {
            file.mime.contains("flac") -> "flac"
            file.mime.contains("mp4") || file.mime.contains("m4a") || file.mime.contains("aac") -> "m4a"
            file.mime.contains("ogg") || file.mime.contains("opus") -> "ogg"
            file.mime.contains("wav") -> "wav"
            else -> "mp3"
        }
        val folder = File(TvProfiles.folder(context, profileId), "songs").apply { mkdirs() }
        val target = File(folder, "${file.id.hashCode().toUInt()}.$extension")
        val partial = File(target.path + ".part")
        LinkWire.get(url, null).use { response ->
            if (!response.isSuccessful) throw IOException("phone didn't send ${file.id}")
            partial.outputStream().use { response.body.byteStream().copyTo(it, 64 * 1024) }
        }
        partial.renameTo(target)
        val copied = readCopied(profileId) + (file.id to target.absolutePath)
        File(TvProfiles.folder(context, profileId), CopiedName).writeText(JSONObject(copied).toString())
        database.openHelper.writableDatabase.execSQL("UPDATE song SET localUri = ? WHERE id = ?", arrayOf<Any?>("file://${target.absolutePath}", file.id))
        internalDatabase.invalidationTracker.refreshVersionsAsync()
    }

    // the songs on the tv itself, whether downloaded here or copied over from the phone
    fun onTv(): Set<String> {
        val profile = TvProfiles.active() ?: return downloadedOnTv()
        return downloadedOnTv() + readCopied(profile.id).keys
    }

    private fun downloadedOnTv(): Set<String> =
        downloadUtil.downloads.value.values.filter { it.state == Download.STATE_COMPLETED }.map { it.request.id }.toSet()

    private class Baseline(val lastEventId: Long, val liked: Set<String>, val library: Set<String>)

    // what the library looked like right after the last sync, so the next one knows what the tv changed since
    private fun saveBaseline(profileId: String) {
        val db = database.openHelper.readableDatabase
        val lastEvent = db.query("SELECT COALESCE(MAX(id), 0) FROM event").use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val json = JSONObject()
            .put("lastEventId", lastEvent)
            .put("liked", JSONArray(idSet(db, "SELECT id FROM song WHERE liked = 1")))
            .put("library", JSONArray(idSet(db, "SELECT id FROM song WHERE inLibrary IS NOT NULL")))
        File(TvProfiles.folder(context, profileId), BaselineName).writeText(json.toString())
    }

    // a profile that never synced sends everything it has, so nothing played on the tv before the link is lost
    private fun readBaseline(profileId: String): Baseline {
        val json = runCatching { JSONObject(File(TvProfiles.folder(context, profileId), BaselineName).readText()) }.getOrNull()
            ?: return Baseline(0L, emptySet(), emptySet())
        return Baseline(json.optLong("lastEventId"), json.optJSONArray("liked").toIdSet(), json.optJSONArray("library").toIdSet())
    }

    private fun readCopied(profileId: String): Map<String, String> {
        val json = runCatching { JSONObject(File(TvProfiles.folder(context, profileId), CopiedName).readText()) }.getOrNull()
            ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.optString(it) }.filterValues { File(it).exists() }
    }

    private fun readFiles(profileId: String): Map<String, PhoneFile> =
        runCatching { parseFiles(JSONArray(File(TvProfiles.folder(context, profileId), FilesName).readText())) }.getOrDefault(emptyMap())

    private fun parseFiles(array: JSONArray): Map<String, PhoneFile> = buildMap {
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val id = json.optString("id").ifBlank { continue }
            put(id, PhoneFile(id, json.optLong("bytes"), json.optString("mime"), json.optBoolean("local")))
        }
    }

    private fun idSet(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Set<String> =
        db.query(sql).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun JSONArray?.toIdSet(): Set<String> =
        this?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet() } ?: emptySet()

    companion object {
        private const val HeartbeatMs = 20_000L
        private const val LookForMs = 6_000L

        // a phone playing music changes its history every few minutes; the tv follows along, not after every song
        private const val AutoSyncGapMs = 5 * 60_000L

        private const val BaselineName = "baseline.json"
        private const val FilesName = "phone_files.json"
        private const val CopiedName = "copied.json"
    }
}
