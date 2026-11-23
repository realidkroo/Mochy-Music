// PlaylistTransition.kt

package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One user-crafted Mix It transition between [fromSongId] and [toSongId] inside [playlistId].
 *
 * Stored by song ids rather than row positions so reordering tracks keeps transitions attached to
 * their pair and playback can look them up directly from the queue's media ids. Unconfigured pairs
 * have no row here and fall back to CrossMix (or the listener's chosen transition mode).
 */
@Immutable
@Entity(
    tableName = "playlist_transition",
    primaryKeys = ["playlistId", "fromSongId", "toSongId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId"]),
    ],
)
data class PlaylistTransition(
    val playlistId: String,
    val fromSongId: String,
    val toSongId: String,
    /** Where the transition window begins in Song 1 (ms, anywhere in the song). */
    val outStartMs: Long,
    /** Where Song 2 sits at the left edge of the transition window (ms, anywhere in Song 2). */
    val inStartMs: Long,
    /** Width of the transition window in ms (typically 1.5s .. 28s). */
    val durationMs: Long = 8_000L,
    /** Outgoing Song 1 audio FX ([com.mochyy.mochymusic.mixit.MixItEffect] name). */
    val outEffect: String = "CROSSFADE",
    /** Incoming Song 2 audio FX ([com.mochyy.mochymusic.mixit.MixItEffect] name). */
    val inEffect: String = "CROSSFADE",
    /** Normalized 0..1 position inside the window where Song 1 starts fading down. */
    val outFadeStart: Float = 0.12f,
    /** Normalized 0..1 position inside the window where Song 1 reaches silence. */
    val outFadeEnd: Float = 0.92f,
    /** Normalized 0..1 position inside the window where Song 2 starts fading up. */
    val inFadeStart: Float = 0.08f,
    /** Normalized 0..1 position inside the window where Song 2 reaches full volume. */
    val inFadeEnd: Float = 0.85f,
    /** Beat matching strategy ([com.mochyy.mochymusic.mixit.MixItBeatMatchMode] name). */
    val beatMatchMode: String = "AUTO",
    /** Magnetically snap drags & window resizing to detected beats/bars. */
    val snapToBeat: Boolean = true,
    /** Cleanly hand the kick/bass from Song 1 to Song 2 at [swapFrac] so kicks never clash. */
    val bassSwap: Boolean = true,
    /** Normalized 0..1 crossover point where the bass hands over (default 0.5). */
    val swapFrac: Float = 0.5f,
    /** Fine phase adjustment in ms (-200..+200) for nudging beats dead-on by ear. */
    val beatNudgeMs: Int = 0,
    /** Optional manual speed override for Song 2 (0f = automatic from beatMatchMode). */
    val customSpeedRatio: Float = 0f,
    /** Optional preset name for displaying a friendly badge on the playlist row. */
    val presetName: String? = null,
    /** Song 1 mix/stem levels during transition (0 = cut, 1 = full). */
    val outVoiceLevel: Float = 1f,
    val outBassLevel: Float = 1f,
    val outInstrumentLevel: Float = 1f,
    /** Song 2 mix/stem levels during transition (0 = cut, 1 = full). */
    val inVoiceLevel: Float = 1f,
    val inBassLevel: Float = 1f,
    val inInstrumentLevel: Float = 1f,
    val updatedAt: Long = System.currentTimeMillis(),
)
