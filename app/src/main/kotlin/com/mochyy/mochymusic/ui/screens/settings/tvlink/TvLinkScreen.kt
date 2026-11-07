// TvLinkScreen.kt

package com.mochyy.mochymusic.ui.screens.settings.tvlink

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.link.discoverPeers
import com.mochyy.mochymusic.link.phone.PairException
import com.mochyy.mochymusic.link.phone.LiveTv
import com.mochyy.mochymusic.link.phone.PairedTv
import com.mochyy.mochymusic.link.phone.PhoneLinkHost
import com.mochyy.mochymusic.link.phone.PhoneLinkService
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TvLinkRoute = "tv_link"

// where a pairing attempt is, for the code dialog
sealed interface PairUi {
    data object Idle : PairUi
    data object Working : PairUi
    data object WrongCode : PairUi
    data object Failed : PairUi
    data class Done(val tvName: String) : PairUi
}

@HiltViewModel
class TvLinkViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: PhoneLinkHost,
) : ViewModel() {
    val tvs: StateFlow<List<PairedTv>> = host.tvs

    // tvs only show up while their phone page or pairing screen is open, so the list is live
    val nearby: StateFlow<List<LinkPeer>> = discoverPeers(context, LinkWire.TvServiceType)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    val linkHost: PhoneLinkHost get() = host
    val live = host.live
    val menuShown: StateFlow<Boolean> = host.menuShown

    fun setMenuShown(on: Boolean) {
        host.showInPlayerMenu = on
    }

    private val _staysReachable = MutableStateFlow(host.staysReachable)
    val staysReachable: StateFlow<Boolean> = _staysReachable.asStateFlow()

    private val _pairing = MutableStateFlow<PairUi>(PairUi.Idle)
    val pairing: StateFlow<PairUi> = _pairing.asStateFlow()

    fun pair(tv: LinkPeer, code: String) {
        _pairing.value = PairUi.Working
        viewModelScope.launch {
            host.pair(tv, code)
                .onSuccess {
                    _pairing.value = PairUi.Done(it.name)
                    PhoneLinkService.start(context)
                }
                .onFailure { _pairing.value = if ((it as? PairException)?.reason == PairException.Reason.WrongCode) PairUi.WrongCode else PairUi.Failed }
        }
    }

    fun resetPairing() {
        _pairing.value = PairUi.Idle
    }

    fun unlink(tvId: String) = host.unlink(tvId)

    fun setStaysReachable(on: Boolean) {
        host.staysReachable = on
        _staysReachable.value = on
    }
}

