// the module api as json both ways, for the bridge; anything missing reads as its default, so an older or newer side still gets along

package com.mochyy.mochymusic.module.bridge

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ItemProblem
import com.mochyy.mochymusic.module.ItemsAdded
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ModuleItem
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

internal object Wire {

    /** what a module says about itself once it's made */
    class About(
        val info: ModuleInfo,
        val adds: Set<ModuleFeature>,
        val signIn: SignInSpec?,
        val servers: Boolean,
        val items: Boolean,
        val itemsLabel: String,
        val itemsAbout: String,
    )

    val Empty = JsonObject(emptyMap())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): JsonElement = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)

    fun objectOf(text: String): JsonObject = parse(text) as? JsonObject ?: Empty

    // ---- the module itself ----

    fun aboutToJson(module: MochyModule): JsonObject = buildJsonObject {
        put("info", infoToJson(module.info))
        put("adds", strings(module.adds.map { it.name }))
        module.signIn?.let { put("signIn", signInToJson(it)) }
        put("servers", module is ServerListModule)
        if (module is ItemListModule) {
            put("items", true)
            put("itemsLabel", module.itemsLabel)
            put("itemsAbout", module.itemsAbout)
        }
    }

    fun aboutFrom(o: JsonObject) = About(
        info = infoFrom(o.obj("info")),
        adds = o.strings("adds").mapNotNull { enumOrNull<ModuleFeature>(it) }.toSet(),
        signIn = (o["signIn"] as? JsonObject)?.let(::signInFrom),
        servers = o.bool("servers"),
        items = o.bool("items"),
        itemsLabel = o.text("itemsLabel").orEmpty(),
        itemsAbout = o.text("itemsAbout").orEmpty(),
    )

    private fun infoToJson(info: ModuleInfo) = buildJsonObject {
        put("id", info.id)
        put("name", info.name)
        put("version", info.version)
        put("description", info.description)
        put("apiVersion", info.apiVersion)
        put("sourceUrl", info.sourceUrl)
        put("author", info.author)
    }

    // a module too old to say its api version reads as version 0, and mochy turns that away
    private fun infoFrom(o: JsonObject) = ModuleInfo(
        id = o.text("id").orEmpty(),
        name = o.text("name").orEmpty(),
        version = o.text("version").orEmpty(),
        description = o.text("description").orEmpty(),
        apiVersion = o.long("apiVersion")?.toInt() ?: 0,
        sourceUrl = o.text("sourceUrl"),
        author = o.text("author"),
    )

    private fun signInToJson(spec: SignInSpec) = buildJsonObject {
        put("startUrl", spec.startUrl)
        put("origins", strings(spec.origins))
        put("sessionOrigins", strings(spec.sessionOrigins))
        put("desktopSite", spec.desktopSite)
        put("title", spec.title)
        put("subtitle", spec.subtitle)
    }

    private fun signInFrom(o: JsonObject) = SignInSpec(
        startUrl = o.text("startUrl").orEmpty(),
        origins = o.strings("origins"),
        sessionOrigins = o.strings("sessionOrigins").toSet(),
        desktopSite = o.bool("desktopSite"),
        title = o.text("title").orEmpty(),
        subtitle = o.text("subtitle").orEmpty(),
    )

    fun accountToJson(state: AccountState) = buildJsonObject {
        when (state) {
            AccountState.SignedOut -> put("state", "signedOut")
            is AccountState.SignedIn -> {
                put("state", "signedIn")
                put("text", state.label)
            }
            is AccountState.Expired -> {
                put("state", "expired")
                put("text", state.reason)
            }
        }
    }

    fun accountFrom(element: JsonElement): AccountState {
        val o = element as? JsonObject ?: return AccountState.SignedOut
        val text = o.text("text").orEmpty()
        return when (o.text("state")) {
            "signedIn" -> AccountState.SignedIn(text)
            "expired" -> AccountState.Expired(text)
            else -> AccountState.SignedOut
        }
    }

    fun choicesToJson(choices: List<ModuleChoice>) = JsonArray(
        choices.map { choice ->
            buildJsonObject {
                put("id", choice.id)
                put("label", choice.label)
                put("options", JsonArray(choice.options.map { option ->
                    buildJsonObject {
                        put("id", option.id)
                        put("label", option.label)
                        put("description", option.description)
                    }
                }))
            }
        },
    )

    fun choicesFrom(element: JsonElement): List<ModuleChoice> = element.objects().map { o ->
        ModuleChoice(
            id = o.text("id").orEmpty(),
            label = o.text("label").orEmpty(),
            options = o["options"].objects().map { StreamOption(it.text("id").orEmpty(), it.text("label").orEmpty(), it.text("description").orEmpty()) },
        )
    }

    // ---- songs ----

    fun trackToJson(track: TrackQuery) = buildJsonObject {
        put("title", track.title)
        put("artists", strings(track.artists))
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("isrc", track.isrc)
    }

    fun trackFrom(o: JsonObject) = TrackQuery(
        title = o.text("title").orEmpty(),
        artists = o.strings("artists"),
        album = o.text("album"),
        durationMs = o.long("durationMs"),
        isrc = o.text("isrc"),
    )

    /** a stream's license server can't cross as json, so it goes as an id the module hands out and mochy asks back with */
    fun resultToJson(result: ResolveResult, licenseId: (LicenseServer) -> String) = buildJsonObject {
        when (result) {
            is ResolveResult.Found -> put("found", streamToJson(result.stream, licenseId))
            is ResolveResult.NotFound -> put("notFound", result.reason)
            is ResolveResult.Unavailable -> put("unavailable", result.reason)
        }
    }

    fun resultFrom(element: JsonElement, license: (String) -> LicenseServer): ResolveResult {
        val o = element as? JsonObject ?: return ResolveResult.Unavailable(NoAnswer)
        (o["found"] as? JsonObject)?.let { return ResolveResult.Found(streamFrom(it, license)) }
        o.text("notFound")?.let { return ResolveResult.NotFound(it) }
        return ResolveResult.Unavailable(o.text("unavailable") ?: NoAnswer)
    }

    private fun streamToJson(stream: SourceStream, licenseId: (LicenseServer) -> String) = buildJsonObject {
        put("url", stream.url)
        put("format", stream.format.name)
        put("headers", mapToJson(stream.headers))
        (stream.drm as? StreamDrm.Widevine)?.let { drm ->
            put("widevine", buildJsonObject {
                put("license", licenseId(drm.license))
                put("initData", strings(drm.initData.map { Base64.encode(it) }))
            })
        }
        put("sourceLabel", stream.sourceLabel)
        put("qualityLabel", stream.qualityLabel)
        put("matched", buildJsonObject {
            put("id", stream.matched.id)
            put("title", stream.matched.title)
            put("artist", stream.matched.artist)
            put("album", stream.matched.album)
            put("durationMs", stream.matched.durationMs)
            put("confidence", stream.matched.confidence)
        })
        put("codec", stream.codec)
        put("sampleRateHz", stream.sampleRateHz)
    }

    private fun streamFrom(o: JsonObject, license: (String) -> LicenseServer): SourceStream {
        val matched = o.obj("matched")
        val widevine = o["widevine"] as? JsonObject
        return SourceStream(
            url = o.text("url").orEmpty(),
            format = o.text("format")?.let { enumOrNull<StreamFormat>(it) } ?: StreamFormat.Progressive,
            headers = mapFrom(o["headers"]),
            drm = widevine?.let { StreamDrm.Widevine(license(it.text("license").orEmpty()), it.strings("initData").map { data -> Base64.decode(data) }) },
            sourceLabel = o.text("sourceLabel").orEmpty(),
            qualityLabel = o.text("qualityLabel").orEmpty(),
            matched = MatchedTrack(
                id = matched.text("id").orEmpty(),
                title = matched.text("title").orEmpty(),
                artist = matched.text("artist").orEmpty(),
                album = matched.text("album"),
                durationMs = matched.long("durationMs"),
                confidence = matched.text("confidence")?.toFloatOrNull() ?: 0f,
            ),
            codec = o.text("codec"),
            sampleRateHz = o.long("sampleRateHz")?.toInt(),
        )
    }

    fun stepToJson(step: DiagnosticStep) = buildJsonObject {
        put("name", step.name)
        put("ok", step.ok)
        put("detail", step.detail)
    }

    fun stepFrom(o: JsonObject) = DiagnosticStep(o.text("name").orEmpty(), (o["ok"] as? JsonPrimitive)?.booleanOrNull, o.text("detail").orEmpty())

    // ---- servers and items ----

    fun serversToJson(servers: List<ModuleServer>) = JsonArray(
        servers.map {
            buildJsonObject {
                put("url", it.url)
                put("name", it.name)
                put("builtIn", it.builtIn)
            }
        },
    )

    fun serversFrom(element: JsonElement) = element.objects().map { ModuleServer(it.text("url").orEmpty(), it.text("name").orEmpty(), it.bool("builtIn")) }

    // a problem a newer module knows and this mochy doesn't still reads as one
    fun serverProblemFrom(element: JsonElement): ServerProblem? =
        (element as? JsonPrimitive)?.contentOrNull?.let { enumOrNull<ServerProblem>(it) ?: ServerProblem.NotAServer }

    fun itemsToJson(items: List<ModuleItem>) = JsonArray(
        items.map {
            buildJsonObject {
                put("id", it.id)
                put("name", it.name)
                put("detail", it.detail)
            }
        },
    )

    fun itemsFrom(element: JsonElement) = element.objects().map { ModuleItem(it.text("id").orEmpty(), it.text("name").orEmpty(), it.text("detail").orEmpty()) }

    fun addedToJson(added: ItemsAdded) = buildJsonObject {
        when (added) {
            is ItemsAdded.Added -> put("added", strings(added.names))
            is ItemsAdded.Problem -> put("problem", added.problem.name)
        }
    }

    fun addedFrom(element: JsonElement): ItemsAdded {
        val o = element as? JsonObject ?: return ItemsAdded.Problem(ItemProblem.Broken)
        if (o["added"] is JsonArray) return ItemsAdded.Added(o.strings("added"))
        return ItemsAdded.Problem(o.text("problem")?.let { enumOrNull<ItemProblem>(it) } ?: ItemProblem.Broken)
    }

    // ---- what the module reaches through mochy ----

    fun requestToJson(request: HttpRequest) = buildJsonObject {
        put("url", request.url)
        put("method", request.method)
        put("headers", mapToJson(request.headers))
        put("contentType", request.contentType)
    }

    fun requestFrom(o: JsonObject, body: ByteArray?) = HttpRequest(
        url = o.text("url").orEmpty(),
        method = o.text("method") ?: "GET",
        headers = mapFrom(o["headers"]),
        body = body,
        contentType = o.text("contentType"),
    )

    fun mapToJson(map: Map<String, String>) = JsonObject(map.mapValues { JsonPrimitive(it.value) })

    fun mapFrom(element: JsonElement?): Map<String, String> =
        (element as? JsonObject)?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }?.toMap().orEmpty()

    fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: Empty

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    private fun strings(values: Iterable<String>) = JsonArray(values.map(::JsonPrimitive))

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }

    private const val NoAnswer = "The module gave no answer"
}
