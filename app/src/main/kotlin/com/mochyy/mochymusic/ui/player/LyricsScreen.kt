// LyricsScreen.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.song.LyricsSongShare
import com.mochyy.mochymusic.ui.player.share.ShareFlightSource
import com.mochyy.mochymusic.ui.player.share.fullBoundsInWindow
import com.mochyy.mochymusic.ui.player.share.ShareSong
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import androidx.media3.common.Player
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.constants.AiProviderKey
import com.mochyy.mochymusic.constants.DeeplApiKey
import com.mochyy.mochymusic.constants.DeeplFormalityKey
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.OpenRouterApiKey
import com.mochyy.mochymusic.constants.OpenRouterBaseUrlKey
import com.mochyy.mochymusic.constants.OpenRouterModelKey
import com.mochyy.mochymusic.constants.LyricsRomanizeBelarusianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeBulgarianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeChineseKey
import com.mochyy.mochymusic.constants.LyricsRomanizeHindiKey
import com.mochyy.mochymusic.constants.LyricsRomanizeJapaneseKey
import com.mochyy.mochymusic.constants.LyricsRomanizeKoreanKey
import com.mochyy.mochymusic.constants.LyricsRomanizeKyrgyzKey
import com.mochyy.mochymusic.constants.LyricsRomanizeMacedonianKey
import com.mochyy.mochymusic.constants.LyricsRomanizePunjabiKey
import com.mochyy.mochymusic.constants.LyricsRomanizeRussianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeSerbianKey
import com.mochyy.mochymusic.constants.LyricsRomanizeUkrainianKey
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.constants.TranslateLyricsKey
import com.mochyy.mochymusic.constants.TranslateSourceLanguageKey
import com.mochyy.mochymusic.constants.TranslateModeKey
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.theme.PlayerColorExtractor
import com.mochyy.mochymusic.viewmodels.LyricsScreenViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding

private const val ActiveLineViewportFraction = 0.055f

/**
 * Floor under [ActiveLineViewportFraction], and in practice the value that wins: the active line
 * rests exactly one lyric row from the top, leaving the just-finished line — and only that line —
 * above it. One row is a 40sp line plus 16dp of vertical padding on each side.
 */
private val MinPastLineVisibleHeight = 72.dp

/**
 * The list's top and bottom padding, worked out from its height while it is measured. The height
 * is written by the box around the list, so the immersive toggle relayouts instead of recomposing.
 */
private class LyricsListPadding : PaddingValues {
    var heightPx = 0
    var density = 1f

    private fun listHeight() = (heightPx / density).dp

    override fun calculateTopPadding() = maxOf(listHeight() * ActiveLineViewportFraction, MinPastLineVisibleHeight)

    override fun calculateBottomPadding() = listHeight() * 0.55f

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

private const val ImmersiveDelayMs = 3_500L

private const val RecenterDurationMs = 950

/** Shared with LyricsInterludeDots, whose collapse has to run in step with the recenter scroll. */
internal const val LyricsRecenterDurationMs = RecenterDurationMs

/** Style 2's recenter curve (the original). */
internal val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

/**
 * Style 1's recenter curve: eases in instead of starting at speed, so the list never gets a kick
 * at the start of a line change.
 */
internal val LyricsSmoothRecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

internal fun recenterEasingFor(style: com.mochyy.mochymusic.constants.LyricsMotionStyle) =
    if (style.risesWords) LyricsSmoothRecenterEasing else LyricsRecenterEasing

private const val ScrollDirectionThresholdPx = 6L

private const val IdleRecenterDelayMs = 3_500L

private const val TopFadeFraction = 0.10f

/**
 * Where a start-aligned line's text begins inside its row, and the middle of its first line: the
 * point the card's lyric grows from. (The row's padding and tap inset put the text at the artwork's
 * left edge, 16dp down; a lyric line is 40sp tall.)
 */
private val LineAnchorX = LyricsHeaderArtX
private val LineAnchorY = 16.dp + 20.dp

// the card isn't showing a gap; the intro's gap is -1, so this can't be a row's index
private const val NoCardGap = Int.MIN_VALUE

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density) =
    with(density) { this@toPx.toPx() }
