package com.mochyy.mochymusic.ui.player.video

import com.mochyy.mochymusic.ui.player.videoCropTransform
import com.mochyy.mochymusic.utils.VideoStreams
import com.mochyy.mochymusic.utils.VideoTrack
import com.mochyy.mochymusic.utils.pickVideoFamily
import com.music.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDashTest {
    private fun format(itag: Int, mime: String, width: Int?, height: Int?, fps: Int = 30, bitrate: Int = 1_000_000) =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = "https://example.googlevideo.com/videoplayback?itag=$itag&a=1",
            mimeType = mime,
            bitrate = bitrate,
            width = width,
            height = height,
            contentLength = 1_000_000L,
            quality = "",
            fps = fps,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = "200000",
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null,
            initRange = PlayerResponse.StreamingData.Format.ByteRange("0", "740"),
            indexRange = PlayerResponse.StreamingData.Format.ByteRange("741", "1500"),
        )

    private fun track(itag: Int, height: Int, chunked: Boolean = true) = VideoTrack(
        itag = itag,
        url = "https://example.googlevideo.com/videoplayback?itag=$itag&n=x",
        mimeType = "video/mp4",
        codecs = "avc1.4d401f",
        width = height * 16 / 9,
        height = height,
        fps = 30,
        bitrate = height * 2_000,
        initRange = if (chunked) 0L..740L else null,
        indexRange = if (chunked) 741L..1500L else null,
        contentLength = 1_000_000L,
    )

    private fun streams(vararg tracks: VideoTrack, durationMs: Long = 200_000L) =
        VideoStreams("abc", tracks.toList(), emptyMap(), Long.MAX_VALUE, durationMs)

    private fun manifest(s: VideoStreams) = VideoDash.manifest(s.tracks, s.durationMs)

    @Test
    fun `h264 wins over vp9 and audio, one per size, the steadier frame rate first`() {
        val formats = listOf(
            format(251, "audio/webm; codecs=\"opus\"", null, null),
            format(248, "video/webm; codecs=\"vp9\"", 1920, 1080),
            format(137, "video/mp4; codecs=\"avc1.640028\"", 1920, 1080),
            format(299, "video/mp4; codecs=\"avc1.64002a\"", 1920, 1080, fps = 60),
            format(136, "video/mp4; codecs=\"avc1.4d401f\"", 1280, 720),
        )
        val picked = pickVideoFamily(formats)
        assertEquals(listOf(136, 137), picked.map { it.itag })
    }

    @Test
    fun `vp9 stands in when a video has no h264`() {
        val formats = listOf(
            format(247, "video/webm; codecs=\"vp9\"", 1280, 720),
            format(244, "video/webm; codecs=\"vp9\"", 854, 480),
        )
        assertEquals(listOf(244, 247), pickVideoFamily(formats).map { it.itag })
    }

    @Test
    fun `the manifest lists every size with its byte ranges and escapes the links`() {
        val xml = manifest(streams(track(134, 360), track(136, 720)))!!
        assertTrue(xml.contains("<Representation id=\"134\""))
        assertTrue(xml.contains("<Representation id=\"136\""))
        assertTrue(xml.contains("<SegmentBase indexRange=\"741-1500\"><Initialization range=\"0-740\"/>"))
        assertTrue(xml.contains("itag=136&amp;n=x"))
        assertTrue(xml.contains("mediaPresentationDuration=\"PT200.000S\""))
    }

    @Test
    fun `a video youtube only serves whole gets no manifest`() {
        assertNull(manifest(streams(track(18, 360, chunked = false))))
        assertNull(manifest(streams(track(136, 720), durationMs = 0L)))
    }

    @Test
    fun `a picked size falls to the nearest one under it, or the smallest`() {
        val s = streams(track(134, 360), track(136, 720), track(137, 1080))
        assertEquals(720, s.trackFor(800)?.shortSide)
        assertEquals(360, s.trackFor(144)?.shortSide)
        assertEquals(1080, s.trackFor(1080)?.shortSide)
    }

    @Test
    fun `filling a wide view never cuts more than it may`() {
        // a 4:3 video in a 20:9 view would lose 40% filled; capped at 22% it shrinks and leaves room at the sides
        val t = videoCropTransform(2400f, 1080f, 4f / 3f, 0f, 0f, maxLoss = 0.22f)
        assertEquals(0.78f, 1f / t.scaleY, 0.01f)
        assertTrue(t.scaleX < 1f)
        // a 16:9 video loses 20%, under the cap, so it still fills the whole view
        val wide = videoCropTransform(2400f, 1080f, 16f / 9f, 0f, 0f, maxLoss = 0.22f)
        assertEquals(1f, wide.scaleX, 0.001f)
    }

    @Test
    fun `edge colours come from each side of the picture`() {
        val size = 144
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            if (x < size / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        val ambient = edgeColors(pixels, size, 0f, 0f)
        assertEquals(1f, ambient.left.red, 0.01f)
        assertEquals(1f, ambient.right.blue, 0.01f)
    }
}
