// the "support mochy's development" sheet: the auto prompt, help us! on the version sheet, and support me on about app all open this

package com.mochyy.mochymusic.ui.screens.donate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.GithubRepoUrl
import com.mochyy.mochymusic.ui.component.PopupSheetHandleHeight
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.screens.update.openUrl

// the band ends around her chest, above where her fade starts (56%), or the letters show through her
private const val BandEndsOnCharacter = 0.53f

/** shows the support sheet, donate us! pushes the donate sheet on top of it */
fun PopupSheetState.showSupportSheet() {
    val popup = this
    show(fullBleed = true, topFade = false, buttonBar = { SupportButtons(popup) }) {
        SupportSheetContent()
    }
}

@Composable
private fun SupportSheetContent() {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val characterWidth = characterWidth(maxWidth, maxHeight)
        val bandHeight = PopupSheetHandleHeight + CharacterTop + characterWidth * CharacterAspect * BandEndsOnCharacter

        NameMarquee(
            modifier = Modifier
                // starts up behind the handle so the blue runs right to the sheet's top edge, and takes no room of its own
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, bandHeight.roundToPx()))
                    layout(constraints.maxWidth, 0) { placeable.place(0, -PopupSheetHandleHeight.roundToPx()) }
                }
                // rides along when a short screen has to scroll, read at draw time so scrolling never recomposes
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(CharacterTop))
            DonateCharacter(width = characterWidth)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.supplemental_support_enjoying),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.supplemental_support_title),
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.supplemental_support_body),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupportButtons(popup: PopupSheetState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DonatePillButton(
                text = stringResource(R.string.supplemental_support_donate),
                onClick = { popup.showDonateSheet() },
                modifier = Modifier.weight(1f),
            )
            DonatePillButton(
                text = stringResource(R.string.supplemental_support_star),
                onClick = { context.openUrl(GithubRepoUrl) },
                modifier = Modifier.weight(1f),
            )
        }
        DonatePillButton(
            text = stringResource(R.string.supplemental_support_dismiss),
            onClick = popup::dismiss,
            quiet = true,
        )
    }
}
