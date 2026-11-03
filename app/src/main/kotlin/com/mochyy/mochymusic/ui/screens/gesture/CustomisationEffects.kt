// CustomisationEffects.kt

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AnimationSpeedKey
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.BlurQualityKey
import com.mochyy.mochymusic.constants.BlurStrengthPxKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.ElementBlurKey
import com.mochyy.mochymusic.constants.EnableElementBlurAnimatorKey
import com.mochyy.mochymusic.constants.EnableProgressiveBlurKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.constants.PlayerNavBarDefault
import com.mochyy.mochymusic.constants.PlayerNavBarKey
import com.mochyy.mochymusic.constants.PlayerStatusBarDefault
import com.mochyy.mochymusic.constants.PlayerStatusBarKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.AnimationSpeeds
import com.mochyy.mochymusic.ui.utils.animationSpeedLabel
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

private const val MinBlurStrength = 10f
private const val MaxBlurStrength = 50f

// the glass's strength until someone moves the slider, and where the reset puts it back
private const val DefaultBlurStrength = 25f

// the blur card leaves this much room for its preview, narrower than a style card's so the switches fit beside it
private val BlurPreviewWidth = 104.dp

private val PillShape = RoundedCornerShape(14.dp)
private val PillTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = (-0.02).em,
)

// glass blur: on or off, progressive or flat bars, low or high quality, and how strong, with the app's own glass as the preview
@Composable
internal fun BlurSection(modifier: Modifier = Modifier) {
    val (disableBlur, onDisableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (progressive, onProgressive) = rememberPreference(EnableProgressiveBlurKey, defaultValue = true)
    val (quality, onQuality) = rememberEnumPreference(BlurQualityKey, com.mochyy.mochymusic.ui.component.DefaultBlurQuality)
    val (strength, onStrength) = rememberPreference(BlurStrengthPxKey, defaultValue = DefaultBlurStrength)
    val haptic = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf((strength / 5f).roundToInt()) }
    val blurOn = !disableBlur
    val defaultQuality = com.mochyy.mochymusic.ui.component.DefaultBlurQuality
    val changed = disableBlur || !progressive || quality != defaultQuality || kotlin.math.abs(strength - DefaultBlurStrength) >= 0.5f

    Column(modifier.fillMaxWidth()) {
        EffectCard(
            title = stringResource(R.string.appearance_blur),
            checked = blurOn,
            onCheckedChange = { onDisableBlur(!it) },
            canReset = changed,
            onReset = {
                onDisableBlur(false)
                onProgressive(true)
                onQuality(defaultQuality)
                onStrength(DefaultBlurStrength)
                lastTick = (DefaultBlurStrength / 5f).roundToInt()
            },
            preview = { BlurPreview() },
            previewWidth = BlurPreviewWidth,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = if (blurOn) 1f else 0.45f },
            ) {
                PillSwitchRow(
                    title = stringResource(R.string.appearance_progressive_blur),
                    checked = progressive,
                    onCheckedChange = onProgressive,
                    enabled = blurOn,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PillShape)
                        .background(LabColors.Row)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(stringResource(R.string.appearance_blur_quality), style = PillTextStyle, color = Color.White)
                    Spacer(Modifier.height(7.dp))
                    MiniSegmented(
                        options = listOf(stringResource(R.string.appearance_low), stringResource(R.string.appearance_high)),
                        selectedIndex = if (quality == BlurQuality.LOW) 0 else 1,
                        onSelect = { onQuality(if (it == 0) BlurQuality.LOW else BlurQuality.HIGH) },
                        enabled = blurOn,
                    )
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = stringResource(R.string.appearance_blur_strength),
            valueText = stringResource(R.string.customise_px, strength.roundToInt()),
            startLabel = stringResource(R.string.customise_px, MinBlurStrength.roundToInt()),
            endLabel = stringResource(R.string.customise_px, MaxBlurStrength.roundToInt()),
            fraction = (strength - MinBlurStrength) / (MaxBlurStrength - MinBlurStrength),
            onFractionChange = { f ->
                val next = MinBlurStrength + f * (MaxBlurStrength - MinBlurStrength)
                val tick = (next / 5f).roundToInt()
                if (tick != lastTick) {
                    lastTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onStrength(next)
            },
            enabled = blurOn,
        )
    }
}

