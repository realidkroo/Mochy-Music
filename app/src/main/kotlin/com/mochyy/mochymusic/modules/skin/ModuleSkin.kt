// what a look module brings beyond plain settings: players of its own, a font, a glass and a nav bar, pages, rows in the player's menu, and where its files live (docs/wiki, module pages)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.module.bridge.shared.SurfaceBridge
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
    /** optional live framebuffer bridge when this skin comes from a signed code module */
    val surface: SurfaceBridge? = null,
    /** optional styling for mochy's floating lyrics bubble overlay */
    val floating: SkinFloating? = null,
    /** optional styling for bottom sheets */
    val sheet: SkinSheetStyle? = null,
    /** optional app-wide background behind non-sensitive screens */
    val appBackground: SkinAppBackground? = null,
    /** optional default chevron/bubble menu style for players */
    val menuStyle: SkinMenuStyle? = null,
    /** the module's own sheets, any player block inside, opened with "openSheet" from a button, a box or a menu item */
    val sheets: List<SkinSheet> = emptyList(),
) {
    /** the first player, the one the look switches to when it's turned on */
    val player: SkinPlayer? get() = players.firstOrNull()

    // a player, a font, a glass, a nav bar, a mini player, floating lyrics, sheet/menu style or app background changes how mochy looks; pages and menu rows alone don't
    val restyles: Boolean
        get() = players.isNotEmpty() || font != null || glass != null || bar != null || mini != null ||
            floating != null || sheet != null || appBackground != null || menuStyle != null

    /** the pages that take one of mochy's own pages over */
    val takeovers: List<SkinPage> get() = pages.filter { it.replaces.isNotEmpty() }

    /** a picture or font a skin names: a link as it is, a path from the module's zip, or a path next to a link module's manifest */
    fun resolve(path: String?): Any? = resolveModulePath(path, files, base)

    /** a font or shader file, which only a zip can carry */
    fun fontFile(path: String?): File? = (resolve(path) as? File)

    /** shaders declared only for custom lyrics fill/effect, which stay android 13+ to avoid per-line offscreen passes */
    val customLyricShaders: Set<String> by lazy {
        val found = mutableSetOf<String>()
        fun walk(p: SkinPlayer) {
            p.all.forEach { block ->
                if (block is SkinBlock.Lyrics) {
                    block.custom?.let { style -> listOfNotNull(style.fill, style.effect).forEach { found += it.file } }
                }
            }
        }
        players.forEach { p -> p.withPages.forEach(::walk); p.variants.forEach { v -> v.player.withPages.forEach(::walk) } }
        mini?.let(::walk)
        sheets.forEach { walk(it.layout) }
        found
    }

    /** every shader file the players, their sub-styles, their lyrics, boxes, app background and the nav bar draw with, so they compile off the main thread with the glass */
    val playerShaders: Set<String> by lazy {
        val found = mutableSetOf<String>()
        fun background(b: SkinBackground) {
            if (b is SkinBackground.Shader) found += b.program.file
            if (b is SkinBackground.Live) background(b.fallback)
        }
        fun pageBg(b: PageBackground) {
            if (b is PageBackground.Shader) found += b.program.file
        }
        fun player(p: SkinPlayer) {
            background(p.background)
            p.layers.forEach { background(it.background) }
            p.all.forEach { block ->
                when (block) {
                    is SkinBlock.Shader -> found += block.program.file
                    is SkinBlock.Box -> block.glassConfig?.shader?.let { found += it.file }
                    is SkinBlock.BottomBar -> block.glassConfig?.shader?.let { found += it.file }
                    is SkinBlock.Lyrics -> block.custom?.let { style -> listOfNotNull(style.fill, style.effect).forEach { found += it.file } }
                    else -> Unit
                }
            }
        }
        players.forEach { p ->
            p.withPages.forEach(::player)
            p.variants.forEach { v -> v.player.withPages.forEach(::player) }
        }
        mini?.let(::player)
        sheets.forEach { player(it.layout) }
        bar?.shader?.let { found += it.file }
        appBackground?.let { bg ->
            pageBg(bg.background)
            bg.layers.forEach { pageBg(it.background) }
        }
        found
    }
}

// ---- the bottom bar's glass -------------------------------------------------------------------

