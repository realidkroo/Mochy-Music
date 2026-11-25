// compose wrappers around the shared gles 3.0 fallback engine for liquid glass, module backdrop blur and standalone agsl shaders on android 12 and below

package com.mochyy.mochymusic.ui.component

import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.modules.skin.ActiveGlass
import com.mochyy.mochymusic.modules.skin.SkinGlassFallback
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.roundToInt

private val MainHandler = Handler(Looper.getMainLooper())

// ---- module chrome glass (liquid glass and custom glass.agsl) on android 10-12 ----------------

private class BackdropGlHolder {
    val input = GlInputSurface()
    val kawase = GlKawaseChain()
    val output = GlOutputTarget()

    @Volatile var inputSurface: Surface? = null
    @Volatile var bitmap: ImageBitmap? = null
    @Volatile var lastBlurredTex = 0
    @Volatile var released = false
    val pendingInput = AtomicBoolean(false)
    val dirtyWhileBusy = AtomicBoolean(false)
    val postScheduled = AtomicBoolean(false)
    val glPending = AtomicBoolean(false)

    // latest request written on main thread, copied once per gl pass
    val lock = Any()
    var reqCapW = 0
    var reqCapH = 0
    var reqOutW = 0
    var reqOutH = 0
    var reqBlurPx = 0f
    var reqWashArgb = 0
    var reqGlsl: String? = null
    var reqSampler: String? = null
    var reqHasNewInput = false
    val reqUniforms = GlUniformBundle()
    val reqComposite = GlBlurCompositeParams()
    // reused exclusively on the shared GL thread so passes never allocate per frame
    val glUniforms = GlUniformBundle()
    val glComposite = GlBlurCompositeParams()

    // last submitted keys so idle frames do zero work
    var lastDrawGen = -1L
    var lastRelX = Float.NaN
    var lastRelY = Float.NaN
    var lastBarW = -1f
    var lastBarH = -1f
    var lastMargin = -1
    var lastWashArgb = 0
    var lastBlurPx = -1f
    var lastShaderKey: Any? = null
    var lastChrome = 0
    var lastAccent = 0
    var lastLow = false
    var lastPillRect: Rect? = null
    var lastPillShown = -1f
    var lastPillLift = -1f
    var lastTint = 0
    var lastFoundation = 0
    var lastMaskMode = -1
    var lastProgDir = -1
    var lastProgRamp = -1f

    // pending main-thread capture snapshot for the post-draw runnable
    var capNode: Any? = null
    var capRelX = 0f
    var capRelY = 0f
    var capMargin = 0
    var capScale = 1f
    var capWidth = 0
    var capHeight = 0
    var capFoundationArgb = 0
    var capBackdropDirty = false
}

