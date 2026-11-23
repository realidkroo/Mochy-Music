// generic interactive framebuffer block for signed code modules: 35 hz viewport inside a damage boundary, touch look, and a multi-touch gamepad

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.security.ModuleSanitizer
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinFiles
import com.mochyy.mochymusic.modules.skin.SurfaceControls
import com.mochyy.mochymusic.ui.component.damageBoundary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** draws a code module's live framebuffer inside a module player, paused automatically when the sheet closes or is covered */
@Composable
internal fun SkinSurface(block: SkinBlock.Surface, context: SkinContext, modifier: Modifier) {
    val accent = block.accent?.let(context::color) ?: DefaultSurfaceAccent
    if (context.preview) {
        SurfacePreview(block, accent, modifier)
        return
    }
    val androidContext = LocalContext.current
    val moduleRoot = remember(androidContext, context.skin.moduleId) {
        runCatching { SkinFiles.folderFor(androidContext, context.skin.moduleId).canonicalPath + File.separator }.getOrNull()
    }
    val assetFile = remember(context.skin, block.asset, moduleRoot) {
        block.asset?.let { path -> confinedFile(context.skin.fontFile(path), moduleRoot) }
    }
    val extraFiles = remember(context.skin, block.extras, moduleRoot) {
        block.extras.mapNotNull { path -> confinedFile(context.skin.fontFile(path), moduleRoot) }
    }
    val dataDir = remember(androidContext, context.skin.moduleId) {
        val safeId = ModuleSanitizer.safeSegment(context.skin.moduleId)
        ModuleSanitizer.safeChild(File(androidContext.filesDir, "module_data"), safeId, "surface")
    }
    if (block.asset != null && assetFile == null) {
        SurfaceStatusBox(block, stringResource(R.string.skin_surface_missing_asset), modifier)
        return
    }
    if (dataDir == null) {
        SurfaceStatusBox(block, stringResource(R.string.skin_surface_engine_missing), modifier)
        return
    }

    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context.skin.surface, assetFile, dataDir, extraFiles) {
        SurfaceSession.attach(context.skin.surface, assetFile, dataDir, extraFiles)
        onDispose { SurfaceSession.detach() }
    }

    LaunchedEffect(showing, obscured) {
        snapshotFlow { showing() && !obscured.value && resumed }.collect { active ->
            SurfaceSession.setPaused(!active)
        }
    }

    val scope = rememberCoroutineScope()
    var runLocked by remember { mutableStateOf(true) }
    var slot by remember { mutableIntStateOf(2) }
    val tapKey: (Int) -> Unit = remember(scope) {
        { code ->
            scope.launch {
                SurfaceSession.setKey(code, true)
                delay(TapHoldMs)
                SurfaceSession.setKey(code, false)
            }
        }
    }
    LaunchedEffect(runLocked, SurfaceSession.state) {
        if (SurfaceSession.state == SurfaceState.Running) {
            SurfaceSession.setKey(SurfaceKey.RUN, runLocked)
        }
    }

    val container = modifier.then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
    Column(
        modifier = container,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .surfaceViewportSize(block)
                .clip(RoundedCornerShape(block.corner))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(block.corner)),
        ) {
            SurfaceViewport(crisp = block.crisp, onTapScreen = { tapKey(SurfaceKey.ENTER) })
            SurfaceStateOverlay(SurfaceSession.state)
            if (block.controls == SurfaceControls.Overlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SurfaceGamepad(
                        accent = accent,
                        overlay = true,
                        runLocked = runLocked,
                        slot = slot,
                        onToggleRun = { runLocked = !runLocked },
                        onTapKey = tapKey,
                        onCycleSlot = {
                            slot = if (slot >= 7) 1 else slot + 1
                            tapKey('0'.code + slot)
                        },
                    )
                }
            }
        }
        if (block.controls == SurfaceControls.Below) {
            SurfaceGamepad(
                accent = accent,
                overlay = false,
                runLocked = runLocked,
                slot = slot,
                onToggleRun = { runLocked = !runLocked },
                onTapKey = tapKey,
                onCycleSlot = {
                    slot = if (slot >= 7) 1 else slot + 1
                    tapKey('0'.code + slot)
                },
            )
        }
    }
}

// ensures an asset file actually sits inside this module's unpacked folder and nowhere else
private fun confinedFile(candidate: File?, rootPrefix: String?): File? {
    if (candidate == null || rootPrefix == null || !candidate.isFile) return null
    val path = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
    return candidate.takeIf { path.startsWith(rootPrefix) }
}

