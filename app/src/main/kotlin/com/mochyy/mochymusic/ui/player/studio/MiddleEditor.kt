package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.PressScaleActionButton
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** the player's menu item label, exactly as its chevron menu sets it */
private val MenuLabelStyle = TextStyle(
    fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), offset = Offset(0f, 2f), blurRadius = 8f),
)

/**
 * Where the middle part's camera goes and where every piece ends up, laid out for this screen the
 * way the concept has it: the camera closes in on the right-hand end of the middle part (the
 * buttons, the seek bar's end, the end time) and sets it left of centre, each piece in a tile of
 * its own with what it edits beside it. The menu then stacks its items over the chevron at the
 * bottom right, and the side button moves into a tile of its own beside it.
 */
private class MiddleStage(
    w: Float,
    h: Float,
    density: Density,
    statusTop: Float,
    navBottom: Float,
    val side: Rect,
    val chevron: Rect,
    val seek: Rect,
    val end: Rect,
    slots: Int,
    labelWidths: FloatArray,
    sideLabelWidth: Float,
) {
    /** the camera's zoom at the end of the move */
    val zoom: Float
    /** the point on the player the camera closes in on, and where it ends up */
    val anchor: Offset
    val target: Offset
    val camera: StudioCamera

    val sideTo: Offset
    val chevronTo: Offset
    val seekTo: Offset
    val endTo: Offset
    val menusTile: Rect
    val seekTile: Rect
    val endTile: Rect
    val labelX: Float

    val menuZoom: Float
    val labelScale: Float
    val chevronMenu: Offset
    val sideMenu: Offset
    val rowCircles: List<Offset>
    val rowTiles: List<Rect>
    val sideTile: Rect
    val labelGap: Float

    init {
        with(density) {
            val button = chevron.height.takeIf { it > 0f } ?: 34.dp.toPx()
            zoom = (0.165f * w / button).coerceIn(1.6f, 2.4f)
            val c = zoom * button
            val pad = 0.42f * c
            val right = 0.5f * w
            val tileRight = right + pad
            labelX = tileRight + 0.07f * w

            val menusH = c + pad * 2f
            val seekH = zoom * 8.dp.toPx() + pad * 2f
            val endH = zoom * end.height.coerceAtLeast(1f) + pad * 2f
            var menusY = 0.592f * h
            var seekY = menusY + menusH / 2f + 0.037f * h + seekH / 2f
            var endY = seekY + seekH / 2f + 0.02f * h + endH / 2f
            val overflow = (endY + endH / 2f) - (h - navBottom - 24.dp.toPx())
            if (overflow > 0f) {
                val room = (menusY - menusH / 2f) - (statusTop + 100.dp.toPx())
                val up = overflow.coerceAtMost(room.coerceAtLeast(0f))
                menusY -= up
                seekY -= up
                endY -= up
            }

            anchor = Offset(seek.right, seek.center.y)
            target = Offset(right, seekY)

            chevronTo = Offset(right - c / 2f, menusY)
            sideTo = Offset(chevronTo.x - (chevron.center.x - side.center.x) * zoom, menusY)
            seekTo = Offset(right - seek.width * zoom / 2f, seekY)
            endTo = Offset(right - end.width * zoom / 2f, endY)
            menusTile = Rect(sideTo.x - c / 2f - 0.32f * c, menusY - menusH / 2f, tileRight, menusY + menusH / 2f)
            seekTile = Rect(-0.2f * w, seekY - seekH / 2f, tileRight, seekY + seekH / 2f)
            endTile = Rect(endTo.x - end.width * zoom / 2f - 0.5f * c, endY - endH / 2f, tileRight, endY + endH / 2f)

            // the menu: its items and the side button, one row each, stacked over the chevron
            var mz = zoom
            var cm = mz * button
            var rowH = cm * 1.84f
            val bottomEdge = h - navBottom - 0.045f * h
            val topLimit = statusTop + 80.dp.toPx()
            val needed = (slots + 1) * rowH
            if (needed > bottomEdge - topLimit) {
                mz *= (bottomEdge - topLimit) / needed
                cm = mz * button
                rowH = cm * 1.84f
            }
            menuZoom = mz
            val bottomCy = bottomEdge - rowH / 2f
            chevronMenu = Offset(0.631f * w, bottomCy)
            sideMenu = Offset(0.367f * w, bottomCy)
            labelGap = 0.3f * cm
            val edge = 12.dp.toPx()
            // labels keep the circles' zoom unless a long one wouldn't fit, then all come down together
            val circleLeft = chevronMenu.x - cm / 2f
            val sideLeft = sideMenu.x - cm / 2f
            var ls = mz
            labelWidths.forEach { lw -> if (lw > 0f) ls = minOf(ls, (circleLeft - labelGap - 0.64f * cm - edge) / lw) }
            if (sideLabelWidth > 0f) ls = minOf(ls, (sideLeft - labelGap - 0.4f * cm - edge) / sideLabelWidth)
            labelScale = ls.coerceAtLeast(0.6f)
            val circles = ArrayList<Offset>()
            val tiles = ArrayList<Rect>()
            val widest = (labelWidths.maxOrNull() ?: 0f) * labelScale
            val rowLeft = (circleLeft - labelGap - widest - 0.64f * cm).coerceAtLeast(edge)
            for (i in 0 until slots) {
                val cy = bottomCy - (slots - i) * rowH
                circles += Offset(chevronMenu.x, cy)
                tiles += Rect(rowLeft, cy - rowH / 2f, chevronMenu.x + cm / 2f + 0.3f * cm, cy + rowH / 2f)
            }
            rowCircles = circles
            rowTiles = tiles
            sideTile = Rect(
                (sideLeft - labelGap - sideLabelWidth * labelScale - 0.4f * cm).coerceAtLeast(edge),
                bottomCy - rowH / 2f,
                sideMenu.x + cm / 2f + 0.34f * cm,
                bottomCy + rowH / 2f,
            )
        }
        camera = StudioCamera(zoom, anchor, target)
    }
}

