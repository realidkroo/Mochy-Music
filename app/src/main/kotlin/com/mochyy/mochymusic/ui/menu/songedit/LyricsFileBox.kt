// LyricsFileBox.kt

package com.mochyy.mochymusic.ui.menu.songedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.songedit.LyricsFileReader
import com.mochyy.mochymusic.songedit.PickedLyrics
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.TopToaster
import kotlinx.coroutines.launch

// .lrc and .ttml files often come without a proper type, so the picker also lets plain binary files through
private val LyricsFileTypes = arrayOf("text/*", "application/octet-stream", "application/xml", "application/ttml+xml", "application/x-subrip")

// the dashed "add lyrics" box from the song edit sheet and the lyrics provider sheet; once a file is picked it shows it with a cross to drop it
@Composable
fun LyricsFileBox(
    picked: PickedLyrics?,
    onPicked: (PickedLyrics) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = LyricsFileReader.read(context, uri)
            if (read == null) {
                TopToaster.show(text = context.getString(R.string.song_edit_lyrics_unreadable), iconRes = R.drawable.lyrics)
            } else {
                onPicked(read)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = { picker.launch(LyricsFileTypes) },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (picked == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.song_edit_lyrics_add), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = stringResource(R.string.song_edit_lyrics_add_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        } else {
            SheetIcon(R.drawable.lyrics)
            Spacer(Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.song_edit_lyrics_lines, picked.lines, picked.fileName, picked.lines),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.song_edit_lyrics_remove),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove)
                    .padding(6.dp),
            )
        }
    }
}