/** runs a module's transpiled glass.agsl (such as liquid glass) on android 10-12 via gles 3.0 and dual-kawase blur */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
internal fun FallbackModuleChromeGlass(
    state: GlassState,
    glass: ActiveGlass,
    transpiled: TranspiledAgsl,
    pureBlack: Boolean,
    accentWash: (() -> Color)?,
    surface: ChromeSurface,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    pill: ((Size) -> ChromePill?)? = null,
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val low = LocalBlurQuality.current == BlurQuality.LOW
    val blurPx = with(density) { glass.blurDp.dp.toPx() } * (if (low) 0.7f else 1f)
    val marginPx = ceil(with(density) { glass.marginDp.dp.toPx() } + blurPx * 1.5f + 2f).toInt()
    val chrome = (if (pureBlack) Color.Black else GlassChromeColor).toArgb()
    val renderScale = if (low) 0.5f else 0.75f
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var position by remember { mutableStateOf(Offset.Zero) }
    val holder = remember { BackdropGlHolder() }
    val outputTick = remember { mutableIntStateOf(0) }
    val currentOnFailed by rememberUpdatedState(onFailed)

    DisposableEffect(state, holder) {
        state.addFallbackReader()
        onDispose {
            state.removeFallbackReader()
            holder.released = true
            holder.bitmap = null
            GlFallbackEngine.post {
                holder.input.release()
                holder.kawase.release()
                holder.output.release()
            }
        }
    }

    val samplerName = transpiled.shaderInputs.firstOrNull() ?: "content"

    val dispatchGl = remember(holder, transpiled, samplerName) {
        {
            if (holder.glPending.compareAndSet(false, true)) {
                GlFallbackEngine.post {
                    runGlassPass(holder, transpiled.glsl, samplerName, outputTick, currentOnFailed)
                }
            }
        }
    }

    val captureRunnable = remember(holder, dispatchGl) {
        Runnable {
            holder.postScheduled.set(false)
            if (holder.released || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@Runnable
            if (holder.capBackdropDirty) {
                val node = holder.capNode
                if (node != null && glassNodeHasContent(node)) {
                    if (holder.pendingInput.get()) {
                        holder.dirtyWhileBusy.set(true)
                        return@Runnable
                    }
                    val inputSurf = holder.inputSurface
                    if (inputSurf == null || holder.input.width != holder.capWidth || holder.input.height != holder.capHeight) {
                        // ask the gl thread to allocate or resize the SurfaceTexture, then force a recapture once ready
                        holder.dirtyWhileBusy.set(true)
                        val capW = holder.capWidth
                        val capH = holder.capHeight
                        GlFallbackEngine.post {
                            if (holder.released) return@post
                            val made = holder.input.ensureSize(capW, capH)
                            holder.inputSurface = made
                            if (made != null) MainHandler.post { outputTick.intValue++ }
                        }
                        return@Runnable
                    }
                    val captured = captureRenderNodeToSurface(
                        surface = inputSurf,
                        node = node,
                        relX = holder.capRelX,
                        relY = holder.capRelY,
                        margin = holder.capMargin,
                        scale = holder.capScale,
                        foundationArgb = 0,
                    )
                    if (captured) {
                        holder.pendingInput.set(true)
                        synchronized(holder.lock) { holder.reqHasNewInput = true }
                    }
                }
            }
            dispatchGl()
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInWindow() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // read here so new gpu frames only redraw this canvas and never recompose the bar
            outputTick.intValue
            val bmp = holder.bitmap
            if (bmp != null) {
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                )
            }
            if (obscured.value || size.width < 1f || size.height < 1f) return@Canvas
            val node = state.renderNode ?: return@Canvas
            if (!glassNodeHasContent(node)) return@Canvas

            val relX = position.x - state.rootPosition.x
            val relY = position.y - state.rootPosition.y
            val wash = accentWash?.invoke()
            val washArgb = if (wash != null && wash.alpha > 0f) wash.copy(alpha = wash.alpha * 0.6f).toArgb() else 0
            val lit = if (glass.drawsPill) pill?.invoke(size) else null
            val pillRect = lit?.rect
            val pillShown = lit?.shown ?: 0f
            val pillLift = lit?.lift ?: 0f

            val backdropChanged = holder.lastDrawGen != state.drawGeneration ||
                holder.lastRelX != relX || holder.lastRelY != relY ||
                holder.lastBarW != size.width || holder.lastBarH != size.height ||
                holder.lastMargin != marginPx || holder.lastWashArgb != washArgb ||
                holder.lastBlurPx != blurPx || holder.lastLow != low ||
                holder.bitmap == null
            val shaderChanged = holder.lastShaderKey !== glass ||
                holder.lastChrome != chrome || holder.lastAccent != accent ||
                holder.lastPillRect != pillRect || holder.lastPillShown != pillShown || holder.lastPillLift != pillLift

            val dirtyRetry = holder.dirtyWhileBusy.getAndSet(false)
            if (!backdropChanged && !shaderChanged && !dirtyRetry) return@Canvas

            val (outW, outH) = clampedFallbackSize(size.width, size.height, renderScale, MaxGlassEdge)
            val actualScale = if (size.width > 0f) outW.toFloat() / size.width else renderScale
            val scaledMargin = ceil(marginPx * actualScale).toInt()
            val capW = (outW + scaledMargin * 2).coerceAtLeast(1)
            val capH = (outH + scaledMargin * 2).coerceAtLeast(1)

            holder.lastDrawGen = state.drawGeneration
            holder.lastRelX = relX; holder.lastRelY = relY
            holder.lastBarW = size.width; holder.lastBarH = size.height
            holder.lastMargin = marginPx; holder.lastWashArgb = washArgb
            holder.lastBlurPx = blurPx; holder.lastLow = low
            holder.lastShaderKey = glass
            holder.lastChrome = chrome; holder.lastAccent = accent
            holder.lastPillRect = pillRect; holder.lastPillShown = pillShown; holder.lastPillLift = pillLift

            holder.capNode = node
            holder.capRelX = relX
            holder.capRelY = relY
            holder.capMargin = marginPx
            holder.capScale = actualScale
            holder.capWidth = capW
            holder.capHeight = capH
            holder.capBackdropDirty = backdropChanged || dirtyRetry || holder.lastBlurredTex == 0

            synchronized(holder.lock) {
                holder.reqCapW = capW
                holder.reqCapH = capH
                holder.reqOutW = outW
                holder.reqOutH = outH
                holder.reqBlurPx = blurPx * actualScale
                holder.reqWashArgb = washArgb
                holder.reqGlsl = transpiled.glsl
                holder.reqSampler = samplerName
                val u = holder.reqUniforms
                u.viewportW = outW.toFloat()
                u.viewportH = outH.toFloat()
                // offset fragment coords by the margin so we shade only visible bar pixels while sampling the full bled backdrop
                u.coordOffsetX = scaledMargin.toFloat()
                u.coordOffsetY = scaledMargin.toFloat()
                u.contentW = capW.toFloat()
                u.contentH = capH.toFloat()
                u.contentDecal = 1f
                u.sizeW = capW.toFloat()
                u.sizeH = capH.toFloat()
                u.margin = scaledMargin.toFloat()
                u.radius = (outH / 2f).coerceAtLeast(1f)
                u.density = density.density * actualScale
                u.quality = if (low) 0f else 1f
                u.kind = surface.kind
                u.chromeArgb = chrome
                u.accentArgb = accent
                if (pillRect != null) {
                    u.pillLeft = pillRect.left * actualScale + scaledMargin
                    u.pillTop = pillRect.top * actualScale + scaledMargin
                    u.pillRight = pillRect.right * actualScale + scaledMargin
                    u.pillBottom = pillRect.bottom * actualScale + scaledMargin
                } else {
                    u.pillLeft = 0f; u.pillTop = 0f; u.pillRight = 0f; u.pillBottom = 0f
                }
                u.pillShown = pillShown
                u.pillLift = pillLift
                u.custom = glass.uniforms
            }

            if (holder.postScheduled.compareAndSet(false, true)) {
                MainHandler.post(captureRunnable)
            }
        }
    }
}

