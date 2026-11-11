// turns a manifest's player, font, glass, pages and menu rows into a skin, every value checked and kept in range so a typo can't break anything

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

internal object SkinParser {

    /** null when the manifest brings none of these, or none of them made sense */
    fun parse(
        moduleId: String,
        moduleName: String,
        player: JsonElement?,
        font: JsonElement?,
        pages: JsonElement?,
        files: File?,
        base: String?,
        actions: JsonElement? = null,
        glass: JsonElement? = null,
    ): ModuleSkin? {
        val parsedPlayer = (player as? JsonObject)?.let { SkinPlayerParser.player(it, moduleName, fromZip = files != null) }
        // a font has to be a file on the phone, so only a zip can bring one
        val parsedFont = (font as? JsonObject)?.let(::font)?.takeIf { files != null }
        val parsedPages = (pages as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> page(o, fromZip = files != null) } }.distinctBy { it.id }.take(MaxPages)
        val parsedActions = (actions as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::action) }.take(MaxActions)
        // a shader is code the gpu runs, so it only comes as a file in a zip, the same as a font
        val parsedGlass = (glass as? JsonObject)?.let(::glass)?.takeIf { files != null }
        if (parsedPlayer == null && parsedFont == null && parsedPages.isEmpty() && parsedActions.isEmpty() && parsedGlass == null) return null
        return ModuleSkin(moduleId, moduleName, parsedPlayer, parsedFont, parsedPages, files, base, parsedActions, parsedGlass)
    }

    // ---- the bottom bar's glass -----------------------------------------------------------------

    private fun glass(o: JsonObject): SkinGlass? {
        val shader = o.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } } ?: return null
        val change = glassChange(o)
        return SkinGlass(shader, change.blurDp ?: 6f, change.marginDp ?: 16f, change.uniforms)
    }

    /** a blur, a margin and uniforms, as the glass and a setting's option both write them; mochy's own uniforms can't be set this way */
    fun glassChange(o: JsonObject): GlassChange = GlassChange(
        blurDp = o.float("blur")?.coerceIn(0f, 40f),
        marginDp = o.float("margin")?.coerceIn(0f, 64f),
        uniforms = shaderUniforms(o["uniforms"], MochyUniforms),
    )

    private fun font(o: JsonObject): SkinFont? = o.string("regular")?.let { SkinFont(regular = it, bold = o.string("bold")) }

    // ---- pages --------------------------------------------------------------------------------

    private fun page(o: JsonObject, fromZip: Boolean): SkinPage? {
        val id = o.string("id")?.lowercase()?.takeIf { PageId.matches(it) } ?: return null
        val blocks = pageBlocks(o["blocks"], fromZip, nested = false)
        if (blocks.isEmpty()) return null
        return SkinPage(
            id = id,
            title = o.string("title")?.take(60) ?: id,
            blocks = blocks,
            inSettings = o.bool("inSettings") ?: o.bool("settings") ?: false,
            subtitle = o.string("subtitle")?.take(80),
            icon = o.string("icon"),
            background = (o["background"] as? JsonObject)?.let { pageBackground(it, fromZip) },
        )
    }

    private fun pageBlocks(element: JsonElement?, fromZip: Boolean, nested: Boolean): List<PageBlock> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> pageBlock(o, fromZip, nested) } }.take(MaxPageBlocks)

    // { "type": "shader", "shader": "page.agsl", "fallback": ["#101020", "#000000"] } or an image or colours
    private fun pageBackground(o: JsonObject, fromZip: Boolean): PageBackground? {
        val colors = pageColors(o)
        return when (o.string("type")?.lowercase()) {
            "image" -> o.string("image")?.let {
                PageBackground.Picture(it, (o.float("blur") ?: 0f).coerceIn(0f, 60f), (o.float("dim") ?: 0.35f).coerceIn(0f, 0.9f))
            }
            "shader" -> pageShader(o, fromZip)?.let { PageBackground.Shader(it, colors.ifEmpty { listOf(SkinColor.Fixed(Color.Black)) }) }
                ?: colors.takeIf { it.isNotEmpty() }?.let(PageBackground::Fill)
            else -> colors.takeIf { it.isNotEmpty() }?.let(PageBackground::Fill)
        }
    }

    // "colors": [...] or one "color" (a shader's "fallback" reads the same way)
    private fun pageColors(o: JsonObject): List<SkinColor> {
        val list = (o["colors"] as? JsonArray) ?: (o["fallback"] as? JsonArray)
        return list?.mapNotNull { skinColor((it as? JsonPrimitive)?.content) }?.take(6)
            ?: listOfNotNull(skinColor(o.string("color") ?: o.string("fallback")))
    }

    // a shader is code the gpu runs, so it only comes as a file in a zip
    private fun pageShader(o: JsonObject, fromZip: Boolean): SkinShaderSource? {
        if (!fromZip) return null
        val file = o.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } } ?: return null
        return SkinShaderSource(file, shaderUniforms(o["uniforms"], PageUniforms))
    }

    private fun pageBlock(o: JsonObject, fromZip: Boolean, nested: Boolean): PageBlock? = when (o.string("type")?.lowercase()) {
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
        "setting" -> o.string("key")?.take(60)?.let(PageBlock::Setting)
        // a card holds plain blocks, never another card
        "card", "box", "panel" -> if (nested) null else pageBlocks(o["blocks"], fromZip, nested = true).takeIf { it.isNotEmpty() }?.let { inside ->
            val glass = when (val g = o["glass"]) {
                is JsonPrimitive -> if (g.content.equals("true", ignoreCase = true)) DefaultCardGlass else g.content.toFloatOrNull()
                is JsonObject -> g.float("blur") ?: DefaultCardGlass
                else -> null
            }?.coerceIn(0f, 60f)
            PageBlock.Card(
                blocks = inside,
                colors = pageColors(o),
                glass = glass,
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 80f).dp,
                padding = (o.float("padding") ?: 16f).coerceIn(0f, 60f).dp,
            )
        }
        "shader" -> pageShader(o, fromZip)?.let {
            PageBlock.Shader(
                program = it,
                height = (o.float("height") ?: 180f).coerceIn(24f, 800f).dp,
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 120f).dp,
                fallback = skinColor(o.string("fallback")),
            )
        }
        else -> null
    }

    // ---- the player's menu ----------------------------------------------------------------------

    // a link only to the web, a page by its id, or a search; the song's {title}, {artist}, {album} and {id} are filled in when it's tapped
    private fun action(o: JsonObject): SkinAction? {
        val title = o.string("title")?.take(40) ?: return null
        return actionTarget(o)?.let { SkinAction(title, o.string("icon"), it) }
    }

    /** a "link" (only to the web, never another app's scheme), a "page" by its id, or a "search" */
    fun actionTarget(o: JsonObject): ActionTarget? =
        o.string("link")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(ActionTarget::Link)
            ?: o.string("page")?.lowercase()?.takeIf { PageId.matches(it) }?.let(ActionTarget::Page)
            ?: o.string("search")?.take(120)?.let(ActionTarget::Search)

    private val PageId = Regex("[a-z0-9_-]{1,40}")
    private const val MaxPages = 12
    private const val MaxPageBlocks = 80
    private const val MaxActions = 6
    private val ShaderKinds = listOf(".agsl", ".sksl")
    private const val DefaultCardGlass = 24f
    // what mochy hands every page shader itself
    val PageUniforms = setOf("size", "time", "density", "touch", "pressed", "accent")
    // what mochy hands every glass shader itself
    val MochyUniforms = setOf("content", "size", "margin", "radius", "density", "quality", "chrome", "accent", "kind")
}
