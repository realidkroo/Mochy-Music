// shared pieces of every audio lab page (cards, stage, toggle rows, sliders, chips, segments) so the hub, equalizer and audio fx read as one set

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.eq.audio.AudioTap
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.LocalNavAnimatedContentScope
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.flow.first

object LabColors {
    val Card = Color(0xFF1C1C1E)
    val Stage = Color(0xFF0E0E10)
    val StageLine = Color(0xFF2E2E32)
    val Row = Color(0xFF26262A)
    val RowSelected = Color(0xFF34343A)
    val Chip = Color(0xFF232327)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5C5C63)
    val Ink = Color(0xFF8A8A92)

    /** the equalizer's node colours, soft enough to sit on mochy's greys and apart enough to tell */
    val Nodes = listOf(
        Color(0xFFF28BA8), Color(0xFFF2A46B), Color(0xFFF2D46B), Color(0xFF8FD98B),
        Color(0xFF6BD9C4), Color(0xFF6BBAF2), Color(0xFF8C9EF2), Color(0xFFB98CF2),
        Color(0xFFE08CF2), Color(0xFFF26B6B),
    )

    fun node(index: Int) = Nodes[index.mod(Nodes.size)]
}

@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .padding(padding),
        content = content,
    )
}

/** outlined dark box for a live picture, bounded so its animation repaints only itself and not the page and glass over it */
@Composable
fun LabStage(
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Stage)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(18.dp))
            .damageBoundary(),
    ) {
        content()
    }
}

@Composable
fun LabToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDot: Boolean = false,
) {
    val (hideDescriptions) = com.mochyy.mochymusic.utils.rememberPreference(
        com.mochyy.mochymusic.constants.HideSettingsDescriptionsKey,
        defaultValue = true,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot) {
            val dot by animateColorAsState(if (checked) Color.White else Color(0xFFD9D9DE), tween(200), label = "dot")
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
            if (subtitle != null && !hideDescriptions) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (enabled) LabColors.Secondary else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** a slider with its name on the left and its value on the right, like the Audio FX design */
@Composable
fun LabSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
        }
        DefaultPlayerSeekBarSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** the inner darker panel the sliders sit in */
@Composable
fun LabPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Row)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** options that fold away while their effect is off */
@Composable
fun LabCollapsible(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun LabSectionLabel(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            color = LabColors.Secondary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** [busy] spins the icon and holds off taps, so a press that waits on the network shows it's working */
@Composable
fun LabTextAction(text: String, iconRes: Int? = null, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            // the turn is read in the layer, so spinning never recomposes the row
            val turn = if (busy) rememberInfiniteTransition(label = "busy")
                .animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "turn") else null
            Icon(
                painterResource(iconRes), null, tint = if (enabled) Color.White else LabColors.Faint,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn?.value ?: 0f },
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (enabled) Color.White else LabColors.Faint)
    }
}

data class LabChip(val key: String, val label: String, val dot: Color? = null)

/** a scrolling row of pills; the picked one is white */
@Composable
fun LabChips(
    chips: List<LabChip>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val selected = chip.key == selectedKey
            val bg by animateColorAsState(if (selected) Color.White else LabColors.Chip, tween(180), label = "chipBg")
            val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "chipFg")
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(bg)
                    .then(
                        if (onLongPress != null) Modifier.combinedClickableCompat(
                            interaction, onClick = { onSelect(chip.key) }, onLongClick = { onLongPress(chip.key) },
                        ) else Modifier.clickable(interaction, null) { onSelect(chip.key) }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chip.dot != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(chip.dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(chip.label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg, maxLines = 1)
            }
        }
        trailing()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this.then(
    Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
)

/** a track with a white pill that slides to the picked option */
@Composable
fun LabSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(LabColors.Card)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(height / 2))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(Color.White)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selectedIndex) Color.Black else Color.White, tween(160), label = "segText")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** the 32dp light circle with a dark glyph that mochy's settings rows lead with */
@Composable
fun LabGlyph(iconRes: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFFE5E5EA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = Color.Black, modifier = Modifier.size(size * 0.55f))
    }
}

/** dims a section while what it belongs to is off, without removing it */
fun Modifier.dimmedWhen(off: Boolean, alpha: Float): Modifier = graphicsLayer { this.alpha = if (off) alpha else 1f }

@Composable
fun animatedDim(off: Boolean): Float =
    animateFloatAsState(if (off) 0.45f else 1f, tween(220), label = "dim").value

/** whether pictures should move: not while the player covers the app, the page is opening or closing, or it is scrolled out of view */
@Composable
fun rememberPicturesHeld(onScreen: State<Boolean>): State<Boolean> {
    val obscured = LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    return remember(obscured, navTransition, onScreen) {
        derivedStateOf {
            !onScreen.value || obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }
}

/** seconds, advancing every frame while the picture is free to move, or checking back a few times a second when moving says no */
@Composable
fun rememberLabTime(held: State<Boolean>, moving: () -> Boolean = { true }): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val currentMoving by androidx.compose.runtime.rememberUpdatedState(moving)
    LaunchedEffect(held) {
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            if (!currentMoving()) {
                kotlinx.coroutines.delay(250)
                last = 0L
                continue
            }
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    return time
}

/** true while any part of this is inside the window; written only when it flips */
@Composable
fun Modifier.trackOnScreen(state: MutableState<Boolean>): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height
        if (visible != state.value) state.value = visible
    }
}

/** asks the audio tap to fill while this page shows a live picture */
@Composable
fun ListenToAudio() {
    DisposableEffect(Unit) {
        AudioTap.listen()
        onDispose { AudioTap.stopListening() }
    }
}

/** a smoothed music level 0..1 for pictures to breathe with, updated from the frame clock */
class LevelFollower {
    private val scratch = FloatArray(1024)
    var level = 0f
        private set
    var punch = 0f
        private set
    private var slow = 0f

    /** call once per frame with the seconds since the last one */
    fun update(dt: Float) {
        val now = AudioTap.level(scratch)
        level += (now - level) * (if (now > level) 0.5f else 0.12f)
        slow += (now - slow) * 0.03f
        // how far above its own recent average it is: a kick or a downbeat
        val p = ((now - slow * 1.25f) * 6f).coerceIn(0f, 1f)
        punch = if (p > punch) p else (punch - dt * 3f).coerceAtLeast(0f)
    }
}

@Composable
fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(true) }

@Composable
fun dpToPx(dp: Dp): Float = with(LocalDensity.current) { dp.toPx() }
