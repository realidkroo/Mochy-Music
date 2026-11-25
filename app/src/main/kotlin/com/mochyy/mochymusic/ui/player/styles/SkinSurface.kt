// generic interactive framebuffer block for signed code modules: 35 hz viewport inside a damage boundary, touch look, a multi-touch gamepad that moves beside the screen when the player is turned, and a fullscreen mode covering the whole player

package com.mochyy.mochymusic.ui.player.styles

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.mochyy.mochymusic.modules.skin.SurfaceFit
import com.mochyy.mochymusic.modules.skin.SurfaceFullscreen
import com.mochyy.mochymusic.ui.component.damageBoundary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** draws a code module's live framebuffer inside a module player, paused automatically when the sheet closes or is covered; [hosted] is the one covering the whole player while it's fullscreen */
@Composable
internal fun SkinSurface(raw: SkinBlock.Surface, context: SkinContext, modifier: Modifier, hosted: Boolean = false) {
    // the options a module hands to its settings, read again as they change
    val block = remember(raw, context.values) { raw.with(context.values) }
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
    val playerStage = LocalSurfaceStage.current
    val stage = playerStage ?: remember { SurfaceStage() }
    val tapKey: (Int) -> Unit = remember(scope) {
        { code ->
            scope.launch {
                SurfaceSession.setKey(code, true)
                delay(TapHoldMs)
                SurfaceSession.setKey(code, false)
            }
        }
    }
    LaunchedEffect(stage.runLocked, SurfaceSession.state) {
        if (SurfaceSession.state == SurfaceState.Running) {
            SurfaceSession.setKey(SurfaceKey.RUN, stage.runLocked)
        }
    }
    // the in-player one is the one the menu's fullscreen item takes over; the fullscreen copy never registers
    if (!hosted && playerStage != null) {
        DisposableEffect(raw, playerStage) {
            playerStage.shown = raw
            onDispose { if (playerStage.shown === raw) playerStage.shown = null }
        }
    }
    // a module that starts fullscreen does so the first time its player shows it, and stays where it's left after
    LaunchedEffect(block.fullscreen, playerStage) {
        if (playerStage != null && block.fullscreen == SurfaceFullscreen.Start && !playerStage.started) {
            playerStage.started = true
            playerStage.full = raw
        }
    }

    val pad = PadInput(
        runLocked = stage.runLocked,
        slot = stage.slot,
        full = if (playerStage != null && block.fullscreen != SurfaceFullscreen.Off) hosted else null,
        onToggleRun = { stage.runLocked = !stage.runLocked },
        onTapKey = tapKey,
        onCycleSlot = {
            stage.slot = if (stage.slot >= 7) 1 else stage.slot + 1
            tapKey('0'.code + stage.slot)
        },
        onToggleFull = { stage.full = if (hosted) null else raw },
    )
    val tones = remember(accent) { PadTones(accent, overlay = false) }
    val frameAspect = SurfaceSession.frameAspect

    // turned sideways the screen fills the height that's left and the pad splits either side of it, like a handheld
    if (context.landscape) {
        SurfaceSideways(block, frameAspect, tones, pad, modifier, hosted)
    } else {
        SurfaceUpright(block, frameAspect, tones, pad, modifier, hosted)
    }
}

/** the player's one surface shared between its place in the layout and the fullscreen copy: which is fullscreen, and the pad's latches so they survive the swap */
@Stable
internal class SurfaceStage {
    /** the surface covering the whole player, or null while it sits in the layout */
    var full: SkinBlock.Surface? by mutableStateOf(null)
    /** the surface in the layout right now, the one going fullscreen takes over */
    var shown: SkinBlock.Surface? by mutableStateOf(null)
    var runLocked: Boolean by mutableStateOf(true)
    var slot: Int by mutableIntStateOf(2)
    // a "start" surface has gone fullscreen once already, so leaving it sticks
    var started = false

