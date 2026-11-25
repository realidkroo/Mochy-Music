// MixItEngine.kt

package com.mochyy.mochymusic.mixit

import com.mochyy.mochymusic.crossmix.CrossmixPlan
import com.mochyy.mochymusic.crossmix.CrossmixPlanner
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.CrossmixSettings
import com.mochyy.mochymusic.crossmix.CrossmixStyle
import com.mochyy.mochymusic.crossmix.CrossmixTrackInput
import com.mochyy.mochymusic.crossmix.FxParams
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.crossmix.cutAt
import com.mochyy.mochymusic.crossmix.entryHighPass
import com.mochyy.mochymusic.crossmix.fadeIn
import com.mochyy.mochymusic.crossmix.fadeOut
import com.mochyy.mochymusic.crossmix.ramp
import com.mochyy.mochymusic.crossmix.recede
import com.mochyy.mochymusic.crossmix.smooth
import com.mochyy.mochymusic.crossmix.sweepDown
import com.mochyy.mochymusic.crossmix.sweepUp
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure, zero-allocation DSP evaluator for user-crafted Mix It transitions.
 *
 * Called per 32-sample block on the audio thread inside [com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor].
 * Guarantees every song is untouched before beat 0, silent before entry (Incoming), cleanly quiet
 * after length + tailBeats (Outgoing), and back to untouched dry signal once settled (Incoming).
 */
object MixItScriptEvaluator {
    private const val OpenHz = FxParams.OpenHz

    fun evaluate(
        spec: MixItSpec,
        role: CrossmixRole,
        beat: Float,
        length: Float,
        swap: Float,
        out: FxParams,
    ) {
        out.reset()
        val safeLen = length.coerceAtLeast(0.5f)
        val p = (beat / safeLen).coerceIn(0f, 1f)
        val outgoing = role == CrossmixRole.Outgoing

        // Outside the transition window: guaranteed untouched / silent boundaries
        if (!outgoing && beat < 0f) {
            out.gain = 0f
            return
        }
        if (outgoing && beat <= 0f) {
            out.gain = 1f
            return
        }

        if (outgoing) {
            val fadeProgress = ramp(p, spec.outFadeStart, spec.outFadeEnd)
            out.gain = fadeOut(fadeProgress)
            applyOutgoingEffect(spec.outEffect, beat, safeLen, p, fadeProgress, spec.outFadeEnd, out)
            // Smoothly engage user's Voice / Bass / Instrument sliders over the first 15% so beat 0 never clicks
            val stemEnv = smooth(ramp(p, 0f, 0.15f))
            applyStemMix(spec.outVoiceLevel, spec.outBassLevel, spec.outInstrumentLevel, stemEnv, out)
            // After length + 1 beat, stop feeding delay/reverb/noise so tails die cleanly
            val feed = cutAt(beat, safeLen + 1f)
            out.echoSend *= feed
            out.reverbSend *= feed
            out.noiseLevel *= feed
            if (beat >= safeLen + 0.1f) {
                out.gain = 0f
                out.deck = FxParams.Deck.Live
            }
        } else {
            val fadeProgress = ramp(p, spec.inFadeStart, spec.inFadeEnd)
            out.gain = if (p < spec.inFadeStart) 0f else fadeIn(fadeProgress)
            if (beat <= safeLen + 4f) {
                applyIncomingEffect(spec.inEffect, beat, safeLen, p, fadeProgress, spec.inFadeStart, spec.inFadeEnd, out)
                // Hold user's incoming Voice / Bass / Instrument mix across the blend, then release smoothly back to dry song
                val releaseEnv = 1f - smooth(ramp(beat, safeLen * 0.85f, safeLen + 2f))
                if (p >= spec.inFadeStart && releaseEnv > 0.001f) {
                    applyStemMix(spec.inVoiceLevel, spec.inBassLevel, spec.inInstrumentLevel, releaseEnv, out)
                }
            }
        }

        // Clean 24 dB/oct bass hand-off over half a beat ending right on the swap point
        if (spec.bassSwap && beat >= 0f && beat <= safeLen + 1f) {
            val handed = ramp(beat, swap - 0.5f, swap)
            val swapCut = if (outgoing) handed else 1f - handed
            out.bassCut = max(out.bassCut, swapCut)
        }
        if (!outgoing && beat > safeLen + 0.5f) {
            out.bassCut = 0f
        }
        if (outgoing && beat >= safeLen) {
            out.bassCut = 0f
        }
    }

