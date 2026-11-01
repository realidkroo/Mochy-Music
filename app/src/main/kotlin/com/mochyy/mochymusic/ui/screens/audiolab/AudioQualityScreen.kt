// audio lab's quality page: streaming tiers per provider, download quality, and the orders songs play and download in

package com.mochyy.mochymusic.ui.screens.audiolab

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.ModuleFeature
import com.mochyy.mochymusic.module.jiosaavn.JioSaavnModule
import com.mochyy.mochymusic.module.applemusic.AppleMusicModule
import com.mochyy.mochymusic.module.monochrome.MonochromeModule
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.constants.AudioQualityKey
import com.mochyy.mochymusic.eq.audio.AudioTap
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private class QualityOption(val quality: AudioQuality, val title: String, val description: String, val format: String)

private val YouTubeOptions = listOf(
    QualityOption(AudioQuality.AUTO, "Auto", "High on Wi-Fi, Medium on mobile data", "Adaptive"),
    QualityOption(AudioQuality.HIGH, "High", "The best the streamer has: less compression, more data", "~160 kbps · Opus"),
    QualityOption(AudioQuality.MEDIUM, "Medium", "Around 128 kbps, easy on data", "~128 kbps · AAC"),
    QualityOption(AudioQuality.LOW, "Low", "The smallest streams, for slow connections", "~50–70 kbps · Opus"),
)

/** what the picture shows: the quality of whichever provider plays first */
private class Effective(val quality: AudioQuality, val title: String, val format: String)

@Composable
fun AudioQualityScreen(navController: NavController, modules: ModulesViewModel = hiltViewModel()) {
    val (stored, onQuality) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (downloadQuality, onDownloadQuality) = rememberEnumPreference(
        com.mochyy.mochymusic.constants.DownloadQualityKey,
        defaultValue = com.mochyy.mochymusic.constants.DownloadQuality.HIGH,
    )
    // the tiers Monochrome used to serve from here belong to its module now
    val quality = if (stored.usesMonochrome) AudioQuality.HIGH else stored
    val entries by modules.entries.collectAsState()
    val order by modules.order.collectAsState()
    val downloadOrder by modules.downloadOrder.collectAsState()
    val youTubeOn by modules.youTubeEnabled.collectAsState()
    val context = LocalContext.current
    val metered = remember { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true }

    val usable = { id: String ->
        if (id == SourceModules.YouTubeId) youTubeOn
        else entries.any { it.module.info.id == id && it.enabled && it.account is AccountState.SignedIn }
    }
    // a module that's switched off isn't part of this page at all, only youtube and the modules that are on show up
    val shownModules = entries.filter { it.enabled && it.adds(ModuleFeature.AudioSource) }
    val shownOrder = order.filter { id -> id == SourceModules.YouTubeId || shownModules.any { it.id == id } }
    val shownDownloadOrder = downloadOrder.filter { id -> id == SourceModules.YouTubeId || shownModules.any { it.id == id } }
    // the first provider in the order that's on plays everything it has
    val first = order.firstOrNull(usable)
    val effective = when (first) {
        null -> Effective(AudioQuality.LOW, "Nothing is on", "Turn a provider on")
        MonochromeModule.Id -> Effective(AudioQuality.LOSSLESS, "Monochrome", "FLAC · lossless")
        AppleMusicModule.Id -> {
            val pick = entries.firstOrNull { it.module.info.id == first }?.picks?.get(AppleMusicModule.ChoiceQuality)
            if (pick == AppleMusicModule.OptionHe64) Effective(AudioQuality.MEDIUM, "Apple Music", "HE-AAC 64")
            else Effective(AudioQuality.HIGH, "Apple Music", "AAC 256")
        }
        JioSaavnModule.Id -> {
            val pick = entries.firstOrNull { it.id == first }?.picks?.get(JioSaavnModule.ChoiceQuality) ?: JioSaavnModule.Option320
            Effective(if (pick == JioSaavnModule.Option96) AudioQuality.MEDIUM else AudioQuality.HIGH, "JioSaavn", "AAC $pick")
        }
        SourceModules.YouTubeId -> {
            val tier = if (quality == AudioQuality.AUTO) (if (metered) AudioQuality.MEDIUM else AudioQuality.HIGH) else quality
            val option = YouTubeOptions.first { it.quality == tier }
            Effective(tier, "YouTube · ${option.title}", option.format)
        }
        // a module added from a link: its name, and the stream it picked when it offers a choice
        else -> {
            val entry = entries.firstOrNull { it.id == first }
            val choice = entry?.module?.choices?.firstOrNull { it.id == "quality" }
            val picked = choice?.options?.firstOrNull { it.id == entry.picks[choice.id] } ?: choice?.options?.firstOrNull()
            Effective(AudioQuality.HIGH, entry?.module?.info?.name ?: first, picked?.label ?: stringResource(R.string.quality_module_stream))
        }
    }

    AudioLabPage(
        title = LabTitle("Audio", "Quality", firstSize = 40.sp, secondSize = 64.sp),
        onBack = { navController.navigateUp() },
        subtitle = "${effective.title} · ${effective.format}",
    ) {
        LabSheetTitle("Streaming")
        LabGroup {
            ProviderCaption(
                logo = R.drawable.provider_youtube_music,
                name = "YouTube Music",
                status = when {
                    youTubeOn -> "Plays what the others don't have"
                    order.none(usable) -> "Off, and nothing else is on, so songs won't play"
                    else -> "Off: songs the others don't have are skipped"
                },
                on = youTubeOn,
            )
            YouTubeOptions.forEach { option ->
                QualityRow(option.title, option.description, selected = quality == option.quality, enabled = youTubeOn) { onQuality(option.quality) }
            }

            // each module's own qualities, under its name
            shownModules.forEach { entry ->
                val id = entry.module.info.id
                val active = entry.enabled && entry.account is AccountState.SignedIn
                Spacer(Modifier.height(6.dp))
                ProviderCaption(logo = providerLogo(id), name = entry.module.info.name, status = moduleStatus(entry), on = active)
                val qualityChoice = entry.module.choices.firstOrNull { it.id == "quality" }
                if (qualityChoice != null) {
                    val picked = entry.picks[qualityChoice.id]?.takeIf { p -> qualityChoice.options.any { it.id == p } }
                        ?: qualityChoice.options.first().id
                    qualityChoice.options.forEach { option ->
                        QualityRow(option.label, option.description, selected = picked == option.id, enabled = active) {
                            modules.setPick(id, qualityChoice.id, option.id)
                        }
                    }
                } else if (id == MonochromeModule.Id) {
                    QualityRow("Lossless", "FLAC, as Monochrome has it: CD quality or better", selected = true, enabled = active) {}
                }
                LabTextAction("${entry.module.info.name} settings", R.drawable.navigate_next) {
                    navController.navigate(com.mochyy.mochymusic.ui.screens.settings.modules.moduleRoute(id))
                }
            }
        }

        // what a download from youtube is saved as; the download order below picks where each download comes from
        Spacer(Modifier.height(24.dp))
        LabSheetTitle("Download")
        LabGroup {
            com.mochyy.mochymusic.constants.DownloadQuality.entries.forEach { option ->
                QualityRow(option.title, option.description, selected = downloadQuality == option, enabled = true) {
                    onDownloadQuality(option)
                }
            }
        }
        Footnote(stringResource(R.string.quality_download_footnote))

        Spacer(Modifier.height(24.dp))
        LabSheetTitle("Provider")
        ProviderOrder(
            order = shownOrder,
            name = modules::nameOf,
            logo = ::providerLogo,
            usable = usable,
            status = { id ->
                if (id == SourceModules.YouTubeId) {
                    if (youTubeOn) "On" else "Off, tap to turn on"
                } else {
                    entries.firstOrNull { it.module.info.id == id }?.let(::moduleStatus) ?: "Not installed"
                }
            },
            onTap = { id ->
                // youtube switches right here; a module's switch is on its own page, switching it off here would make it vanish under the finger
                if (id == SourceModules.YouTubeId) {
                    modules.setEnabled(id, !youTubeOn)
                } else {
                    navController.navigate(com.mochyy.mochymusic.ui.screens.settings.modules.moduleRoute(id))
                }
            },
            onReorder = modules::setVisibleOrder,
        )
        Footnote(stringResource(R.string.quality_provider_footnote))

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.quality_download_order))
        val youTubeForDownloads = stringResource(R.string.quality_download_order_youtube)
        ProviderOrder(
            order = shownDownloadOrder,
            name = modules::nameOf,
            logo = ::providerLogo,
            usable = { id -> id == SourceModules.YouTubeId || usable(id) },
            status = { id ->
                if (id == SourceModules.YouTubeId) {
                    youTubeForDownloads
                } else {
                    entries.firstOrNull { it.module.info.id == id }?.let(::moduleStatus) ?: "Not installed"
                }
            },
            // youtube is always there for downloads, so only a module's row has somewhere to go
            onTap = { id ->
                if (id != SourceModules.YouTubeId) navController.navigate(com.mochyy.mochymusic.ui.screens.settings.modules.moduleRoute(id))
            },
            onReorder = modules::setVisibleDownloadOrder,
        )
        Footnote(stringResource(R.string.quality_download_order_footnote))
    }
}

