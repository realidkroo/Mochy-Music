// AboutPreview.kt

package com.mochyy.mochymusic.ui.player.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AboutSongSourceKey
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.component.RemoteFocus

private const val PreviewLines = 4
private val PreviewLineHeight = 20.sp

/** the first few lines of "about song" under the song on its first sheet; a tap opens the whole sheet */
@Composable
fun AboutPreviewCard(about: SongAbout, onOpen: () -> Unit) {
    val (sourceId, _) = rememberPreference(AboutSongSourceKey, AboutSource.Musixmatch.id)
    val picked = AboutSource.entries.firstOrNull { it.id == sourceId } ?: AboutSource.Musixmatch
    // the picked source while it loads or when it has something, otherwise the first one that does, so the card is rarely empty
    val source = if (!doneFor(about, picked) || textFor(about, picked) != null) picked
    else AboutSource.entries.firstOrNull { textFor(about, it) != null } ?: picked
    // always four lines tall, so the sheet (as tall as what's in it) doesn't jump when the text arrives
    val textHeight = with(LocalDensity.current) { (PreviewLineHeight * PreviewLines).toDp() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = null, indication = RemoteFocus, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(textHeight)) {
            AnimatedContent(
                targetState = Triple(source, doneFor(about, source), textFor(about, source)),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "aboutPreview",
            ) { (shownSource, shownDone, shownText) ->
                when {
                    !shownDone -> Column {
                        LoadingBar(width = 220.dp)
                        Spacer(Modifier.height(10.dp))
                        LoadingBar(width = 180.dp)
                        Spacer(Modifier.height(10.dp))
                        LoadingBar(width = 200.dp)
                    }
                    shownText != null -> Text(
                        text = shownText,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = PreviewLineHeight,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = PreviewLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    else -> Text(
                        text = stringResource(R.string.about_song_nothing_yet, shownSource.label),
                        color = InfoMuted,
                        fontSize = 14.sp,
                        lineHeight = PreviewLineHeight,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_song_provided_by, source.label.lowercase()),
            color = InfoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
