// what a look module brings beyond plain settings: players of its own, a font, a glass and a nav bar, pages, rows in the player's menu, and where its files live (docs/wiki, module pages)

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
    /** every player the look brings, each its own style in the picker with its sub-styles under it */
    val players: List<SkinPlayer>,
    val font: SkinFont?,
    val pages: List<SkinPage>,
    private val files: File?,
    private val base: String?,
    val actions: List<SkinAction> = emptyList(),
    val glass: SkinGlass? = null,
    val bar: SkinBar? = null,
    /** the look's own mini player: its blocks in a row beside mochy's cover, over the pill */
    val mini: SkinPlayer? = null,
) {
    /** the first player, the one the look switches to when it's turned on */
    val player: SkinPlayer? get() = players.firstOrNull()

    // a player, a font, a glass, a nav bar or a mini player changes how mochy looks; pages and menu rows alone don't
    val restyles: Boolean get() = players.isNotEmpty() || font != null || glass != null || bar != null || mini != null

    /** the pages that take one of mochy's own pages over */
    val takeovers: List<SkinPage> get() = pages.filter { it.replaces.isNotEmpty() }

    /** a picture or font a skin names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
    fun resolve(path: String?): Any? = resolveModulePath(path, files, base)

    /** a font or shader file, which only a zip can carry */
    fun fontFile(path: String?): File? = (resolve(path) as? File)

    /** every shader file the players, their sub-styles, their lyrics and the nav bar draw with, so they compile off the main thread with the glass */
    val playerShaders: Set<String> by lazy {
        val found = mutableSetOf<String>()
        fun background(b: SkinBackground) {
            if (b is SkinBackground.Shader) found += b.program.file
            if (b is SkinBackground.Live) background(b.fallback)
        }
        fun player(p: SkinPlayer) {
            background(p.background)
            p.layers.forEach { background(it.background) }
            p.all.forEach { block ->
                when (block) {
                    is SkinBlock.Shader -> found += block.program.file
                    is SkinBlock.Lyrics -> block.custom?.let { style -> listOfNotNull(style.fill, style.effect).forEach { found += it.file } }
                    else -> Unit
                }
            }
        }
        players.forEach { p ->
            player(p)
            p.variants.forEach { player(it.player) }
        }
        mini?.let(::player)
        bar?.shader?.let { found += it.file }
        found
    }
}

// ---- the bottom bar's glass -------------------------------------------------------------------

/** a glass of the module's own for the nav bar and mini player: its agsl file, the blur it reads through, how far past the bar it may read, its own uniforms, and whether mochy still draws its hairline and shadow round the bars */
@Immutable
data class SkinGlass(
    val shader: String,
    val blurDp: Float,
    val marginDp: Float,
    val uniforms: Map<String, Float>,
    val outline: Boolean = true,
    val shadow: Boolean = true,
) {
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
    /** hug the words in a row instead of sharing its width, so a label and its value sit side by side */
    val wrap: Boolean = false,
    /** extra room between letters, in em */
    val spacing: Float = 0f,
)

/** [wholeApp] puts the font on every screen; without it only the module's player uses it */
@Immutable
data class SkinFont(val regular: String, val bold: String?, val wholeApp: Boolean = false)

/** where mochy's chevron sits and how it's dressed: [side] start or end, or hidden when the module brings its own menu button */
@Immutable
data class SkinMenuButton(val side: MenuSide, val color: SkinColor?, val fill: SkinColor?)

