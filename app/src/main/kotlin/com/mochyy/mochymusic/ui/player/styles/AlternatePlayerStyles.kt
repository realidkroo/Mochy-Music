package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.ui.player.carouselText
import com.mochyy.mochymusic.ui.utils.stableStatusBars

/** the player's alternate styles, drawn as they look on the player or, [preview], on a selector card */
@Composable
fun AlternatePlayerStyle(
    style: PlayerStyle,
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    when (style) {
        PlayerStyle.POSTER -> PosterPlayerStyle(actions, modifier, preview)
        PlayerStyle.TIMELINE -> TimelinePlayerStyle(actions, modifier, preview)
        PlayerStyle.LYRICS -> LyricsPlayerStyle(actions, modifier, preview)
        PlayerStyle.IPOD -> com.mochyy.mochymusic.ui.player.ipod.IpodPlayerStyle(modifier, preview)
        PlayerStyle.VIDEO -> com.mochyy.mochymusic.ui.player.video.VideoPlayerStyle(actions, modifier, preview)
        PlayerStyle.BEDSIDE -> com.mochyy.mochymusic.ui.player.bedside.BedsidePlayerStyle(actions, modifier, preview)
        PlayerStyle.BEDSIDE_TILES -> com.mochyy.mochymusic.ui.player.bedside.BedsideTilesStyle(actions, modifier, preview)
        PlayerStyle.MODULE -> ModulePlayerStyle(actions, modifier, preview)
        PlayerStyle.FLAGSHIP, PlayerStyle.CLASSIC, PlayerStyle.TURNTABLE -> Unit
    }
}

/** what every alternate style reads from the player */
internal class StyleTrack(val title: String, val artist: String, val album: String, val artwork: String?)

@Composable
internal fun rememberStyleTrack(): StyleTrack {
    val connection = LocalPlayerConnection.current ?: return StyleTrack("", "", "", null)
    val track by connection.uiState.trackInfo.collectAsState()
    return remember(track) { StyleTrack(track.title, track.artist, track.album, track.thumbnailUrl) }
}

/**
 * The player's row of songs on the live player; a selector card shows the playing song, still.
 */
@Composable
internal fun rememberStyleCarousel(preview: Boolean): com.mochyy.mochymusic.ui.player.SongCarousel? =
    if (preview) null else com.mochyy.mochymusic.ui.player.LocalSongCarousel.current

/** a song's synced lines, and which song they're for */
internal class StyleLyrics(val mediaId: String?, val lines: List<LyricsEntry>)

/** what a style shows for one song in the row: its details, and its lyrics once it has played */
internal class StyleSong(val track: StyleTrack, val lines: List<LyricsEntry>)

/**
 * [content] once per song on screen, riding the row: the playing song, and the one coming in or
 * going out while a swipe or a skip slides them. Each song keeps the lyrics it had while it played,
 * so the one leaving goes with its own words, and a song only takes lyrics that are its own.
 * Without a row (a selector card) it's the playing song alone.
 */
@Composable
internal fun StyleSongs(
    carousel: com.mochyy.mochymusic.ui.player.SongCarousel?,
    track: StyleTrack,
    lyrics: StyleLyrics,
    content: @Composable (song: StyleSong, slotModifier: Modifier) -> Unit,
) {
    if (carousel == null) {
        content(StyleSong(track, lyrics.lines), Modifier)
        return
    }
    for (slot in com.mochyy.mochymusic.ui.player.rememberTextSlots(carousel)) {
        androidx.compose.runtime.key(slot.uid) {
            val playing = slot.step == carousel.step
            val held = remember { arrayOf(emptyList<LyricsEntry>()) }
            if (playing && lyrics.mediaId == slot.mediaId) held[0] = lyrics.lines
            val details = StyleTrack(
                title = slot.title.ifEmpty { if (playing) track.title else "" },
                artist = slot.artist.ifEmpty { if (playing) track.artist else "" },
                album = slot.album,
                artwork = slot.artworkUrl,
            )
            content(StyleSong(details, held[0]), Modifier.carouselText(carousel, slot))
        }
    }
}