/** optional static glass styling for android 7-9 or when live blur and gles can't run */
@Immutable
data class SkinGlassFallback(
    val tint: SkinColor? = null,
    val rim: SkinColor? = null,
    val sheen: Float = 0.08f,
)

/** a glass of the module's own for the nav bar and mini player: its agsl file, the blur it reads through, how far past the bar it may read, its own uniforms, and whether mochy still draws its hairline and shadow round the bars */
@Immutable
data class SkinGlass(
    val shader: String,
    val blurDp: Float,
    val marginDp: Float,
    val uniforms: Map<String, Float>,
    val outline: Boolean = true,
    val shadow: Boolean = true,
    val fallback: SkinGlassFallback? = null,
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
data class SkinMenuButton(
    val side: MenuSide,
    val color: SkinColor?,
    val fill: SkinColor?,
    /** the module's own chevron art in place of mochy's arrow, and another for while the menu is open */
    val image: String? = null,
    val imageOpen: String? = null,
    /** the button's size in dp, and its corner (null keeps it round) */
    val size: Dp? = null,
    val corner: Dp? = null,
    /** how much of the button the art fills */
    val imageScale: Float = 0.5f,
    /** whether the art flips round as the menu opens, the way mochy's arrow does */
    val turns: Boolean = true,
    val stroke: Dp? = null,
    val strokeColor: SkinColor? = null,
)

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
    /** custom styling for this player's chevron / bubble popup menu */
    val menuStyle: SkinMenuStyle? = null,
    /** false hides the white bar at the top of the player; swiping down still closes it */
    val handle: Boolean = true,
    /** the module's own lyrics page and queue page, opened in place of mochy's from this player */
    val lyricsPage: SkinPlayer? = null,
    val queuePage: SkinPlayer? = null,
) {
    /** this player and the pages it opens, for anything that has to look through all of their blocks */
    val withPages: List<SkinPlayer> get() = listOfNotNull(this, lyricsPage, queuePage)

    /** whether anything in it moves with the music or a loop, so the shared clock only runs when something reads it */
    val moves: Boolean get() = layers.any { it.motion != null } || background.drawsShader || blocks.any(SkinBlock::moves) || header.any(SkinBlock::moves)

    // every block, the header's too, styled wrappers and groups opened up, worked out once since the layout never changes
    val all: List<SkinBlock> by lazy { (header + blocks).flatMap { it.walk() } }

    /** the built-in conditions the layout reads, so the player only follows the ones it uses */
    val flags: Set<SkinFlag> by lazy {
        (all.filterIsInstance<SkinBlock.Styled>().flatMap { styled ->
            listOfNotNull(styled.condition) + styled.states.map { it.condition }
        } + menu.mapNotNull { it.condition }).mapNotNull { SkinFlag.of(it.key) }.toSet()
    }

    /** the events some block plays an animation on */
    val events: Set<SkinEvent> by lazy { all.filterIsInstance<SkinBlock.Styled>().flatMap { s -> s.triggers.map { it.event } }.toSet() }

    /** an animation that keeps going while the song is paused, so the clock can't rest just because nothing plays */
    val alwaysMoves: Boolean by lazy { all.filterIsInstance<SkinBlock.Styled>().any { s -> s.timelines.any { it.always && it.clock == SkinClock.Time } } }

    /** a box, blur band, bottom bar or bubble menu asks for frosted glass, so the backdrop has to be captured for it */
    val frosted: Boolean by lazy {
        menuStyle?.glass != null || (menuStyle?.backdropBlur ?: 0f) > 0f || all.any {
            it is SkinBlock.Blur ||
                (it is SkinBlock.Box && (it.glass != null || it.glassConfig != null)) ||
                (it is SkinBlock.BottomBar && (it.glass != null || it.glassConfig != null))
        }
    }

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

    /** some words name the song's facts ({about}, {genres}…) or a condition checks for them, so mochy looks the song up for this player */
    val songInfo: Boolean by lazy { SkinFlag.About in flags || all.any { it is SkinBlock.Label && SkinSongInfoField.containsMatchIn(it.value) } }

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

/** the song info placeholders: what it's about, its genres, when it came out, who wrote and produced it, its label */
val SkinSongInfoField = Regex("""\{(about|genres?|released|year|writers|producers|label)\}""", RegexOption.IGNORE_CASE)

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

/** an item in the chevron menu: its words, a picture from the module (mochy's icon for a control when it has none), optional visibility condition, accent colour, and what it does */
@Immutable
data class SkinMenuItem(
    val title: String,
    val icon: String?,
    val target: SkinTarget,
    val condition: SkinCondition? = null,
    val accent: SkinColor? = null,
)

@Immutable
sealed interface SkinBackground {
    /** one of the player's own backgrounds: ambient, the blurred cover, the dark gradient, plain */
    data class Player(val style: PlayerBackgroundStyle) : SkinBackground
    /** the other styles' played and remaining tones, split at the playhead */
    data object Tones : SkinBackground
    data class Fill(val colors: List<SkinColor>) : SkinBackground
    /** [blur] in dp, only on android 12+, where it's drawn once and kept; [edgeBlur] adds soft progressive top/bottom blur bands */
    data class Picture(val image: String, val blur: Float = 0f, val edgeBlur: Boolean = false) : SkinBackground
    /** the playing song's cover blurred with a custom [blur] radius in dp and [dim] veil */
    data class CoverBlur(val blur: Float = 40f, val dim: Float = 0.25f) : SkinBackground
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

enum class ButtonKind {
    PlayPause, Play, Pause, Previous, Next, Like, Shuffle, Repeat, Lyrics, Queue,
    Forward, Rewind, Forward30, Rewind30, Restart,
    OtherMenu, Artist, Album, Menu, Customise, Close, Editor,
    Equalizer, AudioLab, FloatingLyrics, Library, Download, Pin, Radio, Share,
    SleepTimer, Output, Speed, SongDetails, StreamInfo, AddToPlaylist, CreatePlaylist,
    LyricsProvider, LyricsOffset, LyricsTranslation, LyricsEditor,
    SongEdit, VideoQuality, CanvasQuality, VideoPicker, Devices, EditPlayer,
    VolumeUp, VolumeDown, Mute, OpenPlayer,
    Rotate, FullVideo, Settings, Search, Home, Back,
}

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

/** where a live surface block puts its touch gamepad: under the screen, over it, or hidden */
enum class SurfaceControls { Below, Overlay, None }

/** how a surface's frames fill its screen: stretched to its shape, whole with bars, cropped to fill it, or at the biggest whole-pixel scale */
enum class SurfaceFit { Stretch, Contain, Cover, Pixel }

/** whether a surface can cover its whole player: from a button on its pad and the chevron menu, opened that way, or never */
enum class SurfaceFullscreen { Button, Start, Off }

/** the surface fields a module can hand to one of its settings with "$key|fallback", read again whenever the setting changes */
object SurfaceOptions {
    const val Controls = "controls"
    const val Fit = "fit"
    const val Aspect = "aspect"
    const val Fullscreen = "fullscreen"
    val Bindable = setOf(Controls, Fit, Aspect, Fullscreen)

    fun controls(raw: String?): SurfaceControls? = when (raw?.trim()?.lowercase()) {
        "below", "under", "true" -> SurfaceControls.Below
        "overlay", "over" -> SurfaceControls.Overlay
        "none", "off", "false", "hidden" -> SurfaceControls.None
        else -> null
    }

    fun fit(raw: String?): SurfaceFit? = when (raw?.trim()?.lowercase()) {
        "stretch", "fill" -> SurfaceFit.Stretch
        "contain", "fit", "letterbox" -> SurfaceFit.Contain
        "cover", "crop", "zoom" -> SurfaceFit.Cover
        "pixel", "integer", "sharp" -> SurfaceFit.Pixel
        else -> null
    }

    fun fullscreen(raw: String?): SurfaceFullscreen? = when (raw?.trim()?.lowercase()) {
        "button", "true", "on" -> SurfaceFullscreen.Button
        "start", "always" -> SurfaceFullscreen.Start
        "off", "false", "none" -> SurfaceFullscreen.Off
        else -> null
    }

    /** "4:3", "16/10" or 1.6 as a shape, "frame" for the module's own frame shape, "fill" for all the room left */
    fun aspect(raw: String?): SurfaceAspect? {
        val text = raw?.trim()?.lowercase() ?: return null
        when (text) {
            "frame", "native", "source" -> return SurfaceAspect(ratio = null, fill = false)
            "fill", "full", "screen" -> return SurfaceAspect(ratio = null, fill = true)
        }
        val sides = text.split(':', '/', 'x')
        val ratio = if (sides.size == 2) {
            val w = sides[0].trim().toFloatOrNull()
            val h = sides[1].trim().toFloatOrNull()
            if (w != null && h != null && h > 0f) w / h else null
        } else {
            text.toFloatOrNull()
        }
        return ratio?.takeIf { it.isFinite() && it > 0f }?.let { SurfaceAspect(it.coerceIn(0.25f, 4f), fill = false) }
    }
}

/** a surface screen's shape: a fixed [ratio], the frame's own when null, or all the room left when [fill] */
@Immutable
data class SurfaceAspect(val ratio: Float?, val fill: Boolean)

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
        /** inactive line blur in dp (android 12+); [pastBlur]/[nextBlur] override it per side */
        val blur: Float = 0f,
        val pastBlur: Float? = null,
        val nextBlur: Float? = null,
        /** fade the top and bottom edges of the flowing list */
        val edgeFade: Boolean = true,
        val gap: Dp = 6.dp,
        val weight: FontWeight? = null,
        val activeScale: Float = 1.06f,
        val pastOpacity: Float = 0.35f,
        val nextOpacity: Float = 0.6f,
        val linesAbove: Int = 1,
        /** word-by-word karaoke sweep on the active flowing line when timed words exist */
        val sweep: Boolean = false,
        /** show romanised or translated reading under the active line */
        val translation: Boolean = false,
        /** tap a flowing line to seek to it */
        val seek: Boolean = false,
    ) : SkinBlock
    /** a standalone frosted or progressive blur band over the player's captured backdrop */
    data class Blur(
        val radius: Float,
        val direction: SkinBlurDirection? = null,
        val ramp: Float = 1f,
        val colors: List<SkinColor> = emptyList(),
        val width: Dp? = null,
        val height: Dp = 96.dp,
        val corner: Dp = 0.dp,
        val bleed: Boolean = true,
        val steps: Int = 5,
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
    /** a live interactive framebuffer surface driven by a signed code module inside the player */
    data class Surface(
        val asset: String? = null,
        val extras: List<String> = emptyList(),
        val title: String? = null,
        val width: Dp? = null,
        val height: Dp? = null,
        val aspect: SurfaceAspect = SurfaceAspect(4f / 3f, fill = false),
        val corner: Dp = 12.dp,
        val controls: SurfaceControls = SurfaceControls.Below,
        val crisp: Boolean = true,
        val accent: SkinColor? = null,
        /** null picks for the shape: whole with bars in a filled screen, stretched to a fixed shape like a crt did */
        val fit: SurfaceFit? = null,
        val fullscreen: SurfaceFullscreen = SurfaceFullscreen.Button,
        /** fields read from the module's settings: the field's name to the setting's key */
        val bound: Map<String, String> = emptyMap(),
    ) : SkinBlock {
        val fitting: SurfaceFit get() = fit ?: if (aspect.fill) SurfaceFit.Contain else SurfaceFit.Stretch

        /** the block with every bound field read from the settings as they are now, a setting that isn't a valid value keeping the fallback */
        fun with(values: Map<String, String>): Surface {
            if (bound.isEmpty()) return this
            fun read(field: String) = bound[field]?.let(values::get)
            return copy(
                controls = SurfaceOptions.controls(read(SurfaceOptions.Controls)) ?: controls,
                aspect = SurfaceOptions.aspect(read(SurfaceOptions.Aspect)) ?: aspect,
                fit = SurfaceOptions.fit(read(SurfaceOptions.Fit)) ?: fit,
                fullscreen = SurfaceOptions.fullscreen(read(SurfaceOptions.Fullscreen)) ?: fullscreen,
            )
        }
    }
    /** the bar under the controls that opens the lyrics and the queue, on frosted [glass] or the module's colours */
    data class BottomBar(
        val kind: BottomBarKind,
        val height: Dp,
        val corner: Dp,
        val fill: List<SkinColor>,
        val glass: Float?,
        val color: SkinColor?,
        val gap: Dp,
        val glassConfig: SkinGlassConfig? = null,
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
        val glassConfig: SkinGlassConfig? = null,
    ) : SkinBlock
    /** blocks in a box of their own that scrolls, down or sideways; with no [height] it takes the room the column has left */
    data class Scroll(val blocks: List<SkinBlock>, val gap: Dp, val height: Dp?, val row: Boolean) : SkinBlock
    /** mochy's own lyrics, the ones on its lyrics page with their flowing animation; with no [height] it takes the room left, [header] shows mochy's cover and title over them */
    data class MochyLyrics(val height: Dp?, val header: Boolean) : SkinBlock
    /** mochy's own queue with everything it does (drag to reorder, swipe away, its menus); with no [height] it takes the room left */
    data class MochyQueue(val height: Dp?, val header: Boolean) : SkinBlock
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
            is Scroll -> blocks.any { it.moves }
            else -> false
        }

    /** this block and every block inside it */
    fun walk(): List<SkinBlock> = listOf(this) + when (this) {
        is Styled -> inner.walk()
        is Row -> blocks.flatMap { it.walk() }
        is Column -> blocks.flatMap { it.walk() }
        is Stack -> blocks.flatMap { it.walk() }
        is Box -> blocks.flatMap { it.walk() }
        is Scroll -> blocks.flatMap { it.walk() }
        else -> emptyList()
    }
}

