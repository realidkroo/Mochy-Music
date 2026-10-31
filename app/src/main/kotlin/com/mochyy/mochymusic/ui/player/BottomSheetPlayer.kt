// BottomSheetPlayer.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.media3.common.Player
import com.mochyy.mochymusic.constants.PlayerHorizontalPadding
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.player.studio.playHoldRamp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Build
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.joinAll
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerBackgroundStyleKey
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.DefaultPlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerCoverStyleKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.ShowPlayerBottomCardKey
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import com.mochyy.mochymusic.constants.PlayerBottomBarStyleKey
import com.mochyy.mochymusic.constants.InfiniteQueueKey
import com.mochyy.mochymusic.constants.FlagshipBackgroundStyleKey
import com.mochyy.mochymusic.constants.FlagshipBottomBarStyleKey
import com.mochyy.mochymusic.constants.FlagshipTransportLayout
import com.mochyy.mochymusic.constants.FlagshipTransportLayoutKey
import com.mochyy.mochymusic.ui.player.flagship.FlagshipBarHeight
import com.mochyy.mochymusic.ui.player.flagship.FlagshipBottomBar
import com.mochyy.mochymusic.ui.player.flagship.FlagshipPillLook
import com.mochyy.mochymusic.ui.player.flagship.FlagshipPillMorphSurface
import com.mochyy.mochymusic.ui.player.flagship.FlagshipTransportRow
import com.mochyy.mochymusic.ui.player.flagship.ControlsShade
import com.mochyy.mochymusic.ui.player.flagship.CoverFrost
import com.mochyy.mochymusic.ui.player.flagship.flagshipBarFootprint
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors
import androidx.activity.compose.BackHandler
import com.mochyy.mochymusic.ui.component.BottomSheet
import com.mochyy.mochymusic.ui.component.BottomSheetState
import com.mochyy.mochymusic.ui.component.ExpandedSwipe
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.player.studio.EditTiles
import com.mochyy.mochymusic.ui.player.studio.PlayerSelectorOverlay
import com.mochyy.mochymusic.ui.player.studio.StudioEditOverlay
import com.mochyy.mochymusic.ui.player.studio.StudioSheets
import com.mochyy.mochymusic.ui.player.studio.StudioStage
import com.mochyy.mochymusic.ui.player.studio.studioAnchor
import com.mochyy.mochymusic.ui.player.studio.studioCaptureSource
import com.mochyy.mochymusic.ui.player.studio.studioBarLayer
import com.mochyy.mochymusic.ui.player.studio.LiftedPart
import com.mochyy.mochymusic.ui.player.studio.StudioCameraEffect
import com.mochyy.mochymusic.ui.player.studio.StudioPieces
import com.mochyy.mochymusic.ui.player.studio.studioCoverLayer
import com.mochyy.mochymusic.ui.player.studio.studioMiddleLayer
import com.mochyy.mochymusic.ui.player.studio.studioSheetLayer
import com.mochyy.mochymusic.ui.player.studio.studioTransportLayer
import com.mochyy.mochymusic.ui.player.studio.studioPiece
import com.mochyy.mochymusic.ui.player.studio.holdAnywhere
import com.mochyy.mochymusic.ui.player.menu.PlayerActionMenu
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.utils.stableStatusBars

/**
 * How far the controls are pulled up over the artwork.
 *
 * This only makes sense for an edge-to-edge cover, where the art bleeds behind the controls and
 * the overlap is the intended look. Every other cover style ends its artwork above the controls,
 * so the same 64dp lift dragged the title, timestamps and seek bar back up onto the cover - the
 * overlap that showed up once SQUARED became the default on devices without blur.
 */
private val ControlsDrawShift = 64.dp

/** Small upward nudge for the seek bar and its timestamps, so they sit off the controls below. */
private val SeekBarLift = 6.dp

/** the Turntable's controls stand this far above the card, as in its frame */
private val TurntableControlsLift = 44.dp

/** The queue page's third button (autoplay) and the gap above it, on top of the lyrics pair. */
private val QueueButtonExtraHeight = 41.dp

/** Chevron, shuffle and autoplay: three 34dp buttons, 7dp apart. */
private val QueueButtonColumnHeight = 75.dp + QueueButtonExtraHeight


/** A swipe on the strip below the seek bar commits past this travel, or past [PageSwipeFling]. */
private val PageSwipeCommit = 40.dp

/** Fling speed (per second) that commits a swipe on its own. */
private val PageSwipeFling = 600.dp

/** What the vertical swipe being handled is doing, see the handler in [BottomSheetPlayer]. */
private enum class PlayerSwipe {
    NONE,

    /** A page following the finger up out of the card. */
    PULL,

    /** Up on the strip below the seek bar with a page open: the page the card offers. */
    OPEN_OTHER,

    /** Down on that strip: the open page following the finger back down into the card. */
    PUSH,

    /** Down on that strip over a page opened from the other one: back to that one. */
    BACK,
}

/** A flick on the mini player faster than this skips however short it was. */
private val MiniSwipeFling = 500.dp

/**
 * The strip along the top of the open player, under the status bar, where a swipe down closes it
 * (as well as the whole cover, when it's the edge-to-edge one).
 */
private val PlayerCloseStrip = 64.dp

// the sheet's content is fully faded out up to here, see BottomSheet
private const val StyleHiddenBelow = 0.25f

/** The old gap from the seek bar down to the buttons (24dp plus the bar's lift). */
private val TransportGapAboveRow = 24.dp + SeekBarLift

/** From the top of the page column's chevron to the top of the button under it (34dp + a 7dp gap). */
private val PageButtonStep = 41.dp

/**
 * A page's own buttons (shuffle and autoplay on the queue, like when it isn't the player's side
 * button) open out from behind the chevron once it has nearly arrived: this far into the page's
 * progress they start, and they're out at the end.
 */
private const val PageButtonsFrom = 0.3f

/** Eases out, so the buttons spring out from behind the chevron and settle softly. */
private fun pageButtonsOut(pageProgress: Float): Float {
    val t = ((pageProgress - PageButtonsFrom) / (1f - PageButtonsFrom)).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/**
 * A slot whose height (and a gap under it) is read at measure time, with its content pinned to the
 * slot's bottom and free to overflow upward - what `height(x).wrapContentHeight(Bottom, unbounded)`
 * did, minus the recomposition every frame the height animates.
 */
private fun Modifier.bottomAnchoredSlot(
    height: () -> androidx.compose.ui.unit.Dp,
    bottomGap: () -> androidx.compose.ui.unit.Dp = { 0.dp },
): Modifier = layout { measurable, constraints ->
    val slot = height().roundToPx().coerceAtLeast(0)
    val gap = bottomGap().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
    )
    val total = (slot + gap).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, total) {
        placeable.place(0, slot - placeable.height)
    }
}

@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    // one row of songs for every part of the player (cover, record, title, background), so they
    // all slide together whichever style is showing
    val songCarousel = rememberSongCarousel(playerConnection)
    androidx.compose.runtime.CompositionLocalProvider(LocalSongCarousel provides songCarousel) {
        PlayerSheet(state = state, navController = navController, modifier = modifier, pureBlack = pureBlack)
    }
}

