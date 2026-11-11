// LinkSettings.kt

package com.mochyy.mochymusic.link

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioNormalizationKey
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.constants.AudioQualityKey
import com.mochyy.mochymusic.constants.CrossfadeDurationKey
import com.mochyy.mochymusic.constants.CrossfadeEnabledKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.DataSaverKey
import com.mochyy.mochymusic.constants.InfiniteQueueKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StudioForVideosKey
import com.mochyy.mochymusic.constants.TransportFadeEnabledKey
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey
import com.mochyy.mochymusic.eq.AudioLabStore
import com.mochyy.mochymusic.eq.EqPresets
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LinkSettingsEntryPoint {
    fun audioLabStore(): AudioLabStore
}

// the settings a controlling phone may change on this device, sent with their labels so the phone draws whatever this one has
internal class LinkSettings(context: Context) {
    private val context = context.applicationContext
    private val audioLab: AudioLabStore by lazy {
        EntryPointAccessors.fromApplication(this.context, LinkSettingsEntryPoint::class.java).audioLabStore()
    }

    // a tv has no headphones or mobile data, so those settings never show up for it
    private val available: List<LinkSetting> by lazy {
        val tv = DeviceKind.of(this.context).isTv
        all().filter { !(tv && it.phoneOnly) }
    }

    // called from a link server thread, so the wait for the settings store is fine here
    fun read(): JSONArray = runBlocking {
        val prefs = context.dataStore.data.first()
        JSONArray(available.map { it.toJson(context, prefs, audioLab) })
    }

    // false when the key is unknown here or the value doesn't fit it
    fun write(key: String, value: Any?): Boolean = runBlocking {
        val setting = available.firstOrNull { it.key == key } ?: return@runBlocking false
        // audio lab's processors are fed from the main thread everywhere else, so they are here too
        runCatching { withContext(Dispatchers.Main) { setting.write(context, audioLab, value) } }.getOrDefault(false)
    }

