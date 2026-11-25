// a module's own sheets over the player: in mochy's sheet, or drawn entirely the module's way, any player block inside (docs/wiki, module sheets)

package com.mochyy.mochymusic.ui.player.styles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.ActiveModuleSheets
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleStyle
import com.mochyy.mochymusic.modules.skin.SheetLook
import com.mochyy.mochymusic.modules.skin.SkinSheet
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import kotlinx.coroutines.launch

/** the sheet a module asked for, kept as its own object so a sheet opened twice is two different asks */
internal class ModuleSheetRequest(val moduleId: String, val sheetId: String)

/** which module sheet is up; every caller (a button, a box, a chevron item, a row in the other menu) only asks, the player shows it */
internal object ModuleSheets {
    var request: ModuleSheetRequest? by mutableStateOf(null)
        private set

    fun open(moduleId: String, sheetId: String) {
        request = ModuleSheetRequest(moduleId, sheetId)
    }

    fun close() {
        request = null
    }

    // only the ask this sheet came from, so a sheet still playing its exit can't close the one opened after it
    fun close(asked: ModuleSheetRequest) {
        if (request === asked) request = null
    }
}

/** the module sheet that's been asked for, over whatever style the player shows; one asked for while the player is folded down is dropped */
@Composable
internal fun ModuleSheetHost(actions: StyleActions, playerOpen: () -> Boolean) {
    // read here, not by the sheet around it, so the player opening and closing never recomposes the player for it
    LaunchedEffect(Unit) { snapshotFlow { playerOpen() }.collect { if (!it) ModuleSheets.close() } }
    val request = ModuleSheets.request ?: return
    val module = ActiveSkin.moduleSheets[request.moduleId]
    val sheet = module?.skin?.sheets?.firstOrNull { it.id == request.sheetId }
    if (module == null || sheet == null) {
        // the module went off, or never had a sheet by that name
        LaunchedEffect(request) { ModuleSheets.close(request) }
        return
    }
    key(request) {
        when (sheet.look) {
            SheetLook.Own -> OwnSheet(request, sheet, module, actions)
            SheetLook.Mochy -> MochySheet(request, sheet, module, actions)
        }
    }
}

// in mochy's own sheet, with its handle, its drag and the look module's sheet colours; the blocks sit where mochy's rows would
@Composable
private fun MochySheet(request: ModuleSheetRequest, sheet: SkinSheet, module: ActiveModuleSheets, actions: StyleActions) {
    val menuState = LocalMenuState.current
    val currentActions by rememberUpdatedState(actions)
    DisposableEffect(request) {
        lateinit var shown: PopupSheetHandle
        shown = menuState.showWithHandle(onClosed = { ModuleSheets.close(request) }) {
            MochySheetBlocks(sheet, module, currentActions, close = { shown.dismiss() })
        }
        onDispose { shown.dismiss() }
    }
}

@Composable
private fun ColumnScope.MochySheetBlocks(sheet: SkinSheet, module: ActiveModuleSheets, actions: StyleActions, close: () -> Unit) {
    val context = rememberSheetContext(sheet, module, actions, close)
    val layout = sheet.layout
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val fixed = sheet.height
    PlayerFont(module.skin.moduleId) {
        ScaledContent(context.scale) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fixed != null) Modifier.height(screen * fixed) else Modifier)
                    // a sheet that fits its blocks still scrolls once they're taller than the room mochy's sheet has
                    .then(if (fixed == null || layout.scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = layout.padding, vertical = 8.dp),
                horizontalAlignment = layout.align.horizontal,
            ) {
                ColumnBlocks(layout.blocks, context, grow = fixed != null && !layout.scroll)
            }
        }
    }
}

