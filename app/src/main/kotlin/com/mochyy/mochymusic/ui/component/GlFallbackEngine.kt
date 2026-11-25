// shared opengl es 3.0 worker for running transpiled module agsl shaders and dual-kawase backdrop blur on android 12 and below

package com.mochyy.mochymusic.ui.component

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** reusable uniform bundle uploaded to a transpiled glsl shader without per-frame map allocations */
internal class GlUniformBundle {
    var viewportW = 0f
    var viewportH = 0f
    var coordOffsetX = 0f
    var coordOffsetY = 0f
    var contentW = 0f
    var contentH = 0f
    var contentDecal = 1f
    var sizeW = 0f
    var sizeH = 0f
    var margin = 0f
    var radius = 0f
    var density = 1f
    var quality = 1f
    var kind = 0f
    var chromeArgb = 0
    var accentArgb = 0
    var coverArgb = 0
    var playedArgb = 0
    var remainingArgb = 0
    var pillLeft = 0f
    var pillTop = 0f
    var pillRight = 0f
    var pillBottom = 0f
    var pillShown = 0f
    var pillLift = 0f
    var time = 0f
    var progress = 0f
    var level = 0f
    var bass = 0f
    var mid = 0f
    var treble = 0f
    var playing = 0f
    var tabs = 0f
    var selected = 0f
    var pressed = 0f
    var touchX = -1f
    var touchY = -1f
    var line = 0f
    var lineCount = 0f
    var lineProgress = 0f
    var wordProgress = 0f
    var singing = 0f
    var position = 0f
    var duration = 0f
    var custom: Map<String, Float> = emptyMap()

    fun copyFrom(src: GlUniformBundle) {
        viewportW = src.viewportW; viewportH = src.viewportH
        coordOffsetX = src.coordOffsetX; coordOffsetY = src.coordOffsetY
        contentW = src.contentW; contentH = src.contentH; contentDecal = src.contentDecal
        sizeW = src.sizeW; sizeH = src.sizeH
        margin = src.margin; radius = src.radius; density = src.density
        quality = src.quality; kind = src.kind
        chromeArgb = src.chromeArgb; accentArgb = src.accentArgb; coverArgb = src.coverArgb
        playedArgb = src.playedArgb; remainingArgb = src.remainingArgb
        pillLeft = src.pillLeft; pillTop = src.pillTop; pillRight = src.pillRight; pillBottom = src.pillBottom
        pillShown = src.pillShown; pillLift = src.pillLift
        time = src.time; progress = src.progress
        level = src.level; bass = src.bass; mid = src.mid; treble = src.treble
        playing = src.playing; tabs = src.tabs; selected = src.selected; pressed = src.pressed
        touchX = src.touchX; touchY = src.touchY
        line = src.line; lineCount = src.lineCount; lineProgress = src.lineProgress
        wordProgress = src.wordProgress; singing = src.singing
        position = src.position; duration = src.duration
        custom = src.custom
    }
}

/** blur-only composite parameters for android 10-11 module frosted/progressive glass */
internal class GlBlurCompositeParams {
    var tintArgb = 0
    var foundationArgb = 0
    var washArgb = 0
    var maskMode = 0 // 0 none, 1 top->bottom fade, 2 bottom->top fade, 3 radial
    var maskParam = 0f
    var progressiveDir = 0 // 0 uniform, 1 top->bottom, 2 bottom->top
    var progressiveRamp = 1f

    fun copyFrom(src: GlBlurCompositeParams) {
        tintArgb = src.tintArgb; foundationArgb = src.foundationArgb; washArgb = src.washArgb
        maskMode = src.maskMode; maskParam = src.maskParam
        progressiveDir = src.progressiveDir; progressiveRamp = src.progressiveRamp
    }
}

/** single lazy gl thread and egl context shared by all fallback glass and shader surfaces, never touched on android 13+ */
internal object GlFallbackEngine {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var initAttempted = false
    private var ready = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var dummySurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var emptyVao = 0

    // shared built-in programs compiled once on first use
    private var oesBlitProgram = 0
    private var oesBlitUsesVbo = false
    private var oesQuadBuf: FloatBuffer? = null
    private var oesLocMatrix = -1
    private var oesLocWash = -1
    private var oesLocTex = -1
    private var oesLocPos = -1