@Composable
private fun PlayerSheet(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()

    val morphingGlassState = remember { GlassState() }
    // The like animation: hearts fly home to the like button on whichever page is showing.
    val likeBurst = remember { LikeBurstState() }
    // The lyrics and queue pages. They share the header morph (cover and title move up the same
    // way) and the slot above the seek bar; only one is ever open. Opened from the player, a page
    // grows out of the bottom card - by tap or by swiping up anywhere on the player.
    val transition = rememberPlayerPageTransition()
    val deck = remember { PlayerDeckState() }
    val morph = remember { CardMorphState() }
    val showLyrics by remember { derivedStateOf { transition.page == PlayerPage.LYRICS } }
    val showQueue by remember { derivedStateOf { transition.page == PlayerPage.QUEUE } }
    likeBurst.slotProvider = {
        when {
            showQueue -> LikeSlot.QUEUE
            showLyrics -> LikeSlot.LYRICS
            else -> LikeSlot.PLAYER
        }
    }
    val (infiniteQueue) = rememberPreference(InfiniteQueueKey, defaultValue = true)
    val (showPlayerBottomCard) = rememberPreference(ShowPlayerBottomCardKey, defaultValue = true)

    // the player studio: the selector the player shrinks into, and the flagship's in-place editor
    // the selector's columns, as indexes into PlayerStyle.entries: the flagship with its older layouts under it
    val selectorColumns = remember { PlayerStyle.selectorColumns.map { column -> column.map { it.ordinal } } }
    val studio = com.mochyy.mochymusic.ui.player.studio.rememberPlayerStudioState(selectorColumns)
    val studioAnchors = remember { com.mochyy.mochymusic.ui.player.studio.StudioAnchors() }
    val studioParts = com.mochyy.mochymusic.ui.player.studio.rememberStudioParts(studio, studioAnchors)
    val menuLayout = com.mochyy.mochymusic.ui.player.studio.rememberPlayerMenuLayout()
    val studioFrame = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val studioCapture = remember(studioFrame) { com.mochyy.mochymusic.ui.player.studio.StudioCapture(studioFrame) }
    // the pieces the editor lifts off the player while a part is edited (the middle part's, or the
    // transport and the bottom bar), so it moves the player's own drawing of each
    val studioPieces = com.mochyy.mochymusic.ui.player.studio.rememberStudioPieces()
    // where the editor's hold put the controls (and so how big the cover is) and how tall the card is
    val layoutTweaks = rememberPlayerLayoutTweaks()
    androidx.compose.runtime.SideEffect {
        studioParts.pieces = studioPieces
        studioParts.tweaks = layoutTweaks
    }
    var showPlaybackSpeed by remember { mutableStateOf(false) }
    var showDeviceOutput by remember { mutableStateOf(false) }
    var showLyricsSource by remember { mutableStateOf(false) }

    var showExpandMenu by remember { mutableStateOf(false) }
    var seeVideo by remember { mutableStateOf(false) }
    // a youtube video is playing on the cover, so the chevron offers the full video mode
    var videoShowing by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    // The title and artist's sheet: the artists, the album, about the song
    var showSongDetails by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }

    var showActionMenu by remember { mutableStateOf(false) }
    // Which surface opened the menu. The lyrics tools only appear when it came from the lyrics
    // page, where they have something visible to act on.
    var menuFromLyrics by remember { mutableStateOf(false) }

    val menuReveal = remember { mutableFloatStateOf(0f) }
    val menuRevealShape = remember { AnimatedCornerShape() }

    var coverStyle by rememberEnumPreference(PlayerCoverStyleKey, DefaultPlayerCoverStyle)
    val (savedPlayerStyle, savePlayerStyle) = rememberEnumPreference(
        com.mochyy.mochymusic.constants.PlayerStyleKey,
        com.mochyy.mochymusic.constants.PlayerStyle.FLAGSHIP,
    )
    // A style picked in the studio shows at once. The saved one only comes back after the
    // settings file is written, which landed in the middle of the player growing back out of its
    // card - the old style grew, then the new one was built mid-spring, and the spring stuttered.
    var pickedPlayerStyle by remember { mutableStateOf<PlayerStyle?>(null) }
    val playerStyle = pickedPlayerStyle ?: savedPlayerStyle
    LaunchedEffect(savedPlayerStyle) {
        if (savedPlayerStyle == pickedPlayerStyle) pickedPlayerStyle = null
    }
    val (videoModeReturn, saveVideoModeReturn) = rememberPreference(com.mochyy.mochymusic.constants.VideoModeReturnStyleKey, "")
    val onPlayerStyleChange: (PlayerStyle) -> Unit = { style ->
        // the style the full video mode goes back to when it's left
        if (style == PlayerStyle.VIDEO && playerStyle != PlayerStyle.VIDEO) saveVideoModeReturn(playerStyle.name)
        pickedPlayerStyle = style
        savePlayerStyle(style)
    }
    // the Turntable is the flagship around a record player, whatever cover the flagship is set to
    val isTurntable = playerStyle == com.mochyy.mochymusic.constants.PlayerStyle.TURNTABLE
    val shownCoverStyle = if (isTurntable) PlayerCoverStyle.TURNTABLE else coverStyle
    val controlsDrawShift = if (shownCoverStyle == PlayerCoverStyle.EDGE_TO_EDGE) ControlsDrawShift else 0.dp
    // the new flagship: its own buttons and bottom bar, the cover frosting into moving colours
    val isFlagship = playerStyle == PlayerStyle.FLAGSHIP
    // each flagship keeps its own background, the new one starting on the moving colours it was made with
    var classicBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.COVER_GRADIENT)
    var flagshipBackground by rememberEnumPreference(FlagshipBackgroundStyleKey, PlayerBackgroundStyle.AMBIENT)
    val backgroundStyle = if (isFlagship) flagshipBackground else classicBackground
    var transportLayout by rememberEnumPreference(FlagshipTransportLayoutKey, FlagshipTransportLayout.THREE)
    // the colours the new flagship's backdrop and cover fade drift between
    val ambient = rememberAmbientColors()
    // what sits under the play buttons: the card deck or the volume bar, again each flagship with its own
    val (classicBarStyle, onClassicBarStyle) = rememberEnumPreference(PlayerBottomBarStyleKey, PlayerBottomBarStyle.STACK)
    val (flagshipBarStyle, onFlagshipBarStyle) = rememberEnumPreference(FlagshipBottomBarStyleKey, PlayerBottomBarStyle.STACK)
    // the card under the buttons: the classic's glass, or the new flagship's faint outlined one
    val cardLook = if (isFlagship) FlagshipCardLook else ClassicCardLook
    val barStyle = if (isFlagship) flagshipBarStyle else classicBarStyle
    val onBarStyle: (PlayerBottomBarStyle) -> Unit = if (isFlagship) onFlagshipBarStyle else onClassicBarStyle
    StudioCameraEffect(studio, studioCapture, studioPieces, barStyle)
    // while the bar is edited both its styles are built, each lifted off the player for the editor
    val barLifted by remember { derivedStateOf { studioPieces.liftedPart == LiftedPart.BAR } }
    val showDeck = showPlayerBottomCard && barStyle == PlayerBottomBarStyle.STACK
    val showVolumeBar = barStyle == PlayerBottomBarStyle.VOLUME
    // the flagship's bar has no card to grow a page from, so the page grows out of the pill's lyrics or queue half
    val pillMorph = isFlagship && showVolumeBar
    val pageLook = if (pillMorph) FlagshipPillLook else cardLook
    // a style stored by the ones these replaced lands on its successor
    val (storedPlayerStyle, onStoredPlayerStyle) = rememberPreference(com.mochyy.mochymusic.constants.PlayerStyleKey, "")
    LaunchedEffect(storedPlayerStyle) {
        com.mochyy.mochymusic.constants.PlayerStyle.fromStored(storedPlayerStyle)?.let { onStoredPlayerStyle(it.name) }
    }

    // plain fields, not state: they are written every frame of the slide but only read when a swipe starts
    val rects = remember { PlayerRects() }

    val studioEngaged by remember { derivedStateOf { studio.engaged } }
    LaunchedEffect(studioEngaged) {
        if (studioEngaged) transition.close()
    }
    val playerStyles = remember { com.mochyy.mochymusic.constants.PlayerStyle.entries.toList() }
    val currentPlayerStyleForStudio by androidx.compose.runtime.rememberUpdatedState(playerStyle)
    val openStudio: () -> Unit = remember(studio) {
        {
            showActionMenu = false
            showExpandMenu = false
            studio.open(playerStyles.indexOf(currentPlayerStyleForStudio).coerceAtLeast(0))
        }
    }
    // a hold opens it with a buzz rising from soft to strong, the selector arriving on the peak
    val studioContext = androidx.compose.ui.platform.LocalContext.current
    val studioScope = androidx.compose.runtime.rememberCoroutineScope()
    val holdToStudio: () -> Unit = remember(openStudio, studioContext, studioScope) {
        var pending: kotlinx.coroutines.Job? = null
        {
            if (!studio.engaged && pending?.isActive != true) {
                studioContext.playHoldRamp()
                pending = studioScope.launch {
                    kotlinx.coroutines.delay(com.mochyy.mochymusic.ui.player.studio.HoldRampMs)
                    openStudio()
                }
            }
        }
    }
    // "Customize player" in settings: the player opens, then shrinks into the selector
    val studioRequest by playerConnection.studioRequests.collectAsState()
    val handledStudioRequest = remember { androidx.compose.runtime.mutableLongStateOf(playerConnection.studioRequests.value) }
    LaunchedEffect(studioRequest) {
        if (studioRequest == handledStudioRequest.longValue) return@LaunchedEffect
        handledStudioRequest.longValue = studioRequest
        state.expandSoft()
        androidx.compose.runtime.snapshotFlow { state.isExpanded }.first { it }
        kotlinx.coroutines.delay(180)
        openStudio()
    }
    // the full video mode, from the options sheet or the chevron while a youtube video plays
    val enterVideoMode: () -> Unit = {
        showActionMenu = false
        showExpandMenu = false
        onPlayerStyleChange(PlayerStyle.VIDEO)
    }
    val exitVideoMode = androidx.compose.runtime.rememberUpdatedState {
        val back = PlayerStyle.entries.firstOrNull { it.name == videoModeReturn && it != PlayerStyle.VIDEO }
            ?: PlayerStyle.fromStored(videoModeReturn)
        onPlayerStyleChange(back ?: PlayerStyle.FLAGSHIP)
    }
    // the song's whole lyrics, opened over an alternate style from its ••• menu
    var styleLyricsOpen by remember { mutableStateOf(false) }
    // that page fully up: the style under it stops drawing
    var styleCovered by remember { mutableStateOf(false) }
    val styleActions = remember(openStudio) {
        com.mochyy.mochymusic.ui.player.styles.StyleActions(
            onOtherMenu = {
                // the styles that perform the lyrics get the lyrics tools too: provider, translation
                menuFromLyrics = currentPlayerStyleForStudio == PlayerStyle.POSTER ||
                    currentPlayerStyleForStudio == PlayerStyle.LYRICS
                showActionMenu = true
            },
            onEditPlayer = openStudio,
            onLyrics = { styleLyricsOpen = true },
            onExitVideoMode = { exitVideoMode.value() },
            onOpenArtist = { artistId ->
                navController.navigate("artist/$artistId")
                state.collapseSoft()
            },
            onOpenAlbum = { albumId ->
                navController.navigate("album/$albumId")
                state.collapseSoft()
            },
        )
    }

    // Keyed on where the sheet is headed, not on it sitting exactly at the top: that drops when
    // the window's height changes under the open player, until the sheet catches up, and each
    // drop shut the chevron's menu as it opened.
    OnSheetHeadingDown(state) {
        studio.reset()

        showActionMenu = false
        menuReveal.floatValue = 0f
        showExpandMenu = false
        likeBurst.clear()
        showSleepTimer = false
        showSongDetails = false
        showStreamInfo = false
    }

    // A page open when the player closes stays open: next time the player opens, it opens on that
    // page - the same way it opens on the player itself when that's where it was left. Closing the
    // page as the sheet started to collapse also ran the page's own shrink-into-the-card animation
    // on top of the sheet's, which is what made closing from lyrics or the queue so heavy. Only
    // dismissing the player (nothing left to play) forgets the page.
    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) transition.reset()
    }

    // Any part of the expanded player on screen. The pages and the seek bar's clock only run while
    // it is: collapsed, the pages are unmounted (still remembered) and the seek bar holds still.
    val sheetShowing by remember(state) { derivedStateOf { state.progress > 0f } }

    // Settings > Customise > Gesture: a swipe down anywhere on the open player closes it (#121)
    val (swipeDownAnywhere) = rememberPreference(
        com.mochyy.mochymusic.constants.PlayerSwipeDownAnywhereKey,
        defaultValue = false,
    )
    val currentSwipeDownAnywhere by androidx.compose.runtime.rememberUpdatedState(swipeDownAnywhere)

    val isSheetInTransition by remember(state) {
        derivedStateOf { !state.isExpanded && !state.isCollapsed && !state.isDismissed }
    }

    var songInfoSourceRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    // the side button by the chevron, measured on the plain player: the lyrics column's like
    // flies from here, and anything else tucks in behind the chevron from here
    var sideButtonRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var chevronButtonRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    // Kept apart from chevronButtonRect: both chevrons used to write that one, so the expand
    // menu anchored to whichever laid out last - often the other, stale one - and jumped.
    var lyricsChevronRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val statusBarTopInset = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // The bar at the top of the open player, on every style: bright while it's touched, faint
    // once left alone. The sheet tells it about every touch.
    val handleState = rememberPlayerHandleState()

    /**
     * Distance from the top of the seek bar to the bottom of the screen.
     *
     * The lyrics list ends at the seek bar, so no line is ever drawn behind the bar or its
     * timestamps; the list's own bottom fade finishes just above it.
     */
    var lyricsBottomInset by remember { mutableStateOf(120.dp) }

    // Where the seek bar's top is on screen (root, px; NaN until measured): the vertical swipe
    // handler's line between the player above and the strip of controls below it. Held in a plain
    // array, not state - it is only read when a swipe begins.
    val seekBarTop = remember { floatArrayOf(Float.NaN) }

    var lyricsImmersive by remember { mutableStateOf(false) }
    // The queue hides the controls the same way when scrolled, each page with its own flag so one
    // page's state never hides the controls on the other.
    var queueImmersive by remember { mutableStateOf(false) }
    val controlsHidden by animateFloatAsState(
        targetValue = if (
            (lyricsImmersive && showLyrics) || (queueImmersive && showQueue)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsControlsHide",
    )

    // The header morph: cover and title moving up into the page header. Lyrics and the queue share
    // it, so switching straight from one page to the other leaves the header where it is. Clamped
    // here: the open spring's little landing bounce belongs to the page, not to the cover.
    val lyricsProgress by remember { derivedStateOf { transition.headerProgress.coerceIn(0f, 1f) } }


    // Where the chevron is on its flight from the row to the page's column (root px), or null
    // before both ends are measured. Read while drawing.
    fun flyingChevronCenter(): Offset? {
        val from = chevronButtonRect ?: return null
        val to = lyricsChevronRect ?: return null
        val k = 1f - lyricsProgress
        return Offset(to.center.x + (from.center.x - to.center.x) * k, to.center.y + (from.center.y - to.center.y) * k)
    }

    // A button of the page's own opening out from behind the chevron as the page's progress goes
    // 0 to 1, from wherever the chevron is on its flight: it grows as it slides down into its
    // place, and stays hidden while it's still tucked in behind. [below] is how far under the
    // chevron's top its own place is laid out.
    fun androidx.compose.ui.graphics.GraphicsLayerScope.openOutFromChevron(pageProgress: Float, below: Float) {
        val out = pageButtonsOut(pageProgress)
        val from = chevronButtonRect
        val to = lyricsChevronRect
        val k = 1f - lyricsProgress
        val flightX = if (from != null && to != null) (from.center.x - to.center.x) * k else 0f
        val flightY = if (from != null && to != null) (from.center.y - to.center.y) * k else 0f
        translationX = flightX * (1f - out)
        translationY = (flightY - below) * (1f - out)
        val scale = 0.7f + 0.3f * out
        scaleX = scale
        scaleY = scale
        alpha = (out * 4f).coerceIn(0f, 1f)
    }

    // The lyrics page's like is the player's side button itself, flown over, when that button is
    // the like and the page is growing out of (or back into) the card. Switching over from the
    // queue it opens out from behind the chevron like the page's other buttons.
    fun likeMovesToLyrics(): Boolean =
        menuLayout.side == com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE &&
            transition.isCardMorph(PlayerPage.LYRICS)

    // Each page's own content. Opened from the player it is the header's progress (one motion);
    // switching pages crossfades the content under a header that stays put.
    val lyricsPageProgress by remember { derivedStateOf { transition.lyricsProgress.coerceIn(0f, 1f) } }
    val queuePageProgress by remember { derivedStateOf { transition.queueProgress.coerceIn(0f, 1f) } }
    // the Turntable's pages: lyrics or queue, whichever is opening
    val turntablePageProgress by remember { derivedStateOf { maxOf(lyricsPageProgress, queuePageProgress) } }
    // Also while the page is being built, just before it grows (PlayerPageTransition.preparing)
    val lyricsPageMounted by remember {
        derivedStateOf { lyricsPageProgress > 0.001f || transition.preparing == PlayerPage.LYRICS }
    }
    val queuePageMounted by remember {
        derivedStateOf { queuePageProgress > 0.001f || transition.preparing == PlayerPage.QUEUE }
    }

    val lyricsMounted by remember { derivedStateOf { lyricsProgress > 0.001f } }

    // Counted from the moment the page is being built for its open: it then mounts already in its
    // morphing state, instead of every row recomposing as the flag flips in the open's first frame
    val lyricsMorphing by remember {
        derivedStateOf {
            (lyricsProgress > 0.001f && lyricsProgress < 0.999f) || transition.preparing == PlayerPage.LYRICS
        }
    }
    val lyricsButtonMounted by remember { derivedStateOf { lyricsProgress < 0.999f } }
    // Also built just before a page closes, while the deck is still hidden (preparingClose), and
    // kept from then on: with no page open the controls are showing or on their way back
    val deckMounted by remember {
        derivedStateOf { controlsHidden < 0.99f || transition.preparingClose || transition.page == PlayerPage.NONE }
    }
    androidx.compose.runtime.SideEffect {
        transition.closeNeedsPreparing = { (showDeck || showVolumeBar) && controlsHidden >= 0.99f }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val studioStatusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
        androidx.compose.runtime.SideEffect {
            studioParts.width = with(density) { screenWidth.toPx() }
            studioParts.height = with(density) { screenHeight.toPx() }
            studioParts.statusTop = with(density) { studioStatusTop.toPx() }
            studioParts.density = density
            studioParts.fullCover = shownCoverStyle == PlayerCoverStyle.EDGE_TO_EDGE
            studioParts.barStyle = barStyle
        }
        val progressProvider = remember(state) { { state.progress.coerceIn(0f, 1f) } }
        val horizontalOffsetProvider = remember(state) { { state.horizontalOffset } }

        // Vertical swipes on the expanded player, offered here before the sheet acts on them:
        //  - Around the controls (the title down to the card, never the cover), a swipe up pulls the
        //    card's page up out of it, the page following the finger. One finger-length to the top
        //    of the card is the whole way, so the card's edge stays under the finger.
        //  - With a page open (lyrics or queue), on the strip from the seek bar down, a swipe down
        //    pushes the page back into the card the same way, and a swipe up opens the page the
        //    card offers.
        //  - Only the top of the player closes it with a swipe down: the strip under the status bar
        //    (down to the cover's top), or the whole cover when it's the edge-to-edge or squared one. A swipe
        //    anywhere used to close it, and a slightly crooked swipe over the controls threw the
        //    whole player away.
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val currentPlayerStyle by androidx.compose.runtime.rememberUpdatedState(playerStyle)
        val currentCardShown by androidx.compose.runtime.rememberUpdatedState(showDeck)
        val currentCoverStyle by androidx.compose.runtime.rememberUpdatedState(shownCoverStyle)
        val closeStripBottom = with(density) { (statusBarTopInset + PlayerCloseStrip).toPx() }
        val currentCloseStripBottom by androidx.compose.runtime.rememberUpdatedState(closeStripBottom)
        val swipe = remember(transition, deck, morph, screenHeightPx, density) {
            val commitPx = with(density) { PageSwipeCommit.toPx() }
            val flingPx = with(density) { PageSwipeFling.toPx() }
            object : ExpandedSwipe {
                // Summed per gesture: one move is too small to tell a vertical swipe from a
                // horizontal one, and the start tells a new gesture from the one being summed.
                private var sumStart = Offset.Unspecified
                private var sum = Offset.Zero
                private var mode = PlayerSwipe.NONE
                private var travelled = 0f

                private fun distance() = (morph.cardRect?.top ?: (screenHeightPx * 0.85f)).coerceAtLeast(1f)

                /** The alternate styles and the editor draw and handle their own screen. */
                private fun owned() = currentPlayerStyle.flagshipFamily && !studio.engaged

                /** [start] (root) is on or below the seek bar, with the controls actually showing. */
                private fun inControlStrip(start: Offset): Boolean {
                    val top = seekBarTop[0]
                    return !top.isNaN() && controlsHidden < 0.5f && start.y >= top
                }

                /**
                 * [start] (root) is around the controls: from the title row down to the card, or
                 * anywhere under a cover that doesn't reach the title. Never on the cover itself.
                 */
                private fun aroundControls(start: Offset): Boolean {
                    if (controlsHidden >= 0.5f) return false
                    val controlsTop = rects.controls?.top ?: return false
                    val coverBottom = if (currentCoverStyle == PlayerCoverStyle.EDGE_TO_EDGE) null else rects.cover?.bottom
                    return start.y >= minOf(controlsTop, coverBottom ?: controlsTop)
                }

                override fun onStart(start: Offset, drag: Offset): Boolean {
                    if (!owned()) return false
                    if (start != sumStart) {
                        sumStart = start
                        sum = Offset.Zero
                    }
                    sum += drag
                    if (abs(sum.y) <= abs(sum.x)) return false

                    val up = sum.y < 0f
                    mode = when {
                        transition.page == PlayerPage.NONE ->
                            if (
                                up && aroundControls(start) &&
                                transition.beginDrag(if (currentCardShown) deck.focusedPage else PlayerPage.LYRICS)
                            ) {
                                PlayerSwipe.PULL
                            } else {
                                PlayerSwipe.NONE
                            }
                        inControlStrip(start) -> when {
                            up -> PlayerSwipe.OPEN_OTHER
                            transition.beginCloseDrag() -> PlayerSwipe.PUSH
                            else -> PlayerSwipe.BACK
                        }
                        else -> PlayerSwipe.NONE
                    }
                    if (mode == PlayerSwipe.NONE) return false
                    // What was summed before this move; the move itself arrives through onDrag.
                    travelled = sum.y - drag.y
                    return true
                }

                override fun onDrag(dy: Float) {
                    when (mode) {
                        PlayerSwipe.PULL, PlayerSwipe.PUSH -> transition.dragBy(-dy / distance())
                        else -> travelled += dy
                    }
                }

                override fun onEnd(velocityY: Float) {
                    val finished = mode
                    mode = PlayerSwipe.NONE
                    sumStart = Offset.Unspecified
                    sum = Offset.Zero
                    when (finished) {
                        PlayerSwipe.PULL, PlayerSwipe.PUSH -> transition.release(-velocityY / distance())
                        // 0 is a cancelled gesture: nothing to commit.
                        PlayerSwipe.OPEN_OTHER ->
                            if (velocityY != 0f && (travelled < -commitPx || velocityY < -flingPx)) {
                                transition.open(
                                    if (transition.page == PlayerPage.LYRICS) PlayerPage.QUEUE else PlayerPage.LYRICS
                                )
                            }
                        PlayerSwipe.BACK ->
                            if (velocityY != 0f && (travelled > commitPx || velocityY > flingPx)) transition.back()
                        PlayerSwipe.NONE -> Unit
                    }
                }

                override fun canCollapseFrom(start: Offset): Boolean {
                    if (!owned()) return true
                    val pageOpen = transition.page != PlayerPage.NONE
                    // down to the cover's top when there's room above it (with a page open the
                    // cover is the header's thumbnail, inside the strip anyway)
                    val coverTop = if (pageOpen) null else rects.cover?.top
                    val stripBottom = maxOf(currentCloseStripBottom, coverTop ?: 0f)
                    if (start.y < stripBottom) return true
                    if (pageOpen) return false
                    // chosen in Settings > Gesture: the whole player, as it was before the top-only
                    // close. Swipes up still open the card's page, sideways ones still skip.
                    if (currentSwipeDownAnywhere) return true
                    // the squared cover is a card you can pull down too, sideways on it still swipes songs
                    if (currentCoverStyle == PlayerCoverStyle.SQUARED) return rects.cover?.contains(start) == true
                    if (currentCoverStyle != PlayerCoverStyle.EDGE_TO_EDGE) return false
                    // the edge-to-edge cover is the player's top: all of it, down to the title
                    val controlsTop = rects.controls?.top ?: return false
                    return start.y < controlsTop
                }
            }
        }

        // A sideways swipe on the mini player slides the row of songs, its cover and title with it,
        // the same as a swipe on the open player's cover.
        val songCarousel = LocalSongCarousel.current
        val miniSwipe = remember(songCarousel, density) {
            val flingPx = with(density) { MiniSwipeFling.toPx() }
            songCarousel?.let { row ->
                object : com.mochyy.mochymusic.ui.component.CollapsedSwipe {
                    private var hasPrevious = false
                    private var hasNext = false

                    override fun onStart() {
                        hasPrevious = playerConnection.player.hasPreviousMediaItem()
                        hasNext = playerConnection.player.hasNextMediaItem()
                        row.startDrag()
                    }

                    override fun onDrag(travelledPx: Float, widthPx: Float) =
                        row.dragTo(travelledPx, widthPx, hasPrevious, hasNext)

                    override fun onEnd(travelledPx: Float, velocityPx: Float, widthPx: Float, cancelled: Boolean) {
                        if (cancelled) row.cancelDrag()
                        else row.release(travelledPx, velocityPx, widthPx, flingPx, playerConnection.player)
                    }
                }
            }
        }

        // what the round buttons do: the side button by the chevron and the chevron's menu, each
        // set to one of these in the editor
        val menuExtras = com.mochyy.mochymusic.ui.player.studio.rememberPlayerMenuExtras(
            playerConnection = playerConnection,
            openLyrics = { if (transition.page != PlayerPage.LYRICS) transition.open(PlayerPage.LYRICS) },
        )
        fun menuActionActive(action: com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction): Boolean = when (action) {
            com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE -> trackInfo.liked
            com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SLEEP_TIMER -> playerConnection.service.sleepTimer.isActive
            com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SEE_VIDEO -> seeVideo
            com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.REPEAT -> transportState.repeatMode != Player.REPEAT_MODE_OFF
            com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SHUFFLE -> transportState.shuffleModeEnabled
            else -> menuExtras.isActive(action)
        }
        val onMenuAction: (com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction) -> Unit = { action ->
            when (action) {
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE ->
                    likeBurst.likeFromButton(LikeSlot.PLAYER, trackInfo.liked, playerConnection::toggleLike)
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.OTHER_MENU -> {
                    // The lyrics tools are in this menu now (no button in the lyrics header).
                    menuFromLyrics = showLyrics
                    showActionMenu = true
                }
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SLEEP_TIMER -> showSleepTimer = true
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SEE_VIDEO -> seeVideo = !seeVideo
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.REPEAT -> playerConnection.player.toggleRepeatMode()
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SHUFFLE ->
                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LYRICS -> transition.open(PlayerPage.LYRICS)
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.QUEUE -> transition.open(PlayerPage.QUEUE)
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SPEED -> showPlaybackSpeed = true
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.OUTPUT -> showDeviceOutput = true
                com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.FULL_VIDEO -> enterVideoMode()
                else -> menuExtras.run(action)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // behind the player while it's shrunk into a card or a box, too
                .graphicsLayer { alpha = if (studio.engaged) 1f else menuReveal.floatValue }
                .background(Color.Black)
        )

        BottomSheet(
            state = state,

            modifier = modifier
                .graphicsLayer {

                    val r = menuReveal.floatValue
                    if (r > 0.001f) {
                        val scale = 1f - 0.08f * r
                        scaleX = scale
                        scaleY = scale
                        menuRevealShape.radius = 28.dp * r
                        shape = menuRevealShape
                        clip = true
                    }
                }
                .then(Modifier.studioSheetLayer(studio))
                .then(Modifier.studioCaptureSource(studioCapture)),
            // holding anywhere on the open flagship opens the studio, not just the cover and card.
            // Only attached while open: this box is full screen and sits above the app, so a
            // pointer handler on it took every touch meant for the app under the mini player
            expandedModifier = Modifier.holdAnywhere(
                enabled = {
                    state.isExpanded && currentPlayerStyle.flagshipFamily && !studio.engaged &&
                        transition.page == PlayerPage.NONE && !showExpandMenu && !showActionMenu
                },
                onHold = holdToStudio,
            ),

            isExpandable = !studioEngaged,
            isPillTransition = true,
            expandedSwipe = swipe,
            collapsedSwipe = miniSwipe,
            // the full video mode closes, opens its pane and scrolls by itself; the sheet read its swipes upright and fought its lists for them
            dragsWhenOpen = { currentPlayerStyle != PlayerStyle.VIDEO },
            onTouchActivity = handleState::onTouchActivity,
            pureBlack = pureBlack,
            background = {},
            sharedContent = {
                MorphingCover(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    trackInfo = trackInfo,
                    isPlaying = transportState.isPlaying,
                    playbackState = transportState.playbackState,
                    maxWidth = screenWidth,
                    maxHeight = screenHeight,
                    collapsedBound = state.collapsedBound,
                    pureBlack = pureBlack,
                    glassState = morphingGlassState,
                    lyricsProgressProvider = { lyricsProgress },
                    forceVideoBackground = seeVideo,
                    modifier = Modifier.fillMaxSize(),
                    coverStyle = shownCoverStyle,
                    backgroundStyle = backgroundStyle,
                    editMode = studioEngaged,
                    // the editor's top bar frosts what's under it, so the glass keeps capturing there
                    glassActiveInEdit = { studio.stage >= StudioStage.EDIT && studio.selector.value < 0.01f },
                    // another style draws the open player itself: none of the flagship runs behind it
                    alternateStyle = !playerStyle.flagshipFamily,
                    miniVideo = playerStyle == PlayerStyle.VIDEO,
                    coverOffsetProvider = layoutTweaks::controlsOffset,
                    onLongPressCover = holdToStudio,
                    studioArtModifier = if (playerStyle.flagshipFamily) {
                        Modifier
                            .then(Modifier.studioAnchor(studioAnchors) { it.cover })
                            .then(Modifier.studioCoverLayer(studioParts))
                    } else Modifier,
                    onDoubleTapCover = { position ->
                        if (!studioEngaged) {
                            likeBurst.likeFromDoubleTap(position, trackInfo.liked, playerConnection::toggleLike)
                        }
                    },
                    onArtBoundsChanged = { rects.cover = it },
                    onVideoShowingChange = { videoShowing = it },
                    ambient = if (isFlagship || backgroundStyle == PlayerBackgroundStyle.AMBIENT) ambient else null,
                    frostedCover = isFlagship,
                )
            },
            onDismiss = {
                playerConnection.service.clearAutomix()
                playerConnection.player.stop()
                playerConnection.player.clearMediaItems()
            },
            collapsedContent = {},
        ) {

            // Alternate styles replace the player outright rather than drawing over it, so none
            // of the default composition - blur, morphing cover, controls - runs while one is
            // active.
            if (!playerStyle.flagshipFamily) {
                // The alternate styles draw their own full screen, which meant they also swallowed
                // the player's own gestures - there was no way to close them or reach the editor.
                // Both live here so every style gets them for free.
                // only on the open player: collapsed into the pill, these swallowed the swipe up and
                // the tap that open it
                // also through the open spring's last stretch, which runs on after it looks open
                val styleOpen by remember(state) {
                    derivedStateOf { state.isExpanded || (state.isExpandedOrExpanding && state.progress > 0.9f) }
                }
                // what the style's own gestures ask when a touch lands, so a close beginning recomposes none of it
                val styleInteractive = remember(state) { { styleOpen } }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // not placed while the sheet is a pill: it's invisible there anyway, and placed it sat
                        // over the mini player and took the taps meant for its play and skip buttons
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (state.progress > StyleHiddenBelow) placeable.place(0, 0)
                            }
                        }
                        // the full video mode is turned sideways and has its own pull to close, so a sideways swipe on it
                        // (a vertical one to this) mustn't close the player
                        .pointerInput(styleOpen, playerStyle) {
                            if (!styleOpen || playerStyle == PlayerStyle.VIDEO) return@pointerInput
                            val closePx = 8.dp.toPx()
                            var pulled = 0f
                            var closing = false
                            detectVerticalDragGestures(
                                onDragStart = {
                                    pulled = 0f
                                    closing = false
                                },
                                onVerticalDrag = { _, amount ->
                                    // a swipe down anywhere on a style closes the player, the same
                                    // as on the flagship; the styles' own swipes are sideways. Summed
                                    // too: a fast screen splits a steady swipe into moves too small
                                    // to pass the per-move bar one at a time.
                                    pulled += amount
                                    if (!closing && (amount > 6f || pulled > closePx)) {
                                        closing = true
                                        state.collapseSoft()
                                    }
                                },
                            )
                        }
                        .pointerInput(styleOpen) {
                            if (!styleOpen) return@pointerInput
                            detectTapGestures(
                                onLongPress = { holdToStudio() },
                            )
                        },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive provides styleInteractive,
                    ) {
                        com.mochyy.mochymusic.ui.player.styles.AlternatePlayerStyle(
                            style = playerStyle,
                            actions = styleActions,
                            // under a lyrics page that fills the screen it needn't be drawn
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (styleCovered) 0f else 1f },
                        )
                    }
                }
                // closed with the player, so it isn't still up the next time it opens
                LaunchedEffect(styleOpen) { if (!styleOpen) styleLyricsOpen = false }
                com.mochyy.mochymusic.ui.player.styles.StyleLyricsPage(
                    open = styleLyricsOpen && styleOpen,
                    onClose = { styleLyricsOpen = false },
                    screenHeight = screenHeight,
                    onCoveredChange = { styleCovered = it },
                    onSwitchSource = { showLyricsSource = true },
                )
                // the ipod has its own chevron on its body and the full video mode its own close, a bar over them would just be in the way
                if (playerStyle != PlayerStyle.IPOD && playerStyle != PlayerStyle.VIDEO) PlayerHandle(
                    state = handleState,
                    sheet = state,
                    statusBarTop = statusBarTopInset,
                    enabled = { !studio.engaged },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                return@BottomSheet
            }

            // what the studio measures the flagship's parts against: the sheet itself, so the
            // sheet shrinking into a selector card doesn't move them
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { studioAnchors.root = it },
            )

            // the new flagship's cover fades into its own blurred copy instead, with only a light shade for the text; the Turntable's record isn't a cover to soften
            if (isFlagship) {
                if (shownCoverStyle == PlayerCoverStyle.EDGE_TO_EDGE) CoverFrost(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    maxHeight = screenHeight,
                    fadeProvider = { 1f - lyricsProgress },
                    offsetProvider = layoutTweaks::controlsOffset,
                )
                if (shownCoverStyle == PlayerCoverStyle.EDGE_TO_EDGE) ControlsShade(
                    progressProvider = progressProvider,
                    maxHeight = screenHeight,
                    fadeProvider = { 1f - lyricsProgress },
                    offsetProvider = layoutTweaks::controlsOffset,
                )
            } else if (!isTurntable) SeamBlur(
                glassState = morphingGlassState,
                progressProvider = progressProvider,
                trackInfo = trackInfo,
                maxHeight = screenHeight,

                fadeProvider = { 1f - lyricsProgress },
                offsetProvider = layoutTweaks::controlsOffset,
            )

            // The card growing into the page, under it: its glass widens to the full screen while
            // the player behind dims a touch. Only there while a page is growing or shrinking.
            if (pillMorph) FlagshipPillMorphSurface(transition = transition, morph = morph)
            else ExpandingCardSurface(transition = transition, morph = morph, look = cardLook)

            // Unmounted while the player is collapsed - even though the page itself is remembered -
            // because its lines keep per-frame clocks of their own that would otherwise tick away
            // behind Home. It mounts again with the first frame of the next open. (Keeping it
            // composed but paused measured worse: waking a page whose position had jumped cost
            // more in one frame than building it fresh.)
            if (lyricsPageMounted && sheetShowing) {
                LyricsScreen(
                    onClose = { transition.close() },
                    screenHeight = screenHeight,

                    contentBottomInset = lyricsBottomInset,
                    onImmersiveChange = { lyricsImmersive = it },
                    isSheetDragging = isSheetInTransition,
                    isMorphing = lyricsMorphing,
                    enterProgressProvider = { transition.cardMorphProgress(PlayerPage.LYRICS) },
                    morph = morph,
                    onSwitchSource = { showLyricsSource = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTransitionLayer(PlayerPage.LYRICS, transition, morph, pageLook) { lyricsPageProgress },
                )
            }

            if (queuePageMounted && sheetShowing) {
                QueueScreen(
                    onClose = { transition.close() },
                    // Fades into the seek bar like the lyrics; rows run under the chevron/shuffle/
                    // autoplay column, which reaches this far above the bar.
                    contentBottomInset = lyricsBottomInset,
                    controlsClearance = QueueButtonColumnHeight + 12.dp - SeekBarLift,
                    likeBurst = likeBurst,
                    onImmersiveChange = { queueImmersive = it },
                    enterProgressProvider = { transition.cardMorphProgress(PlayerPage.QUEUE) },
                    morph = morph,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTransitionLayer(PlayerPage.QUEUE, transition, morph, pageLook) { queuePageProgress },
                )
            }

            // the title flying between the player and the page header, drawn over the seam blur and the growing card so it never slides under them and pops back in
            MorphingSongInfo(
                trackInfo = trackInfo,
                lyricsProgressProvider = { lyricsProgress },
                sourceRectProvider = { songInfoSourceRect },
                sheetProgressProvider = progressProvider,

                targetY = statusBarTopInset + 38.dp,
                screenWidth = screenWidth,
                // the Turntable's title is set differently, so its header title fades in over it
                handOff = !isTurntable,
            )
            // Above the title after a like, moving with it into the lyrics/queue header.
            LikedLabel(
                likeCount = likeBurst.likeCount,
                mixing = playerConnection.isCrossmixing.collectAsState().value,
                sourceRectProvider = { songInfoSourceRect },
                headerTitleX = LyricsHeaderArtX + LyricsHeaderArtSize + 18.dp,
                headerTitleY = statusBarTopInset + 38.dp,
                lyricsProgressProvider = { lyricsProgress },
                sheetProgressProvider = progressProvider,
            )

            if ((showDeck || (barLifted && showPlayerBottomCard)) && deckMounted) {
                PlayerBottomCardStack(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    deck = deck,
                    transition = transition,
                    morph = morph,
                    onOpen = { transition.open(it) },
                    onLongPress = holdToStudio,
                    // in the bar's editor the card comes up whole, its body closed off at the bottom
                    closedProvider = { if (studioPieces.liftedPart == LiftedPart.BAR) studio.bar.value else 0f },
                    growthProvider = layoutTweaks::cardGrowth,
                    look = cardLook,

                    modifier = Modifier
                        .align(Alignment.BottomCenter)

                        // the new flagship's card sits further in, as its design has it
                        .padding(horizontal = if (isFlagship) 38.dp else 26.dp)
                        .then(Modifier.studioAnchor(studioAnchors) { it.card })
                        .then(Modifier.studioBarLayer(studioParts) { it.card })
                        .then(Modifier.studioPiece(studioPieces, StudioPieces.CARD_STACK))

                        .graphicsLayer {
                            alpha = 1f - controlsHidden
                            translationY = controlsHidden * size.height * 0.75f
                            letTouchesThroughWhenHidden()
                        }
                )
            }

            if ((showVolumeBar || barLifted) && deckMounted) {
                val volumeBarModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (isFlagship) flagshipBarFootprint() - FlagshipBarHeight
                        else playerVolumeBarFootprint() - PlayerVolumeBarHeight
                    )
                    .padding(horizontal = 26.dp)
                    .studioAnchor(studioAnchors) { it.volumeBar }
                    .studioBarLayer(studioParts) { it.volumeBar }
                    .studioPiece(studioPieces, StudioPieces.VOLUME_BAR)
                    .graphicsLayer {
                        alpha = 1f - controlsHidden
                        translationY = controlsHidden * size.height * 0.75f
                        letTouchesThroughWhenHidden()
                    }
                if (isFlagship) {
                    FlagshipBottomBar(
                        progressProvider = progressProvider,
                        isActive = ::menuActionActive,
                        onAction = onMenuAction,
                        // the page grows out of the button that was tapped, not out of a card that isn't there
                        onPageFrom = { morph.cardRect = it },
                        pages = transition,
                        modifier = volumeBarModifier,
                    )
                } else {
                    PlayerVolumeBar(
                        progressProvider = progressProvider,
                        isActive = ::menuActionActive,
                        onAction = onMenuAction,
                        modifier = volumeBarModifier,
                    )
                }
            }

            if (lyricsButtonMounted && !studioEngaged) {
                // Removed as per request
            }

            // The transport row sits centred between the seek bar and the card. The seek bar keeps
            // its place; only the gap is split evenly now - it used to be 30dp above the buttons
            // and about 70dp below, which put them visibly high.
            //
            // Measured against where the seek bar is DRAWN: with the edge-to-edge cover the title
            // and bar are drawn controlsDrawShift higher than they're laid out, and leaving that
            // out centred the buttons on the bar's layout spot - 64dp too low under the full cover.
            // The navigation bar is only read when there's no card to sit above: read always, it
            // recomposed the whole player through the bar's hide/show animation at the end of every
            // open and the start of every close, for a value the card layout never uses. (The
            // volume bar reads its size as if it were showing, which stays put through all that.)
            val belowControls = when {
                showVolumeBar && isFlagship -> flagshipBarFootprint()
                showVolumeBar -> playerVolumeBarFootprint()
                showPlayerBottomCard -> PlayerDeckHeight
                else -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            }
            val transportGap = (screenHeight * 0.19f - 37.dp - belowControls + TransportGapAboveRow + controlsDrawShift) / 2f
            // What's left of the gap above once the bar's own lift and the draw shift are counted.
            val transportSpacer = (transportGap - SeekBarLift - controlsDrawShift).coerceAtLeast(0.dp)
            // the buttons sit halfway between the seek bar and the bottom part: once the spacer above runs out they'd sit low, so they're lifted by half the difference
            val gapAboveButtons = transportSpacer + SeekBarLift + controlsDrawShift
            val gapBelowButtons = transportGap.coerceAtLeast(0.dp)
            val buttonsLift = (gapAboveButtons - gapBelowButtons) / 2f
            // how far the editor's hold may move the controls, and pull the card taller
            androidx.compose.runtime.SideEffect {
                layoutTweaks.setLimits(
                    roomAboveBar = (gapAboveButtons + gapBelowButtons) / 2f,
                    screenHeightPx = with(density) { screenHeight.toPx() },
                    density = density,
                    controlsMovable = !isTurntable,
                    cardGrowable = showDeck && !isTurntable,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = belowControls + transportGap.coerceAtLeast(0.dp))
                    // where the editor's hold put them; only placed, so a drag never recomposes
                    .offset { androidx.compose.ui.unit.IntOffset(0, layoutTweaks.controlsOffset().roundToInt()) }
                    .graphicsLayer {
                        // the Turntable shows its own controls; these (with the seek bar) come up
                        // only as the lyrics or the queue open, which run off the seek bar
                        alpha = (1f - controlsHidden) * (if (isTurntable) turntablePageProgress else 1f)

                        translationY = controlsHidden * size.height * 0.55f
                        // Hidden over the lyrics, or behind the Turntable's own controls, they let
                        // touches through. A layer that ate every touch used to sit here, and it
                        // blocked the lyrics, the record and swipes along with the hidden buttons.
                        letTouchesThroughWhenHidden()
                    }

                    .onGloballyPositioned { coords ->
                        if (coords.parentLayoutCoordinates == null) return@onGloballyPositioned

                        val bounds = coords.boundsInRoot()
                        rects.controls = androidx.compose.ui.geometry.Rect(
                            left = bounds.left,
                            top = bounds.top - with(density) { controlsDrawShift.toPx() },
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
            ) {
                Column(modifier = Modifier.offset(y = -controlsDrawShift)) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .studioAnchor(studioAnchors) { it.songInfo }
                            .studioMiddleLayer(studioParts) { it.songInfo }
                            .graphicsLayer {
                                // Its parts leave on their own as a page opens: the title moves up
                                // into the header, the buttons into the page's column. Once the page
                                // is up the row is hidden outright, since it sits unseen over the
                                // bottom of the lyrics and its title and buttons there were opening
                                // song details and liking.
                                alpha = if (lyricsProgress >= 0.999f) 0f else 1f
                                letTouchesThroughWhenHidden()
                            }
                    ) {
                        SongInfoRow(
                            // Only while the sheet is up: during a slide the title's root position
                            // moves every frame, and the header morph that reads it recomposed with
                            // it. Its place on the sheet itself doesn't change.
                            onTitlePositioned = { if (state.isExpanded && !isTurntable) songInfoSourceRect = it },
                            onChevronPositioned = { if (!isTurntable) chevronButtonRect = it },
                            isExpandMenuOpen = showExpandMenu,
                            hideChevron = lyricsMounted,
                            onToggleExpandMenu = { showExpandMenu = !showExpandMenu },
                            likeBurst = likeBurst,
                            sideAction = menuLayout.side,
                            sideActive = menuActionActive(menuLayout.side),
                            onSideAction = onMenuAction,
                            // measured only on the plain player: in the editor they're moving with it
                            onSidePlaced = { if (!studio.editing) studioAnchors.capture(studioAnchors.sideButton, it) },
                            onChevronPlaced = { if (!studio.editing) studioAnchors.capture(studioAnchors.chevron, it) },
                            sideModifier = Modifier.studioPiece(studioPieces, com.mochyy.mochymusic.ui.player.studio.StudioPieces.SIDE),
                            chevronModifier = Modifier.studioPiece(studioPieces, com.mochyy.mochymusic.ui.player.studio.StudioPieces.CHEVRON),
                            onSongInfoClick = { if (!studioEngaged) showSongDetails = true },
                            onStreamInfoClick = { if (!studioEngaged) showStreamInfo = true },
                            // the header's title is this one moving, so it hides as that takes over
                            // (or fades, with no measured spot for the header's to start from)
                            infoAlpha = {
                                when {
                                    lyricsProgress <= 0f -> 1f
                                    songInfoSourceRect != null -> 0f
                                    else -> 1f - lyricsProgress
                                }
                            },
                            badgeAlpha = { (1f - lyricsProgress / 0.3f).coerceIn(0f, 1f) },
                            onSidePositioned = { if (state.isExpanded && lyricsProgress <= 0f) sideButtonRect = it },
                            sideLayer = {
                                val lp = lyricsProgress
                                if (lp <= 0f) return@SongInfoRow
                                // the lyrics column's like is this very button, moved
                                if (likeMovesToLyrics()) {
                                    alpha = 0f
                                    return@SongInfoRow
                                }
                                // anything else tucks in behind the chevron as it flies to the page
                                val t = smoothstepF(lp / 0.5f)
                                val side = sideButtonRect
                                val chevron = flyingChevronCenter()
                                if (side != null && chevron != null) {
                                    translationX = (chevron.x - side.center.x) * t
                                    translationY = (chevron.y - side.center.y) * t
                                }
                                val scale = 1f - 0.3f * t
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - t
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chevron + like live here while the lyrics are up: the song info row they
                    // normally sit in is faded out, so they stack above the progress bar instead.
                    // The queue page swaps like (moved to its header) for shuffle and autoplay.
                    // Height tracks the transitions so the controls don't jump when a page opens.
                    if (lyricsMounted) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                                // Read while measuring, not composing: the slot opens with the
                                // transition without recomposing the player on every frame.
                                .bottomAnchoredSlot(
                                    height = { (75.dp + QueueButtonExtraHeight * queuePageProgress) * lyricsProgress },
                                    bottomGap = { 12.dp * lyricsProgress },
                                ),
                            horizontalAlignment = Alignment.End,
                        ) {
                            // The same chevron as in the song info row, not a second one fading
                            // in: it glides from that chevron's spot to this one with the lyrics
                            // transition (the row's own chevron is hidden while this is mounted,
                            // so there's never two of them on screen). Before, this one sat still
                            // at its final spot while the other faded out, which read as a jump.
                            PressScaleActionButton(
                                icon = R.drawable.expand_less,
                                tint = Color.White.copy(alpha = 0.90f),
                                containerColor = Color.White.copy(alpha = 0.15f),
                                onClick = { showExpandMenu = !showExpandMenu },
                                hasShadow = false,
                                modifier = Modifier
                                    // over the buttons that open out from behind it
                                    .zIndex(1f)
                                    .onGloballyPositioned { coords ->
                                        lyricsChevronRect = coords.boundsInRoot()
                                    }
                                    .graphicsLayer {
                                        val from = chevronButtonRect
                                        val to = lyricsChevronRect
                                        if (from != null && to != null) {
                                            val k = 1f - lyricsProgress
                                            translationX = (from.center.x - to.center.x) * k
                                            translationY = (from.center.y - to.center.y) * k
                                        } else {
                                            alpha = lyricsProgress
                                        }
                                        // The overlay draws its own (rotated) chevron here while open.
                                        if (showExpandMenu) alpha = 0f
                                    }
                            )
                            Spacer(modifier = Modifier.height(7.dp))
                            // Like (lyrics) and shuffle (queue) share a slot. Each is only composed
                            // while its page is, so an invisible one never takes the other's taps.
                            // Switching pages, one tucks back behind the chevron as the other opens
                            // out from it.
                            Box {
                                if (lyricsPageMounted) {
                                    PressScaleActionButton(
                                        icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                                        tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                                        onClick = {
                                            likeBurst.likeFromButton(LikeSlot.LYRICS, trackInfo.liked, playerConnection::toggleLike)
                                        },
                                        hasShadow = false,
                                        modifier = Modifier
                                            .likeButtonTarget(likeBurst, LikeSlot.LYRICS)
                                            .graphicsLayer {
                                                if (likeMovesToLyrics()) {
                                                    // the player's own like, moved here
                                                    val side = sideButtonRect
                                                    val chevron = lyricsChevronRect
                                                    if (side != null && chevron != null) {
                                                        val k = 1f - lyricsProgress
                                                        translationX = (side.center.x - chevron.center.x) * k
                                                        translationY = (side.center.y - (chevron.center.y + PageButtonStep.toPx())) * k
                                                    } else {
                                                        alpha = lyricsPageProgress
                                                    }
                                                } else {
                                                    openOutFromChevron(lyricsPageProgress, PageButtonStep.toPx())
                                                }
                                            },
                                    )
                                }
                                if (queuePageMounted) {
                                    val shuffleOn = transportState.shuffleModeEnabled
                                    PressScaleActionButton(
                                        icon = R.drawable.shuffle,
                                        tint = if (shuffleOn) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (shuffleOn) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleOn },
                                        hasShadow = false,
                                        modifier = Modifier.graphicsLayer {
                                            openOutFromChevron(queuePageProgress, PageButtonStep.toPx())
                                        },
                                    )
                                }
                            }
                            // Autoplay grows in underneath: the slot opens from nothing, so shuffle
                            // rises off it rather than everything jumping when it mounts.
                            if (queuePageMounted) {
                                Box(
                                    contentAlignment = Alignment.BottomEnd,
                                    modifier = Modifier.bottomAnchoredSlot(
                                        height = { QueueButtonExtraHeight * queuePageProgress },
                                    ),
                                ) {
                                    PressScaleActionButton(
                                        icon = R.drawable.all_inclusive,
                                        tint = if (infiniteQueue) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (infiniteQueue) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                                        onClick = { playerConnection.setInfiniteQueueEnabled(!infiniteQueue) },
                                        hasShadow = false,
                                        // its slot grows as it opens, so it starts further down
                                        modifier = Modifier.graphicsLayer {
                                            openOutFromChevron(
                                                queuePageProgress,
                                                (PageButtonStep + QueueButtonExtraHeight * queuePageProgress).toPx(),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Column, not Box: PlayerProgressSlider emits the track and the timestamp
                    // row as two siblings, so a Box would stack the timestamps on top of the
                    // seek bar instead of below it.
                    Column(
                        modifier = Modifier
                            .offset(y = -SeekBarLift)
                            .onGloballyPositioned { coords ->
                                // Measured to the TOP of the seek bar: the lyrics list ends there and
                                // its fade finishes above it. Measured to the bottom, lines kept
                                // scrolling behind the bar and the timestamps.
                                //
                                // Only with the sheet up and the controls in place. In root
                                // coordinates this moved on every frame of the sheet's slide (and of
                                // the controls hiding), and the pages that read it recomposed - and
                                // re-laid out their lists - frame by frame for it.
                                if (!state.isExpanded || controlsHidden > 0.001f) return@onGloballyPositioned
                                val rootHeight = coords.findRootCoordinates().size.height
                                val topY = coords.positionInRoot().y
                                seekBarTop[0] = topY
                                val insetPx = (rootHeight - topY).coerceAtLeast(0f)
                                val inset = with(density) { insetPx.toDp() }
                                if ((inset - lyricsBottomInset).value.absoluteValue > 0.5f) {
                                    lyricsBottomInset = inset
                                }
                            }
                            .studioAnchor(studioAnchors) { it.slider }
                            .studioMiddleLayer(studioParts) { it.slider }
                    ) {
                        PlayerProgressSlider(
                            active = sheetShowing,
                            onSeekBarPlaced = { if (!studio.editing) studioAnchors.capture(studioAnchors.seekBar, it) },
                            onEndTimePlaced = { if (!studio.editing) studioAnchors.capture(studioAnchors.endTime, it) },
                            seekBarModifier = Modifier.studioPiece(studioPieces, com.mochyy.mochymusic.ui.player.studio.StudioPieces.SEEK_BAR),
                            endTimeModifier = Modifier.studioPiece(studioPieces, com.mochyy.mochymusic.ui.player.studio.StudioPieces.END_TIME),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(transportSpacer))

                val transportModifier = Modifier
                    // the editor moves the seek bar; the buttons follow it halfway, and meet a taller card halfway, so they stay in the middle
                    .offset {
                        val follow = (layoutTweaks.controlsOffset() + layoutTweaks.cardGrowth()) / 2f
                        androidx.compose.ui.unit.IntOffset(0, (-buttonsLift.toPx() - follow).roundToInt())
                    }
                    .studioAnchor(studioAnchors) { it.transport }
                    .studioTransportLayer(studioParts)
                    .studioPiece(studioPieces, StudioPieces.TRANSPORT)
                if (isFlagship) {
                    FlagshipTransportRow(layout = transportLayout, modifier = transportModifier)
                } else {
                    PlayerTransportRow(modifier = transportModifier)
                }
            }

            // The Turntable's own controls, as its frame lays them out, above the card. They give
            // way to the flagship's (with the seek bar) while a page is up.
            if (isTurntable) {
                TurntableControls(
                    isExpandMenuOpen = showExpandMenu,
                    onToggleExpandMenu = { showExpandMenu = !showExpandMenu },
                    hideChevron = lyricsMounted,
                    onTitlePositioned = { if (state.isExpanded) songInfoSourceRect = it },
                    onChevronPositioned = { chevronButtonRect = it },
                    likeBurst = likeBurst,
                    onSongInfoClick = { if (!studioEngaged) showSongDetails = true },
                    onStreamInfoClick = { if (!studioEngaged) showStreamInfo = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = belowControls + TurntableControlsLift)
                        .graphicsLayer {
                            alpha = (1f - controlsHidden) * (1f - turntablePageProgress)
                            translationY = controlsHidden * size.height * 0.55f
                            // gone with a page up, so the flagship's controls under them answer
                            letTouchesThroughWhenHidden()
                        },
                )
            }

            // the editor's tiles: inside the sheet, so they shrink and grow with it
            EditTiles(studioParts)

            // over everything on the player, pages included
            PlayerHandle(
                state = handleState,
                sheet = state,
                statusBarTop = statusBarTopInset,
                enabled = { !studio.engaged },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Above the player and its controls, so a heart can fly over everything on its way home.
        LikeBurstOverlay(state = likeBurst)

        ExpandMenuOverlay(
            visible = showExpandMenu,
            onDismiss = { showExpandMenu = false },
            // the Turntable's pills have no shuffle or repeat, so its menu adds them on top of the
            // four set in the editor (unless one of those is already there)
            slots = run {
                val set = if (isTurntable) {
                    listOf(
                        com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SHUFFLE,
                        com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.REPEAT,
                    ).filterNot { it in menuLayout.slots } + menuLayout.slots
                } else {
                    menuLayout.slots
                }
                // with a youtube video on the cover, the full video mode goes on top unless it's already one of the four
                val fullVideo = com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.FULL_VIDEO
                if (videoShowing && fullVideo !in set) listOf(fullVideo) + set else set
            },
            isActive = ::menuActionActive,
            onAction = onMenuAction,
            repeatMode = transportState.repeatMode,
            // a lambda, so the chevron moving with the sheet doesn't recompose the whole player
            getChevronRect = { if (showLyrics || showQueue) lyricsChevronRect ?: chevronButtonRect else chevronButtonRect },
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            controlsDrawShift = controlsDrawShift,
        )

        // Pulling down at the top of the queue list stretches the list instead of closing the
        // player. (Lyrics keep the pull-to-close.)
        // the full video mode's pane is a side panel turned sideways: past its top it only stretches, the player stays open
        androidx.compose.runtime.DisposableEffect(state, showQueue, playerStyle) {
            state.collapseOnNestedOverscroll = !showQueue && playerStyle != PlayerStyle.VIDEO
            onDispose { state.collapseOnNestedOverscroll = true }
        }

        // Composed only while a page is open on the expanded player, so it registers after -
        // and wins over - the sheet's own back handler: back shrinks the page into its card
        // first, then collapses the player. Not while collapsed: the page is remembered then,
        // and back belongs to the app.
        PageBackHandler(state, pageOpen = showQueue || showLyrics) { transition.close() }

        if (showSleepTimer) {
            com.mochyy.mochymusic.ui.player.menu.SleepTimerSheet(
                onDismiss = { showSleepTimer = false }
            )
        }

        if (showSongDetails) {
            // Opening an artist or the album leaves the player: it closes on its way to the page,
            // the way the song menus' "view artist" does
            com.mochyy.mochymusic.ui.player.menu.SongDetailsSheet(
                onDismiss = { showSongDetails = false },
                onOpenArtist = { artistId ->
                    showSongDetails = false
                    navController.navigate("artist/$artistId")
                    state.collapseSoft()
                },
                onOpenAlbum = { albumId ->
                    showSongDetails = false
                    navController.navigate("album/$albumId")
                    state.collapseSoft()
                },
            )
        }

        if (showStreamInfo) {
            com.mochyy.mochymusic.ui.player.info.StreamInfoSheet(onDismiss = { showStreamInfo = false })
        }

        if (showActionMenu) {
            PlayerActionMenu(
                fromLyrics = menuFromLyrics,
                onFullVideo = enterVideoMode,
                onDismiss = {
                    showActionMenu = false
                    menuReveal.floatValue = 0f
                },
                onEditPlayer = {
                    // Same destination as a long press: the selector, the player shrinking into
                    // its card. Jumping straight into the editor skipped the choice of style.
                    openStudio()
                },
                onReveal = { menuReveal.floatValue = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showPlaybackSpeed) {
            com.mochyy.mochymusic.ui.player.menu.PlaybackSpeedSheet(onDismiss = { showPlaybackSpeed = false })
        }
        if (showDeviceOutput) {
            com.mochyy.mochymusic.ui.player.menu.DeviceOutputSheet(onDismiss = { showDeviceOutput = false })
        }
        // opened by "Switch" at the end of the lyrics page
        if (showLyricsSource) {
            com.mochyy.mochymusic.ui.player.menu.LyricsProviderSheet(onDismiss = { showLyricsSource = false })
        }

        // the player studio, over the player: the selector, the editor's header and editors, its sheets
        PlayerSelectorOverlay(
            studio = studio,
            styles = playerStyles,
            onSet = { index, velocity ->
                val style = playerStyles[index]
                val restyled = style != playerStyle
                if (restyled) onPlayerStyleChange(style)
                studio.settleOn(index, restyled, velocity = velocity)
            },
            onEdit = { focused ->
                if (playerStyles.getOrNull(focused) == PlayerStyle.POSTER) {
                    // its one edit is its visualizer: back to the player, with the poster's sheet
                    val restyled = playerStyle != PlayerStyle.POSTER
                    if (restyled) onPlayerStyleChange(PlayerStyle.POSTER)
                    studio.settleOn(focused, restyled) {
                        studio.openSheet(com.mochyy.mochymusic.ui.player.studio.StudioSheet.POSTER)
                    }
                } else if (playerStyles.getOrNull(focused) == PlayerStyle.IPOD) {
                    // the ipod's colours, wheel, screen and shadow, in a sheet over the ipod itself
                    val restyled = playerStyle != PlayerStyle.IPOD
                    if (restyled) onPlayerStyleChange(PlayerStyle.IPOD)
                    studio.settleOn(focused, restyled) {
                        studio.openSheet(com.mochyy.mochymusic.ui.player.studio.StudioSheet.IPOD)
                    }
                } else if (playerStyles.getOrNull(focused) == PlayerStyle.TURNTABLE) {
                    // its one edit is the disc label: back to the player, with the label's sheet
                    val restyled = playerStyle != PlayerStyle.TURNTABLE
                    if (restyled) onPlayerStyleChange(PlayerStyle.TURNTABLE)
                    studio.settleOn(focused, restyled) {
                        studio.openSheet(com.mochyy.mochymusic.ui.player.studio.StudioSheet.DISC_LABEL)
                    }
                } else {
                    // the full editor, on whichever flagship is in view
                    val style = playerStyles.getOrNull(focused)?.takeIf { it.hasFullEditor } ?: PlayerStyle.FLAGSHIP
                    if (playerStyle != style) onPlayerStyleChange(style)
                    studio.livePage = playerStyles.indexOf(style)
                    studio.go(StudioStage.EDIT)
                }
            },
        )
        // the Poster's sheet, over the poster itself
        if (playerStyle == PlayerStyle.POSTER || playerStyle == PlayerStyle.IPOD) {
            com.mochyy.mochymusic.ui.player.studio.StyleSheets(studio)
        }
        if (playerStyle.flagshipFamily) {
            val (showBigDiscStyles) = rememberPreference(com.mochyy.mochymusic.constants.ShowBigDiscStylesKey, defaultValue = false)
            val coverStyles = remember(showBigDiscStyles, coverStyle) {
                PlayerCoverStyle.entries.filter { style ->
                    style != PlayerCoverStyle.TURNTABLE
                }.filter { style ->
                    showBigDiscStyles || !(style == PlayerCoverStyle.DISC_BIG_FULL || style == PlayerCoverStyle.DISC_BIG_LABEL) || style == coverStyle
                }
            }
            val backgroundStyles = remember { PlayerBackgroundStyle.entries.toList() }
            var seekBarStyle by rememberEnumPreference(
                com.mochyy.mochymusic.constants.SeekBarStyleKey,
                com.mochyy.mochymusic.ui.component.SeekBarStyle.DEFAULT,
            )
            var buttonStyle by rememberEnumPreference(
                com.mochyy.mochymusic.constants.ButtonStyleKey,
                com.mochyy.mochymusic.ui.component.ButtonStyle.DEFAULT,
            )
            val endTimestamp by rememberEnumPreference(
                com.mochyy.mochymusic.constants.PlayerEndTimestampKey,
                com.mochyy.mochymusic.constants.PlayerEndTimestamp.END_TIME,
            )
            val artworkPalette = com.mochyy.mochymusic.ui.theme.ArtworkPalette.from(com.mochyy.mochymusic.LocalArtworkColor.current)
            if (playerStyle.hasFullEditor) StudioEditOverlay(
                parts = studioParts,
                glassState = morphingGlassState,
                capture = studioCapture,
                coverStyles = coverStyles,
                coverStyle = coverStyle,
                onCoverStyle = { coverStyle = it },
                backgroundStyles = backgroundStyles,
                backgroundStyle = backgroundStyle,
                onBackgroundStyle = { if (isFlagship) flagshipBackground = it else classicBackground = it },
                menuLayout = menuLayout,
                seekBarStyle = seekBarStyle,
                endTimestamp = endTimestamp,
                barStyles = PlayerBottomBarStyle.entries,
                barStyle = barStyle,
                onBarStyle = onBarStyle,
                isMenuActionActive = ::menuActionActive,
            )
            StudioSheets(
                studio = studio,
                coverStyle = shownCoverStyle,
                buttonStyle = buttonStyle,
                onButtonStyle = { buttonStyle = it },
                seekBarStyle = seekBarStyle,
                onSeekBarStyle = { seekBarStyle = it },
                menuLayout = menuLayout,
                // the new flagship picks its button layout there instead of a button style
                transportLayout = if (isFlagship) transportLayout else null,
                onTransportLayout = { transportLayout = it },
                transportColors = com.mochyy.mochymusic.ui.component.TransportColors(
                    accent = artworkPalette.accent,
                    onAccent = artworkPalette.onAccent,
                    container = artworkPalette.container,
                    onContainer = artworkPalette.onContainer,
                ),
            )
        }
    }
}

/** Transparent margin around each expand-menu item's blur layer - roughly the max blur radius. */
private val MenuItemBlurRoom = 20.dp

/**
 * Grows the layer that follows by [room] on every side without changing the layout footprint:
 * pair with `.padding(room)` after the layer. Lets a blur render past the content's edges
 * instead of being cut off at them.
 */
private fun Modifier.blurBleedRoom(room: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val pad = room.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * pad, vertical = 2 * pad))
        val width = constraints.constrainWidth(placeable.width - 2 * pad)
        val height = constraints.constrainHeight(placeable.height - 2 * pad)
        layout(width, height) { placeable.place(-pad, -pad) }
    }


// where the cover and the controls sit on screen (root px), for the swipe handler
private class PlayerRects {
    var cover: androidx.compose.ui.geometry.Rect? = null
    var controls: androidx.compose.ui.geometry.Rect? = null
}

// runs the block each time the sheet starts heading down, read here so the flip doesn't recompose the player
@Composable
private fun OnSheetHeadingDown(state: BottomSheetState, onHeadingDown: () -> Unit) {
    val currentOnHeadingDown by androidx.compose.runtime.rememberUpdatedState(onHeadingDown)
    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding) currentOnHeadingDown()
    }
}

// the back press belongs to the open page while it is up, read here so the flip doesn't recompose the player
@Composable
private fun PageBackHandler(state: BottomSheetState, pageOpen: Boolean, onBack: () -> Unit) {
    if (pageOpen && state.isExpandedOrExpanding) BackHandler(onBack = onBack)
}

@Composable
private fun ExpandMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    /** the actions, top to bottom: the four set in the editor, and any the style adds */
    slots: List<com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction>,
    isActive: (com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction) -> Boolean,
    onAction: (com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction) -> Unit,
    repeatMode: Int,
    getChevronRect: () -> androidx.compose.ui.geometry.Rect?,
    screenWidth: androidx.compose.ui.unit.Dp,
    screenHeight: androidx.compose.ui.unit.Dp,
    controlsDrawShift: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val menuItems = remember(slots) { slots.map { it.label to it.icon } }

    val density = LocalDensity.current
    // one per item, so a style that adds items gets its own set instead of reading past the end
    val itemProgress = remember(menuItems.size) { List(menuItems.size) { androidx.compose.animation.core.Animatable(0f) } }
    val bgAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    var isRendered by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isRendered = true
            launch {
                bgAlpha.animateTo(
                    targetValue = 0.62f,
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                )
            }
            itemProgress.forEachIndexed { index, anim ->
                launch {
                    kotlinx.coroutines.delay((menuItems.size - 1 - index) * 40L)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.54f,
                            stiffness = 320f
                        )
                    )
                }
            }
        } else if (isRendered) {
            val bgJob = launch {
                bgAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                )
            }
            val itemsJob = launch {
                val exitJobs = itemProgress.mapIndexed { index, anim ->
                    launch {
                        kotlinx.coroutines.delay(index * 25L)
                        anim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                    }
                }
                exitJobs.forEach { it.join() }
            }
            bgJob.join()
            itemsJob.join()
            isRendered = false
        }
    }

    if (isRendered) {
        val chevronRect = getChevronRect()
        val chevronBottomDp = if (chevronRect != null) {
            val screenHeightPx = with(density) { screenHeight.toPx() }
            val bottomPx = (screenHeightPx - chevronRect.bottom).coerceAtLeast(0f)
            with(density) { bottomPx.toDp() }
        } else {
            (screenHeight * 0.19f) + 93.dp - controlsDrawShift
        }

        val menuBottomDp = if (chevronRect != null) {
            val screenHeightPx = with(density) { screenHeight.toPx() }
            val topPx = (screenHeightPx - chevronRect.top).coerceAtLeast(0f)
            with(density) { (topPx + 14.dp.toPx()).toDp() }
        } else {
            chevronBottomDp + 48.dp
        }

        // A chevron on the screen's left (the Turntable's) opens the menu the other way round:
        // the buttons up from it on its own line, their names to their right.
        val fromLeft = chevronRect != null && chevronRect.center.x < with(density) { screenWidth.toPx() } / 2f
        val chevronStartDp = with(density) { (chevronRect?.left ?: 0f).toDp() }
        val chevronSizeDp = with(density) { (chevronRect?.width ?: 0f).toDp() }.coerceAtLeast(34.dp)

        // Right edge of the chevron actually tapped, not an assumed padding - the lyrics-page
        // chevron and the song-info one don't share an exact x.
        val chevronEndDp = if (chevronRect != null) {
            val screenWidthPx = with(density) { screenWidth.toPx() }
            with(density) { (screenWidthPx - chevronRect.right).coerceAtLeast(0f).toDp() }
        } else {
            PlayerHorizontalPadding
        }

        val chevronRotation by animateFloatAsState(
            targetValue = if (visible) 180f else 0f,
            label = "overlayChevronRot",
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f)
        )
        val chevronScale by animateFloatAsState(
            targetValue = if (visible) 0.86f else 1f,
            label = "overlayChevronScale",
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f)
        )
        val overlayChevronBgColor by animateColorAsState(
            targetValue = if (visible) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
            label = "overlayChevronBgColor",
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
        val overlayChevronTint by animateColorAsState(
            targetValue = if (visible) Color.White else Color.White.copy(alpha = 0.90f),
            label = "overlayChevronTint",
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha.value))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            // Menu items placed directly ON TOP of the chevron:
            Column(
                modifier = modifier
                    .then(
                        if (fromLeft) {
                            // the 34dp buttons centred over the chevron
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = chevronStartDp + (chevronSizeDp - 34.dp) / 2, bottom = menuBottomDp)
                        } else {
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = chevronEndDp, bottom = menuBottomDp)
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = if (fromLeft) Alignment.Start else Alignment.End
            ) {
                menuItems.forEachIndexed { index, item ->
                    val p = itemProgress[index].value
                    if (p > 0.005f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                // The blur's layer used to end exactly at the row's edges, with
                                // the button circle and text shadow touching them, and CLAMP
                                // tiling smeared those edge pixels outward - the stray lines at
                                // the sides. Give it room to fade out, and let it fade to clear.
                                .blurBleedRoom(MenuItemBlurRoom)
                                .graphicsLayer {
                                    val progress = p.coerceIn(0f, 1.25f)
                                    alpha = progress.coerceIn(0f, 1f)
                                    translationY = (1f - progress) * 55f
                                    val scale = (0.7f + 0.3f * progress).coerceAtLeast(0.01f)
                                    scaleX = scale
                                    scaleY = scale
                                    val blurPx = (1f - progress.coerceIn(0f, 1f)) * 20f
                                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        // DECAL only exists on API 31+; keep the reference inside
                                        // the check so older devices never resolve it.
                                        BlurEffectCache.element(blurPx, android.graphics.Shader.TileMode.DECAL)
                                    } else {
                                        null
                                    }
                                }
                                .padding(MenuItemBlurRoom)
                        ) {
                            val label: @Composable () -> Unit = {
                            androidx.compose.material3.Text(
                                text = item.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                modifier = Modifier.padding(start = if (fromLeft) 4.dp else 0.dp, end = if (fromLeft) 0.dp else 4.dp)
                            )
                            }

                            val action = slots[index]
                            val isToggled = isActive(action)
                            val iconTint = if (isToggled) Color.White else Color.White.copy(alpha = 0.85f)
                            val containerColor = if (isToggled) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f)

                            if (!fromLeft) label()
                            PressScaleActionButton(
                                icon = item.second,
                                tint = iconTint,
                                containerColor = containerColor,
                                hasShadow = false,
                                badgeText = if (action == com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.REPEAT &&
                                    repeatMode == Player.REPEAT_MODE_ONE
                                ) "1" else null,
                                onClick = {
                                    when (action) {
                                        // toggles that stay open, so the change shows on the button
                                        com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.REPEAT,
                                        com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.SHUFFLE,
                                        com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction.LIKE -> onAction(action)
                                        else -> { onDismiss(); onAction(action) }
                                    }
                                }
                            )
                            if (fromLeft) label()
                        }
                    }
                }
            }

            // Chevron rendered ON TOP of the dim background, directly tappable, zoomed out & rotated 180°:
            Box(
                modifier = Modifier
                    .then(
                        if (fromLeft) {
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = chevronStartDp, bottom = chevronBottomDp)
                        } else {
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = chevronEndDp, bottom = chevronBottomDp)
                        }
                    )
            ) {
                PressScaleActionButton(
                    icon = R.drawable.expand_less,
                    tint = overlayChevronTint,
                    containerColor = overlayChevronBgColor,
                    onClick = onDismiss,
                    hasShadow = false,
                    // over the Turntable's chevron exactly, its own size
                    size = if (fromLeft) chevronSizeDp else 34.dp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = chevronRotation
                        scaleX = chevronScale
                        scaleY = chevronScale
                    }
                )
            }
        }
    }
}
