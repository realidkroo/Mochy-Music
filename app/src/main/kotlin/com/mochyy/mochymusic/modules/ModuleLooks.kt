// puts a look module's values on mochy's look settings, and gives the user's own back when it goes off

package com.mochyy.mochymusic.modules

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.BlurQualityKey
import com.mochyy.mochymusic.constants.BlurStrengthPxKey
import com.mochyy.mochymusic.constants.DynamicThemeKey
import com.mochyy.mochymusic.constants.ElementBlurKey
import com.mochyy.mochymusic.constants.EnableProgressiveBlurKey
import com.mochyy.mochymusic.constants.FlagshipBackgroundStyleKey
import com.mochyy.mochymusic.constants.HomeStyle
import com.mochyy.mochymusic.constants.HomeStyleKey
import com.mochyy.mochymusic.constants.IpodTheme
import com.mochyy.mochymusic.constants.IpodThemeKey
import com.mochyy.mochymusic.constants.IpodWheelColor
import com.mochyy.mochymusic.constants.IpodWheelColorKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerBackgroundStyleKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.constants.PlaylistStyle
import com.mochyy.mochymusic.constants.PlaylistStyleKey
import com.mochyy.mochymusic.constants.PureBlackKey
import com.mochyy.mochymusic.constants.SelectedThemeColorKey
import com.mochyy.mochymusic.constants.ThumbnailCornerRadiusKey
import com.mochyy.mochymusic.module.LookSetting
import com.mochyy.mochymusic.modules.skin.parseHexColor
import com.mochyy.mochymusic.utils.dataStore
import org.json.JSONObject

internal class ModuleLooks(private val context: Context) {

    // what each module replaced, kept apart from the settings so it outlives the app closing
    private val backups = context.getSharedPreferences("module_looks", Context.MODE_PRIVATE)

    fun isApplied(id: String): Boolean = backups.contains(id)

    suspend fun apply(id: String, look: Map<String, String>) {
        if (isApplied(id)) return
        val replaced = JSONObject()
        context.dataStore.edit { prefs ->
            look.forEach { (name, raw) ->
                val setting = Settings[name] ?: return@forEach
                val previous = setting.read(prefs)
                if (setting.write(prefs, raw)) replaced.put(name, previous ?: JSONObject.NULL)
            }
        }
        backups.edit { putString(id, replaced.toString()) }
    }

    suspend fun restore(id: String, look: Map<String, String>) {
        val replaced = backups.getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        context.dataStore.edit { prefs ->
            replaced.keys().forEach { name ->
                val setting = Settings[name] ?: return@forEach
                // changed by hand since the look went on: that's the user's choice now, it stays
                if (setting.read(prefs) != setting.normalise(look[name])) return@forEach
                if (replaced.isNull(name)) setting.clear(prefs) else setting.write(prefs, replaced.getString(name))
            }
        }
        backups.edit { remove(id) }
    }

    /** one look setting, the module's text turned into the setting's own type, anything out of range turned away */
    private class Setting<T : Any>(val key: Preferences.Key<T>, val parse: (String) -> T?) {
        fun read(prefs: Preferences): String? = prefs[key]?.toString()
        fun normalise(raw: String?): String? = raw?.let(parse)?.toString()
        fun clear(prefs: MutablePreferences) {
            prefs.remove(key)
        }
        fun write(prefs: MutablePreferences, raw: String): Boolean {
            val value = parse(raw) ?: return false
            prefs[key] = value
            return true
        }
    }

    private companion object {
        inline fun <reified E : Enum<E>> enumName(raw: String): String? =
            raw.trim().uppercase().takeIf { name -> enumValues<E>().any { it.name == name } }

        fun bool(raw: String): Boolean? = raw.trim().lowercase().toBooleanStrictOrNull()

        fun number(raw: String, range: ClosedFloatingPointRange<Float>): Float? = raw.trim().toFloatOrNull()?.takeIf { it in range }

        val Settings: Map<String, Setting<*>> = mapOf(
            LookSetting.PLAYER_STYLE to Setting(PlayerStyleKey) { enumName<PlayerStyle>(it) },
            LookSetting.HOME_STYLE to Setting(HomeStyleKey) { enumName<HomeStyle>(it) },
            LookSetting.PLAYLIST_STYLE to Setting(PlaylistStyleKey) { enumName<PlaylistStyle>(it) },
            LookSetting.COVER_ROUNDING to Setting(ThumbnailCornerRadiusKey) { number(it, 0f..45f) },
            LookSetting.LYRICS_MOTION to Setting(LyricsMotionStyleKey) { enumName<LyricsMotionStyle>(it) },
            LookSetting.BLUR_QUALITY to Setting(BlurQualityKey) { enumName<BlurQuality>(it) },
            LookSetting.BLUR_STRENGTH to Setting(BlurStrengthPxKey) { number(it, 10f..50f) },
            LookSetting.PROGRESSIVE_BLUR to Setting(EnableProgressiveBlurKey) { bool(it) },
            LookSetting.ELEMENT_BLUR to Setting(ElementBlurKey) { bool(it) },
            LookSetting.PURE_BLACK to Setting(PureBlackKey) { bool(it) },
            LookSetting.ACCENT_COLOR to Setting(SelectedThemeColorKey) { parseHexColor(it)?.toArgb() },
            LookSetting.DYNAMIC_THEME to Setting(DynamicThemeKey) { bool(it) },
            LookSetting.PLAYER_BACKGROUND to Setting(PlayerBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.FLAGSHIP_BACKGROUND to Setting(FlagshipBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.IPOD_THEME to Setting(IpodThemeKey) { enumName<IpodTheme>(it) },
            LookSetting.IPOD_WHEEL to Setting(IpodWheelColorKey) { enumName<IpodWheelColor>(it) },
        )
    }
}
