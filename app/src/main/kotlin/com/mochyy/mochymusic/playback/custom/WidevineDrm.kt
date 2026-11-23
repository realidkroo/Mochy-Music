// WidevineDrm.kt

package com.mochyy.mochymusic.playback.custom

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.mochyy.mochymusic.module.LicenseServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Widevine for streams from source modules. The device's own CDM does the decrypting, inside the
 * platform; Mochy only carries the CDM's challenge to the module, which trades it with its
 * service's license server. Multi-session, because Apple's streams change key after the first
 * segment.
 */
@UnstableApi
object WidevineDrm {

    private val client by lazy {
        OkHttpClient.Builder()
            .dispatcher(com.mochyy.mochymusic.utils.AppProxy.dispatcher)
            .connectionPool(com.mochyy.mochymusic.utils.AppProxy.connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun sessionManager(license: LicenseServer): DrmSessionManager =
        DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(callback(license))

    fun callback(license: LicenseServer) = object : MediaDrmCallback {
        override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray =
            provision(request.defaultUrl, request.data)

        override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray =
            runCatching { license.acquire(request.data) }
                .onFailure { android.util.Log.w("WidevineDrm", "License request failed", it) }
                .getOrThrow()
    }

    /** a device that has never played Widevine gets its certificate from Google first */
    fun provision(defaultUrl: String, data: ByteArray): ByteArray {
        val url = defaultUrl + "&signedRequest=" + String(data, Charsets.UTF_8)
        val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Widevine provisioning answered ${response.code}")
            return response.body?.bytes() ?: ByteArray(0)
        }
    }
}
