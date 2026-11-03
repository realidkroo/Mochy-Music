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
import com.mochyy.mochymusic.utils.CrashHandler
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.cipher.CipherDeobfuscator
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.AppProxy
import com.mochyy.mochymusic.utils.BotDetectionMitigator
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// how long after the launch intro the PoToken WebView may boot (Home's reveal runs ~1s)
private const val PoTokenAfterIntroMillis = 2_500L

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var musicDatabase: com.mochyy.mochymusic.db.MusicDatabase

    override fun onCreate() {
        // before super, because that's where hilt builds the database and the player caches
        com.mochyy.mochymusic.importer.transfer.TransferRestore.applyPending(this)
        super.onCreate()

        AppLanguage.initialize(this)
        CrashHandler.install(this)

        CipherDeobfuscator.initialize(this)

        if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "fast") {
            Timber.plant(Timber.DebugTree())
        }

        PreferencesCache.start(this, applicationScope)
        com.mochyy.mochymusic.utils.NotRecommended.init(this)

        applicationScope.launch(Dispatchers.IO) {
            try {
                musicDatabase.query { purgeTimestampArtists() }
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

        applicationScope.launch(Dispatchers.IO) {
            // Boot BotGuard once a session id exists. Without a PoToken YouTube serves only a
            // ~32 second preview window, and paying the engine's cold start inside the playback
            // resolver stalls the first track instead.
            val ready = kotlinx.coroutines.withTimeoutOrNull(60_000) {
                while (YouTube.visitorData == null && YouTube.dataSyncId == null) {
                    kotlinx.coroutines.delay(500)
                }
                true
            }
            if (ready == true) {
                // its WebView boots on the main thread; let the launch intro and Home's reveal
                // play out first. VISIONOS streams need no token, so nothing waits on this
                com.mochyy.mochymusic.ui.launch.LaunchGate.awaitIntro()
                kotlinx.coroutines.delay(PoTokenAfterIntroMillis)
                com.mochyy.mochymusic.utils.YTPlayerUtils.preWarmPoToken()
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            // Local songs whose cover file was cleared away get it back from the song's own file.
            // After the intro, so it doesn't share the disk with the first frames.
            com.mochyy.mochymusic.ui.launch.LaunchGate.awaitIntro()
            runCatching { com.mochyy.mochymusic.importer.LocalArtwork.repair(this@App, musicDatabase) }
                .onFailure { Timber.w(it, "Repairing local covers failed") }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.refreshApplicationResources(this)
    }

    private suspend fun initializeSettings() {
        val settings = dataStore.data.first()
        com.mochyy.mochymusic.utils.OfflineMode.set(settings[OfflineModeKey] ?: false)
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
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e(e, "Could not parse cookie. Clearing existing cookie.")
                        forgetAccount(this@App)
                    }
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
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = runBlocking {
            dataStore.data.map { it[MaxImageCacheSizeKey] ?: 512 }.first()
        }
        return ImageLoader.Builder(this).apply {
            components {
                add(SvgDecoder.Factory())
            }
            // covers ease in as they arrive; coil skips the fade for ones already in memory, so scrolling back never flickers
            crossfade(300)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
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
            }
            Timber.d("forgetAccount: DataStore preferences cleared")

            Timber.d("forgetAccount: Clearing YouTube object auth state")
            Timber.d("forgetAccount: Before - cookie=${YouTube.cookie?.take(50)}, visitorData=${YouTube.visitorData?.take(20)}, dataSyncId=${YouTube.dataSyncId?.take(20)}")
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            Timber.d("forgetAccount: After - cookie=${YouTube.cookie}, visitorData=${YouTube.visitorData}, dataSyncId=${YouTube.dataSyncId}")

            Timber.d("forgetAccount: Clearing WebView CookieManager")
            withContext(Dispatchers.Main) {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies { removed ->
                        Timber.d("forgetAccount: CookieManager.removeAllCookies callback: removed=$removed")
                    }
                    flush()
                }
            }
            Timber.d("forgetAccount: Logout process complete")
        }
    }
}
