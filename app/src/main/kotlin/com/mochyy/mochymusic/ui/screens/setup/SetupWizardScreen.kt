package com.mochyy.mochymusic.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.DisableAiFilterKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.DefaultTransitionMode
import com.mochyy.mochymusic.importer.account.AccountImportState
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.screens.settings.importsync.onSignInTick
import com.mochyy.mochymusic.ui.screens.settings.importsync.CaptureWebContent
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeLoginWebContent
import com.mochyy.mochymusic.ui.screens.settings.importsync.clearWebSession
import com.mochyy.mochymusic.ui.screens.setup.onboarding.BehindScale
import com.mochyy.mochymusic.ui.screens.setup.onboarding.BlurCapability
import com.mochyy.mochymusic.ui.screens.setup.onboarding.BlurWarningPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.BrowserSpring
import com.mochyy.mochymusic.ui.screens.setup.onboarding.CardInfo
import com.mochyy.mochymusic.ui.screens.setup.onboarding.DoneStep
import com.mochyy.mochymusic.ui.screens.setup.onboarding.HelloCardStep
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ImportAskPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ImportModePage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.LastOnePage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.Onb
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnbButton
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnboardingBrowserSheet
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProfilePage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProviderListPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProviderSetupPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.RestoringPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.SyncOptionsPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.WhatToSyncPage
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.OnboardingViewModel
import com.mochyy.mochymusic.viewmodels.TransferStep
import com.mochyy.mochymusic.viewmodels.TransferViewModel
import com.mochyy.mochymusic.viewmodels.YouTubeSyncChoices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.component.backdropClick

private enum class Step {
    Welcome, ImportAsk, ImportMode, Restoring, Providers, ProviderSetup, WhatToSync, SyncOptions,
    Profile, Hello, BlurWarning, LastOne, Done,
}

/** pages that play once and move on by themselves, back never returns to them */
private val AutoSteps = setOf(Step.Hello, Step.Done, Step.Restoring)

