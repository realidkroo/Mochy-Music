package com.mochyy.mochymusic.ui.screens.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.GithubRelease
import com.mochyy.mochymusic.core.updater.UpdateState
import com.mochyy.mochymusic.core.updater.apkFileFor
import com.mochyy.mochymusic.core.updater.downloadApk
import com.mochyy.mochymusic.core.updater.formatBytes
import com.mochyy.mochymusic.core.updater.getLatestRelease
import com.mochyy.mochymusic.core.updater.getPreviousRelease
import com.mochyy.mochymusic.core.updater.installApk
import com.mochyy.mochymusic.core.updater.isDownloaded
import com.mochyy.mochymusic.core.updater.isNewerThanInstalled
import com.mochyy.mochymusic.core.updater.parseFullVersion
import com.mochyy.mochymusic.ui.component.BetaBadge
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSurface
import com.mochyy.mochymusic.ui.screens.donate.showSupportSheet
import kotlinx.coroutines.launch

@Composable
fun rememberUpdateState(): androidx.compose.runtime.State<UpdateState> {
    val state = remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(Unit) { state.value = checkUpdate(force = false) }
    return state
}

/** asks github (or the shared copy, unless force is set) whether a newer release is out */
private suspend fun checkUpdate(force: Boolean): UpdateState =
    getLatestRelease(force).fold(
        onSuccess = { release ->
            when {
                release == null -> UpdateState.UpToDate
                isNewerThanInstalled(release.version) -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
        },
        onFailure = { UpdateState.Failed(it.message ?: "Couldn't reach GitHub") },
    )

/** shows the app version sheet as a PopupSheet, see docs/popup-sheet.skill.md */
fun PopupSheetState.showUpdateSheet(updateState: UpdateState, onOpenDevSpace: (Int) -> Unit = {}) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        UpdateSheetContent(initialState = updateState, popup = popup, onOpenDevSpace = onOpenDevSpace)
    }
}

private fun PopupSheetState.showUpdateDetailSheet(release: GithubRelease) {
    val popup = this
    popup.show {
        UpdateDetailContent(
            release = release,
            // pushed on top of the version sheet, so back just pops down to it again
            onBack = popup::dismiss,
            // locks this same sheet during a download, re-showing it locked would push a second copy with fresh state
            onLocked = popup::setLocked,
        )
    }
}

@Composable
internal fun SheetPrimaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    }
}

private val StableGreen = Color(0xFF9CF58A)
private const val StableLabel = "STABLE"

/** "8.1.0 build#1165" -> "8.1.0", "8.0.2 (1100)" -> "8.0.2" */
internal fun shortVersion(version: String): String = version.substringBefore(' ')

/** the app version sheet: the app, the version you're on and its channel, what's newer, and what you can do about it */
@Composable
private fun ColumnScope.UpdateSheetContent(
    initialState: UpdateState,
    popup: PopupSheetState,
    onOpenDevSpace: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initialState) }
    var latest by remember { mutableStateOf((initialState as? UpdateState.Available)?.release) }
    var previous by remember { mutableStateOf<GithubRelease?>(null) }

    suspend fun refresh(force: Boolean) {
        state = checkUpdate(force)
        // a failed check keeps what it knew, so the rows don't vanish when offline
        latest = getLatestRelease().getOrNull() ?: latest
        previous = getPreviousRelease().getOrNull() ?: previous
    }
    // the shared copy answers instantly when the app already checked on launch
    LaunchedEffect(Unit) { refresh(force = false) }
    val checkAgain: () -> Unit = {
        if (state !is UpdateState.Checking) {
            state = UpdateState.Checking
            scope.launch { refresh(force = true) }
        }
    }

    val installed = shortVersion(BuildConfig.VERSION_NAME)
    Box(Modifier.fillMaxWidth()) {
        // centred just inside the sheet's top-right corner so its edge cuts the ring off, no size of its own so it never pushes content around
        VersionRing(
            text = "MOCHY $installed",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity))
                    layout(0, 0) {
                        placeable.place(x = (-195).dp.roundToPx(), y = (-134).dp.roundToPx())
                    }
                },
        )

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MenuRowSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mochy_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Mochy", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    installed,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                )
                Spacer(Modifier.width(14.dp))
                ChannelBadge(latest)
            }

            Spacer(Modifier.height(16.dp))
            UpdateStatusCard(
                state = state,
                onUpdate = { popup.showUpdateDetailSheet(it) },
                onRetry = checkAgain,
            )

            Spacer(Modifier.height(22.dp))
            Text("Actions", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(
                    icon = R.drawable.refresh,
                    label = if (state is UpdateState.Checking) "Checking…" else "Check for update",
                    spinning = state is UpdateState.Checking,
                    onClick = checkAgain,
                )
                AnimatedVisibility(
                    visible = previous != null,
                    enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    previous?.let { older ->
                        ActionRow(
                            icon = R.drawable.history,
                            label = "Downgrade to ${shortVersion(older.version)}",
                            onClick = { popup.showUpdateDetailSheet(older) },
                        )
                    }
                }
                ActionRow(
                    icon = R.drawable.bedtime,
                    label = "Switch to nightly",
                    hint = "Not out yet",
                    enabled = false,
                    onClick = {},
                )
                ActionRow(
                    icon = R.drawable.info,
                    label = "About app",
                    onClick = { popup.showAboutSheet(onOpenDevSpace = onOpenDevSpace) },
                )
                ActionRow(
                    icon = R.drawable.favorite,
                    label = "Help us!",
                    onClick = { popup.showSupportSheet() },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** which channel this build is: nightly says so, newer than any release is beta, the rest is stable */
@Composable
private fun ChannelBadge(latest: GithubRelease?) {
    val aheadOfReleases = latest != null &&
        parseFullVersion(latest.version).copy(buildNumber = 0) < parseFullVersion(BuildConfig.VERSION_NAME).copy(buildNumber = 0)
    val label = when {
        BuildConfig.IS_NIGHTLY -> "NIGHTLY"
        aheadOfReleases -> "BETA"
        else -> StableLabel
    }
    AnimatedContent(
        targetState = label,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.6f) + fadeIn())
                .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
        },
        label = "channelBadge",
    ) { text ->
        // the same outlined tag as every other beta mark in the app, green while this build is stable
        if (text == StableLabel) BetaBadge(label = text, color = StableGreen) else BetaBadge(label = text)
    }
}