private fun runGlassPass(
    holder: BackdropGlHolder,
    glsl: String,
    samplerName: String,
    outputTick: androidx.compose.runtime.MutableIntState,
    onFailed: (String) -> Unit,
) {
    holder.glPending.set(false)
    if (holder.released) return
    val u = holder.glUniforms
    val capW: Int; val capH: Int; val outW: Int; val outH: Int
    val blurPx: Float; val washArgb: Int; val hasNewInput: Boolean
    synchronized(holder.lock) {
        capW = holder.reqCapW; capH = holder.reqCapH
        outW = holder.reqOutW; outH = holder.reqOutH
        blurPx = holder.reqBlurPx; washArgb = holder.reqWashArgb
        hasNewInput = holder.reqHasNewInput
        holder.reqHasNewInput = false
        u.copyFrom(holder.reqUniforms)
    }
    if (capW <= 0 || capH <= 0 || outW <= 0 || outH <= 0) return

    val prog = GlFallbackEngine.programFor(glsl)
    if (prog.error != null || prog.id == 0) {
        val msg = prog.error ?: "shader failed"
        MainHandler.post { onFailed(msg) }
        return
    }
    if (!holder.output.ensureSize(outW, outH) || !holder.output.makeCurrent()) return

    if (hasNewInput) {
        val updated = holder.input.updateTexImage()
        holder.pendingInput.set(false)
        if (updated) {
            holder.kawase.ensureSize(capW, capH)
            GlFallbackEngine.blitOesToFbo(holder.input.texId, holder.input.stMatrix, holder.kawase.fbo[0], capW, capH, washArgb)
            holder.lastBlurredTex = GlFallbackEngine.runKawaseBlur(holder.kawase, blurPx)
        }
    }
    val contentTex = holder.lastBlurredTex
    if (contentTex == 0) return

    GlFallbackEngine.drawAgslShader(
        program = prog,
        contentTex = contentTex,
        samplerName = samplerName,
        outW = outW,
        outH = outH,
        flipY = holder.output.usesHardwareBuffer,
        u = u,
    )
    val bmp = holder.output.finishFrame() ?: return
    if (holder.released) return
    holder.bitmap = bmp
    MainHandler.post {
        if (!holder.released) outputTick.intValue++
    }
}

