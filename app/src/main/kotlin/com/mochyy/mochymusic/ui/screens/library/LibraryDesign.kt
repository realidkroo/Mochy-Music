// LibraryDesign.kt
//
// Building blocks for the Library tab and its sub-screens (Songs, Artists, Playlists, Albums,
// Recently added, Downloaded).
//
// Reuses SearchColors / SearchField / SearchGlassTopBar from the Search rebuild rather than
// introducing a second palette and a second collapsing header. The two tabs sit one press apart in
// the nav bar; a near-identical-but-not-quite header would read as a bug, and sharing the actual
// implementation is the only way to guarantee the collapse feels the same in both places.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.ui.component.containerTransformSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.HomeTrackRow
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.ui.screens.search.SearchTitleBlockHeight
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** Title + subtitle block height, for the collapsing header on Library screens. */
val LibraryTitleBlockHeight: Dp = SearchTitleBlockHeight + 16.dp

// ---------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .height(1.dp)
            .background(SearchColors.Divider),
    )
}

@Composable
fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = SearchHorizontalPadding),
    )
}

/**
 * Artwork whose size comes entirely from the caller's modifier, for grid cells whose width isn't
 * known ahead of time.
 *
 * Not a wrapper around SearchArtwork: that one always ends its own chain with `.size(size)`, which
 * chained after a `fillMaxWidth().aspectRatio(1f)` collapses the cell — passing `0.dp` there
 * doesn't opt out of sizing, it forces the artwork to zero.
 *
 * Always a rounded square. The Library deliberately has no circular artwork anywhere, including
 * artists: one shape throughout means rows and grids line up on a single edge, and mixed shapes in
 * the same column read as inconsistency rather than as a category cue.
 */