private fun Modifier.surfaceViewportSize(block: SkinBlock.Surface): Modifier {
    val w = block.width
    val h = block.height
    return when {
        w != null && h != null -> size(w, h)
        w != null -> width(w).aspectRatio(block.aspect)
        h != null -> fillMaxWidth().height(h)
        else -> fillMaxWidth().aspectRatio(block.aspect)
    }
}

// draws the latest framebuffer inside its own damage boundary and turns horizontal drags into smooth look deltas
@Composable
private fun SurfaceViewport(crisp: Boolean, onTapScreen: () -> Unit) {
    val interactive = LocalStyleInteractive.current
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .damageBoundary()
            .pointerInput(interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var lastX = down.position.x
                        var totalMoved = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (totalMoved < TapSlopPx) onTapScreen()
                                break
                            }
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            totalMoved += abs(dx)
                            if (abs(dx) > 0.5f) {
                                SurfaceSession.addTurn((dx * TouchLookGain).roundToInt())
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        // read only inside draw so live frames never recompose the player
        SurfaceSession.frameTick
        val frame = SurfaceSession.currentFrame() ?: return@Canvas
        drawImage(
            image = frame,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = if (crisp) FilterQuality.None else FilterQuality.Low,
        )
    }
}

@Composable
private fun SurfaceStateOverlay(state: SurfaceState) {
    val message = when (state) {
        SurfaceState.Running -> return
        SurfaceState.Idle, SurfaceState.Loading -> stringResource(R.string.skin_surface_loading)
        SurfaceState.EngineMissing -> stringResource(R.string.skin_surface_engine_missing)
        SurfaceState.OtherAssetLoaded -> stringResource(R.string.skin_surface_other_loaded)
        is SurfaceState.Failed -> stringResource(R.string.skin_surface_error, state.message)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color(0xFFE6DFD5),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SurfaceStatusBox(block: SkinBlock.Surface, message: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .surfaceViewportSize(block)
            .clip(RoundedCornerShape(block.corner))
            .background(Color(0xFF141010))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(block.corner))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color(0xFFE6DFD5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    }
}

// lightweight static card for the style picker so browsing styles never boots native code
@Composable
private fun SurfacePreview(block: SkinBlock.Surface, accent: Color, modifier: Modifier) {
    val container = modifier.then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
    Column(
        modifier = container,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .surfaceViewportSize(block)
                .clip(RoundedCornerShape(block.corner))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color(0xFF0E0A0C), Color(0xFF181416))))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(block.corner)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = block.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.skin_surface_preview_title),
                    color = accent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = stringResource(R.string.skin_surface_preview_sub),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (block.controls == SurfaceControls.Below) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
                Box(Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.45f)))
            }
        }
    }
}

// ---- multi-touch gamepad ----------------------------------------------------------------------

@Composable
private fun SurfaceGamepad(
    accent: Color,
    overlay: Boolean,
    runLocked: Boolean,
    slot: Int,
    onToggleRun: () -> Unit,
    onTapKey: (Int) -> Unit,
    onCycleSlot: () -> Unit,
) {
    val surfaceAlpha = if (overlay) 0.45f else 0.14f
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (overlay) 6.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UtilityChip(stringResource(R.string.skin_surface_btn_menu), false, accent, surfaceAlpha) { onTapKey(SurfaceKey.ESCAPE) }
            UtilityChip(stringResource(R.string.skin_surface_btn_map), false, accent, surfaceAlpha) { onTapKey(SurfaceKey.TAB) }
            UtilityChip(stringResource(R.string.skin_surface_btn_run), runLocked, accent, surfaceAlpha, onClick = onToggleRun)
            UtilityChip(stringResource(R.string.skin_surface_btn_yes), false, accent, surfaceAlpha) { onTapKey(SurfaceKey.YES) }
            UtilityChip(stringResource(R.string.skin_surface_btn_slot, slot), false, accent, surfaceAlpha, onClick = onCycleSlot)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldKeyButton(stringResource(R.string.skin_surface_btn_strafe_l), SurfaceKey.STRAFE_L, 48.dp, 28.dp, Color.White.copy(alpha = surfaceAlpha), Color.White)
                    HoldKeyButton(stringResource(R.string.skin_surface_btn_strafe_r), SurfaceKey.STRAFE_R, 48.dp, 28.dp, Color.White.copy(alpha = surfaceAlpha), Color.White)
                }
                SurfaceThumbPad(size = if (overlay) 96.dp else 118.dp, accent = accent, surfaceAlpha = surfaceAlpha)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldKeyButton(stringResource(R.string.skin_surface_btn_use), SurfaceKey.USE, 54.dp, 38.dp, Color.White.copy(alpha = surfaceAlpha), Color.White)
                    HoldKeyButton(stringResource(R.string.skin_surface_btn_enter), SurfaceKey.ENTER, 48.dp, 38.dp, Color.White.copy(alpha = surfaceAlpha), Color.White)
                }
                HoldKeyButton(
                    label = stringResource(R.string.skin_surface_btn_action),
                    keyCode = SurfaceKey.ACTION,
                    width = if (overlay) 78.dp else 94.dp,
                    height = if (overlay) 52.dp else 64.dp,
                    fill = accent.copy(alpha = if (overlay) 0.72f else 0.90f),
                    ink = Color.White,
                    circle = true,
                )
            }
        }
    }
}

