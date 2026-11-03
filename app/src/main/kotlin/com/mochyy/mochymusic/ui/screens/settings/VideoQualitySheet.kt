package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.utils.stableSystemBars

// how a size reads: "720p", and 4k by the name people know it by
@Composable
internal fun videoSizeName(shortSide: Int): String =
    if (shortSide >= 2160) stringResource(R.string.video_quality_4k)
    else stringResource(R.string.video_quality_size, shortSide)

// how a quality reads in settings: "Auto", or the size itself
@Composable
internal fun videoQualityName(quality: VideoQuality): String =
    if (quality == VideoQuality.AUTO) stringResource(R.string.video_quality_auto)
    else videoSizeName(quality.shortSide)

// picks how sharp youtube videos play, everywhere they show
fun PopupSheetState.showVideoQualitySheet(selected: VideoQuality, onSelect: (VideoQuality) -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        VideoQualityPicker(selected) { picked ->
            onSelect(picked)
            sheet?.dismiss()
        }
    }
}

@Composable
private fun VideoQualityPicker(selected: VideoQuality, onPick: (VideoQuality) -> Unit) {
    // the list reaches the screen's bottom edge, so its last row clears the gesture bar
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp + navBottom),
    ) {
        SheetLabel(stringResource(R.string.video_quality), Modifier.padding(start = 4.dp, bottom = 4.dp))
        for (quality in VideoQuality.entries) {
            SheetOptionRow(
                title = videoQualityName(quality),
                subtitle = when {
                    quality == VideoQuality.AUTO -> stringResource(R.string.video_quality_auto_desc)
                    quality.shortSide > 1080 -> stringResource(R.string.video_quality_sharp_desc)
                    else -> null
                },
                subtitleLines = 2,
                onClick = { onPick(quality) },
                leading = { ChoiceMark(quality == selected) },
            )
        }
    }
}

// the radio mark a pick-one sheet's rows start with
@Composable
internal fun ChoiceMark(selected: Boolean) {
    SheetIcon(
        icon = if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
        tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
    )
}
