// a module's options, the mock's "Options": its choices and its own settings as lists to pick from, lines to type in and switches

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private val RowShape = RoundedCornerShape(50)

@Composable
internal fun ModuleOptions(
    entry: ModuleEntry,
    onPick: (choice: String, option: String) -> Unit,
    onField: (field: String, value: String) -> Unit,
    titled: Boolean = true,
) {
    // a page's setting block shows only its one field, never the stream choices around it
    val choices = if (titled) entry.module.choices.filter { it.options.isNotEmpty() } else emptyList()
    if (choices.isEmpty() && entry.fields.isEmpty()) return
    if (titled) PageSectionTitle(stringResource(R.string.module_options))
    choices.forEach { choice ->
        // a pick the module no longer offers reads as its first option
        val picked = entry.picks[choice.id]?.takeIf { p -> choice.options.any { it.id == p } } ?: choice.options.first().id
        FieldLabel(choice.label)
        OptionList(choice.options, picked) { onPick(choice.id, it) }
        Spacer(Modifier.height(16.dp))
    }
    entry.fields.forEach { field ->
        val value = entry.values[field.id] ?: field.default
        when (field.kind) {
            FieldKind.Toggle -> ToggleField(field, value.equals("true", ignoreCase = true)) { onField(field.id, it.toString()) }
            FieldKind.Select -> {
                FieldLabel(field.label)
                OptionList(field.options, value.ifBlank { field.options.firstOrNull()?.id.orEmpty() }) { onField(field.id, it) }
            }
            FieldKind.Text -> {
                FieldLabel(field.label)
                TextField(field, value) { onField(field.id, it) }
            }
        }
        if (field.description.isNotBlank() && field.kind != FieldKind.Toggle) {
            Text(field.description, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText, modifier = Modifier.padding(top = 6.dp, start = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

/** the options as round rows under each other, the picked one lit, its description under the list */
@Composable
private fun OptionList(options: List<StreamOption>, picked: String, onPick: (String) -> Unit) {
    Column {
        options.forEach { option ->
            val lit = option.id == picked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .clip(RowShape)
                    .background(if (lit) Color.White else PageCardColor)
                    .clickable { onPick(option.id) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    option.label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (lit) Color.Black else Color.White, modifier = Modifier.weight(1f),
                )
                if (lit) Icon(painterResource(R.drawable.check), null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
        options.firstOrNull { it.id == picked }?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText, modifier = Modifier.padding(top = 2.dp, start = 6.dp))
        }
    }
}

@Composable
private fun ToggleField(field: ModuleField, on: Boolean, onChange: (Boolean) -> Unit) {
    PageCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!on) }) {
            Column(Modifier.weight(1f)) {
                Text(field.label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                if (field.description.isNotBlank()) {
                    Text(field.description, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = PageSoftText)
                }
            }
            Spacer(Modifier.width(12.dp))
            AppSwitch(checked = on, onCheckedChange = onChange)
        }
    }
}

/** a line to type in, kept a moment after typing stops and again when it loses focus, so every key isn't a save */
@OptIn(FlowPreview::class)
@Composable
private fun TextField(field: ModuleField, saved: String, onSave: (String) -> Unit) {
    var text by remember(field.id) { mutableStateOf(saved) }
    val save by rememberUpdatedState(onSave)
    val current by rememberUpdatedState(saved)
    val focus = LocalFocusManager.current
    LaunchedEffect(field.id) {
        snapshotFlow { text }.drop(1).debounce(600).collect { if (it != current) save(it) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(PageCardColor)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (text.isEmpty()) {
            Text(
                field.hint.ifBlank { stringResource(R.string.module_field_hint) },
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                save(text)
                focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text != current) save(text) },
        )
    }
}
