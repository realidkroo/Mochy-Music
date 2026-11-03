package com.mochyy.mochymusic.module.monochrome

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/** The module against tracks.monochrome.st's real search answer (captured 2026-10-12). */
class MonochromeModuleTest {

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
        override val folder: File = createTempDirectory("monochrome").toFile()
    }

    private fun module(routes: (HttpRequest) -> HttpResponse? = { null }): Pair<MonochromeModule, FakeHost> {
        val host = FakeHost { request ->
            routes(request) ?: when {
                "/search/tracks?" in request.url -> HttpResponse(200, resource("search_eastside.json").toByteArray())
                "/track/" in request.url -> HttpResponse(206, eastsideHead)
                else -> HttpResponse(404, ByteArray(0))
            }
        }
        return MonochromeModule().also { it.attach(host) } to host
    }

    // the real first 42 bytes of Eastside's FLAC from tracks.monochrome.st
    private val eastsideHead = byteArrayOf(
        0x66, 0x4c, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22, 0x04, 0x80.toByte(), 0x04, 0x80.toByte(), 0x00, 0x00, 0x0e, 0x00,
        0x11, 0x2d, 0x0a, 0xc4.toByte(), 0x42, 0xf0.toByte(), 0x00, 0x74, 0xf3.toByte(), 0xbf.toByte(), 0xdc.toByte(), 0xee.toByte(),
        0xc0.toByte(), 0x89.toByte(), 0xf2.toByte(), 0x89.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    )

    // as YouTube names it: no "(with Halsey & Khalid)", a second off
    private val eastside = TrackQuery("Eastside", listOf("benny blanco", "Halsey", "Khalid"), durationMs = 173_000)

    @Test
    fun `finds the song and streams its FLAC`() = runBlocking {
        val (module, host) = module()
        val stream = (module.resolve(eastside, emptyMap()) as ResolveResult.Found).stream
        assertEquals("https://tracks.monochrome.st/track/157948484734181376", stream.url)
        assertEquals(StreamFormat.Progressive, stream.format)
        assertNull(stream.drm)
        assertEquals("FLAC", stream.qualityLabel)
        // 44.1 kHz, 16-bit, read from the file's own STREAMINFO
        val info = MonochromeModule().flacInfo(eastsideHead)!!
        assertEquals(44_100, info.sampleRate)
        assertEquals(16, info.bitsPerSample)
        assertEquals("FLAC · 16-bit / 44.1 kHz", info.label)
        assertEquals("FLAC", stream.codec)
        // searched as the web app does: artist first
        assertTrue(host.requests.first().url.contains("q=benny%20blanco%20eastside"))
    }

    @Test
    fun `a file that isn't FLAC isn't played`() = runBlocking {
        val (module, _) = module { if ("/track/" in it.url) HttpResponse(200, "<html".toByteArray()) else null }
        assertTrue(module.resolve(eastside, emptyMap()) is ResolveResult.Unavailable)
    }

    @Test
    fun `a song it doesn't have is a miss, remembered`() = runBlocking {
        val (module, host) = module()
        val other = TrackQuery("Some Song Nobody Has", listOf("Nobody"), durationMs = 200_000)
        assertTrue(module.resolve(other, emptyMap()) is ResolveResult.NotFound)
        val searches = host.requests.size
        assertTrue(module.resolve(other, emptyMap()) is ResolveResult.NotFound)
        assertEquals(searches, host.requests.size)
    }

    @Test
    fun `needs no account, and says when the server is down`() = runBlocking {
        val (module, _) = module { HttpResponse(503, ByteArray(0)) }
        assertTrue(module.accountState() is AccountState.SignedIn)
        assertTrue(module.verifyAccount() is AccountState.Expired)
        assertTrue(module.accountState() is AccountState.Expired)
    }

    @Test
    fun `a lookup the server doesn't answer passes the module over for a while`() = runBlocking {
        val (module, _) = module { throw java.io.IOException("no route to host") }
        assertTrue(module.resolve(eastside, emptyMap()) is ResolveResult.Unavailable)
        assertTrue(module.accountState() is AccountState.Expired)
    }

    @Test
    fun `an old unreachable mark is tried again`() {
        val (module, host) = module()
        host.storage.set("unreachable@https://tracks.monochrome.st", "tracks.monochrome.st didn't answer")
        host.storage.set("unreachable_at@https://tracks.monochrome.st", (System.currentTimeMillis() - 11 * 60 * 1000).toString())
        assertTrue(module.accountState() is AccountState.SignedIn)
        // a mark with no time is old too
        host.storage.set("unreachable_at@https://tracks.monochrome.st", null)
        assertTrue(module.accountState() is AccountState.SignedIn)
    }

    // the audio relay indiachrome plays from, as it answered on 2026-11-01
    private val relaySearch =
        """{"data":[{"id":"r1","title":"Eastside","artist":"benny blanco&Halsey&Khalid","album":"Eastside","duration":173,"subtitle":""}]}"""

    private fun relay(request: HttpRequest): HttpResponse? {
        if (!request.url.startsWith("https://audio-relay.example.dev")) return null
        // like the real one, it turns away anyone who doesn't say which api they speak
        if (request.headers["X-Relay-Client"] != "relay/2") return HttpResponse(404, "{\"error\":\"not found\"}".toByteArray())
        return when {
            "/search?" in request.url -> HttpResponse(200, relaySearch.toByteArray())
            "/s/r1?q=flac" in request.url -> HttpResponse(206, eastsideHead, mapOf("x-relay-stream" to "https://audio-relay.example.dev/s/token"))
            else -> HttpResponse(404, ByteArray(0))
        }
    }

    // a monochrome fork: a page that answers every path with itself, and a script naming its relay
    private fun site(request: HttpRequest): HttpResponse? = when {
        request.url.startsWith("https://audio-relay.example.dev") -> relay(request)
        request.url == "https://fork.example.dev/assets/index-abc.js" ->
            HttpResponse(200, """const a="https://fonts.googleapis.com",b="https://audio-relay.example.dev",c="https://canvas-relay.example.dev";""".toByteArray())
        request.url.startsWith("https://fork.example.dev") ->
            HttpResponse(200, """<!doctype html><html><head><script type="module" crossorigin src="./assets/index-abc.js"></script></head></html>""".toByteArray())
        request.url.startsWith("https://canvas-relay.example.dev") -> HttpResponse(404, ByteArray(0))
        else -> null
    }

    @Test
    fun `a relay server is found from its own link and plays from the link it hands back`() = runBlocking {
        val (module, _) = module(::relay)
        assertNull(module.addServer("audio-relay.example.dev"))
        val stream = (module.resolve(eastside, emptyMap()) as ResolveResult.Found).stream
        assertEquals("https://audio-relay.example.dev/s/token", stream.url)
        assertEquals("FLAC", stream.qualityLabel)
        assertEquals("benny blanco, Halsey, Khalid", stream.matched.artist)
    }

    @Test
    fun `a site link finds the server its scripts name, and it's remembered`() = runBlocking {
        val (module, host) = module(::site)
        assertNull(module.addServer("https://fork.example.dev/search/what%20if%20i%20call"))
        val server = ModuleServer("https://audio-relay.example.dev", "fork.example.dev", builtIn = false)
        assertEquals(listOf(server, ModuleServer("https://tracks.monochrome.st", "tracks.monochrome.st", builtIn = true)), module.servers)
        assertEquals(ServerProblem.AlreadyAdded, module.addServer("fork.example.dev"))
        // a restart reads it back from storage
        assertEquals(server, MonochromeModule().also { it.attach(host) }.servers.first())
        module.removeServer(server.url)
        assertEquals(1, module.servers.size)
    }

    @Test
    fun `a page with no server in it isn't added`() = runBlocking {
        val (module, _) = module { request ->
            if (request.url.startsWith("https://plain.example.dev")) HttpResponse(200, "<html>hi</html>".toByteArray()) else null
        }
        assertEquals(ServerProblem.NotAServer, module.addServer("plain.example.dev"))
        assertEquals(ServerProblem.NotALink, module.addServer("   "))
        assertEquals(1, module.servers.size)
    }

    @Test
    fun `while one server is down the next one plays`() = runBlocking {
        var relayUp = true
        val (module, _) = module { request ->
            if (!relayUp && "audio-relay" in request.url) throw java.io.IOException("timeout") else relay(request)
        }
        assertNull(module.addServer("audio-relay.example.dev"))
        relayUp = false
        val stream = (module.resolve(eastside, emptyMap()) as ResolveResult.Found).stream
        assertEquals("https://tracks.monochrome.st/track/157948484734181376", stream.url)
        assertTrue(module.accountState() is AccountState.SignedIn)
    }

    @Test
    fun `a 24-bit 96 kHz file reads as Hi-Res`() {
        // STREAMINFO for 96000 Hz, 2 channels, 24 bits: rate 0x17700 -> 0x17 0x70 0x0., then (ch-1)=1, (bits-1)=23
        val streamInfo = ByteArray(18).also {
            it[10] = 0x17
            it[11] = 0x70.toByte()
            it[12] = (0x00 or (1 shl 1) or ((23 shr 4) and 1)).toByte()
            it[13] = ((23 and 0x0f) shl 4).toByte()
        }
        val head = "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 0x22) + streamInfo
        val info = MonochromeModule().flacInfo(head)!!
        assertEquals(96_000, info.sampleRate)
        assertEquals(24, info.bitsPerSample)
        assertEquals("Hi-Res FLAC · 24-bit / 96 kHz", info.label)
    }

    @Test
    fun `durations in milliseconds and seconds both read right`() {
        val (module, _) = module()
        val tracks = module.tracksIn("""{"tracks":[{"trackId":"1","title":"A","artistNames":["X"],"duration":173799},{"trackId":"2","title":"B","artistNames":["X"],"duration":174},{"trackId":"3","title":"C","playable":false}]}""")
        assertEquals(listOf(173_799L, 174_000L), tracks.map { it.durationMs })
    }
}