/** the onboarding flow: welcome, import or sync, sign in, playlists, profile card, hello, blur warning, last one, done */
@Composable
fun SetupWizardScreen(
    onComplete: (String, Uri?) -> Unit,
    onDrag: (Float) -> Unit,
    onDragRelease: () -> Unit,
) {
    val vm: OnboardingViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val history = remember { mutableStateListOf(Step.Welcome) }
    var forward by remember { mutableStateOf(true) }
    val step = history.last()

    LaunchedEffect(Unit) { vm.reset() }

    fun go(next: Step, replaceCurrent: Boolean = false) {
        forward = true
        if (replaceCurrent && history.size > 1) history.removeAt(history.lastIndex)
        history.add(next)
    }

    // ---- profile ----
    var username by rememberSaveable { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUncroppedUri by remember { mutableStateOf<Uri?>(null) }
    var lastPickedUri by remember { mutableStateOf<Uri?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    var isLeavingWelcome by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            lastPickedUri = picked
            selectedUncroppedUri = picked
        }
    }
    fun openPhotoPicker() = photoPicker.launch(
        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    // ---- import ----
    var backupError by remember { mutableStateOf<String?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val error = vm.importBackup(uri)
                backupError = error
                if (error == null) go(Step.Profile)
            }
        }
    }

    // ---- previous data: a whole export from the old app or another phone ----
    val transfer: TransferViewModel = hiltViewModel()
    val importStep by transfer.import.collectAsState()
    val transferManifest by transfer.manifest.collectAsState()
    var waitingForPermission by remember { mutableStateOf<Uri?>(null) }
    fun startRestoring(uri: Uri) {
        transfer.importFrom(uri)
        go(Step.Restoring)
    }
    // local songs come back through the phone's music library, so ask first; a no still imports everything else
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        waitingForPermission?.let { uri ->
            waitingForPermission = null
            startRestoring(uri)
        }
    }
    val transferPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                transfer.check(uri)
                    .onSuccess { manifest ->
                        if (transfer.wantsAudioPermission(manifest)) {
                            waitingForPermission = uri
                            audioPermission.launch(TransferViewModel.AudioPermission)
                        } else {
                            startRestoring(uri)
                        }
                    }
                    .onFailure { backupError = it.message }
            }
        }
    }
    // a failed import goes back to the choices and says why there
    LaunchedEffect(importStep) {
        val failed = importStep as? TransferStep.Failed ?: return@LaunchedEffect
        if (step == Step.Restoring) {
            forward = false
            history.removeAt(history.lastIndex)
        }
        backupError = failed.message
        transfer.clearImport()
    }

    val session = vm.session
    val stateFlow: StateFlow<AccountImportState?> = remember(session) { session?.state ?: MutableStateFlow(null) }
    val sessionState by stateFlow.collectAsState()
    val readyFlow = remember(session) { session?.ready ?: MutableStateFlow(null) }
    val readyImport by readyFlow.collectAsState()
    var syncChoices by remember { mutableStateOf(YouTubeSyncChoices()) }

    // ---- the browser sheet ----
    val browserPresence = remember { Animatable(0f) }
    var browserShown by remember { mutableStateOf(false) }
    var browserWanted by remember { mutableStateOf(false) }

    fun openBrowser() {
        if (session == null || browserWanted) return
        browserWanted = true
        browserShown = true
        scope.launch { browserPresence.animateTo(1f, BrowserSpring) }
    }

    fun closeBrowser() {
        if (!browserWanted) return
        browserWanted = false
        scope.launch {
            browserPresence.animateTo(0f, BrowserSpring)
            if (!browserWanted) browserShown = false
        }
    }

    fun back() {
        if (history.size <= 1 || step in AutoSteps) return
        forward = false
        val leaving = history.removeAt(history.lastIndex)
        // leaving the sign-in or its playlists returns to choosing a provider, signed out of this one
        if (leaving == Step.ProviderSetup || leaving == Step.WhatToSync) {
            closeBrowser()
            vm.closeProvider()
        }
    }

    // the provider page opens its browser after a beat, once the user has read what's about to happen
    LaunchedEffect(step, session) {
        if (step != Step.ProviderSetup || session == null) return@LaunchedEffect
        delay(900)
        val state = session.state.value
        if (state is AccountImportState.SigningIn || state is AccountImportState.NeedsYouTubeSignIn) openBrowser()
    }

    // signed in and the library is read: close the browser and show the playlists
    LaunchedEffect(sessionState, step) {
        if (step == Step.ProviderSetup && sessionState is AccountImportState.Choosing) {
            closeBrowser()
            delay(250)
            go(Step.WhatToSync, replaceCurrent = true)
        }
    }

    // the picked playlists are read: the importer takes them from here, onboarding moves on
    LaunchedEffect(readyImport) {
        val parsed = readyImport ?: return@LaunchedEffect
        vm.startImport(parsed)
        session?.consumeReady()
        go(Step.Profile)
    }

    // the account's own name and picture start the profile, once
    LaunchedEffect(step) {
        if (step != Step.Profile || prefilled) return@LaunchedEffect
        prefilled = true
        if (username.isBlank()) vm.accountName()?.let { username = it }
        if (profilePicUri == null) {
            val photo = vm.accountPhoto()
            if (profilePicUri == null && photo != null) profilePicUri = photo
        }
    }

    // ---- settings the last pages flip ----
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (crossmixMode, onCrossmixModeChange) = rememberEnumPreference(CrossmixModeKey, defaultValue = DefaultTransitionMode)
    val (disableAiFilter, onDisableAiFilterChange) = rememberPreference(DisableAiFilterKey, defaultValue = false)

    val card = CardInfo(username = username, photo = profilePicUri, cardNumber = vm.cardNumber, joinedText = vm.joinedText)

    fun afterHello(): Step = if (vm.blur?.verdict == BlurCapability.Verdict.Laggy) Step.BlurWarning else Step.LastOne

    BackHandler(enabled = !browserWanted) {
        if (selectedUncroppedUri != null) selectedUncroppedUri = null else back()
    }

    PhotoCropperContainer(
        uri = selectedUncroppedUri,
        onDone = { cropped ->
            profilePicUri = cropped
            selectedUncroppedUri = null
        },
        onCancel = { selectedUncroppedUri = null },
        onSelectNewImage = { openPhotoPicker() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Onb.Page),
        ) {
            // the page, receding like a covered sheet while the browser is open over it
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = browserPresence.value
                        val s = 1f - (1f - BehindScale) * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        if (p > 0f) {
                            shape = RoundedCornerShape(32.dp)
                            clip = true
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val auto = targetState in AutoSteps || initialState in AutoSteps
                        when {
                            auto -> (fadeIn(tween(420)) + scaleIn(tween(520), initialScale = 0.96f)) togetherWith fadeOut(tween(260))
                            forward -> slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { -it / 4 } + fadeOut(tween(220))
                            else -> slideInHorizontally(tween(380)) { -it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { it / 4 } + fadeOut(tween(220))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "onboardingStep",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        // the welcome page runs to the top of the sheet under the handle, pushed down its strip showed as a bar
                        Step.Welcome -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                            WelcomeStep(isHiding = isLeavingWelcome)
                            OnbButton(
                                text = "Next",
                                onClick = {
                                    isLeavingWelcome = true
                                    scope.launch {
                                        delay(400)
                                        go(Step.ImportAsk)
                                        isLeavingWelcome = false
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 28.dp, vertical = 18.dp),
                            )
                        }

                        Step.ImportAsk -> ImportAskPage(
                            onYes = { go(Step.ImportMode) },
                            onSkip = { go(Step.Profile) },
                        )

                        Step.ImportMode -> ImportModePage(
                            onPreviousData = {
                                backupError = null
                                transferPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                            onImport = { go(Step.Providers) },
                            onBackup = {
                                backupError = null
                                backupPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                            },
                            onBack = ::back,
                            backupError = backupError,
                        )

                        Step.Restoring -> RestoringPage(step = importStep, manifest = transferManifest)

                        Step.Providers -> ProviderListPage(
                            onPick = { service ->
                                vm.startProvider(service)
                                go(Step.ProviderSetup)
                            },
                            onBack = ::back,
                        )

                        Step.ProviderSetup -> {
                            val service = session?.service ?: AccountService.SPOTIFY
                            ProviderSetupPage(
                                service = service,
                                state = sessionState,
                                browserOpen = browserWanted,
                                onOpenBrowser = ::openBrowser,
                                onRetry = {
                                    clearWebSession(service.webOrigins)
                                    session?.signInAgain()
                                    openBrowser()
                                },
                                onBack = ::back,
                            )
                        }

                        Step.WhatToSync -> WhatToSyncPage(
                            service = session?.service ?: AccountService.SPOTIFY,
                            state = sessionState,
                            onToggle = { session?.toggle(it) },
                            onSelectAll = { session?.selectAll() },
                            onSelectNone = { session?.selectNone() },
                            onConfirm = {
                                // youtube music can also stay in sync, the others just import
                                if (session?.service == AccountService.YOUTUBE_MUSIC) go(Step.SyncOptions) else session?.readSelected()
                            },
                            onCancelReading = { session?.cancelReading() },
                            onBack = ::back,
                        )

                        Step.SyncOptions -> SyncOptionsPage(
                            choices = syncChoices,
                            onChange = { syncChoices = it },
                            reading = sessionState as? AccountImportState.Reading,
                            onSync = {
                                // synced playlists come in through sync itself, linked rather than copied
                                val selected = (sessionState as? AccountImportState.Choosing)?.selected.orEmpty()
                                vm.startYouTubeSync(selected, likedId = "LM", choices = syncChoices)
                                go(Step.Profile)
                            },
                            onJustImport = { session?.readSelected() },
                            onBack = { if (sessionState is AccountImportState.Reading) session?.cancelReading() else back() },
                        )

                        Step.Profile -> ProfilePage(
                            username = username,
                            onUsernameChange = { username = it },
                            photo = profilePicUri,
                            onPhotoClick = {
                                val existing = lastPickedUri
                                if (existing != null) selectedUncroppedUri = existing else openPhotoPicker()
                            },
                            cardNumber = vm.cardNumber,
                            joinedText = vm.joinedText,
                            onNext = { if (username.isNotBlank()) go(Step.Hello) },
                        )

                        Step.Hello -> HelloCardStep(
                            card = card,
                            onProbe = vm::onBlurProbe,
                            onFinished = {
                                go(afterHello(), replaceCurrent = true)
                            },
                        )

                        Step.BlurWarning -> BlurWarningPage(
                            disableBlur = disableBlur,
                            onDisableBlurChange = onDisableBlurChange,
                            detected = vm.blur?.summary,
                            onContinue = { go(Step.LastOne) },
                        )

                        Step.LastOne -> LastOnePage(
                            autoCrossfade = crossmixMode != CrossmixMode.OFF,
                            onAutoCrossfadeChange = { on -> onCrossmixModeChange(if (on) CrossmixMode.CROSSMIX else CrossmixMode.OFF) },
                            aiArtistFilter = !disableAiFilter,
                            onAiArtistFilterChange = { on -> onDisableAiFilterChange(!on) },
                            onNext = { go(Step.Done) },
                        )

                        Step.Done -> DoneStep(
                            card = card,
                            onFinished = {
                                vm.saveCard()
                                vm.closeProvider()
                                onComplete(username.trim(), profilePicUri)
                            },
                        )
                    }
                }
            }

            if (browserShown && session != null) {
                // dims the page behind, and a tap on that strip closes the browser
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = browserPresence.value }
                        .background(Color.Black.copy(alpha = 0.45f))
                        .backdropClick(::closeBrowser),
                )
                OnboardingBrowserSheet(
                    service = session.service,
                    presence = browserPresence,
                    onClose = ::closeBrowser,
                ) { onUrlChange, onLoadingChange, modifier ->
                    if (session.service == AccountService.YOUTUBE_MUSIC) {
                        YouTubeLoginWebContent(
                            onSignedIn = {
                                closeBrowser()
                                session.refreshYouTube()
                            },
                            onExit = ::closeBrowser,
                            onUrlChange = onUrlChange,
                            onLoadingChange = onLoadingChange,
                            modifier = modifier,
                        )
                    } else {
                        CaptureWebContent(
                            startUrl = session.service.loginUrl.orEmpty(),
                            playerOrigins = session.service.playerOrigins,
                            desktopSite = session.service.desktopSite,
                            onHeader = session::onHeader,
                            onTick = { view -> session.service.onSignInTick(view, session::onApplePageProbe, session::onCookies) },
                            onExit = ::closeBrowser,
                            onUrlChange = onUrlChange,
                            onLoadingChange = onLoadingChange,
                            modifier = modifier,
                        )
                    }
                }
            }

            // the onboarding sheet's own handle; dragging it rubber-bands the whole sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = onDragRelease,
                            onDragCancel = onDragRelease,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
