package com.mochyy.mochymusic.ui.screens.gesture

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.AppFontMode
import com.mochyy.mochymusic.ui.theme.AppFonts
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.launch

internal const val FontHighlightKey = "Font"

@Composable
internal fun FontSection() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val selection = AppFonts.selection
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppFonts.Error?>(null) }

    fun perform(action: suspend () -> AppFonts.Error?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                error = action()
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform { AppFonts.importFont(context, uri) }
    }
    val openPicker = {
        try {
            // Some providers expose valid TTF documents as application/octet-stream.
            picker.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            error = AppFonts.Error.Storage
        }
    }

    val enabled = selection.ready && !busy
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.customise_font), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Default)
        Text(stringResource(R.string.customise_font_sample), style = MaterialTheme.typography.bodyLarge, fontFamily = InterFontFamily)

        Column(Modifier.selectableGroup()) {
            AppFontMode.entries.forEach { mode ->
                val title = when (mode) {
                    AppFontMode.DEFAULT -> R.string.customise_font_default
                    AppFontMode.SYSTEM -> R.string.customise_font_system
                    AppFontMode.CUSTOM -> R.string.customise_font_custom
                }
                val isSelected = selection.mode == mode
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) {
                        if (mode == AppFontMode.CUSTOM && selection.fileName == null) {
                            openPicker()
                        } else if (!isSelected) {
                            perform { AppFonts.select(context, mode) }
                        }
                    }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                    Text(stringResource(title), fontFamily = FontFamily.Default)
                }
            }
        }

        val fileName = selection.fileName
        if (fileName != null) {
            Text(fileName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Default)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = enabled, onClick = openPicker) {
                    Text(stringResource(R.string.customise_font_replace), fontFamily = FontFamily.Default)
                }
                TextButton(enabled = enabled, onClick = { perform { AppFonts.delete(context) } }) {
                    Text(stringResource(R.string.customise_font_remove), fontFamily = FontFamily.Default)
                }
            }
        } else {
            val help = if (selection.missing) R.string.customise_font_missing else R.string.customise_font_file_help
            Text(stringResource(help), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Default)
        }

        if (busy || !selection.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
        val message = when (error) {
            AppFonts.Error.InvalidFont -> R.string.customise_font_invalid
            AppFonts.Error.TooLarge -> R.string.customise_font_too_large
            AppFonts.Error.Storage -> R.string.customise_font_storage_error
            null -> null
        }
        if (message != null) {
            Text(
                stringResource(message),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Default,
            )
        }
    }
}
