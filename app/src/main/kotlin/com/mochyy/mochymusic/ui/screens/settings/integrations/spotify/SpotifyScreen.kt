// settings > player > spotify canvas, same cards as the discord page, cookie box sits at the bottom

package com.mochyy.mochymusic.ui.screens.settings.integrations.spotify

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.constants.SpotifyCanvasTokenKey
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationPageSubtitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordCard
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordCardTitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordColors
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordSectionTitle
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordTextCard
import com.mochyy.mochymusic.utils.rememberPreference

const val SpotifyRoute = "integrations_settings/spotify"

@Composable
fun SpotifyScreen(navController: NavController) {
    // the cover reads this same key, so no viewmodel needed here
    val (token, setToken) = rememberPreference(SpotifyCanvasTokenKey, defaultValue = "")

    SubSettingsScaffold(
        title = "Spotify Canvas",
        onBack = { navController.navigateUp() },
    ) {
        IntegrationPageSubtitle("Artist videos behind your songs")

        DiscordCard {
            DiscordCardTitle("What is this?")
            Text(
                text = "With your Spotify token, Mochy can pull Spotify Canvas, the short looping " +
                    "videos artists attach to their songs, and play them as the background in the player " +
                    "while the song is on. Songs without a canvas just keep the normal cover.",
                color = DiscordColors.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        DiscordSectionTitle("How to get your token")
        DiscordCard {
            Text(
                text = "1. open open.spotify.com in a desktop browser and log in\n" +
                    "2. devtools > application (or storage) > cookies\n" +
                    "3. copy the value of sp_dc and paste it below\n\n" +
                    "it stays on this phone, empty the box to turn it off. dont share it, its basically your login",
                color = DiscordColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
            )
        }

        DiscordSectionTitle("Token")
        DiscordTextCard(
            title = if (token.isBlank()) "sp_dc (not set)" else "sp_dc (connected)",
            value = token,
            onValueChange = { setToken(it.trim()) },
            hint = "Paste it here",
        )
        // room under the last card for the mini player
        Spacer(Modifier.height(140.dp))
    }
}
