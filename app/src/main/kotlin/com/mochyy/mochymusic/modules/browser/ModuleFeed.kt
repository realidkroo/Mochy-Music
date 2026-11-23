// the module browser's side of mochy's module feed: signing in with discord through it, the list of modules from mochy's bar, and their zips

package com.mochyy.mochymusic.modules.browser

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import com.mochyy.mochymusic.constants.SimulateForkKey
import com.mochyy.mochymusic.ui.component.DiscordMarkdown
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** one module in mochy's bar; [id] is its forum post, which is how its zip is asked for */
data class FeedModule(
    val id: String,
    val name: String,
    val moduleId: String?,
    val version: String?,
    val author: String?,
    /** the maker's words with discord's formatting marks still in, for the store page to style */
    val description: String,
    /** the same words with the marks taken out, for a card's two lines and the search */
    val blurb: String,
    val kind: FeedKind,
    /** the post's tags in the forum, which are the store's categories */
    val tags: List<String>,
    val paid: Boolean,
    /** paid, and this person can't see its forum in discord yet */
    val locked: Boolean,
    val icon: String?,
    /** the store page's wide picture, set with /publish or /banner in discord */
    val banner: String?,
    /** the store page's screenshots, set with /preview */
    val previews: List<String>,
    val install: FeedInstall,
    /** a pack's modules, each added on its own; empty for anything else */
    val pack: List<PackItem>,
    /** the zip's size in bytes (a pack's zips together), null for a linked module */
    val size: Long?,
    /** when it last changed, in milliseconds */
    val updated: Long?,
    /** where a linked module is added from, or the page a web one opens */
    val link: String?,
    val postUrl: String,
)

/** how the store gets a module: its post's zip, a link added like a pasted one, a web page it opens, or a pack of several */
enum class FeedInstall { Zip, Link, Web, Pack }

/** one module in a pack: a zip in the pack's post, or a link it's added from like a pasted one */
data class PackItem(
    val id: String,
    val isLink: Boolean,
    val name: String,
    val moduleId: String?,
    val version: String?,
    val size: Long?,
    val icon: String?,
    val link: String?,
)

enum class FeedKind { Look, Player, Source, Other }

/** who's signed in, for the chip at the top; [member] is whether they're in mochy's bar */
data class FeedPerson(val name: String, val avatar: String?, val member: Boolean, val invite: String?)

sealed interface FeedResult {
    /** [chart] is best from community in order, [categories] the forum's tags the chips offer */
    data class Modules(val person: FeedPerson, val modules: List<FeedModule>, val chart: List<FeedModule>, val categories: List<String>) : FeedResult
    data class NotMember(val person: FeedPerson?, val invite: String?) : FeedResult
    data object SignedOut : FeedResult
    /** the feed hasn't read the forums yet, a minute or so after it's first set up */
    data object WarmingUp : FeedResult
    data object Offline : FeedResult
    /** a fork or changed copy: the feed only answers official builds */
    data object Unofficial : FeedResult
    /** this build has no feed address yet */
    data object NotSetUp : FeedResult
}

@Singleton
class ModuleFeed @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val client by lazy {
        OkHttpClient.Builder()
            .dispatcher(com.mochyy.mochymusic.utils.AppProxy.dispatcher)
            .connectionPool(com.mochyy.mochymusic.utils.AppProxy.connectionPool)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val _session = MutableStateFlow(readSession())

    val session: StateFlow<String?> = _session.asStateFlow()

    private fun readSession(): String? {
        val raw = prefs.getString(SessionKey, null) ?: return null
        val opened = com.mochyy.mochymusic.utils.SecretVault.open(raw, com.mochyy.mochymusic.utils.SecretVault.ModuleFeedDomain)
        if (opened == null) {
            prefs.edit { remove(SessionKey) }
            return null
        }
        if (com.mochyy.mochymusic.utils.SecretVault.needsUpgrade(raw)) {
            prefs.edit { putString(SessionKey, com.mochyy.mochymusic.utils.SecretVault.seal(opened, com.mochyy.mochymusic.utils.SecretVault.ModuleFeedDomain)) }
        }
        com.mochyy.mochymusic.modules.security.SecretGuard.register("module_feed", opened)
        return opened
    }

    val isSetUp: Boolean get() = FeedUrl.isNotBlank()

    private fun isAllowed(): Boolean = StoreSignature.isOfficial(context) && !context.dataStore.get(SimulateForkKey, false)