@Composable
fun LibraryGridArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    round: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(if (round) CircleShape else RoundedCornerShape(corner))
            .background(SearchColors.TileHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(400, 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.fillMaxSize(0.35f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pinned
// ---------------------------------------------------------------------------------------------

/**
 * A pinned item: artwork only, no label. With a 3-wide grid and a 9-item cap the art alone
 * identifies what's pinned, and a caption under every tile would be nine repetitions of noise.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPinnedTile(
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** a pinned album or playlist opens growing out of its tile, as from Home */
    sharedElementKey: String? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .containerTransformSource(key = sharedElementKey, cornerRadius = 18.dp, coverUrl = thumbnailUrl)
            .clip(RoundedCornerShape(18.dp))
            .background(SearchColors.TileHigh)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl.resize(300, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category + wide cards
// ---------------------------------------------------------------------------------------------

/**
 * Two real covers, fanned and tucked into the bottom-right corner of a card.
 *
 * This is what makes the cards specific rather than decorative: each shows two of that category's
 * actual contents. The back tile is dimmed and turned the other way so the pair reads as a stack
 * rather than two unrelated thumbnails side by side.
 *
 * Fills from the right, because the stack sits in the bottom-right corner — the rightmost tile is
 * the one fully on screen, so the first cover belongs there and the second tucks in behind it.
 * Draws nothing at all when there are no covers: an empty category should leave the corner bare,
 * not show placeholder squares implying content that isn't there. [front] replaces the first
 * cover with art of the list's own (Liked songs).
 */
@Composable
private fun LibraryCoverStack(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    tile: Dp = 46.dp,
    round: Boolean = false,
    front: (@Composable (Modifier) -> Unit)? = null,
) {
    val shown = remember(covers) { covers.filterNotNull().take(2) }
    if (shown.isEmpty() && front == null) return
    val back = if (front != null) shown.firstOrNull() else shown.getOrNull(1)
    val corner = if (round) tile / 2 else tile * 0.22f

    Box(modifier = modifier.size(width = tile * 1.55f, height = tile)) {
        // Back cover first, so the front one overlaps it.
        if (back != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer {
                        alpha = 0.55f
                        rotationZ = if (round) 0f else -9f
                    },
            ) {
                LibraryGridArtwork(url = back, corner = corner, round = round, modifier = Modifier.fillMaxSize())
            }
        }
        val frontModifier = Modifier
            .align(Alignment.CenterEnd)
            .size(tile)
            .graphicsLayer { rotationZ = if (round) 0f else 7f }
        if (front != null) {
            front(frontModifier.clip(if (round) CircleShape else RoundedCornerShape(corner)))
        } else {
            Box(modifier = frontModifier) {
                LibraryGridArtwork(url = shown[0], corner = corner, round = round, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The big grey count on every Library card. */
private val LibraryCountStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.03).em,
)

/** A card's label, a step above its count. */
@Composable
private fun LibraryCardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun LibraryCount(count: Int) {
    Text(
        text = count.toString(),
        color = SearchColors.Secondary,
        style = LibraryCountStyle,
        maxLines = 1,
    )
}

/** Centred placeholder for a screen or section with nothing in it. */
@Composable
fun LibraryEmptyState(modifier: Modifier = Modifier, text: String = stringResource(R.string.lib_nothing_to_show)) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = SearchHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * One of the half-width cards: Songs / Playlist / Albums / Artist, and one per service music was
 * imported from, which carries that service's [icon] before its name. The name sits on top and
 * the count reads big under it, so the numbers line up down the grid.
 */
@Composable
fun LibraryCategoryCard(
    title: String,
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    /** Artists: their covers are circles. */
    roundCovers: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SearchColors.Tile)
            .clickable(onClick = onClick),
    ) {
        // The covers sit flush in the corner and are allowed to run past the card's edge — the
        // clip above crops them, which is what gives the "tucked in" look rather than a floating
        // thumbnail with padding around it.
        LibraryCoverStack(
            covers = covers,
            round = roundCovers,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 11.dp, end = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                LibraryCardLabel(title)
            }
            LibraryCount(count)
        }
    }
}

/** The full-width cards: "Recently added" and "Liked". */
@Composable
fun LibraryWideCard(
    title: String,
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** a card that opens a playlist page (Liked, Downloaded...) grows into it, as from Home */
    sharedElementKey: String? = null,
    /** a cover of the list's own in front of the stack (Liked songs); it fills the modifier given */
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .containerTransformSource(
                key = sharedElementKey,
                cornerRadius = 18.dp,
                coverUrl = covers.firstOrNull { it != null },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(SearchColors.Tile)
            .clickable(onClick = onClick),
    ) {
        LibraryCoverStack(
            covers = covers,
            tile = 60.dp,
            front = art,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 14.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp, end = 110.dp),
        ) {
            LibraryCardLabel(title)
            LibraryCount(count)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List rows
// ---------------------------------------------------------------------------------------------

/**
 * A row in the A-Z lists, drawn exactly like Home's track rows: the same 54dp cover and corners,
 * the same type, no pill behind it - only the soft highlight while pressed - and no "more" button,
 * since a long press already opens the menu. The playing song gets Home's playing indicator.
 */
@Composable
fun LibraryListRow(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    /** Artists: a round picture. */
    round: Boolean = false,
    /** A playlist's "playlist-<id>": its page opens growing out of this cover, as from Home. */
    sharedElementKey: String? = null,
    /** A song's id: marks the row when the song is downloaded or an imported file. */
    songId: String? = null,
) {
    HomeTrackRow(
        thumbnailUrl = thumbnailUrl,
        title = title,
        subtitle = subtitle,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        onMoreClick = null,
        coverShape = if (round) CircleShape else LibraryRowCoverShape,
        songId = songId,
        coverModifier = Modifier.containerTransformSource(
            key = sharedElementKey,
            cornerRadius = if (round) LibraryRowCoverSize / 2 else LibraryRowCoverCorner,
            coverUrl = thumbnailUrl,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding, vertical = 3.dp),
    )
}

/** Home's row cover, so the Library's rows match it exactly. */
private val LibraryRowCoverCorner = 14.dp
private val LibraryRowCoverSize = 54.dp
private val LibraryRowCoverShape = RoundedCornerShape(LibraryRowCoverCorner)

/** The header above each section of an A-Z list: its letter, kana row, initial... */
@Composable
fun LibraryLetterHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = SearchColors.Primary,
        style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.03).em,
        ),
        modifier = modifier.padding(horizontal = SearchHorizontalPadding, vertical = 10.dp),
    )
}

/** Play and shuffle above the Albums and Recently-added grids: the playlist page's pill and circle */
@Composable
fun LibraryPlayBar(
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.mochyy.mochymusic.ui.component.detail.PlayPill(isPlaying = false, onClick = onPlay)
        Spacer(modifier = Modifier.width(9.dp))
        com.mochyy.mochymusic.ui.component.detail.CircleAction(
            icon = R.drawable.shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onMore,
            spinOnClick = true,
        )
    }
}