// ---- module frosted / progressive backdrop blur on android 10-11 (api 29-30) ------------------

/** gpu dual-kawase backdrop blur for module cards, boxes, blur bands and frosted chrome on android 10-11 */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
internal fun FallbackGlassBlur(
    state: GlassState,
    radius: () -> Float,
    tint: Color,
    foundationColor: Color?,
    accentWash: (() -> Color)?,
    progressive: BlurDirection?,
    progressiveRamp: Float,
    maskMode: Int,
    maskParam: Float,
    modifier: Modifier = Modifier,
) {
    val low = LocalBlurQuality.current == BlurQuality.LOW
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // quarter-res capture gives 16x fewer pixels and smooth hardware bilinear upscaling
    val scale = if (low) 0.25f else 0.35f

    var position by remember { mutableStateOf(Offset.Zero) }
    val holder = remember { BackdropGlHolder() }
    val outputTick = remember { mutableIntStateOf(0) }

    DisposableEffect(state, holder) {
        state.addFallbackReader()
        onDispose {
            state.removeFallbackReader()
            holder.released = true
            holder.bitmap = null
            GlFallbackEngine.post {
                holder.input.release()
                holder.kawase.release()
                holder.output.release()
            }
        }
    }

    val dispatchGl = remember(holder) {
        {
            if (holder.glPending.compareAndSet(false, true)) {
                GlFallbackEngine.post { runBlurOnlyPass(holder, outputTick) }
            }
        }
    }

    val captureRunnable = remember(holder, dispatchGl) {
        Runnable {
            holder.postScheduled.set(false)
            if (holder.released || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@Runnable
            if (holder.capBackdropDirty) {
                val node = holder.capNode
                if (node != null && glassNodeHasContent(node)) {
                    if (holder.pendingInput.get()) {
                        holder.dirtyWhileBusy.set(true)
                        return@Runnable
                    }
                    val inputSurf = holder.inputSurface
                    if (inputSurf == null || holder.input.width != holder.capWidth || holder.input.height != holder.capHeight) {
                        holder.dirtyWhileBusy.set(true)
                        val cw = holder.capWidth
                        val ch = holder.capHeight
                        GlFallbackEngine.post {
                            if (holder.released) return@post
                            val made = holder.input.ensureSize(cw, ch)
                            holder.inputSurface = made
                            if (made != null) MainHandler.post { outputTick.intValue++ }
                        }
                        return@Runnable
                    }
                    val captured = captureRenderNodeToSurface(
                        surface = inputSurf,
                        node = node,
                        relX = holder.capRelX,
                        relY = holder.capRelY,
                        margin = 0,
                        scale = holder.capScale,
                        foundationArgb = holder.capFoundationArgb,
                    )
                    if (captured) {
                        holder.pendingInput.set(true)
                        synchronized(holder.lock) { holder.reqHasNewInput = true }
                    }
                }
            }
            dispatchGl()
        }
    }

    Box(modifier = modifier.onGloballyPositioned { position = it.positionInWindow() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            outputTick.intValue
            val bmp = holder.bitmap
            if (bmp != null) {
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                )
            } else {
                // instant clean foundation + tint until the first gpu blur lands a frame later
                if (foundationColor != null) drawRect(foundationColor)
                if (tint != Color.Transparent) drawRect(tint)
            }
            if (obscured.value || size.width < 1f || size.height < 1f) return@Canvas
            val node = state.renderNode ?: return@Canvas
            if (!glassNodeHasContent(node)) return@Canvas

            val relX = position.x - state.rootPosition.x
            val relY = position.y - state.rootPosition.y
            val blurPx = radius().coerceAtLeast(0f)
            val tintArgb = tint.toArgb()
            val foundationArgb = foundationColor?.toArgb() ?: 0
            val washArgb = accentWash?.invoke()?.takeIf { it.alpha > 0f }?.toArgb() ?: 0
            val progDir = when (progressive) {
                BlurDirection.TopToBottom -> 1
                BlurDirection.BottomToTop -> 2
                null -> 0
            }

            val backdropChanged = holder.lastDrawGen != state.drawGeneration ||
                holder.lastRelX != relX || holder.lastRelY != relY ||
                holder.lastBarW != size.width || holder.lastBarH != size.height ||
                holder.lastBlurPx != blurPx || holder.lastFoundation != foundationArgb ||
                holder.bitmap == null
            val compositeChanged = holder.lastTint != tintArgb || holder.lastWashArgb != washArgb ||
                holder.lastMaskMode != maskMode || holder.lastProgDir != progDir || holder.lastProgRamp != progressiveRamp

            val dirtyRetry = holder.dirtyWhileBusy.getAndSet(false)
            if (!backdropChanged && !compositeChanged && !dirtyRetry) return@Canvas

            val (outW, outH) = clampedFallbackSize(size.width, size.height, scale, MaxBlurEdge)
            val actualScale = if (size.width > 0f) outW.toFloat() / size.width else scale

            holder.lastDrawGen = state.drawGeneration
            holder.lastRelX = relX; holder.lastRelY = relY
            holder.lastBarW = size.width; holder.lastBarH = size.height
            holder.lastBlurPx = blurPx; holder.lastFoundation = foundationArgb
            holder.lastTint = tintArgb; holder.lastWashArgb = washArgb
            holder.lastMaskMode = maskMode; holder.lastProgDir = progDir; holder.lastProgRamp = progressiveRamp

            holder.capNode = node
            holder.capRelX = relX
            holder.capRelY = relY
            holder.capMargin = 0
            holder.capScale = actualScale
            holder.capWidth = outW
            holder.capHeight = outH
            holder.capFoundationArgb = foundationArgb
            holder.capBackdropDirty = backdropChanged || dirtyRetry || holder.lastBlurredTex == 0

            synchronized(holder.lock) {
                holder.reqCapW = outW
                holder.reqCapH = outH
                holder.reqOutW = outW
                holder.reqOutH = outH
                holder.reqBlurPx = blurPx * actualScale
                holder.reqComposite.tintArgb = tintArgb
                holder.reqComposite.foundationArgb = foundationArgb
                holder.reqComposite.washArgb = washArgb
                holder.reqComposite.maskMode = maskMode
                holder.reqComposite.maskParam = maskParam
                holder.reqComposite.progressiveDir = progDir
                holder.reqComposite.progressiveRamp = progressiveRamp
            }

            if (holder.postScheduled.compareAndSet(false, true)) {
                MainHandler.post(captureRunnable)
            }
        }
    }
}

