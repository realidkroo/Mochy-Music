// CanvasGifProgressRow.kt

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.discord.CanvasGifProgress
import kotlin.math.roundToInt

// progress of the gif that is being made right now, shown under the canvas options on the discord page
@Composable
fun CanvasGifProgressRow() {
    val now by CanvasGifProgress.state.collectAsState()
    val s = now
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp)) {
        if (s == null) {
            Text(
                text = stringResource(R.string.discord_canvas_idle),
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else {
            val pct = (s.fraction * 100).roundToInt()
            val head = when {
                s.failed -> stringResource(R.string.discord_canvas_failed, s.title)
                s.finished -> stringResource(R.string.discord_canvas_ready, s.title)
                else -> stringResource(R.string.discord_canvas_making, s.title)
            }
            Text(text = head, color = DiscordColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!s.failed) {
                LinearProgressIndicator(
                    progress = { s.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            // rough time left, only there once the guess is worth anything
            val eta = s.etaSec?.let {
                if (it < 60) stringResource(R.string.discord_canvas_eta_seconds, it)
                else stringResource(R.string.discord_canvas_eta_minutes, (it + 30) / 60)
            }
            val detail = when {
                s.failed -> stringResource(R.string.discord_canvas_failed_detail)
                s.finished -> stringResource(R.string.discord_canvas_ready_detail)
                else -> listOfNotNull(s.source, "$pct%", stringResource(s.stage.label), eta).joinToString(" · ")
            }
            Text(
                text = detail,
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
