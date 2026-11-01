// mochy → settings → integrations → discord: log in in the browser, then shape the status friends see with a live preview

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.discord.DiscordLogin
import com.mochyy.mochymusic.discord.NowPlaying
import com.mochyy.mochymusic.discord.PresenceConnection
import com.mochyy.mochymusic.discord.PresenceImage
import com.mochyy.mochymusic.discord.PresenceKind
import com.mochyy.mochymusic.discord.PresenceSettings
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationPageSubtitle
import com.mochyy.mochymusic.viewmodels.DiscordViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.utils.stableStatusBars

const val DiscordRoute = "integrations_settings/discord"

/** where SubSettingsScaffold's header ends; the preview counts as gone once it's under it */
private val HeaderHeight = 118.dp

@Composable
fun DiscordScreen(
    navController: NavController,
    viewModel: DiscordViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    val login by viewModel.login.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val playing by viewModel.nowPlaying.collectAsState()
    // nothing playing: show a made-up song so the preview still means something
    val sample = remember { NowPlaying.sample(System.currentTimeMillis()) }
    val track = playing ?: sample

    LaunchedEffect(signedIn) {
        if (signedIn) viewModel.refreshProfile()
    }
    LaunchedEffect(login) {
        (login as? DiscordLogin.Failed)?.let {
            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            viewModel.dismissProblem()
        }
    }
    // back from the browser without an answer, give the redirect a moment to land before the button goes back to log in
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(1_500)
            viewModel.cancelWaiting()
        }
    }

    fun openLogin() {
        scope.launch {
            val uri = viewModel.loginUri()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: android.content.ActivityNotFoundException) {
                viewModel.cancelWaiting()
                Toast.makeText(context, "There's no browser to open Discord's login page in.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val headerBottomPx = with(density) { (statusBarTop + HeaderHeight).toPx() }
    var previewBottomPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val previewHidden by remember(headerBottomPx) { derivedStateOf { previewBottomPx < headerBottomPx } }
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        SubSettingsScaffold(
            title = "Discord",
            onBack = { navController.navigateUp() },
        ) {
            IntegrationPageSubtitle("Rich presence on your profile")
            DiscordToggleCard(
                checked = settings.enabled,
                onCheckedChange = { on -> viewModel.edit { copy(enabled = on) } },
                status = statusLine(viewModel.isConfigured, signedIn, settings.enabled, connection, login),
                enabled = signedIn,
            )
            Spacer(Modifier.height(12.dp))
            DiscordProfileCard(
                profile = profile.takeIf { signedIn },
                track = track,
                settings = settings,
                modifier = Modifier.onGloballyPositioned { previewBottomPx = it.boundsInWindow().bottom },
            )
            Spacer(Modifier.height(12.dp))
            AccountButton(
                configured = viewModel.isConfigured,
                signedIn = signedIn,
                login = login,
                onLogin = ::openLogin,
                onLogOut = viewModel::logOut,
            )
            CustomizeSection(settings = settings, edit = viewModel::edit)
            // room for the floating preview over the last card
            Spacer(Modifier.height(220.dp))
        }

        DiscordFloatingPreview(
            visible = previewHidden,
            track = track,
            settings = settings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomInset + 12.dp),
        )
    }
}

private fun statusLine(
    configured: Boolean,
    signedIn: Boolean,
    enabled: Boolean,
    connection: PresenceConnection,
    login: DiscordLogin,
): String = when {
    !configured -> "Not set up in this build of Mochy"
    login == DiscordLogin.Finishing -> "Signing in…"
    !signedIn -> "Log in below to turn it on"
    !enabled -> "Off, so your profile shows nothing from Mochy"
    connection == PresenceConnection.Live -> "On your profile now"
    connection == PresenceConnection.Connecting -> "Connecting to Discord…"
    connection == PresenceConnection.Retrying -> "Can't reach Discord. Trying again…"
    else -> "Shows up when you play something"
}

