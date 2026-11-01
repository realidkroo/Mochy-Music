// LaunchIntro.kt

package com.mochyy.mochymusic.ui.launch

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.utils.AppIcon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * A cold start, in order:
 *
 *  1. The system splash shows the filled mark (an anime launcher icon on android 13+: the icon
 *     itself). On the app's first frame [LaunchIntroOverlay] draws the very same thing in the very
 *     same spot and the splash goes (see SplashScreenExit). The anime icon then zooms out with a
 *     bounce into the filled mark, a ripple running out from it, before step 3.
 *  2. The mark holds still while the essentials come up underneath it: the player service, the
 *     theme, and Home (its logo and bones). Anything slow here is invisible, nothing moves.
 *  3. The mark flies into Home's logo, crossfading from the filled splash mark into the outline
 *     one with a blur through the middle, while the skeleton rises in ([launchRise]).
 *  4. Only then does the heavy work get the main thread: Home swaps its bones for content, the
 *     PoToken WebView boots, auto-sync runs ([LaunchGate.awaitIntro]).
 */

/** process-wide: heavy startup work waits here until the launch intro has landed */
object LaunchGate {
    private val introDone = MutableStateFlow(false)
    private val splashGone = MutableStateFlow(false)

    /** a launch that plays the intro shows Home's skeleton first, even when its data is in already */
    @Volatile
    internal var holdHome = false

    /** a fresh launch from the launcher: the intro will play */
    fun begin() {
        holdHome = true
        splashGone.value = false
        introDone.value = false
    }

    /** no intro this time (a deep link, a recreated activity): nothing waits */
    fun skip() {
        holdHome = false
        introDone.value = true
    }

    /** the system splash has stepped aside; the app's own mark is the one on screen now */
    fun onSplashGone() {
        splashGone.value = true
    }

    internal fun finish() {
        introDone.value = true
    }

    internal suspend fun awaitSplashGone(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { splashGone.first { it } }
    }

    /**
     * Suspends until the intro has landed. Never longer than [timeoutMillis]: a process started
     * for the player service alone has no intro, and its work shouldn't sit waiting for one.
     */
    suspend fun awaitIntro(timeoutMillis: Long = 8_000L) {
        withTimeoutOrNull(timeoutMillis) { introDone.first { it } }
    }
}

// the flight: still -> a gentle start -> a long settle into the logo. scale leads the move a
// little, so the big mark has shrunk before it crosses the screen
private const val FlightMillis = 660
private val MoveEasing = CubicBezierEasing(0.55f, 0f, 0.1f, 1f)
private val ScaleEasing = CubicBezierEasing(0.45f, 0f, 0.1f, 1f)

// the skeleton: each piece rises a little after the one above it
private const val RiseDelayMillis = 80
private const val RiseMillis = 520
private const val RiseStaggerMillis = 50
private const val RiseOrders = 4
private const val RiseTotalMillis = RiseMillis + (RiseOrders - 1) * RiseStaggerMillis
private val RiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val RiseDistance = 56.dp

// the essentials get this long, then the intro plays regardless. generous on purpose: Home's
// first real composition is the heaviest frame of the launch, and it has to land while the mark
// stands still, not halfway through its flight (on a debug build it can take a couple of seconds)
private const val EssentialsTimeoutMillis = 2_500L

// the splash steps aside once the first frame is up; a launch with no splash at all moves on after this
private const val SplashTimeoutMillis = 1_500L

// blur through the middle of the morph, in the mark's own units (it scales with the mark)
private const val BlurPeak = 8f

// the anime icon bouncing out into the mark, ripple and all
private const val PreludeMillis = 1_100

// an adaptive icon on the splash: its 108dp layers drawn at 240dp, shown through a 160dp circle
private const val IconLayerSize = 240f / 288f * 235f
private const val IconCircleRadius = 80f / 288f * 235f

// the system splash draws an icon with no background at 288dp, masked to a 192dp circle
private val SplashIconSize = 288.dp
private const val SplashViewport = 235f
private const val SplashMaskRadius = 96f / 288f * SplashViewport