    private var kawaseDownProgram = 0
    private var kawaseDownLocHalf = -1
    private var kawaseDownLocOff = -1

    private var kawaseUpProgram = 0
    private var kawaseUpLocHalf = -1
    private var kawaseUpLocOff = -1

    private var compositeProgram = 0
    private var compLocSharp = -1
    private var compLocBlur = -1
    private var compLocTint = -1
    private var compLocFoundation = -1
    private var compLocWash = -1
    private var compLocMaskMode = -1
    private var compLocMaskParam = -1
    private var compLocProgDir = -1
    private var compLocProgRamp = -1

    internal class CompiledGlProgram(val id: Int, val uniforms: HashMap<String, Int>, val error: String?)

    // bounded LRU of compiled glsl programs keyed by full source text, touched only on the gl thread
    private val programCache = LinkedHashMap<String, CompiledGlProgram>(32, 0.75f, true)
    private const val MaxCachedPrograms = 24

    @Synchronized
    private fun ensureHandler(): Handler {
        handler?.let { return it }
        val t = HandlerThread("MochyGlFallback", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
        thread = t
        return Handler(t.looper).also { handler = it }
    }

    /** runs [block] on the shared gl thread once the egl context is ready; skips silently if gles 3.0 isn't available */
    fun post(block: () -> Unit) {
        ensureHandler().post {
            if (!ensureInitialized()) return@post
            try {
                block()
            } catch (t: Throwable) {
                Timber.tag(Tag).w(t, "gles fallback render failed")
            }
        }
    }

    /** validates and pre-compiles [glsl] off the main thread, returning the compiler's first error line or null on success */
    fun compileBlocking(glsl: String): String? {
        val latch = CountDownLatch(1)
        var error: String? = null
        ensureHandler().post {
            error = if (!ensureInitialized()) {
                "OpenGL ES 3.0 isn't available on this device"
            } else {
                programFor(glsl).error
            }
            latch.countDown()
        }
        if (!latch.await(4, TimeUnit.SECONDS)) return "shader compile timed out"
        return error
    }

    internal fun isReadyOnGlThread(): Boolean = ready

    internal fun makeCurrent(surface: EGLSurface): Boolean {
        if (!ready) return false
        return EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
    }

    internal fun makeDummyCurrent() {
        if (ready && dummySurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, dummySurface, dummySurface, eglContext)
        }
    }

    internal fun createWindowSurface(surface: Surface): EGLSurface {
        val cfg = eglConfig ?: return EGL14.EGL_NO_SURFACE
        return EGL14.eglCreateWindowSurface(eglDisplay, cfg, surface, intArrayOf(EGL14.EGL_NONE), 0)
    }

    internal fun createPbufferSurface(width: Int, height: Int): EGLSurface {
        val cfg = eglConfig ?: return EGL14.EGL_NO_SURFACE
        val attrs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        return EGL14.eglCreatePbufferSurface(eglDisplay, cfg, attrs, 0)
    }

