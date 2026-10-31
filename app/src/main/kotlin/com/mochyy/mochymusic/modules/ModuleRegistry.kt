// every module mochy has: built in, installed as its own app, or added from a link, with each one's switch and choices

package com.mochyy.mochymusic.modules

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.mochyy.mochymusic.constants.EnableMonochromeBackendKey
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.lyrics.ModuleLyricsProvider
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.LookModule
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.MODULE_API_VERSION
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.jiosaavn.JioSaavnModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.web.WebModule
import com.mochyy.mochymusic.modules.web.WebModuleManifest
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** where a module came from, for its page and for whether it can be removed */
sealed interface ModuleOrigin {
    data object BuiltIn : ModuleOrigin
    data class App(val packageName: String) : ModuleOrigin
    data class Web(val base: String) : ModuleOrigin
}

/** a module as the settings show it */
data class ModuleEntry(
    val module: MochyModule,
    val origin: ModuleOrigin,
    val enabled: Boolean,
    /** choice id to the option picked; a choice not in here is on its first option */
    val picks: Map<String, String>,
    val account: AccountState,
) {
    val id: String get() = module.info.id

    /** switched on and, when it needs an account, signed in: only then does anything it adds show up */
    val active: Boolean get() = enabled && account is AccountState.SignedIn

    fun adds(feature: ModuleFeature): Boolean = feature in module.adds
}

