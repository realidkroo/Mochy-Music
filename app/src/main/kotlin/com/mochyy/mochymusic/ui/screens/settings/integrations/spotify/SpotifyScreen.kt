// settings > player > spotify canvas, same cards as the discord page, signing in reuses spotify import's own sign-in page

package com.mochyy.mochymusic.ui.screens.settings.integrations.spotify

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.canvas.SpotifyCanvasProvider
import com.mochyy.mochymusic.constants.SpotifyCanvasKey
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.importsync.CaptureWebView
import com.mochyy.mochymusic.ui.screens.settings.importsync.clearWebSession
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationPageSubtitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordCard
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordCardTitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordColors
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordSectionTitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordSwitchCard
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SpotifyRoute = "integrations_settings/spotify"

@Composable
fun SpotifyScreen(navController: NavController) {
    val context = LocalContext.current
    // the canvas lookup reads this same key, so no viewmodel needed here
    val (enabled, setEnabled) = rememberPreference(SpotifyCanvasKey, defaultValue = false)
    var signingIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    // the cookie store can wake the whole web view engine, so it's asked off the main thread
    LaunchedEffect(signingIn) {
        if (!signingIn) signedIn = withContext(Dispatchers.IO) { SpotifyCanvasProvider.signedInCookie() != null }
    }

    if (signingIn) {
        val spotify = AccountService.SPOTIFY
        BackHandler { signingIn = false }
        CaptureWebView(
            title = stringResource(R.string.spotify_canvas_sign_in),
            subtitle = stringResource(R.string.spotify_canvas_sign_in_subtitle),
            startUrl = spotify.loginUrl.orEmpty(),
            playerOrigins = spotify.playerOrigins,
            desktopSite = spotify.desktopSite,
            onHeader = { _, _, _ -> },
            onTick = {
                // spotify sets its sign-in cookie once the login is through, that's all we wait for
                if (SpotifyCanvasProvider.signedInCookie() != null) {
                    signingIn = false
                    SpotifyCanvasProvider.warmUp(context)
                }
            },
            onExit = { signingIn = false },
        )
        return
    }

    SubSettingsScaffold(
        title = stringResource(R.string.spotify_canvas),
        onBack = { navController.navigateUp() },
    ) {
        IntegrationPageSubtitle(stringResource(R.string.spotify_canvas_subtitle))

        DiscordCard {
            DiscordCardTitle(stringResource(R.string.spotify_canvas_what_title))
            Text(
                text = stringResource(R.string.spotify_canvas_what),
                color = DiscordColors.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        DiscordSwitchCard(
            title = stringResource(R.string.spotify_canvas_use),
            description = stringResource(R.string.spotify_canvas_use_desc),
            checked = enabled,
            onCheckedChange = { on ->
                setEnabled(on)
                if (on) SpotifyCanvasProvider.warmUp(context)
            },
        )

        DiscordSectionTitle(stringResource(R.string.spotify_canvas_account))
        DiscordCard {
            DiscordCardTitle(stringResource(if (signedIn) R.string.spotify_canvas_signed_in else R.string.spotify_canvas_signed_out))
        }
        Spacer(Modifier.height(12.dp))
        AccountButton(
            signedIn = signedIn,
            onSignIn = { signingIn = true },
            onSignOut = {
                clearWebSession(AccountService.SPOTIFY.webOrigins)
                SpotifyCanvasProvider.forget()
                signedIn = false
            },
        )
        Text(
            text = stringResource(R.string.spotify_canvas_note),
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 8.dp, end = 8.dp),
        )
        // room under the last card for the mini player
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun AccountButton(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (signedIn) DiscordColors.Card else Color.White)
            .clickable(onClick = if (signedIn) onSignOut else onSignIn),
    ) {
        Text(
            text = stringResource(if (signedIn) R.string.spotify_canvas_sign_out else R.string.spotify_canvas_sign_in),
            color = if (signedIn) Color.White else Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
