// OnDeviceMarks.kt
//
// What a song row shows when the song plays from this device: a grey disc with a down arrow, as
// Apple Music marks its downloads, and for a file imported from the device its format ("FLAC",
// "MP3") in the player's badge.

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDownloadUtil
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.ui.player.StreamFormatBadge
import kotlinx.coroutines.flow.map

/** Imported files have ids of their own; everything else is a YouTube video id. */
fun isImportedFile(songId: String): Boolean = songId.startsWith("LOCAL_")

/**
 * Whether the song with [songId] plays from this device: a file imported from it, or a finished
 * download. Follows the download, so the mark comes and goes as it finishes or is removed.
 */
@Composable
fun rememberOnDevice(songId: String?): Boolean {
    if (songId == null) return false
    if (isImportedFile(songId)) return true
    val downloadUtil = LocalDownloadUtil.current
    val downloaded by remember(songId) {
        downloadUtil.downloads.map { it[songId]?.state == Download.STATE_COMPLETED }
    }.collectAsState(initial = downloadUtil.downloads.value[songId]?.state == Download.STATE_COMPLETED)
    return downloaded
}

/** An imported file's format, as the player's badge names it: "FLAC", "MP3", "AAC"... */
@Composable
private fun rememberFileFormat(songId: String): String? {
    val database = LocalDatabase.current
    val format by remember(songId) {
        database.format(songId).map { it?.let { f -> StreamFacts.codecOf(f.mimeType, f.codecs) } }
    }.collectAsState(initial = null)
    return format
}

/** The marks for [songId], or nothing when it doesn't play from this device. */
@Composable
fun OnDeviceMarks(songId: String?, modifier: Modifier = Modifier, iconSize: Dp = 17.dp, badgeHeight: Dp = 15.dp) {
    if (songId == null || !rememberOnDevice(songId)) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier,
    ) {
        if (isImportedFile(songId)) {
            rememberFileFormat(songId)?.let { StreamFormatBadge(text = it, height = badgeHeight) }
        }
        OnDeviceIcon(size = iconSize)
    }
}

// a song's title with its on-device marks small right after it; a long title is cut short so the marks always show
@Composable
fun TitleWithMarks(
    text: String,
    songId: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        OnDeviceMarks(songId, iconSize = 14.dp, badgeHeight = 13.dp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A grey disc with a dark down arrow: on this device. */
@Composable
fun OnDeviceIcon(modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_downward),
            contentDescription = stringResource(R.string.pc_on_this_device),
            tint = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.size(size * 0.7f),
        )
    }
}
