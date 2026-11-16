package com.mochyy.mochymusic.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCacheKeysTest {

    @Test
    fun dashSegmentUrlKeysAreResolved() {
        // what Media3 puts in DataSpec.key for a DASH segment: the segment URL itself
        assertTrue(isResolvedUrlCacheKey("https://sp-pr-fa.audio.tidal.com/mediatracks/abc/0.mp4?token=x"))
        assertTrue(isResolvedUrlCacheKey("http://example.com/seg/1.m4s"))
        assertTrue(isResolvedUrlCacheKey("HTTPS://example.com/init.mp4"))
        assertTrue(isResolvedUrlCacheKey("data:application/dash+xml;base64,AAAA"))
    }

    @Test
    fun songIdsStillGoToTheResolver() {
        // a YouTube video id keeps going through YouTube resolution
        assertFalse(isResolvedUrlCacheKey("dQw4w9WgXcQ"))
        assertFalse(isResolvedUrlCacheKey("-abc_DEF123"))
        // module and linked-phone keys have their own branches
        assertFalse(isResolvedUrlCacheKey("module|some.source|dQw4w9WgXcQ"))
        assertFalse(isResolvedUrlCacheKey(""))
    }
}
