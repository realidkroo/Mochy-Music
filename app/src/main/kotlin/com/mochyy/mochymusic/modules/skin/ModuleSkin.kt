// what a look module brings beyond plain settings: a player of its own, a font, a glass for the bottom bar, pages, rows in the player's menu, and where its files live (docs/wiki, module pages)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.modules.web.resolveModulePath
import java.io.File

@Immutable
class ModuleSkin(
    val moduleId: String,
    val moduleName: String,
    val player: SkinPlayer?,
    val font: SkinFont?,
    val pages: List<SkinPage>,
    private val files: File?,
    private val base: String?,
    val actions: List<SkinAction> = emptyList(),
    val glass: SkinGlass? = null,
) {
    // a style, a font or a glass changes how mochy looks, so only one such module is on at a time; pages and menu rows alone don't
    val restyles: Boolean get() = player != null || font != null || glass != null

    /** a picture or font a skin names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
    fun resolve(path: String?): Any? = resolveModulePath(path, files, base)

    /** a font or shader file, which only a zip can carry */
    fun fontFile(path: String?): File? = (resolve(path) as? File)

    /** every shader file the player draws with, so they compile off the main thread with the glass */
    val playerShaders: Set<String> by lazy {
        val found = mutableSetOf<String>()
        fun background(b: SkinBackground) {
            if (b is SkinBackground.Shader) found += b.program.file
        }
        fun blocks(list: List<SkinBlock>) {
            list.forEach { block ->
                when (block) {
                    is SkinBlock.Shader -> found += block.program.file
                    is SkinBlock.Styled -> blocks(listOf(block.inner))
                    is SkinBlock.Row -> blocks(block.blocks)
                    is SkinBlock.Column -> blocks(block.blocks)
                    is SkinBlock.Stack -> blocks(block.blocks)
                    else -> Unit
                }
            }
        }
        player?.let { p ->
            background(p.background)
            p.layers.forEach { background(it.background) }
            blocks(p.blocks)
        }
        found
    }
}

// ---- the bottom bar's glass -------------------------------------------------------------------

/** a glass of the module's own for the nav bar and mini player: its agsl file, the blur it reads through, how far past the bar it may read, and its own uniforms */
@Immutable
data class SkinGlass(val shader: String, val blurDp: Float, val marginDp: Float, val uniforms: Map<String, Float>) {
    fun with(change: GlassChange): SkinGlass =
        copy(blurDp = change.blurDp ?: blurDp, marginDp = change.marginDp ?: marginDp, uniforms = uniforms + change.uniforms)
}

/** what a setting's option changes about the module's glass while it's picked */
@Immutable
data class GlassChange(val blurDp: Float?, val marginDp: Float?, val uniforms: Map<String, Float>)

/** a colour as a skin writes it: one of the style's own tones, the cover's colour, the app's accent, or a fixed one */
@Immutable
sealed interface SkinColor {
    data class Fixed(val color: Color) : SkinColor
    data object Ink : SkinColor
    data object InkSoft : SkinColor
    data object Played : SkinColor
    data object Remaining : SkinColor
    data object Chip : SkinColor
    data object Cover : SkinColor
    data object Accent : SkinColor
    /** "$key": a colour someone typed or picked in one of the module's settings, [fallback] until it's a real colour */
    data class Setting(val key: String, val fallback: SkinColor) : SkinColor
}

enum class SkinAlign { Start, Center, End }

@Immutable
data class SkinText(
    val size: TextUnit,
    val weight: FontWeight,
    val color: SkinColor,
    val lines: Int,
    val upper: Boolean,
    val align: SkinAlign?,
)

@Immutable
data class SkinFont(val regular: String, val bold: String?)

// ---- the player -------------------------------------------------------------------------------

