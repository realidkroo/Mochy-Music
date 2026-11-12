package com.mochyy.mochymusic.ui.screens.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandleHeight
import com.mochyy.mochymusic.ui.component.PopupSheetState

// the concept's grey, a touch lighter than the version sheet under it
private val PathSurface = Color(0xFF1F1F1F)
private val PathSoftText = Color.White.copy(alpha = 0.6f)

// the picture's own shape (401 x 427), drawn the sheet's full width
private const val ArtAspect = 427f / 401f

// the sign stays readable behind the title, the road goes dark under it and melts into the sheet
private val ArtShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.3f),
    0.44f to Color.Black.copy(alpha = 0.38f),
    0.47f to Color.Black.copy(alpha = 0.62f),
    0.8f to PathSurface.copy(alpha = 0.85f),
    1f to PathSurface,
)

// "path change": the meme across the top, are you sure, then yes or no; switching back to release reuses it with its own words
fun PopupSheetState.showPathChangeSheet(toNightly: Boolean, onConfirm: () -> Unit) {
    val popup = this
    show(
        fullBleed = true,
        surface = PathSurface,
        buttonBar = {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetPrimaryButton(
                    text = stringResource(R.string.channel_yes),
                    onClick = {
                        onConfirm()
                        popup.dismiss()
                    },
                )
                SheetPrimaryButton(text = stringResource(R.string.channel_no), onClick = popup::dismiss)
            }
        },
    ) {
        PathChangeContent(toNightly)
    }
}

@Composable
private fun PathChangeContent(toNightly: Boolean) {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val artHeight = maxWidth * ArtAspect
        Image(
            painter = painterResource(R.drawable.path_change),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                // starts up behind the handle so the picture runs right to the sheet's top edge, and takes no room of its own
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, artHeight.roundToPx()))
                    layout(constraints.maxWidth, 0) { placeable.place(0, -PopupSheetHandleHeight.roundToPx()) }
                }
                // rides along when a short screen has to scroll, read at draw time so scrolling never recomposes
                .graphicsLayer { translationY = -scroll.value.toFloat() }
                .drawWithContent {
                    drawContent()
                    drawRect(ArtShade)
                },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 34.dp),
        ) {
            // the title over the picture, and the question right where the picture has faded out
            Column(Modifier.heightIn(min = artHeight - PopupSheetHandleHeight).padding(top = 38.dp, bottom = 20.dp)) {
                Text(
                    stringResource(if (toNightly) R.string.channel_nightly_title else R.string.channel_release_title),
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (toNightly) R.string.channel_nightly_tagline else R.string.channel_release_tagline),
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    color = PathSoftText,
                )
            }
            Text(
                stringResource(R.string.channel_nightly_question),
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.4).sp,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(if (toNightly) R.string.channel_nightly_warning else R.string.channel_release_warning),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                color = PathSoftText,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
