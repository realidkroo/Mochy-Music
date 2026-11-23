// SpotifyCanvasProvider.kt

package com.mochyy.mochymusic.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mochyy.mochymusic.utils.followAppProxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// canvas from spotify through the account signed in on the spotify canvas page, that sign-in only lives in this app's web views
object SpotifyCanvasProvider {
    const val PlayerOrigin = "https://open.spotify.com"
    private const val SignedInCookie = "sp_dc"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"
    private const val BRIDGE = "MochySpotifyBridge"
    // last hash that worked, used when we cant find a newer one
    private const val KNOWN_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    private const val SEARCH_HASH = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
    // a sign-in that gave no token waits this long before the next try, so a dead one doesn't open a web view every song
    private const val RetryAfterMs = 30 * 60_000L
    // a song change is busy enough already, the hidden web view waits until the player has settled
    private const val HarvestDelayMs = 3_000L
    // fetched again this long before it runs out, so a long session never plays a song without one
    private const val RefreshEarlyMs = 5 * 60_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val http get() = com.mochyy.mochymusic.utils.AppProxy.sharedClient
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hits = ConcurrentHashMap<String, CanvasArtwork>()
    // songs with no canvas, so we dont ask spotify again every time they play
    private val misses = ConcurrentHashMap.newKeySet<String>()

    // frees cached canvas lookups when the ui hides so they don't grow without bound
    fun trimMemory() {
        hits.clear()
        misses.clear()
    }

    private class Harvest(val token: String, val expiresAt: Long, val clientId: String?)

    // only read and written on the main thread, so two song changes never open two web views
    private var harvesting = false
    @Volatile private var failedFor: String? = null
    @Volatile private var failedAt = 0L

    @Volatile private var token: String? = null
    @Volatile private var tokenFor = ""
    @Volatile private var tokenExp = 0L
    @Volatile private var clientId: String? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExp = 0L
    @Volatile private var clientVersion: String? = null
    @Volatile private var deviceId: String? = null
    @Volatile private var hashValue: String? = null
    @Volatile private var hashAt = 0L

