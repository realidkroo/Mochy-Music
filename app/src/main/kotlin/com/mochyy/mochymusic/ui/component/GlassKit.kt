package com.mochyy.mochymusic.ui.component

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.utils.rememberPreference

@Stable
class GlassState {
    /**
     * The backdrop render node, deliberately typed [Any].
     *
     * `android.graphics.RenderNode` is API 29. Naming it in a property type puts it into this
     * class's accessor signatures, so ART has to resolve it as soon as an accessor is reached -
     * on Android 8 and 9 that is a `NoClassDefFoundError`, even though every real *use* of the
     * node is already version-guarded. Since the glass system backs the nav pill, the player and
     * most screens, that turns into crashes all over the app on older devices.
     *
     * Every touch goes through the guarded helpers below, which cast internally.
     */
    var renderNode by mutableStateOf<Any?>(null)
    var rootPosition by mutableStateOf(Offset.Zero)
}

/*
 * RenderNode helpers. Each is annotated so it is only ever entered above the API level that
 * defines the class, which keeps the reference out of any method reachable on older devices.
 */

@RequiresApi(Build.VERSION_CODES.Q)
internal fun glassNodeFor(existing: Any?, width: Int, height: Int): Any =
    (existing as? RenderNode ?: RenderNode("GlassRoot")).apply { setPosition(0, 0, width, height) }

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeHasContent(node: Any): Boolean = (node as RenderNode).hasDisplayList()

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeBeginRecording(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeEndRecording(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeWidth(node: Any): Int = (node as RenderNode).width

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeHeight(node: Any): Int = (node as RenderNode).height

@RequiresApi(Build.VERSION_CODES.S)
internal fun android.graphics.Canvas.drawGlassNode(node: Any) {
    drawRenderNode(node as RenderNode)
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNode(width: Int, height: Int): Any =
    RenderNode("DamageBoundary").apply {
        setPosition(0, 0, width, height)
        // the whole point: the renderer only bounds a change by a node's size when the node clips
        // to it. Compose's own layers clip by outline instead, so to the renderer a change in any
        // of them is a change to the entire screen
        setClipToBounds(true)
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeBeginRecording(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeEndRecording(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun android.graphics.Canvas.drawDamageNode(node: Any) {
    drawRenderNode(node as RenderNode)
}

/**
 * Keeps whatever animates inside this from counting as a change to the rest of the screen.
 *
 * To the renderer, a change inside a Compose layer is a change to everything (Compose's layers clip
 * by outline, and the renderer only bounds a change by a node's size when it clips to bounds).
 * So a few bouncing equaliser bars, the hero's video or a marquee repainted the whole window on
 * every one of their frames, and made every glass surface that samples the app (the nav bar, the
 * mini player) render and blur its backdrop again although nothing under it had moved. Recorded
 * into a node that clips to its own bounds, a change in here only dirties this rectangle.
 *
 * Content is clipped to the bounds, so only use it where nothing draws outside them.
 */
fun Modifier.damageBoundary(): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        this
    } else {
        this.drawWithCache {
            val width = size.width.toInt()
            val height = size.height.toInt()
            val node = if (width > 0 && height > 0) damageNode(width, height) else null
            onDrawWithContent {
                val target = drawContext.canvas
                if (node == null || !target.nativeCanvas.isHardwareAccelerated) {
                    drawContent()
                    return@onDrawWithContent
                }
                drawContext.canvas = Canvas(damageNodeBeginRecording(node))
                try {
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    damageNodeEndRecording(node)
                }
                target.nativeCanvas.drawDamageNode(node)
            }
        }
            // a plain layer inside, so content that draws itself repaints only this rectangle
            .graphicsLayer()
    }

fun Modifier.glassRoot(state: GlassState, isActive: () -> Boolean = { true }): Modifier = this
    .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
    .drawWithCache {
        val width = size.width.toInt()
        val height = size.height.toInt()

        val node: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            glassNodeFor(state.renderNode, width, height)
        } else null

        state.renderNode = node

        onDrawWithContent {
            val drawContextCanvas = drawContext.canvas

            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isActive()) {
                val nativeCanvas = glassNodeBeginRecording(node)
                val composeCanvas = Canvas(nativeCanvas)

                drawContext.canvas = composeCanvas
                drawContent()

                drawContext.canvas = drawContextCanvas
                glassNodeEndRecording(node)

                if (glassNodeHasContent(node)) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
                }
            } else {
                drawContent()
            }
        }
    }

/**
 * Backdrop colour for the floating glass chrome - the navigation pill and the mini player.
 *
 * Deliberately a fixed dark tone rather than `MaterialTheme.colorScheme.surfaceContainer`: the
 * Material You colour follows the wallpaper accent and washes these surfaces out. They are drawn
 * with white icons over a white hairline border, so they are meant to read as dark glass. The
 * shade is the Figma's.
 */
val GlassChromeColor = Color(0xFF131313)

/** Backdrop blur for that chrome, heavier than the 24f default so it reads as properly frosted. */
const val GlassChromeBlurRadius = 48f

/** how strongly a coloured page's accent washes over the chrome glass, over its usual dark tint */
private const val ChromeAccentWash = 0.55f

/**
 * the open page's accent (a coloured playlist or artist) as a wash for the nav pill and the mini
 * player. it eases in and out by itself, and it's only read while drawing: a page change repaints
 * the chrome, never recomposes it
 */
@Composable
fun rememberChromeAccentWash(): () -> Color {
    val accent = com.mochyy.mochymusic.LocalDetailAccentColor.current
    val wash = remember { androidx.compose.animation.Animatable(Color.Transparent) }
    LaunchedEffect(accent) {
        // fading out keeps the page's hue instead of greying through transparent black
        var hue = Color.Black
        androidx.compose.runtime.snapshotFlow { accent.value }.collect { color ->
            if (color != null) hue = color
            if (wash.value.alpha == 0f) wash.snapTo(hue.copy(alpha = 0f))
            wash.animateTo(
                hue.copy(alpha = if (color != null) ChromeAccentWash else 0f),
                androidx.compose.animation.core.tween(500),
            )
        }
    }
    return remember(wash) { { wash.value } }
}

/**
 * How far below full resolution a glass surface draws what's under it before blurring.
 *
 * A blur this wide removes everything a full-resolution picture of the backdrop adds - Skia
 * itself shrinks the picture by halves before blurring a large radius - so drawing the backdrop
 * at half (or quarter) size and blurring it by a half (or quarter) radius gives the same glass.
 * What it saves is drawing all of the backdrop that sits under the glass at full resolution, plus
 * the first passes of the blur, every frame the glass or what's under it moves. Small radii keep
 * full resolution: there the detail still shows.
 */
internal fun glassDownscale(effectiveRadius: Float, low: Boolean = false): Int = when {
    effectiveRadius >= 64f -> 4
    // low quality drops a step sooner: a little softer, a quarter of the pixels to draw and blur
    low && effectiveRadius >= 32f -> 4
    effectiveRadius >= 16f -> 2
    low && effectiveRadius >= 8f -> 2
    else -> 1
}

/** The blur quality chosen in Appearance, for every glass surface under it. */
val LocalBlurQuality = androidx.compose.runtime.staticCompositionLocalOf { DefaultBlurQuality }

// flagship chips that don't always declare a performance class; anything else waits for one
private val StrongGlassChips = listOf(
    "sm8350", "sm8450", "sm8475", "sm8550", "sm8635", "sm8650", "sm8750", // snapdragon 888 up to 8 elite
    "mt6983", "mt6985", "mt6989", "mt6991", // dimensity 9000 up to 9400
)

/** the quality a phone starts on until someone picks one in Customisation */
val DefaultBlurQuality: com.mochyy.mochymusic.constants.BlurQuality by lazy {
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase(java.util.Locale.ROOT) else ""
    // high's glass took 11 to 15 ms of gpu per scroll frame on a mid-range 120 hz phone, more than a frame has
    val strong = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (Build.VERSION.MEDIA_PERFORMANCE_CLASS >= Build.VERSION_CODES.S || StrongGlassChips.any { soc.startsWith(it) })
    if (strong) com.mochyy.mochymusic.constants.BlurQuality.HIGH else com.mochyy.mochymusic.constants.BlurQuality.LOW
}

/** Lays its content out [margin] px bigger on every side, centred over the space it takes. */
private fun Modifier.bleed(margin: () -> Int): Modifier = layout { measurable, constraints ->
    val m = margin().coerceAtLeast(0)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    val placeable = measurable.measure(Constraints.fixed(width + m * 2, height + m * 2))
    layout(width, height) { placeable.place(-m, -m) }
}

/** The size a downscaled layout was given, before it was divided down. */
private class FullSize {
    var width = 0
    var height = 0
}

/** Lays its content out at 1/[downscale] of the space it takes, from the top left. */
private fun Modifier.downscaledLayout(downscale: () -> Int, full: FullSize): Modifier = layout { measurable, constraints ->
    val k = downscale().coerceAtLeast(1)
    val fullWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val fullHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    full.width = fullWidth
    full.height = fullHeight
    val placeable = measurable.measure(
        Constraints.fixed((fullWidth + k - 1) / k, (fullHeight + k - 1) / k)
    )
    layout(fullWidth, fullHeight) { placeable.place(0, 0) }
}

/** The last masked glass effect, reused while its radius, size and mask stay the same. */
private class MaskedEffectCache {
    var radius = -1f
    var width = -1f
    var height = -1f
    var tileOrdinal = -1
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

/**
 * The blur and the mask in one effect: the blurred content kept only where [mask] is opaque
 * (DstIn) - what drawing the glass into an offscreen layer and DstIn-ing the mask over it did,
 * without that extra layer.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun maskedBlurEffect(radius: Float, tileMode: android.graphics.Shader.TileMode, mask: android.graphics.Shader): RenderEffect {
    val content = if (radius > 0.5f) {
        android.graphics.RenderEffect.createBlurEffect(radius, radius, tileMode)
    } else {
        android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    }
    return android.graphics.RenderEffect.createBlendModeEffect(
        content,
        android.graphics.RenderEffect.createShaderEffect(mask),
        android.graphics.BlendMode.DST_IN,
    ).asComposeRenderEffect()
}

/**
 * Whatever [draw] draws, blurred by [radius] (the radius actually applied - see
 * [BlurEffectCache.effectiveRadius]) and optionally faded by [mask] (DstIn).
 *
 * Drawn at the resolution [glassDownscale] picks for the radius: into a layer 1/k the size, blurred
 * by radius/k and scaled back up - see [glassDownscale] for why that's the same picture for less
 * work. [draw] works in full-size coordinates and gets the full size; the scaling is done here.
 */
@Composable
internal fun DownscaledBlur(
    radius: () -> Float,
    tileMode: android.graphics.Shader.TileMode?,
    mask: Brush?,
    modifier: Modifier = Modifier,
    /** Draw into a buffer of its own even without a blur, for a [draw] that erases with blend modes. */
    offscreen: (() -> Boolean)? = null,
    // set to grow the blur from sharp to radius across the height (true: strongest at the bottom), android 13+ only
    growDown: Boolean? = null,
    // grows across the width instead (growDown true: strongest at the right), full strength ramp of the way along
    growAcross: Boolean = false,
    growRamp: Float = 1f,
    draw: DrawScope.(fullSize: Size) -> Unit,
) {
    if (growDown != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ProgressiveBlurBands(radius, mask, modifier, offscreen, growDown, growAcross, growRamp, draw)
        return
    }
    val currentRadius by rememberUpdatedState(radius)
    val currentOffscreen by rememberUpdatedState(offscreen)
    // A radius that animates only re-lays this out when it crosses into another resolution step.
    val low = LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.LOW
    val downscale by remember(low) { derivedStateOf { glassDownscale(currentRadius(), low) } }
    val maskCache = remember { MaskedEffectCache() }
    val full = remember { FullSize() }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                // Without RenderEffect (below API 31) the mask is applied the old way.
                if (mask != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = mask, blendMode = BlendMode.DstIn)
                        }
                } else Modifier
            )
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .downscaledLayout({ downscale }, full)
                .graphicsLayer {
                    val k = downscale.toFloat()
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                    val r = currentRadius() / k
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val mode = tileMode ?: android.graphics.Shader.TileMode.DECAL
                        if (mask is ShaderBrush) {
                            val quantized = if (r > 0.5f) BlurEffectCache.quantize(r) else 0f
                            val cache = maskCache
                            if (cache.effect == null || cache.radius != quantized || cache.width != size.width ||
                                cache.height != size.height || cache.tileOrdinal != mode.ordinal || cache.mask != mask
                            ) {
                                cache.effect = maskedBlurEffect(quantized, mode, mask.createShader(size))
                                cache.radius = quantized
                                cache.width = size.width
                                cache.height = size.height
                                cache.tileOrdinal = mode.ordinal
                                cache.mask = mask
                            }
                            cache.effect
                        } else {
                            BlurEffectCache.get(r, mode)
                        }
                    } else {
                        null
                    }
                    compositingStrategy = if (currentOffscreen?.invoke() == true) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    clip = true
                }
        ) {
            val k = downscale.toFloat()
            val fullSize = Size(full.width.toFloat(), full.height.toFloat())
            scale(1f / k, 1f / k, pivot = Offset.Zero) { draw(fullSize) }
        }
    }
}

