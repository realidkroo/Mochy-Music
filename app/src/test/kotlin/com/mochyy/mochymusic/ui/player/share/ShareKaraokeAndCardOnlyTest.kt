package com.mochyy.mochymusic.ui.player.share

import androidx.compose.ui.graphics.Color
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.WordTimestamp
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.player.karaokeHeadFor
import com.mochyy.mochymusic.ui.player.share.song.CardOnlyBackground
import com.mochyy.mochymusic.ui.player.share.song.Fetched
import com.mochyy.mochymusic.ui.player.share.song.ShareVideoSource
import com.mochyy.mochymusic.ui.player.share.song.SongBackground
import com.mochyy.mochymusic.ui.player.share.song.SongShareKind
import com.mochyy.mochymusic.ui.player.share.song.SongShareSession
import com.mochyy.mochymusic.ui.player.share.song.isTextureVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareKaraokeAndCardOnlyTest {

    @Test
    fun `buildShareTimedRanges matches syllables and cinematic ALL-CAPS case-insensitively`() {
        val entry = LyricsEntry(
            time = 10_000L,
            text = "Hello beautiful world",
            syllables = listOf(
                WordTimestamp("Hel", 10.0, 10.3),
                WordTimestamp("lo", 10.3, 10.6),
                WordTimestamp("beautiful", 10.7, 11.4),
                WordTimestamp("world", 11.5, 12.2),
            ),
        )
        val ranges = buildShareTimedRanges("HELLO beautiful WORLD", entry, nextLineMs = 13_000L)
        assertEquals(4, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(3, ranges[0].end)
        assertEquals(3, ranges[1].start)
        assertEquals(5, ranges[1].end)
        assertEquals(6, ranges[2].start)
        assertEquals(15, ranges[2].end)
        assertEquals(16, ranges[3].start)
        assertEquals(21, ranges[3].end)
        assertEquals(12.2, ranges[3].endTime, 0.001)
    }

    @Test
    fun `buildShareTimedRanges caps untimed line fallback before long instrumental break`() {
        val entry = LyricsEntry(time = 5_000L, text = "Stay with me")
        val ranges = buildShareTimedRanges("Stay with me", entry, nextLineMs = 35_000L)
        assertEquals(3, ranges.size)
        assertEquals(5.0, ranges.first().startTime, 0.001)
        assertTrue("Untimed line should not crawl across 30s break", ranges.last().endTime <= 9.0)
    }

    @Test
    fun `ShareKaraokeLine animates deterministically, settles shader off, and resets on loop`() {
        val text = "Hold this note"
        val entry = LyricsEntry(
            time = 1_000L,
            text = text,
            words = listOf(
                WordTimestamp("Hold", 1.0, 2.2),
                WordTimestamp("this", 2.2, 2.5),
                WordTimestamp("note", 2.5, 4.0),
            ),
        )
        val timed = buildShareTimedRanges(text, entry, nextLineMs = 4_500L)
        val line = ShareKaraokeLine(text, timed, karaokeHeadFor(text))

        // Before start: completely idle
        line.step(posSec = 0.5, dt = 0.016f, wordMotion = true, snap = true)
        assertEquals(0f, line.head.offset.floatValue, 0.001f)
        assertEquals(0f, line.waveAmp.floatValue, 0.001f)
        assertEquals(0f, line.activeZoom.floatValue, 0.001f)

        // Mid held-note: sweep advances, bloom glow active, waveAmp & activeZoom lit
        var t = 1.0
        while (t <= 1.65) {
            line.step(posSec = t, dt = 0.016f, wordMotion = true, snap = false)
            t += 0.016
        }
        assertTrue("Sweep offset should advance into 'Hold'", line.head.offset.floatValue > 1f)
        assertTrue("Held word should trigger glow", line.head.glow.floatValue > 0.1f)
        assertTrue("Wave amplitude should be active", line.waveAmp.floatValue > 0.5f)
        assertTrue("Active line zoom should engage", line.activeZoom.floatValue > 0.5f)

        // Well after line finishes: settles back to waveAmp = 0 so AGSL RenderEffect turns off
        t = 4.0
        while (t <= 6.0) {
            line.step(posSec = t, dt = 0.016f, wordMotion = true, snap = false)
            t += 0.016
        }
        assertEquals(text.length.toFloat(), line.head.offset.floatValue, 0.01f)
        assertEquals(0f, line.waveAmp.floatValue, 0.001f)
        assertEquals(0f, line.activeZoom.floatValue, 0.001f)

        // Loop back resets everything cleanly
        line.reset()
        assertEquals(0f, line.head.offset.floatValue, 0.001f)
        assertEquals(0f, line.head.glow.floatValue, 0.001f)
        assertEquals(0f, line.waveAmp.floatValue, 0.001f)
        assertEquals(0f, line.activeZoom.floatValue, 0.001f)
    }

    @Test
    fun `wordSpan propagates waiting ink alpha so unlit poster words stay dim`() {
        val waiting = waitingInkOf(Color.White)
        val span = wordSpan(WordLook(glow = true, big = true), waiting)
        assertEquals(waiting, span.color)
        assertTrue("Waiting ink should be dimmed below 0.5 alpha", span.color.alpha < 0.5f)
    }

    @Test
    fun `SongShareSession card only mode supports liquid cover, videos, ink contrast, and cinematic exclusion`() {
        val song = MediaMetadata(id = "test-id", title = "Song", artists = emptyList(), duration = 180)
        val session = SongShareSession(song)
        session.kind = SongShareKind.MusicOnly
        session.toggleCardOnly(true)

        assertTrue(session.isCardOnly)
        assertFalse(session.hasVideoBackground)
        assertFalse(session.isVideo)

        // Solid light card uses dark ink; blurred/video cover always uses crisp white ink
        session.cardOnlyBg = CardOnlyBackground.Colour
        session.cardColor = Color.White
        assertEquals(Color(0xFF111111), session.cardInk)

        session.cardOnlyBg = CardOnlyBackground.BlurredCover
        assertEquals(Color.White, session.cardInk)
        assertFalse(session.hasVideoBackground)

        // LiquidCover (Apple Music-style warping cover) is always a video and Compose-drawn (not texture video)
        session.cardOnlyBg = CardOnlyBackground.Video(ShareVideoSource.LiquidCover)
        assertTrue(session.hasVideoBackground)
        assertTrue(session.isVideo)
        assertEquals(ShareVideoSource.LiquidCover, session.activeVideoSource)
        assertFalse(ShareVideoSource.LiquidCover.isTextureVideo)
        assertEquals(Color.White, session.cardInk)

        // Animated cover only makes it a video when an animated cover was actually found
        session.cardOnlyBg = CardOnlyBackground.Video(ShareVideoSource.AnimatedCover)
        assertFalse(session.hasVideoBackground)
        session.animatedCover = Fetched.Found("https://example.com/canvas.mp4")
        assertTrue(session.hasVideoBackground)
        assertTrue(session.isVideo)
        assertTrue(ShareVideoSource.AnimatedCover.isTextureVideo)
        assertEquals(Color.White, session.cardInk)

        // Turning Card only on carries over an active full-canvas video background on first toggle
        val carrySession = SongShareSession(song)
        carrySession.setVideo(SongBackground.Video(ShareVideoSource.LiquidCover, dim = 0.7f))
        carrySession.toggleCardOnly(true)
        assertEquals(CardOnlyBackground.Video(ShareVideoSource.LiquidCover), carrySession.cardOnlyBg)
        assertEquals(0.7f, carrySession.cardOnlyDim, 0.001f)

        // Switching to Cinematic disables isCardOnly automatically
        val pick = LyricsShareSession(
            song = session.share,
            lines = listOf("First line"),
            entries = listOf(LyricsEntry(1_000L, "First line")),
            held = 0,
            flightSource = null,
        ).also { it.style = ShareStyle.Cinematic }
        session.kind = SongShareKind.Lyrics
        session.pick = pick
        assertFalse(session.hasCard)
        assertFalse(session.isCardOnly)
    }
}
