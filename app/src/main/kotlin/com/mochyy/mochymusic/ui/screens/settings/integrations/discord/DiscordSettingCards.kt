// the pieces the discord page is built from, drawn to the user's mock: dark pill-shaped cards, pickers, a switch and a slider

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.discord.PresenceButton
import com.mochyy.mochymusic.discord.PresenceButtonTarget
import com.mochyy.mochymusic.discord.PresenceImage
import com.mochyy.mochymusic.discord.PresenceSettings
import com.mochyy.mochymusic.discord.PresenceText
import com.mochyy.mochymusic.discord.SpinStyle
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** the mock's colours, read straight off it */
internal object DiscordColors {
    val Card = Color(0xFF1F1F1F)
    val ToggleCard = Color(0xFF232323)
    val Field = Color(0xFF171717)
    val FieldSelected = Color(0xFF2B2B2B)
    val Profile = Color(0xFF0D0D0D)
    val Activity = Color(0xFF1E1E1E)
    val Cover = Color(0xFF0C0C0C)
    val Banner = Color(0xFF3C3C3C)
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.55f)
    val Hint = Color.White.copy(alpha = 0.32f)
    val Menu = Color(0xFF1E1F23)
    val Blurple = Color(0xFF5865F2)
}

private val CardShape = RoundedCornerShape(20.dp)
private val FieldHeight = 38.dp

/** how long the dropdown takes to unfold and fold */
private const val UnfoldMs = 280

@Composable
internal fun DiscordSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
internal fun DiscordCard(
    modifier: Modifier = Modifier,
    color: Color = DiscordColors.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        content = content,
    )
}

