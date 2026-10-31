package com.mochyy.mochymusic.ui.player.styles

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.playerStyleRotatedKey
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.mochyy.mochymusic.ui.player.flagship.AnimatedPlayGlyph
import com.mochyy.mochymusic.ui.player.flagship.AnimatedSkipGlyph
import com.mochyy.mochymusic.ui.player.flagship.SkipCycleState
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Whether a style takes touches: only on the open player. Collapsed into the mini pill the style
 * is still composed under it, and its gestures would swallow the swipe and tap that open the player.
 *
 * Asked when a touch lands ([whileStyleInteractive]), never in composition. As a plain value it
 * changed on the first frame of every close and rebuilt the whole style just as the sheet began to move.
 */
val LocalStyleInteractive = androidx.compose.runtime.compositionLocalOf<() -> Boolean> { { true } }

/**
 * Runs [detect] only while the style takes touches ([interactive]). The rest of the time nothing
 * here listens, so a touch goes on to the player. A flow starts and stops it, so the player opening
 * or closing recomposes nothing.
 */
internal suspend fun PointerInputScope.whileStyleInteractive(
    interactive: () -> Boolean,
    detect: suspend PointerInputScope.() -> Unit,
) {
    snapshotFlow { interactive() }.collectLatest { on -> if (on) detect() }
}

/**
 * The two tones every alternate style is built from, taken from the cover: [played] is the part of
 * the song that has played, [remaining] the darker part still to come, and the line between them
 * is the playhead. Both are the cover's hue held quiet (low saturation), so big flat areas of it
 * stay easy on the eye while still belonging to the song.
 */
@Immutable
data class StyleTones(
    val played: Color,
    val remaining: Color,
    val ink: Color,
    val inkSoft: Color,
    val chip: Color,
)

@Composable
fun rememberStyleTones(): StyleTones {
    val seed = LocalArtworkColor.current
    return remember(seed) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed.toArgb(), hsl)
        val saturation = (hsl[1] * 0.42f).coerceIn(0.06f, 0.24f)
        fun tone(lightness: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], saturation, lightness)))
        StyleTones(
            played = tone(0.45f),
            remaining = tone(0.30f),
            ink = Color(0xFFF1ECEC),
            inkSoft = Color(0xFFF1ECEC).copy(alpha = 0.62f),
            chip = Color.White.copy(alpha = 0.22f),
        )
    }
}

/**
 * Soft light over a flat tone: brighter toward [light], a little darker at the far edge. Drawn
 * behind content in one pass, so the large colour fields read as lit surfaces rather than paint.
 */
fun Modifier.shadedTone(color: Color, light: Offset = Offset(0.15f, 0.05f)): Modifier = drawBehind {
    drawRect(color)
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * light.x, size.height * light.y),
            radius = size.maxDimension * 0.9f,
        )
    )
    drawRect(
        Brush.verticalGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.16f),
        )
    )
}

/**
 * The playback position on every frame, for the parts of a style that move with it (the playhead
 * boundary and the timestamps riding on it). Read it inside draw, layout or graphicsLayer blocks
 * only: those re-run each frame without recomposing anything.
 */
@Stable
class LivePosition internal constructor(private val read: () -> Long, private val tick: () -> Long) {
    private var readAt = Long.MIN_VALUE
    private var value = 0L

    /**
     * ms into the song. reading it subscribes the caller's phase to the frame tick. it's read from
     * the player once per tick, so every part drawn in one frame moves from the same moment
     */
    fun positionMs(): Long {
        val now = tick()
        if (now != readAt) {
            readAt = now
            value = read()
        }
        return value
    }

    /** The same clock moved by [shiftMs], so lyrics can run early or late without touching the rest. */
    fun shifted(shiftMs: () -> Long) = LivePosition(read = { read() + shiftMs() }, tick = tick)
}

