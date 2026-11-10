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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
fun OnDeviceMarks(songId: String?, modifier: Modifier = Modifier) {
    if (songId == null || !rememberOnDevice(songId)) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        if (isImportedFile(songId)) {
            rememberFileFormat(songId)?.let { StreamFormatBadge(text = it, height = 15.dp) }
        }
        OnDeviceIcon()
    }
}

/** A grey disc with a dark down arrow: on this device. */
@Composable
fun OnDeviceIcon(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(17.dp)
            .background(Color.White.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_downward),
            contentDescription = stringResource(R.string.pc_on_this_device),
            tint = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
        )
    }
}