@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    shape: Shape? = null,

    /**
     * Null means DECAL, resolved inside the API 31 guard below.
     *
     * `Shader.TileMode.DECAL` is API 31, and naming it as a default argument makes every caller
     * that omits the parameter evaluate it - throwing NoSuchFieldError on Android 8 through 11.
     * The value is only ever *used* above API 31, so it is resolved there instead.
     */
    tileMode: android.graphics.Shader.TileMode? = null,
    /**
     * Fades the finished glass (backdrop, foundation and tint alike) by this brush's alpha, as a
     * DstIn would. On API 31+ the mask runs inside the blur's own effect; it used to need an
     * offscreen layer of its own around the glass.
     */
    mask: Brush? = null,
    /** a colour laid over [tint], read only while drawing so it can animate without recomposing */
    accentWash: (() -> Color)? = null,
    /**
     * Also blur a margin of the backdrop as wide as the blur around the surface, so the edges are
     * blurred from what's really past them. Without it the blur has to make that part up, and a
     * clamped edge row smears anything bright touching it across the surface. The extra is drawn
     * outside the bounds, so the parent has to clip.
     */
    bleed: Boolean = false,
    // grows the blur from sharp at the start to blurRadius at the far end, in one pass (android 13+)
    progressive: BlurDirection? = null,
    // how far along the progressive blur reaches its full radius (1 is the far end), holding it from there on
    progressiveRamp: Float = 1f,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }

    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (blurStrengthPx) = rememberPreference(com.mochyy.mochymusic.constants.BlurStrengthPxKey, defaultValue = 25f)

    val currentBlurRadius by rememberUpdatedState(blurRadius)

    // The blur actually applied, clamped the way BlurEffectCache clamps it.
    val radius = {
        if (disableBlur) 0f
        else BlurEffectCache.effectiveRadius(currentBlurRadius() * (blurStrengthPx / 25f))
    }

    DownscaledBlur(
        radius = radius,
        tileMode = tileMode,
        mask = mask,
        growDown = progressive?.let { it == BlurDirection.TopToBottom },
        growRamp = progressiveRamp,
        modifier = modifier
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .then(
                // no blur below Android 12, so nothing to read past the edges; and low quality
                // keeps to what's right under the glass, a much smaller area to blur
                if (bleed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.HIGH
                ) {
                    Modifier.bleed { kotlin.math.ceil(radius()).toInt() }
                } else Modifier
            )
            .onGloballyPositioned { position = it.positionInWindow() },
    ) { fullSize ->
        if (foundationColor != null) {
            drawRect(color = foundationColor, size = fullSize)
        }
        if (!disableBlur) {
            val node = state.renderNode
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassNodeHasContent(node)) {
                val relX = position.x - state.rootPosition.x
                val relY = position.y - state.rootPosition.y
                translate(left = -relX, top = -relY) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
                }
            }
        }
        if (tint != Color.Transparent) {
            drawRect(color = tint, size = fullSize)
        }
        val wash = accentWash?.invoke()
        if (wash != null && wash.alpha > 0f) {
            drawRect(color = wash, size = fullSize)
        }
    }
}

