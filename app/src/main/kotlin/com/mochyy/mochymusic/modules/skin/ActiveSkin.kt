// the look module that's on right now, which the player's module style, the app's font and the bottom bar's glass follow

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

/** a module that can bring a skin: its own player, a font, a glass, pages */
internal interface SkinSource {
    val skin: ModuleSkin?

    /** the skin's glass with what the module's settings change about it */
    val glass: SkinGlass? get() = skin?.glass
}

/** a look's glass, compiled and ready for the bars: the shader is shared, each bar builds its own effect from it */
@Immutable
class ActiveGlass(val moduleId: String, val shader: Any, val blurDp: Float, val marginDp: Float, val uniforms: Map<String, Float>)

/** one of the player's shaders, compiled, with the uniform names its source declares so a frame only sets those */
@Immutable
class SkinProgram(val shader: Any, val declared: Set<String>)

object ActiveSkin {
    var current: ModuleSkin? by mutableStateOf(null)
        private set

    /** the glass the nav bar and mini player draw while bottomBar is MODULE; null when the look has none or it didn't compile */
    var glass: ActiveGlass? by mutableStateOf(null)
        private set

    /** why a look's glass didn't load, for its page: the module's id and the shader's own words */
    var glassProblem: Pair<String, String>? by mutableStateOf(null)
        private set

    /** what each of the look's own settings is set to, for the player's conditions and setting colours */
    var values: Map<String, String> by mutableStateOf(emptyMap())
        private set

    /** why one of the player's shaders didn't load, for its page, the same way as [glassProblem] */
    var shaderProblem: Pair<String, String>? by mutableStateOf(null)
        private set

    /** the look's font for its own player, whether or not it also covers the rest of the app */
    var playerFont: FontFamily? by mutableStateOf(null)
        private set

    /** the player's shaders by file name, the ones that compiled; empty below android 13 */
    var shaders: Map<String, SkinProgram> by mutableStateOf(emptyMap())
        private set

    @Volatile
    private var requested: Triple<ModuleSkin?, SkinGlass?, Map<String, String>>? = null

    // the font and the compiled shader last loaded, so a settings change that only moves a uniform reloads neither
    private var fontFor: ModuleSkin? = null
    private var family: FontFamily? = null
    private var shaderKey: String? = null
    private var shader: Any? = null
    private val programs = mutableMapOf<String, SkinProgram>()

    /** called on every refresh of the switches; the font and the shaders load off the main thread, then everything changes on it together */
    internal fun publish(skin: ModuleSkin?, look: SkinGlass?, settings: Map<String, String>, scope: CoroutineScope) {
        val asked = Triple(skin, look, settings)
        if (asked == requested) return
        requested = asked
        scope.launch(Dispatchers.IO) {
            val loaded = synchronized(this@ActiveSkin) {
                if (fontFor !== skin) {
                    family = skin?.font?.let { fontFamily(skin, it) }
                    fontFor = skin
                }
                val compiled = if (skin != null && look != null) compile(skin, look) else Compiled(null, null)
                val programs = skin?.let(::compilePlayer).orEmpty()
                Loaded(family, compiled, look, programs, playerProblem)
            }
            withContext(Dispatchers.Main) {
                if (requested != asked) return@withContext
                current = skin
                // a font stays on the module's player unless the module asks for the whole app
                LookFont.family = loaded.fonts?.takeIf { skin?.font?.wholeApp == true }
                playerFont = loaded.fonts
                glass = if (skin != null && loaded.wanted != null && loaded.compiled.shader != null) {
                    ActiveGlass(skin.moduleId, loaded.compiled.shader, loaded.wanted.blurDp, loaded.wanted.marginDp, loaded.wanted.uniforms)
                } else null
                glassProblem = loaded.compiled.problem?.let { skin?.moduleId.orEmpty() to it }
                shaderProblem = loaded.playerProblem?.let { skin?.moduleId.orEmpty() to it }
                if (shaders != loaded.programs) shaders = loaded.programs
                if (values != settings) values = settings
            }
        }
    }

    private class Loaded(val fonts: FontFamily?, val compiled: Compiled, val wanted: SkinGlass?, val programs: Map<String, SkinProgram>, val playerProblem: String?)

    // why a player shader didn't compile on the last load, written and read under the lock
    private var playerProblem: String? = null

    // each of the player's shader files, compiled once and kept while the file stays the same
    private fun compilePlayer(skin: ModuleSkin): Map<String, SkinProgram> {
        playerProblem = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || skin.playerShaders.isEmpty()) return emptyMap()
        return skin.playerShaders.mapNotNull { name ->
            val file = skin.fontFile(name)?.takeIf { it.isFile && it.length() <= MaxShaderBytes }
            if (file == null) {
                playerProblem = "its player shader $name isn't in the module, or is bigger than ${MaxShaderBytes / 1024} KB"
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
                Timber.tag("ActiveSkin").w(e, "Couldn't compile the player's shader $name")
                playerProblem = "$name: " + (e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: e.javaClass.simpleName)
                null
            }
        }.toMap()
    }

    private class Compiled(val shader: Any?, val problem: String?)

    // the shader's text, compiled once per file; a broken one gives the compiler's first line back for the module's page
    private fun compile(skin: ModuleSkin, look: SkinGlass): Compiled {
        // runtime shaders are android 13+, older phones keep mochy's frost and that's not a fault of the module's
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Compiled(null, null)
        val file = skin.fontFile(look.shader)?.takeIf { it.isFile } ?: return Compiled(null, "its shader file ${look.shader} isn't in the module")
        if (file.length() > MaxShaderBytes) return Compiled(null, "its shader is bigger than ${MaxShaderBytes / 1024} KB")
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        if (key == shaderKey && shader != null) return Compiled(shader, null)
        return try {
            val made = createAgslShader(file.readText())
            shaderKey = key
            shader = made
            Compiled(made, null)
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
