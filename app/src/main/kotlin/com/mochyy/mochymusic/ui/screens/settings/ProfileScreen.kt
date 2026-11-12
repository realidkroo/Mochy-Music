// ProfileScreen.kt
//
// The Mochy page. A dark header holds the profile ID card over a dot grid, with a giant MOCHY
// column sliding up the right edge. Pull the header down and it fills the screen, the card flips to
// a scratch-off barcode, and scratching it opens the month's recap (ui/screens/recap) - which hands
// back to this header when it's done. Below: listening time (into Stats), version, settings.

package com.mochyy.mochymusic.ui.screens.settings

import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.rememberDeviceTilt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.InnerTubeCookieKey
import com.mochyy.mochymusic.constants.ProfileCardNumberKey
import com.mochyy.mochymusic.constants.ProfileJoinedEpochDayKey
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.constants.UsernameKey
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.HomeContentInset
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.OdometerNumber
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.screens.profile.ProfileMorphHost
import com.mochyy.mochymusic.ui.screens.profile.rememberProfileMorphState
import com.mochyy.mochymusic.ui.screens.profile.zoomsAwayFor
import com.mochyy.mochymusic.ui.screens.recap.DateLong
import com.mochyy.mochymusic.ui.screens.recap.Emphasized
import com.mochyy.mochymusic.ui.screens.recap.GiantTextState
import com.mochyy.mochymusic.ui.screens.recap.HeaderDots
import com.mochyy.mochymusic.ui.screens.recap.HeaderGrey
import com.mochyy.mochymusic.ui.screens.recap.HeaderText
import com.mochyy.mochymusic.ui.screens.recap.IdCardBack
import com.mochyy.mochymusic.ui.screens.recap.RecapAnchors
import com.mochyy.mochymusic.ui.screens.recap.RecapAvailability
import com.mochyy.mochymusic.ui.screens.recap.RecapRepository
import com.mochyy.mochymusic.ui.screens.recap.RecapStoryData
import com.mochyy.mochymusic.ui.screens.recap.RecapStoryHost
import com.mochyy.mochymusic.ui.screens.recap.ScanVerdict
import com.mochyy.mochymusic.ui.screens.recap.ScratchState
import com.mochyy.mochymusic.ui.screens.recap.StoryDark
import com.mochyy.mochymusic.ui.screens.recap.StoryDots
import com.mochyy.mochymusic.ui.screens.recap.StoryTextDark
import com.mochyy.mochymusic.ui.screens.recap.easeOutCubic
import com.mochyy.mochymusic.ui.screens.recap.giantText
import com.mochyy.mochymusic.ui.screens.recap.go
import com.mochyy.mochymusic.ui.screens.recap.lerpF
import com.mochyy.mochymusic.ui.screens.recap.recapBarcodeCaption
import com.mochyy.mochymusic.ui.screens.recap.recapBarcodePayload
import com.mochyy.mochymusic.ui.screens.recap.recapDots
import com.mochyy.mochymusic.ui.screens.recap.rememberRecapTime
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevAvatar
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPeople
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.screens.setup.onboarding.IdCardAspect
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProfileIdCard
import com.mochyy.mochymusic.ui.screens.update.UpdateHeadline
import com.mochyy.mochymusic.ui.screens.update.rememberUpdateState
import com.mochyy.mochymusic.ui.screens.update.showAboutSheet
import com.mochyy.mochymusic.ui.screens.update.showUpdateSheet
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.UserProfileViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.rememberResourcePainter

private object MochyGreeting {
    private fun pick() = listOf("Hello again", "Hey", "Welcome back", "Good to see you").random()

    private val greetingState = mutableStateOf("Hello again")
    private var wasBackgrounded = false

    val current: String get() = greetingState.value

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> wasBackgrounded = true
                    Lifecycle.Event.ON_START -> if (wasBackgrounded) {
                        greetingState.value = pick()
                        wasBackgrounded = false
                    }
                    else -> Unit
                }
            },
        )
    }
}

/** Where the header is in its pull-down / scratch / recap life. */
private enum class HeaderStage { Collapsed, Pulling, Expanding, Expanded, Scanning, Invalid, Story }