private fun moduleStatus(entry: com.mochyy.mochymusic.modules.ModuleEntry): String = when {
    entry.account is AccountState.SignedOut -> "Sign in to play from it"
    entry.account is AccountState.Expired -> "Sign in again"
    entry.enabled -> "On"
    else -> "Off, tap to turn on"
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
        modifier = Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp),
    )
}

internal fun providerLogo(id: String): Int? = when (id) {
    AppleMusicModule.Id -> R.drawable.provider_apple_music
    MonochromeModule.Id -> R.drawable.provider_monochrome
    SourceModules.YouTubeId -> R.drawable.provider_youtube_music
    else -> null
}

/** a provider's name over its qualities in the streaming group, dimmed while it's off */
@Composable
private fun ProviderCaption(logo: Int?, name: String, status: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.graphicsLayer { alpha = if (on) 1f else 0.45f }) { ProviderLogo(logo, size = 24, name = name) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Text(status, fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 14.sp, color = LabColors.Secondary)
        }
    }
}

// a provider with no logo of its own (jiosaavn, a module from a link) shows its first letter instead of a blank tile
@Composable
internal fun ProviderLogo(logo: Int?, size: Int, name: String? = null) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(Color(0xFFD9D9D9)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            androidx.compose.foundation.Image(
                painterResource(logo), null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        } else if (name != null) {
            Text(name.take(1).uppercase(), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = (size * 0.44f).sp, color = Color.Black.copy(alpha = 0.7f))
        }
    }
}

@Composable
internal fun QualityRow(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF2A2A2E) else Color(0xFF111113), tween(200), label = "row")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(250), label = "enabled")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "check")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(description, fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 14.sp, color = LabColors.Secondary)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = check; scaleY = check; this.alpha = check },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