    /** Applies user Voice / Bass / Instrument reduction smoothly without ever jumping or clipping. */
    private fun applyStemMix(
        voiceLevel: Float,
        bassLevel: Float,
        instrumentLevel: Float,
        envelope: Float,
        out: FxParams,
    ) {
        val env = envelope.coerceIn(0f, 1f)
        if (env <= 0.001f) return
        val voiceCut = (1f - voiceLevel.coerceIn(0f, 1f)) * env
        val bassCutAmt = (1f - bassLevel.coerceIn(0f, 1f)) * env
        val instCut = (1f - instrumentLevel.coerceIn(0f, 1f)) * env

        if (voiceCut > 0.001f) {
            out.vocalCenterCut = max(out.vocalCenterCut, voiceCut)
            out.midBand *= 1f - 0.55f * voiceCut
        }
        if (bassCutAmt > 0.001f) {
            out.lowBand *= (1f - bassCutAmt)
            if (bassCutAmt > 0.65f) {
                out.bassCut = max(out.bassCut, ((bassCutAmt - 0.65f) / 0.35f).coerceIn(0f, 1f))
            }
        }
        if (instCut > 0.001f) {
            out.highBand *= (1f - 0.85f * instCut)
            out.sideCut = max(out.sideCut, instCut)
            // Softly trim low end too only if user kept bass high while stripping instruments to isolate vocals
            if (bassLevel > 0.85f && instCut > 0.4f) {
                out.lowBand *= (1f - 0.35f * instCut)
            }
        }
    }

    private fun applyOutgoingEffect(
        effect: MixItEffect,
        beat: Float,
        length: Float,
        p: Float,
        fadeProgress: Float,
        fadeEnd: Float,
        out: FxParams,
    ) {
        val cutBeat = (length * fadeEnd).coerceIn(0.5f, length)
        when (effect) {
            MixItEffect.NONE -> Unit

            MixItEffect.CROSSFADE -> {
                out.highCutHz = sweepDown(fadeProgress.pow(0.9f), OpenHz, 2_200f)
            }

            MixItEffect.ECHO -> {
                out.echoBeats = 0.75f
                out.echoFeedback = 0.48f
                out.echoTone = 0.58f
                val sendRamp = ramp(beat, (cutBeat - 2f).coerceAtLeast(0f), cutBeat - 0.05f)
                out.echoSend = 0.85f * sendRamp * cutAt(beat, cutBeat + 0.05f)
                out.highCutHz = sweepDown(fadeProgress, OpenHz, 6_500f)
            }

            MixItEffect.DUB_ECHO -> {
                out.echoBeats = 0.5f
                out.echoPingPong = true
                out.echoFeedback = 0.68f
                out.echoTone = 0.28f
                val sendRamp = ramp(beat, (cutBeat - 4f).coerceAtLeast(0f), cutBeat - 0.1f)
                out.echoSend = 0.9f * sendRamp * cutAt(beat, cutBeat + 0.05f)
                out.reverbSend = 0.2f * sendRamp * cutAt(beat, cutBeat + 0.05f)
                out.highCutHz = sweepDown(fadeProgress, OpenHz, 1_900f)
            }

            MixItEffect.REVERB -> {
                out.reverbSize = 0.90f
                out.reverbFreeze = ramp(p, 0.75f, 1f) * 0.55f
                out.reverbSend = 0.9f * smooth(p) * cutAt(beat, cutBeat + 0.1f)
                out.highCutHz = sweepDown(ramp(p, 0.2f, 1f), OpenHz, 2_200f)
            }

            MixItEffect.LOW_PASS -> {
                out.highCutHz = sweepDown(p.pow(0.65f), OpenHz, 280f)
                out.resonance = 0.75f + 0.35f * sin(PI.toFloat() * p)
            }

            MixItEffect.HIGH_PASS -> {
                out.lowCutHz = sweepUp(p.pow(1.25f), 20f, 2_200f)
                out.resonance = 0.95f
                out.reverbSend = 0.22f * p * cutAt(beat, cutBeat + 0.1f)
            }

            MixItEffect.BACKSPIN -> {
                val spinBeats = min(2f, cutBeat * 0.6f).coerceAtLeast(0.75f)
                val t = ramp(beat, cutBeat - spinBeats, cutBeat)
                if (t > 0f && beat < cutBeat + 0.05f) {
                    out.deck = FxParams.Deck.SpinBack
                    out.deckAmount = t
                }
                out.highCutHz = sweepDown(t, OpenHz, 2_400f)
                out.gain = min(out.gain, cutAt(beat, cutBeat))
            }

            MixItEffect.TAPE_STOP -> {
                val stopBeats = min(2.5f, cutBeat * 0.65f).coerceAtLeast(0.75f)
                val t = ramp(beat, cutBeat - stopBeats, cutBeat)
                if (t > 0f && beat < cutBeat + 0.05f) {
                    out.deck = FxParams.Deck.TapeStop
                    out.deckAmount = t
                }
                out.highCutHz = sweepDown(t, OpenHz, 1_400f)
                out.gain = min(out.gain, cutAt(beat, cutBeat))
            }

            MixItEffect.LOOP_ROLL -> {
                val rollWindow = min(4f, cutBeat * 0.75f).coerceAtLeast(1f)
                val rollStart = cutBeat - rollWindow
                if (beat >= rollStart && beat < cutBeat) {
                    val step = (((beat - rollStart) / rollWindow) * 4f).toInt().coerceIn(0, 3)
                    out.deck = FxParams.Deck.Loop
                    out.deckAmount = 1f / (1 shl step)
                    out.deckMark = rollStart + step * (rollWindow / 4f)
                }
                out.lowCutHz = sweepUp(ramp(beat, (rollStart - 2f).coerceAtLeast(0f), cutBeat), 20f, 1_300f)
                out.gain = min(out.gain, cutAt(beat, cutBeat))
            }

            MixItEffect.STUTTER_GATE -> {
                out.gateDivision = 4f
                out.gateDepth = 0.88f * smooth(ramp(p, 0.05f, 0.65f))
                out.gateDuty = 0.58f - 0.32f * p
                out.echoBeats = 0.25f
                out.echoSend = 0.2f * ramp(p, 0.4f, 1f) * cutAt(beat, cutBeat + 0.1f)
            }

            MixItEffect.RISER -> {
                out.noiseLevel = 0.28f * ramp(p, 0.15f, 1f).pow(1.5f) * cutAt(beat, cutBeat)
                out.noiseHz = sweepUp(ramp(p, 0.15f, 1f), 350f, 8_800f)
                out.lowCutHz = sweepUp(ramp(p, 0.25f, 1f), 20f, 900f)
                out.reverbSend = 0.32f * ramp(p, 0.35f, 1f) * cutAt(beat, cutBeat + 0.1f)
            }

            MixItEffect.PHASER -> {
                out.flangerMix = 0.68f * smooth(ramp(p, 0f, 0.55f))
                out.flangerRateHz = 0.22f + 0.32f * p
            }

            MixItEffect.LOFI_MELT -> {
                out.crush = 0.85f * smooth(ramp(p, 0f, 0.8f))
                out.highCutHz = sweepDown(p, OpenHz, 2_600f)
                out.width = 1f - 0.55f * p
            }

            MixItEffect.ORBIT -> {
                out.orbit = smooth(ramp(p, 0f, 0.75f))
                out.orbitBeats = 8f
                out.width = 1f + 0.5f * p
                out.reverbSend = 0.45f * ramp(p, 0.2f, 1f) * cutAt(beat, cutBeat + 0.1f)
            }

            MixItEffect.DAMPEN -> {
                out.recede(0.88f * smooth(ramp(p, 0f, 0.45f)), 0f, room = 0.65f)
            }
        }
    }