enum class BlurDirection { TopToBottom, BottomToTop }

// the hairline where a flat bar's blur stops, when progressive blur is off
private val FlatBarEdgeColor = Color.White.copy(alpha = 0.16f)
private val FlatBarEdgeWidth = 1.dp

// how dark the one even tint is that replaces a bar's fading scrim while progressive blur is off
const val FlatBarScrimAlpha = 0.45f

// whether bars blur progressively; off, they're flat with a hairline, and their own fading scrims turn into one tint
@Composable
fun rememberProgressiveBlur(): Boolean {
    val (enabled) = rememberPreference(com.mochyy.mochymusic.constants.EnableProgressiveBlurKey, defaultValue = true)
    return enabled
}

@Composable
fun ProgressiveGlassBackground(
    state: GlassState,
    maxBlurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    direction: BlurDirection = BlurDirection.TopToBottom,
    modifier: Modifier = Modifier,
    steps: Int = 5
) {
    // off, the bar is one even blur that stops at a hard edge with a hairline, a classic frosted bar instead of a fade
    if (!rememberProgressiveBlur()) {
        Box(
            modifier = modifier
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val line = FlatBarEdgeWidth.toPx()
                    val y = if (direction == BlurDirection.TopToBottom) 0f else size.height - line
                    drawRect(FlatBarEdgeColor, topLeft = Offset(0f, y), size = Size(size.width, line))
                },
        ) {
            GlassPillBackground(
                state = state,
                blurRadius = maxBlurRadius,
                tint = tint,
                foundationColor = foundationColor,
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                // blurred from what's really past the edge, so the hard edge doesn't smear into a glow
                bleed = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val smooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.HIGH
    if (smooth) {
        // high quality: one pass whose radius grows row by row, faded in where the first step used to fade in
        val fade = 1f / steps.coerceAtLeast(1)
        val mask = remember(direction, fade) {
            if (direction == BlurDirection.TopToBottom) {
                Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black, 1f to Color.Black)
            } else {
                Brush.verticalGradient(0f to Color.Black, (1f - fade) to Color.Black, 1f to Color.Transparent)
            }
        }
        Box(modifier = modifier) {
            GlassPillBackground(
                state = state,
                blurRadius = maxBlurRadius,
                tint = tint,
                foundationColor = foundationColor,
                mask = mask,
                progressive = direction,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    Box(modifier = modifier) {
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps

            val radiusProvider = { maxBlurRadius() * (fraction * fraction) }

            val fadeStart = (i - 1).toFloat() / steps
            val heightFraction = 1f - fadeStart

            if (heightFraction > 0f) {
                val internalFadeDistance = (1f / steps) / heightFraction
                // Each step fades in over its first slice. The mask used to be DstIn-ed over the
                // step in an offscreen layer of its own - one more full-width buffer per step,
                // every frame the bar's backdrop moved; it now runs inside the step's blur.
                val mask = remember(direction, internalFadeDistance) {
                    if (direction == BlurDirection.TopToBottom) {
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            internalFadeDistance to Color.Black,
                            1f to Color.Black
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.Black,
                            (1f - internalFadeDistance) to Color.Black,
                            1f to Color.Transparent
                        )
                    }
                }

                GlassPillBackground(
                    state = state,
                    blurRadius = radiusProvider,
                    tint = tint,
                    foundationColor = foundationColor,
                    mask = mask,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .align(if (direction == BlurDirection.TopToBottom) androidx.compose.ui.Alignment.BottomCenter else androidx.compose.ui.Alignment.TopCenter)
                )
            }
        }
    }
}