// ---- the module's own sheets ------------------------------------------------------------------

/** how a module sheet is drawn: in mochy's own sheet, or entirely the module's (its background, corners and height) */
enum class SheetLook { Mochy, Own }

/** a sheet of the module's own: [layout] is a player layout (background, blocks, scroll), so any block works inside it */
@Immutable
data class SkinSheet(
    val id: String,
    val look: SheetLook,
    val layout: SkinPlayer,
    /** its share of the screen's height, 0.2 to 1; null fits its blocks */
    val height: Float?,
    val corner: Dp,
    /** the veil over the player behind it */
    val dim: Float,
    /** the little bar at its top edge, an own-look sheet only */
    val handle: Boolean,
)

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
    /** extra background layers stacked over [background] */
    val layers: List<PageBgLayer> = emptyList(),
) {
    /** any block on the page asks for frosted glass or blur, so the page's background is captured for it */
    val frosted: Boolean get() = allBlocks.any { block ->
        when (block) {
            is PageBlock.Card -> block.glass != null
            is PageBlock.Blur -> true
            is PageBlock.Songs -> block.glass != null
            is PageBlock.NowPlaying -> block.glass != null
            is PageBlock.ActionButton -> block.glass != null
            is PageBlock.SettingsSearch -> block.glass != null
            is PageBlock.SettingsCategories -> block.glass != null
            is PageBlock.AppSetting -> block.glass != null
            is PageBlock.SettingsLink -> block.glass != null
            is PageBlock.ModuleSettingsPages -> block.glass != null
            is PageBlock.SearchBar -> block.glass != null
            is PageBlock.Stats -> block.glass != null
            is PageBlock.Tabs -> block.glass != null
            else -> false
        }
    }

    val allBlocks: List<PageBlock> by lazy { blocks.flatMap { it.walk() } }
}

