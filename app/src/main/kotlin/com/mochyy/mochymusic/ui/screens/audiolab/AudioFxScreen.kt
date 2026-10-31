// audio lab's effects as cards, each with a live picture, a switch and options that fold away while it is off

package com.mochyy.mochymusic.ui.screens.audiolab

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioNormalizationKey
import com.mochyy.mochymusic.eq.MaxSpeed
import com.mochyy.mochymusic.eq.MinSpeed
import com.mochyy.mochymusic.eq.ReverbPresets
import com.mochyy.mochymusic.eq.audio.SpatialMode
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.AudioLabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun AudioFxScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    ListenToAudio()
    val reverb by viewModel.reverb.collectAsState()
    val loudness by viewModel.loudness.collectAsState()
    val spatial by viewModel.spatial.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val effects = activeEffects(reverb, loudness, spatial, playback, volume)

    AudioLabPage(
        title = LabTitle("Audio", "FX", firstSize = 40.sp, secondSize = 84.sp, firstDim = true),
        onBack = { navController.navigateUp() },
        art = { hidden -> FxCardStack(effects, hidden) },
    ) {
        ReverbCard(viewModel)
        Spacer(Modifier.height(14.dp))
        LoudnessCard(viewModel)
        Spacer(Modifier.height(14.dp))
        SpatialCard(viewModel)
        Spacer(Modifier.height(14.dp))
        MonoCard(viewModel)
        Spacer(Modifier.height(14.dp))
        AtmosCard(viewModel = viewModel)
        Spacer(Modifier.height(14.dp))
        SpeedCard(viewModel)
        Spacer(Modifier.height(14.dp))
        VolumeCard(viewModel)
    }
}

/** the shape every effect takes: picture, switch, then its options */
@Composable
internal fun EffectCard(
    picture: @Composable () -> Unit,
    header: @Composable () -> Unit,
    expanded: Boolean,
    options: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LabCard(padding = 14.dp) {
        LabStage(height = 132.dp) { picture() }
        Spacer(Modifier.height(10.dp))
        header()
        LabCollapsible(expanded) {
            Spacer(Modifier.height(10.dp))
            options()
        }
        footer?.invoke(this)
    }
}

internal fun pct(v: Float) = "${(v * 100).roundToInt()}%"

// ---- loudness ------------------------------------------------------------------------------

