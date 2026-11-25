// the looks that are on right now: every player they bring as a style to pick, the app's font, the glass, the nav bar and the mini player from the highest one that brings them, and the pages that take mochy's over

package com.mochyy.mochymusic.modules.skin

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mochyy.mochymusic.constants.ChromeGlassStyle
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.module.LookSetting
import com.mochyy.mochymusic.modules.security.ShaderSecurityGuard
import com.mochyy.mochymusic.ui.component.AgslTranspiler
import com.mochyy.mochymusic.ui.component.GlFallbackEngine
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.theme.LookFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** a module that can bring a skin: its own players, a font, a glass, a nav bar, pages */
internal interface SkinSource {
    val skin: ModuleSkin?

    /** the skin's glass with what the module's settings change about it */
    val glass: SkinGlass? get() = skin?.glass
}

/** a look's glass, compiled and ready for the bars: native RuntimeShader on 13+, transpiled glsl on 10-12, or static fallback */
@Immutable
class ActiveGlass(
    val moduleId: String,
    val shader: Any?,
    val blurDp: Float,
    val marginDp: Float,
    val uniforms: Map<String, Float>,
    val outline: Boolean = true,
    val shadow: Boolean = true,
    val drawsPill: Boolean = false,
    val isNative: Boolean = true,
    val fallback: SkinGlassFallback? = null,
)

/** one of the player's shaders, compiled as RuntimeShader on 13+ or transpiled glsl on 7-12, with its declared uniforms */
@Immutable
class SkinProgram(val shader: Any, val declared: Set<String>, val isNative: Boolean = true)

/** one style the picker offers: a module's player, or one of its sub-styles, with the module's settings as they are (and the sub-style's own laid over them) */
@Immutable
class ModuleStyle(
    /** "module/player" or "module/player/sub-style", what's kept as the pick */
    val key: String,
    val skin: ModuleSkin,
    val player: SkinPlayer,
    /** the player's own name, and the sub-style's under it */
    val name: String,
    val sub: String?,
    val values: Map<String, String>,
) {
    /** the player this sub-style belongs to, the picker's column */
    val group: String get() = "${skin.moduleId}/${player.id}"

    val title: String get() = sub?.let { "$name · $it" } ?: name
}

/** the look's nav bar, with its module's settings and its shader once it compiled */
@Immutable
data class ActiveBar(val skin: ModuleSkin, val bar: SkinBar, val values: Map<String, String>, val program: SkinProgram?)

/** the look's own mini player, with its module's settings */
@Immutable
data class ActiveMini(val skin: ModuleSkin, val layout: SkinPlayer, val values: Map<String, String>)

/** a module page taking one of mochy's pages over, with its module's settings for the page's conditions */
@Immutable
data class Takeover(val skin: ModuleSkin, val page: SkinPage, val values: Map<String, String>)

/** active floating lyrics bubble skin with its module's values */
@Immutable
data class ActiveFloating(val skin: ModuleSkin, val floating: SkinFloating, val values: Map<String, String>)

/** active sheet style with its module's values */
@Immutable
data class ActiveSheet(val skin: ModuleSkin, val sheet: SkinSheetStyle, val values: Map<String, String>)

/** active app-wide background with its module's values */
@Immutable
data class ActiveAppBackground(val skin: ModuleSkin, val background: SkinAppBackground, val values: Map<String, String>)

/** active default bubble/chevron menu style with its module's values */
@Immutable
data class ActiveMenuStyle(val skin: ModuleSkin, val style: SkinMenuStyle, val values: Map<String, String>)

/** a module's own sheets, with its settings for their conditions and colours */
@Immutable
data class ActiveModuleSheets(val skin: ModuleSkin, val values: Map<String, String>)

/** a skin and its module's settings right now */
internal data class LookValues(val skin: ModuleSkin, val values: Map<String, String>)

/** which look each part comes from: every look with players, the app's font, the glass, the nav bar, floating lyrics, sheet style, app background, menu style and every module with a takeover page */
internal data class SkinParts(
    val players: List<LookValues> = emptyList(),
    val font: ModuleSkin? = null,
    val glassSkin: ModuleSkin? = null,
    val glass: SkinGlass? = null,
    val bar: LookValues? = null,
    val pages: List<LookValues> = emptyList(),
    val mini: LookValues? = null,
    val floating: LookValues? = null,
    val sheet: LookValues? = null,
    val appBackground: LookValues? = null,
    val menuStyle: LookValues? = null,
    val sheets: List<LookValues> = emptyList(),
)

