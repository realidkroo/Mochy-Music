package com.mochyy.mochymusic.crossmix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
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
import kotlin.random.Random

class CrossmixTest {

    // -- analysis ---------------------------------------------------------------------------

    /** a synthetic track: a kick on every beat (harder on the one), off-beat hats, a soft pad */
    private fun synthTrack(bpm: Double, seconds: Int, kickOffsetMs: Double = 250.0): FloatArray {
        val rate = StreamingTrackAnalyzer.SampleRate
        val out = FloatArray(rate * seconds)
        val beat = 60.0 / bpm
        val random = Random(7)
        var t = kickOffsetMs / 1_000.0
        var index = 0
        while (t < seconds) {
            val start = (t * rate).toInt()
            val accent = if (index % 4 == 0) 1.0f else 0.55f
            for (i in 0 until (0.12 * rate).toInt()) {
                val n = start + i
                if (n >= out.size) break
                val env = exp(-i / (0.03 * rate)).toFloat()
                out[n] += accent * 0.8f * env * sin(2 * PI * (55.0 + 60.0 * exp(-i / (0.01 * rate))) * i / rate).toFloat()
            }
            // a hat halfway to the next beat
            val hat = ((t + beat / 2) * rate).toInt()
            for (i in 0 until (0.03 * rate).toInt()) {
                val n = hat + i
                if (n >= out.size) break
                out[n] += 0.12f * exp(-i / (0.006 * rate)).toFloat() * (random.nextFloat() * 2f - 1f)
            }
            t += beat
            index++
        }
        for (n in out.indices) out[n] += 0.06f * sin(2 * PI * 220.0 * n / rate).toFloat()
        return out
    }

    private fun analyze(samples: FloatArray, complete: Boolean = true): TrackAnalysis {
        val analyzer = StreamingTrackAnalyzer()
        var offset = 0
        val chunk = FloatArray(4_096)
        while (offset < samples.size) {
            val count = minOf(chunk.size, samples.size - offset)
            System.arraycopy(samples, offset, chunk, 0, count)
            analyzer.feed(chunk, count)
            offset += count
        }
        return analyzer.finish(complete)!!
    }

    @Test
    fun `finds 128 bpm, an even grid and the downbeat`() {
        val analysis = analyze(synthTrack(128.0, 90))
        val bpm = analysis.bpm!!
        assertEquals(128.0, bpm.toDouble(), 1.0)
        assertTrue("confidence ${analysis.bpmConfidence}", analysis.isBeatReliable)
        val gaps = analysis.beatsMs.toList().zipWithNext { a, b -> (b - a).toDouble() }.sorted()
        assertEquals(60_000.0 / 128, gaps[gaps.size / 2], 6.0)
        // every bar starts on an accented kick: 250 ms + a multiple of four beats
        assertTrue(analysis.barStartsMs().size > 40)
        for (bar in analysis.barStartsMs()) {
            val beats = (bar - 250.0) / (60_000.0 / 128)
            val phase = ((beats % 4) + 4) % 4
            assertTrue("bar at $bar ms, phase $phase", phase < 0.2 || phase > 3.8)
        }
        // the fitted local grid lands on the kicks within a few ms
        val grid = analysis.localGrid(60_000L)!!
        val snapped = grid.snap(60_000L)
        val kicks = (snapped - 250.0) / (60_000.0 / 128)
        assertEquals(kicks, Math.round(kicks).toDouble(), 0.03)
    }

    @Test
    fun `finds 100 bpm rather than its double or half`() {
        val bpm = analyze(synthTrack(100.0, 80)).bpm!!
        assertEquals(100.0, bpm.toDouble(), 1.0)
    }

    @Test
    fun `fast tempos fold into the counted range`() {
        val bpm = analyze(synthTrack(174.0, 80)).bpm!!.toDouble()
        assertTrue("bpm $bpm", abs(bpm - 174.0) < 1.5 || abs(bpm - 87.0) < 1.0)
    }

    /** a played song and where its bars and sections really start */
    private class Band(val samples: FloatArray, val barsMs: List<Double>, val sectionBars: List<Int>)

