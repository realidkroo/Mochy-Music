// every module mochy has: built in, installed as its own app, added from a link or from a zip (a look, or code of its own), with each one's switch and choices

package com.mochyy.mochymusic.modules

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
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
import com.mochyy.mochymusic.module.MetadataModule
import com.mochyy.mochymusic.module.MochyModule
import com.mochyy.mochymusic.module.ModuleArt
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.bridge.BridgedModule
import com.mochyy.mochymusic.module.jiosaavn.JioSaavnModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.code.CodeModules
import com.mochyy.mochymusic.modules.local.LocalModule
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleMenu
import com.mochyy.mochymusic.modules.skin.SkinFiles
import com.mochyy.mochymusic.modules.skin.SkinSource
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** where a module came from, for its page and for whether it can be removed */
sealed interface ModuleOrigin {
    data object BuiltIn : ModuleOrigin
    data class App(val packageName: String) : ModuleOrigin
    data class Web(val base: String) : ModuleOrigin
    data class File(val fileName: String) : ModuleOrigin
    /** a zip that brought code of its own, signed with mochy's key */
    data class Code(val fileName: String) : ModuleOrigin
}

/** a module as the settings show it */
data class ModuleEntry(
    val module: MochyModule,
    val origin: ModuleOrigin,
    val enabled: Boolean,
    /** choice id to the option picked; a choice not in here is on its first option */
    val picks: Map<String, String>,
    val account: AccountState,
    /** the module's own settings, and what each one is set to now */
    val fields: List<ModuleField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    /** when it was added, null for built-in ones and ones added before mochy kept the date */
    val addedAt: Long? = null,
    /** the github project or link it can update from, null for one added from a file */
    val source: ModuleSource? = null,
    /** a newer copy waiting at [source] */
    val update: ModuleUpdate? = null,
    /** when [source] was last asked for a newer copy */
    val checkedAt: Long? = null,
) {
    val id: String get() = module.info.id

    val icon: String? get() = (module as? ModuleArt)?.icon

    val banner: String? get() = (module as? ModuleArt)?.banner

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
    private val updates by lazy { ModuleUpdates(prefs, client) }

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
        if (module is LookModule && restyles(module)) {
            scope.launch {
                if (on) {
                    // one look at a time: the one already on gives the user's settings back first
                    modules.map { it.first }
                        .filter { it.info.id != id && restyles(it) && isEnabled(it.info.id) }
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

    // a look that changes settings, the player or the font; only one of those is on at a time, a module with only pages doesn't count
    private fun restyles(module: MochyModule): Boolean =
        module is LookModule && ModuleFeature.Look in module.adds && (module.look.isNotEmpty() || (module as? SkinSource)?.skin?.restyles == true)

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

    /** what each of a module's own settings is set to, the field's default until someone changes it */
    fun fieldValues(id: String): Map<String, String> = module(id)?.let(::valuesFor).orEmpty()

    private fun valuesFor(module: MochyModule): Map<String, String> {
        val fields = runCatching { (module as? ModuleFields)?.fields }.getOrNull() ?: return emptyMap()
        return fields.associate { field -> field.id to (prefs.getString("field_${module.info.id}_${field.id}", null) ?: field.default) }
    }

    // the module hears every value before its first call, so a song asked for straight after a start already gets them
    private fun handValues(module: MochyModule) {
        val values = valuesFor(module).takeIf { it.isNotEmpty() } ?: return
        runCatching { (module as? ModuleFields)?.onFields(values) }
    }

    /** keeps one of a module's settings; a look that's on swaps over to what the new value brings, giving the old values back first */
    fun setField(id: String, fieldId: String, value: String) {
        val module = module(id) ?: return
        val before = (module as? LookModule)?.look
        prefs.edit { putString("field_${id}_$fieldId", value) }
        handValues(module)
        val after = (module as? LookModule)?.look
        if (before != null && after != null && before != after && isEnabled(id) && restyles(module)) {
            scope.launch {
                looks.restore(id, before)
                looks.apply(id, after)
            }
        }
        refresh()
    }

    // the order modules are listed and asked in: what was dragged on the modules page, else the provider order the songs already use
    private fun savedOrder(): List<String> =
        (prefs.getString(ModuleOrderKey, null) ?: prefs.getString("provider_order", null)).orEmpty().split(',').filter { it.isNotBlank() }

    /** a new order for the modules listed; the ones it leaves out keep their places after them */
    fun setOrder(ids: List<String>) {
        prefs.edit { putString(ModuleOrderKey, (ids + savedOrder()).distinct().joinToString(",")) }
        refresh()
    }

    fun refresh() {
        val order = savedOrder()
        val list = modules.map { (module, origin) ->
            val id = module.info.id
            ModuleEntry(
                module = module,
                origin = origin,
                enabled = isEnabled(id),
                picks = picks(id),
                account = module.accountState(),
                fields = runCatching { (module as? ModuleFields)?.fields }.getOrNull().orEmpty(),
                values = valuesFor(module),
                addedAt = prefs.getLong("added_$id", 0L).takeIf { it > 0L },
                source = updates.source(id),
                update = updates.update(id),
                checkedAt = updates.checkedAt(id),
            )
        }.sortedBy { entry -> order.indexOf(entry.id).let { if (it < 0) Int.MAX_VALUE else it } }
        _entries.value = list
        // the look that's on now, for the player's module style and the app's font
        val restylingEntry = list.firstOrNull { it.active && (it.module as? SkinSource)?.skin?.restyles == true }
        val restyling = restylingEntry?.module as? SkinSource
        ActiveSkin.publish(restyling?.skin, restyling?.glass, restylingEntry?.values.orEmpty(), scope)
        LyricsProviderRegistry.setModuleProviders(
            list.filter { it.active && it.adds(ModuleFeature.Lyrics) }.mapNotNull { (it.module as? LyricsModule)?.let(::ModuleLyricsProvider) },
        )
        ModuleMetadata.sources = list.filter { it.active && it.adds(ModuleFeature.Metadata) }.mapNotNull { it.module as? MetadataModule }
        ModuleMenu.publish(list.filter { it.active }.mapNotNull { (it.module as? SkinSource)?.skin })
    }

    // ---- modules added from a link ------------------------------------------------------------

    /** what a link or a zip turned out to be, for the sheet to say before anything is added */
    sealed interface LinkCheck {
        /** [base] is the service it plays from, null for a look that lives in its file; [from] is what the sheet shows it came from */
        data class Ready(
            val base: String?,
            val manifestText: String,
            val name: String,
            val adds: Set<ModuleFeature>,
            val alreadyAdded: Boolean,
            val from: String,
            // the zip it came in and the manifest's folder inside it, to unpack its pictures and fonts from on install
            val zip: Uri? = null,
            val zipRoot: String = "",
            // the zip's own code, already checked to be signed like mochy, waiting in the cache to be installed
            val code: File? = null,
            // where a newer copy can be found later: the github project or the link it came from
            val source: ModuleSource? = null,
        ) : LinkCheck
        data object NotALink : LinkCheck
        data object Unreachable : LinkCheck
        data object NotAModule : LinkCheck
        data object Website : LinkCheck
        data object NotAZip : LinkCheck
        data object NeedsUrl : LinkCheck
        /** its code isn't signed with this mochy's key, so it can't run here */
        data object NotSigned : LinkCheck
        /** a github project with no published release */
        data object NoRelease : LinkCheck
        /** the newest release has no zip with mochyymodule in its name */
        data object NoModuleInRelease : LinkCheck
    }

    /** fetches the link's manifest.json without adding anything; a github project's link fetches its newest release's module zip instead */
    suspend fun check(link: String): LinkCheck = withContext(Dispatchers.IO) {
        GitHubReleases.parse(link)?.let { return@withContext checkGitHub(it) }
        val base = WebModuleManifest.baseOf(link) ?: return@withContext LinkCheck.NotALink
        val text = runCatching {
            client.newCall(Request.Builder().url("$base/manifest.json").header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
        }.getOrElse { return@withContext LinkCheck.Unreachable }
        if (WebModuleManifest.isWebsite(text)) return@withContext LinkCheck.Website
        val manifest = WebModuleManifest.parse(text) ?: return@withContext LinkCheck.NotAModule
        val found = WebModule(base, manifest, context.resources)
        if (found.adds.isEmpty()) return@withContext LinkCheck.NotAModule
        LinkCheck.Ready(
            base, text, found.info.name, found.adds, alreadyAdded = module(found.info.id) != null, from = base.substringAfter("://"),
            source = ModuleSource.Link(base, ModuleUpdates.fingerprint(text)),
        )
    }

    // the release's zip lands in the cache, then it's read exactly like a zip picked from the phone
    private suspend fun checkGitHub(link: GitHubReleases.Link): LinkCheck {
        val found = GitHubReleases.download(client, context.cacheDir, link, preferDebug = PreferDebugZips)
        val zip = when (found) {
            is GitHubReleases.Found.Zip -> found
            is GitHubReleases.Found.Newest -> return LinkCheck.Unreachable
            GitHubReleases.Found.NoRelease -> return LinkCheck.NoRelease
            GitHubReleases.Found.NoModule -> return LinkCheck.NoModuleInRelease
            GitHubReleases.Found.Unreachable -> return LinkCheck.Unreachable
        }
        val result = checkZip(Uri.fromFile(zip.file))
        val source = ModuleSource.GitHub(link.owner, link.repo, zip.release.tag, zip.release.stamp)
        return (result as? LinkCheck.Ready)?.copy(from = zip.from, source = source) ?: result
    }

    /** reads a module's zip without adding anything: its manifest.json, and the service it names when it plays songs or lyrics */
    suspend fun checkZip(uri: Uri): LinkCheck = withContext(Dispatchers.IO) {
        val (text, root) = runCatching { manifestInZip(uri) }.getOrNull() ?: return@withContext LinkCheck.NotAZip
        val manifest = WebModuleManifest.parse(text) ?: return@withContext LinkCheck.NotAModule
        manifest.code?.let { return@withContext checkCode(uri, text, root, manifest, it) }
        val service = manifest.url?.let { WebModuleManifest.baseOf(it) ?: return@withContext LinkCheck.NotALink }
        if (service != null) {
            val found = WebModule(service, manifest, context.resources)
            if (found.adds.isEmpty()) return@withContext LinkCheck.NotAModule
            return@withContext LinkCheck.Ready(service, text, found.info.name, found.adds, module(found.info.id) != null, service.substringAfter("://"), uri, root)
        }
        // songs and lyrics have to come from somewhere, a file on its own can only hold a look
        if (manifest.resourceNames.any { it in ServiceResources }) return@withContext LinkCheck.NeedsUrl
        if (!manifest.changesLook) return@withContext LinkCheck.NotAModule
        val local = LocalModule(manifest, files = null)
        LinkCheck.Ready(null, text, local.info.name, local.adds, module(local.info.id) != null, fileNameOf(uri), uri, root)
    }

    // a zip that brings its own code: checked to be signed like mochy now, so the sheet can say so before anything is added
    private fun checkCode(uri: Uri, text: String, root: String, manifest: WebModuleManifest, code: String): LinkCheck {
        val apk = CodeModules.extract(context, uri, root + code.trimStart('/')) ?: return LinkCheck.NotAModule
        if (!CodeModules.signedLikeMochy(context, apk)) return LinkCheck.NotSigned
        val adds = buildSet {
            if (manifest.playsSongs) add(ModuleFeature.AudioSource)
            if (manifest.hasLyrics) add(ModuleFeature.Lyrics)
            if (manifest.hasMetadata) add(ModuleFeature.Metadata)
            if (manifest.changesLook) add(ModuleFeature.Look)
        }
        val name = manifest.name.ifBlank { manifest.id }
        return LinkCheck.Ready(null, text, name, adds, module(manifest.id) != null, fileNameOf(uri), uri, root, code = apk)
    }

    // the zip's manifest.json and the folder it sits in, at its root or in the one folder most zip tools wrap things in
    private fun manifestInZip(uri: Uri): Pair<String, String>? = context.contentResolver.openInputStream(uri)?.use { raw ->
        ZipInputStream(raw.buffered()).use { zip ->
            val entry = generateSequence { zip.nextEntry }.take(MaxZipEntries).firstOrNull { entry ->
                val parts = entry.name.trimStart('/').split('/')
                !entry.isDirectory && parts.last() == "manifest.json" && parts.size <= 2 && parts.first() != "__MACOSX"
            } ?: return null
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                // a manifest is a few kilobytes, anything this big is something else
                if (out.size() > MaxManifestBytes) return null
            }
            out.toString(Charsets.UTF_8.name()) to entry.name.trimStart('/').removeSuffix("manifest.json")
        }
    }

    /** a picked file's name and bytes, read no further than one byte past [limit] so the caller can tell it was too big */
    fun readFile(uri: Uri, limit: Int): Pair<String, ByteArray>? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() <= limit) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            fileNameOf(uri) to out.toByteArray()
        }
    }.getOrNull()

    private fun fileNameOf(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    /** adds a checked link or zip as a module, switched off until the user turns it on; gives back its id, null when a zip's code wouldn't start. unpacks a zip's files, so not on the main thread */
    fun install(ready: LinkCheck.Ready): String? {
        ready.code?.let { return installCode(ready, it) }
        val manifest = requireNotNull(WebModuleManifest.parse(ready.manifestText))
        val base = ready.base
        val newId = if (base != null) WebModuleManifest.idFor(base, manifest) else LocalModule.idFor(manifest)
        // the zip's pictures and fonts go in first, so the module is built with them in place; a link brings none, and old ones go
        val files = ready.zip?.let { SkinFiles.unpack(context, it, newId, ready.zipRoot) }
        if (ready.zip == null) SkinFiles.delete(context, newId)
        val (module, origin) = if (base != null) {
            WebModule(base, manifest, context.resources, files) to ModuleOrigin.Web(base)
        } else {
            LocalModule(manifest, files) to ModuleOrigin.File(ready.from)
        }
        val id = module.info.id
        val before = module(id)
        synchronized(this) {
            if (base != null) {
                val saved = savedWebModules().filter { it.optString("base") != base }
                prefs.edit { putString(WebModulesKey, JSONArray(saved + JSONObject().put("base", base).put("manifest", ready.manifestText)).toString()) }
            } else {
                val saved = savedLocalModules().filter { it.optString("id") != id }
                val entry = JSONObject().put("id", id).put("file", ready.from).put("manifest", ready.manifestText)
                prefs.edit { putString(LocalModulesKey, JSONArray(saved + entry).toString()) }
            }
            if (!prefs.contains("added_$id")) prefs.edit { putLong("added_$id", System.currentTimeMillis()) }
            module.attach(hostFor(module))
            handValues(module)
            loaded = modules.filter { it.first.info.id != id } + (module to origin)
        }
        updates.installed(id, ready.source)
        swapLook(id, before, module)
        refresh()
        return id
    }

    // the zip's code goes in a folder of its own and starts; one already here with the same id stops and is replaced
    private fun installCode(ready: LinkCheck.Ready, apk: File): String? {
        val key = WebModuleManifest.parse(ready.manifestText)?.id?.ifBlank { null } ?: ready.name
        val installed = CodeModules.install(context, key, apk) ?: return null
        val fresh = installed.module
        val id = fresh.info.id
        val before = module(id)
        synchronized(this) {
            val saved = savedCodeModules().filter { it.optString("id") != id }
            val entry = JSONObject().put("id", id).put("folder", installed.folder).put("file", ready.from)
            prefs.edit { putString(CodeModulesKey, JSONArray(saved + entry).toString()) }
            // the old copy's folder goes on the next start, its code may still be finishing a call
            (module(id) as? BridgedModule)?.close()
            if (!prefs.contains("added_$id")) prefs.edit { putLong("added_$id", System.currentTimeMillis()) }
            fresh.attach(hostFor(fresh))
            handValues(fresh)
            loaded = modules.filter { it.first.info.id != id } + (fresh to ModuleOrigin.Code(ready.from))
        }
        updates.installed(id, ready.source)
        swapLook(id, before, fresh)
        refresh()
        return id
    }

    // an update of a look that's on gives the old values back and puts the new ones on, so the new copy shows straight away
    private fun swapLook(id: String, before: MochyModule?, after: MochyModule) {
        if (before == null || !isEnabled(id)) return
        val old = (before as? LookModule)?.takeIf(::restyles)?.look
        val new = (after as? LookModule)?.takeIf(::restyles)?.look
        if (old == new) return
        scope.launch {
            old?.let { looks.restore(id, it) }
            new?.let { looks.apply(id, it) }
            refresh()
        }
    }

    // ---- updates --------------------------------------------------------------------------------

    /** asks every module's github project or link whether a newer copy is out; [force] skips the wait between checks, [only] checks one */
    suspend fun checkUpdates(force: Boolean, only: String? = null) = withContext(Dispatchers.IO) {
        modules.map { it.first }.filter { only == null || it.info.id == only }.forEach { module ->
            runCatching { updates.check(module.info.id, module.info.version, force, preferDebug = PreferDebugZips) }
        }
        refresh()
    }

    /** fetches the newer copy from where the module came from and puts it in place; its switch and settings stay */
    suspend fun update(id: String): LinkCheck {
        val source = updates.source(id) ?: return LinkCheck.NotALink
        val check = check(source.link)
        if (check !is LinkCheck.Ready) return check
        // a link the author moved to another module can't replace this one
        if (check.code == null && idOf(check) != id) return LinkCheck.NotAModule
        withContext(Dispatchers.IO) { install(check) } ?: return LinkCheck.NotAModule
        return check
    }

    // the id a checked link or zip turns into once it's added
    private fun idOf(ready: LinkCheck.Ready): String? {
        val manifest = WebModuleManifest.parse(ready.manifestText) ?: return null
        return ready.base?.let { WebModuleManifest.idFor(it, manifest) } ?: LocalModule.idFor(manifest)
    }

    /** takes a module added from a link or a zip away for good, its look, lyrics and files go with it */
    fun remove(id: String) {
        val (module, origin) = modules.firstOrNull { it.first.info.id == id } ?: return
        if (origin !is ModuleOrigin.Web && origin !is ModuleOrigin.File && origin !is ModuleOrigin.Code) return
        val codeFolder = savedCodeModules().firstOrNull { it.optString("id") == id }?.optString("folder")
        scope.launch {
            if (module is LookModule) looks.restore(id, module.look)
            SkinFiles.delete(context, id)
            folderFor(id).deleteRecursively()
            codeFolder?.let { CodeModules.delete(context, it) }
        }
        module.signOut()
        (module as? BridgedModule)?.close()
        updates.forget(id)
        synchronized(this) {
            prefs.edit {
                when (origin) {
                    is ModuleOrigin.Web -> putString(WebModulesKey, JSONArray(savedWebModules().filter { it.optString("base") != origin.base }).toString())
                    is ModuleOrigin.Code -> putString(CodeModulesKey, JSONArray(savedCodeModules().filter { it.optString("id") != id }).toString())
                    else -> putString(LocalModulesKey, JSONArray(savedLocalModules().filter { it.optString("id") != id }).toString())
                }
                remove("enabled_$id")
                remove("added_$id")
                module.choices.forEach { remove("pick_${id}_${it.id}") }
                runCatching { (module as? ModuleFields)?.fields }.getOrNull().orEmpty().forEach { remove("field_${id}_${it.id}") }
            }
            context.getSharedPreferences("module_$id", Context.MODE_PRIVATE).edit { clear() }
            loaded = modules.filter { it.first.info.id != id }
        }
        refresh()
    }

    private fun savedWebModules(): List<JSONObject> = savedList(WebModulesKey)

    private fun savedLocalModules(): List<JSONObject> = savedList(LocalModulesKey)

    private fun savedCodeModules(): List<JSONObject> = savedList(CodeModulesKey)

    private fun savedList(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    // ---- loading --------------------------------------------------------------------------------

    private fun load(): List<Pair<MochyModule, ModuleOrigin>> {
        val builtIn = listOf<MochyModule>(AppleMusicModule(), MonochromeModule(), JioSaavnModule()).map { it to ModuleOrigin.BuiltIn }
        val apps = discover()
        val savedCode = savedCodeModules()
        CodeModules.tidy(context, savedCode.map { it.optString("folder") }.toSet())
        val code = savedCode.mapNotNull { saved ->
            CodeModules.load(context, saved.optString("folder"))?.let { it.module to ModuleOrigin.Code(saved.optString("file")) }
        }
        // sites that got in as modules before they were turned away; they never played a song, so they go
        val (sites, kept) = savedWebModules().partition { WebModuleManifest.isWebsite(it.optString("manifest")) }
        if (sites.isNotEmpty()) prefs.edit { putString(WebModulesKey, JSONArray(kept).toString()) }
        val web = kept.mapNotNull { saved ->
            val base = saved.optString("base").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val manifest = WebModuleManifest.parse(saved.optString("manifest")) ?: return@mapNotNull null
            WebModule(base, manifest, context.resources, filesFor(WebModuleManifest.idFor(base, manifest))) to ModuleOrigin.Web(base)
        }
        val local = savedLocalModules().mapNotNull { saved ->
            val manifest = WebModuleManifest.parse(saved.optString("manifest")) ?: return@mapNotNull null
            LocalModule(manifest, filesFor(LocalModule.idFor(manifest))) to ModuleOrigin.File(saved.optString("file"))
        }
        // a module app or a zip's code replaces the built-in module with its id, so a module can be updated on its own
        val replacing = (apps + code).map { it.first.info.id }.toSet()
        migrateMonochromeSwitch()
        return (builtIn.filter { it.first.info.id !in replacing } + apps + code + web + local)
            .distinctBy { it.first.info.id }
            .onEach { (module, _) ->
                module.attach(hostFor(module))
                handValues(module)
            }
    }

    // the pictures and fonts a module's zip brought, when it came as one
    private fun filesFor(id: String): File? = SkinFiles.folderFor(context, id).takeIf { it.isDirectory }

    // the folder a module keeps its own files in, the scripts or packs people add to it
    private fun folderFor(id: String) = File(context.filesDir, "module_data/" + id.replace(Regex("[^a-z0-9._-]"), "-"))

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
            // okhttp refuses a body on a get or a head, even an empty one
            val bodiless = request.method == "GET" || request.method == "HEAD"
            builder.method(request.method, if (bodiless) null else body ?: ByteArray(0).toRequestBody())
            client.newCall(builder.build()).execute().use { response ->
                return HttpResponse(response.code, response.body.bytes(), response.headers.associate { (name, value) -> name.lowercase() to value })
            }
        }

        override fun log(message: String) {
            Timber.tag("Module:${module.info.id}").d(message)
        }

        override val folder: File by lazy { folderFor(module.info.id).also { it.mkdirs() } }
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
        private const val LocalModulesKey = "local_modules"
        private const val CodeModulesKey = "code_modules"
        private const val ModuleOrderKey = "module_order"
        // a zip is only searched this far for its manifest, and a manifest bigger than this isn't one
        private const val MaxZipEntries = 200
        private const val MaxManifestBytes = 256 * 1024
        private val ServiceResources = setOf("search", "stream", "lyrics", "metadata")
        // a release with a debug and a release zip: debug, fast and profileable builds are signed with the debug key
        private val PreferDebugZips = com.mochyy.mochymusic.BuildConfig.BUILD_TYPE != "release"
        const val ModuleAction = "com.mochyy.mochymusic.SOURCE_MODULE"
        const val EntryMetaKey = "com.mochyy.mochymusic.module.entry"
    }
}
