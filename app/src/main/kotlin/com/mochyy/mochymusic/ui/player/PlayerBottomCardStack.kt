// PlayerBottomCardStack.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.viewmodels.LyricsScreenViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The card's visible height. Its body carries on below the screen edge - see [BottomBleed]. */
internal val PlayerCardHeight = 54.dp
internal val PlayerCardCorner = 28.dp

/** How far the card in front sits from the screen's bottom edge, back card included. */
internal val PeekOffset = 9.dp

/** The whole deck's height on screen: the card plus the back card peeking over it. */
internal val PlayerDeckHeight = PlayerCardHeight + PeekOffset

/**
 * The card is longer than the screen shows: its body runs on past the bottom edge, so the screen
 * cuts it rather than the card ending in a line. (It used to be sized with a plain `height`, which
 * the 50dp slot clamped right back - the "bleed" never happened and the card's bottom border drew
 * a hard line across the bottom of the screen.)
 */
private val BottomBleed = 100.dp

private val ArtworkSize = 28.dp
private val ArtworkShape = RoundedCornerShape(ArtworkSize * 0.3f)

internal val PeekInset = 10.dp

private const val BackCardScale = 0.94f

private val FlipDistance = 70.dp

// how the deck is drawn; the page growing out of the card starts from exactly the front card's look
@Immutable
internal class PlayerCardLook(
    val corner: Dp,
    val frontFill: Float,
    val frontBorder: Float,
    val backFill: Float,
    val backBorder: Float,
    val borderWidth: Dp,
)

// the classic's glassy card, the one behind peeking over it
internal val ClassicCardLook = PlayerCardLook(PlayerCardCorner, 0.11f, 0.20f, 0.06f, 0.10f, 1.dp)

// the new flagship's faint 4% fill and 12% outline, layered and sized like the classic's, the card behind as much fainter as the classic's is
internal val FlagshipCardLook = PlayerCardLook(PlayerCardCorner, 0.04f, 0.12f, 0.02f, 0.06f, 1.35.dp)

/** Where each card's content starts, so a page can grow it from the right spot. */
private val LyricContentStart = 18.dp
private val QueueContentStart = 14.dp

/** The card content fades as it flies, handing over to the page's copy of it. */
private const val FlightFadeEnd = 0.38f