    private fun all(): List<LinkSetting> = listOf(
        LinkSetting.Choice(
            key = "transition",
            section = R.string.link_section_playback,
            title = R.string.link_setting_transition,
            options = { context, _ ->
                listOf(
                    CrossmixMode.OFF.name to context.getString(R.string.link_transition_off),
                    CrossmixMode.AUTO_CROSSFADE.name to context.getString(R.string.link_transition_auto),
                    CrossmixMode.MANUAL_CROSSFADE.name to context.getString(R.string.link_transition_manual),
                    CrossmixMode.CROSSMIX.name to context.getString(R.string.link_transition_crossmix),
                )
            },
            read = { prefs, _ -> transitionOf(prefs).name },
            write = { context, _, value ->
                val mode = CrossmixMode.valueOf(value)
                context.dataStore.edit { prefs ->
                    prefs[CrossmixModeKey] = mode.name
                    prefs[CrossfadeEnabledKey] = mode != CrossmixMode.OFF
                    if (mode == CrossmixMode.AUTO_CROSSFADE) prefs[CrossfadeDurationKey] = AutoCrossfadeSeconds
                }
            },
        ),
        LinkSetting.Slider(
            key = "crossfadeSeconds",
            section = R.string.link_section_playback,
            title = R.string.link_setting_crossfade_length,
            min = 1f,
            max = 20f,
            step = 1f,
            read = { prefs, _ -> (prefs[CrossfadeDurationKey] ?: AutoCrossfadeSeconds).coerceIn(1f, 20f) },
            write = { context, _, seconds ->
                context.dataStore.edit { prefs ->
                    prefs[CrossfadeDurationKey] = seconds.coerceIn(1f, 20f)
                    // auto ignores the length, so picking one means the person wants their own
                    val mode = transitionOf(prefs)
                    if (mode == CrossmixMode.AUTO_CROSSFADE || mode == CrossmixMode.OFF) {
                        prefs[CrossmixModeKey] = CrossmixMode.MANUAL_CROSSFADE.name
                        prefs[CrossfadeEnabledKey] = true
                    }
                }
            },
        ),
        LinkSetting.Switch("transportFade", R.string.link_section_playback, R.string.link_setting_transport_fade, read = { prefs, _ -> prefs[TransportFadeEnabledKey] ?: true }, write = preference(TransportFadeEnabledKey)),
        LinkSetting.Switch("skipSilence", R.string.link_section_playback, R.string.link_setting_skip_silence, read = { prefs, _ -> prefs[SkipSilenceKey] ?: false }, write = preference(SkipSilenceKey)),
        LinkSetting.Switch("normalize", R.string.link_section_playback, R.string.link_setting_normalize, read = { prefs, _ -> prefs[AudioNormalizationKey] ?: true }, write = preference(AudioNormalizationKey)),
        LinkSetting.Switch("autoplay", R.string.link_section_playback, R.string.link_setting_autoplay, read = { prefs, _ -> prefs[InfiniteQueueKey] ?: true }, write = preference(InfiniteQueueKey)),

        LinkSetting.Switch("eq", R.string.link_section_audio_lab, R.string.link_setting_eq, read = { _, lab -> lab.eq.value.enabled }, write = { _, lab, on -> lab.updateEq { it.copy(enabled = on) } }),
        LinkSetting.Choice(
            key = "eqPreset",
            section = R.string.link_section_audio_lab,
            title = R.string.link_setting_eq_preset,
            options = { _, lab -> EqPresets.BuiltIn.map { it.id to it.name } + lab.presets.value.map { it.id to it.name } },
            read = { _, lab -> lab.eq.value.presetId.orEmpty() },
            write = { _, lab, id ->
                val builtIn = EqPresets.BuiltIn.firstOrNull { it.id == id }
                val own = lab.presets.value.firstOrNull { it.id == id }
                // picking a curve from the other phone means hearing it, so the equalizer comes on with it
                lab.updateEq { eq ->
                    when {
                        builtIn != null -> eq.copy(enabled = true, nodes = builtIn.nodes, presetId = id)
                        own != null -> eq.copy(enabled = true, nodes = own.nodes, preamp = own.preamp, presetId = id)
                        else -> eq
                    }
                }
            },
        ),
        LinkSetting.Switch("loudness", R.string.link_section_audio_lab, R.string.link_setting_loudness, read = { _, lab -> lab.loudness.value.enabled }, write = { _, lab, on -> lab.updateLoudness { it.copy(enabled = on) } }),
        LinkSetting.Switch("reverb", R.string.link_section_audio_lab, R.string.link_setting_reverb, read = { _, lab -> lab.reverb.value.enabled }, write = { _, lab, on -> lab.updateReverb { it.copy(enabled = on) } }),
        LinkSetting.Switch("spatial", R.string.link_section_audio_lab, R.string.link_setting_spatial, phoneOnly = true, read = { _, lab -> lab.spatial.value.enabled }, write = { _, lab, on -> lab.updateSpatial { it.copy(enabled = on) } }),
        LinkSetting.Choice(
            key = "speed",
            section = R.string.link_section_audio_lab,
            title = R.string.link_setting_speed,
            options = { context, _ -> Speeds.map { it.toString() to context.getString(R.string.link_speed_times, speedLabel(it)) } },
            read = { _, lab -> lab.playback.value.speed.toString() },
            write = { _, lab, value -> lab.updatePlayback { it.copy(speed = value.toFloat()) } },
        ),

        LinkSetting.Choice(
            key = "audioQuality",
            section = R.string.link_section_youtube,
            title = R.string.link_setting_audio_quality,
            options = { context, _ ->
                listOf(
                    AudioQuality.AUTO.name to context.getString(R.string.link_quality_auto),
                    AudioQuality.HIGH.name to context.getString(R.string.link_quality_high),
                    AudioQuality.MEDIUM.name to context.getString(R.string.link_quality_medium),
                    AudioQuality.LOW.name to context.getString(R.string.link_quality_low),
                )
            },
            read = { prefs, _ -> prefs[AudioQualityKey] ?: AudioQuality.AUTO.name },
            write = { context, _, value -> context.dataStore.edit { it[AudioQualityKey] = AudioQuality.valueOf(value).name } },
        ),
        LinkSetting.Choice(
            key = "videoQuality",
            section = R.string.link_section_youtube,
            title = R.string.link_setting_video_quality,
            options = { context, _ ->
                VideoQualities.map { quality ->
                    quality.name to if (quality == VideoQuality.AUTO) context.getString(R.string.link_quality_auto) else context.getString(R.string.link_video_lines, quality.shortSide)
                }
            },
            read = { prefs, _ -> prefs[VideoQualityKey] ?: VideoQuality.AUTO.name },
            write = { context, _, value -> context.dataStore.edit { it[VideoQualityKey] = VideoQuality.valueOf(value).name } },
        ),
        LinkSetting.Switch("studioForVideos", R.string.link_section_youtube, R.string.link_setting_studio_for_videos, read = { prefs, _ -> prefs[StudioForVideosKey] ?: false }, write = preference(StudioForVideosKey)),
        LinkSetting.Switch("dataSaver", R.string.link_section_youtube, R.string.link_setting_data_saver, phoneOnly = true, read = { prefs, _ -> prefs[DataSaverKey] ?: false }, write = preference(DataSaverKey)),

        LinkSetting.Choice(
            key = "playerStyle",
            section = R.string.link_section_player,
            title = R.string.link_setting_player_style,
            options = { context, _ -> stylesOn(context).map { it.name to it.selectorName } },
            read = { prefs, _ -> prefs[PlayerStyleKey] ?: PlayerStyle.FLAGSHIP.name },
            write = { context, _, value ->
                val style = stylesOn(context).first { it.name == value }
                context.dataStore.edit { it[PlayerStyleKey] = style.name }
            },
        ),
    )

