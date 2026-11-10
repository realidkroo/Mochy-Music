package com.mochyy.mochymusic.ui.player.studio

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.IpodShadowKey
import com.mochyy.mochymusic.constants.IpodTallScreenKey
import com.mochyy.mochymusic.constants.IpodTheme
import com.mochyy.mochymusic.constants.IpodThemeKey
import com.mochyy.mochymusic.constants.IpodWheelColor
import com.mochyy.mochymusic.constants.IpodWheelColorKey
import com.mochyy.mochymusic.ui.player.customize.CoverOption
import com.mochyy.mochymusic.ui.player.ipod.IpodLook
import com.mochyy.mochymusic.ui.player.ipod.lookOf
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

// the ipod's sheet: its body, its wheel, how tall its screen is and the shading round it
@Composable
internal fun ColumnScope.IpodOptions() {
    var theme by rememberEnumPreference(IpodThemeKey, IpodTheme.SILVER)
    var wheel by rememberEnumPreference(IpodWheelColorKey, IpodWheelColor.THEME)
    var tall by rememberPreference(IpodTallScreenKey, defaultValue = false)
    OptionSheet(title = stringResource(R.string.ps_ipod_title)) {
        IpodTheme.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = theme == option,
                onClick = { theme = option },
                caption = if (index == 0) stringResource(R.string.ps_ipod_body) else null,
                compact = true,
            ) {
                Swatch(lookOf(option, wheel, shadow = true, tall = false), stringResource(option.title), stringResource(option.description))
            }
        }
        IpodWheelColor.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = wheel == option,
                onClick = { wheel = option },
                caption = if (index == 0) stringResource(R.string.ps_ipod_click_wheel) else null,
                compact = true,
            ) {
                Swatch(lookOf(theme, option, shadow = true, tall = false), stringResource(option.title), stringResource(option.description))
            }
        }
        OptionRow(selected = !tall, onClick = { tall = false }, caption = stringResource(R.string.ps_ipod_screen), compact = true) {
            TwoLines(stringResource(R.string.ps_ipod_classic), stringResource(R.string.ps_ipod_classic_desc))
        }
        OptionRow(selected = tall, onClick = { tall = true }, compact = true) {
            TwoLines(stringResource(R.string.ps_ipod_tall), stringResource(R.string.ps_ipod_tall_desc))
        }
        SwitchRow(
            CoverOption.Switch(
                title = stringResource(R.string.ps_ipod_shadow),
                description = stringResource(R.string.ps_ipod_shadow_desc),
                icon = R.drawable.contrast,
                key = IpodShadowKey,
                default = true,
            ),
        )
    }
}

// a tiny ipod in the chosen colours next to the choice's name
@Composable
private fun Swatch(look: IpodLook, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 26.dp, height = 40.dp)) {
            val corner = CornerRadius(5.dp.toPx())
            drawRoundRect(look.body.brush, cornerRadius = corner)
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
            val glass = Size(size.width * 0.72f, size.height * 0.3f)
            drawRoundRect(look.screen.background, topLeft = Offset((size.width - glass.width) / 2f, size.height * 0.1f), size = glass, cornerRadius = CornerRadius(2.dp.toPx()))
            val wheelCenter = Offset(size.width / 2f, size.height * 0.7f)
            drawCircle(look.wheel.face, size.width * 0.3f, wheelCenter)
            drawCircle(look.wheel.center, size.width * 0.12f, wheelCenter)
        }
        Spacer(Modifier.width(14.dp))
        TwoLines(title, description)
    }
}

private val IpodTheme.title: Int
    @StringRes get() = when (this) {
        IpodTheme.SILVER -> R.string.ps_ipod_theme_silver
        IpodTheme.BLACK -> R.string.ps_ipod_theme_black
        IpodTheme.U2 -> R.string.ps_ipod_theme_u2
        IpodTheme.TRUE_DARK -> R.string.ps_ipod_theme_true_dark
    }

private val IpodTheme.description: Int
    @StringRes get() = when (this) {
        IpodTheme.SILVER -> R.string.ps_ipod_theme_silver_desc
        IpodTheme.BLACK -> R.string.ps_ipod_theme_black_desc
        IpodTheme.U2 -> R.string.ps_ipod_theme_u2_desc
        IpodTheme.TRUE_DARK -> R.string.ps_ipod_theme_true_dark_desc
    }

private val IpodWheelColor.title: Int
    @StringRes get() = when (this) {
        IpodWheelColor.THEME -> R.string.ps_ipod_wheel_theme
        IpodWheelColor.WHITE -> R.string.ps_ipod_wheel_white
        IpodWheelColor.BLACK -> R.string.ps_ipod_wheel_black
        IpodWheelColor.RED -> R.string.ps_ipod_wheel_red
    }

private val IpodWheelColor.description: Int
    @StringRes get() = when (this) {
        IpodWheelColor.THEME -> R.string.ps_ipod_wheel_theme_desc
        IpodWheelColor.WHITE -> R.string.ps_ipod_wheel_white_desc
        IpodWheelColor.BLACK -> R.string.ps_ipod_wheel_black_desc
        IpodWheelColor.RED -> R.string.ps_ipod_wheel_red_desc
    }