/**
 * The middle part and the menu, edited. The player is a still now (taken as it opened, its middle
 * pieces lifted out) and the camera closes in on it - the cover, the background and all - softening
 * as it lands. The pieces are the player's own: the side button, the chevron, the seek bar and the
 * end time ride the camera in and flow on to their tiles. The menu grows out of the chevron the
 * way the player's own menu does, at the camera's size.
 */
@Composable
internal fun MiddleEditor(
    parts: StudioParts,
    capture: StudioCapture,
    menuLayout: PlayerMenuLayout,
    isActive: (PlayerMenuAction) -> Boolean,
    w: Float,
    h: Float,
) {
    val studio = parts.studio
    val pieces = parts.pieces ?: return
    val connection = LocalPlayerConnection.current ?: return
    val transport by connection.uiState.transportState.collectAsState()
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding().toPx() }
    val navBottom = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val anchors = parts.anchors
    val measurer = rememberTextMeasurer()
    val slots = menuLayout.slots
    val sideAction = menuLayout.side
    val labelWidths = remember(slots) {
        FloatArray(slots.size) { i -> measurer.measure(slots[i].label, MenuLabelStyle).size.width.toFloat() }
    }
    val sideLabelWidth = remember(sideAction) { measurer.measure(sideAction.label, MenuLabelStyle).size.width.toFloat() }
    val sideRect = anchors.sideButton.value
    val chevronRect = anchors.chevron.value
    val seekRect = anchors.seekBar.value
    val endRect = anchors.endTime.value
    val stage = remember(w, h, statusTop, navBottom, sideRect, chevronRect, seekRect, endRect, labelWidths, sideLabelWidth) {
        MiddleStage(w, h, density, statusTop, navBottom, sideRect, chevronRect, seekRect, endRect, slots.size, labelWidths, sideLabelWidth)
    }
    val stageMiddle = studio.stage == StudioStage.MIDDLE && studio.sheet == null
    val stageMenu = studio.stage == StudioStage.MENU && studio.sheet == null

    // the camera, then the pieces flowing on to their rows a beat behind it, then their tiles
    fun cz() = smoother(studio.middle.value)
    fun spread() = smoother((studio.middle.value - 0.1f) / 0.9f)
    fun tiles() = smoother((studio.middle.value - 0.45f) / 0.55f)
    fun menu() = smoother(studio.menu.value)
    // the menu's items rise out of the chevron the way the player's menu does: the lowest first
    fun item(i: Int) = smoother((studio.menu.value - 0.12f - 0.08f * (slots.lastIndex - i)) / 0.56f)

    fun chevronCenter(): Offset {
        val mid = stage.camera.pieceCenter(chevronRect, stage.chevronTo, cz(), spread())
        return lerpO(mid, stage.chevronMenu, menu())
    }
    fun pieceZoom(menuWeight: Float) = lerpF(stage.camera.zoomAt(cz()), stage.menuZoom, menuWeight)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val z = cz()
            val u = menu()
            // the player, as a still, under the camera; it softens as the camera lands
            drawStill(capture.image, stage.camera, z, 1f)
            val soft = smoother((studio.middle.value - 0.25f) / 0.6f)
            if (soft > 0.003f) drawStill(capture.soft, stage.camera, z, soft)
            drawRect(Color.Black.copy(alpha = 0.22f * soft))

            // the editor's tiles from before, carried off by the camera as they fade
            withTransform({
                val a = stage.camera.stillOrigin(z, size.width, size.height)
                translate(a.x, a.y)
                scale(stage.camera.zoomAt(z), stage.camera.zoomAt(z), pivot = Offset.Zero)
            }) { drawTiles(parts) }

            // the middle part's tiles, and the menu's
            val t = tiles() * (1f - u)
            drawTileOutline(stage.menusTile, t)
            drawTileOutline(stage.seekTile.translate(0f, u * h * 0.12f), t)
            drawTileOutline(stage.endTile.translate(0f, u * h * 0.12f), t)
            stage.rowTiles.forEachIndexed { i, r -> drawTileOutline(r, item(i)) }
            drawTileOutline(stage.sideTile, smoother((studio.menu.value - 0.2f) / 0.6f))

            // the pieces themselves
            val zoom = stage.camera.zoomAt(z)
            val away = u * h * 0.12f
            drawPiece(pieces.layers[StudioPieces.SEEK_BAR], seekRect, stage.camera.pieceCenter(seekRect, stage.seekTo, z, spread()) + Offset(0f, away), zoom, 1f - u)
            drawPiece(pieces.layers[StudioPieces.END_TIME], endRect, stage.camera.pieceCenter(endRect, stage.endTo, z, spread()) + Offset(0f, away), zoom, 1f - u)
            val sideMid = stage.camera.pieceCenter(sideRect, stage.sideTo, z, spread())
            drawPiece(pieces.layers[StudioPieces.SIDE], sideRect, lerpO(sideMid, stage.sideMenu, u), pieceZoom(u), 1f)
            // the chevron turns over as its menu opens, as it does on the player
            drawPiece(pieces.layers[StudioPieces.CHEVRON], chevronRect, chevronCenter(), pieceZoom(u) * (1f - 0.08f * u), 1f, rotation = 180f * u)
        }

        // the menu's items: the player's own buttons and labels, grown out of the chevron
        slots.forEachIndexed { i, action ->
            MenuItemButton(
                icon = action.icon,
                active = isActive(action),
                badge = action == PlayerMenuAction.REPEAT && transport.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                center = { lerpO(chevronCenter(), stage.rowCircles[i], item(i)) },
                scale = { lerpF(0.5f, 1f, item(i)) * stage.menuZoom },
                alpha = { item(i) },
            )
            MenuItemLabel(
                text = stringResource(action.labelRes),
                rightX = { stage.rowCircles[i].x - stage.menuZoom * chevronRect.width / 2f - stage.labelGap },
                centerY = { lerpF(chevronCenter().y, stage.rowCircles[i].y, item(i)) },
                scale = stage.labelScale,
                alpha = { item(i) },
            )
        }
        MenuItemLabel(
            text = stringResource(sideAction.labelRes),
            rightX = { stage.sideMenu.x - stage.menuZoom * sideRect.width / 2f - stage.labelGap },
            centerY = { stage.sideMenu.y },
            scale = stage.labelScale,
            alpha = { smoother((studio.menu.value - 0.3f) / 0.6f) },
        )

        // what each tile edits
        val labelStyle = studioText(StudioType.Title, FontWeight.Bold)
        MiddleLabel(stringResource(R.string.ps_studio_edit_menus), labelStyle, stage.labelX, stage.menusTile.center.y) { tiles() * (1f - menu() * 2f) }
        MiddleLabel(stringResource(R.string.ps_studio_edit_seekbar), labelStyle, stage.labelX, stage.seekTile.center.y) { tiles() * (1f - menu() * 2f) }
        MiddleLabel(stringResource(R.string.ps_studio_edit_end_timestamp), labelStyle, stage.labelX, stage.endTile.center.y) { tiles() * (1f - menu() * 2f) }

        // taps
        TileTap(stage.menusTile.copy(right = w), stageMiddle) { studio.go(StudioStage.MENU) }
        TileTap(Rect(0f, stage.seekTile.top, w, stage.seekTile.bottom), stageMiddle) { studio.openSheet(StudioSheet.SEEKBAR) }
        TileTap(Rect(0f, stage.endTile.top, w, stage.endTile.bottom), stageMiddle) { studio.openSheet(StudioSheet.END_TIME) }
        stage.rowTiles.forEachIndexed { i, row -> TileTap(row, stageMenu) { studio.openSheet(StudioSheet.MENU_SLOT, i) } }
        TileTap(stage.sideTile, stageMenu) { studio.openSheet(StudioSheet.MENU_SLOT, PlayerMenuLayout.SideSlot) }
    }
}

