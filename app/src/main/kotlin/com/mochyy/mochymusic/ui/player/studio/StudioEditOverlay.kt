package com.mochyy.mochymusic.ui.player.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerEndTimestamp
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.SeekBarStyle
import com.mochyy.mochymusic.ui.component.SeekBarTrack
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundPreview
import com.mochyy.mochymusic.ui.player.customize.coverArtBox
import com.mochyy.mochymusic.ui.player.customize.displayName
import com.mochyy.mochymusic.ui.player.customize.isDisc
import com.mochyy.mochymusic.ui.player.styles.Responsive
import com.mochyy.mochymusic.ui.player.styles.rememberResponsive
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.makeTimeString
import kotlin.math.abs
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding

internal val PlayerBackgroundStyle.studioName: String
    @Composable get() = when (this) {
        PlayerBackgroundStyle.COVER_GRADIENT -> stringResource(R.string.ps_studio_bg_cover_gradient)
        PlayerBackgroundStyle.SOLID -> stringResource(R.string.ps_studio_bg_solid)
        PlayerBackgroundStyle.DARK_GRADIENT -> stringResource(R.string.ps_studio_bg_dark_gradient)
        PlayerBackgroundStyle.APPLE_MUSIC -> "Apple Music"
        PlayerBackgroundStyle.AMBIENT -> stringResource(R.string.ps_studio_bg_ambient)
    }

private fun inter(size: TextUnit, weight: FontWeight = FontWeight.Bold) = studioText(size, weight)

/**
 * Everything of the editor that draws over the player: its top bar, the cover editor (cover
 * styles sideways, the backgrounds below), and the middle part and the menu as copies of the live
 * controls, which peel away from where the real ones sit.
 */
@Composable
fun StudioEditOverlay(
    parts: StudioParts,
    glassState: GlassState,
    capture: StudioCapture,
    coverStyles: List<PlayerCoverStyle>,
    coverStyle: PlayerCoverStyle,
    onCoverStyle: (PlayerCoverStyle) -> Unit,
    backgroundStyles: List<PlayerBackgroundStyle>,
    backgroundStyle: PlayerBackgroundStyle,
    onBackgroundStyle: (PlayerBackgroundStyle) -> Unit,
    menuLayout: PlayerMenuLayout,
    seekBarStyle: SeekBarStyle,
    endTimestamp: PlayerEndTimestamp,
    barStyles: List<PlayerBottomBarStyle>,
    barStyle: PlayerBottomBarStyle,
    onBarStyle: (PlayerBottomBarStyle) -> Unit,
    modifier: Modifier = Modifier,
    /** which menu actions are on right now, for the menu's buttons */
    isMenuActionActive: (PlayerMenuAction) -> Boolean = { false },
) {
    val studio = parts.studio
    val coverIndex = coverStyles.indexOf(coverStyle).coerceAtLeast(0)
    val backgroundIndex = backgroundStyles.indexOf(backgroundStyle).coerceAtLeast(0)
    val barIndex = barStyles.indexOf(barStyle).coerceAtLeast(0)
    // the applied styles, for the live cover, the live box and the live bar to sit against
    SideEffect {
        studio.coverCommitted = coverIndex
        studio.backdropCommitted = backgroundIndex
        studio.barCommitted = barIndex
    }
    val shown by remember(studio) { derivedStateOf { studio.edit.value > 0.0005f || studio.editing } }
    if (!shown) return
    BackHandler(enabled = studio.stage >= StudioStage.EDIT && studio.sheet == null) { studio.back() }

    val coverOpen by remember(studio) { derivedStateOf { studio.cover.value > 0.0005f } }
    // open from the moment its pieces are lifted, so they never leave the screen for a frame
    val middleOpen by remember(studio) {
        derivedStateOf { studio.middle.value > 0.0005f || parts.pieces?.liftedPart == LiftedPart.MIDDLE }
    }
    val barOpen by remember(studio) {
        derivedStateOf { studio.bar.value > 0.0005f || parts.pieces?.liftedPart == LiftedPart.BAR }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (coverOpen) {
            CoverEditor(
                parts = parts,
                coverStyles = coverStyles,
                coverStyle = coverStyle,
                onCoverStyle = onCoverStyle,
                backgroundStyles = backgroundStyles,
                onBackgroundStyle = onBackgroundStyle,
                w = w,
                h = h,
            )
        }
        if (middleOpen) {
            MiddleEditor(parts, capture, menuLayout, isMenuActionActive, w, h)
        }
        if (barOpen) {
            BarEditor(parts, capture, barStyles, onBarStyle, w, h)
        }
        StudioTopBar(studio, glassState)
    }
}

/**
 * The editor's top bar, the way the playlist page does its own: the round back button, the title
 * beside it, and the content under it frosted and darkened toward the top.
 */
