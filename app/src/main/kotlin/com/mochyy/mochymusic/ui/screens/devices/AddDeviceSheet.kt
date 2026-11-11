// AddDeviceSheet.kt

package com.mochyy.mochymusic.ui.screens.devices

import android.os.Build
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.link.PairingCode
import com.mochyy.mochymusic.link.discoverPeers
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.launch

// pairs a new device both ways round: this phone shows its own code for another phone to type, and lists what's in pairing mode nearby
fun PopupSheetState.showAddDevice(link: DevicesLink) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = { SheetButton(stringResource(R.string.tv_done), Modifier.fillMaxWidth()) { sheet?.dismiss() } },
    ) {
        AddDeviceContent(link, this@showAddDevice)
    }
}

@Composable
private fun ColumnScope.AddDeviceContent(link: DevicesLink, sheets: PopupSheetState) {
    val context = LocalContext.current
    val code by link.pairing.code.collectAsState()
    val devices by link.host.devices.collectAsState()
    // devices only show up here while their own pairing screen is open, so the list is live
    val nearby by remember { discoverPeers(context, LinkWire.PairServiceType) }.collectAsState(initial = emptyList())
    val myId = remember { LinkIdentity.id(context) }
    val detected = nearby.filter { peer -> peer.id != myId && devices.none { it.id == peer.id } }
    val attempt = remember { PairAttempt(link.host) }
    val scope = rememberCoroutineScope()

    // this phone is in pairing mode exactly as long as the sheet is open
    DisposableEffect(Unit) {
        link.pairing.open()
        onDispose { link.pairing.close() }
    }
    LaunchedEffect(Unit) {
        link.pairing.paired.collect { TopToaster.show(context.getString(R.string.devices_paired_with, it.name), iconRes = R.drawable.cast_connected) }
    }
    LaunchedEffect(attempt.state) {
        (attempt.state as? PairUi.Done)?.let { TopToaster.show(context.getString(R.string.devices_paired_with, it.name), iconRes = R.drawable.cast_connected) }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(R.string.devices_add_title))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(CardShape)
                .background(MenuRowSurface),
        ) {
            LinkPicture(Modifier.fillMaxSize(), scale = 1.55f)
        }

        Spacer(Modifier.height(18.dp))
        GroupLabel(stringResource(R.string.devices_pairing_code))
        PairingCodeBlock(code)

        Spacer(Modifier.height(20.dp))
        GroupLabel(stringResource(R.string.devices_detected))
        if (detected.isEmpty()) {
            LookingRow()
        } else {
            detected.forEach { peer ->
                DetectedRow(peer) { sheets.showPairCode(link, peer) }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel(stringResource(R.string.devices_or))
        EnterCodeField(enabled = attempt.state != PairUi.Working) { typed ->
            scope.launch { attempt.pair(detected, typed) }
        }
        val note = attempt.state.note
        if (note != null) {
            Text(
                stringResource(note),
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = if (attempt.state.isProblem) Color(0xFFFF8A80) else Muted,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        // for a wi-fi that hides devices from each other: the tv shows its address under the code
        Text(
            stringResource(R.string.tv_pair_sheet_by_address),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .clickable { sheets.showPairCode(link, null) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

// this phone's code in big type, with a barcode strip made from it and a line saying which device it is
@Composable
private fun PairingCodeBlock(code: PairingCode?) {
    val digits = code?.code ?: "––––––"
    Text(
        digits.take(3) + "  " + digits.drop(3),
        fontFamily = FontFamily.Monospace,
        fontSize = 40.sp,
        letterSpacing = 10.sp,
        color = Color.White,
        maxLines = 1,
    )
    Spacer(Modifier.height(8.dp))
    val bars = remember(code?.code) { barsFor(digits + Build.MODEL.orEmpty()) }
    Canvas(Modifier.width(220.dp).height(20.dp)) {
        val unit = size.width / bars.sum()
        var x = 0f
        bars.forEachIndexed { index, width ->
            if (index % 2 == 0) drawRect(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Size(width * unit, size.height))
            x += width * unit
        }
    }
    Spacer(Modifier.height(4.dp))
    val caption = listOfNotNull(
        digits.take(3) + " " + digits.drop(3),
        "ANDROID ${Build.VERSION.RELEASE}",
        code?.deviceName?.uppercase(),
        code?.address,
    ).joinToString("  ·  ")
    Text(caption, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// bar and gap widths, one to three units each, worked out from the characters so every code draws its own strip
private fun barsFor(seed: String): List<Int> = buildList {
    seed.forEach { char ->
        val bits = char.code
        add(1 + (bits and 1) + (bits shr 3 and 1))
        add(1 + (bits shr 1 and 1))
        add(1 + (bits shr 2 and 1))
    }
}

// nothing in pairing mode yet: three dots taking turns while the phone listens
@Composable
private fun LookingRow() {
    val phase by rememberInfiniteTransition(label = "devicesLooking").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "devicesLookingPhase",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.devices_looking), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(stringResource(R.string.devices_looking_detail), fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                // read in the layer, so the dots move without recomposing the row
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = if (phase.toInt() == index) 0.9f else 0.25f }
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

// a device in pairing mode nearby, with a button to type its code
@Composable
private fun DetectedRow(peer: LinkPeer, onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .clickable(onClick = onConnect)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(peer.kind.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peer.kind.label(), fontFamily = InterFontFamily, fontSize = 12.sp, color = Muted)
        }
        PillButton(stringResource(R.string.devices_connect), onClick = onConnect)
    }
}

// a code typed straight in: once all six digits are there it's tried on whatever is in pairing mode
@Composable
private fun EnterCodeField(enabled: Boolean, onCode: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    BasicTextField(
        value = code,
        onValueChange = { typed ->
            code = typed.filter(Char::isDigit).take(6)
            if (code.length == 6) onCode(code)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 4.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MenuRowSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { inner ->
            if (code.isEmpty()) {
                Text(stringResource(R.string.devices_enter_code), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f))
            }
            inner()
        },
    )
}
