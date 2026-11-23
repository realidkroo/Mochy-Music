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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.player.share.CinematicLayout
import com.mochyy.mochymusic.ui.player.share.LiquidCoverBackdrop
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.MusicCloudsRestPhase
import com.mochyy.mochymusic.ui.player.share.ShareBackground
import com.mochyy.mochymusic.ui.player.share.ShareHeight
import com.mochyy.mochymusic.ui.player.share.ShareRegions
import com.mochyy.mochymusic.ui.player.share.ShareStyle
import com.mochyy.mochymusic.ui.player.share.ShareWidth
import com.mochyy.mochymusic.ui.player.share.drawMusicClouds
import kotlin.math.PI
import kotlin.math.ceil
import com.mochyy.mochymusic.ui.component.RemoteFocus

// subtle dark scrim over blurred/animated card-only covers so white lyrics always pop crisply
private val CardCoverScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.38f)))

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
    val pick = session.pick
    val isCinematic = kind == SongShareKind.Lyrics && style == ShareStyle.Cinematic && pick != null
    // Only record the backdrop into an offscreen layer when a frosted card will actually blur it
    // (Cinematic has no card, TextureView videos aren't captured by Compose layers, and opaque/blur=0 cards don't show it;
    // LiquidCover IS drawn by Compose so it gets real frosted glass!)
    val hasTextureVideo = (session.background as? SongBackground.Video)?.source?.isTextureVideo == true
    val needsGlass = layers.glass && layers.background && layers.card && !isCinematic &&
        !hasTextureVideo &&
        session.cardBlur > 0.005f && session.cardOpacity < 0.995f
    val backdrop = if (needsGlass) rememberGraphicsLayer() else null
    Box(modifier.size(ShareWidth, ShareHeight).clipToBounds()) {
        if (layers.background) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (backdrop != null) {
                            Modifier.drawWithContent {
                                backdrop.record { this@drawWithContent.drawContent() }
                                drawLayer(backdrop)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                SongShareBackdrop(session, positionMs, moving, video)
            }
        }
        if (layers.card && isCinematic) {
            CinematicWords(session, pick, positionMs, moving, regions)
        } else if (layers.card) {
            CardSlot(
                session = session,
                kind = kind,
                positionMs = positionMs,
                moving = moving,
                backdrop = backdrop,
                reportsCard = reportsCard,
                onCardTap = onCardTap,
            )
        }
    }
}

// just the card itself, with the outer 9:16 story background removed: solid colour, blurred cover, or moving cover
@Composable
internal fun SongShareCardOnly(
    session: SongShareSession,
    positionMs: () -> Long,
    layers: ShareLayers,
    moving: Boolean,
    modifier: Modifier = Modifier,
    kind: SongShareKind = session.kind,
    corner: Dp = cardCornerOf(kind),
    onCardTap: (() -> Unit)? = null,
    video: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (corner > 0.dp) Modifier.clip(shape) else Modifier.clipToBounds())
            .then(
                if (onCardTap != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onCardTap)
                } else {
                    Modifier
                },
            ),
    ) {
        if (layers.background) {
            when (val bg = session.cardOnlyBg) {
                CardOnlyBackground.Colour -> {
                    Spacer(Modifier.matchParentSize().background(session.cardTint))
                }
                CardOnlyBackground.BlurredCover -> {
                    Box(Modifier.matchParentSize().background(Color.Black)) {
                        val coverBlur = session.art.coverBlur
                        if (coverBlur != null) {
                            Image(
                                coverBlur,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize().graphicsLayer { alpha = session.cardOnlyDim },
                            )
                        } else {
                            Spacer(Modifier.matchParentSize().background(session.cardTint))
                        }
                    }
                }
                is CardOnlyBackground.Video -> {
                    Box(Modifier.matchParentSize().background(Color.Black)) {
                        if (bg.source == ShareVideoSource.LiquidCover) {
                            LiquidCoverBackdrop(
                                coverBlur = session.art.coverBlur,
                                palette = session.palette,
                                dim = session.cardOnlyDim,
                                timeSec = {
                                    if (moving) (positionMs() - session.windowStartMs) / 1000f else 1.5f
                                },
                                modifier = Modifier.matchParentSize(),
                            )
                        } else if (video != null) {
                            Box(Modifier.matchParentSize().graphicsLayer { alpha = session.cardOnlyDim }) { video() }
                        } else {
                            session.art.coverBlur?.let { coverBlur ->
                                Image(
                                    coverBlur,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize().graphicsLayer { alpha = session.cardOnlyDim },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (layers.card) {
            if (session.cardOnlyBg != CardOnlyBackground.Colour) {
                Spacer(Modifier.matchParentSize().background(CardCoverScrim))
            }
            when (kind) {
                SongShareKind.MusicOnly -> MusicOnlyCard(session, positionMs, moving)
                SongShareKind.Lyrics -> LyricsCard(session, positionMs, moving)
            }
        }
    }
}

// the cinematic words straight on the picture; in a video each word animates with the regular lyrics view's karaoke engine
@Composable
private fun CinematicWords(
    session: SongShareSession,
    pick: LyricsShareSession,
    positionMs: () -> Long,
    moving: Boolean,
    regions: ShareRegions?,
) {
    val lyricsPosition = remember(session, positionMs) { { positionMs() + session.lyricsOffsetMs } }
    val readings = remember(pick.chosen, session.readingChoice, session.lineReadings, session.lineTranslations) {
        pick.chosen.map { (line, _) -> pick.entries.getOrNull(line)?.let(session::cinematicUnder).orEmpty() }
    }
    val look = session.lyricsLook
    CinematicLayout(
        session = pick,
        regions = regions,
        ink = session.wordsInk,
        cover = session.art.cover,
        positionMs = if (moving && session.lyricsSynced) lyricsPosition else null,
        motionStyle = look.motionStyle,
        highBloom = look.highBloom,
        readings = readings,
    )
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
                is SongBackground.Video -> if (background.source == ShareVideoSource.LiquidCover) {
                    LiquidCoverBackdrop(
                        coverBlur = session.art.coverBlur,
                        palette = session.palette,
                        dim = background.dim,
                        timeSec = { if (moving) (positionMs() - session.windowStartMs) / 1000f else 1.5f },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (video != null) {
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
