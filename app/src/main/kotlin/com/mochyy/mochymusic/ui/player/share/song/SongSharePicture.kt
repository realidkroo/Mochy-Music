// SongSharePicture.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.player.share.CinematicFlow
import com.mochyy.mochymusic.ui.player.share.CinematicLayout
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.MusicCloudsRestPhase
import com.mochyy.mochymusic.ui.player.share.ShareBackground
import com.mochyy.mochymusic.ui.player.share.ShareHeight
import com.mochyy.mochymusic.ui.player.share.ShareRegions
import com.mochyy.mochymusic.ui.player.share.ShareStyle
import com.mochyy.mochymusic.ui.player.share.ShareWidth
import com.mochyy.mochymusic.ui.player.share.cinematicTimings
import com.mochyy.mochymusic.ui.player.share.drawMusicClouds
import kotlin.math.PI
import kotlin.math.ceil
import com.mochyy.mochymusic.ui.component.RemoteFocus

// the card's blur at full strength, in the picture's own units
internal val MaxCardBlur = 26.dp

// one lap of the colour clouds, the same slow drift the lyrics share has
private const val CloudLapMs = 22_000f

// which parts of the picture to draw: an export draws some of them itself, a story sticker is the card alone
@Immutable
internal data class ShareLayers(val background: Boolean, val glass: Boolean, val card: Boolean) {
    companion object {
        val Everything = ShareLayers(background = true, glass = true, card = true)

        // the card for a video export, which lays the video and the card's blur underneath itself
        val CardOverVideo = ShareLayers(background = false, glass = false, card = true)

        val BackgroundOnly = ShareLayers(background = true, glass = false, card = false)

        val Sticker = ShareLayers(background = false, glass = false, card = true)
    }
}

// the share picture at its full 360 x 640, in whatever density it's given; preview and export both draw this
@Composable
internal fun SongSharePicture(
    session: SongShareSession,
    positionMs: () -> Long,
    layers: ShareLayers,
    moving: Boolean,
    modifier: Modifier = Modifier,
    kind: SongShareKind = session.kind,
    // the style tiles show both lyrics styles at once, whatever the share has picked
    style: ShareStyle? = session.pick?.style,
    // the editor and the exports say where the card is; the small tiles don't
    reportsCard: Boolean = false,
    onCardTap: (() -> Unit)? = null,
    // the cinematic editor outlines the song and the words; these are where they sit
    regions: ShareRegions? = null,
    // the preview's live video, drawn as the background when the background is a video
    video: (@Composable () -> Unit)? = null,
) {
    val backdrop = rememberGraphicsLayer()
    Box(modifier.size(ShareWidth, ShareHeight).clipToBounds()) {
        if (layers.background) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
            ) {
                SongShareBackdrop(session, positionMs, moving, video)
            }
        }
        val pick = session.pick
        if (layers.card && kind == SongShareKind.Lyrics && style == ShareStyle.Cinematic && pick != null) {
            CinematicWords(session, pick, positionMs, moving, regions)
        } else if (layers.card) {
            CardSlot(
                session = session,
                kind = kind,
                positionMs = positionMs,
                moving = moving,
                backdrop = if (layers.glass && layers.background) backdrop else null,
                reportsCard = reportsCard,
                onCardTap = onCardTap,
            )
        }
    }
}

// the cinematic words straight on the picture; in a video each word lights up as it's sung
@Composable
private fun CinematicWords(
    session: SongShareSession,
    pick: LyricsShareSession,
    positionMs: () -> Long,
    moving: Boolean,
    regions: ShareRegions?,
) {
    val flow = if (moving) {
        val timings = remember(pick.chosen) { cinematicTimings(pick) }
        remember(timings, positionMs, session) { CinematicFlow({ positionMs() + session.lyricsOffsetMs }, timings) }
    } else {
        null
    }
    CinematicLayout(pick, regions, ink = session.wordsInk, cover = session.art.cover, flow = flow)
}

