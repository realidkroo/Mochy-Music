// puts the values of every look module that's on onto mochy's look settings, and gives the user's own back once no look sets them

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
import com.mochyy.mochymusic.constants.ChromeGlassStyle
import com.mochyy.mochymusic.constants.ChromeGlassStyleKey
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal class ModuleLooks(private val context: Context) {

    // for each setting the looks hold, the user's own value from before and what the looks last wanted, kept apart from the settings so it outlives the app closing
    private val backups = context.getSharedPreferences("module_looks", Context.MODE_PRIVATE)

    // two syncs at once would each read the other's half-written holds
    private val lock = Mutex()

    /** puts the looks that are on onto mochy's settings, the first in [looks] winning where two set the same one, and gives back the user's own where none does anymore */
    suspend fun sync(looks: List<Map<String, String>>) = lock.withLock {
        val wanted = LinkedHashMap<String, String>()
        looks.forEach { look ->
            look.forEach { (name, raw) -> if (name !in wanted && Settings[name]?.normalise(raw) != null) wanted[name] = raw }
        }
        val held = readHolds()
        context.dataStore.edit { prefs ->
            migrateOldBackups(prefs, held)
            (held.keys - wanted.keys).forEach { name ->
                val hold = held.remove(name) ?: return@forEach
                val setting = Settings[name] ?: return@forEach
                // changed by hand since a look set it: that's the user's choice now, it stays
                if (setting.read(prefs) != hold.wanted) return@forEach
                if (hold.own == null) setting.clear(prefs) else setting.restore(prefs, hold.own)
            }
            wanted.forEach { (name, raw) ->
                val setting = Settings.getValue(name)
                val value = setting.normalise(raw) ?: return@forEach
                val hold = held[name]
                // the same as last time and the user has changed it by hand since: theirs stays until a look asks for something new
                if (hold != null && hold.wanted == value) return@forEach
                held[name] = Hold(own = if (hold != null) hold.own else setting.read(prefs), wanted = value)
                setting.write(prefs, raw)
            }
        }
        writeHolds(held)
    }

    private class Hold(val own: String?, val wanted: String)

    private fun readHolds(): MutableMap<String, Hold> {
        val saved = backups.getString(HoldsKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return mutableMapOf()
        return saved.keys().asSequence().mapNotNull { name ->
            val hold = saved.optJSONObject(name) ?: return@mapNotNull null
            name to Hold(own = hold.optString("own").takeUnless { hold.isNull("own") }, wanted = hold.optString("wanted"))
        }.toMap(mutableMapOf())
    }

    private fun writeHolds(held: Map<String, Hold>) {
        val json = JSONObject()
        held.forEach { (name, hold) -> json.put(name, JSONObject().put("own", hold.own ?: JSONObject.NULL).put("wanted", hold.wanted)) }
        backups.edit {
            clear()
            putString(HoldsKey, json.toString())
        }
    }

    // before several looks could be on, each kept its own backup under its id; those turn into holds once, assuming the look's values are still in place
    private fun migrateOldBackups(prefs: Preferences, held: MutableMap<String, Hold>) {
        backups.all.keys.filter { it != HoldsKey }.forEach { id ->
            val replaced = backups.getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            replaced.keys().forEach { name ->
                val now = Settings[name]?.read(prefs) ?: return@forEach
                if (name !in held) held[name] = Hold(own = replaced.optString(name).takeUnless { replaced.isNull(name) }, wanted = now)
            }
        }
    }

    /** one look setting, the module's text turned into the setting's own type, anything out of range turned away; [saved] reads back what [read] gave */
    private class Setting<T : Any>(val key: Preferences.Key<T>, val saved: ((String) -> T?)? = null, val parse: (String) -> T?) {
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
        fun restore(prefs: MutablePreferences, own: String) {
            (saved ?: parse)(own)?.let { prefs[key] = it }
        }
    }

    private companion object {
        const val HoldsKey = "held"

        inline fun <reified E : Enum<E>> enumName(raw: String): String? =
            raw.trim().uppercase().takeIf { name -> enumValues<E>().any { it.name == name } }

        fun bool(raw: String): Boolean? = raw.trim().lowercase().toBooleanStrictOrNull()

        fun number(raw: String, range: ClosedFloatingPointRange<Float>): Float? = raw.trim().toFloatOrNull()?.takeIf { it in range }

        val Settings: Map<String, Setting<*>> = mapOf(
            LookSetting.PLAYER_STYLE to Setting(PlayerStyleKey) { PlayerStyle.fromStored(it.trim().uppercase())?.name ?: enumName<PlayerStyle>(it) },
            LookSetting.HOME_STYLE to Setting(HomeStyleKey) { enumName<HomeStyle>(it) },
            LookSetting.PLAYLIST_STYLE to Setting(PlaylistStyleKey) { enumName<PlaylistStyle>(it) },
            LookSetting.COVER_ROUNDING to Setting(ThumbnailCornerRadiusKey) { number(it, 0f..45f) },
            LookSetting.LYRICS_MOTION to Setting(LyricsMotionStyleKey) { enumName<LyricsMotionStyle>(it) },
            LookSetting.BLUR_QUALITY to Setting(BlurQualityKey) { enumName<BlurQuality>(it) },
            LookSetting.BLUR_STRENGTH to Setting(BlurStrengthPxKey) { number(it, 10f..50f) },
            LookSetting.PROGRESSIVE_BLUR to Setting(EnableProgressiveBlurKey) { bool(it) },
            LookSetting.ELEMENT_BLUR to Setting(ElementBlurKey) { bool(it) },
            LookSetting.PURE_BLACK to Setting(PureBlackKey) { bool(it) },
            // the user's own accent is kept as a number, not as #RRGGBB, so it reads back as one
            LookSetting.ACCENT_COLOR to Setting(SelectedThemeColorKey, saved = String::toIntOrNull) { parseHexColor(it)?.toArgb() },
            LookSetting.DYNAMIC_THEME to Setting(DynamicThemeKey) { bool(it) },
            LookSetting.PLAYER_BACKGROUND to Setting(PlayerBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.FLAGSHIP_BACKGROUND to Setting(FlagshipBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.IPOD_THEME to Setting(IpodThemeKey) { enumName<IpodTheme>(it) },
            LookSetting.IPOD_WHEEL to Setting(IpodWheelColorKey) { enumName<IpodWheelColor>(it) },
            LookSetting.BOTTOM_BAR to Setting(ChromeGlassStyleKey) { enumName<ChromeGlassStyle>(it) },
        )
    }
}
