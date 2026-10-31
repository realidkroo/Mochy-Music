package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.ui.player.PlayerPageTransition
import com.mochyy.mochymusic.ui.player.rememberMusicVolume
import com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.rubberBand
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// the volume's row, the room under it and the pills' row (the pill is drawn smaller inside, the rest is touch room)
private val VolumeRowHeight = 28.dp
private val PillGap = 15.dp
private val PillTouchHeight = 41.dp

// the pills as the design has them: 294 by 67 of its 1172 units across
internal val PillHeight = 23.dp
internal val PillWidth = 99.dp

// the bar from the top of the volume line to the bottom of its pills' touch room
internal val FlagshipBarHeight = VolumeRowHeight + PillGap + PillTouchHeight

// the room under the bar, above where the navigation bar comes back
private val FlagshipBarBottomGap = 8.dp

// the caller pads the bar 26dp in, this brings everything in to the design's edges
private val BarInset = 18.dp

// barely there, so the picture behind shows through
internal val PillFill = Color.White.copy(alpha = 0.1f)

// the volume line at rest and while it's held, and how far past its end it can be stretched
private val TrackThin = 6.5.dp
private val TrackThick = 12.dp
private val StretchMax = 26.dp
private val SpeakerSize = 15.dp
private val SpeakerGap = 10.dp

// no bounce, about a third of a second, quick enough that a held volume key never lags behind
private val VolumeGlide = spring<Float>(dampingRatio = 1f, stiffness = 400f)

private val PillText = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 9.5.sp,
    letterSpacing = (-0.03).em,
    color = Color.White,
)

// how far up from the screen's bottom the bar reaches, the nav bar read as if showing so it never jumps when it hides
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun flagshipBarFootprint(): Dp =
    WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() +
        FlagshipBarBottomGap + FlagshipBarHeight

// the new flagship's bottom bar: the phone's volume, then where the music plays and the lyrics and queue
@Composable
internal fun FlagshipBottomBar(
    isActive: (PlayerMenuAction) -> Boolean,
    onAction: (PlayerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    // like the card deck, the bar only shows on the open player
    progressProvider: () -> Float = { 1f },
    // the selector's still picture of it doesn't follow the volume keys
    preview: Boolean = false,
    // told where the lyrics or queue button is (root px) just before it opens its page, so the page grows out of it
    onPageFrom: (Rect) -> Unit = {},
    // the lyrics and queue pages, so the pill can come apart as one grows out of it; the selector's still picture has none
    pages: PlayerPageTransition? = null,
) {
    if (LocalPlayerConnection.current == null) return
    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(FlagshipBarHeight)
            .padding(horizontal = BarInset),
    ) {
        AppleVolume(listening = !preview)
        Spacer(Modifier.height(PillGap))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(PillTouchHeight),
        ) {
            OutputPill(onClick = { onAction(PlayerMenuAction.OUTPUT) }, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            PagesPill(
                isActive = isActive,
                onAction = onAction,
                onPageFrom = onPageFrom,
                pages = pages,
            )
        }
    }
}

// the volume the way the iphone does it: a thin line that swells under the finger, stretches past its ends and springs back
@Composable
private fun AppleVolume(listening: Boolean) {
    val volume = rememberMusicVolume(listening)
    val scope = rememberCoroutineScope()
    // read while drawing only, so a drag redraws the line at the screen's own rate and recomposes nothing
    val held = remember { Animatable(0f) }
    val stretch = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    // the volume the line shows, so the volume keys slide it along instead of jumping it a step at a time
    val line = remember(volume) { Animatable(volume.shown) }
    LaunchedEffect(volume) {
        snapshotFlow { volume.shown }.collect { target ->
            // a fresh launch per change keeps the speed it had, so holding a key glides in one go
            if (volume.isDragging) launch { line.snapTo(target) }
            else launch { line.animateTo(target, VolumeGlide) }
        }
    }
    val quiet = rememberVectorPainter(
        if (volume.shown <= 0f) ImageVector.vectorResource(R.drawable.volume_off)
        else ImageVector.vectorResource(R.drawable.volume_down)
    )
    val loud = rememberVectorPainter(ImageVector.vectorResource(R.drawable.volume_up))
    val description = stringResource(R.string.flagship_volume)

    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(VolumeRowHeight)
            .pointerInput(volume) {
                val reach = StretchMax.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var travelled = 0f
                    // up and down is left to the player sheet; only a sideways move takes the line
                    val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        travelled = over
                    } ?: return@awaitEachGesture
                    settle[0]?.cancel()
                    scope.launch { held.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                    val track = (size.width - (SpeakerSize + SpeakerGap).toPx() * 2f).coerceAtLeast(1f)
                    // grab the line where it's drawn, even if it's still gliding after a key press
                    val start = line.value
                    fun follow() {
                        val raw = start + travelled / track
                        volume.drag(raw.coerceIn(0f, 1f))
                        val over = when {
                            raw > 1f -> (raw - 1f) * track
                            raw < 0f -> raw * track
                            else -> 0f
                        }
                        stretch.floatValue = rubberBand(over, reach)
                    }
                    follow()
                    horizontalDrag(first.id) { change ->
                        travelled += change.positionChange().x
                        change.consume()
                        follow()
                    }
                    volume.release()
                    // a bouncy spring back, the way the iphone's slider snaps home
                    val from = stretch.floatValue
                    settle[0] = scope.launch {
                        animate(from, 0f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)) { v, _ ->
                            stretch.floatValue = v
                        }
                    }
                    scope.launch { held.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(volume.shown, 0f..1f)
                setProgress { target ->
                    volume.drag(target.coerceIn(0f, 1f))
                    volume.release()
                    true
                }
            }
            .drawWithCache {
                val iconPx = SpeakerSize.toPx()
                val gapPx = SpeakerGap.toPx()
                val reach = StretchMax.toPx()
                val tint = ColorFilter.tint(Color.White)
                onDrawBehind {
                    val h = held.value.coerceIn(0f, 1.2f)
                    val s = stretch.floatValue
                    val pull = (abs(s) / reach).coerceIn(0f, 1f)
                    val cy = size.height / 2f
                    val left = iconPx + gapPx + minOf(s, 0f)
                    val right = size.width - iconPx - gapPx + maxOf(s, 0f)
                    // a little thinner the further it's stretched, like a band pulled tight
                    val thickness = (TrackThin.toPx() + (TrackThick.toPx() - TrackThin.toPx()) * h) * (1f - 0.18f * pull)
                    val radius = CornerRadius(thickness / 2f)
                    val top = cy - thickness / 2f
                    drawRoundRect(Color.White.copy(alpha = 0.22f + 0.08f * h), Offset(left, top), Size(right - left, thickness), radius)
                    val filled = (right - left) * line.value.coerceIn(0f, 1f)
                    if (filled > 0.5f) {
                        drawRoundRect(Color.White, Offset(left, top), Size(filled.coerceAtLeast(thickness), thickness), radius)
                    }
                    val alpha = 0.6f + 0.4f * h.coerceAtMost(1f)
                    speaker(quiet, x = minOf(s, 0f) * 0.55f, cy, iconPx, grow = 1f + 0.1f * h + 0.12f * if (s < 0f) pull else 0f, alpha, tint)
                    speaker(loud, x = size.width - iconPx + maxOf(s, 0f) * 0.55f, cy, iconPx, grow = 1f + 0.1f * h + 0.12f * if (s > 0f) pull else 0f, alpha, tint)
                }
            },
    )
}

