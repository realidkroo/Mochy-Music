package com.mochyy.mochymusic.playback.custom

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import android.util.Base64
import androidx.media3.common.MimeTypes
import java.io.IOException

@UnstableApi
class DynamicResolvingMediaSource(
    private val originalMediaItem: MediaItem,
    private val mediaSourceFactoryProvider: (androidx.media3.exoplayer.drm.DrmSessionManager?) -> MediaSource.Factory,
    private val fetcherAction: suspend (String) -> CustomStreamResult?,
    private val onSourceResolved: (mediaId: String, source: String) -> Unit = { _, _ -> }
) : CompositeMediaSource<Void>() {

    private var innerMediaSource: MediaSource? = null
    private var timeline: Timeline? = null
    // written on dispatchers.io and read on exoplayer's playback thread in maybeThrowSourceInfoRefreshError
    @Volatile private var fetchError: IOException? = null
    private var fetchScope = CoroutineScope(Dispatchers.IO + Job())

    // The resolved stream's source is built and prepared on the playback thread this source was
    // prepared on, as Media3 requires. It used to be done on main, which mostly got away with it,
    // but a DRM session manager binds itself to the thread it's set up on, and one bound to main
    // never decrypted anything ("DefaultDrmSessionManager accessed on the wrong thread").
    @Volatile private var playbackHandler: Handler? = null
    /** bumped on every prepare and release, so a resolution that finishes late is dropped */
    private var generation = 0

    override fun prepareSourceInternal(mediaTransferListener: androidx.media3.datasource.TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        playbackHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
        val gen = ++generation

        // A source is released and prepared again whenever the player stops and restarts (a
        // quality change does exactly that). Release cancels the fetch scope, and launching on a
        // cancelled scope silently does nothing, which left the song buffering forever.
        fetchScope.cancel()
        fetchScope = CoroutineScope(Dispatchers.IO + Job())
        fetchError = null

        fetchScope.launch {
            val mediaId = originalMediaItem.mediaId
            val result = try {
                fetcherAction(mediaId)
            } catch (e: androidx.media3.common.PlaybackException) {

                fetchError = java.io.IOException(e)
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch stream for mediaId: $mediaId")
                fetchError = if (e is java.io.IOException) e else java.io.IOException(e)
                null
            }

            // a failed lookup plays from nowhere: announcing YouTube for it would be wrong
            if (fetchError == null) onSourceResolved(mediaId, result?.source ?: "YouTube Music")

            if (result == null) {
                if (fetchError == null) {

                    prepareChild(gen, mediaSourceFactoryProvider(null), originalMediaItem)
                }

                return@launch
            }

            val builder = originalMediaItem.buildUpon().setUri(Uri.parse(result.streamUrl))
            result.cacheKey?.let(builder::setCustomCacheKey)

            val cleanUrl = result.streamUrl.substringBefore('?')
            if (result.isDash || cleanUrl.endsWith(".mpd", ignoreCase = true)) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            if (result.isHls || cleanUrl.endsWith(".m3u8", ignoreCase = true)) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }

            val widevine = result.widevineLicense
            if (widevine != null) {
                // a source module's Widevine stream: the device's CDM decrypts, the module licenses
                val drmManager = WidevineDrm.sessionManager(widevine)
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setMultiSession(true).build()
                )
                prepareChild(gen, mediaSourceFactoryProvider(drmManager), builder.build())
            } else if (!result.decryptionKey.isNullOrBlank()) {

                val drmCallback = object : MediaDrmCallback {
                    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
                        return ByteArray(0)
                    }

                    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
                        try {
                            val requestStr = String(request.data, Charsets.UTF_8)
                            val requestJson = JSONObject(requestStr)
                            val kids = requestJson.getJSONArray("kids")
                            val kid = kids.getString(0)

                            val keyBytes = hexStringToByteArray(result.decryptionKey)
                            val kBase64Url = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                            val responseJson = JSONObject().apply {
                                put("keys", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("kty", "oct")
                                        put("k", kBase64Url)
                                        put("kid", kid)
                                    })
                                })
                                put("type", "temporary")
                            }
                            return responseJson.toString().toByteArray(Charsets.UTF_8)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to generate ClearKey response")
                            throw RuntimeException(e)
                        }
                    }
                }

                val drmManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)

                val localFactory = mediaSourceFactoryProvider(drmManager)

                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build()
                )

                prepareChild(gen, localFactory, builder.build())
            } else {
                prepareChild(gen, mediaSourceFactoryProvider(null), builder.build())
            }
        }
    }

    private fun prepareChild(gen: Int, factory: MediaSource.Factory, item: MediaItem) {
        playbackHandler?.post {
            // released, or prepared again, while the stream was being looked up
            if (gen != generation) return@post
            val source = factory.createMediaSource(item)
            innerMediaSource = source
            prepareChildSource(null, source)
        }
    }

    override fun getMediaItem(): MediaItem = originalMediaItem

    override fun maybeThrowSourceInfoRefreshError() {
        super.maybeThrowSourceInfoRefreshError()
        fetchError?.let { throw it }
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        if (innerMediaSource == null) {
            throw IllegalStateException("createPeriod called before innerMediaSource was prepared")
        }
        return innerMediaSource!!.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        innerMediaSource?.releasePeriod(mediaPeriod)
    }

    override fun onChildSourceInfoRefreshed(id: Void?, mediaSource: MediaSource, timeline: Timeline) {
        this.timeline = timeline
        refreshSourceInfo(timeline)
    }

    override fun releaseSourceInternal() {
        super.releaseSourceInternal()
        generation++
        playbackHandler = null
        fetchScope.cancel()
        innerMediaSource = null
        timeline = null
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