    // the sign-in cookie spotify set in this app's web views, null while nobody is signed in
    fun signedInCookie(): String? = runCatching {
        CookieManager.getInstance().getCookie(PlayerOrigin).orEmpty().split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=') == SignedInCookie }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // right after signing in, so the first song already finds a token waiting
    fun warmUp(context: Context) {
        val cookie = signedInCookie() ?: return
        failedFor = null
        refresh(context.applicationContext, cookie, delayMs = 0L)
    }

    // signing out drops the token and every miss, the next account may have other luck
    fun forget() {
        token = null
        tokenFor = ""
        tokenExp = 0L
        cachedClientToken = null
        failedFor = null
        misses.clear()
    }

    // never waits for a token: without a fresh one this song skips spotify and one is fetched in the background
    suspend fun getBySongArtist(context: Context, song: String, artist: String, durationSec: Int?): CanvasArtwork? {
        val cookie = signedInCookie() ?: return null
        val key = "$song|$artist|${durationSec ?: 0}"
        hits[key]?.let { return it }
        if (key in misses) return null

        val now = System.currentTimeMillis()
        val t = token?.takeIf { tokenFor == cookie && now < tokenExp - 30_000 }
        if (t == null || now > tokenExp - RefreshEarlyMs) refresh(context.applicationContext, cookie, HarvestDelayMs)
        if (t == null) return null

        return try {
            val ct = clientToken()
            val url = searchTrack(t, ct, song, artist, durationSec)?.let { canvasUrl(it, t, ct) }
            if (url == null) {
                misses.add(key)
                null
            } else {
                CanvasArtwork(name = song, artist = artist, videoUrl = url).also { hits[key] = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // network hiccup or spotify changed something, just no canvas this time
            null
        }
    }

    private fun refresh(context: Context, cookie: String, delayMs: Long) {
        if (failedFor == cookie && System.currentTimeMillis() - failedAt < RetryAfterMs) return
        main.launch {
            if (harvesting) return@launch
            harvesting = true
            try {
                delay(delayMs)
                val h = harvest(context)
                if (h == null) {
                    failedFor = cookie
                    failedAt = System.currentTimeMillis()
                    return@launch
                }
                token = h.token
                tokenFor = cookie
                tokenExp = h.expiresAt
                h.clientId?.let { clientId = it }
                cachedClientToken = null
            } finally {
                harvesting = false
            }
        }
    }

    // plain requests with sp_dc get blocked, so we let the real web player ask for the token and just listen to it
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvest(context: Context): Harvest? {
        val done = CompletableDeferred<Harvest?>()
        // only spotify's own saved data goes, so the page asks for a new token instead of reusing one
        runCatching { WebStorage.getInstance().deleteOrigin(PlayerOrigin) }
        var view: WebView? = null
        return try {
            view = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(Bridge(done), BRIDGE)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView, url: String?, icon: Bitmap?) {
                        v.evaluateJavascript(HOOK, null)
                    }

                    override fun onPageFinished(v: WebView, url: String?) {
                        v.evaluateJavascript(HOOK, null)
                    }
                }
                loadUrl("$PlayerOrigin/")
            }
            withTimeoutOrNull(20_000) { done.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching {
                view?.removeJavascriptInterface(BRIDGE)
                view?.stopLoading()
                view?.destroy()
            }
        }
    }

    private class Bridge(private val done: CompletableDeferred<Harvest?>) {
        @JavascriptInterface
        fun onTokenPayload(payload: String?) {
            if (payload.isNullOrBlank() || done.isCompleted) return
            runCatching {
                val o = json.parseToJsonElement(payload).jsonObject
                val t = o["accessToken"]?.jsonPrimitive?.contentOrNull
                val anonymous = o["isAnonymous"]?.jsonPrimitive?.booleanOrNull ?: false
                // anonymous token means the cookie is dead or wrong, wait for a real one
                if (t.isNullOrBlank() || anonymous) return
                val exp = o["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.longOrNull
                    ?.takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + 3_600_000)
                done.complete(Harvest(t, exp, o["clientId"]?.jsonPrimitive?.contentOrNull))
            }
        }
    }

    // hooks fetch and xhr so we see the /api/token response the page gets
    private val HOOK = """
        (function(){
          if(window.__mh)return;window.__mh=true;
          var r=function(b){try{$BRIDGE.onTokenPayload(b)}catch(e){}};
          var t=function(u){try{return String(u).indexOf('/api/token')!==-1}catch(e){return false}};
          var f=window.fetch;
          if(f){window.fetch=function(i,n){var u=(i&&i.url)?i.url:i;var p=f.apply(this,arguments);if(t(u)){try{p.then(function(s){s.clone().text().then(r).catch(function(){})}).catch(function(){})}catch(e){}}return p}}
          var o=XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open=function(m,u){this.__u=u;return o.apply(this,arguments)};
          var s=XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send=function(){var x=this;try{x.addEventListener('load',function(){if(t(x.__u)){try{r(x.responseText)}catch(e){}}})}catch(e){}return s.apply(this,arguments)}
        })();
    """.trimIndent()

    private fun call(req: Request): String? = runCatching {
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    private fun get(url: String, headers: Map<String, String>): String? =
        call(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    private fun authHeaders(token: String, ct: String?): Map<String, String> {
        val h = mutableMapOf("Authorization" to "Bearer $token", "User-Agent" to UA)
        if (ct != null) h["Client-Token"] = ct
        return h
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    // grabs the web player version and a device id, the client token request wants both
    private fun loadSession() {
        val req = Request.Builder().url(PlayerOrigin).header("User-Agent", UA).build()
        runCatching {
            http.newCall(req).execute().use { r ->
                val html = r.body?.string().orEmpty()
                deviceId = r.headers("Set-Cookie")
                    .firstNotNullOfOrNull { Regex("sp_t=([^;]+)").find(it)?.groupValues?.get(1) }
                    ?: UUID.randomUUID().toString()
                val cfg = Regex("<script id=\"appServerConfig\" type=\"text/plain\">([^<]+)</script>")
                    .find(html)?.groupValues?.get(1)
                if (cfg != null) {
                    val raw = String(android.util.Base64.decode(cfg, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    clientVersion = parse(raw)?.get("clientVersion").str()
                }
            }
        }
    }

    // this mimics what the web player does, if spotify changes it this is the first thing to break
    private fun clientToken(): String? {
        val now = System.currentTimeMillis()
        cachedClientToken?.takeIf { now < clientTokenExp - 30_000 }?.let { return it }
        val id = clientId ?: return null
        if (clientVersion == null) loadSession()
        val version = clientVersion ?: return null
        val body = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", version)
                put("client_id", id)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "android")
                    put("os_version", android.os.Build.VERSION.RELEASE.orEmpty())
                    put("device_id", deviceId ?: UUID.randomUUID().toString())
                    put("device_type", "smartphone")
                }
            }
        }.toString()
        val req = Request.Builder().url("https://clienttoken.spotify.com/v1/clienttoken")
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .build()
        val o = call(req)?.let { parse(it) } ?: return null
        if (o["response_type"].str() != "RESPONSE_GRANTED_TOKEN_RESPONSE") return null
        val g = o["granted_token"].obj() ?: return null
        val tok = g["token"].str() ?: return null
        val ttl = g["expires_after_seconds"].str()?.toLongOrNull() ?: 3600L
        cachedClientToken = tok
        clientTokenExp = now + ttl * 1000
        return tok
    }

    private fun searchTrack(token: String, ct: String?, song: String, artist: String, durationSec: Int?): String? {
        val term = "$song $artist"
        // pathfinder first, the rest api is the fallback when we have no client token
        if (ct != null) pathfinderSearch(token, ct, term, song)?.let { return it }
        return restSearch(token, ct, term, song, artist, durationSec)
    }

    private fun pathfinderSearch(token: String, ct: String, term: String, song: String): String? {
        val variables = buildJsonObject {
            put("searchTerm", term)
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }.toString()
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", SEARCH_HASH)
            }
        }.toString()
        val url = "https://api-partner.spotify.com/pathfinder/v1/query".toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks")
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build().toString()
        val headers = authHeaders(token, ct) + mapOf("App-platform" to "WebPlayer", "Accept" to "application/json")
        val root = get(url, headers)?.let { parse(it) } ?: return null
        val items = root["data"].obj()?.get("searchV2").obj()?.get("tracksV2").obj()?.get("items") as? JsonArray ?: return null
        for (item in items) {
            val d = item.obj()?.get("item").obj()?.get("data").obj() ?: continue
            val name = d["name"].str()
            if (name != null && !CanvasMatch.sameSong(name, song)) continue
            return d["uri"].str() ?: d["id"].str()?.let { "spotify:track:$it" } ?: continue
        }
        return null
    }

