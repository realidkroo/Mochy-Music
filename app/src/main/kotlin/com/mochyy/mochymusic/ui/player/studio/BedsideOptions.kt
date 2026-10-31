package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BedsideClockFace
import com.mochyy.mochymusic.constants.BedsideClockFaceKey
import com.mochyy.mochymusic.constants.BedsideKeepAwakeKey
import com.mochyy.mochymusic.constants.BedsideLockScreenKey
import com.mochyy.mochymusic.constants.BedsideNightTint
import com.mochyy.mochymusic.constants.BedsideNightTintKey
import com.mochyy.mochymusic.constants.BedsideScreenSaverKey
import com.mochyy.mochymusic.ui.player.bedside.BedsideOrange
import com.mochyy.mochymusic.ui.player.bedside.LockScreenSupported
import com.mochyy.mochymusic.ui.player.bedside.coverTint
import com.mochyy.mochymusic.ui.player.customize.CoverOption
import com.mochyy.mochymusic.utils.rememberEnumPreference

// the bedside's sheet: which clock, what it turns into when left alone, and how it treats the screen
@Composable
internal fun ColumnScope.BedsideOptions() {
    var face by rememberEnumPreference(BedsideClockFaceKey, BedsideClockFace.BIG)
    var tint by rememberEnumPreference(BedsideNightTintKey, BedsideNightTint.ORANGE)
    val seed = LocalArtworkColor.current
    val coverHue = remember(seed) { coverTint(seed) }
    OptionSheet(title = stringResource(R.string.bedside_sheet_title)) {
        BedsideClockFace.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = face == option,
                onClick = { face = option },
                caption = if (index == 0) stringResource(R.string.bedside_clock_caption) else null,
                compact = true,
            ) {
                TwoLines(stringResource(option.title), stringResource(option.caption))
            }
        }
        BedsideNightTint.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = tint == option,
                onClick = { tint = option },
                caption = if (index == 0) stringResource(R.string.bedside_rest_caption) else null,
                compact = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(18.dp)
                            .background(option.swatch(coverHue), CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    TwoLines(stringResource(option.title), stringResource(option.caption))
                }
            }
        }
        SwitchRow(
            CoverOption.Switch(
                title = stringResource(R.string.bedside_saver),
                description = stringResource(R.string.bedside_saver_caption),
                icon = R.drawable.contrast,
                key = BedsideScreenSaverKey,
                default = true,
            ),
        )
        SwitchRow(
            CoverOption.Switch(
                title = stringResource(R.string.bedside_keep_awake),
                description = stringResource(R.string.bedside_keep_awake_caption),
                icon = R.drawable.lock_open,
                key = BedsideKeepAwakeKey,
                default = true,
            ),
        )
        if (LockScreenSupported) {
            SwitchRow(
                CoverOption.Switch(
                    title = stringResource(R.string.bedside_lock_screen),
                    description = stringResource(R.string.bedside_lock_screen_caption),
                    icon = R.drawable.lock,
                    key = BedsideLockScreenKey,
                    default = true,
                ),
            )
        }
    }
}

private val BedsideClockFace.title: Int
    get() = when (this) {
        BedsideClockFace.BIG -> R.string.bedside_face_big
        BedsideClockFace.STACKED -> R.string.bedside_face_stacked
        BedsideClockFace.ANALOG -> R.string.bedside_face_analog
        BedsideClockFace.COVER -> R.string.bedside_face_cover
    }

private val BedsideClockFace.caption: Int
    get() = when (this) {
        BedsideClockFace.BIG -> R.string.bedside_face_big_caption
        BedsideClockFace.STACKED -> R.string.bedside_face_stacked_caption
        BedsideClockFace.ANALOG -> R.string.bedside_face_analog_caption
        BedsideClockFace.COVER -> R.string.bedside_face_cover_caption
    }

private val BedsideNightTint.title: Int
    get() = when (this) {
        BedsideNightTint.OFF -> R.string.bedside_tint_off
        BedsideNightTint.ORANGE -> R.string.bedside_tint_orange
        BedsideNightTint.COVER -> R.string.bedside_tint_cover
    }

private val BedsideNightTint.caption: Int
    get() = when (this) {
        BedsideNightTint.OFF -> R.string.bedside_tint_off_caption
        BedsideNightTint.ORANGE -> R.string.bedside_tint_orange_caption
        BedsideNightTint.COVER -> R.string.bedside_tint_cover_caption
    }

// a dot of the colour it settles into, the cover colour taken from the song playing now
private fun BedsideNightTint.swatch(coverHue: Color): Color = when (this) {
    BedsideNightTint.OFF -> Color.White
    BedsideNightTint.ORANGE -> BedsideOrange
    BedsideNightTint.COVER -> coverHue
}