@Singleton
class ModuleRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // the same file the switches always lived in, so nobody's choices are lost
    private val prefs = context.getSharedPreferences("source_modules", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val looks = ModuleLooks(context)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var loaded: List<Pair<MochyModule, ModuleOrigin>>? = null

    private val modules: List<Pair<MochyModule, ModuleOrigin>>
        get() = loaded ?: synchronized(this) { loaded ?: load().also { loaded = it } }

    private val _entries = MutableStateFlow<List<ModuleEntry>>(emptyList())

    /** filled by [refresh]; the settings call it when they open */
    val entries: StateFlow<List<ModuleEntry>> = _entries.asStateFlow()

    init {
        // lyrics modules have to be in the lyrics sources before the first song asks for lyrics
        scope.launch { refresh() }
    }

    fun module(id: String): MochyModule? = modules.firstOrNull { it.first.info.id == id }?.first

    fun all(): List<MochyModule> = modules.map { it.first }

    // youtube starts on, modules start off until they're turned on (or signed in to)
    fun isEnabled(id: String): Boolean = prefs.getBoolean("enabled_$id", id == SourceModules.YouTubeId)

    fun setEnabled(id: String, on: Boolean) {
        val module = module(id)
        prefs.edit { putBoolean("enabled_$id", on) }
        if (module is LookModule && ModuleFeature.Look in module.adds) {
            scope.launch {
                if (on) {
                    // one look at a time: the one already on gives the user's settings back first
                    modules.map { it.first }
                        .filter { it.info.id != id && it is LookModule && ModuleFeature.Look in it.adds && isEnabled(it.info.id) }
                        .forEach { other ->
                            prefs.edit { putBoolean("enabled_${other.info.id}", false) }
                            looks.restore(other.info.id, (other as LookModule).look)
                        }
                    looks.apply(id, module.look)
                } else {
                    looks.restore(id, module.look)
                }
                refresh()
            }
        }
        refresh()
    }

    fun picks(id: String): Map<String, String> {
        val module = module(id) ?: return emptyMap()
        return module.choices.mapNotNull { choice ->
            prefs.getString("pick_${id}_${choice.id}", null)?.let { choice.id to it }
        }.toMap()
    }

    fun setPick(id: String, choiceId: String, optionId: String) {
        prefs.edit { putString("pick_${id}_$choiceId", optionId) }
        refresh()
    }

    fun refresh() {
        val list = modules.map { (module, origin) ->
            ModuleEntry(module, origin, isEnabled(module.info.id), picks(module.info.id), module.accountState())
        }
        _entries.value = list
        LyricsProviderRegistry.setModuleProviders(
            list.filter { it.active && it.adds(ModuleFeature.Lyrics) }.mapNotNull { (it.module as? LyricsModule)?.let(::ModuleLyricsProvider) },
        )
    }

    // ---- modules added from a link ------------------------------------------------------------

    /** what a link answered, for the sheet to say before anything is added */
    sealed interface LinkCheck {
        data class Ready(val base: String, val manifestText: String, val name: String, val adds: Set<ModuleFeature>, val alreadyAdded: Boolean) : LinkCheck
        data object NotALink : LinkCheck
        data object Unreachable : LinkCheck
        data object NotAModule : LinkCheck
    }

    /** fetches the link's manifest.json without adding anything */
    suspend fun check(link: String): LinkCheck = withContext(Dispatchers.IO) {
        val base = WebModuleManifest.baseOf(link) ?: return@withContext LinkCheck.NotALink
        val text = runCatching {
            client.newCall(Request.Builder().url("$base/manifest.json").header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
        }.getOrElse { return@withContext LinkCheck.Unreachable }
        val manifest = WebModuleManifest.parse(text) ?: return@withContext LinkCheck.NotAModule
        val found = WebModule(base, manifest, context.resources)
        if (found.adds.isEmpty()) return@withContext LinkCheck.NotAModule
        LinkCheck.Ready(base, text, found.info.name, found.adds, alreadyAdded = module(found.info.id) != null)
    }

    /** adds a checked link as a module, switched off until the user turns it on; gives back its id */
    fun install(ready: LinkCheck.Ready): String {
        val manifest = requireNotNull(WebModuleManifest.parse(ready.manifestText))
        val module = WebModule(ready.base, manifest, context.resources)
        val id = module.info.id
        synchronized(this) {
            val saved = savedWebModules().filter { it.optString("base") != ready.base }
            prefs.edit { putString(WebModulesKey, JSONArray(saved + JSONObject().put("base", ready.base).put("manifest", ready.manifestText)).toString()) }
            module.attach(hostFor(module))
            loaded = modules.filter { it.first.info.id != id } + (module to ModuleOrigin.Web(ready.base))
        }
        refresh()
        return id
    }

    /** takes a module added from a link away for good, its look and lyrics go with it */
    fun remove(id: String) {
        val (module, origin) = modules.firstOrNull { it.first.info.id == id } ?: return
        if (origin !is ModuleOrigin.Web) return
        scope.launch {
            if (module is LookModule) looks.restore(id, module.look)
        }
        module.signOut()
        synchronized(this) {
            val saved = savedWebModules().filter { it.optString("base") != origin.base }
            prefs.edit {
                putString(WebModulesKey, JSONArray(saved).toString())
                remove("enabled_$id")
                module.choices.forEach { remove("pick_${id}_${it.id}") }
            }
            context.getSharedPreferences("module_$id", Context.MODE_PRIVATE).edit { clear() }
            loaded = modules.filter { it.first.info.id != id }
        }
        refresh()
    }

    private fun savedWebModules(): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(WebModulesKey, "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    // ---- loading --------------------------------------------------------------------------------

    private fun load(): List<Pair<MochyModule, ModuleOrigin>> {
        val builtIn = listOf<MochyModule>(AppleMusicModule(), MonochromeModule(), JioSaavnModule()).map { it to ModuleOrigin.BuiltIn }
        val apps = discover()
        val web = savedWebModules().mapNotNull { saved ->
            val base = saved.optString("base").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val manifest = WebModuleManifest.parse(saved.optString("manifest")) ?: return@mapNotNull null
            WebModule(base, manifest, context.resources) to ModuleOrigin.Web(base)
        }
        // a module app replaces the built-in module with its id, so a module can be updated on its own
        val appIds = apps.map { it.first.info.id }.toSet()
        migrateMonochromeSwitch()
        return (builtIn.filter { it.first.info.id !in appIds } + apps + web)
            .distinctBy { it.first.info.id }
            .onEach { (module, _) -> module.attach(hostFor(module)) }
    }

    /** monochrome used to be built into playback with its own switch; it carries over, once */
    private fun migrateMonochromeSwitch() {
        if (prefs.getBoolean("migrated_monochrome", false)) return
        val wasOn = runCatching { context.dataStore.get(EnableMonochromeBackendKey, false) }.getOrDefault(false)
        prefs.edit {
            if (wasOn) putBoolean("enabled_${MonochromeModule.Id}", true)
            putBoolean("migrated_monochrome", true)
        }
    }

    private fun hostFor(module: MochyModule) = object : ModuleHost {
        private val store = context.getSharedPreferences("module_${module.info.id}", Context.MODE_PRIVATE)

        override val storage = object : ModuleStorage {
            override fun get(key: String): String? = store.getString(key, null)
            override fun set(key: String, value: String?) = store.edit { if (value == null) remove(key) else putString(key, value) }
        }

        override fun http(request: HttpRequest): HttpResponse {
            val builder = Request.Builder().url(request.url)
            request.headers.forEach { (k, v) -> builder.header(k, v) }
            val body = request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull())
            builder.method(request.method, if (request.method == "GET") null else body ?: ByteArray(0).toRequestBody())
            client.newCall(builder.build()).execute().use { response ->
                return HttpResponse(response.code, response.body.bytes())
            }
        }

        override fun log(message: String) {
            Timber.tag("Module:${module.info.id}").d(message)
        }
    }

    /** module apps installed on the phone, signed like mochy and built for this api */
    @SuppressLint("QueryPermissionsNeeded")
    private fun discover(): List<Pair<MochyModule, ModuleOrigin>> = runCatching {
        val pm = context.packageManager
        pm.queryIntentServices(Intent(ModuleAction), PackageManager.GET_META_DATA).mapNotNull { info ->
            val service = info.serviceInfo ?: return@mapNotNull null
            val pkg = service.packageName
            val entry = service.metaData?.getString(EntryMetaKey) ?: return@mapNotNull null
            // its code runs inside mochy, so only an app signed with mochy's own key is let in
            if (pm.checkSignatures(context.packageName, pkg) != PackageManager.SIGNATURE_MATCH) {
                Timber.tag(Tag).w("Skipping module $pkg: not signed like Mochy")
                return@mapNotNull null
            }
            runCatching {
                val app = service.applicationInfo
                val loader = PathClassLoader(app.sourceDir, app.nativeLibraryDir, javaClass.classLoader)
                val module = loader.loadClass(entry).getDeclaredConstructor().newInstance() as MochyModule
                if (module.info.apiVersion != MODULE_API_VERSION) {
                    Timber.tag(Tag).w("Skipping module $pkg: built for API ${module.info.apiVersion}, Mochy has $MODULE_API_VERSION")
                    null
                } else module to ModuleOrigin.App(pkg)
            }.onFailure { Timber.tag(Tag).w(it, "Couldn't load module $pkg") }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val Tag = "ModuleRegistry"
        private const val WebModulesKey = "web_modules"
        const val ModuleAction = "com.mochyy.mochymusic.SOURCE_MODULE"
        const val EntryMetaKey = "com.mochyy.mochymusic.module.entry"
    }
}