private fun runBlurOnlyPass(holder: BackdropGlHolder, outputTick: androidx.compose.runtime.MutableIntState) {
    holder.glPending.set(false)
    if (holder.released) return
    val comp = holder.glComposite
    val capW: Int; val capH: Int; val blurPx: Float; val hasNewInput: Boolean
    synchronized(holder.lock) {
        capW = holder.reqCapW; capH = holder.reqCapH
        blurPx = holder.reqBlurPx
        hasNewInput = holder.reqHasNewInput
        holder.reqHasNewInput = false
        comp.copyFrom(holder.reqComposite)
    }
    if (capW <= 0 || capH <= 0) return
    if (!holder.output.ensureSize(capW, capH) || !holder.output.makeCurrent()) return

    if (hasNewInput) {
        val updated = holder.input.updateTexImage()
        holder.pendingInput.set(false)
        if (updated) {
            holder.kawase.ensureSize(capW, capH)
            GlFallbackEngine.blitOesToFbo(holder.input.texId, holder.input.stMatrix, holder.kawase.fbo[0], capW, capH, 0)
            holder.lastBlurredTex = GlFallbackEngine.runKawaseBlur(holder.kawase, blurPx)
        }
    }
    val blurred = holder.lastBlurredTex
    if (blurred == 0 || holder.kawase.levels == 0) return
    GlFallbackEngine.drawBlurComposite(holder.kawase.tex[0], blurred, capW, capH, comp)
    val bmp = holder.output.finishFrame() ?: return
    if (holder.released) return
    holder.bitmap = bmp
    MainHandler.post {
        if (!holder.released) outputTick.intValue++
    }
}