// "mochy on your tv": pair a tv once and it mirrors this phone's library until one of them unlinks
@Composable
fun TvLinkScreen(navController: NavController, viewModel: TvLinkViewModel = hiltViewModel()) {
    val tvs by viewModel.tvs.collectAsState()
    val nearby by viewModel.nearby.collectAsState()
    val stays by viewModel.staysReachable.collectAsState()
    val pairing by viewModel.pairing.collectAsState()
    val live by viewModel.live.collectAsState()
    val menuShown by viewModel.menuShown.collectAsState()
    var pairingWith by remember { mutableStateOf<LinkPeer?>(null) }
    var unlinking by remember { mutableStateOf<PairedTv?>(null) }
    var controlling by remember { mutableStateOf<LiveTv?>(null) }
    val openTvs = remember(live) { viewModel.linkHost.liveTvs() }

    SubSettingsScaffold(title = stringResource(R.string.tv_link_title), onBack = { navController.navigateUp() }) {
        ImportInfoCard {
            Text(stringResource(R.string.tv_link_intro), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        if (tvs.isNotEmpty()) {
            SettingsGroup(
                title = stringResource(R.string.tv_link_your_tvs),
                style = SettingsGroupStyle.Grouped,
                items = tvs.map { tv ->
                    SettingsItem(
                        title = { Text(tv.name) },
                        descriptionText = stringResource(if (openTvs.any { it.id == tv.id }) R.string.tv_link_open_now else R.string.tv_link_tap_to_unlink),
                        icon = painterResource(R.drawable.link),
                        iconShape = CircleShape,
                        onClick = { unlinking = tv },
                    )
                },
            )
            if (openTvs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SettingsGroup(
                    title = stringResource(R.string.tv_link_control_title),
                    style = SettingsGroupStyle.Grouped,
                    items = openTvs.map { tv ->
                        SettingsItem(
                            title = { Text(stringResource(R.string.tv_menu_control, tv.name)) },
                            descriptionText = stringResource(R.string.tv_link_control_detail),
                            icon = painterResource(R.drawable.cast_connected),
                            iconShape = CircleShape,
                            onClick = { controlling = tv },
                        )
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.tv_link_in_player_menu)) },
                        descriptionText = stringResource(R.string.tv_link_in_player_menu_detail),
                        icon = painterResource(R.drawable.more_horiz),
                        iconShape = CircleShape,
                        trailingContent = { AppSwitch(checked = menuShown, onCheckedChange = viewModel::setMenuShown) },
                        onClick = { viewModel.setMenuShown(!menuShown) },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.tv_link_stay_reachable)) },
                        descriptionText = stringResource(R.string.tv_link_stay_reachable_detail),
                        icon = painterResource(R.drawable.sync),
                        iconShape = CircleShape,
                        trailingContent = { AppSwitch(checked = stays, onCheckedChange = viewModel::setStaysReachable) },
                        onClick = { viewModel.setStaysReachable(!stays) },
                    ),
                ),
            )
            Spacer(Modifier.height(16.dp))
        }
        SettingsGroup(
            title = stringResource(R.string.tv_link_found),
            style = SettingsGroupStyle.Grouped,
            items = if (nearby.isEmpty()) {
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.tv_link_looking)) },
                        descriptionText = stringResource(R.string.tv_link_looking_detail),
                        icon = painterResource(R.drawable.smartphone),
                        iconShape = CircleShape,
                    ),
                )
            } else {
                nearby.map { tv ->
                    SettingsItem(
                        title = { Text(tv.name) },
                        descriptionText = stringResource(R.string.tv_link_tap_to_pair),
                        icon = painterResource(R.drawable.ic_mochy_mark),
                        iconShape = CircleShape,
                        onClick = {
                            viewModel.resetPairing()
                            pairingWith = tv
                        },
                    )
                }
            },
        )
    }

    pairingWith?.let { tv ->
        TvCodeDialog(
            tvName = tv.name,
            state = pairing,
            onPair = { code -> viewModel.pair(tv, code) },
            onDismiss = { pairingWith = null },
        )
    }
    controlling?.let { tv ->
        com.mochyy.mochymusic.ui.player.menu.TvRemoteDialog(viewModel.linkHost, tv, onDismiss = { controlling = null })
    }
    unlinking?.let { tv ->
        DefaultDialog(
            onDismiss = { unlinking = null },
            buttons = {
                TextButton(onClick = { unlinking = null }) { Text(stringResource(R.string.tv_cancel)) }
                TextButton(onClick = {
                    viewModel.unlink(tv.id)
                    unlinking = null
                }) { Text(stringResource(R.string.tv_unlink)) }
            },
        ) {
            Text(stringResource(R.string.tv_link_unlink_question, tv.name), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TvCodeDialog(tvName: String, state: PairUi, onPair: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.tv_link_enter_code, tvName)) },
        buttons = {
            if (state is PairUi.Done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_done)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_cancel)) }
                TextButton(onClick = { onPair(code) }, enabled = code.length == 6 && state != PairUi.Working) {
                    Text(stringResource(R.string.tv_link_pair))
                }
            }
        },
    ) {
        if (state is PairUi.Done) {
            Text(stringResource(R.string.tv_link_paired, state.tvName), style = MaterialTheme.typography.bodyLarge)
            return@DefaultDialog
        }
        OutlinedTextField(
            value = code,
            onValueChange = { typed -> code = typed.filter(Char::isDigit).take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text(stringResource(R.string.tv_link_code_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        val note = when (state) {
            PairUi.Working -> stringResource(R.string.tv_link_pairing)
            PairUi.WrongCode -> stringResource(R.string.tv_link_wrong_code)
            PairUi.Failed -> stringResource(R.string.tv_link_failed)
            else -> null
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
