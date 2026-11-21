// proves to the module feed that this is an official build: those carry a key that isn't in the public code, and sign every request with it

package com.mochyy.mochymusic.modules.browser

import com.mochyy.mochymusic.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object StoreSignature {
    // the build stores the key scrambled with this, so it isn't sitting in the app as plain text; that only slows a reader down, the feed's check is what counts
    private const val Mask = "mochy-module-store"

    private val key: ByteArray? by lazy {
        val hex = BuildConfig.MODULE_STORE_KEY
        if (hex.isEmpty() || hex.length % 2 != 0) return@lazy null
        hex.chunked(2).mapIndexed { index, pair -> (pair.toInt(16) xor Mask[index % Mask.length].code).toByte() }.toByteArray()
    }

    /** a fork builds without the key, so it never gets one */
    val isOfficial: Boolean get() = key != null

    /** the time and the signature over one request; the feed turns away a signature more than a few minutes old */
    fun sign(method: String, path: String, extra: String): Pair<String, String>? {
        val secret = key ?: return null
        val time = (System.currentTimeMillis() / 1000).toString()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        val digest = mac.doFinal("$time\n$method\n$path\n$extra".toByteArray())
        return time to digest.joinToString("") { "%02x".format(it) }
    }
}
