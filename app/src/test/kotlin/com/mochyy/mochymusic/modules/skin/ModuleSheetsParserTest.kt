package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.web.WebModuleManifest
import com.mochyy.mochymusic.ui.player.info.SongAbout
import com.mochyy.mochymusic.ui.player.styles.SkinSongInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// the player's own pages, the module's sheets, scroll boxes, mochy's lyrics and queue as blocks, song info and popping words
class ModuleSheetsParserTest {

    private fun skin(text: String): ModuleSkin? {
        val m = requireNotNull(WebModuleManifest.parse(text))
        return SkinParser.parse(
            "local.test", "Test", m.player, m.font, m.pages, null, null,
            m.actions, m.glass, m.players, m.bar, m.mini,
            floating = m.floating, background = m.background, appBackground = m.appBackground,
            sheet = m.sheet, menu = m.menu, sheets = m.sheets,
        )
    }

    private val look = """
        {
          "id": "pages", "name": "Pages",
          "player": {
            "handle": false,
            "keyframes": { "pop": { "from": { "scale": 1.3 }, "to": {} } },
            "blocks": [
              { "type": "title" },
              { "type": "button", "openSheet": "Eq", "size": 40 },
              { "type": "scroll", "height": 5000, "direction": "row", "blocks": [ { "type": "text", "text": "a" }, { "type": "space", "grow": true } ] },
              { "type": "text", "text": "{about}", "when": "@about" }
            ],
            "menu": [ { "title": "Equalizer sheet", "openSheet": "eq" } ],
            "lyricsPage": {
              "background": { "type": "color", "color": "#000000" },
              "lyricsPage": { "blocks": [ { "type": "title" } ] },
              "blocks": [
                { "type": "lyrics", "style": "mochy", "header": true },
                { "type": "lyrics", "style": "custom", "wordEnter": { "keyframes": "pop", "duration": 280 } }
              ]
            },
            "queuePage": { "blocks": [ { "type": "queue", "style": "mochy", "height": 300 }, { "type": "scroll", "blocks": [ { "type": "queue" } ] } ] }
          },
          "actions": [ { "title": "Open my sheet", "openSheet": "eq" } ],
          "sheets": {
            "eq": { "look": "own", "height": 3, "corner": 99, "veil": 0.5, "handle": false, "blocks": [ { "type": "controls" } ] },
            "info": { "blocks": [ { "type": "text", "text": "{genres} · {year}" } ] },
            "bad id!": { "blocks": [ { "type": "title" } ] },
            "empty": { "blocks": [] }
          }
        }
    """.trimIndent()

    @Test
    fun `a player can hide its handle and bring its own lyrics and queue pages`() {
        val player = requireNotNull(skin(look)?.player)
        assertFalse(player.handle)
        val lyrics = requireNotNull(player.lyricsPage)
        // a page can't open pages of its own
        assertNull(lyrics.lyricsPage)
        assertEquals(SkinBlock.MochyLyrics(null, header = true), lyrics.blocks[0])
        // the page borrows the player's keyframes by name, so the word pop finds "pop"
        val custom = (lyrics.blocks[1] as SkinBlock.Lyrics).custom
        val pop = requireNotNull(custom?.wordEnter)
        assertEquals(280, pop.durationMs)
        assertTrue(pop.frames.tracks.any { it.prop == PoseProp.Scale })
        val queue = requireNotNull(player.queuePage)
        assertEquals(SkinBlock.MochyQueue(300.dp, header = false), queue.blocks[0])
        assertEquals(3, player.withPages.size)
    }

    @Test
    fun `a scroll box keeps its height in range and goes sideways when asked`() {
        val player = requireNotNull(skin(look)?.player)
        val scroll = player.blocks.filterIsInstance<SkinBlock.Scroll>().single()
        assertTrue(scroll.row)
        assertEquals(1200.dp, scroll.height)
        assertEquals(2, scroll.blocks.size)
        // one with no height takes the room left
        val inPage = requireNotNull(player.queuePage).blocks[1] as SkinBlock.Scroll
        assertNull(inPage.height)
        assertTrue(inPage.blocks.single() is SkinBlock.QueueList)
    }

    @Test
    fun `sheets read by id, keep their numbers in range and skip what can't be one`() {
        val sheets = requireNotNull(skin(look)).sheets
        assertEquals(listOf("eq", "info"), sheets.map { it.id })
        val eq = sheets.first()
        assertEquals(SheetLook.Own, eq.look)
        assertEquals(1f, eq.height)
        assertEquals(60.dp, eq.corner)
        assertEquals(0.5f, eq.dim)
        assertFalse(eq.handle)
        val info = sheets[1]
        assertEquals(SheetLook.Mochy, info.look)
        assertNull(info.height)
        assertTrue(info.layout.songInfo)
    }

    @Test
    fun `openSheet works from a button, a chevron item and a row in the other menu`() {
        val parsed = requireNotNull(skin(look))
        val player = requireNotNull(parsed.player)
        val button = player.blocks.filterIsInstance<SkinBlock.Button>().single()
        assertEquals(SkinTarget.Action(ActionTarget.ModuleSheet("eq")), button.target)
        assertEquals(SkinTarget.Action(ActionTarget.ModuleSheet("eq")), player.menu.single().target)
        assertEquals(ActionTarget.ModuleSheet("eq"), parsed.actions.single().target)
    }

    @Test
    fun `song info words and the about condition make the player look the song up`() {
        val player = requireNotNull(skin(look)?.player)
        assertTrue(player.songInfo)
        assertTrue(SkinFlag.About in player.flags)
        assertNotNull(SkinFlag.of("@about"))
    }

    @Test
    fun `a module with only sheets is still a skin`() {
        val only = skin("""{ "id": "s", "name": "S", "sheets": { "one": { "blocks": [ { "type": "title" } ] } } }""")
        assertEquals(listOf("one"), only?.sheets?.map { it.id })
    }

    @Test
    fun `song info fills its words from what the about sheet knows`() {
        val info = SkinSongInfo()
        assertEquals("? · ", info.fill("? · {about}"))
        info.about = SongAbout(youtubeDescription = "  A song about rain  ", libraryYear = 2014)
        assertTrue(info.hasAbout)
        assertEquals("A song about rain · 2014 · 2014", info.fill("{about} · {released} · {year}"))
        // words without a placeholder come back as they were
        assertEquals("plain", info.fill("plain"))
    }
}
