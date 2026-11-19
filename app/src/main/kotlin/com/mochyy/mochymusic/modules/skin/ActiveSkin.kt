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

/** a look's glass, compiled and ready for the bars: the shader is shared, each bar builds its own effect from it; [drawsPill] when it draws the lit tab itself */
@Immutable
class ActiveGlass(
    val moduleId: String,
    val shader: Any,
    val blurDp: Float,
    val marginDp: Float,
    val uniforms: Map<String, Float>,
    val outline: Boolean = true,
    val shadow: Boolean = true,
    val drawsPill: Boolean = false,
)

/** one of the player's shaders, compiled, with the uniform names its source declares so a frame only sets those */
@Immutable
class SkinProgram(val shader: Any, val declared: Set<String>)

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

/** a skin and its module's settings right now */
internal data class LookValues(val skin: ModuleSkin, val values: Map<String, String>)

/** which look each part comes from: every look with players, the app's font, the glass, the nav bar, and every module with a takeover page */
internal data class SkinParts(
    val players: List<LookValues> = emptyList(),
    val font: ModuleSkin? = null,
    val glassSkin: ModuleSkin? = null,
    val glass: SkinGlass? = null,
    val bar: LookValues? = null,
    val pages: List<LookValues> = emptyList(),
    val mini: LookValues? = null,
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
    private val programs = mutableMapOf<String, SkinProgram>()

    /** called on every refresh of the switches; the fonts and the shaders load off the main thread, then everything changes on it together */
    internal fun publish(parts: SkinParts, scope: CoroutineScope) {
        if (parts == requested) return
        requested = parts
        val glassSkin = parts.glassSkin
        val shaded = (parts.players.map { it.skin } + listOfNotNull(parts.bar?.skin, parts.mini?.skin)).distinct()
        scope.launch(Dispatchers.IO) {
            val loaded = synchronized(this@ActiveSkin) {
                // the app's font and each player's own font, each loaded once and kept while its look stays
                val wanted = (listOfNotNull(parts.font) + parts.players.map { it.skin }.filter { it.font != null }).distinct()
                fonts = wanted.associateWith { skin -> if (skin in fonts) fonts[skin] else skin.font?.let { fontFamily(skin, it) } }
                val compiled = if (glassSkin != null && parts.glass != null) compile(glassSkin, parts.glass) else Compiled(null, null)
                val problems = mutableMapOf<String, String>()
                val loadedPrograms = shaded.flatMap { skin -> compilePlayer(skin) { problems[skin.moduleId] = it }.map { (file, program) -> "${skin.moduleId}/$file" to program } }.toMap()
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
                glass = if (glassSkin != null && parts.glass != null && loaded.compiled.shader != null) {
                    ActiveGlass(
                        glassSkin.moduleId, loaded.compiled.shader, parts.glass.blurDp, parts.glass.marginDp, parts.glass.uniforms,
                        outline = parts.glass.outline, shadow = parts.glass.shadow, drawsPill = loaded.compiled.drawsPill,
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
    private fun compilePlayer(skin: ModuleSkin, problem: (String) -> Unit): Map<String, SkinProgram> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || skin.playerShaders.isEmpty()) return emptyMap()
        return skin.playerShaders.mapNotNull { name ->
            val file = skin.fontFile(name)?.takeIf { it.isFile && it.length() <= MaxShaderBytes }
            if (file == null) {
                problem("its shader $name isn't in the module, or is bigger than ${MaxShaderBytes / 1024} KB")
                return@mapNotNull null
            }
            val key = "${file.path}:${file.lastModified()}:${file.length()}"
            programs[key]?.let { return@mapNotNull name to it }
            try {
                val source = file.readText()
                val program = SkinProgram(createAgslShader(source), DeclaredUniform.findAll(source).map { it.groupValues[1] }.toSet())
                programs[key] = program
                name to program
            } catch (e: Exception) {
                Timber.tag("ActiveSkin").w(e, "Couldn't compile the look's shader $name")
                problem("$name: " + (e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName))
                null
            }
        }.toMap()
    }

    private class Compiled(val shader: Any?, val problem: String?, val drawsPill: Boolean = false)

    // the shader's text, compiled once per file; a broken one gives the compiler's first line back for the module's page
    private fun compile(skin: ModuleSkin, look: SkinGlass): Compiled {
        // runtime shaders are android 13+, older phones keep mochy's frost and that's not a fault of the module's
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Compiled(null, null)
        val file = skin.fontFile(look.shader)?.takeIf { it.isFile } ?: return Compiled(null, "its shader file ${look.shader} isn't in the module")
        if (file.length() > MaxShaderBytes) return Compiled(null, "its shader is bigger than ${MaxShaderBytes / 1024} KB")
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        if (key == shaderKey && shader != null) return Compiled(shader, null, shaderDrawsPill)
        return try {
            val source = file.readText()
            val made = createAgslShader(source)
            shaderKey = key
            shader = made
            // a glass that takes the lit tab's place draws it itself, and mochy leaves its own highlight out
            shaderDrawsPill = DeclaredUniform.findAll(source).any { it.groupValues[1] == "pill" }
            Compiled(made, null, shaderDrawsPill)
        } catch (e: Exception) {
            Timber.tag("ActiveSkin").w(e, "Couldn't compile the look's glass")
            shaderKey = null
            shader = null
            Compiled(null, e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName)
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
