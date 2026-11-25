// App.kt

package com.mochyy.mochymusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import com.music.kugou.KuGou
import com.mochyy.mochymusic.constants.*
import com.mochyy.mochymusic.di.ApplicationScope
import com.mochyy.mochymusic.i18n.AppLanguage
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.ui.theme.AppFonts
import com.mochyy.mochymusic.utils.CrashHandler
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.cipher.CipherDeobfuscator
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.AppProxy
import com.mochyy.mochymusic.utils.BotDetectionMitigator
import com.mochyy.mochymusic.utils.isMainProcess
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import com.mochyy.mochymusic.link.isTelevision

// one character from each system fallback font titles commonly reach for: japanese, chinese, korean, emoji
private const val FallbackFontWarmup = "あ漢한😀"

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var musicDatabase: com.mochyy.mochymusic.db.MusicDatabase

    // only built once a tv is paired, it pulls in the downloads
    @Inject
    lateinit var phoneLink: dagger.Lazy<com.mochyy.mochymusic.link.phone.PhoneLinkHost>

    override fun onCreate() {
        // init random-key vault first so transfer restore and settings can use it right away
        com.mochyy.mochymusic.utils.SecretVault.init(this)
        com.mochyy.mochymusic.importer.transfer.TransferRestore.applyPending(this)
        // the folders a move to another drive left behind, cleared once nothing has them open
        com.mochyy.mochymusic.playback.StoragePlaces.cleanUpLeftovers(this)
        super.onCreate()

        AppLanguage.initialize(this)
        CrashHandler.install(this)

        // share one okhttp connection pool and dispatcher between innertube, coil, and the rest of the app
        YouTube.configureSharedPool(AppProxy.connectionPool, AppProxy.dispatcher)
        CipherDeobfuscator.initialize(this)

        if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "fast") {
            Timber.plant(Timber.DebugTree())
        }

        PreferencesCache.start(this, applicationScope)
        if (!isTelevision() && isMainProcess(this)) AppFonts.start(this, applicationScope)
        // tokens saved before the vault existed are sealed on the first start after the update
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) { (dataStore as? com.mochyy.mochymusic.utils.SecretPreferences)?.sealLeftovers() }
        com.mochyy.mochymusic.utils.NotRecommended.init(this)

        applicationScope.launch(Dispatchers.IO) {
            try {
                musicDatabase.query { purgeTimestampArtists() }
            } catch (e: Exception) {
                reportException(e)
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            // the plain 5 s crossfade used to be the default and onboarding's pick, and it never mixed a thing: anyone left on it moves to crossmix once
            try {
                if (dataStore.data.first()[CrossmixDefaultMovedKey] != true) {
                    dataStore.edit { prefs ->
                        if (prefs[CrossmixModeKey] == CrossmixMode.AUTO_CROSSFADE.name) prefs[CrossmixModeKey] = CrossmixMode.CROSSMIX.name
                        prefs[CrossmixDefaultMovedKey] = true
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            // lyrics saved by older versions are cleared once, the first time 7.1.0 runs, so every
            // song fetches them fresh. only the lyrics table is touched (songs, playlists, the
            // library and the profile stay), and lyrics someone typed in or edited by hand are kept.
            try {
                if ((dataStore.data.first()[LyricsCacheWipeKey] ?: 0) < LyricsCacheWipeGeneration) {
                    musicDatabase.clearFetchedLyrics(
                        keepProviders = listOf(
                            com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel.USER_EDITED,
                            "Manual",
                        ),
                    )
                    dataStore.edit { it[LyricsCacheWipeKey] = LyricsCacheWipeGeneration }
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }

        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }

        // potoken webview and innertubex both boot on demand when a song actually needs them, keeping ~80 mb of chromium and quackjs out of startup and background ram

        applicationScope.launch(Dispatchers.IO) {
            // only boot the background link service at process start when the user opted in and a paired tv needs it;
            // otherwise MainActivity.onStart brings the link up while on screen and tears it down when closed
            if (isTelevision() || !com.mochyy.mochymusic.link.phone.PhoneLinkHost.shouldStartInBackground(this@App)) return@launch
            com.mochyy.mochymusic.ui.launch.LaunchGate.awaitIntro()
            phoneLink.get().refresh()
            com.mochyy.mochymusic.link.phone.PhoneLinkService.start(this@App)
        }

        applicationScope.launch(Dispatchers.IO) {
            // Local songs whose cover file was cleared away get it back from the song's own file.
            // After the intro, so it doesn't share the disk with the first frames.
            com.mochyy.mochymusic.ui.launch.LaunchGate.awaitIntro()
            runCatching { com.mochyy.mochymusic.importer.LocalArtwork.repair(this@App, musicDatabase) }
                .onFailure { Timber.w(it, "Repairing local covers failed") }
            // once: the cover every song of an album saved for itself becomes one shared file, and huge ones shrink
            runCatching { com.mochyy.mochymusic.importer.LocalArtwork.compact(this@App) }
                .onFailure { Timber.w(it, "Sharing out local covers failed") }
        }

        applicationScope.launch(Dispatchers.Default) {
            // the first japanese, korean or emoji title loaded its system font inside a scroll frame (6 ms on the avd); here it loads once, off the main thread
            com.mochyy.mochymusic.ui.launch.LaunchGate.awaitIntro()
            runCatching { android.graphics.Paint().measureText(FallbackFontWarmup) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.refreshApplicationResources(this)
    }

    private suspend fun initializeSettings() {
        val settings = dataStore.data.first()
        com.mochyy.mochymusic.utils.OfflineMode.set(settings[OfflineModeKey] ?: false)
        com.mochyy.mochymusic.utils.DataSaver.attach(this)
        val locale = AppLanguage.systemLocale(this)
        val languageTag = locale.language

        YouTube.locale = YouTubeLocale(
            gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = settings[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )

        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        // before anything asks youtube, so the first requests already go through the proxy
        AppProxy.apply(AppProxy.configFrom(settings))

        YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: true
        YouTube.ipVersion = settings[IpVersionKey]?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO
        com.mochyy.mochymusic.utils.YTPlayerUtils.backend =
            settings[StreamBackendKey]?.toEnum(defaultValue = StreamBackend.INNERTUBEX) ?: StreamBackend.INNERTUBEX
        com.mochyy.mochymusic.utils.YTPlayerUtils.accountIsPremium = settings[YtAccountIsPremiumKey]
        com.mochyy.mochymusic.utils.YTPlayerUtils.onPremiumDetected = { isPremium ->
            applicationScope.launch(Dispatchers.IO) {
                runCatching { dataStore.edit { it[YtAccountIsPremiumKey] = isPremium } }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "updates",
                getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.update_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun observeSettingsChanges() {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[OfflineModeKey] ?: false }
                .distinctUntilChanged()
                .collect { com.mochyy.mochymusic.utils.OfflineMode.set(it) }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData?.takeIf { it != "null" }
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie?.takeIf { it.isNotBlank() }
                    } catch (e: Exception) {
                        Timber.e(e, "Could not parse cookie. Clearing existing cookie.")
                        forgetAccount(this@App)
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[YtAccountIsPremiumKey] }
                .distinctUntilChanged()
                .collect { isPremium ->
                    com.mochyy.mochymusic.utils.YTPlayerUtils.accountIsPremium = isPremium
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[ContentCountryKey] to it[ContentLanguageKey] }
                .distinctUntilChanged()
                .collect { (contentCountry, contentLanguage) ->
                    // UI language must not silently change content preferences.
                    val systemLocale = AppLanguage.systemLocale(this@App)
                    YouTube.locale = YouTubeLocale(
                        gl = contentCountry?.takeIf { it != SYSTEM_DEFAULT }
                            ?: systemLocale.country.takeIf { it in CountryCodeToName }
                            ?: "US",
                        hl = contentLanguage?.takeIf { it != SYSTEM_DEFAULT }
                            ?: systemLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
                            ?: systemLocale.language.takeIf { it in LanguageCodeToName }
                            ?: "en"
                    )
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { AppProxy.configFrom(it) }
                .distinctUntilChanged()
                .collect { config ->
                    // a new way out gets a fresh guest session too, the old one may be what youtube flagged
                    if (AppProxy.apply(config) && YouTube.cookie == null) BotDetectionMitigator.rotateGuestSession()
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[IpVersionKey] }
                .distinctUntilChanged()
                .collect { ipVersion ->
                    YouTube.ipVersion = ipVersion?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[StreamBackendKey]?.toEnum(defaultValue = StreamBackend.INNERTUBEX) ?: StreamBackend.INNERTUBEX }
                .distinctUntilChanged()
                .collectIndexed { _, backend ->
                    com.mochyy.mochymusic.utils.YTPlayerUtils.backend = backend
                }
        }
    }

    // frees ui-only caches, webviews, kuromoji and sqlite pages when hidden or short on ram while leaving playback and dac untouched
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.mochyy.mochymusic.utils.AppMemory.onTrimMemory(this, level, applicationScope)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // read from the already-warmed preferences cache so the first image never blocks the main thread
        val cacheSize = com.mochyy.mochymusic.utils.PreferencesCache.current(dataStore)[MaxImageCacheSizeKey] ?: 512
        return ImageLoader.Builder(this).apply {
            components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { AppProxy.sharedClient }))
                add(SvgDecoder.Factory())
                add(com.mochyy.mochymusic.utils.ImageRetry)
            }
            // covers ease in as they arrive; coil skips the fade for ones already in memory, so scrolling back never flickers
            crossfade(300)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    companion object {
        suspend fun forgetAccount(context: Context) {
            Timber.d("forgetAccount: Starting logout process")

            Timber.d("forgetAccount: Clearing DataStore preferences")
            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
                settings.remove(YtAccountIsPremiumKey)
                settings.remove(LastFullSyncKey)
                settings.remove(YtSyncHoldKey)
                settings.remove(SelectedYtmPlaylistsKey)
                settings.remove(YtSyncSourcesTaggedKey)
            }
            Timber.d("forgetAccount: DataStore preferences cleared")

            Timber.d("forgetAccount: Clearing YouTube object auth state")
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            com.mochyy.mochymusic.utils.YTPlayerUtils.accountIsPremium = null
            com.mochyy.mochymusic.importer.YouTubeLikesMemory.clear(context)
            com.mochyy.mochymusic.importer.PendingYouTubeLikes.clear(context)
            com.mochyy.mochymusic.importer.PlaylistAddedHere.clearAll(context)

            Timber.d("forgetAccount: Clearing WebView CookieManager & WebStorage")
            withContext(Dispatchers.Main) {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies { removed ->
                        Timber.d("forgetAccount: CookieManager.removeAllCookies callback: removed=$removed")
                    }
                    flush()
                }
                android.webkit.WebStorage.getInstance().apply {
                    listOf(
                        "https://music.youtube.com",
                        "https://www.youtube.com",
                        "https://youtube.com",
                        "https://accounts.google.com",
                        "https://google.com",
                    ).forEach { origin -> runCatching { deleteOrigin(origin) } }
                }
            }
            Timber.d("forgetAccount: Logout process complete")
        }
    }
}
