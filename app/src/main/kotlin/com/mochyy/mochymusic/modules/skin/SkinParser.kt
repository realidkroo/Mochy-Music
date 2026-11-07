// turns a manifest's player, font, glass, pages and menu rows into a skin, every value checked and kept in range so a typo can't break anything

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        val parsedPages = (pages as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::page) }.distinctBy { it.id }.take(MaxPages)
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

    private fun page(o: JsonObject): SkinPage? {
        val id = o.string("id")?.lowercase()?.takeIf { PageId.matches(it) } ?: return null
        val blocks = (o["blocks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::pageBlock) }.take(MaxPageBlocks)
        if (blocks.isEmpty()) return null
        return SkinPage(
            id = id,
            title = o.string("title")?.take(60) ?: id,
            blocks = blocks,
            inSettings = o.bool("inSettings") ?: o.bool("settings") ?: false,
            subtitle = o.string("subtitle")?.take(80),
            icon = o.string("icon"),
        )
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
        "setting" -> o.string("key")?.take(60)?.let(PageBlock::Setting)
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
    // what mochy hands every glass shader itself
    val MochyUniforms = setOf("content", "size", "margin", "radius", "density", "quality", "chrome")
}