    /** in the layout to fullscreen and back, for the chevron menu's item */
    fun toggle() {
        full = if (full != null) null else shown
    }
}

internal val LocalSurfaceStage = staticCompositionLocalOf<SurfaceStage?> { null }

/** the chevron menu's fullscreen item, there while the layout shows a surface that may go fullscreen */
@Composable
internal fun rememberSurfaceMenuItem(stage: SurfaceStage, context: SkinContext): StyleMenuExtra? {
    if (context.preview) return null
    val target = stage.full ?: stage.shown ?: return null
    if (target.with(context.values).fullscreen == SurfaceFullscreen.Off) return null
    val on = stage.full != null
    return StyleMenuExtra(
        label = stringResource(if (on) R.string.skin_surface_fullscreen_exit else R.string.skin_surface_fullscreen),
        icon = if (on) R.drawable.fullscreen_exit else R.drawable.fullscreen,
        active = on,
    ) { stage.toggle() }
}

/** a fullscreen surface over the whole player, the module's layout and header gone, the chevron still over it; back brings the layout back */
@Composable
internal fun SurfaceFullscreenHost(block: SkinBlock.Surface, context: SkinContext, insets: StyleInsets) {
    val stage = LocalSurfaceStage.current ?: return
    val showing = rememberStyleShowing()
    // only the flip recomposes here, not every frame of the sheet's drag
    val open by remember(showing) { derivedStateOf { showing() } }
    BackHandler(enabled = open) { stage.full = null }
    DisposableEffect(Unit) {
        SurfaceSession.fullscreen = true
        onDispose { SurfaceSession.fullscreen = false }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                start = context.fixed(insets.start) + FullEdge,
                end = context.fixed(insets.end) + FullEdge,
                // upright the chevron needs its row; sideways it sits clear above the pad
                top = context.fixed(insets.top) + if (context.landscape) FullEdge else FullTopRoom,
                bottom = context.fixed(insets.bottom) + FullEdge,
            ),
    ) {
        SkinSurface(block, context, Modifier.fillMaxSize(), hosted = true)
    }
}

// upright: the screen over the pad, at its shape or, filling, as tall as the pad leaves it
@Composable
private fun SurfaceUpright(block: SkinBlock.Surface, frameAspect: Float, tones: PadTones, pad: PadInput, modifier: Modifier, hosted: Boolean) {
    val ratio = block.aspect.ratio ?: frameAspect
    val container = modifier.then(if (hosted) Modifier else block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
    BoxWithConstraints(container) {
        // a scrolling player has no bottom to fill to, so there the screen keeps a shape
        val bounded = constraints.hasBoundedHeight
        val fills = block.aspect.fill && bounded
        Column(
            modifier = if ((fills || hosted) && bounded) Modifier.fillMaxWidth().height(maxHeight) else Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            val screen = when {
                fills -> Modifier.fillMaxWidth().weight(1f)
                hosted -> Modifier.fillMaxWidth().aspectRatio(ratio)
                else -> Modifier.surfaceViewportSize(block, ratio)
            }
            SurfaceScreen(block, screen, tones, pad)
            if (block.controls == SurfaceControls.Below) SurfaceGamepad(tones, pad, compact = false)
        }
    }
}

// ensures an asset file actually sits inside this module's unpacked folder and nowhere else
private fun confinedFile(candidate: File?, rootPrefix: String?): File? {
    if (candidate == null || rootPrefix == null || !candidate.isFile) return null
    val path = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
    return candidate.takeIf { path.startsWith(rootPrefix) }
}

private fun Modifier.surfaceViewportSize(block: SkinBlock.Surface, ratio: Float): Modifier {
    val w = block.width
    val h = block.height
    return when {
        w != null && h != null -> size(w, h)
        w != null -> width(w).aspectRatio(ratio)
        h != null -> fillMaxWidth().height(h)
        else -> fillMaxWidth().aspectRatio(ratio)
    }
}

// the frame's size and place in a screen of [box] for the block's fit, centred
private fun fitFrame(fit: SurfaceFit, frameW: Int, frameH: Int, box: androidx.compose.ui.geometry.Size): Pair<IntOffset, IntSize> {
    val fw = frameW.toFloat()
    val fh = frameH.toFloat()
    val contain = min(box.width / fw, box.height / fh)
    val (w, h) = when (fit) {
        SurfaceFit.Stretch -> box.width to box.height
        SurfaceFit.Contain -> fw * contain to fh * contain
        SurfaceFit.Cover -> max(box.width / fw, box.height / fh).let { fw * it to fh * it }
        // whole multiples only, so every source pixel is the same size; a screen smaller than the frame shrinks it whole instead
        SurfaceFit.Pixel -> (if (contain >= 1f) floor(contain) else contain).let { fw * it to fh * it }
    }
    val offset = IntOffset(((box.width - w) / 2f).roundToInt(), ((box.height - h) / 2f).roundToInt())
    return offset to IntSize(w.roundToInt(), h.roundToInt())
}

// the framebuffer in its bezel, with the state over it and, for an overlay pad, the controls on top
@Composable
private fun SurfaceScreen(block: SkinBlock.Surface, size: Modifier, tones: PadTones, pad: PadInput) {
    val shape = RoundedCornerShape(block.corner)
    Box(
        modifier = size
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Bezel, shape),
    ) {
        SurfaceViewport(crisp = block.crisp, fit = block.fitting, onTapScreen = { pad.onTapKey(SurfaceKey.ENTER) })
        SurfaceStateOverlay(SurfaceSession.state)
        if (block.controls == SurfaceControls.Overlay) {
            val overlayTones = remember(tones.accent) { PadTones(tones.accent, overlay = true) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SurfaceGamepad(overlayTones, pad, compact = true)
            }
        }
    }
}

