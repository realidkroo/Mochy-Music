// the sign-in browser sheet that opens inside the onboarding sheet, the page behind it recedes like a covered popup sheet

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.account.AccountService
import kotlinx.coroutines.launch

/** one spring for the sheet and the page behind it so the two never drift apart */
internal val BrowserSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow, visibilityThreshold = 0.0005f)

/** the browser bar is solid behind its text, then fades out over the top of the web page */
private val BarSolidHeight = 76.dp
private val BarFadeHeight = 28.dp

/** how far the page behind shrinks and how much of its top shows above the browser sheet */
internal const val BehindScale = 0.92f
internal val BrowserPeek = 26.dp

/** the browser sheet, presence goes 0 hidden to 1 open and the caller recedes the page behind with it */
@Composable
internal fun OnboardingBrowserSheet(
    service: AccountService,
    presence: Animatable<Float, AnimationVector1D>,
    onClose: () -> Unit,
    content: @Composable (onUrlChange: (String) -> Unit, onLoadingChange: (Boolean) -> Unit, modifier: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://") }
    var dragPx by remember { mutableStateOf(0f) }
    var sheetHeightPx by remember { mutableStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = BrowserPeek)
            .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationY = (1f - presence.value) * size.height + dragPx
            }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF1C1C1C)),
    ) {
        // the page starts right under the bar's solid part; the bar's fade then lies over the page
        content({ url = it }, { }, Modifier.fillMaxSize().padding(top = BarSolidHeight).navigationBarsPadding())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarSolidHeight + BarFadeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF4A4A4A),
                        BarSolidHeight / (BarSolidHeight + BarFadeHeight) to Color(0xFF3A3A3A),
                        1f to Color(0x003A3A3A),
                    )
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragPx > sheetHeightPx * 0.22f) {
                                onClose()
                            }
                            scope.launch {
                                val start = dragPx
                                androidx.compose.animation.core.animate(start, 0f, animationSpec = BrowserSpring) { v, _ -> dragPx = v }
                            }
                        },
                    ) { change, delta ->
                        change.consume()
                        dragPx = (dragPx + delta).coerceAtLeast(0f)
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(120.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
            }
            BrowserSheetBar(
                icon = { ProviderSquircle(service, size = 38.dp) },
                title = stringResource(R.string.onb_sign_in_to, service.label),
                url = url,
                onClose = onClose,
            )
        }
    }
}

/** the browser's bar: whose sign-in it is, the address it's on, and a close button; the module sign-in sheet shows the same one */
@Composable
internal fun BrowserSheetBar(
    icon: @Composable () -> Unit,
    title: String,
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 22.dp, end = 14.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                url.removePrefix("https://").removePrefix("www.").substringBefore('?').ifEmpty { "https://" },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.onb_close), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