@Composable
private fun StudioTopBar(studio: PlayerStudioState, glassState: GlassState) {
    val r = rememberResponsive()
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val editBackground = stringResource(R.string.ps_studio_edit_background)
    val editCover = stringResource(R.string.ps_studio_edit_cover)
    val editMiddle = stringResource(R.string.ps_studio_edit_middle)
    val editMenu = stringResource(R.string.ps_studio_edit_menu)
    val editPlayer = stringResource(R.string.ps_studio_edit_player)
    val title by remember(studio, editBackground, editCover, editMiddle, editMenu, editPlayer) {
        derivedStateOf {
            when (studio.stage) {
                StudioStage.COVER -> if (studio.backdrop.value > 0.5f) editBackground else editCover
                StudioStage.MIDDLE -> editMiddle
                StudioStage.MENU -> editMenu
                else -> editPlayer
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // slides down from above the screen as the editor comes up
            .graphicsLayer { translationY = -(1f - studio.edit.value) * (statusTop.toPx() + 110.dp.toPx()) },
    ) {
        ProgressiveGlassBackground(
            state = glassState,
            // over a camera's still (already soft) the bar only darkens
            maxBlurRadius = {
                30f * (1f - studio.backdrop.value * studio.cover.value) *
                    (1f - studio.middle.value.coerceIn(0f, 1f)) * (1f - studio.bar.value.coerceIn(0f, 1f))
            },
            tint = Color.Black.copy(alpha = 0.16f),
            direction = BlurDirection.BottomToTop,
            steps = 3,
            modifier = Modifier
                .fillMaxWidth()
                .height(statusTop + 92.dp)
                .graphicsLayer { alpha = (1f - studio.backdrop.value * studio.cover.value).coerceIn(0f, 1f) },
        )
        val progressiveBar = com.mochyy.mochymusic.ui.component.rememberProgressiveBlur()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // a flat bar's tint stops at its hairline, the fade ran a little past it
                .height(statusTop + if (progressiveBar) 112.dp else 92.dp)
                .drawBehind {
                    if (progressiveBar) {
                        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent))
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.3f))
                    }
                },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .stableStatusBarsPadding()
                .padding(top = 10.dp)
                .height(40.dp)
                .padding(start = 22.dp, end = 22.dp),
        ) {
            // the playlist page's back button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = studio.stage >= StudioStage.EDIT) { studio.back() },
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = stringResource(R.string.back_button_desc),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp).offset(x = 2.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (slideInVertically(tween(420)) { it } + fadeIn(tween(300))) togetherWith
                            (slideOutVertically(tween(360)) { -it } + fadeOut(tween(220)))
                    },
                    label = "studioTitle",
                ) { text ->
                    Text(text, color = Color.White, style = inter(StudioType.Title))
                }
                // what the first page's tiles are for; it goes as a tile opens
                val onTiles = studio.stage == StudioStage.EDIT
                androidx.compose.animation.AnimatedVisibility(
                    visible = onTiles,
                    enter = fadeIn(tween(240)) + androidx.compose.animation.expandVertically(tween(300)),
                    exit = fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(260)),
                ) {
                    Text(stringResource(R.string.ps_studio_hold_to_adjust), color = Color.White.copy(alpha = 0.7f), style = inter(StudioType.Subtitle, FontWeight.Medium))
                }
            }
        }
    }
}

// ---- cover editor ------------------------------------------------------------------------