// sideways: the screen as big as the height left allows, the movement half of the pad on its left and the buttons on its right
@Composable
private fun SurfaceSideways(block: SkinBlock.Surface, frameAspect: Float, tones: PadTones, pad: PadInput, modifier: Modifier, hosted: Boolean) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // a scrolling player has no bottom, so the screen takes most of the phone's height instead
        val bounded = constraints.hasBoundedHeight
        val height = if (bounded) maxHeight else screenHeight * 0.72f
        val sides = block.controls == SurfaceControls.Below
        val wings = if (sides) (SideCluster + SideGap) * 2 else 0.dp
        val room = (maxWidth - wings).coerceAtLeast(120.dp)
        val ratio = block.aspect.ratio ?: frameAspect
        val screenWidth = if (block.aspect.fill) room else minOf(room, height * ratio)
        val screenTall = if (block.aspect.fill) height else screenWidth / ratio
        val chips = pad.chips()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hosted && bounded) Modifier.height(maxHeight) else Modifier),
            horizontalArrangement = if (sides) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sides) {
                Column(
                    modifier = Modifier.width(SideCluster),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // menu, map and fullscreen over the stick, the latches over the buttons
                    PadBar(tones, chips.take(2) + listOfNotNull(pad.fullChip()))
                    MoveCluster(tones, compact = false)
                }
            }
            SurfaceScreen(block, Modifier.size(screenWidth, screenTall), tones, pad)
            if (sides) {
                Column(
                    modifier = Modifier.width(SideCluster),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PadBar(tones, chips.drop(2))
                    FaceCluster(tones, compact = false)
                }
            }
        }
    }
}