@Composable
private fun LoudnessCard(viewModel: AudioLabViewModel) {
    val loudness by viewModel.loudness.collectAsState()
    val (normalize, onNormalize) = rememberPreference(AudioNormalizationKey, defaultValue = true)
    EffectCard(
        picture = { LoudnessPicture(loudness, viewModel.fx) },
        header = {
            LabToggleRow("Loudness", "Louder and fuller, never clipping", loudness.enabled,
                { on -> viewModel.updateLoudness { it.copy(enabled = on) } })
        },
        expanded = loudness.enabled,
        options = {
            LabChips(
                chips = listOf(LabChip("gentle", "Gentle"), LabChip("punchy", "Punchy"), LabChip("night", "Night mode"), LabChip("max", "Max")),
                selectedKey = when {
                    loudness.boostDb == 2f && loudness.compression == 0.15f -> "gentle"
                    loudness.boostDb == 4f && loudness.compression == 0.3f -> "punchy"
                    loudness.boostDb == 3f && loudness.compression == 0.85f -> "night"
                    loudness.boostDb == 9f && loudness.compression == 0.5f -> "max"
                    else -> null
                },
                onSelect = { key ->
                    val (boost, comp) = when (key) {
                        "gentle" -> 2f to 0.15f
                        "night" -> 3f to 0.85f
                        "max" -> 9f to 0.5f
                        else -> 4f to 0.3f
                    }
                    viewModel.updateLoudness { it.copy(boostDb = boost, compression = comp) }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider("Boost", "+${String.format("%.1f", loudness.boostDb)} dB", loudness.boostDb,
                    { v -> viewModel.updateLoudness { it.copy(boostDb = (v * 2).roundToInt() / 2f) } }, 0f..12f)
                LabSlider("Compression", pct(loudness.compression), loudness.compression,
                    { v -> viewModel.updateLoudness { it.copy(compression = (v * 100).roundToInt() / 100f) } }, 0f..1f)
                Text(
                    "Compression lifts the quiet parts up toward the loud ones. Night mode keeps whispers and explosions close.",
                    fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        footer = {
            Spacer(Modifier.height(6.dp))
            LabToggleRow("Even out songs", "Plays every song at the same loudness", normalize, onNormalize)
            val rg by viewModel.normalize.collectAsState()
            LabCollapsible(normalize) {
                Spacer(Modifier.height(4.dp))
                LabPanel {
                    Text(
                        "REPLAYGAIN",
                        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.3.sp,
                        color = LabColors.Secondary,
                    )
                    LabChips(
                        chips = listOf(LabChip("track", "Track"), LabChip("album", "Album")),
                        selectedKey = if (rg.replayGainMode == com.mochyy.mochymusic.eq.audio.ReplayGainMode.Album) "album" else "track",
                        onSelect = { key ->
                            viewModel.updateNormalize {
                                it.copy(replayGainMode = if (key == "album") com.mochyy.mochymusic.eq.audio.ReplayGainMode.Album else com.mochyy.mochymusic.eq.audio.ReplayGainMode.Track)
                            }
                        },
                    )
                    LabSlider("Preamp", (if (rg.preampDb > 0) "+" else "") + String.format("%.1f dB", rg.preampDb), rg.preampDb,
                        { v -> viewModel.updateNormalize { it.copy(preampDb = (v * 2).roundToInt() / 2f) } }, -6f..6f)
                    Text(
                        "Songs that carry ReplayGain (Monochrome's FLACs, many of your own files) even out by it: Track keeps every song level, Album keeps an album's quiet and loud songs as mastered. YouTube songs use YouTube's loudness.",
                        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- spatial -------------------------------------------------------------------------------

private class CrossfeedLevel(val key: String, val label: String, val cutoff: Float, val feed: Float)

private val CrossfeedLevels = listOf(
    CrossfeedLevel("light", "Light", 700f, 4.5f),
    CrossfeedLevel("natural", "Natural", 700f, 6f),
    CrossfeedLevel("strong", "Strong", 650f, 9.5f),
)

@Composable
private fun SpatialCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    EffectCard(
        picture = { SpatialPicture(spatial) },
        header = {
            LabToggleRow("Spatial audio", "Takes stereo out of your head and puts it in front of you", spatial.enabled,
                { on -> viewModel.updateSpatial { it.copy(enabled = on) } })
        },
        expanded = spatial.enabled,
        options = {
            LabSegmented(
                options = listOf("Crossfeed", "Speakers"),
                selectedIndex = if (spatial.mode == SpatialMode.Crossfeed) 0 else 1,
                onSelect = { i -> viewModel.updateSpatial { it.copy(mode = if (i == 0) SpatialMode.Crossfeed else SpatialMode.Speakers) } },
                height = 42.dp,
            )
            Spacer(Modifier.height(10.dp))
            if (spatial.mode == SpatialMode.Crossfeed) {
                LabChips(
                    chips = CrossfeedLevels.map { LabChip(it.key, it.label) },
                    selectedKey = CrossfeedLevels.firstOrNull { it.cutoff == spatial.cutoffHz && it.feed == spatial.feedDb }?.key,
                    onSelect = { key ->
                        CrossfeedLevels.firstOrNull { it.key == key }?.let { l ->
                            viewModel.updateSpatial { it.copy(cutoffHz = l.cutoff, feedDb = l.feed) }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                LabPanel {
                    LabSlider("Head shadow", "${spatial.cutoffHz.roundToInt()} Hz", spatial.cutoffHz,
                        { v -> viewModel.updateSpatial { it.copy(cutoffHz = (v / 10).roundToInt() * 10f) } }, 300f..1500f)
                    LabSlider("Crossfeed", "−${String.format("%.1f", spatial.feedDb)} dB", 16f - spatial.feedDb,
                        { v -> viewModel.updateSpatial { it.copy(feedDb = ((16f - v) * 2).roundToInt() / 2f) } }, 1f..15f)
                    LabSlider("Amount", pct(spatial.amount), spatial.amount,
                        { v -> viewModel.updateSpatial { it.copy(amount = v) } }, 0f..1f)
                }
            } else {
                LabPanel {
                    LabSlider("Speaker angle", "±${spatial.speakerAngle.roundToInt()}°", spatial.speakerAngle,
                        { v -> viewModel.updateSpatial { it.copy(speakerAngle = v.roundToInt().toFloat()) } }, 10f..90f)
                    LabSlider("Amount", pct(spatial.amount), spatial.amount,
                        { v -> viewModel.updateSpatial { it.copy(amount = v) } }, 0f..1f)
                    Text(
                        "Two virtual speakers in front of a modelled head: the far ear hears each one a little later and darker, as in a room. 30° is a studio pair.",
                        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- mono --------------------------------------------------------------------------------

@Composable
private fun MonoCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = "Mono audio",
            subtitle = "Both ears hear the whole mix: for one earbud, or hearing in one ear",
            checked = spatial.mono,
            onCheckedChange = { on -> viewModel.updateSpatial { it.copy(mono = on) } },
            leadingDot = true,
        )
    }
}

// ---- dolby atmos ---------------------------------------------------------------------------

@Composable
private fun AtmosCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    val context = LocalContext.current
    var device by remember { mutableStateOf<AtmosSupport?>(null) }
    LaunchedEffect(Unit) { device = withContext(Dispatchers.Default) { atmosSupport(context) } }

    EffectCard(
        picture = { AtmosPicture(enabled = spatial.surround, renderOnHeadphones = spatial.surround) },
        header = {
            LabToggleRow(
                title = "Dolby Atmos & surround",
                subtitle = "Plays 5.1, 7.1 and Atmos beds as 3D sound on any headphones, with Mochy's head model, instead of folding them to stereo. From the next song.",
                checked = spatial.surround,
                onCheckedChange = { on -> viewModel.updateSpatial { it.copy(surround = on) } },
            )
        },
        expanded = false,
        options = {},
        footer = {
            Text(
                stringResource(R.string.atmos_streaming_note),
                fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
            device?.let { support ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SupportPill("Atmos decoder", support.decoder)
                    SupportPill("Android spatializer", support.spatializer)
                }
            }
        },
    )
}

private class AtmosSupport(val decoder: String, val spatializer: String)

/** what this phone can do with an atmos stream: decode it, and spatialise it itself */
private fun atmosSupport(context: Context): AtmosSupport {
    val decoder = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals("audio/eac3-joc", ignoreCase = true) }
        }
    }.getOrDefault(false)
    val spatializer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) spatializerState(context) else "Android 12L+"
    return AtmosSupport(if (decoder) "Yes" else "Not on this phone", spatializer)
}

@RequiresApi(Build.VERSION_CODES.S_V2)
private fun spatializerState(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val spatializer = manager.spatializer
    when {
        !spatializer.isAvailable -> "Not available"
        spatializer.isEnabled -> "On"
        else -> "Off"
    }
}.getOrDefault("Unknown")

@Composable
private fun SupportPill(label: String, value: String) {
    Column {
        Text(label.uppercase(), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
            letterSpacing = 1.sp, color = LabColors.Faint)
        Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LabColors.Secondary)
    }
}

// ---- speed & pitch -------------------------------------------------------------------------

private class SpeedPreset(val key: String, val label: String, val speed: Float, val preservePitch: Boolean, val withReverb: Boolean = false)

private val SpeedPresets = listOf(
    SpeedPreset("normal", "Normal", 1f, true),
    SpeedPreset("slowed", "Slowed", 0.85f, false),
    SpeedPreset("slowed_reverb", "Slowed + reverb", 0.85f, false, withReverb = true),
    SpeedPreset("nightcore", "Nightcore", 1.25f, false),
    SpeedPreset("0.75", "0.75×", 0.75f, true),
    SpeedPreset("1.25", "1.25×", 1.25f, true),
    SpeedPreset("1.5", "1.5×", 1.5f, true),
    SpeedPreset("2", "2×", 2f, true),
)

@Composable
private fun SpeedCard(viewModel: AudioLabViewModel) {
    val playback by viewModel.playback.collectAsState()
    val reverb by viewModel.reverb.collectAsState()
    EffectCard(
        picture = { SpeedPicture(playback) },
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playback speed", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(
                        if (playback.preservePitch) "Pitch stays where it is" else "Pitch follows the speed, like a record",
                        fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary,
                    )
                }
                StepButton("−") { viewModel.updatePlayback { it.copy(speed = ((it.speed - 0.05f) * 100).roundToInt() / 100f) } }
                Text(
                    String.format("%.2f×", playback.speed),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                StepButton("+") { viewModel.updatePlayback { it.copy(speed = ((it.speed + 0.05f) * 100).roundToInt() / 100f) } }
            }
        },
        expanded = true,
        options = {
            LabChips(
                chips = SpeedPresets.map { LabChip(it.key, it.label) },
                selectedKey = if (playback.speed == 0.85f && !playback.preservePitch) {
                    if (reverb.enabled) "slowed_reverb" else "slowed"
                } else {
                    SpeedPresets.firstOrNull { p ->
                        !p.withReverb && p.speed == playback.speed && p.preservePitch == playback.preservePitch &&
                            (!p.preservePitch || playback.pitchSemitones == 0f)
                    }?.key
                },
                onSelect = { key ->
                    val p = SpeedPresets.first { it.key == key }
                    viewModel.updatePlayback { it.copy(speed = p.speed, preservePitch = p.preservePitch, pitchSemitones = 0f) }
                    if (p.withReverb) {
                        ReverbPresets.All.firstOrNull { it.id == "hall" }?.let { hall ->
                            viewModel.updateReverb { it.withPreset(hall).copy(enabled = true) }
                        }
                    } else if (key == "slowed" && reverb.enabled) {
                        // otherwise the tap does nothing: the chip stays on slowed + reverb while the reverb plays
                        viewModel.updateReverb { it.copy(enabled = false) }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(
                    "Speed", String.format("%.2f×", playback.speed), log10(playback.speed),
                    { v -> viewModel.updatePlayback { it.copy(speed = (10f.pow(v) * 100).roundToInt() / 100f) } },
                    log10(MinSpeed)..log10(MaxSpeed),
                )
                LabToggleRow("Preserve pitch", "Keep the original key when changing speed", playback.preservePitch,
                    { on -> viewModel.updatePlayback { it.copy(preservePitch = on) } })
                LabCollapsible(playback.preservePitch) {
                    LabSlider(
                        "Pitch",
                        (if (playback.pitchSemitones > 0) "+" else "") + "${playback.pitchSemitones.roundToInt()} semitones",
                        playback.pitchSemitones,
                        { v -> viewModel.updatePlayback { it.copy(pitchSemitones = v.roundToInt().toFloat()) } },
                        -12f..12f,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LabTextAction("Reset", R.drawable.refresh, enabled = !playback.isNormal) {
                    viewModel.updatePlayback { com.mochyy.mochymusic.eq.PlaybackSettings() }
                }
            }
        },
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(LabColors.Row).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
    }
}

// ---- volume curve --------------------------------------------------------------------------

private class CurveShape(val key: String, val label: String, val exponent: Float)

private val CurveShapes = listOf(
    CurveShape("gentle", "Gentle", 1.6f),
    CurveShape("exponential", "Exponential", 2.2f),
    CurveShape("steep", "Steep", 3.2f),
)

@Composable
private fun VolumeCard(viewModel: AudioLabViewModel) {
    val volume by viewModel.volume.collectAsState()
    val playerVolume = LocalPlayerConnection.current?.service?.playerVolume?.collectAsState()
    EffectCard(
        picture = { VolumeCurvePicture(volume, playerVolume?.value ?: 0.6f) },
        header = {
            LabToggleRow("Exponential volume", "A logarithmic curve, for finer control at low volume", volume.enabled,
                { on -> viewModel.updateVolume { it.copy(enabled = on) } })
        },
        expanded = volume.enabled,
        options = {
            LabChips(
                chips = CurveShapes.map { LabChip(it.key, it.label) },
                selectedKey = CurveShapes.firstOrNull { it.exponent == volume.exponent }?.key,
                onSelect = { key -> CurveShapes.firstOrNull { it.key == key }?.let { s -> viewModel.updateVolume { it.copy(exponent = s.exponent) } } },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider("Curve", String.format("x^%.1f", volume.exponent), volume.exponent,
                    { v -> viewModel.updateVolume { it.copy(exponent = (v * 10).roundToInt() / 10f) } }, 1f..4f)
                LabSlider("Volume limit", pct(volume.limit), volume.limit,
                    { v -> viewModel.updateVolume { it.copy(limit = (v * 100).roundToInt() / 100f) } }, 0.2f..1f)
                Text(
                    "Shapes Mochy's own volume (the player's slider, fades and ducking). The limit caps how loud the app ever plays.",
                    fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}
