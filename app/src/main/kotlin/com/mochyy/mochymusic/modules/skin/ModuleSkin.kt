// what a look module brings beyond plain settings: a player of its own, a font, pages, and where its pictures live (modules/README.md)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
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
) {
    // a style or a font changes how mochy looks, so only one such module is on at a time; pages alone don't
    val restyles: Boolean get() = player != null || font != null

    /** a picture or font a skin names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
    fun resolve(path: String?): Any? {
        val name = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (name.startsWith("https://") || name.startsWith("http://")) return name
        // plain string work, no disk: this runs while the player draws, and a path can't climb out of the module's folder
        val inside = name.trimStart('/').takeIf { ".." !in it.split('/') } ?: return null
        if (files != null) return File(files, inside)
        return base?.let { "$it/$inside" }
    }

    /** a font file, which only a zip can carry */
    fun fontFile(path: String?): File? = (resolve(path) as? File)
}

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

@Immutable
data class SkinPage(val id: String, val title: String, val blocks: List<PageBlock>)

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
}
