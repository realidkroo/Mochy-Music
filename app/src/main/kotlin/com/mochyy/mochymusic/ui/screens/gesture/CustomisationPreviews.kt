// CustomisationPreviews.kt

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.HomeStyle
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlaylistStyle
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors

internal val PreviewWidth = 133.dp

private val FrameShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
internal val FrameFill = Color(0xFF2B2B2D)
internal val ArtFill = Brush.verticalGradient(listOf(Color(0xFF636365), Color(0xFF3F3F41)))
private val SoftArtFill = Brush.verticalGradient(listOf(Color(0xFF545456), FrameFill))
private val ArtLight = Color(0xFF7C7C7E)
internal val Ink = Color.White.copy(alpha = 0.92f)
internal val Bone = Color.White.copy(alpha = 0.2f)
internal val BoneDim = Color.White.copy(alpha = 0.1f)
private val Pill = RoundedCornerShape(50)

// the little phone screen peeking up from a style card's bottom right, sinking into the card at its foot
@Composable
internal fun PreviewFrame(
    modifier: Modifier = Modifier,
    width: Dp = PreviewWidth,
    top: Dp = 23.dp,
    // the whole screen, lifted off the card's foot, for a preview whose point sits at its bottom edge
    floating: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(top = top, end = 18.dp, bottom = if (floating) 18.dp else 0.dp)
            .width(width)
            .fillMaxHeight()
            .clip(if (floating) FloatingFrameShape else FrameShape)
            .background(FrameFill)
            .then(if (floating) Modifier else SinkingFoot),
        content = content,
    )
}

private val FloatingFrameShape = RoundedCornerShape(15.dp)

private val SinkingFoot = Modifier.drawWithCache {
    val top = size.height * 0.72f
    val foot = Brush.verticalGradient(0f to Color.Transparent, 1f to LabColors.Card, startY = top, endY = size.height)
    onDrawWithContent {
        drawContent()
        drawRect(foot, topLeft = Offset(0f, top))
    }
}

@Composable
internal fun PlaylistStylePreview(style: PlaylistStyle, coverRadius: Dp) {
    when (style) {
        PlaylistStyle.FLOW -> Box(Modifier.fillMaxSize().background(ArtFill)) {
            TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 38.dp))
        }
        // the squared cover over a soft blur of itself
        PlaylistStyle.CLASSIC -> Column(Modifier.fillMaxSize().background(SoftArtFill).padding(top = 14.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(78.dp)
                    .background(ArtLight, RoundedCornerShape(scaledRadius(coverRadius, 0.28f))),
            )
            Spacer(Modifier.height(10.dp))
            TitleBars(Modifier.padding(start = 12.dp))
        }
        PlaylistStyle.MINIMAL -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 16.dp)) {
            Bar(56.dp, 7.dp)
            Spacer(Modifier.height(5.dp))
            Bar(26.dp, 8.dp)
            Spacer(Modifier.height(12.dp))
            repeat(4) {
                TrackRow(coverRadius)
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
internal fun PlayerStylePreview(style: PlayerStyle) {
    when (style) {
        PlayerStyle.IPOD -> IpodPreview()
        // the video across the card with a line of lyrics over it and the pane's bar at the bottom right
        PlayerStyle.VIDEO -> Box(Modifier.fillMaxSize().background(ArtFill)) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 22.dp)) {
                Bar(54.dp, 6.dp, BoneDim)
                Spacer(Modifier.height(5.dp))
                Bar(92.dp, 9.dp)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp)) { Bar(40.dp, 3.dp, BoneDim) }
        }
        // a big quiet time on black, the date small over it
        PlayerStyle.BEDSIDE -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
            Bar(46.dp, 5.dp, BoneDim)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(40.dp, 26.dp, Bone)
                Bar(5.dp, 5.dp, Bone)
                Bar(40.dp, 26.dp, Bone)
            }
        }
        // the cover on one side, a stacked clock on black on the other
        PlayerStyle.BEDSIDE_TILES -> Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(ArtFill)) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp)) {
                    Bar(64.dp, 7.dp)
                    Spacer(Modifier.height(6.dp))
                    Bar(PreviewWidth - 36.dp, 2.5.dp, Bone)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Bar(24.dp, 4.dp, BoneDim)
                Bar(40.dp, 22.dp, Bone)
                Bar(40.dp, 22.dp, Bone)
            }
        }
        PlayerStyle.LYRICS -> Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 18.dp),
        ) {
            Bar(70.dp, 7.dp, BoneDim)
            Bar(100.dp, 9.dp)
            Bar(86.dp, 9.dp)
            Bar(64.dp, 7.dp, BoneDim)
            Bar(92.dp, 7.dp, BoneDim)
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (style == PlayerStyle.TURNTABLE) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp).background(Bone, CircleShape)) {
                        Box(Modifier.size(26.dp).background(ArtLight, CircleShape))
                    }
                }
            } else {
                // the flagship's cover runs edge to edge across the top
                Box(Modifier.fillMaxWidth().height(96.dp).background(ArtFill))
            }
            Spacer(Modifier.height(10.dp))
            NowPlayingBars()
        }
    }
}