// ic_splash_mark and ic_mochy_mark are the same shapes; the splash one sits 58,48 further in
private val MarkOffset = Offset(58f, 48f)
private const val MarkViewportWidth = 128f
private const val MarkViewportHeight = 139f

// the splash viewport's centre: the point the flight scales around
private val Pivot = Offset(SplashViewport / 2f, SplashViewport / 2f)

// everything the marks draw, plus room for the blur, in splash units
private val ArtBounds = RectF(44f, 36f, 204f, 200f)

// the layer the blur runs on: the marks, and the anime icon's circle as it winds up
private val LayerBounds = RectF(28f, 28f, 207f, 207f)

@Stable
class LaunchIntroState internal constructor(
    val plays: Boolean,
    // the anime icon the splash showed (android 13+); null starts from the filled mark as always
    internal val icon: AppIcon? = null,
) {
    internal val prelude = Animatable(if (plays && icon != null) 0f else 1f)
    internal val flight = Animatable(if (plays) 0f else 1f)
    internal val rise = Animatable(if (plays) 0f else 1f)

    /** Home's logo in root coordinates: where the mark lands */
    internal var markBounds by mutableStateOf<Rect?>(null)

    /** the real Home (not its shell) has composed once */
    internal var homeComposed by mutableStateOf(false)

    /** the mark has reached the logo; the real logo shows from this frame on */
    internal var landed by mutableStateOf(!plays)

    /** everything has settled; the overlay and every launch modifier step out of the way */
    var done by mutableStateOf(!plays)
        internal set

    internal fun riseAt(order: Int): Float {
        val millis = rise.value * RiseTotalMillis - order * RiseStaggerMillis
        return RiseEasing.transform((millis / RiseMillis).coerceIn(0f, 1f))
    }
}

val LocalLaunchIntro = staticCompositionLocalOf<LaunchIntroState?> { null }

@Composable
fun rememberLaunchIntroState(plays: Boolean): LaunchIntroState = remember { LaunchIntroState(plays) }

/**
 * Home's logo: the spot the launch mark lands on. It reports where it is and stays hidden until
 * the flying mark gets there, then takes over in the same frame.
 */
@Composable
fun Modifier.launchMarkTarget(): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return this
        .onGloballyPositioned { state.markBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (state.landed) 1f else 0f }
}

/**
 * A piece of the skeleton that rises into place during the intro; [order] staggers it after the
 * pieces above it. Read in the draw phase only, so the rise never recomposes anything.
 */
