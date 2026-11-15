package com.mochyy.mochymusic.utils

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException

// how long a failed cover waits before each new try
private val RetryWaitsMs = longArrayOf(1_000L, 3_000L)

// a cover from the web that fails on a dropped connection, a timeout or a busy server is asked for again, instead of leaving its tile blank until the row scrolls away
object ImageRetry : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        var result = chain.proceed()
        if (!isFromWeb(chain.request.data)) return result
        var tries = 0
        for (wait in RetryWaitsMs) {
            val error = result as? ErrorResult ?: return result
            if (!worthAnotherTry(error.throwable)) return result
            delay(wait)
            tries++
            result = chain.proceed()
        }
        // still failing after every try: says why in logcat (tag Covers), so a blank tile can be traced
        (result as? ErrorResult)?.let { Timber.tag("Covers").w(it.throwable, "Cover still failing after $tries tries") }
        return result
    }

    private fun isFromWeb(data: Any): Boolean {
        val text = data.toString()
        return text.startsWith("https://") || text.startsWith("http://")
    }

    // network trouble, coil thinking the phone was offline (a 504 from the cache), or the server asking to wait; a missing or broken picture would only fail the same way again
    private fun worthAnotherTry(error: Throwable): Boolean = when (error) {
        is IOException -> true
        is HttpException -> error.response.code.let { it == 408 || it == 429 || it >= 500 }
        else -> false
    }
}
