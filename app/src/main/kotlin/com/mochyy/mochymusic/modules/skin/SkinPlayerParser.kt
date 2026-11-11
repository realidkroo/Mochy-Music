// turns a manifest's player into a layout: backgrounds, blocks, loops, keyframes, states, entrances and settings that switch blocks on and off, every number kept in range

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
        )
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
            else -> Ambient
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
            val opacity = (o.float("opacity") ?: 1f).coerceIn(0f, 1f)
            val x = (o.float("x") ?: 0f).coerceIn(-400f, 400f)
            val y = (o.float("y") ?: 0f).coerceIn(-400f, 400f)
            val rotate = (o.float("rotate") ?: 0f).coerceIn(-180f, 180f)
            val scale = (o.float("scale") ?: 1f).coerceIn(0f, 5f)
            val blur = (o.float("blur") ?: 0f).coerceIn(0f, SkinAnimationParser.MaxBlur)
            val (originX, originY) = origin(o)
            val plain = motions.isEmpty() && timelines.isEmpty() && enter == null && condition == null && states.isEmpty() && triggers.isEmpty() &&
                opacity == 1f && x == 0f && y == 0f && rotate == 0f && scale == 1f && blur == 0f
            if (plain) return inner
            return SkinBlock.Styled(
                inner, motions, enter, condition, opacity, x.dp, y.dp, rotate,
                scale = scale, blur = blur, originX = originX, originY = originY,
                timelines = timelines, states = states, transition = transition, triggers = triggers,
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
            )
            "title" -> SkinBlock.Title(textOf(o, 24f, FontWeight.Bold, SkinColor.Ink))
            "artist" -> SkinBlock.Artist(textOf(o, 16f, FontWeight.Medium, SkinColor.InkSoft))
            "album" -> SkinBlock.Album(textOf(o, 14f, FontWeight.Medium, SkinColor.InkSoft))
            "text" -> o.string("text")?.take(200)?.let { SkinBlock.Label(it, textOf(o, 14f, FontWeight.Medium, SkinColor.InkSoft)) }
            "image" -> o.string("image")?.let {
                SkinBlock.Picture(
                    image = it,
                    width = o.float("width")?.coerceIn(8f, 600f)?.dp,
                    height = (o.float("height") ?: 120f).coerceIn(8f, 600f).dp,
                    corner = (o.float("corner") ?: 12f).coerceIn(0f, 80f).dp,
                    fit = o.string("fit")?.lowercase() == "fit",
                    tint = skinColor(o.string("tint")),
                )
            }
            "box", "panel", "card" -> box(o, depth)
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
            "lyrics" -> SkinBlock.Lyrics(
                height = (o.float("height") ?: 160f).coerceIn(60f, 600f).dp,
                size = (o.float("size") ?: 28f).coerceIn(14f, 60f).sp,
                color = skinColor(o.string("color")) ?: SkinColor.Ink,
            )
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
    }

    private fun textOf(o: JsonObject, defaultSize: Float, defaultWeight: FontWeight, defaultColor: SkinColor) = SkinText(
        size = (o.float("size") ?: defaultSize).coerceIn(8f, 72f).sp,
        weight = skinWeight(o.string("weight")) ?: defaultWeight,
        color = skinColor(o.string("color")) ?: defaultColor,
        lines = (o.float("lines")?.toInt() ?: 1).coerceIn(1, 4),
        upper = o.bool("upper") ?: false,
        align = skinAlign(o.string("align")),
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
        "menu" -> ButtonKind.Menu
        "customise", "customize" -> ButtonKind.Customise
        "close" -> ButtonKind.Close
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
    private val ShaderKinds = listOf(".agsl", ".sksl")
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
    // what mochy hands every player shader itself
    val PlayerUniforms = setOf("size", "time", "progress", "level", "bass", "mid", "treble", "playing", "density", "played", "remaining", "cover", "accent")
}