/** the card under the version: what github said, and the one thing to do about it */
@Composable
private fun UpdateStatusCard(
    state: UpdateState,
    onUpdate: (GithubRelease) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 4 })
                    .togetherWith(fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            label = "updateStatus",
        ) { shown ->
            when (shown) {
                is UpdateState.Available -> StatusRow(
                    title = "New update!",
                    big = shortVersion(shown.release.version),
                    action = "UPDATE NOW",
                    onAction = { onUpdate(shown.release) },
                )
                UpdateState.Checking -> StatusRow(title = "Looking for updates", big = "…", pulsing = true)
                UpdateState.UpToDate -> StatusRow(title = "You're on the latest", big = "All good")
                is UpdateState.Failed -> StatusRow(
                    title = "Couldn't check for updates",
                    big = "Offline?",
                    action = "TRY AGAIN",
                    onAction = onRetry,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    big: String,
    action: String? = null,
    onAction: () -> Unit = {},
    pulsing: Boolean = false,
) {
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "statusPulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "statusPulseAlpha",
        )
    } else {
        null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(
                big,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.8).sp,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = pulse?.value ?: 1f },
            )
        }
        if (action != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                action,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .pressBounce(interaction)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

/** a row under actions: a light disc with the icon, then the label, dimmed ones can't be tapped */
@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    spinning: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentAlpha = if (enabled) 1f else 0.4f
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
            }
        } else {
            // finish the turn it's on instead of snapping back
            val rest = (spin.value / 360f).let { kotlin.math.ceil(it) * 360f }
            spin.animateTo(rest, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface.copy(alpha = if (enabled) 1f else 0.6f))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFDADADA)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(0xFF1C1C1E),
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value },
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = Color.White.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(hint, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

const val UpdateHeadline = "Theres an update~!"

@Composable
private fun ColumnScope.UpdateDetailContent(
    release: GithubRelease,
    onBack: () -> Unit,
    onLocked: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    val startInstall: (java.io.File) -> Unit = { file ->
        if (!installApk(context, file)) {
            error = "Allow \"Install unknown apps\" for mochy, then tap Install here again."
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_mochy_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = release.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(text = "Changelog", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        ChangelogMarkdown(
            markdown = release.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strongColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "To Be Installed -",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(14.dp)
    ) {
        AppIconImage(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.apkName ?: "mochy.apk",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${BuildConfig.APPLICATION_ID} · ${formatBytes(release.apkSizeBytes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    error?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(18.dp))

    val animatedProgress by animateFloatAsState(
        targetValue = progress.floatValue,
        animationSpec = tween(durationMillis = 120),
        label = "downloadProgress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .clickable(
                enabled = !downloading && release.apkUrl != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    error = null
                    if (isDownloaded(context, release)) {
                        startInstall(apkFileFor(context, release))
                    } else {
                        downloading = true
                        onLocked(true)
                        progress.floatValue = 0f
                        scope.launch {
                            val result = downloadApk(context, release) { progress.floatValue = it }
                            downloading = false
                            onLocked(false)
                            result.fold(
                                onSuccess = { startInstall(it) },
                                onFailure = { error = it.message ?: "Download failed" },
                            )
                        }
                    }
                },
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        Text(
            text = when {
                downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                release.apkUrl == null -> "No APK in this release"
                else -> "Install here"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { context.openUrl(release.htmlUrl) },
            )
    ) {
        Text(text = "Open the web", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    }
}

internal fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
internal fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        }.getOrNull()
    }

    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MenuSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mochy_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}
