package com.mochyy.mochymusic.utils

import android.net.ConnectivityManager
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.AuthenticationPolicy
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.utils.potoken.PoTokenResult
import com.music.innertube.YouTube
import com.music.innertube.models.Thumbnail
import com.music.innertube.models.Thumbnails
import com.music.innertube.models.response.PlayerResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.extraction.AudioQuality as XAudioQuality
import com.metrolist.innertubex.extraction.PoTokenResult as XPoTokenResult
import com.metrolist.innertubex.models.YouTubeLocale as XLocale

// innertubex's stream finder, asked before mochy's own client walk; it rides the walk's okhttp, so the app proxy and ip version follow it
internal class InnerTubeXStreams(
    private val okHttp: OkHttpClient,
    private val mintToken: suspend (videoId: String, sessionId: String) -> PoTokenResult?,
) {
    private class Engine(val http: HttpClient, val innerTube: InnerTubeX, val extractor: InnerTubeExtractor)

    // mochy's sign-in, guest id and region as innertubex last saw them
    private data class Session(
        val cookie: String?,
        val visitorData: String?,
        val dataSyncId: String?,
        val gl: String,
        val hl: String,
        val loginForBrowse: Boolean,
    )

    @Volatile private var mirrored: Session? = null
    @Volatile private var engineInstance: Engine? = null
    private val engineLock = Any()

    // built on demand and dropped in the background so quackjs's parsed player ast doesn't sit in heap forever
    private fun getOrBuildEngine(): Engine =
        engineInstance ?: synchronized(engineLock) {
            engineInstance ?: build().also { engineInstance = it }
        }

    // lets background memory trims drop the parsed player script and close ktor's scope cleanly
    fun release() {
        val old = synchronized(engineLock) {
            val current = engineInstance
            engineInstance = null
            mirrored = null
            current
        }
        old?.let { runCatching { it.http.close() } }
    }

    // the same webview tokens the walk mints, so both backends share one botguard session
    private val tokens = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): XPoTokenResult? {
            val sessionId = (if (cookie != null) YouTube.dataSyncId else null) ?: visitorData
            val token = mintToken(videoId, sessionId) ?: return null
            return XPoTokenResult(token.playerRequestPoToken, token.streamingDataPoToken, visitorData)
        }
    }

    // its warnings land in the same log as the walk's; innertubex already leaves links and tokens out of them
    private val logger = InnerTubeLogger { event ->
        val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
            InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
            InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
            InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
        }
    }

    private fun build(): Engine {
        val http = HttpClient(OkHttp) {
            // innertubex checks each answer's status itself and retries what's worth retrying
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })
            }
            engine { preconfigured = okHttp }
        }
        val innerTube = InnerTubeX(http, logger = logger)
        // no remote player hints: with this off innertubex only ever talks to youtube
        val configStore = RemotePlayerConfigStore(http, PlayerConfigRepository.disabled(), logger)
        val cipher = YouTubeCipherService(http, configStore, logger)
        val extractor = InnerTubeExtractor(
            configParser = YtConfigParserImpl(http, innerTube, configStore, logger, cipher),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = tokens,
            logger = logger,
        )
        return Engine(http, innerTube, extractor)
    }

    // fetches and compiles the player script ahead of the first song
    suspend fun prewarm() {
        val engine = getOrBuildEngine()
        mirrorSession(engine.innerTube)
        engine.extractor.prewarm()
    }

    // manifest IDs that cannot authenticate: excluded on the first pass when fetching YouTube Music Premium 256kbps streams
    private val unauthenticatedManifestIds: Set<String> by lazy {
        PlaybackClientCatalog.manifests
            .filter { it.authentication == AuthenticationPolicy.UNSUPPORTED || !it.client.loginSupported }
            .mapTo(HashSet()) { it.id }
    }

    private val unauthenticatedClientNames: Set<String> by lazy {
        PlaybackClientCatalog.manifests
            .filter { it.authentication == AuthenticationPolicy.UNSUPPORTED || !it.client.loginSupported }
            .mapTo(HashSet()) { it.client.clientName }
    }

    /** a playable stream for [videoId], or null when innertubex has none and the walk should try */
    suspend fun resolve(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferMp4: Boolean = false,
        preferAuthenticatedPremium: Boolean = false,
    ): YTPlayerUtils.PlaybackData? {
        val engine = getOrBuildEngine()
        mirrorSession(engine.innerTube)
        val isSignedIn = !YouTube.cookie.isNullOrBlank()
        val requestedQuality = if (preferMp4) XAudioQuality.MP4 else audioQuality.toInnerTubeX(connectivityManager)
        val hints = ContentHints(isUploaded = playlistId?.contains("MLPT") == true)
            .withPremium(isSignedIn && (preferAuthenticatedPremium || YTPlayerUtils.accountIsPremium == true))
            // only plain links the player can read straight through: no hls or sabr, and none that need small byte ranges
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = false)

        if (isSignedIn && preferAuthenticatedPremium && requestedQuality == XAudioQuality.HIGH) {
            val authStream = withTimeoutOrNull(GiveUpMs) {
                engine.extractor.extract(
                    videoId = videoId,
                    hints = hints,
                    excludedClients = unauthenticatedManifestIds,
                    audioQuality = requestedQuality,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                )
            }
            if (authStream != null && authStream.sabrBootstrap == null && authStream.audioUrl.isNotBlank()) {
                val isPremiumQuality = YTPlayerUtils.isPremiumStream(authStream.itag, authStream.bitrate)
                if (isPremiumQuality) {
                    YTPlayerUtils.notifyPremiumStatus(true)
                } else if (YTPlayerUtils.accountIsPremium == null) {
                    YTPlayerUtils.notifyPremiumStatus(false)
                }
                return authStream.toPlaybackData(authenticatedTracking = true)
            }
        }

        val stream = withTimeoutOrNull(GiveUpMs) {
            engine.extractor.extract(
                videoId = videoId,
                hints = hints,
                excludedClients = emptySet(),
                audioQuality = requestedQuality,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            )
        } ?: return null
        if (stream.sabrBootstrap != null || stream.audioUrl.isBlank()) return null
        // cast receivers (especially samsung and other dlna tvs) reject opus/webm, so fall through to the client walk if innertubex still handed out non-mp4
        if (preferMp4 && stream.mimeType?.contains("mp4", ignoreCase = true) != true) return null
        if (isSignedIn && YTPlayerUtils.isPremiumStream(stream.itag, stream.bitrate)) {
            YTPlayerUtils.notifyPremiumStatus(true)
        }
        val wasAuthenticatedClient = isSignedIn && stream.clientName !in unauthenticatedClientNames
        return stream.toPlaybackData(authenticatedTracking = wasAuthenticatedClient)
    }

    // copied over only when something changed, since every change starts innertubex's per-session caches again
    private fun mirrorSession(innerTube: InnerTubeX) {
        val now = Session(
            cookie = YouTube.cookie,
            visitorData = YouTube.visitorData,
            dataSyncId = YouTube.dataSyncId,
            gl = YouTube.locale.gl,
            hl = YouTube.locale.hl,
            loginForBrowse = YouTube.useLoginForBrowse,
        )
        if (now == mirrored) return
        synchronized(this) {
            if (now == mirrored) return
            innerTube.locale = XLocale(gl = now.gl, hl = now.hl)
            innerTube.replaceSession(
                cookie = now.cookie,
                visitorData = now.visitorData,
                dataSyncId = now.dataSyncId,
                authUser = "0",
                useLoginForBrowse = now.loginForBrowse,
            )
            mirrored = now
        }
    }

    // mochy's quality ladder in innertubex's words: medium is the 128k aac rung, the lossless tiers belong to modules
    private fun AudioQuality.toInnerTubeX(connectivityManager: ConnectivityManager): XAudioQuality {
        val metered = connectivityManager.isActiveNetworkMetered
        return when {
            DataSaver.isActive(metered) -> XAudioQuality.LOW
            this == AudioQuality.LOW -> XAudioQuality.LOW
            this == AudioQuality.MEDIUM -> XAudioQuality.MP4
            this == AudioQuality.AUTO -> if (metered) XAudioQuality.MP4 else XAudioQuality.HIGH
            else -> XAudioQuality.HIGH
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun ExtractedStream.toPlaybackData(authenticatedTracking: Boolean): YTPlayerUtils.PlaybackData {
        val metadata = mediaMetadata
        val fullMimeType = if (codecs.isNullOrBlank()) mimeType.orEmpty() else "${mimeType.orEmpty()}; codecs=\"$codecs\""
        val expiresIn = expiresAt
            ?.let { ((it.toEpochMilliseconds() - System.currentTimeMillis()) / 1000L).toInt() }
            ?.coerceAtLeast(1)
            ?: DefaultLinkLifeSeconds
        return YTPlayerUtils.PlaybackData(
            audioConfig = if (loudnessDb != null || perceptualLoudnessDb != null) {
                PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
            } else {
                null
            },
            videoDetails = metadata?.let {
                PlayerResponse.VideoDetails(
                    videoId = videoId,
                    title = it.title,
                    author = it.author,
                    channelId = it.channelId.orEmpty(),
                    lengthSeconds = it.durationSeconds?.toString().orEmpty(),
                    musicVideoType = it.musicVideoType,
                    viewCount = it.viewCount,
                    thumbnail = Thumbnails(it.thumbnails.map { thumb -> Thumbnail(thumb.url, thumb.width, thumb.height) }),
                )
            },
            // signed in, only authenticated tracking URLs update the user's YouTube watch history;
            // returning null for anonymous clients lets YouTubePlayReporter fetch authenticated WEB_REMIX tracking.
            playbackTracking = if (authenticatedTracking || YouTube.cookie.isNullOrBlank()) {
                playbackTracking?.let {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl = it.playbackUrl?.let(PlayerResponse.PlaybackTracking::VideostatsPlaybackUrl),
                        videostatsWatchtimeUrl = it.watchtimeUrl?.let(PlayerResponse.PlaybackTracking::VideostatsWatchtimeUrl),
                        atrUrl = null,
                    )
                }
            } else {
                null
            },
            format = PlayerResponse.StreamingData.Format(
                itag = itag,
                url = audioUrl,
                mimeType = fullMimeType,
                bitrate = bitrate ?: 0,
                width = null,
                height = null,
                contentLength = contentLengthBytes,
                quality = "",
                fps = null,
                qualityLabel = null,
                averageBitrate = bitrate,
                audioQuality = null,
                approxDurationMs = metadata?.durationSeconds?.times(1000)?.toString(),
                audioSampleRate = sampleRate,
                audioChannels = null,
                loudnessDb = loudnessDb,
                lastModified = null,
                signatureCipher = null,
                cipher = null,
                audioTrack = null,
            ),
            streamUrl = audioUrl,
            streamExpiresInSeconds = expiresIn,
            // the diagnostics page and the source banner show which backend and client found it
            streamClient = "InnerTubeX · $clientName",
            streamHeaders = headers,
        )
    }

    private companion object {
        // past this the walk takes over, so a stuck lookup never holds a song for innertubex's own 30 s budget
        const val GiveUpMs = 12_000L
        // links without an expiry are refetched after five minutes, like innertubex's own default
        const val DefaultLinkLifeSeconds = 5 * 60
    }
}
