// SpeedDialHero.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.utils.resize

// the concept's tile grid: tiles this share of the hero's width, leaning up to the right
private const val TileShare = 0.44f
private const val TileGapShare = 0.05f
private const val TileTilt = -17f
private const val TileColumns = 4
private const val TileRows = 4

// where the middle of the grid sits, as a share of the hero, so the brightest tiles land above the words
private const val GridCenterX = 0.56f
private const val GridCenterY = 0.36f

// the concept's blank tile, shown until a cover loads
private val TileBlank = Color(0xFFD9D9D9)

// the hero's own scrims dim this card already, so the wash keeps more of the covers' colour than a card does
private val HeroTintDarkening = floatArrayOf(0.3f, 0.45f, 0.6f)

private val SummonTitleStyle: TextStyle
    get() = HomeTitleStyle.copy(fontSize = 36.sp, lineHeight = 36.sp)

/** the hero card that opens your speed dial: its covers tilted over a wash of their own colours */
@Immutable
data class SpeedDialHeroItem(val covers: List<String>) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("Speed Dial", textRes = R.string.speed_dial_label)
    override val mainText: String = "Summon speed dial"
    override val subText: String = ""
    override val thumbnailUrl: String? = null
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override val opensPage: Boolean = true
    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) = Unit
}

@Composable
internal fun SpeedDialHeroPage(
    item: SpeedDialHeroItem,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    transitionBlur: Boolean,
    onSummon: () -> Unit,
) {
    val peek = LocalHeroPeek.current
    PageWipe(pageOffset = pageOffset, curve = wipeCurve, blur = transitionBlur, peek = peek) {
        // the whole card summons, not just the pill; the pill above it still takes its own taps
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onSummon)
                .pageMediaDrift(pageOffset, peek)
                .coverTintBackground(item.covers.take(2), seed = "speed_dial", darkening = HeroTintDarkening)
        ) {
            SpeedDialTiles(item.covers)
        }

        HeroScrims()

        Box(
            modifier = Modifier
                .matchParentSize()
                .pageTextDrift(pageOffset, peek)
        ) {
            SummonChevrons(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 92.dp)
                    .offset(x = 18.dp)
            )
            HeroText(
                label = null,
                title = stringResource(R.string.speed_dial_summon_title),
                pill = HeroPillKind.Summon,
                onPillClick = onSummon,
                titleStyle = SummonTitleStyle,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

// a tilted wall of the speed dial's covers, overflowing the card on every side
@Composable
private fun SpeedDialTiles(covers: List<String>) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tile = maxWidth * TileShare
        val gap = maxWidth * TileGapShare
        val gridWidth = tile * TileColumns + gap * (TileColumns - 1)
        val gridHeight = tile * TileRows + gap * (TileRows - 1)
        val shape = RoundedCornerShape(tile * 0.14f)
        // a painter rather than a background, so a tile is one draw whether its cover is in or not
        val blank = remember { ColorPainter(TileBlank) }
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                // bigger than the card on purpose: required size lets it spill out, centred
                .requiredSize(gridWidth, gridHeight)
                .offset(x = maxWidth * (GridCenterX - 0.5f), y = maxHeight * (GridCenterY - 0.5f))
                .graphicsLayer { rotationZ = TileTilt }
        ) {
            repeat(TileRows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(TileColumns) { column ->
                        // a short speed dial repeats, staggered a step per row so the same cover never stacks
                        val url = covers[(row * TileColumns + column + row) % covers.size]
                        AsyncImage(
                            model = url.resize(360, 360),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = blank,
                            error = blank,
                            fallback = blank,
                            modifier = Modifier
                                .size(tile)
                                .clip(shape),
                        )
                    }
                }
            }
        }
    }
}

// the two soft arrows at the right of the concept, pointing on toward the sheet; built once per size, not per frame
@Composable
private fun SummonChevrons(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(width = 96.dp, height = 60.dp)
            .drawWithCache {
                val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.42f),
                    1f to Color.White.copy(alpha = 0.08f),
                )
                val arm = size.height / 2f - stroke.width
                val reach = arm * 0.8f
                val chevrons = Path().apply {
                    listOf(size.width * 0.3f, size.width * 0.72f).forEach { tipX ->
                        moveTo(tipX - reach, size.height / 2f - arm)
                        lineTo(tipX, size.height / 2f)
                        lineTo(tipX - reach, size.height / 2f + arm)
                    }
                }
                onDrawBehind { drawPath(chevrons, brush, style = stroke) }
            }
    )
}
