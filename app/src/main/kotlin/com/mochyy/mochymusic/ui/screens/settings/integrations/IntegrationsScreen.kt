// mochy → settings → integrations: a row per service, always all of them, tapping one opens its own page

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.backup.BackupRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute

const val IntegrationsRoute = "integrations_settings"

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

// backup has no brand of its own, so its white cloud sits on a plain blue tile
private val BackupBlue = Color(0xFF2F7CF6)

@Composable
fun IntegrationsScreen(navController: NavController) {
    SubSettingsScaffold(
        title = stringResource(R.string.integrations),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegrationRow(
                name = "Discord",
                description = stringResource(R.string.integration_discord_desc),
                icon = R.drawable.integration_discord,
                onClick = { navController.navigate(DiscordRoute) },
            )
            IntegrationRow(
                name = ScrobbleSite.LASTFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LASTFM.label),
                icon = R.drawable.integration_lastfm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LASTFM)) },
            )
            IntegrationRow(
                name = ScrobbleSite.LIBREFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LIBREFM.label),
                icon = R.drawable.integration_librefm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LIBREFM)) },
            )
            IntegrationRow(
                name = stringResource(R.string.module_youtube_name),
                description = stringResource(R.string.module_youtube_desc),
                icon = R.drawable.provider_youtube_music,
                iconBackground = YouTubeMusicRed,
                onClick = { navController.navigate(YouTubeSyncRoute) },
            )
            IntegrationRow(
                name = stringResource(R.string.integration_backup_name),
                description = stringResource(R.string.integration_backup_desc),
                icon = R.drawable.backup,
                iconBackground = BackupBlue,
                iconPadding = 7.dp,
                onClick = { navController.navigate(BackupRoute) },
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