object ActiveSkin {
    /** every module player and sub-style there is to pick, the highest look's first */
    var styles: List<ModuleStyle> by mutableStateOf(emptyList())
        private set

    // the pick as kept: a style's key, or "module/" for that module's first player
    private var picked: String by mutableStateOf("")

    /** where a new pick is kept, set by the registry so it survives a restart */
    internal var onPick: ((String) -> Unit)? = null

    /** the style the module player draws: the one picked, else the highest look's first */
    val style: ModuleStyle?
        get() {
            val all = styles
            val pick = picked
            return all.firstOrNull { it.key == pick } ?: all.firstOrNull { pick.isNotEmpty() && it.key.startsWith(pick) } ?: all.firstOrNull()
        }

    /** the look whose player the module style draws */
    val current: ModuleSkin? get() = style?.skin

    /** what each of the drawn player's settings is set to, for its conditions and setting colours */
    val values: Map<String, String> get() = style?.values.orEmpty()

    /** the glass the nav bar and mini player draw while bottomBar is MODULE; null when the look has none or it didn't compile */
    var glass: ActiveGlass? by mutableStateOf(null)
        private set

    /** the highest look's own nav bar, null to keep mochy's */
    var bar: ActiveBar? by mutableStateOf(null)
        private set

    /** the highest look's own mini player, null to keep mochy's */
    var mini: ActiveMini? by mutableStateOf(null)
        private set

    /** module pages by the name of the mochy page each one takes over */
    var takeovers: Map<String, List<Takeover>> by mutableStateOf(emptyMap())
        private set

    /** the highest look's floating lyrics bubble style, null to keep mochy's */
    var floating: ActiveFloating? by mutableStateOf(null)
        private set

    /** the highest look's bottom sheet style, null to keep mochy's */
    var sheet: ActiveSheet? by mutableStateOf(null)
        private set

    /** the highest look's app-wide background, null to keep mochy's */
    var appBackground: ActiveAppBackground? by mutableStateOf(null)
        private set

    /** the highest look's default chevron/bubble menu style, null to keep mochy's */
    var menuStyle: ActiveMenuStyle? by mutableStateOf(null)
        private set

    /** every switched-on module's own sheets, by module id */
    var moduleSheets: Map<String, ActiveModuleSheets> by mutableStateOf(emptyMap())
        private set

    /** why a look's glass didn't load, for its page: the module's id and the shader's own words */
    var glassProblem: Pair<String, String>? by mutableStateOf(null)
        private set

    /** why one of a module's player or bar shaders didn't load, by module, for its page */
    var shaderProblems: Map<String, String> by mutableStateOf(emptyMap())
        private set

    // each look's font for its own players, by module
    private var playerFonts: Map<String, FontFamily> by mutableStateOf(emptyMap())

    /** a look's font for its own players, whether or not it also covers the rest of the app */
    fun playerFont(moduleId: String): FontFamily? = playerFonts[moduleId]

    // every look's player and bar shaders, by module and file name, the ones that compiled; empty below android 13
    private var shaders: Map<String, SkinProgram> by mutableStateOf(emptyMap())

    /** one of a module's shaders by its file name, null while it compiles, when it didn't, or below android 13 */
    fun shader(moduleId: String, file: String): SkinProgram? = shaders["$moduleId/$file"]

    /** picks a style by key (or "module/" for its first one) and keeps it */
    fun pick(key: String) {
        if (key == picked) return
        picked = key
        onPick?.invoke(key)
    }

    /** the pick saved last time, before the first publish */
    internal fun restorePick(key: String) {
        picked = key
    }

    @Volatile
    private var requested: SkinParts? = null

    // the fonts and the compiled glass last loaded, so a settings change that only moves a uniform reloads neither
    private var fonts: Map<ModuleSkin, FontFamily?> = emptyMap()
    private var shaderKey: String? = null
    private var shaderDrawsPill = false
    private var shader: Any? = null
    // bounded access-ordered cache so disabled/uninstalled module shaders don't accumulate forever
    private val programs = LinkedHashMap<String, SkinProgram>(32, 0.75f, true)
    private const val MaxCachedPrograms = 24