@Composable
private fun CoverEditor(
    parts: StudioParts,
    coverStyles: List<PlayerCoverStyle>,
    coverStyle: PlayerCoverStyle,
    onCoverStyle: (PlayerCoverStyle) -> Unit,
    backgroundStyles: List<PlayerBackgroundStyle>,
    onBackgroundStyle: (PlayerBackgroundStyle) -> Unit,
    w: Float,
    h: Float,
) {
    val studio = parts.studio
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val queue by connection.uiState.queueState.collectAsState()
    val density = LocalDensity.current
    val r = rememberResponsive()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val currentCovers by rememberUpdatedState(coverStyles)
    val currentBackgrounds by rememberUpdatedState(backgroundStyles)
    val currentOnCover by rememberUpdatedState(onCoverStyle)
    val currentOnBackground by rememberUpdatedState(onBackgroundStyle)
    val active = studio.stage == StudioStage.COVER && studio.sheet == null
    val gapPx = with(density) { CardGap.toPx() }
    val maxW = with(density) { w.toDp() }
    val maxH = with(density) { h.toDp() }
    // the other covers sit where the live one does, moved with the controls under it (nothing is
    // held while this editor is open, so it's read once)
    val coverOffset = with(density) { (parts.tweaks?.controlsOffset() ?: 0f).toDp() }
    val inBackdrop by remember(studio) { derivedStateOf { studio.backdrop.value > 0.5f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!active) Modifier else Modifier.pointerInput(w, h) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        var dx = 0f
                        var dy = 0f
                        var mode = 0
                        val backdrop = studio.backdrop.value > 0.5f
                        val startBackdrop = studio.backdrop.value
                        val page = if (backdrop) studio.backdropPage else studio.coverPage
                        val startPage = page.value
                        val stride = if (backdrop) w * BoxScale + gapPx else w
                        val last = (if (backdrop) currentBackgrounds.size else currentCovers.size) - 1
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChangeIgnoreConsumed()
                            dx += delta.x
                            dy += delta.y
                            tracker.addPointerInputChange(change)
                            if (mode == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) mode = if (abs(dx) >= abs(dy)) 1 else 2
                            when (mode) {
                                1 -> {
                                    change.consume()
                                    // past either end it gives a little and comes back
                                    val raw = startPage - dx / stride
                                    page.value = when {
                                        raw < 0f -> raw * 0.3f
                                        raw > last -> last + (raw - last) * 0.3f
                                        else -> raw
                                    }
                                }
                                2 -> {
                                    change.consume()
                                    studio.backdrop.value = (startBackdrop - dy / (h * 0.55f)).coerceIn(0f, 1f)
                                }
                            }
                        }
                        val velocity = tracker.calculateVelocity()
                        when (mode) {
                            1 -> {
                                val flick = (-velocity.x / stride).coerceIn(-3f, 3f)
                                val target = (page.value + flick * 0.3f).roundToInt().coerceIn(0, last)
                                // the style under it applies once it comes to rest; a swipe before
                                // then just carries on from wherever it is
                                page.animateTo(target.toFloat(), velocity = flick) {
                                    if (backdrop) {
                                        currentBackgrounds.getOrNull(target)?.let(currentOnBackground)
                                    } else {
                                        currentCovers.getOrNull(target)?.let(currentOnCover)
                                    }
                                }
                            }
                            2 -> {
                                val target = when {
                                    velocity.y < -900f -> 1f
                                    velocity.y > 900f -> 0f
                                    studio.backdrop.value > 0.5f -> 1f
                                    else -> 0f
                                }
                                studio.backdrop.animateTo(target, velocity = -velocity.y / (h * 0.55f))
                            }
                        }
                    }
                }
            ),
    ) {
        // every other cover style, waiting beside the live cover; only the ones in view are drawn
        coverStyles.forEachIndexed { index, style ->
            key(style) {
                val box = remember(style, maxW, maxH, statusTop, coverOffset) { coverArtBox(style, maxW, maxH, statusTop, coverOffset) }
                Box(
                    modifier = Modifier
                        .offset(box.x, box.y)
                        .size(box.width, box.height)
                        .graphicsLayer {
                            val d = index - studio.coverPage.value
                            val visible = index != studio.coverCommitted && abs(d) < 1f
                            translationX = d * w
                            alpha = if (visible) (studio.cover.value * (1f - studio.backdrop.value)).coerceIn(0f, 1f) else 0f
                        },
                ) {
                    if (index != coverIndexOf(studio)) {
                        CoverArt(style, track, isPlaying = false, queueIndex = queue.currentIndex)
                    }
                }
            }
        }

        // every other background, as a box beside the live one
        backgroundStyles.forEachIndexed { index, style ->
            key(style) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val b = studio.backdrop.value * studio.cover.value
                            val s = 1f + (BoxScale - 1f) * b
                            val stride = w * s + gapPx
                            val d = index - studio.backdropPage.value
                            val visible = index != studio.backdropCommitted && b > 0.002f && abs(d * stride) < w / 2f + w * s / 2f
                            scaleX = s
                            scaleY = s
                            translationX = d * stride
                            translationY = (BoxCenterY * h - h / 2f) * b
                            shape = RoundedCornerShape(BoxCorner.toPx() * b.coerceIn(0f, 1f) / s.coerceAtLeast(0.01f))
                            clip = true
                            alpha = if (visible) 1f else 0f
                        }
                        .inert(),
                ) {
                    if (index != studio.backdropCommitted) {
                        PlayerBackgroundPreview(style = style, thumbnailUrl = track.thumbnailUrl, width = maxW, height = maxH, animate = false, warp = false, modifier = Modifier.fillMaxSize())
                        val box = remember(coverStyle, maxW, maxH, statusTop, coverOffset) { coverArtBox(coverStyle, maxW, maxH, statusTop, coverOffset) }
                        Box(Modifier.offset(box.x, box.y).size(box.width, box.height)) {
                            CoverArt(coverStyle, track, false, queue.currentIndex)
                        }
                    }
                }
            }
        }

        // the disc's label: tap it for what it says. With an album card in front of the disc, the
        // card is the target: the disc's middle is under it
        if (coverStyle.isDisc) {
            val art = parts.anchors.cover.value
            if (art.width > 0f) {
                val card = remember(coverStyle) { com.mochyy.mochymusic.ui.player.customize.discGeometryFor(coverStyle).albumCard }
                val target = if (card != null) {
                    val side = minOf(art.width, art.height) * card.side
                    val c = Offset(art.left + art.width * card.centerX, art.top + art.height * card.centerY)
                    androidx.compose.ui.geometry.Rect(c.x - side / 2f, c.y - side / 2f, c.x + side / 2f, c.y + side / 2f)
                } else {
                    val radius = art.width * 0.2f
                    androidx.compose.ui.geometry.Rect(art.center.x - radius, art.center.y - radius, art.center.x + radius, art.center.y + radius)
                }
                val pulse = rememberInfiniteTransition(label = "discLabel")
                val beat by pulse.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "discLabelBeat")
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val off = abs(studio.coverPage.value - studio.coverCommitted)
                            alpha = (studio.cover.value * (1f - off * 3f) * (1f - studio.backdrop.value * 4f)).coerceIn(0f, 1f)
                        },
                ) {
                    val stroke = Stroke(width = TileStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                    val color = Color.White.copy(alpha = 0.8f * beat)
                    if (card != null) {
                        // just inside the card's own rounded corners
                        val inset = TileStroke.toPx() * 2f
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(target.left + inset, target.top + inset),
                            size = androidx.compose.ui.geometry.Size(target.width - inset * 2, target.height - inset * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(target.width * 0.06f),
                            style = stroke,
                        )
                    } else {
                        drawCircle(color = color, radius = target.width / 2f, center = target.center, style = stroke)
                    }
                }
                with(density) {
                    Box(
                        modifier = Modifier
                            .offset(target.left.toDp(), target.top.toDp())
                            .size(target.width.toDp(), target.height.toDp())
                            .clip(if (card != null) RoundedCornerShape((target.width * 0.06f).toDp()) else CircleShape)
                            .clickable(enabled = active && !inBackdrop) { studio.openSheet(StudioSheet.DISC_LABEL) },
                    )
                }
            }
        }

        // what's showing, and its options: rising up from the bottom with the editor
        val shownIndex by remember(studio, coverStyles.size, backgroundStyles.size) {
            derivedStateOf {
                if (studio.backdrop.value > 0.5f) {
                    studio.backdropPage.value.roundToInt().coerceIn(0, backgroundStyles.lastIndex)
                } else {
                    studio.coverPage.value.roundToInt().coerceIn(0, coverStyles.lastIndex)
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = navBottom + 60.dp)
                .graphicsLayer { translationY = (1f - studio.cover.value) * 320.dp.toPx() },
        ) {
            AnimatedContent(
                targetState = stringResource(if (inBackdrop) R.string.ps_studio_background_n else R.string.ps_studio_style_n, shownIndex + 1),
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "studioStyleNumber",
            ) { label ->
                Text(label, color = Color.White.copy(alpha = 0.6f), style = inter(StudioType.Label, FontWeight.SemiBold))
            }
            AnimatedContent(
                targetState = if (inBackdrop) {
                    backgroundStyles.getOrNull(shownIndex)?.studioName.orEmpty()
                } else {
                    coverStyles.getOrNull(shownIndex)?.displayName.orEmpty()
                },
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "studioStyleName",
            ) { name ->
                Text(name, color = Color.White, style = inter(StudioType.Name).copy(lineHeight = 24.sp))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = (1f - studio.backdrop.value * 2f).coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(enabled = active && !inBackdrop) { studio.openSheet(StudioSheet.COVER_OPTIONS) }
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.tune), null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.ps_studio_more_options), color = Color.White, style = inter(StudioType.Body, FontWeight.SemiBold))
            }
        }

        // the way down to the backgrounds (and back up)
        val bounce = rememberInfiniteTransition(label = "studioChevron")
        val nudge by bounce.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "studioChevronNudge")
        Icon(
            painter = painterResource(R.drawable.expand_more),
            contentDescription = stringResource(if (inBackdrop) R.string.ps_studio_back_to_covers else R.string.ps_studio_backgrounds),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 14.dp)
                .size(32.dp)
                .graphicsLayer {
                    val b = studio.backdrop.value
                    translationY = (1f - studio.cover.value) * 200.dp.toPx() + nudge * 5.dp.toPx() * (if (b > 0.5f) -1f else 1f)
                    rotationZ = 180f * b
                }
                .clip(CircleShape)
                .clickable(enabled = active) { studio.backdrop.animateTo(if (inBackdrop) 0f else 1f) },
        )
    }
}

private fun coverIndexOf(studio: PlayerStudioState) = studio.coverCommitted