/** a round button of the player's menu, drawn by the player's own button, scaled and placed each frame */
@Composable
private fun MenuItemButton(icon: Int, active: Boolean, badge: Boolean, center: () -> Offset, scale: () -> Float, alpha: () -> Float) {
    val basePx = with(LocalDensity.current) { 34.dp.toPx() }
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints.fixed(basePx.roundToInt(), basePx.roundToInt()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                p.placeWithLayer(0, 0) {
                    val a = alpha().coerceIn(0f, 1f)
                    this.alpha = a
                    val s = scale()
                    scaleX = s
                    scaleY = s
                    val c = center()
                    translationX = c.x - basePx / 2f
                    translationY = c.y - basePx / 2f
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        val blur = (1f - a) * 14.dp.toPx()
                        renderEffect = if (blur > 0.5f && com.mochyy.mochymusic.ui.utils.AppMotion.elementBlur) {
                            androidx.compose.ui.graphics.BlurEffect(blur, blur, androidx.compose.ui.graphics.TileMode.Clamp)
                        } else null
                    }
                }
            }
        },
    ) {
        PressScaleActionButton(
            icon = icon,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.85f),
            containerColor = if (active) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f),
            onClick = {},
            hasShadow = false,
            badgeText = if (badge) "1" else null,
        )
    }
}

