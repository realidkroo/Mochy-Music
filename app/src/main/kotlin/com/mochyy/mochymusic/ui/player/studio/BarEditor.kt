package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** how far the bar's camera closes in on the player behind it */
private const val BarCameraZoom = 1.5f

/** where the bar lands, as a share of the screen's height */
private const val BarLandingY = 0.56f

/** the highest the transport goes, as a share of the screen's height */
private const val TransportLandingY = 0.345f

/** half the height of the "Card Style" title and the style's name under it */
private val LabelHalf = 16.dp

/**
 * Where the bottom bar's camera goes and where its pieces land, as the concept has it: the bar
 * comes up to just below the middle of the screen with its style's name over it and the transport
 * above that. The bar's other styles wait in a row beside it, a swipe away, the next one peeking in
 * at the edge.
 */
private class BarStage(
    w: Float,
    h: Float,
    density: Density,
    /** where each style's bar sits on the player, in style order */
    val bars: List<Rect>,
    applied: Int,
    val transport: Rect,
) {
    val camera: StudioCamera
    val barTo: Offset
    val transportTo: Offset
    val labelY: Float
    val stride: Float
    /** the band the styles sit in, for taps */
    val rowTop: Float
    val rowBottom: Float

    init {
        with(density) {
            val own = bars[applied]
            val tallest = bars.maxOf { it.height }
            barTo = Offset(w / 2f, h * BarLandingY)
            labelY = barTo.y - tallest / 2f - 14.dp.toPx() - LabelHalf.toPx()
            val labelTop = labelY - LabelHalf.toPx()
            transportTo = Offset(
                transport.center.x,
                minOf(h * TransportLandingY, labelTop - 20.dp.toPx() - transport.height / 2f),
            )
            stride = bars.maxOf { it.width } + CardGap.toPx()
            rowTop = barTo.y - tallest / 2f - TilePadding.toPx()
            rowBottom = barTo.y + tallest / 2f + TilePadding.toPx()
            camera = StudioCamera(BarCameraZoom, own.center, barTo)
        }
    }
}

/**
 * The bottom bar's page. Like the middle part, the player turns into a still and the camera
 * closes in on it, softening as it lands; the bar and the transport are the player's own, lifted
 * off it, riding the camera up to the middle of the screen. The bar's other style comes in beside
 * it. Swiping between them puts the one that comes to rest on the player.
 */
