package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ItemProblem
import com.mochyy.mochymusic.module.ItemsAdded
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import kotlin.io.path.createTempDirectory

// a module behind the bridge in a class loader of its own, the way mochy loads one from a zip: if anything but the shared package crossed, these would throw
class BridgeTest {

    private class FakeHost : ModuleHost {
        val saved = HashMap<String, String>()
        val logs = ArrayList<String>()
        val requests = ArrayList<HttpRequest>()

        override val storage = object : ModuleStorage {
            override fun get(key: String) = saved[key]
            override fun set(key: String, value: String?) {
                if (value == null) saved.remove(key) else saved[key] = value
            }
        }

        override fun http(request: HttpRequest): HttpResponse {
            synchronized(requests) { requests += request }
            return HttpResponse(200, "{\"ok\":true}".toByteArray(), mapOf("content-type" to "application/json"))
        }

        override fun log(message: String) {
            synchronized(logs) { logs += message }
        }

        override val folder: File = createTempDirectory("bridge").toFile()
    }

    private val song = TrackQuery("Espresso", listOf("Sabrina Carpenter"), durationMs = 175_000)

    // the module's own copy of everything it runs on, mochy's classes only reachable through the shared package
    private fun isolated(): URLClassLoader {
        val homes = listOf(
            ModuleSide::class.java, FakeModule::class.java, MochyModule::class.java, KotlinVersion::class.java,
            Job::class.java, Json::class.java, SerializationException::class.java,
        ).map { it.protectionDomain.codeSource.location }.distinct()
        return URLClassLoader(homes.toTypedArray(), BridgeOnlyParent(javaClass.classLoader))
    }

    private fun open(entry: String = FakeModule::class.java.name): Pair<BridgedModule, FakeHost> {
        val bridge = isolated().loadClass(BridgedModule.ModuleSideClass).getDeclaredConstructor().newInstance() as ModuleBridge
        val module = BridgedModule.open(bridge, entry)
        val host = FakeHost()
        module.attach(host)
        return module to host
    }

    @Test
    fun theModuleRunsOnItsOwnCopyOfEverything() {
        val loader = isolated()
        assertNotSame(FakeModule::class.java, loader.loadClass(FakeModule::class.java.name))
        assertNotSame(KotlinVersion::class.java, loader.loadClass(KotlinVersion::class.java.name))
        assertTrue(ModuleBridge::class.java === loader.loadClass(ModuleBridge::class.java.name))
    }

    @Test
    fun itSaysWhatItIsAndOnlyClaimsTheListsItHas() {
        val (module, host) = open()
        assertEquals("fake", module.info.id)
        assertEquals(setOf(ModuleFeature.AudioSource), module.adds)
        assertTrue(module is ItemListModule)
        assertFalse(module is ServerListModule)
        assertEquals("Scripts", (module as ItemListModule).itemsLabel)
        assertEquals(AccountState.SignedIn("ready"), module.accountState())
        assertEquals(listOf("quality"), module.choices.map { it.id })
        // attach reached mochy's storage and its private folder
        assertEquals(host.folder.path, host.saved["folder"])
    }

    @Test
    fun aSongComesBackWithItsLicenseServerStillWorking() = runBlocking {
        val (module, host) = open()
        val found = module.resolve(song, mapOf("quality" to "lossless")) as ResolveResult.Found
        val stream = found.stream
        assertEquals("https://cdn.example/espresso.mpd?q=lossless", stream.url)
        assertEquals(StreamFormat.Dash, stream.format)
        assertEquals(mapOf("Referer" to "https://fake.example"), stream.headers)
        assertEquals("FLAC", stream.codec)
        assertEquals(96_000, stream.sampleRateHz)
        assertEquals(0.9f, stream.matched.confidence)
        val drm = stream.drm as StreamDrm.Widevine
        assertArrayEquals(byteArrayOf(1, 2, 3), drm.initData.single())
        assertArrayEquals(byteArrayOf(3, 2, 1), drm.license.acquire(byteArrayOf(1, 2, 3)))
        // its http went through mochy, and the answer came back with its headers
        assertEquals("https://fake.example/search?q=Espresso", host.requests.single().url)
        assertTrue(host.logs.any { it == "search said application/json {\"ok\":true}" })
    }

    @Test
    fun aCheckReportsEachStepAsItGoes() = runBlocking {
        val (module, _) = open()
        val steps = ArrayList<DiagnosticStep>()
        val result = module.diagnose(song, emptyMap()) { steps += it }
        assertTrue(result is ResolveResult.NotFound)
        assertEquals(listOf(DiagnosticStep("Find", null, "Looking…"), DiagnosticStep("Find", false, "Not there")), steps)
    }

    @Test
    fun itemsGoInWithTheirBytesAndComeOut() = runBlocking {
        val (module, _) = open()
        val items = module as ItemListModule
        assertEquals(ItemsAdded.Added(listOf("pack.zip:4")), items.addZip("pack.zip", byteArrayOf(1, 2, 3, 4)))
        assertEquals(ItemsAdded.Problem(ItemProblem.NotALink), items.addLink("nope"))
        assertEquals(listOf("pack.zip:4"), items.items.map { it.name })
        items.removeItem(items.items.single().id)
        assertTrue(items.items.isEmpty())
    }

    @Test
    fun aFailureComesBackAsItsMessage() = runBlocking {
        val (module, _) = open()
        try {
            module.resolve(TrackQuery("boom", emptyList()), emptyMap())
            fail("it should have thrown")
        } catch (e: IOException) {
            assertEquals("the module broke", e.message)
        }
    }

    @Test
    fun cancellingTheCallerStopsTheModulesWork() = runBlocking {
        val (module, host) = open()
        val call = async { module.resolve(TrackQuery("slow", emptyList()), emptyMap()) }
        delay(200)
        call.cancel()
        repeat(50) {
            if (synchronized(host.logs) { "slow search cancelled" in host.logs }) return@runBlocking
            delay(20)
        }
        fail("the module kept going")
    }

    @Test
    fun aModuleMadeForAnotherApiIsTurnedAway() {
        try {
            open(OldModule::class.java.name)
            fail("it should have been turned away")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("module API 2"))
        }
    }
}