private const val BottomFadeStart = 0.62f


@Composable
fun LyricsScreen(
    onClose: () -> Unit,

    screenHeight: androidx.compose.ui.unit.Dp,

    contentBottomInset: androidx.compose.ui.unit.Dp,

    onImmersiveChange: (Boolean) -> Unit = {},

    isSheetDragging: Boolean = false,

    isMorphing: Boolean = false,

    /**
     * How far the page has grown out of the bottom card (unclamped); 1 when it isn't growing from
     * it. Drives the rows rising into place and the card's line flying to its own.
     */
    enterProgressProvider: () -> Float = { 1f },

    morph: CardMorphState? = null,

    /** Opens the source picker from the footer's "Switch" button. Without it the button is hidden. */
    onSwitchSource: (() -> Unit)? = null,

    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsScreenViewModel = hiltViewModel()
    val density = LocalDensity.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()
    val lyricsOffsetMs by playerConnection.lyricsOffsetMs.collectAsState()

    // frame-accurate, so the sweep, the gap dots and backing vocals glide at the display's rate instead of stepping with the 66ms poller
    val positionProvider = remember(playerConnection) {
        PlaybackClock({ playerConnection.player }, { playerConnection.lyricsOffsetMs.value.toLong() })
    }
    val sheetDragging by rememberUpdatedState(isSheetDragging)

    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(viewModel::ensureLyricsLoaded)
    }

    val context = LocalContext.current
    val fallbackColorInt = MaterialTheme.colorScheme.primary.toArgb()
    var accentColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val thumbnailUrl = mediaMetadata?.thumbnailUrl
        accentColor = null
        if (thumbnailUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(8)
                            .resizeBitmapArea(100 * 100)
                            .generate()
                    }
                    val colors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColorInt,
                    )
                    accentColor = colors.firstOrNull()?.let { PlayerColorExtractor.darkenIfTooLight(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    // Lyrics with no timing (plain text, or LRC with every line on [00:00.00]) are shown as a
    // static sheet: every line readable, nothing blurred, no follow-playback scrolling. Played as
    // synced, all lines counted as "already sung" a few seconds in and the whole page sat blurred.
    val parsedLyrics = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) {
            emptyList<LyricsEntry>() to false
        } else {
            val timed = LyricsUtils.parseLyricsCached(raw)
            when {
                timed.isEmpty() -> LyricsUtils.parsePlainLyrics(raw) to true
                LyricsUtils.isUnsynced(timed) -> timed to true
                else -> timed to false
            }
        }
    }
    val lines = parsedLyrics.first
    val unsynced = parsedLyrics.second

    // romanisation and translation, shared with the player styles that perform the lyrics
    LyricsReadings(lines, mediaMetadata?.id, viewModel.database, lyricsEntity)

    val currentIndex by remember(lines, unsynced) {
        derivedStateOf { if (unsynced) -1 else LyricsUtils.findCurrentLineIndex(lines, progress.position + lyricsOffsetMs) }
    }

    val anchorIndex by remember(lines, unsynced) {
        derivedStateOf { if (unsynced) -1 else lyricAnchorIndex(lines, progress.position + lyricsOffsetMs) }
    }

    // Every instrumental gap in the song, as its own list row. Building these up front — rather
    // than conditionally rendering dots inside the next line's item, as before — is what stops the
    // list jumping. A gap's row grows in when the gap starts and collapses, in step with the
    // recenter scroll, when the next line takes over (see the recenter effect below).
    val rows = remember(lines) { buildLyricRows(lines) }

    /** Lyric line index -> its row in [rows]. Gap rows sit between lines, so the two differ. */
    val rowOfLine = remember(rows, lines) {
        IntArray(lines.size).also { map ->
            rows.forEachIndexed { rowIndex, row -> if (row is LyricRow.Line) map[row.index] = rowIndex }
        }
    }

    val (motionStyle) = com.mochyy.mochymusic.utils.rememberEnumPreference(
        com.mochyy.mochymusic.constants.LyricsMotionStyleKey,
        defaultValue = com.mochyy.mochymusic.constants.LyricsMotionStyle.MOTION,
    )
    val cascade = motionStyle.risesWords

    // Opens already on the playing line. Starting at the top and scrolling down to it fought the
    // open transition - the line the card grows into would still be moving.
    // read once without subscribing: a read here recomposed the whole page on every line change
    val initialRow = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
        rowOfLine.getOrNull(anchorIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    var followPlayback by remember { mutableStateOf(true) }

    // What the card is showing: the page's copy of it (a line, or the dots of a gap) is what the card grows into.
    val cardSlot by remember(lines) { derivedStateOf { tickerSlot(lines, progress.position + lyricsOffsetMs) } }
    val cardLine by remember { derivedStateOf { if (cardSlot.kind == TickerSlot.LINE) cardSlot.index else -1 } }
    val cardGap by remember { derivedStateOf { if (cardSlot.kind == TickerSlot.GAP) cardSlot.index else NoCardGap } }
    // its row, read only inside layers so the card moving on doesn't recompose every row
    val cardLineRow by remember(rowOfLine) { derivedStateOf { rowOfLine.getOrNull(cardLine) ?: -1 } }
    if (morph != null) {
        LaunchedEffect(morph, lines) {
            // a line or a gap row claims the anchor itself as it's placed; clearing it then raced that claim and lost the flight
            snapshotFlow { cardSlot.kind == TickerSlot.LINE || cardSlot.kind == TickerSlot.GAP }.collect { claimed ->
                if (!claimed) morph.pageLyricAnchor = androidx.compose.ui.geometry.Offset.Unspecified
            }
        }
    }

    val stagger = remember(rows) { LyricsStagger(rows.size + 1) }
    LaunchedEffect(stagger) { stagger.run() }

    var isAutoScrolling by remember { mutableStateOf(false) }

    val userScrolling by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }

    val suppressEffects = userScrolling || isSheetDragging || isMorphing

    LaunchedEffect(userScrolling) {
        if (userScrolling) followPlayback = false
    }

    LaunchedEffect(userScrolling, followPlayback) {
        if (!followPlayback && !userScrolling) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    var immersive by remember { mutableStateOf(false) }

    LaunchedEffect(userScrolling, immersive) {
        if (!userScrolling && !immersive) {
            delay(ImmersiveDelayMs)
            immersive = true
        }
    }

    LaunchedEffect(listState) {
        var previous = -1L
        snapshotFlow {
            listState.firstVisibleItemIndex.toLong() * 1_000_000L +
                listState.firstVisibleItemScrollOffset
        }.collect { now ->
            val last = previous
            previous = now
            if (last < 0L || isAutoScrolling) return@collect
            val delta = now - last
            if (delta > ScrollDirectionThresholdPx) immersive = true
            else if (delta < -ScrollDirectionThresholdPx) immersive = false
        }
    }

    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }

    val immersion by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsImmersion",
    )

    // collected rather than keyed on the line: a key is read in composition and recomposed the whole page on every line change
    LaunchedEffect(followPlayback, lines, motionStyle) {
        if (!followPlayback) return@LaunchedEffect
        snapshotFlow { anchorIndex }.collectLatest { target ->
        if (target !in lines.indices) return@collectLatest
        val targetRow = rowOfLine[target]

        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val minOffsetPx = with(density) { MinPastLineVisibleHeight.toPx() }
        val restingOffset = maxOf(viewportHeight * ActiveLineViewportFraction, minOffsetPx).roundToInt()

        try {
        isAutoScrolling = true

        // The sheet itself on the move - the page reopening with the player, the list just built
        // and still at its top: put the line straight where it rests. Animating it there stacked
        // a long scroll, composing every row it passed, on top of the sheet's own open.
        if (sheetDragging) {
            listState.scrollToItem(targetRow, 0)
            return@collectLatest
        }

        // Compared by ROW index. This used to compare the line index against the list's item
        // index, and every gap row before the target shifted the two apart - after the first
        // instrumental break it measured the wrong item and scrolled to the wrong place.
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
        if (visible != null) {

            val current = visible.offset - layoutInfo.viewportStartOffset
            // A gap row above the target that's on its way out collapses during this same scroll
            // (same duration and easing), and that collapse alone lifts the target by its height.
            // Scroll only the rest, or the line would overshoot its resting place.
            val collapsing = layoutInfo.visibleItemsInfo
                .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                .sumOf { it.size }
            val distance = (current - restingOffset - collapsing).toFloat()

            stagger.anchorRow = targetRow
            listState.scroll {
                var previous = 0f
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = distance,
                    animationSpec = tween(durationMillis = RecenterDurationMs, easing = recenterEasingFor(motionStyle)),
                ) { value, _ ->
                    val consumed = scrollBy(value - previous)
                    previous += consumed
                    if (cascade && consumed != 0f) {
                        // rows below the active line hold back by what just scrolled, then catch up on their own
                        for (item in listState.layoutInfo.visibleItemsInfo) {
                            if (item.index > targetRow) stagger.holdBack(item.index, consumed)
                        }
                    }
                }
            }
        } else {

            listState.animateScrollToItem(targetRow, 0)
        }
        } finally {
            isAutoScrolling = false
        }
        }
    }

    /** Which gap, if any, is happening right now. [NoInterlude] when a line is being sung. */
    val activeInterlude by remember(lines, rows) {
        derivedStateOf { activeInterludeOf(rows, progress.position + lyricsOffsetMs) }
    }

    // Only honour {agent:v1}/{agent:v2} when the song actually has more than one voice. A solo
    // track whose provider tagged every line as v2 would otherwise render entirely right-aligned.
    //
    // Untagged lines count as the lead voice rather than being ignored. Counting only non-null
    // agents meant a song that tags just its answering lines — some TTML leaves the primary
    // singer implicit — saw a single distinct agent and fell back to all-left, which is exactly
    // the case where left/right matters most. Background vocals are excluded because they are
    // centred regardless and would otherwise fake a second voice on a solo track.
    val useAgentAlignment = remember(lines) { usesAgentAlignment(lines) }
    val mirrored = rememberLyricsMirrored(lines)

    // the boring and plain styles are styles 1 and 2 with the shader left out
    val lyricsWaveAnimation = motionStyle.shaded
    // the lines' blur by their distance from the sung one is an element blur, the glass blur switch doesn't touch it
    val (lineBlurAllowed) = rememberPreference(com.mochyy.mochymusic.constants.ElementBlurKey, defaultValue = true)
    val (lyricsHighBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    // Drives the per-line loading dots and lifts the readings out of the way. The button that
    // starts a translation lives in the translation sheet, not here.
    val translationStatus by LyricsTranslationHelper.status.collectAsState()
    val isTranslating = translationStatus is LyricsTranslationHelper.TranslationStatus.Translating

    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    // Holding a line opens the lyrics share with that line picked. Each line's place is kept (a
    // reference, no work) so the share can lift the line out of exactly where it sits, and fly it
    // back there when the share closes.
    var shareSession by remember { mutableStateOf<LyricsShareSession?>(null) }
    val lineCoords = remember(lines) { HashMap<Int, LayoutCoordinates>() }
    val haptic = LocalHapticFeedback.current
    val lineBaseStyle = MaterialTheme.typography.headlineSmall
    // held as updated state, so the rows' long-press lambdas stay the same object and the rows can skip recomposing
    val startShare by rememberUpdatedState { index: Int, state: LyricsLineState, alignment: LyricsAlignment ->
        val meta = mediaMetadata ?: return@rememberUpdatedState
        val shareable = lines.mapIndexedNotNull { i, e -> e.text.trim().takeIf { it.isNotEmpty() }?.let { i to it } }
        val held = shareable.indexOfFirst { it.first == index }
        if (held < 0) return@rememberUpdatedState
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val bounds = { lineCoords[index]?.takeIf { it.isAttached }?.fullBoundsInWindow() }
        shareSession = LyricsShareSession(
            song = ShareSong(meta.title, meta.artists.joinToString { it.name }, meta.thumbnailUrl),
            lines = shareable.map { it.second },
            entries = shareable.map { lines[it.first] },
            held = held,
            flightSource = bounds()?.let {
                ShareFlightSource(
                    lyricIndex = index,
                    text = shareable[held].second,
                    style = restingLineStyle(lineBaseStyle, active = state == LyricsLineState.ACTIVE, letterMotion = cascade),
                    alignment = alignment,
                    insets = lyricsLineInsets(alignment),
                    scale = restingScaleOf(state),
                    alpha = restingAlphaOf(state),
                    bounds = bounds,
                )
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .stableStatusBarsPadding()
                .padding(horizontal = LyricsHeaderArtX)
                // Matches where the morph layer actually draws the cover, so the hitbox sits on
                // the artwork and the menu centres against it.
                .padding(top = LyricsHeaderArtTopFromStatusBar, bottom = 10.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(LyricsHeaderArtSize)
                    .clip(RoundedCornerShape(10.dp))

                    .clickable { if (immersive) immersive = false else onClose() }
            )

            Spacer(modifier = Modifier.width(18.dp))

            // No menu button up here: on the lyrics page the options button takes the chevron's
            // slot next to the seek bar (BottomSheetPlayer), within reach of the thumb.
        }

        val listPadding = remember { LyricsListPadding() }
        Box(
            modifier = Modifier
                .weight(1f)
                // Ends at the top of the seek bar (contentBottomInset). It used to reach 28dp past
                // its bottom, so lines scrolled behind the bar and the timestamps.
                // Read in layout, so the immersive toggle doesn't recompose the whole page.
                .layout { measurable, constraints ->
                    val inset = (contentBottomInset * (1f - immersion)).coerceAtLeast(0.dp).roundToPx()
                    val inner = constraints.offset(vertical = -inset)
                    listPadding.heightPx = inner.maxHeight
                    listPadding.density = this.density
                    val placeable = measurable.measure(inner)
                    layout(
                        constraints.constrainWidth(placeable.width),
                        constraints.constrainHeight(placeable.height + inset),
                    ) { placeable.place(0, 0) }
                }
        ) {
            if (lines.isEmpty()) {
                if (lyricsEntity == null) {

                    LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = "No lyrics found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,

                    contentPadding = listPadding,
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()

                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

                        .drawWithCache {
                            val fade = Brush.verticalGradient(
                                0f to Color.Transparent,
                                TopFadeFraction to Color.Black,
                                BottomFadeStart to Color.Black,
                                1f to Color.Transparent,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = fade, blendMode = BlendMode.DstIn)
                            }
                        },
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                    ) { listIndex, row ->
                        when (row) {
                            is LyricRow.Interlude -> {
                                // the card's dots grow into these when it's showing this gap
                                val growsFromCard by remember(row, morph) {
                                    derivedStateOf { morph != null && cardGap == row.afterIndex }
                                }
                                val dotsStart = remember { floatArrayOf(Float.NaN, Float.NaN) }
                                LyricsInterludeDots(
                                    modifier = Modifier
                                        .then(
                                            if (growsFromCard) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    if (dotsStart[0].isNaN()) return@onGloballyPositioned
                                                    // before the row's own layer, so the flight doesn't move the point it flies to
                                                    val origin = coords.positionInRoot()
                                                    morph!!.pageLyricAnchor = androidx.compose.ui.geometry.Offset(
                                                        origin.x + dotsStart[0],
                                                        origin.y + dotsStart[1],
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .graphicsLayer {
                                            translationY = stagger.lagOf(listIndex)
                                            val e = enterProgressProvider()
                                            // the card draws its dots at half size, the same as its line, so the line's scale fits them
                                            val flew = growsFromCard && !dotsStart[0].isNaN() && applyCardFlight(
                                                e = e,
                                                cardAnchor = morph!!.cardLyricAnchor,
                                                pageAnchor = morph.pageLyricAnchor,
                                                localAnchor = androidx.compose.ui.geometry.Offset(dotsStart[0], dotsStart[1]),
                                                cardToPage = CardToPageLyricScale,
                                            )
                                            if (!flew) applyPageRowEnter(e, listIndex - cardLineRow.coerceAtLeast(0))
                                        },
                                    onDotsStart = if (growsFromCard) {
                                        { at -> dotsStart[0] = at.x; dotsStart[1] = at.y }
                                    } else {
                                        null
                                    },
                                    startMs = row.startMs,
                                    endMs = row.endMs,
                                    // A provider, not the position itself: passing the value would
                                    // recompose this row on every playback tick.
                                    positionProvider = positionProvider,
                                    accentColor = accent,
                                    visible = activeInterlude == row.afterIndex,
                                    collapseEasing = recenterEasingFor(motionStyle),
                                    // The gap belongs to whoever sings next, so it waits on that
                                    // singer's side rather than always sitting down the middle.
                                    alignment = lines.getOrNull(row.afterIndex + 1)
                                        ?.let { alignmentFor(it, useAgentAlignment, mirrored) }
                                        ?: LyricsAlignment.CENTER,
                                )
                            }

                            is LyricRow.Line -> {
                                val index = row.index
                                val entry = row.entry

                                // worked out per row, so a line change only recomposes the few rows whose look changes
                                val look by remember(index, lines, unsynced) {
                                    derivedStateOf {
                                        lineLook(index, currentIndex, anchorIndex, unsynced, activeInterlude != NoInterlude)
                                    }
                                }
                                val state = look.state
                                val blurStage = if (suppressEffects || !lineBlurAllowed) 0 else look.blurStage

                                val romanized by entry.romanizedTextFlow.collectAsState()
                                val ruby by entry.rubyFlow.collectAsState()
                                val translated by entry.translatedTextFlow.collectAsState()
                                val rowIndex = rowOfLine[index]
                                val lineAlignment = alignmentFor(entry, useAgentAlignment, mirrored)
                                // any alignment grows from the card: a centred or right-aligned line measures where its words start
                                val growsFromCard by remember(index, lines, morph) {
                                    derivedStateOf { morph != null && index == cardLine }
                                }
                                val textStart = remember { floatArrayOf(Float.NaN) }
                                LyricsGlowLine(
                                    onTextStart = if (growsFromCard) {
                                        { x -> textStart[0] = x }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .then(
                                            if (growsFromCard) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    // Before this row's own layer, so the flight
                                                    // below doesn't move the point it flies to.
                                                    val origin = coords.positionInRoot()
                                                    val startX = textStart[0].takeUnless { it.isNaN() } ?: LineAnchorX.toPx(density)
                                                    morph!!.pageLyricAnchor = androidx.compose.ui.geometry.Offset(
                                                        origin.x + startX,
                                                        origin.y + LineAnchorY.toPx(density),
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .graphicsLayer {
                                            translationY = stagger.lagOf(rowIndex)
                                            val e = enterProgressProvider()
                                            val flew = growsFromCard && applyCardFlight(
                                                e = e,
                                                cardAnchor = morph!!.cardLyricAnchor.let {
                                                    if (it == androidx.compose.ui.geometry.Offset.Unspecified) it
                                                    else androidx.compose.ui.geometry.Offset(it.x - morph.cardLyricScroll, it.y)
                                                },
                                                pageAnchor = morph.pageLyricAnchor,
                                                localAnchor = androidx.compose.ui.geometry.Offset(
                                                    textStart[0].takeUnless { it.isNaN() } ?: LineAnchorX.toPx(),
                                                    LineAnchorY.toPx(),
                                                ),
                                                cardToPage = CardToPageLyricScale,
                                            )
                                            if (!flew) applyPageRowEnter(e, rowIndex - cardLineRow.coerceAtLeast(0))
                                            // the line held for sharing is out flying; no copy stays behind
                                            shareSession?.let { alpha *= it.lyricsLineAlpha(index) }
                                        }
                                        .onPlaced { lineCoords[index] = it },
                                    entry = entry,
                                    state = state,
                                    blurStage = blurStage,
                                    suppressEffects = suppressEffects,
                                    positionProvider = positionProvider,
                                    accentColor = accent,
                                    subLine = romanized,
                                    ruby = ruby,
                                    // Readings sit under the line normally. With a translation on
                                    // screen they move above it, so the line keeps one thing on
                                    // each side instead of two stacked underneath.
                                    //
                                    // Keyed on "translation requested", not "translation arrived",
                                    // so the layout settles once when you press the button rather
                                    // than shifting a second time when the text lands.
                                    rubyPlacement = if (isTranslating || !translated.isNullOrBlank()) {
                                        RubyPlacement.ABOVE
                                    } else {
                                        RubyPlacement.BELOW
                                    },
                                    translationLoading = isTranslating && translated.isNullOrBlank(),
                                    translation = translated,
                                    alignment = lineAlignment,
                                    waveEnabled = lyricsWaveAnimation && !suppressEffects,
                                    motionStyle = motionStyle,
                                    highBloom = lyricsHighBloom,
                                    onClick = {
                                        // Untimed lines have nowhere to seek to.
                                        if (!unsynced) {
                                            playerConnection.player.seekTo(entry.time)
                                            followPlayback = true
                                        }
                                    },
                                    onLongClick = { startShare(index, look.state, lineAlignment) },
                                )
                            }
                        }
                    }

                    // Where the lyrics came from, after the last line. "Unknown" and "no
                    // connection" aren't sources, so there's nothing to credit for them.
                    val provider = lyricsEntity?.provider
                    if (provider != null && provider != "Unknown" &&
                        provider != com.mochyy.mochymusic.lyrics.LyricsHelper.NoConnectionProvider
                    ) {
                        item(key = "source", contentType = "source") {
                            LyricsSourceFooter(
                                provider = provider,
                                onSwitchSource = onSwitchSource,
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationY = stagger.lagOf(rows.size)
                                        applyPageRowEnter(enterProgressProvider(), rows.size - cardLineRow.coerceAtLeast(0))
                                    }
                                    .padding(horizontal = LyricsHeaderArtX)
                                    .padding(top = 36.dp),
                            )
                        }
                    }
                }
            }

            // Repeat and like used to float here at the top right; they now sit under the playback
            // timestamps in BottomSheetPlayer, where the song info row's own pair lives.
        }

    }

    // holding a line opens the song share on its line picker, the same share the menus open
    shareSession?.let { pick ->
        mediaMetadata?.let { song -> LyricsSongShare(song, pick, lines, onDismiss = { shareSession = null }) }
    }
}

/** Sentinel for "no instrumental gap is playing right now". */
internal const val NoInterlude = Int.MIN_VALUE

// the line the list rests on: the sung one, or an earlier one whose words are still running into it
internal fun lyricAnchorIndex(lines: List<LyricsEntry>, positionMs: Long): Int {
    var i = LyricsUtils.findCurrentLineIndex(lines, positionMs)
    // findCurrentLineIndex returns lines.size once playback is past the final line. Don't
    // walk back from there looking for an anchor — there is no active line to anchor.
    if (i <= 0 || i >= lines.size) return i
    while (i > 0) {
        val previous = i - 1
        val endMs = lines[previous].words
            ?.lastOrNull()
            ?.let { (it.endTime * 1000).toLong() }
            ?: return i
        if (endMs <= positionMs) return i
        i = previous
    }
    return i
}

// the gap playing right now, or NoInterlude; it ends with the same lead-in the next line starts with, so the dots collapse on the very frame that line takes over
internal fun activeInterludeOf(rows: List<LyricRow>, positionMs: Long): Int =
    rows.firstOrNull {
        it is LyricRow.Interlude && positionMs >= it.startMs &&
            positionMs + LyricsUtils.LineLeadInMs < it.endMs
    }?.let { (it as LyricRow.Interlude).afterIndex } ?: NoInterlude

// left and right only mean something when the song really has more than one voice; untagged lines count as the lead, background ones don't count
internal fun usesAgentAlignment(lines: List<LyricsEntry>): Boolean =
    lines.filter { !it.isBackground }
        .map { it.agent ?: "v1" }
        .distinct()
        .size > 1

// how a line looks right now: its state, and how blurred it is by its distance from the sung line
internal data class LineLook(val state: LyricsLineState, val blurStage: Int)

internal fun lineLook(index: Int, current: Int, anchor: Int, unsynced: Boolean, interlude: Boolean): LineLook {
    // no timing: every line is equally current, so all of them read at full strength
    if (unsynced) return LineLook(LyricsLineState.ACTIVE, 0)
    val distance = when {
        current < 0 -> index - current
        index < anchor -> index - anchor
        index > current -> index - current
        else -> 0
    }
    val state = when {
        current >= 0 && index in anchor..current ->
            if (interlude) LyricsLineState.PAST else LyricsLineState.ACTIVE
        index == current + 1 -> LyricsLineState.UPCOMING
        index < current -> LyricsLineState.PAST
        else -> LyricsLineState.DEFAULT
    }
    val blurStage = when (kotlin.math.abs(distance)) {
        0 -> 0
        1 -> 1
        else -> 2
    }
    return LineLook(state, blurStage)
}

/**
 * One row of the lyrics list. Instrumental gaps are rows in their own right rather than something
 * a lyric line grows when the playhead reaches it — see the comment at the [buildLyricRows] call
 * site for why that matters to scrolling.
 */
internal sealed interface LyricRow {
    /** Stable across the life of the song, so LazyColumn never re-creates a row. */
    val key: Long

    data class Line(val index: Int, val entry: LyricsEntry) : LyricRow {
        override val key get() = index.toLong()
    }

    /**
     * @param afterIndex the line this gap follows, or -1 for the intro before the first line.
     */
    data class Interlude(val afterIndex: Int, val startMs: Long, val endMs: Long) : LyricRow {
        // Offset into a range no line index can reach, so lines and interludes never collide.
        override val key get() = -1_000_000L - afterIndex
    }
}

internal fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)

    // A long instrumental intro gets dots too. The old code could not show this: it keyed the dots
    // off "the item for currentIndex + 1", and before the first line currentIndex is -1.
    if (lines.first().time >= MinInterludeGapMs) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }

    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = LyricsUtils.lineEndMs(lines, index)
        if (next.time - gapStart >= MinInterludeGapMs) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

/**
 * Maps a line's `{agent:…}` tag onto a side of the screen. `v1` leads on the left, `v2` answers on
 * the right, `v1000` is a group/chorus line and sits centred; background vocals centre as well.
 * [mirrored] swaps the sides, for lyrics written the other way round from the app (see [rememberLyricsMirrored]).
 */
internal fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean, mirrored: Boolean = false): LyricsAlignment {
    val side = when {
        !useAgentAlignment -> LyricsAlignment.START
        entry.isBackground -> LyricsAlignment.CENTER
        entry.agent == "v2" -> LyricsAlignment.END
        entry.agent == "v1000" -> LyricsAlignment.CENTER
        else -> LyricsAlignment.START
    }
    if (!mirrored) return side
    return when (side) {
        LyricsAlignment.START -> LyricsAlignment.END
        LyricsAlignment.END -> LyricsAlignment.START
        LyricsAlignment.CENTER -> LyricsAlignment.CENTER
    }
}

// arabic, hebrew, persian... lyrics read from the right edge, like every other player shows them (#141)
@Composable
internal fun rememberLyricsMirrored(lines: List<LyricsEntry>): Boolean {
    val appRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lyricsRtl = remember(lines) { lyricsReadRtl(lines) }
    return lyricsRtl != appRtl
}

// the whole song goes one way, so a latin line in an arabic song doesn't jump to the other side
internal fun lyricsReadRtl(lines: List<LyricsEntry>): Boolean {
    var rtl = 0
    var ltr = 0
    for (line in lines) {
        for (ch in line.text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> { rtl++; break }
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> { ltr++; break }
            }
        }
    }
    return rtl > ltr
}

@Composable
internal fun LyricsLoadingDots(
    modifier: Modifier = Modifier,
    /** Start-aligned with a little air between the dots for the bottom card; centred on the page. */
    alignStart: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignStart) Arrangement.spacedBy(5.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->

            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "lyricsLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