private val PageCard = Color(0xFF0F0F0F)

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val zoomOutState = LocalZoomOutOverlayState.current
    val updateState by rememberUpdateState()
    val obscured = LocalAppContentObscured.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val statusBarTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val topPx = with(density) { statusBarTop.toPx() }
    val cornerPx = with(density) { 26.dp.toPx() }

    val (localUsername) = rememberPreference(UsernameKey, "")
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val (cardNumberPref, setCardNumber) = rememberPreference(ProfileCardNumberKey, "")
    val (joinedEpochDay, setJoinedEpochDay) = rememberPreference(ProfileJoinedEpochDayKey, -1L)
    val (forceRecap) = rememberPreference(com.mochyy.mochymusic.constants.ForceMonthlyRecapKey, false)

    // the profile page that opens out of the card (ui/screens/profile)
    val profile = rememberProfileMorphState()
    val cardPress = remember { MutableInteractionSource() }
    val cardPressed by cardPress.collectIsPressedAsState()
    val profileViewModel: UserProfileViewModel = hiltViewModel()

    var liveAccountName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(innerTubeCookie) {
        if (innerTubeCookie.isBlank()) {
            liveAccountName = null
            return@LaunchedEffect
        }
        com.music.innertube.YouTube.cookie = innerTubeCookie
        com.music.innertube.YouTube.accountInfo().onSuccess { liveAccountName = it.name }
    }
    val accountName = localUsername.ifBlank { liveAccountName.orEmpty() }
    val totalListeningMs by remember(database) { database.totalListeningTimeMs() }.collectAsState(initial = 0L)

    // the onboarding writes the card's number and join date; an install from before it gets them once, here
    LaunchedEffect(cardNumberPref, joinedEpochDay) {
        if (cardNumberPref.isBlank()) setCardNumber((10_000_000_000L + Random.nextLong(89_999_999_999L)).toString())
        if (joinedEpochDay < 0L) setJoinedEpochDay((RecapRepository.firstPlayDate(database) ?: LocalDate.now()).toEpochDay())
    }
    val cardNumber = cardNumberPref.ifBlank { "00000000000" }
    val joinedDate = if (joinedEpochDay >= 0L) LocalDate.ofEpochDay(joinedEpochDay) else LocalDate.now()
    val joinedText = "joined at " + joinedDate.format(DateLong)
    val photo: Any? = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }

    // ---- header state -------------------------------------------------------------------------
    var stage by remember { mutableStateOf(HeaderStage.Collapsed) }
    var expand by remember { mutableFloatStateOf(0f) }
    var expandJob by remember { mutableStateOf<Job?>(null) }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var downInHeader by remember { mutableStateOf(false) }
    val flip = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var tiltTarget by remember { mutableStateOf(Offset.Zero) }
    val deviceTilt = rememberDeviceTilt()
    var scratchKey by remember { mutableIntStateOf(0) }
    val scratch = remember(scratchKey) { ScratchState() }
    val scan = remember { Animatable(0f) }
    var verdict by remember { mutableStateOf<ScanVerdict?>(null) }
    var recapKey by remember { mutableIntStateOf(0) }
    var recap by remember { mutableStateOf<RecapAvailability?>(null) }
    var story by remember { mutableStateOf<Pair<RecapStoryData, RecapAnchors>?>(null) }
    var storyCovering by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val contentIn = remember { Animatable(1f) }
    var rootOnScreen by remember { mutableStateOf(Offset.Zero) }
    var cardOnScreen by remember { mutableStateOf(Rect.Zero) }

    // the player covering the page is a hold, not a stop, so opening it doesn't recompose the whole page
    val time = rememberRecapTime(running = !storyCovering, held = { profile.holdsPage || obscured.value })
    val measurer = rememberTextMeasurer()
    val giant = remember {
        GiantTextState("MOCHY", filledRows = 0..0).apply { color = { lerp(HeaderText, StoryTextDark, expand) } }
    }

    // whether there's a recap waiting, so the pull hint can say so now and then
    var recapWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(forceRecap) {
        recapWaiting = runCatching {
            RecapRepository.recapFor(database, LocalDate.now(), force = forceRecap) is RecapAvailability.Ready
        }.getOrDefault(false)
    }
    // 0 = the chevron, 1 = "swipe down here!!"; it turns into the words every few seconds and back
    val hintWords = remember { Animatable(0f) }
    LaunchedEffect(recapWaiting, stage) {
        if (!recapWaiting || stage != HeaderStage.Collapsed) {
            hintWords.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        while (true) {
            delay(3600)
            // nobody sees it under the profile page
            snapshotFlow { profile.isActive }.first { !it }
            hintWords.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            delay(2400)
            hintWords.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(recapKey) {
        if (recapKey == 0) return@LaunchedEffect
        recap = null
        recap = RecapRepository.recapFor(database, LocalDate.now(), force = forceRecap)
    }
    // the finger's tilt, eased so the card feels weighty rather than glued on
    LaunchedEffect(Unit) {
        snapshotFlow { tiltTarget }.collect { t ->
            val settle = t == Offset.Zero
            val spec = if (settle) spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessMediumLow)
            launch { tiltX.animateTo(t.y, spec) }
            launch { tiltY.animateTo(t.x, spec) }
        }
    }

    fun animateExpand(target: Float, ms: Int) {
        expandJob?.cancel()
        val from = expand
        expandJob = scope.launch { animate(from, target, animationSpec = tween(ms, easing = Emphasized)) { v, _ -> expand = v } }
    }

    fun collapse() {
        stage = HeaderStage.Collapsed
        pullPx = 0f
        animateExpand(0f, 650)
        scope.launch { flip.animateTo(0f, tween(650, easing = FastOutSlowInEasing)) }
        scope.launch {
            delay(650)
            scratchKey++
            scan.snapTo(0f)
            verdict = null
        }
    }

    fun expandFully() {
        stage = HeaderStage.Expanding
        pullPx = 0f
        tiltTarget = Offset.Zero
        recapKey++
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        animateExpand(1f, 760)
        scope.launch {
            delay(140)
            flip.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            stage = HeaderStage.Expanded
        }
    }

    fun onScratched() {
        if (stage != HeaderStage.Expanded) return
        stage = HeaderStage.Scanning
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            scratch.dissolve.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            val r = recap ?: snapshotFlow { recap }.filterNotNull().first()
            when (r) {
                is RecapAvailability.Ready -> {
                    stage = HeaderStage.Story
                    val headerTop = rootOnScreen.y
                    val top = topPx
                    story = RecapStoryData(
                        stats = r.stats,
                        monthProgress = r.monthProgressPercent,
                        username = accountName,
                        photo = photo,
                        cardNumber = cardNumber,
                        joinedText = joinedText,
                        joinedDate = joinedDate,
                        allTimeMs = totalListeningMs,
                    ) to RecapAnchors(
                        expandedCard = cardOnScreen,
                        headerCard = Rect(
                            offset = Offset(cardOnScreen.left, headerTop + top + with(density) { CardTopDp.dp.toPx() }),
                            size = cardOnScreen.size,
                        ),
                        headerHeight = headerTop + collapsedHeaderPx(top, cardOnScreen.height, density.density),
                        topInset = headerTop + top,
                    )
                }
                is RecapAvailability.NotYet -> {
                    verdict = ScanVerdict.Invalid
                    scan.animateTo(1f, tween(1950, easing = LinearEasing))
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    stage = HeaderStage.Invalid
                }
            }
        }
    }

    BackHandler(enabled = stage == HeaderStage.Expanded || stage == HeaderStage.Invalid || stage == HeaderStage.Pulling) { collapse() }

    // on its side the card sits at the left and the cards at the right; the recap only plays upright
    val landscape = com.mochyy.mochymusic.ui.utils.isLandscapeLayout()
    // the recap asked for from the sideways page: the window turns upright first, then the card flips
    var recapAsked by remember { mutableStateOf(false) }
    com.mochyy.mochymusic.ui.utils.HoldOrientation(
        orientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        active = recapAsked || stage != HeaderStage.Collapsed || story != null,
    )
    LaunchedEffect(recapAsked, landscape) {
        if (recapAsked && !landscape) {
            recapAsked = false
            expandFully()
        }
    }

    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }
    val bottomBarInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // the Mochy page, and over it the profile page when the card has been tapped
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { rootOnScreen = it.positionOnScreen() }
                .pointerInput(Unit) {
                    // a new touch is "in the header" only if the header itself says so (it sees it next)
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        downInHeader = false
                    }
                }
                // zooms towards you and fades as the profile page opens out of the card
                .zoomsAwayFor(profile),
        ) {
            val viewportH = constraints.maxHeight.toFloat()
            val inset = with(density) { HomeContentInset.toPx() }
            // on its side the header is the left pane, the card as big as fits between the logo and the bottom bar
            val paneW = if (landscape) constraints.maxWidth * LandscapePaneShare else constraints.maxWidth.toFloat()
            val landscapeTop = topPx + with(density) { LandscapeCardTop.toPx() }
            val landscapeBottom = viewportH - with(density) { (bottomBarInset + LandscapeHintRoom).toPx() }
            val cardW = if (landscape) {
                minOf(paneW - 2 * inset, (landscapeBottom - landscapeTop).coerceAtLeast(1f) * IdCardAspect)
            } else {
                constraints.maxWidth - 2 * inset
            }
            val cardH = cardW / IdCardAspect
            val collapsedH = if (landscape) viewportH else collapsedHeaderPx(topPx, cardH, density.density)
            val cardTopCollapsed = if (landscape) {
                landscapeTop + ((landscapeBottom - landscapeTop - cardH) / 2f).coerceAtLeast(0f)
            } else {
                topPx + with(density) { CardTopDp.dp.toPx() }
            }
            val cardTopExpanded = viewportH * 0.49f - cardH / 2f
            val pullRange = (viewportH - collapsedH).coerceAtLeast(1f)
            val collapseProgress by remember { derivedStateOf { (scrollState.value / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f) } }

            val pullConnection = remember(pullRange) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (stage != HeaderStage.Pulling || available.y >= 0f) return Offset.Zero
                        val before = pullPx
                        pullPx = (pullPx + available.y).coerceAtLeast(0f)
                        expand = (pullPx * 0.62f / pullRange).coerceIn(0f, 0.8f)
                        if (pullPx == 0f) stage = HeaderStage.Collapsed
                        return Offset(0f, pullPx - before)
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        val canPull = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling
                        if (!canPull || !downInHeader || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                        stage = HeaderStage.Pulling
                        pullPx += available.y
                        expand = (pullPx * 0.62f / pullRange).coerceIn(0f, 0.8f)
                        return Offset(0f, available.y)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (stage != HeaderStage.Pulling) return Velocity.Zero
                        if (expand > 0.16f || available.y > 1600f) expandFully() else collapse()
                        return available
                    }
                }
            }

            // ---- the scrolling page ---------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { collapseProgress > 0f && stage == HeaderStage.Collapsed }),
            ) {
                ProfilePageLayout(
                    landscape = landscape,
                    pageModifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullConnection),
                    scroll = Modifier.verticalScroll(scrollState, enabled = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling),
                    header = {
                    // header: grows to the viewport as it's pulled
                    Box(
                        modifier = Modifier
                            .then(if (landscape) Modifier.width(with(density) { paneW.toDp() }) else Modifier.fillMaxWidth())
                            .layout { measurable, constraints ->
                                val h = lerpF(collapsedH, viewportH, expand).roundToInt()
                                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                                layout(constraints.maxWidth, h) { p.place(0, 0) }
                            }
                            .pointerInput(Unit) {
                                // tilt follows the finger; nothing is consumed, so scrolling still works
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    downInHeader = true
                                    var acc = Offset.Zero
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        if (stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling) {
                                            acc += change.positionChange()
                                            tiltTarget = Offset(
                                                (acc.x * 0.09f).coerceIn(-26f, 26f),
                                                (-acc.y * 0.07f).coerceIn(-18f, 18f),
                                            )
                                        }
                                    }
                                    tiltTarget = Offset.Zero
                                }
                            }
                            .pointerInput(stage) {
                                if (stage != HeaderStage.Expanded && stage != HeaderStage.Invalid) return@pointerInput
                                // expanded: a swipe up folds it away again
                                var dy = 0f
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dy < -with(density) { 64.dp.toPx() }) collapse() else animateExpand(1f, 300)
                                        dy = 0f
                                    },
                                    onDragCancel = { animateExpand(1f, 300) },
                                ) { change, amount ->
                                    change.consume()
                                    dy += amount
                                    expand = (1f + dy.coerceAtMost(0f) / pullRange * 0.8f).coerceIn(0.4f, 1f)
                                }
                            }
                            .then(if (landscape) Modifier.clipEndRounded(cornerPx) else Modifier.clipBottomRounded { cornerPx * (1f - expand) })
                            .drawBehind { drawRect(lerp(HeaderGrey, StoryDark, expand)) }
                            .giantText(giant, time, measurer, centerY = { windowHeight / 2f - rootOnScreen.y })
                            .recapDots(color = { lerp(HeaderDots, StoryDots, expand) }),
                    ) {
                        // greeting (in the header; its scrolled-away twin lives in the top bar); on its side it heads the cards at the right
                        if (!landscape) Text(
                            text = "${MochyGreeting.current}, ${accountName.ifBlank { "there" }}!",
                            style = PageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = HomeContentInset, end = HomeContentInset, top = statusBarTop + 80.dp)
                                .graphicsLayer {
                                    alpha = ((1f - expand * 3f) * (1f - collapseProgress * 1.6f)).coerceIn(0f, 1f) * contentIn.value
                                    translationY = (1f - contentIn.value) * 24.dp.toPx()
                                },
                        )

                        // the card: tilts with the finger, drifts a little on its own, flips to the barcode
                        val showBack by remember { derivedStateOf { flip.value >= 0.5f } }
                        val coatingGone by remember(scratch) { derivedStateOf { scratch.dissolve.value >= 1f } }
                        Box(
                            modifier = Modifier
                                .then(
                                    if (landscape) {
                                        Modifier
                                            .padding(start = with(density) { ((paneW - cardW) / 2f).toDp() })
                                            .width(with(density) { cardW.toDp() })
                                    } else {
                                        Modifier
                                            .padding(horizontal = HomeContentInset)
                                            .fillMaxWidth()
                                    }
                                )
                                .aspectRatio(IdCardAspect)
                                .offset { androidx.compose.ui.unit.IntOffset(0, lerpF(cardTopCollapsed, cardTopExpanded, expand).roundToInt()) }
                                .onPlaced { profile.cardSlot = it }
                                .onGloballyPositioned { coords ->
                                    val p = coords.positionOnScreen()
                                    cardOnScreen = Rect(p, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                }
                                .graphicsLayer {
                                    // under the profile page the card holds still (no frames for nobody),
                                    // and it eases back into leaning as the page closes over it again
                                    val follow = profile.cardFollow.value
                                    var leanX = profile.heldTiltX
                                    var leanY = profile.heldTiltY
                                    if (follow > 0f) {
                                        val live = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling
                                        val sway = if (live) 1f else 0f
                                        val t = time.sway
                                        // the phone's lean too; half as much on the back, so scratching stays steady
                                        val lean = if (flip.value >= 0.5f) 0.5f else 1f
                                        val liveY = flip.value * 180f + tiltY.value + deviceTilt.rotationY * lean + sway * sin(t * 0.9f) * 3f
                                        val liveX = tiltX.value + deviceTilt.rotationX * lean + sway * cos(t * 0.7f) * 2f - (if (stage == HeaderStage.Pulling) expand * 14f else 0f)
                                        leanX = lerpF(leanX, liveX, follow)
                                        leanY = lerpF(leanY, liveY, follow)
                                    }
                                    rotationX = leanX
                                    rotationY = leanY
                                    // the profile page's flying copies lean the same way when they take off or land
                                    profile.cardTiltX = leanX
                                    profile.cardTiltY = leanY
                                    cameraDistance = 14f * this.density
                                    alpha = if (cardHidden) 0f else 1f
                                }
                                .clickable(enabled = stage == HeaderStage.Collapsed, interactionSource = cardPress, indication = RemoteFocus) {
                                    if (expand > 0f) return@clickable
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    profile.open()
                                },
                        ) {
                            if (!showBack) {
                                ProfileIdCard(
                                    username = accountName,
                                    photo = photo,
                                    cardNumber = cardNumber,
                                    joinedText = joinedText,
                                    showIdCardOf = true,
                                    anchors = profile.card,
                                    partsAlpha = { if (profile.coversCard) 0f else 1f },
                                    marqueeRunning = { !profile.holdsPage },
                                    showEditHint = true,
                                    pressed = { cardPressed && stage == HeaderStage.Collapsed },
                                )
                            } else {
                                val range = (recap as? RecapAvailability.Ready)?.stats?.range
                                val payload = recapBarcodePayload(range)
                                IdCardBack(
                                    username = accountName,
                                    payload = payload,
                                    caption = recapBarcodeCaption(payload, cardNumber),
                                    scratch = scratch.takeIf { !coatingGone && (stage == HeaderStage.Expanding || stage == HeaderStage.Expanded || stage == HeaderStage.Scanning) },
                                    time = time,
                                    scan = { scan.value },
                                    verdict = { verdict },
                                    onScratchProgress = { if (it >= 0.55f) onScratched() },
                                    modifier = Modifier.graphicsLayer { rotationY = 180f },
                                )
                            }
                        }

                        // pull hint: a chevron that, with a recap waiting, turns into words now and then; on its side
                        // there's nothing to pull, so a tap turns the phone upright and opens the recap
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset { androidx.compose.ui.unit.IntOffset(0, (cardTopCollapsed + cardH + 14.dp.toPx()).roundToInt()) }
                                .height(30.dp)
                                .then(
                                    if (landscape) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = RemoteFocus,
                                            enabled = stage == HeaderStage.Collapsed,
                                        ) { recapAsked = true }
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    alpha = (1f - expand * 4f).coerceIn(0f, 1f) * contentIn.value
                                    translationY = ((sin(time.sway * 2.4f) + 1f) / 2f) * 4.dp.toPx()
                                },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = "Pull down",
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(30.dp)
                                    .graphicsLayer {
                                        val w = hintWords.value
                                        alpha = 1f - w
                                        scaleX = 1f - 0.3f * w
                                        scaleY = 1f - 0.3f * w
                                        renderEffect = BlurEffectCache.element(w * 14f)
                                    },
                            )
                            if (recapWaiting) {
                                Text(
                                    text = if (landscape) stringResource(R.string.recap_tap_hint) else "swipe down here!!",
                                    style = MonoSmall.copy(color = Color.White.copy(alpha = 0.6f)),
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer {
                                        val w = hintWords.value
                                        alpha = w
                                        scaleX = 0.85f + 0.15f * w
                                        scaleY = 0.85f + 0.15f * w
                                        renderEffect = BlurEffectCache.element((1f - w) * 14f)
                                    },
                                )
                            }
                        }

                        // under the expanded card: "scratch!", or why the code didn't scan
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { androidx.compose.ui.unit.IntOffset(0, (cardTopExpanded + cardH + 34.dp.toPx()).roundToInt()) }
                                .padding(horizontal = 40.dp)
                                .graphicsLayer {
                                    alpha = ((expand - 0.75f) / 0.25f).coerceIn(0f, 1f)
                                    translationY = (1f - expand) * 40.dp.toPx()
                                },
                        ) {
                            val invalid = stage == HeaderStage.Invalid
                            Text(
                                text = if (invalid) "come back later" else "scratch!",
                                style = PageTitle.copy(fontSize = 20.sp, textAlign = TextAlign.Center),
                                modifier = Modifier.graphicsLayer { alpha = if (invalid) 1f else 1f - scratch.dissolve.value },
                            )
                            if (invalid) {
                                Text(
                                    text = (recap as? RecapAvailability.NotYet)?.comeBack.orEmpty(),
                                    style = PageTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    text = "swipe up to close",
                                    style = MonoSmall.copy(color = Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier.padding(top = 26.dp),
                                )
                            }
                        }
                    }

                    },
                    content = {
                    // ---- everything under the header (beside it, on its side) ----
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .graphicsLayer {
                                alpha = contentIn.value * (1f - expand * 2f).coerceIn(0f, 1f)
                                translationY = (1f - contentIn.value) * 60.dp.toPx()
                            },
                    ) {
                        if (landscape) {
                            Spacer(Modifier.height(statusBarTop + 30.dp))
                            Text(
                                text = "${MochyGreeting.current}, ${accountName.ifBlank { "there" }}!",
                                style = PageTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = 1f - collapseProgress },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        ListeningTimeCard(totalListeningMs, onViewStats = { navController.navigate("stats") })
                        Spacer(Modifier.height(16.dp))
                        val openDevSpace: (Int) -> Unit = { personIndex ->
                            zoomOutState.dismissAll()
                            navController.navigate("developer_space?person=$personIndex")
                        }
                        val hasUpdate = updateState is com.mochyy.mochymusic.core.updater.UpdateState.Available
                        VersionCard(
                            updateHeadline = if (hasUpdate) UpdateHeadline else null,
                            onCardClick = { zoomOutState.showUpdateSheet(updateState, onOpenDevSpace = openDevSpace) },
                            onPillClick = {
                                if (hasUpdate) {
                                    zoomOutState.showUpdateSheet(updateState, onOpenDevSpace = openDevSpace)
                                } else {
                                    zoomOutState.showAboutSheet(onOpenDevSpace = openDevSpace)
                                }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        DeveloperCard(onClick = { navController.navigate("developer_space") })
                        Spacer(Modifier.height(16.dp))
                        DiscordCard(onClick = { context.openDevUrl(DiscordUrl) })
                        Spacer(Modifier.height(28.dp))
                        Text("Settings", style = PageTitle.copy(fontSize = 15.sp), modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                        OpenSettingsRow(onClick = { navController.navigate("mochy_settings") })
                        Spacer(Modifier.height(180.dp))
                    }
                    },
                )
            }

            // ---- the glass top bar, a sibling of the glassRoot box (never inside it) ------------------
            if (collapseProgress > 0.005f && stage == HeaderStage.Collapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarTop + 84.dp)
                        .align(Alignment.TopCenter),
                ) {
                    ProgressiveGlassBackground(
                        state = glassState,
                        maxBlurRadius = { 55f * collapseProgress },
                        foundationColor = Color.Black.copy(alpha = 0.55f * collapseProgress),
                        direction = BlurDirection.BottomToTop,
                        steps = 3,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (collapseProgress * 1.5f).coerceIn(0f, 1f) },
                    )
                    val progressiveBar = com.mochyy.mochymusic.ui.component.rememberProgressiveBlur()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (progressiveBar) {
                                    Brush.verticalGradient(
                                        listOf(Color.Black, Color.Black.copy(alpha = 0.85f * collapseProgress), Color.Transparent),
                                    )
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = com.mochyy.mochymusic.ui.component.FlatBarScrimAlpha))
                                },
                            ),
                    )
                }
            }

            // ---- logo, and the greeting sliding into the bar beside it ----
            Icon(
                painter = painterResource(R.drawable.ic_mochy_mark),
                contentDescription = "Mochy",
                tint = Color.White,
                modifier = Modifier
                    .padding(start = HomeContentInset, top = statusBarTop + TopBarLogoTop)
                    .size(width = 31.dp, height = 34.dp)
                    .graphicsLayer {
                        val k = 1f - collapseProgress * 0.18f
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
            if (collapseProgress > 0.01f) {
                Text(
                    text = "${MochyGreeting.current}, ${accountName.ifBlank { "there" }}!",
                    style = PageTitle.copy(fontSize = 18.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = HomeContentInset + 40.dp, end = HomeContentInset, top = statusBarTop + 37.dp)
                        .graphicsLayer {
                            val p = ((collapseProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            alpha = easeOutCubic(p)
                            translationY = (1f - easeOutCubic(p)) * 12.dp.toPx()
                        },
                )
            }
        }

        ProfileMorphHost(
            state = profile,
            username = accountName,
            photo = photo,
            cardNumber = cardNumber,
            navController = navController,
            viewModel = profileViewModel,
        )
    }

    story?.let { (data, anchors) ->
        val payload = recapBarcodePayload(data.stats.range)
        RecapStoryHost(
            data = data,
            anchors = anchors,
            payload = payload,
            caption = recapBarcodeCaption(payload, cardNumber),
            onCovered = {
                // hidden under the story: reset to the collapsed header, card and content tucked away
                storyCovering = true
                expandJob?.cancel()
                expand = 0f
                cardHidden = true
                scope.launch {
                    flip.snapTo(0f)
                    scan.snapTo(0f)
                    contentIn.snapTo(0f)
                    scrollState.scrollTo(0)
                }
                scratchKey++
                verdict = null
            },
            onReturned = {
                cardHidden = false
                storyCovering = false
                stage = HeaderStage.Collapsed
            },
            onClosed = {
                story = null
                scope.launch { contentIn.go(1f, 900, Emphasized) }
            },
        )
    }
}

private const val CardTopDp = 130f

// on its side: the card's pane takes this share of the width, the card starts under the logo and keeps room under it for the hint
private const val LandscapePaneShare = 0.46f
private val LandscapeCardTop = 92.dp
private val LandscapeHintRoom = 50.dp

// upright the header and the cards scroll as one column; on its side the header stands still at the left and the cards scroll beside it
@Composable
private fun ProfilePageLayout(
    landscape: Boolean,
    pageModifier: Modifier,
    scroll: Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (landscape) {
        Row(modifier = pageModifier) {
            header()
            Column(modifier = Modifier.weight(1f).fillMaxHeight().then(scroll)) { content() }
        }
    } else {
        Column(modifier = pageModifier.then(scroll)) {
            header()
            content()
        }
    }
}

// the header pane on its side: rounded on the side that faces the cards
private fun Modifier.clipEndRounded(radius: Float): Modifier = drawWithCache {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topRight = CornerRadius(radius),
                bottomRight = CornerRadius(radius),
            ),
        )
    }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}