    internal fun destroySurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            makeDummyCurrent()
            EGL14.eglDestroySurface(eglDisplay, surface)
        }
    }

    internal fun swapBuffers(surface: EGLSurface): Boolean =
        EGL14.eglSwapBuffers(eglDisplay, surface)

    internal fun programFor(glsl: String): CompiledGlProgram {
        programCache[glsl]?.let { return it }
        val built = buildProgram(FullscreenVert, glsl)
        programCache[glsl] = built
        if (programCache.size > MaxCachedPrograms) {
            val eldest = programCache.entries.iterator().next()
            programCache.remove(eldest.key)
            if (eldest.value.id != 0) GLES30.glDeleteProgram(eldest.value.id)
        }
        return built
    }

    /** blits a SurfaceTexture external oes image into a 2d fbo texture, applying optional accent wash before blur */
    internal fun blitOesToFbo(
        oesTexId: Int,
        stMatrix: FloatArray,
        targetFbo: Int,
        width: Int,
        height: Int,
        washArgb: Int,
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(oesBlitProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES30.glUniform1i(oesLocTex, 0)
        GLES30.glUniformMatrix4fv(oesLocMatrix, 1, false, stMatrix, 0)
        setColorVec4(oesLocWash, washArgb)
        if (oesBlitUsesVbo) {
            val buf = oesQuadBuf ?: return
            GLES30.glBindVertexArray(0)
            GLES20.glEnableVertexAttribArray(oesLocPos)
            GLES20.glVertexAttribPointer(oesLocPos, 2, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(oesLocPos)
            GLES30.glBindVertexArray(emptyVao)
        } else {
            drawFullscreenTriangle()
        }
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    /** runs dual-kawase downsample + upsample passes across [chain], returning the texture id with the blurred result */
    internal fun runKawaseBlur(chain: GlKawaseChain, blurPx: Float): Int {
        if (blurPx <= 0.5f) return chain.tex[0]
        val passes = when {
            blurPx <= 10f -> 1
            blurPx <= 28f -> 2
            else -> 3
        }.coerceAtMost(chain.levels - 1)
        if (passes <= 0) return chain.tex[0]
        // smooth offset scales the filter continuously between discrete pyramid levels
        val scaleBase = (1 shl passes).toFloat()
        val offset = (blurPx / (scaleBase * 3.2f)).coerceIn(0.6f, 3.0f)

        GLES30.glDisable(GLES30.GL_BLEND)
        // downsample: 0 -> 1 -> ... -> passes
        GLES30.glUseProgram(kawaseDownProgram)
        for (i in 0 until passes) {
            val dst = i + 1
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, chain.fbo[dst])
            GLES30.glViewport(0, 0, chain.w[dst], chain.h[dst])
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, chain.tex[i])
            GLES30.glUniform2f(kawaseDownLocHalf, 0.5f / chain.w[i], 0.5f / chain.h[i])
            GLES30.glUniform1f(kawaseDownLocOff, offset)
            drawFullscreenTriangle()
        }
        // upsample back up into the alternate ping-pong slot (or reuse levels going up)
        GLES30.glUseProgram(kawaseUpProgram)
        var currentTex = chain.tex[passes]
        var currentW = chain.w[passes]
        var currentH = chain.h[passes]
        for (i in passes - 1 downTo 0) {
            val targetFbo = if (i == 0) chain.blurFbo else chain.fbo[i]
            val targetTex = if (i == 0) chain.blurTex else chain.tex[i]
            val dstW = chain.w[i]
            val dstH = chain.h[i]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
            GLES30.glViewport(0, 0, dstW, dstH)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentTex)
            GLES30.glUniform2f(kawaseUpLocHalf, 0.5f / currentW, 0.5f / currentH)
            GLES30.glUniform1f(kawaseUpLocOff, offset)
            drawFullscreenTriangle()
            currentTex = targetTex
            currentW = dstW
            currentH = dstH
        }
        return currentTex
    }

    /** runs a blur-only frosted/progressive glass composite directly to the bound default egl surface */
    internal fun drawBlurComposite(
        sharpTex: Int,
        blurredTex: Int,
        outW: Int,
        outH: Int,
        params: GlBlurCompositeParams,
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outW, outH)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(compositeProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sharpTex)
        GLES30.glUniform1i(compLocSharp, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurredTex)
        GLES30.glUniform1i(compLocBlur, 1)
        setColorVec4(compLocTint, params.tintArgb)
        setColorVec4(compLocFoundation, params.foundationArgb)
        setColorVec4(compLocWash, params.washArgb)
        GLES30.glUniform1i(compLocMaskMode, params.maskMode)
        GLES30.glUniform1f(compLocMaskParam, params.maskParam)
        GLES30.glUniform1i(compLocProgDir, params.progressiveDir)
        GLES30.glUniform1f(compLocProgRamp, params.progressiveRamp)
        drawFullscreenTriangle()
    }

    /** runs a transpiled agsl shader directly to the bound default egl surface, optionally binding [contentTex] */
    internal fun drawAgslShader(
        program: CompiledGlProgram,
        contentTex: Int,
        samplerName: String?,
        outW: Int,
        outH: Int,
        flipY: Boolean,
        u: GlUniformBundle,
    ) {
        if (program.id == 0) return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outW, outH)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(program.id)

        val locs = program.uniforms
        locs["_u_viewport"]?.let { GLES30.glUniform2f(it, u.viewportW, u.viewportH) }
        locs["_u_coord_offset"]?.let { GLES30.glUniform2f(it, u.coordOffsetX, u.coordOffsetY) }
        locs["_u_flip_y"]?.let { GLES30.glUniform1f(it, if (flipY) 1f else 0f) }
        if (contentTex != 0 && samplerName != null) {
            locs["_u_content_size"]?.let { GLES30.glUniform2f(it, u.contentW, u.contentH) }
            locs["_u_content_decal"]?.let { GLES30.glUniform1f(it, u.contentDecal) }
            locs[samplerName]?.let { loc ->
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, contentTex)
                GLES30.glUniform1i(loc, 0)
            }
        }
        locs["size"]?.let { GLES30.glUniform2f(it, u.sizeW, u.sizeH) }
        locs["margin"]?.let { GLES30.glUniform1f(it, u.margin) }
        locs["radius"]?.let { GLES30.glUniform1f(it, u.radius) }
        locs["density"]?.let { GLES30.glUniform1f(it, u.density) }
        locs["quality"]?.let { GLES30.glUniform1f(it, u.quality) }
        locs["kind"]?.let { GLES30.glUniform1f(it, u.kind) }
        locs["chrome"]?.let { setColorVec4(it, u.chromeArgb) }
        locs["accent"]?.let { setColorVec4(it, u.accentArgb) }
        locs["cover"]?.let { setColorVec4(it, u.coverArgb) }
        locs["played"]?.let { setColorVec4(it, u.playedArgb) }
        locs["remaining"]?.let { setColorVec4(it, u.remainingArgb) }
        locs["pill"]?.let { GLES30.glUniform4f(it, u.pillLeft, u.pillTop, u.pillRight, u.pillBottom) }
        locs["pillShown"]?.let { GLES30.glUniform1f(it, u.pillShown) }
        locs["pillLift"]?.let { GLES30.glUniform1f(it, u.pillLift) }
        locs["time"]?.let { GLES30.glUniform1f(it, u.time) }
        locs["progress"]?.let { GLES30.glUniform1f(it, u.progress) }
        locs["level"]?.let { GLES30.glUniform1f(it, u.level) }
        locs["bass"]?.let { GLES30.glUniform1f(it, u.bass) }
        locs["mid"]?.let { GLES30.glUniform1f(it, u.mid) }
        locs["treble"]?.let { GLES30.glUniform1f(it, u.treble) }
        locs["playing"]?.let { GLES30.glUniform1f(it, u.playing) }
        locs["tabs"]?.let { GLES30.glUniform1f(it, u.tabs) }
        locs["selected"]?.let { GLES30.glUniform1f(it, u.selected) }
        locs["pressed"]?.let { GLES30.glUniform1f(it, u.pressed) }
        locs["touch"]?.let { GLES30.glUniform2f(it, u.touchX, u.touchY) }
        locs["line"]?.let { GLES30.glUniform1f(it, u.line) }
        locs["lineCount"]?.let { GLES30.glUniform1f(it, u.lineCount) }
        locs["lineProgress"]?.let { GLES30.glUniform1f(it, u.lineProgress) }
        locs["wordProgress"]?.let { GLES30.glUniform1f(it, u.wordProgress) }
        locs["singing"]?.let { GLES30.glUniform1f(it, u.singing) }
        locs["position"]?.let { GLES30.glUniform1f(it, u.position) }
        locs["duration"]?.let { GLES30.glUniform1f(it, u.duration) }
        if (u.custom.isNotEmpty()) {
            u.custom.forEach { (name, value) -> locs[name]?.let { GLES30.glUniform1f(it, value) } }
        }
        drawFullscreenTriangle()
    }

    private fun drawFullscreenTriangle() {
        GLES30.glBindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun setColorVec4(loc: Int, argb: Int) {
        if (loc < 0) return
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        GLES30.glUniform4f(loc, r, g, b, a)
    }

    private fun ensureInitialized(): Boolean {
        if (initAttempted) return ready
        initAttempted = true
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        var picked = EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0
        if (!picked) {
            // some older drivers only advertise es2 bit on configs even though they create an es3 context cleanly
            configAttribs[9] = EGL14.EGL_OPENGL_ES2_BIT
            picked = EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0
        }
        if (!picked) return false
        val cfg = configs[0] ?: return false
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val ctx = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (ctx == EGL14.EGL_NO_CONTEXT) return false
        val pbuf = EGL14.eglCreatePbufferSurface(
            display, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        if (pbuf == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, pbuf, pbuf, ctx)) {
            EGL14.eglDestroyContext(display, ctx)
            return false
        }
        eglDisplay = display
        eglContext = ctx
        eglConfig = cfg
        dummySurface = pbuf

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        emptyVao = vaos[0]

        // try essl3 external texture first, falling back to essl1 for older drivers without the essl3 extension
        val oes3 = buildProgram(FullscreenVert, OesBlitFrag300)
        if (oes3.id != 0) {
            oesBlitProgram = oes3.id
            oesBlitUsesVbo = false
            oesLocMatrix = GLES30.glGetUniformLocation(oes3.id, "uStMatrix")
            oesLocWash = GLES30.glGetUniformLocation(oes3.id, "uWash")
            oesLocTex = GLES30.glGetUniformLocation(oes3.id, "uTex")
        } else {
            val oes1 = buildProgram(OesFallbackVert100, OesBlitFrag100)
            if (oes1.id == 0) return false
            oesBlitProgram = oes1.id
            oesBlitUsesVbo = true
            oesLocMatrix = GLES20.glGetUniformLocation(oes1.id, "uStMatrix")
            oesLocWash = GLES20.glGetUniformLocation(oes1.id, "uWash")
            oesLocTex = GLES20.glGetUniformLocation(oes1.id, "uTex")
            oesLocPos = GLES20.glGetAttribLocation(oes1.id, "aPos")
            oesQuadBuf = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }
        }

        val down = buildProgram(FullscreenVert, KawaseDownFrag)
        val up = buildProgram(FullscreenVert, KawaseUpFrag)
        val comp = buildProgram(FullscreenVert, BlurCompositeFrag)
        if (down.id == 0 || up.id == 0 || comp.id == 0) return false
        kawaseDownProgram = down.id
        kawaseDownLocHalf = GLES30.glGetUniformLocation(down.id, "uHalfPixel")
        kawaseDownLocOff = GLES30.glGetUniformLocation(down.id, "uOffset")
        kawaseUpProgram = up.id
        kawaseUpLocHalf = GLES30.glGetUniformLocation(up.id, "uHalfPixel")
        kawaseUpLocOff = GLES30.glGetUniformLocation(up.id, "uOffset")
        compositeProgram = comp.id
        compLocSharp = GLES30.glGetUniformLocation(comp.id, "uSharpTex")
        compLocBlur = GLES30.glGetUniformLocation(comp.id, "uBlurTex")
        compLocTint = GLES30.glGetUniformLocation(comp.id, "uTint")
        compLocFoundation = GLES30.glGetUniformLocation(comp.id, "uFoundation")
        compLocWash = GLES30.glGetUniformLocation(comp.id, "uWash")
        compLocMaskMode = GLES30.glGetUniformLocation(comp.id, "uMaskMode")
        compLocMaskParam = GLES30.glGetUniformLocation(comp.id, "uMaskParam")
        compLocProgDir = GLES30.glGetUniformLocation(comp.id, "uProgDir")
        compLocProgRamp = GLES30.glGetUniformLocation(comp.id, "uProgRamp")

        ready = true
        return true
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): CompiledGlProgram {
        val vs = compileStage(GLES30.GL_VERTEX_SHADER, vertSrc)
        if (vs.second != null) return CompiledGlProgram(0, HashMap(), vs.second)
        val fs = compileStage(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (fs.second != null) {
            GLES30.glDeleteShader(vs.first)
            return CompiledGlProgram(0, HashMap(), fs.second)
        }
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs.first)
        GLES30.glAttachShader(prog, fs.first)
        GLES30.glLinkProgram(prog)
        GLES30.glDeleteShader(vs.first)
        GLES30.glDeleteShader(fs.first)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog).lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: "link failed"
            GLES30.glDeleteProgram(prog)
            return CompiledGlProgram(0, HashMap(), log)
        }
        val uniformCount = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_ACTIVE_UNIFORMS, uniformCount, 0)
        val map = HashMap<String, Int>(uniformCount[0] * 2)
        val sizeArr = IntArray(1)
        val typeArr = IntArray(1)
        for (i in 0 until uniformCount[0]) {
            val raw = GLES30.glGetActiveUniform(prog, i, sizeArr, 0, typeArr, 0) ?: continue
            val name = raw.substringBefore('[')
            val loc = GLES30.glGetUniformLocation(prog, name)
            if (loc >= 0) map[name] = loc
        }
        return CompiledGlProgram(prog, map, null)
    }

    private fun compileStage(type: Int, src: String): Pair<Int, String?> {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader).lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(200) ?: "compile failed"
            GLES30.glDeleteShader(shader)
            return 0 to log
        }
        return shader to null
    }

    private const val Tag = "GlFallbackEngine"
    private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040

    private const val FullscreenVert = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);
    vUv = p * 0.5 + 0.5;
    gl_Position = vec4(p, 0.0, 1.0);
}
"""

    private const val OesBlitFrag300 = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 vUv;
uniform samplerExternalOES uTex;
uniform mat4 uStMatrix;
uniform vec4 uWash;
out vec4 outColor;
void main() {
    vec2 uv = (uStMatrix * vec4(vUv, 0.0, 1.0)).xy;
    vec4 c = texture(uTex, uv);
    if (uWash.a > 0.001) {
        vec4 w = vec4(uWash.rgb * uWash.a, uWash.a);
        c = w + c * (1.0 - w.a);
    }
    outColor = c;
}
"""

    private const val OesFallbackVert100 = """attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    private const val OesBlitFrag100 = """#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
