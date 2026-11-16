package com.mochyy.mochymusic.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFileNamesTest {
    @Test
    fun `mochy's export name splits into artist and title`() {
        assertEquals(LocalFileNames.Guess("MILI", "Not My Paradiso"), LocalFileNames.guess("MILI - Not My Paradiso.opus"))
    }

    @Test
    fun `underscores for spaces still split`() {
        assertEquals(LocalFileNames.Guess("MILI", "Not My Paradiso"), LocalFileNames.guess("MILI_-_Not_My_Paradiso.jpg"))
    }

    @Test
    fun `a ripped album's track number is dropped`() {
        assertEquals(LocalFileNames.Guess(null, "Intro"), LocalFileNames.guess("01 - Intro.flac"))
        assertEquals(LocalFileNames.Guess(null, "Intro"), LocalFileNames.guess("01 Intro.mp3"))
    }

    @Test
    fun `a title that starts with a number keeps it`() {
        assertEquals(LocalFileNames.Guess(null, "99 Luftballons"), LocalFileNames.guess("99 Luftballons.mp3"))
    }

    @Test
    fun `a dash inside the title stays with the title`() {
        assertEquals(LocalFileNames.Guess("Artist", "Song - Remastered"), LocalFileNames.guess("Artist - Song - Remastered.m4a"))
    }

    @Test
    fun `songs find their lyrics and pictures by name`() {
        assertEquals(LocalFileNames.matchKey("MILI - Not My Paradiso.opus"), LocalFileNames.matchKey("MILI_-_Not_My_Paradiso.jpg"))
        assertEquals(LocalFileNames.matchKey("Song.opus"), LocalFileNames.matchKey("song.LRC"))
        assertEquals(0, LocalFileNames.folderCoverRank("Cover.JPG"))
    }

    @Test
    fun `files are told apart by extension`() {
        assertEquals(LocalFileNames.Kind.Lyrics, LocalFileNames.kind("a.krc"))
        assertEquals(LocalFileNames.Kind.Lyrics, LocalFileNames.kind("a.txt", "text/plain"))
        assertEquals(LocalFileNames.Kind.Audio, LocalFileNames.kind("a.opus"))
        assertEquals(LocalFileNames.Kind.Image, LocalFileNames.kind("a.webp"))
        assertEquals(LocalFileNames.Kind.Other, LocalFileNames.kind("a.pdf", "application/pdf"))
    }
}
