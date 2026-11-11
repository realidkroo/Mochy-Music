package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CrossfadeDurationKey
import com.mochyy.mochymusic.constants.CrossfadeEnabledKey
import com.mochyy.mochymusic.constants.CrossfadeGaplessKey
import com.mochyy.mochymusic.constants.CrossmixLength
import com.mochyy.mochymusic.constants.CrossmixLengthKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.CrossmixStuntsKey
import com.mochyy.mochymusic.constants.CrossmixStyleAuto
import com.mochyy.mochymusic.constants.CrossmixStyleKey
import com.mochyy.mochymusic.constants.CrossmixTempoMatch
import com.mochyy.mochymusic.constants.CrossmixTempoMatchKey
import com.mochyy.mochymusic.constants.CrossmixTestToolsKey
import com.mochyy.mochymusic.constants.HideSettingsDescriptionsKey
import com.mochyy.mochymusic.crossmix.CrossmixStyle
import com.mochyy.mochymusic.crossmix.StyleUse
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabCollapsible
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSegmented
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.screens.audiolab.ListenToAudio
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

private const val AutoCrossfadeDurationSeconds = 5f
private const val ManualMinSeconds = 1f
private const val ManualMaxSeconds = 20f
private val HeadingColor = Color.White.copy(alpha = 0.82f)

@Composable
fun CrossmixSettingsScreen(navController: NavController) {
    val (storedMode, onStoredModeChange) = rememberEnumPreference(
        CrossmixModeKey,
        defaultValue = CrossmixMode.AUTO_CROSSFADE,
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = AutoCrossfadeDurationSeconds,
    )
    val (disableForGapless, onDisableForGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )
    val (testTools, _) = rememberPreference(CrossmixTestToolsKey, defaultValue = false)
    val (hideDescriptions) = rememberPreference(HideSettingsDescriptionsKey, defaultValue = true)

    // people upgrading from the old crossfade switch land on the equivalent manual mode
    val selectedMode = if (storedMode == CrossmixMode.OFF && crossfadeEnabled) CrossmixMode.MANUAL_CROSSFADE else storedMode

    fun selectMode(mode: CrossmixMode) {
        onStoredModeChange(mode)
        onCrossfadeEnabledChange(mode != CrossmixMode.OFF)
        if (mode == CrossmixMode.AUTO_CROSSFADE) {
            onCrossfadeDurationChange(AutoCrossfadeDurationSeconds)
        }
    }

    val crossmixTools = crossmixTestItems(isVisible = testTools && selectedMode == CrossmixMode.CROSSMIX)
    val manualSeconds = crossfadeDuration.coerceIn(ManualMinSeconds, ManualMaxSeconds)

    // the crossmix picture glows with the music
    ListenToAudio()

    SubSettingsScaffold(
        title = stringResource(R.string.transition_title),
        backLabel = "Audio Lab",
        onBack = { navController.navigateUp() },
    ) {
        TransitionHeading(stringResource(R.string.transition_music_heading))
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.transition_off),
                subtitle = stringResource(R.string.transition_off_desc),
                checked = selectedMode == CrossmixMode.OFF,
                // turning "off" off brings back the everyday blend
                onCheckedChange = { selectMode(if (it) CrossmixMode.OFF else CrossmixMode.AUTO_CROSSFADE) },
            )
        }
        Spacer(Modifier.height(10.dp))

        TransitionModeGrid(selected = selectedMode) { mode, opened ->
            val selected = mode == selectedMode
            when (mode) {
                CrossmixMode.AUTO_CROSSFADE -> TransitionModeCard(
                    title = stringResource(R.string.transition_auto),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(mode) },
                    picture = { TransitionPicture(TransitionStyle.Auto, overlap = 0.3f, active = selected) },
                ) {
                    CardNote(stringResource(R.string.transition_auto_desc, AutoCrossfadeDurationSeconds.toInt()), hideDescriptions)
                }
                CrossmixMode.MANUAL_CROSSFADE -> TransitionModeCard(
                    title = stringResource(R.string.transition_manual),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(mode) },
                    picture = {
                        TransitionPicture(
                            TransitionStyle.Manual,
                            overlap = 0.08f + 0.42f * (manualSeconds - ManualMinSeconds) / (ManualMaxSeconds - ManualMinSeconds),
                            active = selected,
                        )
                    },
                ) {
                    CardNote(stringResource(R.string.transition_manual_desc), hideDescriptions)
                    ManualLength(manualSeconds) { onCrossfadeDurationChange(it) }
                }
                else -> TransitionModeCard(
                    title = stringResource(R.string.transition_crossmix),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(CrossmixMode.CROSSMIX) },
                    picture = { TransitionPicture(TransitionStyle.CrossMix, overlap = 0.42f, active = selected) },
                ) {
                    CardNote(stringResource(R.string.crossmix_desc), hideDescriptions)
                    CrossmixControls()
                    LabCollapsible(crossmixTools.any { it.isVisible }) {
                        Spacer(Modifier.height(8.dp))
                        SettingsGroup(style = SettingsGroupStyle.Grouped, items = crossmixTools)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        LabCollapsible(selectedMode != CrossmixMode.OFF) {
            LabCard(padding = 10.dp) {
                LabToggleRow(
                    title = "Keep gapless albums intact",
                    subtitle = "Album tracks that run straight into each other play through unmixed",
                    checked = disableForGapless,
                    onCheckedChange = onDisableForGaplessChange,
                )
            }
        }

        TransportFadeSection()
        Spacer(Modifier.height(120.dp))
    }
}

