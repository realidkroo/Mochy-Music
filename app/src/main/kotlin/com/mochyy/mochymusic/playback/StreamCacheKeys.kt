package com.mochyy.mochymusic.playback

/**
 * A DASH stream's init and media segments don't carry the song's cache key: Media3 keys each
 * segment request by the segment's own resolved URL (DashUtil.resolveCacheKey). Such a key is
 * an already-resolved address, not a song id, and must never be looked up on YouTube.
 *
 * YouTube song ids, `module|` keys and linked-phone keys never start with a URL scheme.
 */
internal fun isResolvedUrlCacheKey(key: String): Boolean =
    key.startsWith("https://", ignoreCase = true) ||
        key.startsWith("http://", ignoreCase = true) ||
        key.startsWith("data:", ignoreCase = true)
