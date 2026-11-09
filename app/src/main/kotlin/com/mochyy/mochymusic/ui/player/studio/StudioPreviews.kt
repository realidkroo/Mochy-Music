package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.DefaultPlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerBackgroundStyleKey
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import com.mochyy.mochymusic.constants.PlayerBottomBarStyleKey
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerCoverStyleKey
import com.mochyy.mochymusic.constants.FlagshipBackgroundStyleKey
import com.mochyy.mochymusic.constants.FlagshipBottomBarStyleKey
import com.mochyy.mochymusic.constants.FlagshipTransportLayout
import com.mochyy.mochymusic.constants.FlagshipTransportLayoutKey
import com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop
import com.mochyy.mochymusic.ui.player.flagship.FlagshipBottomBar
import com.mochyy.mochymusic.ui.player.flagship.FlagshipTransportRow
import com.mochyy.mochymusic.ui.player.flagship.FollowPicture
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import com.mochyy.mochymusic.ui.player.PlayerProgressSlider
import com.mochyy.mochymusic.ui.player.PlayerTransportRow
import com.mochyy.mochymusic.ui.player.PlayerVolumeBar
import com.mochyy.mochymusic.ui.player.FlagshipCardLook
import com.mochyy.mochymusic.ui.player.SongInfoRow
import com.mochyy.mochymusic.ui.player.customize.DiscCoverStack
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview
import com.mochyy.mochymusic.ui.player.customize.coverArtBox
import com.mochyy.mochymusic.ui.player.customize.isDisc
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import com.mochyy.mochymusic.constants.ShowPlayerBottomCardKey
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.player.ClassicCardLook
import com.mochyy.mochymusic.ui.player.ControlsDrawShift
import com.mochyy.mochymusic.ui.player.PlayerDeckHeight
import com.mochyy.mochymusic.ui.player.PlayerDeckPreview
import com.mochyy.mochymusic.ui.player.PlayerVolumeBarHeight
import com.mochyy.mochymusic.ui.player.SeamBlur
import com.mochyy.mochymusic.ui.player.SeekBarLift
import com.mochyy.mochymusic.ui.player.TransportGapAboveRow
import com.mochyy.mochymusic.ui.player.TurntableControls
import com.mochyy.mochymusic.ui.player.TurntableControlsLift
import com.mochyy.mochymusic.ui.player.flagship.FlagshipBarHeight
import com.mochyy.mochymusic.ui.player.flagship.flagshipBarFootprint
import com.mochyy.mochymusic.ui.player.playerVolumeBarFootprint
import com.mochyy.mochymusic.utils.rememberPreference