    // here we also check artist and duration, pathfinder results dont give us enough to do that
    private fun restSearch(token: String, ct: String?, term: String, song: String, artist: String, durationSec: Int?): String? {
        val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", term)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build().toString()
        val root = get(url, authHeaders(token, ct))?.let { parse(it) } ?: return null
        val items = root["tracks"].obj()?.get("items") as? JsonArray ?: return null
        for (item in items) {
            val o = item.obj() ?: continue
            val name = o["name"].str() ?: continue
            val artists = (o["artists"] as? JsonArray)?.mapNotNull { it.obj()?.get("name").str() }.orEmpty()
            val sec = (o["duration_ms"] as? JsonPrimitive)?.doubleOrNull?.div(1000)
            if (!CanvasMatch.sameSong(name, song)) continue
            if (artists.none { CanvasMatch.sameArtist(it, artist) }) continue
            if (!CanvasMatch.closeDuration(sec, durationSec)) continue
            return o["uri"].str() ?: continue
        }
        return null
    }

    // spotify rotates this hash now and then. we remember the one we found for 12h, retry a failed lookup after 30min
    @Synchronized
    private fun canvasHash(force: Boolean): String {
        val now = System.currentTimeMillis()
        val cur = hashValue
        val ttl = if (cur != null) 43_200_000L else 1_800_000L
        if (!force && hashAt != 0L && now - hashAt < ttl) return cur ?: KNOWN_HASH
        hashAt = now
        lookUpHash()?.let { hashValue = it }
        return hashValue ?: KNOWN_HASH
    }