// draws the latest framebuffer inside its own damage boundary and turns horizontal drags into smooth look deltas
@Composable
private fun SurfaceViewport(crisp: Boolean, fit: SurfaceFit, onTapScreen: () -> Unit) {
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
        val (at, drawn) = fitFrame(fit, frame.width, frame.height, size)
        drawImage(
            image = frame,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = at,
            dstSize = drawn,
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
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = StatusText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SurfaceStatusBox(block: SkinBlock.Surface, message: String, modifier: Modifier) {
    val shape = RoundedCornerShape(block.corner)
    Box(
        modifier = modifier
            .surfaceViewportSize(block, block.aspect.ratio ?: DefaultFrameAspect)
            .clip(shape)
            .background(Color(0xFF111113))
            .border(1.dp, Bezel, shape)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = StatusText, textAlign = TextAlign.Center)
    }
}

// lightweight static card for the style picker so browsing styles never boots native code
@Composable
private fun SurfacePreview(block: SkinBlock.Surface, accent: Color, modifier: Modifier) {
    val container = modifier.then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
    val shape = RoundedCornerShape(block.corner)
    val tones = remember(accent) { PadTones(accent, overlay = false) }
    Column(
        modifier = container,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .surfaceViewportSize(block, block.aspect.ratio ?: DefaultFrameAspect)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF161618), Color(0xFF0B0B0C))))
                .border(1.dp, Bezel, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = block.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.skin_surface_preview_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Box(Modifier.size(width = 28.dp, height = 2.dp).background(accent, CircleShape))
                Text(
                    text = stringResource(R.string.skin_surface_preview_sub),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                )
            }
        }
        if (block.controls == SurfaceControls.Below) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(tones.well))
                Box(Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.85f)))
            }
        }
    }
}

// ---- multi-touch gamepad ----------------------------------------------------------------------

/** what the pad's latching chips read and what its taps do, handed down as one; [full] is whether it's fullscreen, null where it can't be */
private class PadInput(
    val runLocked: Boolean,
    val slot: Int,
    val full: Boolean?,
    val onToggleRun: () -> Unit,
    val onTapKey: (Int) -> Unit,
    val onCycleSlot: () -> Unit,
    val onToggleFull: () -> Unit,
) {
    @Composable
    fun fullChip(): PadChip? {
        val on = full ?: return null
        val label = stringResource(if (on) R.string.skin_surface_fullscreen_exit else R.string.skin_surface_fullscreen)
        return PadChip(label, false, icon = if (on) R.drawable.fullscreen_exit else R.drawable.fullscreen, onClick = onToggleFull)
    }

    // menu and map first: sideways they sit over the stick, the rest over the buttons
    @Composable
    fun chips(): List<PadChip> = listOf(
        PadChip(stringResource(R.string.skin_surface_btn_menu), false) { onTapKey(SurfaceKey.ESCAPE) },
        PadChip(stringResource(R.string.skin_surface_btn_map), false) { onTapKey(SurfaceKey.TAB) },
        PadChip(stringResource(R.string.skin_surface_btn_run), runLocked, onClick = onToggleRun),
        PadChip(stringResource(R.string.skin_surface_btn_yes), false) { onTapKey(SurfaceKey.YES) },
        PadChip(stringResource(R.string.skin_surface_btn_slot, slot), false, onClick = onCycleSlot),
    )
}

private class PadChip(val label: String, val active: Boolean, @DrawableRes val icon: Int? = null, val onClick: () -> Unit)

/** one set of tones for every control so the pad reads as a single piece: lighter keys on a dark well under the screen, darker glass over it */
@Immutable
private class PadTones(val accent: Color, overlay: Boolean) {
    val well = if (overlay) Color.Black.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.05f)
    val key = if (overlay) Color.Black.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.085f)
    val keyDown = if (overlay) Color.Black.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.17f)
    val hairline = Color.White.copy(alpha = if (overlay) 0.16f else 0.07f)
    val ink = Color.White.copy(alpha = 0.9f)
    val inkSoft = Color.White.copy(alpha = 0.45f)
    val accentDown = lerp(accent, Color.White, 0.18f)
    val accentSoft = accent.copy(alpha = 0.22f)
}

@Composable
private fun SurfaceGamepad(tones: PadTones, pad: PadInput, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 18.dp),
    ) {
        PadBar(tones, pad.chips() + listOfNotNull(pad.fullChip()))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            MoveCluster(tones, compact)
            FaceCluster(tones, compact)
        }
    }
}