    /** the page to sign in on; the check value is kept so only the sign-in started here is taken back; null on unofficial builds */
    fun signInUri(): Uri? {
        if (!isSetUp || !isAllowed()) return null
        val state = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        // the sign-in page opens in a browser, so the build's signature rides along in the link instead of a header
        val (time, signature) = StoreSignature.sign(context, "GET", "/login", state) ?: return null
        prefs.edit { putString(StateKey, state) }
        return Uri.parse("$FeedUrl/login").buildUpon()
            .appendQueryParameter("state", state)
            .appendQueryParameter("t", time)
            .appendQueryParameter("sig", signature)
            .build()
    }

    /** back from the browser with mochy-modules://signed-in; true when it carried a session from the sign-in started here */
    fun finishSignIn(redirect: Uri): Boolean {
        if (!isSetUp || !isAllowed()) {
            prefs.edit { remove(StateKey) }
            return false
        }
        if (redirect.scheme != "mochy-modules" || redirect.host != "signed-in") return false
        val expected = prefs.getString(StateKey, null) ?: return false
        prefs.edit { remove(StateKey) }
        val session = redirect.getQueryParameter("session")?.takeIf { SessionPattern.matches(it) }
        if (redirect.getQueryParameter("state") != expected || session == null) return false
        prefs.edit { putString(SessionKey, com.mochyy.mochymusic.utils.SecretVault.seal(session, com.mochyy.mochymusic.utils.SecretVault.ModuleFeedDomain)) }
        com.mochyy.mochymusic.modules.security.SecretGuard.register("module_feed", session)
        _session.value = session
        return true
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = _session.value
        forgetSession()
        if (session != null) runCatching { call(Request.Builder().url("$FeedUrl/signout").post(ByteArray(0).toRequestBody()), session)?.close() }
    }

    suspend fun load(): FeedResult = withContext(Dispatchers.IO) {
        if (!isSetUp) return@withContext FeedResult.NotSetUp
        // a build without the official key and signature would only be turned away, so it doesn't ask; experimental can pretend to be one, to see what a fork gets
        if (!isAllowed()) return@withContext FeedResult.Unofficial
        val session = _session.value ?: return@withContext FeedResult.SignedOut
        val response = try {
            call(Request.Builder().url("$FeedUrl/modules"), session) ?: return@withContext FeedResult.Unofficial
        } catch (e: IOException) {
            return@withContext FeedResult.Offline
        }
        response.use {
            val body = runCatching { JSONObject(it.body.string()) }.getOrNull() ?: JSONObject()
            when (it.code) {
                200 -> modulesPage(body)
                401 -> FeedResult.SignedOut.also { forgetSession() }
                403 -> if (body.optString("error") == "unofficial_build") {
                    forgetSession()
                    FeedResult.Unofficial
                } else {
                    FeedResult.NotMember(personOf(body), body.optString("invite").takeIf { link -> link.startsWith("https://") })
                }
                503 -> FeedResult.WarmingUp
                else -> FeedResult.Offline
            }
        }
    }