@Immutable
data class SkinPlayer(
    val name: String,
    val background: SkinBackground,
    val dim: Float,
    val padding: Dp,
    val align: SkinAlign,
    val blocks: List<SkinBlock>,
    /** more backgrounds stacked over the first one, each with its own see-through and motion */
    val layers: List<SkinLayer> = emptyList(),
    /** the settings the player's customise sheet offers, from the ••• menu or a customise button */
    val customise: SkinCustomise? = null,
    /** the module's own items in the player's chevron menu, above mochy's other menu and edit player */
    val menu: List<SkinMenuItem> = emptyList(),
) {
    /** whether anything in it moves with the music or a loop, so the shared clock only runs when something reads it */
    val moves: Boolean get() = layers.any { it.motion != null } || background is SkinBackground.Shader || blocks.any(SkinBlock::moves)
}

@Immutable
data class SkinCustomise(val title: String?, val keys: List<String>)

/** what a player button or a chevron menu item does: one of mochy's own controls, or a link, a page or a search like a menu row */
@Immutable
sealed interface SkinTarget {
    data class Control(val kind: ButtonKind) : SkinTarget
    data class Action(val target: ActionTarget) : SkinTarget
}

/** an item in the chevron menu: its words, a picture from the module (mochy's icon for a control when it has none) and what it does */
@Immutable
data class SkinMenuItem(val title: String, val icon: String?, val target: SkinTarget)

@Immutable
sealed interface SkinBackground {
    /** one of the player's own backgrounds: ambient, the blurred cover, the dark gradient, plain */
    data class Player(val style: PlayerBackgroundStyle) : SkinBackground
    /** the other styles' played and remaining tones, split at the playhead */
    data object Tones : SkinBackground
    data class Fill(val colors: List<SkinColor>) : SkinBackground
    /** [blur] in dp, only on android 12+, where it's drawn once and kept */
    data class Picture(val image: String, val blur: Float = 0f) : SkinBackground
    /** the module's own agsl, android 13+; [fallback] everywhere else and while it compiles */
    data class Shader(val program: SkinShaderSource, val fallback: SkinBackground) : SkinBackground
}

/** a shader file in the zip and the module's own float uniforms for it */
@Immutable
data class SkinShaderSource(val file: String, val uniforms: Map<String, Float>)

/** a background stacked over the player's own one */
@Immutable
data class SkinLayer(val background: SkinBackground, val opacity: Float, val motion: SkinMotion?)

/** a loop a block or a layer runs while the song plays: [speed] 1 is the normal pace, [amount] 1 the normal size */
@Immutable
data class SkinMotion(val kind: MotionKind, val speed: Float, val amount: Float)

enum class MotionKind { Float, Sway, Breathe, Pulse, Spin, Beat, Drift, Zoom, Shake }

/** how a block comes in: when the player opens, or again with every new song when [eachSong] */
@Immutable
data class SkinEnter(val kind: EnterKind, val delayMs: Int, val durationMs: Int, val eachSong: Boolean)

enum class EnterKind { Fade, Rise, Drop, Zoom, SlideStart, SlideEnd }

/** "key", "!key", "key=value" or "key!=value": a block that only shows while one of the module's settings says so */
@Immutable
data class SkinCondition(val key: String, val value: String?, val negate: Boolean)

enum class CoverShape { Square, Rounded, Circle }

enum class ProgressStyle { Line, Thick, Pill }

enum class VisualizerStyle { Bars, Mirror, Wave, Dots, Ring }

enum class ButtonKind { PlayPause, Previous, Next, Like, Shuffle, Repeat, Lyrics, Menu, Customise, Close }

enum class ShapeKind { Rect, Circle, Pill, Line }

enum class TimeKind { Elapsed, Remaining, Length }

enum class StackAlign { Center, Top, Bottom, Start, End }