/** The logo's top below the status bar: Settings and Library centre a 32dp mark in a 62dp block 18dp down. */
private val TopBarLogoTop = 32.dp

/** Inner padding every card on this page shares. */
private val CardPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)

/** The collapsed header: inset, logo, greeting, the card, and room for the pull hint. */
private fun collapsedHeaderPx(top: Float, cardH: Float, density: Float): Float = top + CardTopDp * density + cardH + 49f * density

/** Clips to the bounds with only the bottom corners rounded, by a radius that can change every frame. */
private fun Modifier.clipBottomRounded(radius: () -> Float): Modifier = drawWithCache {
    val path = Path()
    onDrawWithContent {
        val r = radius()
        if (r <= 0.5f) {
            drawContent()
            return@onDrawWithContent
        }
        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                bottomLeft = CornerRadius(r),
                bottomRight = CornerRadius(r),
            ),
        )
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

private val PageTitle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    letterSpacing = (-0.6).sp,
    color = Color.White,
)

private val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/** A big figure's size in dp: it follows the card's width, not the font-size setting, so it never overflows. */
@Composable
internal fun figureSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

/** How much the cards' figures grow or shrink from the design's 360dp-wide phone, by the width they actually get. */
private fun widthScale(cardWidthDp: Float): Float = (cardWidthDp / 320f).coerceIn(0.72f, 1.2f)

