package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.modules.web.WebModuleManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkinParserTest {

    private fun manifest(text: String) = requireNotNull(WebModuleManifest.parse(text))

    private fun skin(text: String, files: File? = null, base: String? = null): ModuleSkin? {
        val m = manifest(text)
        return SkinParser.parse("local.test", "Test", m.player, m.font, m.pages, files, base)
    }

    private val vinyl = """
        {
          "id": "vinyl", "name": "Vinyl Night",
          "look": { "accentColor": "#E8B86B" },
          "font": { "regular": "fonts/Lora.ttf" },
          "player": {
            "name": "Vinyl",
            "background": { "type": "image", "image": "images/wood.webp", "dim": 2 },
            "padding": 500,
            "blocks": [
              { "type": "space", "grow": true },
              { "type": "cover", "shape": "circle", "size": "3", "spin": "true" },
              { "type": "title", "size": 26, "weight": "black", "color": "#80FFFFFF" },
              { "type": "sparkles" },
              { "type": "row", "blocks": [ { "type": "artist" }, { "type": "controls", "style": "plain", "size": 10 } ] },
              { "type": "progress", "style": "pill" }
            ]
          },
          "pages": [
            { "id": "About", "title": "About", "blocks": [
              { "type": "heading", "text": "Hi" },
              { "type": "button", "text": "Open", "link": "intent://evil" },
              { "type": "button", "text": "Discord", "link": "https://discord.gg/x" },
              { "type": "playlist", "id": "PL1", "title": "Picks" }
            ]},
            { "id": "bad id!", "blocks": [ { "type": "heading", "text": "x" } ] },
            { "id": "empty", "blocks": [] }
          ]
        }
    """

    @Test
    fun `a player layout reads with every value kept in range and unknown blocks skipped`() {
        val player = skin(vinyl)!!.player!!
        assertEquals("Vinyl", player.name)
        assertEquals(SkinBackground.Picture("images/wood.webp"), player.background)
        assertEquals(0.9f, player.dim)
        assertEquals(64.dp, player.padding)
        // the unknown "sparkles" block is gone, the rest keep their order
        assertEquals(5, player.blocks.size)
        val cover = player.blocks[1] as SkinBlock.Cover
        assertEquals(CoverShape.Circle, cover.shape)
        assertEquals(1f, cover.size)
        assertTrue(cover.spin)
        val title = player.blocks[2] as SkinBlock.Title
        assertEquals(SkinColor.Fixed(Color(0x80FFFFFF)), title.text.color)
        val controls = (player.blocks[3] as SkinBlock.Row).blocks[1] as SkinBlock.Controls
        assertTrue(controls.plain)
        assertEquals(28.dp, controls.size)
        assertEquals(ProgressStyle.Pill, (player.blocks[4] as SkinBlock.Progress).style)
    }

    @Test
    fun `a font needs the zip's files, a link module can't bring one`() {
        assertNull(skin(vinyl)!!.font)
        assertEquals("fonts/Lora.ttf", skin(vinyl, files = File("/tmp/x"))!!.font!!.regular)
    }

    @Test
    fun `pages keep good ids and web links only`() {
        val pages = skin(vinyl)!!.pages
        assertEquals(listOf("about"), pages.map { it.id })
        val blocks = pages.single().blocks
        assertEquals(PageBlock.Heading("Hi"), blocks[0])
        assertEquals(PageBlock.Button("Discord", "https://discord.gg/x"), blocks[1])
        assertEquals(PageBlock.Playlist("PL1", "Picks", null), blocks[2])
    }

    @Test
    fun `a manifest with only a look or a skin brings no songs and isn't a website`() {
        val m = manifest(vinyl)
        assertTrue(m.changesLook)
        assertFalse(m.playsSongs)
        assertFalse(WebModuleManifest.isWebsite(vinyl))
        assertTrue(WebModuleManifest.isWebsite("""{ "name": "Site", "start_url": "./", "icons": [] }"""))
    }

    @Test
    fun `nothing usable is no skin, and a broken background falls back to ambient`() {
        assertNull(skin("""{ "id": "x", "player": { "blocks": [ { "type": "nope" } ] } }"""))
        val player = skin("""{ "id": "x", "player": { "background": { "type": "gradient", "colors": ["#fff"] }, "blocks": [ { "type": "title" } ] } }""")!!.player!!
        assertEquals(SkinBackground.Player(PlayerBackgroundStyle.AMBIENT), player.background)
    }

    @Test
    fun `pictures resolve to links, zip files or files beside the manifest, never outside`() {
        val zip = skin(vinyl, files = File("/data/m"))!!
        assertEquals(File("/data/m", "images/a.webp"), zip.resolve("images/a.webp"))
        assertNull(zip.resolve("../../secrets"))
        assertEquals("https://x.dev/a.png", zip.resolve("https://x.dev/a.png"))
        val link = skin(vinyl, base = "https://mod.dev")!!
        assertEquals("https://mod.dev/images/a.webp", link.resolve("/images/a.webp"))
    }

    @Test
    fun `a player switches the player style and a fixed accent turns the cover theme off`() {
        val skin = skin(vinyl)!!
        val look = appliedLook(mapOf("accentColor" to "#E8B86B"), skin)
        assertEquals("MODULE", look["playerStyle"])
        assertEquals("false", look["dynamicTheme"])
        // the look's own choice wins
        assertEquals("POSTER", appliedLook(mapOf("playerStyle" to "POSTER"), skin)["playerStyle"])
    }

    @Test
    fun `hex colours`() {
        assertEquals(Color(0xFFE8B86B), parseHexColor("#E8B86B"))
        assertEquals(Color(0x80000000), parseHexColor("80000000"))
        assertNull(parseHexColor("#12"))
        assertNull(parseHexColor("red"))
    }
}