// blur on things themselves, apart from the glass: lyric lines, the cover zooming away, title morphs, menus, page wipes
@Composable
internal fun ElementBlurSection(modifier: Modifier = Modifier) {
    val (elementBlur, onElementBlur) = rememberPreference(ElementBlurKey, defaultValue = true)
    EffectCard(
        title = stringResource(R.string.customise_element_blur),
        checked = elementBlur,
        onCheckedChange = onElementBlur,
        preview = { ElementBlurPreview() },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.customise_element_blur_desc),
            style = CaptionStyle,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

// every animation in the app: off stops them all, the speed sits on top of android's own animation scale
@Composable
internal fun AnimationSection(modifier: Modifier = Modifier) {
    val (enabled, onEnabled) = rememberPreference(EnableElementBlurAnimatorKey, defaultValue = true)
    val (speed, onSpeed) = rememberPreference(AnimationSpeedKey, defaultValue = 1f)
    val haptic = LocalHapticFeedback.current
    val stops = AnimationSpeeds
    val index = stops.indexOfFirst { it >= speed - 0.01f }.takeIf { it >= 0 } ?: stops.lastIndex

    Column(modifier.fillMaxWidth()) {
        EffectCard(
            title = stringResource(R.string.appearance_animation),
            checked = enabled,
            onCheckedChange = onEnabled,
            value = if (enabled) animationSpeedLabel(speed) else stringResource(R.string.customise_off),
            preview = { AnimationPreview() },
        ) {
            Text(
                text = stringResource(if (speed == 1f) R.string.customise_animation_follows else R.string.customise_animation_on_top),
                style = CaptionStyle,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = stringResource(R.string.customise_animation_speed),
            valueText = animationSpeedLabel(speed),
            startLabel = stringResource(R.string.customise_slower),
            endLabel = stringResource(R.string.customise_faster),
            fraction = index / stops.lastIndex.toFloat(),
            onFractionChange = { f ->
                val next = stops[(f * stops.lastIndex).roundToInt()]
                if (next != speed) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSpeed(next)
                }
            },
            enabled = enabled,
        )
    }
}

// the four lyric styles to swipe through, each singing on its card as it really would
@Composable
internal fun LyricsStyleSection(modifier: Modifier = Modifier) {
    val (style, onStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val names = LyricsMotionStyle.entries.associateWith { stringResource(lyricStyleNameRes(it)) }
    StylePagerSection(
        category = stringResource(R.string.customise_lyrics_animation),
        styles = LyricsMotionStyle.entries,
        selected = style,
        nameOf = { names.getValue(it) },
        onSelect = onStyle,
        modifier = modifier,
    ) { LyricsStylePreview(it) }
}

// a lyric style's name in the app's language
private fun lyricStyleNameRes(style: LyricsMotionStyle): Int = when (style) {
    LyricsMotionStyle.MOTION -> R.string.appearance_style_1
    LyricsMotionStyle.LEGACY -> R.string.appearance_style_2
    LyricsMotionStyle.BORING -> R.string.customise_style_boring
    LyricsMotionStyle.PLAIN -> R.string.customise_style_plain
}

// the phone's status bar over the open player
@Composable
internal fun TopBarSection(modifier: Modifier = Modifier) {
    val (shown, onShown) = rememberPreference(PlayerStatusBarKey, defaultValue = PlayerStatusBarDefault)
    Column(modifier.fillMaxWidth()) {
        EffectCard(
            title = stringResource(R.string.customise_top_bar),
            checked = shown,
            onCheckedChange = onShown,
            preview = { StatusBarPreview(shown) },
        ) {
            Text(
                text = stringResource(R.string.customise_top_bar_desc),
                style = CaptionStyle,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

// the phone's navigation bar under the open player
@Composable
internal fun BottomBarSection(modifier: Modifier = Modifier) {
    val (shown, onShown) = rememberPreference(PlayerNavBarKey, defaultValue = PlayerNavBarDefault)
    EffectCard(
        title = stringResource(R.string.customise_bottom_bar),
        checked = shown,
        onCheckedChange = onShown,
        floatingPreview = true,
        preview = { NavBarPreview(shown) },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.customise_bottom_bar_desc),
            style = CaptionStyle,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

// a card with a switch in its corner, what it controls on the left and a live preview peeking up on the right
@Composable
private fun EffectCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    previewWidth: androidx.compose.ui.unit.Dp = PreviewWidth,
    floatingPreview: Boolean = false,
    canReset: Boolean = false,
    onReset: (() -> Unit)? = null,
    preview: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(StyleCardHeight)
            .clip(CardShape)
            .background(LabColors.Card),
    ) {
        PreviewFrame(
            modifier = Modifier.align(Alignment.BottomEnd),
            width = previewWidth,
            top = 58.dp,
            floating = floatingPreview,
            content = preview,
        )
        CardTitle(title = title, canReset = canReset, onReset = onReset)
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp),
        )
        Column(
            modifier = Modifier.padding(
                start = CardTextInset,
                top = 52.dp,
                end = previewWidth + 30.dp,
            ),
            content = content,
        )
        if (value != null) {
            BasicText(
                text = value,
                style = CardValueStyle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 24.dp),
            )
        }
    }
}

// a dark pill with a small label and a shrunken switch, for a setting inside a card
@Composable
private fun PillSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(LabColors.Row)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(title, style = PillTextStyle, color = Color.White, modifier = Modifier.weight(1f))
        Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.requiredSize(52.dp, 32.dp).scale(0.72f),
            )
        }
    }
}

// a small two-way picker with a white pill sliding to the chosen side
@Composable
private fun MiniSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, enabled: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LabColors.Card)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "miniSegment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selectedIndex) Color.Black else Color.White,
                    tween(160),
                    label = "miniSegmentText",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { onSelect(index) },
                ) {
                    Text(label, style = PillTextStyle, color = color)
                }
            }
        }
    }
}