@Composable
fun Modifier.launchRise(order: Int, distance: Dp = RiseDistance): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return graphicsLayer {
        val e = state.riseAt(order)
        alpha = e
        translationY = (1f - e) * distance.toPx()
        // faded per draw rather than through a buffer: some of these pieces are screen-sized
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** the real Home calls this once it has composed; the mark doesn't take off before then */
@Composable
fun ReportLaunchHomeComposed() {
    val state = LocalLaunchIntro.current ?: return
    if (state.done || state.homeComposed) return
    LaunchedEffect(state) { state.homeComposed = true }
}

/**
 * Home holds its skeleton while this is true: the first Home after a launch starts from bones,
 * so its content always arrives with its reveal instead of popping in under the intro.
 */
@Composable
fun rememberLaunchHold(): Boolean {
    var held by remember { mutableStateOf(LaunchGate.holdHome) }
    if (held) {
        LaunchedEffect(Unit) {
            // the intro always ends (its waits all time out), so this is only a backstop
            LaunchGate.awaitIntro(timeoutMillis = 15_000L)
            LaunchGate.holdHome = false
            held = false
        }
    }
    return held
}

/** where the mark is and how big, at one point of its flight */
private class MarkPlacement(val origin: Offset, val unit: Float)

/** this frame's placement, worked out in the layout pass and read by the layer and draw passes */
private class MarkFrame {
    var origin = Offset.Zero
    var unit = 1f
    var layerTopLeft = Offset.Zero
}

/**
 * Sits on top of the whole app for the intro: the black of the splash, and the mark flying from
 * the splash's spot into Home's logo. [essentialsReady] is what the mark waits for before it moves.
 */
@Composable
fun LaunchIntroOverlay(state: LaunchIntroState, essentialsReady: Boolean) {
    if (state.done) return
    val ready by rememberUpdatedState(essentialsReady)
    val art = remember { LaunchMarkArt() }
    val frame = remember { MarkFrame() }
    val context = LocalContext.current
    // read once for the first frame: it has to match what the splash showed
    val iconArt = remember { state.icon?.let { LaunchIconArt(context, it) } }

    LaunchedEffect(state) {
        val shownAt = SystemClock.uptimeMillis()
        val inTime = withTimeoutOrNull(EssentialsTimeoutMillis) {
            snapshotFlow { ready && state.homeComposed && state.markBounds != null }.first { it }
        } != null
        // the system splash still covers us until the first frame is up; flying under it would
        // spend the start of the flight where nobody can see it
        LaunchGate.awaitSplashGone(SplashTimeoutMillis)
        Timber.tag("LaunchIntro").d(
            "take-off after %dms, essentials %s (player+theme=%b home=%b logo=%b)",
            SystemClock.uptimeMillis() - shownAt, if (inTime) "ready" else "timed out",
            ready, state.homeComposed, state.markBounds != null,
        )
        // whatever just came up draws its first (heavy) frame while the mark is still standing
        withFrameNanos { }
        withFrameNanos { }
        if (state.prelude.value < 1f) {
            state.prelude.animateTo(1f, tween(PreludeMillis, easing = LinearEasing))
        }
        val flightAt = SystemClock.uptimeMillis()
        var worstFrame = 0L
        coroutineScope {
            val watch = launch {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    worstFrame = maxOf(worstFrame, (now - last) / 1_000_000L)
                    last = now
                }
            }
            launch { state.rise.animateTo(1f, tween(RiseTotalMillis, RiseDelayMillis, LinearEasing)) }
            state.flight.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
            state.landed = true
            watch.cancel()
        }
        Timber.tag("LaunchIntro").d(
            "landed %dms after take-off, worst frame %dms", SystemClock.uptimeMillis() - flightAt, worstFrame,
        )
        LaunchGate.finish()
        state.done = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // the splash's black, fading off the skeleton; it also keeps taps off the skeleton
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .drawBehind {
                    val backdrop = 1f - smoothstep(0.18f, 0.7f, state.flight.value)
                    if (backdrop > 0f) drawRect(Color.Black.copy(alpha = backdrop))
                }
        )

        // the anime icon's ripple, running out from under the mark as it bounces in; drawn flat, never blurred
        if (iconArt != null) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val ripple = rippleAt(state.prelude.value) ?: return@drawBehind
                        drawCircle(
                            color = Color.White.copy(alpha = 0.32f * (1f - ripple)),
                            radius = IconCircleRadius * (0.9f + 1.3f * ripple) * frame.unit,
                            center = frame.origin + Pivot * frame.unit,
                            style = Stroke(width = (7f * (1f - ripple) + 1.5f) * frame.unit),
                        )
                    }
            )
        }

        // the mark, on a layer just big enough for it so the blur stays cheap at every size
        Spacer(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placement = placementAt(state, IntSize(constraints.maxWidth, constraints.maxHeight))
                    val left = floor(placement.origin.x + LayerBounds.left * placement.unit).toInt()
                    val top = floor(placement.origin.y + LayerBounds.top * placement.unit).toInt()
                    val right = ceil(placement.origin.x + LayerBounds.right * placement.unit).toInt()
                    val bottom = ceil(placement.origin.y + LayerBounds.bottom * placement.unit).toInt()
                    frame.origin = placement.origin
                    frame.unit = placement.unit
                    frame.layerTopLeft = Offset(left.toFloat(), top.toFloat())
                    val placeable = measurable.measure(Constraints.fixed(right - left, bottom - top))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(left, top)
                    }
                }
                .graphicsLayer {
                    val p = state.prelude.value
                    val blur = if (p < 1f) preludeLook(p).blur else BlurPeak * bell((state.flight.value - 0.04f) / 0.76f)
                    renderEffect = BlurEffectCache.element(blur * frame.unit)
                }
                .drawBehind {
                    val t = state.flight.value
                    if (state.landed) return@drawBehind
                    val p = state.prelude.value
                    if (p < 1f && iconArt != null) {
                        val look = preludeLook(p)
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            native.save()
                            native.translate(frame.origin.x - frame.layerTopLeft.x, frame.origin.y - frame.layerTopLeft.y)
                            native.scale(frame.unit, frame.unit)
                            iconArt.draw(native, look.iconAlpha, look.iconScale)
                            art.draw(native, look.markAlpha, 0f, look.markScale)
                            native.restore()
                        }
                        return@drawBehind
                    }
                    val fade = if (state.markBounds == null) 1f - smoothstep(0.55f, 1f, t) else 1f
                    val filledAlpha = (1f - smoothstep(0.3f, 0.78f, t)) * fade
                    val outlineAlpha = smoothstep(0.12f, 0.6f, t) * fade
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        // the layer starts at the art's top left, rounded down to a whole pixel
                        native.translate(frame.origin.x - frame.layerTopLeft.x, frame.origin.y - frame.layerTopLeft.y)
                        native.scale(frame.unit, frame.unit)
                        art.draw(native, filledAlpha, outlineAlpha)
                        native.restore()
                    }
                }
        )
    }
}