    // digs the canvas query hash out of the web player's js bundles
    private fun lookUpHash(): String? {
        val html = get("$PlayerOrigin/", mapOf("User-Agent" to UA)) ?: return null
        val declaration = Regex("[\"'](\\w+)[\"']\\s*,\\s*[\"']query[\"']\\s*,\\s*[\"']([0-9a-f]{64})[\"']")
        val scripts = Regex("https://open\\.spotifycdn\\.com/cdn/build/web-player/[A-Za-z0-9._~-]+\\.js")
            .findAll(html).map { it.value }.distinct().take(8)
        for (script in scripts) {
            val body = get(script, mapOf("User-Agent" to UA)) ?: continue
            declaration.findAll(body).firstOrNull { it.groupValues[1] == "canvas" }?.let { return it.groupValues[2] }
        }
        return null
    }

    private fun canvasUrl(uri: String, token: String, ct: String?): String? {
        for (attempt in 0..1) {
            val body = buildJsonObject {
                put("operationName", "canvas")
                putJsonObject("variables") { put("trackUri", uri) }
                putJsonObject("extensions") {
                    putJsonObject("persistedQuery") {
                        put("version", 1)
                        put("sha256Hash", canvasHash(attempt == 1))
                    }
                }
            }.toString()
            val req = Request.Builder().url("https://api-partner.spotify.com/pathfinder/v2/query")
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { authHeaders(token, ct).forEach { (k, v) -> header(k, v) } }
                .header("App-platform", "WebPlayer")
                .header("Accept", "application/json")
                .build()
            val root = call(req)?.let { parse(it) } ?: break
            val data = root["data"].obj()
            if (data == null) {
                // stale hash, go around once more with a fresh one
                val stale = root.toString().contains("PersistedQueryNotFound", ignoreCase = true)
                if (stale && attempt == 0) continue
                break
            }
            val url = data["trackUnion"].obj()?.get("canvas").obj()?.get("url").str()
                ?.takeIf { it.startsWith("https://") && Regex("\\.mp4(?:[?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (url != null) return url
            break
        }
        return canvazUrl(uri, token, ct)
    }

    // old canvaz endpoint, the request is a tiny protobuf so we write the bytes by hand instead of pulling in a lib
    private fun canvazUrl(uri: String, token: String, ct: String?): String? {
        val u = uri.toByteArray(Charsets.UTF_8)
        val track = byteArrayOf(0x0A, u.size.toByte()) + u
        val body = byteArrayOf(0x0A, track.size.toByte()) + track
        val req = Request.Builder().url("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases")
            .post(body.toRequestBody("application/protobuf".toMediaType()))
            .apply { authHeaders(token, ct).forEach { (k, v) -> header(k, v) } }
            .header("Accept", "application/protobuf")
            .header("Accept-Language", "en")
            .header("User-Agent", APP_UA)
            .build()
        val bytes = runCatching {
            http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull() ?: return null
        // not worth decoding the whole reply, we only need the mp4 link in it
        return Regex("https://[^\"'\\s\\u0000-\\u001F]+\\.cnvs\\.mp4").find(String(bytes, Charsets.ISO_8859_1))?.value
    }
}
