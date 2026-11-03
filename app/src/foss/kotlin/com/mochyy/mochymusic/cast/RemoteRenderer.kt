// RemoteRenderer.kt

package com.mochyy.mochymusic.cast

import kotlinx.coroutines.flow.StateFlow

/** what a receiver is doing, as far as mochy can tell */
data class RemoteState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** the song it was given has played to its end */
    val finished: Boolean = false,
    val volume: Float = 1f,
    /** the connection dropped or the receiver was taken over by something else */
    val lost: Boolean = false,
    /** the receiver turned the song down (a format it can't play, a link it couldn't open) */
    val failed: Boolean = false,
)

/** the song as a receiver gets it */
data class RemoteMedia(
    val url: String,
    val mimeType: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

/** a cast receiver mochy drives itself, without google play services: a chromecast or a dlna renderer */
interface RemoteRenderer {
    val state: StateFlow<RemoteState>

    /** opens the connection; throws when the receiver can't be reached */
    suspend fun connect()

    suspend fun load(media: RemoteMedia, startMs: Long)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun setVolume(volume: Float)

    /** where it is right now, moved on from its last report while it plays */
    fun positionNow(): Long

    /** lets go of the receiver, stopping what it plays unless it was lost or taken over (stopping then could stop someone else's) */
    suspend fun close(stopPlayback: Boolean = true)
}
