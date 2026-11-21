// UnisonLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.mochyy.mochymusic.betterlyrics.TTMLParser
import com.mochyy.mochymusic.constants.EnableUnisonKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.directlyrics.Unison
import com.music.directlyrics.models.UnisonLyrics

/** crowdsourced lyrics from better lyrics' unison api, keeping the song and submitter links as an lrc meta tag */
object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Unison.getLyrics(id, title, artist, duration, album).mapCatching { result ->
        val body = formatBody(result)
        check(body.isNotBlank()) { "unison returned empty lyrics for this track" }
        attachCredit(body, result)
    }

    // ttml goes through better lyrics' parser so word timing, singers and background vocals stay intact; lrc and plain pass straight through
    internal fun formatBody(result: UnisonLyrics): String {
        val raw = result.lyrics.trim()
        val isTtml = result.format.equals("ttml", ignoreCase = true) || raw.startsWith("<tt")
        if (!isTtml) return raw
        val lines = TTMLParser.parseTTML(raw)
        check(lines.isNotEmpty()) { "unison ttml has no timed lyrics for this track" }
        return TTMLParser.toLRC(lines)
    }

    // stored as a standard bracketed lrc metadata line so room keeps it offline without a schema change and every lyrics parser skips it
    internal fun attachCredit(body: String, result: UnisonLyrics): String {
        val videoId = sanitize(result.videoId)
        val keyId = sanitize(result.submitterKeyId)
        val displayName = sanitize(result.submitterName)
        if (videoId.isEmpty() && displayName.isEmpty()) return body
        return "$TagPrefix$videoId|$keyId|$displayName]\n$body"
    }

    /** reads the unison song and curator links embedded in [rawLyrics], or null when absent */
    fun parseCredit(rawLyrics: String?): UnisonCredit? {
        if (rawLyrics.isNullOrBlank()) return null
        val line = rawLyrics.lineSequence().firstOrNull { it.trim().startsWith(TagPrefix) }?.trim() ?: return null
        val inner = line.removePrefix(TagPrefix).removeSuffix("]")
        val parts = inner.split("|", limit = 3)
        if (parts.size != 3) return null
        val videoId = parts[0].takeIf { it.isNotEmpty() }
        val keyId = parts[1].takeIf { it.isNotEmpty() }
        val displayName = parts[2].takeIf { it.isNotEmpty() }
        if (videoId == null && displayName == null) return null
        return UnisonCredit(
            songUrl = videoId?.let(Unison::songUrl) ?: Unison.BaseUrl,
            submitterName = displayName,
            submitterUrl = keyId?.let(Unison::curatorUrl),
        )
    }

    private fun sanitize(raw: String?): String =
        raw.orEmpty().replace(Regex("[|\\]\\r\\n]"), " ").trim()

    private const val TagPrefix = "[unison:"
}

/** attribution links unison requires next to its lyrics: the song page and the submitter's profile */
data class UnisonCredit(
    val songUrl: String,
    val submitterName: String?,
    val submitterUrl: String?,
)
