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

    private val stage = """
        {
          "id": "stage", "name": "Stage",
          "player": {
            "styles": { "title": { "size": 30, "weight": "bold" }, ".soft": { "color": "inkSoft", "enter": "rise" } },
            "background": [
              { "type": "shader", "shader": "bg.agsl", "uniforms": { "speed": 2, "time": 9 }, "fallback": { "type": "solid" } },
              { "type": "image", "image": "glow.png", "opacity": 3, "animate": { "type": "drift", "speed": 99 } }
            ],
            "customise": { "title": "Stage", "settings": ["glow", "glow", "layout"] },
            "menu": [
              { "title": "Wallpapers", "icon": "w.png", "page": "walls" },
              { "title": "Like", "action": "like" },
              { "title": "Nothing" }
            ],
            "blocks": [
              { "type": "title", "class": "soft", "size": 40 },
              { "type": "visualizer", "style": "ring", "count": 99, "when": "!calm" },
              { "type": "button", "action": "customise" },
              { "type": "button", "link": "https://genius.com/search?q={title}", "image": "g.png" },
              { "type": "button", "link": "intent://evil" },
              { "type": "stack", "blocks": [ { "type": "cover" }, { "type": "visualizer", "style": "ring", "x": 900 } ] },
              { "type": "text", "text": "hi", "color": "${'$'}glow|#FFFFFF", "when": "layout=big" }
            ]
          }
        }
    """

    @Test
    fun `style sheets cascade like css, the block's own fields winning`() {
        val title = skin(stage)!!.player!!.blocks[0] as SkinBlock.Styled
        val text = (title.inner as SkinBlock.Title).text
        assertEquals(40f, text.size.value)
        assertEquals(SkinColor.InkSoft, text.color)
        assertEquals(EnterKind.Rise, title.enter!!.kind)
    }

    @Test
    fun `layers, conditions, buttons and menu items read with their values in range`() {
        val player = skin(stage, files = File("/data/m"))!!.player!!
        val shader = player.background as SkinBackground.Shader
        assertEquals("bg.agsl", shader.program.file)
        // mochy fills time in itself, so the module can't set it
        assertEquals(mapOf("speed" to 2f), shader.program.uniforms)
        assertEquals(SkinBackground.Player(PlayerBackgroundStyle.SOLID), shader.fallback)
        val layer = player.layers.single()
        assertEquals(1f, layer.opacity)
        assertEquals(MotionKind.Drift, layer.motion!!.kind)
        assertEquals(4f, layer.motion!!.speed)
        assertEquals(listOf("glow", "layout"), player.customise!!.keys)
        assertEquals(2, player.menu.size)
        assertEquals(SkinTarget.Control(ButtonKind.Like), player.menu[1].target)

        val visualizer = player.blocks[1] as SkinBlock.Styled
        assertEquals(SkinCondition("calm", null, negate = true), visualizer.condition)
        assertEquals(40, (visualizer.inner as SkinBlock.Visualizer).count)
        assertEquals(SkinTarget.Control(ButtonKind.Customise), (player.blocks[2] as SkinBlock.Button).target)
        assertEquals(SkinTarget.Action(ActionTarget.Link("https://genius.com/search?q={title}")), (player.blocks[3] as SkinBlock.Button).target)
        // the intent:// button is dropped, so the stack comes next
        val stack = player.blocks[4] as SkinBlock.Stack
        assertEquals(400.dp, (stack.blocks[1] as SkinBlock.Styled).x)
        val text = player.blocks[5] as SkinBlock.Styled
        assertEquals(SkinCondition("layout", "big", negate = false), text.condition)
        assertEquals(SkinColor.Setting("glow", SkinColor.Fixed(Color.White)), (text.inner as SkinBlock.Label).text.color)
        assertTrue(player.moves)
    }

    @Test
    fun `a shader only counts from a zip`() {
        val player = skin(stage, base = "https://mod.dev")!!.player!!
        assertEquals(SkinBackground.Player(PlayerBackgroundStyle.SOLID), player.background)
        assertTrue(skin(stage, files = File("/data/m"))!!.playerShaders == setOf("bg.agsl"))
        assertTrue(skin(stage, base = "https://mod.dev")!!.playerShaders.isEmpty())
    }

    private val motion = """
        {
          "id": "neon", "name": "Neon",
          "player": {
            "keyframes": {
              "pop": { "0%": { "scale": 0.6, "opacity": 0 }, "60%": { "scale": 1.1, "easing": "back" }, "100%": { "scale": 1 } },
              "wobble": [ { "at": 0.5, "rotate": 6 } ]
            },
            "blocks": [
              { "type": "cover", "animate": [ "beat", { "keyframes": "wobble", "duration": 900, "repeat": "pingpong" } ],
                "states": { "@paused": { "scale": 0.86, "blur": 99 } }, "transition": "spring", "origin": "bottom left" },
              { "type": "title", "enter": { "keyframes": "pop", "duration": 700, "on": "song" },
                "on": { "like": "pop", "beat": { "keyframes": [ { "at": 0, "y": -4 }, { "at": 1, "y": 0 } ], "duration": 200 }, "sneeze": "pop" } },
              { "type": "box", "glass": true, "direction": "row", "colors": ["#40FFFFFF", "#10FFFFFF"], "gradient": "diagonal",
                "blocks": [ { "type": "path", "d": "M2 2 L22 22 Z", "viewBox": "0 0 24 24", "stroke": 2 }, { "type": "text", "text": "{artist} · {${'$'}mood}" } ] },
              { "type": "path", "d": "<script>" },
              { "type": "text", "text": "glow", "animate": { "keyframes": "pop", "clock": "bass", "run": "always" }, "when": "@liked" }
            ]
          }
        }
    """

    @Test
    fun `keyframes read from both forms, fill in neutral ends and ease each step`() {
        val player = skin(motion)!!.player!!
        val cover = player.blocks[0] as SkinBlock.Styled
        assertEquals(listOf(MotionKind.Beat), cover.motions.map { it.kind })
        val wobble = cover.timelines.single()
        assertEquals(SkinRepeat.PingPong, wobble.repeat)
        assertEquals(900, wobble.durationMs)
        // only the middle was given, so the turn starts and ends at rest
        val turn = wobble.frames.tracks.single()
        assertEquals(PoseProp.Rotate, turn.prop)
        assertEquals(listOf(0f, 6f, 0f), turn.values.toList())
        assertEquals(0f, turn.valueAt(0f, androidx.compose.animation.core.LinearEasing))
        assertEquals(3f, turn.valueAt(0.25f, androidx.compose.animation.core.LinearEasing), 0.001f)
        assertEquals(0.5f, wobble.position(450f + 900f), 0.001f)
    }

    @Test
    fun `states, transitions, origins and events read with their values in range`() {
        val player = skin(motion)!!.player!!
        val cover = player.blocks[0] as SkinBlock.Styled
        val paused = cover.states.single()
        assertEquals(SkinCondition("@paused", null, negate = false), paused.condition)
        assertEquals(0.86f, paused.values[PoseProp.Scale])
        assertEquals(60f, paused.values[PoseProp.Blur])
        assertTrue(cover.transition!!.spring)
        assertEquals(0f, cover.originX)
        assertEquals(1f, cover.originY)

        val title = player.blocks[1] as SkinBlock.Styled
        assertEquals(EnterKind.Keyframes, title.enter!!.kind)
        assertTrue(title.enter!!.eachSong)
        // an event mochy doesn't know is skipped
        assertEquals(listOf(SkinEvent.Like, SkinEvent.Beat), title.triggers.map { it.event })
        assertEquals(SkinRepeat.Once, title.triggers[0].timeline.repeat)
        assertEquals(setOf(SkinEvent.Like, SkinEvent.Beat), player.events)
        assertEquals(setOf(SkinFlag.Paused, SkinFlag.Liked), player.flags)
        assertTrue(player.alwaysMoves.not())
        assertEquals(SkinClock.Bass, (player.blocks[3] as SkinBlock.Styled).timelines.single().clock)
    }

    @Test
    fun `boxes hold blocks on glass or a gradient, and paths only take path data`() {
        val player = skin(motion)!!.player!!
        val box = player.blocks[2] as SkinBlock.Box
        assertTrue(box.row)
        assertEquals(24f, box.glass)
        assertEquals(GradientKind.Diagonal, box.gradient)
        assertEquals(2, box.fill.size)
        val path = box.blocks[0] as SkinBlock.Vector
        // an outline only: a stroke and no fill unless it asks for one
        assertTrue(path.fill.isEmpty())
        assertEquals(SkinColor.Ink, path.strokeColor)
        assertEquals(24f, path.viewWidth)
        assertTrue(player.frosted)
        // the script tag wasn't path data, so the next block is the glowing text
        assertTrue((player.blocks[3] as SkinBlock.Styled).inner is SkinBlock.Label)
    }

    @Test
    fun `easings by name, as css cubic-bezier and as steps`() {
        assertNull(SkinAnimationParser.easing("wiggly"))
        val steps = SkinAnimationParser.easing("steps(4)")!!
        assertEquals(0.25f, steps.transform(0.3f))
        val bezier = SkinAnimationParser.easing("cubic-bezier(0.2, -0.5, 0.8, 1.5)")!!
        assertEquals(1f, bezier.transform(1f), 0.001f)
        assertEquals(1f, SkinAnimationParser.easing("bounce")!!.transform(1f), 0.001f)
    }

    @Test
    fun `a page brings a background, frosted cards and shaders, shaders only from a zip`() {
        val text = """
            { "id": "p", "name": "P", "pages": [ { "id": "home", "title": "Home",
              "background": { "type": "shader", "shader": "page.agsl", "fallback": ["#101020", "#000000"], "uniforms": { "time": 3, "speed": 2 } },
              "blocks": [
                { "type": "shader", "shader": "orb.agsl", "height": 2000 },
                { "type": "card", "glass": 18, "blocks": [ { "type": "heading", "text": "Hi" }, { "type": "card", "blocks": [ { "type": "text", "text": "x" } ] } ] }
              ] } ] }
        """
        val page = skin(text, files = File("/data/m"))!!.pages.single()
        val background = page.background as PageBackground.Shader
        // mochy fills time in itself, so the module's own value is dropped
        assertEquals(mapOf("speed" to 2f), background.program.uniforms)
        assertEquals(2, background.fallback.size)
        assertEquals(800.dp, (page.blocks[0] as PageBlock.Shader).height)
        val card = page.blocks[1] as PageBlock.Card
        assertEquals(18f, card.glass)
        // a card inside a card is dropped
        assertEquals(1, card.blocks.size)
        assertTrue(page.frosted)

        val fromLink = skin(text, base = "https://mod.dev")!!.pages.single()
        assertEquals(PageBackground.Fill(listOf(SkinColor.Fixed(Color(0xFF101020)), SkinColor.Fixed(Color.Black))), fromLink.background)
        assertTrue(fromLink.blocks.first() is PageBlock.Card)
    }

    @Test
    fun `hex colours`() {
        assertEquals(Color(0xFFE8B86B), parseHexColor("#E8B86B"))
        assertEquals(Color(0x80000000), parseHexColor("80000000"))
        assertNull(parseHexColor("#12"))
        assertNull(parseHexColor("red"))
    }
}
