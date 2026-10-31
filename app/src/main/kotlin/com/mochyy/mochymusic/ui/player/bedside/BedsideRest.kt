package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.constants.BedsideKeepAwakeKey
import com.mochyy.mochymusic.constants.BedsideLockScreenKey
import com.mochyy.mochymusic.constants.BedsideNightTint
import com.mochyy.mochymusic.constants.BedsideNightTintKey
import com.mochyy.mochymusic.constants.BedsideScreenSaverKey
import com.mochyy.mochymusic.ui.player.styles.rememberStyleShowing
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.random.Random

// the soft warm orange the bedside settles into at night, the salmon of the mockups
internal val BedsideOrange = Color(0xFFF4A68C)

private const val DotsLingerMs = 1_400L
private const val ChromeLingerMs = 4_000L
private const val RestAfterMs = 15_000L
private const val ShowMs = 260
private const val HideMs = 700
private const val SleepInMs = 2_400
private const val DriftEveryMs = 60_000L
private const val DriftMoveMs = 4_000
private val DriftRange = 8.dp
// how much the words and the cover dim once the screen saver has settled in
private const val InkDim = 0.5f
private const val CoverDim = 0.65f
private const val SoftAlpha = 0.55f
private val Luma = floatArrayOf(0.2126f, 0.7152f, 0.0722f)

// what the bedside is doing right now: awake with its buttons out, or left alone and settling into the night
@Stable
internal class BedsideRest(live: Boolean) {
    var fingerDown by mutableStateOf(false)
    // bumped by every wake (a touch, the player opening) so the timers start over
    var wakes by mutableIntStateOf(0)
        private set
    var chromeShown by mutableStateOf(live)
        private set
    var resting by mutableStateOf(false)
        private set
    private var dotsShown by mutableStateOf(false)

    // the same moods as 0..1, read while drawing so a fade only redraws
    val chrome = Animatable(if (live) 1f else 0f)
    val dots = Animatable(0f)
    val sleep = Animatable(0f)
    val drift = Animatable(Offset.Zero, Offset.VectorConverter)

    fun wake() {
        wakes++
    }

    // everything comes out while a finger is down; let go and it fades back a step at a time
    suspend fun settle() {
        chromeShown = true
        dotsShown = true
        resting = false
        if (fingerDown) return
        delay(DotsLingerMs)
        dotsShown = false
        delay(ChromeLingerMs - DotsLingerMs)
        chromeShown = false
        delay(RestAfterMs - ChromeLingerMs)
        resting = true
    }

    suspend fun followDots() {
        snapshotFlow { dotsShown }.collectLatest { shown -> dots.animateTo(if (shown) 1f else 0f, tween(if (shown) ShowMs else HideMs)) }
    }
}

// the bedside's timers, all asked from effects so a touch recomposes nothing
@Composable
internal fun rememberBedsideRest(live: Boolean, saver: Boolean): BedsideRest {
    val rest = remember(live) { BedsideRest(live) }
    if (!live) return rest
    val showing = rememberStyleShowing()
    val driftPx = with(LocalDensity.current) { DriftRange.toPx() }
    // opening the player wakes it too, so it never opens already asleep
    LaunchedEffect(rest) {
        snapshotFlow { showing() }.distinctUntilChanged().collect { if (it) rest.wake() }
    }
    LaunchedEffect(rest) {
        snapshotFlow { rest.fingerDown to rest.wakes }.collectLatest { rest.settle() }
    }
    LaunchedEffect(rest) {
        snapshotFlow { rest.chromeShown }.collectLatest { shown ->
            rest.chrome.animateTo(if (shown) 1f else 0f, tween(if (shown) ShowMs else HideMs))
        }
    }
    LaunchedEffect(rest) { rest.followDots() }
    LaunchedEffect(rest) {
        snapshotFlow { rest.resting }.collectLatest { resting ->
            rest.sleep.animateTo(if (resting) 1f else 0f, tween(if (resting) SleepInMs else ShowMs))
        }
    }
    LaunchedEffect(rest, saver, driftPx) {
        snapshotFlow { rest.resting && saver }.collectLatest { drifting ->
            if (!drifting) {
                rest.drift.animateTo(Offset.Zero, tween(ShowMs))
                return@collectLatest
            }
            while (true) {
                delay(DriftEveryMs)
                val target = Offset((Random.nextFloat() * 2f - 1f) * driftPx, (Random.nextFloat() * 2f - 1f) * driftPx)
                rest.drift.animateTo(target, tween(DriftMoveMs))
            }
        }
    }
    return rest
}

// notes every touch on the bedside without taking it, so the timers know someone's there
internal fun Modifier.watchTouches(rest: BedsideRest): Modifier = pointerInput(rest) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.any { it.pressed }
            if (down != rest.fingerDown) rest.fingerDown = down
        }
    }
}

