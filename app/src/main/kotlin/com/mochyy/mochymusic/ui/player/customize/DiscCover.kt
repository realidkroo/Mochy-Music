// DiscCover.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.min

@Immutable
data class DiscGeometry(

    val diameter: Float,

    val centerX: Float,
    val centerY: Float,

    val art: Float,

    val plateOffsetX: Float,
    val plateOffsetY: Float,

    val plateWidth: Float,
    val plateHeight: Float,

    val plateRotation: Float,

    val albumCard: AlbumCard? = null,

    /**
     * the centre's height as a fraction of the box's width instead of [centerY]'s height. Such a
     * record is laid out entirely in the box's width - its size too - and exactly where it says,
     * even where it runs past the box's edges (the Turntable's arm is placed the same way).
     */
    val centerYByWidth: Float? = null,
) {
    val byWidth: Boolean get() = centerYByWidth != null
    @Immutable
    data class AlbumCard(val centerX: Float, val centerY: Float, val side: Float)
}

fun discGeometryFor(style: PlayerCoverStyle): DiscGeometry = when (style) {

    PlayerCoverStyle.DISC_SMALL_FULL -> DiscGeometry(
        diameter = 1f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.88f,
        plateOffsetX = -0.30f,
        plateOffsetY = -0.30f,
        plateWidth = 0.26f,
        plateHeight = 0.10f,
        plateRotation = -38f,
    )
    PlayerCoverStyle.DISC_SMALL_LABEL -> DiscGeometry(
        diameter = 1f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.44f,
        plateOffsetX = -0.29f,
        plateOffsetY = -0.22f,
        plateWidth = 0.28f,
        plateHeight = 0.11f,
        plateRotation = -60f,
    )
    PlayerCoverStyle.DISC_BIG_FULL -> DiscGeometry(
        diameter = 1.55f,
        centerX = 0.24f,
        centerY = 0.20f,
        art = 0.95f,
        plateOffsetX = 0.02f,
        plateOffsetY = 0.30f,
        plateWidth = 0.22f,
        plateHeight = 0.085f,
        plateRotation = 0f,
    )
    PlayerCoverStyle.DISC_BIG_LABEL -> DiscGeometry(
        diameter = 1.60f,
        centerX = 0.22f,
        centerY = 0.18f,
        art = 0.42f,
        plateOffsetX = 0.06f,
        plateOffsetY = 0.28f,
        plateWidth = 0.24f,
        plateHeight = 0.09f,
        plateRotation = -8f,
    )

    // the disc and the album both inside the box: the disc used to start left of it, and the
    // parent clipped its left edge flat
    PlayerCoverStyle.DISC_ALBUM -> DiscGeometry(
        diameter = 0.82f,
        centerX = 0.43f,
        centerY = 0.5f,
        art = 0.40f,
        plateOffsetX = -0.28f,
        plateOffsetY = -0.22f,
        plateWidth = 0.28f,
        plateHeight = 0.11f,
        plateRotation = -60f,
        albumCard = DiscGeometry.AlbumCard(centerX = 0.665f, centerY = 0.5f, side = 0.64f),
    )

    // the record on its player: big, running off the left edge, its top up at the screen's, the
    // arm reaching in from the top right corner (a size up and a step up-left from the frame, as
    // the user drew it)
    PlayerCoverStyle.TURNTABLE -> DiscGeometry(
        diameter = 1.406f,
        centerX = 0.162f,
        centerY = 0.5f,
        art = 0.526f,
        plateOffsetX = 0f,
        plateOffsetY = 0f,
        plateWidth = 0f,
        plateHeight = 0f,
        plateRotation = 0f,
        centerYByWidth = 0.703f,
    )

    PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> DiscGeometry(
        diameter = 0.86f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.88f,
        plateOffsetX = 0f,
        plateOffsetY = 0f,
        plateWidth = 0.26f,
        plateHeight = 0.10f,
        plateRotation = 0f,
    )
}

private val PlatterBlack = Color(0xFF0B0B0D)
private val PlatterEdge = Color(0xFF2A2830)
/** under the label's picture until it loads */
private val LabelFallback = Color(0xFF27303B)