@Composable
private fun BoxScope.CardSlot(
    session: SongShareSession,
    kind: SongShareKind,
    positionMs: () -> Long,
    moving: Boolean,
    backdrop: GraphicsLayer?,
    reportsCard: Boolean,
    onCardTap: (() -> Unit)?,
) {
    // read only while drawing the glass, so finding its place never recomposes the card
    var origin by remember { mutableStateOf(Offset.Zero) }
    val maxBlurPx = with(LocalDensity.current) { MaxCardBlur.toPx() }
    val shape = RoundedCornerShape(cardCornerOf(kind))
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .onPlaced { coords ->
                origin = coords.positionInParent()
                if (reportsCard) {
                    val parent = coords.parentLayoutCoordinates?.size ?: return@onPlaced
                    if (parent.width > 0 && parent.height > 0) {
                        session.cardBounds = Rect(
                            origin.x / parent.width,
                            origin.y / parent.height,
                            (origin.x + coords.size.width) / parent.width,
                            (origin.y + coords.size.height) / parent.height,
                        )
                    }
                }
            }
            .clip(shape)
            .then(
                if (onCardTap != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onCardTap)
                } else {
                    Modifier
                },
            ),
    ) {
        if (backdrop != null) {
            CardGlass(backdrop, origin = { origin }, radiusPx = { session.cardBlur * maxBlurPx }, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.matchParentSize().background(session.cardTint.copy(alpha = session.cardOpacity)))
        when (kind) {
            SongShareKind.MusicOnly -> MusicOnlyCard(session, positionMs, moving)
            SongShareKind.Lyrics -> LyricsCard(session, positionMs, moving)
        }
    }
}

// the background seen through the card, blurred; it reaches past the card by the blur's whole spread, so the edge is blurred from what's really there and no faded rim shows round the card
@Composable
private fun CardGlass(backdrop: GraphicsLayer, origin: () -> Offset, radiusPx: () -> Float, modifier: Modifier) {
    val margin = { ceil(radiusPx() * 2f).toInt() + 2 }
    DownscaledBlur(
        radius = radiusPx,
        tileMode = null,
        mask = null,
        modifier = modifier.bleedBy(margin),
    ) {
        val m = margin().toFloat()
        val o = origin()
        translate(m - o.x, m - o.y) { drawLayer(backdrop) }
    }
}

// soft clouds and a blurred cover look the same drawn at a quarter of the size and stretched, for a sixteenth of the pixels every frame
private fun Modifier.quarterSize(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((width + 3) / 4, (height + 3) / 4))
    layout(width, height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = 4f
            scaleY = 4f
            transformOrigin = TransformOrigin(0f, 0f)
            // drawn into its own small buffer, then stretched; without it the gpu would draw it full size anyway
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

private fun Modifier.bleedBy(margin: () -> Int): Modifier = layout { measurable, constraints ->
    val m = margin().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + m * 2, constraints.maxHeight + m * 2))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-m, -m) }
}

@Composable
private fun SongShareBackdrop(
    session: SongShareSession,
    positionMs: () -> Long,
    moving: Boolean,
    video: (@Composable () -> Unit)?,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = session.bgZoom
                    scaleY = session.bgZoom
                    translationX = session.bgOffsetX * size.width
                    translationY = session.bgOffsetY * size.height
                },
        ) {
            when (val background = session.background) {
                is SongBackground.Still -> StillBackdrop(session, background.background, positionMs, moving)
                is SongBackground.Video -> if (video != null) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = background.dim }) { video() }
                }
            }
        }
    }
}

@Composable
private fun StillBackdrop(session: SongShareSession, background: ShareBackground, positionMs: () -> Long, moving: Boolean) {
    val art = session.art
    when (background) {
        is ShareBackground.Solid -> Spacer(Modifier.fillMaxSize().background(background.color))
        is ShareBackground.Picture -> {
            val picture = if (background.blur) art.pictureBlur else art.picture
            if (picture != null && art.pictureUri == background.uri) {
                Image(
                    picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = background.opacity },
                )
            }
        }
        ShareBackground.FromMusic -> Box(Modifier.fillMaxSize().quarterSize().background(session.palette.firstOrNull() ?: Color(0xFF2A2A2E))) {
            art.coverBlur?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.75f })
            }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val phase = if (moving) {
                            MusicCloudsRestPhase + (2 * PI).toFloat() * (positionMs() - session.windowStartMs) / CloudLapMs
                        } else {
                            MusicCloudsRestPhase
                        }
                        drawMusicClouds(session.palette, phase)
                    },
            )
        }
    }
}
