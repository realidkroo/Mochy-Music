// ShareCanvas.kt
//
// The Cinematic lyrics picture: the words straight on the background, word by word, laid out on
// the share's fixed 360 x 640 picture with the font scale held at 1, so it looks the same on every
// phone and in every export. The song share (share/song) puts it over its background.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.sqrt

internal val ShareWidth = 360.dp
internal val ShareHeight = 640.dp

/**
 * The Cinematic editor outlines the song and the words; these are where they sit on the picture,
 * in its own coordinates, updated as they move. Null until laid out.
 */
@Stable
internal class ShareRegions {
    var header by mutableStateOf<Rect?>(null)
    var words by mutableStateOf<Rect?>(null)
}

// The Cinematic layout, on the 360 x 640 picture
private val CinematicSide = 34.dp
private val CinematicTop = 70.dp
private val CinematicMarkBottom = 44.dp
private val CinematicMarkGap = 28.dp
private val CinematicHeaderGap = 22.dp
private val CinematicLineGap = 2.dp
private const val CinematicTextSize = 23f
private const val CinematicSmallestText = 12f

/** Room the words get when the song sits with them: the picture less the margins, the mark and the song. */
private val CinematicWordsRoom = ShareHeight - CinematicTop - CinematicMarkBottom - 16.dp - CinematicMarkGap - 38.dp - CinematicHeaderGap

private fun shareText(size: Float, weight: FontWeight = FontWeight.Bold, color: Color, tracking: Float = -0.5f) =
    TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

@Composable
internal fun MochyMark(color: Color, size: Float = 14f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_mochy_mark),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size((size + 2f).dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Mochy", style = shareText(size, color = color, tracking = -0.4f))
    }
}

// the cover, the title and the artist; a cover already in memory is drawn straight away, so an export never waits on it
@Composable
private fun SongHeader(song: ShareSong, color: Color, cover: Float, title: Float, modifier: Modifier = Modifier, art: ImageBitmap? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        val coverModifier = Modifier
            .size(cover.dp)
            .clip(RoundedCornerShape((cover * 0.22f).dp))
            .background(color.copy(alpha = 0.2f))
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = coverModifier)
        } else {
            AsyncImage(
                model = song.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = coverModifier,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(song.title, style = shareText(title, color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist,
                style = shareText(title * 0.78f, FontWeight.SemiBold, color.copy(alpha = 0.75f), -0.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The cinematic style: the words straight on the background, each one as its look says - bigger,
 * in capitals, glowing. Lines sit on their own, so a big word only pushes its own line apart. The
 * words shrink to fit when there are many, the Mochy mark always keeps its place at the bottom,
 * and the song slides past the words when it moves above or below them.
 */
@Composable
internal fun CinematicLayout(
    session: LyricsShareSession,
    regions: ShareRegions?,
    ink: Color,
    cover: ImageBitmap? = null,
    // in a video the words light up as they're sung; null keeps them all lit for a picture
    flow: CinematicFlow? = null,
    // the reading and the translation under each picked line, as the share's switches want them
    readings: List<List<String>> = emptyList(),
) {
    val textSize = rememberCinematicTextSize(session, readings)
    val below = animateFloatAsState(
        if (session.headerBelow) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 240f),
        label = "cinematicHeader",
    )
    Layout(
        contents = listOf(
            {
                SongHeader(
                    session.song,
                    ink,
                    cover = 38f,
                    title = 14f,
                    modifier = if (regions != null) Modifier.onGloballyPositioned { regions.header = it.boundsInParent() } else Modifier,
                    art = cover,
                )
            },
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CinematicLineGap),
                    modifier = if (regions != null) Modifier.onGloballyPositioned { regions.words = it.boundsInParent() } else Modifier,
                ) {
                    session.chosen.forEachIndexed { i, (line, text) ->
                        val style = shareText(textSize, color = ink, tracking = -1f)
                        if (flow == null) {
                            Text(cinematicLine(session, line, text, ink, textSize), style = style)
                        } else {
                            SungLine(session, line, text, ink, textSize, style, flow)
                        }
                        readings.getOrNull(i)?.forEach { reading ->
                            Text(reading, style = readingText(textSize, ink))
                        }
                    }
                }
            },
            { MochyMark(ink) },
        ),
        modifier = Modifier.fillMaxSize(),
    ) { (headerPart, wordsPart, markPart), constraints ->
        val side = CinematicSide.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth - 2 * side)
        val header = headerPart.first().measure(loose)
        val words = wordsPart.first().measure(loose)
        val mark = markPart.first().measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val markTop = constraints.maxHeight - CinematicMarkBottom.roundToPx() - mark.height
            val gap = CinematicHeaderGap.roundToPx()
            val areaTop = CinematicTop.roundToPx()
            val areaBottom = markTop - CinematicMarkGap.roundToPx()
            val block = header.height + gap + words.height
            val top = areaTop + ((areaBottom - areaTop - block) / 2).coerceAtLeast(0)
            // read here, so the slide only places things again and never recomposes
            val p = below.value
            words.place(side, lerp(top + header.height + gap, top, p))
            header.placeWithLayer(side, lerp(top, top + words.height + gap, p)) {
                // dips back a little as it passes the words, so it reads as going round them
                val dip = 4f * p * (1f - p)
                alpha = 1f - 0.45f * dip
                scaleX = 1f - 0.08f * dip
                scaleY = 1f - 0.08f * dip
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            mark.place(side, markTop)
        }
    }
}