    /** called on every refresh of the switches; the fonts and the shaders load off the main thread, then everything changes on it together */
    internal fun publish(parts: SkinParts, scope: CoroutineScope) {
        if (parts == requested) return
        requested = parts
        val glassSkin = parts.glassSkin
        val shaded = (parts.players.map { it.skin } + parts.sheets.map { it.skin } + listOfNotNull(parts.bar?.skin, parts.mini?.skin, parts.appBackground?.skin)).distinct()
        scope.launch(Dispatchers.IO) {
            val loaded = synchronized(this@ActiveSkin) {
                // the app's font and each player's own font, each loaded once and kept while its look stays
                val wanted = (listOfNotNull(parts.font) + parts.players.map { it.skin }.filter { it.font != null }).distinct()
                fonts = wanted.associateWith { skin -> if (skin in fonts) fonts[skin] else skin.font?.let { fontFamily(skin, it) } }
                val compiled = if (glassSkin != null && parts.glass != null) compile(glassSkin, parts.glass) else Compiled(null, null)
                val problems = mutableMapOf<String, String>()
                val activeKeys = HashSet<String>()
                val loadedPrograms = shaded.flatMap { skin -> compilePlayer(skin, activeKeys) { problems[skin.moduleId] = it }.map { (file, program) -> "${skin.moduleId}/$file" to program } }.toMap()
                while (programs.size > MaxCachedPrograms) {
                    val evictKey = programs.keys.firstOrNull { it !in activeKeys } ?: break
                    programs.remove(evictKey)
                }
                val playerFonts = parts.players.mapNotNull { (skin, _) -> fonts[skin]?.let { skin.moduleId to it } }.toMap()
                Loaded(parts.font?.let { fonts[it] }, playerFonts, compiled, loadedPrograms, problems)
            }
            withContext(Dispatchers.Main) {
                if (requested != parts) return@withContext
                val nextStyles = stylesOf(parts.players)
                // the same styles again change nothing, so the picker and the player don't rebuild; a reloaded module is a new skin and counts
                val same = nextStyles.size == styles.size && nextStyles.zip(styles).all { (a, b) -> a.key == b.key && a.values == b.values && a.skin === b.skin && a.player == b.player }
                if (!same) styles = nextStyles
                LookFont.family = loaded.appFonts
                if (playerFonts != loaded.playerFonts) playerFonts = loaded.playerFonts
                glass = if (glassSkin != null && parts.glass != null && (loaded.compiled.shader != null || parts.glass.fallback != null)) {
                    ActiveGlass(
                        glassSkin.moduleId,
                        loaded.compiled.shader,
                        parts.glass.blurDp,
                        parts.glass.marginDp,
                        parts.glass.uniforms,
                        outline = parts.glass.outline,
                        shadow = parts.glass.shadow,
                        drawsPill = loaded.compiled.drawsPill,
                        isNative = loaded.compiled.isNative,
                        fallback = parts.glass.fallback,
                    )
                } else null
                glassProblem = loaded.compiled.problem?.let { glassSkin?.moduleId.orEmpty() to it }
                if (shaderProblems != loaded.problems) shaderProblems = loaded.problems
                if (shaders != loaded.programs) shaders = loaded.programs
                val nextBar = parts.bar?.let { (skin, values) ->
                    val own = skin.bar ?: return@let null
                    ActiveBar(skin, own, values, own.shader?.let { loaded.programs["${skin.moduleId}/${it.file}"] })
                }
                if (nextBar != bar) bar = nextBar
                val nextMini = parts.mini?.let { (skin, values) -> skin.mini?.let { ActiveMini(skin, it, values) } }
                if (nextMini != mini) mini = nextMini
                val nextFloating = parts.floating?.let { (skin, values) -> skin.floating?.let { ActiveFloating(skin, it, values) } }
                if (nextFloating != floating) floating = nextFloating
                val nextSheet = parts.sheet?.let { (skin, values) -> skin.sheet?.let { ActiveSheet(skin, it, values) } }
                if (nextSheet != sheet) sheet = nextSheet
                val nextAppBg = parts.appBackground?.let { (skin, values) -> skin.appBackground?.let { ActiveAppBackground(skin, it, values) } }
                if (nextAppBg != appBackground) appBackground = nextAppBg
                val nextMenuStyle = parts.menuStyle?.let { (skin, values) -> skin.menuStyle?.let { ActiveMenuStyle(skin, it, values) } }
                if (nextMenuStyle != menuStyle) menuStyle = nextMenuStyle
                val nextSheets = parts.sheets.associate { (skin, values) -> skin.moduleId to ActiveModuleSheets(skin, values) }
                if (nextSheets != moduleSheets) moduleSheets = nextSheets
                val nextTakeovers = takeoversOf(parts.pages)
                if (nextTakeovers != takeovers) takeovers = nextTakeovers
            }
        }
    }

