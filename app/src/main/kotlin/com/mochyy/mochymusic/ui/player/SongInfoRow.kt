// SongInfoRow.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerHorizontalPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.mochyy.mochymusic.constants.ShowFormatBadgeKey
import com.mochyy.mochymusic.constants.ShowFormatSampleRateKey
import com.mochyy.mochymusic.utils.rememberPreference

@Composable
fun SongInfoRow(
    modifier: Modifier = Modifier,
    isExpandMenuOpen: Boolean = false,
    /** Hides this row's chevron while the lyrics page's chevron (the same control, moved) is up. */
    hideChevron: Boolean = false,
    onToggleExpandMenu: () -> Unit = {},
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onChevronPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    /** Sends a heart flying when the like button likes the song. */
    likeBurst: LikeBurstState? = null,
    /** What the round button beside the chevron does (set in the editor's menu); a like out of the box. */
    sideAction: com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction = com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE,
    /** [sideAction] is on (repeat on, shuffle on, a sleep timer running...), for its highlight. */
    sideActive: Boolean = false,
    onSideAction: (com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction) -> Unit = {},
    /** Where the two round buttons sit, for the editor, which pulls copies of them forward. */
    onSidePlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    onChevronPlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    /** On the two round buttons themselves: the editor lifts them off the row to edit them. */
    sideModifier: Modifier = Modifier,
    chevronModifier: Modifier = Modifier,
    /** A tap on the title and artist: the song's details sheet (its artists, album, about). */
    onSongInfoClick: (() -> Unit)? = null,
    /** A tap on the format badge: the playing stream's info, straight away. */
    onStreamInfoClick: (() -> Unit)? = null,
    /**
     * How much of the title and artist shows. The page header's title takes their place as a page
     * opens, drawn exactly like them, so this hides them the moment it does. Read while drawing.
     */
    infoAlpha: () -> Float = { 1f },
    /** How much of the format badge shows; it has nowhere to go in the header, so it fades. */
    badgeAlpha: () -> Float = { 1f },
    /** Where the side button goes as a page opens (into the page's button column, or behind the chevron). */
    sideLayer: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    onSidePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    // the player's row of songs: the title rides it with the cover
    val carousel = LocalSongCarousel.current

    val (showFormatBadge) = rememberPreference(ShowFormatBadgeKey, defaultValue = true)
    val (showSampleRate) = rememberPreference(ShowFormatSampleRateKey, defaultValue = false)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { onTitlePositioned(it.boundsInRoot()) }
                .then(
                    if (onSongInfoClick != null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onSongInfoClick,
                        )
                    } else Modifier
                )

                // Only the last few dp fade here, softening what slides past the edge on a song
                // change; each line fades its own overflow, so the format badge after the title
                // stays crisp. The left edge fades too, only while a title is crossing it: cut
                // straight there, the outgoing title's first letters were sliced off.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val edge = (TitleEdgeFade.toPx() / size.width).coerceIn(0f, 1f)
                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        (1f - edge) to Color.Black,
                        1f to Color.Transparent,
                    )
                    val leftFadeWidth = TitleEdgeFade.toPx() * 2f
                    val leftFade = Brush.horizontalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        endX = leftFadeWidth,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        val crossing = carousel?.let { row ->
                            // strongest half way between two songs, none at either end
                            val between = row.position - kotlin.math.floor(row.position)
                            (minOf(between, 1f - between) * 8f).coerceIn(0f, 1f)
                        } ?: 0f
                        if (crossing > 0f) {
                            drawRect(
                                brush = leftFade,
                                size = androidx.compose.ui.geometry.Size(leftFadeWidth, size.height),
                                alpha = crossing,
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    }
                }
        ) {
            // the leaving and arriving songs' lines, riding the row with the cover
            Box(modifier = Modifier.fillMaxWidth()) {
                if (carousel == null) {
                    SongLines(
                        mediaId = trackInfo.mediaId,
                        title = trackInfo.title,
                        artist = trackInfo.artist,
                        showFormatBadge = showFormatBadge,
                        showSampleRate = showSampleRate,
                        infoAlpha = infoAlpha,
                        badgeAlpha = badgeAlpha,
                        onBadgeClick = onStreamInfoClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    for (slot in rememberTextSlots(carousel)) {
                        key(slot.uid) {
                            SongLines(
                                mediaId = slot.mediaId,
                                title = slot.title,
                                artist = slot.artist,
                                showFormatBadge = showFormatBadge,
                                showSampleRate = showSampleRate,
                                infoAlpha = infoAlpha,
                                badgeAlpha = badgeAlpha,
                                onBadgeClick = onStreamInfoClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .carouselText(carousel, slot),
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val sidePlaced = (onSidePlaced?.let { Modifier.onGloballyPositioned(it) } ?: Modifier)
                .onGloballyPositioned { onSidePositioned(it.boundsInRoot()) }
                .then(sideModifier)
                .then(if (sideLayer != null) Modifier.graphicsLayer(sideLayer) else Modifier)
            if (sideAction == com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE) {
                PressScaleActionButton(
                    icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                    tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                    containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                    onClick = {
                        if (likeBurst != null) {
                            likeBurst.likeFromButton(LikeSlot.PLAYER, trackInfo.liked, playerConnection::toggleLike)
                        } else {
                            playerConnection.toggleLike()
                        }
                    },
                    hasShadow = false,
                    modifier = sidePlaced.likeButtonTarget(likeBurst, LikeSlot.PLAYER),
                )
            } else {
                PressScaleActionButton(
                    icon = sideAction.icon,
                    tint = if (sideActive) Color.White else Color.White.copy(alpha = 0.85f),
                    containerColor = if (sideActive) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                    onClick = { onSideAction(sideAction) },
                    hasShadow = false,
                    modifier = sidePlaced,
                )
            }
            val arrowRotation by animateFloatAsState(
                targetValue = if (isExpandMenuOpen) 180f else 0f, 
                label = "arrowRot",
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
            )
            val arrowScale by animateFloatAsState(
                targetValue = if (isExpandMenuOpen) 0.86f else 1f, 
                label = "arrowScale",
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
            )
            val chevronBgColor by animateColorAsState(
                targetValue = if (isExpandMenuOpen) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                label = "chevronBgColor",
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            val chevronTint by animateColorAsState(
                targetValue = if (isExpandMenuOpen) Color.White else Color.White.copy(alpha = 0.90f),
                label = "chevronTint",
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            PressScaleActionButton(
                icon = R.drawable.expand_less,
                tint = chevronTint,
                containerColor = chevronBgColor,
                onClick = onToggleExpandMenu,
                hasShadow = false,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        onChevronPositioned(coords.boundsInRoot())
                        onChevronPlaced?.invoke(coords)
                    }
                    .then(chevronModifier)
                    .graphicsLayer {
                        rotationZ = arrowRotation
                        scaleX = arrowScale
                        scaleY = arrowScale
                        alpha = if (isExpandMenuOpen || hideChevron) 0f else 1f
                    }
            )
        }
    }
}

/** One song's title, format badge and artist. */
@Composable
private fun SongLines(
    mediaId: String,
    title: String,
    artist: String,
    showFormatBadge: Boolean,
    showSampleRate: Boolean,
    infoAlpha: () -> Float,
    badgeAlpha: () -> Float,
    onBadgeClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    Column(modifier = modifier) {
        // each song keeps its own badge through the slide
        val facts by remember(mediaId) {
            playerConnection.streamFacts(mediaId)
        }.collectAsState(initial = null)
        val larp = rememberLarpMode()
        val badge = when {
            larp != null -> larpBadgeText(larp)
            showFormatBadge -> streamBadgeText(facts, showSampleRate)
            else -> null
        }
        // what the badge said, so it can still say it while it leaves
        val lastBadge = remember { arrayOfNulls<String>(1) }
        if (badge != null) lastBadge[0] = badge

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // A long title gives way to the badge: it ends short of it, fades out where
            // it's cut and scrolls by, as it did against the buttons.
            FadingOverflowText(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .graphicsLayer { alpha = infoAlpha() },
            )
            AnimatedVisibility(
                visible = badge != null,
                modifier = Modifier.graphicsLayer { alpha = badgeAlpha() },
                enter = fadeIn(tween(220)) + expandHorizontally(tween(280, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(160)) + shrinkHorizontally(tween(240, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    StreamFormatBadge(
                        text = badge ?: lastBadge[0].orEmpty(),
                        modifier = if (onBadgeClick != null) {
                            Modifier.clickable(interactionSource = null, indication = null, onClick = onBadgeClick)
                        } else Modifier,
                    )
                    // clear of the row's edge fade
                    Spacer(Modifier.width(TitleEdgeFade))
                }
            }
        }
        if (artist.isNotBlank()) {
            Text(
                text = artist,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = infoAlpha() }
                    .trailingFade()
                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
            )
        }
    }
}

/** How much of the song info's right edge fades, softening what slides past it. */
private val TitleEdgeFade = 10.dp

/** The song info's old fade: its last 18%, transparent at the edge; [cut] says when it applies. */
private fun Modifier.trailingFade(cut: (shownWidth: Float) -> Boolean = { true }): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fade = Brush.horizontalGradient(
            0f to Color.Black,
            0.82f to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            if (cut(size.width)) drawRect(brush = fade, blendMode = BlendMode.DstIn)
        }
    }

/**
 * One line that scrolls by once when it doesn't fit, fading where it's cut. A title that fits
 * isn't faded at all, so whatever sits right after it (the format badge) meets its last letter.
 */
@Composable
internal fun FadingOverflowText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // The marquee lays the text out at its full width, wider than it's shown when it's cut. Read
    // while drawing, so a new width redraws the fade without recomposing.
    var fullWidth by remember { mutableIntStateOf(0) }
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        color = color,
        onTextLayout = { fullWidth = it.size.width },
        modifier = modifier
            .trailingFade { shown -> fullWidth > shown + 0.5f }
            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
    )
}

/** Shared with the lyrics page, so its repeat/like pair matches the player's exactly. */
@Composable
fun PressScaleActionButton(
    icon: Int,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boldIcon: Boolean = false,
    badgeText: String? = null,
    hasShadow: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 34.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleActionButton"
    )

    // The tap is taken on a box that holds still; the press only shrinks what's drawn inside it.
    // With the click on the shrinking layer, its bounds shrank under a resting finger, and on
    // screens that report the finger's slightest tremble as a move, a tap near the button's edge
    // read as sliding off it and was dropped - the chevron that wouldn't open on some phones.
    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hasShadow) Modifier.shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                        else Modifier
                    )
                    .clip(CircleShape)
                    .background(containerColor)
            ) {

                if (boldIcon) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                        modifier = Modifier
                            .size(18.dp)
                            .offset(x = 0.45.dp)
                    )
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(18.dp)
                )
            }
            // outside the round clip, sitting on the rim: inside it the circle cut the 1 in half
            if (badgeText != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tint)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.Black,
                        fontSize = 7.sp,
                        lineHeight = 7.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
