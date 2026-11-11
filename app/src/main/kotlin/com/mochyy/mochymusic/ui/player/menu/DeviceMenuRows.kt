// DeviceMenuRows.kt

package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkSongs
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.devices.showRemoteControl
import kotlinx.coroutines.launch
import org.json.JSONObject

// the player menu's device rows: move what's playing to a paired device that's around, line the song up there, or steer it from here
@Composable
internal fun DeviceMenuRows(onDismiss: () -> Unit) {
    val link = rememberDevicesLink()
    val shown by link.host.menuShown.collectAsState()
    val live by link.host.live.collectAsState()
    val devices by link.host.devices.collectAsState()
    val player = LocalPlayerConnection.current ?: return
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // only devices that may be steered from here, the switch on each device's own sheet
    val around = remember(live, devices) {
        link.host.liveDevices().mapNotNull { device -> devices.firstOrNull { it.id == device.id && it.remoteControl }?.let { device to it } }
    }
    if (!shown || around.isEmpty()) return

    around.forEach { (device, paired) ->
        Spacer(Modifier.height(8.dp))
        MenuRow(
            icon = R.drawable.cast_connected,
            title = stringResource(R.string.tv_menu_play_on, device.name),
            onClick = {
                link.remote.start(paired, handOver = false)
                link.remote.handOver()
                onDismiss()
            },
        )
        Spacer(Modifier.height(8.dp))
        MenuRow(
            icon = R.drawable.playlist_play,
            title = stringResource(R.string.tv_menu_play_next_on, device.name),
            enabled = player.mediaMetadata.value != null,
            onClick = {
                val song = player.mediaMetadata.value ?: return@MenuRow
                scope.launch {
                    val sent = link.host.tell(device, "/next", JSONObject().put("songs", LinkSongs.listToJson(listOf(song))))
                    TopToaster.show(context.getString(if (sent != null) R.string.tv_menu_next_sent else R.string.tv_menu_unreachable, device.name))
                }
                onDismiss()
            },
        )
        Spacer(Modifier.height(8.dp))
        MenuRow(
            icon = R.drawable.tune,
            title = stringResource(R.string.tv_menu_control, device.name),
            onClick = {
                link.remote.start(paired, handOver = false)
                onDismiss()
                menu.showRemoteControl(link)
            },
        )
    }
}