/** The playback clock the lyrics follow: [this] plus the song's own lyrics offset. */
@Composable
fun LivePosition.withLyricsOffset(): LivePosition {
    val connection = LocalPlayerConnection.current ?: return this
    return remember(this, connection) { shifted { connection.lyricsOffsetMs.value.toLong() } }
}

/**
 * [animated] false (a selector card's preview): it follows the player's own updates, not every frame.
 *
 * The player's updates are followed in an effect, not in composition: they come 15 times a second,
 * and reading them here rebuilt the whole style that often, through every open and close too.
 * Collapsed into the mini player the style can't be seen, so the clock waits for it to open again.
 */
@Composable
fun rememberLivePosition(animated: Boolean = true): LivePosition {
    val connection = LocalPlayerConnection.current
    val tick = remember { mutableLongStateOf(0L) }
    val showing = rememberStyleShowing()
    LaunchedEffect(connection, animated, showing) {
        val ui = connection?.uiState ?: return@LaunchedEffect
        combine(
            ui.transportState.map { it.isPlaying }.distinctUntilChanged(),
            ui.progressState.map { it.position }.distinctUntilChanged(),
            snapshotFlow { showing() },
        ) { playing, _, shown -> playing to shown }
            .collectLatest { (playing, shown) ->
                if (!shown) return@collectLatest
                // a seek or a pause still moves the boundary once; playing, it moves every frame
                tick.longValue++
                while (playing && animated) withFrameMillis { tick.longValue = it }
            }
    }
    return remember(connection) {
        LivePosition(read = { connection?.player?.currentPosition ?: 0L }, tick = { tick.longValue })
    }
}

/**
 * Whether any of the open player is on screen. A style is still composed while it's collapsed into
 * the mini player, and holds its clocks still there. Read it in effects and gestures, not composition.
 */
@Composable
internal fun rememberStyleShowing(): () -> Boolean {
    val sheet = LocalPlayerBottomSheetState.current
    return remember(sheet) { { sheet == null || sheet.progress > 0f } }
}

/** the song's length in ms, never 0 */
@Composable
fun rememberDurationMs(): Long {
    val connection = LocalPlayerConnection.current ?: return 1L
    // the length alone: the whole progress state changes 15 times a second, and recomposed the style with it
    val duration by remember(connection) {
        connection.uiState.progressState.map { it.duration }.distinctUntilChanged()
    }.collectAsState(initial = connection.uiState.progressState.value.duration)
    return duration.takeIf { it > 0L } ?: 1L
}

/** the whole seconds played, recomposing once a second, for the big timestamp */
@Composable
fun rememberPlayedSeconds(position: LivePosition): Long {
    val seconds by remember(position) { derivedStateOf { position.positionMs() / 1_000L } }
    return seconds
}

/**
 * The played and remaining tones, split at the playhead: across the screen top to bottom when
 * [vertical] (played on top), left to right otherwise. [boundary] maps the playhead fraction to
 * where the line is drawn, so a style can keep it clear of its header and its controls.
 */
fun Modifier.progressSplit(
    tones: StyleTones,
    vertical: Boolean,
    boundary: () -> Float,
): Modifier = drawBehind { drawSplit(tones, vertical, boundary()) }

/** a small round button: the alternate styles' only controls */
@Composable
fun StyleCircle(
    @DrawableRes icon: Int = 0,
    tones: StyleTones,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    enabled: Boolean = true,
    /** dressed as the Turntable's pills (a faint white fill, a white icon), for a style wearing them */
    pill: Boolean = false,
    /** a toggle that's on (shuffle, repeat): a brighter fill */
    active: Boolean = false,
    // drawn in place of the icon, in the icon's colour: the transport's animated glyphs
    glyph: (@Composable (Color) -> Unit)? = null,
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactive = LocalStyleInteractive.current
    // the tap handler below lives as long as its pointerInput, so it calls the latest onClick
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
                alpha = if (enabled) 1f else 0.4f
            }
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    active -> ActiveFill
                    pill -> PillFill
                    else -> tones.chip
                }
            )
            .pointerInput(enabled, interactive) {
                if (!enabled) return@pointerInput
                whileStyleInteractive(interactive) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { press.animateTo(0.86f, spring(dampingRatio = 0.6f, stiffness = 900f)) }
                            tryAwaitRelease()
                            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                        },
                        onTap = { currentOnClick() },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            active -> Color.White
            pill -> Color.White.copy(alpha = 0.9f)
            else -> tones.ink
        }
        if (glyph != null) {
            glyph(tint)
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (pill) 20.dp else size * 0.5f),
            )
        }
    }
}

