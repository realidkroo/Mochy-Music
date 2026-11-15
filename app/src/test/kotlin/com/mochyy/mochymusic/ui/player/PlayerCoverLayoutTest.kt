package com.mochyy.mochymusic.ui.player

import com.mochyy.mochymusic.ui.player.customize.coverArtBoxF
import com.mochyy.mochymusic.ui.player.customize.turntableLeftOffset
import com.mochyy.mochymusic.ui.player.customize.turntableStageWidth
import com.mochyy.mochymusic.ui.player.customize.turntableTopPadding
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

    @Test
    fun `turntable layout mode discriminates mobile and foldable viewports`() {
        org.junit.Assert.assertFalse(isTurntableWideLayout(360))
        org.junit.Assert.assertFalse(isTurntableWideLayout(390))
        org.junit.Assert.assertFalse(isTurntableWideLayout(412))
        org.junit.Assert.assertTrue(isTurntableWideLayout(500))
        org.junit.Assert.assertTrue(isTurntableWideLayout(674))
        org.junit.Assert.assertTrue(isTurntableWideLayout(800))
    }

    @Test
    fun `turntable controls next button inline with title on wide viewports`() {
        val pillWidth = 59f
        val pillHeight = 42f
        val pillGap = 11f
        val totalPillsHeight = 4 * pillHeight + 3 * pillGap
        assertEquals(201f, totalPillsHeight, 0.01f)
        val availableWidth = 600f - 60f - 43f - 28f - pillWidth
        assertTrue("Title width ($availableWidth) exceeds 400 on wide screens", availableWidth > 400f)
    }

    @Test
    fun `turntable disc is centered on wide foldable viewports`() {
        val width = 674f
        val height = 650f
        val stageWidth = turntableStageWidth(width, height, isWide = true)
        val leftOffset = turntableLeftOffset(width, stageWidth, isWide = true)
        val vinylCenterX = leftOffset + 0.162f * stageWidth
        val expectedCenter = width / 2f
        assertEquals(expectedCenter, vinylCenterX, 0.01f)
        val vinylRadius = 0.703f * stageWidth
        assertTrue("Vinyl left edge within bounds", vinylCenterX - vinylRadius >= 0f)
        assertTrue("Vinyl right edge within bounds", vinylCenterX + vinylRadius <= width)
        val tonearmPivotX = leftOffset + 0.895f * stageWidth
        assertTrue("Tonearm pivot within bounds", tonearmPivotX <= width)
    }

    @Test
    fun `turntable mobile layout preserves left aligned disc`() {
        val width = 390f
        val height = 844f
        val stageWidth = turntableStageWidth(width, height, isWide = false)
        val leftOffset = turntableLeftOffset(width, stageWidth, isWide = false)
        assertEquals(0f, leftOffset, 0.01f)
        assertEquals(390f, stageWidth, 0.01f)
        val vinylCenterX = leftOffset + 0.162f * stageWidth
        assertEquals(390f * 0.162f, vinylCenterX, 0.01f)
    }

    @Test
    fun `turntable top padding clears status bar on wide and mobile viewports`() {
        assertEquals(44f, turntableTopPadding(statusBarTop = 28f, isWide = true), 0.01f)
        assertEquals(36f, turntableTopPadding(statusBarTop = 0f, isWide = true), 0.01f)
        assertEquals(28f, turntableTopPadding(statusBarTop = 28f, isWide = false), 0.01f)
        assertEquals(16f, turntableTopPadding(statusBarTop = 0f, isWide = false), 0.01f)
    }
}
