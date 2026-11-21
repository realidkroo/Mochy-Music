// proves to the module feed that this is an official build: native code verifies the package and signing certificate and signs every request in native memory

package com.mochyy.mochymusic.modules.browser

import android.content.Context

internal object StoreSignature {
    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("mochypass") }.isSuccess }

    /** a fork without the official key, package id, or release signature never passes */
    fun isOfficial(context: Context): Boolean =
        loaded && runCatching { nativeIsOfficial(context.applicationContext) }.getOrDefault(false)

    /** the time and the signature over one request; the feed turns away a signature more than a few minutes old */
    fun sign(context: Context, method: String, path: String, extra: String): Pair<String, String>? {
        if (!loaded) return null
        val time = (System.currentTimeMillis() / 1000).toString()
        val digest = runCatching { nativeSign(context.applicationContext, "$time\n$method\n$path\n$extra") }.getOrNull() ?: return null
        return time to digest
    }

    private external fun nativeIsOfficial(context: Context): Boolean
    private external fun nativeSign(context: Context, message: String): String?
}
