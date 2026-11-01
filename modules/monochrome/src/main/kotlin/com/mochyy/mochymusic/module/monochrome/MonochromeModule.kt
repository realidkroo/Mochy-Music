package com.mochyy.mochymusic.module.monochrome

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.NO_ACCOUNT_LABEL
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

// lossless flac from monochrome's own track streamer, and from any server people add off a monochrome site (docs/notes/modules.md)
class MonochromeModule : SourceModule, ServerListModule {

    override val info = ModuleInfo(
        id = Id,
        name = "Monochrome",
        version = "2.1",
        description = "Lossless FLAC for every song Monochrome has, no account needed, with every Audio Lab effect",
        sourceUrl = "https://github.com/monochrome-music/monochrome",
    )

    // the two kinds of server monochrome sites play from: its own track streamer, and the audio relay some forks run
    internal enum class Kind { Streamer, Relay }

    internal data class Server(val url: String, val kind: Kind, val name: String)

    // where a song streams from, and the file's first bytes to check it by
    private class Opened(val url: String, val head: ByteArray)

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, CatalogTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    // the servers people added, tried before the built-in one since they were added for a reason
    @Volatile
    private var added: List<Server> = emptyList()

    private val all: List<Server> get() = added + DefaultServer

    override fun attach(host: ModuleHost) {
        this.host = host
        added = readServers()
    }

    override val signIn: SignInSpec? = null

    override val choices: List<ModuleChoice> = emptyList()

    override val servers: List<ModuleServer>
        get() = all.map { ModuleServer(it.url, it.name, builtIn = it == DefaultServer) }

