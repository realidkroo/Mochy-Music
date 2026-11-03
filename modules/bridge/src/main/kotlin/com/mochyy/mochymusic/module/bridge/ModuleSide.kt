// the module's side of the bridge, packed into its zip: makes the module from its own code and answers mochy's calls with it, in json

package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.bridge.Wire.obj
import com.mochyy.mochymusic.module.bridge.Wire.text
import com.mochyy.mochymusic.module.bridge.shared.BridgeReply
import com.mochyy.mochymusic.module.bridge.shared.HostBridge
import com.mochyy.mochymusic.module.bridge.shared.ModuleBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64

class ModuleSide : ModuleBridge {

    private lateinit var module: MochyModule

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = ConcurrentHashMap<Long, Job>()

    // license servers handed out with streams, by the id mochy asks back with; only the latest few, a stream that old has long been replaced
    private val licenses = object : LinkedHashMap<String, LicenseServer>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LicenseServer>) = size > MaxLicenses
    }
    private val licenseIds = AtomicLong()

    override fun open(entry: String): String {
        module = Class.forName(entry, true, javaClass.classLoader).getDeclaredConstructor().newInstance() as MochyModule
        return Wire.aboutToJson(module).toString()
    }

    override fun attach(host: HostBridge) = module.attach(HostOverBridge(host))

    // anything the module throws goes back as a plain java exception, so mochy never has to know a class from this side
    override fun ask(method: String, args: String): String = try {
        answer(method, Wire.objectOf(args)).toString()
    } catch (e: Throwable) {
        throw IllegalStateException(e.message ?: e.javaClass.simpleName)
    }

    private fun answer(method: String, args: JsonObject): JsonElement = when (method) {
        "accountState" -> Wire.accountToJson(module.accountState())
        "onSignInHeader" -> JsonPrimitive(module.onSignInHeader(args.text("name").orEmpty(), args.text("value").orEmpty(), args.text("url").orEmpty()))
        "signOut" -> JsonNull.also { module.signOut() }
        "choices" -> Wire.choicesToJson(module.choices)
        "look" -> Wire.mapToJson((module as? LookModule)?.look.orEmpty())
        "servers" -> Wire.serversToJson((module as? ServerListModule)?.servers.orEmpty())
        "removeServer" -> JsonNull.also { (module as? ServerListModule)?.removeServer(args.text("url").orEmpty()) }
        "items" -> Wire.itemsToJson((module as? ItemListModule)?.items.orEmpty())
        "removeItem" -> JsonNull.also { (module as? ItemListModule)?.removeItem(args.text("id").orEmpty()) }
        "license" -> JsonPrimitive(Base64.encode(licenseFor(args.text("id")).acquire(Base64.decode(args.text("challenge").orEmpty()))))
        else -> throw IllegalArgumentException("$method isn't something a module answers")
    }

    override fun start(id: Long, method: String, args: String, data: ByteArray?, reply: BridgeReply) {
        val job = work.launch(start = CoroutineStart.LAZY) {
            val answer = try {
                run(method, Wire.objectOf(args), data, reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reply.failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            reply.done(answer.toString())
        }
        calls[id] = job
        job.invokeOnCompletion { calls.remove(id) }
        job.start()
    }

    private suspend fun run(method: String, args: JsonObject, data: ByteArray?, reply: BridgeReply): JsonElement = when (method) {
        "verifyAccount" -> Wire.accountToJson(module.verifyAccount())
        "resolve" -> Wire.resultToJson(source().resolve(Wire.trackFrom(args.obj("track")), Wire.mapFrom(args["picks"])), ::licenseId)
        "diagnose" -> {
            val result = source().diagnose(Wire.trackFrom(args.obj("track")), Wire.mapFrom(args["picks"])) { step ->
                reply.step(Wire.stepToJson(step).toString())
            }
            Wire.resultToJson(result, ::licenseId)
        }
        "lyrics" -> JsonPrimitive((module as LyricsModule).lyrics(Wire.trackFrom(args.obj("track"))))
        "addServer" -> JsonPrimitive((module as ServerListModule).addServer(args.text("link").orEmpty())?.name)
        "addZip" -> Wire.addedToJson((module as ItemListModule).addZip(args.text("fileName").orEmpty(), data ?: ByteArray(0)))
        "addLink" -> Wire.addedToJson((module as ItemListModule).addLink(args.text("link").orEmpty()))
        else -> throw IllegalArgumentException("$method isn't something a module answers")
    }

    override fun cancel(id: Long) {
        calls.remove(id)?.cancel()
    }

    override fun close() {
        work.cancel()
        (module as? AutoCloseable)?.close()
        synchronized(licenses) { licenses.clear() }
    }

    private fun source(): SourceModule = module as SourceModule

    private fun licenseId(server: LicenseServer): String {
        val id = licenseIds.incrementAndGet().toString()
        synchronized(licenses) { licenses[id] = server }
        return id
    }

    private fun licenseFor(id: String?): LicenseServer =
        synchronized(licenses) { licenses[id] } ?: throw IllegalStateException("That stream's license is gone, start it again")

    // what the module sees as mochy: the same host every module gets, each call crossing the bridge
    private class HostOverBridge(private val bridge: HostBridge) : ModuleHost {
        override val storage = object : ModuleStorage {
            override fun get(key: String): String? = bridge.get(key)
            override fun set(key: String, value: String?) = bridge.set(key, value)
        }

        override fun http(request: HttpRequest): HttpResponse {
            val answer = bridge.http(Wire.requestToJson(request).toString(), request.body)
            return HttpResponse(answer.code, answer.body, Wire.mapFrom(Wire.parse(answer.headers)))
        }

        override fun log(message: String) = bridge.log(message)

        override val folder = File(bridge.folder())
    }

    private companion object {
        const val MaxLicenses = 32
    }
}
