// CarLyricsPlayer.kt

package com.mochyy.mochymusic.car

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArrayList

// what the media session hands the car: the real player, except that while car lyrics are on the line being sung sits where the artist was
@UnstableApi
class CarLyricsPlayer(player: Player) : ForwardingPlayer(player) {
    // a lyric is tied to its song, so a skip never shows the old song's line over the new one
    class Overlay(val mediaId: String, val title: String, val line: String)

    // the session's own listeners, told straight away when the line changes since the player underneath never changes its metadata for it
    private val listeners = CopyOnWriteArrayList<Player.Listener>()

    @Volatile private var overlay: Overlay? = null

    // call on the main thread, the session checks it reads the player there
    fun show(next: Overlay?) {
        if (next == null && overlay == null) return
        overlay = next
        val metadata = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    // the cover is left alone, so the car keeps the song's own picture and never reloads it for a new line
    override fun getMediaMetadata(): MediaMetadata {
        val real = super.getMediaMetadata()
        val shown = overlay ?: return real
        if (currentMediaItem?.mediaId != shown.mediaId) return real
        // the car reads the subtitle when there is one and the artist otherwise, so the line goes in both
        return real.buildUpon()
            .setTitle(shown.title)
            .setDisplayTitle(shown.title)
            .setArtist(shown.line)
            .setSubtitle(shown.line)
            .build()
    }
}