/** a menu item's label, as the player's menu sets it, its right edge at [rightX] */
@Composable
private fun MenuItemLabel(text: String, rightX: () -> Float, centerY: () -> Float, scale: Float, alpha: () -> Float) {
    Text(
        text = text,
        style = MenuLabelStyle,
        maxLines = 1,
        modifier = Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints())
            layout(constraints.maxWidth, constraints.maxHeight) {
                p.placeWithLayer(0, 0) {
                    val a = alpha().coerceIn(0f, 1f)
                    this.alpha = a
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    scaleX = scale
                    scaleY = scale
                    translationX = rightX() - p.width + (1f - a) * 24.dp.toPx()
                    translationY = centerY() - p.height / 2f
                }
            }
        },
    )
}

/** a label set beside its tile, sliding in from the right as [progress] goes to 1 */
@Composable
private fun MiddleLabel(text: String, style: TextStyle, x: Float, centerY: Float, progress: () -> Float) {
    val density = LocalDensity.current
    Text(
        text = text,
        color = Color.White,
        style = style,
        maxLines = 2,
        modifier = Modifier.layout { measurable, constraints ->
            val maxWidth = (constraints.maxWidth - x - with(density) { 16.dp.toPx() }).roundToInt()
            val p = measurable.measure(Constraints(maxWidth = maxWidth.coerceAtLeast(1)))
            layout(constraints.maxWidth, constraints.maxHeight) {
                p.placeWithLayer(x.roundToInt(), (centerY - p.height / 2f).roundToInt()) {
                    val t = progress().coerceIn(0f, 1f)
                    alpha = t
                    translationX = (1f - t) * 40.dp.toPx()
                }
            }
        },
    )
}

/** a tap target over [rect] */
@Composable
private fun TileTap(rect: Rect, enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .offset(rect.left.coerceAtLeast(0f).toDp(), rect.top.toDp())
                .size((rect.right - rect.left.coerceAtLeast(0f)).toDp(), rect.height.toDp())
                .clip(RoundedCornerShape(TileCorner))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onClick),
        )
    }
}
