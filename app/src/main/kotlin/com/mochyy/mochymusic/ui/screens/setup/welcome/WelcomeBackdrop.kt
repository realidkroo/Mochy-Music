// the welcome page's backdrop: a dot grid with giant outlined welcomes drifting past

package com.mochyy.mochymusic.ui.screens.setup.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

internal object WelcomeColors {
    val Page = Color(0xFF1C1C1C)
    val Dot = Color(0xFF2F2F2F)
    val Outline = Color(0xFF343434)
}

// the dot grid, drawn as one batch of points that never changes
@Composable
internal fun WelcomeDots(spacing: Dp, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val step = spacing.toPx()
                val dot = 1.6.dp.toPx()
                val width = size.width
                val height = size.height
                val points = buildList {
                    var y = step / 2f
                    while (y < height) {
                        var x = step / 2f
                        while (x < width) {
                            add(Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                onDrawBehind {
                    drawPoints(points, PointMode.Points, WelcomeColors.Dot, strokeWidth = dot, cap = StrokeCap.Round)
                }
            },
    )
}

// rows of giant outlined welcomes in twenty languages, each row drifting the other way to the one above
@Composable
internal fun WelcomeWords(seconds: FloatState, fontSize: Dp, firstRowTop: Dp, modifier: Modifier = Modifier) {
    val words = stringArrayResource(R.array.welcome_words).toList()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val family = InterFontFamily
    // twenty big lines with script fallbacks take a while to lay out, so it happens off the main thread
    val layouts by produceState<List<TextLayoutResult>?>(null, words, fontSize, density, family) {
        value = withContext(Dispatchers.Default) {
            val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 0)
            val style = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { fontSize.toSp() },
                letterSpacing = (-0.02).em,
            )
            words.map { measurer.measure(it, style, TextOverflow.Clip, softWrap = false, maxLines = 1) }
        }
    }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(layouts != null) {
        if (layouts != null) fade.animateTo(1f, tween(700))
    }
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .drawWithCache {
                val list = layouts
                val font = fontSize.toPx()
                val gap = font * 0.45f
                val pitch = font * 0.92f
                val top = firstRowTop.toPx()
                val outline = Stroke(width = 1.dp.toPx())
                val rows = if (list == null) 0 else ceil((size.height - top) / pitch).toInt().coerceAtLeast(1)
                // each row starts somewhere else in the list, so no two rows say the same word at once
                val order = Array(rows) { row -> IntArray(words.size) { (it + row * 7) % words.size } }
                val starts = Array(rows) { FloatArray(words.size) }
                var length = 0f
                if (list != null) {
                    for (row in 0 until rows) {
                        var x = 0f
                        for (i in order[row].indices) {
                            starts[row][i] = x
                            x += list[order[row][i]].size.width + gap
                        }
                        length = x
                    }
                }
                val speed = font * 0.18f
                onDrawBehind {
                    val t = seconds.floatValue
                    if (list == null || length <= 0f) return@onDrawBehind
                    for (row in 0 until rows) {
                        val direction = if (row % 2 == 0) 1f else -1f
                        val shift = ((t * speed * direction + row * length * 0.37f) % length + length) % length
                        val y = top + row * pitch
                        for (i in order[row].indices) {
                            val layout = list[order[row][i]]
                            val w = layout.size.width
                            // the strip repeats, so a word can show a loop before or after its own place too
                            val home = starts[row][i] - shift
                            for (loop in -1..1) {
                                val x = home + loop * length
                                if (x > size.width || x + w < 0f) continue
                                drawText(layout, color = WelcomeColors.Outline, topLeft = Offset(x, y), drawStyle = outline)
                            }
                        }
                    }
                }
            },
    )
}