    /** the module's zip, or one of a pack's by its [item] id, into the cache, ready to be checked like a zip picked from the phone; null when it couldn't be fetched */
    suspend fun download(postId: String, item: String? = null): File? = withContext(Dispatchers.IO) {
        if (!isSetUp || !isAllowed()) return@withContext null
        if (!PostIdPattern.matches(postId) || (item != null && !ItemIdPattern.matches(item))) return@withContext null
        val session = _session.value ?: return@withContext null
        val folder = File(context.cacheDir, "module_browser").apply { mkdirs() }
        val file = File(folder, if (item != null) "$postId-$item.zip" else "$postId.zip")
        val url = if (item != null) "$FeedUrl/modules/$postId/zip?item=${Uri.encode(item)}" else "$FeedUrl/modules/$postId/zip"
        runCatching {
            val response = call(Request.Builder().url(url), session) ?: return@runCatching null
            response.use { res ->
                if (res.code == 403) {
                    val body = runCatching { JSONObject(res.body.string()) }.getOrNull()
                    if (body?.optString("error") == "unofficial_build") forgetSession()
                    return@use null
                }
                if (!res.isSuccessful) return@use null
                // cap zip downloads so a busted or shady response can't nuke storage
                val buffer = ByteArray(16 * 1024)
                var written = 0L
                val ok = file.outputStream().use { out ->
                    val input = res.body.byteStream()
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) return@use true
                        written += n
                        if (written > MaxZipBytes) return@use false
                        out.write(buffer, 0, n)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                }
                if (!ok) {
                    file.delete()
                    return@use null
                }
                file
            }
        }.getOrNull()
    }

    // every call carries the build's native signature over what it asks for, and never goes out unsigned
    private fun call(request: Request.Builder, session: String): Response? {
        if (!isAllowed()) return null
        val built = request.header("Authorization", "Bearer $session").build()
        val (time, signature) = StoreSignature.sign(context, built.method, built.url.encodedPath, session) ?: return null
        val signed = built.newBuilder().header("X-Mochy-Time", time).header("X-Mochy-Sign", signature).build()
        return client.newCall(signed).execute()
    }

    private fun forgetSession() {
        prefs.edit { remove(SessionKey) }
        com.mochyy.mochymusic.modules.security.SecretGuard.clear("module_feed")
        _session.value = null
    }

    private fun personOf(json: JSONObject?): FeedPerson? = json?.optString("name")?.takeIf { it.isNotBlank() }?.let { name ->
        FeedPerson(name, json.optString("avatar").takeIf { it.startsWith("https://") }, json.optBoolean("member"), json.optString("invite").takeIf { it.startsWith("https://") })
    }

    private fun modulesPage(body: JSONObject): FeedResult.Modules {
        val modules = modulesOf(body)
        val byId = modules.associateBy { it.id }
        val chart = body.optJSONArray("chart")?.let { ids -> (0 until ids.length()).mapNotNull { byId[ids.optString(it)] } }.orEmpty()
        return FeedResult.Modules(
            person = personOf(body.optJSONObject("me")) ?: FeedPerson("", null, member = true, invite = null),
            modules = modules,
            chart = chart,
            categories = body.optJSONArray("categories")?.let { names -> (0 until names.length()).map { names.optString(it) } }.orEmpty().filter { it.isNotBlank() },
        )
    }

    private fun packItemOf(item: JSONObject?): PackItem? {
        val id = item?.optString("id")?.takeIf { it.isNotBlank() } ?: return null
        return PackItem(
            id = id,
            isLink = item.optString("kind") == "link",
            name = item.optString("name").ifBlank { id },
            moduleId = item.optString("moduleId").takeIf { it.isNotBlank() && !item.isNull("moduleId") },
            version = item.optString("version").takeIf { it.isNotBlank() && !item.isNull("version") },
            size = item.optLong("size", -1).takeIf { it >= 0 && !item.isNull("size") },
            icon = item.optString("icon").takeIf { it.startsWith("https://") },
            link = item.optString("link").takeIf { it.startsWith("https://") },
        )
    }

    private fun modulesOf(body: JSONObject): List<FeedModule> {
        val list = body.optJSONArray("modules") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FeedModule(
                id = id,
                name = item.optString("name").ifBlank { id },
                moduleId = item.optString("moduleId").takeIf { it.isNotBlank() && !item.isNull("moduleId") },
                version = item.optString("version").takeIf { it.isNotBlank() && !item.isNull("version") },
                author = item.optString("author").takeIf { it.isNotBlank() && !item.isNull("author") },
                description = item.optString("description"),
                // worked out here on the feed's own thread, so a list of cards never parses while it scrolls
                blurb = DiscordMarkdown.plain(item.optString("description")),
                kind = when (item.optString("kind")) {
                    "look" -> FeedKind.Look
                    "player" -> FeedKind.Player
                    "source" -> FeedKind.Source
                    else -> FeedKind.Other
                },
                tags = item.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it) } }.orEmpty(),
                paid = item.optBoolean("paid"),
                locked = item.optBoolean("locked"),
                icon = item.optString("icon").takeIf { it.startsWith("https://") },
                banner = item.optString("banner").takeIf { it.startsWith("https://") },
                previews = item.optJSONArray("previews")?.let { list ->
                    (0 until list.length()).map { list.optString(it) }.filter { it.startsWith("https://") }
                }.orEmpty(),
                install = when (item.optString("install")) {
                    "link" -> FeedInstall.Link
                    "web" -> FeedInstall.Web
                    "pack" -> FeedInstall.Pack
                    else -> FeedInstall.Zip
                },
                pack = item.optJSONArray("pack")?.let { items -> (0 until items.length()).mapNotNull { packItemOf(items.optJSONObject(it)) } }.orEmpty(),
                link = item.optString("link").takeIf { it.startsWith("https://") },
                size = item.optLong("size", -1).takeIf { it >= 0 && !item.isNull("size") },
                updated = item.optLong("updated", -1).takeIf { it > 0 },
                postUrl = item.optString("postUrl").takeIf { it.startsWith("https://") }.orEmpty(),
            )
        }
    }

    companion object {
        // the worker's address (see the mochy-module-feed project); blank makes the browser say it isn't set up
        const val FeedUrl = "https://mochy-module-feed.mochyy.workers.dev"

        private const val SessionKey = "session"
        private const val PrefsName = "module_browser"
        private const val StateKey = "state"
        private const val MaxZipBytes = 40L * 1024 * 1024
        private val SessionPattern = Regex("^[0-9a-f]{64}$")
        private val PostIdPattern = Regex("^\\d+$")
        private val ItemIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}
