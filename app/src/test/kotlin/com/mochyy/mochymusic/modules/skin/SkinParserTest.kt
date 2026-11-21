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
        return SkinParser.parse("local.test", "Test", m.player, m.font, m.pages, files, base, m.actions, m.glass, m.players, m.bar, m.mini)
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

    @Test
    fun `several players, each with sub-styles laid over it`() {
        val text = """
            { "id": "p", "name": "P",
              "player": { "name": "Neon", "styles": { "title": { "size": 30 } }, "blocks": [ { "type": "title" }, { "type": "cover" } ],
                "variants": [
                  { "id": "pink", "name": "Pink", "values": { "glow": "pink" }, "styles": { "artist": { "size": 12 } } },
                  { "id": "bare", "name": "Bare", "blocks": [ { "type": "title" } ] },
                  { "name": "no id is dropped" }
                ] },
              "players": [ { "id": "tape", "name": "Tape", "editor": "edit", "designWidth": 390, "scale": "${'$'}size", "blocks": [ { "type": "artist" } ] } ] }
        """
        val skin = skin(text)!!
        assertEquals(listOf("main", "tape"), skin.players.map { it.id })
        val neon = skin.players[0]
        assertEquals(listOf("pink", "bare"), neon.variants.map { it.id })
        // the sub-style keeps the base player's blocks and its title style, and only adds what it brings
        val pink = neon.variants[0]
        assertEquals(2, pink.player.blocks.size)
        assertEquals(mapOf("glow" to "pink"), pink.values)
        assertEquals("Neon", pink.player.name)
        assertEquals(1, neon.variants[1].player.blocks.size)
        val tape = skin.players[1]
        assertEquals("edit", tape.editor)
        assertEquals(SkinSize(390f, 1f, "size"), tape.size)
        assertEquals(1.25f, tape.size.factor(487.5f, mapOf("size" to "1")), 0.001f)
        assertEquals(0.5f, tape.size.factor(390f, mapOf("size" to "0.1")), 0.001f)
    }

    @Test
    fun `a nav bar always keeps the settings tab`() {
        val text = """
            { "id": "p", "name": "P", "bar": { "tabs": ["home", { "page": "mine", "label": "Mine" }, "nope", "search"],
              "height": 200, "indicator": { "type": "dot", "color": "accent" }, "labels": "selected", "shader": "bar.agsl" } }
        """
        val bar = skin(text, files = File("/data/m"))!!.bar!!
        assertEquals(
            listOf(BarTarget.Tab("home"), BarTarget.Page("mine"), BarTarget.Tab("search_input"), BarTarget.Tab("settings")),
            bar.tabs.map { it.target },
        )
        assertEquals(72.dp, bar.height)
        assertEquals(IndicatorKind.Dot, bar.indicator.kind)
        assertEquals(BarLabels.Selected, bar.labels)
        assertEquals("bar.agsl", bar.shader?.file)
        // a shader only comes from a zip
        assertNull(skin(text, base = "https://mod.dev")!!.bar!!.shader)
    }

    @Test
    fun `a page takes mochy's pages over, with the subject's blocks`() {
        val text = """
            { "id": "p", "name": "P", "pages": [ { "id": "list", "title": "{title}", "replaces": ["playlist", "Album", "bad name!"], "when": "custom",
              "header": false, "blocks": [ { "type": "cover", "shape": "circle" }, { "type": "play" }, { "type": "control", "action": "original" },
              { "type": "songs", "limit": 9999, "numbers": true }, { "type": "player" }, { "type": "styles" } ] } ] }
        """
        val page = skin(text)!!.pages.single()
        assertEquals(listOf("playlist", "album"), page.replaces)
        assertEquals(SkinCondition("custom", null, negate = false), page.condition)
        assertFalse(page.header)
        assertTrue((page.blocks[0] as PageBlock.Cover).circle)
        assertEquals(PageControl.Play, (page.blocks[1] as PageBlock.Control).kind)
        assertEquals(PageControl.Original, (page.blocks[2] as PageBlock.Control).kind)
        assertEquals(500, (page.blocks[3] as PageBlock.Songs).limit)
        assertTrue(page.blocks[5] == PageBlock.Styles)
        assertTrue(page.condition!!.holdsFor(mapOf("custom" to "true")))
        assertFalse(page.condition!!.holdsFor(mapOf("custom" to "false")))
    }

    @Test
    fun `custom lyrics with poses, an entrance and shaders from a zip`() {
        val text = """
            { "id": "p", "name": "P", "player": { "keyframes": { "pop": { "from": { "scale": 0.8 }, "to": { "scale": 1 } } },
              "blocks": [ { "type": "lyrics", "style": "custom", "past": { "opacity": 0.2, "blur": 4 }, "lineEnter": { "keyframes": "pop", "duration": 300 }, "textScale": "${'$'}lyricSize",
                "fill": "lyrics.agsl", "effect": { "shader": "bend.agsl", "uniforms": { "lineProgress": 3, "amount": 2 } } } ] } }
        """
        val lyrics = skin(text, files = File("/data/m"))!!.player!!.blocks.single() as SkinBlock.Lyrics
        val style = lyrics.custom!!
        assertEquals(mapOf(PoseProp.Opacity to 0.2f, PoseProp.Blur to 4f), style.past)
        assertEquals(300, style.enter!!.durationMs)
        assertEquals("lyricSize", style.scale.scaleKey)
        assertEquals("lyrics.agsl", style.fill?.file)
        // mochy fills the line's progress in itself
        assertEquals(mapOf("amount" to 2f), style.effect?.uniforms)
        assertTrue(lyrics.moves)
        val fromLink = (skin(text, base = "https://mod.dev")!!.player!!.blocks.single() as SkinBlock.Lyrics).custom!!
        assertNull(fromLink.fill)
    }

    @Test
    fun `a slider setting keeps its ends`() {
        val m = manifest("""{ "id": "p", "name": "P", "settings": [ { "key": "size", "type": "slider", "min": 0.8, "max": 1.4, "step": 0.1 }, { "key": "x", "type": "slider", "min": 3, "max": 1 } ] }""")
        val size = m.setting("size")!!
        assertEquals(com.mochyy.mochymusic.modules.web.FieldRange(0.8f, 1.4f, 0.1f), size.range)
        assertEquals("0.8", size.toField().default)
        // ends the wrong way round get a sensible top
        assertEquals(4f, m.setting("x")!!.range!!.end)
    }

    @Test
    fun `editor parts, the bottom bar and a mini player`() {
        val text = """
            { "id": "p", "name": "P",
              "player": {
                "edit": [ { "part": "cover", "title": "Cover", "choose": "shape", "settings": ["spin"], "hold": "size" }, { "part": "bad id!" }, { "part": "bg" } ],
                "blocks": [ { "type": "cover", "part": "cover" }, { "type": "bottomBar", "style": "pills", "glass": true, "height": 999 } ] },
              "mini": { "blocks": [ { "type": "title" }, { "type": "button", "action": "playPause" } ] } }
        """
        val skin = skin(text)!!
        val player = skin.player!!
        assertEquals(listOf("cover", "bg"), player.edit.map { it.id })
        assertEquals(SkinEditPart("cover", "Cover", "shape", listOf("spin"), "size"), player.edit[0])
        assertEquals("Bg", player.edit[1].title)
        assertEquals(setOf("cover"), player.partIds)
        val bar = player.blocks[1] as SkinBlock.BottomBar
        assertEquals(BottomBarKind.Pills, bar.kind)
        assertEquals(160.dp, bar.height)
        assertTrue(player.frosted)
        val mini = skin.mini!!
        assertEquals("mini", mini.id)
        // a mini player that names no background keeps mochy's glass
        assertEquals(SkinBackground.Tones, mini.background)
        assertTrue(skin.restyles)
    }

    @Test
    fun `custom queue cards, dynamic covers, tappable boxes, volume and extra controls`() {
        val text = """
            { "id": "p", "name": "P",
              "player": {
                "keyframes": { "pop": { "from": { "scale": 0.9 }, "to": { "scale": 1 } } },
                "blocks": [
                  { "type": "box", "glass": true, "direction": "row", "action": "queue", "when": "@hasNext", "on": { "queue": "pop" },
                    "blocks": [
                      { "type": "image", "image": "{nextCover}", "fallback": "fallback.png", "width": 44, "height": 44 },
                      { "type": "image", "image": "{next3Cover}", "width": 32, "height": 32 },
                      { "type": "image", "image": "{prevCover}", "width": 32, "height": 32 },
                      { "type": "image", "image": "{cover}", "width": 32, "height": 32 },
                      { "type": "text", "text": "{next} · {next2Artist} · {next2Length} · {prev} · {queueLeft} · {queueTitle}" }
                    ] },
                  { "type": "queue", "limit": 99, "covers": true, "coverSize": 100, "times": true, "numbers": true, "tap": "queue", "height": 220, "empty": "All done" },
                  { "type": "volume", "style": "pill", "icons": false },
                  { "type": "button", "action": "queue" },
                  { "type": "button", "action": "forward10" },
                  { "type": "button", "action": "rewind" },
                  { "type": "button", "action": "more" },
                  { "type": "button", "action": "artist" },
                  { "type": "button", "action": "album" },
                  { "type": "text", "text": "hi-res", "when": "@lossless" },
                  { "type": "text", "text": "loop one", "when": "@repeatOne" },
                  { "type": "text", "text": "back", "when": "@prev" }
                ] } }
        """
        val player = skin(text)!!.player!!
        val card = player.blocks[0] as SkinBlock.Styled
        val box = card.inner as SkinBlock.Box
        assertEquals(SkinTarget.Control(ButtonKind.Queue), box.target)
        assertEquals(SkinPictureSource.QueueCover(1), (box.blocks[0] as SkinBlock.Picture).source)
        assertEquals("fallback.png", (box.blocks[0] as SkinBlock.Picture).fallback)
        assertEquals(SkinPictureSource.QueueCover(3), (box.blocks[1] as SkinBlock.Picture).source)
        assertEquals(SkinPictureSource.QueueCover(-1), (box.blocks[2] as SkinBlock.Picture).source)
        assertEquals(SkinPictureSource.CurrentCover, (box.blocks[3] as SkinBlock.Picture).source)
        assertTrue(player.queue)
        assertTrue(player.audio)
        assertTrue(player.playerFields)
        assertEquals(setOf(SkinEvent.Queue), player.events)
        assertEquals(setOf(SkinFlag.HasNext, SkinFlag.HasPrev, SkinFlag.RepeatOne, SkinFlag.Lossless), player.flags)

        val queue = player.blocks[1] as SkinBlock.QueueList
        assertEquals(20, queue.limit)
        assertEquals(72.dp, queue.coverSize)
        assertTrue(queue.times)
        assertTrue(queue.numbers)
        assertEquals(QueueTap.Queue, queue.tap)
        assertEquals(220.dp, queue.height)
        assertEquals("All done", queue.empty)

        val volume = player.blocks[2] as SkinBlock.Volume
        assertEquals(ProgressStyle.Pill, volume.style)
        assertFalse(volume.icons)

        assertEquals(
            listOf(ButtonKind.Queue, ButtonKind.Forward, ButtonKind.Rewind, ButtonKind.OtherMenu, ButtonKind.Artist, ButtonKind.Album),
            (3..8).map { ((player.blocks[it] as SkinBlock.Button).target as SkinTarget.Control).kind },
        )
    }

    @Test
    fun `a plain layout without queue or lossless fields doesn't subscribe to them`() {
        val player = skin("""{ "id": "p", "name": "P", "player": { "blocks": [ { "type": "title" }, { "type": "image", "image": "pic.png" } ] } }""")!!.player!!
        assertFalse(player.queue)
        assertFalse(player.audio)
        assertFalse(player.playerFields)
        assertEquals(SkinPictureSource.Asset("pic.png"), (player.blocks[1] as SkinBlock.Picture).source)
    }
}
