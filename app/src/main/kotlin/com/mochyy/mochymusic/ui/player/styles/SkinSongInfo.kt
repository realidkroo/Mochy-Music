// the song's facts for a module's words ({about}, {genres}, {released}…), gathered off the main thread the same way the about sheet does (docs/wiki, player info)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.modules.skin.SkinSongInfoField
import com.mochyy.mochymusic.ui.player.info.AboutSource
import com.mochyy.mochymusic.ui.player.info.SongAbout
import com.mochyy.mochymusic.ui.player.info.SongAboutLoader
import com.mochyy.mochymusic.ui.player.info.textFor

/** what's known about the playing song; a label reading it redraws when more of it comes in */
@Stable
internal class SkinSongInfo {
    var about: SongAbout? by mutableStateOf(null)

    val hasAbout: Boolean get() = aboutText(about) != null

    fun fill(text: String): String {
        if (!SkinSongInfoField.containsMatchIn(text)) return text
        val facts = about
        return SkinSongInfoField.replace(text) { match ->
            when (match.groupValues[1].lowercase()) {
                "about" -> aboutText(facts)
                "genre", "genres" -> facts?.genre
                "released" -> facts?.released
                "year" -> facts?.released?.let { Year.find(it)?.value }
                "writers" -> facts?.writtenBy
                "producers" -> facts?.producedBy
                "label" -> facts?.label
                else -> null
            }.orEmpty()
        }
    }

    // the first source with a few words about the song, in the order the about sheet trusts them
    private fun aboutText(facts: SongAbout?): String? {
        facts ?: return null
        return AboutOrder.firstNotNullOfOrNull { source -> textFor(facts, source)?.trim()?.takeIf { it.isNotEmpty() } }
    }

    private companion object {
        val AboutOrder = listOf(AboutSource.Modules, AboutSource.Genius, AboutSource.Wikipedia, AboutSource.Musixmatch, AboutSource.YouTube)
        val Year = Regex("""\b(1[89]|20)\d{2}\b""")
    }
}

/** the song's facts, looked up again for every new song; null when no words name them, so other players never fetch any */
@Composable
internal fun rememberSkinSongInfo(needed: Boolean, preview: Boolean): SkinSongInfo? {
    if (!needed || preview) return null
    val connection = LocalPlayerConnection.current ?: return null
    val database = LocalDatabase.current
    val app = LocalContext.current.applicationContext
    val info = remember { SkinSongInfo() }
    val song by connection.mediaMetadata.collectAsState()
    LaunchedEffect(song?.id) {
        info.about = null
        val now = song ?: return@LaunchedEffect
        SongAboutLoader.load(now, database, app).collect { info.about = it }
    }
    return info
}
