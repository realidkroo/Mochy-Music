// bits the support sheet and the donate sheet share: the coffee girl, her sizing, and the pill buttons

package com.mochyy.mochymusic.ui.screens.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.component.RemoteFocus

// the art is 768 x 1013, solid down to 56% of her height and fading out below that
internal const val CharacterAspect = 1013f / 768f

// gap between the sheet's handle row and the top of her hair
internal val CharacterTop = 24.dp

internal val DonateSoftText = Color.White.copy(alpha = 0.72f)

/** about half the sheet wide like the mocks, but small enough on a short or sideways screen that the words still fit */
internal fun characterWidth(maxWidth: Dp, maxHeight: Dp): Dp =
    minOf(maxWidth * 0.46f, maxHeight * 0.44f / CharacterAspect).coerceIn(120.dp, 250.dp)

/** the coffee girl, loaded by coil so the decode never lands on the frame the sheet opens on */
@Composable
internal fun DonateCharacter(width: Dp, modifier: Modifier = Modifier, overlapBelow: Dp = 0.dp) {
    val context = LocalContext.current
    val request = remember(context) {
        ImageRequest.Builder(context)
            .data(R.drawable.donate_character)
            .crossfade(true)
            .build()
    }
    val height = width * CharacterAspect
    // takes up less room than she's tall, so whatever comes next can sit over her faded bottom
    Box(modifier.size(width, height - overlapBelow)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .size(width, height),
        )
    }
}

/** the round grey buttons under both sheets, quiet ones are the way out */
@Composable
internal fun DonatePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = if (quiet) Color.White.copy(alpha = 0.38f) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
