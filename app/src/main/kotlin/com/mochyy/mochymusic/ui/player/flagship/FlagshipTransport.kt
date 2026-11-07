package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.FlagshipTransportLayout
import com.mochyy.mochymusic.ui.component.tapOrHold
import com.mochyy.mochymusic.ui.player.HoldFastFactor
import com.mochyy.mochymusic.ui.player.HoldSlowFactor
import com.mochyy.mochymusic.ui.player.HoldSpeed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.component.RemoteFocus

// the design's phone is this many of its units across; one unit of every shape is the screen's width over this
private const val DesignWidth = 1172f

// where each button's centre sits across the row, as in the design
private val ThreeCentres = floatArrayOf(0.215f, 0.5f, 0.785f)
private val TenCentres = floatArrayOf(0.1485f, 0.2981f, 0.5f, 0.7019f, 0.8507f)

// the ten second row's white circle and its round arrows, in design units
private const val TenCircle = 187f
private const val TenIconWidth = 100f
private const val TenIconHeight = 99f

// how far a ten second skip goes
private const val TenSecondsMs = 10_000L

private val ButtonTouch = 76.dp
private val TenSkipTouch = 58.dp

// the play buttons and both skips, as the new flagship lays them out; reads the player itself
@Composable
fun FlagshipTransportRow(
    layout: FlagshipTransportLayout,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val transportState by playerConnection.uiState.transportState.collectAsState()
    val shapes = rememberTransportShapes(small = layout == FlagshipTransportLayout.TEN_SECONDS)

    val holdSpeed = remember(playerConnection) { HoldSpeed(playerConnection.player) }
    // a hold that outlives the row (the player closed under the finger) still lets go
    DisposableEffect(holdSpeed) { onDispose { holdSpeed.end() } }

    val onPlayPause: () -> Unit = {
        if (transportState.playbackState == Player.STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.togglePlayPause()
        }
    }
    val ended = transportState.playbackState == Player.STATE_ENDED

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // one design unit, so the whole row keeps the design's proportions on any screen
        val unit = (maxWidth / DesignWidth).coerceIn(0.27.dp, 0.42.dp)
        when (layout) {
            FlagshipTransportLayout.THREE -> {
                SlotRow(ThreeCentres) {
                    SkipButton(
                        shapes = shapes, unit = unit, backwards = true,
                        enabled = transportState.canSkipPrevious,
                        onClick = playerConnection::seekToPrevious,
                        onHoldStart = { holdSpeed.start(HoldSlowFactor) },
                        onHoldEnd = holdSpeed::end,
                        holdLabel = "0.5×", holdPulseMs = 900L,
                        description = stringResource(R.string.flagship_previous),
                    )
                    PlayButton(
                        shapes = shapes, unit = unit, playing = transportState.isPlaying, ended = ended,
                        circle = false, onClick = onPlayPause,
                    )
                    SkipButton(
                        shapes = shapes, unit = unit, backwards = false,
                        enabled = transportState.canSkipNext,
                        onClick = playerConnection::seekToNext,
                        onHoldStart = { holdSpeed.start(HoldFastFactor) },
                        onHoldEnd = holdSpeed::end,
                        holdLabel = "2×", holdPulseMs = 420L,
                        description = stringResource(R.string.flagship_next),
                    )
                }
            }
            FlagshipTransportLayout.TEN_SECONDS -> {
                val skipUnit = unit
                val circle = unit * TenCircle
                SlotRow(TenCentres) {
                    TenSecondsButton(
                        forward = false,
                        iconSize = DpSize(unit * TenIconWidth, unit * TenIconHeight),
                        onClick = {
                            val player = playerConnection.player
                            playerConnection.seekTo((player.currentPosition - TenSecondsMs).coerceAtLeast(0L))
                        },
                        description = stringResource(R.string.flagship_back_ten),
                    )
                    SkipButton(
                        shapes = shapes, unit = skipUnit, backwards = true,
                        enabled = transportState.canSkipPrevious,
                        onClick = playerConnection::seekToPrevious,
                        onHoldStart = { holdSpeed.start(HoldSlowFactor) },
                        onHoldEnd = holdSpeed::end,
                        holdLabel = "0.5×", holdPulseMs = 900L,
                        description = stringResource(R.string.flagship_previous),
                        touch = TenSkipTouch,
                    )
                    PlayButton(
                        shapes = shapes, unit = unit,
                        playing = transportState.isPlaying, ended = ended,
                        circle = true, circleSize = circle, onClick = onPlayPause,
                    )
                    SkipButton(
                        shapes = shapes, unit = skipUnit, backwards = false,
                        enabled = transportState.canSkipNext,
                        onClick = playerConnection::seekToNext,
                        onHoldStart = { holdSpeed.start(HoldFastFactor) },
                        onHoldEnd = holdSpeed::end,
                        holdLabel = "2×", holdPulseMs = 420L,
                        description = stringResource(R.string.flagship_next),
                        touch = TenSkipTouch,
                    )
                    TenSecondsButton(
                        forward = true,
                        iconSize = DpSize(unit * TenIconWidth, unit * TenIconHeight),
                        onClick = {
                            val player = playerConnection.player
                            val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            playerConnection.seekTo((player.currentPosition + TenSecondsMs).coerceAtMost(duration))
                        },
                        description = stringResource(R.string.flagship_forward_ten),
                    )
                }
            }
        }
    }
}

// each child centred on its share of the width, all on one centre line
@Composable
private fun SlotRow(centres: FloatArray, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = constraints.maxWidth
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val x = (width * centres.getOrElse(i) { 0.5f } - p.width / 2f).roundToInt()
                p.place(x, (height - p.height) / 2)
            }
        }
    }
}