@Composable
fun DiscCover(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    discName: String,
    /** the album's cover for the album card; the song's own artwork when there's none */
    albumArtworkUrl: String? = artworkUrl,
    realistic: Boolean,
    editMode: Boolean,
    rotationProvider: () -> Float,
    modifier: Modifier = Modifier,
    /**
     * keeps the record's surface in a layer of its own, drawn once. Every record in the row keeps
     * one: drawn fresh each frame of a slide, the surface's gradients cost a weak GPU a frame.
     */
    cacheSurface: Boolean = true,
    /** shared by a row of records, so the letters of their names are laid out once between them */
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
    /**
     * how much colour the pictures keep (1 all, lower greyer while paused). Only the pictures take
     * it: greyed as a whole, the record's layer went offscreen, and a layer drawn offscreen keeps
     * nothing outside its box - the big records and their shadows were cut flat at the bottom.
     */
    saturationProvider: () -> Float = { 1f },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val geometry = remember(style) { discGeometryFor(style) }

    BoxWithConstraints(modifier = modifier) {
        val boxSide = if (geometry.byWidth) maxWidth.value else min(maxWidth.value, maxHeight.value)
        val discSide = boxSide * geometry.diameter
        val discSidePx = with(density) { discSide.dp.toPx() }
        val radiusPx = discSidePx / 2f

        val discLeft = maxWidth.value * geometry.centerX - discSide / 2f
        val discTop = (geometry.centerYByWidth?.let { maxWidth.value * it } ?: (maxHeight.value * geometry.centerY)) - discSide / 2f
        val discLeftPx = with(density) { discLeft.dp.toPx() }
        val discTopPx = with(density) { discTop.dp.toPx() }

        val platterModifier = Modifier
            // a record bigger than the box would otherwise be centred on it, off where it says
            .then(if (geometry.byWidth) Modifier.wrapContentSize(Alignment.TopStart, unbounded = true) else Modifier)
            .requiredSize(discSide.dp)
            .offset { IntOffset(discLeftPx.toInt(), discTopPx.toInt()) }

        // A soft shadow under the record lifts it off the deck.
        if (realistic) {
            Canvas(modifier = platterModifier) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.86f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        center = center + Offset(0f, r * 0.035f),
                        radius = r * 1.09f,
                    ),
                    radius = r * 1.09f,
                    center = center + Offset(0f, r * 0.035f),
                )
            }
        }

        // The record, still: its grooves are circles and its light doesn't move, so only the
        // label (and what's printed round it) turns. It's drawn at the screen's resolution into a
        // layer of its own, once; the label turning over it never redraws it.
        Canvas(
            modifier = platterModifier.then(
                if (realistic && cacheSurface) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
            )
        ) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            if (realistic) {
                drawVinyl(center, r, geometry.art)
            } else {
                drawCircle(color = PlatterBlack, radius = r, center = center)
                drawCircle(
                    color = PlatterEdge,
                    radius = r - StrokeHairline.toPx() / 2f,
                    center = center,
                    style = Stroke(width = StrokeHairline.toPx()),
                )
            }
        }

        val labelSide = (discSide * geometry.art).dp
        Box(
            modifier = platterModifier
                .graphicsLayer { rotationZ = rotationProvider() }
        ) {
            // the label: the cover itself, round
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(labelSide)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(LabelFallback),
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUrl.resize(1200, 1200))
                            .allowHardware(true)
                            .crossfade(300)
                            .size(CoilSize(1200, 1200))
                            .precision(Precision.INEXACT)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { colorFilter = pausedFilter(saturationProvider()) },
                    )
                }
            }

            DiscNamePlate(
                discName = discName,
                editMode = editMode,
                discSide = discSide,
                geometry = geometry,
                measurer = textMeasurer,
            )
        }

        // over the turning label and still: the paper takes the light softly and its edge sits a hair below the vinyl, with no pin through the middle
        if (realistic) {
            Canvas(modifier = platterModifier) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val labelR = r * geometry.art
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        center = center + Offset(-labelR * 0.35f, -labelR * 0.45f),
                        radius = labelR * 1.1f,
                    ),
                    radius = labelR,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.82f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                        center = center,
                        radius = labelR,
                    ),
                    radius = labelR,
                    center = center,
                )
            }
        }

        val card = geometry.albumCard
        val cardArt = albumArtworkUrl ?: artworkUrl
        if (card != null && cardArt != null) {
            val side = boxSide * card.side
            val cardLeftPx = with(density) { (maxWidth.value * card.centerX - side / 2f).dp.toPx() }
            val cardTopPx = with(density) { (maxHeight.value * card.centerY - side / 2f).dp.toPx() }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cardArt.resize(1200, 1200))
                    .allowHardware(true)
                    .crossfade(300)
                    .size(CoilSize(1200, 1200))
                    .precision(Precision.INEXACT)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .requiredSize(side.dp)
                    .offset { IntOffset(cardLeftPx.toInt(), cardTopPx.toInt()) }
                    .clip(RoundedCornerShape(side.dp * 0.06f))
                    .graphicsLayer { colorFilter = pausedFilter(saturationProvider()) },
            )
        }
    }
}

private val StrokeHairline = 1.dp

/** a picture's grey while paused; none at full colour, so a playing record's layers stay plain */
private fun pausedFilter(saturation: Float): androidx.compose.ui.graphics.ColorFilter? =
    if (saturation < 0.999f) {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }
        )
    } else null

/**
 * The disc's label, printed the way a CD's is: in capitals, spaced out, all the way round the
 * ring between the artwork and the rim, repeated to close the circle. It sits inside the platter's
 * turning layer, so it spins with the disc and is drawn once, not every frame. Nothing is cut off:
 * a long name just takes more of the ring.
 *
 * It's printed into the record rather than laid over it: a faint plain pass, then the letters
 * blended as an overlay with what's under them, so they take the record's light where the sheen
 * runs, go dim where it doesn't, and show its grooves through them.
 */
