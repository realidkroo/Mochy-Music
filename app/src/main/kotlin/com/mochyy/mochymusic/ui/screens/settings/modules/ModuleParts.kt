// the small pieces the modules list and a module's page share: its logo, what it adds and its status line

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleOrigin
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private fun logoFor(id: String): Int? = when (id) {
    AppleMusicModule.Id -> R.drawable.provider_apple_music
    MonochromeModule.Id -> R.drawable.provider_monochrome
    else -> null
}

/** the module's own logo, or its first letter on a colour of its own when it has none */
@Composable
internal fun ModuleLogo(id: String, name: String, size: Int = 46) {
    val logo = logoFor(id)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.26f).dp))
            .background(if (logo != null) LabColors.Row else LabColors.node(id.hashCode())),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(painterResource(logo), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        } else {
            Text(
                name.trim().take(1).uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.44f).sp,
                color = Color.Black.copy(alpha = 0.72f),
            )
        }
    }
}

/** what a module brings, as small pills: songs, lyrics, a look */
@Composable
internal fun FeatureChips(adds: Set<ModuleFeature>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModuleFeature.entries.filter { it in adds }.forEach { feature ->
            val (icon, label) = when (feature) {
                ModuleFeature.AudioSource -> R.drawable.music_note to R.string.module_adds_songs
                ModuleFeature.Lyrics -> R.drawable.lyrics to R.string.module_adds_lyrics
                ModuleFeature.Look -> R.drawable.palette to R.string.module_adds_look
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LabColors.Chip)
                    .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            ) {
                Icon(painterResource(icon), null, tint = LabColors.Secondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(label), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
            }
        }
    }
}

/** on or off, and the account it's on, in one short line */
@Composable
internal fun moduleStatus(entry: ModuleEntry): String = when (val account = entry.account) {
    AccountState.SignedOut -> stringResource(R.string.module_status_signed_out)
    // a module with no account can't be signed in again, its reason is only that the server didn't answer
    is AccountState.Expired -> if (entry.module.signIn == null) account.reason else stringResource(R.string.module_status_expired, account.reason)
    is AccountState.SignedIn -> stringResource(if (entry.enabled) R.string.module_status_on else R.string.module_status_off, account.label)
}

/** where it came from and its version */
@Composable
internal fun moduleOrigin(entry: ModuleEntry): String = stringResource(
    when (entry.origin) {
        ModuleOrigin.BuiltIn -> R.string.module_origin_built_in
        is ModuleOrigin.App -> R.string.module_origin_app
        is ModuleOrigin.Web -> R.string.module_origin_web
        is ModuleOrigin.File, is ModuleOrigin.Code -> R.string.module_origin_file
    },
    entry.module.info.version,
)