@Composable
private fun AccountButton(
    configured: Boolean,
    signedIn: Boolean,
    login: DiscordLogin,
    onLogin: () -> Unit,
    onLogOut: () -> Unit,
) {
    val (text, action) = when {
        !configured -> "Discord isn't set up in this build" to null
        signedIn -> "Log out from Discord" to onLogOut
        login == DiscordLogin.Finishing -> "Signing in…" to null
        login == DiscordLogin.InBrowser -> "Waiting for Discord… Tap to open it again" to onLogin
        else -> "Log in with Discord" to onLogin
    }
    val inviting = configured && !signedIn
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (inviting) DiscordColors.Blurple else DiscordColors.Profile)
            .clickable(enabled = action != null) { action?.invoke() },
    ) {
        Text(
            text = text,
            color = if (action != null) Color.White else DiscordColors.Secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (inviting) {
        Text(
            text = "Opens Discord's own login page in your browser. Your password stays with Discord.",
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CustomizeSection(
    settings: PresenceSettings,
    edit: (PresenceSettings.() -> PresenceSettings) -> Unit,
) {
    val gap = Modifier.height(12.dp)

    DiscordSectionTitle("CUSTOMIZE")
    DiscordPlaceholderCard()

    DiscordSectionTitle("Options")
    DiscordCard {
        DiscordCardTitle("Refresh interval")
        Spacer(Modifier.height(4.dp))
        DiscordStepSlider(
            value = settings.refreshSeconds,
            values = (PresenceSettings.MinRefreshSeconds..PresenceSettings.MaxRefreshSeconds).toList(),
            label = { "${it}s" },
            onValueChange = { seconds -> edit { copy(refreshSeconds = seconds) } },
        )
    }

    DiscordSectionTitle(stringResource(R.string.discord_behaviour))
    DiscordSwitchCard(
        title = stringResource(R.string.discord_clear_on_pause),
        description = stringResource(R.string.discord_clear_on_pause_desc),
        checked = settings.clearOnPause,
        onCheckedChange = { on -> edit { copy(clearOnPause = on) } },
    )

    DiscordSectionTitle("Activity")
    DiscordCard {
        DiscordCardTitle("Select the activity type")
        Spacer(Modifier.height(10.dp))
        DiscordSegmented(
            options = PresenceKind.entries,
            selected = settings.kind,
            label = { it.label },
            onSelect = { kind -> edit { copy(kind = kind) } },
        )
    }
    Spacer(gap)
    DiscordTextCard("Activity name", settings.name, { text -> edit { copy(name = text) } }, hint = "Mochy")

    DiscordSectionTitle("Strings customisation")
    DiscordNoticeCard("Empty a box to hide that line")
    Spacer(gap)
    DiscordTextCard("Details string", settings.details, { text -> edit { copy(details = text) } })
    Spacer(gap)
    DiscordTextCard("State string", settings.state, { text -> edit { copy(state = text) } })
    Spacer(gap)
    DiscordTextCard("Large image hover text", settings.largeText, { text -> edit { copy(largeText = text) } })
    Spacer(gap)
    DiscordTextCard("Small image hover text", settings.smallText, { text -> edit { copy(smallText = text) } })

    DiscordSectionTitle("Image customisation")
    DiscordImageSourceCard(
        title = "Small image source",
        selected = settings.smallImage,
        options = PresenceImage.ForSmall,
        onSelect = { image -> edit { copy(smallImage = image) } },
        spin = settings.spin,
        onSpinChange = { spin -> edit { copy(spinSeconds = spin.secondsPerTurn, spinFps = spin.fps) } },
    )
    Spacer(gap)
    DiscordImageSourceCard(
        title = "Big image source",
        selected = settings.largeImage,
        options = PresenceImage.ForLarge,
        onSelect = { image -> edit { copy(largeImage = image) } },
    )

    DiscordSectionTitle("Buttons")
    DiscordButtonCard("1st button", settings.button1) { button -> edit { copy(button1 = button) } }
    Spacer(gap)
    DiscordButtonCard("2nd button", settings.button2) { button -> edit { copy(button2 = button) } }
    Text(
        text = "Discord only shows the buttons to other people, never on your own profile.",
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}
