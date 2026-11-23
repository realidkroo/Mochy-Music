// the liveprog editor: a slot's script with line numbers, checked by the real engine before it goes live

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.ui.screens.audiolab.AudioLabPage
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabTextAction
import com.mochyy.mochymusic.ui.screens.audiolab.LabTitle
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.MixerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFE6E6EA))

@Composable
fun LiveprogEditorScreen(
    navController: NavController,
    uid: Long,
    viewModel: MixerViewModel = hiltViewModel(),
) {
    val store = viewModel.store
    val state by viewModel.state.collectAsState()
    val slot = state.slot(uid)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(uid) { mutableStateOf(slot?.script.orEmpty()) }
    var name by rememberSaveable(uid) { mutableStateOf(slot?.scriptName.orEmpty()) }
    var verdict by remember { mutableStateOf<String?>(null) }
    val parsed = remember(text) { store.script(text) }
    val dirty = slot != null && (text != slot.script || name != slot.scriptName.orEmpty())
    val saved = stringResource(R.string.liveprog_saved)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (viewModel.writeScript(uri, text)) Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
        }
    }

    AudioLabPage(
        title = LabTitle(stringResource(R.string.liveprog_title_first), stringResource(R.string.liveprog_title_second), firstSize = 40.sp, secondSize = 84.sp, firstDim = true),
        onBack = { navController.navigateUp() },
    ) {
        if (slot == null) return@AudioLabPage
        LabCard(padding = 14.dp) {
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LabColors.Primary),
                cursorBrush = SolidColor(LabColors.Primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                decorationBox = { field ->
                    if (name.isEmpty()) Text(stringResource(R.string.liveprog_name_hint), fontFamily = InterFontFamily, fontSize = 18.sp, color = LabColors.Faint)
                    field()
                },
            )
            Text(
                if (parsed.knobs.isEmpty()) stringResource(R.string.liveprog_no_knobs)
                else pluralStringResource(R.plurals.liveprog_knobs_found, parsed.knobs.size, parsed.knobs.size),
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = LabColors.Secondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            CodeEditor(text) {
                text = it
                verdict = null
            }
            Spacer(Modifier.height(10.dp))
            verdict?.let { result ->
                Text(
                    text = if (result.isEmpty()) stringResource(R.string.liveprog_builds) else stringResource(R.string.mixer_problem_compile, result.removePrefix("compile:")),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = if (result.isEmpty()) Color(0xFF8FD98B) else Color(0xFFF28B8B),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabTextAction(stringResource(R.string.liveprog_check)) {
                    scope.launch { verdict = withContext(Dispatchers.Default) { if (MixerNative.loaded) MixerNative.checkScript(text) else "" } }
                }
                LabTextAction(stringResource(R.string.liveprog_save_file)) { saveFile.launch("${name.ifBlank { "liveprog" }}.eel") }
                Spacer(Modifier.weight(1f))
                LabTextAction(stringResource(R.string.liveprog_apply), R.drawable.check, enabled = dirty) {
                    store.setScript(uid, text, name.ifBlank { null })
                }
            }
            if (dirty) {
                Text(stringResource(R.string.liveprog_unsaved), fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.liveprog_help), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = LabColors.Secondary, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.liveprog_credit), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = LabColors.Faint, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** the script with its line numbers in a gutter, scrolling sideways for long lines instead of wrapping them */
@Composable
private fun CodeEditor(text: String, onChange: (String) -> Unit) {
    val lines = remember(text) { text.count { it == '\n' } + 1 }
    val numbers = remember(lines) { (1..lines).joinToString("\n") }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LabColors.Stage)
            .padding(vertical = 12.dp),
    ) {
        Text(numbers, style = CodeStyle.copy(color = LabColors.Faint, textAlign = TextAlign.End), modifier = Modifier.width(36.dp).padding(end = 8.dp))
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = CodeStyle,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 12.dp),
        )
    }
}