enum class MenuSide { Start, End, Hidden }

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
    val menuButton: SkinMenuButton = DefaultMenuButton,
    /** blocks in a row level with the chevron, at the very top, taking no room from the rest */
    val header: List<SkinBlock> = emptyList(),
    /** the blocks scroll when they're taller than the screen, and grow spaces stop growing */
    val scroll: Boolean = false,
    /** how it's known among the look's players, so the one picked stays picked across updates */
    val id: String = "main",
    /** sub-styles: the same player with some parts or settings changed, stacked under it in the picker */
    val variants: List<SkinVariant> = emptyList(),
    /** one of the module's pages that edits this player, opened by edit in the picker */
    val editor: String? = null,
    /** how big its blocks are drawn: fitted from the screen it was made for, times its own scale */
    val size: SkinSize = SkinSize.Plain,
    /** the parts the player editor offers, each over the blocks marked with its id */
    val edit: List<SkinEditPart> = emptyList(),
) {
    /** whether anything in it moves with the music or a loop, so the shared clock only runs when something reads it */
    val moves: Boolean get() = layers.any { it.motion != null } || background.drawsShader || blocks.any(SkinBlock::moves) || header.any(SkinBlock::moves)

    // every block, the header's too, styled wrappers and groups opened up, worked out once since the layout never changes
    val all: List<SkinBlock> by lazy { (header + blocks).flatMap { it.walk() } }

    /** the built-in conditions the layout reads, so the player only follows the ones it uses */
    val flags: Set<SkinFlag> by lazy {
        all.filterIsInstance<SkinBlock.Styled>().flatMap { styled ->
            listOfNotNull(styled.condition) + styled.states.map { it.condition }
        }.mapNotNull { SkinFlag.of(it.key) }.toSet()
    }

    /** the events some block plays an animation on */
    val events: Set<SkinEvent> by lazy { all.filterIsInstance<SkinBlock.Styled>().flatMap { s -> s.triggers.map { it.event } }.toSet() }

    /** an animation that keeps going while the song is paused, so the clock can't rest just because nothing plays */
    val alwaysMoves: Boolean by lazy { all.filterIsInstance<SkinBlock.Styled>().any { s -> s.timelines.any { it.always && it.clock == SkinClock.Time } } }

    /** a box or the bottom bar asks for frosted glass, so the backdrop has to be captured for it */
    val frosted: Boolean by lazy { all.any { (it is SkinBlock.Box && it.glass != null) || (it is SkinBlock.BottomBar && it.glass != null) } }

    /** where each editable part sits: the part's id to the blocks marked with it */
    val partIds: Set<String> by lazy { all.mapNotNull { (it as? SkinBlock.Styled)?.part }.toSet() }

    /** some words name the stream or the phone, or a condition checks lossless, so mochy only gathers stream details for players that use them */
    val audio: Boolean by lazy { SkinFlag.Lossless in flags || all.any { it is SkinBlock.Label && SkinAudioField.containsMatchIn(it.value) } }

    /** lyrics show somewhere or something moves with them, so the player fetches and parses them */
    val lyrics: Boolean by lazy {
        SkinFlag.Lyrics in flags || SkinFlag.Singing in flags || SkinEvent.Line in events || all.any { block ->
            block is SkinBlock.Lyrics || (block is SkinBlock.Label && SkinLyricField.containsMatchIn(block.value)) ||
                (block is SkinBlock.BottomBar && block.kind == BottomBarKind.Cards) ||
                (block is SkinBlock.Styled && block.timelines.any { it.clock == SkinClock.Line || it.clock == SkinClock.Word })
        }
    }

    /** some words name the time, the queue or what plays next, so mochy follows those for this player */
    val playerFields: Boolean by lazy { all.any { it is SkinBlock.Label && SkinPlayerField.containsMatchIn(it.value) } }

    /** anything in the layout reads what is coming up or before in the queue, so we only collect the queue snapshot when needed */
    val queue: Boolean by lazy {
        SkinFlag.HasNext in flags || SkinFlag.HasPrev in flags || all.any { block ->
            block is SkinBlock.QueueList ||
                (block is SkinBlock.BottomBar && block.kind == BottomBarKind.Cards) ||
                (block is SkinBlock.Picture && block.source is SkinPictureSource.QueueCover) ||
                (block is SkinBlock.Label && SkinQueueField.containsMatchIn(block.value))
        }
    }
}

/** one part of the player editor: its [title], a setting whose options are offered side by side ([choose]), more [settings] under them, and a slider a [hold] drags */
@Immutable
data class SkinEditPart(val id: String, val title: String, val choose: String?, val settings: List<String>, val hold: String?)

/** a sub-style: [player] is the whole player with the variant's own parts laid over it, [values] settings it holds while it's picked */
@Immutable
data class SkinVariant(val id: String, val name: String, val player: SkinPlayer, val values: Map<String, String>)

val DefaultMenuButton = SkinMenuButton(MenuSide.End, color = null, fill = null)

/** the stream and phone placeholders a text block fills in; android's regex engine needs the closing brace escaped */
val SkinAudioField = Regex("""\{(codec|khz|kbps|bits|size|quality|device|volume|cpu)\}""", RegexOption.IGNORE_CASE)