/** the Turntable's pill fill */
private val PillFill = Color.White.copy(alpha = 0.08f)
/** a toggle that's on, as bright as the flagship's chevron menu shows one */
private val ActiveFill = Color.White.copy(alpha = 0.32f)

/** one button in a style's chevron menu, with its name beside it */
private class MenuItem(
    val label: String,
    @DrawableRes val icon: Int,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

// the glyphs' size in a circle: the play triangle about as tall as the old icon, the skip pair a little wider than it
private const val CirclePlayHeight = 0.48f
private const val CircleSkipWidth = 0.46f

/** previous, play/pause and next as three [StyleCircle]s, laid out by the caller's [Row] or [Column] */
@Composable
fun TransportCircles(tones: StyleTones, size: Dp = 30.dp, vertical: Boolean = false, spacing: Dp = 8.dp) {
    val connection = LocalPlayerConnection.current ?: return
    val transport by connection.uiState.transportState.collectAsState()
    val scope = rememberCoroutineScope()
    // the flagship's running triangles, each a step on with every press
    val previous = remember { SkipCycleState() }
    val next = remember { SkipCycleState() }
    val content: @Composable () -> Unit = {
        StyleCircle(
            tones = tones,
            size = size,
            enabled = transport.canSkipPrevious,
            onClick = {
                scope.launch { previous.step() }
                connection.seekToPrevious()
            },
            glyph = { tint -> AnimatedSkipGlyph(cycle = previous, backwards = true, color = tint, width = size * CircleSkipWidth) },
        )
        StyleCircle(
            tones = tones,
            size = size,
            glyph = { tint -> AnimatedPlayGlyph(playing = transport.isPlaying, color = tint, height = size * CirclePlayHeight) },
            onClick = {
                if (transport.playbackState == Player.STATE_ENDED) {
                    connection.player.seekTo(0, 0)
                    connection.player.playWhenReady = true
                } else {
                    connection.togglePlayPause()
                }
            },
        )
        StyleCircle(
            tones = tones,
            size = size,
            enabled = transport.canSkipNext,
            onClick = {
                scope.launch { next.step() }
                connection.seekToNext()
            },
            glyph = { tint -> AnimatedSkipGlyph(cycle = next, backwards = false, color = tint, width = size * CircleSkipWidth) },
        )
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { content() }
    }
}

/** what a style's ••• menu can do, wired by the player */
@Immutable
class StyleActions(
    val onOtherMenu: () -> Unit,
    val onEditPlayer: () -> Unit,
    /** opens the song's whole lyrics over the style; no item without it (the selector's cards) */
    val onLyrics: (() -> Unit)? = null,
    // the full video mode's way back to the style it was opened from
    val onExitVideoMode: () -> Unit = {},
    // the song info sheet's artist and album rows: the page opens and the player closes on the way
    val onOpenArtist: (String) -> Unit = {},
    val onOpenAlbum: (String) -> Unit = {},
)

/** whether [style] is turned sideways, and the switch for it */
@Composable
fun rememberStyleRotation(style: PlayerStyle): Pair<Boolean, (Boolean) -> Unit> {
    val (rotated, onRotated) = rememberPreference(playerStyleRotatedKey(style), defaultValue = false)
    return rotated to onRotated
}

/**
 * Double tap plays or pauses; a sideways swipe slides the player's row of songs under the finger
 * (see SongCarousel), on to the next song or back to the last when it's let go. The alternate
 * styles have no big transport buttons, so the whole screen is the transport. Only while
 * [interactive] says the player is open.
 */
fun Modifier.styleGestures(
    enabled: Boolean,
    interactive: () -> Boolean,
    onToggle: () -> Unit,
    carousel: com.mochyy.mochymusic.ui.player.SongCarousel?,
    player: () -> androidx.media3.common.Player?,
): Modifier =
    if (!enabled) this else this
        .pointerInput(enabled, interactive) {
            whileStyleInteractive(interactive) { detectTapGestures(onDoubleTap = { onToggle() }) }
        }
        .then(
            if (carousel == null) Modifier else Modifier.pointerInput(enabled, interactive, carousel) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                val flingPx = SwipeFling.toPx()
                var travelled = 0f
                var hasPrevious = false
                var hasNext = false
                // whether the row is this swipe's to put back if the swipe is cut off
                var mine = false
                whileStyleInteractive(interactive) {
                    try {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                travelled = 0f
                                tracker.resetTracking()
                                val p = player()
                                hasPrevious = p?.hasPreviousMediaItem() == true
                                hasNext = p?.hasNextMediaItem() == true
                                carousel.startDrag()
                                mine = true
                            },
                            onDragEnd = {
                                mine = false
                                carousel.release(travelled, tracker.calculateVelocity().x, size.width.toFloat(), flingPx, player())
                            },
                            onDragCancel = {
                                mine = false
                                carousel.cancelDrag()
                            },
                            onHorizontalDrag = { change, amount ->
                                tracker.addPointerInputChange(change)
                                travelled += amount
                                carousel.dragTo(travelled, size.width.toFloat(), hasPrevious, hasNext)
                            },
                        )
                    } finally {
                        if (mine) {
                            mine = false
                            carousel.cancelDrag()
                        }
                    }
                }
            }
        )

