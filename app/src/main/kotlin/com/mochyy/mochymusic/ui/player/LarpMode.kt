// LarpMode.kt

package com.mochyy.mochymusic.ui.player

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LarpLevelKey
import com.mochyy.mochymusic.constants.LarpModeKey
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.ui.player.info.StatCard
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import java.util.Locale

// how hard the player larps, from a setup a real iem nerd could own up to the wadax; every level has its own numbers and gear
enum class LarpLevel(
    @StringRes val title: Int,
    val bitDepth: Int,
    val sampleRateKhz: Int,
    @StringRes val dac: Int,
    @StringRes val lead: Int,
    @StringRes val power: Int,
    @StringRes val channels: Int,
    @StringRes val source: Int,
    @StringRes val decoder: Int,
    @StringRes val runsOn: Int,
    @StringRes val effects: Int,
    @StringRes val hiRes: Int,
    @StringRes val route: Int,
    @StringRes val jitter: Int,
    @StringRes val buffer: Int,
) {
    SKIBIDI(
        R.string.larp_skibidi_title, 24, 96, R.string.larp_skibidi_dac, R.string.larp_skibidi_lead, R.string.larp_skibidi_power,
        R.string.larp_skibidi_channels, R.string.larp_skibidi_source, R.string.larp_skibidi_decoder, R.string.larp_skibidi_runs,
        R.string.larp_skibidi_effects, R.string.larp_skibidi_hires, R.string.larp_skibidi_route, R.string.larp_skibidi_jitter,
        R.string.larp_skibidi_buffer,
    ),
    SIXTY_SEVEN(
        R.string.larp_sixty_seven_title, 24, 192, R.string.larp_sixty_seven_dac, R.string.larp_sixty_seven_lead, R.string.larp_sixty_seven_power,
        R.string.larp_sixty_seven_channels, R.string.larp_sixty_seven_source, R.string.larp_sixty_seven_decoder, R.string.larp_sixty_seven_runs,
        R.string.larp_sixty_seven_effects, R.string.larp_sixty_seven_hires, R.string.larp_sixty_seven_route, R.string.larp_sixty_seven_jitter,
        R.string.larp_sixty_seven_buffer,
    ),
    BRAINROT(
        R.string.larp_brainrot_title, 32, 384, R.string.larp_brainrot_dac, R.string.larp_brainrot_lead, R.string.larp_brainrot_power,
        R.string.larp_brainrot_channels, R.string.larp_brainrot_source, R.string.larp_brainrot_decoder, R.string.larp_brainrot_runs,
        R.string.larp_brainrot_effects, R.string.larp_brainrot_hires, R.string.larp_brainrot_route, R.string.larp_brainrot_jitter,
        R.string.larp_brainrot_buffer,
    ),
    MAXXING(
        R.string.larp_maxxing_title, 32, 768, R.string.larp_maxxing_dac, R.string.larp_maxxing_lead, R.string.larp_maxxing_power,
        R.string.larp_maxxing_channels, R.string.larp_maxxing_source, R.string.larp_maxxing_decoder, R.string.larp_maxxing_runs,
        R.string.larp_maxxing_effects, R.string.larp_maxxing_hires, R.string.larp_maxxing_route, R.string.larp_maxxing_jitter,
        R.string.larp_maxxing_buffer,
    ),
    ;

    // what flac at this depth and rate really takes, two channels of it
    val bitrateKbps: Int get() = bitDepth * sampleRateKhz * 2
}

// the level the player is larping at, or null while larp mode is off; settings > app behaviour flips it, behind the 67 sheet
@Composable
fun rememberLarpMode(): LarpLevel? {
    val (larping) = rememberPreference(LarpModeKey, defaultValue = false)
    // the max, the only level there was before levels, so nobody's larp gets quieter
    val (level) = rememberEnumPreference(LarpLevelKey, defaultValue = LarpLevel.MAXXING)
    return if (larping) level else null
}

// the badge while larping, shown even with the badge switched off, or what's the point
internal fun larpBadgeText(level: LarpLevel): String? =
    streamBadgeText(StreamFacts(codec = "FLAC", sampleRate = level.sampleRateKhz * 1000), showSampleRate = true)

@Composable
internal fun larpQualityText(level: LarpLevel): String =
    stringResource(R.string.larp_quality, level.bitDepth, level.sampleRateKhz)

// the output row's second line: how the dac is fed, its rate and depth, and its power
@Composable
internal fun larpDacDetail(level: LarpLevel): String =
    stringResource(R.string.larp_dac_detail, stringResource(level.lead), level.sampleRateKhz, level.bitDepth, stringResource(level.power))

// stats for nerds, larp edition: at the top level no decoder, no detours, a million watts of pure dopamine
@Composable
internal fun larpStatCards(level: LarpLevel): List<StatCard> = listOf(
    StatCard(
        title = stringResource(R.string.larp_card_format),
        lines = listOf(
            stringResource(R.string.larp_bit_depth) to stringResource(R.string.larp_bit_depth_value, level.bitDepth),
            stringResource(R.string.larp_sample_rate) to stringResource(R.string.larp_sample_rate_value, level.sampleRateKhz),
            stringResource(R.string.larp_bitrate) to
                stringResource(R.string.larp_bitrate_value, String.format(Locale.US, "%,d", level.bitrateKbps)),
            stringResource(R.string.larp_channels) to stringResource(level.channels),
            stringResource(R.string.larp_source) to stringResource(level.source),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_decoder),
        lines = listOf(
            stringResource(R.string.larp_decoder_name) to stringResource(level.decoder),
            stringResource(R.string.larp_runs_on) to stringResource(level.runsOn),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_dsp),
        lines = listOf(
            stringResource(R.string.larp_effects) to stringResource(level.effects),
            stringResource(R.string.larp_hi_res) to stringResource(level.hiRes),
        ),
    ),
    StatCard(
        title = stringResource(R.string.larp_card_output),
        lines = listOf(
            stringResource(R.string.larp_device) to stringResource(level.dac),
            stringResource(R.string.larp_route) to stringResource(level.route),
            stringResource(R.string.larp_dac_mixer) to stringResource(R.string.larp_dac_mixer_value, level.sampleRateKhz, level.bitDepth),
            stringResource(R.string.larp_power) to stringResource(level.power),
            stringResource(R.string.larp_jitter) to stringResource(level.jitter),
            stringResource(R.string.larp_buffer) to stringResource(level.buffer),
        ),
    ),
)