    private val chords = arrayOf(
        doubleArrayOf(220.0, 261.6, 329.6), doubleArrayOf(174.6, 220.0, 261.6),
        doubleArrayOf(261.6, 329.6, 392.0), doubleArrayOf(196.0, 246.9, 293.7),
    )
    private val loudChords = arrayOf(
        doubleArrayOf(293.7, 349.2, 440.0), doubleArrayOf(233.1, 293.7, 349.2),
        doubleArrayOf(329.6, 415.3, 493.9), doubleArrayOf(261.6, 329.6, 392.0),
    )

    /** a song marked the way real ones are: every kick equally hard, chords changing on the one, maybe a bar cut to two beats and quiet and loud sections */
    private fun bandTrack(
        bpm: Double,
        seconds: Int,
        backbeat: Boolean,
        chordBars: Int = 1,
        shortBar: Int = -1,
        sectionEvery: Int = 0,
        sectionFrom: Int = 0,
    ): Band {
        val rate = StreamingTrackAnalyzer.SampleRate
        val out = FloatArray(rate * seconds)
        val beat = 60.0 / bpm
        val random = Random(3)
        fun add(atSeconds: Double, lengthSeconds: Double, sample: (Int) -> Float) {
            val start = (atSeconds * rate).toInt()
            for (i in 0 until (lengthSeconds * rate).toInt()) {
                val n = start + i
                if (n in out.indices) out[n] += sample(i)
            }
        }
        val bars = ArrayList<Double>()
        val sections = ArrayList<Int>()
        var t = 0.25
        var bar = 0
        while (t < seconds - 1) {
            bars += t * 1_000.0
            if (sectionEvery > 0 && bar >= sectionFrom && (bar - sectionFrom) % sectionEvery == 0) sections += bar
            // the intro counts as a quiet section, then loud and quiet take turns
            val section = if (sectionEvery == 0) 1 else if (bar < sectionFrom) 0 else 1 + (bar - sectionFrom) / sectionEvery
            val loud = section % 2 == 1
            val level = if (loud) 1f else 0.45f
            val beatsInBar = if (bar == shortBar) 2 else 4
            val chord = (if (loud && sectionEvery > 0) loudChords else chords)[(bar / chordBars) % 4]
            add(t, beatsInBar * beat) { i ->
                val swell = min(1f, i / (0.03f * rate)) * (0.7f + 0.3f * exp(-i / (0.4f * rate)))
                var tone = 0.0
                for (f in chord) tone += sin(2 * PI * f * i / rate) + 0.3 * sin(4 * PI * f * i / rate)
                0.035f * swell * tone.toFloat() * (if (loud) 1.3f else 1f)
            }
            for (p in 0 until beatsInBar) {
                val at = t + p * beat + random.nextDouble(-0.004, 0.004)
                if (!backbeat || p % 2 == 0) add(at, 0.12) { i ->
                    level * 0.8f * exp(-i / (0.03f * rate)) * sin(2 * PI * (55.0 + 60.0 * exp(-i / (0.01 * rate))) * i / rate).toFloat()
                }
                // a snare (or clap) on two and four
                if (p % 2 == 1) add(at, 0.12) { i ->
                    level * (0.45f * exp(-i / (0.035f * rate)) * (random.nextFloat() * 2f - 1f) +
                        0.2f * exp(-i / (0.02f * rate)) * sin(2 * PI * 185.0 * i / rate).toFloat())
                }
                add(at + beat / 2, 0.03) { i -> level * 0.12f * exp(-i / (0.006f * rate)) * (random.nextFloat() * 2f - 1f) }
            }
            t += beatsInBar * beat
            bar++
        }
        return Band(out, bars, sections)
    }

    /** every bar the analyser found between fromMs and toMs starts on a real one, and it found nearly all of them */
    private fun assertBarsOnTheOne(band: Band, analysis: TrackAnalysis, bpm: Double, fromMs: Long, toMs: Long) {
        val found = analysis.barStartsMs().filter { it in fromMs..toMs }
        val expected = ((toMs - fromMs) / (4 * 60_000.0 / bpm)).toInt() - 2
        assertTrue("found ${found.size} bars, expected $expected", found.size >= expected)
        for (bar in found) assertTrue("a bar starts at $bar ms, off the one", band.barsMs.any { abs(it - bar) < 60.0 })
    }