    private fun applyIncomingEffect(
        effect: MixItEffect,
        beat: Float,
        length: Float,
        p: Float,
        fadeProgress: Float,
        fadeStart: Float,
        fadeEnd: Float,
        out: FxParams,
    ) {
        if (p < fadeStart) return
        val settle = ramp(beat, length, length + 2f)
        val openProgress = fadeProgress.coerceIn(0f, 1f)
        when (effect) {
            MixItEffect.NONE -> Unit

            MixItEffect.CROSSFADE -> {
                val openBy = fadeEnd.coerceIn(0.25f, 0.95f)
                out.lowCutHz = entryHighPass(ramp(p, fadeStart, openBy), 650f, 0.85f) * (1f - settle)
            }

            MixItEffect.ECHO -> {
                out.echoBeats = 0.5f
                out.echoFeedback = 0.38f
                out.echoTone = 0.65f
                out.echoSend = 0.45f * (1f - openProgress) * (1f - settle)
            }

            MixItEffect.REVERB -> {
                out.reverbSize = 0.86f
                out.reverbSend = 0.55f * (1f - openProgress) * (1f - settle)
                out.highCutHz = sweepUp(openProgress, 1_400f, OpenHz)
            }

            MixItEffect.LOW_PASS -> {
                out.highCutHz = sweepUp(openProgress.pow(0.8f), 420f, OpenHz)
                out.resonance = 0.72f + 0.3f * sin(PI.toFloat() * openProgress) * (1f - settle)
            }

            MixItEffect.HIGH_PASS -> {
                out.lowCutHz = sweepDown(openProgress.pow(0.75f), 1_600f, 0f) * (1f - settle)
                out.resonance = 0.88f - 0.17f * openProgress
            }

            MixItEffect.PHASER -> {
                out.flangerMix = 0.55f * (1f - openProgress) * (1f - settle)
                out.flangerRateHz = 0.34f
            }

            MixItEffect.LOFI_MELT -> {
                out.crush = 0.55f * (1f - openProgress) * (1f - settle)
                out.highCutHz = sweepUp(openProgress, 2_400f, OpenHz)
            }

            MixItEffect.ORBIT -> {
                val env = (1f - smooth(openProgress)) * (1f - settle)
                out.pan = -0.78f * env
                out.width = 0.35f + 0.65f * (1f - env)
            }

            else -> Unit
        }
    }
}

