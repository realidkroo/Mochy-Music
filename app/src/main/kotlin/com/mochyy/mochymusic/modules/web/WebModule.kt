// a module added from a link: a service that answers search, stream and lyrics over plain http, no code of its own runs in mochy

package com.mochyy.mochymusic.modules.web

import android.content.res.Resources
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.NO_ACCOUNT_LABEL
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.DeviceDecoders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal class WebModule(
    val base: String,
    private val manifest: WebModuleManifest,
    private val resources: Resources,
) : SourceModule, LyricsModule, LookModule {

    private val siteName = runCatching { URI(base).host }.getOrNull().orEmpty()

    override val info = ModuleInfo(
        id = WebModuleManifest.idFor(base, manifest),
        name = manifest.name.ifBlank { siteName },
        version = manifest.version.ifBlank { "1" },
        description = manifest.description.ifBlank { "Added from $siteName" },
        author = manifest.author,
    )

    override val adds: Set<ModuleFeature> = buildSet {
        if (manifest.playsSongs) add(ModuleFeature.AudioSource)
        if (manifest.hasLyrics) add(ModuleFeature.Lyrics)
        if (manifest.look.isNotEmpty()) add(ModuleFeature.Look)
    }

    override val look: Map<String, String> = manifest.look.mapNotNull { (key, value) -> value.contentOrNull?.let { key to it } }.toMap()

    // read from resources each time so the chips follow the app's language
    override val choices: List<ModuleChoice>
        get() = if (ModuleFeature.AudioSource !in adds) emptyList() else listOf(
            ModuleChoice(
                ChoiceQuality, resources.getString(R.string.quality_module_stream),
                listOf(
                    option(OptionLossless, R.string.web_quality_lossless, R.string.web_quality_lossless_detail),
                    option(OptionHiRes, R.string.web_quality_hires, R.string.web_quality_hires_detail),
                    option(OptionHigh, R.string.web_quality_high, R.string.web_quality_high_detail),
                    option(OptionLow, R.string.web_quality_low, R.string.web_quality_low_detail),
                ),
            ),
            ModuleChoice(
                ChoiceMix, resources.getString(R.string.web_mix),
                listOf(
                    option(OptionStereo, R.string.web_mix_stereo, R.string.web_mix_stereo_detail),
                    option(OptionAtmos, R.string.web_mix_atmos, R.string.web_mix_atmos_detail),
                ),
            ),
        )

    private fun option(id: String, label: Int, detail: Int) = StreamOption(id, resources.getString(label), resources.getString(detail))

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val matches = ConcurrentHashMap<String, CatalogTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    // a search answer may already carry each song's stream, then there's no second request; keyed by what was asked too
    private val directStreams = ConcurrentHashMap<String, String>()

    // the songs that are an atmos mix, tidal lists those as songs of their own
    private val atmosRows = ConcurrentHashMap.newKeySet<String>()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override suspend fun verifyAccount(): AccountState {
        val response = runCatching { host.http(HttpRequest("$base/manifest.json", headers = headers)) }.getOrNull()
        return if (response?.ok == true) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · $siteName answers")
        } else {
            val reason = "$siteName didn't answer (${response?.code ?: "offline"})"
            markUnreachable(reason)
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    // ---- songs ----------------------------------------------------------------------------------

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (ModuleFeature.AudioSource !in adds) return ResolveResult.Unavailable("${info.name} doesn't play songs")
        val tier = picks[ChoiceQuality]?.takeIf { it in TidalNames } ?: OptionLossless
        // the atmos mix only when this phone can decode it, otherwise the stereo one plays instead of silence
        val atmos = picks[ChoiceMix] == OptionAtmos && DeviceDecoders.canPlay(AtmosCodec)
        val asked = "$tier|$atmos"
        val key = "${track.title}|${track.artists.joinToString(",")}|$asked"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on ${info.name}"))
            return ResolveResult.NotFound("Not on ${info.name}")
        }

        report(DiagnosticStep("Find the song", null, "Searching ${info.name}…"))
        val found = matches[key] ?: run {
            val query = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
            val response = runCatching { host.http(HttpRequest("$base/search?q=${enc(query)}&${paramsFor(tier, atmos)}", headers = headers)) }.getOrElse {
                report(DiagnosticStep("Find the song", false, "$siteName didn't answer"))
                markUnreachable("$siteName didn't answer")
                return ResolveResult.Unavailable("$siteName didn't answer")
            }
            if (!response.ok) {
                report(DiagnosticStep("Find the song", false, "$siteName answered ${response.code}"))
                return ResolveResult.Unavailable("$siteName answered ${response.code}")
            }
            host.storage.set("unreachable", null)
            val best = pick(TrackMatcher.ranked(track, tracksIn(response.text, asked)), atmos)
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on ${info.name}")
            }
            matches[key] = best
            best
        }
        report(DiagnosticStep("Find the song", true, "${found.title} · ${found.artist}"))

        report(DiagnosticStep("Stream", null, "Asking for the stream…"))
        val asTiered = streamFor(found, tier, atmos)
        // a 192 khz master this phone can't decode would be passed over, the same song in cd quality is the better answer
        val stream = if (asTiered != null && tier == OptionHiRes && !DeviceDecoders.canPlay(asTiered)) {
            streamFor(found, OptionLossless, atmos) ?: asTiered
        } else {
            asTiered
        }
        if (stream == null) {
            report(DiagnosticStep("Stream", false, "$siteName gave no stream Mochy can play"))
            return ResolveResult.Unavailable("$siteName gave no stream Mochy can play")
        }
        report(DiagnosticStep("Stream", true, stream.qualityLabel))
        return ResolveResult.Found(
            stream.copy(
                matched = MatchedTrack(
                    id = found.id,
                    title = found.title,
                    artist = found.artist,
                    album = found.album,
                    durationMs = found.durationMs,
                    confidence = TrackMatcher.score(track, found),
                ),
            ),
        )
    }

    // tidal lists the atmos mix as its own song: take it when atmos is wanted, keep to the stereo one otherwise
    private fun pick(ranked: List<TrackMatcher.Scored>, atmos: Boolean): CatalogTrack? {
        val (immersive, stereo) = ranked.partition { it.track.id in atmosRows }
        val preferred = if (atmos) immersive + stereo else stereo + immersive
        return preferred.firstOrNull()?.track
    }

    // the service's own defaults, then the picked quality and mix in the words the service declared for them
    private fun paramsFor(tier: String, atmos: Boolean): String {
        val params = LinkedHashMap<String, String>()
        manifest.settings.forEach { setting -> setting.defaultValue?.takeIf { setting.key.isNotBlank() }?.let { params[setting.key] = it } }
        params[manifest.setting(QualityKey)?.key ?: QualityKey] = qualityValue(tier)
        val atmosKey = manifest.setting(AtmosKey)?.key ?: AtmosKey
        val mix = atmosValue(atmos)
        if (mix != null) params[atmosKey] = mix else params.remove(atmosKey)
        return params.entries.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }
    }

    // the declared option nearest the picked quality; a service that declares none gets tidal's names, which most of them use
    private fun qualityValue(tier: String): String {
        val setting = manifest.setting(QualityKey)
        val offered = setting?.values.orEmpty()
        if (offered.isEmpty()) return TidalNames.getValue(tier)
        val hiRes = offered.filter { HiResWords.containsMatchIn(it) }
        val lossless = offered.filter { LosslessWords.containsMatchIn(it) && it !in hiRes }
        val lossy = offered.filter { it !in hiRes && it !in lossless }
        val high = lossy.firstOrNull { HighWords.containsMatchIn(it) }
        return when (tier) {
            OptionHiRes -> hiRes.firstOrNull() ?: lossless.firstOrNull()
            OptionLossless -> lossless.firstOrNull() ?: hiRes.firstOrNull()
            OptionLow -> lossy.firstOrNull { LowWords.containsMatchIn(it) } ?: high
            else -> high ?: lossy.firstOrNull()
        } ?: setting?.defaultValue ?: offered.first()
    }

    // the atmos switch in the service's own words; a service with no such switch only hears about atmos when it's wanted
    private fun atmosValue(atmos: Boolean): String? {
        val setting = manifest.setting(AtmosKey)
        val offered = setting?.values.orEmpty()
        return when {
            atmos -> offered.firstOrNull { AtmosOnWords.matches(it) } ?: "auto"
            setting != null -> offered.firstOrNull { AtmosOffWords.matches(it) } ?: "0"
            else -> null
        }
    }

    private fun tracksIn(text: String, asked: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val tracks = root["tracks"] as? JsonArray ?: return emptyList()
        return tracks.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            val id = t.string("id") ?: return@mapNotNull null
            // seconds, though a few services send milliseconds
            val duration = (t["duration"] as? JsonPrimitive)?.doubleOrNull?.let { if (it > 10_000) it.toLong() else (it * 1000).toLong() }
            CatalogTrack(
                id = id,
                title = t.string("title") ?: return@mapNotNull null,
                artist = t.string("artist").orEmpty(),
                album = t.string("album"),
                durationMs = duration,
                isrc = t.string("isrc"),
            ).also { track ->
                t.string("streamURL")?.let { directStreams["$asked|${track.id}"] = it }
                if (t.marksAtmos()) atmosRows += track.id
            }
        }
    }

    private fun streamFor(track: CatalogTrack, tier: String, atmos: Boolean): SourceStream? {
        val rowIsAtmos = track.id in atmosRows
        directStreams["$tier|$atmos|${track.id}"]?.let { url -> return streamOf(url, JsonObject(emptyMap()), rowIsAtmos) }
        val response = runCatching { host.http(HttpRequest("$base/stream/${enc(track.id)}?${paramsFor(tier, atmos)}", headers = headers)) }.getOrNull()
        if (response == null || !response.ok) return null
        val answer = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull() ?: return null
        // a locked stream would need keys mochy doesn't have
        if ((answer["encrypted"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull !in setOf(null, "", "false", "none")) } == true) return null
        val url = answer.string("url")?.takeIf { it.startsWith("http") } ?: return null
        return streamOf(url, answer, rowIsAtmos)
    }

    private fun streamOf(url: String, answer: JsonObject, rowIsAtmos: Boolean): SourceStream {
        val stated = listOfNotNull(answer.string("manifest"), answer.string("mediaType"), answer.string("format")).joinToString(" ").lowercase()
        val format = when {
            "hls" in stated || "m3u8" in stated || ".m3u8" in url -> StreamFormat.Hls
            "dash" in stated || "mpd" in stated || ".mpd" in url -> StreamFormat.Dash
            else -> StreamFormat.Progressive
        }
        val named = codecName(answer.string("codec") ?: answer.string("fileCodec") ?: answer.string("format") ?: url.substringBefore('?').substringAfterLast('.', ""))
        // an atmos dash stream often names no codec at all, only its mix says what it is
        val codec = if ((rowIsAtmos || answer.marksAtmos()) && (named == null || named == "E-AC-3")) AtmosCodec else named
        // some services only write "24-bit 96 kHz" in their quality text, and the decoder check needs the rate
        val qualityText = listOfNotNull(answer.string("quality"), answer.string("streamQuality"), answer.string("audioQuality")).joinToString(" ")
        val rate = (answer["sampleRate"] as? JsonPrimitive)?.doubleOrNull?.let { if (it < 1000) (it * 1000).toInt() else it.toInt() }
            ?: KhzLabel.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toInt() }
        val bits = (answer["bitDepth"] as? JsonPrimitive)?.doubleOrNull?.toInt()
            ?: BitsLabel.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()
        val quality = listOfNotNull(answer.string("quality"), answer.string("audioQuality")).firstOrNull { it.isNotBlank() }
        val label = quality ?: listOfNotNull(codec, bits?.let { "$it-bit" }, rate?.let { "${it / 1000.0} kHz".replace(".0 ", " ") }).joinToString(" · ").ifEmpty { "Stream" }
        return SourceStream(
            url = url,
            format = format,
            sourceLabel = info.name,
            qualityLabel = label,
            codec = codec,
            sampleRateHz = rate,
            matched = MatchedTrack("", "", "", null, null, 0f),
        )
    }

    private fun codecName(raw: String): String? = when (raw.lowercase().trim()) {
        "flac" -> "FLAC"
        "alac" -> "ALAC"
        "aac", "mp4a", "m4a", "mp4" -> "AAC"
        "opus", "webm" -> "Opus"
        "mp3", "mpeg" -> "MP3"
        // the player's badge names, so the decoder check and the badge read the same word
        "eac3-joc", "eac3_joc", "ec3-joc", "ec+3", "atmos" -> AtmosCodec
        "eac3", "ec-3", "e-ac-3" -> "E-AC-3"
        "ac3", "ac-3" -> "AC-3"
        else -> null
    }

    // atmos shows up in a flag, a mix list, or a quality name, depending on the service and on row or stream
    private fun JsonObject.marksAtmos(): Boolean {
        if ((this["atmos"] as? JsonPrimitive)?.booleanOrNull == true) return true
        val modes = (this["audioModes"] as? JsonArray)?.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        val text = listOfNotNull(string("audioMode"), modes, string("audioQuality"), string("quality"), string("streamQuality"), string("codec"), string("format"))
        return AtmosWords.containsMatchIn(text.joinToString(" "))
    }

    // ---- lyrics ---------------------------------------------------------------------------------

    override suspend fun lyrics(track: TrackQuery): String? {
        if (ModuleFeature.Lyrics !in adds) return null
        val params = buildList {
            add("title=${enc(track.title)}")
            add("artist=${enc(track.artists.joinToString(", "))}")
            track.album?.let { add("album=${enc(it)}") }
            track.durationMs?.let { add("duration=${it / 1000}") }
        }.joinToString("&")
        val response = runCatching { host.http(HttpRequest("$base/lyrics?$params", headers = headers)) }.getOrNull()
        if (response == null || !response.ok) return null
        val answer = runCatching { json.parseToJsonElement(response.text) as? JsonObject }.getOrNull()
            ?: return response.text.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
        // timed lyrics first, plain ones when that's all there is
        return listOf("syncedLyrics", "lrc", "lyrics", "plainLyrics").firstNotNullOfOrNull { answer.string(it)?.takeIf(String::isNotBlank) }
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private companion object {
        const val MissMemoryMs = 30L * 60 * 1000
        const val UnreachableRetryMs = 10L * 60 * 1000
        val headers = mapOf("Accept" to "application/json", "User-Agent" to "Mochy")

        // the id the audio quality page looks for to list a module's qualities
        const val ChoiceQuality = "quality"
        const val OptionLossless = "lossless"
        const val OptionHiRes = "hires"
        const val OptionHigh = "high"
        const val OptionLow = "low"
        const val ChoiceMix = "mix"
        const val OptionStereo = "stereo"
        const val OptionAtmos = "atmos"

        const val QualityKey = "quality"
        const val AtmosKey = "atmos"
        const val AtmosCodec = "DOLBY ATMOS"

        val TidalNames = mapOf(OptionLossless to "LOSSLESS", OptionHiRes to "HI_RES_LOSSLESS", OptionHigh to "HIGH", OptionLow to "LOW")
        val HiResWords = Regex("""hi[-_ ]?res|24[-_ ]?bit|max""", RegexOption.IGNORE_CASE)
        val LosslessWords = Regex("""lossless|flac|alac|hi[-_ ]?fi|\bcd\b|16[-_ ]?bit""", RegexOption.IGNORE_CASE)
        val HighWords = Regex("""high|320|256|normal|standard""", RegexOption.IGNORE_CASE)
        val LowWords = Regex("""low|96|128|min""", RegexOption.IGNORE_CASE)
        val AtmosOnWords = Regex("""auto|on|1|true|yes|always""", RegexOption.IGNORE_CASE)
        val AtmosOffWords = Regex("""off|0|false|no|none|never|stereo""", RegexOption.IGNORE_CASE)
        val AtmosWords = Regex("""atmos|joc|ec\+3""", RegexOption.IGNORE_CASE)
        val KhzLabel = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
        val BitsLabel = Regex("""(\d{2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
    }
}
