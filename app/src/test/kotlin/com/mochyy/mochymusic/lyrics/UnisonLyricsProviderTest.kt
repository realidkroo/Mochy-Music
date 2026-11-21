package com.mochyy.mochymusic.lyrics

import com.music.directlyrics.models.UnisonLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnisonLyricsProviderTest {

    @Test
    fun `credit tag round-trips song and submitter links and stays invisible to every lyrics parser`() {
        val sample = UnisonLyrics(
            lyrics = "[00:01.00]こんにちは世界\n[00:04.00]また明日",
            format = "lrc",
            videoId = "Ugr4KKK_SqY",
            submitterKeyId = "abc123",
            submitterName = "boidu",
        )
        val stored = UnisonLyricsProvider.attachCredit(UnisonLyricsProvider.formatBody(sample), sample)

        val credit = UnisonLyricsProvider.parseCredit(stored)!!
        assertEquals("https://unison.betterlyrics.org/song/Ugr4KKK_SqY", credit.songUrl)
        assertEquals("boidu", credit.submitterName)
        assertEquals("https://unison.betterlyrics.org/curator/abc123", credit.submitterUrl)

        val parsed = LyricsUtils.parseLyrics(stored)
        assertEquals(listOf("こんにちは世界", "また明日"), parsed.map { it.text })
        assertEquals(LyricsUtils.SyncKind.LINE, LyricsUtils.syncKind(stored))
        // the ascii credit tag must never pull a japanese song toward latin script
        assertEquals(LyricsUtils.Script.KANA, LyricsUtils.lyricsScript(stored))
    }

    @Test
    fun `plain unison lyrics keep their credit tag out of displayed lines`() {
        val sample = UnisonLyrics(
            lyrics = "First line\nSecond line",
            format = "plain",
            videoId = "Ugr4KKK_SqY",
            submitterKeyId = "k1",
            submitterName = "a|b]c",
        )
        val stored = UnisonLyricsProvider.attachCredit(UnisonLyricsProvider.formatBody(sample), sample)

        assertEquals(listOf("First line", "Second line"), LyricsUtils.parsePlainLyrics(stored).map { it.text })
        assertEquals(LyricsUtils.SyncKind.PLAIN, LyricsUtils.syncKind(stored))
        assertEquals("a b c", UnisonLyricsProvider.parseCredit(stored)?.submitterName)
        assertNull(UnisonLyricsProvider.parseCredit("[00:01.00]No credit here"))
    }
}
