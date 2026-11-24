// turns a manifest's "bar" into a nav bar: its tabs, sizes, colours, marker, shader and animations, every number kept in range and settings always reachable

package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.security.SafeModuleRoutes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object SkinBarParser {

    fun bar(o: JsonObject, fromZip: Boolean): SkinBar {
        val library = SkinAnimationParser.library(o["keyframes"])
        fun oneShot(name: String) = (o[name] as? JsonObject)?.let { SkinAnimationParser.timeline(it, library, SkinRepeat.Once, DefaultTapMs) }?.copy(clock = SkinClock.Time)
        return SkinBar(
            tabs = tabs(o["tabs"]),
            height = (o.float("height") ?: 64f).coerceIn(40f, 72f).dp,
            side = (o.float("side") ?: o.float("margin") ?: 24f).coerceIn(0f, 48f).dp,
            corner = o.float("corner")?.coerceIn(0f, 40f)?.dp,
            glass = o.bool("glass") ?: true,
            fill = colors(o),
            gradient = when (o.string("gradient")?.lowercase()) {
                "vertical", "down" -> GradientKind.Vertical
                "diagonal" -> GradientKind.Diagonal
                "radial", "round" -> GradientKind.Radial
                else -> GradientKind.Horizontal
            },
            shader = shader(o, fromZip),
            labels = when (o.string("labels")?.lowercase()) {
                "always", "all", "true" -> BarLabels.Always
                "selected", "lit", "active" -> BarLabels.Selected
                else -> BarLabels.Never
            },
            iconSize = (o.float("iconSize") ?: 24f).coerceIn(14f, 40f).dp,
            color = skinColor(o.string("color")),
            active = skinColor(o.string("active")),
            indicator = indicator(o["indicator"]),
            drag = o.bool("drag") ?: true,
            press = oneShot("press"),
            select = oneShot("select"),
            idle = (o["idle"] as? JsonObject)?.let { SkinAnimationParser.timeline(it, library, SkinRepeat.Loop, DefaultIdleMs) },
            outline = o.bool("outline") ?: true,
            shadow = o.bool("shadow") ?: true,
            size = SkinSize.of(o),
        )
    }

    // "home", "search", { "page": "mine", "icon": "icons/mine.png", "label": "Mine" }; settings always stays, or a broken bar could hide the way to turn its module off
    private fun tabs(element: JsonElement?): List<SkinBarTab> {
        val list = (element as? JsonArray).orEmpty().mapNotNull(::tab).distinctBy { it.target }.take(MaxTabs)
        if (list.isEmpty()) return emptyList()
        val keepsSettings = list.any { (it.target as? BarTarget.Tab)?.route == SettingsTab }
        return if (keepsSettings) list else list.take(MaxTabs - 1) + SkinBarTab(BarTarget.Tab(SettingsTab), null, null, null)
    }

    private fun tab(element: JsonElement): SkinBarTab? {
        val o = element as? JsonObject
        val name = (element as? JsonPrimitive)?.contentOrNull ?: o?.string("tab")
        val target = name?.let(::mochyTab)?.let(BarTarget::Tab)
            ?: o?.string("page")?.lowercase()?.takeIf { PageId.matches(it) }?.let(BarTarget::Page)
            ?: (o?.string("route") ?: (element as? JsonPrimitive)?.contentOrNull?.takeIf { mochyTab(it) == null })
                ?.let(SafeModuleRoutes::sanitize)
                ?.let { clean -> if (clean == "home" || clean == "search_input" || clean == "library") BarTarget.Tab(clean) else BarTarget.Route(clean) }
            ?: return null
        return SkinBarTab(target, o?.string("icon"), o?.string("iconOn") ?: o?.string("activeIcon"), o?.string("label")?.take(20))
    }

    // the routes of mochy's four tabs, by the names people know them by
    private fun mochyTab(raw: String): String? = when (raw.trim().lowercase()) {
        "home" -> "home"
        "search", "explore" -> "search_input"
        "library" -> "library"
        SettingsTab, "setting" -> SettingsTab
        else -> null
    }

    // "dot" or { "type": "line", "color": "accent", "corner": 2 }
    private fun indicator(element: JsonElement?): BarIndicator {
        val o = element as? JsonObject
        val kind = when (((element as? JsonPrimitive)?.contentOrNull ?: o?.string("type"))?.trim()?.lowercase()) {
            "dot" -> IndicatorKind.Dot
            "line", "underline" -> IndicatorKind.Line
            "glow" -> IndicatorKind.Glow
            "none", "false", "off" -> IndicatorKind.None
            else -> IndicatorKind.Pill
        }
        return BarIndicator(kind, skinColor(o?.string("color")), o?.float("corner")?.coerceIn(0f, 40f)?.dp)
    }

    private fun colors(o: JsonObject): List<SkinColor> =
        (o["colors"] as? JsonArray)?.mapNotNull { skinColor((it as? JsonPrimitive)?.contentOrNull) }?.take(6)
            ?: listOfNotNull(skinColor(o.string("fill") ?: o.string("background")))

    // a shader is code the gpu runs, so it only comes as a file in a zip
    private fun shader(o: JsonObject, fromZip: Boolean): SkinShaderSource? {
        if (!fromZip) return null
        val file = o.string("shader")?.takeIf { name -> name.endsWith(".agsl", ignoreCase = true) || name.endsWith(".sksl", ignoreCase = true) } ?: return null
        return SkinShaderSource(file, shaderUniforms(o["uniforms"], BarUniforms))
    }

    private const val SettingsTab = "settings"
    private const val MaxTabs = 6
    private const val DefaultTapMs = 320
    private const val DefaultIdleMs = 2400
    private val PageId = Regex("[a-z0-9_-]{1,40}")
    // what mochy hands the bar's shader itself
    val BarUniforms = setOf("size", "time", "density", "tabs", "selected", "pill", "pillShown", "pillLift", "pressed", "accent", "chrome")
}