    // a failed server is only left alone for a while, since playback passes over a module that isn't signed in and nothing would check it again
    override fun accountState(): AccountState {
        val reasons = all.map { downReason(it) }
        val down = reasons.filterNotNull()
        return if (down.size == reasons.size) AccountState.Expired(down.first()) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override fun onSignInHeader(name: String, value: String, url: String): Boolean = false

    override suspend fun verifyAccount(): AccountState {
        val reachable = all.count { reachable(it) }
        return when {
            reachable == 0 -> accountState()
            all.size == 1 -> AccountState.SignedIn("$NO_ACCOUNT_LABEL · server reachable")
            else -> AccountState.SignedIn("$NO_ACCOUNT_LABEL · $reachable of ${all.size} servers reachable")
        }
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        // a server that just failed waits its turn, unless every one of them did
        val usable = all.filter { downReason(it) == null }.ifEmpty { all }
        var result: ResolveResult = ResolveResult.NotFound("Not on Monochrome")
        for (server in usable) {
            result = resolveOn(server, track, report)
            if (result is ResolveResult.Found) return result
        }
        return result
    }

    private fun resolveOn(server: Server, track: TrackQuery, report: (DiagnosticStep) -> Unit): ResolveResult {
        val key = "${server.url}|${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on ${server.name}"))
            return ResolveResult.NotFound("Not on Monochrome")
        }

        report(DiagnosticStep("Find the song", null, "Searching ${server.name}…"))
        val found = matches[key] ?: run {
            val query = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
            val response = runCatching { host.http(searchRequest(server, query, SearchLimit)) }.getOrElse {
                val reason = "${server.name} didn't answer"
                report(DiagnosticStep("Find the song", false, reason))
                markDown(server.url, reason)
                return ResolveResult.Unavailable(reason)
            }
            if (!response.ok) {
                report(DiagnosticStep("Find the song", false, "${server.name} answered ${response.code}"))
                return ResolveResult.Unavailable("${server.name} answered ${response.code}")
            }
            clearDown(server.url)
            val best = TrackMatcher.ranked(track, tracksIn(server.kind, response.text)).firstOrNull()
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on Monochrome")
            }
            matches[key] = best.track
            best.track
        }
        report(DiagnosticStep("Find the song", true, "${found.title} · ${found.artist}"))

        // the head of the file: it should open as flac, and its streaminfo says how fine it is
        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val opened = open(server, found.id)
        if (opened == null) {
            report(DiagnosticStep("Stream", false, "${server.name} gave no stream"))
            return ResolveResult.Unavailable("${server.name} gave no stream")
        }
        val info = flacInfo(opened.head)
        if (info == null) {
            report(DiagnosticStep("Stream", false, "Not a FLAC file"))
            return ResolveResult.Unavailable("Monochrome's stream isn't FLAC")
        }
        report(DiagnosticStep("Stream", true, info.label))

        return ResolveResult.Found(
            SourceStream(
                url = opened.url,
                format = StreamFormat.Progressive,
                headers = streamHeaders,
                sourceLabel = "Monochrome",
                qualityLabel = info.shortLabel,
                codec = "FLAC",
                sampleRateHz = info.sampleRate,
                matched = MatchedTrack(
                    id = found.id,
                    title = found.title,
                    artist = found.artist,
                    album = found.album,
                    durationMs = found.durationMs,
                    confidence = TrackMatcher.score(track, found),
                ),
            )
        )
    }

    private fun open(server: Server, id: String): Opened? = when (server.kind) {
        Kind.Streamer -> {
            val url = "${server.url}/track/$id"
            host.httpOrNull(HttpRequest(url, headers = streamHeaders + HeadRange))?.takeIf { it.ok }?.let { Opened(url, it.body) }
        }
        // the relay finds the file on the first ask and hands back a link of its own, which plays without the relay's header
        Kind.Relay -> {
            val response = host.httpOrNull(HttpRequest("${server.url}/s/${enc(id)}?q=flac", headers = relayHeaders + HeadRange))?.takeIf { it.ok }
            val link = response?.header("X-Relay-Stream")?.takeIf { it.startsWith("${originOf(server.url)}/") }
            if (response != null && link != null) Opened(link, response.body) else null
        }
    }

    private fun searchRequest(server: Server, query: String, limit: Int): HttpRequest = when (server.kind) {
        Kind.Streamer -> HttpRequest("${server.url}/search/tracks?q=${enc(query)}&limit=$limit", headers = headers)
        Kind.Relay -> HttpRequest("${server.url}/search?q=${enc(query)}&n=$limit", headers = relayHeaders)
    }

    private fun tracksIn(kind: Kind, text: String): List<CatalogTrack> = when (kind) {
        Kind.Streamer -> tracksIn(text)
        Kind.Relay -> relayTracksIn(text)
    }

    internal fun tracksIn(text: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val tracks = root["tracks"] as? JsonArray ?: return emptyList()
        return tracks.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            if ((t["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val id = t.string("trackId") ?: t.string("id") ?: return@mapNotNull null
            val artists = (t["artistNames"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            // the streamer gives milliseconds; older answers gave seconds
            val duration = (t["duration"] as? JsonPrimitive)?.longOrNull?.let { if (it > 10_000) it else it * 1000 }
            CatalogTrack(
                id = id,
                title = t.string("title") ?: return@mapNotNull null,
                artist = artists.joinToString(", "),
                album = t.string("albumTitle") ?: t.string("releaseTitle"),
                durationMs = duration,
                isrc = t.string("isrc"),
            )
        }
    }

    // the relay's search: every artist on one line joined by a bare &, and seconds
    internal fun relayTracksIn(text: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val rows = root["data"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            CatalogTrack(
                id = t.string("id") ?: return@mapNotNull null,
                title = t.string("title") ?: return@mapNotNull null,
                artist = t.string("artist").orEmpty().split(RelayArtistJoin).joinToString(", ") { it.trim() },
                album = t.string("album")?.takeIf { it.isNotBlank() },
                durationMs = (t["duration"] as? JsonPrimitive)?.longOrNull?.let { if (it > 10_000) it else it * 1000 },
                isrc = t.string("isrc"),
            )
        }
    }

    // ---- servers people add ---------------------------------------------------------------------

    override suspend fun addServer(link: String): ServerProblem? {
        val (base, origin) = linkParts(link) ?: return ServerProblem.NotALink
        // a server's own link answers a search, a site's doesn't, so then its scripts get read for the server it plays from
        val direct = listOf(base, origin).distinct().firstNotNullOfOrNull { identify(it) }
        val found = direct ?: run {
            val page = host.httpOrNull(HttpRequest(origin, headers = pageHeaders)) ?: return ServerProblem.Unreachable
            if (!page.ok) return ServerProblem.NotAServer
            serverInSite(origin, page.text)?.copy(name = hostOf(origin))
        } ?: return ServerProblem.NotAServer
        if (all.any { it.url == found.url }) return ServerProblem.AlreadyAdded
        added = added + found
        saveServers()
        clearDown(found.url)
        return null
    }

    override fun removeServer(url: String) {
        added = added.filterNot { it.url == url }
        saveServers()
        clearDown(url)
        matches.keys.removeIf { it.startsWith("$url|") }
        misses.keys.removeIf { it.startsWith("$url|") }
    }

    // which kind of server answers a search at this link, if either does
    private fun identify(base: String): Server? = Kind.entries.firstNotNullOfOrNull { kind ->
        val server = Server(base, kind, hostOf(base))
        host.httpOrNull(searchRequest(server, "test", 1))?.takeIf { it.ok && answersSearch(kind, it.text) }?.let { server }
    }

    // a real search answer of that kind, not a web page or some other api that happens to say 200
    private fun answersSearch(kind: Kind, text: String): Boolean {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return false
        return root[if (kind == Kind.Streamer) "tracks" else "data"] is JsonArray
    }

    // a monochrome site names its server in its own scripts, so each server-looking link in them gets tried
    private fun serverInSite(origin: String, html: String): Server? = ScriptSrc.findAll(html)
        .mapNotNull { runCatching { URI("$origin/").resolve(it.groupValues[1]).toString() }.getOrNull() }
        .filter { it.startsWith("$origin/") }
        .take(MaxScripts)
        .mapNotNull { script -> host.httpOrNull(HttpRequest(script, headers = pageHeaders))?.takeIf { it.ok }?.text }
        .flatMap { script -> ServerLink.findAll(script).map { it.value } }
        .filter { link -> hostOf(link).let { it.startsWith("tracks.") || "relay" in it } }
        .distinct()
        .take(MaxCandidates)
        .firstNotNullOfOrNull { identify(it) }

    // a pasted link as its own base and its site's origin, spaces and a missing https:// forgiven
    private fun linkParts(link: String): Pair<String, String>? {
        val text = link.trim().replace(" ", "%20").let { if ("://" in it) it else "https://$it" }
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
        val origin = "${uri.scheme}://${uri.rawAuthority}"
        return (origin + uri.rawPath.orEmpty()).trimEnd('/') to origin
    }

    private fun readServers(): List<Server> = runCatching {
        val saved = json.parseToJsonElement(host.storage.get(ServersKey) ?: return emptyList()) as JsonArray
        saved.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val kind = Kind.entries.firstOrNull { it.name == o.string("kind") } ?: return@mapNotNull null
            Server(o.string("url") ?: return@mapNotNull null, kind, o.string("name").orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun saveServers() {
        val list = buildJsonArray {
            added.forEach { server ->
                addJsonObject {
                    put("url", server.url)
                    put("kind", server.kind.name)
                    put("name", server.name)
                }
            }
        }
        host.storage.set(ServersKey, list.toString())
    }

    // ---- servers that didn't answer -------------------------------------------------------------

    // one small search, and the server's mark set or cleared by how it went
    private fun reachable(server: Server): Boolean {
        val response = host.httpOrNull(searchRequest(server, "test", 1))
        if (response?.ok == true) {
            clearDown(server.url)
            return true
        }
        markDown(server.url, "${server.name} didn't answer (${response?.code ?: "offline"})")
        return false
    }

    // kept in storage so a restart remembers, and set by a song's lookup too, so a dead server costs one song a timeout, not every song
    private fun markDown(url: String, reason: String) {
        host.storage.set("$DownKey$url", reason)
        host.storage.set("$DownAtKey$url", System.currentTimeMillis().toString())
    }

    private fun clearDown(url: String) = host.storage.set("$DownKey$url", null)

    // why the server is being left alone right now, null once its time is up
    private fun downReason(server: Server): String? {
        val reason = host.storage.get("$DownKey${server.url}") ?: return null
        val markedAt = host.storage.get("$DownAtKey${server.url}")?.toLongOrNull() ?: 0L
        return reason.takeIf { System.currentTimeMillis() - markedAt < UnreachableRetryMs }
    }

    /** what a FLAC file's STREAMINFO says: its bit depth and sample rate */
    internal class FlacInfo(val bitsPerSample: Int, val sampleRate: Int) {
        val hiRes: Boolean get() = bitsPerSample > 16 || sampleRate > 48_000
        private val rate: String get() = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else String.format("%.1f", sampleRate / 1000.0)
        val shortLabel: String get() = if (hiRes) "Hi-Res FLAC" else "FLAC"
        val label: String get() = "$shortLabel · $bitsPerSample-bit / $rate kHz"
    }

    /**
     * "fLaC", then the STREAMINFO block: 4 bytes of header, then (after the block and frame
     * sizes, 10 bytes) 20 bits of sample rate, 3 of channels - 1, 5 of bits per sample - 1.
     */
    internal fun flacInfo(bytes: ByteArray): FlacInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 4 + 4 + 18) return FlacInfo(16, 44_100)
        val b = bytes.copyOfRange(8, 26).map { it.toInt() and 0xff }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] shr 4)
        val bits = (((b[12] and 0x01) shl 4) or (b[13] shr 4)) + 1
        return if (sampleRate in 8_000..768_000 && bits in 8..32) FlacInfo(bits, sampleRate) else FlacInfo(16, 44_100)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun ModuleHost.httpOrNull(request: HttpRequest): HttpResponse? = runCatching { http(request) }.getOrNull()

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty()

    private fun originOf(url: String): String = URI(url).let { "${it.scheme}://${it.rawAuthority}" }

    companion object {
        const val Id = "monochrome"
        private val DefaultServer = Server("https://tracks.monochrome.st", Kind.Streamer, "tracks.monochrome.st")
        private const val ServersKey = "servers"
        private const val DownKey = "unreachable@"
        private const val DownAtKey = "unreachable_at@"
        private const val SearchLimit = 8
        private const val MissMemoryMs = 30L * 60 * 1000
        // how long a server that didn't answer is left alone before it's tried again
        private const val UnreachableRetryMs = 10L * 60 * 1000
        // a site has a script or two of its own, and only a handful of its links look like servers
        private const val MaxScripts = 4
        private const val MaxCandidates = 8
        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val headers = mapOf("Accept" to "application/json", "User-Agent" to UserAgent)
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
        private val pageHeaders = mapOf("User-Agent" to UserAgent)
        // the relay only answers clients that say which version of its api they speak
        private val relayHeaders = headers + ("X-Relay-Client" to "relay/2")
        private val HeadRange = "Range" to "bytes=0-41"
        // a bare & between artists, so a name like Simon & Garfunkel keeps its spaced one
        private val RelayArtistJoin = Regex("""(?<! )&(?! )""")
        private val ScriptSrc = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val ServerLink = Regex("""https://[a-z0-9-]+(?:\.[a-z0-9-]+)+""")
    }
}
