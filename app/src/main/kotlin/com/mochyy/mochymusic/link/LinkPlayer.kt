// LinkPlayer.kt

package com.mochyy.mochymusic.link

import com.mochyy.mochymusic.playback.PlayerConnection

// the open app's player, for a paired device that plays, skips or changes something here; null while no screen is up
object LinkPlayer {
    @Volatile var connection: PlayerConnection? = null

    // only the screen that set it may clear it, so a recreated activity doesn't wipe the new one's player
    fun release(connection: PlayerConnection?) {
        if (connection != null && this.connection === connection) this.connection = null
    }
}
