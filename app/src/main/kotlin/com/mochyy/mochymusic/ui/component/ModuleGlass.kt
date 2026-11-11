// a look module's own glass for the nav bar, the wide bar and the mini player: what's under the bar, blurred, then the module's agsl over it (docs/notes/module-glass.md)

package com.mochyy.mochymusic.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.modules.skin.ActiveGlass
import kotlin.math.ceil

/** the effect a bar last built from a glass, kept while nothing it depends on changes */
private class GlassEffectCache {
    var glass: ActiveGlass? = null
    var width = -1f
    var height = -1f
    var margin = -1
    var low = false
    var chrome = 0
    var accent = 0
    var effect: RenderEffect? = null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun ModuleChromeGlass(
    state: GlassState,
    glass: ActiveGlass,
    pureBlack: Boolean,
    accentWash: (() -> Color)?,
    surface: ChromeSurface,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    val low = LocalBlurQuality.current == BlurQuality.LOW
    // low quality blurs a little less, the shader hears the quality and can skip its own extras
    val blurPx = with(density) { glass.blurDp.dp.toPx() } * (if (low) 0.7f else 1f)
    // the shader may read this far past the bar, and the blur needs room past that so the edge isn't made up
    val margin = ceil(with(density) { glass.marginDp.dp.toPx() } + blurPx * 2f + 2f).toInt()
    val chrome = (if (pureBlack) Color.Black else GlassChromeColor).toArgb()
    var position by remember { mutableStateOf(Offset.Zero) }
    val cache = remember { GlassEffectCache() }

    Box(
        modifier = modifier
            .bleed { margin }
            .onGloballyPositioned { position = it.positionInWindow() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    if (cache.effect == null || cache.glass !== glass || cache.width != size.width || cache.height != size.height ||
                        cache.margin != margin || cache.low != low || cache.chrome != chrome || cache.accent != accent
                    ) {
                        cache.effect = glassEffect(glass, size, margin.toFloat(), density.density, low, chrome, accent, surface.kind, blurPx)
                        cache.glass = glass
                        cache.width = size.width
                        cache.height = size.height
                        cache.margin = margin
                        cache.low = low
                        cache.chrome = chrome
                        cache.accent = accent
                    }
                    renderEffect = cache.effect
                },
        ) {
            val node = state.renderNode ?: return@Canvas
            if (!glassNodeHasContent(node)) return@Canvas
            translate(left = -(position.x - state.rootPosition.x), top = -(position.y - state.rootPosition.y)) {
                drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
            }
            // a coloured page's wash goes in under the shader, so the glass bends it like everything else
            val wash = accentWash?.invoke()
            if (wash != null && wash.alpha > 0f) drawRect(wash.copy(alpha = wash.alpha * 0.6f))
        }
    }
}

// mochy's uniforms first, then the module's own; a name the shader doesn't declare is skipped, so a shader only takes what it uses
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun glassEffect(
    glass: ActiveGlass,
    size: Size,
    margin: Float,
    density: Float,
    low: Boolean,
    chrome: Int,
    accent: Int,
    kind: Float,
    blur: Float,
): RenderEffect {
    val shader = glass.shader
    fun set(block: () -> Unit) = runCatching(block)
    set { shader.setAgslUniform("size", size.width, size.height) }
    set { shader.setAgslUniform("margin", margin) }
    set { shader.setAgslUniform("radius", (size.height / 2f - margin).coerceAtLeast(1f)) }
    set { shader.setAgslUniform("density", density) }
    set { shader.setAgslUniform("quality", if (low) 0f else 1f) }
    set { shader.setAgslColorUniform("chrome", chrome) }
    set { shader.setAgslColorUniform("accent", accent) }
    set { shader.setAgslUniform("kind", kind) }
    glass.uniforms.forEach { (name, value) -> set { shader.setAgslUniform(name, value) } }
    val lens = agslRenderEffect(shader, "content")
    // the blur goes first, then the shader works on the blurred picture
    val effect = if (blur > 0.5f) {
        android.graphics.RenderEffect.createChainEffect(
            lens,
            android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.DECAL),
        )
    } else lens
    return effect.asComposeRenderEffect()
}