    // each player as a style, its sub-styles after it, in the looks' order
    private fun stylesOf(players: List<LookValues>): List<ModuleStyle> = players.flatMap { (skin, values) ->
        skin.players.flatMap { player ->
            listOf(ModuleStyle("${skin.moduleId}/${player.id}", skin, player, player.name, null, values)) +
                player.variants.map { variant ->
                    ModuleStyle("${skin.moduleId}/${player.id}/${variant.id}", skin, variant.player, player.name, variant.name, values + variant.values)
                }
        }
    }

    // every takeover page by the name it replaces, the highest module first, so where two take the same page the higher one wins
    private fun takeoversOf(pages: List<LookValues>): Map<String, List<Takeover>> {
        val found = linkedMapOf<String, MutableList<Takeover>>()
        pages.forEach { (skin, values) ->
            skin.takeovers.forEach { page -> page.replaces.forEach { name -> found.getOrPut(name) { mutableListOf() } += Takeover(skin, page, values) } }
        }
        return found
    }

    private class Loaded(
        val appFonts: FontFamily?,
        val playerFonts: Map<String, FontFamily>,
        val compiled: Compiled,
        val programs: Map<String, SkinProgram>,
        val problems: Map<String, String>,
    )

    // each of a skin's shader files, compiled once and kept while the file stays the same; [problem] hears the last thing that went wrong
    private fun compilePlayer(skin: ModuleSkin, activeKeys: MutableSet<String>, problem: (String) -> Unit): Map<String, SkinProgram> {
        if (skin.playerShaders.isEmpty()) return emptyMap()
        val native = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        return skin.playerShaders.mapNotNull { name ->
            val file = skin.fontFile(name)?.takeIf { it.isFile && it.length() <= MaxShaderBytes }
            if (file == null) {
                problem("its shader $name isn't in the module, or is bigger than ${MaxShaderBytes / 1024} KB")
                return@mapNotNull null
            }
            val key = "${if (native) "agsl" else "gles"}:${file.path}:${file.lastModified()}:${file.length()}"
            activeKeys.add(key)
            programs[key]?.let { return@mapNotNull name to it }
            try {
                val source = file.readText()
                ShaderSecurityGuard.validate(source)?.let { rejected ->
                    problem("$name: $rejected")
                    return@mapNotNull null
                }
                val program = if (native) {
                    SkinProgram(createAgslShader(source), DeclaredUniform.findAll(source).map { it.groupValues[1] }.toSet(), isNative = true)
                } else {
                    val transpiled = AgslTranspiler.transpile(source) ?: run {
                        if (name !in skin.customLyricShaders) problem("$name: shader has no main(float2) entry point")
                        return@mapNotNull null
                    }
                    // custom lyrics effect shaders sample text content and stay android 13+ to avoid per-line offscreen passes
                    if (transpiled.shaderInputs.isNotEmpty() || name in skin.customLyricShaders) return@mapNotNull null
                    GlFallbackEngine.compileBlocking(transpiled.glsl)?.let { err ->
                        problem("$name: $err")
                        return@mapNotNull null
                    }
                    SkinProgram(transpiled, transpiled.declaredUniforms, isNative = false)
                }
                programs[key] = program
                name to program
            } catch (e: Exception) {
                Timber.tag("ActiveSkin").w(e, "Couldn't compile the look's shader $name")
                problem("$name: " + (e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName))
                null
            }
        }.toMap()
    }