/** what a page draws behind its blocks: colours, a picture, live covers, ambient or the module's own shader */
@Immutable
sealed interface PageBackground {
    data class Fill(val colors: List<SkinColor>, val gradient: GradientKind = GradientKind.Vertical) : PageBackground
    /** [blur] in dp (android 12+), [dim] a black veil so the words stay readable */
    data class Picture(val image: String, val blur: Float, val dim: Float) : PageBackground
    /** the page subject's cover art blurred behind the page */
    data class SubjectCover(val blur: Float = 36f, val dim: Float = 0.45f) : PageBackground
    /** the currently playing song's cover art blurred behind the page */
    data class PlayingCover(val blur: Float = 36f, val dim: Float = 0.45f) : PageBackground
    /** drifting ambient colours following the current artwork */
    data class Ambient(val dim: Float = 0.3f) : PageBackground
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
    data class Card(
        val blocks: List<PageBlock>,
        val colors: List<SkinColor>,
        val glass: Float?,
        val corner: Dp,
        val padding: Dp,
        val progressive: SkinBlurDirection? = null,
        val stroke: Dp? = null,
        val strokeColor: SkinColor? = null,
        val target: ActionTarget? = null,
    ) : PageBlock
    /** the module's own agsl in a box that answers touch, [fallback] colour where shaders can't run */
    data class Shader(val program: SkinShaderSource, val height: Dp, val corner: Dp, val fallback: SkinColor?) : PageBlock
    /** the picture of what the page took over: the playlist's, the album's, the artist's */
    data class Cover(val height: Dp, val corner: Dp, val circle: Boolean) : PageBlock
    /** live song list from the subject, user library, liked, downloaded, recent, queue, or top tracks */
    data class Songs(
        val limit: Int,
        val numbers: Boolean,
        val covers: Boolean,
        val corner: Dp,
        val source: SongSource = SongSource.Subject,
        val layout: MediaListLayout = MediaListLayout.List,
        val searchable: Boolean = false,
        val showMenu: Boolean = true,
        val empty: String? = null,
        val glass: Float? = null,
    ) : PageBlock
    /** a button that does something here: play or shuffle what the page took over, mochy's own page, or the player's editor bits */
    data class Control(val text: String, val kind: PageControl, val filled: Boolean) : PageBlock
    /** the module's player as it looks right now, small, for an editor page */
    data class Player(val height: Dp, val corner: Dp) : PageBlock
    /** the module's players and their sub-styles as chips, the picked one lit */
    data object Styles : PageBlock

