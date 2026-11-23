// turns a manifest's players into layouts: backgrounds, blocks, loops, keyframes, states, entrances, sub-styles and settings that switch blocks on and off, every number kept in range

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal object SkinPlayerParser {

    /** "player" and "players" together, the single one first; each with its sub-styles, and no two with the same id */
    fun players(single: JsonElement?, list: JsonElement?, fallbackName: String, fromZip: Boolean): List<SkinPlayer> {
        val raw = listOfNotNull(single as? JsonObject) + (list as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return raw.take(MaxPlayers)
            .mapIndexedNotNull { i, o -> withVariants(o, fallbackName, fromZip, defaultId = if (i == 0) "main" else "player$i") }
            .distinctBy { it.id }
    }

    private fun withVariants(o: JsonObject, fallbackName: String, fromZip: Boolean, defaultId: String): SkinPlayer? {
        val id = o.string("id")?.lowercase()?.takeIf { PlayerId.matches(it) } ?: defaultId
        val base = player(o, fallbackName, fromZip)?.copy(id = id) ?: return null
        val list = (o["variants"] as? JsonArray) ?: (o["substyles"] as? JsonArray)
        val variants = list.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let { v -> variant(o, v, base, fallbackName, fromZip) } }
            .distinctBy { it.id }
            .take(MaxVariants)
        return base.copy(variants = variants)
    }

    // a sub-style is the base player with its own fields laid over it; styles and keyframes merge by name so it only has to bring what it changes
    private fun variant(base: JsonObject, v: JsonObject, parsed: SkinPlayer, fallbackName: String, fromZip: Boolean): SkinVariant? {
        val id = v.string("id")?.lowercase()?.takeIf { PlayerId.matches(it) } ?: return null
        val own = JsonObject(v - "id" - "name" - "values")
        val merged = buildMap {
            putAll(base - "variants" - "substyles")
            putAll(own)
            for (key in MergedKeys) {
                val a = base[key] as? JsonObject
                val b = own[key] as? JsonObject
                if (a != null && b != null) put(key, JsonObject(a + b))
            }
        }
        val player = player(JsonObject(merged), fallbackName, fromZip) ?: return null
        val values = (v["values"] as? JsonObject).orEmpty().entries
            .mapNotNull { (key, e) -> (e as? JsonPrimitive)?.contentOrNull?.let { key.take(60) to it.take(200) } }
            .take(MaxVariantValues)
            .toMap()
        return SkinVariant(id, v.string("name")?.take(40) ?: id, player.copy(id = parsed.id, name = parsed.name, editor = parsed.editor), values)
    }

    /** null when it has no block mochy knows; shaders only count from a zip, the same as a glass */
    fun player(o: JsonObject, fallbackName: String, fromZip: Boolean): SkinPlayer? {
        val reader = Reader(fromZip, styleSheet(o["styles"]), SkinAnimationParser.library(o["keyframes"]))
        val blocks = reader.blocks(o["blocks"], depth = 0)
        if (blocks.isEmpty()) return null
        // one background, or a list of them stacked: the first at the back
        val backgrounds = when (val raw = o["background"]) {
            is JsonArray -> raw.mapNotNull { it as? JsonObject }.take(MaxLayers)
            is JsonObject -> listOf(raw)
            else -> emptyList()
        }
        val base = backgrounds.firstOrNull()
        return SkinPlayer(
            name = o.string("name")?.take(40) ?: fallbackName,
            background = reader.background(base),
            dim = (o.float("dim") ?: base?.float("dim"))?.coerceIn(0f, 0.9f) ?: 0f,
            padding = (o.float("padding") ?: 24f).coerceIn(0f, 64f).dp,
            align = skinAlign(o.string("align")) ?: SkinAlign.Center,
            blocks = blocks,
            layers = backgrounds.drop(1).map { layer ->
                SkinLayer(reader.background(layer), (layer.float("opacity") ?: 1f).coerceIn(0f, 1f), motion(layer["animate"]))
            },
            customise = customise(o["customise"]),
            menu = (o["menu"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::menuItem) }.take(MaxMenu),
            menuButton = menuButton(o["menuButton"]),
            header = reader.blocks(o["header"], depth = 1).take(MaxHeader),
            scroll = o.bool("scroll") ?: false,
            editor = o.string("editor")?.lowercase()?.takeIf { PlayerId.matches(it) },
            size = SkinSize.of(o),
            edit = editParts(o["edit"]),
        )
    }

    /** a look's mini player: blocks in a row over the pill, and no background of its own unless it names one, so mochy's glass shows */
    fun mini(o: JsonObject, fallbackName: String, fromZip: Boolean): SkinPlayer? {
        val parsed = player(o, fallbackName, fromZip) ?: return null
        return parsed.copy(
            id = "mini",
            padding = (o.float("padding") ?: 0f).coerceIn(0f, 32f).dp,
            background = if (o["background"] == null) SkinBackground.Tones else parsed.background,
        )
    }

    // "edit": [ { "part": "cover", "title": "Cover", "choose": "shape", "settings": ["spin"], "hold": "coverSize" } ]
    private fun editParts(element: JsonElement?): List<SkinEditPart> =
        (element as? JsonArray).orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = (o.string("part") ?: o.string("id"))?.lowercase()?.takeIf { PlayerId.matches(it) } ?: return@mapNotNull null
            val settings = (o["settings"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.take(60) }
            SkinEditPart(
                id = id,
                title = o.string("title")?.take(30) ?: id.replaceFirstChar { it.uppercase() },
                choose = o.string("choose")?.take(60),
                settings = settings.distinct().take(MaxCustomise),
                hold = o.string("hold")?.take(60),
            )
        }.distinctBy { it.id }.take(MaxEditParts)

    // "menuButton": "start", "hidden", false, or { "side": "start", "color": "cover", "fill": "#00000000" }
    private fun menuButton(element: JsonElement?): SkinMenuButton {
        val o = element as? JsonObject
        val raw = (element as? JsonPrimitive)?.contentOrNull ?: o?.string("side")
        val side = when (raw?.trim()?.lowercase()) {
            "start", "left" -> MenuSide.Start
            "hidden", "none", "false", "off" -> MenuSide.Hidden
            else -> MenuSide.End
        }
        return SkinMenuButton(side, color = skinColor(o?.string("color")), fill = skinColor(o?.string("fill")))
    }

    // { "title": "Wallpapers", "icon": "images/w.png", "page": "walls" } or { "title": "Like", "action": "like" }
    private fun menuItem(o: JsonObject): SkinMenuItem? {
        val title = o.string("title")?.take(30) ?: return null
        return target(o)?.let { SkinMenuItem(title, o.string("icon") ?: o.string("image"), it) }
    }

    /** one of mochy's controls by "action", else a link, a page or a search */
    fun target(o: JsonObject): SkinTarget? =
        buttonKind(o.string("action"))?.let(SkinTarget::Control) ?: SkinParser.actionTarget(o)?.let(SkinTarget::Action)

    // "styles": { "title": {...}, ".glow": {...} }: rules by block type, and classes a block names, css style
    private fun styleSheet(element: JsonElement?): Map<String, JsonObject> =
        (element as? JsonObject).orEmpty().entries
            .mapNotNull { (name, rule) -> (rule as? JsonObject)?.let { name.trim().lowercase() to JsonObject(it - "type") } }
            .take(MaxStyles)
            .toMap()

    // "customise": ["glow", "spin"] or { "title": "Vinyl", "settings": [...] }
    private fun customise(element: JsonElement?): SkinCustomise? {
        val (title, list) = when (element) {
            is JsonArray -> null to element
            is JsonObject -> element.string("title")?.take(40) to (element["settings"] as? JsonArray)
            else -> return null
        }
        val keys = list.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.take(60) }.distinct().take(MaxCustomise)
        return keys.takeIf { it.isNotEmpty() }?.let { SkinCustomise(title, it) }
    }

    private class Reader(private val fromZip: Boolean, private val styles: Map<String, JsonObject>, private val library: Map<String, SkinKeyframes>) {

        fun background(o: JsonObject?): SkinBackground = when (o?.string("type")?.lowercase()) {
            "blurredcover", "cover" -> SkinBackground.Player(PlayerBackgroundStyle.APPLE_MUSIC)
            "darkgradient" -> SkinBackground.Player(PlayerBackgroundStyle.DARK_GRADIENT)
            "solid" -> SkinBackground.Player(PlayerBackgroundStyle.SOLID)
            "tones" -> SkinBackground.Tones
            "color" -> SkinBackground.Fill(listOf(skinColor(o.string("color")) ?: SkinColor.Fixed(Color.Black)))
            "gradient" -> colors(o["colors"])?.let(SkinBackground::Fill) ?: Ambient
            "image" -> o.string("image")?.let { SkinBackground.Picture(it, (o.float("blur") ?: 0f).coerceIn(0f, 60f)) } ?: Ambient
            "shader" -> {
                val fallback = (o["fallback"] as? JsonObject)?.takeIf { it.string("type")?.lowercase() != "shader" }?.let(::background) ?: Ambient
                shader(o)?.let { SkinBackground.Shader(it, fallback) } ?: fallback
            }
            // the song's video or animated cover across the whole screen, over the blurred cover unless it names another fallback
            "livecover" -> {
                val fallback = (o["fallback"] as? JsonObject)?.takeIf { it.string("type")?.lowercase() != "livecover" }?.let(::background)
                SkinBackground.Live(coverMedia(o["media"]).ifEmpty { listOf(CoverMedia.Auto) }, fallback ?: BlurredCover)
            }
            else -> Ambient
        }

        // "media": "video", or a list tried in order: video, animated, auto, and loops of the module's own
        fun coverMedia(element: JsonElement?): List<CoverMedia> {
            val names = when (element) {
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                else -> emptyList()
            }
            return names.mapNotNull { raw ->
                val name = raw.trim()
                when (name.lowercase()) {
                    "animated", "canvas" -> CoverMedia.Animated
                    "video", "youtube" -> CoverMedia.Video
                    "auto", "true" -> CoverMedia.Auto
                    else -> name.takeIf { LoopFile.containsMatchIn(it.substringBefore('?')) }?.let(CoverMedia::Loop)
                }
            }.distinct().take(MaxMedia)
        }

        // "16:9", "9:16" or a plain number, always width over height
        private fun aspect(element: JsonElement?): Float? {
            val raw = (element as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            val sides = raw.split(':', '/')
            val ratio = if (sides.size == 2) {
                val w = sides[0].trim().toFloatOrNull()
                val h = sides[1].trim().toFloatOrNull()
                if (w != null && h != null && h > 0f) w / h else null
            } else {
                raw.toFloatOrNull()
            }
            return ratio?.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.25f, 4f)
        }

        fun blocks(element: JsonElement?, depth: Int): List<SkinBlock> =
            (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> styled(cascade(o), depth) } }.take(MaxBlocks)

        // the type's rule first, then each class in order, then the block's own fields, so the most specific one wins like css
        private fun cascade(o: JsonObject): JsonObject {
            if (styles.isEmpty()) return o
            val type = o.string("type")?.lowercase() ?: return o
            val classes = o.string("class")?.split(' ', ',')?.map { "." + it.trim().lowercase() }?.filter { it.length > 1 }.orEmpty()
            val rules = listOfNotNull(styles[type]) + classes.mapNotNull { styles[it] }
            if (rules.isEmpty()) return o
            return JsonObject(rules.fold(emptyMap<String, JsonElement>()) { merged, rule -> merged + rule } + o)
        }

        // the block itself, wrapped in its loops, keyframes, states, events, entrance, condition, nudges and blur when it has any
        private fun styled(o: JsonObject, depth: Int): SkinBlock? {
            val inner = block(o, depth) ?: return null
            // "animate" is one loop or a list of them: a preset by name, or keyframes
            val animate = o["animate"]
            val loops = ((animate as? JsonArray)?.toList() ?: listOfNotNull(animate)).take(MaxLoops)
            val motions = loops.mapNotNull(::motion)
            val timelines = loops.mapNotNull { loop ->
                (loop as? JsonObject)?.takeIf { "keyframes" in it }?.let { SkinAnimationParser.timeline(it, library, SkinRepeat.Loop, DefaultLoopMs) }
            }
            val enter = enter(o["enter"])
            val condition = o.string("when")?.let(SkinAnimationParser::condition)
            val states = SkinAnimationParser.states(o["states"])
            val transition = SkinAnimationParser.transition(o["transition"]) ?: SkinAnimationParser.DefaultTransition.takeIf { states.isNotEmpty() }
            val triggers = SkinAnimationParser.triggers(o["on"], library)
            val part = o.string("part")?.lowercase()?.takeIf { PlayerId.matches(it) }
            val opacity = (o.float("opacity") ?: 1f).coerceIn(0f, 1f)
            val x = (o.float("x") ?: 0f).coerceIn(-400f, 400f)
            val y = (o.float("y") ?: 0f).coerceIn(-400f, 400f)
            val rotate = (o.float("rotate") ?: 0f).coerceIn(-180f, 180f)
            val scale = (o.float("scale") ?: 1f).coerceIn(0f, 5f)
            val blur = (o.float("blur") ?: 0f).coerceIn(0f, SkinAnimationParser.MaxBlur)
            val (originX, originY) = origin(o)
            val plain = motions.isEmpty() && timelines.isEmpty() && enter == null && condition == null && states.isEmpty() && triggers.isEmpty() &&
                opacity == 1f && x == 0f && y == 0f && rotate == 0f && scale == 1f && blur == 0f && part == null
            if (plain) return inner
            return SkinBlock.Styled(
                inner, motions, enter, condition, opacity, x.dp, y.dp, rotate,
                scale = scale, blur = blur, originX = originX, originY = originY,
                timelines = timelines, states = states, transition = transition, triggers = triggers, part = part,
            )
        }

        // "origin": "top left", or originX and originY from 0 to 1: the point a block turns and grows around
        private fun origin(o: JsonObject): Pair<Float, Float> {
            val words = o.string("origin")?.lowercase().orEmpty()
            val x = o.float("originX") ?: when {
                "left" in words || "start" in words -> 0f
                "right" in words || "end" in words -> 1f
                else -> 0.5f
            }
            val y = o.float("originY") ?: when {
                "top" in words -> 0f
                "bottom" in words -> 1f
                else -> 0.5f
            }
            return x.coerceIn(-1f, 2f) to y.coerceIn(-1f, 2f)
        }

        // "enter": "rise", { "type": "rise", "delay": 120 }, or keyframes { "keyframes": "popIn", "duration": 700 }
        private fun enter(element: JsonElement?): SkinEnter? {
            val o = element as? JsonObject
            if (o != null && "keyframes" in o) {
                val timeline = SkinAnimationParser.timeline(o, library, SkinRepeat.Once, DefaultEnterMs) ?: return null
                return SkinEnter(
                    kind = EnterKind.Keyframes,
                    delayMs = timeline.delayMs.coerceAtMost(3000),
                    durationMs = timeline.durationMs.coerceIn(100, 5000),
                    eachSong = o.string("on")?.lowercase() == "song",
                    frames = timeline.frames,
                )
            }
            return presetEnter(element)
        }

        private fun block(o: JsonObject, depth: Int): SkinBlock? = when (o.string("type")?.lowercase()) {
            "cover" -> SkinBlock.Cover(
                shape = when (o.string("shape")?.lowercase()) {
                    "circle" -> CoverShape.Circle
                    "square" -> CoverShape.Square
                    else -> CoverShape.Rounded
                },
                corner = (o.float("corner") ?: 18f).coerceIn(0f, 80f).dp,
                size = (o.float("size") ?: 0.8f).coerceIn(0.1f, 1f),
                spin = o.bool("spin") ?: false,
                frame = o.string("frame"),
                shadow = o.bool("shadow") ?: false,
                spinSpeed = (o.float("spinSpeed") ?: 1f).coerceIn(0.1f, 4f),
                width = o.float("width")?.coerceIn(16f, 600f)?.dp,
                height = o.float("height")?.coerceIn(16f, 800f)?.dp,
                aspect = aspect(o["aspect"]) ?: 1f,
                fit = o.string("fit")?.lowercase() == "fit",
                media = coverMedia(o["media"]),
            )
            "title" -> SkinBlock.Title(textOf(o, 24f, FontWeight.Bold, SkinColor.Ink))
            "artist" -> SkinBlock.Artist(textOf(o, 16f, FontWeight.Medium, SkinColor.InkSoft))
            "album" -> SkinBlock.Album(textOf(o, 14f, FontWeight.Medium, SkinColor.InkSoft))
            "text" -> o.string("text")?.take(200)?.let { SkinBlock.Label(it, textOf(o, 14f, FontWeight.Medium, SkinColor.InkSoft)) }
            "image" -> o.string("image")?.let { raw ->
                SkinBlock.Picture(
                    image = raw,
                    width = o.float("width")?.coerceIn(8f, 600f)?.dp,
                    height = (o.float("height") ?: 120f).coerceIn(8f, 600f).dp,
                    corner = (o.float("corner") ?: 12f).coerceIn(0f, 80f).dp,
                    fit = o.string("fit")?.lowercase() == "fit",
                    tint = skinColor(o.string("tint")),
                    source = pictureSource(raw),
                    fallback = o.string("fallback"),
                )
            }
            "box", "panel", "card" -> box(o, depth)
            "bottombar", "deck" -> bottomBar(o)
            "queue", "upnext" -> queueList(o)
            "volume" -> volume(o)
            "path", "vector", "svg" -> vector(o)
            "progress" -> SkinBlock.Progress(
                style = when (o.string("style")?.lowercase()) {
                    "thick" -> ProgressStyle.Thick
                    "pill" -> ProgressStyle.Pill
                    else -> ProgressStyle.Line
                },
                color = skinColor(o.string("color")) ?: SkinColor.Ink,
                track = skinColor(o.string("track")) ?: SkinColor.Chip,
                times = o.bool("times") ?: true,
                seek = o.bool("seek") ?: true,
            )
            "controls" -> SkinBlock.Controls(
                size = (o.float("size") ?: 48f).coerceIn(28f, 96f).dp,
                plain = o.string("style")?.lowercase() == "plain",
                color = skinColor(o.string("color")),
                icon = skinColor(o.string("icon")),
            )
            "lyrics" -> {
                // a flowing list is small words, the sung-through kind needs room for its big ones
                val flow = o.string("style")?.lowercase() == "flow"
                val custom = if (o.string("style")?.lowercase() == "custom") SkinLyricsParser.style(o, library, ::lyricShader) else null
                SkinBlock.Lyrics(
                    height = (o.float("height") ?: if (flow) 72f else if (custom != null) 260f else 160f).coerceIn(if (flow) 16f else 60f, 800f).dp,
                    size = (o.float("size") ?: if (flow) 14f else 28f).coerceIn(if (flow) 8f else 14f, 60f).sp,
                    color = skinColor(o.string("color")) ?: SkinColor.Ink,
                    flow = flow,
                    active = skinColor(o.string("active")),
                    prefix = o.string("prefix")?.take(8).orEmpty(),
                    align = skinAlign(o.string("align")),
                    custom = custom,
                )
            }
            "space" -> SkinBlock.Space(size = (o.float("size") ?: 16f).coerceIn(0f, 300f).dp, grow = o.bool("grow") ?: false)
            "row" -> group(o, depth) { blocks, gap -> SkinBlock.Row(blocks, gap) }
            "column" -> group(o, depth) { blocks, gap -> SkinBlock.Column(blocks, gap) }
            "stack" -> group(o, depth) { blocks, _ ->
                SkinBlock.Stack(blocks, stackAlign(o.string("align")), o.float("height")?.coerceIn(8f, 800f)?.dp)
            }
            "visualizer" -> SkinBlock.Visualizer(
                style = when (o.string("style")?.lowercase()) {
                    "mirror" -> VisualizerStyle.Mirror
                    "wave" -> VisualizerStyle.Wave
                    "dots" -> VisualizerStyle.Dots
                    "ring" -> VisualizerStyle.Ring
                    else -> VisualizerStyle.Bars
                },
                count = (o.float("count")?.toInt() ?: 24).coerceIn(4, 40),
                height = (o.float("height") ?: 64f).coerceIn(16f, 400f).dp,
                color = skinColor(o.string("color")) ?: SkinColor.Ink,
                fill = (o.float("fill") ?: 0.6f).coerceIn(0.1f, 1f),
                round = o.bool("round") ?: true,
            )
            "button" -> target(o)?.let { target ->
                SkinBlock.Button(
                    target = target,
                    size = (o.float("size") ?: 44f).coerceIn(24f, 96f).dp,
                    color = skinColor(o.string("color")),
                    fill = skinColor(o.string("fill")),
                    image = o.string("image"),
                    imageOn = o.string("imageOn"),
                )
            }
            "shape" -> SkinBlock.Shape(
                kind = when (o.string("shape")?.lowercase()) {
                    "circle" -> ShapeKind.Circle
                    "pill" -> ShapeKind.Pill
                    "line" -> ShapeKind.Line
                    else -> ShapeKind.Rect
                },
                width = o.float("width")?.coerceIn(1f, 600f)?.dp,
                height = (o.float("height") ?: 2f).coerceIn(1f, 600f).dp,
                colors = colors(o["colors"]) ?: listOf(skinColor(o.string("color")) ?: SkinColor.Chip),
                corner = (o.float("corner") ?: 0f).coerceIn(0f, 300f).dp,
                stroke = o.float("stroke")?.coerceIn(0.5f, 40f)?.dp,
            )
            "time" -> SkinBlock.Time(
                kind = when (o.string("show")?.lowercase()) {
                    "remaining", "left" -> TimeKind.Remaining
                    "length", "total", "duration" -> TimeKind.Length
                    else -> TimeKind.Elapsed
                },
                text = textOf(o, 13f, FontWeight.SemiBold, SkinColor.InkSoft),
            )
            "shader" -> shader(o)?.let { program ->
                SkinBlock.Shader(
                    program = program,
                    width = o.float("width")?.coerceIn(8f, 600f)?.dp,
                    height = (o.float("height") ?: 120f).coerceIn(8f, 800f).dp,
                    corner = (o.float("corner") ?: 0f).coerceIn(0f, 300f).dp,
                    fallback = skinColor(o.string("fallback")),
                )
            }
            "surface" -> surface(o)
            else -> null
        }

        // a panel can hold nothing at all and still be a coloured shape with its own height
        private fun box(o: JsonObject, depth: Int): SkinBlock? {
            if (depth >= MaxDepth) return null
            val inside = blocks(o["blocks"], depth + 1)
            val glass = when (val g = o["glass"]) {
                is JsonPrimitive -> g.booleanOrNull?.let { if (it) DefaultGlassBlur else null } ?: g.contentOrNull?.toFloatOrNull()
                is JsonObject -> g.float("blur") ?: DefaultGlassBlur
                else -> null
            }?.coerceIn(0f, SkinAnimationParser.MaxBlur)
            return SkinBlock.Box(
                blocks = inside,
                row = o.string("direction")?.lowercase() == "row",
                gap = (o.float("gap") ?: 8f).coerceIn(0f, 80f).dp,
                padding = (o.float("padding") ?: 16f).coerceIn(0f, 80f).dp,
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 300f).dp,
                fill = colors(o["colors"]) ?: listOfNotNull(skinColor(o.string("fill") ?: o.string("color"))),
                gradient = when (o.string("gradient")?.lowercase()) {
                    "horizontal", "across" -> GradientKind.Horizontal
                    "diagonal" -> GradientKind.Diagonal
                    "radial", "round" -> GradientKind.Radial
                    else -> GradientKind.Vertical
                },
                stroke = o.float("stroke")?.coerceIn(0.5f, 20f)?.dp,
                strokeColor = skinColor(o.string("strokeColor")) ?: SkinColor.Fixed(Color.White.copy(alpha = 0.18f)),
                glass = glass,
                width = o.float("width")?.coerceIn(8f, 600f)?.dp,
                height = o.float("height")?.coerceIn(8f, 800f)?.dp,
                shadow = o.bool("shadow") ?: false,
                target = target(o),
            )
        }

        // { "type": "queue", "limit": 5, "covers": true, "tap": "play" }: upcoming songs right in the player
        private fun queueList(o: JsonObject): SkinBlock = SkinBlock.QueueList(
            limit = (o.float("limit")?.toInt() ?: 4).coerceIn(1, 20),
            covers = o.bool("covers") ?: true,
            coverSize = (o.float("coverSize") ?: 40f).coerceIn(24f, 72f).dp,
            corner = (o.float("corner") ?: 10f).coerceIn(0f, 36f).dp,
            gap = (o.float("gap") ?: 10f).coerceIn(0f, 32f).dp,
            text = textOf(o, 14f, FontWeight.SemiBold, SkinColor.Ink),
            artistColor = skinColor(o.string("artistColor")) ?: SkinColor.InkSoft,
            times = o.bool("times") ?: false,
            numbers = o.bool("numbers") ?: false,
            tap = when (o.string("tap")?.lowercase()) {
                "queue", "open" -> QueueTap.Queue
                "none", "off", "false" -> QueueTap.None
                else -> QueueTap.Play
            },
            height = o.float("height")?.coerceIn(60f, 600f)?.dp,
            empty = o.string("empty")?.take(80),
        )

        // { "type": "volume", "style": "line", "color": "ink", "icons": true }
        private fun volume(o: JsonObject): SkinBlock = SkinBlock.Volume(
            style = when (o.string("style")?.lowercase()) {
                "thick" -> ProgressStyle.Thick
                "pill" -> ProgressStyle.Pill
                else -> ProgressStyle.Line
            },
            color = skinColor(o.string("color")) ?: SkinColor.Ink,
            track = skinColor(o.string("track")) ?: SkinColor.Chip,
            icons = o.bool("icons") ?: true,
        )

        // an image block can point at {cover}, {prevCover} or {nextCover}..{next10Cover} instead of a static file
        private fun pictureSource(raw: String): SkinPictureSource {
            val clean = raw.trim()
            if (clean.equals("{cover}", ignoreCase = true)) return SkinPictureSource.CurrentCover
            if (clean.equals("{prevCover}", ignoreCase = true)) return SkinPictureSource.QueueCover(-1)
            val next = NextCoverToken.matchEntire(clean)
            if (next != null) {
                val offset = next.groupValues[1].toIntOrNull() ?: 1
                return SkinPictureSource.QueueCover(offset)
            }
            return SkinPictureSource.Asset(raw)
        }

        // { "type": "bottomBar", "style": "cards", "glass": true } or "pills", or mochy's own "volume"
        private fun bottomBar(o: JsonObject): SkinBlock {
            val kind = when (o.string("style")?.lowercase()) {
                "pills", "pill", "buttons" -> BottomBarKind.Pills
                "volume" -> BottomBarKind.Volume
                else -> BottomBarKind.Cards
            }
            val glass = when (val g = o["glass"]) {
                is JsonPrimitive -> g.booleanOrNull?.let { if (it) DefaultGlassBlur else null } ?: g.contentOrNull?.toFloatOrNull()
                is JsonObject -> g.float("blur") ?: DefaultGlassBlur
                else -> null
            }?.coerceIn(0f, SkinAnimationParser.MaxBlur)
            return SkinBlock.BottomBar(
                kind = kind,
                height = (o.float("height") ?: if (kind == BottomBarKind.Pills) 48f else 76f).coerceIn(36f, 160f).dp,
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 80f).dp,
                fill = colors(o["colors"]) ?: listOfNotNull(skinColor(o.string("fill"))),
                glass = glass,
                color = skinColor(o.string("color")),
                gap = (o.float("gap") ?: 10f).coerceIn(0f, 40f).dp,
            )
        }

        // { "type": "path", "d": "M12 2 L22 22 H2 Z", "viewBox": "0 0 24 24", "size": 64, "fill": "accent" }
        private fun vector(o: JsonObject): SkinBlock? {
            val data = o.string("d")?.trim()?.takeIf { it.length <= MaxPathChars && PathChars.matches(it) } ?: return null
            val view = o.string("viewBox")?.split(' ', ',')?.mapNotNull { it.trim().toFloatOrNull() }.orEmpty()
            val viewWidth = view.getOrNull(view.size - 2)?.takeIf { it > 0f } ?: 24f
            val viewHeight = view.lastOrNull()?.takeIf { it > 0f && view.size >= 2 } ?: viewWidth
            val stroke = o.float("stroke")?.coerceIn(0.5f, 40f)?.dp
            // no fill at all with a stroke, "none" for an outline only, the ink when it says nothing
            val fill = colors(o["colors"]) ?: when {
                "fill" in o -> listOfNotNull(skinColor(o.string("fill")))
                stroke != null -> emptyList()
                else -> listOf(SkinColor.Ink)
            }
            return SkinBlock.Vector(
                data = data,
                viewWidth = viewWidth.coerceIn(1f, 4096f),
                viewHeight = viewHeight.coerceIn(1f, 4096f),
                width = o.float("width")?.coerceIn(4f, 600f)?.dp,
                height = (o.float("height") ?: o.float("size") ?: 48f).coerceIn(4f, 600f).dp,
                fill = fill,
                stroke = stroke,
                strokeColor = skinColor(o.string("strokeColor")) ?: SkinColor.Ink.takeIf { stroke != null },
            )
        }

        private fun group(o: JsonObject, depth: Int, make: (List<SkinBlock>, androidx.compose.ui.unit.Dp) -> SkinBlock): SkinBlock? {
            if (depth >= MaxDepth) return null
            val inside = blocks(o["blocks"], depth + 1).takeIf { it.isNotEmpty() } ?: return null
            return make(inside, (o.float("gap") ?: 12f).coerceIn(0f, 80f).dp)
        }

        // a shader is code the gpu runs, so it only comes as a file in a zip
        private fun shader(o: JsonObject): SkinShaderSource? {
            if (!fromZip) return null
            val file = o.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } } ?: return null
            return SkinShaderSource(file, shaderUniforms(o["uniforms"], PlayerUniforms))
        }

        // a live surface runs from a signed code module zip with optional unpacked data files
        private fun surface(o: JsonObject): SkinBlock.Surface? {
            if (!fromZip) return null
            val rawAsset = o.string("asset")
            val asset = if (rawAsset != null) safeSurfaceAsset(rawAsset) ?: return null else null
            val rawExtras = when (val extra = o["extras"] ?: o["files"]) {
                is JsonArray -> extra.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(extra.contentOrNull)
                else -> emptyList()
            }
            val extras = rawExtras.mapNotNull(::safeSurfaceAsset).distinct().take(4)
            val controls = when (val c = o["controls"]) {
                is JsonPrimitive -> when {
                    c.booleanOrNull == false -> SurfaceControls.None
                    c.booleanOrNull == true -> SurfaceControls.Below
                    else -> when (c.contentOrNull?.trim()?.lowercase()) {
                        "overlay", "over" -> SurfaceControls.Overlay
                        "none", "off", "false", "hidden" -> SurfaceControls.None
                        else -> SurfaceControls.Below
                    }
                }
                else -> SurfaceControls.Below
            }
            return SkinBlock.Surface(
                asset = asset,
                extras = extras,
                title = o.string("title")?.take(24),
                width = o.float("width")?.coerceIn(120f, 800f)?.dp,
                height = o.float("height")?.coerceIn(80f, 800f)?.dp,
                aspect = aspect(o["aspect"]) ?: (4f / 3f),
                corner = (o.float("corner") ?: 12f).coerceIn(0f, 48f).dp,
                controls = controls,
                crisp = o.bool("crisp") ?: true,
                accent = skinColor(o.string("accent")),
            )
        }

        // only relative paths with allowed surface asset extensions inside the module zip
        private fun safeSurfaceAsset(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 200 || trimmed.startsWith("/") || '\\' in trimmed || '\u0000' in trimmed || "://" in trimmed) return null
            val parts = trimmed.split('/')
            if (parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return null
            val ext = trimmed.substringAfterLast('.', "").lowercase()
            return trimmed.takeIf { ext in SkinFiles.SurfaceAssetKinds }
        }

        // the lyrics' fill and effect, "shader" or a plain file name, with the lyrics' own uniforms kept for mochy
        private fun lyricShader(element: JsonElement?): SkinShaderSource? {
            if (!fromZip) return null
            val o = (element as? JsonObject) ?: (element as? JsonPrimitive)?.contentOrNull?.let { JsonObject(mapOf("shader" to JsonPrimitive(it))) } ?: return null
            val file = o.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } } ?: return null
            return SkinShaderSource(file, shaderUniforms(o["uniforms"], LyricUniforms))
        }
    }

    private fun textOf(o: JsonObject, defaultSize: Float, defaultWeight: FontWeight, defaultColor: SkinColor) = SkinText(
        size = (o.float("size") ?: defaultSize).coerceIn(8f, 72f).sp,
        weight = skinWeight(o.string("weight")) ?: defaultWeight,
        color = skinColor(o.string("color")) ?: defaultColor,
        lines = (o.float("lines")?.toInt() ?: 1).coerceIn(1, 4),
        upper = o.bool("upper") ?: false,
        align = skinAlign(o.string("align")),
        wrap = o.bool("wrap") ?: false,
        spacing = (o.float("spacing") ?: 0f).coerceIn(-0.1f, 1f),
    )

    private fun colors(element: JsonElement?): List<SkinColor>? =
        (element as? JsonArray).orEmpty().mapNotNull { skinColor((it as? JsonPrimitive)?.contentOrNull) }.take(6).takeIf { it.size >= 2 }

    // "animate": "float" or { "type": "float", "speed": 1, "amount": 1 }
    fun motion(element: JsonElement?): SkinMotion? {
        val o = element as? JsonObject
        val name = (element as? JsonPrimitive)?.contentOrNull ?: o?.string("type") ?: return null
        val kind = when (name.trim().lowercase()) {
            "float" -> MotionKind.Float
            "sway" -> MotionKind.Sway
            "breathe" -> MotionKind.Breathe
            "pulse" -> MotionKind.Pulse
            "spin" -> MotionKind.Spin
            "beat" -> MotionKind.Beat
            "drift" -> MotionKind.Drift
            "zoom" -> MotionKind.Zoom
            "shake" -> MotionKind.Shake
            else -> return null
        }
        return SkinMotion(kind, (o?.float("speed") ?: 1f).coerceIn(0.1f, 4f), (o?.float("amount") ?: 1f).coerceIn(0f, 3f))
    }

    // "enter": "rise" or { "type": "rise", "delay": 120, "duration": 500, "on": "song" }
    private fun presetEnter(element: JsonElement?): SkinEnter? {
        val o = element as? JsonObject
        val name = (element as? JsonPrimitive)?.contentOrNull ?: o?.string("type") ?: return null
        val kind = when (name.trim().lowercase()) {
            "fade" -> EnterKind.Fade
            "rise", "up" -> EnterKind.Rise
            "drop", "down" -> EnterKind.Drop
            "zoom", "pop" -> EnterKind.Zoom
            "slidestart", "slideleft" -> EnterKind.SlideStart
            "slideend", "slideright" -> EnterKind.SlideEnd
            else -> return null
        }
        return SkinEnter(
            kind = kind,
            delayMs = (o?.float("delay") ?: 0f).coerceIn(0f, 3000f).toInt(),
            durationMs = (o?.float("duration") ?: 450f).coerceIn(100f, 3000f).toInt(),
            eachSong = o?.string("on")?.lowercase() == "song",
        )
    }

    private fun buttonKind(raw: String?): ButtonKind? = when (raw?.trim()?.lowercase()) {
        "playpause", "play" -> ButtonKind.PlayPause
        "previous", "prev" -> ButtonKind.Previous
        "next" -> ButtonKind.Next
        "like" -> ButtonKind.Like
        "shuffle" -> ButtonKind.Shuffle
        "repeat" -> ButtonKind.Repeat
        "lyrics" -> ButtonKind.Lyrics
        "queue" -> ButtonKind.Queue
        "forward", "forward10", "seekforward" -> ButtonKind.Forward
        "rewind", "back10", "replay10", "seekback" -> ButtonKind.Rewind
        "othermenu", "more" -> ButtonKind.OtherMenu
        "artist" -> ButtonKind.Artist
        "album" -> ButtonKind.Album
        "menu" -> ButtonKind.Menu
        "customise", "customize" -> ButtonKind.Customise
        "close" -> ButtonKind.Close
        "editor", "edit" -> ButtonKind.Editor
        else -> null
    }

    private fun stackAlign(raw: String?): StackAlign = when (raw?.trim()?.lowercase()) {
        "top" -> StackAlign.Top
        "bottom" -> StackAlign.Bottom
        "start", "left" -> StackAlign.Start
        "end", "right" -> StackAlign.End
        else -> StackAlign.Center
    }

    private val Ambient = SkinBackground.Player(PlayerBackgroundStyle.AMBIENT)
    private val BlurredCover = SkinBackground.Player(PlayerBackgroundStyle.APPLE_MUSIC)
    private val ShaderKinds = listOf(".agsl", ".sksl")
    // the loops a cover can play: videos mochy streams itself, and pictures android animates
    private val LoopFile = Regex("""\.(mp4|webm|m3u8|gif|webp)$""", RegexOption.IGNORE_CASE)
    private const val MaxMedia = 4
    private const val MaxBlocks = 40
    private const val MaxDepth = 4
    private const val MaxLoops = 4
    private const val DefaultLoopMs = 2000
    private const val DefaultEnterMs = 600
    private const val DefaultGlassBlur = 24f
    private const val MaxPathChars = 8000
    // svg path commands and numbers, nothing else
    private val PathChars = Regex("""[MmLlHhVvCcSsQqTtAaZz0-9eE.,+\-\s]+""")
    private const val MaxLayers = 4
    private const val MaxCustomise = 12
    private const val MaxStyles = 40
    private const val MaxMenu = 4
    private const val MaxHeader = 8
    private const val MaxPlayers = 6
    private const val MaxEditParts = 8
    private const val MaxVariants = 8
    private const val MaxVariantValues = 24
    private val PlayerId = Regex("[a-z0-9_-]{1,40}")
    // matches {nextCover} and {next1Cover}..{next10Cover}, with both braces escaped for android's regex engine
    private val NextCoverToken = Regex("""\{next([1-9]|10)?cover\}""", RegexOption.IGNORE_CASE)
    // a sub-style adds to these by name instead of replacing them whole
    private val MergedKeys = listOf("styles", "keyframes")
    // what mochy hands every player shader itself
    val PlayerUniforms = setOf(
        "size", "time", "progress", "level", "bass", "mid", "treble", "playing", "density", "played", "remaining", "cover", "accent",
        "line", "lineCount", "lineProgress", "wordProgress", "singing", "position", "duration",
    )
    // the lyrics' shaders get the player's and their own: how far a line is from the sung one, and the drawn words
    val LyricUniforms = PlayerUniforms + setOf("lineOffset", "content")
}