/** swallows every touch, so a preview shows its content without playing it */
internal fun Modifier.inert(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** the cover as [style] draws it, filling its box: the same art the player shows */
@Composable
internal fun CoverArt(
    style: PlayerCoverStyle,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    queueIndex: Int,
    modifier: Modifier = Modifier,
    // the edge-to-edge cover fading its own bottom out, as the player does with blur off
    fadeBottom: Boolean = false,
) {
    if (style.isDisc) {
        DiscCoverStack(
            style = style,
            artworkUrl = trackInfo.thumbnailUrl,
            mediaId = trackInfo.mediaId,
            isPlaying = isPlaying,
            spinActive = true,
            editMode = true,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val url = trackInfo.thumbnailUrl ?: return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url.resize(1200, 1200))
            .crossfade(200)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .then(if (style == PlayerCoverStyle.SQUARED) Modifier.clip(RoundedCornerShape(22.dp)) else Modifier)
            .then(if (fadeBottom) Modifier.bottomEdgeFade() else Modifier),
    )
}

// how much of the cover, from its bottom up, fades out without blur: the player's own share
private const val EdgeFadeShare = 0.3f

// the no-blur player's bottom fade, eased so it has no visible start or end; a card's picture holds still, so the layer is drawn once
private fun Modifier.bottomEdgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val steps = 8
        val stops = Array(steps + 1) { i ->
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            t to Color.Black.copy(alpha = 1f - eased)
        }
        val mask = Brush.verticalGradient(*stops, startY = size.height * (1f - EdgeFadeShare), endY = size.height)
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * The flagship player put together from its real parts - its background, the cover in the cover
 * style it's set to, the title row, the seek bar, the transport and the card - for its selector
 * card when another style is the live one. Laid out with the live player's own sums, in its
 * no-blur look, since a card can't blur the live player under it.
 */
@Composable
internal fun FlagshipPreview(
    modifier: Modifier = Modifier,
    /** the Turntable: the flagship around a record player, its arm for the seek bar */
    turntable: Boolean = false,
    // the new flagship: its own background and bar, its buttons, the cover melting into its colours
    flagship: Boolean = false,
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val transport by connection.uiState.transportState.collectAsState()
    val queue by connection.uiState.queueState.collectAsState()
    val flagshipCover by rememberEnumPreference(PlayerCoverStyleKey, DefaultPlayerCoverStyle)
    val coverStyle = if (turntable) PlayerCoverStyle.TURNTABLE else flagshipCover
    val backgroundStyle by rememberEnumPreference(
        if (flagship) FlagshipBackgroundStyleKey else PlayerBackgroundStyleKey,
        if (flagship) PlayerBackgroundStyle.AMBIENT else PlayerBackgroundStyle.COVER_GRADIENT,
    )
    // the Turntable is built on the new flagship, so it shares the new bottom bar, as on the live player
    val newBottomBar = flagship || turntable
    val barStyle by rememberEnumPreference(
        if (newBottomBar) FlagshipBottomBarStyleKey else PlayerBottomBarStyleKey,
        PlayerBottomBarStyle.STACK,
    )
    val (showBottomCard) = rememberPreference(ShowPlayerBottomCardKey, defaultValue = true)
    val transportLayout by rememberEnumPreference(FlagshipTransportLayoutKey, FlagshipTransportLayout.THREE)
    val ambient = rememberAmbientColors()
    if (flagship) FollowPicture(ambient, track.thumbnailUrl)
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val edgeToEdge = coverStyle == PlayerCoverStyle.EDGE_TO_EDGE
    val showVolumeBar = barStyle == PlayerBottomBarStyle.VOLUME
    val showDeck = showBottomCard && barStyle == PlayerBottomBarStyle.STACK
    BoxWithConstraints(modifier = modifier.fillMaxSize().inert()) {
        if (flagship && backgroundStyle == PlayerBackgroundStyle.AMBIENT) {
            // held still on the card, the live player's own clock isn't running here
            AmbientBackdrop(colors = ambient, time = { 0f }, modifier = Modifier.fillMaxSize())
        } else {
            PlayerBackgroundPreview(
                style = backgroundStyle,
                thumbnailUrl = track.thumbnailUrl,
                width = maxWidth,
                height = maxHeight,
                animate = false,
                warp = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val box = coverArtBox(coverStyle, maxWidth, maxHeight, statusTop)
        Box(
            modifier = Modifier
                .offset(box.x, box.y)
                .size(box.width, box.height),
        ) {
            CoverArt(coverStyle, track, transport.isPlaying, queue.currentIndex, fadeBottom = edgeToEdge)
        }
        if (flagship && edgeToEdge) {
            // the design's dark shade under the controls, as the live flagship lays it over its frost
            Spacer(
                modifier = Modifier
                    .offset(y = maxHeight * 0.538f)
                    .fillMaxWidth()
                    .height(maxHeight * 0.462f)
                    .drawWithCache {
                        val shade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.46f to Color.Black.copy(alpha = 0.24f),
                            1f to Color.Black.copy(alpha = 0.24f),
                        )
                        onDrawBehind { drawRect(shade) }
                    },
            )
        } else if (!flagship && !turntable) {
            // the classic's band of the cover's colour behind the title, without the blur it has live
            SeamBlur(
                glassState = remember { GlassState() },
                progressProvider = { 1f },
                trackInfo = track,
                maxHeight = maxHeight,
                noBlur = true,
            )
        }

        // what sits under the controls, by the live player's own sums, so the buttons land where they do there
        val belowControls = when {
            showVolumeBar && newBottomBar -> flagshipBarFootprint()
            showVolumeBar -> playerVolumeBarFootprint()
            showDeck -> PlayerDeckHeight
            else -> navBottom + 8.dp
        }
        if (turntable) {
            // the Turntable's own pills and title row, standing above the card as in its frame
            TurntableControls(
                isExpandMenuOpen = false,
                onToggleExpandMenu = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = belowControls + TurntableControlsLift),
            )
        } else {
            val drawShift = if (edgeToEdge) ControlsDrawShift else 0.dp
            val transportGap = (maxHeight * 0.19f - 37.dp - belowControls + TransportGapAboveRow + drawShift) / 2f
            val transportSpacer = (transportGap - SeekBarLift - drawShift).coerceAtLeast(0.dp)
            val buttonsLift = (transportSpacer + SeekBarLift + drawShift - transportGap.coerceAtLeast(0.dp)) / 2f
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = belowControls + transportGap.coerceAtLeast(0.dp)),
            ) {
                Column(modifier = Modifier.offset(y = -drawShift)) {
                    Spacer(Modifier.height(20.dp))
                    SongInfoRow()
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.offset(y = -SeekBarLift)) { PlayerProgressSlider() }
                }
                Spacer(Modifier.height(transportSpacer))
                Box(modifier = Modifier.offset(y = -buttonsLift)) {
                    if (flagship) FlagshipTransportRow(layout = transportLayout) else PlayerTransportRow()
                }
            }
        }

        when {
            showVolumeBar && newBottomBar -> FlagshipBottomBar(
                isActive = { false },
                onAction = {},
                preview = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = flagshipBarFootprint() - FlagshipBarHeight)
                    .padding(horizontal = 26.dp),
            )
            showVolumeBar -> PlayerVolumeBar(
                isActive = { false },
                onAction = {},
                preview = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = playerVolumeBarFootprint() - PlayerVolumeBarHeight)
                    .padding(horizontal = 26.dp),
            )
            // the deck as it rests on the live player: the front card running off the bottom, the one behind peeking over it
            showDeck -> PlayerDeckPreview(
                look = if (newBottomBar) FlagshipCardLook else ClassicCardLook,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 26.dp),
            )
        }
    }
}
