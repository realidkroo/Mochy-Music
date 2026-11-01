// the look module that's on right now, which the player's module style and the app's font follow

package com.mochyy.mochymusic.modules.skin

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.module.LookSetting
import com.mochyy.mochymusic.ui.theme.LookFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** a module that can bring a skin: its own player, a font, pages */
internal interface SkinSource {
    val skin: ModuleSkin?
}

object ActiveSkin {
    var current: ModuleSkin? by mutableStateOf(null)
        private set

    @Volatile
    private var requested: ModuleSkin? = null

    /** called on every refresh of the switches; the font loads off the main thread, then both change on it together */
    internal fun publish(skin: ModuleSkin?, scope: CoroutineScope) {
        if (skin === requested) return
        requested = skin
        scope.launch(Dispatchers.IO) {
            val family = skin?.font?.let { fontFamily(skin, it) }
            withContext(Dispatchers.Main) {
                if (requested !== skin) return@withContext
                current = skin
                LookFont.family = family
            }
        }
    }

    private fun fontFamily(skin: ModuleSkin, font: SkinFont): FontFamily? = runCatching {
        val regular = skin.fontFile(font.regular)?.takeIf { it.isFile } ?: return null
        val bold = skin.fontFile(font.bold)?.takeIf { it.isFile }
        // a broken file throws here, off the main thread, not the first time some text draws with it
        val typeface = android.graphics.Typeface.createFromFile(regular)
        // fonts from files with their weights are android 8+, older phones get the regular one and a drawn bold
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(listOfNotNull(Font(regular, FontWeight.Normal), bold?.let { Font(it, FontWeight.Bold) }))
        } else {
            FontFamily(typeface)
        }
    }.onFailure { Timber.tag("ActiveSkin").w(it, "Couldn't load the look's font") }.getOrNull()
}

/** the look as it's applied: a module with its own player switches to it, and a fixed accent turns the cover-coloured theme off */
internal fun appliedLook(look: Map<String, String>, skin: ModuleSkin?): Map<String, String> = buildMap {
    putAll(look)
    if (skin?.player != null && LookSetting.PLAYER_STYLE !in look) put(LookSetting.PLAYER_STYLE, PlayerStyle.MODULE.name)
    if (LookSetting.ACCENT_COLOR in look && LookSetting.DYNAMIC_THEME !in look) put(LookSetting.DYNAMIC_THEME, "false")
}