    private class Compiled(val shader: Any?, val problem: String?, val drawsPill: Boolean = false, val isNative: Boolean = true)

    // the shader's text, compiled once per file; android 13+ uses native RuntimeShader and 10-12 transpiles to gles 3.0
    private fun compile(skin: ModuleSkin, look: SkinGlass): Compiled {
        // backdrop capture needs RenderNode (android 10+); older phones use look.fallback or mochy's frost
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Compiled(null, null, isNative = false)
        val native = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val file = skin.fontFile(look.shader)?.takeIf { it.isFile } ?: return Compiled(null, "its shader file ${look.shader} isn't in the module", isNative = native)
        if (file.length() > MaxShaderBytes) return Compiled(null, "its shader is bigger than ${MaxShaderBytes / 1024} KB", isNative = native)
        val key = "${if (native) "agsl" else "gles"}:${file.path}:${file.lastModified()}:${file.length()}"
        if (key == shaderKey && shader != null) return Compiled(shader, null, shaderDrawsPill, isNative = native)
        return try {
            val source = file.readText()
            ShaderSecurityGuard.validate(source)?.let { rejected ->
                shaderKey = null
                shader = null
                return Compiled(null, rejected, isNative = native)
            }
            if (native) {
                val made = createAgslShader(source)
                shaderKey = key
                shader = made
                // a glass that takes the lit tab's place draws it itself, and mochy leaves its own highlight out
                shaderDrawsPill = DeclaredUniform.findAll(source).any { it.groupValues[1] == "pill" }
                Compiled(made, null, shaderDrawsPill, isNative = true)
            } else {
                val transpiled = AgslTranspiler.transpile(source) ?: return Compiled(null, "shader has no main(float2) entry point", isNative = false)
                GlFallbackEngine.compileBlocking(transpiled.glsl)?.let { err ->
                    shaderKey = null
                    shader = null
                    return Compiled(null, err, isNative = false)
                }
                shaderKey = key
                shader = transpiled
                shaderDrawsPill = "pill" in transpiled.declaredUniforms
                Compiled(transpiled, null, shaderDrawsPill, isNative = false)
            }
        } catch (e: Exception) {
            Timber.tag("ActiveSkin").w(e, "Couldn't compile the look's glass")
            shaderKey = null
            shader = null
            Compiled(null, e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName, isNative = native)
        }
    }

    private fun fontFamily(skin: ModuleSkin, font: SkinFont): FontFamily? = runCatching {
        val regular = skin.fontFile(font.regular)?.takeIf { it.isFile } ?: return null
        val bold = skin.fontFile(font.bold)?.takeIf { it.isFile }
        // a broken file throws here, off the main thread, not the first time some text draws with it
        val typeface = android.graphics.Typeface.createFromFile(regular)
        // fonts from files with their weights are android 8+, older phones get the regular one and a drawn bold
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(listOfNotNull(Font(regular, FontWeight.Normal), bold?.let { Font(it, FontWeight.Bold) }))
        } else {
            FontFamily(typeface)
        }
    }.onFailure { Timber.tag("ActiveSkin").w(it, "Couldn't load the look's font") }.getOrNull()

    private const val MaxShaderBytes = 64 * 1024
    // "uniform float time;" or "layout(color) uniform half4 played;", the name is what a frame may set
    internal val DeclaredUniform = Regex("""\buniform\s+\w+\s+(\w+)\s*[;\[]""")
}

/** the look as it's applied: a module with its own player or glass switches to it, and a fixed accent turns the cover-coloured theme off */
internal fun appliedLook(look: Map<String, String>, skin: ModuleSkin?): Map<String, String> = buildMap {
    putAll(look)
    if (skin?.player != null && LookSetting.PLAYER_STYLE !in look) put(LookSetting.PLAYER_STYLE, PlayerStyle.MODULE.name)
    if (skin?.glass != null && LookSetting.BOTTOM_BAR !in look) put(LookSetting.BOTTOM_BAR, ChromeGlassStyle.MODULE.name)
    if (LookSetting.ACCENT_COLOR in look && LookSetting.DYNAMIC_THEME !in look) put(LookSetting.DYNAMIC_THEME, "false")
}