/** A flick faster than this skips however short it was. */
private val SwipeFling = 500.dp

/**
 * The chevron and the little menu it opens: lyrics, shuffle and repeat, other menu, rotate and
 * edit player, rising out of the button the way the flagship's chevron menu does. The button sits
 * [edge] from the top (or the bottom, [atBottom]) and [end] from the right; the menu opens away
 * from that edge.
 * Call it last in the style's Box so the dimmed backdrop and the menu draw over the style.
 */
@Composable
fun BoxScope.StyleMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    tones: StyleTones,
    actions: StyleActions,
    rotated: Boolean,
    onRotate: (Boolean) -> Unit,
    atBottom: Boolean,
    end: Dp,
    edge: Dp,
    buttonSize: Dp = 30.dp,
    showButton: Boolean = true,
    /** the button and the menu's circles dressed as the Turntable's pills */
    pill: Boolean = false,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(open) {
        reveal.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    if (reveal.value > 0.001f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.42f * reveal.value.coerceIn(0f, 1f))) }
                .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
        )
        val connection = LocalPlayerConnection.current
        val transport = connection?.uiState?.transportState?.collectAsState()?.value
        val rows = listOfNotNull(
            actions.onLyrics?.let { open -> listOf(MenuItem("Lyrics", R.drawable.lyrics) { onOpenChange(false); open() }) },
            // the styles have no shuffle or repeat buttons of their own. The two share a row so the
            // menu still fits a turned style, and stay open when tapped so the change shows.
            if (connection != null && transport != null) listOf(
                MenuItem("Shuffle", R.drawable.shuffle, active = transport.shuffleModeEnabled) {
                    connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
                },
                MenuItem(
                    label = if (transport.repeatMode == Player.REPEAT_MODE_ONE) "Repeat one" else "Repeat",
                    icon = if (transport.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                    active = transport.repeatMode != Player.REPEAT_MODE_OFF,
                ) { connection.player.toggleRepeatMode() },
            ) else null,
            listOf(MenuItem("Other menu", R.drawable.more_horiz) { onOpenChange(false); actions.onOtherMenu() }),
            listOf(MenuItem(if (rotated) "Turn upright" else "Rotate", R.drawable.screen_rotation) { onOpenChange(false); onRotate(!rotated) }),
            listOf(MenuItem("Edit player", R.drawable.tune) { onOpenChange(false); actions.onEditPlayer() }),
        )
        val itemGap = 10.dp
        Column(
            modifier = Modifier
                .align(if (atBottom) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(
                    end = end - 3.dp,
                    top = if (atBottom) 0.dp else edge + buttonSize + itemGap,
                    bottom = if (atBottom) edge + buttonSize + itemGap else 0.dp,
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(itemGap),
        ) {
            rows.forEachIndexed { index, row ->
                // nearest the button first
                val order = if (atBottom) rows.size - 1 - index else index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.graphicsLayer {
                        val delay = order * 0.12f
                        val p = ((reveal.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                        alpha = p.coerceIn(0f, 1f)
                        val away = if (atBottom) 1f else -1f
                        translationY = (1f - p) * 28.dp.toPx() * away
                        val s = 0.7f + 0.3f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    },
                ) {
                    row.forEachIndexed { k, item ->
                        if (k > 0) Spacer(Modifier.width(4.dp))
                        Text(text = item.label, color = if (pill) Color.White else tones.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.05).em, fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily)
                        StyleCircle(icon = item.icon, tones = tones, size = buttonSize + 6.dp, onClick = item.onClick, pill = pill, active = item.active)
                    }
                }
            }
        }
    }
    if (showButton) {
        Box(
            modifier = Modifier
                .align(if (atBottom) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(end = end, top = if (atBottom) 0.dp else edge, bottom = if (atBottom) edge else 0.dp),
        ) {
            MenuChevron(open, tones, buttonSize, pill, opensDown = !atBottom, onOpenChange)
        }
    }
}

/**
 * The menu's button: a chevron, as on the Turntable, pointing the way the menu opens and flipping
 * round once it's open.
 */
@Composable
private fun MenuChevron(open: Boolean, tones: StyleTones, size: Dp, pill: Boolean, opensDown: Boolean, onOpenChange: (Boolean) -> Unit) {
    val turn = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) { turn.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    StyleCircle(
        icon = R.drawable.expand_less,
        tones = tones,
        size = size,
        pill = pill,
        onClick = { onOpenChange(!open) },
        // up-pointing art: turned to point down at rest when the menu drops below it
        modifier = Modifier.graphicsLayer { rotationZ = (if (opensDown) 180f else 0f) + turn.value * 180f },
    )
}

/**
 * Where each orientation of a style puts its playhead split, as a share of the screen from the top.
 * Upright the split runs down the screen; sideways it runs along the turned layout, which on the
 * phone is down the screen too - so one split, drawn by the frame outside the turn, serves both,
 * and the background never turns with the layout.
 */
@Stable
class StyleSplits {
    var upright by mutableStateOf<(() -> Float)?>(null)
    var sideways by mutableStateOf<(() -> Float)?>(null)
    operator fun get(landscape: Boolean) = if (landscape) sideways else upright
    operator fun set(landscape: Boolean, value: (() -> Float)?) {
        if (landscape) sideways = value else upright = value
    }
}

internal val LocalStyleSplits = androidx.compose.runtime.compositionLocalOf<StyleSplits?> { null }

/** a layout reporting where its split is, for the frame to draw it */
@Composable
internal fun ReportSplit(landscape: Boolean, boundary: () -> Float) {
    val splits = LocalStyleSplits.current ?: return
    val current by androidx.compose.runtime.rememberUpdatedState(boundary)
    androidx.compose.runtime.DisposableEffect(splits, landscape) {
        val read = { current() }
        splits[landscape] = read
        onDispose { if (splits[landscape] === read) splits[landscape] = null }
    }
}

/** the played/remaining split as [progressSplit] draws it, into any draw scope */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSplit(tones: StyleTones, vertical: Boolean, boundary: Float) =
    drawSplit(tones.played, tones.remaining, vertical, boundary)

/** the split in two given colours, for a frame whose tones are fading to the next song's */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSplit(
    played: Color,
    remaining: Color,
    vertical: Boolean,
    boundary: Float,
) {
    val b = boundary.coerceIn(0f, 1f)
    if (vertical) {
        val y = size.height * b
        drawRect(played, size = size.copy(height = y))
        drawRect(remaining, topLeft = Offset(0f, y), size = size.copy(height = size.height - y))
        // light falls on the played part from the top, the line itself catches a highlight
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), endY = y.coerceAtLeast(1f)),
            size = size.copy(height = y),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                startY = y,
                endY = y + size.height * 0.18f,
            ),
            topLeft = Offset(0f, y),
            size = size.copy(height = size.height * 0.18f),
        )
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, y - 1f), size = size.copy(height = 1.5f))
    } else {
        val x = size.width * b
        drawRect(played, size = size.copy(width = x))
        drawRect(remaining, topLeft = Offset(x, 0f), size = size.copy(width = size.width - x))
        drawRect(
            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), endX = x.coerceAtLeast(1f)),
            size = size.copy(width = x),
        )
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                startX = x,
                endX = x + size.width * 0.14f,
            ),
            topLeft = Offset(x, 0f),
            size = size.copy(width = size.width * 0.14f),
        )
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(x - 1f, 0f), size = size.copy(width = 1.5f))
    }
}