/** the lyrics placeholders: the line being sung, the ones around it, its translation and the word */
val SkinLyricField = Regex("""\{(line|nextline|prevline|translation|word)\}""", RegexOption.IGNORE_CASE)

/** the queue placeholders: what's coming up, what played before, where we are and the queue's name */
val SkinQueueField = Regex("""\{(next(?:[1-9]|10)?(?:artist|length)?|prev(?:artist|length)?|queue(?:size|position|left|title)?)\}""", RegexOption.IGNORE_CASE)

/** the clock and queue placeholders: how far in, how long, what's next and where in the queue */
val SkinPlayerField = Regex("""\{(elapsed|remaining|length|percent|next(?:[1-9]|10)?(?:artist|length)?|prev(?:artist|length)?|queue(?:size|position|left|title)?)\}""", RegexOption.IGNORE_CASE)

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
    /** the song's moving cover filling the screen, over [fallback] until it starts and for songs that have none */
    data class Live(val media: List<CoverMedia>, val fallback: SkinBackground) : SkinBackground

    /** a shader is drawn, itself or under a moving cover, so the shared clock has to run for it */
    val drawsShader: Boolean get() = this is Shader || (this is Live && fallback is Shader)
}

/** what moves over a cover's picture, tried in order until the song has one */
@Immutable
sealed interface CoverMedia {
    /** the song's animated cover, from the sources picked in mochy's settings */
    data object Animated : CoverMedia
    /** the song's youtube video, kept in time with the song */
    data object Video : CoverMedia
    /** what the person turned on for mochy's own player: the video when video covers are on, then the animated cover */
    data object Auto : CoverMedia
    /** a loop of the module's own from its zip or a link, the same for every song: an mp4, webm or hls video, or a gif or webp that moves */
    data class Loop(val path: String) : CoverMedia
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

/** how a block comes in: when the player opens, or again with every new song when [eachSong]; [frames] when it brings its own keyframes */
@Immutable
data class SkinEnter(val kind: EnterKind, val delayMs: Int, val durationMs: Int, val eachSong: Boolean, val frames: SkinKeyframes? = null)

enum class EnterKind { Fade, Rise, Drop, Zoom, SlideStart, SlideEnd, Keyframes }

/** "key", "!key", "key=value" or "key!=value": a block that only shows while one of the module's settings says so */
@Immutable
data class SkinCondition(val key: String, val value: String?, val negate: Boolean) {
    /** whether it holds for a module's settings as they are now; away from the player there's no song for an @ condition, so those read as off */
    fun holdsFor(values: Map<String, String>): Boolean {
        val now = if (key.startsWith("@")) null else values[key]?.trim()
        val matches = if (value == null) !now.isNullOrEmpty() && !now.equals("false", ignoreCase = true) && now != "0" else now.equals(value, ignoreCase = true)
        return matches != negate
    }
}

enum class CoverShape { Square, Rounded, Circle }

enum class ProgressStyle { Line, Thick, Pill }

enum class VisualizerStyle { Bars, Mirror, Wave, Dots, Ring }

enum class ButtonKind { PlayPause, Previous, Next, Like, Shuffle, Repeat, Lyrics, Queue, Forward, Rewind, OtherMenu, Artist, Album, Menu, Customise, Close, Editor }

enum class ShapeKind { Rect, Circle, Pill, Line }

enum class TimeKind { Elapsed, Remaining, Length }

/** what a tap on a song inside a custom queue block does */
enum class QueueTap { Play, Queue, None }

/** where an image block's picture comes from: a file or link of the module's own, the playing song's cover, or a queued song's cover */
@Immutable
sealed interface SkinPictureSource {
    data class Asset(val path: String) : SkinPictureSource
    data object CurrentCover : SkinPictureSource
    data class QueueCover(val offset: Int) : SkinPictureSource
}

/** the player's bottom bar as a block: two cards (the sung line and what's next), two pills, or mochy's own volume bar */
enum class BottomBarKind { Cards, Pills, Volume }

enum class StackAlign { Center, Top, Bottom, Start, End }

/** which way a box's colours run when it has more than one */
enum class GradientKind { Vertical, Horizontal, Diagonal, Radial }

@Immutable
sealed interface SkinBlock {
    /** [size] is its share of the width unless [width] or [height] fix it in dp, [aspect] is width over height, [fit] shows all of a picture or video that isn't its shape, [media] moves over the picture */
    data class Cover(
        val shape: CoverShape,
        val corner: Dp,
        val size: Float,
        val spin: Boolean,
        val frame: String?,
        val shadow: Boolean,
        val spinSpeed: Float = 1f,
        val width: Dp? = null,
        val height: Dp? = null,
        val aspect: Float = 1f,
        val fit: Boolean = false,
        val media: List<CoverMedia> = emptyList(),
    ) : SkinBlock
    data class Title(val text: SkinText) : SkinBlock
    data class Artist(val text: SkinText) : SkinBlock
    data class Album(val text: SkinText) : SkinBlock
    /** words of the module's own; {title}, {artist} and {album} fill in the song, {$key} one of its settings */
    data class Label(val value: String, val text: SkinText) : SkinBlock
    /** [tint] recolours every pixel, for an icon drawn in one colour; [source] also reads {cover}, {nextCover}..{next10Cover} and {prevCover} */
    data class Picture(
        val image: String,
        val width: Dp?,
        val height: Dp,
        val corner: Dp,
        val fit: Boolean = false,
        val tint: SkinColor? = null,
        val source: SkinPictureSource = SkinPictureSource.Asset(image),
        val fallback: String? = null,
    ) : SkinBlock
    data class Progress(val style: ProgressStyle, val color: SkinColor, val track: SkinColor, val times: Boolean, val seek: Boolean) : SkinBlock
    /** the phone's music volume as a draggable bar, with optional quiet and loud speaker icons */
    data class Volume(val style: ProgressStyle, val color: SkinColor, val track: SkinColor, val icons: Boolean) : SkinBlock
    data class Controls(val size: Dp, val plain: Boolean, val color: SkinColor?, val icon: SkinColor?) : SkinBlock
    /** [flow] swaps the big sung-through lyrics for a few small lines rolling up, the current one in [active] after [prefix] */
    data class Lyrics(
        val height: Dp,
        val size: TextUnit,
        val color: SkinColor,
        val flow: Boolean = false,
        val active: SkinColor? = null,
        val prefix: String = "",
        val align: SkinAlign? = null,
        /** the module's own way of showing them, from "style": "custom" */
        val custom: SkinLyricStyle? = null,
    ) : SkinBlock
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
    /** the bar under the controls that opens the lyrics and the queue, on frosted [glass] or the module's colours */
    data class BottomBar(
        val kind: BottomBarKind,
        val height: Dp,
        val corner: Dp,
        val fill: List<SkinColor>,
        val glass: Float?,
        val color: SkinColor?,
        val gap: Dp,
    ) : SkinBlock
    /** upcoming songs straight inside the player, each row tapping to jump there, open the queue, or just read */
    data class QueueList(
        val limit: Int,
        val covers: Boolean,
        val coverSize: Dp,
        val corner: Dp,
        val gap: Dp,
        val text: SkinText,
        val artistColor: SkinColor,
        val times: Boolean,
        val numbers: Boolean,
        val tap: QueueTap,
        val height: Dp?,
        val empty: String?,
    ) : SkinBlock
    /** a panel holding blocks in a column (or a row): a fill, a gradient or frosted glass of [glass] dp behind them, a border, round corners, and an optional tap [target] */
    data class Box(
        val blocks: List<SkinBlock>,
        val row: Boolean,
        val gap: Dp,
        val padding: Dp,
        val corner: Dp,
        val fill: List<SkinColor>,
        val gradient: GradientKind,
        val stroke: Dp?,
        val strokeColor: SkinColor,
        val glass: Float?,
        val width: Dp?,
        val height: Dp?,
        val shadow: Boolean,
        val target: SkinTarget? = null,
    ) : SkinBlock
    /** a shape drawn from svg path data, scaled from its [viewWidth] by [viewHeight] box into its own */
    data class Vector(
        val data: String,
        val viewWidth: Float,
        val viewHeight: Float,
        val width: Dp?,
        val height: Dp,
        val fill: List<SkinColor>,
        val stroke: Dp?,
        val strokeColor: SkinColor?,
    ) : SkinBlock
    /** any block with loops, keyframes, states, events, an entrance, a condition, a nudge, a blur or see-through on top */
    data class Styled(
        val inner: SkinBlock,
        val motions: List<SkinMotion>,
        val enter: SkinEnter?,
        val condition: SkinCondition?,
        val opacity: Float,
        val x: Dp,
        val y: Dp,
        val rotate: Float,
        val scale: Float = 1f,
        val blur: Float = 0f,
        val originX: Float = 0.5f,
        val originY: Float = 0.5f,
        val timelines: List<SkinTimeline> = emptyList(),
        val states: List<SkinState> = emptyList(),
        val transition: SkinTransition? = null,
        val triggers: List<SkinTrigger> = emptyList(),
        /** the player editor's part this block belongs to */
        val part: String? = null,
    ) : SkinBlock

