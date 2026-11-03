// LarpMode.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LarpModeKey
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.ui.player.info.StatCard
import com.mochyy.mochymusic.utils.rememberPreference

// yes. lets larp: the badge swears it's lossless at the highest rate a dac has ever been asked for
internal val LarpFacts = StreamFacts(codec = "FLAC", sampleRate = 768_000)

// whether the player is larping right now; settings > app behaviour flips it, behind the 67 sheet
@Composable
fun rememberLarpMode(): Boolean {
    val (larping) = rememberPreference(LarpModeKey, defaultValue = false)
    return larping
}

// the badge while larping, shown even with the badge switched off, or what's the point
internal fun larpBadgeText(): String? = streamBadgeText(LarpFacts, showSampleRate = true)

// stats for nerds, larp edition: no decoder, no detours, a million watts of pure dopamine
@Composable
internal fun larpStatCards(): List<StatCard> = listOf(
    StatCard(
        title = stringResource(R.string.larp_card_format),
        lines = listOf(
            stringResource(R.string.larp_bit_depth) to stringResource(R.string.larp_bit_depth_value),
            stringResource(R.string.larp_sample_rate) to stringResource(R.string.larp_sample_rate_value),
            stringResource(R.string.larp_bitrate) to stringResource(R.string.larp_bitrate_value),
            stringResource(R.string.larp_channels) to stringResource(R.string.larp_channels_value),
            stringResource(R.string.larp_source) to stringResource(R.string.larp_source_value),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_decoder),
        lines = listOf(
            stringResource(R.string.larp_decoder_name) to stringResource(R.string.larp_decoder_name_value),
            stringResource(R.string.larp_runs_on) to stringResource(R.string.larp_runs_on_value),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_dsp),
        lines = listOf(
            stringResource(R.string.larp_effects) to stringResource(R.string.larp_effects_value),
            stringResource(R.string.larp_hi_res) to stringResource(R.string.larp_hi_res_value),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_output),
        lines = listOf(
            stringResource(R.string.larp_device) to stringResource(R.string.larp_dac_name),
            stringResource(R.string.larp_route) to stringResource(R.string.larp_route_value),
            stringResource(R.string.larp_dac_mixer) to stringResource(R.string.larp_dac_mixer_value),
            stringResource(R.string.larp_power) to stringResource(R.string.larp_power_value),
            stringResource(R.string.larp_jitter) to stringResource(R.string.larp_jitter_value),
            stringResource(R.string.larp_buffer) to stringResource(R.string.larp_buffer_value),
        ),
    ),
)
