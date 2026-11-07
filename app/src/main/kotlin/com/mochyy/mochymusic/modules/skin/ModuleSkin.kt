// what a look module brings beyond plain settings: a player of its own, a font, a glass for the bottom bar, pages, rows in the player's menu, and where its files live (modules/README.md)

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
)

@Immutable
sealed interface SkinBackground {
    /** one of the player's own backgrounds: ambient, the blurred cover, the dark gradient, plain */
    data class Player(val style: PlayerBackgroundStyle) : SkinBackground
    /** the other styles' played and remaining tones, split at the playhead */
    data object Tones : SkinBackground
    data class Fill(val colors: List<SkinColor>) : SkinBackground
    data class Picture(val image: String) : SkinBackground
}

enum class CoverShape { Square, Rounded, Circle }

enum class ProgressStyle { Line, Thick, Pill }

@Immutable
sealed interface SkinBlock {
    data class Cover(val shape: CoverShape, val corner: Dp, val size: Float, val spin: Boolean, val frame: String?, val shadow: Boolean) : SkinBlock
    data class Title(val text: SkinText) : SkinBlock
    data class Artist(val text: SkinText) : SkinBlock
    data class Album(val text: SkinText) : SkinBlock
    data class Label(val value: String, val text: SkinText) : SkinBlock
    data class Picture(val image: String, val width: Dp?, val height: Dp, val corner: Dp) : SkinBlock
    data class Progress(val style: ProgressStyle, val color: SkinColor, val track: SkinColor, val times: Boolean, val seek: Boolean) : SkinBlock
    data class Controls(val size: Dp, val plain: Boolean, val color: SkinColor?, val icon: SkinColor?) : SkinBlock
    data class Lyrics(val height: Dp, val size: TextUnit, val color: SkinColor) : SkinBlock
    data class Space(val size: Dp, val grow: Boolean) : SkinBlock
    data class Row(val blocks: List<SkinBlock>, val gap: Dp) : SkinBlock
    data class Column(val blocks: List<SkinBlock>, val gap: Dp) : SkinBlock
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