@Composable
private fun BoxScope.DiscNamePlate(
    discName: String,
    editMode: Boolean,
    discSide: Float,
    geometry: DiscGeometry,
    measurer: androidx.compose.ui.text.TextMeasurer? = null,
) {
    if (discName.isBlank() && !editMode) return
    val ownMeasurer = rememberTextMeasurer(cacheSize = 64)
    val measurer = measurer ?: ownMeasurer
    // the middle of the band between the artwork's edge and the rim, a little toward the rim
    val ringFraction = geometry.art + (1f - geometry.art) * 0.58f
    val band = (1f - geometry.art) / 2f * discSide
    val fontSize = (band * 0.30f).coerceIn(8f, 15f).sp
    val style = TextStyle(
        fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        letterSpacing = 0.22.em,
        color = Color.White,
    )
    Canvas(Modifier.matchParentSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringR = size.minDimension / 2f * ringFraction
        if (discName.isBlank()) {
            // editing, nothing chosen yet: where the name will go
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = ringR,
                center = center,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
            return@Canvas
        }
        val unit = discName.trim().uppercase() + "  ·  "
        val glyphs = unit.map { measurer.measure(it.toString(), style) }
        val unitWidth = glyphs.sumOf { it.size.width.toDouble() }.toFloat().coerceAtLeast(1f)
        val circumference = (2 * Math.PI * ringR).toFloat()
        val repeats = (circumference / unitWidth).toInt().coerceAtLeast(1)
        // spread the repeats evenly so the end meets the start with no gap
        val stretch = circumference / (unitWidth * repeats)
        var travelled = 0f
        repeat(repeats) {
            for (layout in glyphs) {
                val w = layout.size.width * stretch
                val angle = (travelled + w / 2f) / ringR
                rotate(Math.toDegrees(angle.toDouble()).toFloat(), center) {
                    val at = Offset(center.x - layout.size.width / 2f, center.y - ringR - layout.size.height / 2f)
                    drawText(layout, color = PrintBase, topLeft = at)
                    drawText(layout, color = PrintInk, topLeft = at, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
                }
                travelled += w
            }
        }
    }
}

/** the printed name: a faint plain pass so it reads on the darkest vinyl, then the ink, overlaid */
private val PrintBase = Color.White.copy(alpha = 0.18f)
private val PrintInk = Color.White.copy(alpha = 0.7f)

/**
 * Where [style]'s record sits in a box [size] (centre and radius, in pixels), exactly as
 * DiscCover lays it out - including a record wider than the box being centred on it, for the
 * styles laid out by the box's shorter side.
 */
internal fun discCircleIn(style: PlayerCoverStyle, size: Size): Pair<Offset, Float> {
    val g = discGeometryFor(style)
    val unit = if (g.byWidth) size.width else min(size.width, size.height)
    val side = unit * g.diameter
    var left = size.width * g.centerX - side / 2f
    var top = (g.centerYByWidth?.let { size.width * it } ?: (size.height * g.centerY)) - side / 2f
    if (!g.byWidth) {
        if (side > size.width) left -= (side - size.width) / 2f
        if (side > size.height) top -= (side - size.height) / 2f
    }
    return Offset(left + side / 2f, top + side / 2f) to side / 2f
}

/** [p] lands on [style]'s record in a box [size] */
internal fun isOnDisc(style: PlayerCoverStyle, size: Size, p: Offset): Boolean {
    val (center, radius) = discCircleIn(style, size)
    return (p - center).getDistance() <= radius
}

@Composable
fun DiscTonearm(
    liftProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val lift = liftProvider().coerceIn(0f, 1f)

        val pivot = Offset(size.width * 1.02f, size.height * 0.22f)
        val armLength = size.width * 0.62f

        val angle = Math.toRadians((TonearmRestDegrees + (TonearmLiftDegrees - TonearmRestDegrees) * lift).toDouble())
        val head = Offset(
            x = pivot.x + (armLength * kotlin.math.cos(angle)).toFloat(),
            y = pivot.y + (armLength * kotlin.math.sin(angle)).toFloat(),
        )

        drawLine(
            color = ArmMetal,
            start = pivot,
            end = head,
            strokeWidth = size.width * 0.022f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        val headW = size.width * 0.20f
        val headH = size.width * 0.15f
        drawOval(
            color = HeadBlack,
            topLeft = Offset(head.x - headW / 2f, head.y - headH / 2f),
            size = Size(headW, headH),
        )
        drawCircle(color = HeadBlack, radius = size.width * 0.045f, center = pivot)
    }
}

private val ArmMetal = Color(0xFFCFCBD3)
private val HeadBlack = Color(0xFF17161A)
private const val TonearmRestDegrees = 168f
private const val TonearmLiftDegrees = 196f