    @Test
    fun `the one comes from the chords and the backbeat, at the start and the end`() {
        // kick on one and three, snare on two and four: the old loudest-kick guess put the bar on beat three
        val backbeat = bandTrack(96.0, 180, backbeat = true, chordBars = 2)
        val backbeatAnalysis = analyze(backbeat.samples)
        assertBarsOnTheOne(backbeat, backbeatAnalysis, 96.0, 5_000L, 40_000L)
        assertBarsOnTheOne(backbeat, backbeatAnalysis, 96.0, 136_000L, 176_000L)
        // a kick on every beat with a clap on two and four: the old guess put it on beat four
        val floor = bandTrack(124.0, 180, backbeat = false, chordBars = 2)
        val floorAnalysis = analyze(floor.samples)
        assertBarsOnTheOne(floor, floorAnalysis, 124.0, 5_000L, 40_000L)
        assertBarsOnTheOne(floor, floorAnalysis, 124.0, 136_000L, 176_000L)
    }

    @Test
    fun `a bar cut to two beats moves the count along with it`() {
        val band = bandTrack(100.0, 180, backbeat = true, shortBar = 30)
        assertBarsOnTheOne(band, analyze(band.samples), 100.0, 136_000L, 176_000L)
    }

    @Test
    fun `phrases are counted from where the sections change, not from the first bar`() {
        // three bars of intro: counting eight bars from the first one would put every phrase three bars late
        val band = bandTrack(104.0, 200, backbeat = true, chordBars = 2, sectionEvery = 8, sectionFrom = 3)
        val analysis = analyze(band.samples)
        val bars = analysis.barStartsMs()
        for (section in band.sectionBars) {
            val index = bars.indexOfFirst { abs(it - band.barsMs[section]) < 60.0 }
            assertTrue("no bar found at section bar $section", index >= 0)
            assertTrue("section bar $section not found as one", index in analysis.sectionBars)
            assertEquals(8, analysis.phraseLevel(index))
            assertEquals(2, analysis.phraseLevel(index + 2))
            assertEquals(4, analysis.phraseLevel(index + 4))
        }
    }

    // -- planning ---------------------------------------------------------------------------

    private val outgoingTrack by lazy { analyze(synthTrack(128.0, 200)) }
    private val nearTempoTrack by lazy { analyze(synthTrack(126.0, 150), complete = false) }
    private val farTempoTrack by lazy { analyze(synthTrack(100.0, 150), complete = false) }

    @Test
    fun `a plan starts within the last 30 seconds and enters within two minutes`() {
        for (incoming in listOf(nearTempoTrack, farTempoTrack)) {
            for (seed in 0 until 6) {
                val plan = CrossmixPlanner.plan(
                    CrossmixTrackInput("out$seed", 200_000L, outgoingTrack, null),
                    CrossmixTrackInput("in$seed", 0L, incoming, null),
                )
                assertTrue("${plan.style} starts ${plan.outStartMs}", plan.outStartMs >= 200_000L - CrossmixPlanner.MaxLeadMs)
                assertTrue("${plan.style} hands over too late", plan.outMediaAt(plan.lengthBeats.toFloat()) <= 200_000.0)
                assertTrue("cue ${plan.inCueMs}", plan.inCueMs in 0..CrossmixPlanner.MaxEntryMs)
                // the slower song is sped up, by 10% at most, and nothing is ever slowed down
                assertTrue("speed in ${plan.inSpeed}", plan.inSpeed in 1f..1.1f)
                assertTrue("speed out ${plan.outSpeed}", plan.outSpeed in 1f..1.1f)
                assertFalse("both sped up", plan.inSpeed > 1f && plan.outSpeed > 1f)
                assertTrue("starts before the song", plan.inStartMs >= 0L)
            }
        }
    }

