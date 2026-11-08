// TvLinkScreen.kt

package com.mochyy.mochymusic.ui.screens.settings.tvlink

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.link.discoverPeers
import com.mochyy.mochymusic.link.phone.LiveTv
import com.mochyy.mochymusic.link.phone.PairException
import com.mochyy.mochymusic.link.phone.PairedTv
import com.mochyy.mochymusic.link.phone.PhoneLinkHost
import com.mochyy.mochymusic.link.phone.PhoneLinkService
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.player.menu.TvRemoteDialog
import com.mochyy.mochymusic.ui.player.menu.sendQueueToTv
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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
    val host: PhoneLinkHost,
) : ViewModel() {
    val tvs: StateFlow<List<PairedTv>> = host.tvs
    val live = host.live
    val menuShown: StateFlow<Boolean> = host.menuShown
    val notifyShown: StateFlow<Boolean> = host.notifyShown

    // tvs only show up while their phone page or pairing screen is open, so the list is live
    val nearby: StateFlow<List<LinkPeer>> = discoverPeers(context, LinkWire.TvServiceType)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

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

    fun setMenuShown(on: Boolean) {
        host.showInPlayerMenu = on
    }

    fun setNotify(on: Boolean) {
        host.notifySync = on
    }
}

private val CardColor = Color(0xFF1C1C1E)
private val CardRaised = Color(0xFF2A2A2E)
private val Muted = Color.White.copy(alpha = 0.6f)

// "mochy on your tv": link a tv once and it mirrors this phone's library, streams its songs and takes its queue, until one of them unlinks
@Composable
fun TvLinkScreen(navController: NavController, viewModel: TvLinkViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val tvs by viewModel.tvs.collectAsState()
    val live by viewModel.live.collectAsState()
    val nearby by viewModel.nearby.collectAsState()
    val stays by viewModel.staysReachable.collectAsState()
    val menuShown by viewModel.menuShown.collectAsState()
    val notify by viewModel.notifyShown.collectAsState()
    val overlay = com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState.current
    var unlinking by remember { mutableStateOf<PairedTv?>(null) }
    var controlling by remember { mutableStateOf<LiveTv?>(null) }
    // a tv drops to "not open" on its own, so the page looks at the clock now and then
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val openTvs = remember(live, now) { viewModel.host.liveTvs(now) }
    val paired = tvs.map { it.id }.toSet()

    SubSettingsScaffold(title = stringResource(R.string.tv_link_title), onBack = { navController.navigateUp() }) {
        TvLinkHero(linked = tvs.isNotEmpty(), connected = openTvs.isNotEmpty(), tvName = openTvs.firstOrNull()?.name ?: tvs.firstOrNull()?.name)
        Spacer(Modifier.height(16.dp))

        if (tvs.isNotEmpty()) {
            SectionLabel(stringResource(R.string.tv_link_your_tvs))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tvs.forEach { tv ->
                    val open = openTvs.firstOrNull { it.id == tv.id }
                    TvCard(
                        tv = tv,
                        open = open,
                        now = now,
                        canPlayHere = player?.mediaMetadata?.value != null,
                        onControl = { open?.let { controlling = it } },
                        onPlayHere = { open?.let { target -> player?.let { scope.launch { sendQueueToTv(context, viewModel.host, target, it) } } } },
                        onUnlink = { unlinking = tv },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        SectionLabel(stringResource(if (tvs.isEmpty()) R.string.tv_link_found else R.string.tv_link_add_another))
        val fresh = nearby.filter { it.id !in paired }
        val found = if (fresh.isEmpty()) {
            listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.tv_link_looking)) },
                    descriptionText = stringResource(R.string.tv_link_looking_detail),
                    icon = painterResource(R.drawable.smartphone),
                    iconShape = CircleShape,
                    trailingContent = { LookingDots() },
                ),
            )
        } else {
            fresh.map { tv ->
                SettingsItem(
                    title = { Text(tv.name) },
                    descriptionText = stringResource(R.string.tv_link_tap_to_pair),
                    icon = painterResource(R.drawable.ic_mochy_mark),
                    iconShape = CircleShape,
                    onClick = { overlay.showTvPairSheet(tv, viewModel) },
                )
            }
        }
        // a wi-fi that hides tvs from phones: the tv shows its address under the code, typed here instead
        val byAddress = SettingsItem(
            title = { Text(stringResource(R.string.tv_pair_sheet_by_address)) },
            descriptionText = stringResource(R.string.tv_link_by_address_detail),
            icon = painterResource(R.drawable.link),
            iconShape = CircleShape,
            onClick = { overlay.showTvPairSheet(null, viewModel) },
        )
        SettingsGroup(style = SettingsGroupStyle.Grouped, items = found + byAddress)

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.tv_link_how_title))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.tv_link_stay_reachable)) },
                    descriptionText = stringResource(R.string.tv_link_stay_reachable_detail),
                    icon = painterResource(R.drawable.cast_connected),
                    iconShape = CircleShape,
                    trailingContent = { AppSwitch(checked = stays, onCheckedChange = viewModel::setStaysReachable) },
                    onClick = { viewModel.setStaysReachable(!stays) },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.tv_link_notify)) },
                    descriptionText = stringResource(R.string.tv_link_notify_detail),
                    icon = painterResource(R.drawable.sync),
                    iconShape = CircleShape,
                    trailingContent = { AppSwitch(checked = notify, onCheckedChange = viewModel::setNotify) },
                    onClick = { viewModel.setNotify(!notify) },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.tv_link_in_player_menu)) },
                    descriptionText = stringResource(R.string.tv_link_in_player_menu_detail),
                    icon = painterResource(R.drawable.more_horiz),
                    iconShape = CircleShape,
                    trailingContent = { AppSwitch(checked = menuShown, onCheckedChange = viewModel::setMenuShown) },
                    onClick = { viewModel.setMenuShown(!menuShown) },
                ),
            ),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.tv_link_intro),
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = InterFontFamily,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    controlling?.let { tv -> TvRemoteDialog(viewModel.host, tv, onDismiss = { controlling = null }) }
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
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Muted,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

