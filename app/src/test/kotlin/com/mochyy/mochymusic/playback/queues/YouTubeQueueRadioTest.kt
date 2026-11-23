package com.mochyy.mochymusic.playback.queues

import com.music.innertube.models.WatchEndpoint
import com.mochyy.mochymusic.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeQueueRadioTest {

    private fun songMeta(id: String) = MediaMetadata(
        id = id,
        title = "Test Song",
        artists = emptyList(),
        duration = 200,
    )

    @Test
    fun `radio watch endpoint uses RDAMVM station prefix`() {
        val ep = YouTubeQueue.radioWatchEndpoint("dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", ep.videoId)
        assertEquals("RDAMVMdQw4w9WgXcQ", ep.playlistId)
        assertTrue(YouTubeQueue.isSongRadioEndpoint(ep))
    }

    @Test
    fun `forSong upgrades bare song endpoints to RDAMVM radio`() {
        val song = songMeta("abc12345678")
        val bareQueue = YouTubeQueue.forSong(WatchEndpoint(videoId = song.id), song)
        val nullQueue = YouTubeQueue.forSong(null, song)

        val field = YouTubeQueue::class.java.getDeclaredField("endpoint").apply { isAccessible = true }
        val bareEndpoint = field.get(bareQueue) as WatchEndpoint
        val nullEndpoint = field.get(nullQueue) as WatchEndpoint

        assertEquals("RDAMVMabc12345678", bareEndpoint.playlistId)
        assertEquals("RDAMVMabc12345678", nullEndpoint.playlistId)
        assertEquals(song, bareQueue.preloadItem)
    }

    @Test
    fun `forSong preserves album and playlist endpoints`() {
        val song = songMeta("abc12345678")
        val albumEndpoint = WatchEndpoint(videoId = song.id, playlistId = "OLAK5uy_example")
        val queue = YouTubeQueue.forSong(albumEndpoint, song)

        val field = YouTubeQueue::class.java.getDeclaredField("endpoint").apply { isAccessible = true }
        val resolved = field.get(queue) as WatchEndpoint

        assertEquals("OLAK5uy_example", resolved.playlistId)
        assertFalse(YouTubeQueue.isSongRadioEndpoint(resolved))
    }
}
