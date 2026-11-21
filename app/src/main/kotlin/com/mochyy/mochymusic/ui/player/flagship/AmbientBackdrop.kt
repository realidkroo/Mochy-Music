package com.mochyy.mochymusic.ui.player.flagship

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.component.setAgslUniform
import com.mochyy.mochymusic.ui.component.setAgslUniformArray
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// one shader for every ambient backdrop: each draw sets its own numbers first, so nothing compiles on a song change
private val sharedAmbientShader: Any? by lazy {
    if (Build.VERSION.SDK_INT >= 33) createAgslShader(AmbientShaderSource) else null
}

// the clock's loop, in seconds: every drift in the shader turns a whole number of times in it, so the wrap never shows
private const val LoopSeconds = 100f

// the colours are soft pools, so a quarter of the pixels scaled back up look the same at a sixteenth of the work
private const val Downscale = 4

// the drift is slow, so 30 steps a second look like 120 and redraw (and re-blur the frosted fade over it) a quarter as often
private const val StepsPerSecond = 30f

// the cover's colours drifting under the player; time is in seconds and loops every 100
@Composable
fun AmbientBackdrop(
    colors: AmbientColors,
    modifier: Modifier = Modifier,
    time: () -> Float,
) {
    val currentTime by rememberUpdatedState(time)
    // only tells the drawing when the next step is due, however often the clock itself ticks
    val step by remember { derivedStateOf { floor(currentTime() * StepsPerSecond) } }
    val shader = sharedAmbientShader
    Spacer(
        modifier = modifier
            .downscaledLayer()
            .then(
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    Modifier.drawWithCache {
                        // the same shader every frame, only its numbers change, so drawing allocates nothing
                        val brush = ShaderBrush(shader.asAgslPaintShader())
                        val grid = FloatArray(GridSide * GridSide * 4)
                        onDrawBehind {
                            colors.gridInto(grid)
                            shader.setAgslUniform("resolution", size.width, size.height)
                            shader.setAgslUniform("time", step / StepsPerSecond)
                            shader.setAgslUniformArray("grid", grid)
                            drawRect(brush)
                        }
                    }
                } else {
                    Modifier.drawWithCache {
                        val deep = colors.tone(ToneDeep)
                        val radius = size.maxDimension * 0.55f
                        // built again only when new colours are handed out, then they only move
                        val blobs = listOf(
                            colors.tone(ToneSeamLeft),
                            colors.tone(ToneSeamRight),
                            colors.tone(ToneAccent),
                        ).map { tone ->
                            Brush.radialGradient(
                                colors = listOf(tone, tone.copy(alpha = 0f)),
                                center = Offset.Zero,
                                radius = radius,
                            )
                        }
                        onDrawBehind {
                            drawRect(deep)
                            val t = step / StepsPerSecond * (2f * PI.toFloat() / LoopSeconds)
                            blobs.forEachIndexed { i, brush ->
                                val cx = size.width * (0.25f + 0.5f * (i % 2) + 0.14f * sin(t * (i + 1) + i * 1.7f))
                                val cy = size.height * (0.55f + 0.18f * i / 2f + 0.08f * cos(t * (i + 2) + i))
                                translate(cx, cy) {
                                    drawCircle(brush, radius = radius, center = Offset.Zero)
                                }
                            }
                        }
                    }
                }
            ),
    )
}

// lays the content out a quarter the size and draws it into a small layer of its own, scaled back up to fill the space
internal fun Modifier.downscaledLayer(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val small = measurable.measure(
        Constraints.fixed(
            ((width + Downscale - 1) / Downscale).coerceAtLeast(1),
            ((height + Downscale - 1) / Downscale).coerceAtLeast(1),
        )
    )
    layout(width, height) {
        small.placeWithLayer(0, 0) {
            scaleX = Downscale.toFloat()
            scaleY = Downscale.toFloat()
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

// paced to the shader's 30 hz step so 120 hz screens aren't woken four times per actual redraw
private const val AmbientWakeMillis = 30L

// its own clock for a backdrop with no player clock to share (a selector card, an alternate style's page)
@Composable
fun rememberAmbientClock(running: Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { nanos ->
                time.floatValue = ((nanos / 1_000_000L) % (LoopSeconds * 1000f).toLong()) / 1000f
            }
            kotlinx.coroutines.delay(AmbientWakeMillis)
        }
    }
    return { time.floatValue }
}

// the cover's grid of colours spread over the screen, each cell a soft pool in its own place, the whole picture slowly turning and flowing like apple music's backdrop
private const val AmbientShaderSource = """
uniform float2 resolution;
uniform float time;
uniform float4 grid[16];

const float TAU = 6.2831853;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float t = time * TAU / 100.0;

    // the picture fills the screen and turns a little each way about its middle, breathing in and out
    float2 c = uv - 0.5;
    c.x *= resolution.x / max(resolution.y, 1.0);
    float a = 0.32 * sin(t) + 0.12 * sin(t * 3.0 + 1.1);
    float s = 1.0 + 0.07 * sin(t * 2.0 + 0.6);
    c = float2(c.x * cos(a) - c.y * sin(a), c.x * sin(a) + c.y * cos(a)) / s;

    // a slow liquid warp, so the colours flow past each other instead of sliding as one sheet
    c += 0.06 * float2(sin(c.y * 4.5 + t * 2.0), cos(c.x * 4.0 - t * 3.0));
    c += 0.035 * float2(sin((c.x + c.y) * 7.0 - t * 4.0), cos((c.x - c.y) * 6.0 + t * 5.0));

    // grid coordinates, each cell's centre on a whole number
    float2 g = (c + 0.5) * 4.0 - 0.5;
    float3 col = float3(0.0);
    float sum = 0.0001;
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float row = floor(fi / 4.0);
        float2 d = g - float2(fi - row * 4.0, row);
        float w = exp(-dot(d, d) * 1.25);
        col += grid[i].rgb * w;
        sum += w;
    }
    col /= sum;

    col *= 1.0 - 0.16 * smoothstep(0.6, 1.0, uv.y);
    float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""
