// turns a manifest's player, font and pages into a skin, every value checked and kept in range so a typo can't break the player

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
import kotlinx.serialization.json.floatOrNull
import java.io.File

internal object SkinParser {

    /** null when the manifest brings none of the three, or none of them made sense */
    fun parse(
        moduleId: String,
        moduleName: String,
        player: JsonElement?,
        font: JsonElement?,
        pages: JsonElement?,
        files: File?,
        base: String?,
    ): ModuleSkin? {
        val parsedPlayer = (player as? JsonObject)?.let { player(it, moduleName) }
        // a font has to be a file on the phone, so only a zip can bring one
        val parsedFont = (font as? JsonObject)?.let(::font)?.takeIf { files != null }
        val parsedPages = (pages as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::page) }.distinctBy { it.id }.take(MaxPages)
        if (parsedPlayer == null && parsedFont == null && parsedPages.isEmpty()) return null
        return ModuleSkin(moduleId, moduleName, parsedPlayer, parsedFont, parsedPages, files, base)
    }

    // ---- the player ---------------------------------------------------------------------------

    private fun player(o: JsonObject, fallbackName: String): SkinPlayer? {
        val blocks = blocks(o["blocks"], depth = 0)
        if (blocks.isEmpty()) return null
        val background = o["background"] as? JsonObject
        return SkinPlayer(
            name = o.string("name")?.take(40) ?: fallbackName,
            background = background(background),
            dim = background?.float("dim")?.coerceIn(0f, 0.9f) ?: 0f,
            padding = (o.float("padding") ?: 24f).coerceIn(0f, 64f).dp,
            align = align(o.string("align")) ?: SkinAlign.Center,
            blocks = blocks,
        )
    }

    private fun background(o: JsonObject?): SkinBackground = when (o?.string("type")?.lowercase()) {
        "blurredcover", "cover" -> SkinBackground.Player(PlayerBackgroundStyle.APPLE_MUSIC)
        "darkgradient" -> SkinBackground.Player(PlayerBackgroundStyle.DARK_GRADIENT)
        "solid" -> SkinBackground.Player(PlayerBackgroundStyle.SOLID)
        "tones" -> SkinBackground.Tones
        "color" -> SkinBackground.Fill(listOf(color(o.string("color")) ?: SkinColor.Fixed(Color.Black)))
        "gradient" -> (o["colors"] as? JsonArray).orEmpty().mapNotNull { color((it as? JsonPrimitive)?.contentOrNull) }.take(6)
            .takeIf { it.size >= 2 }?.let(SkinBackground::Fill) ?: Ambient
        "image" -> o.string("image")?.let(SkinBackground::Picture) ?: Ambient
        else -> Ambient
    }

    private fun blocks(element: JsonElement?, depth: Int): List<SkinBlock> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> block(o, depth) } }.take(MaxBlocks)

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
            )
        }
        "progress" -> SkinBlock.Progress(
            style = when (o.string("style")?.lowercase()) {
                "thick" -> ProgressStyle.Thick
                "pill" -> ProgressStyle.Pill
                else -> ProgressStyle.Line
            },
            color = color(o.string("color")) ?: SkinColor.Ink,
            track = color(o.string("track")) ?: SkinColor.Chip,
            times = o.bool("times") ?: true,
            seek = o.bool("seek") ?: true,
        )
        "controls" -> SkinBlock.Controls(
            size = (o.float("size") ?: 48f).coerceIn(28f, 96f).dp,
            plain = o.string("style")?.lowercase() == "plain",
            color = color(o.string("color")),
            icon = color(o.string("icon")),
        )
        "lyrics" -> SkinBlock.Lyrics(
            height = (o.float("height") ?: 160f).coerceIn(60f, 600f).dp,
            size = (o.float("size") ?: 28f).coerceIn(14f, 60f).sp,
            color = color(o.string("color")) ?: SkinColor.Ink,
        )
        "space" -> SkinBlock.Space(size = (o.float("size") ?: 16f).coerceIn(0f, 300f).dp, grow = o.bool("grow") ?: false)
        "row" -> if (depth < MaxDepth) blocks(o["blocks"], depth + 1).takeIf { it.isNotEmpty() }?.let { SkinBlock.Row(it, gap(o)) } else null
        "column" -> if (depth < MaxDepth) blocks(o["blocks"], depth + 1).takeIf { it.isNotEmpty() }?.let { SkinBlock.Column(it, gap(o)) } else null
        else -> null
    }

    private fun gap(o: JsonObject) = (o.float("gap") ?: 12f).coerceIn(0f, 80f).dp

    private fun textOf(o: JsonObject, defaultSize: Float, defaultWeight: FontWeight, defaultColor: SkinColor) = SkinText(
        size = (o.float("size") ?: defaultSize).coerceIn(8f, 72f).sp,
        weight = weight(o.string("weight")) ?: defaultWeight,
        color = color(o.string("color")) ?: defaultColor,
        lines = (o.float("lines")?.toInt() ?: 1).coerceIn(1, 4),
        upper = o.bool("upper") ?: false,
        align = align(o.string("align")),
    )

    private fun font(o: JsonObject): SkinFont? = o.string("regular")?.let { SkinFont(regular = it, bold = o.string("bold")) }

    // ---- pages --------------------------------------------------------------------------------

    private fun page(o: JsonObject): SkinPage? {
        val id = o.string("id")?.lowercase()?.takeIf { PageId.matches(it) } ?: return null
        val blocks = (o["blocks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::pageBlock) }.take(MaxPageBlocks)
        if (blocks.isEmpty()) return null
        return SkinPage(id, o.string("title")?.take(60) ?: id, blocks)
    }

    private fun pageBlock(o: JsonObject): PageBlock? = when (o.string("type")?.lowercase()) {
        "heading" -> o.string("text")?.take(120)?.let(PageBlock::Heading)
        "text" -> o.string("text")?.take(2000)?.let(PageBlock::Paragraph)
        "image" -> o.string("image")?.let {
            PageBlock.Picture(it, (o.float("height") ?: 160f).coerceIn(40f, 400f).dp, (o.float("corner") ?: 16f).coerceIn(0f, 80f).dp)
        }
        // only a web link: a page can send someone to a site, never into another app's scheme
        "button" -> {
            val link = o.string("link")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val text = o.string("text")?.take(60)
            if (link != null && text != null) PageBlock.Button(text, link) else null
        }
        "divider" -> PageBlock.Divider
        "space" -> PageBlock.Space((o.float("size") ?: 16f).coerceIn(0f, 120f).dp)
        "song" -> o.string("id")?.let { PageBlock.Song(it, o.string("title")?.take(80) ?: it, o.string("artist")?.take(80)) }
        "playlist" -> o.string("id")?.let { PageBlock.Playlist(it, o.string("title")?.take(80) ?: it, o.string("subtitle")?.take(80)) }
        "album" -> o.string("id")?.let { PageBlock.Album(it, o.string("title")?.take(80) ?: it, o.string("subtitle")?.take(80)) }
        "artist" -> o.string("id")?.let { PageBlock.Artist(it, o.string("title")?.take(80) ?: it) }
        "search" -> o.string("query")?.take(120)?.let { PageBlock.Search(it, o.string("title")?.take(80) ?: it) }
        else -> null
    }

    // ---- values -------------------------------------------------------------------------------

    fun color(raw: String?): SkinColor? = when (raw?.trim()?.lowercase()) {
        null, "" -> null
        "ink", "text" -> SkinColor.Ink
        "inksoft", "textsoft" -> SkinColor.InkSoft
        "played" -> SkinColor.Played
        "remaining" -> SkinColor.Remaining
        "chip" -> SkinColor.Chip
        "cover" -> SkinColor.Cover
        "accent" -> SkinColor.Accent
        else -> parseHexColor(raw)?.let(SkinColor::Fixed)
    }

    private fun weight(raw: String?): FontWeight? = when (raw?.trim()?.lowercase()) {
        "thin" -> FontWeight.Thin
        "light" -> FontWeight.Light
        "normal", "regular" -> FontWeight.Normal
        "medium" -> FontWeight.Medium
        "semibold" -> FontWeight.SemiBold
        "bold" -> FontWeight.Bold
        "black" -> FontWeight.Black
        else -> null
    }

    private fun align(raw: String?): SkinAlign? = when (raw?.trim()?.lowercase()) {
        "start", "left" -> SkinAlign.Start
        "center", "centre" -> SkinAlign.Center
        "end", "right" -> SkinAlign.End
        else -> null
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.float(name: String): Float? = (this[name] as? JsonPrimitive)?.let { it.floatOrNull ?: it.contentOrNull?.trim()?.toFloatOrNull() }

    private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.trim()?.lowercase()?.toBooleanStrictOrNull() }

    private val Ambient = SkinBackground.Player(PlayerBackgroundStyle.AMBIENT)
    private val PageId = Regex("[a-z0-9_-]{1,40}")
    private const val MaxBlocks = 40
    private const val MaxDepth = 3
    private const val MaxPages = 12
    private const val MaxPageBlocks = 80
}

/** "#RRGGBB" or "#AARRGGBB", the way skins and the accent colour write a colour */
fun parseHexColor(raw: String?): Color? {
    val digits = raw?.trim()?.removePrefix("#") ?: return null
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}
