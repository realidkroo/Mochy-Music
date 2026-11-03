package com.mochyy.mochymusic.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositing
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

// one separable gaussian pass whose radius grows along the blur (maxRadius * t^2, full ramp of the way along); taps past the picture get no weight, and preSigma of blur already in it is taken off
private const val VariableBlurSource = """
uniform shader content;
uniform float2 dir;
uniform float2 size;
uniform float2 origin;
uniform float2 full;
uniform float maxRadius;
uniform float growDown;
uniform float across;
uniform float ramp;
uniform float preSigma;

half4 main(float2 p) {
    float2 g = (p + origin) / full;
    float along = clamp(mix(g.y, g.x, across), 0.0, 1.0);
    if (growDown < 0.5) { along = 1.0 - along; }
    float t = clamp(along / ramp, 0.0, 1.0);
    float r = maxRadius * t * t;
    float sigma = 0.57735 * r + 0.5;
    float rest = sigma * sigma - preSigma * preSigma;
    if (r < 0.5 || rest < 0.25) { return content.eval(p); }
    float stepPx = sqrt(rest) * 0.25;
    float4 sum = float4(0.0);
    float total = 0.0;
    for (int i = -12; i <= 12; i++) {
        float2 q = p + dir * (float(i) * stepPx);
        float inside = step(0.0, q.x) * step(q.x, size.x) * step(0.0, q.y) * step(q.y, size.y);
        float w = exp(-0.03125 * float(i * i)) * inside;
        sum += float4(content.eval(q)) * w;
        total += w;
    }
    return half4(sum / max(total, 0.0001));
}
"""

// compiled once on first use; every effect built from it keeps the uniforms it was built with
private object VariableBlurShader {
    var shader: Any? = null
}

// the pre-blur against the taps' spacing: the taps sit a quarter sigma apart, and anything finer than that came out as a grid of stripes
private const val PreBlurOfSigma = 0.15f

// below this (in the band's own pixels) the taps are close enough that nothing aliases
private const val MinPreSigma = 0.6f

// band edges land on whole pixels at every resolution, so the bands' pixels line up where they meet
private const val BandGrid = 4f

private const val MaxBands = 3

// one stretch of the blur, in px along it from the sharp end: shown over start..end, read over captureStart..captureEnd so no tap runs out of picture
private class BlurBand {
    var k = 1
    var start = 0f
    var end = 0f
    var captureStart = 0f
    var captureEnd = 0f
    var preSigma = 0f
}

// the bands for one length and radius, worked out again only when either changes
private class BandPlan {
    val bands = Array(MaxBands) { BlurBand() }
    var count = 0
    private var length = -1f
    private var radius = -1f
    private var ramp = -1f
    private var low = false

    fun update(length: Float, radius: Float, ramp: Float, low: Boolean) {
        if (length == this.length && radius == this.radius && ramp == this.ramp && low == this.low) return
        this.length = length
        this.radius = radius
        this.ramp = ramp
        this.low = low
        val rampLength = max(ramp * length, 1f)
        fun radiusAt(a: Float): Float {
            val t = (a / rampLength).coerceIn(0f, 1f)
            return radius * t * t
        }
        fun reach(r: Float, k: Int, preSigma: Float): Float {
            val sigma = 0.57735f * r / k + 0.5f
            return (3f * sigma + 3f * preSigma + 1f) * k
        }

        // where the resolution steps up, from glassDownscale itself so both always agree
        val starts = FloatArray(MaxBands)
        val ks = IntArray(MaxBands)
        count = 1
        starts[0] = 0f
        ks[0] = glassDownscale(0f, low)
        for (step in StepRadii) {
            if (count == MaxBands || step >= radius) break
            val k = glassDownscale(step, low)
            if (k == ks[count - 1]) continue
            val at = (rampLength * sqrt(step / radius) / BandGrid).roundToInt() * BandGrid
            if (at <= starts[count - 1] || at >= length) continue
            starts[count] = at
            ks[count] = k
            count++
        }

        for (i in 0 until count) {
            val band = bands[i]
            band.k = ks[i]
            band.start = starts[i]
            band.end = if (i + 1 < count) starts[i + 1] else length
            val strongest = 0.57735f * radiusAt(band.end) / band.k + 0.5f
            band.preSigma = (strongest * PreBlurOfSigma).takeIf { it >= MinPreSigma } ?: 0f
            var before = 0f
            var after = 0f
            for (j in 0..16) {
                val a = band.start + (band.end - band.start) * j / 16f
                val r = reach(radiusAt(a), band.k, band.preSigma)
                before = max(before, r - (a - band.start))
                after = max(after, a + r - band.end)
            }
            band.captureStart = (kotlin.math.floor((band.start - before) / BandGrid) * BandGrid).coerceAtLeast(0f)
            band.captureEnd = (ceil((band.end + after) / BandGrid) * BandGrid).coerceAtMost(length)
        }
    }

