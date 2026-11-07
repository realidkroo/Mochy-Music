// the small pieces the modules list and a module's page share: its icon, what it adds, its status line and dot, the section titles and the dashed slot

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleOrigin
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import java.io.File

/** the darker card a module's row and the empty slots sit on, the mock's #131313 */
internal val ModuleRowColor = Color(0xFF131314)

/** green on, grey off, amber when it needs something (signing in, a server that didn't answer) */
internal val StatusOn = Color(0xFF30D158)
internal val StatusOff = Color(0xFF5C5C63)
internal val StatusTrouble = Color(0xFFFFB340)

private fun logoFor(id: String): Int? = when (id) {
    AppleMusicModule.Id -> R.drawable.provider_apple_music
    MonochromeModule.Id -> R.drawable.provider_monochrome
    else -> null
}

/** a module's picture as coil reads it: a file the zip brought, or a link */
internal fun artModel(art: String?): Any? = art?.let { if (it.startsWith("/")) File(it) else it }

/** the module's own icon, a built-in logo, or its first letter on a colour of its own when it has neither */
@Composable
internal fun ModuleLogo(id: String, name: String, size: Int = 46, art: String? = null, corner: Float = 0.26f) {
    val logo = logoFor(id)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * corner).dp))
            .background(if (logo != null || art != null) LabColors.Row else LabColors.node(id.hashCode())),
        contentAlignment = Alignment.Center,
    ) {
        when {
            art != null -> SubcomposeAsyncImage(
                model = artModel(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp),
                // a picture that won't load still leaves the module recognisable
                error = { LetterMark(name, size) },
            )
            logo != null -> Image(painterResource(logo), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
            else -> LetterMark(name, size)
        }
    }
}

@Composable
private fun LetterMark(name: String, size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase(),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.44f).sp,
            color = Color.Black.copy(alpha = 0.72f),
        )
    }
}

/** what a module brings, as small pills: songs, lyrics, song info, a look */
@Composable
internal fun FeatureChips(adds: Set<ModuleFeature>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModuleFeature.entries.filter { it in adds }.forEach { feature ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LabColors.Chip)
                    .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            ) {
                Icon(painterResource(featureIcon(feature)), null, tint = LabColors.Secondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(featureLabel(feature)), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
            }
        }
    }
}

internal fun featureIcon(feature: ModuleFeature): Int = when (feature) {
    ModuleFeature.AudioSource -> R.drawable.music_note
    ModuleFeature.Lyrics -> R.drawable.lyrics
    ModuleFeature.Look -> R.drawable.palette
    ModuleFeature.Metadata -> R.drawable.info
}

internal fun featureLabel(feature: ModuleFeature): Int = when (feature) {
    ModuleFeature.AudioSource -> R.string.module_adds_songs
    ModuleFeature.Lyrics -> R.string.module_adds_lyrics
    ModuleFeature.Look -> R.string.module_adds_look
    ModuleFeature.Metadata -> R.string.module_adds_metadata
}

/** "Songs · Lyrics", what a module adds in a few words */
@Composable
internal fun featureSummary(adds: Set<ModuleFeature>): String =
    ModuleFeature.entries.filter { it in adds }.map { stringResource(featureLabel(it)) }.joinToString(" · ")

/** on or off, and the account it's on, in one short line */
@Composable
internal fun moduleStatus(entry: ModuleEntry): String = when (val account = entry.account) {
    AccountState.SignedOut -> stringResource(R.string.module_status_signed_out)
    // a module with no account can't be signed in again, its reason is only that the server didn't answer
    is AccountState.Expired -> if (entry.module.signIn == null) account.reason else stringResource(R.string.module_status_expired, account.reason)
    is AccountState.SignedIn -> stringResource(if (entry.enabled) R.string.module_status_on else R.string.module_status_off, account.label)
}

/** the dot beside a module's name: green while it's working for you */
internal fun statusColor(entry: ModuleEntry): Color = when {
    entry.account !is AccountState.SignedIn -> StatusTrouble
    entry.enabled -> StatusOn
    else -> StatusOff
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

/** a module only changes how mochy looks (or adds pages and menu rows), it brings nothing to play or read */
internal val ModuleEntry.isLookOnly: Boolean
    get() = adds(ModuleFeature.Look) && !adds(ModuleFeature.AudioSource) && !adds(ModuleFeature.Lyrics) && !adds(ModuleFeature.Metadata)

/** a module added from a link or a zip, the only kind that can be removed */
internal val ModuleEntry.removable: Boolean
    get() = origin is ModuleOrigin.Web || origin is ModuleOrigin.File || origin is ModuleOrigin.Code

@Composable
internal fun StatusDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** a section's big grey title, the mock's "Looks Module", with an optional small line under it */
@Composable
internal fun ModuleSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 2.dp, bottom = 14.dp)) {
        Text(
            title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp,
            letterSpacing = (-0.6).sp, color = Color(0xFFB4B4B8),
        )
        subtitle?.let {
            Text(it, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LabColors.Secondary)
        }
    }
}

/** an empty slot drawn in dashes with a square plus in it: there's room for a module here */
@Composable
internal fun DashedSlot(text: String, onClick: () -> Unit, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ModuleRowColor)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(23.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = if (compact) 16.dp else 22.dp),
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlusSquare(size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(text, fontFamily = InterFontFamily, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f))
            }
        } else {
            Text(text, fontFamily = InterFontFamily, fontSize = 19.sp, letterSpacing = (-0.3).sp, color = Color.White.copy(alpha = 0.88f))
            Spacer(Modifier.height(14.dp))
            PlusSquare(size = 52.dp)
        }
    }
}

// the mock's outlined square with a thin plus
@Composable
private fun PlusSquare(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .drawBehind {
                val stroke = 1.2.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    cornerRadius = CornerRadius(this.size.minDimension * 0.2f),
                    style = Stroke(stroke),
                )
                val arm = this.size.minDimension * 0.32f
                val c = center
                drawLine(Color.White, c.copy(x = c.x - arm), c.copy(x = c.x + arm), stroke)
                drawLine(Color.White, c.copy(y = c.y - arm), c.copy(y = c.y + arm), stroke)
            },
    )
}