// ---- standalone agsl fill shaders (player, nav bar, module page) on android 7-12 ---------------

private class PaintGlHolder {
    val output = GlOutputTarget()
    @Volatile var bitmap: ImageBitmap? = null
    @Volatile var released = false
    val glPending = AtomicBoolean(false)
    val lock = Any()
    var reqW = 0
    var reqH = 0
    val reqUniforms = GlUniformBundle()
    // reused exclusively on the shared GL thread so standalone shader frames never allocate
    val glUniforms = GlUniformBundle()
    var lastW = -1
    var lastH = -1
    var lastSignature = 0L
}

/** renders a standalone transpiled agsl shader (player background, block, nav bar or page) via gles 3.0 on android 7-12 */
@Composable
internal fun FallbackGlShaderBox(
    transpiled: TranspiledAgsl,
    modifier: Modifier,
    onFailed: (String) -> Unit,
    fillUniforms: (out: GlUniformBundle, size: Size, density: Float, scale: Float) -> Long,
) {
    val low = LocalBlurQuality.current == BlurQuality.LOW
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scale = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0.5f
        low -> 0.5f
        else -> 0.75f
    }
    val holder = remember { PaintGlHolder() }
    val outputTick = remember { mutableIntStateOf(0) }
    val currentOnFailed by rememberUpdatedState(onFailed)
    val scratch = remember { GlUniformBundle() }

    DisposableEffect(holder) {
        onDispose {
            holder.released = true
            holder.bitmap = null
            GlFallbackEngine.post { holder.output.release() }
        }
    }

    // force a redraw if the transpiled program swaps
    LaunchedEffect(transpiled) {
        holder.lastSignature = Long.MIN_VALUE
        outputTick.intValue++
    }

    Spacer(
        modifier
            .damageBoundary()
            .drawBehind {
                outputTick.intValue
                val bmp = holder.bitmap
                if (bmp != null) {
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    )
                }
                if (obscured.value || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || size.width < 1f || size.height < 1f) return@drawBehind
                val (outW, outH) = clampedFallbackSize(size.width, size.height, scale, MaxPaintEdge)
                val actualScale = if (size.width > 0f) outW.toFloat() / size.width else scale
                scratch.viewportW = outW.toFloat()
                scratch.viewportH = outH.toFloat()
                scratch.coordOffsetX = 0f
                scratch.coordOffsetY = 0f
                scratch.sizeW = outW.toFloat()
                scratch.sizeH = outH.toFloat()
                scratch.density = density * actualScale
                val sig = fillUniforms(scratch, Size(outW.toFloat(), outH.toFloat()), density * actualScale, actualScale)
                if (outW == holder.lastW && outH == holder.lastH && sig == holder.lastSignature && holder.bitmap != null) return@drawBehind
                holder.lastW = outW
                holder.lastH = outH
                holder.lastSignature = sig
                synchronized(holder.lock) {
                    holder.reqW = outW
                    holder.reqH = outH
                    holder.reqUniforms.copyFrom(scratch)
                }
                if (holder.glPending.compareAndSet(false, true)) {
                    GlFallbackEngine.post {
                        holder.glPending.set(false)
                        if (holder.released) return@post
                        val w: Int; val h: Int
                        val u = holder.glUniforms
                        synchronized(holder.lock) {
                            w = holder.reqW; h = holder.reqH
                            u.copyFrom(holder.reqUniforms)
                        }
                        if (w <= 0 || h <= 0) return@post
                        val prog = GlFallbackEngine.programFor(transpiled.glsl)
                        if (prog.error != null || prog.id == 0) {
                            val msg = prog.error ?: "shader failed"
                            MainHandler.post { currentOnFailed(msg) }
                            return@post
                        }
                        if (!holder.output.ensureSize(w, h) || !holder.output.makeCurrent()) return@post
                        GlFallbackEngine.drawAgslShader(
                            program = prog,
                            contentTex = 0,
                            samplerName = null,
                            outW = w,
                            outH = h,
                            flipY = holder.output.usesHardwareBuffer,
                            u = u,
                        )
                        val rendered = holder.output.finishFrame() ?: return@post
                        if (holder.released) return@post
                        holder.bitmap = rendered
                        MainHandler.post {
                            if (!holder.released) outputTick.intValue++
                        }
                    }
                }
            },
    )
}