/** the frame every style is drawn in: tones, gestures, rotation, the ••• menu's state */
@Composable
internal fun StyleFrame(
    style: PlayerStyle,
    preview: Boolean,
    modifier: Modifier,
    tones: StyleTones,
    /** what lies under the style and holds still when it turns; the playhead split when null */
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(landscape: Boolean, menuOpen: Boolean, setMenuOpen: (Boolean) -> Unit, rotated: Boolean, setRotated: (Boolean) -> Unit) -> Unit,
) {
    val connection = LocalPlayerConnection.current
    val (rotated, setRotated) = rememberStyleRotation(style)
    var menuOpen by remember { mutableStateOf(false) }
    // turned sideways, its lists still fling the way the finger went
    TurnedFlingFix(active = rotated && !preview)
    RotatingFrame(
        rotated = rotated,
        tones = tones,
        background = background,
        modifier = modifier
            .then(
                if (preview) {
                    // a card shows the style, it doesn't play it
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                } else Modifier
            ),
    ) { landscape ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .styleGestures(
                    enabled = !preview && connection != null,
                    interactive = LocalStyleInteractive.current,
                    onToggle = { connection?.togglePlayPause() },
                    carousel = rememberStyleCarousel(preview),
                    player = { connection?.player },
                ),
        ) {
            // Inter and nothing else, in every style
            androidx.compose.material3.ProvideTextStyle(
                androidx.compose.ui.text.TextStyle(fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily)
            ) {
                content(landscape, menuOpen, { menuOpen = it }, rotated, setRotated)
            }
        }
    }
}

/** The backdrop's warp clock, shared by the copies crossfading along the row. */
@Composable
private fun rememberBackdropClock(running: Boolean): () -> Float {
    val time = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val showing = rememberStyleShowing()
    androidx.compose.runtime.LaunchedEffect(running, showing) {
        if (!running) return@LaunchedEffect
        // the time is absolute, so it can sleep while the player is collapsed and wake up in step
        androidx.compose.runtime.snapshotFlow { showing() }.collectLatest { shown ->
            if (!shown) return@collectLatest
            time.floatValue = backdropTime(System.nanoTime())
            while (true) {
                androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameTimeNanos ->
                    time.floatValue = backdropTime(frameTimeNanos)
                }
            }
        }
    }
    return remember { { time.floatValue } }
}

private fun backdropTime(frameTimeNanos: Long): Float =
    ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)

/**
 * Insets the way they fall on a style: upright, the status bar is at its top and the navigation
 * bar at its bottom; turned sideways, those same bars run down its left and right edges.
 */
