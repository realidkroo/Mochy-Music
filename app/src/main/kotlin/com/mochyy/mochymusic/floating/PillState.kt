// PillState.kt

package com.mochyy.mochymusic.floating

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.player.PlaybackClock

// what the floating pill shows, written by the service on the main thread and read by the pill's compose
@Stable
internal class PillState(private val player: () -> Player?) {
    var song by mutableStateOf<MediaMetadata?>(null)

    // null while the song's lyrics are still being looked up, the bubble shows its title meanwhile
    var script by mutableStateOf<PillScript?>(null)

    var playing by mutableStateOf(false)

    // bumped on a seek or a speed change, so everything timed works out again where the song is
    var seekTick by mutableIntStateOf(0)

    // the play and pause button is out
    var expanded by mutableStateOf(false)

    // bumped on every touch while expanded, so the button only tucks away once it's left alone
    var touchTick by mutableIntStateOf(0)

    // the song's own lyrics shift, read every frame by the clock so it isn't state
    var offsetMs = 0

    // where the lyrics are for the frame on screen, with the shift
    val lyricsClock = PlaybackClock(player, { offsetMs.toLong() })

    // where the song itself is, for the ring and the times
    fun songPositionMs(): Long = player()?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long {
        val known = player()?.duration ?: C.TIME_UNSET
        if (known != C.TIME_UNSET && known > 0L) return known
        return (song?.duration ?: 0) * 1000L
    }

    fun speed(): Float = player()?.playbackParameters?.speed?.takeIf { it > 0f } ?: 1f
}