/** the mark's place at the flight's current point, for an overlay of [size] */
private fun Density.placementAt(state: LaunchIntroState, size: IntSize): MarkPlacement {
    val t = state.flight.value
    val splashSize = SplashIconSize.toPx()
    val startUnit = splashSize / SplashViewport
    val startOrigin = Offset(size.width / 2f - splashSize / 2f, size.height / 2f - splashSize / 2f)

    val target = state.markBounds
    val endUnit: Float
    val endOrigin: Offset
    if (target != null) {
        // the logo is an Icon: its art is fitted and centred in the logo's box
        endUnit = min(target.width / MarkViewportWidth, target.height / MarkViewportHeight)
        val artLeft = target.left + (target.width - MarkViewportWidth * endUnit) / 2f
        val artTop = target.top + (target.height - MarkViewportHeight * endUnit) / 2f
        endOrigin = Offset(artLeft, artTop) - MarkOffset * endUnit
    } else {
        // nowhere to land (Home never laid out): settle smaller where it is and fade
        endUnit = startUnit * 0.6f
        endOrigin = startOrigin + Pivot * (startUnit - endUnit)
    }

    val unit = startUnit * (endUnit / startUnit).pow(ScaleEasing.transform(t))
    val pivot = lerp(startOrigin + Pivot * startUnit, endOrigin + Pivot * endUnit, MoveEasing.transform(t))
    return MarkPlacement(origin = pivot - Pivot * unit, unit = unit)
}

/** the two marks, in the splash drawable's units, ready to draw at any scale */
private class LaunchMarkArt {
    private val triangle = path(
        "M163.5 96.3493C180.167 105.972 180.167 130.028 163.5 139.651L108 171.694C91.3333 181.316 " +
            "70.5 169.288 70.5 150.043V85.9569C70.5 66.7119 91.3333 54.6838 108 64.3063L163.5 96.3493Z"
    )
    // the face: the little chevron and the smile under it
    private val face = path("M97 112L108.5 98L120 112M110.325 125C111.871 133.439 121.868 148.704 142.944 149.92")
    private val mask = Path().apply { addCircle(Pivot.x, Pivot.y, SplashMaskRadius, Path.Direction.CW) }

