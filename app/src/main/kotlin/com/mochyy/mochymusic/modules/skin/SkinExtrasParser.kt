// parses extra skin features: custom bubble menus, floating lyrics bubble skins, custom sheets, app-wide backgrounds, progressive blur and media list options

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal object SkinExtrasParser {

    fun sheetKind(raw: String?): SheetKind? = when (raw?.trim()?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "playermenu", "actionmenu", "more", "actions", "othermenu" -> SheetKind.PlayerMenu
        "sleeptimer", "sleep", "timer" -> SheetKind.SleepTimer
        "output", "device", "devices", "deviceoutput", "audiooutput" -> SheetKind.Output
        "speed", "playbackspeed", "tempo", "pitch" -> SheetKind.Speed
        "songdetails", "details", "about", "aboutsong", "info", "songinfo" -> SheetKind.SongDetails
        "streaminfo", "stream", "codec", "qualityinfo" -> SheetKind.StreamInfo
        "share", "sharesong" -> SheetKind.Share
        "addtoplaylist", "addplaylist", "playlistadd" -> SheetKind.AddToPlaylist
        "lyricsprovider", "lyricssource", "lyricsproviders" -> SheetKind.LyricsProvider
        "lyricsoffset", "offset", "lyricssync" -> SheetKind.LyricsOffset
        "lyricstranslation", "translate", "translation" -> SheetKind.LyricsTranslation
        "lyricseditor", "editlyrics" -> SheetKind.LyricsEditor
        "customise", "customize", "skincustomise" -> SheetKind.Customise
        "createplaylist", "newplaylist" -> SheetKind.CreatePlaylist
        "songmenu" -> SheetKind.SongMenu
        "playlistmenu" -> SheetKind.PlaylistMenu
        "artistmenu" -> SheetKind.ArtistMenu
        "songedit", "editsong" -> SheetKind.SongEdit
        "videoquality" -> SheetKind.VideoQuality
        "canvasquality" -> SheetKind.CanvasQuality
        "videopicker", "pickvideo", "choosevideo" -> SheetKind.VideoPicker
        "devices", "deviceintegration", "cast", "connect" -> SheetKind.Devices
        "editplayer", "playerstudio", "studio", "styleselector" -> SheetKind.EditPlayer
        else -> null
    }

    fun blurDirection(raw: String?): SkinBlurDirection? = when (raw?.trim()?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "toptobottom", "down", "bottom", "fadein" -> SkinBlurDirection.TopToBottom
        "bottomtotop", "up", "top", "fadeout" -> SkinBlurDirection.BottomToTop
        "radial", "center", "centre", "round" -> SkinBlurDirection.Radial
        else -> null
    }

    fun gradientKind(raw: String?): GradientKind = when (raw?.trim()?.lowercase()) {
        "horizontal", "across" -> GradientKind.Horizontal
        "diagonal" -> GradientKind.Diagonal
        "radial", "round" -> GradientKind.Radial
        else -> GradientKind.Vertical
    }

    fun mediaListLayout(raw: String?, default: MediaListLayout = MediaListLayout.List): MediaListLayout =
        when (raw?.trim()?.lowercase()) {
            "grid", "cards", "tiles" -> MediaListLayout.Grid
            "row", "carousel", "horizontal" -> MediaListLayout.Row
            "compact", "dense", "mini" -> MediaListLayout.Compact
            "list", "rows", "vertical" -> MediaListLayout.List
            else -> default
        }

    fun songSource(raw: String?): SongSource = when (raw?.trim()?.lowercase()?.replace("_", "")) {
        "library", "mysongs", "all" -> SongSource.Library
        "liked", "likes", "favorites", "favourites" -> SongSource.Liked
        "downloaded", "downloads", "offline" -> SongSource.Downloaded
        "recent", "history", "recentlyplayed" -> SongSource.Recent
        "queue", "upnext" -> SongSource.Queue
        "top", "mostplayed" -> SongSource.Top
        else -> SongSource.Subject
    }

    fun playlistSource(raw: String?): PlaylistSource = when (raw?.trim()?.lowercase()?.replace("_", "")) {
        "subject", "page", "artist" -> PlaylistSource.Subject
        "auto", "smart", "system" -> PlaylistSource.Auto
        "all" -> PlaylistSource.All
        else -> PlaylistSource.Library
    }

    fun mediaSource(raw: String?): MediaSource = when (raw?.trim()?.lowercase()) {
        "library", "all" -> MediaSource.Library
        "liked", "favorites", "favourites" -> MediaSource.Liked
        else -> MediaSource.Subject
    }

    /** reads a boolean, float, or rich glass object: { blur, progressive, ramp, tint, bleed, outline, shader, uniforms } */
    fun glassConfig(element: JsonElement?, fromZip: Boolean, defaultBlur: Float = DefaultGlassBlur): Pair<Float?, SkinGlassConfig?> = when (element) {
        is JsonPrimitive -> {
            val radius = (element.booleanOrNull?.let { if (it) defaultBlur else null } ?: element.contentOrNull?.toFloatOrNull())
                ?.coerceIn(0f, SkinAnimationParser.MaxBlur)
            radius to radius?.let { SkinGlassConfig(blur = it) }
        }
        is JsonObject -> {
            val radius = (element.float("blur") ?: element.float("radius") ?: defaultBlur).coerceIn(0f, SkinAnimationParser.MaxBlur)
            val progressive = blurDirection(element.string("progressive") ?: element.string("direction"))
            val ramp = (element.float("ramp") ?: 1f).coerceIn(0.1f, 1f)
            val tint = skinColor(element.string("tint") ?: element.string("color"))
            val bleed = element.bool("bleed") ?: true
            val outline = element.bool("outline") ?: false
            val shaderFile = if (fromZip) {
                element.string("shader")?.takeIf { name -> ShaderKinds.any { name.endsWith(it, ignoreCase = true) } }
            } else null
            val shader = shaderFile?.let { SkinShaderSource(it, shaderUniforms(element["uniforms"], SkinPlayerParser.PlayerUniforms)) }
            radius to SkinGlassConfig(radius, progressive, ramp, tint, bleed, outline, shader)
        }
        else -> null to null
    }

    /** custom chevron / "more options" bubble popup menu style */
    fun menuStyle(element: JsonElement?): SkinMenuStyle? {
        val o = element as? JsonObject ?: return null
        val layout = when (o.string("layout")?.lowercase() ?: o.string("style")?.lowercase()) {
            "pill", "pills", "bubble", "bubbles" -> SkinMenuLayout.Pill
            "card", "panel", "sheet", "box" -> SkinMenuLayout.Card
            "grid", "tiles" -> SkinMenuLayout.Grid
            else -> SkinMenuLayout.Stack
        }
        val glass = when (val g = o["glass"]) {
            is JsonPrimitive -> g.booleanOrNull?.let { if (it) DefaultGlassBlur else null } ?: g.contentOrNull?.toFloatOrNull()
            is JsonObject -> g.float("blur") ?: DefaultGlassBlur
            else -> null
        }?.coerceIn(0f, SkinAnimationParser.MaxBlur)
        return SkinMenuStyle(
            layout = layout,
            dim = (o.float("dim") ?: 0.72f).coerceIn(0f, 0.92f),
            backdropBlur = (o.float("backdropBlur") ?: o.float("blur") ?: 0f).coerceIn(0f, SkinAnimationParser.MaxBlur),
            glass = glass,
            fill = colorList(o, "fill", "color"),
            activeFill = skinColor(o.string("activeFill") ?: o.string("active")),
            ink = skinColor(o.string("ink") ?: o.string("iconColor")),
            labelColor = skinColor(o.string("labelColor") ?: o.string("textColor")),
            labelFill = skinColor(o.string("labelFill")),
            corner = o.float("corner")?.coerceIn(0f, 60f)?.dp,
            stroke = o.float("stroke")?.coerceIn(0.5f, 6f)?.dp,
            strokeColor = skinColor(o.string("strokeColor")),
            size = o.float("size")?.coerceIn(28f, 64f)?.dp,
            gap = (o.float("gap") ?: 10f).coerceIn(4f, 28f).dp,
            atBottom = o.bool("atBottom") ?: (o.string("position")?.lowercase() == "bottom"),
            showDefaults = o.bool("showDefaults") ?: o.bool("defaults") ?: true,
            triggerIcon = o.string("triggerIcon") ?: o.string("icon"),
            columns = (o.float("columns")?.toInt() ?: 2).coerceIn(2, 3),
        )
    }

    /** floating lyrics overlay bubble skin */
    fun floating(element: JsonElement?): SkinFloating? {
        val o = element as? JsonObject ?: return null
        val bubble = colorList(o, "bubble", "background", "color")
        return SkinFloating(
            bubble = bubble,
            gradient = gradientKind(o.string("gradient")),
            ink = skinColor(o.string("ink") ?: o.string("textColor") ?: o.string("active")),
            waiting = skinColor(o.string("waiting") ?: o.string("inactive")),
            soft = skinColor(o.string("soft") ?: o.string("translationColor")),
            corner = (o.float("corner") ?: 18f).coerceIn(0f, 36f).dp,
            tail = o.bool("tail") ?: true,
            border = (o.float("border") ?: o.float("stroke"))?.coerceIn(0.5f, 4f)?.dp,
            borderColor = skinColor(o.string("borderColor") ?: o.string("strokeColor")),
            coverShape = when (o.string("coverShape")?.lowercase()) {
                "square" -> CoverShape.Square
                "rounded", "squircle" -> CoverShape.Rounded
                else -> CoverShape.Circle
            },
            coverCorner = (o.float("coverCorner") ?: 14f).coerceIn(0f, 32f).dp,
            opacity = (o.float("opacity") ?: 1f).coerceIn(0.45f, 1f),
            blur = (o.float("blur") ?: 0f).coerceIn(0f, 40f),
            fontSize = o.float("fontSize")?.coerceIn(11f, 20f),
            weight = skinWeight(o.string("weight")),
            shadow = o.bool("shadow") ?: true,
            widthScale = (o.float("widthScale") ?: 1f).coerceIn(0.8f, 1.35f),
            progressColor = skinColor(o.string("progressColor")),
            trackColor = skinColor(o.string("trackColor")),
        )
    }

    /** custom sheet surface style (clamped so sheets never turn invisible or unreadable) */
    fun sheetStyle(element: JsonElement?): SkinSheetStyle? {
        val o = element as? JsonObject ?: return null
        val style = SkinSheetStyle(
            surface = skinColor(o.string("surface") ?: o.string("background")),
            rowSurface = skinColor(o.string("rowSurface") ?: o.string("row") ?: o.string("card")),
            ink = skinColor(o.string("ink") ?: o.string("textColor")),
            accent = skinColor(o.string("accent")),
            corner = o.float("corner")?.coerceIn(0f, 48f)?.dp,
            rowCorner = o.float("rowCorner")?.coerceIn(0f, 32f)?.dp,
            dim = o.float("dim")?.coerceIn(0.2f, 0.85f),
            glass = when (val g = o["glass"]) {
                is JsonPrimitive -> g.booleanOrNull?.let { if (it) 20f else null } ?: g.contentOrNull?.toFloatOrNull()
                is JsonObject -> g.float("blur") ?: 20f
                else -> null
            }?.coerceIn(0f, 48f),
            stroke = o.float("stroke")?.coerceIn(0.5f, 4f)?.dp,
            strokeColor = skinColor(o.string("strokeColor")),
        )
        return style.takeIf {
            it.surface != null || it.rowSurface != null || it.ink != null || it.accent != null ||
                it.corner != null || it.rowCorner != null || it.dim != null || it.glass != null || it.stroke != null
        }
    }

    /** app-wide custom background behind non-sensitive screens */
    fun appBackground(
        element: JsonElement?,
        fromZip: Boolean,
        parseBg: (JsonObject, Boolean) -> PageBackground?,
    ): SkinAppBackground? {
        val o = element as? JsonObject ?: return null
        val bgObj = (o["background"] as? JsonObject) ?: o
        val base = parseBg(bgObj, fromZip) ?: return null
        val layers = (o["layers"] as? JsonArray).orEmpty().take(MaxBgLayers).mapNotNull { item ->
            val layerObj = item as? JsonObject ?: return@mapNotNull null
            val layerBg = parseBg(layerObj, fromZip) ?: return@mapNotNull null
            PageBgLayer(layerBg, (layerObj.float("opacity") ?: 1f).coerceIn(0.05f, 1f))
        }
        val screens = (o["screens"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.takeIf { s -> s.isNotEmpty() && !SafeModuleRoutes.isSensitiveRoute(s) } }
            .take(MaxScreens)
            .toSet()
        return SkinAppBackground(
            background = base,
            layers = layers,
            dim = (o.float("dim") ?: 0.35f).coerceIn(0.15f, 0.92f),
            opacity = (o.float("opacity") ?: 1f).coerceIn(0.1f, 1f),
            screens = screens,
        )
    }

    fun colorList(o: JsonObject, vararg singleKeys: String): List<SkinColor> {
        val arr = (o["colors"] as? JsonArray) ?: (o["bubble"] as? JsonArray) ?: (o["fill"] as? JsonArray)
        if (arr != null) {
            val parsed = arr.mapNotNull { skinColor((it as? JsonPrimitive)?.contentOrNull) }.take(6)
            if (parsed.isNotEmpty()) return parsed
        }
        for (key in singleKeys) {
            val one = skinColor(o.string(key))
            if (one != null) return listOf(one)
        }
        return emptyList()
    }

    private val ShaderKinds = listOf(".agsl", ".sksl")
    private const val DefaultGlassBlur = 24f
    private const val MaxBgLayers = 4
    private const val MaxScreens = 24
}
