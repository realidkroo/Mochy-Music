// InfoSheetKit.kt

package com.mochyy.mochymusic.ui.player.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.component.RemoteFocus

// the look both info sheets share, drawn from the about song and stream info mocks

internal val InfoSidePadding = 20.dp
internal val InfoMuted = Color.White.copy(alpha = 0.55f)
internal val InfoLine = Color.White.copy(alpha = 0.12f)
private val CardShape = RoundedCornerShape(26.dp)

@Composable
internal fun InfoSheetTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )
}

@Composable
internal fun InfoSectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
internal fun InfoDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .height(1.dp)
            .background(InfoLine),
    )
}

/** the spaced-out "stats for nerds" rule between the provider and the format cards */
@Composable
internal fun NerdsDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(InfoLine))
        Text(
            text = "STATS FOR NERDS",
            color = InfoMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.55.em,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(InfoLine))
    }
}

@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        content = content,
    )
}

/** the short line that joins one stats card to the next, like a signal path */
@Composable
internal fun CardConnector() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(4.dp)
                .height(22.dp)
                .background(MenuRowSurface),
        )
    }
}

/** one "name : value" line of a stats card */
@Composable
internal fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = InfoMuted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f).padding(end = 10.dp),
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** the round little buttons under the provider: outlined, or filled for the one that does something big */
@Composable
internal fun InfoPill(text: String, filled: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)) else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), shape))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (!enabled) 0.4f else if (filled) 1f else 0.75f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** a soft grey block standing in for text that's still on its way */
@Composable
internal fun LoadingBar(width: Dp, height: Dp = 12.dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
internal fun InfoBullet(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(InfoMuted))
        Spacer(Modifier.width(9.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = InfoMuted)) { append(label) }
                append("  ")
                withStyle(SpanStyle(color = Color.White)) { append(value) }
            },
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun InfoColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = InfoSidePadding)
            .padding(top = 8.dp, bottom = 28.dp),
        content = content,
    )
}
