// where songs play and download from: the provider order and the download order over youtube and the modules that add audio, and the walk down each

package com.mochyy.mochymusic.modules

import android.content.Context
import androidx.core.content.edit
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceModules @Inject constructor(
    @ApplicationContext context: Context,
    private val registry: ModuleRegistry,
) {
    private val prefs = context.getSharedPreferences("source_modules", Context.MODE_PRIVATE)

    // ---- provider order -----------------------------------------------------------------------
    //
    // The order songs are looked for in: the modules and YouTube Music. Each song plays from the
    // first switched-on provider that has it. YouTube is on unless the user turns it off, and then
    // a song none of the modules have is skipped rather than quietly played from YouTube.

    private val _order = MutableStateFlow(readOrder())

    val order: StateFlow<List<String>> = _order.asStateFlow()

    private fun readOrder(): List<String> {
        var saved = prefs.getString("provider_order", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        // Monochrome is preferred when it has the song (lossless): first by default, and moved to
        // the top once for orders saved before it was
        if (!prefs.getBoolean("order_monochrome_first", false)) {
            saved = listOf(MonochromeModule.Id) + saved.filter { it != MonochromeModule.Id }
            prefs.edit {
                putString("provider_order", (saved + KnownOrder).distinct().joinToString(","))
                putBoolean("order_monochrome_first", true)
            }
        }
        return (saved + KnownOrder).distinct()
    }

    fun setOrder(ids: List<String>) {
        val full = (ids + _order.value).distinct()
        prefs.edit { putString("provider_order", full.joinToString(",")) }
        _order.value = full
    }

    /** a new order for the providers a page shows; the ones it hides (modules switched off) keep their places */
    fun setVisibleOrder(visible: List<String>) = setOrder(reorderVisible(_order.value, visible))

    // downloads walk their own order, started as a copy of the playback one, since a kept file may want another source than what plays
    private val _downloadOrder = MutableStateFlow(readDownloadOrder())

    val downloadOrder: StateFlow<List<String>> = _downloadOrder.asStateFlow()

    private fun readDownloadOrder(): List<String> {
        val saved = prefs.getString("download_order", null)?.split(',')?.filter { it.isNotBlank() }
        if (saved != null) return (saved + KnownOrder).distinct()
        val copy = _order.value
        prefs.edit { putString("download_order", copy.joinToString(",")) }
        return copy
    }

    fun setDownloadOrder(ids: List<String>) {
        val full = (ids + _downloadOrder.value).distinct()
        prefs.edit { putString("download_order", full.joinToString(",")) }
        _downloadOrder.value = full
    }

    fun setVisibleDownloadOrder(visible: List<String>) = setDownloadOrder(reorderVisible(_downloadOrder.value, visible))

    private fun reorderVisible(current: List<String>, visible: List<String>): List<String> {
        val shown = visible.toSet()
        val reordered = ArrayDeque(visible)
        return current.map { if (it in shown && reordered.isNotEmpty()) reordered.removeFirst() else it } + reordered
    }

    /** whether [id] can play anything now: YouTube while it's on, a module when on and signed in */
    fun isUsable(id: String): Boolean = isEnabled(id) &&
        (id == YouTubeId || module(id)?.accountState() is AccountState.SignedIn)

    /** whether [id] can give a download: youtube always, since downloads have always fallen back to it, a module when on and signed in */
    fun isUsableForDownload(id: String): Boolean = id == YouTubeId || isUsable(id)

    fun nameOf(id: String): String = if (id == YouTubeId) "YouTube Music" else module(id)?.info?.name ?: id

    /** where a song plays from */
    sealed interface Route {
        data class Module(val module: SourceModule, val stream: SourceStream) : Route
        /** hand it to YouTube, by its video id */
        data object YouTube : Route
        /** every provider in [skip] or without it */
        data object Exhausted : Route
    }

    /**
     * Walks the provider order for [track], passing over the providers in [skip] (the ones that
     * already failed on this song). YouTube in the order means "play it from YouTube now".
     * [onUndecodable] hears of each stream passed over because this phone has no decoder for it.
     */
    suspend fun route(
        track: TrackQuery?,
        skip: Set<String>,
        onUndecodable: (id: String, stream: SourceStream) -> Unit = { _, _ -> },
    ): Route = walk(_order.value, track, skip, ::isUsable) { id, stream ->
        // a stream this phone has no decoder for would play in silence: the next provider has it
        DeviceDecoders.canPlay(stream).also { playable ->
            if (!playable) {
                android.util.Log.i(Tag, "$id: ${track?.title}: this phone can't decode ${stream.codec} at ${stream.sampleRateHz} Hz, passing over")
                onUndecodable(id, stream)
            }
        }
    }

    /** the download order's walk: the first provider that gives one whole file this phone can play, else youtube */
    suspend fun downloadRoute(track: TrackQuery?, skip: Set<String>): Route =
        walk(_downloadOrder.value, track, skip, ::isUsableForDownload) { id, stream ->
            // a download is saved as one file, so a stream in pieces or one only a cdm can open can't be kept
            (stream.format == StreamFormat.Progressive && stream.drm == null && DeviceDecoders.canPlay(stream)).also { savable ->
                if (!savable) android.util.Log.i(Tag, "$id: ${track?.title}: can't download a ${stream.format} ${stream.codec} stream, passing over")
            }
        }

    private suspend fun walk(
        order: List<String>,
        track: TrackQuery?,
        skip: Set<String>,
        usable: (String) -> Boolean,
        accept: (id: String, stream: SourceStream) -> Boolean,
    ): Route = withContext(Dispatchers.IO) {
        for (id in order) {
            if (id in skip || !usable(id)) continue
            if (id == YouTubeId) return@withContext Route.YouTube
            val module = module(id) ?: continue
            if (track == null) continue
            val result = runCatching { module.resolve(track, picks(id)) }
                .onFailure { android.util.Log.w(Tag, "$id failed for ${track.title}", it) }
                .getOrNull()
            when (result) {
                is ResolveResult.Found -> {
                    if (!accept(id, result.stream)) continue
                    android.util.Log.i(Tag, "$id: ${track.title} -> ${result.stream.matched.title} (${result.stream.qualityLabel})")
                    return@withContext Route.Module(module, result.stream)
                }
                is ResolveResult.NotFound -> android.util.Log.i(Tag, "$id: ${track.title}: ${result.reason}")
                is ResolveResult.Unavailable -> android.util.Log.i(Tag, "$id unavailable for ${track.title}: ${result.reason}")
                null -> Unit
            }
        }
        Route.Exhausted
    }

    /** every module, whatever it adds; playback only looks at the ones that add audio */
    val entries: StateFlow<List<ModuleEntry>> = registry.entries

    /** a module songs can play from; null for youtube and for modules that only add lyrics or a look */
    fun module(id: String): SourceModule? =
        (registry.module(id) as? SourceModule)?.takeIf { ModuleFeature.AudioSource in it.adds }

    fun isEnabled(id: String): Boolean = registry.isEnabled(id)

    private val _youTubeEnabled = MutableStateFlow(isEnabled(YouTubeId))

    /** youtube music isn't a module, so it has no entry: the settings watch its switch here */
    val youTubeEnabled: StateFlow<Boolean> = _youTubeEnabled.asStateFlow()

    fun setEnabled(id: String, enabled: Boolean) {
        registry.setEnabled(id, enabled)
        if (id == YouTubeId) _youTubeEnabled.value = enabled
        refresh()
    }

    fun picks(id: String): Map<String, String> = registry.picks(id)

    fun setPick(id: String, choiceId: String, optionId: String) = registry.setPick(id, choiceId, optionId)

    fun refresh() {
        registry.refresh()
        val installed = registry.all().filter { ModuleFeature.AudioSource in it.adds }.map { it.info.id }
        slotIn(installed, _order.value, ::setOrder)
        slotIn(installed, _downloadOrder.value, ::setDownloadOrder)
    }

    // a module installed since goes into an order just ahead of youtube
    private fun slotIn(installed: List<String>, current: List<String>, save: (List<String>) -> Unit) {
        val missing = installed.filter { it !in current }
        if (missing.isEmpty()) return
        val at = current.indexOf(YouTubeId).let { if (it < 0) current.size else it }
        save(current.take(at) + missing + current.drop(at))
    }

    companion object {
        private const val Tag = "SourceModules"
        /** youtube music in the provider order: not a module, and needs no account */
        const val YouTubeId = "youtube"
        // modules added later (jiosaavn, link modules) aren't listed: refresh slots them in just ahead of youtube, where they get asked
        private val KnownOrder = listOf(MonochromeModule.Id, AppleMusicModule.Id, YouTubeId)
    }
}
