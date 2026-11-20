// MorphingSongInfo.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.player.models.TrackInfo

private val TargetX = 36.dp + 60.dp + 18.dp

/** In the header the title stops this far from the screen's right edge: the cover's margin on the left. */
private val HeaderEndMargin = LyricsHeaderArtX

/** A title too long for the header fades out over this much of its right end. */
private val TitleEndFade = 20.dp

// the queue page's heart chip and a gap before it, kept clear of the title (its 34dp, 10dp of gap, from its own edge margin to the title's)
private val ChipRoom = 34.dp + 10.dp + com.mochyy.mochymusic.constants.PlayerHorizontalPadding - LyricsHeaderArtX

// with the chip there the fade runs longer, so the title has faded out well before it
private val ChipEndFade = 32.dp

// the player row's own edge fade, so the flying title lands looking exactly like the one it hands over to
private val RowEndFade = 10.dp

private val CollapsedTitleSize = 18.sp
private val CollapsedArtistSize = 14.sp

@Composable
fun MorphingSongInfo(
    trackInfo: TrackInfo,
    lyricsProgressProvider: () -> Float,
    sourceRectProvider: () -> Rect?,
    targetY: Dp,
    /** the screen's width, for how far the header title may run */
    screenWidth: Dp,
    modifier: Modifier = Modifier,
    // the sheet's progress, only to skip composing this while collapsed; the player content around it does the fading
    sheetProgressProvider: () -> Float = { 1f },
    /**
     * The player's own title hides the moment this starts and this takes its place, drawn exactly
     * like it, so it reads as that same title moving up into the header. Off (the Turntable, whose
     * title is set differently) it fades in over the other one instead.
     */
    handOff: Boolean = true,
    // how much the header's trailing chip (the queue page's heart) is there, 0 to 1; read while drawing
    endRoom: () -> Float = { 0f },
) {
    // Whether it can be seen at all. Decided in a derived state so the providers aren't read here:
    // the source rect moves with every frame of a sheet slide, and reading it in composition
    // recomposed this on every one of those frames.
    //
    // It no longer waits for the player's title to have been measured. That rect is only taken on
    // the fully open player, and it can be clipped flat (when the controls slide away under the
    // lyrics), so the header sometimes showed the cover with no title next to it.
    val shown by remember {
        derivedStateOf { lyricsProgressProvider() > 0f && sheetFade(sheetProgressProvider()) > 0f }
    }
    if (!shown) return

    val density = LocalDensity.current
    val targetXPx = with(density) { TargetX.toPx() }
    val targetYPx = with(density) { targetY.toPx() }

    // The lines keep the player's sizes and shrink by drawing smaller, not by laying their text out
    // again at a new size on every frame of the move. Laid out wide enough that, at the header's
    // size, they still reach its end.
    val titleStyle = MaterialTheme.typography.titleLarge
    val artistStyle = MaterialTheme.typography.titleMedium
    val titleEndScale = CollapsedTitleSize.value / titleStyle.fontSize.value
    val artistEndScale = CollapsedArtistSize.value / artistStyle.fontSize.value
    // From the title's start to the cover's margin on the right, whatever the player's own title
    // measured.
    val headerWidth = (screenWidth - TargetX - HeaderEndMargin).coerceAtLeast(0.dp)
    val headerWidthPx = with(density) { headerWidth.toPx() }
    val layoutWidth = maxOf(headerWidth / minOf(titleEndScale, artistEndScale), screenWidth)
    val window = remember { TitleWindow() }

    Column(
        modifier = modifier
            .width(layoutWidth)
            .graphicsLayer {
                val progress = lyricsProgressProvider()
                val source = sourceRectProvider()?.takeIf { it.width > 1f && it.height > 1f }
                // flies from the player's title; with no usable rect for it, it fades in in place
                alpha = if (handOff && source != null) 1f else (progress / 0.35f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = if (source != null) androidx.compose.ui.util.lerp(source.left, targetXPx, progress) else targetXPx
                translationY = if (source != null) androidx.compose.ui.util.lerp(source.top, targetYPx, progress) else targetYPx
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // what shows of the lines: as wide as the player's title at the start, the
                    // header's width at the end, fading out over its last few dp
                    val progress = lyricsProgressProvider().coerceIn(0f, 1f)
                    val sourceWidth = sourceRectProvider()?.width?.takeIf { it > 1f } ?: headerWidthPx
                    val chip = endRoom().coerceIn(0f, 1f)
                    val headerEnd = headerWidthPx - ChipRoom.toPx() * chip
                    val shownWidth = androidx.compose.ui.util.lerp(sourceWidth, headerEnd, progress)
                    clipRect(right = shownWidth) { this@drawWithContent.drawContent() }
                    val headerFade = androidx.compose.ui.util.lerp(TitleEndFade.toPx(), ChipEndFade.toPx(), chip)
                    val fadePx = androidx.compose.ui.util.lerp(RowEndFade.toPx(), headerFade, progress)
                    drawRect(
                        brush = window.fadeAt(shownWidth, fadePx),
                        size = size.copy(width = shownWidth),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            Text(
                text = trackInfo.title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                color = Color.White,
                // as wide as the header holds once shrunk, so a title too long for it still scrolls
                modifier = Modifier
                    .width(headerWidth / titleEndScale)
                    .graphicsLayer {
                        val scale = androidx.compose.ui.util.lerp(1f, titleEndScale, lyricsProgressProvider().coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2500,
                        repeatDelayMillis = 2500,
                        velocity = 26.dp,
                    ),
            )
            if (trackInfo.artist.isNotBlank()) {
                Text(
                    text = trackInfo.artist,
                    style = artistStyle,
                    maxLines = 1,
                    softWrap = false,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .width(headerWidth / artistEndScale)
                        .graphicsLayer {
                            val progress = lyricsProgressProvider().coerceIn(0f, 1f)
                            val scale = androidx.compose.ui.util.lerp(1f, artistEndScale, progress)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 2500,
                            repeatDelayMillis = 2500,
                            velocity = 26.dp,
                        ),
                )
            }
        }
    }
}

/** The fade at the end of the title's window, built again only when the window's width changes. */
private class TitleWindow {
    private var width = -1f
    private var brush: Brush = Brush.horizontalGradient(listOf(Color.Black, Color.Black))

    private var fade = -1f

    fun fadeAt(shownWidth: Float, fadePx: Float): Brush {
        if (shownWidth != width || fadePx != fade) {
            width = shownWidth
            fade = fadePx
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startX = (shownWidth - fadePx).coerceAtLeast(0f),
                endX = shownWidth.coerceAtLeast(1f),
            )
        }
        return brush
    }
}

/** The player sheet's own content fade (BottomSheet's pill branch), so the title goes with it. */
private fun sheetFade(sheetProgress: Float): Float = ((sheetProgress.coerceIn(0f, 1f) - 0.25f) / 0.60f).coerceIn(0f, 1f)