// 8-way thumb pad driving up/down/left/right simultaneously while consuming drags so the player sheet stays open
@Composable
private fun SurfaceThumbPad(size: Dp, accent: Color, surfaceAlpha: Float) {
    val interactive = LocalStyleInteractive.current
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = surfaceAlpha))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .pointerInput(interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        active = true
                        var up = false
                        var downKey = false
                        var left = false
                        var right = false
                        val centerX = this.size.width / 2f
                        val centerY = this.size.height / 2f
                        val maxRadius = (minOf(this.size.width, this.size.height) * 0.38f).coerceAtLeast(1f)
                        val deadZone = maxRadius * 0.24f

                        fun applyVector(pos: Offset) {
                            val rawX = pos.x - centerX
                            val rawY = pos.y - centerY
                            val dist = hypot(rawX, rawY)
                            val clamped = if (dist > maxRadius) maxRadius / dist else 1f
                            knobX = rawX * clamped
                            knobY = rawY * clamped
                            val nextUp = rawY < -deadZone
                            val nextDown = rawY > deadZone
                            val nextLeft = rawX < -deadZone
                            val nextRight = rawX > deadZone
                            if (nextUp != up) { up = nextUp; SurfaceSession.setKey(SurfaceKey.UP, up) }
                            if (nextDown != downKey) { downKey = nextDown; SurfaceSession.setKey(SurfaceKey.DOWN, downKey) }
                            if (nextLeft != left) { left = nextLeft; SurfaceSession.setKey(SurfaceKey.LEFT, left) }
                            if (nextRight != right) { right = nextRight; SurfaceSession.setKey(SurfaceKey.RIGHT, right) }
                        }

                        applyVector(down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                applyVector(change.position)
                            }
                        } finally {
                            if (up) SurfaceSession.setKey(SurfaceKey.UP, false)
                            if (downKey) SurfaceSession.setKey(SurfaceKey.DOWN, false)
                            if (left) SurfaceSession.setKey(SurfaceKey.LEFT, false)
                            if (right) SurfaceSession.setKey(SurfaceKey.RIGHT, false)
                            knobX = 0f
                            knobY = 0f
                            active = false
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val cross = this.size.minDimension * 0.28f
            val guide = Color.White.copy(alpha = 0.22f)
            drawLine(guide, Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 2.dp.toPx())
            drawLine(guide, Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 2.dp.toPx())
            drawCircle(
                color = if (active) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.34f),
                radius = this.size.minDimension * 0.2f,
                center = Offset(cx + knobX, cy + knobY),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = this.size.minDimension * 0.2f,
                center = Offset(cx + knobX, cy + knobY),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

// hold-to-press button that consumes pointers immediately so action and use work alongside the thumb pad
@Composable
private fun HoldKeyButton(
    label: String,
    keyCode: Int,
    width: Dp,
    height: Dp,
    fill: Color,
    ink: Color,
    circle: Boolean = false,
) {
    val interactive = LocalStyleInteractive.current
    var pressed by remember { mutableStateOf(false) }
    val shape = if (circle) RoundedCornerShape(percent = 50) else RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(if (pressed) fill.copy(alpha = (fill.alpha + 0.25f).coerceAtMost(1f)) else fill)
            .border(1.dp, Color.White.copy(alpha = if (pressed) 0.55f else 0.22f), shape)
            .pointerInput(keyCode, interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        SurfaceSession.setKey(keyCode, true)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            pressed = false
                            SurfaceSession.setKey(keyCode, false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = if (circle) 14.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun UtilityChip(label: String, active: Boolean, accent: Color, surfaceAlpha: Float, onClick: () -> Unit) {
    val interactive = LocalStyleInteractive.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.78f) else Color.White.copy(alpha = surfaceAlpha))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.45f else 0.18f), shape)
            .pointerInput(interactive, onClick) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                onClick()
                                break
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

private val DefaultSurfaceAccent = Color(0xFFD92B2B)
private const val TouchLookGain = 3.2f
private const val TapSlopPx = 12f
private const val TapHoldMs = 60L
