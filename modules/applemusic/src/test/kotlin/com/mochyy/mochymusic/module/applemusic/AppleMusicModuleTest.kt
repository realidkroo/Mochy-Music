package com.mochyy.mochymusic.module.applemusic

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.TrackMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import java.util.Base64

/**
 * The module against Apple's real catalog search (captured 2026-10-12) and a web-playback
 * exchange shaped like Apple's, served by a fake host.
 */
class AppleMusicModuleTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.readText()

    private class FakeHost(val routes: (HttpRequest) -> HttpResponse) : ModuleHost {
        val requests = ArrayList<HttpRequest>()
        override val storage = object : ModuleStorage {
            val map = HashMap<String, String>()
            override fun get(key: String) = map[key]
            override fun set(key: String, value: String?) {
                if (value == null) map.remove(key) else map[key] = value
            }
        }
        override fun http(request: HttpRequest): HttpResponse {
            requests += request
            return routes(request)
        }
        override fun log(message: String) = Unit
        override val folder: File = createTempDirectory("applemusic").toFile()
    }

    /** a developer token whose payload says it's good for a day */
    private val devToken = "eyJhbGciOiJFUzI1NiJ9." +
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"iss":"AMPWebPlay","exp":${System.currentTimeMillis() / 1000 + 86_400}}""".toByteArray()
        ) + ".c2ln"

    private val keyUri = "data:;base64,ESIzRFVmd4iZqrvM3e7/AA=="

    private val legacyPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:5
        #EXT-X-TARGETDURATION:11
        #EXT-X-KEY:METHOD=SAMPLE-AES,URI="$keyUri",KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",KEYFORMATVERSIONS="1"
        #EXT-X-MAP:URI="P123_A456_ctrp256.mp4",BYTERANGE="700@0"
        #EXTINF:10.0,
        #EXT-X-BYTERANGE:1000@700
        P123_A456_ctrp256.mp4
        #EXT-X-ENDLIST
    """.trimIndent()

    private val webPlayback = """{"songList":[{"assets":[
        {"flavor":"30:cbcp256","URL":"https://aod.itunes.apple.com/a/b/fairplay.m3u8"},
        {"flavor":"32:ctrp64","URL":"https://aod.itunes.apple.com/a/b/P123_64.m3u8"},
        {"flavor":"28:ctrp256","URL":"https://aod.itunes.apple.com/a/b/P123.m3u8"}]}]}"""

    private fun signedInModule(routes: (HttpRequest) -> HttpResponse?): Pair<AppleMusicModule, FakeHost> {
        val host = FakeHost { request ->
            routes(request) ?: when {
                request.url.endsWith("/v1/me/storefront") ->
                    HttpResponse(200, """{"data":[{"id":"us","attributes":{"name":"United States"}}]}""".toByteArray())
                "/search?" in request.url -> HttpResponse(200, resource("search.json").toByteArray())
                request.url.endsWith("wa/webPlayback") -> HttpResponse(200, webPlayback.toByteArray())
                request.url.startsWith("https://aod.itunes.apple.com/a/b/P123") -> HttpResponse(200, legacyPlaylist.toByteArray())
                else -> HttpResponse(404, ByteArray(0))
            }
        }
        val module = AppleMusicModule().also { it.attach(host) }
        module.onSignInHeader("authorization", "Bearer $devToken", "https://amp-api.music.apple.com/v1/me")
        assertTrue(module.onSignInHeader("media-user-token", "A".repeat(40), "https://amp-api.music.apple.com/v1/me"))
        return module to host
    }

    private val espresso = TrackQuery(title = "Espresso", artists = listOf("Sabrina Carpenter"), durationMs = 175_000)

    @Test
    fun `finds the song and opens its web-playback stream`() = runBlocking {
        val (module, host) = signedInModule { null }
        val stream = (module.resolve(espresso, emptyMap()) as ResolveResult.Found).stream
        assertEquals(StreamFormat.Hls, stream.format)
        assertEquals("1740212434", stream.matched.id)
        assertEquals("AAC 256", stream.qualityLabel)
        assertEquals("AAC", stream.codec)
        assertTrue(stream.url.startsWith("data:application/vnd.apple.mpegurl;base64,"))
        val playlist = Base64.getDecoder().decode(stream.url.substringAfter(',')).decodeToString()
        assertTrue(playlist.contains("https://aod.itunes.apple.com/a/b/P123_A456_ctrp256.mp4"))
        assertEquals(1, (stream.drm as StreamDrm.Widevine).initData.size)
        // the stream is asked for the song the search matched, with the account's token
        val playback = host.requests.single { it.url.endsWith("wa/webPlayback") }
        assertTrue(playback.body!!.decodeToString().contains(""""salableAdamId":"1740212434""""))
        assertEquals("A".repeat(40), playback.headers["Media-User-Token"])
        // Apple turns away requests that don't look like its web player
        assertTrue(host.requests.all { it.headers["Origin"] == "https://music.apple.com" })
    }

    @Test
    fun `HE-AAC picks the smaller stream`() = runBlocking {
        val (module, host) = signedInModule { null }
        val stream = (module.resolve(espresso, mapOf(AppleMusicModule.ChoiceQuality to AppleMusicModule.OptionHe64)) as ResolveResult.Found).stream
        assertEquals("HE-AAC 64", stream.qualityLabel)
        assertEquals("HE-AAC", stream.codec)
        assertTrue(host.requests.any { it.url == "https://aod.itunes.apple.com/a/b/P123_64.m3u8" })
    }

    @Test
    fun `the license request carries the challenge and the key as the playlist wrote it`() = runBlocking {
        var licenseBody: String? = null
        val (module, _) = signedInModule { request ->
            if (request.url.endsWith("acquireWebPlaybackLicense")) {
                licenseBody = request.body!!.decodeToString()
                assertEquals("A".repeat(40), request.headers["Media-User-Token"])
                HttpResponse(200, """{"status":0,"license":"AQID"}""".toByteArray())
            } else null
        }
        val drm = (module.resolve(espresso, emptyMap()) as ResolveResult.Found).stream.drm as StreamDrm.Widevine
        val challenge = byteArrayOf(8, 1, 0x12, 0x10, 1, 2, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), drm.license.acquire(challenge))
        val body = licenseBody!!
        assertTrue(body, body.contains(""""uri":"$keyUri""""))
        assertTrue(body.contains(""""key-system":"com.widevine.alpha""""))
        assertTrue(body.contains(""""adamId":"1740212434""""))
        assertTrue(body.contains(Base64.getEncoder().encodeToString(challenge)))
    }

    @Test
    fun `a signed-out license answer marks the account expired`() = runBlocking {
        val (module, _) = signedInModule { request ->
            if (request.url.endsWith("acquireWebPlaybackLicense")) {
                HttpResponse(200, """{"customerMessage":"Your session has ended. Sign in again.","status":-1002}""".toByteArray())
            } else null
        }
        val drm = (module.resolve(espresso, emptyMap()) as ResolveResult.Found).stream.drm as StreamDrm.Widevine
        assertTrue(runCatching { drm.license.acquire(ByteArray(8)) }.isFailure)
        assertTrue(module.accountState() is AccountState.Expired)
    }

    @Test
    fun `a copy web playback refuses gives way to the same recording's next copy`() = runBlocking {
        // the single and the album: the same recording twice, as the catalog often has it
        val twoCopies = """{"results":{"songs":{"data":[
            {"id":"111","attributes":{"name":"Espresso","artistName":"Sabrina Carpenter","durationInMillis":175459}},
            {"id":"222","attributes":{"name":"Espresso","artistName":"Sabrina Carpenter","durationInMillis":175459}}]}}}"""
        val (module, host) = signedInModule { request ->
            when {
                "/search?" in request.url -> HttpResponse(200, twoCopies.toByteArray())
                request.url.endsWith("wa/webPlayback") && request.body!!.decodeToString().contains("\"111\"") ->
                    HttpResponse(200, """{"failureType":"3076","customerMessage":"This item isn't available."}""".toByteArray())
                else -> null
            }
        }
        val stream = (module.resolve(espresso, emptyMap()) as ResolveResult.Found).stream
        assertEquals("222", stream.matched.id)
        assertEquals(2, host.requests.count { it.url.endsWith("wa/webPlayback") })
    }

    @Test
    fun `the web-playback key becomes a proper Widevine box`() {
        val (uri, keyId) = AppleHls.legacyKey(legacyPlaylist)!!
        assertEquals(keyUri, uri)
        assertEquals(16, keyId.size)
        val pssh = AppleHls.psshFor(keyId)
        assertEquals("pssh", String(pssh, 4, 4))
        assertArrayEquals(keyId, AppleHls.keyIdOf(pssh))
        val rewritten = AppleHls.rewriteLegacy(legacyPlaylist, "https://aod.itunes.apple.com/a/b/P123.m3u8", uri, pssh)
        assertTrue(rewritten, rewritten.contains("https://aod.itunes.apple.com/a/b/P123_A456_ctrp256.mp4\n"))
        assertTrue(rewritten.contains("""URI="https://aod.itunes.apple.com/a/b/P123_A456_ctrp256.mp4",BYTERANGE"""))
        val keyLines = rewritten.lines().filter { it.startsWith("#EXT-X-KEY") }
        assertEquals(1, keyLines.size)
        assertEquals(AppleHls.WidevineKeyFormat, AppleHls.attribute(keyLines[0], "KEYFORMAT"))
        val written = Base64.getDecoder().decode(AppleHls.attribute(keyLines[0], "URI")!!.substringAfter(','))
        assertArrayEquals(pssh, written)
    }

    @Test
    fun `the key method is written the way the player parses it`() {
        // Apple's AES-CTR streams name the standard; the player only knows its own names
        val iso = legacyPlaylist.replace("METHOD=SAMPLE-AES,", "METHOD=ISO-23001-7,")
        val (uri, keyId) = AppleHls.legacyKey(iso)!!
        val rewritten = AppleHls.rewriteLegacy(iso, "https://aod.itunes.apple.com/a/b/P123.m3u8", uri, AppleHls.psshFor(keyId))
        val method = AppleHls.attribute(rewritten.lines().single { it.startsWith("#EXT-X-KEY") }, "METHOD")
        assertEquals("SAMPLE-AES-CTR", method)
        // the player's own pattern, from Media3's HlsPlaylistParser
        assertTrue(Regex("METHOD=(NONE|AES-128|SAMPLE-AES|SAMPLE-AES-CENC|SAMPLE-AES-CTR)\\s*(?:,|$)").containsMatchIn(rewritten))
        assertEquals("SAMPLE-AES", AppleHls.playerMethod("SAMPLE-AES"))
    }

    @Test
    fun `a region pick searches that catalog`() = runBlocking {
        val (module, host) = signedInModule { null }
        module.resolve(espresso, mapOf(AppleMusicModule.ChoiceRegion to "jp"))
        assertTrue(host.requests.any { "/v1/catalog/jp/search?" in it.url })
    }

    @Test
    fun `matching ignores decorations but not different recordings`() {
        fun song(title: String, artist: String = "Sabrina Carpenter", ms: Long = 175_459) =
            CatalogTrack("1", title, artist, null, ms, null)
        val official = TrackQuery("Espresso (Official Video)", listOf("Sabrina Carpenter"), durationMs = 176_000)
        assertTrue(TrackMatcher.score(official, song("Espresso")) > 0.9f)
        assertTrue(TrackMatcher.score(official, song("Espresso (Remix)")) < 0.7f)
        assertTrue(TrackMatcher.score(official, song("Espresso", artist = "Someone Else")) == 0f)
        assertTrue(TrackMatcher.score(TrackQuery("Alive", listOf("Sia"), durationMs = 263_000), song("Alive", "Sia", 263_000)) > 0.9f)
        val feat = TrackQuery("Die With A Smile (feat. Bruno Mars)", listOf("Lady Gaga", "Bruno Mars"), durationMs = 251_000)
        assertTrue(TrackMatcher.score(feat, song("Die With A Smile", "Lady Gaga & Bruno Mars", 251_667)) > 0.9f)
        // much longer or shorter is probably another cut
        assertTrue(TrackMatcher.score(TrackQuery("Espresso", listOf("Sabrina Carpenter"), durationMs = 240_000), song("Espresso")) < 0.85f)
    }

    @Test
    fun `signed out says so instead of searching`() = runBlocking {
        val host = FakeHost { HttpResponse(500, ByteArray(0)) }
        val module = AppleMusicModule().also { it.attach(host) }
        assertTrue(module.resolve(espresso, emptyMap()) is ResolveResult.Unavailable)
        assertTrue(host.requests.isEmpty())
    }
}