/** [height], and [extra] px more (read while measuring, so it can change every frame without recomposing). */
private fun Modifier.heightPlus(height: Dp, extra: () -> Float): Modifier = layout { measurable, constraints ->
    val h = (height.toPx() + extra()).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

@Composable
internal fun PlayerBottomCardStack(
    glassState: GlassState,
    progressProvider: () -> Float,
    deck: PlayerDeckState,
    transition: PlayerPageTransition,
    morph: CardMorphState,
    onOpen: (PlayerPage) -> Unit,
    /** Long press opens the player editor, matching the cover's gesture. */
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 1 when the cards are shown whole, their bodies closed off at the bottom instead of running
     * off the screen: the bar's editor lifts the deck up to the middle of the screen.
     */
    closedProvider: () -> Float = { 0f },
    /**
     * How much taller than usual the cards are, px: pulled up in the player editor to show more of
     * the lyrics (and of the queue). Read while laying out and drawing only.
     */
    growthProvider: () -> Float = { 0f },
    look: PlayerCardLook = ClassicCardLook,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val lyricsViewModel: LyricsScreenViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(lyricsViewModel::ensureLyricsLoaded)
    }

    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    // The queue in play order, with the current song's place in that order. The song's index in
    // the player is a different number once shuffled, and reading the list with it showed a
    // "next song" that wasn't the one about to play.
    val queue by playerConnection.queueSnapshot.collectAsState()

    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyricsCached(raw)
    }

    val nextSong = remember(queue) {
        queue.items.getOrNull(queue.currentIndex + 1)
    }

    // A taller lyric card shows each line's reading and translation, so it fills them in the way the
    // lyrics page does. Only while it's tall: otherwise nothing here would show them.
    val tall by remember { derivedStateOf { growthProvider() > 0.5f } }
    if (tall && lines.isNotEmpty()) {
        LyricsReadings(lines, mediaMetadata?.id, com.mochyy.mochymusic.LocalDatabase.current, lyricsEntity)
    }

    val flipDistancePx = with(density) { FlipDistance.toPx() }

    // How far the queue card is in front: the flip, pulled forward while lyrics are open and back
    // while the queue is, so the deck always offers the page that isn't showing.
    fun queueFrontness(): Float {
        val lyricsP = transition.lyricsProgress.coerceIn(0f, 1f)
        val queueP = transition.queueProgress.coerceIn(0f, 1f)
        val flip = deck.flip.value
        return (flip + (1f - flip) * lyricsP) * (1f - queueP)
    }

    // A taller deck makes only the lyric card taller, and only while it's in front. The queue card
    // keeps its usual size with just the next song, and from behind it peeks over the tall card.
    val lyricsGrowth: () -> Float = remember(growthProvider) {
        { growthProvider() * (1f - queueFrontness()).coerceIn(0f, 1f) }
    }
    val queueGrowth: () -> Float = remember { { 0f } }

    // Only the order is decided in composition, and it changes once per flip - everything that
    // moves is read in layers below, so the deck doesn't recompose while it animates.
    val queueOnTop by remember { derivedStateOf { queueFrontness() >= 0.5f } }

    // The front card's slot, through to the screen's bottom edge: where the expanding surface starts
    // from and shrinks back into. The card a page grows out of is the one it's sized from - a
    // short queue card or a tall lyric card - so the surface never jumps between the two mid-way.
    val deckBounds = remember { arrayOfNulls<Rect>(1) }
    fun publishCardRect() {
        val bounds = deckBounds[0] ?: return
        val shortCard = when (transition.linked) {
            PlayerPage.QUEUE -> true
            PlayerPage.LYRICS -> false
            else -> queueOnTop
        }
        val growth = if (shortCard) 0f else growthProvider()
        val cardTop = bounds.bottom - with(density) { PlayerCardHeight.toPx() } - growth
        val slot = Rect(bounds.left, cardTop, bounds.right, bounds.bottom + with(density) { BottomBleed.toPx() })
        if (morph.cardRect != slot) morph.cardRect = slot
    }
    LaunchedEffect(transition.linked, queueOnTop) { publishCardRect() }
    // The card growing into its page is drawn over the other one, at the front slot.
    val flying by remember {
        derivedStateOf {
            val linked = transition.linked
            if (linked != PlayerPage.NONE && transition.headerProgress > 0.0005f) linked else PlayerPage.NONE
        }
    }
    // Fully grown into its page, the card is invisible: don't keep its sweep running behind the
    // page. It comes back as soon as the page starts shrinking, and is still faded out until well
    // into the close, so it has settled by the time it shows.
    val absorbed by remember {
        derivedStateOf {
            val linked = transition.linked
            if (linked != PlayerPage.NONE && transition.headerProgress >= 0.98f) linked else PlayerPage.NONE
        }
    }
    val topCard = when (flying) {
        PlayerPage.NONE -> if (queueOnTop) PlayerPage.QUEUE else PlayerPage.LYRICS
        else -> flying
    }
    val order = if (topCard == PlayerPage.QUEUE) {
        listOf(PlayerPage.LYRICS, PlayerPage.QUEUE)
    } else {
        listOf(PlayerPage.QUEUE, PlayerPage.LYRICS)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightPlus(PlayerDeckHeight, growthProvider)
            .onGloballyPositioned { coords ->
                deckBounds[0] = coords.boundsInRoot()
                publishCardRect()
            }
            // Directional on purpose. A plain vertical `draggable` claimed *every* drag that
            // began on the card, so touching it made the player impossible to swipe closed. The
            // flip only ever runs upward, so a downward drag is left unconsumed for the sheet.
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var claimed = false
                    var travelled = 0f
                    var dragAccum = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dy = change.positionChange().y
                        travelled += dy

                        if (!claimed) {
                            if (travelled < -slop) {
                                claimed = true
                                dragAccum = 0f
                            } else if (travelled > slop) {
                                // Heading down: hand the gesture to the sheet and stop watching.
                                break
                            }
                        }

                        if (claimed) {
                            change.consume()
                            dragAccum += -dy
                            val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
                            val target = if (deck.frontIndex == 0) frac else 1f - frac
                            scope.launch { deck.flip.snapTo(target) }
                        }
                    }

                    if (claimed) {
                        if (abs(dragAccum) >= flipDistancePx * 0.4f) {
                            deck.frontIndex = 1 - deck.frontIndex
                        }
                        scope.launch {
                            deck.flip.animateTo(
                                targetValue = deck.frontIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    }
                }
            }
            .pointerInput(onLongPress) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    // Whichever card is in front, not the flip alone: with a page open the deck
                    // reorders itself, and tapping the card on top has to open that card.
                    onTap = { onOpen(if (queueFrontness() >= 0.5f) PlayerPage.QUEUE else PlayerPage.LYRICS) },
                )
            }
    ) {
        // Keyed and in one loop, so reordering the deck moves the cards instead of rebuilding
        // them - the lyric card keeps its line, its sweep and its slide through a flip.
        for (page in order) {
            if (page == absorbed) continue
            key(page) {
                DeckCard(
                    page = page,
                    look = look,
                    frontnessProvider = {
                        val q = queueFrontness()
                        if (page == PlayerPage.QUEUE) q else 1f - q
                    },
                    transition = transition,
                    morph = morph,
                    closedProvider = closedProvider,
                    ownGrowth = if (page == PlayerPage.LYRICS) lyricsGrowth else queueGrowth,
                    otherGrowth = if (page == PlayerPage.LYRICS) queueGrowth else lyricsGrowth,
                ) {
                    if (page == PlayerPage.LYRICS) {
                        MiniLyricsTicker(
                            lines = lines,
                            loaded = lyricsEntity != null,
                            morph = morph,
                            growthProvider = lyricsGrowth,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = LyricContentStart, end = LyricContentStart),
                        )
                    } else {
                        QueueCardContent(
                            nextTitle = nextSong?.title,
                            nextArtist = nextSong?.artist,
                            artworkUri = nextSong?.artworkUri?.toString(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DeckCard(
    page: PlayerPage,
    look: PlayerCardLook,
    frontnessProvider: () -> Float,
    transition: PlayerPageTransition,
    morph: CardMorphState,
    closedProvider: () -> Float,
    /** How much taller than usual this card is, px, and the other card in the deck. */
    ownGrowth: () -> Float,
    otherGrowth: () -> Float,
    content: @Composable () -> Unit,
) {
    // Growing into its page: the card's surface is handed to the expanding one (drawn by the
    // player), and its content flies to where the page has the same thing.
    fun flightProgress(): Float =
        if (transition.linked == page) transition.headerProgress else 0f

    fun frontness(): Float =
        if (flightProgress() > 0.0005f) 1f else frontnessProvider().coerceIn(0f, 1f)

    val density = LocalDensity.current

    // Behind a taller card, a card is lifted by the difference, so it still peeks over the top of
    // the one in front instead of hiding behind it.
    fun lift(f: Float): Float = (otherGrowth() - ownGrowth()).coerceAtLeast(0f) * BackCardScale * (1f - f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightPlus(PlayerCardHeight, ownGrowth)
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                val f = frontness()
                val scale = mixF(BackCardScale, 1f, f)
                scaleX = scale
                scaleY = scale
                translationY = -mixF(PeekOffset.toPx(), 0f, f) - lift(f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                if (flightProgress() > 0.0005f) return@drawBehind
                val f = frontness()
                val inset = mixF(PeekInset.toPx(), 0f, f)
                val corner = look.corner.toPx()
                // Only the strip of a back card that shows above the front one is drawn. Each card's
                // top sits its own height above the bottom (scaled when behind), raised by the peek
                // and, behind a taller card, by the lift.
                val scale = mixF(BackCardScale, 1f, f)
                val otherF = 1f - f
                val otherScale = mixF(BackCardScale, 1f, otherF)
                val baseHeight = PlayerCardHeight.toPx()
                val thisTop = size.height * scale + mixF(PeekOffset.toPx(), 0f, f) + lift(f)
                val otherLift = (ownGrowth() - otherGrowth()).coerceAtLeast(0f) * BackCardScale * (1f - otherF)
                val otherTop = (baseHeight + otherGrowth()) * otherScale +
                    mixF(PeekOffset.toPx(), 0f, otherF) + otherLift
                val exposed = (thisTop - otherTop) / scale
                val bottom = size.height + BottomBleed.toPx() * (1f - closedProvider().coerceIn(0f, 1f))
                val visibleBottom = if (exposed > 0f) minOf(exposed, bottom) else bottom
                if (visibleBottom <= 0f) return@drawBehind

                clipRect(bottom = visibleBottom) {
                    val topLeft = Offset(inset, 0f)
                    val cardSize = Size(size.width - inset * 2f, bottom)
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(look.backFill, look.frontFill, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    // The border runs down the sides and off the screen with the body, so there's
                    // no line along the bottom (unless the editor has closed the card off).
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(look.backBorder, look.frontBorder, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = look.borderWidth.toPx()),
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    // Measured only at rest in front: mid-flip the card is scaled, mid-flight the
                    // numbers would chase themselves.
                    if (flightProgress() > 0.0005f || frontnessProvider() < 0.999f) return@onGloballyPositioned
                    val r = coords.boundsInRoot()
                    with(density) {
                        // the card's own row: all of a usual card, the top of a taller one
                        val rowCenter = r.top + PlayerCardHeight.toPx() / 2f
                        if (page == PlayerPage.LYRICS) {
                            morph.cardLyricAnchor = Offset(r.left + LyricContentStart.toPx(), rowCenter)
                        } else {
                            morph.cardArtAnchor = Offset(
                                r.left + QueueContentStart.toPx(),
                                rowCenter - ArtworkSize.toPx() / 2f,
                            )
                        }
                    }
                }
                .graphicsLayer {
                    val e = flightProgress()
                    if (e <= 0.0005f) {
                        val f = frontness()
                        alpha = f * f * (3f - 2f * f)
                        return@graphicsLayer
                    }
                    alpha = 1f - smoothstepF(e / FlightFadeEnd)
                    val source: Offset
                    val target: Offset
                    val grow: Float
                    if (page == PlayerPage.LYRICS) {
                        source = morph.cardLyricAnchor.let {
                            if (it.isSpecified()) Offset(it.x - morph.cardLyricScroll, it.y) else it
                        }
                        target = morph.pageLyricAnchor
                        grow = 1f / CardToPageLyricScale
                    } else {
                        source = morph.cardArtAnchor
                        target = morph.pageArtAnchor
                        grow = 1f / CardToPageArtScale
                    }
                    if (!source.isSpecified() || !target.isSpecified()) {
                        // Nothing on the page to become: lift off and fade.
                        translationY = -e * 40.dp.toPx()
                        return@graphicsLayer
                    }
                    // Pivot on the anchor itself, so scaling grows the content out of that point
                    // and the translation carries the point along the path.
                    val start = if (page == PlayerPage.LYRICS) {
                        LyricContentStart.toPx() - morph.cardLyricScroll
                    } else {
                        QueueContentStart.toPx()
                    }
                    val rowCenter = PlayerCardHeight.toPx() / 2f
                    val pivotY = if (page == PlayerPage.LYRICS) {
                        rowCenter
                    } else {
                        rowCenter - ArtworkSize.toPx() / 2f
                    }
                    transformOrigin = TransformOrigin(
                        (start / size.width).coerceIn(-4f, 4f),
                        pivotY / size.height,
                    )
                    val s = mixF(1f, grow, e)
                    scaleX = s
                    scaleY = s
                    translationX = (target.x - source.x) * e
                    translationY = (target.y - source.y) * e
                }
        ) {
            content()
        }
    }
}

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified

/** The queue card: just the next song, the same however tall the deck is. */
@Composable
private fun QueueCardContent(
    nextTitle: String?,
    nextArtist: String?,
    artworkUri: String?,
) {
    Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
        NextSongRow(nextTitle, nextArtist, artworkUri)
    }
}

/** the queue card's own row: the next song's artwork, and its title and artist */
@Composable
private fun NextSongRow(
    nextTitle: String?,
    nextArtist: String?,
    artworkUri: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayerCardHeight)
            .padding(horizontal = QueueContentStart)
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).fadingRightEdge()) {

            Text(
                text = "Next Song",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = if (nextTitle != null) {
                    listOfNotNull(nextTitle, nextArtist?.takeIf { it.isNotBlank() })
                        .joinToString(" - ")
                } else {
                    "End of queue"
                },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (nextTitle != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private fun Modifier.fadingRightEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fade = Brush.horizontalGradient(
            0f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = fade, blendMode = BlendMode.DstIn)
        }
    }
