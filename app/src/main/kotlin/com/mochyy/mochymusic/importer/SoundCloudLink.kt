// SoundCloudLink.kt

package com.mochyy.mochymusic.importer

import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.URLEncoder

/** reads a public soundcloud playlist or album through the api its own website uses, with the client id that website hands every visitor */
internal object SoundCloudLink {

    private const val API = "https://api-v2.soundcloud.com"
    private val SET_REGEX = Regex("soundcloud\\.com/([^/?#]+)/sets/([^/?#]+)")
    private val CLIENT_ID_REGEX = Regex("\"clientId\":\"([A-Za-z0-9]{16,})\"")

    // soundcloud only sends a playlist's first few songs whole, the rest are ids to look up in batches this big
    private const val TRACK_BATCH = 50

    // the id stays good for weeks, so one read per run is plenty; a 401 means it changed and it's read again
    @Volatile private var clientId: String? = null

    suspend fun fetch(url: String): ParsedImport {
        val match = SET_REGEX.find(url) ?: throw LinkImportException("Paste a SoundCloud playlist or album link.")
        val (user, set) = match.destructured
        val page = "https://soundcloud.com/$user/sets/$set"
        val playlist = api("$API/resolve?url=${URLEncoder.encode(page, "UTF-8")}") as? JsonObject
            ?: throw LinkImportException("SoundCloud sent something we couldn't read.")

        val name = playlist.string("title") ?: "SoundCloud playlist"
        val entries = playlist["tracks"].array()
        val isAlbum = (playlist["is_album"] as? JsonPrimitive)?.booleanOrNull == true
        val tracks = readTracks(entries, album = if (isAlbum) name else null)
        if (tracks.isEmpty()) throw LinkImportException("That SoundCloud playlist has no songs we can read.")

        val cover = (playlist.string("artwork_url") ?: entries.firstNotNullOfOrNull { it.obj()?.string("artwork_url") })
            ?.replace("-large.", "-t500x500.")
        val origin = PlaylistOrigin(
            remoteId = playlist.idString("id"),
            remoteUrl = playlist.string("permalink_url") ?: page,
            author = playlist["user"].obj()?.toAuthor(),
        )
        return ParsedImport(
            likedSongs = emptyList(),
            playlists = mapOf(name to tracks),
            provider = ImportSource.SOUNDCLOUD,
            playlistCovers = if (cover != null) mapOf(name to cover) else emptyMap(),
            playlistOrigins = mapOf(name to origin),
        )
    }

    /** the songs in playlist order, looking up the ones soundcloud only sent as ids */
    private suspend fun readTracks(entries: List<JsonElement>, album: String?): List<ImportedTrack> {
        val full = HashMap<String, JsonObject>()
        val missing = mutableListOf<String>()
        entries.forEach { element ->
            val obj = element.obj() ?: return@forEach
            val id = obj.idString("id") ?: return@forEach
            if (obj.string("title") != null) full[id] = obj else missing += id
        }
        missing.chunked(TRACK_BATCH).forEach { ids ->
            (api("$API/tracks?ids=${ids.joinToString(",")}") as? JsonArray).orEmpty().forEach { element ->
                val obj = element.obj() ?: return@forEach
                obj.idString("id")?.let { full[it] = obj }
            }
        }
        // a removed or blocked song never comes back from the lookup, so it's just left out
        return entries.mapNotNull { element -> element.obj()?.idString("id")?.let(full::get)?.toTrack(album) }
    }

    private fun JsonObject.toTrack(album: String?): ImportedTrack? {
        val title = string("title") ?: return null
        // the label's credit is the real artist when there is one, the uploader is often a fan channel
        val artist = this["publisher_metadata"].obj()?.string("artist") ?: this["user"].obj()?.string("username")
        return ImportedTrack(title = title, artist = artist.orEmpty(), album = album, durationMs = long("duration"))
    }

    private fun JsonObject.toAuthor(): PlaylistAuthor? {
        val name = string("username") ?: return null
        return PlaylistAuthor(
            name = name,
            avatarUrl = string("avatar_url")?.replace("-large.", "-t300x300."),
            profileUrl = string("permalink_url"),
        )
    }

    /** a call to soundcloud's api, reading a fresh client id once if the one we had stopped working */
    private suspend fun api(url: String): JsonElement {
        repeat(2) { attempt ->
            val id = clientId ?: readClientId()
            val response = LinkImporter.get("$url&client_id=$id")
            when (response.status.value) {
                in 200..299 -> return LinkImporter.json.parseToJsonElement(response.bodyAsText())
                401, 403 -> if (attempt == 0) clientId = null
                404 -> throw LinkImportException("That SoundCloud playlist is private or doesn't exist.")
                429 -> throw LinkImportException("SoundCloud is rate-limiting us. Try again in a minute.")
                else -> throw LinkImportException("SoundCloud answered with an error (${response.status.value}).")
            }
        }
        throw LinkImportException("SoundCloud didn't let us in. Try again in a bit.")
    }

    private suspend fun readClientId(): String {
        val home = LinkImporter.getOk("https://soundcloud.com/", "SoundCloud")
        return CLIENT_ID_REGEX.find(home)?.groupValues?.get(1)?.also { clientId = it }
            ?: throw LinkImportException("SoundCloud changed its page, so we can't read links from it right now.")
    }
}
