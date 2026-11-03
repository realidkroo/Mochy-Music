// adding to a module's own list, the things people bring it: the .zip they came in, or a link to a zip or a single file

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.ItemProblem
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.mochyy.mochymusic.viewmodels.ModulesViewModel.ItemsOutcome
import kotlinx.coroutines.launch

/** opens the sheet for module [id], called [name]; it closes itself once something is in, with a toast saying what */
fun PopupSheetState.showAddItemsSheet(id: String, name: String, viewModel: ModulesViewModel) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        AddItemsContent(id, name, viewModel, onAdded = { sheet?.dismiss() })
    }
}

@Composable
private fun AddItemsContent(id: String, name: String, viewModel: ModulesViewModel, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var link by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // which of the two, the file or the link, the last try came from: its button says adding, its problem shows under it
    var problemFromFile by remember { mutableStateOf(false) }

    fun show(outcome: ItemsOutcome) {
        val message = when (outcome) {
            is ItemsOutcome.Added -> context.getString(R.string.module_items_added, outcome.names.joinToString(", "))
            is ItemsOutcome.AddedAsModule -> context.getString(R.string.module_items_added_as_module, outcome.name)
            is ItemsOutcome.Problem -> {
                problem = context.getString(problemText(outcome.problem), name)
                return
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onAdded()
    }

    fun add(fromFile: Boolean, work: suspend () -> ItemsOutcome) {
        if (adding) return
        keyboard?.hide()
        adding = true
        problem = null
        problemFromFile = fromFile
        scope.launch {
            val outcome = work()
            adding = false
            show(outcome)
        }
    }

    fun addLink() {
        if (link.isNotBlank()) add(fromFile = false) { viewModel.addItemsLink(id, link) }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) add(fromFile = true) { viewModel.addItemsZip(id, uri) }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(stringResource(R.string.module_items_add_to, name), color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.module_items_add_body, name), color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        SheetPillButton(
            text = stringResource(if (adding && problemFromFile) R.string.module_items_adding else R.string.modules_choose_zip),
            onClick = { zipPicker.launch(ZipTypes) },
            enabled = !adding,
        )
        problem?.takeIf { problemFromFile }?.let { ProblemText(it) }

        Spacer(Modifier.height(26.dp))
        SheetLabel(stringResource(R.string.modules_add_from_link))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = link,
            onValueChange = {
                link = it
                problem = null
            },
            placeholder = stringResource(R.string.modules_link_hint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addLink() }),
        )
        problem?.takeIf { !problemFromFile }?.let { ProblemText(it) }
        Spacer(Modifier.height(14.dp))
        SheetPillButton(
            text = stringResource(if (adding && !problemFromFile) R.string.module_items_adding else R.string.module_items_add_link),
            onClick = { addLink() },
            enabled = link.isNotBlank() && !adding,
        )
    }
}

// some of these name the module, the rest ignore the name they're handed
private fun problemText(problem: ItemProblem): Int = when (problem) {
    ItemProblem.NotAZip -> R.string.module_items_not_a_zip
    ItemProblem.NotALink -> R.string.modules_link_not_a_link
    ItemProblem.Unreachable -> R.string.modules_link_unreachable
    ItemProblem.NothingFound -> R.string.module_items_nothing
    ItemProblem.Broken -> R.string.module_items_broken
    ItemProblem.TooBig -> R.string.module_items_too_big
}
