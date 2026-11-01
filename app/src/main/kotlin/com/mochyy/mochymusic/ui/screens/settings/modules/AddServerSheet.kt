// adding a server to a module from a pasted link: the server's own, or a site that plays from one

package com.mochyy.mochymusic.ui.screens.settings.modules

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
import com.mochyy.mochymusic.module.ServerProblem
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import kotlinx.coroutines.launch

/** opens the sheet for module [id]; it closes itself once the server is in */
fun PopupSheetState.showAddServerSheet(id: String, viewModel: ModulesViewModel) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        AddServerContent(id, viewModel, onAdded = { sheet?.dismiss() })
    }
}

@Composable
private fun AddServerContent(id: String, viewModel: ModulesViewModel, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var link by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun add() {
        if (checking || link.isBlank()) return
        keyboard?.hide()
        checking = true
        problem = null
        scope.launch {
            val result = viewModel.addServer(id, link)
            checking = false
            if (result == null) {
                onAdded()
                return@launch
            }
            problem = context.getString(
                when (result) {
                    ServerProblem.NotALink -> R.string.modules_link_not_a_link
                    ServerProblem.Unreachable -> R.string.modules_link_unreachable
                    ServerProblem.NotAServer -> R.string.module_server_not_found
                    ServerProblem.AlreadyAdded -> R.string.module_server_already_added
                }
            )
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(stringResource(R.string.module_server_add), color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.module_server_add_body), color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        SheetTextField(
            value = link,
            onValueChange = {
                link = it
                problem = null
            },
            placeholder = stringResource(R.string.modules_link_hint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        problem?.let {
            Text(it, color = ProblemColor, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        SheetPillButton(
            text = stringResource(if (checking) R.string.module_server_finding else R.string.module_server_add_button),
            onClick = { add() },
            enabled = link.isNotBlank() && !checking,
        )
    }
}