/** Visual direction of beat-matching so the UI shows which track syncs to which with zero wordy descriptions. */
enum class TempoSyncDirection {
    NONE,
    NATURAL_LOCK,
    SONG2_TO_SONG1,
    SONG1_TO_SONG2,
    GAP,
}

/** Tempo compatibility summary for the Transition Editor visual beat-match diagram (zero Camelot jargon). */
data class TempoMatchSummary(
    val title: String,
    val detail: String,
    val isLocked: Boolean,
    val outBpm: Float?,
    val inBpm: Float?,
    val inSpeed: Float,
    val outSpeed: Float,
    val direction: TempoSyncDirection = TempoSyncDirection.NONE,
    val syncedBpm: Float? = null,
)

/**
 * Turns a user-crafted [PlaylistTransition] into an executable beat-locked [CrossmixPlan], and
 * provides smart beat snapping, beat alignment, and default drafts for the editor.
 */
object MixItPlanner {
    private const val DefaultBeatMs = 500.0 // 120 BPM fallback when track has no steady beat

    /**
     * Builds an executable [CrossmixPlan] starting at the exact positions the user dragged on
     * Song 1 ([transition.outStartMs]) and Song 2 ([transition.inStartMs]) — works at start,
     * middle, or end of either song.
     */
    fun buildPlan(
        transition: PlaylistTransition,
        outDurationMs: Long,
        inDurationMs: Long,
        outAnalysis: TrackAnalysis?,
        inAnalysis: TrackAnalysis?,
    ): CrossmixPlan {
        val durationMs = transition.durationMs.coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
        val safeOutDur = if (outDurationMs > 0L) outDurationMs else (transition.outStartMs + durationMs + 5_000L)
        val maxOutStart = (safeOutDur - 400L).coerceAtLeast(0L)
        val outStartMs = transition.outStartMs.coerceIn(0L, maxOutStart)
        val effectiveDurationMs = min(durationMs, (safeOutDur - outStartMs - 100L).coerceAtLeast(400L))

        val rawInStart = transition.inStartMs.let { raw ->
            if (inDurationMs > 1_000L) raw.coerceIn(0L, (inDurationMs - 500L).coerceAtLeast(0L))
            else raw.coerceAtLeast(0L)
        }

        val outGrid = outAnalysis?.takeIf { it.isBeatReliable }?.localGrid(outStartMs)
        val outBeatMs = outGrid?.periodMs
            ?: outAnalysis?.beatMs?.toDouble()?.takeIf { it in 250.0..1_200.0 }
            ?: DefaultBeatMs

        val inGrid = inAnalysis?.takeIf { it.isBeatReliable }?.localGrid(rawInStart)
        val inBeatMsRaw = inGrid?.periodMs
            ?: inAnalysis?.beatMs?.toDouble()?.takeIf { it in 250.0..1_200.0 }

        val mode = MixItBeatMatchMode.fromName(transition.beatMatchMode)
        val speeds = resolveSpeeds(
            outBeatMs = outBeatMs,
            inBeatMsRaw = inBeatMsRaw,
            outReliable = outAnalysis?.isBeatReliable == true,
            inReliable = inAnalysis?.isBeatReliable == true,
            mode = mode,
            customSpeedRatio = transition.customSpeedRatio,
        )

        // True beat-phase locking: when beat-matched, shift Song 2 by at most ±0.5 beat so Song 2's
        // kicks hit on the EXACT same millisecond as Song 1's kicks at this transition region!
        val phaseAlignedInStart = if (speeds.beatMatched && outGrid != null && inGrid != null && transition.customSpeedRatio == 0f) {
            val outPhase = positiveMod((outStartMs - outGrid.firstBeatMs) / outGrid.periodMs, 1.0)
            val inPeriod = speeds.inMediaPerBeat
            val inPhase = positiveMod((rawInStart - inGrid.firstBeatMs) / inPeriod, 1.0)
            var deltaBeats = inPhase - outPhase
            if (deltaBeats > 0.5) deltaBeats -= 1.0
            if (deltaBeats < -0.5) deltaBeats += 1.0
            // Move Song 2 so its beat phase equals Song 1's beat phase at beat 0; wrap by ±1 beat if near track bounds
            var aligned = rawInStart - deltaBeats * inPeriod
            if (aligned < 0.0) aligned += inPeriod
            if (inDurationMs > 1_000L && aligned > inDurationMs - 500L) aligned -= inPeriod
            aligned.toLong().coerceAtLeast(0L)
        } else {
            rawInStart
        }

        val nudgedInStart = (phaseAlignedInStart + transition.beatNudgeMs).let { raw ->
            if (inDurationMs > 1_000L) raw.coerceIn(0L, (inDurationMs - 500L).coerceAtLeast(0L))
            else raw.coerceAtLeast(0L)
        }

        val exactLengthBeats = (effectiveDurationMs / outBeatMs).toFloat().coerceIn(0.5f, 128f)
        val lengthBeatsInt = exactLengthBeats.roundToInt().coerceAtLeast(1)
        val swapFrac = transition.swapFrac.coerceIn(0.1f, 0.95f)
        val exactSwapBeat = (exactLengthBeats * swapFrac).coerceIn(min(0.5f, exactLengthBeats * 0.5f), exactLengthBeats)
        val swapBeatInt = exactSwapBeat.roundToInt().coerceIn(1, lengthBeatsInt)

        val inMediaPerBeat = speeds.inMediaPerBeat
        // inCueMs is where Song 2 sits at swapBeat, so inMediaAt(0f) == nudgedInStart
        val inCueMs = (nudgedInStart + exactSwapBeat * inMediaPerBeat).toLong().coerceAtLeast(0L)

        // Up to 4 beats of silent pre-roll when both tracks have room before beat 0, never negative on Song 1
        val maxInPreRoll = (nudgedInStart / inMediaPerBeat).toFloat()
        val maxOutPreRoll = (outStartMs / outBeatMs).toFloat()
        val preRollBeats = min(4f, min(maxInPreRoll, maxOutPreRoll)).coerceAtLeast(0f)
        val spec = MixItSpec.from(transition)

        return CrossmixPlan(
            style = CrossmixStyle.SilkBlend,
            variation = 0,
            lengthBeats = lengthBeatsInt,
            swapBeat = swapBeatInt,
            outStartMs = outStartMs,
            outBeatMs = outBeatMs,
            inCueMs = inCueMs,
            inMediaPerBeat = inMediaPerBeat,
            inSpeed = speeds.inSpeed,
            outSpeed = speeds.outSpeed,
            beatMatched = speeds.beatMatched,
            inStartBeat = -preRollBeats,
            yieldAmount = 0f,
            mixIt = spec,
            exactLengthBeats = exactLengthBeats,
            exactSwapBeat = exactSwapBeat,
        )
    }

