// turns a manifest's players, font, glass, nav bar, pages and menu rows into a skin, every value checked and kept in range so a typo can't break anything

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.module.bridge.shared.SurfaceBridge
import com.mochyy.mochymusic.modules.security.ModuleSanitizer
import com.mochyy.mochymusic.modules.security.ModuleUrlGuard
import com.mochyy.mochymusic.modules.security.SafeAppSettings
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
        players: JsonElement? = null,
        bar: JsonElement? = null,
        mini: JsonElement? = null,
        surface: SurfaceBridge? = null,
        floating: JsonElement? = null,
        background: JsonElement? = null,
        appBackground: JsonElement? = null,
        sheet: JsonElement? = null,
        menu: JsonElement? = null,
        sheets: JsonElement? = null,
    ): ModuleSkin? {
        val fromZip = files != null
        val parsedPlayers = SkinPlayerParser.players(player, players, moduleName, fromZip = fromZip)
        // a font has to be a file on the phone, so only a zip can bring one
        val parsedFont = (font as? JsonObject)?.let(::font)?.takeIf { fromZip }
        val parsedPages = (pages as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> page(o, fromZip = fromZip) } }.distinctBy { it.id }.take(MaxPages)
        val parsedActions = (actions as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::action) }.take(MaxActions)
        // a shader is code the gpu runs, so it only comes as a file in a zip, the same as a font
        val parsedGlass = (glass as? JsonObject)?.let(::glass)?.takeIf { fromZip }
        val parsedBar = (bar as? JsonObject)?.let { SkinBarParser.bar(it, fromZip = fromZip) }
        val parsedMini = (mini as? JsonObject)?.let { SkinPlayerParser.mini(it, moduleName, fromZip = fromZip) }
        val parsedFloating = SkinExtrasParser.floating(floating)
        val parsedSheet = SkinExtrasParser.sheetStyle(sheet)
        val parsedAppBg = SkinExtrasParser.appBackground(appBackground ?: background, fromZip, ::pageBackground)
        val parsedMenuStyle = SkinExtrasParser.menuStyle(menu)
        // "sheets": { "eq": { "look": "own", "blocks": [...] } }, each by an id the callers name
        val parsedSheets = (sheets as? JsonObject).orEmpty().entries
            .mapNotNull { (id, o) -> id.trim().lowercase().takeIf { PageId.matches(it) }?.let { key -> (o as? JsonObject)?.let { SkinPlayerParser.sheet(key, it, moduleName, fromZip) } } }
            .distinctBy { it.id }
            .take(MaxSheets)
        if (parsedPlayers.isEmpty() && parsedFont == null && parsedPages.isEmpty() && parsedActions.isEmpty() &&
            parsedGlass == null && parsedBar == null && parsedMini == null &&
            parsedFloating == null && parsedSheet == null && parsedAppBg == null && parsedMenuStyle == null && parsedSheets.isEmpty()
        ) return null
        return ModuleSkin(
            moduleId = moduleId,
            moduleName = moduleName,
            players = parsedPlayers,
            font = parsedFont,
            pages = parsedPages,
            files = files,
            base = base,
            actions = parsedActions,
            glass = parsedGlass,
            bar = parsedBar,
            mini = parsedMini,
            surface = surface,
            floating = parsedFloating,
            sheet = parsedSheet,
            appBackground = parsedAppBg,
            menuStyle = parsedMenuStyle,
            sheets = parsedSheets,
        )
    }

    // ---- the bottom bar's glass -----------------------------------------------------------------

    private fun glass(o: JsonObject): SkinGlass? {
        val shader = o.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } } ?: return null
        val change = glassChange(o)
        // a glass that draws its own edge can turn off mochy's hairline and shadow, or the bars get two edges
        return SkinGlass(
            shader = shader,
            blurDp = change.blurDp ?: 6f,
            marginDp = change.marginDp ?: 16f,
            uniforms = change.uniforms,
            outline = o.bool("outline") ?: true,
            shadow = o.bool("shadow") ?: true,
            fallback = glassFallback(o["fallback"]),
        )
    }

    // "fallback": "#99131313" or { "tint": "#88131313", "rim": "#40FFFFFF", "sheen": 0.12 } for api 24-28 or blur-off
    private fun glassFallback(element: JsonElement?): SkinGlassFallback? = when (element) {
        is JsonPrimitive -> {
            val raw = element.contentOrNull?.trim()
            if (raw.isNullOrEmpty() || raw.equals("frosted", ignoreCase = true) || raw.equals("false", ignoreCase = true)) null
            else skinColor(raw)?.let { SkinGlassFallback(tint = it) }
        }
        is JsonObject -> {
            val tint = skinColor(element.string("tint") ?: element.string("color"))
            val rim = skinColor(element.string("rim") ?: element.string("outlineColor"))
            val sheen = (element.float("sheen") ?: 0.08f).coerceIn(0f, 0.4f)
            if (tint == null && rim == null && element["sheen"] == null) null else SkinGlassFallback(tint, rim, sheen)
        }
        else -> null
    }

    /** a blur, a margin and uniforms, as the glass and a setting's option both write them; mochy's own uniforms can't be set this way */
    fun glassChange(o: JsonObject): GlassChange = GlassChange(
        blurDp = o.float("blur")?.coerceIn(0f, 40f),
        marginDp = o.float("margin")?.coerceIn(0f, 64f),
        uniforms = shaderUniforms(o["uniforms"], MochyUniforms),
    )

    // "scope": "app" puts the font everywhere; left out, it stays on the module's player
    private fun font(o: JsonObject): SkinFont? = o.string("regular")?.let {
        SkinFont(regular = it, bold = o.string("bold"), wholeApp = o.string("scope")?.lowercase() == "app")
    }

    // ---- pages --------------------------------------------------------------------------------

    private fun page(o: JsonObject, fromZip: Boolean): SkinPage? {
        val id = o.string("id")?.lowercase()?.takeIf { PageId.matches(it) } ?: return null
        val blocks = pageBlocks(o["blocks"], fromZip, depth = 0)
        if (blocks.isEmpty()) return null
        val bgRaw = o["background"]
        val bgObjects = when (bgRaw) {
            is JsonArray -> bgRaw.mapNotNull { it as? JsonObject }.take(MaxPageLayers + 1)
            is JsonObject -> listOf(bgRaw)
            else -> emptyList()
        }
        val mainBg = bgObjects.firstOrNull()?.let { pageBackground(it, fromZip) }
        val extraLayers = bgObjects.drop(1).mapNotNull { layerObj ->
            pageBackground(layerObj, fromZip)?.let { PageBgLayer(it, (layerObj.float("opacity") ?: 1f).coerceIn(0.05f, 1f)) }
        } + (o["layers"] as? JsonArray).orEmpty().take(MaxPageLayers).mapNotNull { item ->
            val layerObj = item as? JsonObject ?: return@mapNotNull null
            pageBackground(layerObj, fromZip)?.let { PageBgLayer(it, (layerObj.float("opacity") ?: 1f).coerceIn(0.05f, 1f)) }
        }
        return SkinPage(
            id = id,
            title = o.string("title")?.take(60) ?: id,
            blocks = blocks,
            inSettings = o.bool("inSettings") ?: o.bool("settings") ?: false,
            subtitle = o.string("subtitle")?.take(80),
            icon = o.string("icon"),
            background = mainBg,
            replaces = replaces(o["replaces"] ?: o["takeover"]),
            condition = o.string("when")?.let(SkinAnimationParser::condition),
            size = SkinSize.of(o),
            header = o.bool("header") ?: true,
            layers = extraLayers.take(MaxPageLayers),
        )
    }

    // "album" or ["local_playlist", "online_playlist"]: the names of mochy's pages this one opens in place of
    private fun replaces(element: JsonElement?): List<String> {
        val list = (element as? JsonArray)?.toList() ?: listOfNotNull(element)
        return list.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.takeIf { name -> PageName.matches(name) } }.distinct().take(MaxReplaces)
    }

    private fun pageBlocks(element: JsonElement?, fromZip: Boolean, depth: Int, inCard: Boolean = false): List<PageBlock> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> conditionalPageBlock(o, fromZip, depth, inCard) } }.take(MaxPageBlocks)

    private fun conditionalPageBlock(o: JsonObject, fromZip: Boolean, depth: Int, inCard: Boolean): PageBlock? {
        val inner = pageBlock(o, fromZip, depth, inCard) ?: return null
        val condition = o.string("when")?.let(SkinAnimationParser::condition) ?: return inner
        return PageBlock.Conditional(inner, condition)
    }

    // { "type": "shader", "shader": "page.agsl", "fallback": ["#101020", "#000000"] } or an image, live cover, ambient, or colours
    internal fun pageBackground(o: JsonObject, fromZip: Boolean): PageBackground? {
        val colors = pageColors(o)
        val gradient = SkinExtrasParser.gradientKind(o.string("gradient"))
        return when (o.string("type")?.lowercase()?.replace("_", "")?.replace("-", "")) {
            "image", "picture" -> o.string("image")?.let {
                PageBackground.Picture(it, (o.float("blur") ?: 0f).coerceIn(0f, 60f), (o.float("dim") ?: 0.35f).coerceIn(0f, 0.9f))
            }
            "subjectcover", "cover", "blurredcover" -> PageBackground.SubjectCover(
                blur = (o.float("blur") ?: 36f).coerceIn(0f, 60f),
                dim = (o.float("dim") ?: 0.45f).coerceIn(0f, 0.9f),
            )
            "playingcover", "nowplayingcover", "currentcover" -> PageBackground.PlayingCover(
                blur = (o.float("blur") ?: 36f).coerceIn(0f, 60f),
                dim = (o.float("dim") ?: 0.45f).coerceIn(0f, 0.9f),
            )
            "ambient" -> PageBackground.Ambient(dim = (o.float("dim") ?: 0.3f).coerceIn(0f, 0.9f))
            "shader" -> pageShader(o, fromZip)?.let { PageBackground.Shader(it, colors.ifEmpty { listOf(SkinColor.Fixed(Color.Black)) }) }
                ?: colors.takeIf { it.isNotEmpty() }?.let { PageBackground.Fill(it, gradient) }
            else -> colors.takeIf { it.isNotEmpty() }?.let { PageBackground.Fill(it, gradient) }
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

    private fun pageGlass(o: JsonObject): Float? = when (val g = o["glass"]) {
        is JsonPrimitive -> if (g.content.equals("true", ignoreCase = true)) DefaultCardGlass else g.content.toFloatOrNull()
        is JsonObject -> g.float("blur") ?: DefaultCardGlass
        else -> null
    }?.coerceIn(0f, 60f)

    private fun pageBlock(o: JsonObject, fromZip: Boolean, depth: Int, inCard: Boolean): PageBlock? = when (val type = o.string("type")?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "heading" -> o.string("text")?.take(120)?.let(PageBlock::Heading)
        "text" -> o.string("text")?.take(2000)?.let(PageBlock::Paragraph)
        "image" -> o.string("image")?.let {
            PageBlock.Picture(it, (o.float("height") ?: 160f).coerceIn(40f, 400f).dp, (o.float("corner") ?: 16f).coerceIn(0f, 80f).dp)
        }
        "button", "actionbutton" -> {
            val text = o.string("text")?.take(60)
            val link = o.string("link")?.takeIf { ModuleUrlGuard.isSafeRemoteUrl(it) }
            val hasRichProps = o["sheet"] != null || o["sheetPage"] != null || o["route"] != null || o["page"] != null ||
                o["search"] != null || o["action"] != null || o["toggle"] != null || o["set"] != null ||
                o["toggleSetting"] != null || o["setSetting"] != null ||
                o["icon"] != null || o["glass"] != null ||
                o["colors"] != null || o["color"] != null || o["filled"] != null || o["corner"] != null
            when {
                text == null -> null
                link != null && !hasRichProps && type == "button" -> PageBlock.Button(text, link)
                else -> actionTarget(o)?.let { target ->
                    PageBlock.ActionButton(
                        text = text,
                        target = target,
                        icon = o.string("icon"),
                        filled = o.bool("filled") ?: true,
                        colors = pageColors(o),
                        corner = (o.float("corner") ?: 24f).coerceIn(0f, 50f).dp,
                        glass = pageGlass(o),
                    )
                }
            }
        }
        "divider" -> PageBlock.Divider
        "space" -> PageBlock.Space((o.float("size") ?: 16f).coerceIn(0f, 120f).dp)
        "song" -> ModuleSanitizer.safeNavArg(o.string("id"))?.let { PageBlock.Song(it, o.string("title")?.take(80) ?: it, o.string("artist")?.take(80)) }
        "playlist" -> ModuleSanitizer.safeNavArg(o.string("id"))?.let { PageBlock.Playlist(it, o.string("title")?.take(80) ?: it, o.string("subtitle")?.take(80)) }
        "album" -> ModuleSanitizer.safeNavArg(o.string("id"))?.let { PageBlock.Album(it, o.string("title")?.take(80) ?: it, o.string("subtitle")?.take(80)) }
        "artist" -> ModuleSanitizer.safeNavArg(o.string("id"))?.let { PageBlock.Artist(it, o.string("title")?.take(80) ?: it) }
        "search" -> o.string("query")?.take(120)?.let { PageBlock.Search(it, o.string("title")?.take(80) ?: it) }
        "setting" -> o.string("key")?.take(60)?.let(PageBlock::Setting)
        // container blocks enforce a strict depth cap, and cards cannot hold nested cards
        "card", "box", "panel" -> if (inCard || depth >= MaxPageDepth) null else pageBlocks(o["blocks"], fromZip, depth + 1, inCard = true).takeIf { it.isNotEmpty() }?.let { inside ->
            PageBlock.Card(
                blocks = inside,
                colors = pageColors(o),
                glass = pageGlass(o),
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 80f).dp,
                padding = (o.float("padding") ?: 16f).coerceIn(0f, 60f).dp,
                progressive = SkinExtrasParser.blurDirection(o.string("progressive") ?: (o["glass"] as? JsonObject)?.string("progressive")),
                stroke = o.float("stroke")?.coerceIn(0.5f, 6f)?.dp,
                strokeColor = skinColor(o.string("strokeColor")),
                target = actionTarget(o),
            )
        }
        "row" -> if (depth >= MaxPageDepth) null else pageBlocks(o["blocks"], fromZip, depth + 1, inCard = inCard).takeIf { it.isNotEmpty() }?.let { inside ->
            PageBlock.Row(inside.take(MaxRowBlocks), (o.float("gap") ?: 10f).coerceIn(0f, 40f).dp)
        }
        "column" -> if (depth >= MaxPageDepth) null else pageBlocks(o["blocks"], fromZip, depth + 1, inCard = inCard).takeIf { it.isNotEmpty() }?.let { inside ->
            PageBlock.Column(inside, (o.float("gap") ?: 10f).coerceIn(0f, 40f).dp)
        }
        "stack" -> if (depth >= MaxPageDepth) null else pageBlocks(o["blocks"], fromZip, depth + 1, inCard = inCard).takeIf { it.isNotEmpty() }?.let { inside ->
            PageBlock.Stack(inside, stackAlign(o.string("align")), o.float("height")?.coerceIn(40f, 600f)?.dp)
        }
        "blur" -> PageBlock.Blur(
            radius = (o.float("radius") ?: o.float("blur") ?: DefaultCardGlass).coerceIn(1f, 60f),
            direction = SkinExtrasParser.blurDirection(o.string("direction") ?: o.string("progressive")),
            ramp = (o.float("ramp") ?: 1f).coerceIn(0.1f, 1f),
            colors = pageColors(o),
            height = (o.float("height") ?: 80f).coerceIn(16f, 400f).dp,
            corner = (o.float("corner") ?: 20f).coerceIn(0f, 80f).dp,
            steps = (o.float("steps")?.toInt() ?: 5).coerceIn(2, 8),
        )
        "shader" -> pageShader(o, fromZip)?.let {
            PageBlock.Shader(
                program = it,
                height = (o.float("height") ?: 180f).coerceIn(24f, 800f).dp,
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 120f).dp,
                fallback = skinColor(o.string("fallback")),
            )
        }
        "cover" -> PageBlock.Cover(
            height = (o.float("height") ?: 240f).coerceIn(40f, 600f).dp,
            corner = (o.float("corner") ?: 20f).coerceIn(0f, 300f).dp,
            circle = o.string("shape")?.lowercase() == "circle",
        )
        "artistbanner" -> PageBlock.ArtistBanner(
            artistId = ModuleSanitizer.safeNavArg(o.string("artistId") ?: o.string("id")),
            height = (o.float("height") ?: 320f).coerceIn(160f, 520f).dp,
            corner = (o.float("corner") ?: 24f).coerceIn(0f, 48f).dp,
            edgeBlur = o.bool("edgeBlur") ?: o.bool("blur") ?: true,
            motion = o.bool("motion") ?: true,
            showAvatar = o.bool("showAvatar") ?: o.bool("avatar") ?: true,
            showLogo = o.bool("showLogo") ?: o.bool("logo") ?: true,
            showStats = o.bool("showStats") ?: o.bool("stats") ?: true,
            showActions = o.bool("showActions") ?: o.bool("actions") ?: true,
            scrim = (o.float("scrim") ?: 0.25f).coerceIn(0f, 0.85f),
        )
        "bio", "about" -> PageBlock.Bio(
            maxLines = (o.float("maxLines")?.toInt() ?: 4).coerceIn(1, 20),
            expandable = o.bool("expandable") ?: true,
            title = o.string("title")?.take(60),
        )
        "songs", "tracks" -> PageBlock.Songs(
            limit = (o.float("limit")?.toInt() ?: MaxSongs).coerceIn(1, MaxSongs),
            numbers = o.bool("numbers") ?: false,
            covers = o.bool("covers") ?: true,
            corner = (o.float("corner") ?: 8f).coerceIn(0f, 30f).dp,
            source = SkinExtrasParser.songSource(o.string("source")),
            layout = SkinExtrasParser.mediaListLayout(o.string("layout")),
            searchable = o.bool("searchable") ?: o.bool("search") ?: false,
            showMenu = o.bool("showMenu") ?: o.bool("menu") ?: true,
            empty = o.string("empty")?.take(80),
            glass = pageGlass(o),
        )
        "playlists" -> PageBlock.Playlists(
            source = SkinExtrasParser.playlistSource(o.string("source")),
            layout = SkinExtrasParser.mediaListLayout(o.string("layout")),
            limit = (o.float("limit")?.toInt() ?: 30).coerceIn(1, 100),
            columns = (o.float("columns")?.toInt() ?: 2).coerceIn(2, 4),
            covers = o.bool("covers") ?: true,
            corner = (o.float("corner") ?: 14f).coerceIn(0f, 32f).dp,
            showCount = o.bool("showCount") ?: true,
            showMenu = o.bool("showMenu") ?: o.bool("menu") ?: true,
            empty = o.string("empty")?.take(80),
        )
        "albums" -> PageBlock.Albums(
            source = SkinExtrasParser.mediaSource(o.string("source")),
            layout = SkinExtrasParser.mediaListLayout(o.string("layout"), default = MediaListLayout.Row),
            limit = (o.float("limit")?.toInt() ?: 20).coerceIn(1, 100),
            columns = (o.float("columns")?.toInt() ?: 2).coerceIn(2, 4),
            corner = (o.float("corner") ?: 14f).coerceIn(0f, 32f).dp,
            showMenu = o.bool("showMenu") ?: o.bool("menu") ?: true,
            empty = o.string("empty")?.take(80),
        )
        "artists" -> PageBlock.Artists(
            source = SkinExtrasParser.mediaSource(o.string("source")),
            layout = SkinExtrasParser.mediaListLayout(o.string("layout"), default = MediaListLayout.Row),
            limit = (o.float("limit")?.toInt() ?: 20).coerceIn(1, 100),
            columns = (o.float("columns")?.toInt() ?: 3).coerceIn(2, 4),
            empty = o.string("empty")?.take(80),
        )
        "nowplaying" -> PageBlock.NowPlaying(
            glass = pageGlass(o),
            corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
            showControls = o.bool("showControls") ?: o.bool("controls") ?: true,
            showLyrics = o.bool("showLyrics") ?: o.bool("lyrics") ?: false,
        )
        "lyrics" -> PageBlock.Lyrics(
            height = (o.float("height") ?: 200f).coerceIn(60f, 520f).dp,
            size = (o.float("size") ?: 15f).coerceIn(10f, 36f).sp,
            color = skinColor(o.string("color")) ?: SkinColor.Ink,
            active = skinColor(o.string("active")),
            blur = (o.float("blur") ?: 0f).coerceIn(0f, 20f),
            sweep = o.bool("sweep") ?: true,
            seek = o.bool("seek") ?: true,
            flow = o.string("style")?.lowercase() != "expressive",
            translation = o.bool("translation") ?: false,
        )
        "settingssearch" -> PageBlock.SettingsSearch(
            placeholder = o.string("placeholder")?.take(60),
            glass = pageGlass(o),
            corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
        )
        "settingscategories" -> {
            val cats = (o["categories"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.take(40) }
                .distinct()
                .take(16)
            PageBlock.SettingsCategories(
                layout = SkinExtrasParser.mediaListLayout(o.string("layout")),
                categories = cats,
                columns = (o.float("columns")?.toInt() ?: 2).coerceIn(2, 3),
                glass = pageGlass(o),
                colors = pageColors(o),
                corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
            )
        }
        "appsetting", "appsettings" -> {
            val setting = SafeAppSettings.find(o.string("key") ?: o.string("setting"))
            val category = SafeAppSettings.normalizeCategory(o.string("category"))
            if (setting != null || category != null) {
                PageBlock.AppSetting(
                    settingKey = setting?.id,
                    category = if (setting == null) category else null,
                    glass = pageGlass(o),
                    corner = (o.float("corner") ?: 20f).coerceIn(0f, 40f).dp,
                )
            } else null
        }
        "settingslink" -> {
            val target = SafeModuleRoutes.sanitize(o.string("target") ?: o.string("route"))?.takeIf(SafeModuleRoutes::isSafeSettingsRoute)
            target?.let {
                PageBlock.SettingsLink(
                    target = it,
                    title = o.string("title")?.take(60),
                    subtitle = o.string("subtitle")?.take(100),
                    icon = o.string("icon"),
                    glass = pageGlass(o),
                    corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
                )
            }
        }
        "modulesettingspages", "modulepages" -> PageBlock.ModuleSettingsPages(
            glass = pageGlass(o),
            corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
        )
        "searchbar" -> PageBlock.SearchBar(
            placeholder = o.string("placeholder")?.take(60),
            glass = pageGlass(o),
            corner = (o.float("corner") ?: 24f).coerceIn(0f, 40f).dp,
        )
        "speeddial", "pinned", "quickpicks" -> PageBlock.SpeedDial(
            layout = SkinExtrasParser.mediaListLayout(o.string("layout"), default = MediaListLayout.Grid),
            columns = (o.float("columns")?.toInt() ?: 3).coerceIn(2, 4),
            limit = (o.float("limit")?.toInt() ?: 9).coerceIn(1, 24),
            corner = (o.float("corner") ?: 14f).coerceIn(0f, 32f).dp,
            empty = o.string("empty")?.take(80),
        )
        "stats", "listeningstats" -> PageBlock.Stats(
            glass = pageGlass(o),
            corner = (o.float("corner") ?: 22f).coerceIn(0f, 40f).dp,
            showLibrary = o.bool("showLibrary") ?: true,
        )
        "tabs", "segmented" -> {
            val key = (o.string("key") ?: o.string("setting"))?.take(60)
            val items = (o["tabs"] as? JsonArray ?: o["items"] as? JsonArray).orEmpty().mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { v -> v.take(40) to v.replaceFirstChar { it.uppercase() }.take(30) }
                    is JsonObject -> {
                        val v = el.string("value")?.take(40) ?: el.string("id")?.take(40) ?: return@mapNotNull null
                        val l = el.string("label")?.take(30) ?: el.string("title")?.take(30) ?: v
                        v to l
                    }
                    else -> null
                }
            }.distinctBy { it.first }.take(8)
            if (key != null && items.size >= 2) {
                PageBlock.Tabs(key, items, glass = pageGlass(o), corner = (o.float("corner") ?: 20f).coerceIn(0f, 40f).dp)
            } else null
        }
        "play", "shuffle", "original", "use" -> control(o, type)
        "control" -> o.string("action")?.lowercase()?.let { control(o, it) }
        "player", "preview" -> PageBlock.Player(
            height = (o.float("height") ?: 420f).coerceIn(160f, 720f).dp,
            corner = (o.float("corner") ?: 28f).coerceIn(0f, 60f).dp,
        )
        "styles", "substyles" -> PageBlock.Styles
        else -> null
    }

    private fun stackAlign(raw: String?): StackAlign = when (raw?.trim()?.lowercase()) {
        "top" -> StackAlign.Top
        "bottom" -> StackAlign.Bottom
        "start", "left" -> StackAlign.Start
        "end", "right" -> StackAlign.End
        else -> StackAlign.Center
    }

    // a button that plays, shuffles, opens mochy's own page or sets the module's player, in the page's words or mochy's
    private fun control(o: JsonObject, action: String): PageBlock? {
        val kind = when (action) {
            "play" -> PageControl.Play
            "shuffle" -> PageControl.Shuffle
            "original", "mochy" -> PageControl.Original
            "use", "useplayer", "setplayer" -> PageControl.UsePlayer
            else -> return null
        }
        return PageBlock.Control(o.string("text")?.take(40).orEmpty(), kind, filled = o.bool("filled") ?: (kind == PageControl.Play))
    }

    // ---- the player's menu ----------------------------------------------------------------------

    // a link only to the web, a page by its id, a search, a built-in sheet, a safe route, or a player control
    private fun action(o: JsonObject): SkinAction? {
        val title = o.string("title")?.take(40) ?: return null
        return actionTarget(o)?.let { SkinAction(title, o.string("icon"), it) }
    }

    fun actionTarget(o: JsonObject): ActionTarget? {
        o.string("link")?.takeIf { ModuleUrlGuard.isSafeRemoteUrl(it.replace(Regex("""\{[a-zA-Z0-9_]+\}"""), "x").replace(" ", "%20")) }
            ?.let { return ActionTarget.Link(it) }
        // "sheet": "sleepTimer" for a built-in sheet, or "sheet": "page:my_page" / "sheetPage": "my_page" for a module page inside a PopupSheet
        o.string("sheetPage")?.lowercase()?.takeIf { PageId.matches(it) }
            ?.let { return ActionTarget.Sheet(SheetKind.ModulePage, it) }
        o.string("sheet")?.let { raw ->
            val builtin = SkinExtrasParser.sheetKind(raw)
            if (builtin != null) return ActionTarget.Sheet(builtin)
            val pageId = raw.trim().lowercase().removePrefix("page:")
            if (PageId.matches(pageId)) return ActionTarget.Sheet(SheetKind.ModulePage, pageId)
        }
        // "openSheet": "eq" opens the module's own sheet of that id over the player
        o.string("openSheet")?.trim()?.lowercase()?.takeIf { PageId.matches(it) }?.let { return ActionTarget.ModuleSheet(it) }
        o.string("route")?.let { SafeModuleRoutes.sanitize(it) }?.let { return ActionTarget.Route(it) }
        o.string("page")?.lowercase()?.takeIf { PageId.matches(it) }?.let { return ActionTarget.Page(it) }
        o.string("search")?.take(120)?.let { return ActionTarget.Search(it) }
        // toggle or set one of the module's own settings: "toggle": "glow" or "set": { "key": "tab", "value": "songs" }
        o.string("toggle")?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("$") }?.let { return ActionTarget.SetField(it.take(60), null) }
        (o["set"] as? JsonObject)?.let { setObj ->
            val key = setObj.string("key")?.take(60)
            val value = setObj.string("value")?.take(120)
            if (key != null) return ActionTarget.SetField(key, value)
        }
        // toggle or set an allowlisted safe Mochy app setting: "toggleSetting": "blur" or "setSetting": { "key": "pureBlack", "value": "true" }
        o.string("toggleSetting")?.let { SafeAppSettings.find(it) }?.let { return ActionTarget.SetAppSetting(it.id, null) }
        (o["setSetting"] as? JsonObject)?.let { setObj ->
            val spec = SafeAppSettings.find(setObj.string("key") ?: setObj.string("setting"))
            if (spec != null) return ActionTarget.SetAppSetting(spec.id, setObj.string("value")?.take(60))
        }
        SkinPlayerParser.buttonKind(o.string("action"))?.let { return ActionTarget.Control(it) }
        return null
    }

    private val PageId = Regex("[a-z0-9_-]{1,40}")
    // a page's name as a route writes it, with no arguments: album, local_playlist, library/songs
    private val PageName = Regex("[a-z0-9_/]{1,60}")
    private const val MaxReplaces = 8
    private const val MaxSongs = 500
    private const val MaxPages = 20
    private const val MaxPageBlocks = 120
    private const val MaxPageDepth = 3
    private const val MaxRowBlocks = 6
    private const val MaxPageLayers = 4
    private const val MaxActions = 12
    private const val MaxSheets = 12
    private val ShaderKinds = listOf(".agsl", ".sksl")
    private const val DefaultCardGlass = 24f
    // what mochy hands every page shader itself
    val PageUniforms = setOf("size", "time", "density", "touch", "pressed", "accent")
    // what mochy hands every glass shader itself
    val MochyUniforms = setOf("content", "size", "margin", "radius", "density", "quality", "chrome", "accent", "kind", "pill", "pillShown", "pillLift")
}