/**
 * Lays [content] out sideways when [rotated]: measured with width and height swapped and turned a
 * quarter clockwise, so the style reads upright with the phone held on its side.
 *
 * Changing it is a real turn: the layout leaving and the layout arriving are both drawn, turning
 * together through the whole quarter as one piece, shrunk just enough at each angle to stay on the
 * screen, tipped back a little in depth and motion-blurred at the fastest part of the swing, the
 * one handing over to the other through it. [background] sits under the turn and holds still: the
 * style's own tone, or its playhead split, which the layouts report through [ReportSplit] and which
 * glides from where the old layout had it to where the new one does.
 */
@Composable
fun RotatingFrame(
    rotated: Boolean,
    tones: StyleTones,
    modifier: Modifier = Modifier,
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(landscape: Boolean) -> Unit,
) {
    var shown by remember { mutableStateOf(rotated) }
    // the orientation turning away, only while a turn plays
    var leaving by remember { mutableStateOf<Boolean?>(null) }
    val turn = remember { Animatable(1f) }
    val splits = remember { StyleSplits() }
    // A new song's tones fade in rather than snap: the split is the whole background of these
    // styles, and it used to change colour in one frame. Read only while drawing, so the fade
    // repaints the background without recomposing the style.
    val played = androidx.compose.animation.animateColorAsState(tones.played, tween(ToneFadeMs), label = "playedTone")
    val remaining = androidx.compose.animation.animateColorAsState(tones.remaining, tween(ToneFadeMs), label = "remainingTone")
    LaunchedEffect(rotated) {
        if (rotated == shown) return@LaunchedEffect
        leaving = shown
        shown = rotated
        turn.snapTo(0f)
        // a little weight at the end: it settles a hair past square and comes back
        turn.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 150f))
        leaving = null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (background != null) Modifier else Modifier.drawBehind {
                    val now = splits[shown]
                    if (now == null) {
                        drawRect(played.value)
                        return@drawBehind
                    }
                    val from = leaving?.let { splits[it] }
                    val t = turn.value.coerceIn(0f, 1f)
                    val b = if (from != null) from() + (now() - from()) * t else now()
                    drawSplit(played.value, remaining.value, vertical = true, boundary = b)
                }
            ),
    ) {
        if (background != null) {
            Box(modifier = Modifier.fillMaxSize(), content = background)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalStyleSplits provides splits) {
            val layers = listOfNotNull(leaving, shown)
            layers.forEach { orientation ->
                androidx.compose.runtime.key(orientation) {
                    val arriving = orientation == shown
                    Box(
                        modifier = Modifier
                            .quarterTurn(orientation)
                            .graphicsLayer {
                                if (leaving == null) return@graphicsLayer
                                val t = turn.value
                                // toward landscape the turn is clockwise, back upright counter-clockwise
                                val direction = if (shown) 1f else -1f
                                // this layer's angle away from where it rests
                                val angle = if (arriving) -direction * 90f * (1f - t) else direction * 90f * t
                                rotationZ = angle
                                val fit = turnFit(size.width, size.height, if (orientation) 90f else 0f, angle)
                                val s = fit * (1f - 0.04f * sin01(t))
                                scaleX = s
                                scaleY = s
                                // tipped back a touch mid-swing, so it turns in space rather than on glass
                                cameraDistance = 14f * density
                                rotationX = 9f * sin01(t)
                                // the hand-over: the old fades through the first half, the new through the second
                                alpha = if (arriving) smooth((t - 0.3f) / 0.45f) else 1f - smooth((t - 0.1f) / 0.45f)
                                if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    val blur = 14.dp.toPx() * sin01(t)
                                    renderEffect = if (blur > 0.5f && com.mochyy.mochymusic.ui.utils.AppMotion.elementBlur) {
                                        androidx.compose.ui.graphics.BlurEffect(blur, blur, androidx.compose.ui.graphics.TileMode.Clamp)
                                    } else null
                                }
                            },
                    ) {
                        content(orientation)
                    }
                }
            }
        }
    }
}