    private fun positiveMod(value: Double, mod: Double): Double {
        val r = value % mod
        return if (r < 0.0) r + mod else r
    }

    private data class ResolvedSpeeds(
        val inMediaPerBeat: Double,
        val inSpeed: Float,
        val outSpeed: Float,
        val beatMatched: Boolean,
    )

    private fun resolveSpeeds(
        outBeatMs: Double,
        inBeatMsRaw: Double?,
        outReliable: Boolean,
        inReliable: Boolean,
        mode: MixItBeatMatchMode,
        customSpeedRatio: Float,
    ): ResolvedSpeeds {
        // Explicit custom speed override from the user
        if (customSpeedRatio in 0.80f..1.25f && abs(customSpeedRatio - 1f) > 0.002f) {
            return ResolvedSpeeds(
                inMediaPerBeat = outBeatMs * customSpeedRatio,
                inSpeed = customSpeedRatio,
                outSpeed = 1f,
                beatMatched = true,
            )
        }
        if (mode == MixItBeatMatchMode.OFF || !outReliable || !inReliable || inBeatMsRaw == null) {
            return ResolvedSpeeds(outBeatMs, 1f, 1f, beatMatched = false)
        }
        val baseInBeat: Double = inBeatMsRaw

        // Fold half / double time so 85 BPM and 170 BPM match cleanly
        var bestInBeat: Double = baseInBeat
        var bestDiff = abs(baseInBeat / outBeatMs - 1.0)
        for (factor in doubleArrayOf(0.5, 2.0)) {
            val candidate = baseInBeat * factor
            val diff = abs(candidate / outBeatMs - 1.0)
            if (diff < bestDiff) {
                bestDiff = diff
                bestInBeat = candidate
            }
        }
        val ratio = (bestInBeat / outBeatMs).toFloat()

        return when (mode) {
            MixItBeatMatchMode.OFF -> ResolvedSpeeds(outBeatMs, 1f, 1f, false)

            MixItBeatMatchMode.AUTO -> {
                if (abs(ratio - 1f) < 0.003f) {
                    ResolvedSpeeds(bestInBeat, 1f, 1f, beatMatched = true)
                } else if (ratio > 1f && ratio - 1f <= MixItLimits.MaxAutoSpeedUp) {
                    // Song 2 is slightly slower -> speed Song 2 up during overlap
                    ResolvedSpeeds(bestInBeat, ratio, 1f, beatMatched = true)
                } else if (ratio < 1f && (1f / ratio) - 1f <= MixItLimits.MaxAutoSpeedUp) {
                    // Song 1 is slightly slower -> gently ramp Song 1 up before mix
                    ResolvedSpeeds(bestInBeat, 1f, 1f / ratio, beatMatched = true)
                } else {
                    ResolvedSpeeds(outBeatMs, 1f, 1f, beatMatched = false)
                }
            }

            MixItBeatMatchMode.MATCH_OUT -> {
                // Explicitly bring Song 2 to Song 1's tempo (both speeding up and slowing down cleanly up to ±25%)
                val minRatio = 1f - MixItLimits.MaxManualSpeedShift
                val maxRatio = 1f + MixItLimits.MaxManualSpeedShift
                if (ratio !in (minRatio - 0.03f)..(maxRatio + 0.03f)) {
                    ResolvedSpeeds(outBeatMs, 1f, 1f, beatMatched = false)
                } else {
                    val speed = if (abs(ratio - 1f) < 0.003f) 1f else ratio.coerceIn(minRatio, maxRatio)
                    ResolvedSpeeds(bestInBeat, speed, 1f, beatMatched = true)
                }
            }

            MixItBeatMatchMode.MATCH_IN -> {
                // Explicitly bring Song 1 to Song 2's tempo (both speeding up and slowing down cleanly up to ±25%)
                val outTarget = 1f / ratio
                val minRatio = 1f - MixItLimits.MaxManualSpeedShift
                val maxRatio = 1f + MixItLimits.MaxManualSpeedShift
                if (outTarget !in (minRatio - 0.03f)..(maxRatio + 0.03f)) {
                    ResolvedSpeeds(outBeatMs, 1f, 1f, beatMatched = false)
                } else {
                    val speed = if (abs(outTarget - 1f) < 0.003f) 1f else outTarget.coerceIn(minRatio, maxRatio)
                    ResolvedSpeeds(bestInBeat, 1f, speed, beatMatched = true)
                }
            }
        }
    }

