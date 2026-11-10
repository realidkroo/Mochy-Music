package com.mochyy.mochymusic.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.PlaybackLogEntry
import com.mochyy.mochymusic.utils.PlaybackLogLevel
import com.mochyy.mochymusic.utils.PlaybackLogManager
import com.mochyy.mochymusic.utils.YTPlayerUtils

/** newest entries first, capped so the scrolling column stays responsive */
private const val VISIBLE_ENTRIES = 200

@Composable
fun PlaybackDiagnosticsScreen(navController: NavController) {
    val context = LocalContext.current
    val logs by PlaybackLogManager.logs.collectAsStateWithLifecycle()

    val recent = remember(logs) { logs.asReversed().take(VISIBLE_ENTRIES) }

    val lastValidated = remember(logs) { logs.lastOrNull { it.message == "Stream validated" } }
    val hasPoToken = YTPlayerUtils.hasPoToken

    SubSettingsScaffold(
        title = stringResource(R.string.playback_diagnostics_title),
        onBack = { navController.navigateUp() },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text =
                        if (hasPoToken) {
                            stringResource(
                                R.string.playback_potoken_ready,
                            )
                        } else {
                            stringResource(R.string.playback_no_potoken)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (hasPoToken) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )

                Spacer(Modifier.height(8.dp))

                DiagnosticsRow(stringResource(R.string.playback_last_stream), lastValidated?.details ?: "—")
            }
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.playback_copy_logs_title)) },
                        descriptionText = stringResource(R.string.playback_copy_logs_desc),
                        icon = painterResource(R.drawable.key),
                        iconShape = CircleShape,
                        onClick = {
                            copyLogs(context, logs)
                            Toast.makeText(context, context.getString(R.string.playback_logs_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.playback_clear_logs_title)) },
                        descriptionText = stringResource(R.string.playback_clear_logs_desc),
                        icon = painterResource(R.drawable.delete),
                        iconShape = CircleShape,
                        onClick = { PlaybackLogManager.clearLogs() },
                    ),
                ),
        )

        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.playback_logs_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        recent.forEach { entry ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${entry.timestamp}  ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = entry.level.tint(),
                )
                entry.details?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlaybackLogLevel.tint(): Color =
    when (this) {
        PlaybackLogLevel.ERROR -> MaterialTheme.colorScheme.error
        PlaybackLogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        PlaybackLogLevel.INFO -> MaterialTheme.colorScheme.primary
        PlaybackLogLevel.BOT -> MaterialTheme.colorScheme.secondary
        PlaybackLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun DiagnosticsRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun copyLogs(
    context: Context,
    logs: List<PlaybackLogEntry>,
) {
    val text =
        logs.joinToString("\n") { entry ->
            buildString {
                append(entry.timestamp)
                append(' ')
                append(entry.level.name)
                append(' ')
                append(entry.message)
                entry.details?.let {
                    append(" | ")
                    append(it)
                }
            }
        }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("mochy playback log", text))
}
