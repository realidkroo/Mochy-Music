package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private class LanguageOption(val tag: String, val nativeName: String, val englishName: String) {
    // "in" and "id" are both indonesian, so tags are compared the way locale writes them
    val normalized: String = Locale.forLanguageTag(tag).toLanguageTag()
}

internal fun appLanguageNativeName(tag: String): String {
    if (tag == "id" || tag == "in") return "Bahasa Indonesia"
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).ifBlank { tag }
}

// the app's own language, searchable by its own name, its english name or its code
fun PopupSheetState.showAppLanguageSheet(selected: String, onSelect: (String) -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        AppLanguagePicker(selected) { tag ->
            // picking a language can restart the screen, so the sheet is on its way out first
            sheet?.dismiss()
            onSelect(tag)
        }
    }
}

@Composable
private fun AppLanguagePicker(selected: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // every language named in its own words, worked out off the main thread so the sheet opens without a hitch
    val options by produceState(initialValue = emptyList<LanguageOption>()) {
        value = withContext(Dispatchers.Default) {
            context.resources.getStringArray(R.array.app_language_tags).map { tag ->
                LanguageOption(tag, appLanguageNativeName(tag), Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH))
            }.sortedBy { it.nativeName.lowercase(Locale.ROOT) }
        }
    }
    val chosen = remember(selected) { Locale.forLanguageTag(selected).toLanguageTag() }
    val systemDefault = stringResource(R.string.system_default)
    val followDevice = stringResource(R.string.app_language_follow_device)
    val needle = query.trim()
    val filtered = remember(options, needle) {
        options.filter {
            needle.isBlank() || it.nativeName.contains(needle, ignoreCase = true) ||
                it.englishName.contains(needle, ignoreCase = true) || it.tag.contains(needle, ignoreCase = true)
        }
    }
    val showSystem = needle.isBlank() || systemDefault.contains(needle, ignoreCase = true) ||
        followDevice.contains(needle, ignoreCase = true)
    // the list runs to the screen's bottom edge, so its last row clears the gesture bar
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetLabel(stringResource(R.string.app_language), Modifier.padding(start = 4.dp))
        Text(
            text = stringResource(R.string.app_language_incomplete),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
        )
        SheetSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.app_language_search))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 12.dp + navBottom),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (showSystem) {
                item(key = SYSTEM_DEFAULT, contentType = "language") {
                    SheetOptionRow(
                        title = systemDefault,
                        subtitle = followDevice,
                        onClick = { onPick(SYSTEM_DEFAULT) },
                        leading = { ChoiceMark(selected == SYSTEM_DEFAULT) },
                    )
                }
            }
            items(filtered, key = { it.tag }, contentType = { "language" }) { option ->
                SheetOptionRow(
                    title = option.nativeName,
                    subtitle = option.englishName.takeIf { !it.equals(option.nativeName, ignoreCase = true) },
                    onClick = { onPick(option.tag) },
                    leading = { ChoiceMark(selected != SYSTEM_DEFAULT && option.normalized == chosen) },
                )
            }
        }
    }
}
