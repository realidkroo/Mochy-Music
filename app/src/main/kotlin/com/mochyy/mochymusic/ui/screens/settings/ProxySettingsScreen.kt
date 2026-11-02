// ProxySettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.ProxyEnabledKey
import com.mochyy.mochymusic.constants.ProxyPasswordKey
import com.mochyy.mochymusic.constants.ProxyTypeKey
import com.mochyy.mochymusic.constants.ProxyUrlKey
import com.mochyy.mochymusic.constants.ProxyUsernameKey
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.utils.AppProxy
import com.mochyy.mochymusic.utils.ProxyConfig
import com.mochyy.mochymusic.utils.ProxyRefusedException
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// the route the app behaviour row opens
const val ProxySettingsRoute = "proxy_settings"

private enum class ProxyDialog { Address, Type, Login }

private sealed interface ProxyCheck {
    data object Idle : ProxyCheck
    data object Running : ProxyCheck
    data class Passed(val millis: Long) : ProxyCheck
    data class Failed(val reason: Int) : ProxyCheck
}

// settings > app behaviour > proxy: youtube through another server, for blocked networks and countries youtube music skips
@Composable
fun ProxySettingsScreen(navController: NavController) {
    var enabled by rememberPreference(ProxyEnabledKey, defaultValue = false)
    var address by rememberPreference(ProxyUrlKey, defaultValue = "")
    var typeName by rememberPreference(ProxyTypeKey, defaultValue = Proxy.Type.HTTP.name)
    var username by rememberPreference(ProxyUsernameKey, defaultValue = "")
    var password by rememberPreference(ProxyPasswordKey, defaultValue = "")

    val type = typeName.toEnum(defaultValue = Proxy.Type.HTTP)
    val config = remember(type, address, username, password) {
        AppProxy.parseAddress(address)?.let { ProxyConfig(type, it, username, password) }
    }
    var dialog by remember { mutableStateOf<ProxyDialog?>(null) }
    var check by remember { mutableStateOf<ProxyCheck>(ProxyCheck.Idle) }
    val scope = rememberCoroutineScope()

    // a changed setting makes the last check's answer stale
    LaunchedEffect(config) { check = ProxyCheck.Idle }

    SubSettingsScaffold(
        title = stringResource(R.string.proxy),
        onBack = { navController.navigateUp() },
    ) {
        Text(
            text = stringResource(R.string.proxy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_use)) },
                    descriptionText = stringResource(if (config == null) R.string.proxy_use_needs_address else R.string.proxy_use_desc),
                    icon = painterResource(R.drawable.wifi_proxy),
                    iconShape = CircleShape,
                    enabled = config != null,
                    onClick = { enabled = !enabled },
                    trailingContent = {
                        AppSwitch(checked = enabled && config != null, onCheckedChange = { enabled = it }, enabled = config != null)
                    },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_address)) },
                    descriptionText = config?.let { AppProxy.addressLabel(it.address) } ?: stringResource(R.string.proxy_address_not_set),
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    onClick = { dialog = ProxyDialog.Address },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_type)) },
                    descriptionText = stringResource(type.labelRes()),
                    icon = painterResource(R.drawable.network_node),
                    iconShape = CircleShape,
                    onClick = { dialog = ProxyDialog.Type },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_login)) },
                    descriptionText = if (username.isEmpty()) stringResource(R.string.proxy_login_none) else stringResource(R.string.proxy_login_set, username),
                    icon = painterResource(R.drawable.key),
                    iconShape = CircleShape,
                    onClick = { dialog = ProxyDialog.Login },
                ),
            ),
        )

        Spacer(Modifier.height(18.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(if (check == ProxyCheck.Running) R.string.proxy_checking else R.string.proxy_check)) },
                    descriptionText = when (val result = check) {
                        is ProxyCheck.Passed -> stringResource(R.string.proxy_check_ok, result.millis.toInt())
                        is ProxyCheck.Failed -> stringResource(result.reason)
                        else -> stringResource(R.string.proxy_check_desc)
                    },
                    icon = painterResource(R.drawable.network_update),
                    iconShape = CircleShape,
                    enabled = config != null && check != ProxyCheck.Running,
                    onClick = {
                        val target = config
                        if (target != null) {
                            check = ProxyCheck.Running
                            scope.launch {
                                check = AppProxy.check(target).fold(
                                    onSuccess = { ProxyCheck.Passed(it) },
                                    onFailure = { ProxyCheck.Failed(it.checkReason()) },
                                )
                            }
                        }
                    },
                ),
            ),
        )

        Spacer(Modifier.height(140.dp))
    }

    when (dialog) {
        ProxyDialog.Address -> AddressDialog(
            current = config?.let { AppProxy.addressLabel(it.address) }.orEmpty(),
            onDone = { text ->
                address = AppProxy.parseAddress(text)?.let(AppProxy::addressLabel).orEmpty()
                // a pasted link names its own type
                when {
                    text.trim().startsWith("socks", ignoreCase = true) -> typeName = Proxy.Type.SOCKS.name
                    text.trim().startsWith("http", ignoreCase = true) -> typeName = Proxy.Type.HTTP.name
                }
                if (address.isEmpty()) enabled = false
            },
            onDismiss = { dialog = null },
        )
        ProxyDialog.Type -> TypeDialog(
            selected = type,
            onSelect = {
                typeName = it.name
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        ProxyDialog.Login -> LoginDialog(
            username = username,
            password = password,
            onDone = { newUsername, newPassword ->
                username = newUsername.trim()
                password = newPassword
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun AddressDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    TextFieldDialog(
        title = { Text(stringResource(R.string.proxy_address)) },
        initialTextFieldValue = TextFieldValue(current, TextRange(current.length)),
        placeholder = { Text(stringResource(R.string.proxy_address_hint)) },
        // empty clears the proxy, anything else has to read as host:port
        isInputValid = { it.isBlank() || AppProxy.parseAddress(it) != null },
        keyboardType = KeyboardType.Uri,
        onDone = onDone,
        onDismiss = onDismiss,
    )
}

@Composable
private fun TypeDialog(selected: Proxy.Type, onSelect: (Proxy.Type) -> Unit, onDismiss: () -> Unit) {
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text(stringResource(R.string.proxy_type)) },
    ) {
        listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(stringResource(option.labelRes()), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LoginDialog(
    username: String,
    password: String,
    onDone: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newUsername by remember { mutableStateOf(username) }
    var newPassword by remember { mutableStateOf(password) }
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text(stringResource(R.string.proxy_login)) },
        buttons = {
            TextButton(onClick = { onDone("", "") }) { Text(stringResource(R.string.proxy_login_clear)) }
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            TextButton(onClick = { onDone(newUsername, newPassword) }) { Text(stringResource(android.R.string.ok)) }
        },
    ) {
        OutlinedTextField(
            value = newUsername,
            onValueChange = { newUsername = it },
            singleLine = true,
            label = { Text(stringResource(R.string.proxy_username)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            singleLine = true,
            label = { Text(stringResource(R.string.proxy_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Proxy.Type.labelRes(): Int = if (this == Proxy.Type.SOCKS) R.string.proxy_type_socks else R.string.proxy_type_http

// the line that says what to fix, from what the check ran into
private fun Throwable.checkReason(): Int = when (this) {
    is UnknownHostException -> R.string.proxy_check_not_found
    is ProxyRefusedException -> if (code == 407) R.string.proxy_check_login else R.string.proxy_check_failed
    is ConnectException, is SocketTimeoutException, is NoRouteToHostException -> R.string.proxy_check_no_answer
    else -> R.string.proxy_check_failed
}
