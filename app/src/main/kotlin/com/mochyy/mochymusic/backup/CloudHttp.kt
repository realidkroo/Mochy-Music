// CloudHttp.kt

package com.mochyy.mochymusic.backup

import com.mochyy.mochymusic.utils.await
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** the one connection pool every backup cloud shares, with long timeouts since a backup can be big and the line slow */
internal object CloudHttp {
    val Binary: MediaType = "application/octet-stream".toMediaType()
    val Json: MediaType = "application/json".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(com.mochyy.mochymusic.utils.AppProxy.dispatcher)
            .connectionPool(com.mochyy.mochymusic.utils.AppProxy.connectionPool)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    class Reply(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    /** the caller closes the response, so a download can stream straight into a file */
    suspend fun send(request: Request): Response = client.newCall(request).await()

    suspend fun text(request: Request): Reply = send(request).use { Reply(it.code, it.body.string()) }

    /** the codes every cloud shares; anything else means the cloud itself is having a bad moment */
    fun problemOf(code: Int): CloudProblem = when (code) {
        401, 403 -> CloudProblem.SignedOut
        404, 409 -> CloudProblem.NotFound
        507 -> CloudProblem.Full
        else -> CloudProblem.Busy
    }

    fun fail(code: Int): Nothing = throw CloudException(problemOf(code))
}

/** a piece of a file as an upload body, saying how far into the whole file it has got as it goes */
internal class FileSliceBody(
    private val file: File,
    private val offset: Long,
    private val length: Long,
    private val onSent: (Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = CloudHttp.Binary

    override fun contentLength(): Long = length

    // okhttp may write the body twice when it retries, so the position is reported whole each time instead of added up
    override fun writeTo(sink: BufferedSink) {
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (sent < length) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), length - sent).toInt())
                if (read < 0) break
                sink.write(buffer, 0, read)
                sent += read
                onSent(offset + sent)
            }
        }
    }
}

/** streams a download into a file, with expected as the size when the server doesn't say */
internal fun Response.saveTo(into: File, expected: Long, onProgress: (Float) -> Unit) {
    val total = body.contentLength().takeIf { it > 0 } ?: expected
    var copied = 0L
    val buffer = ByteArray(128 * 1024)
    body.byteStream().use { input ->
        into.outputStream().buffered(256 * 1024).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                if (total > 0) onProgress((copied.toDouble() / total).toFloat().coerceIn(0f, 1f))
            }
        }
    }
}
