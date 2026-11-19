package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.layout.Column
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
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CanvasBestQualityKey
import com.mochyy.mochymusic.ui.player.video.QualityRow
import com.mochyy.mochymusic.ui.screens.settings.videoSizeName
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference

// what the animated cover row reads as: "Best · 4K", the pick and the size really playing
@Composable
internal fun canvasQualityLabel(best: Boolean, playingSide: Int): String {
    val pick = stringResource(if (best) R.string.canvas_quality_best else R.string.canvas_quality_light)
    return if (playingSide > 0) stringResource(R.string.canvas_quality_now, pick, videoSizeName(playingSide)) else pick
}

// the animated cover's two picks, the same switch as the one in settings
@Composable
internal fun CanvasQualitySheet(onDismiss: () -> Unit) {
    val (best, setBest) = rememberPreference(CanvasBestQualityKey, defaultValue = true)
    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val close = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.canvas_quality),
                color = Color.White,
                fontFamily = InterFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            QualityRow(
                title = stringResource(R.string.canvas_quality_best),
                description = stringResource(R.string.canvas_quality_best_desc),
                selected = best,
                onClick = {
                    setBest(true)
                    close()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            QualityRow(
                title = stringResource(R.string.canvas_quality_light),
                description = stringResource(R.string.canvas_quality_light_desc),
                selected = !best,
                onClick = {
                    setBest(false)
                    close()
                },
            )
        }
    }
}