    /** shows [inner] only when [condition] holds for the module's settings */
    data class Conditional(val inner: PageBlock, val condition: SkinCondition) : PageBlock
    /** a standalone frosted or progressive blur strip over the page background */
    data class Blur(
        val radius: Float,
        val direction: SkinBlurDirection? = null,
        val ramp: Float = 1f,
        val colors: List<SkinColor> = emptyList(),
        val height: Dp = 80.dp,
        val corner: Dp = 20.dp,
        val steps: Int = 5,
    ) : PageBlock
    data class Row(val blocks: List<PageBlock>, val gap: Dp = 10.dp) : PageBlock
    data class Column(val blocks: List<PageBlock>, val gap: Dp = 10.dp) : PageBlock
    data class Stack(val blocks: List<PageBlock>, val align: StackAlign = StackAlign.Center, val height: Dp? = null) : PageBlock
    /** editorial artist banner with motion cover, progressive edge blur, avatar, logo, stats, and play/like/subscribe actions */
    data class ArtistBanner(
        val artistId: String? = null,
        val height: Dp = 320.dp,
        val corner: Dp = 24.dp,
        val edgeBlur: Boolean = true,
        val motion: Boolean = true,
        val showAvatar: Boolean = true,
        val showLogo: Boolean = true,
        val showStats: Boolean = true,
        val showActions: Boolean = true,
        val scrim: Float = 0.25f,
    ) : PageBlock
    /** expandable artist bio / description card */
    data class Bio(val maxLines: Int = 4, val expandable: Boolean = true, val title: String? = null) : PageBlock
    /** live playlist list/grid/row with tap-to-open and native playlist menu sheet */
    data class Playlists(
        val source: PlaylistSource = PlaylistSource.Library,
        val layout: MediaListLayout = MediaListLayout.List,
        val limit: Int = 30,
        val columns: Int = 2,
        val covers: Boolean = true,
        val corner: Dp = 14.dp,
        val showCount: Boolean = true,
        val showMenu: Boolean = true,
        val empty: String? = null,
    ) : PageBlock
    /** live album list/grid/carousel */
    data class Albums(
        val source: MediaSource = MediaSource.Subject,
        val layout: MediaListLayout = MediaListLayout.Row,
        val limit: Int = 20,
        val columns: Int = 2,
        val corner: Dp = 14.dp,
        val showMenu: Boolean = true,
        val empty: String? = null,
    ) : PageBlock
    /** live artist list/grid/carousel */
    data class Artists(
        val source: MediaSource = MediaSource.Subject,
        val layout: MediaListLayout = MediaListLayout.Row,
        val limit: Int = 20,
        val columns: Int = 3,
        val empty: String? = null,
    ) : PageBlock
    /** live mini now-playing card embedded inside a custom page */
    data class NowPlaying(
        val glass: Float? = null,
        val corner: Dp = 22.dp,
        val showControls: Boolean = true,
        val showLyrics: Boolean = false,
    ) : PageBlock
    /** embedded synced flowing lyrics inside a custom page */
    data class Lyrics(
        val height: Dp = 200.dp,
        val size: TextUnit = 15.sp,
        val color: SkinColor = SkinColor.Ink,
        val active: SkinColor? = null,
        val blur: Float = 0f,
        val sweep: Boolean = true,
        val seek: Boolean = true,
        val flow: Boolean = true,
        val translation: Boolean = false,
    ) : PageBlock
    /** rich button calling any sandboxed ActionTarget: sheet, module page in sheet, route, control, search or link */
    data class ActionButton(
        val text: String,
        val target: ActionTarget,
        val icon: String? = null,
        val filled: Boolean = true,
        val colors: List<SkinColor> = emptyList(),
        val corner: Dp = 24.dp,
        val glass: Float? = null,
    ) : PageBlock
    /** live search across all of Mochy's settings */
    data class SettingsSearch(
        val placeholder: String? = null,
        val glass: Float? = null,
        val corner: Dp = 22.dp,
    ) : PageBlock
    /** Mochy's settings categories in a list, grid or row (always keeps Modules reachable) */
    data class SettingsCategories(
        val layout: MediaListLayout = MediaListLayout.List,
        val categories: List<String> = emptyList(),
        val columns: Int = 2,
        val glass: Float? = null,
        val colors: List<SkinColor> = emptyList(),
        val corner: Dp = 22.dp,
    ) : PageBlock
    /** one safe Mochy setting (or an entire safe settings category) rendered interactively */
    data class AppSetting(
        val settingKey: String? = null,
        val category: String? = null,
        val glass: Float? = null,
        val corner: Dp = 20.dp,
    ) : PageBlock
    /** row linking to a validated safe settings destination */
    data class SettingsLink(
        val target: String,
        val title: String? = null,
        val subtitle: String? = null,
        val icon: String? = null,
        val glass: Float? = null,
        val corner: Dp = 22.dp,
    ) : PageBlock
    /** embeds active modules' inSettings pages on a custom settings screen */
    data class ModuleSettingsPages(
        val glass: Float? = null,
        val corner: Dp = 22.dp,
    ) : PageBlock
    /** interactive search bar that submits queries to Mochy's search screen */
    data class SearchBar(
        val placeholder: String? = null,
        val glass: Float? = null,
        val corner: Dp = 24.dp,
    ) : PageBlock
    /** pinned speed dial tiles (songs, albums, artists, playlists) from the user's library */
    data class SpeedDial(
        val layout: MediaListLayout = MediaListLayout.Grid,
        val columns: Int = 3,
        val limit: Int = 9,
        val corner: Dp = 14.dp,
        val empty: String? = null,
    ) : PageBlock
    /** user's live listening stats summary (songs played, minutes, library counts) */
    data class Stats(
        val glass: Float? = null,
        val corner: Dp = 22.dp,
        val showLibrary: Boolean = true,
    ) : PageBlock
    /** segmented tab switcher bound to one of the module's own settings for multi-tab custom pages */
    data class Tabs(
        val settingKey: String,
        val items: List<Pair<String, String>>,
        val glass: Float? = null,
        val corner: Dp = 20.dp,
    ) : PageBlock