    @Test
    fun `tempos too far apart are never laid on top of each other`() {
        for (seed in 0 until 12) {
            val plan = CrossmixPlanner.plan(
                CrossmixTrackInput("o$seed", 200_000L, outgoingTrack, null),
                CrossmixTrackInput("i$seed", 0L, farTempoTrack, null),
            )
            assertFalse(plan.style.label, plan.style.needsBeatMatch)
            assertFalse(plan.beatMatched)
            assertEquals(1f, plan.inSpeed)
            assertEquals(1f, plan.outSpeed)
        }
    }

    @Test
    fun `close tempos are matched by the beats around the cue`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, nearTempoTrack, null),
        )
        if (plan.beatMatched) {
            // 126 laid on 128: the incoming plays 128/126 faster
            assertEquals(128.0 / 126.0, plan.inSpeed.toDouble(), 0.01)
        }
    }

    @Test
    fun `the same two songs always get the same mix`() {
        val plans = (0 until 4).map {
            CrossmixPlanner.plan(
                CrossmixTrackInput("out", 200_000L, outgoingTrack, null),
                CrossmixTrackInput("in", 0L, nearTempoTrack, null),
            )
        }
        assertTrue(plans.distinct().size == 1)
        // and the ids don't come into it: only the music does
        val renamed = CrossmixPlanner.plan(
            CrossmixTrackInput("other", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("names", 0L, nearTempoTrack, null),
        )
        assertEquals(plans.first(), renamed)
    }

    @Test
    fun `songs that belong together run into each other without a stunt`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, nearTempoTrack, null),
        )
        if (plan.beatMatched) {
            val stunts = setOf(
                CrossmixStyle.Backspin, CrossmixStyle.RewindWash, CrossmixStyle.TapeStop,
                CrossmixStyle.PowerDown, CrossmixStyle.DropCut, CrossmixStyle.ThunderDrop,
            )
            assertFalse(plan.style.label, plan.style in stunts)
            assertFalse(plan.style.label, plan.style.entersAtEnd)
        }
    }

    @Test
    fun `no analysis still gives a smooth fade inside the window`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("x", 240_000L, null, null),
            CrossmixTrackInput("y", 0L, null, null),
        )
        assertEquals(CrossmixStyle.Dampen, plan.style)
        assertTrue(plan.outStartMs >= 240_000L - CrossmixPlanner.MaxLeadMs)
        assertTrue(plan.outMediaAt(plan.lengthBeats.toFloat()) <= 240_000.0)
    }

    /** synced lyrics with a sung line every [everyMs], from [fromMs] to [toMs] */
    private fun lrc(fromMs: Long, toMs: Long, everyMs: Long = 3_000L): String = buildString {
        var t = fromMs
        while (t < toMs) {
            val minutes = t / 60_000
            val seconds = (t % 60_000) / 1_000
            val hundredths = (t % 1_000) / 10
            append("[%02d:%02d.%02d] la la la\n".format(minutes, seconds, hundredths))
            t += everyMs
        }
    }

    @Test
    fun `two songs that sing all the way through never have both voices out in front`() {
        // the outgoing song sings to its last second, the next one from its first
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("sing-out", 200_000L, outgoingTrack, lrc(0L, 200_000L)),
            CrossmixTrackInput("sing-in", 0L, nearTempoTrack, lrc(0L, 150_000L)),
        )
        val dealtWith = plan.yieldAmount > 0f || plan.style.entersAtEnd ||
            plan.style == CrossmixStyle.FarAway || plan.style == CrossmixStyle.Dampen
        assertTrue("${plan.style.label} lays one voice straight over the other", dealtWith)
    }

    @Test
    fun `the vocal yield leaves both songs untouched outside the transition`() {
        val p = FxParams()
        for (style in CrossmixStyle.entries) for (length in style.lengths) for (variation in 0 until 2) {
            val name = "${style.label} $length/$variation"
            style.evaluate(CrossmixRole.Outgoing, -2f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            style.yieldVocals(CrossmixRole.Outgoing, -2f, length.toFloat(), style.swapAt(length).toFloat(), 1f, p)
            assertEquals("$name out gain before", 1f, p.gain, 1e-4f)
            assertTrue("$name out space before", p.pan == 0f && p.width == 1f && p.reverbSend == 0f && p.highCutHz >= 19_000f)

            style.evaluate(CrossmixRole.Incoming, -0.25f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            style.yieldVocals(CrossmixRole.Incoming, -0.25f, length.toFloat(), style.swapAt(length).toFloat(), 1f, p)
            assertEquals("$name in before entry", 0f, p.gain, 0f)
            assertEquals("$name in room before entry", 0f, p.reverbSend, 0f)

            style.evaluate(CrossmixRole.Outgoing, length + style.tailBeats + 1f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            style.yieldVocals(CrossmixRole.Outgoing, length + style.tailBeats + 1f, length.toFloat(), style.swapAt(length).toFloat(), 1f, p)
            assertTrue("$name out gain after", p.gain < 1e-3f)
            assertEquals("$name out still feeding the room", 0f, p.reverbSend, 0f)

            style.evaluate(CrossmixRole.Incoming, length + 8f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            style.yieldVocals(CrossmixRole.Incoming, length + 8f, length.toFloat(), style.swapAt(length).toFloat(), 1f, p)
            assertEquals("$name in gain after", 1f, p.gain, 1e-3f)
            assertTrue("$name in space after", abs(p.pan) < 1e-3f && abs(p.width - 1f) < 1e-3f && p.highCutHz >= 19_000f)
        }
    }

    @Test
    fun `the next song's drop lands on the hand-over`() {
        // eight quiet bars, then the whole band comes in: that bar is what the mix should land on
        val outgoing = bandTrack(124.0, 200, backbeat = true, sectionEvery = 8)
        val incoming = bandTrack(126.0, 160, backbeat = true, sectionEvery = 8, sectionFrom = 8)
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("out", 200_000L, analyze(outgoing.samples), null),
            CrossmixTrackInput("in", 0L, analyze(incoming.samples, complete = false), null),
        )
        assertTrue("${plan.style.label} isn't a blend", !plan.style.entersAtEnd && plan.beatMatched)
        val drop = incoming.barsMs[8]
        assertEquals("lands at ${plan.inMediaAt(plan.swapBeat.toFloat())}", drop, plan.inMediaAt(plan.swapBeat.toFloat()), 80.0)
        // and it starts at least a bar before, so its intro runs under this one
        assertTrue(plan.inMediaAt(0f) <= drop - 4 * plan.inMediaPerBeat + 1.0)
    }

    @Test
    fun `the slower song is the one sped up, nothing is slowed down`() {
        val slow = analyze(synthTrack(120.0, 200))
        val fast = analyze(synthTrack(128.0, 200))
        // a faster next song: this one comes up to its tempo, the next plays as it is
        val intoFaster = CrossmixPlanner.plan(
            CrossmixTrackInput("slow", 200_000L, slow, null),
            CrossmixTrackInput("fast", 0L, fast, null),
        )
        assertTrue(intoFaster.beatMatched)
        assertEquals(128.0 / 120.0, intoFaster.outSpeed.toDouble(), 0.01)
        assertEquals(1f, intoFaster.inSpeed)
        // a slower next song is sped up itself
        val intoSlower = CrossmixPlanner.plan(
            CrossmixTrackInput("fast", 200_000L, fast, null),
            CrossmixTrackInput("slow", 0L, slow, null),
        )
        assertTrue(intoSlower.beatMatched)
        assertEquals(128.0 / 120.0, intoSlower.inSpeed.toDouble(), 0.01)
        assertEquals(1f, intoSlower.outSpeed)
    }

    @Test
    fun `everyday picks only, and blends stay short`() {
        val pairs = listOf(
            outgoingTrack to nearTempoTrack,
            outgoingTrack to farTempoTrack,
            analyze(synthTrack(100.0, 200)) to nearTempoTrack,
        )
        for ((out, inc) in pairs) {
            val plan = CrossmixPlanner.plan(CrossmixTrackInput("o", 200_000L, out, null), CrossmixTrackInput("i", 0L, inc, null))
            assertTrue("${plan.style.label} is a flavour style", plan.style.use != StyleUse.Flavour)
            if (!plan.style.entersAtEnd) {
                val seconds = plan.lengthBeats * plan.outBeatMs / 1_000.0
                assertTrue("${plan.style.label} blends for $seconds s", seconds <= if (plan.beatMatched) 16.0 else 8.5)
            }
        }
    }

    // -- styles: every transition must start and end clean ----------------------------------

    @Test
    fun `the bass belongs to one song at a time and changes hands on the swap`() {
        val out = FxParams()
        val inc = FxParams()
        for (style in CrossmixStyle.entries) {
            if (style.entersAtEnd) continue
            for (length in style.lengths) {
                val swap = style.swapAt(length).toFloat()
                var beat = 0f
                while (beat <= length) {
                    style.evaluate(CrossmixRole.Outgoing, beat, length.toFloat(), swap, 0, out)
                    style.evaluate(CrossmixRole.Incoming, beat, length.toFloat(), swap, 0, inc)
                    val name = "${style.label} $length at $beat"
                    assertEquals(name, 1f, out.bassCut + inc.bassCut, 1e-4f)
                    if (beat <= swap - 0.5f) assertEquals(name, 0f, out.bassCut, 0f)
                    if (beat >= swap) assertEquals(name, 1f, out.bassCut, 0f)
                    beat += 0.25f
                }
            }
        }
    }

    @Test
    fun `two songs playing together never add up past the loudness of one`() {
        val out = FxParams()
        val inc = FxParams()
        for (style in CrossmixStyle.entries) for (length in style.lengths) for (amount in floatArrayOf(0f, 1f)) {
            val swap = style.swapAt(length).toFloat()
            var beat = -1f
            while (beat <= length + 4f) {
                style.evaluate(CrossmixRole.Outgoing, beat, length.toFloat(), swap, 0, out)
                style.evaluate(CrossmixRole.Incoming, beat, length.toFloat(), swap, 0, inc)
                style.yieldVocals(CrossmixRole.Outgoing, beat, length.toFloat(), swap, amount, out)
                style.yieldVocals(CrossmixRole.Incoming, beat, length.toFloat(), swap, amount, inc)
                val trim = headroom(out.gain, inc.gain)
                val power = (out.gain * trim).let { it * it } + (inc.gain * trim).let { it * it }
                assertTrue("${style.label} $length at $beat: power $power", power <= 1.0001f)
                beat += 0.25f
            }
        }
    }

    @Test
    fun `every style leaves both songs untouched outside the transition`() {
        val p = FxParams()
        for (style in CrossmixStyle.entries) for (length in style.lengths) for (variation in 0 until 4) {
            val name = "${style.label} $length/$variation"
            style.evaluate(CrossmixRole.Outgoing, -2f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            assertEquals("$name out gain before", 1f, p.gain, 1e-4f)
            assertTrue("$name out filtered before", p.lowCutHz <= 21f && p.highCutHz >= 19_000f)
            assertTrue("$name out effects before", p.echoSend == 0f && p.reverbSend == 0f && p.noiseLevel == 0f)
            assertTrue("$name out shaping before", p.gateDepth == 0f && p.crush == 0f && p.flangerMix == 0f)
            assertTrue("$name out space before", p.pan == 0f && p.width == 1f && p.orbit == 0f)
            assertTrue("$name out bands before", p.lowBand == 1f && p.midBand == 1f && p.highBand == 1f && p.bassCut == 0f)
            assertEquals("$name out deck before", FxParams.Deck.Live, p.deck)

            style.evaluate(CrossmixRole.Incoming, -0.25f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            assertEquals("$name in before entry", 0f, p.gain, 0f)

            style.evaluate(CrossmixRole.Outgoing, length + style.tailBeats + 1f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            assertTrue("$name out gain after ${p.gain}", p.gain < 1e-3f)
            assertTrue("$name out still feeding effects", p.echoSend == 0f && p.reverbSend == 0f && p.noiseLevel < 1e-4f)

            style.evaluate(CrossmixRole.Incoming, length + 8f, length.toFloat(), style.swapAt(length).toFloat(), variation, p)
            assertEquals("$name in gain after", 1f, p.gain, 1e-3f)
            assertTrue("$name in filtered after (${p.lowCutHz}, ${p.highCutHz})", p.lowCutHz <= 21f && p.highCutHz >= 19_000f)
            assertTrue("$name in effects after", p.echoSend == 0f && p.reverbSend < 1e-3f && p.noiseLevel == 0f)
            assertTrue("$name in shaping after", p.gateDepth < 1e-3f && p.crush < 1e-3f && p.flangerMix < 1e-3f)
            assertTrue("$name in space after", abs(p.pan) < 1e-3f && abs(p.width - 1f) < 1e-3f)
            assertTrue("$name in bands after", abs(p.lowBand - 1f) < 1e-3f && abs(p.midBand - 1f) < 1e-3f && abs(p.highBand - 1f) < 1e-3f && p.bassCut < 1e-3f)
            assertEquals("$name in deck after", FxParams.Deck.Live, p.deck)
        }
    }

    // -- rendering: no clicks, no clipping, exact at both ends ------------------------------

    private class Rendered(val input: FloatArray, val output: FloatArray)

    private fun render(style: CrossmixStyle, role: CrossmixRole, length: Int, seconds: Double, hz: Double = 440.0): Rendered {
        val rate = 44_100
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val beatMs = 500.0
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = role,
                style = style,
                variation = 1,
                lengthBeats = length.toFloat(),
                swapBeat = style.swapAt(length).toFloat(),
                anchorMs = 2_000.0,
                mediaPerBeatMs = beatMs,
                musicalBeatMs = beatMs,
                endBeat = if (role == CrossmixRole.Outgoing) length + style.tailBeats + 2f else length + 8f,
                calibrationMs = 0.0,
                calibrationNanos = 0L,
            )
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
                val s = (v * 32_767).toInt().toShort()
                buffer.putShort(s); buffer.putShort(s)
            }
            buffer.flip()
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buffer)
            val out = processor.output
            for (i in 0 until count) {
                val l = out.getShort() / 32_768f
                out.getShort()
                output[frame + i] = l
            }
            frame += count
        }
        return Rendered(input, output)
    }

    @Test
    fun `every style renders without clicks or clipping and ends exactly`() {
        for (style in CrossmixStyle.entries) {
            val length = style.lengths.first()
            for (role in CrossmixRole.entries) {
                val endBeat = if (role == CrossmixRole.Outgoing) length + style.tailBeats + 3 else length + 10
                val seconds = 2.0 + endBeat * 0.5 + 1.0
                val r = render(style, role, length, seconds)
                val name = "${style.label} $role"
                var maxJump = 0f
                for (i in 1 until r.output.size) {
                    val v = r.output[i]
                    assertFalse("$name NaN at $i", v.isNaN())
                    assertTrue("$name clipped at $i", abs(v) <= 1f)
                    maxJump = maxOf(maxJump, abs(v - r.output[i - 1]))
                }
                // a 440 Hz sine at half scale moves at most ~0.03 per sample; pitch moves and noise
                // add some, but anything near the full swing is a click
                assertTrue("$name jump $maxJump", maxJump < 0.6f)

                val beforeEnd = (1.5 * 44_100).toInt()
                if (role == CrossmixRole.Outgoing) {
                    // untouched before the transition, silent once it's over
                    for (i in 0 until beforeEnd) assertEquals("$name before at $i", r.input[i], r.output[i], 2e-4f)
                    val after = ((2.0 + (length + style.tailBeats + 2.5) * 0.5) * 44_100).toInt()
                    for (i in after until r.output.size) assertEquals("$name after at $i", 0f, r.output[i], 2e-3f)
                } else {
                    // silent before it enters, the plain song once it has settled
                    for (i in 0 until beforeEnd) assertEquals("$name before at $i", 0f, r.output[i], 1e-3f)
                    val after = ((2.0 + (length + 9.5) * 0.5) * 44_100).toInt()
                    for (i in after until r.output.size) assertEquals("$name after at $i", r.input[i], r.output[i], 2e-4f)
                }
            }
        }
    }

    @Test
    fun `the swap really takes the bass away from one song and gives it to the other`() {
        // a 60 hz bass note through a 16 beat bass swap: beats of 500 ms from 2 s, so the swap (beat 8) is at 6 s
        fun level(r: Rendered, fromS: Double, toS: Double): Double {
            val from = (fromS * 44_100).toInt()
            val to = (toS * 44_100).toInt()
            return kotlin.math.sqrt((from until to).sumOf { (r.output[it] * r.output[it]).toDouble() } / (to - from))
        }
        val out = render(CrossmixStyle.BassSwap, CrossmixRole.Outgoing, 16, 9.0, hz = 60.0)
        val inc = render(CrossmixStyle.BassSwap, CrossmixRole.Incoming, 16, 9.0, hz = 60.0)
        val before = 4.5 to 5.5
        val after = 6.2 to 6.8
        // both are up around the swap, so each sits about 3 db down for headroom: a 0.5 sine is 0.35 rms on its own
        assertTrue("outgoing bass before the swap", level(out, before.first, before.second) > 0.2)
        assertTrue("outgoing bass after the swap", level(out, after.first, after.second) < 0.02)
        assertTrue("incoming bass before the swap", level(inc, before.first, before.second) < 0.02)
        assertTrue("incoming bass after the swap", level(inc, after.first, after.second) > 0.2)
    }

    // -- the listener's settings ---------------------------------------------------------------

    @Test
    fun `a pinned style is the one that plays, even a flavour the planner never picks by itself`() {
        for (style in listOf(CrossmixStyle.PhaserGlide, CrossmixStyle.FilterSweep, CrossmixStyle.EchoOut)) {
            val plan = CrossmixPlanner.plan(
                CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
                CrossmixTrackInput("b", 0L, nearTempoTrack, null),
                CrossmixSettings(style = style),
            )
            assertEquals(style, plan.style)
        }
    }

    @Test
    fun `a pinned style that needs a beat match the pair can't give falls back to the planner's pick`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, farTempoTrack, null),
            CrossmixSettings(style = CrossmixStyle.BassSwap),
        )
        assertFalse(plan.style.needsBeatMatch)
    }

    @Test
    fun `tempo match off never stretches, and a short length never overlaps long`() {
        val off = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, nearTempoTrack, null),
            CrossmixSettings(tempoMatch = com.mochyy.mochymusic.constants.CrossmixTempoMatch.OFF),
        )
        assertEquals(1f, off.inSpeed)
        assertEquals(1f, off.outSpeed)
        for (seed in 0 until 4) {
            val short = CrossmixPlanner.plan(
                CrossmixTrackInput("o$seed", 200_000L, outgoingTrack, null),
                CrossmixTrackInput("i$seed", 0L, nearTempoTrack, null),
                CrossmixSettings(length = com.mochyy.mochymusic.constants.CrossmixLength.SHORT),
            )
            if (!short.style.entersAtEnd) assertTrue("${short.lengthBeats} beats", short.lengthBeats * short.outBeatMs <= 16_000.0 * 0.6 + 1.0)
        }
    }

    @Test
    fun `the swap and the landing sit on the fitted beat grids, not on one detected beat`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, nearTempoTrack, null),
        )
        val swapMs = plan.outMediaAt(plan.swapBeat.toFloat()).toLong()
        val outGrid = outgoingTrack.localGrid(swapMs)!!
        assertEquals(outGrid.snap(swapMs).toDouble(), swapMs.toDouble(), 1.5)
        if (plan.beatMatched) {
            val inGrid = nearTempoTrack.localGrid(plan.inCueMs)!!
            assertEquals(inGrid.snap(plan.inCueMs).toDouble(), plan.inCueMs.toDouble(), 1.5)
        }
    }
}
