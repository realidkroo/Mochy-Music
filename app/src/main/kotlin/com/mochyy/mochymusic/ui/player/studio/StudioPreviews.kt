package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.mochyy.mochymusic.ui.player.PeekInset
import com.mochyy.mochymusic.ui.player.PeekOffset
import com.mochyy.mochymusic.ui.player.SongInfoRow
import com.mochyy.mochymusic.ui.player.customize.DiscCoverStack
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview
import com.mochyy.mochymusic.ui.player.customize.coverArtBox
import com.mochyy.mochymusic.ui.player.customize.isDisc
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.ui.utils.stableStatusBars

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
            .then(if (style == PlayerCoverStyle.SQUARED) Modifier.clip(RoundedCornerShape(22.dp)) else Modifier),
    )
}

/**
 * The flagship player put together from its real parts - its background, the cover in the cover
 * style it's set to, the title row, the seek bar, the transport and the card - for its selector
 * card when another style is the live one.
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
    val transportLayout by rememberEnumPreference(FlagshipTransportLayoutKey, FlagshipTransportLayout.THREE)
    val ambient = rememberAmbientColors()
    if (flagship) FollowPicture(ambient, track.thumbnailUrl)
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
            CoverArt(coverStyle, track, transport.isPlaying, queue.currentIndex)
        }
        if (flagship && coverStyle == PlayerCoverStyle.EDGE_TO_EDGE) {
            // a card can't blur the live player under it, so it gets only the design's dark shade under the controls, no colour
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
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = navBottom + 108.dp),
        ) {
            SongInfoRow()
            Spacer(Modifier.height(10.dp))
            // the Turntable's arm is its seek bar: the bar's room is kept, empty
            if (turntable) Box(Modifier.graphicsLayer { alpha = 0f }) { PlayerProgressSlider(active = false) } else PlayerProgressSlider()
            Spacer(Modifier.height(22.dp))
            if (flagship) FlagshipTransportRow(layout = transportLayout) else PlayerTransportRow()
        }
        if (newBottomBar && barStyle == PlayerBottomBarStyle.VOLUME) {
            FlagshipBottomBar(
                isActive = { false },
                onAction = {},
                preview = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 26.dp, end = 26.dp, bottom = navBottom + 14.dp),
            )
        } else if (barStyle == PlayerBottomBarStyle.VOLUME) {
            PlayerVolumeBar(
                isActive = { false },
                onAction = {},
                preview = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 26.dp, end = 26.dp, bottom = navBottom + 14.dp),
            )
        } else if (newBottomBar) {
            // the new card: faint, outlined, running off the bottom, with the card behind peeking over it like the live deck
            val look = FlagshipCardLook
            val shape = RoundedCornerShape(topStart = look.corner, topEnd = look.corner)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 26.dp + PeekInset, end = 26.dp + PeekInset)
                    .fillMaxWidth()
                    .height(navBottom + 60.dp + PeekOffset)
                    .clip(shape)
                    .background(Color.White.copy(alpha = look.backFill))
                    .border(look.borderWidth, Color.White.copy(alpha = look.backBorder), shape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 26.dp, end = 26.dp)
                    .fillMaxWidth()
                    .height(navBottom + 60.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = look.frontFill))
                    .border(look.borderWidth, Color.White.copy(alpha = look.frontBorder), shape),
            )
        } else {
            // the bottom card, as its glass plate
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 26.dp, end = 26.dp, bottom = navBottom + 14.dp)
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
            )
        }
    }
}
