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
import okhttp3.RequestBody.Companion.toRequestBody
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
    val description: String,
    val kind: FeedKind,
    val paid: Boolean,
    /** paid, and this person can't see its forum in discord yet */
    val locked: Boolean,
    val icon: String?,
    val postUrl: String,
)

enum class FeedKind { Look, Player, Source, Other }

/** who's signed in, for the chip at the top; [member] is whether they're in mochy's bar */
data class FeedPerson(val name: String, val avatar: String?, val member: Boolean, val invite: String?)

sealed interface FeedResult {
    data class Modules(val person: FeedPerson, val modules: List<FeedModule>) : FeedResult
    data class NotMember(val person: FeedPerson?, val invite: String?) : FeedResult
    data object SignedOut : FeedResult
    /** the feed hasn't read the forums yet, a minute or so after it's first set up */
    data object WarmingUp : FeedResult
    data object Offline : FeedResult
    /** this build has no feed address yet */
    data object NotSetUp : FeedResult
}

@Singleton
class ModuleFeed @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("module_browser", Context.MODE_PRIVATE)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    private val _session = MutableStateFlow(prefs.getString(SessionKey, null))

    /** the session the feed gave back, null while signed out; the browser reloads when it changes */
    val session: StateFlow<String?> = _session.asStateFlow()

    val isSetUp: Boolean get() = FeedUrl.isNotBlank()

    /** the page to sign in on; the check value is kept so only the sign-in started here is taken back */
    fun signInUri(): Uri {
        val state = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        prefs.edit { putString(StateKey, state) }
        return Uri.parse("$FeedUrl/login").buildUpon().appendQueryParameter("state", state).build()
    }

    /** back from the browser with mochy-modules://signed-in; true when it carried a session from the sign-in started here */
    fun finishSignIn(redirect: Uri): Boolean {
        val expected = prefs.getString(StateKey, null) ?: return false
        val session = redirect.getQueryParameter("session")?.takeIf { SessionPattern.matches(it) }
        if (redirect.getQueryParameter("state") != expected || session == null) return false
        prefs.edit {
            remove(StateKey)
            putString(SessionKey, session)
        }
        _session.value = session
        return true
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = _session.value
        forgetSession()
        if (session != null) runCatching { call(Request.Builder().url("$FeedUrl/signout").post(ByteArray(0).toRequestBody()), session).close() }
    }

    suspend fun load(): FeedResult = withContext(Dispatchers.IO) {
        if (!isSetUp) return@withContext FeedResult.NotSetUp
        val session = _session.value ?: return@withContext FeedResult.SignedOut
        val response = try {
            call(Request.Builder().url("$FeedUrl/modules"), session)
        } catch (e: IOException) {
            return@withContext FeedResult.Offline
        }
        response.use {
            val body = runCatching { JSONObject(it.body.string()) }.getOrNull() ?: JSONObject()
            when (it.code) {
                200 -> FeedResult.Modules(personOf(body.optJSONObject("me")) ?: FeedPerson("", null, member = true, invite = null), modulesOf(body))
                401 -> FeedResult.SignedOut.also { forgetSession() }
                403 -> FeedResult.NotMember(personOf(body), body.optString("invite").takeIf { link -> link.startsWith("https://") })
                503 -> FeedResult.WarmingUp
                else -> FeedResult.Offline
            }
        }
    }

    /** the module's zip into the cache, ready to be checked like a zip picked from the phone; null when it couldn't be fetched */
    suspend fun download(postId: String): File? = withContext(Dispatchers.IO) {
        val session = _session.value ?: return@withContext null
        val folder = File(context.cacheDir, "module_browser").apply { mkdirs() }
        val file = File(folder, "$postId.zip")
        runCatching {
            call(Request.Builder().url("$FeedUrl/modules/$postId/zip"), session).use { response ->
                if (!response.isSuccessful) return@use null
                file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                file
            }
        }.getOrNull()
    }

    private fun call(request: Request.Builder, session: String) =
        client.newCall(request.header("Authorization", "Bearer $session").build()).execute()

    private fun forgetSession() {
        prefs.edit { remove(SessionKey) }
        _session.value = null
    }

    private fun personOf(json: JSONObject?): FeedPerson? = json?.optString("name")?.takeIf { it.isNotBlank() }?.let { name ->
        FeedPerson(name, json.optString("avatar").takeIf { it.startsWith("https://") }, json.optBoolean("member"), json.optString("invite").takeIf { it.startsWith("https://") })
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
                kind = when (item.optString("kind")) {
                    "look" -> FeedKind.Look
                    "player" -> FeedKind.Player
                    "source" -> FeedKind.Source
                    else -> FeedKind.Other
                },
                paid = item.optBoolean("paid"),
                locked = item.optBoolean("locked"),
                icon = item.optString("icon").takeIf { it.startsWith("https://") },
                postUrl = item.optString("postUrl"),
            )
        }
    }

    companion object {
        // the worker's address (see the mochy-module-feed project); empty until it's deployed, and the browser says so
        const val FeedUrl = ""

        private const val SessionKey = "session"
        private const val StateKey = "state"
        private val SessionPattern = Regex("^[0-9a-f]{64}$")
    }
}