uniform mat4 uStMatrix;
uniform vec4 uWash;
void main() {
    vec2 uv = (uStMatrix * vec4(vUv, 0.0, 1.0)).xy;
    vec4 c = texture2D(uTex, uv);
    if (uWash.a > 0.001) {
        vec4 w = vec4(uWash.rgb * uWash.a, uWash.a);
        c = w + c * (1.0 - w.a);
    }
    gl_FragColor = c;
}
"""

    private const val KawaseDownFrag = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uHalfPixel;
uniform float uOffset;
out vec4 outColor;
void main() {
    vec2 d = uHalfPixel * uOffset;
    vec4 sum = texture(uTex, vUv) * 4.0;
    sum += texture(uTex, vUv - d);
    sum += texture(uTex, vUv + d);
    sum += texture(uTex, vUv + vec2(d.x, -d.y));
    sum += texture(uTex, vUv - vec2(d.x, -d.y));
    outColor = sum * 0.125;
}
"""

    private const val KawaseUpFrag = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uHalfPixel;
uniform float uOffset;
out vec4 outColor;
void main() {
    vec2 d = uHalfPixel * uOffset;
    vec4 sum = texture(uTex, vUv + vec2(-d.x * 2.0, 0.0));
    sum += texture(uTex, vUv + vec2(-d.x, d.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(0.0, d.y * 2.0));
    sum += texture(uTex, vUv + vec2(d.x, d.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(d.x * 2.0, 0.0));
    sum += texture(uTex, vUv + vec2(d.x, -d.y)) * 2.0;
    sum += texture(uTex, vUv + vec2(0.0, -d.y * 2.0));
    sum += texture(uTex, vUv + vec2(-d.x, -d.y)) * 2.0;
    outColor = sum * (1.0 / 12.0);
}
"""

    private const val BlurCompositeFrag = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uSharpTex;
uniform sampler2D uBlurTex;
uniform vec4 uTint;
uniform vec4 uFoundation;
uniform vec4 uWash;
uniform int uMaskMode;
uniform float uMaskParam;
uniform int uProgDir;
uniform float uProgRamp;
out vec4 outColor;
void main() {
    // vUv.y is 1 at the top and 0 at the bottom in gl; along grows from top (0) to bottom (1)
    float down = 1.0 - vUv.y;
    vec4 blurC = texture(uBlurTex, vUv);
    vec4 c = blurC;
    if (uProgDir == 1 || uProgDir == 2) {
        float along = (uProgDir == 1) ? down : vUv.y;
        float t = clamp(along / max(uProgRamp, 0.01), 0.0, 1.0);
        c = mix(texture(uSharpTex, vUv), blurC, t * t);
    }
    if (uFoundation.a > 0.001) {
        vec4 f = vec4(uFoundation.rgb * uFoundation.a, uFoundation.a);
        c = c + f * (1.0 - c.a);
    }
    if (uTint.a > 0.001) {
        vec4 ti = vec4(uTint.rgb * uTint.a, uTint.a);
        c = ti + c * (1.0 - ti.a);
    }
    if (uWash.a > 0.001) {
        vec4 wa = vec4(uWash.rgb * uWash.a, uWash.a);
        c = wa + c * (1.0 - wa.a);
    }
    float mask = 1.0;
    if (uMaskMode == 1) {
        mask = clamp(down / max(uMaskParam, 0.001), 0.0, 1.0);
    } else if (uMaskMode == 2) {
        mask = clamp(vUv.y / max(uMaskParam, 0.001), 0.0, 1.0);
    } else if (uMaskMode == 3) {
        float dist = length((vUv - vec2(0.5)) * 2.0);
        mask = 1.0 - smoothstep(uMaskParam, 1.0, dist);
    }
    outColor = c * mask;
}
"""
}

/** mip-chain of fbo textures used by dual-kawase blur, allocated once per capture size and freed on release */
internal class GlKawaseChain {
    val fbo = IntArray(4)
    val tex = IntArray(4)
    val w = IntArray(4)
    val h = IntArray(4)
    var blurFbo = 0
    var blurTex = 0
    var levels = 0
    private var curW = 0
    private var curH = 0

    fun ensureSize(width: Int, height: Int) {
        if (width == curW && height == curH && levels > 0) return
        release()
        curW = width
        curH = height
        var lw = width
        var lh = height
        var count = 0
        for (i in 0 until 4) {
            w[i] = lw
            h[i] = lh
            val pair = createFboTex(lw, lh)
            fbo[i] = pair.first
            tex[i] = pair.second
            count++
            if (lw <= 8 || lh <= 8) break
            lw = max(2, lw / 2)
            lh = max(2, lh / 2)
        }
        val blur = createFboTex(width, height)
        blurFbo = blur.first
        blurTex = blur.second
        levels = count
    }

    fun release() {
        if (levels > 0) {
            GLES30.glDeleteFramebuffers(levels, fbo, 0)
            GLES30.glDeleteTextures(levels, tex, 0)
            levels = 0
        }
        if (blurFbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(blurFbo), 0)
            blurFbo = 0
        }
        if (blurTex != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(blurTex), 0)
            blurTex = 0
        }
        curW = 0
        curH = 0
    }

    private fun createFboTex(width: Int, height: Int): Pair<Int, Int> {
        val t = IntArray(1)
        val f = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glGenFramebuffers(1, f, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t[0], 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return f[0] to t[0]
    }
}