    /**
     * Generates a smart starting draft when the user opens an unconfigured pair in the Transition
     * Editor, seeded from Crossmix's musical analysis so it already sounds good out of the box.
     */
    fun defaultDraft(
        playlistId: String,
        fromSongId: String,
        toSongId: String,
        outDurationMs: Long,
        inDurationMs: Long,
        outAnalysis: TrackAnalysis?,
        inAnalysis: TrackAnalysis?,
        outLyrics: String? = null,
        inLyrics: String? = null,
    ): PlaylistTransition {
        val safeOutDur = outDurationMs.coerceAtLeast(15_000L)
        if (outAnalysis != null && outAnalysis.isBeatReliable) {
            val smart = CrossmixPlanner.plan(
                outgoing = CrossmixTrackInput(fromSongId, safeOutDur, outAnalysis, outLyrics),
                incoming = CrossmixTrackInput(toSongId, inDurationMs, inAnalysis, inLyrics),
                settings = CrossmixSettings.Default,
            )
            val durMs = (smart.lengthBeats * smart.outBeatMs).toLong()
                .coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
            val inStart = smart.inMediaAt(0f).toLong().coerceAtLeast(0L)
            return PlaylistTransition(
                playlistId = playlistId,
                fromSongId = fromSongId,
                toSongId = toSongId,
                outStartMs = smart.outStartMs.coerceIn(0L, (safeOutDur - 1_500L).coerceAtLeast(0L)),
                inStartMs = inStart,
                durationMs = durMs,
                outEffect = mapStyleToOutEffect(smart.style).name,
                inEffect = mapStyleToInEffect(smart.style).name,
                outFadeStart = if (smart.style.entersAtEnd) 0.72f else 0.20f,
                outFadeEnd = 0.96f,
                inFadeStart = if (smart.style.entersAtEnd) 0.78f else 0.06f,
                inFadeEnd = if (smart.style.entersAtEnd) 0.95f else 0.76f,
                beatMatchMode = MixItBeatMatchMode.AUTO.name,
                snapToBeat = true,
                bassSwap = !smart.style.entersAtEnd,
                swapFrac = (smart.swapBeat.toFloat() / smart.lengthBeats.coerceAtLeast(1)).coerceIn(0.25f, 0.85f),
                presetName = null,
            )
        }
        val fallbackDur = min(MixItLimits.DefaultDurationMs, (safeOutDur * 0.35f).toLong().coerceAtLeast(MixItLimits.MinDurationMs))
        val outStart = (safeOutDur - fallbackDur - 2_000L).coerceAtLeast(0L)
        val inStart = inAnalysis?.leadingSilenceEndMs?.coerceAtMost(30_000L) ?: 0L
        return PlaylistTransition(
            playlistId = playlistId,
            fromSongId = fromSongId,
            toSongId = toSongId,
            outStartMs = outStart,
            inStartMs = inStart,
            durationMs = fallbackDur,
            presetName = null,
        )
    }