@Composable
internal fun HomeStylePreview(style: HomeStyle, coverRadius: Dp) {
    val radius = RoundedCornerShape(scaledRadius(coverRadius, 0.2f))
    when (style) {
        HomeStyle.MOCHYY -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(108.dp).background(ArtFill)) {
                TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp))
            }
            Spacer(Modifier.height(10.dp))
            Shelf(radius, Modifier.padding(start = 12.dp))
        }
        HomeStyle.LISTEN_NOW -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, top = 16.dp)) {
            Bar(62.dp, 6.dp)
            Spacer(Modifier.height(9.dp))
            repeat(3) {
                ListenRow(radius)
                Spacer(Modifier.height(7.dp))
            }
            Spacer(Modifier.height(4.dp))
            Bar(44.dp, 5.dp)
            Spacer(Modifier.height(6.dp))
            Shelf(radius)
        }
        HomeStyle.HOP_IN -> Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 16.dp)) {
            Bar(34.dp, 6.dp)
            Spacer(Modifier.height(9.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(2) { HopTile(radius, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.height(6.dp))
            Bar(44.dp, 5.dp)
            Spacer(Modifier.height(6.dp))
            Shelf(radius)
        }
    }
}

@Composable
private fun IpodPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp).background(Bone, CircleShape)) {
            Box(Modifier.size(24.dp).background(FrameFill, CircleShape))
        }
    }
}

// title and artist, the seek bar and the three buttons under a player's cover
@Composable
private fun NowPlayingBars() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TitleBars()
        Spacer(Modifier.height(8.dp))
        Bar(PreviewWidth, 2.5.dp, Bone)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.size(9.dp).background(Bone, CircleShape))
            Box(Modifier.size(14.dp).background(Ink, CircleShape))
            Box(Modifier.size(9.dp).background(Bone, CircleShape))
        }
    }
}

// a title and the line under it, like the mock's two white bars
@Composable
internal fun TitleBars(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Bar(42.dp, 4.5.dp)
        Bar(30.dp, 4.5.dp)
    }
}

@Composable
private fun TrackRow(coverRadius: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).background(Bone, RoundedCornerShape(scaledRadius(coverRadius, 0.12f))))
        Spacer(Modifier.width(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Bar(48.dp, 4.dp)
            Bar(30.dp, 3.dp, BoneDim)
        }
    }
}

@Composable
private fun ListenRow(shape: RoundedCornerShape) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(22.dp).background(Bone, shape))
        Spacer(Modifier.width(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Bar(52.dp, 4.dp)
            Bar(24.dp, 3.dp, BoneDim)
        }
        Box(Modifier.size(7.dp).background(Bone, CircleShape))
    }
}

@Composable
private fun HopTile(shape: RoundedCornerShape, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(24.dp).clip(shape).background(BoneDim),
    ) {
        Box(Modifier.size(24.dp).background(Bone))
        Spacer(Modifier.width(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Bar(18.dp, 3.5.dp)
            Bar(12.dp, 3.dp, BoneDim)
        }
    }
}

// a row of album squares, the third cut off at the edge like a real shelf
@Composable
private fun Shelf(shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        repeat(3) { Box(Modifier.size(34.dp).background(Bone, shape)) }
    }
}

@Composable
internal fun Bar(width: Dp, height: Dp, color: Color = Ink) {
    Box(Modifier.size(width, height).background(color, Pill))
}

// the real cover rounding shrunk to the preview's size, so the slider above shows up here too
private fun scaledRadius(coverRadius: Dp, scale: Float): Dp = (coverRadius * scale).coerceIn(0.dp, 14.dp)
