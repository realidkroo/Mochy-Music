// a module player's own animations: keyframes, the clocks that drive them, states that blend in and out, and one-shot animations on events

package com.mochyy.mochymusic.modules.skin

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/** what a keyframe can move; [neutral] is the value that changes nothing, which a track starts and ends on when a frame leaves it out */
enum class PoseProp(val neutral: Float) {
    X(0f), Y(0f), Scale(1f), ScaleX(1f), ScaleY(1f), Rotate(0f), RotateX(0f), RotateY(0f), Opacity(1f), Blur(0f);

    // offsets and turns add up, sizes and see-through multiply, so stacked animations combine the way people expect
    val multiplies: Boolean get() = this == Scale || this == ScaleX || this == ScaleY || this == Opacity
}

/** one property's keyframes: where each sits (0..1), its value there, and the easing from it to the next (null takes the animation's own) */
@Immutable
class SkinTrack(val prop: PoseProp, val at: FloatArray, val values: FloatArray, val easings: List<Easing?>) {
    fun valueAt(t: Float, fallback: Easing): Float {
        if (t <= at[0]) return values[0]
        val last = at.size - 1
        if (t >= at[last]) return values[last]
        var i = 0
        while (i < last - 1 && t >= at[i + 1]) i++
        val span = (at[i + 1] - at[i]).coerceAtLeast(0.0001f)
        val local = (easings[i] ?: fallback).transform(((t - at[i]) / span).coerceIn(0f, 1f))
        return values[i] + (values[i + 1] - values[i]) * local
    }
}

/** a named set of keyframes, one track per property it moves */
@Immutable
class SkinKeyframes(val tracks: List<SkinTrack>)

/** how many times an animation runs: once, round and round, or there and back */
enum class SkinRepeat { Once, Loop, PingPong }

/** what moves an animation along: the time, how far the song has played, or how loud a part of the music is right now */
enum class SkinClock { Time, Progress, Level, Bass, Mid, Treble }

/** keyframes played over [durationMs] by a [clock]; [always] keeps a time animation going while the song is paused */
@Immutable
data class SkinTimeline(
    val frames: SkinKeyframes,
    val durationMs: Int,
    val delayMs: Int,
    val repeat: SkinRepeat,
    val clock: SkinClock,
    val easing: Easing,
    val always: Boolean,
) {
    /** where a time animation is, 0..1, [elapsedMs] after it started; before its delay it rests on its first frame */
    fun position(elapsedMs: Float): Float {
        val run = elapsedMs - delayMs
        if (run <= 0f) return 0f
        val cycles = run / durationMs.coerceAtLeast(1)
        return when (repeat) {
            SkinRepeat.Once -> cycles.coerceAtMost(1f)
            SkinRepeat.Loop -> cycles - cycles.toInt()
            SkinRepeat.PingPong -> (cycles % 2f).let { if (it > 1f) 2f - it else it }
        }
    }
}

/** a pose a block takes while its condition holds, blended in and out over the block's transition */
@Immutable
data class SkinState(val condition: SkinCondition, val values: Map<PoseProp, Float>)

/** how a block moves between its states */
@Immutable
data class SkinTransition(val durationMs: Int, val easing: Easing, val spring: Boolean)

/** what can set off a one-shot animation on a block */
enum class SkinEvent { Song, Play, Pause, Like, Unlike, Open, Beat }

/** a one-shot animation a block plays each time [event] happens */
@Immutable
data class SkinTrigger(val event: SkinEvent, val timeline: SkinTimeline)

/** the built-in conditions, written with an @ in "when" and "states": they follow the player instead of a setting */
enum class SkinFlag(val key: String) {
    Playing("@playing"), Paused("@paused"), Liked("@liked"), Shuffle("@shuffle"), Repeat("@repeat"), Lyrics("@lyrics"), Landscape("@landscape");

    companion object {
        fun of(key: String): SkinFlag? = entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}
