package com.mochyy.mochymusic.songedit

import com.mochyy.mochymusic.lyrics.LyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class LyricsFileReaderTest {
    private fun parse(text: String) = LyricsFileReader.parse(text.toByteArray())

    @Test
    fun `netease yrc keeps every word's time`() {
        val lyrics = parse("{\"t\":0,\"c\":[{\"tx\":\"credits\"}]}\n[16210,3460](16210,670,0)Ever (16880,500,0)after")
        assertEquals("[00:16.21]<00:16.210>Ever <00:16.880>after", lyrics)
        val line = LyricsUtils.parseLyrics(lyrics!!).single()
        assertEquals("Ever after", line.text)
        assertEquals(2, line.words!!.size)
    }

    @Test
    fun `qq music qrc puts the time after each word`() {
        assertEquals("[00:01.00]<00:01.000>Hi <00:01.300>there", parse("[ti:Song]\n[1000,2000]Hi (1000,300)there(1300,400)"))
    }

    @Test
    fun `kugou krc is unlocked and timed from its line`() {
        val text = "[id:\$00000000]\n[1000,2000]<0,300,0>Hel<300,200,0>lo"
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val deflater = Deflater().apply {
            setInput(text.toByteArray())
            finish()
        }
        val zipped = ByteArrayOutputStream().apply {
            val buffer = ByteArray(1024)
            while (!deflater.finished()) write(buffer, 0, deflater.deflate(buffer))
        }.toByteArray()
        val krc = "krc1".toByteArray() + ByteArray(zipped.size) { (zipped[it].toInt() xor key[it % key.size]).toByte() }
        assertEquals("[00:01.00]<00:01.000>Hel<00:01.300>lo", LyricsFileReader.parse(krc))
    }

    @Test
    fun `ass karaoke syllables become word times`() {
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.18,0:00:03.00,Default,,0,0,0,,{\\k20}Hel{\\k30}lo\n" +
            "Dialogue: 0,0:00:04.00,0:00:05.00,Default,,0,0,0,,{\\i1}Plain\\Nline"
        assertEquals("[00:01.18]<00:01.180>Hel<00:01.380>lo\n[00:04.00]Plain line", parse(ass))
    }

    @Test
    fun `vtt without hours reads like srt`() {
        assertEquals("[00:01.50]Hello there", parse("WEBVTT\n\n00:01.500 --> 00:03.000\nHello there\n"))
    }

    @Test
    fun `loose lrc stamps and the offset tag are fixed`() {
        assertEquals("[00:12.00]Hello\n[00:15.50]World", parse("[0:12]Hello\n[00:15.5]World"))
        assertEquals("[00:09.50]Hi", parse("[offset:500]\n[00:10.00]Hi"))
    }

    @Test
    fun `well formed lrc stays exactly as it was`() {
        val lrc = "[00:01.179]{agent:v1}<00:01.179>Cassie <00:01.376>Wei\n[00:02.752]{agent:v1}<00:02.752>You <00:02.926>said"
        assertEquals(lrc, parse(lrc))
    }

    @Test
    fun `utf-16 text from windows is read`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "[00:01.00]Hi".toByteArray(Charsets.UTF_16LE)
        assertEquals("[00:01.00]Hi", LyricsFileReader.parse(bytes))
    }

    @Test
    fun `an empty file is not lyrics`() {
        assertNull(parse("   \n"))
    }
}