/** how long a style's background takes to fade to a new song's colours */
private const val ToneFadeMs = 700

/**
 * How far a layer [w] x [h] (as laid out, before its own [restAngle]) must shrink, turned a further
 * [angle] degrees, to stay inside the screen it fills at rest.
 */
internal fun turnFit(w: Float, h: Float, restAngle: Float, angle: Float): Float {
    if (w <= 0f || h <= 0f) return 1f
    // the screen: the layer's own size, turned by its rest angle
    val screenW = if (restAngle == 0f) w else h
    val screenH = if (restAngle == 0f) h else w
    val a = Math.toRadians((restAngle + angle).toDouble())
    val c = abs(kotlin.math.cos(a)).toFloat()
    val s = abs(kotlin.math.sin(a)).toFloat()
    val extentW = w * c + h * s
    val extentH = w * s + h * c
    return minOf(screenW / extentW, screenH / extentH, 1f)
}

internal fun sin01(t: Float): Float = kotlin.math.sin(Math.PI.toFloat() * t.coerceIn(0f, 1f))

internal fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** measures with width and height swapped and turns the result a quarter clockwise, about its centre */
fun Modifier.quarterTurn(enabled: Boolean): Modifier = layout { measurable, constraints ->
    if (!enabled || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(h, w))
        layout(w, h) {
            placeable.placeWithLayer((w - h) / 2, (h - w) / 2) { rotationZ = 90f }
        }
    }
}

/**
 * Sizes that follow the screen: a share of its short side, held between sensible bounds, so the
 * styles and the editor look the same on a small phone and a big one.
 */
@Immutable
class Responsive(val shortSideDp: Float) {
    fun dp(fraction: Float, min: Dp, max: Dp): Dp = (shortSideDp * fraction).dp.coerceIn(min, max)
    fun sp(fraction: Float, min: Float, max: Float): androidx.compose.ui.unit.TextUnit =
        (shortSideDp * fraction).coerceIn(min, max).sp
}

@Composable
fun rememberResponsive(): Responsive {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val short = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    return remember(short) { Responsive(short) }
}

/** a colour between two tones, for the bits that cross the playhead */
fun toneBetween(a: Color, b: Color, t: Float): Color = lerp(a, b, t.coerceIn(0f, 1f))