internal class StyleInsets(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

@Composable
internal fun styleInsets(landscape: Boolean): StyleInsets {
    val status = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (landscape) StyleInsets(start = status, top = 0.dp, end = nav, bottom = 0.dp)
    else StyleInsets(start = 0.dp, top = status, end = 0.dp, bottom = nav)
}

// ---- Poster ------------------------------------------------------------------------------

/** what the words give the visualizer's bars under the cover (beside it, sideways) */
private val VisualizerRoom = 60.dp
private val VisualizerRoomSideways = 40.dp
/** how far the words move down while the cover's seek bar is out */
private val RingPush = 34.dp
/** the Turntable's pills, side by side: as tall as one */
private val PillsHeight = 42.dp
/** the menu's chevron, the Turntable's size */
private val PosterChevron = 43.dp

/**
 * The cover cut into a great circle swung in from the top-left corner, over the flagship's own
 * background, and beneath it the song's words performed big ([ExpressiveLyrics]) with the artist
 * and title set small under them. A song with no synced lyrics shouts its title instead. Turned
 * sideways, the circle swings in from the left.
 */
@Composable
private fun PosterPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val r = rememberResponsive()
    val position = rememberLivePosition(animated = !preview)
    val lyrics = rememberStyleLyrics(readings = !preview)
    val carousel = rememberStyleCarousel(preview)
    val backgroundClock = rememberBackdropClock(running = !preview)
    val backgroundStyle by com.mochyy.mochymusic.utils.rememberEnumPreference(
        com.mochyy.mochymusic.constants.PlayerBackgroundStyleKey,
        com.mochyy.mochymusic.constants.PlayerBackgroundStyle.COVER_GRADIENT,
    )
    val (visualizer) = com.mochyy.mochymusic.utils.rememberPreference(com.mochyy.mochymusic.constants.PosterVisualizerKey, defaultValue = false)
    val ring = rememberCoverRingState()
    // The live player, not a selector card, whether open or not. Tied to the player being open, the
    // ring jumped to a fixed spot and lost its bars the moment a close began.
    val live = !preview
    val density = LocalDensity.current
    // the pills' column, and the room the words keep from it
    val pillsWidth = com.mochyy.mochymusic.ui.player.TurntablePillWidth
    StyleFrame(
        PlayerStyle.POSTER, preview, modifier, tones,
        background = {
            // the flagship's background, moving as it does there; it stays put when the poster turns.
            // The cover's colours crossfade along the row of songs as a swipe or a skip slides it.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                if (backgroundStyle == com.mochyy.mochymusic.constants.PlayerBackgroundStyle.COVER_GRADIENT) {
                    com.mochyy.mochymusic.ui.player.CarouselCrossfade(
                        carousel = carousel,
                        fallbackUrl = track.artwork,
                        modifier = Modifier.fillMaxSize(),
                    ) { url ->
                        com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview(
                            style = backgroundStyle,
                            thumbnailUrl = url,
                            width = w,
                            height = h,
                            animate = false,
                            timeProvider = backgroundClock,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview(
                        style = backgroundStyle,
                        thumbnailUrl = track.artwork,
                        width = w,
                        height = h,
                        animate = !preview,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    ) { landscape, menuOpen, setMenuOpen, rotated, setRotated ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            if (!landscape) {
                // circle from the top-left: through the right edge at 0.8 W, the left edge at 1.19 W
                CircleCover(
                    carousel = carousel,
                    url = track.artwork,
                    center = Offset(0.13f, 0.06f),
                    radius = 1.14f,
                    unit = w,
                    modifier = Modifier.fillMaxWidth().height(w * 1.22f),
                )
                val bottom = insets.bottom + 36.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            // the visualizer's bars stand where the words would start
                            top = (w * 1.24f).coerceAtMost(h * 0.6f) + (if (visualizer) VisualizerRoom else 0.dp),
                            start = 28.dp,
                            end = 22.dp + pillsWidth + 16.dp,
                            bottom = bottom,
                        ),
                ) {
                    StyleSongs(carousel, track, lyrics) { song, slotModifier ->
                        Column(Modifier.fillMaxSize().then(slotModifier)) {
                            PosterText(
                                song.lines, position, song.track, tones, r, baseSize = r.sp(0.12f, 34f, 56f), gap = 12.dp,
                                // the seek bar coming out past the rim moves the words down with it
                                push = { Offset(0f, ring.expand.value * with(density) { RingPush.toPx() }) },
                            )
                        }
                    }
                }
                // the seek bar on the cover's rim and the visualizer round it
                val wPx = with(density) { w.toPx() }
                CoverRing(
                    state = ring,
                    circle = RingCircle(Offset(0.13f * wPx, 0.06f * wPx), 1.14f * wPx),
                    visualizer = visualizer,
                    live = live,
                    position = position,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = bottom),
                ) {
                    com.mochyy.mochymusic.ui.player.TurntablePills()
                }
                // the menu at the top, opening down, clear of the transport
                StyleMenu(
                    open = menuOpen, onOpenChange = setMenuOpen, tones = tones, actions = actions,
                    rotated = rotated, onRotate = setRotated, atBottom = false, end = 22.dp, edge = insets.top + 16.dp,
                    buttonSize = PosterChevron, pill = true,
                )
            } else {
                // circle from the left edge, the words to its right
                CircleCover(
                    carousel = carousel,
                    url = track.artwork,
                    center = Offset(0.04f, 0.30f),
                    radius = 0.953f,
                    unit = h,
                    modifier = Modifier.fillMaxHeight().width(h * 1.0f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(
                            start = maxOf(h * 1.08f, w * 0.5f) + (if (visualizer) VisualizerRoomSideways else 0.dp),
                            end = 24.dp + insets.end,
                            top = 18.dp + maxOf(PosterChevron, PillsHeight) + 18.dp,
                            bottom = 20.dp,
                        ),
                ) {
                    StyleSongs(carousel, track, lyrics) { song, slotModifier ->
                        Column(Modifier.fillMaxHeight().then(slotModifier)) {
                            PosterText(
                                song.lines, position, song.track, tones, r, baseSize = r.sp(0.095f, 28f, 46f), gap = 8.dp,
                                // sideways the bar comes out to the right, and moves the words that way
                                push = { Offset(ring.expand.value * with(density) { RingPush.toPx() }, 0f) },
                            )
                        }
                    }
                }
                val hPx = with(density) { h.toPx() }
                CoverRing(
                    state = ring,
                    circle = RingCircle(Offset(0.04f * hPx, 0.30f * hPx), 0.953f * hPx),
                    visualizer = visualizer,
                    live = live,
                    position = position,
                    side = RingSide.Right,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 20.dp + insets.end + PosterChevron + 8.dp),
                ) {
                    com.mochyy.mochymusic.ui.player.TurntablePills(horizontal = true)
                }
                StyleMenu(
                    open = menuOpen, onOpenChange = setMenuOpen, tones = tones, actions = actions,
                    rotated = rotated, onRotate = setRotated, atBottom = false, end = 20.dp + insets.end, edge = 18.dp,
                    buttonSize = PosterChevron, pill = true,
                )
            }
        }
    }
}

