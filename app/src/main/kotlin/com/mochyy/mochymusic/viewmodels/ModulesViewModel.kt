// ModulesViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.media.MediaDrm
import android.media.NotProvisionedException
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.ItemListModule
import com.mochyy.mochymusic.module.ItemProblem
import com.mochyy.mochymusic.module.ItemsAdded
import com.mochyy.mochymusic.module.ModuleItem
import com.mochyy.mochymusic.module.ModuleServer
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.ServerListModule
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.CatalogModule
import com.mochyy.mochymusic.modules.ModuleCatalog
import com.mochyy.mochymusic.modules.ModuleRegistry
import com.mochyy.mochymusic.modules.SourceModules
import org.json.JSONObject
import com.mochyy.mochymusic.playback.custom.WidevineDrm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The Modules settings: each module's account, switch, choices, its playback test, and adding new ones. */
@HiltViewModel
class ModulesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val modules: SourceModules,
    private val registry: ModuleRegistry,
    private val moduleCatalog: ModuleCatalog,
) : ViewModel() {

    val entries = modules.entries

    /** the provider order: module ids and "youtube" */
    val order = modules.order

    /** reorders only the providers a page shows, the switched-off modules it hides keep their places */
    fun setVisibleOrder(ids: List<String>) = modules.setVisibleOrder(ids)

    /** the order downloads are looked for in, apart from the one songs play in */
    val downloadOrder = modules.downloadOrder

    fun setVisibleDownloadOrder(ids: List<String>) = modules.setVisibleDownloadOrder(ids)

    /** YouTube Music's switch; it's not a module, so it isn't in [entries] */
    val youTubeEnabled = modules.youTubeEnabled

    fun isUsable(id: String): Boolean = modules.isUsable(id)

    fun nameOf(id: String): String = modules.nameOf(id)

    private val _steps = MutableStateFlow<List<DiagnosticStep>>(emptyList())
    val steps: StateFlow<List<DiagnosticStep>> = _steps.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    // true while a check-server or check-account press is waiting on the service
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _servers = MutableStateFlow<List<ModuleServer>>(emptyList())
    val servers: StateFlow<List<ModuleServer>> = _servers.asStateFlow()

    private val _items = MutableStateFlow<List<ModuleItem>>(emptyList())
    val items: StateFlow<List<ModuleItem>> = _items.asStateFlow()

    private var testJob: Job? = null
    private var signedIn = false

    init {
        modules.refresh()
    }

    fun setEnabled(id: String, on: Boolean) = modules.setEnabled(id, on)

    fun setPick(id: String, choiceId: String, optionId: String) = modules.setPick(id, choiceId, optionId)

    fun signOut(id: String) {
        registry.module(id)?.signOut()
        modules.setEnabled(id, false)
        _steps.value = emptyList()
    }

    fun verify(id: String) {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { registry.module(id)?.verifyAccount() } }
            modules.refresh()
            _checking.value = false
        }
    }

    // ---- a module's servers ----

    fun loadServers(id: String) {
        _servers.value = (registry.module(id) as? ServerListModule)?.servers.orEmpty()
    }

    /** checks the link and adds the server it finds; null once it's in, otherwise why not */
    suspend fun addServer(id: String, link: String): ServerProblem? {
        val module = registry.module(id) as? ServerListModule ?: return ServerProblem.NotAServer
        val problem = withContext(Dispatchers.IO) { runCatching { module.addServer(link) }.getOrDefault(ServerProblem.Unreachable) }
        loadServers(id)
        modules.refresh()
        return problem
    }

    fun removeServer(id: String, url: String) {
        (registry.module(id) as? ServerListModule)?.removeServer(url)
        loadServers(id)
        modules.refresh()
    }

    // ---- what people added to a module ----

    /** what adding to a module's list came to, for the sheet to say */
    sealed interface ItemsOutcome {
        data class Added(val names: List<String>) : ItemsOutcome
        /** it was a web module and nothing the module could use, so it went in on its own like "find more sources" would add it */
        data class AddedAsModule(val name: String) : ItemsOutcome
        data class Problem(val problem: ItemProblem) : ItemsOutcome
    }

    fun loadItems(id: String) {
        _items.value = (registry.module(id) as? ItemListModule)?.items.orEmpty()
    }

    suspend fun addItemsZip(id: String, uri: android.net.Uri): ItemsOutcome {
        val module = registry.module(id) as? ItemListModule ?: return ItemsOutcome.Problem(ItemProblem.NothingFound)
        val (name, bytes) = withContext(Dispatchers.IO) { registry.readFile(uri, MaxItemsZipBytes) } ?: return ItemsOutcome.Problem(ItemProblem.NotAZip)
        if (bytes.size > MaxItemsZipBytes) return ItemsOutcome.Problem(ItemProblem.TooBig)
        val added = withContext(Dispatchers.IO) { runCatching { module.addZip(name, bytes) }.getOrDefault(ItemsAdded.Problem(ItemProblem.Broken)) }
        return settleItems(id, added, fallBackOn = setOf(ItemProblem.NothingFound)) { registry.checkZip(uri) }
    }

    suspend fun addItemsLink(id: String, link: String): ItemsOutcome {
        val module = registry.module(id) as? ItemListModule ?: return ItemsOutcome.Problem(ItemProblem.NothingFound)
        val added = withContext(Dispatchers.IO) { runCatching { module.addLink(link) }.getOrDefault(ItemsAdded.Problem(ItemProblem.Unreachable)) }
        // a web module's own link often answers nothing at its root, only at its manifest.json
        return settleItems(id, added, fallBackOn = setOf(ItemProblem.NothingFound, ItemProblem.Unreachable)) { registry.check(link) }
    }

    fun removeItem(id: String, itemId: String) {
        (registry.module(id) as? ItemListModule)?.removeItem(itemId)
        loadItems(id)
        modules.refresh()
    }

    // something that plays over plain http is a web module, so when the module found nothing it could use it goes in that way, switched off like any new module
    private suspend fun settleItems(
        id: String,
        added: ItemsAdded,
        fallBackOn: Set<ItemProblem>,
        asWebModule: suspend () -> ModuleRegistry.LinkCheck,
    ): ItemsOutcome {
        loadItems(id)
        modules.refresh()
        if (added is ItemsAdded.Added) return ItemsOutcome.Added(added.names)
        val problem = (added as ItemsAdded.Problem).problem
        if (problem !in fallBackOn) return ItemsOutcome.Problem(problem)
        val check = runCatching { asWebModule() }.getOrNull()
        if (check !is ModuleRegistry.LinkCheck.Ready || check.base == null) return ItemsOutcome.Problem(problem)
        install(check)
        return ItemsOutcome.AddedAsModule(check.name)
    }

    /** a header from the sign-in page; [onDone] runs once, when the account is in */
    fun onSignInHeader(id: String, name: String, value: String, url: String, onDone: (AccountState) -> Unit) {
        val module = registry.module(id) ?: return
        if (signedIn || !module.onSignInHeader(name, value, url)) return
        signedIn = true
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { runCatching { module.verifyAccount() }.getOrNull() } ?: module.accountState()
            // signing in is asking for it: the module plays from now on
            if (state is AccountState.SignedIn) modules.setEnabled(id, true) else modules.refresh()
            onDone(state)
        }
    }

    fun resetSignIn() {
        signedIn = false
    }

    /** what the import's musickit probe read off music.apple.com: the same two tokens the web player sends as headers */
    fun onApplePageProbe(id: String, raw: String?, onDone: (AccountState) -> Unit) {
        if (raw.isNullOrBlank() || raw == "null") return
        val tokens = runCatching { JSONObject(raw) }.getOrNull() ?: return
        tokens.optString("dev").takeIf { it.length > 20 && it != "null" }?.let {
            onSignInHeader(id, "authorization", "Bearer $it", AppleApi, onDone)
        }
        tokens.optString("user").takeIf { it.length > 20 && it != "null" }?.let {
            onSignInHeader(id, "media-user-token", it, AppleApi, onDone)
        }
    }

    /** apple also keeps the user token as a cookie on music.apple.com */
    fun onAppleCookies(id: String, cookies: String?, onDone: (AccountState) -> Unit) {
        cookies?.split(';')?.forEach { pair ->
            if (pair.substringBefore('=').trim().equals("media-user-token", ignoreCase = true)) {
                onSignInHeader(id, "media-user-token", pair.substringAfter('=').trim(), "https://music.apple.com/", onDone)
            }
        }
    }

    // ---- finding and adding modules ----

    suspend fun check(link: String): ModuleRegistry.LinkCheck = registry.check(link)

    suspend fun checkZip(uri: android.net.Uri): ModuleRegistry.LinkCheck = registry.checkZip(uri)

    /** adds a checked module, switched off so nothing changes until the user turns it on; gives back its id, null when a zip's code wouldn't start */
    suspend fun install(ready: ModuleRegistry.LinkCheck.Ready): String? {
        // a zip's pictures and fonts are unpacked on the way in, and its code is copied and started
        val id = withContext(Dispatchers.IO) { registry.install(ready) }
        modules.refresh()
        return id
    }

    /** a module's skin (its player, font and pages), when it brings one */
    fun skinOf(id: String): com.mochyy.mochymusic.modules.skin.ModuleSkin? =
        (registry.module(id) as? com.mochyy.mochymusic.modules.skin.SkinSource)?.skin

    fun remove(id: String) {
        registry.remove(id)
        modules.refresh()
    }

    suspend fun catalog(): Result<List<CatalogModule>> = moduleCatalog.load()

    /** the module's own steps, then whether this phone's Widevine actually gets a license */
    fun runTest(id: String, track: TrackQuery) {
        val module = modules.module(id) ?: return
        testJob?.cancel()
        _steps.value = emptyList()
        _testing.value = true
        testJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { module.diagnose(track, modules.picks(id)) { report(it) } }
                    .getOrElse { e ->
                        report(DiagnosticStep("Error", false, e.message ?: e.javaClass.simpleName))
                        null
                    }
            }
            val stream = (result as? ResolveResult.Found)?.stream
            val drm = stream?.drm as? StreamDrm.Widevine
            var licensed = stream != null && drm == null
            if (drm != null) {
                report(DiagnosticStep("License", null, "Asking for a license on this phone…"))
                val outcome = withContext(Dispatchers.IO) { runCatching { checkWidevine(drm) } }
                licensed = outcome.isSuccess
                report(
                    outcome.fold(
                        onSuccess = { DiagnosticStep("License", true, it) },
                        onFailure = { DiagnosticStep("License", false, it.message ?: it.javaClass.simpleName) },
                    )
                )
            }
            // a license alone isn't proof: Apple's enhanced streams licensed fine and then
            // decrypted nothing, so the test ends by really decoding the start of the song
            if (stream != null && licensed) {
                report(DiagnosticStep("Play", null, "Decoding the start of the song, muted…"))
                val outcome = runCatching { checkPlayback(stream) }
                report(
                    outcome.fold(
                        onSuccess = { DiagnosticStep("Play", true, it) },
                        onFailure = { DiagnosticStep("Play", false, it.message ?: it.javaClass.simpleName) },
                    )
                )
            }
            modules.refresh()
            _testing.value = false
        }
    }

    private fun report(step: DiagnosticStep) {
        // release builds drop Timber; this shows in `adb logcat -s ModuleTest`
        if (step.ok != null) android.util.Log.i("ModuleTest", "${if (step.ok == true) "OK" else "FAIL"} ${step.name}: ${step.detail}")
        val list = _steps.value.toMutableList()
        val at = list.indexOfFirst { it.name == step.name }
        if (at >= 0) list[at] = step else list += step
        _steps.value = list
    }

    /**
     * Plays the stream in a throwaway, muted player until two seconds have been decoded: the
     * whole path playback takes (playlist, segments, DRM sessions, the decoder), nothing faked.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun checkPlayback(stream: com.mochyy.mochymusic.module.SourceStream): String {
        val drm = stream.drm as? StreamDrm.Widevine
        val factory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(context)
        )
        if (drm != null) factory.setDrmSessionManagerProvider { WidevineDrm.sessionManager(drm.license) }
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
        var error: androidx.media3.common.PlaybackException? = null
        try {
            player.volume = 0f
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                    error = e
                }
            })
            val item = androidx.media3.common.MediaItem.Builder()
                .setUri(stream.url)
                .apply {
                    if (stream.format == com.mochyy.mochymusic.module.StreamFormat.Hls) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    if (drm != null) setDrmConfiguration(
                        androidx.media3.common.MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setMultiSession(true).build()
                    )
                }
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline) {
                error?.let { e ->
                    android.util.Log.w("ModuleTest", "Test playback failed", e)
                    throw java.io.IOException(generateSequence<Throwable>(e) { it.cause }.last().message ?: e.errorCodeName)
                }
                if (player.currentPosition >= 2_000) return "Decoded ${player.currentPosition / 1000} s here (${stream.qualityLabel})"
                kotlinx.coroutines.delay(200)
            }
            throw java.io.IOException("Didn't start playing within 20 s")
        } finally {
            player.release()
        }
    }

    /**
     * Opens a Widevine session on the device's own CDM and has every key of the stream licensed,
     * the same exchange playback makes, without playing anything.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun checkWidevine(drm: StreamDrm.Widevine): String {
        val mediaDrm = MediaDrm(C.WIDEVINE_UUID)
        var session: ByteArray? = null
        try {
            val level = runCatching { mediaDrm.getPropertyString("securityLevel") }.getOrNull()
            session = try {
                mediaDrm.openSession()
            } catch (e: NotProvisionedException) {
                val request = mediaDrm.provisionRequest
                mediaDrm.provideProvisionResponse(WidevineDrm.provision(request.defaultUrl, request.data))
                mediaDrm.openSession()
            }
            drm.initData.forEach { pssh ->
                val request = mediaDrm.getKeyRequest(session, pssh, "audio/mp4", MediaDrm.KEY_TYPE_STREAMING, null)
                mediaDrm.provideKeyResponse(session, drm.license.acquire(request.data))
            }
            return "Licensed on this phone (Widevine ${level ?: "?"}, ${drm.initData.size} keys)"
        } finally {
            session?.let { runCatching { mediaDrm.closeSession(it) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mediaDrm.close() else @Suppress("DEPRECATION") mediaDrm.release()
        }
    }

    private companion object {
        const val AppleApi = "https://amp-api.music.apple.com/"
        // a zip for a module's list is read no further than this, the same ceiling the js modules put on one
        const val MaxItemsZipBytes = 25 * 1024 * 1024
    }
}
