// Pkce.kt

package com.mochyy.mochymusic.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** the two codes a browser sign-in with pkce needs, so no secret ever has to ship inside mochy */
object Pkce {
    /** a random url-safe code, for the verifier and the check value a login is matched against */
    fun randomCode(bytes: Int): String {
        val raw = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** the hash of the verifier, the only part that goes out in the login link */
    fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