// ---- zero-overhead static fallback chrome glass (api 24-28 or explicit fallback config) -------

/** draws a module's declared static glass fallback (tint, top sheen and rim) with zero offscreen passes */
@Composable
internal fun StaticFallbackChromeGlass(
    fallback: SkinGlassFallback,
    pureBlack: Boolean,
    accentWash: (() -> Color)?,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val base = fallback.tint?.onChrome(accent, emptyMap()) ?: (if (pureBlack) Color.Black else GlassChromeColor).copy(alpha = 0.84f)
    val rim = fallback.rim?.onChrome(accent, emptyMap()) ?: Color.White.copy(alpha = 0.22f)
    val sheen = fallback.sheen
    Spacer(
        modifier = modifier.drawBehind {
            drawRect(base)
            val wash = accentWash?.invoke()
            if (wash != null && wash.alpha > 0f) drawRect(wash.copy(alpha = wash.alpha * 0.55f))
            if (sheen > 0.001f) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = sheen),
                        0.45f to Color.Transparent,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            if (rim.alpha > 0.001f) {
                val strokePx = 1.2.dp.toPx()
                val half = strokePx / 2f
                val corner = (size.height / 2f - half).coerceAtLeast(0f)
                drawRoundRect(
                    color = rim,
                    topLeft = Offset(half, half),
                    size = Size((size.width - strokePx).coerceAtLeast(0f), (size.height - strokePx).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(strokePx),
                )
            }
        },
    )
}

// draws the recorded RenderNode slice into a hardware-backed SurfaceTexture outside Compose's RecordingCanvas
@RequiresApi(Build.VERSION_CODES.Q)
private fun captureRenderNodeToSurface(
    surface: Surface,
    node: Any,
    relX: Float,
    relY: Float,
    margin: Int,
    scale: Float,
    foundationArgb: Int,
): Boolean {
    if (!surface.isValid) return false
    return runCatching {
        val canvas = surface.lockHardwareCanvas()
        try {
            if (foundationArgb != 0) {
                canvas.drawColor(foundationArgb, PorterDuff.Mode.SRC)
            } else {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            }
            canvas.scale(scale, scale)
            canvas.translate(-(relX - margin), -(relY - margin))
            canvas.drawGlassNode(node)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        true
    }.onFailure { Timber.tag(Tag).w(it, "backdrop capture to surface failed") }.getOrDefault(false)
}

private const val Tag = "GlFallbackSurfaces"
private const val MaxGlassEdge = 960
private const val MaxBlurEdge = 360
private const val MaxPaintEdge = 720
