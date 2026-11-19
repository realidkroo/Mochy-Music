// OkHttpAwait.kt

package com.mochyy.mochymusic.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** runs the call without blocking a thread; cancelling the coroutine cancels the call too, so leaving a page doesn't leave it hanging */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) =
            continuation.resume(response) { _, unused, _ -> unused.close() }
    })
}