    /** Snaps [ms] onto the nearest detected beat if within [maxSnapMs]; returns [ms] unchanged otherwise. */
    fun snapToNearestBeat(ms: Long, analysis: TrackAnalysis?, maxSnapMs: Long = 180L): Long {
        if (analysis == null || !analysis.isBeatReliable) return ms.coerceAtLeast(0L)
        val grid = analysis.localGrid(ms)
        if (grid != null) {
            val snapped = grid.snap(ms)
            if (abs(snapped - ms) <= maxSnapMs) return snapped.coerceAtLeast(0L)
        }
        val beats = analysis.beatsMs
        if (beats.isEmpty()) return ms.coerceAtLeast(0L)
        val nearest = beats.minByOrNull { abs(it - ms) } ?: return ms.coerceAtLeast(0L)
        return if (abs(nearest - ms) <= maxSnapMs) nearest.coerceAtLeast(0L) else ms.coerceAtLeast(0L)
    }

    /** Snaps [ms] directly to the closest bar downbeat when available, else closest beat. */
    fun snapToStrongBeat(ms: Long, analysis: TrackAnalysis?): Long {
        if (analysis == null || !analysis.isBeatReliable) return ms.coerceAtLeast(0L)
        val bars = analysis.barStartsMs()
        val grid = analysis.localGrid(ms)
        if (bars.isNotEmpty()) {
            val nearestBar = bars.minByOrNull { abs(it - ms) }
            if (nearestBar != null && abs(nearestBar - ms) <= 1_800L) {
                return (grid?.snap(nearestBar) ?: nearestBar).coerceAtLeast(0L)
            }
        }
        return (grid?.snap(ms) ?: ms).coerceAtLeast(0L)
    }

    /**
     * Aligns both tracks right where the user is currently hovering so Song 1's transition start
     * and Song 2's entry point both land cleanly on downbeats/beats!
     */
    fun autoAlignAtRegion(
        outStartMs: Long,
        inStartMs: Long,
        outAnalysis: TrackAnalysis?,
        inAnalysis: TrackAnalysis?,
    ): Pair<Long, Long> {
        val alignedOut = snapToStrongBeat(outStartMs, outAnalysis)
        val alignedIn = snapToStrongBeat(inStartMs, inAnalysis)
        return alignedOut to alignedIn
    }

    /** Rounds [durationMs] to the nearest musical beat count (2, 4, 6, 8, 12, 16, 24, 32 beats). */
    fun snapDurationToBeats(durationMs: Long, analysis: TrackAnalysis?): Long {
        val beatMs = analysis?.beatMs?.toDouble()?.takeIf { it in 250.0..1_200.0 } ?: DefaultBeatMs
        val rawBeats = durationMs / beatMs
        val targetBeats = MusicalBeatCounts.minByOrNull { abs(it - rawBeats) } ?: 8
        return (targetBeats * beatMs).toLong().coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
    }

    private val MusicalBeatCounts = intArrayOf(1, 2, 4, 6, 8, 12, 16, 24, 32)

    /** Human-friendly beat-match summary for the Transition Editor UI (no confusing key codes). */
    fun describeTempoMatch(
        outAnalysis: TrackAnalysis?,
        inAnalysis: TrackAnalysis?,
        mode: MixItBeatMatchMode,
        customSpeedRatio: Float = 0f,
    ): TempoMatchSummary {
        val outBpm = outAnalysis?.bpm?.takeIf { outAnalysis.isBeatReliable }
        val inBpm = inAnalysis?.bpm?.takeIf { inAnalysis.isBeatReliable }
        val outBeatMs = outBpm?.let { 60_000.0 / it } ?: DefaultBeatMs
        val inBeatMs = inBpm?.let { 60_000.0 / it }
        val resolved = resolveSpeeds(
            outBeatMs = outBeatMs,
            inBeatMsRaw = inBeatMs,
            outReliable = outBpm != null,
            inReliable = inBpm != null,
            mode = mode,
            customSpeedRatio = customSpeedRatio,
        )
        val hasCustomSpeed = customSpeedRatio in 0.75f..1.30f && abs(customSpeedRatio - 1f) > 0.002f
        val direction = when {
            hasCustomSpeed -> TempoSyncDirection.SONG2_TO_SONG1
            mode == MixItBeatMatchMode.OFF -> TempoSyncDirection.NONE
            outBpm == null || inBpm == null -> TempoSyncDirection.NONE
            !resolved.beatMatched -> TempoSyncDirection.GAP
            abs(resolved.inSpeed - 1f) < 0.005f && abs(resolved.outSpeed - 1f) < 0.005f -> TempoSyncDirection.NATURAL_LOCK
            resolved.inSpeed != 1f -> TempoSyncDirection.SONG2_TO_SONG1
            else -> TempoSyncDirection.SONG1_TO_SONG2
        }
        val syncedBpm = when (direction) {
            TempoSyncDirection.NATURAL_LOCK, TempoSyncDirection.SONG2_TO_SONG1 -> outBpm
            TempoSyncDirection.SONG1_TO_SONG2 -> inBpm
            else -> null
        }
        val title = when (direction) {
            TempoSyncDirection.NATURAL_LOCK -> "${outBpm?.roundToInt() ?: 120} BPM · Synced"
            TempoSyncDirection.SONG2_TO_SONG1 -> if (hasCustomSpeed) "${(customSpeedRatio * 100).roundToInt()}% Speed" else "Match → Song 1"
            TempoSyncDirection.SONG1_TO_SONG2 -> "Match → Song 2"
            TempoSyncDirection.GAP -> "Free Sync"
            TempoSyncDirection.NONE -> if (mode == MixItBeatMatchMode.OFF) "Sync Off" else "Beat-Match"
        }
        val detail = when (direction) {
            TempoSyncDirection.NATURAL_LOCK -> "${outBpm?.roundToInt()} BPM"
            TempoSyncDirection.SONG2_TO_SONG1 -> "${inBpm?.roundToInt() ?: "?"} → ${outBpm?.roundToInt() ?: "?"} BPM"
            TempoSyncDirection.SONG1_TO_SONG2 -> "${outBpm?.roundToInt() ?: "?"} → ${inBpm?.roundToInt() ?: "?"} BPM"
            TempoSyncDirection.GAP -> "${outBpm?.roundToInt() ?: "?"} / ${inBpm?.roundToInt() ?: "?"} BPM"
            TempoSyncDirection.NONE -> ""
        }
        return TempoMatchSummary(
            title = title,
            detail = detail,
            isLocked = resolved.beatMatched,
            outBpm = outBpm,
            inBpm = inBpm,
            inSpeed = resolved.inSpeed,
            outSpeed = resolved.outSpeed,
            direction = direction,
            syncedBpm = syncedBpm,
        )
    }

