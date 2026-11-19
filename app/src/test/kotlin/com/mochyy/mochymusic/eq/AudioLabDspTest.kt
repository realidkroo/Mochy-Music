package com.mochyy.mochymusic.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.mochyy.mochymusic.eq.audio.CustomEqualizerAudioProcessor
import com.mochyy.mochymusic.eq.audio.EightDAudioProcessor
import com.mochyy.mochymusic.eq.audio.EightDParams
import com.mochyy.mochymusic.eq.audio.LoudnessAudioProcessor
import com.mochyy.mochymusic.eq.audio.LoudnessParams
import com.mochyy.mochymusic.eq.audio.ReverbAudioProcessor
import com.mochyy.mochymusic.eq.audio.ReverbParams
import com.mochyy.mochymusic.eq.audio.SpatialAudioProcessor
import com.mochyy.mochymusic.eq.audio.SpatialMode
import com.mochyy.mochymusic.eq.audio.SpatialParams
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** Audio Lab's processors on synthetic signals: they must do what they say and never click. */
@Suppress("DEPRECATION")
class AudioLabDspTest {

    private val rate = 44_100

    private fun stereo(frames: Int, gen: (Int) -> Pair<Double, Double>): ShortArray =
        ShortArray(frames * 2).also { out ->
            for (f in 0 until frames) {
                val (l, r) = gen(f)
                out[f * 2] = (l * 32767).toInt().coerceIn(-32768, 32767).toShort()
                out[f * 2 + 1] = (r * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
        }

    private fun sine(hz: Double, amp: Double, frames: Int) = stereo(frames) { f ->
        val v = amp * sin(2 * PI * hz * f / rate)
        v to v
    }

    /** runs [input] through [p] in 1024-frame buffers, as the sink would */
    private fun run(p: AudioProcessor, input: ShortArray, channels: Int = 2, outChannels: Int = channels, between: (Int) -> Unit = {}): ShortArray {
        val out = ArrayList<Short>(input.size)
        val block = 1024 * channels
        var i = 0
        var n = 0
        while (i < input.size) {
            between(n++)
            val len = minOf(block, input.size - i)
            val buf = ByteBuffer.allocateDirect(len * 2).order(ByteOrder.nativeOrder())
            for (k in 0 until len) buf.putShort(input[i + k])
            buf.flip()
            p.queueInput(buf)
            val o = p.output
            while (o.hasRemaining()) out += o.short
            i += len
        }
        assertEquals(input.size / channels * outChannels, out.size)
        return out.toShortArray()
    }

    private fun configure(p: AudioProcessor, channels: Int = 2): AudioProcessor.AudioFormat {
        val format = p.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        p.flush()
        return format
    }

    private fun rmsDb(s: ShortArray, from: Int = s.size / 2, to: Int = s.size): Double {
        var sum = 0.0
        for (i in from until to) sum += (s[i] / 32768.0).let { it * it }
        return 10 * log10(sum / (to - from))
    }

    private fun maxStep(s: ShortArray, channel: Int = 0): Int {
        var m = 0
        for (i in (2 + channel) until s.size step 2) m = maxOf(m, abs(s[i] - s[i - 2]))
        return m
    }

    @Test
    fun `equalizer is a plain copy until it's given a curve, end included`() {
        val eq = CustomEqualizerAudioProcessor()
        configure(eq)
        val input = sine(440.0, 0.3, rate / 2)
        val out = run(eq, input)
        // the limiter holds the last few milliseconds until the song ends
        eq.queueEndOfStream()
        assertTrue(!eq.isEnded)
        eq.queueInput(ByteBuffer.allocateDirect(0))
        val tail = eq.output
        val all = out + ShortArray(tail.remaining() / 2) { tail.short }
        assertTrue(eq.isEnded)
        val late = all.size - input.size
        assertTrue("$late samples late", late in 2..(rate / 100 * 2))
        assertArrayEquals(input, all.copyOfRange(late, all.size))
    }

    @Test
    fun `a boost that would clip is caught by the limiter, louder and still round`() {
        val eq = CustomEqualizerAudioProcessor()
        configure(eq)
        eq.applyProfile(ParametricEQ(0.0, listOf(ParametricEQBand(60.0, 9.0, 1.0, FilterType.PK))))
        val input = sine(60.0, 0.9, rate * 2)
        val out = run(eq, input)
        assertTrue(rmsDb(out) > rmsDb(input))
        // clipped flat, most of each cycle would sit at full scale; limited, only the crests touch it
        val atFullScale = (out.size / 2 until out.size).count { abs(out[it].toInt()) >= 32767 }
        assertTrue("$atFullScale samples at full scale", atFullScale < out.size / 2 / 50)
    }

    @Test
    fun `a peak boosts its frequency by its gain and leaves others alone`() {
        val eq = CustomEqualizerAudioProcessor()
        configure(eq)
        eq.applyProfile(ParametricEQ(0.0, listOf(ParametricEQBand(1000.0, 6.0, 1.0, FilterType.PK))))
        val atPeak = sine(1000.0, 0.1, rate)
        val far = sine(60.0, 0.1, rate)
        assertEquals(6.0, rmsDb(run(eq, atPeak)) - rmsDb(atPeak), 0.3)
        assertEquals(0.0, rmsDb(run(eq, far)) - rmsDb(far), 0.3)
    }

    @Test
    fun `changing the curve mid-song doesn't click`() {
        val eq = CustomEqualizerAudioProcessor()
        configure(eq)
        val input = sine(200.0, 0.5, rate * 2)
        val plain = maxStep(input)
        var gain = 0.0
        val out = run(eq, input) { block ->
            // a drag: a new design every buffer, then switched off and on
            if (block % 2 == 0) {
                gain = if (gain >= 12) -12.0 else gain + 3
                eq.applyProfile(ParametricEQ(0.0, listOf(ParametricEQBand(200.0, gain, 1.4, FilterType.PK))))
            }
            if (block == 40) eq.disable()
            if (block == 50) eq.applyProfile(ParametricEQ(-3.0, listOf(ParametricEQBand(200.0, 9.0, 1.4, FilterType.PK))))
        }
        // a 200 Hz sine moves at most ~plain per sample; boosted up to 4x, a click would be far more
        assertTrue("step ${maxStep(out)} vs plain $plain", maxStep(out) < plain * 4.5)
    }

    @Test
    fun `reverb leaves a tail that dies away, and is off by default`() {
        val fx = AudioFxService()
        val reverb = ReverbAudioProcessor(fx)
        configure(reverb)
        val burst = stereo(rate * 2) { f ->
            val v = if (f < 2000) 0.5 * sin(2 * PI * 300.0 * f / rate) else 0.0
            v to v
        }
        assertArrayEquals(burst, run(reverb, burst))

        fx.reverb = ReverbParams(enabled = true, mix = 1f, roomSize = 0.8f, wet = 0.8f)
        reverb.flush()
        val out = run(reverb, burst)
        // interleaved stereo: sample index = seconds * rate * 2
        val tail = rmsDb(out, from = rate / 4 * 2, to = rate * 3 / 4 * 2)
        val late = rmsDb(out, from = rate * 7 / 4 * 2, to = rate * 2 * 2)
        assertTrue("tail $tail dB after the burst", tail > -60)
        assertTrue("dies away: late $late < tail $tail", late < tail)
    }

    @Test
    fun `8D panning bypasses unsupported formats and smooths live enable changes`() {
        val fx = AudioFxService()
        val processor = EightDAudioProcessor(fx)
        configure(processor)
        val input = sine(440.0, 0.4, rate)
        assertArrayEquals(input, run(processor, input))

        fx.eightD = EightDParams(enabled = true, rotationSpeedHz = 0.15f, depth = 0.85f)
        val output = run(processor, input)
        assertTrue("enabled panning changes the signal", !input.contentEquals(output))
        assertTrue("fade-in remains smooth", maxStep(output) < maxStep(input) * 3)

        val mono = EightDAudioProcessor(AudioFxService())
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, configure(mono, channels = 1))
        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            mono.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT)),
        )
    }

    @Test
    fun `loudness gets quiet music louder and loud music never past the ceiling`() {
        val fx = AudioFxService()
        val loud = LoudnessAudioProcessor(fx)
        fx.loudness = LoudnessParams(enabled = true, boostDb = 12f, compression = 0.5f)
        configure(loud)
        val hot = sine(500.0, 0.98, rate)
        val out = run(loud, hot)
        val peak = out.drop(out.size / 4).maxOf { abs(it.toInt()) } / 32768.0
        assertTrue("peak $peak", peak <= 0.97)

        loud.flush()
        val quiet = sine(500.0, 0.01, rate)
        assertTrue(rmsDb(run(loud, quiet)) > rmsDb(quiet) + 6)
    }

    @Test
    fun `crossfeed lets each ear hear the other side, softly`() {
        val fx = AudioFxService()
        val spatial = SpatialAudioProcessor(fx)
        fx.spatial = SpatialParams(enabled = true, mode = SpatialMode.Crossfeed)
        configure(spatial)
        val leftOnly = stereo(rate) { f -> 0.3 * sin(2 * PI * 200.0 * f / rate) to 0.0 }
        val out = run(spatial, leftOnly)
        val right = ShortArray(out.size / 2) { out[it * 2 + 1] }
        val left = ShortArray(out.size / 2) { out[it * 2] }
        val bleed = rmsDb(left) - rmsDb(right)
        assertTrue("the right ear hears it ($bleed dB down)", bleed in 2.0..15.0)
    }

    @Test
    fun `virtual speakers keep the level sane`() {
        val fx = AudioFxService()
        val spatial = SpatialAudioProcessor(fx)
        fx.spatial = SpatialParams(enabled = true, mode = SpatialMode.Speakers, speakerAngle = 30f)
        configure(spatial)
        val input = sine(1000.0, 0.3, rate)
        val diff = rmsDb(run(spatial, input)) - rmsDb(input)
        assertTrue("level change $diff dB", abs(diff) < 6)
    }

    @Test
    fun `surround is rendered to two ears only when asked`() {
        val fx = AudioFxService()
        val off = SpatialAudioProcessor(fx)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, configure(off, channels = 6))

        fx.spatial = SpatialParams(surround = true)
        val on = SpatialAudioProcessor(fx)
        val format = configure(on, channels = 6)
        assertEquals(2, format.channelCount)
        val six = ShortArray(rate * 6) { i -> if (i % 6 == 2) (8000 * sin(2 * PI * 440.0 * (i / 6) / rate)).toInt().toShort() else 0 }
        val out = run(on, six, channels = 6, outChannels = 2)
        // the centre channel arrives in both ears, equally
        val left = ShortArray(out.size / 2) { out[it * 2] }
        val right = ShortArray(out.size / 2) { out[it * 2 + 1] }
        assertEquals(rmsDb(left), rmsDb(right), 0.5)
        assertTrue(rmsDb(left) > -40)
    }

    @Test
    fun `ReplayGain comes from the file's Vorbis comments, track or album, never past the peak`() {
        // as Monochrome's FLAC of Eastside carries them, plus album tags to tell apart
        val tags = com.mochyy.mochymusic.eq.audio.ReplayGainTags()
        tags.update(
            listOf(
                "REPLAYGAIN_TRACK_GAIN" to "-8.89 dB",
                "REPLAYGAIN_TRACK_PEAK" to "1.0",
                "REPLAYGAIN_ALBUM_GAIN" to "-6.00 dB",
                "replaygain_album_peak" to "0.5",
            )
        )
        assertTrue(tags.present)
        val track = com.mochyy.mochymusic.eq.audio.ReplayGainParams(com.mochyy.mochymusic.eq.audio.ReplayGainMode.Track, preampDb = 3f)
        // -8.89 + 3 = -5.89 dB
        assertEquals(0.5075f, tags.linear(track, normalize = true), 0.002f)
        // album: -6 + 3 = -3 dB is 0.708, but its peak of 0.5 allows at most 2x, so -3 dB stands
        val album = track.copy(mode = com.mochyy.mochymusic.eq.audio.ReplayGainMode.Album)
        assertEquals(0.708f, tags.linear(album, normalize = true), 0.002f)
        // a big preamp can't push past full scale on a 1.0 peak
        assertEquals(1f, tags.linear(track.copy(preampDb = 20f), normalize = true), 0.0001f)
        // "Even out songs" off: nothing
        assertEquals(1f, tags.linear(track, normalize = false), 0.0001f)
        // a song without tags clears the last one's
        tags.update(emptyList<Pair<String, String>>())
        assertEquals(1f, tags.linear(track, normalize = true), 0.0001f)
    }

    @Test
    fun `mono gives both ears the whole mix`() {
        val fx = AudioFxService()
        fx.spatial = SpatialParams(mono = true)
        val spatial = SpatialAudioProcessor(fx)
        configure(spatial)
        val leftOnly = stereo(rate / 2) { f -> 0.4 * sin(2 * PI * 300.0 * f / rate) to 0.0 }
        val out = run(spatial, leftOnly)
        for (i in out.size / 2 until out.size step 2) assertEquals(out[i].toInt(), out[i + 1].toInt())
        assertEquals(rmsDb(leftOnly) - 6.02 + 3.01, rmsDb(out), 0.5)
    }
}