// a speaker icon at x, centred on cy, grown about its own centre
private fun DrawScope.speaker(painter: Painter, x: Float, cy: Float, sizePx: Float, grow: Float, alpha: Float, tint: ColorFilter) {
    translate(x, cy - sizePx / 2f) {
        scale(grow, grow, Offset(sizePx / 2f, sizePx / 2f)) {
            with(painter) { draw(Size(sizePx, sizePx), alpha = alpha, colorFilter = tint) }
        }
    }
}

// where the music is coming out right now, as the phone, the headphones, a bluetooth name or a cast receiver
@Composable
private fun OutputPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val service = LocalPlayerConnection.current?.service
    val devices by remember(service) { service?.audioOutputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsState()
    val cast = service?.castConnectionHandler
    val isCasting by remember(cast) { cast?.isCasting ?: MutableStateFlow(false) }.collectAsState()
    val castingTo by remember(cast) { cast?.castDeviceName ?: MutableStateFlow(null) }.collectAsState()
    val active = devices.firstOrNull { it.isActive }

    val label = when {
        isCasting -> castingTo ?: stringResource(R.string.flagship_output_speaker)
        active == null -> stringResource(R.string.flagship_output_speaker)
        active.name.isNotBlank() -> active.name
        else -> stringResource(active.kind.labelRes())
    }
    val painter: Painter = when {
        isCasting -> rememberVectorPainter(Icons.Rounded.Cast)
        active == null || active.kind == AudioOutputs.Kind.PHONE -> painterResource(R.drawable.flagship_output_phone)
        else -> rememberVectorPainter(active.kind.icon())
    }
    val description = stringResource(R.string.flagship_output_change)
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxHeight()
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = PillWidth, max = 200.dp)
                .height(PillHeight)
                .pressShrink(source)
                .clip(RoundedCornerShape(50))
                .background(PillFill)
                .padding(horizontal = 11.dp),
        ) {
            Icon(painter = painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.5.dp))
            Spacer(Modifier.width(5.dp))
            Text(text = label, style = PillText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// a little squish under the finger, read while drawing only
@Composable
internal fun Modifier.pressShrink(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "flagshipPillPress",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private fun AudioOutputs.Kind.labelRes(): Int = when (this) {
    AudioOutputs.Kind.PHONE -> R.string.flagship_output_speaker
    AudioOutputs.Kind.WIRED -> R.string.flagship_output_wired
    AudioOutputs.Kind.USB -> R.string.flagship_output_usb
    AudioOutputs.Kind.BLUETOOTH -> R.string.flagship_output_bluetooth
    AudioOutputs.Kind.HDMI -> R.string.flagship_output_hdmi
}

private fun AudioOutputs.Kind.icon() = when (this) {
    AudioOutputs.Kind.PHONE, AudioOutputs.Kind.WIRED -> Icons.Rounded.Headphones
    AudioOutputs.Kind.USB -> Icons.Rounded.Usb
    AudioOutputs.Kind.BLUETOOTH -> Icons.Rounded.Bluetooth
    AudioOutputs.Kind.HDMI -> Icons.Rounded.Tv
}
