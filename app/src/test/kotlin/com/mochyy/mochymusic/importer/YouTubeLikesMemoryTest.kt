package com.mochyy.mochymusic.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLikesMemoryTest {
    private val library = (1..300).map { "song$it" }

    private fun dropped(
        likedHere: List<String> = library,
        seen: Set<String>? = library.toSet(),
        remote: Set<String>,
        wholeList: Boolean = true,
        keep: Set<String> = emptySet(),
    ) = YouTubeLikesMemory.droppedOnYouTube(likedHere, seen, remote, wholeList, keep)

    @Test
    fun aShortListTakesNothingOff() {
        assertNull(dropped(remote = library.take(100).toSet(), wholeList = false))
    }

    @Test
    fun aFewUnlikesOnYouTubeComeOff() {
        assertEquals(setOf("song1", "song2"), dropped(remote = library.drop(2).toSet()))
    }

    @Test
    fun aMassDropIsTreatedAsAHiccup() {
        // the whole list "came back", but with only the newest 100 of 300 songs
        assertNull(dropped(remote = library.take(100).toSet()))
    }

    @Test
    fun anEmptyListNeverUnlikes() {
        assertNull(dropped(likedHere = listOf("a", "b"), seen = setOf("a", "b"), remote = emptySet()))
    }

    @Test
    fun likesYouTubeNeverListedStay() {
        // imports and local files were liked here and never showed up on youtube
        val here = library + listOf("imported1", "imported2", "localFile")
        assertEquals(emptySet<String>(), dropped(likedHere = here, remote = library.toSet()))
    }

    @Test
    fun theFirstSyncAfterUpdatingTakesNothingOff() {
        assertEquals(emptySet<String>(), dropped(seen = null, remote = library.take(10).toSet()))
    }

    @Test
    fun likesJustPushedUpStay() {
        assertEquals(setOf("song2"), dropped(remote = library.drop(2).toSet(), keep = setOf("song1")))
    }
}