// buttons fade with the chrome but still take the very first tap, hidden or not; swallowing it to wake the screen made every button need two
internal fun Modifier.bedsideChrome(rest: BedsideRest): Modifier = graphicsLayer {
    alpha = rest.chrome.value
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

// tints and dims everything drawn inside while the bedside rests, at draw time, so plain white content needs to know nothing about the night
internal fun Modifier.nightTint(ink: BedsideInk): Modifier = this
    .graphicsLayer { compositingStrategy = if (ink.tinting()) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithContent {
        drawContent()
        if (ink.tinting()) drawRect(ink.ink(), blendMode = BlendMode.Modulate)
    }

// the colour of everything on the bedside: white while awake, the night tint and the saver's dim once it rests
@Stable
internal class BedsideInk(
    private val rest: BedsideRest,
    private val tint: State<Color?>,
    private val saver: State<Boolean>,
) {
    fun ink(): Color {
        val sleep = rest.sleep.value
        val tint = tint.value
        val colour = if (tint == null) Color.White else lerp(Color.White, tint, sleep)
        return colour.copy(alpha = brightness(sleep, InkDim))
    }

    val main = ColorProducer { ink() }
    val soft = ColorProducer { ink().let { it.copy(alpha = it.alpha * SoftAlpha) } }

    private fun brightness(sleep: Float, dim: Float) = if (saver.value) 1f - dim * sleep else 1f

    // whether resting has changed anything yet, so an awake screen skips the tint layer entirely
    fun tinting(): Boolean = rest.sleep.value > 0f && (tint.value != null || saver.value)

    // pictures inside a night tint layer only lose their colour; the layer itself does the tint and the dim
    private var greyAmount = -1f
    private var grey: ColorFilter? = null

    fun greyFilter(): ColorFilter? {
        val amount = if (tint.value == null) 0f else rest.sleep.value
        if (amount == 0f) return null
        if (amount == greyAmount) return grey
        greyAmount = amount
        return ColorFilter.colorMatrix(tintMatrix(Color.White, amount, 1f)).also { grey = it }
    }

    // the last filter built, so a still cover doesn't make a new one every time it's drawn
    private var builtAmount = -1f
    private var builtBright = -1f
    private var builtTint: Color? = null
    private var built: ColorFilter? = null

    fun coverFilter(): ColorFilter? {
        val sleep = rest.sleep.value
        val tint = tint.value
        val amount = if (tint == null) 0f else sleep
        val bright = brightness(sleep, CoverDim)
        if (amount == 0f && bright == 1f) return null
        if (amount == builtAmount && bright == builtBright && tint == builtTint) return built
        builtAmount = amount
        builtBright = bright
        builtTint = tint
        return ColorFilter.colorMatrix(tintMatrix(tint ?: Color.White, amount, bright)).also { built = it }
    }
}

@Composable
internal fun rememberBedsideInk(rest: BedsideRest, tint: Color?, saver: Boolean): BedsideInk {
    val tintState = rememberUpdatedState(tint)
    val saverState = rememberUpdatedState(saver)
    return remember(rest) { BedsideInk(rest, tintState, saverState) }
}

// the cover's own hue made bright enough to read; a grey cover has no hue to give, so it gets the orange
internal fun coverTint(seed: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    if (hsl[1] < 0.12f) return BedsideOrange
    return Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], 0.85f, 0.62f)))
}

// turns a picture toward one colour by how bright each pixel is, and dims it
private fun tintMatrix(tint: Color, amount: Float, bright: Float): ColorMatrix {
    val keep = 1f - amount
    val channels = floatArrayOf(tint.red, tint.green, tint.blue)
    val values = FloatArray(20)
    for (row in 0..2) {
        for (column in 0..2) {
            val mixed = amount * channels[row] * Luma[column] + if (row == column) keep else 0f
            values[row * 5 + column] = mixed * bright
        }
    }
    values[18] = 1f
    return ColorMatrix(values)
}

// what both bedside styles set up the same way: the resting timers, the night colours, whether the phone is locked, and the window flags
@Stable
internal class BedsideNight(val rest: BedsideRest, val ink: BedsideInk, val locked: Boolean)

@Composable
internal fun rememberBedsideNight(live: Boolean): BedsideNight {
    val tintChoice by rememberEnumPreference(BedsideNightTintKey, BedsideNightTint.ORANGE)
    val saver by rememberPreference(BedsideScreenSaverKey, defaultValue = true)
    val keepAwake by rememberPreference(BedsideKeepAwakeKey, defaultValue = true)
    val overLockScreen by rememberPreference(BedsideLockScreenKey, defaultValue = true)
    val seed = LocalArtworkColor.current
    val coverHue = remember(seed) { coverTint(seed) }
    val tint = when (tintChoice) {
        BedsideNightTint.OFF -> null
        BedsideNightTint.ORANGE -> BedsideOrange
        BedsideNightTint.COVER -> coverHue
    }
    val rest = rememberBedsideRest(live, saver)
    val ink = rememberBedsideInk(rest, tint, saver)
    val locked = if (live) rememberPhoneLocked().value else false
    if (live) BedsideWindowFlags(keepAwake, overLockScreen && LockScreenSupported)
    return remember(rest, ink, locked) { BedsideNight(rest, ink, locked) }
}