@Immutable
sealed interface SkinBlock {
    data class Cover(val shape: CoverShape, val corner: Dp, val size: Float, val spin: Boolean, val frame: String?, val shadow: Boolean, val spinSpeed: Float = 1f) : SkinBlock
    data class Title(val text: SkinText) : SkinBlock
    data class Artist(val text: SkinText) : SkinBlock
    data class Album(val text: SkinText) : SkinBlock
    data class Label(val value: String, val text: SkinText) : SkinBlock
    data class Picture(val image: String, val width: Dp?, val height: Dp, val corner: Dp, val fit: Boolean = false) : SkinBlock
    data class Progress(val style: ProgressStyle, val color: SkinColor, val track: SkinColor, val times: Boolean, val seek: Boolean) : SkinBlock
    data class Controls(val size: Dp, val plain: Boolean, val color: SkinColor?, val icon: SkinColor?) : SkinBlock
    data class Lyrics(val height: Dp, val size: TextUnit, val color: SkinColor) : SkinBlock
    data class Space(val size: Dp, val grow: Boolean) : SkinBlock
    data class Row(val blocks: List<SkinBlock>, val gap: Dp) : SkinBlock
    data class Column(val blocks: List<SkinBlock>, val gap: Dp) : SkinBlock
    /** its blocks drawn on top of each other, the first at the back */
    data class Stack(val blocks: List<SkinBlock>, val align: StackAlign, val height: Dp?) : SkinBlock
    /** bars, a wave or dots that follow the music as it's heard */
    data class Visualizer(val style: VisualizerStyle, val count: Int, val height: Dp, val color: SkinColor, val fill: Float, val round: Boolean) : SkinBlock
    /** one control or custom action, with mochy's icon or the module's [image] ([imageOn] while it's on: playing, liked, shuffling) */
    data class Button(val target: SkinTarget, val size: Dp, val color: SkinColor?, val fill: SkinColor?, val image: String?, val imageOn: String?) : SkinBlock
    /** a plain shape to decorate with: a box, a circle, a pill or a line, filled or outlined */
    data class Shape(val kind: ShapeKind, val width: Dp?, val height: Dp, val colors: List<SkinColor>, val corner: Dp, val stroke: Dp?) : SkinBlock
    data class Time(val kind: TimeKind, val text: SkinText) : SkinBlock
    /** the module's own agsl drawn in a box, [fallback] colour where shaders can't run */
    data class Shader(val program: SkinShaderSource, val width: Dp?, val height: Dp, val corner: Dp, val fallback: SkinColor?) : SkinBlock
    /** any block with a loop, an entrance, a condition, a nudge or see-through on top */
    data class Styled(
        val inner: SkinBlock,
        val motion: SkinMotion?,
        val enter: SkinEnter?,
        val condition: SkinCondition?,
        val opacity: Float,
        val x: Dp,
        val y: Dp,
        val rotate: Float,
    ) : SkinBlock

    val moves: Boolean
        get() = when (this) {
            is Styled -> motion != null || inner.moves
            is Visualizer, is Shader -> true
            is Row -> blocks.any { it.moves }
            is Column -> blocks.any { it.moves }
            is Stack -> blocks.any { it.moves }
            else -> false
        }
}

// ---- pages ------------------------------------------------------------------------------------

/** a page of the module's own; [inSettings] lists it on the settings page too, with [icon] beside it */
@Immutable
data class SkinPage(
    val id: String,
    val title: String,
    val blocks: List<PageBlock>,
    val inSettings: Boolean = false,
    val subtitle: String? = null,
    val icon: String? = null,
)

@Immutable
sealed interface PageBlock {
    data class Heading(val text: String) : PageBlock
    data class Paragraph(val text: String) : PageBlock
    data class Picture(val image: String, val height: Dp, val corner: Dp) : PageBlock
    data class Button(val text: String, val link: String) : PageBlock
    data object Divider : PageBlock
    data class Space(val size: Dp) : PageBlock
    data class Song(val id: String, val title: String, val artist: String?) : PageBlock
    data class Playlist(val id: String, val title: String, val subtitle: String?) : PageBlock
    data class Album(val id: String, val title: String, val subtitle: String?) : PageBlock
    data class Artist(val id: String, val title: String) : PageBlock
    data class Search(val query: String, val title: String) : PageBlock
    /** one of the module's own settings, changed right there on the page */
    data class Setting(val key: String) : PageBlock
}

// ---- the player's menu ------------------------------------------------------------------------

/** a row the module adds to the player's menu: it opens a link, one of the module's pages, or a search, with the song filled in */
@Immutable
data class SkinAction(
    val title: String,
    val icon: String?,
    val target: ActionTarget,
)

@Immutable
sealed interface ActionTarget {
    data class Link(val template: String) : ActionTarget
    data class Page(val id: String) : ActionTarget
    data class Search(val template: String) : ActionTarget
}
