// the sheet a preset is named on, in the equalizer and audio fx alike: the usual header, the name field and ok, and a bin while editing

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetHeader
import com.mochyy.mochymusic.ui.component.SheetHeaderButton
import com.mochyy.mochymusic.ui.component.SheetHeaderGap
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.library.showConfirm
import com.mochyy.mochymusic.ui.theme.InterFontFamily

/** which page a preset belongs to: its name under the sheet's title, its icon on the tile and an example name for the field */
internal class PresetPlace(@StringRes val name: Int, @DrawableRes val icon: Int, @StringRes val example: Int)

internal val EqPresetPlace = PresetPlace(R.string.eqx_title, R.drawable.equalizer, R.string.eqx_save_preset_hint)
internal val FxPresetPlace = PresetPlace(R.string.fx_presets_place, R.drawable.tune, R.string.fx_preset_hint)

// long enough for a real name, short enough that its chip still fits on a phone
private const val MaxNameLength = 32

private val TakenColor = Color(0xFFFF8A80)

/** names a new preset, onSave gets the name already trimmed */
internal fun PopupSheetState.showNewPresetSheet(place: PresetPlace, takenNames: List<String>, onSave: (String) -> Unit) =
    showPresetSheet(place, name = "", takenNames, onSave, onDelete = null)

/** renames one of your presets, its bin asks once more before the preset goes */
internal fun PopupSheetState.showEditPresetSheet(
    place: PresetPlace,
    name: String,
    takenNames: List<String>,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) = showPresetSheet(place, name, takenNames, onRename, onDelete)

private fun PopupSheetState.showPresetSheet(
    place: PresetPlace,
    name: String,
    takenNames: List<String>,
    onDone: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val context = LocalContext.current
        PresetSheetContent(
            place = place,
            name = name,
            takenNames = takenNames,
            onDone = { picked ->
                onDone(picked)
                sheet?.dismiss()
            },
            onDelete = onDelete?.let { delete ->
                {
                    // pushed over this one, so cancelling comes straight back to the name
                    this@showPresetSheet.showConfirm(
                        title = context.getString(R.string.eqx_delete_title, name),
                        message = context.getString(R.string.preset_delete_body),
                        action = context.getString(R.string.delete),
                    ) {
                        delete()
                        sheet?.dismiss()
                    }
                }
            },
        )
    }
}

@Composable
private fun PresetSheetContent(
    place: PresetPlace,
    name: String,
    takenNames: List<String>,
    onDone: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    // the cursor starts at the end, so a rename picks up where the name leaves off
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    val typed = field.text.trim()
    // keeping its own name while editing is fine, wearing another preset's isn't
    val taken = typed.isNotEmpty() && !typed.equals(name.trim(), ignoreCase = true) &&
        takenNames.any { it.trim().equals(typed, ignoreCase = true) }
    val canSave = typed.isNotEmpty() && !taken
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        if (!canSave) return
        keyboard?.hide()
        onDone(typed)
    }

    // the keyboard comes up with the sheet, so the name can be typed straight away
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    // the nav bar gap only counts while the keyboard is down, the sheet already sits above it when it's up
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        val deleteLabel = stringResource(R.string.preset_sheet_delete)
        SheetHeader(
            icon = place.icon,
            title = stringResource(if (onDelete != null) R.string.preset_sheet_edit else R.string.preset_sheet_new),
            subtitle = stringResource(place.name),
            trailing = if (onDelete != null) {
                { SheetHeaderButton(R.drawable.delete, onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteLabel }) }
            } else null,
        )
        Spacer(Modifier.height(SheetHeaderGap))
        SheetLabel(stringResource(R.string.preset_sheet_name), Modifier.padding(start = 4.dp, bottom = 10.dp))
        SheetTextField(
            value = field,
            onValueChange = { if (it.text.length <= MaxNameLength) field = it },
            placeholder = stringResource(place.example),
            modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        if (taken) {
            Text(
                stringResource(R.string.preset_sheet_taken),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TakenColor,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        SheetPillButton(text = stringResource(R.string.preset_sheet_ok), onClick = { save() }, enabled = canSave)
    }
}
