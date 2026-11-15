package com.music.directlyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val LyricsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

// one client for netease, qq music, deezer and lrc.red, some label their json as plain text or html so those are read as json too
internal val lyricsHttp by lazy {
    HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
        // says who's asking so the server owner can reach us instead of blocking us
        install(UserAgent) { agent = "Mochy (+https://github.com/realidkroo/Mochy-Music)" }
        install(ContentNegotiation) {
            json(LyricsJson)
            json(LyricsJson, ContentType.Text.Plain)
            json(LyricsJson, ContentType.Text.Html)
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
    }
}