    private companion object {
        val StepRadii = floatArrayOf(8f, 16f, 32f, 64f)
    }
}

// the last effect built for a band, reused while nothing it was built from has moved
private class BandEffect {
    var k = 0
    var preSigma = 0f
    var maxRadius = 0f
    var full = Size.Zero
    var layerSize = IntSize.Zero
    var origin = Offset.Zero
    var growDown = false
    var across = false
    var ramp = 0f
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

// downscaledblur's growing blur in up to three bands, each at the resolution its own radius needs, so the sharp end is never a blown-up quarter-size picture
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun ProgressiveBlurBands(
    radius: () -> Float,
    mask: Brush?,
    modifier: Modifier,
    offscreen: (() -> Boolean)?,
    growDown: Boolean,
    across: Boolean,
    ramp: Float,
    draw: DrawScope.(fullSize: Size) -> Unit,
) {
    val currentRadius by rememberUpdatedState(radius)
    val currentOffscreen by rememberUpdatedState(offscreen)
    val currentDraw by rememberUpdatedState(draw)
    val low = LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.LOW
    val layers = arrayOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    // hwui hands the clip a blurred layer is drawn under to its filter, which then reads nothing past it: each band's blur sits in a frame of its own, and only the frame is clipped
    val frames = arrayOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    val plan = remember { BandPlan() }
    val effects = remember { Array(MaxBands) { BandEffect() } }

    Box(modifier = modifier.clipToBounds()) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val full = size
                    val length = if (across) full.width else full.height
                    if (length <= 0f || full.minDimension <= 0f) return@drawBehind
                    val r = currentRadius()
                    // rounded the way BlurEffectCache rounds, at the coarsest resolution the blur reaches
                    val coarsest = glassDownscale(r, low)
                    val maxRadius = if (r > 0.5f) BlurEffectCache.quantize(r / coarsest) * coarsest else 0f
                    plan.update(length, maxRadius, ramp, low)
                    val isOffscreen = currentOffscreen?.invoke() == true

                    for (i in 0 until plan.count) {
                        val band = plan.bands[i]
                        val k = band.k.toFloat()
                        // along the blur from the sharp end, turned into this box's own coordinates, on whole pixels so the layer sits exactly where it was read from
                        val from = (if (growDown) band.captureStart else length - band.captureEnd).roundToInt().toFloat()
                        val to = if (growDown) band.captureEnd else length - band.captureStart
                        val shownFrom = if (growDown) band.start else length - band.end
                        val shownTo = if (growDown) band.end else length - band.start
                        val left = if (across) from else 0f
                        val top = if (across) 0f else from
                        val width = if (across) to - from else full.width
                        val height = if (across) full.height else to - from
                        val layerSize = IntSize(
                            ceil(width / k).toInt().coerceAtLeast(1),
                            ceil(height / k).toInt().coerceAtLeast(1),
                        )

                        val layer = layers[i]
                        layer.record(layerSize) {
                            scale(1f / k, 1f / k, pivot = Offset.Zero) {
                                translate(-left, -top) { currentDraw(full) }
                            }
                        }
                        layer.renderEffect = effects[i].effectFor(
                            band, maxRadius, full, layerSize, Offset(left / k, top / k), growDown, across, ramp, mask,
                        )
                        layer.clip = true
                        layer.compositingStrategy = if (isOffscreen) LayerCompositing.Offscreen else LayerCompositing.Auto

                        val frame = frames[i]
                        frame.record(layerSize) { drawLayer(layer) }
                        frame.compositingStrategy = LayerCompositing.Offscreen
                        frame.pivotOffset = Offset.Zero
                        frame.scaleX = k
                        frame.scaleY = k
                        frame.topLeft = IntOffset(left.roundToInt(), top.roundToInt())
                        frame.clip = true

                        // only the band's own stretch shows; the rest of its picture is there for the taps
                        if (across) {
                            clipRect(left = shownFrom, top = 0f, right = shownTo, bottom = full.height) { drawLayer(frame) }
                        } else {
                            clipRect(left = 0f, top = shownFrom, right = full.width, bottom = shownTo) { drawLayer(frame) }
                        }
                    }
                },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun BandEffect.effectFor(
    band: BlurBand,
    maxRadius: Float,
    full: Size,
    layerSize: IntSize,
    origin: Offset,
    growDown: Boolean,
    across: Boolean,
    ramp: Float,
    mask: Brush?,
): RenderEffect {
    val cached = effect
    if (cached != null && k == band.k && preSigma == band.preSigma && this.maxRadius == maxRadius && this.full == full &&
        this.layerSize == layerSize && this.origin == origin && this.growDown == growDown && this.across == across &&
        this.ramp == ramp && this.mask == mask
    ) return cached
    k = band.k
    preSigma = band.preSigma
    this.maxRadius = maxRadius
    this.full = full
    this.layerSize = layerSize
    this.origin = origin
    this.growDown = growDown
    this.across = across
    this.ramp = ramp
    this.mask = mask
    return variableBlurEffect(band, maxRadius, full, layerSize, origin, growDown, across, ramp, mask).also { effect = it }
}

// the band's blur: a light pre-blur so the spaced taps can't alias, then across, then down, then the mask (dst in)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun variableBlurEffect(
    band: BlurBand,
    maxRadius: Float,
    full: Size,
    layerSize: IntSize,
    origin: Offset,
    growDown: Boolean,
    across: Boolean,
    ramp: Float,
    mask: Brush?,
): RenderEffect {
    val k = band.k.toFloat()
    val shader = VariableBlurShader.shader ?: createAgslShader(VariableBlurSource).also { VariableBlurShader.shader = it }
    shader.setAgslUniform("size", layerSize.width.toFloat(), layerSize.height.toFloat())
    shader.setAgslUniform("origin", origin.x, origin.y)
    shader.setAgslUniform("full", full.width / k, full.height / k)
    shader.setAgslUniform("maxRadius", maxRadius / k)
    shader.setAgslUniform("growDown", if (growDown) 1f else 0f)
    shader.setAgslUniform("across", if (across) 1f else 0f)
    shader.setAgslUniform("ramp", ramp.coerceIn(0.01f, 1f))
    shader.setAgslUniform("preSigma", band.preSigma)
    shader.setAgslUniform("dir", 1f, 0f)
    val acrossPass = agslRenderEffect(shader, "content")
    shader.setAgslUniform("dir", 0f, 1f)
    val downPass = agslRenderEffect(shader, "content")
    val firstPass = if (band.preSigma > 0f) {
        // the platform blur takes a radius, sigma = 0.57735 * radius + 0.5
        val preRadius = (band.preSigma - 0.5f) / 0.57735f
        val pre = android.graphics.RenderEffect.createBlurEffect(preRadius, preRadius, android.graphics.Shader.TileMode.CLAMP)
        android.graphics.RenderEffect.createChainEffect(acrossPass, pre)
    } else {
        acrossPass
    }
    val blurred = android.graphics.RenderEffect.createChainEffect(downPass, firstPass)
    val maskShader = (mask as? ShaderBrush)?.createShader(Size(full.width / k, full.height / k))
    val effect = if (maskShader != null) {
        // the mask is drawn for the whole box, moved so this band's piece of it sits under the band
        maskShader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-origin.x, -origin.y) })
        android.graphics.RenderEffect.createBlendModeEffect(
            blurred,
            android.graphics.RenderEffect.createShaderEffect(maskShader),
            android.graphics.BlendMode.DST_IN,
        )
    } else {
        blurred
    }
    return effect.asComposeRenderEffect()
}
