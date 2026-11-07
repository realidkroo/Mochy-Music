// shared building blocks for every onboarding page, width is capped so tablets don't stretch them

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import androidx.compose.ui.unit.em
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.component.RemoteFocus

internal object Onb {
    val Page = Color(0xFF1C1C1C)
    val Card = Color(0xFF2C2C2C)
    val CardPressed = Color(0xFF353535)
    val Field = Color(0xFF2C2C2C)
    val Title = Color.White
    val Subtitle = Color(0xFFBDBDBD)
    val Muted = Color(0xFF8C8C8C)
    val Faint = Color(0xFF6A6A6A)
    val ButtonPrimary = Color(0xFF5E5E5E)
    val ButtonSecondary = Color(0xFF353535)
    val Badge = Color(0xFFD9D9D9)

    /** wider than this and pages stop growing, centred, so a tablet doesn't get 1000dp lines */
    val MaxContentWidth = 560.dp

    /** room the sheet's handle needs above the page */
    val TopClearance = 52.dp
}

/** title tracking as tight as home's headings, the old looser values made them read wider than the text around them */
internal val OnbTitleTracking = (-0.04).em
internal val OnbSubtitleTracking = (-0.035).em

/** side padding that shrinks on very narrow screens or big display-size settings */
@Composable
internal fun rememberSidePadding(maxWidth: Dp): Dp = remember(maxWidth) { if (maxWidth < 340.dp) 20.dp else 28.dp }

/** one onboarding page, content scrolls when it doesn't fit and footer stays pinned above the navigation bar */
@Composable
internal fun OnboardingPage(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    /** space above the badge, a popup sheet has its own handle row so it needs less */
    contentTop: Dp = Onb.TopClearance + 18.dp,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val side = rememberSidePadding(maxWidth)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = Onb.MaxContentWidth)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = side)
                    .padding(top = contentTop, bottom = 16.dp),
            ) {
                badge()
                Spacer(Modifier.height(22.dp))
                Text(
                    text = title,
                    color = Onb.Title,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = OnbTitleTracking,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = subtitle,
                        color = Onb.Subtitle,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = OnbSubtitleTracking,
                    )
                }
                Spacer(Modifier.height(22.dp))
                content()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = side)
                    .padding(bottom = 18.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = footer,
            )
        }
    }
}

/** the light circle top-left of every page, holding the page's own glyph */
@Composable
internal fun PageBadge(icon: Painter, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Onb.Badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF1C1C1C), modifier = Modifier.size(size * 0.52f))
    }
}

internal enum class OnbButtonStyle { Primary, Secondary }

/** the pill buttons, pressing dips the pill like the app's other buttons */
@Composable
internal fun OnbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OnbButtonStyle = OnbButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer {
                val s = if (pressed && enabled) 0.97f else 1f
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(CircleShape)
            .background(if (style == OnbButtonStyle.Primary) Onb.ButtonPrimary else Onb.ButtonSecondary)
            .clickable(interactionSource = interaction, indication = RemoteFocus, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (style == OnbButtonStyle.Primary) Color.White else Onb.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** round single-line field for the username on the profile pages */
@Composable
internal fun OnbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Onb.Field)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Onb.Faint, fontSize = 16.sp)
                inner()
            }
        },
    )
}

/** a tappable card that dims a touch while pressed */
@Composable
internal fun OnbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = if (pressed) 0.985f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(if (pressed) Onb.CardPressed else Onb.Card)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
                else Modifier
            ),
    ) {
        content()
    }
}

/** big circle glyph, a title and a line of detail, used for import, import and sync, import from backup */
@Composable
internal fun OnbChoiceCard(
    icon: Painter,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OnbCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBadge(icon = icon, size = 56.dp)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Onb.Subtitle, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
    }
}

/** a setting with a switch, optionally with an illustration above it */
@Composable
internal fun OnbToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    OnbCard(modifier = modifier.fillMaxWidth(), onClick = { onCheckedChange(!checked) }) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (header != null) {
                header()
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (description.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(description, color = Onb.Muted, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

/** soft fade at the bottom of scrolling content so it slides under the buttons instead of ending in a hard line */
internal val BottomFade = Brush.verticalGradient(listOf(Color.Transparent, Onb.Page))