@Composable
private fun ListeningTimeCard(totalMs: Long, onViewStats: () -> Unit) {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onViewStats)
            .padding(CardPadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // long hour counts ("1234h") shrink the figures a step so the label keeps its room
            val k = widthScale(maxWidth.value + 40f) * if (hours >= 1000) 0.8f else if (hours >= 100) 0.9f else 1f
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeValue(hours.toString(), "h", k)
                Box(
                    modifier = Modifier
                        .padding(horizontal = (14 * k).dp)
                        .width(1.dp)
                        .height((34 * k).dp)
                        .background(Color.White.copy(alpha = 0.4f)),
                )
                TimeValue((totalMinutes % 60).toString(), "m", k)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Total Listening\ntime",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.End),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF474747)))
        Spacer(Modifier.height(12.dp))
        Text(
            "View Stats  >",
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF8A8A8A)),
        )
    }
}

@Composable
private fun TimeValue(value: String, unit: String, k: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        OdometerNumber(
            value = value,
            fontSize = figureSp(44f * k),
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = Color.White,
            letterSpacing = (-2).sp,
        )
        Text(
            unit,
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = figureSp(18f * k), color = Color.White),
            modifier = Modifier.padding(start = 1.dp, bottom = (6 * k).dp),
        )
    }
}