    // ic_splash_mark: a white shape with a dark outline and a dark face
    private val fill = paint(android.graphics.Color.WHITE) { style = Paint.Style.FILL }
    private val darkOutline = paint(0xFF212121.toInt()) {
        style = Paint.Style.STROKE
        strokeWidth = 19f
        strokeJoin = Paint.Join.ROUND
    }
    private val darkFace = paint(0xFF212121.toInt()) {
        style = Paint.Style.STROKE
        strokeWidth = 11f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ic_mochy_mark: the same outline and face, white, nothing filled
    private val lightOutline = paint(android.graphics.Color.WHITE) {
        style = Paint.Style.STROKE
        strokeWidth = 19f
        strokeJoin = Paint.Join.ROUND
    }
    private val lightFace = paint(android.graphics.Color.WHITE) {
        style = Paint.Style.STROKE
        strokeWidth = 11f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: android.graphics.Canvas, filledAlpha: Float, outlineAlpha: Float, filledScale: Float = 1f) {
        // each mark is faded as a whole, so its overlapping strokes never show through each other
        if (filledAlpha > 0.002f) {
            canvas.saveLayerAlpha(LayerBounds, (filledAlpha * 255f).toInt())
            canvas.scale(filledScale, filledScale, Pivot.x, Pivot.y)
            canvas.clipPath(mask)
            canvas.drawPath(triangle, fill)
            canvas.drawPath(triangle, darkOutline)
            canvas.drawPath(face, darkFace)
            canvas.restore()
        }
        if (outlineAlpha > 0.002f) {
            canvas.saveLayerAlpha(ArtBounds, (outlineAlpha * 255f).toInt())
            canvas.drawPath(triangle, lightOutline)
            canvas.drawPath(face, lightFace)
            canvas.restore()
        }
    }

    private fun path(data: String): Path = PathParser.createPathFromPathData(data)

    private inline fun paint(color: Int, setup: Paint.() -> Unit) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            setup()
        }
}

/** the launcher icon as the splash drew it: its background and art through a circle, in splash units */
private class LaunchIconArt(context: Context, icon: AppIcon) {
    private val fill = icon.background?.let { color -> Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = context.getColor(color) } }
    // drawn into a fixed box and scaled, so a vector renders sharp at whatever size the canvas asks
    private val art = ContextCompat.getDrawable(context, icon.foreground)?.mutate()?.apply { setBounds(0, 0, ArtBox, ArtBox) }
    private val circle = Path().apply { addCircle(Pivot.x, Pivot.y, IconCircleRadius, Path.Direction.CW) }

    fun draw(canvas: android.graphics.Canvas, alpha: Float, scale: Float) {
        if (alpha <= 0.002f) return
        canvas.saveLayerAlpha(LayerBounds, (alpha * 255f).toInt())
        canvas.scale(scale, scale, Pivot.x, Pivot.y)
        canvas.clipPath(circle)
        fill?.let { canvas.drawPath(circle, it) }
        art?.let {
            canvas.translate(Pivot.x - IconLayerSize / 2f, Pivot.y - IconLayerSize / 2f)
            canvas.scale(IconLayerSize / ArtBox, IconLayerSize / ArtBox)
            it.draw(canvas)
        }
        canvas.restore()
    }

    private companion object {
        const val ArtBox = 1024
    }
}

/** the icon and the mark at one point of the prelude, and the blur over both */
private class PreludeLook(val iconAlpha: Float, val iconScale: Float, val markAlpha: Float, val markScale: Float, val blur: Float)

// mochy winds up a little and zooms out, then the mark pops in past its size and settles while the ripple runs out
private fun preludeLook(t: Float): PreludeLook {
    val out = (t / 0.5f).coerceIn(0f, 1f)
    val pop = ((t - 0.38f) / 0.62f).coerceIn(0f, 1f)
    return PreludeLook(
        iconAlpha = 1f - smoothstep(0.55f, 1f, out),
        iconScale = 1f - 0.7f * backIn(out),
        markAlpha = smoothstep(0f, 0.25f, pop),
        markScale = 0.35f + 0.65f * springOut(pop),
        blur = BlurPeak * 1.4f * bell((t - 0.12f) / 0.5f),
    )
}

/** how far the ripple has run, 0 to 1, or null while there's none */
private fun rippleAt(t: Float): Float? = ((t - 0.5f) / 0.5f).takeIf { it in 0f..1f }

// starts by pulling back a little before it goes, the wind-up of a zoom out
private fun backIn(x: Float): Float = x * x * (2.70158f * x - 1.70158f)

// a spring let go: past 1 by about a sixth, back under, then still, landing on exactly 1
private fun springOut(x: Float): Float = (1f - exp(-6f * x) * cos(10f * x)) / (1f - exp(-6f) * cos(10f))

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0 at both ends, 1 in the middle, soft all the way */
private fun bell(x: Float): Float {
    if (x <= 0f || x >= 1f) return 0f
    val s = sin(PI.toFloat() * x)
    return s * s
}
