// MixItConnectorRow.kt

package com.mochyy.mochymusic.ui.mixit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.ui.component.detail.PlaylistInset
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.theme.InterFontFamily

/**
 * Inline transition row placed between consecutive songs while Mix It mode is open on a playlist
 * (matching Screen 4 of the Mix It concept): hairline + current transition label on the left,
 * crisp "Edit transition" pill on the right.
 */
@Composable
internal fun MixItTransitionConnectorRow(
    transition: PlaylistTransition?,
    fallbackLabel: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustom = transition != null
    val label = remember(transition, fallbackLabel) {
        if (transition != null) MixItPlanner.displayLabel(transition) else fallbackLabel
    }
    val cueSummary = remember(transition) {
        transition?.let { "${formatMixClock(it.outStartMs)} → ${formatMixClock(it.inStartMs)}" }
    }
    val interaction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PlaylistInset, end = 24.dp, top = 2.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCustom) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (isCustom) MixItColors.Gold else Color.White.copy(alpha = 0.35f)),
                )
                Text(
                    text = label,
                    fontFamily = InterFontFamily,
                    fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = if (isCustom) MixItColors.GoldBright else Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (cueSummary != null) {
                    Text(
                        text = cueSummary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .height(1.dp)
                    .background(
                        if (isCustom) MixItColors.Gold.copy(alpha = 0.38f)
                        else Color.White.copy(alpha = 0.14f),
                    ),
            )
        }

        // Tactile "Edit transition" pill button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressBounce(interaction)
                .height(27.dp)
                .clip(CircleShape)
                .background(
                    if (isCustom) MixItColors.Gold.copy(alpha = 0.22f)
                    else Color.White.copy(alpha = 0.16f),
                )
                .border(
                    width = 1.dp,
                    color = if (isCustom) MixItColors.Gold.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onEditClick)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(if (isCustom) R.string.mixit_edit_custom else R.string.mixit_edit_transition),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = if (isCustom) MixItColors.GoldBright else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
    }
}

/**
 * Compact status pill shown on a playlist when Mix It is active (outside edit mode), so the user
 * can tell at a glance their custom DJ transitions are live and tap straight into editing.
 */
@Composable
internal fun MixItActiveBanner(
    customCount: Int,
    fallbackLabel: String,
    onOpenMixIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset, vertical = 6.dp)
            .pressBounce(interaction)
            .clip(CircleShape)
            .background(MixItColors.Gold.copy(alpha = 0.14f))
            .border(1.dp, MixItColors.Gold.copy(alpha = 0.45f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenMixIt)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mix_it),
            contentDescription = null,
            tint = MixItColors.Gold,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (customCount > 0) {
                stringResource(R.string.mixit_active_banner_count, customCount)
            } else {
                stringResource(R.string.mixit_active_banner_default, fallbackLabel)
            },
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MixItColors.GoldBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.mixit_edit_pill),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MixItColors.OnGold,
            modifier = Modifier
                .clip(CircleShape)
                .background(MixItColors.Gold)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
