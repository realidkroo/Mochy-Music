// the "arigatou! :3" sheet with the actual places to donate, opened from donate us! or a dev's profile

package com.mochyy.mochymusic.ui.screens.donate

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.update.VersionRing
import com.mochyy.mochymusic.ui.screens.update.openUrl
import com.mochyy.mochymusic.ui.component.RemoteFocus

const val KofiUrl = "https://ko-fi.com/realidkroo"
const val SocialBuzzUrl = "https://sociabuzz.com/realidkroo/tribe"
const val BuyMeACoffeeUrl = "https://buymeacoffee.com/realkeichii"

// the title tucks up over her faded bottom by this much, like the mock
private val TitleOverlap = 18.dp

/** shows the donate sheet, on top of the support sheet when that's what opened it */
fun PopupSheetState.showDonateSheet() {
    val popup = this
    show(
        fullBleed = true,
        buttonBar = { DonatePillButton(text = "what no i dont want to", onClick = popup::dismiss, quiet = true) },
    ) {
        DonateSheetContent()
    }
}

@Composable
private fun DonateSheetContent() {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val characterWidth = characterWidth(maxWidth, maxHeight)

        VersionRing(
            text = "MOCHY MUSIC",
            modifier = Modifier
                .align(Alignment.TopEnd)
                // centred just in from the top-right corner so the sheet's edge cuts it off, and takes no room of its own
                .layout { measurable, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)
                    val placeable = measurable.measure(loose)
                    layout(0, 0) { placeable.place(x = (-198).dp.roundToPx(), y = (-109).dp.roundToPx()) }
                }
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(CharacterTop))
            DonateCharacter(
                width = characterWidth,
                overlapBelow = TitleOverlap,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "Arigatou! :3",
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Here’s a way to support and donate to us!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = DonateSoftText,
            )

            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DonateLinkRow(
                    title = "Support us on Ko-fi",
                    icon = R.drawable.ic_kofi,
                    iconSize = 22.dp,
                    onClick = { context.openUrl(KofiUrl) },
                )
                DonateLinkRow(
                    title = "Support us on SociaBuzz",
                    subtitle = "Recommended for some SEA and LATAM countries",
                    icon = R.drawable.link,
                    iconSize = 16.dp,
                    iconTint = Color.Black,
                    onClick = { context.openUrl(SocialBuzzUrl) },
                )
                DonateLinkRow(
                    title = "Support us on Buy Me a Coffee",
                    icon = R.drawable.ic_bmc_logo,
                    iconSize = 20.dp,
                    onClick = { context.openUrl(BuyMeACoffeeUrl) },
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "your donation, no matter how small it is, will always be very very useful for us :3",
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DonateLinkRow(
    title: String,
    @DrawableRes icon: Int,
    iconSize: Dp,
    onClick: () -> Unit,
    subtitle: String? = null,
    iconTint: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        // plain white, not themed: the brand marks inside only read on a light badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.88f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}
