// "find more sources": add a module from a pasted link, a zip on the phone or mochy's catalog, after seeing what it adds

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.CatalogModule
import com.mochyy.mochymusic.modules.ModuleRegistry.LinkCheck
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import kotlinx.coroutines.launch

internal val ProblemColor = Color(0xFFFF8A80)

// file managers name a zip in a few ways, and some only know it as bytes
internal val ZipTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")

/** opens the sheet; [onAdded] gets the new module's id once it's in, to open its page */
fun PopupSheetState.showFindModulesSheet(viewModel: ModulesViewModel, onAdded: (String) -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        FindModulesContent(viewModel, onAdded = { id ->
            sheet?.dismiss()
            onAdded(id)
        })
    }
}

@Composable
private fun FindModulesContent(viewModel: ModulesViewModel, onAdded: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var link by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // a problem shows under whichever of the two, the link or the file, it came from
    var problemFromFile by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<LinkCheck.Ready?>(null) }
    var catalog by remember { mutableStateOf<Result<List<CatalogModule>>?>(null) }

    LaunchedEffect(Unit) { catalog = viewModel.catalog() }

    fun show(result: LinkCheck) {
        when (result) {
            is LinkCheck.Ready -> ready = result
            LinkCheck.NotALink -> problem = context.getString(R.string.modules_link_not_a_link)
            LinkCheck.Unreachable -> problem = context.getString(R.string.modules_link_unreachable)
            LinkCheck.NotAModule -> problem = context.getString(R.string.modules_link_not_a_module)
            LinkCheck.Website -> problem = context.getString(R.string.modules_link_website)
            LinkCheck.NotAZip -> problem = context.getString(R.string.modules_file_not_a_zip)
            LinkCheck.NeedsUrl -> problem = context.getString(R.string.modules_file_needs_url)
            LinkCheck.NotSigned -> problem = context.getString(R.string.modules_file_not_signed)
        }
    }

    fun check(target: String) {
        if (checking || target.isBlank()) return
        keyboard?.hide()
        checking = true
        problem = null
        problemFromFile = false
        scope.launch {
            show(viewModel.check(target))
            checking = false
        }
    }

    fun checkFile(uri: Uri) {
        if (checking) return
        checking = true
        problem = null
        problemFromFile = true
        scope.launch {
            show(viewModel.checkZip(uri))
            checking = false
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) checkFile(uri)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        Text(stringResource(R.string.modules_find_more), color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.modules_find_more_body), color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))

        val confirming = ready
        if (confirming != null) {
            ConfirmAdd(
                ready = confirming,
                adding = checking,
                onAdd = {
                    checking = true
                    scope.launch {
                        val id = viewModel.install(confirming)
                        if (id != null) return@launch onAdded(id)
                        // only a zip's own code can fail here, so the problem shows under the file button
                        checking = false
                        ready = null
                        problemFromFile = true
                        problem = context.getString(R.string.modules_code_broken)
                    }
                },
                onCancel = { ready = null },
            )
            return@Column
        }

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
            keyboardActions = KeyboardActions(onDone = { check(link) }),
        )
        problem?.takeIf { !problemFromFile }?.let { ProblemText(it) }
        Spacer(Modifier.height(14.dp))
        SheetPillButton(
            text = stringResource(if (checking) R.string.modules_link_checking else R.string.modules_link_check),
            onClick = { check(link) },
            enabled = link.isNotBlank() && !checking,
        )

        Spacer(Modifier.height(26.dp))
        SheetLabel(stringResource(R.string.modules_add_from_file))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.modules_add_from_file_body), color = SearchColors.Secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))
        SheetPillButton(
            text = stringResource(R.string.modules_choose_zip),
            onClick = { zipPicker.launch(ZipTypes) },
            enabled = !checking,
        )
        problem?.takeIf { problemFromFile }?.let { ProblemText(it) }

        Spacer(Modifier.height(26.dp))
        SheetLabel(stringResource(R.string.modules_catalog))
        Spacer(Modifier.height(10.dp))
        CatalogList(catalog, busy = checking, onPick = { check(it.url) })
    }
}

@Composable
internal fun ProblemText(text: String) {
    Text(text, color = ProblemColor, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun CatalogList(catalog: Result<List<CatalogModule>>?, busy: Boolean, onPick: (CatalogModule) -> Unit) {
    val list = catalog?.getOrNull()
    when {
        catalog == null -> Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        list == null -> CatalogNote(stringResource(R.string.modules_catalog_failed))
        list.isEmpty() -> CatalogNote(stringResource(R.string.modules_catalog_empty))
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { item -> CatalogRow(item, enabled = !busy) { onPick(item) } }
        }
    }
}

@Composable
private fun CatalogNote(text: String) {
    Text(text, color = SearchColors.Secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun CatalogRow(item: CatalogModule, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    ) {
        ModuleLogo(item.url, item.name, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (item.description.isNotBlank()) {
                Text(item.description, color = SearchColors.Secondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (item.adds.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FeatureChips(item.adds)
            }
        }
    }
}

/** what the link turned out to be, and one last yes before it's added */
@Composable
private fun ConfirmAdd(ready: LinkCheck.Ready, adding: Boolean, onAdd: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LabColors.Card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleLogo(ready.from, ready.name, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ready.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(ready.from, color = SearchColors.Secondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        FeatureChips(ready.adds)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (ready.alreadyAdded) R.string.modules_confirm_update else R.string.modules_confirm_add),
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
    Spacer(Modifier.height(16.dp))
    SheetPillButton(text = stringResource(if (adding) R.string.modules_adding else R.string.modules_add, ready.name), onClick = onAdd, enabled = !adding)
    Text(
        stringResource(R.string.modules_cancel),
        color = SearchColors.Secondary,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCancel)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}