// the phone and the tv drawn side by side: a dashed line while only linked, a bright pulsing one while they're talking
@Composable
private fun TvLinkHero(linked: Boolean, connected: Boolean, tvName: String?) {
    val pulse by rememberInfiniteTransition(label = "tvLinkPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "tvLinkPulseValue",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardColor)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val phoneW = 34.dp.toPx()
            val phoneH = 62.dp.toPx()
            val tvW = 104.dp.toPx()
            val tvH = 64.dp.toPx()
            val gap = 64.dp.toPx()
            val total = phoneW + gap + tvW
            val left = (size.width - total) / 2
            val mid = size.height / 2 - 6.dp.toPx()
            val stroke = Stroke(2.dp.toPx())
            val white = Color.White.copy(alpha = 0.85f)
            drawRoundRect(white, Offset(left, mid - phoneH / 2), Size(phoneW, phoneH), CornerRadius(8.dp.toPx()), style = stroke)
            val tvLeft = left + phoneW + gap
            drawRoundRect(white, Offset(tvLeft, mid - tvH / 2), Size(tvW, tvH), CornerRadius(8.dp.toPx()), style = stroke)
            drawLine(white, Offset(tvLeft + tvW / 2 - 14.dp.toPx(), mid + tvH / 2 + 10.dp.toPx()), Offset(tvLeft + tvW / 2 + 14.dp.toPx(), mid + tvH / 2 + 10.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            val lineAlpha = when {
                connected -> pulse
                linked -> 0.55f
                else -> 0.25f
            }
            drawLine(
                Color(0xFF8FD3FF).copy(alpha = lineAlpha),
                Offset(left + phoneW + 10.dp.toPx(), mid),
                Offset(tvLeft - 10.dp.toPx(), mid),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (connected) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                connected -> stringResource(R.string.tv_link_hero_connected, tvName.orEmpty())
                linked -> stringResource(R.string.tv_link_hero_linked, tvName.orEmpty())
                else -> stringResource(R.string.tv_link_hero_title)
            },
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                when {
                    connected -> R.string.tv_link_hero_connected_detail
                    linked -> R.string.tv_link_hero_linked_detail
                    else -> R.string.tv_link_hero_detail
                },
            ),
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

// one linked tv: whether it's open now and when it last synced, with what can be done with it
@Composable
private fun TvCard(
    tv: PairedTv,
    open: LiveTv?,
    now: Long,
    canPlayHere: Boolean,
    onControl: () -> Unit,
    onPlayHere: () -> Unit,
    onUnlink: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardColor)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(CardRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (open != null) Color(0xFF5BD68A) else Color.White.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(6.dp))
                    Text(statusOf(tv, open, now), fontFamily = InterFontFamily, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open != null) {
                CardButton(stringResource(R.string.tv_link_control), bright = true, onClick = onControl)
                if (canPlayHere) CardButton(stringResource(R.string.tv_link_play_there), onClick = onPlayHere)
            }
            CardButton(stringResource(R.string.tv_unlink), onClick = onUnlink)
        }
    }
}

@Composable
private fun statusOf(tv: PairedTv, open: LiveTv?, now: Long): String {
    val synced = tv.lastSyncAt.takeIf { it > 0 }?.let { DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString() }
    return when {
        open != null && synced != null -> stringResource(R.string.tv_link_status_open_synced, synced)
        open != null -> stringResource(R.string.tv_link_status_open)
        tv.lastSeenAt > 0 -> stringResource(R.string.tv_link_status_seen, DateUtils.getRelativeTimeSpanString(tv.lastSeenAt, now, DateUtils.MINUTE_IN_MILLIS).toString())
        else -> stringResource(R.string.tv_link_status_waiting)
    }
}

@Composable
private fun CardButton(text: String, bright: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = if (bright) Color.Black else Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (bright) Color.White else CardRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// three dots taking turns, while the phone listens for tvs on the wi-fi
@Composable
private fun LookingDots() {
    val phase by rememberInfiniteTransition(label = "tvLookingDots").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200)),
        label = "tvLookingDotsPhase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val lit = phase.toInt() == index
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (lit) 0.9f else 0.25f)))
        }
    }
}
