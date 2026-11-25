// MixItModels.kt

package com.mochyy.mochymusic.mixit

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.db.entities.PlaylistTransition

/**
 * Audio effects a user can put on the outgoing track (Song 1) or incoming track (Song 2) inside
 * the Mix It Transition Editor. Every entry maps directly onto [com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor].
 */
enum class MixItEffect(
    val label: String,
    val shortLabel: String,
    val description: String,
    val forOutgoing: Boolean = true,
    val forIncoming: Boolean = true,
    /** Beats the outgoing effect keeps ringing after the window ends (echo/reverb tails). */
    val tailBeats: Int = 0,
) {
    NONE(
        label = "None",
        shortLabel = "Off",
        description = "Clean volume curve only, no extra audio effect",
    ),
    CROSSFADE(
        label = "Crossfade",
        shortLabel = "Fade",
        description = "Smooth, natural blend with gentle top-end softening",
    ),
    ECHO(
        label = "Echo",
        shortLabel = "Echo",
        description = "Beat-synced echo repeats that trail smoothly into the next track",
        tailBeats = 10,
    ),
    DUB_ECHO(
        label = "Dub Echo",
        shortLabel = "Dub",
        description = "Bouncing left-right stereo echo with warm dark repeats",
        forIncoming = false,
        tailBeats = 12,
    ),
    REVERB(
        label = "Reverb Wash",
        shortLabel = "Reverb",
        description = "Spacious hall tail that melts the track into the atmosphere",
        tailBeats = 12,
    ),
    LOW_PASS(
        label = "Low-Pass Sweep",
        shortLabel = "LPF",
        description = "Warm muffled sweep like music playing from behind a club wall",
    ),
    HIGH_PASS(
        label = "High-Pass Lift",
        shortLabel = "HPF",
        description = "Airy sweep that lifts the bass out so the next drop hits hard",
    ),
    BACKSPIN(
        label = "Vinyl Backspin",
        shortLabel = "Spin",
        description = "Classic DJ turntable pull-back right before the next song hits",
        forIncoming = false,
    ),
    TAPE_STOP(
        label = "Tape Stop",
        shortLabel = "Stop",
        description = "Slows the track down to a stop like turning off a vinyl deck",
        forIncoming = false,
    ),
    LOOP_ROLL(
        label = "Loop Roll",
        shortLabel = "Roll",
        description = "Beat repeats faster and faster (1 → 1/2 → 1/4 → 1/8) into the drop",
        forIncoming = false,
    ),
    STUTTER_GATE(
        label = "Stutter Gate",
        shortLabel = "Gate",
        description = "Rhythmic beat-synced chopper pulsing in time with the groove",
        forIncoming = false,
    ),
    RISER(
        label = "Energy Riser",
        shortLabel = "Riser",
        description = "Sweeping white-noise swell that builds hype into the next track",
        forIncoming = false,
        tailBeats = 4,
    ),
    PHASER(
        label = "Phaser Glide",
        shortLabel = "Phase",
        description = "Swooshing jet-plane stereo swirl across the transition",
    ),
    LOFI_MELT(
        label = "Lo-Fi Melt",
        shortLabel = "Lo-Fi",
        description = "Warm retro bit-crushed texture that dissolves across the mix",
    ),
    ORBIT(
        label = "3D Orbit",
        shortLabel = "Orbit",
        description = "Circling spatial stereo movement wrapping around your ears",
        tailBeats = 6,
    ),
    DAMPEN(
        label = "Fade to Far",
        shortLabel = "Far",
        description = "Softly pulls the song into the distance behind the incoming track",
        forIncoming = false,
        tailBeats = 6,
    );

    companion object {
        fun fromName(name: String?): MixItEffect =
            entries.firstOrNull { it.name == name } ?: CROSSFADE

        val outgoingOptions: List<MixItEffect> = entries.filter { it.forOutgoing }
        val incomingOptions: List<MixItEffect> = entries.filter { it.forIncoming }
    }
}

