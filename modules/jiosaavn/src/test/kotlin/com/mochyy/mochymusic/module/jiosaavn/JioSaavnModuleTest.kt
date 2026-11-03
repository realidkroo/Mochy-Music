package com.mochyy.mochymusic.module.jiosaavn

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class JioSaavnModuleTest {

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
        override val folder: File = createTempDirectory("jiosaavn").toFile()
    }

    private fun module(fileThere: Boolean = true): Pair<JioSaavnModule, FakeHost> {
        val host = FakeHost { request ->
            when {
                "search.getResults" in request.url -> HttpResponse(200, resource("search_tum_hi_ho.json").toByteArray())
                "saavncdn.com" in request.url -> HttpResponse(if (fileThere) 206 else 404, ByteArray(2))
                else -> HttpResponse(404, ByteArray(0))
            }
        }
        return JioSaavnModule().also { it.attach(host) } to host
    }

    private val tumHiHo = TrackQuery("Tum Hi Ho", listOf("Arijit Singh"), durationMs = 262_000)

    @Test
    fun `reads songs with their artists and unescaped titles`() {
        val (saavn, _) = module()
        val songs = saavn.songsIn(resource("search_tum_hi_ho.json"))
        assertEquals(2, songs.size)
        assertEquals("Tum Hi Ho", songs[0].catalog.title)
        assertEquals("Mithoon, Arijit Singh", songs[0].catalog.artist)
        assertEquals(262_000L, songs[0].catalog.durationMs)
        assertEquals("Tum Hi Ho (From \"Aashiqui 2\")", songs[1].catalog.title)
        assertTrue(songs[0].has320)
    }

    @Test
    fun `decrypts the media link`() {
        val (saavn, _) = module()
        val encrypted = saavn.songsIn(resource("search_tum_hi_ho.json"))[0].encryptedUrl
        assertEquals("https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_96.mp4", saavn.decryptMediaUrl(encrypted))
    }

    @Test
    fun `streams the 320 file when the song has one`() = runBlocking {
        val (saavn, _) = module()
        val found = saavn.resolve(tumHiHo, emptyMap()) as ResolveResult.Found
        assertEquals("https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_320.mp4", found.stream.url)
        assertEquals("AAC 320", found.stream.qualityLabel)
    }

    @Test
    fun `keeps the given file when the cdn doesn't have the one asked for`() = runBlocking {
        val (saavn, _) = module(fileThere = false)
        val found = saavn.resolve(tumHiHo, emptyMap()) as ResolveResult.Found
        assertTrue(found.stream.url.endsWith("_96.mp4"))
        assertEquals("AAC 96", found.stream.qualityLabel)
    }

    @Test
    fun `keeps query parameters when changing the bitrate`() {
        val (saavn, _) = module()
        val stream = saavn.streamAt("https://aac.saavncdn.com/1/abc_96.mp4?t=1", 160)
        assertEquals("https://aac.saavncdn.com/1/abc_160.mp4?t=1", stream.url)
    }
}