@Composable
internal fun BarEditor(
    parts: StudioParts,
    capture: StudioCapture,
    barStyles: List<PlayerBottomBarStyle>,
    onBarStyle: (PlayerBottomBarStyle) -> Unit,
    w: Float,
    h: Float,
) {
    val studio = parts.studio
    val pieces = parts.pieces ?: return
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val anchors = parts.anchors
    val applied = studio.barCommitted.coerceIn(0, barStyles.lastIndex)
    val bars = barStyles.map { anchors.bar(it).value }
    val transportRect = anchors.transport.value
    if (bars[applied].width <= 0f) return
    val stage = remember(w, h, bars, applied, transportRect) {
        BarStage(w, h, density, bars, applied, transportRect)
    }
    val currentStage by rememberUpdatedState(stage)
    val currentOnBarStyle by rememberUpdatedState(onBarStyle)
    val active = studio.stage == StudioStage.BAR && studio.sheet == null
    val last = barStyles.lastIndex

    // the camera, then the pieces flowing on to their places a beat behind it, then the outlines
    fun cz() = smoother(studio.bar.value)
    fun spread() = smoother((studio.bar.value - 0.1f) / 0.9f)
    fun tiles() = smoother((studio.bar.value - 0.45f) / 0.55f)
    // the pieces swell a little with the camera and settle back to their own size as they land
    fun pieceScale() = 1f + (BarCameraZoom - 1f) * cz() * (1f - spread())

    // a style picked here moves the controls above the bar, and the still is taken again: the new
    // soft picture fades in over the old one rather than jumping
    val soft = capture.soft
    val shownSoft = remember { mutableStateOf(soft) }
    val leavingSoft = remember { mutableStateOf<ImageBitmap?>(null) }
    val softSwap = remember { Animatable(1f) }
    LaunchedEffect(soft) {
        val before = shownSoft.value
        shownSoft.value = soft
        if (before == null || soft == null || before === soft) return@LaunchedEffect
        leavingSoft.value = before
        softSwap.snapTo(0f)
        softSwap.animateTo(1f, tween(360))
        leavingSoft.value = null
    }

    // once a swipe comes to rest, the style there goes on the player
    fun settle(target: Int, velocity: Float = 0f) {
        studio.barPage.animateTo(target.toFloat(), velocity = velocity) {
            if (target != studio.barCommitted) barStyles.getOrNull(target)?.let(currentOnBarStyle)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!active) Modifier else Modifier.pointerInput(w, last) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        val stride = currentStage.stride
                        val startPage = studio.barPage.value
                        var dx = 0f
                        var dy = 0f
                        var mode = 0 // 0 undecided, 1 sideways, 2 up or down (nothing to do)
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            val delta = change.positionChangeIgnoreConsumed()
                            dx += delta.x
                            dy += delta.y
                            tracker.addPointerInputChange(change)
                            if (mode == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                mode = if (abs(dx) >= abs(dy)) 1 else 2
                            }
                            if (mode != 0) change.consume()
                            if (mode == 1) {
                                // past either end it gives a little and comes back
                                val raw = startPage - dx / stride
                                studio.barPage.value = when {
                                    raw < 0f -> raw * 0.3f
                                    raw > last -> last + (raw - last) * 0.3f
                                    else -> raw
                                }
                            }
                        }
                        when (mode) {
                            // a tap on a style beside the one in view brings it over
                            0 -> if (released) {
                                val y = down.position.y
                                if (y in currentStage.rowTop..currentStage.rowBottom) {
                                    val tapped = (studio.barPage.value + (down.position.x - w / 2f) / stride).roundToInt()
                                    if (tapped in 0..last) settle(tapped)
                                }
                            }
                            1 -> {
                                val flick = (-tracker.calculateVelocity().x / stride).coerceIn(-3f, 3f)
                                settle((studio.barPage.value + flick * 0.3f).roundToInt().coerceIn(0, last), flick)
                            }
                        }
                    }
                }
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val z = cz()
            val camera = stage.camera
            // the player, as a still, under the camera; it softens as the camera lands
            drawStill(capture.image, camera, z, 1f)
            val softness = smoother((studio.bar.value - 0.25f) / 0.6f)
            val leaving = leavingSoft.value
            if (leaving != null) drawStill(leaving, camera, z, softness)
            drawStill(shownSoft.value, camera, z, softness * (if (leaving != null) softSwap.value else 1f))
            drawRect(Color.Black.copy(alpha = 0.22f * softness))

            // the editor's tiles from before, carried off by the camera as they fade
            withTransform({
                val origin = camera.stillOrigin(z, size.width, size.height)
                translate(origin.x, origin.y)
                scale(camera.zoomAt(z), camera.zoomAt(z), pivot = Offset.Zero)
            }) { drawTiles(parts) }

            val s = pieceScale()
            val drift = spread()
            val page = studio.barPage.value
            val base = camera.pieceCenter(stage.bars[applied], stage.barTo, z, drift)
            val pad = TilePadding.toPx()
            stage.bars.forEachIndexed { i, rect ->
                if (rect.width <= 0f) return@forEachIndexed
                val center = base + Offset((i - page) * stage.stride, 0f)
                val half = rect.width * s / 2f + pad
                if (center.x + half < 0f || center.x - half > size.width) return@forEachIndexed
                // the styles to the side wear an outline, so the one peeking in reads as a choice
                val outline = tiles() * (abs(i - page) * 1.6f).coerceIn(0f, 1f)
                drawTileOutline(
                    Rect(center.x - half, center.y - rect.height * s / 2f - pad, center.x + half, center.y + rect.height * s / 2f + pad),
                    outline,
                )
                val alpha = if (i == applied) 1f else drift
                drawPiece(pieces.layers[StudioPieces.bar(barStyles[i])], rect, center, s, alpha)
            }
            drawPiece(
                pieces.layers[StudioPieces.TRANSPORT],
                stage.transport,
                camera.pieceCenter(stage.transport, stage.transportTo, z, drift),
                s,
                1f,
            )
        }

        // "Card Style", and the style in view under it
        val focused by remember(studio, last) {
            derivedStateOf { studio.barPage.value.roundToInt().coerceIn(0, last) }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.placeWithLayer((constraints.maxWidth - p.width) / 2, (stage.labelY - p.height / 2f).roundToInt()) {
                        val t = tiles()
                        alpha = t
                        translationY = (1f - t) * 16.dp.toPx()
                    }
                }
            },
        ) {
            Text(stringResource(R.string.ps_studio_card_style), color = Color.White, style = studioText(StudioType.Title, FontWeight.Bold))
            AnimatedContent(
                targetState = barStyles.getOrNull(focused)?.displayName.orEmpty(),
                transitionSpec = {
                    (slideInVertically(tween(320)) { it } + fadeIn(tween(240))) togetherWith
                        (slideOutVertically(tween(280)) { -it } + fadeOut(tween(180)))
                },
                label = "barStyleName",
            ) { name ->
                Text(name, color = Color.White.copy(alpha = 0.6f), style = studioText(StudioType.Subtitle, FontWeight.Medium))
            }
        }
    }
}
