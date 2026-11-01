// customisation's launcher icons: mochy, its outline and anime mochy, each on its backgrounds, the one in use outlined

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.findActivity
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.utils.AppIcon
import com.mochyy.mochymusic.utils.AppIconFamily
import com.mochyy.mochymusic.utils.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val TileSize = 66.dp
// close to a launcher's squircle, so the tile reads as the icon it will be
private val TileShape = RoundedCornerShape(TileSize * 0.27f)
private val TileRing = Color.White.copy(alpha = 0.12f)
private val TileRingPicked = Color.White.copy(alpha = 0.9f)

@Composable
internal fun IconsSection() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(AppIcons.current(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LabColors.Card)
            .padding(top = CardTextInset, bottom = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.customise_icons),
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = CardTextInset),
        )
        AppIconFamily.entries.forEach { family ->
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(family.title),
                style = CaptionStyle,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = CardTextInset),
            )
            Spacer(Modifier.height(10.dp))
            // a row per family, swiped sideways: five tiles don't fit across a phone with their names
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(PaddingValues(horizontal = CardTextInset)),
            ) {
                AppIcon.entries.filter { it.family == family }.forEach { icon ->
                    IconTile(icon = icon, picked = icon == picked) {
                        if (icon == picked) return@IconTile
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        picked = icon
                        // the next launch's splash goes with it: the icon itself for an anime one, the bare mark for the rest
                        context.findActivity()?.let { AppIcons.syncSplash(it, icon) }
                        // the package manager is a call to the system, kept off the main thread
                        scope.launch(Dispatchers.IO) { AppIcons.apply(context.applicationContext, icon) }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.customise_icons_caption),
            style = CaptionStyle,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = CardTextInset, end = CardTextInset, top = 16.dp),
        )
    }
}

/** the icon as the launcher will draw it: its background, and its art at the adaptive icon's 108 to 72 crop */
@Composable
private fun IconTile(icon: AppIcon, picked: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (picked) TileRingPicked else TileRing, tween(220), label = "iconRing")
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TileSize + 10.dp)
            .pressBounce(interaction, pressedScale = 0.94f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(TileSize)
                .clip(TileShape)
                .then(icon.background?.let { Modifier.background(colorResource(it)) } ?: Modifier)
                .border(if (picked) 2.dp else 1.dp, ring, TileShape),
        ) {
            Image(
                painter = painterResource(icon.foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.requiredSize(TileSize * 1.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(icon.label),
            style = CaptionStyle,
            color = Color.White.copy(alpha = if (picked) 0.95f else 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
