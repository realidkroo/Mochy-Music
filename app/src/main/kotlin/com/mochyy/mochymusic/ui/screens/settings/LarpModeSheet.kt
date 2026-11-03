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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LarpAspect = 220f / 287f

// 67
fun PopupSheetState.showLarpSheet(onLarp: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        LarpSheetContent(
            onLarp = {
                onLarp()
                sheet?.dismiss()
            },
        )
    }
}

@Composable
private fun LarpSheetContent(onLarp: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        LarpPicture(
            Modifier
                .fillMaxWidth(0.56f)
                .aspectRatio(LarpAspect),
        )
        Spacer(Modifier.height(28.dp))
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
        Spacer(Modifier.height(56.dp))
        SheetPillButton(text = stringResource(R.string.larp_sheet_button), onClick = onLarp)
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
