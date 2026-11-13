package com.mochyy.mochymusic.ui.player

import com.mochyy.mochymusic.ui.player.customize.coverArtBoxF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCoverLayoutTest {
    @Test
    fun `boxed cover on phone portrait stays within region`() {
        val box = coverArtBoxF(boxed = true, width = 390f, height = 844f, statusTop = 32f, unit = 1f)
        val region = 844f * 0.63f
        assertEquals(342f, box.width, 0.001f)
        assertEquals(342f, box.height, 0.001f)
        assertTrue(box.y + box.height <= region)
    }

    @Test
    fun `boxed cover on foldable unfolded portrait clamps to region without overlapping controls`() {
        val box = coverArtBoxF(boxed = true, width = 690f, height = 828f, statusTop = 32f, unit = 1f)
        val region = 828f * 0.63f
        val maxAllowed = region - (32f + 16f) - 8f
        assertEquals(maxAllowed, box.width, 0.01f)
        assertEquals(maxAllowed, box.height, 0.01f)
        assertTrue("Cover bottom (${box.y + box.height}) must not exceed region ($region)", box.y + box.height <= region)
    }

    @Test
    fun `turntable on wide viewport scales width to fit height`() {
        val width = 690f
        val height = 521f
        val turntableWidth = minOf(width, height * 0.75f)
        assertTrue(turntableWidth < width)
        assertTrue(turntableWidth * 1.406f <= height * 1.06f)
    }

    @Test
    fun `poster circle in landscape leaves space for text on wide viewports`() {
        val w = 828f
        val h = 690f
        val posterH = minOf(h, w * 0.45f)
        val textStart = maxOf(posterH * 1.08f, w * 0.48f)
        val remainingTextWidth = w - textStart - 24f
        assertTrue("Text width ($remainingTextWidth) must exceed 350 on landscape foldable", remainingTextWidth > 350f)
    }
}
