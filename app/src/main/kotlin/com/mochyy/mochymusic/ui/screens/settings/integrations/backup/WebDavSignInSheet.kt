// connecting a webdav server for backups: address, username and password, checked against the server before anything is saved

package com.mochyy.mochymusic.ui.screens.settings.integrations.backup

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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.backup.CloudAccount
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.CloudBackupViewModel
import kotlinx.coroutines.launch

private val ProblemColor = Color(0xFFFF8A80)

@Composable
internal fun WebDavSignInContent(
    viewModel: CloudBackupViewModel,
    // the server already connected, so changing a password doesn't mean typing the address again
    current: CloudAccount?,
    onConnected: (host: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var address by rememberSaveable { mutableStateOf(current?.serverUrl.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(current?.username.orEmpty()) }
    // plain remember on purpose, a saveable password would be written into the saved screen state
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val canSubmit = address.isNotBlank() && username.isNotBlank() && !busy

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        busy = true
        problem = null
        scope.launch {
            val failure = viewModel.connectWebDav(address, username.trim(), password)
            busy = false
            if (failure == null) onConnected(viewModel.account.value?.name.orEmpty()) else problem = failure
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(stringResource(R.string.webdav_title), color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.webdav_body), color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))

        SheetLabel(stringResource(R.string.webdav_address))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = address,
            onValueChange = {
                address = it
                problem = null
            },
            placeholder = stringResource(R.string.webdav_address_hint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel(stringResource(R.string.username))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = username,
            onValueChange = {
                username = it
                problem = null
            },
            placeholder = stringResource(R.string.webdav_username_hint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.semantics { contentType = ContentType.Username },
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel(stringResource(R.string.password))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            placeholder = stringResource(R.string.webdav_password_hint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.semantics { contentType = ContentType.Password },
        )

        problem?.let {
            Text(it, color = ProblemColor, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
        }
        Spacer(Modifier.height(22.dp))

        SheetPillButton(
            text = stringResource(if (busy) R.string.webdav_connecting else R.string.webdav_connect),
            onClick = ::submit,
            enabled = canSubmit,
        )
    }
}