@Composable
private fun VersionCard(
    updateHeadline: String?,
    onCardClick: () -> Unit,
    onPillClick: () -> Unit,
) {
    BoxWithConstraints {
        val k = widthScale(maxWidth.value)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(PageCard)
                .clickable(onClick = onCardClick)
                .padding(CardPadding),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Version & about app", style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF9A9A9A)))
                Text(
                    // "7.1.1 build#1093" -> "7.1.1"
                    BuildConfig.VERSION_NAME.substringBefore(' '),
                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = figureSp(44f * k), letterSpacing = (-2).sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (updateHeadline != null) {
                    Text(updateHeadline, style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = Color(0xFF88FF76)), maxLines = 2)
                }
            }
            Spacer(Modifier.width(10.dp))
            VersionCapsule(update = updateHeadline != null, onAbout = onPillClick)
        }
    }
}

/**
 * The version card's capsule: "About app", or "Update" once one is out. The swap pops - the
 * capsule squashes and springs back past its size while the new word grows in on a bounce and
 * the width springs to fit it.
 */
@Composable
private fun VersionCapsule(update: Boolean, onAbout: () -> Unit) {
    val shown = if (update) "Update" else "About app"

    // the pop: a quick squash, then a spring that carries past full size and settles
    val pop = remember { Animatable(1f) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        // not on first showing - only when the label actually changes
        if (!popped) {
            popped = true
            return@LaunchedEffect
        }
        pop.animateTo(0.86f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(CircleShape)
            .background(Color(0xFF4D4D4D))
            .clickable(onClick = onAbout)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                // the new word grows in on a bounce as the old one shrinks away
                val enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                    initialScale = 0.35f,
                ) + fadeIn(tween(durationMillis = 160))
                val exit = scaleOut(
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    targetScale = 0.35f,
                ) + fadeOut(tween(durationMillis = 120))
                // and the capsule's width springs to fit it, overshooting a touch
                (enter togetherWith exit) using SizeTransform(clip = false) { _, _ ->
                    spring(
                        dampingRatio = 0.6f,
                        stiffness = 380f,
                        visibilityThreshold = IntSize.VisibilityThreshold,
                    )
                }
            },
            contentAlignment = Alignment.Center,
            label = "versionCapsule",
        ) { label ->
            Text(
                label,
                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun DeveloperCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spread by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "devCardSpread",
    )
    val face = 30.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // room for every face at rest, 40% of a face apart; pressed they spill to the right
        Box(Modifier.size(width = face + face * 0.4f * (DevPeople.size - 1), height = face)) {
            DevPeople.forEachIndexed { i, person ->
                DevAvatar(
                    person,
                    Modifier
                        .size(face)
                        .graphicsLayer { translationX = i * face.toPx() * (0.4f + 0.35f * spread) }
                        .border(1.5.dp, PageCard, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "meet da dev and contributors :3",
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.6).sp, color = Color(0xFF9A9A9A)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiscordCard(onClick: () -> Unit) {
    val face = 30.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(
            painter = rememberResourcePainter(R.drawable.mochy_discord),
            contentDescription = "Discord",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(face)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Join mochy’s discord server!",
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.6).sp, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenSettingsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.settings), contentDescription = null, tint = Color(0xFF232323), modifier = Modifier.size(18.dp))
        }
        Text("Open settings", style = PageTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}