/**
 * The biggest text size, up to the usual one, at which every picked line fits above the mark. Up
 * to 40 words with big ones mixed in can't all sit at full size.
 */
@Composable
private fun rememberCinematicTextSize(session: LyricsShareSession, readings: List<List<String>>): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chosen = session.chosen
    val looks = session.looks.toMap()
    return remember(chosen, looks, readings, density) {
        val width = with(density) { (ShareWidth - CinematicSide * 2).roundToPx() }
        val room = with(density) { CinematicWordsRoom.toPx() }
        val lineGap = with(density) { CinematicLineGap.toPx() }
        fun heightAt(size: Float): Float = chosen.mapIndexed { i, (line, text) ->
            measurer.measure(
                cinematicLine(session, line, text, Color.White, size),
                style = shareText(size, color = Color.White, tracking = -1f),
                constraints = Constraints(maxWidth = width),
            ).size.height +
                readings.getOrNull(i).orEmpty().sumOf { measurer.measure(it, readingText(size, Color.White), constraints = Constraints(maxWidth = width)).size.height }
        }.sum() + lineGap * (chosen.size - 1).coerceAtLeast(0)

        val full = heightAt(CinematicTextSize)
        if (full <= room) return@remember CinematicTextSize
        // text area grows with the square of its size, so this lands close; then step down to be sure
        var size = (CinematicTextSize * sqrt(room / full)).coerceAtLeast(CinematicSmallestText)
        while (size > CinematicSmallestText && heightAt(size) > room) size -= 1f
        size
    }
}

// a reading or a translation under a cinematic line: half the size, softer, no bold
private fun readingText(base: Float, ink: Color) =
    shareText(base * CinematicReadingShare, weight = FontWeight.Medium, color = ink.copy(alpha = ink.alpha * CinematicReadingAlpha), tracking = -0.3f)

private const val CinematicReadingShare = 0.5f
private const val CinematicReadingAlpha = 0.72f

internal fun cinematicLine(
    session: LyricsShareSession,
    line: Int,
    text: String,
    ink: Color,
    base: Float = CinematicTextSize,
): AnnotatedString =
    buildAnnotatedString {
        wordsOf(text).forEachIndexed { i, word ->
            if (i > 0) append(' ')
            val look = session.look(WordKey(line, i))
            withStyle(wordSpan(look, ink, base)) { append(if (look.caps) word.uppercase() else word) }
        }
    }

internal fun wordSpan(look: WordLook, ink: Color, base: Float = CinematicTextSize): SpanStyle = SpanStyle(
    fontSize = if (look.big) (base * 2.1f).sp else base.sp,
    fontWeight = if (look.big) FontWeight.ExtraBold else FontWeight.Bold,
    letterSpacing = if (look.big) (-2.6).sp else (-1).sp,
    shadow = if (look.glow) Shadow(ink.copy(alpha = 0.9f * ink.alpha), Offset.Zero, blurRadius = 30f) else null,
)
