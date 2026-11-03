// LarpModeSheet.kt

package com.mochyy.mochymusic.ui.screens.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.LarpLevel
import com.mochyy.mochymusic.ui.player.larpQualityText
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LarpAspect = 220f / 287f

// 67, at the level picked here
fun PopupSheetState.showLarpSheet(current: LarpLevel, onLarp: (LarpLevel) -> Unit) {
    // shared by the levels and the pinned button, which compose apart
    val picked = mutableStateOf(current)
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.larp_sheet_button),
                onClick = {
                    onLarp(picked.value)
                    sheet?.dismiss()
                },
            )
        },
    ) {
        LarpSheetContent(picked)
    }
}

@Composable
private fun LarpSheetContent(picked: MutableState<LarpLevel>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            // the picture and four levels are taller than a small phone's sheet
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        LarpPicture(
            Modifier
                .fillMaxWidth(0.44f)
                .aspectRatio(LarpAspect),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.larp_sheet_title),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.03).em,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.larp_sheet_body),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = (-0.02).em,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        LarpLevels(picked)
    }
}

@Composable
private fun LarpLevels(picked: MutableState<LarpLevel>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.larp_sheet_levels), Modifier.padding(start = 4.dp, bottom = 4.dp))
        for (level in LarpLevel.entries) {
            SheetOptionRow(
                title = stringResource(level.title),
                subtitle = stringResource(R.string.larp_level_summary, level.ordinal + 1, larpQualityText(level), stringResource(level.dac)),
                subtitleLines = 2,
                onClick = { picked.value = level },
                leading = { ChoiceMark(picked.value == level) },
            )
        }
    }
}

// larplarp
@Composable
private fun LarpPicture(modifier: Modifier) {
    val context = LocalContext.current
    val description = stringResource(R.string.larp_sheet_image)
    // decoded off the main thread the sheet's spring shouldn't wait on a gif
    val picture by produceState<Drawable?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { decodeLarp(context) }
    }
    DisposableEffect(picture) {
        onDispose { (picture as? Animatable)?.stop() }
    }
    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = description
            }
        },
        update = { view ->
            if (view.drawable !== picture) {
                view.setImageDrawable(picture)
                (picture as? Animatable)?.start()
            }
        },
        modifier = modifier,
    )
}

private fun decodeLarp(context: Context): Drawable? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeAnimatedLarp(context)
    } else {
        BitmapFactory.decodeResource(context.resources, R.raw.larp_mode)?.let { BitmapDrawable(context.resources, it) }
    }
}.getOrNull()

// no limit to the larp, go larp 4ever
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedLarp(context: Context): Drawable {
    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, R.raw.larp_mode))
    (drawable as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    return drawable
}