// entirely the module's: its background, corners, height and veil, rising over the player and dragged down to close
@Composable
private fun OwnSheet(request: ModuleSheetRequest, sheet: SkinSheet, module: ActiveModuleSheets, actions: StyleActions) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    // only read by the drag when it lets go, so it's kept out of state
    val panelHeight = remember { floatArrayOf(1f) }
    var closing by remember { mutableStateOf(false) }
    val dismiss = remember(request) {
        {
            if (!closing) {
                closing = true
                scope.launch {
                    shown.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
                    ModuleSheets.close(request)
                }
            }
        }
    }
    // let go past a quarter of its height, or flicked down, it closes; otherwise it springs back up
    val settle: (Float) -> Unit = remember(dismiss) {
        { velocity ->
            if (dragged > panelHeight[0] * 0.25f || velocity > FlingToClose) {
                dismiss()
            } else {
                scope.launch { animate(dragged, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)) { v, _ -> dragged = v } }
            }
        }
    }
    // a list or scroll box inside hands its overscroll at the top to the sheet, so dragging down from there pulls it closed
    val pull = remember(settle) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || dragged <= 0f) return Offset.Zero
                val used = maxOf(available.y, -dragged)
                dragged += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                dragged += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragged <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 300f)) }
    BackHandler(onBack = dismiss)

    val context = rememberSheetContext(sheet, module, actions, dismiss)
    val layout = sheet.layout
    val fixed = sheet.height
    val shape = RoundedCornerShape(topStart = sheet.corner, topEnd = sheet.corner)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // the veil over the player; a tap on it closes the sheet
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = sheet.dim))
                .pointerInput(dismiss) { detectTapGestures { dismiss() } },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fixed != null) Modifier.fillMaxHeight(fixed) else Modifier.heightIn(max = maxHeight * MaxOwnHeight))
                .onSizeChanged { panelHeight[0] = it.height.coerceAtLeast(1).toFloat() }
                .graphicsLayer { translationY = (1f - shown.value) * size.height + dragged }
                .clip(shape)
                .nestedScroll(pull)
                .pointerInput(settle) {
                    detectVerticalDragGestures(
                        onDragEnd = { settle(0f) },
                        onDragCancel = { settle(0f) },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragged = (dragged + amount).coerceAtLeast(0f)
                        },
                    )
                }
                // a tap inside stays inside, it never reaches the veil
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // the panel's own backdrop, sized to it rather than pushing it to full height
            Box(Modifier.matchParentSize()) { SkinBackdrop(context) }
            PlayerFont(module.skin.moduleId) {
                ScaledContent(context.scale) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fixed != null) Modifier.fillMaxHeight() else Modifier)
                            .then(if (fixed == null || layout.scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .navigationBarsPadding()
                            .padding(horizontal = layout.padding)
                            .padding(top = if (sheet.handle) 0.dp else layout.padding, bottom = 16.dp),
                        horizontalAlignment = layout.align.horizontal,
                    ) {
                        if (sheet.handle) Grabber(context)
                        ColumnBlocks(layout.blocks, context, grow = fixed != null && !layout.scroll)
                    }
                }
            }
        }
    }
}

// the short bar at the top of an own-look sheet, in the sheet's soft ink
@Composable
private fun ColumnScope.Grabber(context: SkinContext) {
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = 12.dp)
            .size(width = 36.dp, height = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(context.tones.inkSoft.copy(alpha = 0.5f)),
    )
}

/** everything a sheet's blocks read, the way a player's do, with close shutting the sheet instead of folding the player away */
@Composable
private fun rememberSheetContext(sheet: SkinSheet, module: ActiveModuleSheets, actions: StyleActions, close: () -> Unit): SkinContext {
    val style = remember(sheet, module) {
        ModuleStyle("${module.skin.moduleId}/sheet/${sheet.id}", module.skin, sheet.layout, sheet.layout.name, null, module.values)
    }
    val sheetActions = remember(actions, close) { actions.closingWith(close) }
    return rememberSkinContext(style, sheetActions, preview = false, carousel = null, values = module.values, openMenu = {}, openCustomise = {})
}

/** the same actions, with close shutting the sheet or page they're in instead of folding the player down */
internal fun StyleActions.closingWith(close: () -> Unit) = StyleActions(
    onOtherMenu = onOtherMenu,
    onEditPlayer = onEditPlayer,
    onLyrics = onLyrics,
    onExitVideoMode = onExitVideoMode,
    onOpenArtist = onOpenArtist,
    onOpenAlbum = onOpenAlbum,
    onOpenRoute = onOpenRoute,
    onClose = close,
    onQueue = onQueue,
    turn = turn,
    onOpenSheet = onOpenSheet,
    onFullVideo = onFullVideo,
    onEqualizer = onEqualizer,
)

// an own-look sheet that fits its blocks still leaves a strip of the player showing above it
private const val MaxOwnHeight = 0.92f
// px per second downward that closes a sheet even before it's dragged far
private const val FlingToClose = 1800f