/** How tempos align across a Mix It transition — plain English, no DJ jargon. */
enum class MixItBeatMatchMode(
    val label: String,
    val subtitle: String,
) {
    AUTO(
        label = "Smart Auto",
        subtitle = "Automatically locks beats when tempos are close",
    ),
    MATCH_OUT(
        label = "Match Song 1",
        subtitle = "Adjusts Song 2 to match Song 1's beat during the mix",
    ),
    MATCH_IN(
        label = "Match Song 2",
        subtitle = "Gently ramps Song 1 up to Song 2's beat before mixing",
    ),
    OFF(
        label = "Original Speeds",
        subtitle = "Never changes either song's playback speed",
    );

    companion object {
        fun fromName(name: String?): MixItBeatMatchMode =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** What unconfigured pairs in a Mix It playlist do. */
enum class MixItFallbackMode(
    val label: String,
    val description: String,
) {
    APP_DEFAULT(
        label = "Your Transition Setting",
        description = "Follows whatever you chose in Settings → Transitions (CrossMix by default)",
    ),
    CROSSMIX(
        label = "CrossMix",
        description = "Smart automatic DJ transitions for unconfigured songs",
    ),
    AUTO_CROSSFADE(
        label = "Auto Crossfade",
        description = "Simple 5-second smooth fade between unconfigured songs",
    ),
    OFF(
        label = "No Transition",
        description = "Play unconfigured songs straight through with no overlap",
    );

    companion object {
        fun fromName(name: String?): MixItFallbackMode =
            entries.firstOrNull { it.name == name } ?: APP_DEFAULT
    }
}

/** One-tap curated DJ presets so anyone can sound like a pro instantly and tweak from there. */
enum class MixItPreset(
    val title: String,
    val subtitle: String,
    val durationBeats: Int,
    val outEffect: MixItEffect,
    val inEffect: MixItEffect,
    val outFadeStart: Float,
    val outFadeEnd: Float,
    val inFadeStart: Float,
    val inFadeEnd: Float,
    val bassSwap: Boolean,
    val swapFrac: Float,
    val beatMatchMode: MixItBeatMatchMode = MixItBeatMatchMode.AUTO,
) {
    SMOOTH_BLEND(
        title = "Smooth Blend",
        subtitle = "Effortless, balanced fade with clean bass hand-off",
        durationBeats = 12,
        outEffect = MixItEffect.CROSSFADE,
        inEffect = MixItEffect.CROSSFADE,
        outFadeStart = 0.20f,
        outFadeEnd = 0.95f,
        inFadeStart = 0.05f,
        inFadeEnd = 0.80f,
        bassSwap = true,
        swapFrac = 0.50f,
    ),
    CLUB_BASS_SWAP(
        title = "Club Bass Swap",
        subtitle = "Both tracks ride together, then the kick swaps dead on the beat",
        durationBeats = 16,
        outEffect = MixItEffect.CROSSFADE,
        inEffect = MixItEffect.HIGH_PASS,
        outFadeStart = 0.52f,
        outFadeEnd = 0.98f,
        inFadeStart = 0.02f,
        inFadeEnd = 0.48f,
        bassSwap = true,
        swapFrac = 0.50f,
    ),
    ECHO_DROP(
        title = "Echo Drop",
        subtitle = "Song 1 echoes out on the beat as Song 2 hits clean",
        durationBeats = 8,
        outEffect = MixItEffect.ECHO,
        inEffect = MixItEffect.NONE,
        outFadeStart = 0.55f,
        outFadeEnd = 0.82f,
        inFadeStart = 0.72f,
        inFadeEnd = 0.92f,
        bassSwap = false,
        swapFrac = 0.78f,
    ),
    FILTER_RIDE(
        title = "Filter Ride",
        subtitle = "Warm low-pass melt out while Song 2 opens up bright",
        durationBeats = 16,
        outEffect = MixItEffect.LOW_PASS,
        inEffect = MixItEffect.HIGH_PASS,
        outFadeStart = 0.35f,
        outFadeEnd = 0.98f,
        inFadeStart = 0.05f,
        inFadeEnd = 0.65f,
        bassSwap = true,
        swapFrac = 0.50f,
    ),
    DREAMY_REVERB(
        title = "Dreamy Reverb",
        subtitle = "Floating hall wash that melts two vibes together",
        durationBeats = 16,
        outEffect = MixItEffect.REVERB,
        inEffect = MixItEffect.REVERB,
        outFadeStart = 0.15f,
        outFadeEnd = 0.88f,
        inFadeStart = 0.25f,
        inFadeEnd = 0.92f,
        bassSwap = true,
        swapFrac = 0.55f,
    ),
    VINYL_BACKSPIN(
        title = "Vinyl Backspin",
        subtitle = "Quick DJ turntable spin-back straight into the next track",
        durationBeats = 6,
        outEffect = MixItEffect.BACKSPIN,
        inEffect = MixItEffect.NONE,
        outFadeStart = 0.75f,
        outFadeEnd = 0.88f,
        inFadeStart = 0.82f,
        inFadeEnd = 0.92f,
        bassSwap = false,
        swapFrac = 0.85f,
    ),
    TAPE_STOP_DROP(
        title = "Tape Stop",
        subtitle = "Deck powers down to a halt, then Song 2 drops in hard",
        durationBeats = 6,
        outEffect = MixItEffect.TAPE_STOP,
        inEffect = MixItEffect.NONE,
        outFadeStart = 0.72f,
        outFadeEnd = 0.88f,
        inFadeStart = 0.84f,
        inFadeEnd = 0.93f,
        bassSwap = false,
        swapFrac = 0.86f,
    ),
    HYPE_LOOP_ROLL(
        title = "Hype Loop Roll",
        subtitle = "Rapid-fire beat roll build-up straight into the drop",
        durationBeats = 8,
        outEffect = MixItEffect.LOOP_ROLL,
        inEffect = MixItEffect.NONE,
        outFadeStart = 0.82f,
        outFadeEnd = 0.94f,
        inFadeStart = 0.88f,
        inFadeEnd = 0.95f,
        bassSwap = false,
        swapFrac = 0.90f,
    ),
    LOFI_MELT(
        title = "Lo-Fi Melt",
        subtitle = "Retro crushed shimmer dissolving into the next song",
        durationBeats = 12,
        outEffect = MixItEffect.LOFI_MELT,
        inEffect = MixItEffect.LOFI_MELT,
        outFadeStart = 0.22f,
        outFadeEnd = 0.95f,
        inFadeStart = 0.12f,
        inFadeEnd = 0.82f,
        bassSwap = true,
        swapFrac = 0.50f,
    ),
    SPATIAL_ORBIT(
        title = "3D Orbit",
        subtitle = "Songs swirl around your head as they trade places",
        durationBeats = 16,
        outEffect = MixItEffect.ORBIT,
        inEffect = MixItEffect.ORBIT,
        outFadeStart = 0.25f,
        outFadeEnd = 0.95f,
        inFadeStart = 0.10f,
        inFadeEnd = 0.80f,
        bassSwap = true,
        swapFrac = 0.50f,
    );

    fun applyTo(base: PlaylistTransition, beatMs: Double): PlaylistTransition {
        val targetDuration = (durationBeats * beatMs).toLong().coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
        return base.copy(
            durationMs = targetDuration,
            outEffect = outEffect.name,
            inEffect = inEffect.name,
            outFadeStart = outFadeStart,
            outFadeEnd = outFadeEnd,
            inFadeStart = inFadeStart,
            inFadeEnd = inFadeEnd,
            bassSwap = bassSwap,
            swapFrac = swapFrac,
            beatMatchMode = beatMatchMode.name,
            presetName = title,
        )
    }
}

/**
 * Immutable DSP specification carried by [com.mochyy.mochymusic.crossmix.CrossmixPlan] and
 * [com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor.Script] when playing a
 * user-crafted Mix It transition.
 */
@Immutable
data class MixItSpec(
    val outEffect: MixItEffect,
    val inEffect: MixItEffect,
    val outFadeStart: Float,
    val outFadeEnd: Float,
    val inFadeStart: Float,
    val inFadeEnd: Float,
    val bassSwap: Boolean,
    val swapFrac: Float,
    val label: String,
    val outVoiceLevel: Float = 1f,
    val outBassLevel: Float = 1f,
    val outInstrumentLevel: Float = 1f,
    val inVoiceLevel: Float = 1f,
    val inBassLevel: Float = 1f,
    val inInstrumentLevel: Float = 1f,
) {
    val tailBeats: Int get() = outEffect.tailBeats

    companion object {
        private fun Float.finiteIn(min: Float, max: Float, fallback: Float): Float =
            if (isFinite()) coerceIn(min, max) else fallback

        fun from(t: PlaylistTransition): MixItSpec {
            val outFx = MixItEffect.fromName(t.outEffect)
            val inFx = MixItEffect.fromName(t.inEffect)
            val oStart = t.outFadeStart.finiteIn(0f, 0.95f, 0.2f)
            val oEnd = t.outFadeEnd.finiteIn(oStart + 0.03f, 1f, 0.95f)
            val iStart = t.inFadeStart.finiteIn(0f, 0.95f, 0.05f)
            val iEnd = t.inFadeEnd.finiteIn(iStart + 0.03f, 1f, 0.8f)
            return MixItSpec(
                outEffect = outFx,
                inEffect = inFx,
                outFadeStart = oStart,
                outFadeEnd = oEnd,
                inFadeStart = iStart,
                inFadeEnd = iEnd,
                bassSwap = t.bassSwap,
                swapFrac = t.swapFrac.finiteIn(0.1f, 0.95f, 0.5f),
                label = t.presetName?.takeIf { it.isNotBlank() } ?: outFx.label,
                outVoiceLevel = t.outVoiceLevel.finiteIn(0f, 1f, 1f),
                outBassLevel = t.outBassLevel.finiteIn(0f, 1f, 1f),
                outInstrumentLevel = t.outInstrumentLevel.finiteIn(0f, 1f, 1f),
                inVoiceLevel = t.inVoiceLevel.finiteIn(0f, 1f, 1f),
                inBassLevel = t.inBassLevel.finiteIn(0f, 1f, 1f),
                inInstrumentLevel = t.inInstrumentLevel.finiteIn(0f, 1f, 1f),
            )
        }
    }
}

object MixItLimits {
    const val MinDurationMs = 500L
    const val MaxDurationMs = 28_000L
    const val DefaultDurationMs = 8_000L
    const val MinTrackMs = 2_000L
    const val MaxAutoSpeedUp = 0.15f
    const val MaxManualSpeedShift = 0.25f
    const val MaxNudgeMs = 200
}
