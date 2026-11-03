// YTPlayerUtils.kt

package com.mochyy.mochymusic.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.mochyy.mochymusic.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.utils.cipher.CipherDeobfuscator
import com.mochyy.mochymusic.utils.YTPlayerUtils.MAIN_CLIENT
import com.mochyy.mochymusic.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.mochyy.mochymusic.utils.YTPlayerUtils.validateStatus
import com.mochyy.mochymusic.utils.potoken.PoTokenGenerator
import com.mochyy.mochymusic.utils.potoken.PoTokenResult
import com.mochyy.mochymusic.utils.sabr.EjsNTransformSolver
import com.mochyy.mochymusic.utils.PlaybackLogLevel
import com.mochyy.mochymusic.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import io.ktor.client.plugins.ResponseException
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /** True once the BotGuard engine holds a usable session token. Surfaced in diagnostics. */
    val hasPoToken: Boolean
        get() = poTokenGenerator.hasSessionToken

    /**
     * Boot the BotGuard engine before the first track needs it. Safe to call more than once.
     */
    suspend fun preWarmPoToken() {
        val sessionId = (if (YouTube.cookie != null) YouTube.dataSyncId else null)
            ?: YouTube.visitorData
            ?: return
        poTokenGenerator.preWarm(sessionId)
    }

    /**
     * VISIONOS, which Metrolist also puts first for streams.
     *
     * Verified against live googlevideo: with `visitorData` set it returns `OK`, every audio
     * itag (139/140/249/250/251) with a **direct, non-ciphered** url, full `audioConfig`
     * loudness data and `playbackTracking` - and its urls serve the *whole* file (206 at 1 MB,
     * at the midpoint and at the final byte). It needs **no PoToken**.
     *
     * That last point is why it belongs here rather than WEB_REMIX. An untokened url is served
     * only up to roughly its first 786 KB, which is ~32 seconds at 128 kbps and ~28 at higher
     * bitrates - the fixed *byte* window is the giveaway. WEB_REMIX can only clear that window
     * with a working PoToken, and minting one blocks the first play for as long as BotGuard
     * takes. VISIONOS sidesteps both problems, so PoToken minting now only happens if playback
     * actually falls through to a web client (see the lazy mint in the loop below).
     *
     * Requires `visitorData`: without it VISIONOS answers `UNPLAYABLE`. App.kt fetches one on
     * startup when absent, so this holds for guests too.
     */
    private val MAIN_CLIENT: YouTubeClient = VISIONOS

    /**
     * Playability statuses that mean "this client may not serve the stream, but another one
     * can". `LOGIN_REQUIRED` belongs here - it is what YouTube returns for "Sign in to confirm
     * you're not a bot" - and leaving it out meant those tracks never reached the fallback
     * clients that can play them. Matches Metrolist.
     */
    private val RESTRICTED_STATUSES = listOf(
        "AGE_CHECK_REQUIRED",
        "AGE_VERIFICATION_REQUIRED",
        "LOGIN_REQUIRED",
        "CONTENT_CHECK_REQUIRED",
    )

    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * VISIONOS first - it is the only client measured to serve a complete track without a
     * PoToken. The PoToken-capable web clients follow for anything it cannot play, then the
     * remaining clients for restricted content. IOS / IPADOS / ANDROID_VR are last: they return
     * clean urls that look healthy and then stop dead at the ~786 KB untokened window.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        WEB_REMIX,
        WEB,
        WEB_CREATOR,
        IPADOS,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_65_10,
        ANDROID_VR_NO_AUTH,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_CREATOR,
        MOBILE,
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
        val streamHeaders: Map<String, String> = emptyMap(),
        // youtube refused the song, so this is its music video's sound
        val fromMusicVideo: Boolean = false,
    )

    /**
     * Headers googlevideo expects alongside a stream url. It checks these against the client
     * named in the url's own `c=` parameter, so a WEB_REMIX url fetched without the YouTube
     * Music origin is refused. mochy previously sent none of this.
     */
    internal fun YouTubeClient.streamHeaders(): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "en-US,en;q=0.9")

            when (clientName) {
                "WEB_REMIX" -> {
                    put("Referer", "https://music.youtube.com/")
                    put("Origin", "https://music.youtube.com")
                }

                "WEB_CREATOR" -> {
                    put("Referer", "https://studio.youtube.com/")
                    put("Origin", "https://studio.youtube.com")
                }

                else -> {
                    put("Referer", "https://www.youtube.com/")
                    put("Origin", "https://www.youtube.com")
                }
            }
        }

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> {
        val firstAttempt = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)
            .orStandIn(videoId, audioQuality, connectivityManager)

        // a rate limit is the whole network, not the session: rotating asks YouTube again and
        // retrying walks every client again, which only keeps the limit in place
        if (firstAttempt.isFailure && YouTube.cookie == null &&
            firstAttempt.exceptionOrNull() !is YouTubeRateLimitedException
        ) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult.unwrapRefusal()
        }

        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt.unwrapRefusal()
    }

    // youtube turns a few uploads away from every client we have ("please sign in"), but the artist's other upload of the song usually plays
    private suspend fun Result<PlaybackData>.orStandIn(
        videoId: String,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> {
        val details = (exceptionOrNull() as? UploadRefusedException)?.videoDetails ?: return this
        val title = details.title ?: return this
        val artist = details.author ?: return this
        val length = details.lengthSeconds.toIntOrNull() ?: return this

        for (standIn in SongVersions.otherUploads(videoId, title, artist, length).take(MAX_STAND_INS)) {
            val attempt = resolvePlaybackData(standIn.id, null, audioQuality, connectivityManager)
            if (attempt.exceptionOrNull() is YouTubeRateLimitedException) return attempt
            val data = attempt.getOrNull() ?: continue
            Timber.tag(logTag).i("YouTube refused $videoId, playing its other upload ${standIn.id}")
            PlaybackLogManager.log(PlaybackLogLevel.INFO, "Playing another upload", "$videoId -> ${standIn.id}")
            // the song keeps its own details, and the play isn't reported as the stand-in's
            return Result.success(
                data.copy(videoDetails = details, playbackTracking = null, fromMusicVideo = standIn.isVideoSong),
            )
        }
        return this
    }

    // the refusal only carried the song's details to the stand-in lookup, callers get the error it wraps
    private fun Result<PlaybackData>.unwrapRefusal(): Result<PlaybackData> =
        (exceptionOrNull() as? UploadRefusedException)?.let { Result.failure(it.failure) } ?: this

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")

        println("[PLAYBACK_DEBUG] playerResponseForPlayback called: videoId=$videoId, playlistId=$playlistId")

        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        // VISIONOS answers UNPLAYABLE without a visitorData, and App.kt only fetches one
        // asynchronously at startup - so a track played early would silently fall through to a
        // client that truncates at ~786 KB. Make sure we have one before asking for a stream.
        if (!isLoggedIn && YouTube.visitorData.isNullOrEmpty()) {
            Timber.tag(logTag).d("No visitorData yet - fetching one before resolving playback")
            YouTube.refreshVisitorData()
                .onFailure { Timber.tag(logTag).w(it, "Could not fetch visitorData: ${it.message}") }
        }

        // NewPipe's signature-timestamp lookup is a blocking network round trip, and it is only
        // meaningful for clients that hand back ciphered urls. VISIONOS does not, so resolving
        // this eagerly made every play wait for a request it had no use for. Resolved lazily:
        // an ordinary play never touches it, and only a ciphered web client pays the cost.
        val signatureTimestamp: SignatureTimestampResult by lazy { getSignatureTimestampOrNull(videoId) }

        // why each client couldn't play, in the order tried: what the error says when none can
        val failures = mutableListOf<String>()
        // false to the end means youtube refused the upload itself, not just how we asked for it
        var anyClientServed = false

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.poTokenFor(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")
        val mainSigTimestamp = if (MAIN_CLIENT.useSignatureTimestamp) signatureTimestamp.timestamp else null
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, mainSigTimestamp, poToken?.playerRequestPoToken)
            .getOrElse { e ->
                if (e.isRateLimit()) throw YouTubeRateLimitedException()
                // Ktor's message carries the whole request url and response body
                if (e is ResponseException) throw Exception("YouTube refused the request (${e.shortReason()})", e)
                throw e
            }

        var metadataResponse: PlayerResponse? = null
        if (isLoggedIn) {
            Timber.tag(logTag).d("Fetching metadata from METADATA_CLIENT (WEB_REMIX) for authenticated tracking")
            try {

                var metaPoToken: PoTokenResult? = null
                val metaSessionId = YouTube.dataSyncId
                if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                    try {
                        metaPoToken = poTokenGenerator.poTokenFor(videoId, metaSessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Metadata PoToken generation failed")
                    }
                }
                metadataResponse = YouTube.player(
                    videoId, playlistId, METADATA_CLIENT,
                    if (METADATA_CLIENT.useSignatureTimestamp) signatureTimestamp.timestamp else null,
                    metaPoToken?.playerRequestPoToken
                ).getOrNull()
                Timber.tag(logTag).d("Metadata response obtained: ${metadataResponse?.playabilityStatus?.status}")
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "Failed to fetch metadata from METADATA_CLIENT")
            }
        }

        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in RESTRICTED_STATUSES
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Restricted content detected, trying WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        if (mainPlayerResponse == null) {
            throw Exception("Failed to get player response")
        }

        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        // signed in, only the signed-in lookup's links count a play on the account; visionos ones would land in an anonymous history
        val playbackTracking = if (isLoggedIn) metadataResponse?.playbackTracking else mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var successClient: YouTubeClient? = null
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        val currentStatus = mainPlayerResponse.playabilityStatus.status
        var isAgeRestricted = currentStatus in RESTRICTED_STATUSES

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Log.i(TAG, "Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        val startIndex = when {
            isPrivateTrack -> 1
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {

            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {

                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {

                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {

                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    try {
                        poToken = poTokenGenerator.poTokenFor(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null

                val clientSigTimestamp =
                    if (wasOriginallyAgeRestricted || !client.useSignatureTimestamp) {
                        null
                    } else {
                        signatureTimestamp.timestamp
                    }
                val result = YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken)
                result.exceptionOrNull()?.let { e ->
                    // every client asks the same host: the rest would be refused too
                    if (e.isRateLimit()) throw YouTubeRateLimitedException()
                    failures += "${client.clientName}: ${e.shortReason()}"
                }
                streamPlayerResponse = result.getOrNull()
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                anyClientServed = true
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName)

                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                val responseToUse = streamPlayerResponse

                val clientLabel =
                    if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName

                val formatCandidates = selectAudioFormatCandidates(
                    responseToUse,
                    audioQuality,
                    connectivityManager.isActiveNetworkMetered,
                )

                if (formatCandidates.isEmpty()) {
                    Timber.tag(logTag).d("No suitable format found for client: $clientLabel")
                    continue
                }

                // Walk the candidates so a format we cannot deobfuscate falls back to the
                // next best one instead of throwing away an otherwise healthy client.
                for (candidate in formatCandidates) {
                    val candidateUrl =
                        findUrlOrNull(candidate, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                    if (candidateUrl != null) {
                        format = candidate
                        streamUrl = candidateUrl
                        break
                    }
                    Timber.tag(logTag).d("Could not resolve URL for ${candidate.mimeType}@${candidate.bitrate}, trying next candidate")
                }

                if (format == null || streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for any candidate format on client: $clientLabel")
                    continue
                }

                Timber.tag(logTag).d("Format selected: ${format.mimeType}, bitrate: ${format.bitrate} ($clientLabel)")

                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (currentClient.useWebPoTokens) {
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${android.net.Uri.encode(poToken.streamingDataPoToken)}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                // The last client used to be taken untested too, as better than nothing. It isn't:
                // a cut-off url plays ~30 seconds, reaches READY and resets the skip counter, so
                // the player kept going down the queue - failing cleanly here stops that.
                if (isPrivatelyOwned) {
                    Timber.tag(logTag).d("Using stream without validation: ${currentClient.clientName}")
                    successClient = currentClient
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    break
                }

                if (validateStatus(streamUrl!!, currentClient.streamHeaders(), format.contentLength)) {

                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    successClient = currentClient
                    PlaybackLogManager.log(
                        PlaybackLogLevel.INFO,
                        "Stream validated",
                        "client=${currentClient.clientName} itag=${format.itag} " +
                            "pot=${poToken?.streamingDataPoToken?.length ?: 0} " +
                            "bytes=${format.contentLength ?: 0}",
                    )

                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")

                    if (currentClient.useWebPoTokens) {
                        var nTransformWorked = false

                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            if (nTransformed != streamUrl) {
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(nTransformed, currentClient.streamHeaders(), format.contentLength)) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    successClient = currentClient
                                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                        }

                        if (nTransformWorked) break
                    }
                    failures += "${currentClient.clientName}: stream cut off"
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")
                // a request that failed outright was noted where it was made
                if (streamPlayerResponse != null) {
                    failures += "${client.clientName}: $status" +
                        streamPlayerResponse.playabilityStatus.reason?.let { " ($it)" }.orEmpty()
                }

                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        // Every client failed. The error used to describe only the last one tried - MOBILE, whose
        // request failing outright came out as "Bad stream player response" - so the reason the
        // song couldn't play (VISIONOS refused it, the others cut off) never reached the user.
        if (successClient == null) {
            Timber.tag(logTag).e("All clients failed for $videoId: ${failures.joinToString(" | ")}")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            val summary = failureSummary(failures)
            val last = streamPlayerResponse
            val failure = if (last != null && last.playabilityStatus.status != "OK") {
                PlaybackException(summary, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
            } else {
                Exception(summary)
            }
            // a user's own upload is never swapped for someone else's copy
            if (!anyClientServed && !isUploadedTrack && !isPrivateTrack) {
                throw UploadRefusedException(failure, mainPlayerResponse.videoDetails)
            }
            throw failure
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            streamClient = successClient?.clientName ?: "unknown",
            streamHeaders = successClient?.streamHeaders().orEmpty(),
        )
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")

        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }

    private val VIDEO_BACKGROUND_CLIENTS: Array<YouTubeClient> = arrayOf(
        MAIN_CLIENT,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_VR_1_61_48,
        WEB,
    )

    // every size of the video's picture from the first client whose links really serve the whole file
    suspend fun resolveVideoStreams(videoId: String): Result<VideoStreams> = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // without a visitorData the first client says UNPLAYABLE and the ones after it stop the video a few seconds in
            if (YouTube.cookie == null && YouTube.visitorData.isNullOrEmpty()) {
                YouTube.refreshVisitorData()
                    .onFailure { Timber.tag(logTag).w(it, "resolveVideoStreams: no visitorData: ${it.message}") }
            }
            val signatureTimestamp: SignatureTimestampResult by lazy { getSignatureTimestampOrNull(videoId) }

            for (client in VIDEO_BACKGROUND_CLIENTS) {
                val streams = try {
                    videoStreamsFrom(client, videoId) { signatureTimestamp.timestamp }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(logTag).d("resolveVideoStreams: client ${client.clientName} threw: ${e.message}")
                    null
                }
                if (streams != null) {
                    Timber.tag(logTag).d("resolveVideoStreams: ${streams.tracks.size} sizes via ${client.clientName}")
                    return@withContext streams
                }
            }
            throw Exception("Could not resolve a playable video stream for videoId=$videoId after trying ${VIDEO_BACKGROUND_CLIENTS.size} clients")
        }
    }

    // one client's sizes, or null when it can't play the video or its links stop early
    private suspend fun videoStreamsFrom(
        client: YouTubeClient,
        videoId: String,
        signatureTimestamp: () -> Int?,
    ): VideoStreams? {
        val timestamp = if (client.useSignatureTimestamp) signatureTimestamp() else null
        val response = YouTube.player(videoId, null, client, timestamp, null).getOrNull() ?: return null
        if (response.playabilityStatus.status != "OK") {
            Timber.tag(logTag).d("resolveVideoStreams: client ${client.clientName} not OK: ${response.playabilityStatus.status}")
            return null
        }
        val streaming = response.streamingData ?: return null
        suspend fun trackOf(format: PlayerResponse.StreamingData.Format): VideoTrack? {
            var url = runCatching { findUrlOrNull(format, videoId, response, skipNewPipe = false) }.getOrNull()
                ?: return null
            if (client.useWebPoTokens) {
                url = runCatching { EjsNTransformSolver.transformNParamInUrl(url) }.getOrDefault(url)
            }
            return VideoTrack(
                itag = format.itag,
                url = url,
                mimeType = format.containerType(),
                codecs = format.codecs(),
                width = format.width ?: 0,
                height = format.height ?: 0,
                fps = format.fps ?: 30,
                bitrate = format.bitrate,
                initRange = format.initRange?.toLongRange(),
                indexRange = format.indexRange?.toLongRange(),
                contentLength = format.contentLength,
            )
        }
        val tracks = pickVideoFamily(streaming.adaptiveFormats).mapNotNull { trackOf(it) }
        if (tracks.isEmpty()) {
            Timber.tag(logTag).d("resolveVideoStreams: client ${client.clientName} had no video format")
            return null
        }
        val headers = client.streamHeaders()
        // checked at the end of the file: a link that only serves its first ~786 KB froze the picture three seconds in
        val probe = tracks.minBy { kotlin.math.abs(it.shortSide - 720) }
        if (!validateStatus(probe.url, headers, probe.contentLength)) {
            Timber.tag(logTag).d("resolveVideoStreams: client ${client.clientName} cuts the video off")
            return null
        }
        val durationMs = streaming.adaptiveFormats.firstNotNullOfOrNull { it.approxDurationMs?.toLongOrNull() }
            ?: response.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
            ?: 0L
        val sharpTracks = pickSharpFamily(streaming.adaptiveFormats, tracks.last().shortSide).mapNotNull { trackOf(it) }
        return VideoStreams(
            videoId = videoId,
            tracks = tracks,
            headers = headers,
            expiresAtMs = System.currentTimeMillis() + streaming.expiresInSeconds * 1000L,
            durationMs = durationMs,
            sharpTracks = sharpTracks,
        )
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? =
        selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            connectivityManager.isActiveNetworkMetered,
        ).firstOrNull()

    /**
     * Returns every usable audio format ordered best-first for [audioQuality].
     *
     * Callers walk this list so that a format whose URL cannot be resolved (ciphered
     * without a working deobfuscator, for example) falls back to the next best one
     * instead of discarding the whole client.
     */
    internal fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.bitrate > 0 }
            ?.filter { !it.url.isNullOrEmpty() || !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }
            .orEmpty()

        if (audioFormats.isEmpty()) {
            Timber.tag(logTag).d("No suitable audio format found")
            return emptyList()
        }

        // Prefer the original (non auto-dubbed) track when the response offers both.
        val originalFormats = audioFormats.filter { it.isOriginal }
        val usableFormats = originalFormats.ifEmpty { audioFormats }

        val effectiveQuality = when (audioQuality) {
            AudioQuality.AUTO -> if (networkMetered) AudioQuality.MEDIUM else AudioQuality.HIGH
            // Lossless and Atmos tiers were Monochrome's (a source module now); when
            // YouTube is the source we still want its best available stream.
            AudioQuality.LOSSLESS, AudioQuality.HI_RES_LOSSLESS, AudioQuality.DOLBY_ATMOS -> AudioQuality.HIGH
            else -> audioQuality
        }

        // YouTube's audio ladder is roughly: ~50k (itag 139/249), ~70k (250),
        // ~128k AAC (140), ~160k Opus (251). The MEDIUM ceiling sits just under the
        // Opus rung so it lands on 128k AAC while HIGH keeps the best stream.
        val targetBitrateBps = when (effectiveQuality) {
            AudioQuality.LOW -> 70_000
            AudioQuality.MEDIUM -> 135_000
            else -> null // HIGH: take the best available
        }

        // YouTube's own AUDIO_QUALITY_* label outranks raw bitrate: a track can carry a
        // higher-bitrate stream that YouTube still labels MEDIUM, and picking it is what made
        // the HIGH setting feel like it did nothing.
        fun qualityRank(format: PlayerResponse.StreamingData.Format): Int = when (format.audioQuality) {
            "AUDIO_QUALITY_HIGH" -> 3
            "AUDIO_QUALITY_MEDIUM" -> 2
            "AUDIO_QUALITY_LOW" -> 1
            else -> 0
        }

        val preferHigher = compareByDescending<PlayerResponse.StreamingData.Format> { !it.url.isNullOrEmpty() }
            .thenByDescending { qualityRank(it) }
            .thenByDescending { it.audioChannels ?: 2 }
            .thenByDescending { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget = compareByDescending<PlayerResponse.StreamingData.Format> { !it.url.isNullOrEmpty() }
            .thenBy { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates = if (targetBitrateBps == null) {
            usableFormats.sortedWith(preferHigher)
        } else {
            // Best stream at or below the target, then the cheapest one above it.
            usableFormats.filter { it.bitrate <= targetBitrateBps }.sortedWith(preferHigher) +
                usableFormats.filter { it.bitrate > targetBitrateBps }.sortedWith(preferLowerAboveTarget)
        }

        Timber.tag(logTag).d(
            "Format candidates ($effectiveQuality): " + candidates.take(8).joinToString {
                "${it.mimeType}@${it.bitrate}${if (it.url.isNullOrEmpty()) " (cipher)" else ""}"
            }
        )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? =
        Regex("""codecs="([^"]+)"""").find(mimeType)
            ?.groupValues?.getOrNull(1)
            ?.split(",")?.firstOrNull()?.trim()

    private fun codecRank(codec: String?): Int = when {
        codec.isNullOrBlank() -> 0
        codec.contains("opus", ignoreCase = true) -> 3
        codec.contains("mp4a", ignoreCase = true) -> 2
        else -> 1
    }

    /**
     * Checks whether a stream url serves the whole song.
     *
     * It asks for the song's last byte. Untokened urls (IPADOS, ANDROID_VR) serve only the first
     * ~786 KB and answer 403 past it, so the first byte - what this used to ask for - came back
     * fine, the song was taken, and it died about 30 seconds in. Each of those reached READY,
     * which resets the skip-after-failure counter, so the player went down the whole queue
     * downloading 30 seconds of every song and playing none. Measured 2026-10-18: VISIONOS 206
     * at byte 0, at 1 MiB and at the end; IPADOS and ANDROID_VR 1.65 206 up to 786 KB, 403 from
     * 1 MiB on.
     *
     * The headers matter: googlevideo validates Origin/Referer against the client named in the
     * url, so probing without them can fail on a url that plays perfectly well. HEAD is not
     * usable here - googlevideo answers it with 403 even for good urls - so this is a ranged GET.
     */
    private fun validateStatus(
        url: String,
        headers: Map<String, String> = emptyMap(),
        contentLength: Long? = null,
    ): Boolean {
        val offset = contentLength?.takeIf { it > 1 }?.minus(1) ?: UNTOKENED_WINDOW_PROBE_BYTE
        Timber.tag(logTag).d("Validating stream URL status at byte $offset")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .get()
                .url(url)
                .header("Range", "bytes=$offset-$offset")

            headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            if (headers["User-Agent"] == null) {
                requestBuilder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            }

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            httpClient.newCall(requestBuilder.build()).execute().use {
                // with no length to aim at, a song shorter than the probe answers 416: served, not
                // refused (a cut-off url answers 403)
                val isSuccessful = it.isSuccessful || it.code == 206 ||
                    (it.code == 416 && contentLength == null)
                Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${it.code})")
                return isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }

    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }

    /** Past the ~786 KB an untokened url serves; probed when the song's length isn't known. */
    private const val UNTOKENED_WINDOW_PROBE_BYTE = 1_048_576L

    // each stand-in costs another walk through every client, so only the closest few are tried
    private const val MAX_STAND_INS = 2

    // no client got as far as a stream, so another upload of the song is worth a try before failing
    private class UploadRefusedException(
        val failure: Exception,
        val videoDetails: PlayerResponse.VideoDetails?,
    ) : Exception(failure.message, failure)

    /** One line for the notification: the first client's reason, which is the main client's. */
    private fun failureSummary(failures: List<String>): String {
        val first = failures.firstOrNull() ?: return "YouTube didn't return a playable stream"
        val others = failures.size - 1
        return if (others > 0) {
            "YouTube wouldn't stream this song ($first; $others other clients failed too)"
        } else {
            "YouTube wouldn't stream this song ($first)"
        }
    }

    private fun Throwable.isRateLimit(): Boolean =
        this is ResponseException && response.status.value == 429

    /** Short: Ktor's own message carries the whole request url and response body. */
    private fun Throwable.shortReason(): String = when (this) {
        is ResponseException -> "HTTP ${response.status.value}"
        is java.net.SocketTimeoutException -> "timed out"
        is java.net.UnknownHostException, is java.net.ConnectException -> "no connection"
        else -> javaClass.simpleName
    }
}

/**
 * YouTube answered 429: it's refusing this network for a while. Nothing else is asked - every
 * client goes to the same host, and more requests only keep the limit in place.
 */
class YouTubeRateLimitedException :
    Exception("YouTube is limiting requests from this network. Try again in a few minutes.")
