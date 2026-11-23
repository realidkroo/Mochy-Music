// the mixer's card on audio fx: every bus's meter side by side, the switch, and the way into the mixer

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.ui.screens.audiolab.EffectCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.MixerViewModel

@Composable
internal fun MixerCard(onOpen: () -> Unit, viewModel: MixerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val meters = rememberMixerMeters(active = state.enabled)
    val effects = (state.buses + state.master).sumOf { it.slots.size }
    EffectCard(
        picture = {
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
                val buses = state.buses + state.master
                Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    meters.tick.longValue
                    val count = buses.size
                    val slot = size.width / count
                    val bar = (slot * 0.42f).coerceAtMost(26.dp.toPx())
                    val r = CornerRadius(bar / 2, bar / 2)
                    for ((i, bus) in buses.withIndex()) {
                        val index = if (bus.isMaster) MixerNative.Master else bus.id
                        val level = maxOf(meters.levelL[index], meters.levelR[index])
                        val fill = ((level - MixerMeterState.Floor) / -MixerMeterState.Floor).coerceIn(0f, 1f)
                        val x = slot * i + (slot - bar) / 2
                        drawRoundRect(LabColors.StageLine, Offset(x, 0f), Size(bar, size.height), r)
                        // a quiet bus still shows a sliver of its colour, so the row reads as buses even in silence
                        val h = size.height * maxOf(fill, 0.04f)
                        drawRoundRect(busColor(bus).copy(alpha = if (state.enabled) 0.9f else 0.35f), Offset(x, size.height - h), Size(bar, h), r)
                    }
                }
            }
        },
        header = {
            LabToggleRow(
                title = stringResource(R.string.mixer_card_title),
                subtitle = stringResource(R.string.mixer_card_sub),
                checked = state.enabled,
                onCheckedChange = viewModel.store::setEnabled,
            )
        },
        expanded = false,
        options = {},
        footer = {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.mixer_effect_count, effects, effects),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = LabColors.Secondary,
                )
                Spacer(Modifier.weight(1f))
                LabTextAction(stringResource(R.string.mixer_open), R.drawable.arrow_forward, onClick = onOpen)
            }
        },
    )
}
