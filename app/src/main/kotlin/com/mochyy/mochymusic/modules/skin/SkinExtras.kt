// extra immutable skin models: custom bubble menus, floating lyrics overlay skins, custom sheet styling, app-wide backgrounds, progressive blur config and media list modes

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** built-in sheets (or a module's own page shown as a sheet) that buttons, menu items and page blocks can open */
enum class SheetKind {
    PlayerMenu,
    SleepTimer,
    Output,
    Speed,
    SongDetails,
    StreamInfo,
    Share,
    AddToPlaylist,
    LyricsProvider,
    LyricsOffset,
    LyricsTranslation,
    LyricsEditor,
    Customise,
    CreatePlaylist,
    SongMenu,
    PlaylistMenu,
    ArtistMenu,
    SongEdit,
    VideoQuality,
    CanvasQuality,
    VideoPicker,
    Devices,
    EditPlayer,
    ModulePage,
}

/** how the player's chevron / "more options" bubble popup arranges its items */
enum class SkinMenuLayout { Stack, Pill, Card, Grid }

/** which way a progressive blur block or frosted glass surface ramps its radius */
enum class SkinBlurDirection { TopToBottom, BottomToTop, Radial }

/** how song, playlist, album, artist or settings lists lay out on custom pages */
enum class MediaListLayout { List, Grid, Row, Compact }

/** where a page's song list pulls its tracks from */
enum class SongSource { Subject, Library, Liked, Downloaded, Recent, Queue, Top }

/** where a page's playlist list pulls its playlists from */
enum class PlaylistSource { Library, Subject, Auto, All }

/** where a page's album or artist list pulls its items from */
enum class MediaSource { Subject, Library, Liked }

/** detailed glass / progressive blur configuration for boxes, cards and bottom bars */
@Immutable
data class SkinGlassConfig(
    val blur: Float,
    val progressive: SkinBlurDirection? = null,
    val ramp: Float = 1f,
    val tint: SkinColor? = null,
    val bleed: Boolean = true,
    val outline: Boolean = false,
    val shader: SkinShaderSource? = null,
)

/** custom styling for the player's chevron / bubble popup menu */
@Immutable
data class SkinMenuStyle(
    val layout: SkinMenuLayout = SkinMenuLayout.Stack,
    val dim: Float = 0.72f,
    val backdropBlur: Float = 0f,
    val glass: Float? = null,
    val fill: List<SkinColor> = emptyList(),
    val activeFill: SkinColor? = null,
    val ink: SkinColor? = null,
    val labelColor: SkinColor? = null,
    val labelFill: SkinColor? = null,
    val corner: Dp? = null,
    val stroke: Dp? = null,
    val strokeColor: SkinColor? = null,
    val size: Dp? = null,
    val gap: Dp = 10.dp,
    val atBottom: Boolean = false,
    val showDefaults: Boolean = true,
    val triggerIcon: String? = null,
    val columns: Int = 2,
)

/** custom styling for mochy's system-overlay floating lyrics bubble */
@Immutable
data class SkinFloating(
    val bubble: List<SkinColor> = emptyList(),
    val gradient: GradientKind = GradientKind.Horizontal,
    val ink: SkinColor? = null,
    val waiting: SkinColor? = null,
    val soft: SkinColor? = null,
    val corner: Dp = 18.dp,
    val tail: Boolean = true,
    val border: Dp? = null,
    val borderColor: SkinColor? = null,
    val coverShape: CoverShape = CoverShape.Circle,
    val coverCorner: Dp = 14.dp,
    val opacity: Float = 1f,
    val blur: Float = 0f,
    val fontSize: Float? = null,
    val weight: FontWeight? = null,
    val shadow: Boolean = true,
    val widthScale: Float = 1f,
    val progressColor: SkinColor? = null,
    val trackColor: SkinColor? = null,
)

/** custom styling for bottom sheets (MenuSheetSurface and PopupSheet), clamped for legibility */
@Immutable
data class SkinSheetStyle(
    val surface: SkinColor? = null,
    val rowSurface: SkinColor? = null,
    val ink: SkinColor? = null,
    val accent: SkinColor? = null,
    val corner: Dp? = null,
    val rowCorner: Dp? = null,
    val dim: Float? = null,
    val glass: Float? = null,
    val stroke: Dp? = null,
    val strokeColor: SkinColor? = null,
)

/** an extra background layer composited over a page or app background */
@Immutable
data class PageBgLayer(
    val background: PageBackground,
    val opacity: Float = 1f,
)

/** app-wide custom background behind non-sensitive screens; empty [screens] applies to all safe screens */
@Immutable
data class SkinAppBackground(
    val background: PageBackground,
    val layers: List<PageBgLayer> = emptyList(),
    val dim: Float = 0.35f,
    val opacity: Float = 1f,
    val screens: Set<String> = emptySet(),
)
