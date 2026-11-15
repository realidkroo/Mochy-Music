// DeviceIntegrationSheet.kt

package com.mochyy.mochymusic.ui.screens.devices

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.phone.LiveDevice
import com.mochyy.mochymusic.link.phone.PairedDevice
import com.mochyy.mochymusic.link.phone.PhoneLinkHost
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.delay

// settings, the import page and the link's notification all open this; it isn't a page of its own any more
const val DeviceIntegrationRoute = "device_integration"

// the phone's paired devices with a way to steer each, a way to add more, and how the link behaves
fun PopupSheetState.showDeviceIntegration(link: DevicesLink) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = { SheetButton(stringResource(R.string.tv_done), Modifier.fillMaxWidth()) { sheet?.dismiss() } },
    ) {
        DeviceIntegrationContent(link)
    }
}

// opens the sheet whenever requests goes up, for the link's notification
@Composable
fun DeviceIntegrationOpener(requests: Int) {
    val sheets = LocalZoomOutOverlayState.current
    val link = rememberDevicesLink()
    LaunchedEffect(requests) {
        if (requests > 0) sheets.showDeviceIntegration(link)
    }
}

@Composable
private fun ColumnScope.DeviceIntegrationContent(link: DevicesLink) {
    val sheets = LocalZoomOutOverlayState.current
    val devices by link.host.devices.collectAsState()
    val live by link.host.live.collectAsState()
    val target by link.remote.target.collectAsState()
    val notify by link.host.notifyShown.collectAsState()
    val menuShown by link.host.menuShown.collectAsState()
    var stays by remember { mutableStateOf(link.host.staysReachable) }
    // pairing and finding devices both need the wi-fi, which android 17 asks about first
    com.mochyy.mochymusic.link.AskForLocalNetwork(onGranted = { link.host.advertiseAgain() })
    // a device drops to "not around" on its own, so the list looks at the clock now and then
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        link.host.checkPeersNow()
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val around = remember(live, now) { link.host.liveDevices(now).associateBy { it.id } }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(R.string.devices_title))
        Spacer(Modifier.height(14.dp))
        HeroCard()
        Spacer(Modifier.height(22.dp))

        if (devices.isNotEmpty()) {
            GroupLabel(stringResource(R.string.devices_mine).uppercase())
            devices.forEach { device ->
                DeviceRow(
                    device = device,
                    live = around[device.id],
                    steering = target?.id == device.id,
                    now = now,
                    onControl = {
                        if (link.remote.isSteering(device.id)) link.remote.stop() else link.remote.start(device, handOver = true)
                    },
                    onOpen = { sheets.showDeviceSheet(link, device.id) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        AddDevicesBox(full = devices.size >= PhoneLinkHost.MaxDevices) { sheets.showAddDevice(link) }

        Spacer(Modifier.height(22.dp))
        GroupLabel(stringResource(R.string.devices_options).uppercase())
        Card {
            ToggleRow(
                icon = Icons.Rounded.CastConnected,
                title = stringResource(R.string.devices_stay_reachable),
                detail = stringResource(R.string.devices_stay_reachable_detail),
                checked = stays,
            ) {
                link.host.staysReachable = it
                stays = it
            }
            ToggleRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.tv_link_notify),
                detail = stringResource(R.string.tv_link_notify_detail),
                checked = notify,
            ) { link.host.notifySync = it }
            ToggleRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.devices_in_player_menu),
                detail = stringResource(R.string.devices_in_player_menu_detail),
                checked = menuShown,
            ) { link.host.showInPlayerMenu = it }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// the phone and a tv drawn small, over what the link is for
@Composable
private fun HeroCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(16.dp),
    ) {
        LinkPicture(Modifier.width(112.dp).height(40.dp), scale = 0.9f, centered = false)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.devices_hero),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color.White,
        )
    }
}

// one paired device: what it is, whether it's around, a button to steer it and one for its own settings
@Composable
private fun DeviceRow(
    device: PairedDevice,
    live: LiveDevice?,
    steering: Boolean,
    now: Long,
    onControl: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(device.kind.icon, contentDescription = device.kind.label(), tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (live != null) LiveGreen else Color.White.copy(alpha = 0.3f)))
                Spacer(Modifier.width(6.dp))
                Text(
                    statusOf(device, live, steering, now),
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (device.remoteControl) {
            Spacer(Modifier.width(8.dp))
            PillButton(
                stringResource(if (steering) R.string.devices_controlling_button else R.string.devices_control),
                bright = steering,
                enabled = steering || live != null,
                onClick = onControl,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.devices_device_settings), tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun statusOf(device: PairedDevice, live: LiveDevice?, steering: Boolean, now: Long): String {
    val synced = device.lastSyncAt.takeIf { it > 0 && device.kind.isTv && device.mirrorLibrary }
        ?.let { DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString() }
    return when {
        steering && live != null -> stringResource(R.string.devices_status_controlling)
        live != null && synced != null -> stringResource(R.string.tv_link_status_open_synced, synced)
        live != null -> stringResource(R.string.tv_link_status_open)
        device.lastSeenAt > 0 -> stringResource(R.string.tv_link_status_seen, DateUtils.getRelativeTimeSpanString(device.lastSeenAt, now, DateUtils.MINUTE_IN_MILLIS).toString())
        else -> stringResource(R.string.tv_link_status_waiting)
    }
}

// the dashed box under the list that opens "add device"; greyed out once the phone has all it keeps
@Composable
private fun AddDevicesBox(full: Boolean, onClick: () -> Unit) {
    val edge = Color.White.copy(alpha = if (full) 0.15f else 0.35f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))),
                )
            }
            .clickable(enabled = !full, onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White.copy(alpha = if (full) 0.3f else 0.8f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (full) R.string.devices_full else R.string.devices_add_more),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = if (full) 0.4f else 0.8f),
        )
    }
}