/** zero-copy gpu output target on api 29+ (ImageReader + HardwareBuffer -> Bitmap.wrapHardwareBuffer), pbuffer + reused bitmaps on api 24-28 */
internal class GlOutputTarget {
    private var curW = 0
    private var curH = 0
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // api 29+ triple-buffered hardware output so hwui never reads a buffer slot egl is writing to
    private var reader: ImageReader? = null
    private var displayedImage: Image? = null
    private var retiringImage: Image? = null

    // api 24-28 software readback pair, alternating so compose draws one while gl fills the other
    private var readBuf: ByteBuffer? = null
    private var swA: Bitmap? = null
    private var swB: Bitmap? = null
    private var useA = true

    val usesHardwareBuffer: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun ensureSize(width: Int, height: Int): Boolean {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w == curW && h == curH && eglSurface != EGL14.EGL_NO_SURFACE) return true
        release()
        curW = w
        curH = h
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return initHardwareTarget(w, h)
        }
        val surface = GlFallbackEngine.createPbufferSurface(w, h)
        if (surface == EGL14.EGL_NO_SURFACE) return false
        eglSurface = surface
        readBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        swA = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        swB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initHardwareTarget(w: Int, h: Int): Boolean {
        val r = ImageReader.newInstance(
            w,
            h,
            PixelFormat.RGBA_8888,
            3,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val s = GlFallbackEngine.createWindowSurface(r.surface)
        if (s == EGL14.EGL_NO_SURFACE) {
            r.close()
            return false
        }
        reader = r
        eglSurface = s
        return true
    }

    fun makeCurrent(): Boolean = eglSurface != EGL14.EGL_NO_SURFACE && GlFallbackEngine.makeCurrent(eglSurface)

    /** finishes the frame and hands back an ImageBitmap ready for compose Canvas.drawImage */
    fun finishFrame(): ImageBitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return finishHardwareFrame()
        }
        val buf = readBuf ?: return null
        val bmp = (if (useA) swA else swB) ?: return null
        useA = !useA
        buf.position(0)
        GLES30.glReadPixels(0, 0, curW, curH, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        buf.position(0)
        bmp.copyPixelsFromBuffer(buf)
        return bmp.asImageBitmap()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun finishHardwareFrame(): ImageBitmap? {
        if (!GlFallbackEngine.swapBuffers(eglSurface)) return null
        val r = reader ?: return null
        val next = r.acquireLatestImage() ?: return null
        // retire the two-frames-old image before swapping so the currently drawn hardware buffer stays pinned
        runCatching { retiringImage?.close() }
        retiringImage = displayedImage
        displayedImage = next
        val hb = next.hardwareBuffer ?: return null
        val wrapped = hb.use { Bitmap.wrapHardwareBuffer(it, null) }
        return wrapped?.asImageBitmap()
    }

    fun release() {
        GlFallbackEngine.destroySurface(eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        runCatching { retiringImage?.close() }
        runCatching { displayedImage?.close() }
        retiringImage = null
        displayedImage = null
        runCatching { reader?.close() }
        reader = null
        readBuf = null
        swA?.recycle()
        swB?.recycle()
        swA = null
        swB = null
        curW = 0
        curH = 0
    }
}

/** creates an external oes texture + SurfaceTexture + Surface for receiving hardware-accelerated RenderNode captures */
internal class GlInputSurface {
    var texId = 0
        private set
    var surfaceTexture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set
    var width = 0
        private set
    var height = 0
        private set
    val stMatrix = FloatArray(16)

    fun ensureSize(w: Int, h: Int): Surface? {
        val cw = w.coerceAtLeast(1)
        val ch = h.coerceAtLeast(1)
        if (texId == 0) {
            val t = IntArray(1)
            GLES30.glGenTextures(1, t, 0)
            texId = t[0]
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val st = SurfaceTexture(texId)
            st.setDefaultBufferSize(cw, ch)
            surfaceTexture = st
            surface = Surface(st)
            width = cw
            height = ch
            return surface
        }
        if (cw != width || ch != height) {
            surfaceTexture?.setDefaultBufferSize(cw, ch)
            width = cw
            height = ch
        }
        return surface
    }

    fun updateTexImage(): Boolean {
        val st = surfaceTexture ?: return false
        return runCatching {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
            true
        }.getOrDefault(false)
    }

    fun release() {
        runCatching { surface?.release() }
        surface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        if (texId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
            texId = 0
        }
        width = 0
        height = 0
    }
}

/** clamps output dimensions so fallback shaders never waste fill-rate on huge displays */
internal fun clampedFallbackSize(widthPx: Float, heightPx: Float, scale: Float, maxEdge: Int): Pair<Int, Int> {
    if (widthPx <= 0f || heightPx <= 0f) return 0 to 0
    var w = (widthPx * scale).toInt().coerceAtLeast(1)
    var h = (heightPx * scale).toInt().coerceAtLeast(1)
    val longest = max(w, h)
    if (longest > maxEdge) {
        val fit = maxEdge.toFloat() / longest
        w = max(1, (w * fit).toInt())
        h = max(1, (h * fit).toInt())
    }
    return min(w, maxEdge) to min(h, maxEdge)
}
