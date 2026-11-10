// ShareBackgroundKit.kt
//
// The pieces every share's background pager is made of: its cards, the add circle, the picture's
// blur and opacity, and the colour swatches with the full spectrum.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.abs

// plain colours offered next to the cover's own
internal val ShareBasicColours = listOf(Color(0xFF111111), Color(0xFF2C2C2E), Color(0xFFF2F2F2), Color(0xFFE8DCC8))

// the pager's cards: the ones either side sit back a little, so the one in the middle reads as chosen
internal fun Modifier.backgroundPageCard(pager: PagerState, page: Int): Modifier = this
    .fillMaxSize()
    .graphicsLayer {
        val offset = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
        val s = 1f - 0.1f * offset
        scaleX = s
        scaleY = s
        alpha = 1f - 0.35f * offset
    }
    .clip(RoundedCornerShape(28.dp))
    .background(Color(0xFFD9D9D9))

// the round add button in the middle of an empty card, for picking a picture or a video
@Composable
internal fun AddCircle(contentDescription: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(Color(0xFF4F4F52))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(R.drawable.add), contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

// blur, how bright your own picture shows, and swapping it for another
@Composable
internal fun PictureBackgroundControls(
    picture: ShareBackground.Picture,
    onChange: (ShareBackground.Picture) -> Unit,
    onPickAnother: () -> Unit,
) {
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = stringResource(R.string.ps_share_bg_blur),
            subtitle = null,
            checked = picture.blur,
            onCheckedChange = { on -> onChange(picture.copy(blur = on)) },
            leadingDot = true,
        )
        LabSlider(
            label = stringResource(R.string.ps_share_bg_opacity),
            valueText = "${(picture.opacity * 100).toInt()}%",
            value = picture.opacity,
            onValueChange = { v -> onChange(picture.copy(opacity = v)) },
            valueRange = 0.2f..1f,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
            stringResource(R.string.ps_share_bg_change_picture),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(CircleShape)
                .clickable(onClick = onPickAnother)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

// the cover's colours and a few plain ones as swatches, then the whole spectrum to pick any other
@Composable
internal fun ColourBackgroundControls(colour: Color, palette: List<Color>, onPick: (Color) -> Unit) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        ) {
            (palette + ShareBasicColours).forEach { c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (c == colour) 3.dp else 1.dp, Color.White.copy(alpha = if (c == colour) 0.9f else 0.15f), CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LabCard(padding = 10.dp) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)) {
                Text(
                    stringResource(R.string.ps_share_bg_own_colour),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    hexOf(colour),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = ShareMuted,
                )
            }
            ColourSpectrum(colour, onPick = onPick)
        }
    }
}

@Composable
internal fun ShareHint(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = ShareMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

private fun hexOf(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)
