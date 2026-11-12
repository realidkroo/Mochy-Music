package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.audio.pass.PassBlocker
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.screens.update.SheetPrimaryButton
import com.mochyy.mochymusic.ui.theme.InterFontFamily

// asked right when passthrough goes on while something still changes the sound, so nobody has to find the card further down to learn why nothing happened
fun PopupSheetState.showPassBlockersOffer(blockers: Set<PassBlocker>, onTurnOff: () -> Unit) {
    val popup = this
    show(
        buttonBar = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetPrimaryButton(
                    text = stringResource(R.string.audiopass_blockers_button),
                    onClick = {
                        onTurnOff()
                        popup.dismiss()
                    },
                )
                SheetPrimaryButton(text = stringResource(R.string.audiopass_offer_keep), onClick = popup::dismiss)
            }
        },
    ) {
        PassBlockersOfferContent(blockers)
    }
}

@Composable
private fun PassBlockersOfferContent(blockers: Set<PassBlocker>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.audiopass_offer_title),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp,
            letterSpacing = (-0.6).sp, color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.audiopass_offer_body),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp,
            color = LabColors.Secondary,
        )
        Spacer(Modifier.height(14.dp))
        PassBlocker.entries.filter { it in blockers }.forEach { blocker ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(blocker.label),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