// the latching and one-shot keys as one segmented strip, so they read as a toolbar instead of loose stickers
@Composable
private fun PadBar(tones: PadTones, chips: List<PadChip>) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(tones.well)
            .border(1.dp, tones.hairline, shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEachIndexed { index, chip ->
            if (index > 0 && !chip.active && !chips[index - 1].active) {
                Box(Modifier.width(1.dp).height(12.dp).background(tones.hairline))
            }
            PadBarItem(tones, chip)
        }
    }
}

@Composable
private fun PadBarItem(tones: PadTones, chip: PadChip) {
    val interactive = LocalStyleInteractive.current
    val haptics = LocalHapticFeedback.current
    val onClick by rememberUpdatedState(chip.onClick)
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                when {
                    chip.active -> tones.accentSoft
                    pressed -> tones.keyDown
                    else -> Color.Transparent
                },
            )
            .pointerInput(interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClick()
                                    break
                                }
                            }
                        } finally {
                            pressed = false
                        }
                    }
                }
            }
            .padding(horizontal = if (chip.icon != null) 11.dp else 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (chip.active) lerp(tones.accent, Color.White, 0.35f) else tones.ink.copy(alpha = 0.72f)
        if (chip.icon != null) {
            Icon(painterResource(chip.icon), contentDescription = chip.label, tint = ink, modifier = Modifier.size(16.dp))
        } else {
            Text(text = chip.label, style = PadLabel, color = ink, maxLines = 1)
        }
    }
}

// the two strafe shoulders over the stick
@Composable
private fun MoveCluster(tones: PadTones, compact: Boolean) {
    val shoulder = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey(stringResource(R.string.skin_surface_btn_strafe_l), SurfaceKey.STRAFE_L, 50.dp, 30.dp, shoulder, tones)
            PadKey(stringResource(R.string.skin_surface_btn_strafe_r), SurfaceKey.STRAFE_R, 50.dp, 30.dp, shoulder, tones)
        }
        SurfaceThumbPad(size = if (compact) 96.dp else 116.dp, tones = tones)
    }
}

// the face buttons in a console's arc: the main action biggest and lowest, the two others stepping up and in from it
@Composable
private fun FaceCluster(tones: PadTones, compact: Boolean) {
    val k = if (compact) 0.82f else 1f
    val big = 72.dp * k
    val small = 46.dp * k
    Box(Modifier.size(width = 142.dp * k, height = 128.dp * k)) {
        PadKey(
            label = stringResource(R.string.skin_surface_btn_enter),
            keyCode = SurfaceKey.ENTER,
            width = small, height = small, shape = CircleShape, tones = tones,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 13.dp * k),
        )
        PadKey(
            label = stringResource(R.string.skin_surface_btn_use),
            keyCode = SurfaceKey.USE,
            width = small, height = small, shape = CircleShape, tones = tones,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 13.dp * k),
        )
        PadKey(
            label = stringResource(R.string.skin_surface_btn_action),
            keyCode = SurfaceKey.ACTION,
            width = big, height = big, shape = CircleShape, tones = tones,
            primary = true,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

// 8-way thumb pad driving up/down/left/right simultaneously while consuming drags so the player sheet stays open
@Composable
private fun SurfaceThumbPad(size: Dp, tones: PadTones) {
    val interactive = LocalStyleInteractive.current
    val haptics = LocalHapticFeedback.current
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    // which ways are held, so their ticks light up
    var held by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        active = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        var up = false
                        var downKey = false
                        var left = false
                        var right = false
                        val centerX = this.size.width / 2f
                        val centerY = this.size.height / 2f
                        val maxRadius = (minOf(this.size.width, this.size.height) * 0.30f).coerceAtLeast(1f)
                        val deadZone = maxRadius * 0.3f

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
                            held = (if (up) HeldUp else 0) or (if (right) HeldRight else 0) or (if (downKey) HeldDown else 0) or (if (left) HeldLeft else 0)
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
                            held = 0
                            active = false
                        }
                    }
                }
            },
    ) {
        val tick = remember { Path() }
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            // a recessed well: darker in the middle, a soft lip at the rim
            drawCircle(tones.well, r, c)
            drawCircle(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = c, radius = r),
                r, c,
            )
            drawCircle(tones.hairline, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            // a small tick each way, lit while it's held
            for (i in 0 until 4) {
                val lit = held and (1 shl i) != 0
                rotate(90f * i, c) { drawTick(tick, c, r, if (lit) tones.accent else tones.inkSoft) }
            }
            val knob = r * 0.46f
            val at = Offset(c.x + knobX, c.y + knobY)
            drawCircle(Color.Black.copy(alpha = 0.35f), knob + 2.dp.toPx(), at + Offset(0f, 2.dp.toPx()))
            drawCircle(
                Brush.verticalGradient(
                    listOf(Color(0xFF3B3B40), Color(0xFF242428)),
                    startY = at.y - knob, endY = at.y + knob,
                ),
                knob, at,
            )
            drawCircle(
                if (active) tones.accent else Color.White.copy(alpha = 0.12f),
                knob - 0.75.dp.toPx(), at,
                style = Stroke(1.5.dp.toPx()),
            )
            // a shallow dish in the cap for the thumb
            drawCircle(Color.Black.copy(alpha = 0.18f), knob * 0.52f, at)
        }
    }
}

