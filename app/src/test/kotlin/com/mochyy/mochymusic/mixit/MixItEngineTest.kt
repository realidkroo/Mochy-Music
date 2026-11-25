// MixItEngineTest.kt

package com.mochyy.mochymusic.mixit

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.FxParams
import com.mochyy.mochymusic.crossmix.StreamingTrackAnalyzer
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.crossmix.WaveformRateHz
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import com.mochyy.mochymusic.ui.mixit.waveformBarNorms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MixItEngineTest {

    private fun synthTrack(bpm: Double, seconds: Int, quietIntroSeconds: Int = 0): FloatArray {
        val rate = StreamingTrackAnalyzer.SampleRate
        val out = FloatArray(rate * seconds)
        val beat = 60.0 / bpm
        val random = Random(11)
        var t = 0.25
        var index = 0
        while (t < seconds) {
            val scale = if (t < quietIntroSeconds) 0.12f else 1.0f
            val start = (t * rate).toInt()
            val accent = if (index % 4 == 0) 1.0f else 0.55f
            for (i in 0 until (0.12 * rate).toInt()) {
                val n = start + i
                if (n >= out.size) break
                val env = exp(-i / (0.03 * rate)).toFloat()
                out[n] += scale * accent * 0.8f * env * sin(2 * PI * (55.0 + 60.0 * exp(-i / (0.01 * rate))) * i / rate).toFloat()
            }
            val hat = ((t + beat / 2) * rate).toInt()
            for (i in 0 until (0.03 * rate).toInt()) {
                val n = hat + i
                if (n >= out.size) break
                out[n] += scale * 0.12f * exp(-i / (0.006 * rate)).toFloat() * (random.nextFloat() * 2f - 1f)
            }
            t += beat
            index++
        }
        return out
    }

    private fun analyze(samples: FloatArray): TrackAnalysis {
        val analyzer = StreamingTrackAnalyzer()
        val chunk = FloatArray(4_096)
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(chunk.size, samples.size - offset)
            System.arraycopy(samples, offset, chunk, 0, count)
            analyzer.feed(chunk, count)
            offset += count
        }
        return analyzer.finish(complete = true)!!
    }

    private val track124 by lazy { analyze(synthTrack(124.0, 180)) }
    private val track128 by lazy { analyze(synthTrack(128.0, 180)) }
    private val track90 by lazy { analyze(synthTrack(90.0, 180)) }

    private fun phaseDistance(a: Double, b: Double): Double {
        val d = abs(((a - b) % 1.0 + 1.0) % 1.0)
        return min(d, 1.0 - d)
    }

    @Test
    fun `waveform envelope captures quiet intro vs loud drop at 32 Hz and slices peak vs body`() {
        assertEquals(32f, WaveformRateHz, 0.01f)
        val analysis = analyze(synthTrack(120.0, 40, quietIntroSeconds = 12))
        assertTrue(
            "expected >= ${(38 * WaveformRateHz).toInt()} waveform buckets, got ${analysis.waveform.size}",
            analysis.waveform.size >= (38 * WaveformRateHz).toInt(),
        )
        val introAvg = (2..10).map { analysis.waveformAt(it * 1_000L) }.average()
        val dropAvg = (18..30).map { analysis.waveformAt(it * 1_000L) }.average()
        assertTrue("drop ($dropAvg) should be louder than quiet intro ($introAvg)", dropAvg > introAvg * 1.4)
        for (v in analysis.waveform) {
            assertTrue("waveform value out of 0..1: $v", v in 0f..1f)
        }

        val (introPeak, introBody) = analysis.waveformSlice(3_000L, 6_000L)
        val (dropPeak, dropBody) = analysis.waveformSlice(20_000L, 23_000L)
        assertTrue("introPeak ($introPeak) >= introBody ($introBody)", introPeak >= introBody - 1e-4f)
        assertTrue("dropPeak ($dropPeak) >= dropBody ($dropBody)", dropPeak >= dropBody - 1e-4f)
        assertTrue("dropPeak ($dropPeak) > introPeak ($introPeak)", dropPeak > introPeak * 1.4f)
    }

    @Test
    fun `waveformBarNorms never throws on silent, sub-threshold, inverted, or non-finite samples`() {
        val out = FloatArray(2)
        val cases = listOf(
            0f to 0f,
            0.005f to 0.005f,
            0.015f to 0.014f,
            0.019f to 0.019f,
            0.02f to 0.02f,
            0.03f to 0.9f,
            1.8f to 2.5f,
            -0.5f to -1f,
            Float.NaN to Float.NaN,
            Float.POSITIVE_INFINITY to Float.NEGATIVE_INFINITY,
        )
        for ((peak, body) in cases) {
            waveformBarNorms(peak, body, out)
            assertTrue("peakNorm finite and in 0.03..1 for ($peak,$body), got ${out[0]}", out[0].isFinite() && out[0] in 0.03f..1f)
            assertTrue("bodyNorm finite and in 0.02..peakNorm for ($peak,$body), got ${out[1]} vs ${out[0]}", out[1].isFinite() && out[1] in 0.02f..out[0])
        }

        // a track with complete digital silence at the start and end still slices cleanly without throwing
        val withSilence = FloatArray(StreamingTrackAnalyzer.SampleRate * 12)
        val quiet = synthTrack(120.0, 4)
        System.arraycopy(quiet, 0, withSilence, StreamingTrackAnalyzer.SampleRate * 4, quiet.size)
        val analysis = analyze(withSilence)
        for (sec in 0..11) {
            analysis.waveformSliceInto(sec * 1_000L, (sec + 1) * 1_000L, out)
            waveformBarNorms(out[0], out[1], out)
            assertTrue(out[0] in 0.03f..1f)
            assertTrue(out[1] in 0.02f..out[0])
        }
    }

    @Test
    fun `custom transition can start anywhere in Song 1 and enter anywhere in Song 2`() {
        val placements = listOf(
            Triple(6_000L, 45_000L, 6_000L),    // early out -> middle in
            Triple(88_000L, 92_000L, 10_000L),  // middle out -> middle in
            Triple(165_000L, 0L, 8_000L),       // end out -> start in
        )
        for ((outStart, inStart, dur) in placements) {
            val t = PlaylistTransition(
                playlistId = "LP_TEST",
                fromSongId = "songA",
                toSongId = "songB",
                outStartMs = outStart,
                inStartMs = inStart,
                durationMs = dur,
                beatMatchMode = MixItBeatMatchMode.OFF.name,
                beatNudgeMs = 35,
            )
            val plan = MixItPlanner.buildPlan(t, 180_000L, 180_000L, track124, track128)
            assertEquals(outStart, plan.outStartMs)
            assertEquals((inStart + 35).toDouble(), plan.inMediaAt(0f), 2.0)
            assertEquals(dur.toDouble(), plan.effectiveLengthBeats * plan.outBeatMs, 15.0)
            assertTrue(plan.mixIt != null)
        }
    }

    @Test
    fun `beat-matched transitions phase-lock Song 2 kicks directly onto Song 1 kicks`() {
        val cases = listOf(
            Triple(6_120L, 45_210L, MixItBeatMatchMode.AUTO),
            Triple(88_340L, 92_080L, MixItBeatMatchMode.MATCH_OUT),
            Triple(120_190L, 30_410L, MixItBeatMatchMode.MATCH_IN),
            Triple(165_220L, 0L, MixItBeatMatchMode.AUTO), // near 0:00 wrap-forward edge case
        )
        for ((outStart, inStart, mode) in cases) {
            val t = PlaylistTransition(
                playlistId = "LP_LOCK",
                fromSongId = "songA",
                toSongId = "songB",
                outStartMs = outStart,
                inStartMs = inStart,
                durationMs = 8_000L,
                beatMatchMode = mode.name,
                beatNudgeMs = 20,
            )
            val plan = MixItPlanner.buildPlan(t, 180_000L, 180_000L, track124, track128)
            assertTrue("Expected beatMatched for $mode", plan.beatMatched)

            val outGrid = track124.localGrid(outStart)!!
            val inGrid = track128.localGrid(inStart)!!
            val alignedIn = plan.inMediaAt(0f) - 20.0
            val outPhase = (((outStart - outGrid.firstBeatMs) / outGrid.periodMs) % 1.0 + 1.0) % 1.0
            val inPhase = (((alignedIn - inGrid.firstBeatMs) / plan.inMediaPerBeat) % 1.0 + 1.0) % 1.0
            val errBeats = phaseDistance(outPhase, inPhase)
            assertTrue(
                "Beat phase mismatch for $mode at ($outStart,$inStart): err=$errBeats beats (alignedIn=$alignedIn)",
                errBeats < 0.02,
            )
            // Never jumps far from where the user dragged
            assertTrue(
                "Phase lock shifted too far: ${abs(alignedIn - inStart)} ms vs beat ${plan.inMediaPerBeat} ms",
                abs(alignedIn - inStart) <= plan.inMediaPerBeat + 5.0,
            )
        }
    }

    @Test
    fun `beat match modes auto, match out, match in bidirectional, off, and custom speed work cleanly`() {
        val base = PlaylistTransition("LP1", "a", "b", outStartMs = 90_000L, inStartMs = 30_000L, durationMs = 8_000L)

        // AUTO: 124 -> 128 speeds outgoing song gently up to 128
        val autoClose = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.AUTO.name), 180_000L, 180_000L, track124, track128)
        assertTrue(autoClose.beatMatched)
        assertEquals(128.0 / 124.0, autoClose.outSpeed.toDouble(), 0.02)
        assertEquals(1f, autoClose.inSpeed, 0.001f)

        // AUTO with big tempo gap (124 -> 90): free blend without stretching
        val autoFar = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.AUTO.name), 180_000L, 180_000L, track124, track90)
        assertFalse(autoFar.beatMatched)
        assertEquals(1f, autoFar.inSpeed, 0.001f)
        assertEquals(1f, autoFar.outSpeed, 0.001f)

        // MATCH_OUT: pulls Song 2 (128) down to Song 1 (124)
        val matchOut = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.MATCH_OUT.name), 180_000L, 180_000L, track124, track128)
        assertTrue(matchOut.beatMatched)
        assertEquals(124.0 / 128.0, matchOut.inSpeed.toDouble(), 0.02)
        assertEquals(1f, matchOut.outSpeed, 0.001f)

        // MATCH_IN faster: ramps Song 1 (124) UP to Song 2 (128)
        val matchInUp = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.MATCH_IN.name), 180_000L, 180_000L, track124, track128)
        assertTrue(matchInUp.beatMatched)
        assertEquals(128.0 / 124.0, matchInUp.outSpeed.toDouble(), 0.02)
        assertEquals(1f, matchInUp.inSpeed, 0.001f)

        // MATCH_IN slower: ramps Song 1 (128) DOWN to Song 2 (124)
        val matchInDown = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.MATCH_IN.name), 180_000L, 180_000L, track128, track124)
        assertTrue(matchInDown.beatMatched)
        assertEquals(124.0 / 128.0, matchInDown.outSpeed.toDouble(), 0.02)
        assertEquals(1f, matchInDown.inSpeed, 0.001f)

        // OFF: original speeds
        val off = MixItPlanner.buildPlan(base.copy(beatMatchMode = MixItBeatMatchMode.OFF.name), 180_000L, 180_000L, track124, track128)
        assertFalse(off.beatMatched)
        assertEquals(1f, off.inSpeed, 0.001f)
        assertEquals(1f, off.outSpeed, 0.001f)

        // Custom speed override
        val custom = MixItPlanner.buildPlan(base.copy(customSpeedRatio = 1.06f), 180_000L, 180_000L, track124, track90)
        assertTrue(custom.beatMatched)
        assertEquals(1.06f, custom.inSpeed, 0.001f)
    }

    @Test
    fun `tempo summary exposes sync direction cleanly and is free of Camelot key codes`() {
        val auto124To128 = MixItPlanner.describeTempoMatch(track124, track128, MixItBeatMatchMode.AUTO)
        assertTrue(auto124To128.isLocked)
        assertEquals(TempoSyncDirection.SONG1_TO_SONG2, auto124To128.direction)
        val combined = "${auto124To128.title} ${auto124To128.detail}"
        assertFalse("Should not contain Camelot key jargon: $combined", Regex("""\b\d{1,2}[AB]\b""").containsMatchIn(combined))
        assertTrue(combined.contains("BPM"))

        val matchOut = MixItPlanner.describeTempoMatch(track124, track128, MixItBeatMatchMode.MATCH_OUT)
        assertEquals(TempoSyncDirection.SONG2_TO_SONG1, matchOut.direction)

        val matchInDown = MixItPlanner.describeTempoMatch(track128, track124, MixItBeatMatchMode.MATCH_IN)
        assertEquals(TempoSyncDirection.SONG1_TO_SONG2, matchInDown.direction)

        val natural = MixItPlanner.describeTempoMatch(track124, track124, MixItBeatMatchMode.AUTO)
        assertEquals(TempoSyncDirection.NATURAL_LOCK, natural.direction)

        val gap = MixItPlanner.describeTempoMatch(track124, track90, MixItBeatMatchMode.AUTO)
        assertEquals(TempoSyncDirection.GAP, gap.direction)

        val off = MixItPlanner.describeTempoMatch(track124, track128, MixItBeatMatchMode.OFF)
        assertEquals(TempoSyncDirection.NONE, off.direction)
    }

    @Test
    fun `snap and auto align lock cue points onto the fitted beat grid`() {
        val grid = track128.localGrid(60_000L)!!
        val trueBeat = grid.snap(60_000L)
        val slightlyOff = trueBeat + 55L
        val snapped = MixItPlanner.snapToNearestBeat(slightlyOff, track128)
        assertEquals(trueBeat.toDouble(), snapped.toDouble(), 2.0)

        val (alignedOut, alignedIn) = MixItPlanner.autoAlignAtRegion(60_040L, 30_045L, track124, track128)
        assertTrue(alignedOut >= 0L)
        assertTrue(alignedIn >= 0L)
    }

    @Test
    fun `all presets produce valid curve ordering and specs`() {
        val base = PlaylistTransition("LP1", "a", "b", outStartMs = 60_000L, inStartMs = 15_000L)
        for (preset in MixItPreset.entries) {
            val applied = preset.applyTo(base, beatMs = 500.0)
            assertTrue("${preset.title} duration", applied.durationMs in MixItLimits.MinDurationMs..MixItLimits.MaxDurationMs)
            assertTrue("${preset.title} out fade", applied.outFadeStart < applied.outFadeEnd)
            assertTrue("${preset.title} in fade", applied.inFadeStart < applied.inFadeEnd)
            val spec = MixItSpec.from(applied)
            assertEquals(preset.outEffect, spec.outEffect)
            assertEquals(preset.inEffect, spec.inEffect)
            assertEquals(1f, spec.outVoiceLevel, 1e-4f)
            assertEquals(1f, spec.outBassLevel, 1e-4f)
            assertEquals(1f, spec.outInstrumentLevel, 1e-4f)
        }
    }

    private class Rendered(val input: FloatArray, val output: FloatArray)

    private fun renderMixIt(
        spec: MixItSpec,
        role: CrossmixRole,
        lengthBeats: Float = 8f,
        hz: Double = 440.0,
        stereoSideOnly: Boolean = false,
    ): Rendered {
        val rate = 44_100
        val beatMs = 500.0
        val endBeat = if (role == CrossmixRole.Outgoing) lengthBeats + spec.tailBeats + 2f else lengthBeats + 8f
        val seconds = 2.0 + endBeat * 0.5 + 1.0
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = role,
                style = com.mochyy.mochymusic.crossmix.CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = lengthBeats,
                swapBeat = lengthBeats * spec.swapFrac,
                anchorMs = 2_000.0,
                mediaPerBeatMs = beatMs,
                musicalBeatMs = beatMs,
                endBeat = endBeat,
                calibrationMs = 0.0,
                calibrationNanos = 0L,
                mixIt = spec,
            ),
        )
        val frames = (seconds * rate).toInt()
        val input = FloatArray(frames)
        val output = FloatArray(frames)
        val block = 1_024
        var frame = 0
        while (frame < frames) {
            val count = minOf(block, frames - frame)
            val buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                val v = (0.5 * sin(2 * PI * hz * (frame + i) / rate)).toFloat()
                input[frame + i] = v
                val sL = (v * 32_767).toInt().toShort()
                val sR = ((if (stereoSideOnly) -v else v) * 32_767).toInt().toShort()
                buffer.putShort(sL); buffer.putShort(sR)
            }
            buffer.flip()
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buffer)
            val out = processor.output
            for (i in 0 until count) {
                output[frame + i] = out.getShort() / 32_768f
                out.getShort()
            }
            frame += count
        }
        return Rendered(input, output)
    }

    private fun rmsLevel(r: Rendered, fromS: Double, toS: Double): Double {
        val a = (fromS * 44_100).toInt()
        val b = (toS * 44_100).toInt()
        return sqrt((a until b).sumOf { (r.output[it] * r.output[it]).toDouble() } / (b - a))
    }

    @Test
    fun `every Mix It effect renders without NaNs, clipping, or clicks and respects boundaries`() {
        for (fx in MixItEffect.outgoingOptions) {
            val spec = MixItSpec(
                outEffect = fx,
                inEffect = MixItEffect.CROSSFADE,
                outFadeStart = 0.2f,
                outFadeEnd = 0.95f,
                inFadeStart = 0.05f,
                inFadeEnd = 0.85f,
                bassSwap = true,
                swapFrac = 0.5f,
                label = fx.label,
            )
            val r = renderMixIt(spec, CrossmixRole.Outgoing)
            var maxJump = 0f
            for (i in 1 until r.output.size) {
                val v = r.output[i]
                assertFalse("Out ${fx.name} NaN at $i", v.isNaN())
                assertTrue("Out ${fx.name} clipped at $i", abs(v) <= 1f)
                maxJump = maxOf(maxJump, abs(v - r.output[i - 1]))
            }
            assertTrue("Out ${fx.name} click jump $maxJump", maxJump < 0.6f)
            // Untouched before beat 0
            val before = (1.5 * 44_100).toInt()
            for (i in 0 until before) assertEquals("Out ${fx.name} before at $i", r.input[i], r.output[i], 2e-4f)
            // Silent once transition + tail finished
            val after = ((2.0 + (8f + fx.tailBeats + 2.5f) * 0.5) * 44_100).toInt()
            for (i in after until r.output.size) assertEquals("Out ${fx.name} after at $i", 0f, r.output[i], 2e-3f)
        }

        for (fx in MixItEffect.incomingOptions) {
            val spec = MixItSpec(
                outEffect = MixItEffect.CROSSFADE,
                inEffect = fx,
                outFadeStart = 0.2f,
                outFadeEnd = 0.95f,
                inFadeStart = 0.05f,
                inFadeEnd = 0.85f,
                bassSwap = true,
                swapFrac = 0.5f,
                label = fx.label,
            )
            val r = renderMixIt(spec, CrossmixRole.Incoming)
            var maxJump = 0f
            for (i in 1 until r.output.size) {
                val v = r.output[i]
                assertFalse("In ${fx.name} NaN at $i", v.isNaN())
                assertTrue("In ${fx.name} clipped at $i", abs(v) <= 1f)
                maxJump = maxOf(maxJump, abs(v - r.output[i - 1]))
            }
            assertTrue("In ${fx.name} click jump $maxJump", maxJump < 0.6f)
            // Silent before beat 0
            val before = (1.5 * 44_100).toInt()
            for (i in 0 until before) assertEquals("In ${fx.name} before at $i", 0f, r.output[i], 1e-3f)
            // Dry song once settled
            val after = ((2.0 + (8f + 9f) * 0.5) * 44_100).toInt()
            for (i in after until r.output.size) assertEquals("In ${fx.name} after at $i", r.input[i], r.output[i], 2e-4f)
        }
    }

    @Test
    fun `voice, bass, and instrument stem sliders cut target bands cleanly and release back to dry`() {
        val baseSpec = MixItSpec(
            outEffect = MixItEffect.NONE,
            inEffect = MixItEffect.NONE,
            outFadeStart = 0.85f,
            outFadeEnd = 0.99f,
            inFadeStart = 0.01f,
            inFadeEnd = 0.15f,
            bassSwap = false,
            swapFrac = 0.5f,
            label = "Stem Test",
        )

        // 1. Outgoing Voice cut reduces centered 1 kHz vocal signal cleanly
        val dryVoice = renderMixIt(baseSpec, CrossmixRole.Outgoing, lengthBeats = 16f, hz = 1_000.0)
        val cutVoice = renderMixIt(baseSpec.copy(outVoiceLevel = 0f), CrossmixRole.Outgoing, lengthBeats = 16f, hz = 1_000.0)
        val dryVoiceRms = rmsLevel(dryVoice, 4.5, 6.0)
        val cutVoiceRms = rmsLevel(cutVoice, 4.5, 6.0)
        assertTrue("Expected voice cut ($cutVoiceRms) << dry ($dryVoiceRms)", cutVoiceRms < dryVoiceRms * 0.25)

        // 2. Outgoing Bass cut removes 60 Hz kick/bass cleanly
        val dryBass = renderMixIt(baseSpec, CrossmixRole.Outgoing, lengthBeats = 16f, hz = 60.0)
        val cutBass = renderMixIt(baseSpec.copy(outBassLevel = 0f), CrossmixRole.Outgoing, lengthBeats = 16f, hz = 60.0)
        val dryBassRms = rmsLevel(dryBass, 4.5, 6.0)
        val cutBassRms = rmsLevel(cutBass, 4.5, 6.0)
        assertTrue("Expected bass cut ($cutBassRms) << dry ($dryBassRms)", cutBassRms < dryBassRms * 0.12)

        // 3. Outgoing Instrument cut reduces stereo-side 1.2 kHz accompaniment cleanly
        val dryInst = renderMixIt(baseSpec, CrossmixRole.Outgoing, lengthBeats = 16f, hz = 1_200.0, stereoSideOnly = true)
        val cutInst = renderMixIt(
            baseSpec.copy(outInstrumentLevel = 0f),
            CrossmixRole.Outgoing,
            lengthBeats = 16f,
            hz = 1_200.0,
            stereoSideOnly = true,
        )
        val dryInstRms = rmsLevel(dryInst, 4.5, 6.0)
        val cutInstRms = rmsLevel(cutInst, 4.5, 6.0)
        assertTrue("Expected instrument side cut ($cutInstRms) << dry ($dryInstRms)", cutInstRms < dryInstRms * 0.25)

        // 4. Incoming stem cuts attenuate during the overlap AND glide smoothly back to 100% dry song after!
        val incCut = renderMixIt(
            baseSpec.copy(inVoiceLevel = 0f, inBassLevel = 0f, inInstrumentLevel = 0f),
            CrossmixRole.Incoming,
            lengthBeats = 8f,
            hz = 1_000.0,
        )
        for (i in 1 until incCut.output.size) {
            assertFalse("Incoming stem NaN at $i", incCut.output[i].isNaN())
            assertTrue("Incoming stem clipped at $i", abs(incCut.output[i]) <= 1f)
        }
        val duringBlendRms = rmsLevel(incCut, 3.0, 4.2)
        val afterSettleRms = rmsLevel(incCut, 9.0, 10.0)
        assertTrue("Incoming voice cut during blend ($duringBlendRms) << dry after ($afterSettleRms)", duringBlendRms < afterSettleRms * 0.25)
        // Exact untouched dry audio once settled
        val settledStart = ((2.0 + (8f + 9f) * 0.5) * 44_100).toInt()
        for (i in settledStart until incCut.output.size) {
            assertEquals("Incoming stem should release to exact dry at $i", incCut.input[i], incCut.output[i], 2e-4f)
        }
    }

    @Test
    fun `bass swap cleanly hands 60 Hz kick from Song 1 to Song 2`() {
        val spec = MixItSpec(
            outEffect = MixItEffect.CROSSFADE,
            inEffect = MixItEffect.CROSSFADE,
            outFadeStart = 0.55f,
            outFadeEnd = 0.98f,
            inFadeStart = 0.02f,
            inFadeEnd = 0.45f,
            bassSwap = true,
            swapFrac = 0.5f,
            label = "Bass Swap",
        )
        // 16 beats @ 500ms = 8s starting at 2s -> swap at beat 8 = 6.0s
        val out = renderMixIt(spec, CrossmixRole.Outgoing, lengthBeats = 16f, hz = 60.0)
        val inc = renderMixIt(spec, CrossmixRole.Incoming, lengthBeats = 16f, hz = 60.0)
        assertTrue("Out bass before swap", rmsLevel(out, 4.4, 5.4) > 0.2)
        assertTrue("Out bass cut after swap", rmsLevel(out, 6.2, 6.8) < 0.02)
        assertTrue("In bass cut before swap", rmsLevel(inc, 4.4, 5.4) < 0.02)
        assertTrue("In bass arrives after swap", rmsLevel(inc, 6.2, 6.8) > 0.2)
    }

    @Test
    fun `MixItScriptEvaluator respects custom user fade curves`() {
        val spec = MixItSpec(
            outEffect = MixItEffect.NONE,
            inEffect = MixItEffect.NONE,
            outFadeStart = 0.5f,
            outFadeEnd = 0.8f,
            inFadeStart = 0.4f,
            inFadeEnd = 0.7f,
            bassSwap = false,
            swapFrac = 0.6f,
            label = "Custom",
        )
        val p = FxParams()
        // Outgoing holds 1.0 until 0.5 (beat 5 of 10), silent by 0.8 (beat 8 of 10)
        MixItScriptEvaluator.evaluate(spec, CrossmixRole.Outgoing, beat = 4f, length = 10f, swap = 6f, out = p)
        assertEquals(1f, p.gain, 1e-4f)
        MixItScriptEvaluator.evaluate(spec, CrossmixRole.Outgoing, beat = 8.5f, length = 10f, swap = 6f, out = p)
        assertEquals(0f, p.gain, 1e-4f)

        // Incoming stays silent until 0.4 (beat 4), full by 0.7 (beat 7)
        MixItScriptEvaluator.evaluate(spec, CrossmixRole.Incoming, beat = 3.5f, length = 10f, swap = 6f, out = p)
        assertEquals(0f, p.gain, 1e-4f)
        MixItScriptEvaluator.evaluate(spec, CrossmixRole.Incoming, beat = 7.5f, length = 10f, swap = 6f, out = p)
        assertEquals(1f, p.gain, 1e-4f)
    }

    @Test
    fun `early and ultra-short custom transitions never produce negative firstMoveMs`() {
        for ((outStart, inStart, dur) in listOf(
            Triple(0L, 45_000L, 2_000L),
            Triple(500L, 60_000L, 1_000L),
            Triple(1_000L, 81_000L, 500L),
        )) {
            val t = PlaylistTransition(
                playlistId = "LP_SHORT",
                fromSongId = "s1",
                toSongId = "s2",
                outStartMs = outStart,
                inStartMs = inStart,
                durationMs = dur,
                beatMatchMode = MixItBeatMatchMode.OFF.name,
            )
            val plan = MixItPlanner.buildPlan(t, 180_000L, 180_000L, track124, track128)
            assertTrue(
                "outMediaAt(inStartBeat) must never be negative: ${plan.outMediaAt(plan.inStartBeat)}",
                plan.outMediaAt(plan.inStartBeat) >= -0.01,
            )
            assertEquals(outStart, plan.outStartMs)
            assertEquals(inStart.toDouble(), plan.inMediaAt(0f), 2.0)
        }
    }

    @Test
    fun `chained short transitions 2m23-to-3m32 then 3m36-to-1m21 fit pre-roll without cutting T1`() {
        val t1Out = 143_000L // 2:23
        val t1In = 212_000L  // 3:32
        val t2Out = 216_000L // 3:36 (only 4s after 3:32!)
        val t2In = 81_000L   // 1:21

        val t2 = PlaylistTransition(
            playlistId = "LP_CHAIN",
            fromSongId = "song2",
            toSongId = "song3",
            outStartMs = t2Out,
            inStartMs = t2In,
            durationMs = 4_000L,
            beatMatchMode = MixItBeatMatchMode.OFF.name,
        )
        val rawT2Plan = MixItPlanner.buildPlan(t2, 240_000L, 240_000L, null, null)
        // Default 4-beat pre-roll (2000ms @ 500ms/beat)
        assertEquals(-4f, rawT2Plan.inStartBeat, 0.01f)

        // Case 1: T1 is 2s (ends on Song 2 at 3:34 = 214_000ms) -> full 2s pre-roll fits inside [3:34, 3:36]!
        val fitAfter2s = com.mochyy.mochymusic.playback.CrossmixDirector.fitCustomPreRoll(
            plan = rawT2Plan,
            currentPositionMs = 210_000L, // Song 2 promoted at 3:30
            prevIncomingEndMs = (t1In + 2_000L).toDouble(),
        )
        assertEquals(-4f, fitAfter2s.inStartBeat, 0.01f)
        assertEquals(214_000.0, fitAfter2s.outMediaAt(fitAfter2s.inStartBeat), 1.0)
        assertEquals(t2In.toDouble(), fitAfter2s.inMediaAt(0f), 1.0)

        // Case 2: T1 is 3s (ends on Song 2 at 3:35 = 215_000ms) -> pre-roll shrinks to 2 beats (1s) so T1 isn't cut
        val fitAfter3s = com.mochyy.mochymusic.playback.CrossmixDirector.fitCustomPreRoll(
            plan = rawT2Plan,
            currentPositionMs = 210_000L,
            prevIncomingEndMs = (t1In + 3_000L).toDouble(),
        )
        assertEquals(-2f, fitAfter3s.inStartBeat, 0.01f)
        assertEquals(215_000.0, fitAfter3s.outMediaAt(fitAfter3s.inStartBeat), 1.0)
        assertEquals(t2In.toDouble(), fitAfter3s.inMediaAt(0f), 1.0)

        // Case 3: T1 is 4s (3:32..3:36) or 8s (3:32..3:40, overlapping T2) -> pre-roll shrinks to 0 beats
        for (t1Dur in listOf(4_000L, 8_000L)) {
            val fitTight = com.mochyy.mochymusic.playback.CrossmixDirector.fitCustomPreRoll(
                plan = rawT2Plan,
                currentPositionMs = 210_000L,
                prevIncomingEndMs = (t1In + t1Dur).toDouble(),
            )
            assertEquals("Expected 0 pre-roll for T1 dur $t1Dur", 0f, fitTight.inStartBeat, 0.01f)
            assertEquals(t2Out.toDouble(), fitTight.outMediaAt(fitTight.inStartBeat), 1.0)
            assertEquals(t2In, fitTight.inStartMs)
        }

        // Case 4: User seeks to 1s before outStartMs -> pre-roll shrinks instead of aborting
        val fitAfterSeek = com.mochyy.mochymusic.playback.CrossmixDirector.fitCustomPreRoll(
            plan = rawT2Plan,
            currentPositionMs = t2Out - 1_000L,
            prevIncomingEndMs = null,
        )
        assertTrue(
            "Expected firstMoveMs (${fitAfterSeek.outMediaAt(fitAfterSeek.inStartBeat)}) ahead of currentPosition",
            fitAfterSeek.outMediaAt(fitAfterSeek.inStartBeat) >= t2Out - 401.0,
        )

        // PlayAt compensates for residual beat-lock delay carried on outgoing from T1
        val basePlayAt = com.mochyy.mochymusic.playback.CrossmixDirector.computePlayAtMs(fitAfter2s, 60.0, 70.0, 1f, 0.0)
        val delayedPlayAt = com.mochyy.mochymusic.playback.CrossmixDirector.computePlayAtMs(fitAfter2s, 60.0, 70.0, 1f, 45.0)
        assertEquals(basePlayAt + 45.0, delayedPlayAt, 0.01)

        // Analysis budget reserves room for buffering on tight chained transitions
        val tightBudget = com.mochyy.mochymusic.playback.CrossmixDirector.customAnalysisBudgetMs(
            outStartMs = t2Out,
            currentPositionMs = 210_000L, // 6s away
            speed = 1f,
            testJump = false,
        )
        assertEquals(1_500L, tightBudget)
        val longBudget = com.mochyy.mochymusic.playback.CrossmixDirector.customAnalysisBudgetMs(
            outStartMs = t1Out,
            currentPositionMs = 0L,
            speed = 1f,
            testJump = false,
        )
        assertTrue("Long runway keeps large analysis budget ($longBudget)", longBudget > 100_000L)
    }

    @Test
    fun `processor supports click-free live chained handoff from T1 Incoming directly to T2 Outgoing`() {
        val rate = 44_100
        val beatMs = 500.0
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val t1Spec = MixItSpec(
            outEffect = MixItEffect.CROSSFADE,
            inEffect = MixItEffect.CROSSFADE,
            outFadeStart = 0.2f,
            outFadeEnd = 0.9f,
            inFadeStart = 0.05f,
            inFadeEnd = 0.7f,
            bassSwap = true,
            swapFrac = 0.5f,
            label = "T1",
        )
        val t2Spec = MixItSpec(
            outEffect = MixItEffect.ECHO,
            inEffect = MixItEffect.CROSSFADE,
            outFadeStart = 0.1f,
            outFadeEnd = 0.9f,
            inFadeStart = 0.1f,
            inFadeEnd = 0.8f,
            bassSwap = true,
            swapFrac = 0.5f,
            label = "T2",
            outVoiceLevel = 0.2f,
        )

        // Song 2 enters at 210s (2s pre-roll before T1 beat 0 at 212s = 3:32), with 60ms beat-lock delay
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = CrossmixRole.Incoming,
                style = com.mochyy.mochymusic.crossmix.CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = 8f,
                swapBeat = 4f,
                anchorMs = 212_000.0,
                mediaPerBeatMs = beatMs,
                musicalBeatMs = beatMs,
                endBeat = 16f,
                calibrationMs = 210_000.0,
                calibrationNanos = 0L,
                mixIt = t1Spec,
            ),
        )
        processor.setBeatLockDelay(60.0)

        val startSec = 210.0
        val handoffSec = 215.8 // right before T2 at 216.0s (3:36)!
        val endSec = 228.0     // past T2 window (4s) + echo tail (5s) + 2 beats
        val totalFrames = ((endSec - startSec) * rate).toInt()
        val handoffFrame = ((handoffSec - startSec) * rate).toInt()
        val output = FloatArray(totalFrames)
        val block = 1_024
        var frame = 0
        var handedOff = false
        var delayAtHandoff = 0.0

        while (frame < totalFrames) {
            if (!handedOff && frame >= handoffFrame) {
                handedOff = true
                delayAtHandoff = processor.currentDelayMs
                assertTrue("Expected beat-lock delay still active before handoff ($delayAtHandoff ms)", delayAtHandoff > 40.0)
                // Seamlessly hand Song 2's processor directly from T1 Incoming -> T2 Outgoing (no flush!)
                processor.arm(
                    CrossmixTransitionAudioProcessor.Script(
                        role = CrossmixRole.Outgoing,
                        style = com.mochyy.mochymusic.crossmix.CrossmixStyle.SilkBlend,
                        variation = 0,
                        lengthBeats = 8f,
                        swapBeat = 4f,
                        anchorMs = 216_000.0,
                        mediaPerBeatMs = beatMs,
                        musicalBeatMs = beatMs,
                        endBeat = 8f + t2Spec.tailBeats + 2f,
                        calibrationMs = (startSec + frame.toDouble() / rate) * 1_000.0,
                        calibrationNanos = 0L,
                        mixIt = t2Spec,
                    ),
                    preserveDelayAndHistory = true,
                )
            }
            val count = minOf(block, totalFrames - frame)
            val buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                val t = startSec + (frame + i).toDouble() / rate
                val v = (0.5 * sin(2 * PI * 440.0 * t)).toFloat()
                val s = (v * 32_767).toInt().toShort()
                buffer.putShort(s); buffer.putShort(s)
            }
            buffer.flip()
            val ptsUs = ((startSec + frame.toDouble() / rate) * 1_000_000.0).toLong()
            processor.onInputTimestamp(ptsUs)
            processor.queueInput(buffer)
            val out = processor.output
            for (i in 0 until count) {
                output[frame + i] = out.getShort() / 32_768f
                out.getShort()
            }
            frame += count
        }

        // Verify zero NaNs, zero clipping, and zero click spikes around the live handoff seam
        for (i in 1 until output.size) {
            val v = output[i]
            assertFalse("NaN at $i", v.isNaN())
            assertTrue("Clipped at $i", abs(v) <= 1f)
        }
        val seamStart = (handoffFrame - 2_048).coerceAtLeast(1)
        val seamEnd = (handoffFrame + 4_096).coerceAtMost(output.lastIndex)
        var maxSeamJump = 0f
        for (i in seamStart..seamEnd) {
            maxSeamJump = maxOf(maxSeamJump, abs(output[i] - output[i - 1]))
        }
        assertTrue("Expected click-free handoff seam, got jump $maxSeamJump", maxSeamJump < 0.2f)

        // Verify Song 2 was audible around 3:35 (after T1 faded it in) and cleanly silent after T2 + echo tail (3:46+)
        val midRms = rmsLevel(Rendered(output, output), 4.8, 5.6)
        val endRms = rmsLevel(Rendered(output, output), 16.5, 17.5)
        assertTrue("Song 2 should be audible before T2 ($midRms)", midRms > 0.25)
        assertTrue("Song 2 should be silent after T2 + tail ($endRms)", endRms < 1e-3)
    }

    @Test
    fun `re-arming processor cleanly resets stale fadeOutNow state without flush`() {
        val rate = 44_100
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val spec = MixItSpec(
            outEffect = MixItEffect.NONE,
            inEffect = MixItEffect.NONE,
            outFadeStart = 0.2f,
            outFadeEnd = 0.9f,
            inFadeStart = 0.1f,
            inFadeEnd = 0.8f,
            bassSwap = false,
            swapFrac = 0.5f,
            label = "Reset Test",
        )
        // First script fades out early via fadeOutNow() (driving internal outFade to 0)
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = CrossmixRole.Outgoing,
                style = com.mochyy.mochymusic.crossmix.CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = 8f,
                swapBeat = 4f,
                anchorMs = 10_000.0,
                mediaPerBeatMs = 500.0,
                musicalBeatMs = 500.0,
                endBeat = 10f,
                calibrationMs = 0.0,
                calibrationNanos = 0L,
                mixIt = spec,
            ),
        )
        processor.fadeOutNow()
        var frame = 0
        repeat(8) {
            val buf = ByteBuffer.allocateDirect(1_024 * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until 1_024) {
                val s = (0.5 * sin(2 * PI * 440.0 * (frame + i) / rate) * 32_767).toInt().toShort()
                buf.putShort(s); buf.putShort(s)
            }
            buf.flip()
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buf)
            processor.output
            frame += 1_024
        }

        // Re-arm without flush (calibrationNanos = 0L simulating paused arm) -> must play full volume before beat 0!
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = CrossmixRole.Outgoing,
                style = com.mochyy.mochymusic.crossmix.CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = 8f,
                swapBeat = 4f,
                anchorMs = 10_000.0,
                mediaPerBeatMs = 500.0,
                musicalBeatMs = 500.0,
                endBeat = 10f,
                calibrationMs = frame * 1_000.0 / rate,
                calibrationNanos = 0L,
                mixIt = spec,
            ),
        )
        val outSamples = FloatArray(4_096)
        var written = 0
        repeat(4) {
            val buf = ByteBuffer.allocateDirect(1_024 * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until 1_024) {
                val s = (0.5 * sin(2 * PI * 440.0 * (frame + i) / rate) * 32_767).toInt().toShort()
                buf.putShort(s); buf.putShort(s)
            }
            buf.flip()
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buf)
            val out = processor.output
            for (i in 0 until 1_024) {
                outSamples[written++] = out.getShort() / 32_768f
                out.getShort()
            }
            frame += 1_024
        }
        val rms = sqrt(outSamples.sumOf { (it * it).toDouble() } / outSamples.size)
        assertTrue("Re-armed processor must output full audio before beat 0, got rms=$rms", rms > 0.3)
    }
}
