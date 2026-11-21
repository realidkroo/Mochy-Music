// the module store's gate: the welcome sheet with its terms for someone who hasn't signed in yet, and the page a fork gets instead of the store

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetHandleHeight
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.rememberResourcePainter
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.isLandscapeWindow
import kotlinx.coroutines.flow.first

/** bump it when the terms change, and everyone is asked again before their next visit */
internal const val StoreTermsVersion = 1

private const val ReleasesUrl = "https://github.com/realidkroo/Mochy-Music/releases"
private val WelcomeSurface = Color(0xFF1E1E1E)
private val TermsShape = RoundedCornerShape(28.dp)
private val ArtHeight = 210.dp
private val LandscapeArtHeight = 130.dp
// how far the title climbs into the art's faded bottom
private val TitleOverlap = 64.dp

/** a first visit before signing in: next only wakes up once the terms were read to the end, agrees and goes on to discord; swiping the sheet away leaves the store */
internal fun PopupSheetState.showStoreWelcome(name: String, onNext: () -> Unit, onNotNow: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    var agreed = false
    // shared by the words and the pinned button, which compose apart
    val scroll = ScrollState(0)
    sheet = showWithHandle(
        fullBleed = true,
        topFade = false,
        surface = WelcomeSurface,
        onClosed = { if (!agreed) onNotNow() },
        buttonBar = {
            val read = rememberReadToEnd(scroll)
            SheetPillButton(
                text = stringResource(if (read) R.string.module_store_next else R.string.module_store_read_first),
                enabled = read,
                onClick = {
                    agreed = true
                    onNext()
                    sheet?.dismiss()
                },
            )
        },
    ) {
        WelcomeContent(name, scroll)
    }
}

// true once the sheet has been scrolled to its end, and it stays true if they scroll back up to reread
@Composable
private fun rememberReadToEnd(scroll: ScrollState): Boolean {
    var read by remember { mutableStateOf(false) }
    val slack = with(LocalDensity.current) { 24.dp.roundToPx() }
    LaunchedEffect(scroll) {
        // maxValue is Int.MAX_VALUE until the words are laid out the first time
        snapshotFlow { scroll.maxValue != Int.MAX_VALUE && scroll.value >= scroll.maxValue - slack }.first { it }
        read = true
    }
    return read
}

@Composable
private fun WelcomeContent(name: String, scroll: ScrollState) {
    // a phone on its side shows the sheet as a short card, so the art gives the words more of it
    val artHeight = if (isLandscapeWindow()) LandscapeArtHeight else ArtHeight
    Box(Modifier.fillMaxWidth()) {
        WelcomeArt(
            Modifier
                // starts up behind the handle so the art runs to the sheet's top edge, and takes no room of its own
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, (PopupSheetHandleHeight + artHeight).roundToPx()))
                    layout(constraints.maxWidth, 0) { placeable.place(0, -PopupSheetHandleHeight.roundToPx()) }
                }
                // rides along with the words, read at draw time so scrolling never recomposes
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 26.dp)) {
            Spacer(Modifier.height(artHeight - TitleOverlap))
            Text(
                stringResource(R.string.module_store_welcome_title), color = Color.White, fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.2).sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.module_store_welcome_body, name.trim().ifBlank { stringResource(R.string.module_store_welcome_there) }),
                color = BrowserSoft, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(20.dp))
            StoreTerms()
            Spacer(Modifier.height(12.dp))
        }
    }
}

// the art melts into the sheet at the bottom, drawn straight over the picture with no extra layer
@Composable
private fun WelcomeArt(modifier: Modifier) {
    Image(
        rememberResourcePainter(R.drawable.module_store_welcome), null,
        contentScale = ContentScale.Crop,
        modifier = modifier.drawWithCache {
            val fade = Brush.verticalGradient(0.5f to Color.Transparent, 1f to WelcomeSurface)
            onDrawWithContent {
                drawContent()
                drawRect(fade)
            }
        },
    )
}

// the terms as a card: a heading and a few lines for each part, scrolling with the rest of the sheet
@Composable
private fun StoreTerms() {
    val parts = stringArrayResource(R.array.module_store_terms)
    Column(Modifier.fillMaxWidth().clip(TermsShape).background(BrowserCard).padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(stringResource(R.string.module_store_terms_intro), color = Color.White.copy(alpha = 0.8f), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        parts.toList().chunked(2).forEach { part ->
            Spacer(Modifier.height(14.dp))
            Text(part[0], color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            part.getOrNull(1)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** what a fork sees in place of the store: what it means for someone using it, and for someone building it */
@Composable
internal fun ForkNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(top = 26.dp).clip(RoundedCornerShape(30.dp)).background(BrowserCard).padding(22.dp)) {
        Icon(
            painter = painterResource(R.drawable.warning),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.module_store_fork_title), color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.module_store_fork_body), color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        ForkPart(stringResource(R.string.module_store_fork_users_title), stringResource(R.string.module_store_fork_users))
        ForkPart(stringResource(R.string.module_store_fork_devs_title), stringResource(R.string.module_store_fork_devs))
        Spacer(Modifier.height(18.dp))
        PillButton(text = stringResource(R.string.module_store_fork_get), filled = true, onClick = { context.openDevUrl(ReleasesUrl) })
    }
}

@Composable
private fun ForkPart(title: String, body: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Spacer(Modifier.height(4.dp))
    Text(body, color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
}
