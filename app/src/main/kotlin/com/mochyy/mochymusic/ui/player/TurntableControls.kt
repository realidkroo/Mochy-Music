// TurntableControls.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.ShowFormatBadgeKey
import com.mochyy.mochymusic.constants.ShowFormatSampleRateKey
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference

// The Turntable frame's controls, at its sizes: four pills stacked on the right (like, back,
// play/pause, forward) above the title row - the chevron on the left, the title and its format
// badge running the width under them.
/** a pill's width, for a layout that leaves room for the stack */
val TurntablePillWidth = 59.dp
private val PillWidth = TurntablePillWidth
private val PillHeight = 42.dp
private val PillGap = 11.dp
private val ChevronSize = 43.dp
private val SideMargin = 30.dp
private val PillFill = Color.White.copy(alpha = 0.08f)
/** between the stack and the title row, on top of the stack's own gap */
private val TitleGap = 4.dp

@Composable
fun TurntableControls(
    isExpandMenuOpen: Boolean,
    onToggleExpandMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /** hides the chevron while the lyrics page's own (the same control, moved) is up */
    hideChevron: Boolean = false,
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onChevronPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    likeBurst: LikeBurstState? = null,
    onSongInfoClick: (() -> Unit)? = null,
    /** a tap on the format badge opens the playing stream's info */
    onStreamInfoClick: (() -> Unit)? = null,
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(PillGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SideMargin, end = SideMargin),
    ) {
        TurntablePills(likeBurst = likeBurst, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.height(TitleGap))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ChevronButton(
                open = isExpandMenuOpen,
                hidden = hideChevron,
                onClick = onToggleExpandMenu,
                modifier = Modifier.onGloballyPositioned { onChevronPositioned(it.boundsInRoot()) },
            )
            Spacer(Modifier.width(14.dp))
            TitleBlock(
                title = track.title,
                artist = track.artist,
                mediaId = track.mediaId,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { onTitlePositioned(it.boundsInRoot()) }
                    .then(
                        if (onSongInfoClick != null) Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onSongInfoClick,
                        ) else Modifier
                    ),
                onBadgeClick = onStreamInfoClick,
            )
        }
    }
}

/**
 * The Turntable's stack of pills, top to bottom: like, back, play/pause, forward. Holding back or
 * forward plays slower or faster while held. The Poster wears the same stack.
 */
@Composable
fun TurntablePills(modifier: Modifier = Modifier, likeBurst: LikeBurstState? = null, horizontal: Boolean = false) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val transport by connection.uiState.transportState.collectAsState()

    val holdSpeed = remember(connection) { HoldSpeed(connection.player) }
    DisposableEffect(holdSpeed) { onDispose { holdSpeed.end() } }

    val pills: @Composable () -> Unit = {
        Pill {
            val liked = track.liked
            PressScaleIcon(
                icon = if (liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                tint = if (liked) Color.White else Color.White.copy(alpha = 0.85f),
                onClick = {
                    if (likeBurst != null) likeBurst.likeFromButton(LikeSlot.PLAYER, liked, connection::toggleLike)
                    else connection.toggleLike()
                },
                modifier = Modifier.likeButtonTarget(likeBurst, LikeSlot.PLAYER),
            )
        }
        Pill {
            AnimatedPressScaleSkipButton(
                backwards = true,
                onClick = connection::seekToPrevious,
                enabled = transport.canSkipPrevious,
                tint = Color.White,
                iconSize = 24.dp,
                onHoldStart = { holdSpeed.start(HoldSlowFactor) },
                onHoldEnd = holdSpeed::end,
                holdLabel = "0.5×",
                holdPulseMs = 900L,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Pill {
            AnimatedPressScalePlayPauseButton(
                isPlaying = transport.isPlaying,
                playbackState = transport.playbackState,
                onClick = {
                    if (transport.playbackState == Player.STATE_ENDED) {
                        connection.player.seekTo(0, 0)
                        connection.player.playWhenReady = true
                    } else {
                        connection.togglePlayPause()
                    }
                },
                tint = Color.White,
                iconSize = 26.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Pill {
            AnimatedPressScaleSkipButton(
                backwards = false,
                onClick = connection::seekToNext,
                enabled = transport.canSkipNext,
                tint = Color.White,
                iconSize = 24.dp,
                onHoldStart = { holdSpeed.start(HoldFastFactor) },
                onHoldEnd = holdSpeed::end,
                holdLabel = "2×",
                holdPulseMs = 420L,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (horizontal) {
        Row(horizontalArrangement = Arrangement.spacedBy(PillGap), modifier = modifier) { pills() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(PillGap), modifier = modifier) { pills() }
    }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PillWidth, PillHeight)
            .clip(RoundedCornerShape(50))
            .background(PillFill),
    ) { content() }
}

@Composable
private fun PressScaleIcon(icon: Int, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.54f, stiffness = 720f), label = "pillPress")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier
                .size(20.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
private fun ChevronButton(open: Boolean, hidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "ttChevron")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.54f, stiffness = 720f), label = "ttChevronPress")
    // Tapped on a box that holds still, the press shrinking only what's drawn: with the click on
    // the shrinking layer, a tap near the edge was dropped on screens that report a resting
    // finger's tremble as moves (see PressScaleActionButton).
    Box(
        modifier = modifier
            .size(ChevronSize)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    alpha = if (open || hidden) 0f else 1f
                }
                .clip(CircleShape)
                .background(PillFill),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.expand_less),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

/** The song's title, badge and artist, riding the player's row of songs with the record. */
@Composable
private fun TitleBlock(
    title: String,
    artist: String,
    mediaId: String,
    modifier: Modifier = Modifier,
    onBadgeClick: (() -> Unit)? = null,
) {
    val carousel = LocalSongCarousel.current
    Box(modifier = modifier) {
        if (carousel == null) {
            TitleLines(mediaId, title, artist, Modifier, onBadgeClick)
        } else {
            for (slot in rememberTextSlots(carousel)) {
                key(slot.uid) {
                    TitleLines(slot.mediaId, slot.title, slot.artist, Modifier.carouselText(carousel, slot), onBadgeClick)
                }
            }
        }
    }
}

@Composable
private fun TitleLines(mediaId: String, title: String, artist: String, modifier: Modifier, onBadgeClick: (() -> Unit)?) {
    val connection = LocalPlayerConnection.current ?: return
    val (showBadge) = rememberPreference(ShowFormatBadgeKey, defaultValue = true)
    val (showSampleRate) = rememberPreference(ShowFormatSampleRateKey, defaultValue = false)
    Column(modifier = modifier) {
        val facts by remember(mediaId) { connection.streamFacts(mediaId) }.collectAsState(initial = null)
        val badge = if (showBadge) streamBadgeText(facts, showSampleRate) else null
        Row(verticalAlignment = Alignment.CenterVertically) {
            FadingOverflowText(
                text = title,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    letterSpacing = (-0.035).em,
                ),
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                StreamFormatBadge(
                    text = badge,
                    height = 18.dp,
                    modifier = if (onBadgeClick != null) {
                        Modifier.clickable(interactionSource = null, indication = null, onClick = onBadgeClick)
                    } else Modifier,
                )
            }
        }
        if (artist.isNotBlank()) {
            Text(
                text = artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.02).em,
                    color = Color.White.copy(alpha = 0.55f),
                ),
            )
        }
    }
}
