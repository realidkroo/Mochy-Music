// mochy's side of the bridge: a module that came with its own code, every call crossing as json and coming back the same way

package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ItemsAdded
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleItem
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.bridge.Wire.text
import com.mochyy.mochymusic.module.bridge.shared.BridgeReply
import com.mochyy.mochymusic.module.bridge.shared.BridgeResponse
import com.mochyy.mochymusic.module.bridge.shared.HostBridge
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

// songs, lyrics and a look are always here and only asked for when the module says it adds them; servers and items need a subclass, since the page checks for those by type
open class BridgedModule private constructor(private val bridge: ModuleBridge, about: Wire.About) : SourceModule, LyricsModule, LookModule {

    override val info = about.info
    override val adds = about.adds
    override val signIn = about.signIn
    val itemsLabel: String = about.itemsLabel
    val itemsAbout: String = about.itemsAbout

    private val callIds = AtomicLong()

    override fun attach(host: ModuleHost) = bridge.attach(HostEnd(host))

    override fun accountState(): AccountState = Wire.accountFrom(ask("accountState"))

    override fun onSignInHeader(name: String, value: String, url: String): Boolean =
        (ask("onSignInHeader", buildJsonObject {
            put("name", name)
            put("value", value)
            put("url", url)
        }) as? JsonPrimitive)?.contentOrNull == "true"

    override suspend fun verifyAccount(): AccountState = Wire.accountFrom(call("verifyAccount"))

    override fun signOut() {
        ask("signOut")
    }

    override val choices: List<ModuleChoice> get() = Wire.choicesFrom(ask("choices"))

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        Wire.resultFrom(call("resolve", songArgs(track, picks)), ::license)

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult =
        Wire.resultFrom(call("diagnose", songArgs(track, picks)) { report(Wire.stepFrom(Wire.objectOf(it))) }, ::license)

    override suspend fun lyrics(track: TrackQuery): String? =
        (call("lyrics", buildJsonObject { put("track", Wire.trackToJson(track)) }) as? JsonPrimitive)?.contentOrNull

    override val look: Map<String, String> get() = Wire.mapFrom(ask("look"))

    val servers: List<ModuleServer> get() = Wire.serversFrom(ask("servers"))

    suspend fun addServer(link: String): ServerProblem? = Wire.serverProblemFrom(call("addServer", buildJsonObject { put("link", link) }))

    fun removeServer(url: String) {
        ask("removeServer", buildJsonObject { put("url", url) })
    }

    val items: List<ModuleItem> get() = Wire.itemsFrom(ask("items"))

    // the zip's bytes go across as they are, not inside the json
    suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded =
        Wire.addedFrom(call("addZip", buildJsonObject { put("fileName", fileName) }, data = bytes))

    suspend fun addLink(link: String): ItemsAdded = Wire.addedFrom(call("addLink", buildJsonObject { put("link", link) }))

    fun removeItem(id: String) {
        ask("removeItem", buildJsonObject { put("id", id) })
    }

    /** stops the module's work and lets go of what it holds, once it's removed or replaced */
    fun close() = bridge.close()

    private fun ask(method: String, args: JsonObject = Wire.Empty): JsonElement = Wire.parse(bridge.ask(method, args.toString()))

    // cancelling the caller cancels the module's work too, so a song skipped past doesn't keep searching
    private suspend fun call(method: String, args: JsonObject = Wire.Empty, data: ByteArray? = null, onStep: (String) -> Unit = {}): JsonElement {
        val id = callIds.incrementAndGet()
        val answer = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { bridge.cancel(id) }
            bridge.start(id, method, args.toString(), data, object : BridgeReply {
                override fun step(json: String) = onStep(json)
                override fun done(json: String) = continuation.resume(json)
                override fun failed(message: String) = continuation.resumeWithException(IOException(message))
            })
        }
        return Wire.parse(answer)
    }

    private fun songArgs(track: TrackQuery, picks: Map<String, String>) = buildJsonObject {
        put("track", Wire.trackToJson(track))
        put("picks", Wire.mapToJson(picks))
    }

    // the player's drm thread asks for a license and waits, so this one blocks
    private fun license(id: String) = LicenseServer { challenge ->
        val answer = ask("license", buildJsonObject {
            put("id", id)
            put("challenge", Base64.encode(challenge))
        })
        Base64.decode((answer as? JsonPrimitive)?.contentOrNull ?: throw IOException("The module gave no license"))
    }

    // mochy's host as the module reaches it, one call at a time across the bridge
    private class HostEnd(private val host: ModuleHost) : HostBridge {
        override fun get(key: String): String? = host.storage.get(key)

        override fun set(key: String, value: String?) = host.storage.set(key, value)

        override fun http(head: String, body: ByteArray?): BridgeResponse {
            val response = host.http(Wire.requestFrom(Wire.objectOf(head), body))
            return BridgeResponse(response.code, Wire.mapToJson(response.headers).toString(), response.body)
        }

        override fun log(message: String) = host.log(message)

        override fun folder(): String = host.folder.path
    }

    private class WithServers(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ServerListModule

    private class WithItems(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ItemListModule

    private class WithBoth(bridge: ModuleBridge, about: Wire.About) : BridgedModule(bridge, about), ServerListModule, ItemListModule

    companion object {
        /** the class mochy makes from a zip's code, the module's side of the bridge */
        const val ModuleSideClass = "com.mochyy.mochymusic.module.bridge.ModuleSide"

        /** makes the module named [entry] on the far side of [bridge]; throws when it can't run in this mochy */
        fun open(bridge: ModuleBridge, entry: String): BridgedModule {
            val about = Wire.aboutFrom(Wire.objectOf(bridge.open(entry)))
            val version = about.info.apiVersion
            if (version != MODULE_API_VERSION) throw IOException("It's made for module API $version, this Mochy has $MODULE_API_VERSION")
            if (about.info.id.isBlank()) throw IOException("It doesn't say what it is")
            return when {
                about.servers && about.items -> WithBoth(bridge, about)
                about.servers -> WithServers(bridge, about)
                about.items -> WithItems(bridge, about)
                else -> BridgedModule(bridge, about)
            }
        }
    }
}

/** the parent of a zip module's class loader: the phone's own java and android classes and the bridge's shared package, nothing else of mochy's */
class BridgeOnlyParent(private val mochy: ClassLoader) : ClassLoader(String::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (name.startsWith(SharedPackage)) mochy.loadClass(name) else super.loadClass(name, resolve)

    private companion object {
        val SharedPackage = ModuleBridge::class.java.name.substringBeforeLast('.') + "."
    }
}
