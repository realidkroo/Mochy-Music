// TvPairSheet.kt

package com.mochyy.mochymusic.ui.screens.settings.tvlink

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.screens.update.SheetPrimaryButton
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private val BoxColor = Color(0xFF2A2A2E)
private val Muted = Color.White.copy(alpha = 0.6f)

// the code from the tv's screen, typed into six boxes on a sheet; tv is null when it's linked by its address instead
fun PopupSheetState.showTvPairSheet(tv: LinkPeer?, viewModel: TvLinkViewModel) {
    viewModel.resetPairing()
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        TvPairSheetContent(tv = tv, viewModel = viewModel, onClose = { sheet?.dismiss() })
    }
}

@Composable
private fun ColumnScope.TvPairSheetContent(tv: LinkPeer?, viewModel: TvLinkViewModel, onClose: () -> Unit) {
    val state by viewModel.pairing.collectAsState()
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val target = tv ?: address.trim().takeIf { it.isNotEmpty() }?.let {
        LinkPeer(id = "", name = it, host = it.substringBefore(':'), port = it.substringAfter(':', "").toIntOrNull() ?: LinkWire.TvPairPort)
    }
    val codeFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { (if (tv == null) addressFocus else codeFocus).requestFocus() }
        keyboard?.show()
    }
    // a wrong code clears the boxes, ready for the next try
    LaunchedEffect(state) {
        if (state == PairUi.WrongCode) code = ""
        if (state is PairUi.Done) keyboard?.hide()
    }

    AnimatedContent(
        targetState = state is PairUi.Done,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "tvPairSheet",
    ) { done ->
        if (done) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF5BD68A).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color(0xFF5BD68A), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.tv_link_paired, (state as? PairUi.Done)?.tvName.orEmpty()),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(20.dp))
                SheetPrimaryButton(stringResource(R.string.tv_done), onClick = onClose)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                SheetLabel(
                    if (tv != null) stringResource(R.string.tv_pair_sheet_title, tv.name) else stringResource(R.string.tv_pair_sheet_by_address),
                    Modifier.padding(start = 4.dp),
                )
                Text(
                    stringResource(if (tv != null) R.string.tv_pair_sheet_detail else R.string.tv_pair_sheet_address_detail),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp),
                )
                if (tv == null) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it.filter { char -> char.isDigit() || char == '.' || char == ':' }.take(21) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BoxColor)
                            .focusRequester(addressFocus)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        decorationBox = { inner ->
                            if (address.isEmpty()) Text(stringResource(R.string.tv_pair_address_hint), color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp, fontFamily = InterFontFamily)
                            inner()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                CodeBoxes(
                    code = code,
                    focus = codeFocus,
                    onCode = { typed ->
                        code = typed
                        val peer = target
                        if (typed.length == CodeLength && peer != null && state != PairUi.Working) viewModel.pair(peer, typed)
                    },
                )
                Spacer(Modifier.height(14.dp))
                val note = when (state) {
                    PairUi.Working -> stringResource(R.string.tv_link_pairing)
                    PairUi.WrongCode -> stringResource(R.string.tv_link_wrong_code)
                    PairUi.Failed -> stringResource(R.string.tv_link_failed)
                    else -> null
                }
                Text(
                    note.orEmpty(),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = if (state == PairUi.WrongCode || state == PairUi.Failed) Color(0xFFFF8A80) else Muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).height(18.dp),
                )
            }
        }
    }
}

// six boxes the typed code fills in, over a hidden field that takes the keyboard
@Composable
private fun CodeBoxes(code: String, focus: FocusRequester, onCode: (String) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = code,
            onValueChange = { typed -> onCode(typed.filter(Char::isDigit).take(CodeLength)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().height(1.dp).alpha(0f).focusRequester(focus),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { runCatching { focus.requestFocus() } },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(CodeLength) { index ->
                val digit = code.getOrNull(index)
                val active = index == code.length
                val ring by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "codeBox")
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BoxColor)
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f + 0.65f * ring), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        digit?.toString().orEmpty(),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private const val CodeLength = 6