/**
 * The poster's words and its credits. With lyrics, the lyrics are performed big and the title sits
 * small under them with the artist; without, the title is the big words. When a song's lyrics are
 * found, its big title slides down and shrinks into its small place at the bottom while the lyrics
 * take the space it leaves - and back up if they go away.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PosterText(
    lines: List<LyricsEntry>,
    position: LivePosition,
    track: StyleTrack,
    tones: StyleTones,
    r: Responsive,
    baseSize: TextUnit,
    gap: Dp,
    /** how far the words are pushed aside (px), read as they're drawn */
    push: () -> Offset = { Offset.Zero },
) {
    val hasLyrics = lines.isNotEmpty()
    val found = remember { androidx.compose.animation.core.Animatable(if (hasLyrics) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(hasLyrics) {
        found.animateTo(
            if (hasLyrics) 1f else 0f,
            androidx.compose.animation.core.tween(820, easing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
        )
    }
    val titleShown by remember { androidx.compose.runtime.derivedStateOf { found.value < 0.999f } }
    // the column, the big title and the small title: where the slide runs from and to, measured
    // against the column so a turned poster slides along its own axes
    val places = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(3) }
    val smallTitle = r.sp(0.05f, 17f, 22f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .graphicsLayer {
                val o = push()
                translationX = o.x
                translationY = o.y
            }
            .onPlaced { places[0] = it },
    ) {
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = found.value
                        if (f < 0.999f) alpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveLyrics(
                    lines = lines,
                    position = position.withLyricsOffset(),
                    color = tones.ink,
                    baseSize = baseSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (titleShown) {
            Box(
                modifier = Modifier
                    .onPlaced { places[1] = it }
                    .graphicsLayer {
                        val f = found.value
                        val column = places[0]
                        val from = places[1]
                        val to = places[2]
                        if (f > 0.0005f && column != null && from != null && to != null &&
                            column.isAttached && from.isAttached && to.isAttached
                        ) {
                            val a = column.localPositionOf(from, Offset.Zero)
                            val b = column.localPositionOf(to, Offset.Zero)
                            translationX = (b.x - a.x) * f
                            translationY = (b.y - a.y) * f
                        }
                        val s = 1f + (smallTitle.value / baseSize.value - 1f) * f
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        // it becomes the small title in the last part of the slide
                        alpha = 1f - ((f - 0.6f) / 0.35f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveTitle(text = track.title, baseSize = baseSize, color = tones.ink)
            }
        }
    }
    Spacer(Modifier.height(gap))
    if (track.artist.isNotBlank()) {
        CreditLine(track.artist, color = tones.inkSoft, style = posterType(r.sp(0.034f, 12f, 15f), FontWeight.SemiBold))
    }
    Spacer(Modifier.height(3.dp))
    CreditLine(
        track.title,
        color = tones.inkSoft,
        style = posterType(smallTitle, FontWeight.Bold),
        modifier = Modifier
            .onPlaced { places[2] = it }
            .graphicsLayer { alpha = ((found.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
    )
}

/**
 * One line of the poster's credits. Too long for the line, it scrolls by every few seconds and
 * fades where it's cut, so the whole name gets read. A right-to-left name starts at its right
 * edge and scrolls the other way.
 */
@Composable
private fun CreditLine(text: String, color: Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val rtl = remember(text) { isRtlText(text) }
    // the marquee lays the text out at its full width, so this says whether it's cut
    var fullWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { fullWidth = it.size.width },
            modifier = modifier
                .graphicsLayer {
                    // only a cut line needs its own layer for the fade
                    compositingStrategy = if (fullWidth > size.width + 0.5f) {
                        androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    } else {
                        androidx.compose.ui.graphics.CompositingStrategy.Auto
                    }
                }
                .drawWithCache {
                    val fade = if (rtl) {
                        Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black)
                    } else {
                        Brush.horizontalGradient(0.82f to Color.Black, 1f to Color.Transparent)
                    }
                    onDrawWithContent {
                        drawContent()
                        if (fullWidth > size.width + 0.5f) {
                            drawRect(brush = fade, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                        }
                    }
                }
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2500,
                    repeatDelayMillis = 2500,
                    velocity = 26.dp,
                ),
        )
    }
}

/**
 * The current song's synced lines, the background vocals left out, with their pronunciation and
 * translation filled in by the same code as the lyrics page when [readings] (the live player, not
 * a selector card). Lyrics without timings show nothing here: a style performs lines in time.
 */
@Composable
internal fun rememberStyleLyrics(readings: Boolean): StyleLyrics {
    val connection = LocalPlayerConnection.current
    val lyricsEntity = connection?.currentLyrics?.collectAsState(initial = null)?.value
    val all = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList()
        else runCatching { LyricsUtils.parseLyricsCached(raw) }.getOrDefault(emptyList())
            .takeIf { it.isNotEmpty() && !LyricsUtils.isUnsynced(it) }.orEmpty()
    }
    if (readings && connection != null) {
        val metadata by connection.mediaMetadata.collectAsState()
        // The flagship's bottom card is what asks for a song's lyrics; a style has no card, so it
        // asks itself. Without this the words only came once the flagship had been shown.
        val loader: com.mochyy.mochymusic.viewmodels.LyricsScreenViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        androidx.compose.runtime.LaunchedEffect(metadata?.id) {
            metadata?.let(loader::ensureLyricsLoaded)
        }
        if (all.isNotEmpty()) {
            // the whole song, background lines too, so a stored translation lines up with the lyrics page's
            com.mochyy.mochymusic.ui.player.LyricsReadings(all, metadata?.id, com.mochyy.mochymusic.LocalDatabase.current, lyricsEntity)
        }
    }
    val lines = remember(all) { all.filter { !it.isBackground && it.text.isNotBlank() } }
    return remember(lyricsEntity?.id, lines) { StyleLyrics(lyricsEntity?.id, lines) }
}

/**
 * [url] cropped into a circle of [radius] around [center], both in multiples of [unit], with a
 * soft shadow on the tone beneath and the rim shaded so the disc reads as lifted off the page.
 */
@Composable
private fun CircleCover(
    carousel: com.mochyy.mochymusic.ui.player.SongCarousel?,
    url: String?,
    center: Offset,
    radius: Float,
    unit: Dp,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val unitPx = with(density) { unit.toPx() }
    val c = Offset(center.x * unitPx, center.y * unitPx)
    val r = radius * unitPx
    val shape = remember(c, r) {
        GenericShape { _, _ -> addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    }
    Box(
        modifier = modifier.drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    0.9f to Color.Black.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = c + Offset(0f, 10.dp.toPx()),
                    radius = r * 1.06f,
                ),
                radius = r * 1.06f,
                center = c + Offset(0f, 10.dp.toPx()),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.82f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.28f),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                },
        ) {
            // the songs' covers slide past under the circle, the next one coming in as this one goes
            com.mochyy.mochymusic.ui.player.CarouselPictures(
                carousel = carousel,
                fallbackUrl = url,
                modifier = Modifier.fillMaxSize(),
            ) { pictureUrl, pictureModifier ->
                if (pictureUrl != null) {
                    AsyncImage(
                        model = pictureUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = pictureModifier,
                    )
                }
            }
        }
    }
}

// ---- Timeline ----------------------------------------------------------------------------

/**
 * The song as time: the screen split into what's played and what's left, the line between them
 * the playhead, with the elapsed and total times riding either side of it in huge type.
 */
@Composable
private fun TimelinePlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val r = rememberResponsive()
    val icon = r.dp(0.15f, 50.dp, 72.dp)
    val duration = rememberDurationMs()
    val carousel = rememberStyleCarousel(preview)
    StyleFrame(PlayerStyle.TIMELINE, preview, modifier, tones) { landscape, menuOpen, setMenuOpen, rotated, setRotated ->
        val insets = styleInsets(landscape)
        RidingTimes(
            tones = tones,
            position = position,
            duration = duration,
            landscape = landscape,
            insets = insets,
            topReserve = if (landscape) 0.dp else 20.dp + icon + 14.dp,
        ) {
            // header: the cover as an icon, the title and artist beside it
            Row(
                modifier = Modifier
                    .padding(start = 22.dp + insets.start, top = insets.top + 20.dp, end = 70.dp + insets.end)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the icon's covers and the words beside them ride the row of songs
                com.mochyy.mochymusic.ui.player.CarouselPictures(
                    carousel = carousel,
                    fallbackUrl = track.artwork,
                    modifier = Modifier
                        .size(if (landscape) icon * 0.86f else icon)
                        .clip(RoundedCornerShape(icon * 0.26f))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) { url, pictureModifier ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = pictureModifier,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box {
                    StyleSongs(carousel, track, StyleLyrics(null, emptyList())) { song, slotModifier ->
                        Column(slotModifier) {
                            Text(song.track.title, color = tones.ink, fontSize = r.sp(0.04f, 14f, 18f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.track.artist, color = tones.inkSoft, fontSize = r.sp(0.034f, 12f, 16f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            StyleControls(tones, landscape, insets, menuOpen, setMenuOpen, actions, rotated, setRotated, menuTop = insets.top + 20.dp + (icon - r.dp(0.078f, 28.dp, 40.dp)) / 2)
        }
    }
}

// ---- Lyrics ------------------------------------------------------------------------------

/**
 * The words, performed: the current line set huge and sung through letter by letter, over the
 * same played/remaining split and riding times as the timeline.
 */
@Composable
private fun LyricsPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val r = rememberResponsive()
    val duration = rememberDurationMs()
    val lyrics = rememberStyleLyrics(readings = !preview)
    val carousel = rememberStyleCarousel(preview)
    StyleFrame(PlayerStyle.LYRICS, preview, modifier, tones) { landscape, menuOpen, setMenuOpen, rotated, setRotated ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val lyricHeight = if (landscape) maxHeight * 0.52f else maxHeight * 0.3f
            RidingTimes(
                tones = tones,
                position = position,
                duration = duration,
                landscape = landscape,
                insets = insets,
                topReserve = if (landscape) 0.dp else 18.dp + lyricHeight + 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 24.dp + insets.start, top = insets.top + 18.dp, end = 64.dp + insets.end)
                        .fillMaxWidth()
                        .height(lyricHeight),
                ) {
                    // each song's words ride the row: the leaving song's go with it, the next one's come in
                    StyleSongs(carousel, track, lyrics) { song, slotModifier ->
                        Box(Modifier.fillMaxSize().then(slotModifier)) {
                            if (song.lines.isEmpty()) {
                                Column {
                                    ExpressiveTitle(text = song.track.title, baseSize = r.sp(0.112f, 32f, 56f), color = tones.ink.copy(alpha = 0.55f))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No synced lyrics for this song", color = tones.inkSoft, fontSize = r.sp(0.034f, 12f, 15f), fontWeight = FontWeight.Medium)
                                }
                            } else {
                                ExpressiveLyrics(
                                    lines = song.lines,
                                    position = position.withLyricsOffset(),
                                    color = tones.ink,
                                    baseSize = if (landscape) r.sp(0.092f, 28f, 46f) else r.sp(0.118f, 34f, 58f),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                StyleControls(tones, landscape, insets, menuOpen, setMenuOpen, actions, rotated, setRotated, menuTop = insets.top + 20.dp)
            }
        }
    }
}

// ---- shared pieces -----------------------------------------------------------------------

/** the transport circles and the ••• menu where the timeline and lyrics styles keep them */
@Composable
private fun BoxScope.StyleControls(
    tones: StyleTones,
    landscape: Boolean,
    insets: StyleInsets,
    menuOpen: Boolean,
    setMenuOpen: (Boolean) -> Unit,
    actions: StyleActions,
    rotated: Boolean,
    setRotated: (Boolean) -> Unit,
    menuTop: Dp,
) {
    val circle = rememberResponsive().dp(0.078f, 28.dp, 40.dp)
    if (landscape) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp + insets.end, bottom = 20.dp),
        ) {
            TransportCircles(tones = tones, vertical = true, size = circle)
        }
    } else {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.bottom + 36.dp),
        ) {
            TransportCircles(tones = tones, size = circle)
        }
    }
    StyleMenu(
        open = menuOpen, onOpenChange = setMenuOpen, tones = tones, actions = actions,
        rotated = rotated, onRotate = setRotated, atBottom = false,
        end = 22.dp + insets.end, edge = if (landscape) 18.dp else menuTop,
        buttonSize = circle,
    )
}


/**
 * The played/remaining split with the two times riding on the playhead: upright, the elapsed time
 * sits just above the line and the total just below it, the line running down the screen as the
 * song plays (between [topReserve] under the top and the controls); sideways, the line runs across
 * and the times sit either side of it along the bottom. [content] draws over the split.
 */
@Composable
private fun RidingTimes(
    tones: StyleTones,
    position: LivePosition,
    duration: Long,
    landscape: Boolean,
    insets: StyleInsets,
    topReserve: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val total = makeTimeString(duration)
    val circle = rememberResponsive().dp(0.078f, 28.dp, 40.dp)
    val timeStyle = posterType(rememberResponsive().sp(0.185f, 56f, 96f), FontWeight.Bold)
    val timeWidthPx = remember(total, timeStyle) { measurer.measure(total, timeStyle).size.width.toFloat() }
    val timeHeightPx = remember(timeStyle) { measurer.measure("0:00", timeStyle).size.height.toFloat() }
    val fraction = { (position.positionMs().toFloat() / duration).coerceIn(0f, 1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val (lo, hi) = with(density) {
            if (!landscape) {
                val top = (insets.top + topReserve).toPx() + timeHeightPx
                val bottom = hPx - (insets.bottom + 36.dp + circle + 18.dp).toPx() - timeHeightPx
                top to bottom.coerceAtLeast(top)
            } else {
                val left = (insets.start + 22.dp).toPx() + timeWidthPx + 10.dp.toPx()
                val right = wPx - (insets.end + 20.dp + circle + 22.dp).toPx() - timeWidthPx - 10.dp.toPx()
                left to right.coerceAtLeast(left)
            }
        }
        val linePx = { lo + (hi - lo) * fraction() }
        // the frame draws the split itself, under the turn, so it holds still when the style rotates
        ReportSplit(landscape) { linePx() / (if (landscape) wPx else hPx) }
        Box(modifier = Modifier.fillMaxSize()) {
            val gapPx = with(density) { 6.dp.toPx() }
            val startPx = with(density) { (insets.start + 24.dp).toPx() }
            val bottomPx = with(density) { 16.dp.toPx() }
            // the elapsed time: above the line, or left of it
            PlayedTime(
                position = position,
                color = tones.ink,
                style = timeStyle,
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (!landscape) {
                            p.place(startPx.toInt(), (linePx() - p.height - gapPx * 0.3f).toInt())
                        } else {
                            p.place((linePx() - p.width - gapPx * 1.6f).toInt(), (hPx - bottomPx - p.height).toInt())
                        }
                    }
                },
            )
            // the total: below the line, or right of it
            Text(
                text = total,
                color = tones.ink.copy(alpha = 0.78f),
                style = timeStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (!landscape) {
                            p.place(startPx.toInt(), (linePx() + gapPx * 0.6f).toInt())
                        } else {
                            p.place((linePx() + gapPx * 1.6f).toInt(), (hPx - bottomPx - p.height).toInt())
                        }
                    }
                },
            )
            content()
        }
    }
}

/** the played time on its own, so the second ticking over recomposes this text and nothing around it */
@Composable
internal fun PlayedTime(position: LivePosition, color: Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    val seconds = rememberPlayedSeconds(position)
    Text(
        text = makeTimeString(seconds * 1_000L),
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}