    fun walk(): List<PageBlock> = listOf(this) + when (this) {
        is Conditional -> inner.walk()
        is Card -> blocks.flatMap { it.walk() }
        is Row -> blocks.flatMap { it.walk() }
        is Column -> blocks.flatMap { it.walk() }
        is Stack -> blocks.flatMap { it.walk() }
        else -> emptyList()
    }
}

/** what a page's control button does */
enum class PageControl { Play, Shuffle, Original, UsePlayer }

// ---- the player's menu ------------------------------------------------------------------------

/** a row the module adds to the player's menu: it opens a link, one of the module's pages, a search, a sheet, a safe route, or a control */
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
    /** opens a native Mochy sheet, or a module page inside a PopupSheet when [kind] == [SheetKind.ModulePage] */
    data class Sheet(val kind: SheetKind, val pageId: String? = null) : ActionTarget
    /** navigates to a strict-allowlisted safe Mochy route */
    data class Route(val route: String) : ActionTarget
    /** invokes one of Mochy's built-in player/app controls */
    data class Control(val kind: ButtonKind) : ActionTarget
    /** toggles or sets one of the module's own settings (when [value] is null, toggles "true"/"false") */
    data class SetField(val key: String, val value: String? = null) : ActionTarget
    /** toggles or sets an allowlisted safe Mochy app setting (validated against SafeAppSettings) */
    data class SetAppSetting(val key: String, val value: String? = null) : ActionTarget
    /** opens one of the module's own sheets over the player */
    data class ModuleSheet(val id: String) : ActionTarget
}
