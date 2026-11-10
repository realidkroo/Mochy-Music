// mochy → settings → integrations → discord: log in in the browser, then shape the status friends see with a live preview

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.viewmodels.DiscordViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                Toast.makeText(context, context.getString(R.string.discord_no_browser_toast), Toast.LENGTH_LONG).show()
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
            title = stringResource(R.string.discord_settings_title),
            onBack = { navController.navigateUp() },
        ) {
            IntegrationPageSubtitle(stringResource(R.string.discord_subtitle_rich_presence))
            DiscordToggleCard(
                checked = settings.enabled,
                onCheckedChange = { on -> viewModel.edit { copy(enabled = on) } },
                status = statusLine(context, viewModel.isConfigured, signedIn, settings.enabled, connection, login),
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
            CustomizeSection(
                settings = settings,
                edit = viewModel::edit,
                onPickCustom = { large, picked ->
                    scope.launch {
                        if (!viewModel.pickCustomPicture(large, picked)) {
                            Toast.makeText(context, context.getString(R.string.discord_custom_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
            // room for the floating preview over the last card
            Spacer(Modifier.height(220.dp))
        }

        DiscordFloatingPreview(
            visible = previewHidden,
            track = track,
            settings = settings,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomInset + 12.dp),
        )
    }
}

private fun statusLine(
    context: Context,
    configured: Boolean,
    signedIn: Boolean,
    enabled: Boolean,
    connection: PresenceConnection,
    login: DiscordLogin,
): String =
    when {
        !configured -> context.getString(R.string.discord_status_not_configured)
        login == DiscordLogin.Finishing -> context.getString(R.string.discord_status_signing_in)
        !signedIn -> context.getString(R.string.discord_status_login_below)
        !enabled -> context.getString(R.string.discord_status_off)
        connection == PresenceConnection.Live -> context.getString(R.string.discord_status_live)
        connection == PresenceConnection.Connecting -> context.getString(R.string.discord_status_connecting)
        connection == PresenceConnection.Retrying -> context.getString(R.string.discord_status_retrying)
        else -> context.getString(R.string.discord_status_idle_playing)
    }

@Composable
private fun AccountButton(
    configured: Boolean,
    signedIn: Boolean,
    login: DiscordLogin,
    onLogin: () -> Unit,
    onLogOut: () -> Unit,
) {
    val (text, action) =
        when {
            !configured -> stringResource(R.string.discord_btn_not_configured) to null
            signedIn -> stringResource(R.string.discord_btn_logout) to onLogOut
            login == DiscordLogin.Finishing -> stringResource(R.string.discord_status_signing_in) to null
            login == DiscordLogin.InBrowser -> stringResource(R.string.discord_btn_waiting_browser) to onLogin
            else -> stringResource(R.string.discord_btn_login) to onLogin
        }
    val inviting = configured && !signedIn
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
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
            text = stringResource(R.string.discord_login_disclaimer),
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CustomizeSection(
    settings: PresenceSettings,
    edit: (PresenceSettings.() -> PresenceSettings) -> Unit,
    // a picture picked for the big (true) or small slot's "your own picture"
    onPickCustom: (large: Boolean, picked: android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    val gap = Modifier.height(12.dp)
    val pickSmall =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
            if (picked != null) onPickCustom(false, picked)
        }
    val pickLarge =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
            if (picked != null) onPickCustom(true, picked)
        }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    DiscordSectionTitle(stringResource(R.string.discord_section_customize))
    DiscordPlaceholderCard()

    DiscordSectionTitle(stringResource(R.string.discord_section_options))
    DiscordCard {
        DiscordCardTitle(stringResource(R.string.discord_refresh_interval))
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

    DiscordSectionTitle(stringResource(R.string.discord_section_activity))
    DiscordCard {
        DiscordCardTitle(stringResource(R.string.discord_select_activity_type))
        Spacer(Modifier.height(10.dp))
        DiscordSegmented(
            options = PresenceKind.entries,
            selected = settings.kind,
            label = {
                when (it) {
                    PresenceKind.LISTENING -> context.getString(R.string.discord_kind_listening)
                    PresenceKind.PLAYING -> context.getString(R.string.discord_kind_playing)
                }
            },
            onSelect = { kind -> edit { copy(kind = kind) } },
        )
    }
    Spacer(gap)
    DiscordTextCard(
        stringResource(R.string.discord_activity_name_title),
        settings.name,
        { text -> edit { copy(name = text) } },
        hint = "Mochy",
    )

    DiscordSectionTitle(stringResource(R.string.discord_section_strings))
    DiscordNoticeCard(stringResource(R.string.discord_empty_to_hide))
    Spacer(gap)
    DiscordTextCard(stringResource(R.string.discord_details_string), settings.details, { text -> edit { copy(details = text) } })
    Spacer(gap)
    DiscordTextCard(stringResource(R.string.discord_state_string), settings.state, { text -> edit { copy(state = text) } })
    Spacer(gap)
    DiscordTextCard(stringResource(R.string.discord_large_image_hover), settings.largeText, { text -> edit { copy(largeText = text) } })
    Spacer(gap)
    DiscordTextCard(stringResource(R.string.discord_small_image_hover), settings.smallText, { text -> edit { copy(smallText = text) } })

    DiscordSectionTitle(stringResource(R.string.discord_section_image_customisation))
    DiscordImageSourceCard(
        title = stringResource(R.string.discord_small_image_source),
        selected = settings.smallImage,
        options = PresenceImage.ForSmall,
        // "your own picture" with none picked yet opens the picker first, and only switches once one is chosen
        onSelect = { image ->
            if (image == PresenceImage.CUSTOM && settings.smallCustomImage.isBlank()) {
                pickSmall.launch(imagesOnly)
            } else {
                edit { copy(smallImage = image) }
            }
        },
        spin = settings.spin,
        onSpinChange = { spin -> edit { copy(spinSeconds = spin.secondsPerTurn, spinFps = spin.fps) } },
        customPicture = settings.smallCustomImage,
        onPickCustom = { pickSmall.launch(imagesOnly) },
    )
    Spacer(gap)
    DiscordImageSourceCard(
        title = stringResource(R.string.discord_big_image_source),
        selected = settings.largeImage,
        options = PresenceImage.ForLarge,
        onSelect = { image ->
            if (image == PresenceImage.CUSTOM && settings.largeCustomImage.isBlank()) {
                pickLarge.launch(imagesOnly)
            } else {
                edit { copy(largeImage = image) }
            }
        },
        customPicture = settings.largeCustomImage,
        onPickCustom = { pickLarge.launch(imagesOnly) },
    )

    DiscordSectionTitle(stringResource(R.string.discord_section_buttons))
    DiscordButtonCard(stringResource(R.string.discord_first_button), settings.button1) { button -> edit { copy(button1 = button) } }
    Spacer(gap)
    DiscordButtonCard(stringResource(R.string.discord_second_button), settings.button2) { button -> edit { copy(button2 = button) } }
    Text(
        text = stringResource(R.string.discord_buttons_note),
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}
