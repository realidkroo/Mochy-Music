// DiscordHttp.kt

package com.mochyy.mochymusic.discord

import com.mochyy.mochymusic.utils.await
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Mochy's calls to Discord's web API and gateway, sharing one connection pool. */
internal object DiscordHttp {
    const val Api = "https://discord.com/api/v10"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    class Reply(val code: Int, val body: String, val retryAfterSeconds: Double?) {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun get(path: String, token: String): Reply = send(
        Request.Builder().url(Api + path).header("Authorization", "Bearer $token").build(),
    )

    suspend fun postForm(path: String, fields: Map<String, String>): Reply {
        val body = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return send(Request.Builder().url(Api + path).post(body).build())
    }

    suspend fun postJson(path: String, token: String, json: String): Reply = send(
        Request.Builder()
            .url(Api + path)
            .header("Authorization", "Bearer $token")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build(),
    )

    /** Cancelling the coroutine cancels the call too, so leaving the page doesn't leave it hanging. */
    private suspend fun send(request: Request): Reply = client.newCall(request).await().use { response ->
        Reply(
            code = response.code,
            body = response.body?.string().orEmpty(),
            retryAfterSeconds = response.header("Retry-After")?.toDoubleOrNull(),
        )
    }
}
