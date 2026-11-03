// StreamInfoSheet.kt

package com.mochyy.mochymusic.ui.player.info

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.AudioNormalizationKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.HiResOutputKey
import com.mochyy.mochymusic.constants.MusicHapticsEnabledKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.playable
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.PlayingFrom
import com.mochyy.mochymusic.playback.StreamDetails
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetStack
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.ui.player.menu.SongHeader
import com.mochyy.mochymusic.ui.player.menu.rememberMenuSheetStack
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** "stream info": where the playing song comes from, and its whole road to the speaker */
@Composable
fun StreamInfoSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val service = playerConnection.service
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val song = metadata ?: return
    var showForce by remember { mutableStateOf(false) }
    val stack = LocalMenuSheetStack.current ?: rememberMenuSheetStack()

    val from by remember(song.id) { service.playingFrom.map { it[song.id] }.distinctUntilChanged() }.collectAsState(initial = null)
    val cards = rememberStatCards(service, song.id, song.duration * 1000L)

    CompositionLocalProvider(LocalMenuSheetStack provides stack) {
        MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.72f, fullDetent = 0.94f) { _ ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                InfoColumn(modifier = Modifier.widthIn(max = 600.dp)) {
                    InfoSheetTitle("Stream Info")
                    Spacer(Modifier.height(16.dp))
                    SongHeader(song)
                    InfoDivider()
                    InfoSectionTitle("Provider")
                    Spacer(Modifier.height(12.dp))
                    ProviderRow(service, from, onForce = { showForce = true })
                    NerdsDivider()
                    InfoSectionTitle("Format & about")
                    Spacer(Modifier.height(14.dp))
                    cards.forEachIndexed { index, card ->
                        if (index > 0) CardConnector()
                        StatCardView(card)
                    }
                }
            }
        }

        if (showForce) {
            ForceProviderSheet(currentProviderId = from?.providerId, onDismiss = { showForce = false })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderRow(service: MusicService, from: PlayingFrom?, onForce: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (from == null) {
            LoadingBar(width = 64.dp, height = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                LoadingBar(width = 120.dp)
                Spacer(Modifier.height(10.dp))
                LoadingBar(width = 160.dp, height = 24.dp)
            }
            return@Row
        }
        ProviderLogo(from.providerId, from.name, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = from.name,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // larping, the source swears it's handing over the master
            val larp = com.mochyy.mochymusic.ui.player.rememberLarpMode()
            val quality = if (larp != null) com.mochyy.mochymusic.ui.player.larpQualityText(larp) else from.quality
            quality?.let {
                Text(text = it, color = InfoMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            // side by side when they fit, the second one on its own line when they don't
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // only a module that publishes its code gets the star; others say nothing rather than a dead button
                val sourceUrl = service.sourceModules.module(from.providerId)?.info?.sourceUrl
                if (sourceUrl != null) {
                    InfoPill(text = "Star on GitHub", filled = false, onClick = { runCatching { uriHandler.openUri(sourceUrl) } })
                }
                // a file on the phone has nowhere else to come from
                if (from.providerId != PlayingFrom.Device) {
                    InfoPill(text = "Force change", filled = true, onClick = onForce)
                }
            }
        }
    }
}

@Composable
private fun StatCardView(card: StatCard) {
    // lines arrive as the decoder and track open; the card grows into them instead of jumping
    InfoCard(modifier = Modifier.animateContentSize(spring(stiffness = 500f))) {
        Text(
            text = card.title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
        )
        Spacer(Modifier.height(8.dp))
        card.lines.forEach { (label, value) -> StatLine(label, value) }
    }
}

/** everything the cards read, gathered from the service, audio lab and the settings */
@Composable
private fun rememberStatCards(service: MusicService, mediaId: String, durationMs: Long): List<StatCard> {
    val playerConnection = LocalPlayerConnection.current ?: return emptyList()
    val context = LocalContext.current
    val player by service.playerFlow.collectAsState()
    val details by remember(player) {
        player?.let(service::inspectorOf)?.details ?: MutableStateFlow(StreamDetails())
    }.collectAsState()
    val facts by remember(mediaId) { playerConnection.streamFacts(mediaId) }.collectAsState(initial = null)
    val format by playerConnection.currentFormat.collectAsState(initial = null)
    val from by remember(mediaId) { service.playingFrom.map { it[mediaId] } }.collectAsState(initial = null)
    val devices by service.audioOutputs.devices.collectAsState()
    val mixer by service.audioOutputs.mixer.collectAsState()
    val volume by service.playerVolume.collectAsState()
    val castName by remember { service.castConnectionHandler?.castDeviceName ?: MutableStateFlow(null) }.collectAsState()
    val casting by remember { service.castConnectionHandler?.isCasting ?: MutableStateFlow(false) }.collectAsState()
    val eq by service.audioLab.eq.collectAsState()
    val reverb by service.audioLab.reverb.collectAsState()
    val loudness by service.audioLab.loudness.collectAsState()
    val spatial by service.audioLab.spatial.collectAsState()
    val curve by service.audioLab.volume.collectAsState()
    val playback by service.audioLab.playback.collectAsState()
    val (normalize) = rememberPreference(AudioNormalizationKey, true)
    val (skipSilence) = rememberPreference(SkipSilenceKey, false)
    val (haptics) = rememberPreference(MusicHapticsEnabledKey, false)
    val (hiRes) = rememberPreference(HiResOutputKey, false)
    val (crossmixMode) = rememberPreference(CrossmixModeKey, CrossmixMode.AUTO_CROSSFADE.name)
    val systemMixerRate = remember {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    }
    val hardware by produceState<Boolean?>(initialValue = null, details.decoderName) {
        value = details.decoderName?.let { name -> withContext(Dispatchers.Default) { isHardwareDecoder(name) } }
    }

    val transitions = when (runCatching { CrossmixMode.valueOf(crossmixMode) }.getOrNull()?.playable()) {
        CrossmixMode.CROSSMIX -> "Crossmix"
        CrossmixMode.AUTO_CROSSFADE, CrossmixMode.MANUAL_CROSSFADE -> "Crossfade"
        else -> null
    }
    val input = StatsInput(
        mediaId = mediaId,
        details = details.takeIf { it.mediaId == null || it.mediaId == mediaId } ?: StreamDetails(mediaId = mediaId),
        facts = facts,
        // youtube's record only describes youtube's stream
        youtubeFormat = format?.takeIf { from?.providerId == com.mochyy.mochymusic.modules.SourceModules.YouTubeId },
        from = from,
        durationMs = durationMs,
        playerVolume = volume,
        decoderHardware = hardware,
        device = devices.firstOrNull { it.isActive },
        mixer = mixer,
        systemMixerRate = systemMixerRate,
        castingTo = castName.takeIf { casting },
        effects = EffectsSnapshot(
            equalizerBands = eq.nodes.size.takeIf { eq.enabled },
            spatial = spatial.enabled,
            mono = spatial.mono,
            reverb = reverb.enabled,
            loudness = loudness.enabled,
            volumeCurve = curve.enabled,
            normalize = normalize,
            skipSilence = skipSilence,
            haptics = haptics,
            transitions = transitions,
            speed = playback.speed,
            pitch = playback.pitch,
            hiResWanted = hiRes,
            floatAllowed = service.audioOutputs.floatAllowed,
        ),
    )
    val cards = remember(input) { buildStatCards(input) }
    // larping, the nerds get the level's gear story instead
    val larp = com.mochyy.mochymusic.ui.player.rememberLarpMode()
    return if (larp != null) com.mochyy.mochymusic.ui.player.larpStatCards(larp) else cards
}

private val hardwareDecoders = ConcurrentHashMap<String, Boolean>()

// android 10+ says outright; before that a google or c2.android software decoder gives itself away by name
private fun isHardwareDecoder(name: String): Boolean = hardwareDecoders.getOrPut(name) {
    val info = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name } }.getOrNull()
    if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        val lower = name.lowercase()
        !(lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw."))
    }
}