// shrinks a little under the finger and springs back, read while drawing only
@Composable
private fun pressScale(source: MutableInteractionSource): () -> Float {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "flagshipPress",
    )
    return { scale.value }
}

@Composable
private fun PlayButton(
    shapes: TransportShapes,
    unit: Dp,
    playing: Boolean,
    ended: Boolean,
    circle: Boolean,
    onClick: () -> Unit,
    circleSize: Dp = 0.dp,
) {
    val source = remember { MutableInteractionSource() }
    val press = pressScale(source)
    val glyph = rememberPlayGlyph(playing)
    val description = stringResource(if (playing) R.string.flagship_pause else R.string.flagship_play)
    val glyphColor = if (circle) Color.Black else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (circle) circleSize + 14.dp else ButtonTouch)
            .graphicsLayer {
                val s = press()
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = source, indication = RemoteFocus, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (circle) circleSize else ButtonTouch)
                .then(
                    if (circle) {
                        // the white circle squashes and bounces along with its glyph
                        Modifier
                            .graphicsLayer {
                                val s = glyph.squash.value
                                scaleX = 0.85f + 0.15f * s
                                scaleY = 0.85f + 0.15f * s
                            }
                            .clip(CircleShape)
                            .background(Color.White)
                    } else Modifier
                ),
        ) {
            if (ended) {
                Image(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(glyphColor),
                    modifier = Modifier.size(shapes.playSize.height.let { unit * it * 0.9f }),
                )
            } else {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind { drawPlayGlyph(shapes, glyph, unit.toPx(), glyphColor) },
                )
            }
        }
    }
}

@Composable
private fun SkipButton(
    shapes: TransportShapes,
    unit: Dp,
    backwards: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    holdLabel: String,
    holdPulseMs: Long,
    description: String,
    // narrower in the ten second row, where a wide one would reach into the ring beside it
    touch: Dp = ButtonTouch,
) {
    val scope = rememberCoroutineScope()
    val cycle = remember { SkipCycleState() }
    val source = remember { MutableInteractionSource() }
    val press = pressScale(source)
    var holding by remember { mutableStateOf(false) }
    // the triangles keep running while it's held, so the button itself shows it's going faster or slower
    LaunchedEffect(holding) {
        while (holding) {
            launch { cycle.step() }
            kotlinx.coroutines.delay(holdPulseMs)
        }
    }
    val badge by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "flagshipHoldBadge",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(touch)
            .graphicsLayer {
                val s = press()
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.36f
            }
            .tapOrHold(
                enabled = enabled,
                onTap = {
                    scope.launch { cycle.step() }
                    onClick()
                },
                onHoldStart = {
                    holding = true
                    onHoldStart()
                },
                onHoldEnd = {
                    holding = false
                    onHoldEnd()
                },
                interactionSource = source,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawSkipGlyph(shapes, cycle.phase(), unit.toPx(), Color.White, backwards) },
        )
        if (badge > 0.01f) {
            Text(
                text = holdLabel,
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = badge.coerceIn(0f, 1f)
                        val s = 0.6f + 0.4f * badge
                        scaleX = s
                        scaleY = s
                        translationY = -(4.dp.toPx() + 12.dp.toPx() * badge)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
    }
}

// the design's round arrow with 10 in it; a tap gives it a quick turn its way and lets it bounce back
@Composable
private fun TenSecondsButton(
    forward: Boolean,
    iconSize: DpSize,
    onClick: () -> Unit,
    description: String,
) {
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val source = remember { MutableInteractionSource() }
    val press = pressScale(source)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                val s = press()
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = source, indication = RemoteFocus) {
                onClick()
                scope.launch {
                    spin.animateTo(1f, tween(durationMillis = 120, easing = FastOutSlowInEasing))
                    spin.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f))
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        TenSecondsIcon(iconSize, spin = { spin.value * (if (forward) 32f else -32f) })
    }
}

// the design uses one picture for both ways, so it isn't mirrored (that would flip the 10 too)
@Composable
private fun TenSecondsIcon(size: DpSize, spin: () -> Float = { 0f }) {
    Image(
        painter = painterResource(R.drawable.flagship_ten_seconds),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = spin() },
    )
}

// a layout drawn small and still, for the editor's controls sheet
@Composable
fun TransportLayoutPreview(layout: FlagshipTransportLayout, modifier: Modifier = Modifier) {
    val ten = layout == FlagshipTransportLayout.TEN_SECONDS
    val shapes = rememberTransportShapes(small = ten)
    val still = remember { PlayGlyphState(playing = false) }
    val unit = 0.2.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (ten) 12.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (ten) TenSecondsIcon(DpSize(unit * TenIconWidth, unit * TenIconHeight))
        StillSkip(shapes, unit, backwards = true)
        if (ten) {
            Box(
                modifier = Modifier
                    .size(unit * TenCircle)
                    .clip(CircleShape)
                    .background(Color.White)
                    .drawBehind { drawPlayGlyph(shapes, still, unit.toPx(), Color.Black) },
            )
        } else {
            Spacer(
                modifier = Modifier
                    .size(unit * shapes.playSize.height)
                    .drawBehind { drawPlayGlyph(shapes, still, unit.toPx(), Color.White) },
            )
        }
        StillSkip(shapes, unit, backwards = false)
        if (ten) TenSecondsIcon(DpSize(unit * TenIconWidth, unit * TenIconHeight))
    }
}

@Composable
private fun StillSkip(shapes: TransportShapes, unit: Dp, backwards: Boolean) {
    Spacer(
        modifier = Modifier
            .size(width = unit * shapes.pairWidth + 2.dp, height = unit * shapes.skipSize.height + 2.dp)
            .drawBehind { drawSkipGlyph(shapes, 1f, unit.toPx(), Color.White, backwards) },
    )
}