// one up-pointing chevron tick near the top of the well
private fun DrawScope.drawTick(path: Path, c: Offset, r: Float, color: Color) {
    val w = r * 0.13f
    val tip = c.y - r * 0.84f
    path.reset()
    path.moveTo(c.x - w, tip + w * 0.9f)
    path.lineTo(c.x, tip)
    path.lineTo(c.x + w, tip + w * 0.9f)
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

// hold-to-press key that consumes pointers immediately so action and use work alongside the thumb pad
@Composable
private fun PadKey(
    label: String,
    keyCode: Int,
    width: Dp,
    height: Dp,
    shape: Shape,
    tones: PadTones,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val interactive = LocalStyleInteractive.current
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val fill = when {
        primary && pressed -> Brush.verticalGradient(listOf(tones.accentDown, tones.accent))
        primary -> Brush.verticalGradient(listOf(lerp(tones.accent, Color.White, 0.08f), lerp(tones.accent, Color.Black, 0.14f)))
        pressed -> Brush.verticalGradient(listOf(tones.keyDown, tones.keyDown))
        else -> Brush.verticalGradient(listOf(tones.key.copy(alpha = tones.key.alpha * 1.25f), tones.key))
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = if (pressed) 0.94f else 1f
                scaleX = s
                scaleY = s
            }
            .size(width, height)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (primary) Color.White.copy(alpha = 0.18f) else tones.hairline, shape)
            .pointerInput(keyCode, interactive) {
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            style = if (primary) PadLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold) else PadLabel,
            color = if (primary) Color.White else tones.ink.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

private val DefaultSurfaceAccent = Color(0xFFD92B2B)
private const val DefaultFrameAspect = 320f / 200f
/** the fullscreen surface's margin from the screen's edges, and upright the room left for the chevron's row */
private val FullEdge = 12.dp
private val FullTopRoom = 68.dp
private val Bezel = Color.White.copy(alpha = 0.08f)
private val PadLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp)
private val StatusText = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
/** how wide each half of the pad is beside a sideways screen, and its gap to the screen */
private val SideCluster = 164.dp
private val SideGap = 12.dp
private const val HeldUp = 1
private const val HeldRight = 2
private const val HeldDown = 4
private const val HeldLeft = 8
private const val TouchLookGain = 3.2f
private const val TapSlopPx = 12f
private const val TapHoldMs = 60L