/** the soft heading over each group on the transition page */
@Composable
internal fun TransitionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        color = HeadingColor,
        modifier = modifier.padding(start = 4.dp, end = 16.dp, bottom = 10.dp),
    )
}

// the small line under an open card's picture, gone when descriptions are hidden
@Composable
private fun CardNote(text: String, hidden: Boolean) {
    if (hidden) return
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = LabColors.Secondary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
    )
}

// the manual blend's length, from a quick one-second cut to a long twenty-second wash
@Composable
private fun ManualLength(seconds: Float, onSecondsChange: (Float) -> Unit) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp)) {
        LabSlider(
            label = stringResource(R.string.transition_manual_length),
            valueText = stringResource(R.string.transition_seconds, seconds.roundToInt()),
            value = seconds,
            onValueChange = { onSecondsChange(it.roundToInt().toFloat()) },
            valueRange = ManualMinSeconds..ManualMaxSeconds,
            steps = (ManualMaxSeconds - ManualMinSeconds).toInt() - 1,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.transition_seconds, ManualMinSeconds.toInt()),
                fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.transition_seconds, ManualMaxSeconds.toInt()), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
        }
    }
}

// the styles a listener can pin, grouped the way the planner thinks of them: everyday moves, then lifts, falls and the flavours it never picks by itself
private val PinnableStyles: List<CrossmixStyle> =
    listOf(StyleUse.Everyday, StyleUse.Lift, StyleUse.Fall, StyleUse.Flavour).flatMap { use -> CrossmixStyle.entries.filter { it.use == use } }

/** the listener's say over a mix: which transition, how long, how far tempos bend to meet, and whether the stunts are in the pool */
@Composable
private fun CrossmixControls() {
    val (styleName, onStyleNameChange) = rememberPreference(CrossmixStyleKey, defaultValue = CrossmixStyleAuto)
    val (length, onLengthChange) = rememberEnumPreference(CrossmixLengthKey, defaultValue = CrossmixLength.MEDIUM)
    val (tempoMatch, onTempoMatchChange) = rememberEnumPreference(CrossmixTempoMatchKey, defaultValue = CrossmixTempoMatch.FULL)
    val (stunts, onStuntsChange) = rememberPreference(CrossmixStuntsKey, defaultValue = true)
    val pinned = PinnableStyles.indexOfFirst { it.name == styleName }

    Spacer(Modifier.height(16.dp))
    ControlHeading(stringResource(R.string.crossmix_style_title), stringResource(R.string.crossmix_style_desc))
    Spacer(Modifier.height(12.dp))
    LabSelect(
        options = listOf(stringResource(R.string.crossmix_style_auto)) + PinnableStyles.map { it.label },
        selectedIndex = pinned + 1,
        onSelect = { onStyleNameChange(if (it == 0) CrossmixStyleAuto else PinnableStyles[it - 1].name) },
        contentDescription = stringResource(R.string.crossmix_style_choose),
    )

    Spacer(Modifier.height(18.dp))
    ControlHeading(stringResource(R.string.crossmix_length_title), stringResource(R.string.crossmix_length_desc))
    Spacer(Modifier.height(12.dp))
    LabSegmented(
        options = listOf(
            stringResource(R.string.crossmix_length_short),
            stringResource(R.string.crossmix_length_medium),
            stringResource(R.string.crossmix_length_long),
        ),
        selectedIndex = length.ordinal,
        onSelect = { onLengthChange(CrossmixLength.entries[it]) },
        modifier = Modifier.padding(horizontal = 6.dp),
    )

    Spacer(Modifier.height(18.dp))
    ControlHeading(stringResource(R.string.crossmix_tempo_title), stringResource(R.string.crossmix_tempo_desc))
    Spacer(Modifier.height(12.dp))
    LabSegmented(
        options = listOf(
            stringResource(R.string.crossmix_tempo_off),
            stringResource(R.string.crossmix_tempo_gentle),
            stringResource(R.string.crossmix_tempo_full),
        ),
        selectedIndex = tempoMatch.ordinal,
        onSelect = { onTempoMatchChange(CrossmixTempoMatch.entries[it]) },
        modifier = Modifier.padding(horizontal = 6.dp),
    )

    Spacer(Modifier.height(10.dp))
    LabToggleRow(
        title = stringResource(R.string.crossmix_stunts_title),
        subtitle = stringResource(R.string.crossmix_stunts_desc),
        checked = stunts,
        onCheckedChange = onStuntsChange,
    )
}

@Composable
private fun ControlHeading(title: String, detail: String) {
    Column(Modifier.padding(horizontal = 6.dp)) {
        Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LabColors.Primary)
        Text(detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 15.sp, color = LabColors.Secondary)
    }
}