    /** Crisp connector label shown between two tracks on the playlist screen. */
    fun displayLabel(transition: PlaylistTransition): String {
        val sec = (transition.durationMs / 1000f)
        val secText = if (abs(sec - sec.roundToInt()) < 0.15f) "${sec.roundToInt()}s" else "%.1fs".format(sec)
        val name = transition.presetName?.takeIf { it.isNotBlank() } ?: run {
            val outFx = MixItEffect.fromName(transition.outEffect)
            val inFx = MixItEffect.fromName(transition.inEffect)
            when {
                outFx != MixItEffect.CROSSFADE && outFx != MixItEffect.NONE -> outFx.label
                inFx != MixItEffect.CROSSFADE && inFx != MixItEffect.NONE -> inFx.label
                transition.bassSwap -> "Bass Swap"
                else -> "Custom Fade"
            }
        }
        return "$name · $secText"
    }

    private fun mapStyleToOutEffect(style: CrossmixStyle): MixItEffect = when (style) {
        CrossmixStyle.EchoOut, CrossmixStyle.ThunderDrop -> MixItEffect.ECHO
        CrossmixStyle.DubThrow -> MixItEffect.DUB_ECHO
        CrossmixStyle.ReverbWash, CrossmixStyle.VocalHandoff -> MixItEffect.REVERB
        CrossmixStyle.FilterSweep, CrossmixStyle.BreakdownBridge -> MixItEffect.LOW_PASS
        CrossmixStyle.HighPassLift -> MixItEffect.HIGH_PASS
        CrossmixStyle.Backspin, CrossmixStyle.RewindWash -> MixItEffect.BACKSPIN
        CrossmixStyle.TapeStop, CrossmixStyle.PowerDown -> MixItEffect.TAPE_STOP
        CrossmixStyle.LoopRoll -> MixItEffect.LOOP_ROLL
        CrossmixStyle.StutterGate, CrossmixStyle.GateFade -> MixItEffect.STUTTER_GATE
        CrossmixStyle.RiserBuild -> MixItEffect.RISER
        CrossmixStyle.PhaserGlide -> MixItEffect.PHASER
        CrossmixStyle.LofiMelt -> MixItEffect.LOFI_MELT
        CrossmixStyle.SpatialOrbit, CrossmixStyle.DopplerPass -> MixItEffect.ORBIT
        CrossmixStyle.FarAway, CrossmixStyle.Dampen -> MixItEffect.DAMPEN
        else -> MixItEffect.CROSSFADE
    }

    private fun mapStyleToInEffect(style: CrossmixStyle): MixItEffect = when (style) {
        CrossmixStyle.FilterSweep, CrossmixStyle.BassSwap, CrossmixStyle.SilkBlend, CrossmixStyle.HarmonicGlide -> MixItEffect.HIGH_PASS
        CrossmixStyle.ReverbWash -> MixItEffect.REVERB
        CrossmixStyle.PhaserGlide -> MixItEffect.PHASER
        CrossmixStyle.LofiMelt -> MixItEffect.LOFI_MELT
        CrossmixStyle.SpatialOrbit, CrossmixStyle.DopplerPass -> MixItEffect.ORBIT
        CrossmixStyle.BreakdownBridge -> MixItEffect.LOW_PASS
        else -> MixItEffect.CROSSFADE
    }
}
