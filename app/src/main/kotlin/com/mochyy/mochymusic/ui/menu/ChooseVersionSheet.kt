// ChooseVersionSheet.kt
//
// "Choose different version" on an imported song: the versions a search turns up for the track it
// was imported as, with how long each runs and where it's from, so a live or sped-up take is easy
// to tell from the studio one. The pick is saved for that track, so later imports use it too.

package com.mochyy.mochymusic.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.ImportedVersions
import com.mochyy.mochymusic.importer.TrackMatch
import com.mochyy.mochymusic.importer.TrackMatcher
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.library.LibraryGridArtwork
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.utils.makeTimeString
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Opens over the song menu; [onChosen] closes that menu too once a version is picked. */
fun PopupSheetState.showChooseVersionSheet(match: TrackMatch, onChosen: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        ChooseVersionContent(match) {
            sheet?.dismiss()
            onChosen()
        }
    }
}

@Composable
private fun ColumnScope.ChooseVersionContent(match: TrackMatch, onDone: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val candidates by produceState<List<TrackMatcher.Candidate>?>(initialValue = null, match) {
        value = runCatching { ImportedVersions.candidates(match) }.getOrDefault(emptyList())
    }
    val sourceSeconds = match.durationMs?.takeIf { it > 0 }?.div(1000.0)

    Text(
        text = stringResource(R.string.pc_version_title),
        color = SearchColors.Primary,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )
    Text(
        text = buildString {
            append("${match.title} · ${match.artist}")
            val label = ImportSource.fromKey(match.source)?.takeIf { it.isListed }?.label
            if (sourceSeconds != null && label != null) {
                append(" · ${stringResource(R.string.pc_version_on, makeTimeString(match.durationMs), label)}")
            }
        },
        color = SearchColors.Secondary,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(14.dp))

    val list = candidates
    val message = when {
        list == null -> stringResource(R.string.pc_version_looking)
        list.isEmpty() -> stringResource(R.string.pc_version_none)
        else -> null
    }
    if (message != null || list == null) {
        Text(
            text = message.orEmpty(),
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(list, key = { it.song.id }) { candidate ->
            val song = candidate.song
            val isCurrent = song.id == match.songId
            VersionRow(song = song, sourceSeconds = sourceSeconds, isCurrent = isCurrent) {
                ImportedVersions.scope.launch {
                    ImportedVersions.choose(context.applicationContext, database, match, song)
                    TopToaster.show(
                        text = context.getString(R.string.pc_version_switched, song.title),
                        thumbnail = song.thumbnail,
                        iconRes = R.drawable.sync,
                    )
                }
                onDone()
            }
        }
    }
}

@Composable
private fun VersionRow(song: SongItem, sourceSeconds: Double?, isCurrent: Boolean, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (isCurrent) Modifier.background(MenuRowSurface) else Modifier)
            .clickable(enabled = !isCurrent, role = Role.Button, onClick = onPick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        LibraryGridArtwork(url = song.thumbnail, corner = 12.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = SearchColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    song.artists.joinToString { it.name }.ifBlank { null },
                    uploadKind(song),
                    song.album?.name,
                ).joinToString(" · "),
                color = SearchColors.Secondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = song.duration?.let { makeTimeString(it * 1000L) } ?: "–:––",
                color = SearchColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isCurrent) stringResource(R.string.pc_in_library) else lengthHint(song.duration, sourceSeconds).orEmpty(),
                color = SearchColors.Secondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** Where a result comes from, in the words people know: the artist's own audio, a video, someone's upload. */
@Composable
private fun uploadKind(song: SongItem): String = when (song.musicVideoType) {
    MUSIC_VIDEO_TYPE_ATV, null -> stringResource(R.string.pc_version_song)
    MUSIC_VIDEO_TYPE_OMV -> stringResource(R.string.pc_version_video)
    else -> stringResource(R.string.pc_version_upload)
}

/** How far a result's length is from the track's: a live or sped-up take shows up here at a glance. */
@Composable
private fun lengthHint(seconds: Int?, sourceSeconds: Double?): String? {
    if (seconds == null || sourceSeconds == null) return null
    val diff = (seconds - sourceSeconds).roundToInt()
    return when {
        abs(diff) <= TrackMatcher.CLOSE_SECONDS -> stringResource(R.string.pc_version_same_length)
        diff > 0 -> stringResource(R.string.pc_version_longer, diff)
        else -> stringResource(R.string.pc_version_shorter, -diff)
    }
}