    val moves: Boolean
        get() = when (this) {
            is Styled -> motions.isNotEmpty() || timelines.isNotEmpty() || inner.moves
            is Visualizer, is Shader -> true
            // lyrics painted or bent by the module's shader hear the music and the clock like a shader block
            is Lyrics -> custom != null && (custom.fill != null || custom.effect != null)
            is Row -> blocks.any { it.moves }
            is Column -> blocks.any { it.moves }
            is Stack -> blocks.any { it.moves }
            is Box -> blocks.any { it.moves }
            else -> false
        }

    /** this block and every block inside it */
    fun walk(): List<SkinBlock> = listOf(this) + when (this) {
        is Styled -> inner.walk()
        is Row -> blocks.flatMap { it.walk() }
        is Column -> blocks.flatMap { it.walk() }
        is Stack -> blocks.flatMap { it.walk() }
        is Box -> blocks.flatMap { it.walk() }
        else -> emptyList()
    }
}

// ---- pages ------------------------------------------------------------------------------------

/** a page of the module's own; [inSettings] lists it on the settings page too, with [icon] beside it, and [background] behind it all */
@Immutable
data class SkinPage(
    val id: String,
    val title: String,
    val blocks: List<PageBlock>,
    val inSettings: Boolean = false,
    val subtitle: String? = null,
    val icon: String? = null,
    val background: PageBackground? = null,
    /** mochy's pages this one opens in place of (album, local_playlist, home...), while [condition] holds */
    val replaces: List<String> = emptyList(),
    val condition: SkinCondition? = null,
    /** how big its blocks are drawn */
    val size: SkinSize = SkinSize.Plain,
    /** the title bar at the top; a page that draws its own header can leave it out */
    val header: Boolean = true,
) {
    /** a card asks for frosted glass, so the page's background is captured for it */
    val frosted: Boolean get() = blocks.any { it is PageBlock.Card && it.glass != null }
}

/** what a page draws behind its blocks: colours, a picture, or the module's own shader */
@Immutable
sealed interface PageBackground {
    data class Fill(val colors: List<SkinColor>) : PageBackground
    /** [blur] in dp (android 12+), [dim] a black veil so the words stay readable */
    data class Picture(val image: String, val blur: Float, val dim: Float) : PageBackground
    /** the module's own agsl, android 13+; [fallback] colours everywhere else */
    data class Shader(val program: SkinShaderSource, val fallback: List<SkinColor>) : PageBackground
}

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
    /** blocks grouped on a card: frosted glass of [glass] dp over the page's background, or a fill */
    data class Card(val blocks: List<PageBlock>, val colors: List<SkinColor>, val glass: Float?, val corner: Dp, val padding: Dp) : PageBlock
    /** the module's own agsl in a box that answers touch, [fallback] colour where shaders can't run */
    data class Shader(val program: SkinShaderSource, val height: Dp, val corner: Dp, val fallback: SkinColor?) : PageBlock
    /** the picture of what the page took over: the playlist's, the album's, the artist's */
    data class Cover(val height: Dp, val corner: Dp, val circle: Boolean) : PageBlock
    /** the songs of what the page took over, each one played from there with the rest after it */
    data class Songs(val limit: Int, val numbers: Boolean, val covers: Boolean, val corner: Dp) : PageBlock
    /** a button that does something here: play or shuffle what the page took over, mochy's own page, or the player's editor bits */
    data class Control(val text: String, val kind: PageControl, val filled: Boolean) : PageBlock
    /** the module's player as it looks right now, small, for an editor page */
    data class Player(val height: Dp, val corner: Dp) : PageBlock
    /** the module's players and their sub-styles as chips, the picked one lit */
    data object Styles : PageBlock
}

/** what a page's control button does */
enum class PageControl { Play, Shuffle, Original, UsePlayer }

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