@Composable
internal fun DiscordCardTitle(text: String) {
    Text(text, color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/** a card that turns one behaviour on or off, with a line saying what it does */
@Composable
internal fun DiscordSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DiscordCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                DiscordCardTitle(title)
                Text(
                    text = description,
                    color = DiscordColors.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** the small grey "string" / "action" labels inside the button cards */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** a pill text box, hint shows while it's empty so the user knows what empty means */
@Composable
internal fun DiscordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = DiscordColors.Primary,
        ),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .clip(CircleShape)
                    .background(DiscordColors.Field)
                    .padding(horizontal = 16.dp),
            ) {
                if (value.isEmpty()) {
                    Text(hint, color = DiscordColors.Hint, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** a text line of the status: title and its box */
@Composable
internal fun DiscordTextCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "Hidden",
) {
    DiscordCard {
        DiscordCardTitle(title)
        Spacer(Modifier.height(10.dp))
        DiscordTextField(value = value, onValueChange = onValueChange, hint = hint)
    }
}

/** a pill that unfolds into its own list at the same width and row height, so it reads as one shape opening up and not a menu popping up elsewhere */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> DiscordDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    optionIcon: ((T) -> Int)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, tween(UnfoldMs), label = "chevron")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            // once it has finished growing, scroll just enough for the whole list to show
            delay(UnfoldMs.toLong())
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(RoundedCornerShape(FieldHeight / 2))
            .background(DiscordColors.Field),
    ) {
        DropdownRow(
            text = label(selected),
            icon = optionIcon?.invoke(selected),
            color = if (open) DiscordColors.Primary else DiscordColors.Secondary,
            onClick = { open = !open },
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = if (open) "Close" else "Open",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = chevronTurn },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 520f), expandFrom = Alignment.Top) +
                fadeIn(tween(180, delayMillis = 60)),
            exit = shrinkVertically(tween(UnfoldMs), shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
        ) {
            // every choice, the current one included, so picking doesn't reshuffle the list mid-fold
            Column(Modifier.padding(bottom = 4.dp)) {
                options.forEach { option ->
                    DropdownRow(
                        text = label(option),
                        icon = optionIcon?.invoke(option),
                        color = DiscordColors.Primary,
                        highlighted = option == selected,
                        onClick = {
                            open = false
                            if (option != selected) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** one row of the dropdown: centred text in the pill's own size, the chosen one on a lighter pill */
@Composable
private fun DropdownRow(
    text: String,
    icon: Int?,
    color: Color,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    trailing: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(if (highlighted) DiscordColors.FieldSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { Box(Modifier.align(Alignment.CenterEnd), content = it) }
    }
}

@Composable
internal fun DiscordImageSourceCard(
    title: String,
    selected: PresenceImage,
    options: List<PresenceImage>,
    onSelect: (PresenceImage) -> Unit,
    spin: SpinStyle? = null,
    onSpinChange: (SpinStyle) -> Unit = {},
) {
    DiscordCard {
        DiscordCardTitle(title)
        Spacer(Modifier.height(10.dp))
        DiscordDropdown(selected = selected, options = options, label = { it.label }, onSelect = onSelect)
        if (selected.isRotating && spin != null) {
            FieldLabel("One full turn")
            DiscordStepSlider(
                value = spin.secondsPerTurn,
                values = (PresenceSettings.MinSpinSeconds..PresenceSettings.MaxSpinSeconds).toList(),
                label = { "${it}s" },
                onValueChange = { onSpinChange(spin.copy(secondsPerTurn = it)) },
            )
            FieldLabel("Smoothness")
            DiscordStepSlider(
                value = spin.fps,
                values = PresenceSettings.SpinFpsSteps,
                label = { "$it fps" },
                onValueChange = { onSpinChange(spin.copy(fps = it)) },
            )
            Text(
                text = "Turns forever. Discord can only show pictures from the web, so Mochy makes a GIF of " +
                    "the artwork and puts it on a free file host (uguu.se, which deletes it after three hours). " +
                    "Slower, smoother turns make a bigger GIF that takes a little longer to show up; the still " +
                    "artwork stands in meanwhile. 50 fps is as smooth as a GIF can play.",
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

@Composable
internal fun DiscordButtonCard(
    title: String,
    button: PresenceButton,
    onChange: (PresenceButton) -> Unit,
) {
    DiscordCard {
        DiscordCardTitle(title)
        FieldLabel("Label")
        DiscordTextField(
            value = button.text,
            onValueChange = { onChange(button.copy(text = it)) },
            hint = "No button",
        )
        FieldLabel("Opens")
        DiscordDropdown(
            selected = button.target,
            options = PresenceButtonTarget.entries,
            label = { it.label },
            onSelect = { onChange(button.copy(target = it)) },
            optionIcon = { it.icon },
        )
    }
}

/** two choices side by side, the chosen one sits on a lighter pill */
@Composable
internal fun <T> DiscordSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(DiscordColors.Field)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(Color.White.copy(alpha = if (option == selected || options[index - 1] == selected) 0f else 0.25f)),
                )
            }
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSelected) DiscordColors.FieldSelected else Color.Transparent)
                    .clickable { onSelect(option) },
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) DiscordColors.Primary else DiscordColors.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** a thin track with the value riding above the end of the fill, tap or drag anywhere and it snaps to one of values */
@Composable
internal fun DiscordStepSlider(
    value: Int,
    values: List<Int>,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    val latest by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val lastIndex = values.lastIndex.coerceAtLeast(1)
        fun valueAt(x: Float): Int {
            val fraction = (x / widthPx).coerceIn(0f, 1f)
            return values[(fraction * lastIndex).roundToInt().coerceIn(0, values.lastIndex)]
        }
        val fraction = values.indexOf(value).coerceAtLeast(0).toFloat() / lastIndex
        val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DiscordColors.Secondary)

        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(widthPx, values) { detectTapGestures { latest(valueAt(it.x)) } }
                .pointerInput(widthPx, values) { detectHorizontalDragGestures { change, _ -> latest(valueAt(change.position.x)) } }
                .drawBehind {
                    val y = size.height - 6.dp.toPx()
                    val thickness = 3.dp.toPx()
                    val radius = CornerRadius(thickness / 2)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width, thickness),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width * fraction.coerceAtLeast(0.02f), thickness),
                        cornerRadius = radius,
                    )
                },
        )
        // the value follows the end of the fill, but never runs off either edge or into the last value
        val labelWidth = with(density) { SliderLabelWidth.toPx() }
        val labelX = (widthPx * fraction - labelWidth / 2).coerceIn(0f, (widthPx - labelWidth * 2).coerceAtLeast(0f))
        Text(
            text = label(value),
            style = mono.copy(color = DiscordColors.Primary),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset { IntOffset(labelX.roundToInt(), 0) }
                .width(SliderLabelWidth),
        )
        Text(
            text = label(values.last()),
            style = mono,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

private val SliderLabelWidth = 52.dp

/** the bold one-liner card, like "empty it to disable" */
@Composable
internal fun DiscordNoticeCard(text: String) {
    DiscordCard {
        Text(text, color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** what each placeholder means, with a worked example */
@Composable
internal fun DiscordPlaceholderCard() {
    val body = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Color.White.copy(alpha = 0.85f),
    )
    DiscordCard {
        Text("Placeholder list", color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        PresenceText.Placeholders.forEach { (placeholder, meaning) ->
            Text("$placeholder = $meaning", style = body)
        }
        Spacer(Modifier.height(12.dp))
        Text("For example, this activity name", style = body)
        Text("{name} by {artist} on {album}", style = body.copy(color = DiscordColors.Primary, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        Text("shows up as", style = body)
        Text(
            "Listening to Fake Plastic Trees by Radiohead on The Bends",
            style = body.copy(color = DiscordColors.Primary, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(12.dp))
        Text("They work everywhere on this page, buttons included.", style = body)
    }
}
