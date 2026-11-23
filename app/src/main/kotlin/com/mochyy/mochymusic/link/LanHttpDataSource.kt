// LanHttpDataSource.kt

package com.mochyy.mochymusic.link

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.mochyy.mochymusic.utils.LanHttpClient
import java.io.IOException
import java.net.InetAddress

/**
 * Media3 DataSource wrapper that routes strictly private-LAN `/file/` phone-link streams through
 * [LanHttpClient] while delegating every other URL to [fallbackFactory] (typically OkHttp).
 *
 * This lets the app enforce `cleartextTrafficPermitted="false"` globally in Network Security Config
 * without breaking TV↔Phone local Wi-Fi song streaming or downloads.
 */
@UnstableApi
class LanHttpDataSource private constructor(
    private val fallbackFactory: DataSource.Factory,
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val fallbackFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = LanHttpDataSource(fallbackFactory)
    }

    private var delegate: DataSource? = null
    private var lanResponse: LanHttpClient.Response? = null
    private var currentUri: Uri? = null
    private var openedLan = false
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (!isLanPhoneFileUrl(uri)) {
            val upstream = fallbackFactory.createDataSource()
            delegate = upstream
            return upstream.open(dataSpec)
        }

        currentUri = uri
        transferInitializing(dataSpec)

        val headers = LinkedHashMap<String, String>(dataSpec.httpRequestHeaders)
        if (dataSpec.position > 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val range = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                "bytes=${dataSpec.position}-${dataSpec.position + dataSpec.length - 1}"
            } else {
                "bytes=${dataSpec.position}-"
            }
            headers["Range"] = range
        }

        val response = LanHttpClient.execute(
            method = "GET",
            url = uri.toString(),
            headers = headers,
            connectTimeoutMs = 5_000,
            readTimeoutMs = 30_000,
        )
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("LAN phone stream returned HTTP $code")
        }

        lanResponse = response
        openedLan = true
        val contentLen = response.body.contentLength
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            contentLen >= 0L -> contentLen
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegate?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = lanResponse?.body?.byteStream() ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }
        val read = stream.read(buffer, offset, toRead)
        if (read < 0) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = delegate?.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> {
        delegate?.let { return it.responseHeaders }
        val resp = lanResponse ?: return emptyMap()
        return resp.headers.mapValues { listOf(it.value) }
    }

    override fun close() {
        delegate?.let {
            delegate = null
            it.close()
            return
        }
        currentUri = null
        lanResponse?.close()
        lanResponse = null
        if (openedLan) {
            openedLan = false
            transferEnded()
        }
    }

    companion object {
        internal fun isLanPhoneFileUrl(uri: Uri): Boolean {
            if (!uri.scheme.equals("http", ignoreCase = true)) return false
            val path = uri.path ?: return false
            if (!path.startsWith("/file/")) return false
            val host = uri.host?.trim()?.removePrefix("[")?.removeSuffix("]")?.substringBefore('%')
                ?.takeIf { it.isNotEmpty() } ?: return false
            return runCatching {
                val addrs = InetAddress.getAllByName(host)
                addrs.isNotEmpty() && addrs.all { LanHttpClient.isPrivateLanAddress(it) }
            }.getOrDefault(false)
        }
    }
}