    private fun preference(key: Preferences.Key<Boolean>): suspend (Context, AudioLabStore, Boolean) -> Unit =
        { context, _, on -> context.dataStore.edit { it[key] = on } }

    companion object {
        private const val AutoCrossfadeSeconds = 5f
        private val Speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
        private val VideoQualities = listOf(VideoQuality.AUTO, VideoQuality.Q1080, VideoQuality.Q720, VideoQuality.Q480, VideoQuality.Q360)

        // the transition that really plays, read the way the player reads it: no saved mode means auto crossfade
        private fun transitionOf(prefs: Preferences): CrossmixMode {
            val legacy = prefs[CrossfadeEnabledKey] ?: false
            return prefs[CrossmixModeKey]?.let { runCatching { CrossmixMode.valueOf(it) }.getOrNull() }
                ?: if (legacy) CrossmixMode.MANUAL_CROSSFADE else CrossmixMode.AUTO_CROSSFADE
        }

        // a tv only draws the looks made for a big screen
        fun stylesOn(context: Context): List<PlayerStyle> =
            if (DeviceKind.of(context).isTv) PlayerStyle.entries.filter { it.availableOnTv } else PlayerStyle.entries

        private fun speedLabel(speed: Float): String =
            if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    }
}

// one setting as the phone draws it; each kind knows how to read itself from the store and write a value back
internal sealed class LinkSetting(
    val key: String,
    @StringRes val section: Int,
    @StringRes val title: Int,
    val phoneOnly: Boolean,
) {
    abstract suspend fun write(context: Context, lab: AudioLabStore, value: Any?): Boolean

    protected abstract fun fill(json: JSONObject, context: Context, prefs: Preferences, lab: AudioLabStore)

    protected abstract val type: String

    fun toJson(context: Context, prefs: Preferences, lab: AudioLabStore): JSONObject = JSONObject()
        .put("key", key)
        .put("section", context.getString(section))
        .put("title", context.getString(title))
        .put("type", type)
        .also { fill(it, context, prefs, lab) }

    class Switch(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        phoneOnly: Boolean = false,
        read: (Preferences, AudioLabStore) -> Boolean,
        write: suspend (Context, AudioLabStore, Boolean) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        private val reader = read
        private val writer = write
        override val type = "switch"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences, lab: AudioLabStore) {
            json.put("value", reader(prefs, lab))
        }

        override suspend fun write(context: Context, lab: AudioLabStore, value: Any?): Boolean {
            writer(context, lab, value as? Boolean ?: return false)
            return true
        }
    }

    class Choice(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        phoneOnly: Boolean = false,
        private val options: (Context, AudioLabStore) -> List<Pair<String, String>>,
        read: (Preferences, AudioLabStore) -> String,
        write: suspend (Context, AudioLabStore, String) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        private val reader = read
        private val writer = write
        override val type = "choice"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences, lab: AudioLabStore) {
            json.put("value", reader(prefs, lab))
            json.put("options", JSONArray(options(context, lab).map { (value, label) -> JSONObject().put("value", value).put("label", label) }))
        }

        override suspend fun write(context: Context, lab: AudioLabStore, value: Any?): Boolean {
            val picked = value as? String ?: return false
            if (options(context, lab).none { it.first == picked }) return false
            writer(context, lab, picked)
            return true
        }
    }

    class Slider(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        phoneOnly: Boolean = false,
        private val min: Float,
        private val max: Float,
        private val step: Float,
        read: (Preferences, AudioLabStore) -> Float,
        write: suspend (Context, AudioLabStore, Float) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        private val reader = read
        private val writer = write
        override val type = "seconds"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences, lab: AudioLabStore) {
            json.put("value", reader(prefs, lab).toDouble()).put("min", min.toDouble()).put("max", max.toDouble()).put("step", step.toDouble())
        }

        override suspend fun write(context: Context, lab: AudioLabStore, value: Any?): Boolean {
            val seconds = (value as? Number)?.toFloat() ?: return false
            writer(context, lab, seconds.coerceIn(min, max))
            return true
        }
    }
}
