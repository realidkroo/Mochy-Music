package com.mochyy.mochymusic.ui.player.styles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.player.LyricsHeaderArtSize
import com.mochyy.mochymusic.ui.player.LyricsHeaderArtTopFromStatusBar
import com.mochyy.mochymusic.ui.player.LyricsHeaderArtX
import com.mochyy.mochymusic.ui.player.LyricsScreen
import com.mochyy.mochymusic.ui.player.QueueScreen
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding

/**
 * The song's whole lyrics over a style, from its menu: the flagship's own lyrics page on the
 * player's own background (the one the flagship's lyrics sit on, not the style seen through a
 * veil), with the cover and title where the flagship's header sits. Tapping the cover or the back
 * gesture closes it. [onCoveredChange] says when it fills the screen, so the style under it can
 * stop drawing.
 */
@Composable
fun StyleLyricsPage(
    open: Boolean,
    onClose: () -> Unit,
    screenHeight: Dp,
    modifier: Modifier = Modifier,
    onCoveredChange: (Boolean) -> Unit = {},
    onSwitchSource: (() -> Unit)? = null,
) {
    StyleOverlayPage(open = open, onClose = onClose, modifier = modifier, onCoveredChange = onCoveredChange) { nav ->
        LyricsScreen(
            onClose = onClose,
            screenHeight = screenHeight,
            contentBottomInset = nav + 28.dp,
            onSwitchSource = onSwitchSource,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// the queue over the poster, from its chevron menu: the same page as the lyrics, with the flagship's queue list in it
@Composable
fun StyleQueuePage(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCoveredChange: (Boolean) -> Unit = {},
) {
    StyleOverlayPage(open = open, onClose = onClose, modifier = modifier, onCoveredChange = onCoveredChange) { nav ->
        QueueScreen(
            onClose = onClose,
            contentBottomInset = nav + 28.dp,
            controlsClearance = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// a page that rises over a style on the player's own background, with the cover and title where the flagship's header sits; [content] gets the nav bar's height
@Composable
private fun StyleOverlayPage(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier,
    onCoveredChange: (Boolean) -> Unit,
    content: @Composable (nav: Dp) -> Unit,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(open) {
        shown.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = if (open) 260f else 420f))
    }
    BackHandler(enabled = open, onBack = onClose)
    val currentOnCovered by androidx.compose.runtime.rememberUpdatedState(onCoveredChange)
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { shown.value >= 0.999f }
            .collect { currentOnCovered(it) }
    }
    if (!open && shown.value < 0.001f) return

    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val backgroundStyle by com.mochyy.mochymusic.utils.rememberEnumPreference(
        com.mochyy.mochymusic.constants.PlayerBackgroundStyleKey,
        com.mochyy.mochymusic.constants.PlayerBackgroundStyle.COVER_GRADIENT,
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        // the player's own background, as under the flagship's lyrics: nothing of the style shows through
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) },
        ) {
            com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview(
                style = backgroundStyle,
                thumbnailUrl = track.thumbnailUrl,
                width = pageWidth,
                height = pageHeight,
                animate = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = shown.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 72.dp.toPx()
                },
        ) {
            // where the flagship's cover morphs to on its lyrics page; the page's own hit box over it closes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .stableStatusBarsPadding()
                    .padding(start = LyricsHeaderArtX, top = LyricsHeaderArtTopFromStatusBar, end = 24.dp),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(LyricsHeaderArtSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.03).em, color = Color.White),
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.02).em, color = Color.White.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
            content(nav)
        }
    }
}
