package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel

private const val UNMATCHED_SHOWN = 200

@Composable
fun ImportProgressScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.importProgress.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun leave() {
        if (!navController.popBackStack(ImportSyncRoute, inclusive = false)) navController.navigateUp()
    }

    val resync = progress.resync
    SubSettingsScaffold(
        title =
            when {
                resync != null -> stringResource(R.string.import_progress_title_resync)
                progress.isRunning -> stringResource(R.string.import_progress_title_importing)
                else -> stringResource(R.string.import_sync_import_title)
            },
        onBack = { navController.navigateUp() },
    ) {
        ImportInfoCard {
            val fraction = if (progress.totalTracks > 0) progress.processedTracks.toFloat() / progress.totalTracks else 0f
            Text(
                text =
                    when {
                        progress.isRunning -> {
                            stringResource(R.string.import_progress_songs_count, progress.processedTracks, progress.totalTracks)
                        }

                        progress.cancelled -> {
                            stringResource(R.string.import_progress_status_stopped)
                        }

                        progress.error != null -> {
                            if (resync !=
                                null
                            ) {
                                stringResource(R.string.import_progress_status_resync_failed)
                            } else {
                                stringResource(R.string.import_sync_progress_failed)
                            }
                        }

                        progress.isDone -> {
                            stringResource(R.string.import_progress_status_done)
                        }

                        else -> {
                            stringResource(R.string.import_progress_status_no_import)
                        }
                    },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (progress.isRunning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    text = progress.currentLabel.ifEmpty { stringResource(R.string.import_progress_starting) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.import_progress_background_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (progress.isDone || progress.processedTracks > 0) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryLine(
                        stringResource(R.string.import_progress_found_ytm),
                        stringResource(R.string.import_progress_fraction, progress.matchedTracks, progress.totalTracks),
                    )
                    if (resync != null &&
                        progress.isDone
                    ) {
                        SummaryLine(stringResource(R.string.import_progress_added_to, resync.playlistName), resync.added.toString())
                    }
                    if (progress.playlistsCreated >
                        0
                    ) {
                        SummaryLine(stringResource(R.string.import_progress_new_playlists), progress.playlistsCreated.toString())
                    }
                    if (progress.likedAdded >
                        0
                    ) {
                        SummaryLine(stringResource(R.string.import_progress_songs_liked), progress.likedAdded.toString())
                    }
                    if (progress.unmatched.isNotEmpty()) {
                        SummaryLine(
                            stringResource(R.string.import_progress_not_found),
                            progress.unmatched.size.toString(),
                        )
                    }
                }
            }
            progress.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (progress.youtubeFailures.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.import_progress_youtube_failures, progress.youtubeFailures.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                buildList {
                    if (progress.isRunning) {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.import_progress_stop_import)) },
                                descriptionText = stringResource(R.string.import_progress_stop_import_desc),
                                icon = painterResource(R.drawable.close),
                                iconShape = CircleShape,
                                onClick = viewModel::cancelImport,
                            ),
                        )
                    } else {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.import_progress_done_btn)) },
                                icon = painterResource(R.drawable.check),
                                iconShape = CircleShape,
                                onClick = {
                                    viewModel.clearFinishedImport()
                                    leave()
                                },
                            ),
                        )
                    }
                    if (progress.unmatched.isNotEmpty()) {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.import_progress_copy_not_found)) },
                                descriptionText = stringResource(R.string.import_progress_copy_not_found_desc),
                                icon = painterResource(R.drawable.share),
                                iconShape = CircleShape,
                                onClick = {
                                    val text =
                                        progress.unmatched.joinToString("\n") { miss ->
                                            buildString {
                                                append(miss.track.title)
                                                if (miss.track.artist.isNotEmpty()) append(" - ").append(miss.track.artist)
                                                miss.playlistName?.let { append("  [").append(it).append(']') }
                                            }
                                        }
                                    clipboard.setText(AnnotatedString(text))
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.import_progress_copied_toast, progress.unmatched.size),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ),
                        )
                    }
                },
        )

        // listed once finished: re-laying out hundreds of rows on every progress tick is wasted work
        if (progress.unmatched.isNotEmpty() && !progress.isRunning) {
            val fallbackName = stringResource(R.string.import_progress_liked_songs_fallback)
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                title = stringResource(R.string.import_progress_not_found),
                style = SettingsGroupStyle.Grouped,
                items =
                    progress.unmatched.take(UNMATCHED_SHOWN).map { miss ->
                        SettingsItem(
                            title = { Text(miss.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            descriptionText =
                                listOfNotNull(miss.track.artist.ifEmpty { null }, miss.playlistName ?: fallbackName)
                                    .joinToString(" · "),
                        )
                    },
            )
            if (progress.unmatched.size > UNMATCHED_SHOWN) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_progress_unmatched_more, progress.unmatched.size - UNMATCHED_SHOWN),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    androidx.compose.foundation.layout.Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
