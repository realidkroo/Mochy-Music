package com.mochyy.mochymusic.crossmix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackAnalysis(
    val analyzedMs: Long,
    val complete: Boolean,
    val bpm: Float?,
    /** how sure the tempo is, 0..1. below ~0.35 nothing is beat-matched to it */
    val bpmConfidence: Float,
    val beatsMs: LongArray,
    /** the beats that start a bar, as indices into beatsMs */
    val barBeats: IntArray,
    /** the bars a new section starts on, as indices into barStartsMs */
    val sectionBars: IntArray,
    val key: String?,
    val keyConfidence: Float,
    val leadingSilenceEndMs: Long,
    val trailingSilenceStartMs: Long,
    val energy: FloatArray,
    val bass: FloatArray,
    val brightness: FloatArray,
    val vibe: Vibe,
    val vocal: FloatArray = FloatArray(0),
    /** 32 Hz (~31 ms bucket) high-resolution amplitude envelope (0..1) for crisp real waveform rendering in Mix It. */
    val waveform: FloatArray = FloatArray(0),
) {
    val beatMs: Float? get() = bpm?.let { 60_000f / it }

    val isBeatReliable: Boolean get() = bpm != null && bpmConfidence >= 0.35f && beatsMs.size >= 16

    /** Waveform amplitude (0..1) at [ms], linearly interpolated from [waveform] or falling back to per-second energy. */
    fun waveformAt(ms: Long): Float {
        if (ms < 0L) return 0f
        if (waveform.isNotEmpty()) {
            val pos = (ms / 1_000f) * WaveformRateHz
            val idx = pos.toInt()
            if (idx >= waveform.lastIndex) return waveform.last()
            val frac = pos - idx
            return waveform[idx] + (waveform[idx + 1] - waveform[idx]) * frac
        }
        if (energy.isNotEmpty()) {
            val sec = (ms / 1_000f).coerceIn(0f, energy.lastIndex.toFloat())
            val idx = sec.toInt()
            if (idx >= energy.lastIndex) return energy.last()
            val frac = sec - idx
            return energy[idx] + (energy[idx + 1] - energy[idx]) * frac
        }
        return 0f
    }

    /**
     * Returns `(peak, body)` over `[startMs, endMs]` so each drawn waveform bar catches every
     * kick/snare transient without aliasing and can render a brighter inner RMS core.
     */
    fun waveformSlice(startMs: Long, endMs: Long): Pair<Float, Float> {
        val out = FloatArray(2)
        waveformSliceInto(startMs, endMs, out)
        return out[0] to out[1]
    }

    /**
     * Zero-allocation variant of [waveformSlice] writing `(peak, body)` into `out[0]` and `out[1]`
     * for 120 Hz canvas drawing without per-bar boxing.
     */
    fun waveformSliceInto(startMs: Long, endMs: Long, out: FloatArray) {
        if (endMs <= 0L) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        if (waveform.isNotEmpty()) {
            val from = ((startMs.coerceAtLeast(0L) / 1_000f) * WaveformRateHz).toInt().coerceIn(0, waveform.lastIndex)
            val to = ((endMs.coerceAtLeast(startMs) / 1_000f) * WaveformRateHz).toInt().coerceIn(from, waveform.lastIndex)
            var peak = 0f
            var sum = 0f
            val count = to - from + 1
            for (i in from..to) {
                val v = waveform[i]
                if (v > peak) peak = v
                sum += v
            }
            out[0] = peak
            out[1] = sum / count
            return
        }
        val v = waveformAt((startMs + endMs) / 2L)
        out[0] = v
        out[1] = v * 0.7f
    }

    /** the beats that start a bar, in order */
    fun barStartsMs(): LongArray = LongArray(barBeats.size) { beatsMs[barBeats[it]] }

    /** how big a phrase bar index starts (8, 4, 2 or 1 bars), counted from the section starts either side of it */
    fun phraseLevel(index: Int): Int {
        var previous = 0
        var next = -1
        for (bar in sectionBars) {
            if (bar <= index) previous = bar else { next = bar; break }
        }
        val fromPrevious = phraseLevelOf(index - previous)
        return if (next < 0) fromPrevious else max(fromPrevious, phraseLevelOf(next - index))
    }

    private fun phraseLevelOf(bars: Int): Int = when {
        bars % 8 == 0 -> 8
        bars % 4 == 0 -> 4
        bars % 2 == 0 -> 2
        else -> 1
    }

    /** the 0..1 loudness around ms, averaged over windowMs */
    fun energyAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(energy, ms, windowMs)

    fun bassAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(bass, ms, windowMs)

    /** seconds of real music between two points: a fading tail or silence costs nothing when a mix leaves it unplayed */
    fun audibleSeconds(fromMs: Long, toMs: Long): Float {
        if (toMs <= fromMs || energy.isEmpty()) return 0f
        val from = (fromMs / 1_000L).toInt().coerceIn(0, energy.size)
        val to = (toMs / 1_000L).toInt().coerceIn(from, energy.size)
        var seconds = 0
        for (s in from until to) if (energy[s] >= AudibleEnergy) seconds++
        return seconds.toFloat()
    }

    /** beat grid near ms, least squares over nearby beats: far steadier than any one detected beat, so two tracks lock without flamming */
    fun localGrid(ms: Long, span: Int = 12): LocalGrid? {
        if (beatsMs.size < 4) return null
        val nearest = beatsMs.indices.minByOrNull { abs(beatsMs[it] - ms) } ?: return null
        val from = (nearest - span).coerceAtLeast(0)
        val to = (nearest + span).coerceAtMost(beatsMs.lastIndex)
        val count = to - from + 1
        if (count < 4) return null
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in from..to) {
            val x = (i - from).toDouble(); val y = beatsMs[i].toDouble()
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denominator = count * sxx - sx * sx
        if (denominator == 0.0) return null
        val period = (count * sxy - sx * sy) / denominator
        val intercept = (sy - period * sx) / count
        if (period <= 200.0 || period >= 1_500.0) return null
        return LocalGrid(firstBeatMs = intercept, periodMs = period, firstIndex = from)
    }

    private fun averageOver(values: FloatArray, ms: Long, windowMs: Long): Float {
        if (values.isEmpty()) return 0.5f
        val from = ((ms - windowMs / 2) / 1_000L).toInt().coerceIn(0, values.lastIndex)
        val to = ((ms + windowMs / 2) / 1_000L).toInt().coerceIn(from, values.lastIndex)
        var sum = 0f
        for (i in from..to) sum += values[i]
        return sum / (to - from + 1)
    }
}

// loudness above which a second counts as music: about 16 db under the song's loudest, on the 24 db scale of energy
private const val AudibleEnergy = 0.35f
const val WaveformRateHz = 32f

/** a stretch of steady beats: beat n of the stretch falls at firstBeatMs + n * periodMs */
data class LocalGrid(val firstBeatMs: Double, val periodMs: Double, val firstIndex: Int) {
    /** the beat time nearest ms on this grid */
    fun snap(ms: Long): Long {
        val n = ((ms - firstBeatMs) / periodMs).roundToInt()
        return (firstBeatMs + n * periodMs).roundToLong()
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}

data class Vibe(
    val energy: Float,
    val danceability: Float,
    val brightness: Float,
    val bassWeight: Float,
    val minor: Boolean,
)

class StreamingTrackAnalyzer {
    // the last FrameSize samples, written round and round; read oldest-first for each frame
    private val ring = FloatArray(FrameSize)
    private var writeIndex = 0
    private var sinceHop = 0
    private var totalSamples = 0L

    private val window = FloatArray(FrameSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FrameSize - 1))).toFloat() }
    private val real = FloatArray(FrameSize)
    private val imaginary = FloatArray(FrameSize)
    private val previousLog = FloatArray(Bins)
    private val bandRise = FloatArray(BandWeights.size)
    private val chroma = FloatArray(12)
    private val frameChroma = FloatArray(12)

    private val flux = GrowingFloats()
    private val rms = GrowingFloats()
    private val low = GrowingFloats()
    private val centroid = GrowingFloats()
    private val lead = GrowingFloats()
    private val leadShare = GrowingFloats()
    private val tonality = GrowingFloats()
    private val kick = GrowingFloats()
    private val snap = GrowingFloats()
    private val harmony = GrowingFloats()

    /** samples are mono at SampleRate */
    fun feed(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            ring[writeIndex] = samples[i]
            writeIndex = (writeIndex + 1) and (FrameSize - 1)
            totalSamples++
            if (totalSamples >= FrameSize && ++sinceHop >= HopSize) {
                sinceHop = 0
                analyzeFrame()
            }
        }
    }

    val analyzedMs: Long get() = totalSamples * 1_000L / SampleRate

    fun finish(complete: Boolean): TrackAnalysis? {
        val frames = flux.size
        if (frames < FramesPerSecondF * 8) return null

        val onset = onsetEnvelope(flux.toArray())
        val tempo = estimateTempo(onset)
        val beats = tempo?.let { trackBeats(onset, it.first) } ?: LongArray(0)
        val lowArray = low.toArray()
        val rmsArray = rms.toArray()
        val centroidArray = centroid.toArray()
        val bars = if (beats.size >= 8) {
            CrossmixBars.find(
                beatFrames = IntArray(beats.size) { msToFrame(beats[it]) },
                frames = CrossmixBars.Frames(onset, kick.toArray(), snap.toArray(), harmony.toArray(), rmsArray, lowArray, centroidArray),
            )
        } else {
            CrossmixBars.None
        }
        val key = estimateKey(chroma)
        val silence = silenceBounds(rmsArray)
        val perSecond = perSecondCurves(rmsArray, lowArray, centroidArray)
        val vocal = vocalCurve(lead.toArray(), leadShare.toArray(), tonality.toArray(), rmsArray)
        val waveform = waveformEnvelope(rmsArray)

        val vibe = Vibe(
            energy = overallEnergy(rmsArray, onset),
            danceability = ((tempo?.second ?: 0f) * 0.7f + pulseStrength(beats, onset) * 0.3f).coerceIn(0f, 1f),
            brightness = perSecond.third.averageOfLoud(perSecond.first),
            bassWeight = perSecond.second.averageOfLoud(perSecond.first),
            minor = key.first?.endsWith("minor") == true,
        )

        return TrackAnalysis(
            analyzedMs = analyzedMs,
            complete = complete,
            bpm = tempo?.first,
            bpmConfidence = tempo?.second ?: 0f,
            beatsMs = beats,
            barBeats = bars.bars,
            sectionBars = bars.sections,
            key = key.first,
            keyConfidence = key.second,
            leadingSilenceEndMs = silence.first,
            trailingSilenceStartMs = silence.second,
            energy = perSecond.first,
            bass = perSecond.second,
            brightness = perSecond.third,
            vibe = vibe,
            vocal = vocal,
            waveform = waveform,
        )
    }

    /** 32 Hz high-contrast peak envelope (0..1) preserving sharp drum transients, vocal phrases, and drops. */
    private fun waveformEnvelope(rms: FloatArray): FloatArray {
        if (rms.isEmpty()) return FloatArray(0)
        val buckets = ((rms.size / FramesPerSecondF) * WaveformRateHz).toInt().coerceAtLeast(1)
        val framesPerBucket = FramesPerSecondF / WaveformRateHz
        val raw = FloatArray(buckets)
        var maxVal = 0f
        for (b in 0 until buckets) {
            val from = (b * framesPerBucket).toInt().coerceIn(0, rms.lastIndex)
            val to = ((b + 1) * framesPerBucket).toInt().coerceIn(from + 1, rms.size)
            var peak = 0f
            var sumSq = 0.0
            for (f in from until to) {
                val v = rms[f]
                if (v > peak) peak = v
                sumSq += v * v
            }
            val avg = sqrt(sumSq / max(1, to - from)).toFloat()
            // Favor local peak (85%) so kick/snare transients stand out crisply like a studio DAW wave
            val blended = peak * 0.85f + avg * 0.15f
            raw[b] = blended
            if (blended > maxVal) maxVal = blended
        }
        if (maxVal <= 1e-6f) return FloatArray(buckets)
        val sorted = raw.sortedArray()
        val floor = sorted[(sorted.size * 0.05f).toInt().coerceAtMost(sorted.lastIndex)]
        val ceiling = sorted[(sorted.size * 0.985f).toInt().coerceAtMost(sorted.lastIndex)].coerceAtLeast(maxVal * 0.4f)
        val span = (ceiling - floor).coerceAtLeast(1e-6f)
        return FloatArray(buckets) { i ->
            val norm = ((raw[i] - floor) / span).coerceIn(0f, 1f)
            // Keep strong dynamic contrast (near-linear) instead of flattening with sqrt
            if (norm < 0.015f) 0f else Math.pow(norm.toDouble(), 0.85).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun vocalCurve(lead: FloatArray, share: FloatArray, tonal: FloatArray, rms: FloatArray): FloatArray {
        val seconds = (lead.size / FramesPerSecondF).toInt().coerceAtLeast(1)
        val leadDb = FloatArray(seconds)
        val shareMean = FloatArray(seconds)
        val tonalMean = FloatArray(seconds)
        val quiet = FloatArray(seconds)
        val peakRms = (rms.maxOrNull() ?: 0f).coerceAtLeast(1e-6f)
        for (s in 0 until seconds) {
            val from = (s * FramesPerSecondF).toInt().coerceAtMost(lead.size)
            val to = min(lead.size, ((s + 1) * FramesPerSecondF).toInt())
            var power = 0.0; var shareSum = 0f; var tonalSum = 0f; var level = 0f
            for (f in from until to) { power += lead[f]; shareSum += share[f]; tonalSum += tonal[f]; level = max(level, rms[f]) }
            val count = max(1, to - from)
            leadDb[s] = (10.0 * log10(power / count + 1e-12)).toFloat()
            shareMean[s] = shareSum / count
            tonalMean[s] = tonalSum / count
            quiet[s] = if (level < peakRms * 0.03f) 0f else 1f
        }
        val leadN = normalised(leadDb)
        val shareN = normalised(shareMean)
        val tonalN = normalised(tonalMean)
        val raw = FloatArray(seconds) { (0.45f * leadN[it] + 0.35f * shareN[it] + 0.2f * tonalN[it]) * quiet[it] }
        // a voice holds for phrases, not single seconds: smooth over three
        return FloatArray(seconds) { s ->
            val a = raw[max(0, s - 1)]; val b = raw[s]; val c = raw[min(seconds - 1, s + 1)]
            ((a + 2f * b + c) / 4f).coerceIn(0f, 1f)
        }
    }

    /** each value placed between this song's 20th and 95th percentile: 0 its low, 1 its high */
    private fun normalised(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val sorted = values.sortedArray()
        val low = sorted[(sorted.size * 0.2f).toInt().coerceAtMost(sorted.size - 1)]
        val high = sorted[(sorted.size * 0.95f).toInt().coerceAtMost(sorted.size - 1)]
        val span = (high - low).takeIf { it > 1e-6f } ?: return FloatArray(values.size)
        return FloatArray(values.size) { ((values[it] - low) / span).coerceIn(0f, 1f) }
    }

    private fun analyzeFrame() {
        var energy = 0.0
        for (i in 0 until FrameSize) {
            val v = ring[(writeIndex + i) and (FrameSize - 1)]
            energy += v * v
            real[i] = v * window[i]
            imaginary[i] = 0f
        }
        fft(real, imaginary)

        bandRise.fill(0f)
        frameChroma.fill(0f)
        var lowEnergy = 0f
        var totalEnergy = 0f
        var weighted = 0f
        var voiceEnergy = 0f
        var voiceLogSum = 0f
        var voiceBins = 0
        for (bin in 1 until Bins) {
            val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
            // log-compressed flux: a quiet hi-hat still registers next to a loud pad
            val compressed = ln(1f + 1_000f * magnitude)
            val rise = compressed - previousLog[bin]
            if (rise > 0f) bandRise[BandOf[bin]] += rise
            previousLog[bin] = compressed
            val power = magnitude * magnitude
            totalEnergy += power
            weighted += power * bin
            if (bin <= LowBins) lowEnergy += power
            if (bin in VoiceFromBin..VoiceToBin) {
                voiceEnergy += power
                voiceLogSum += ln(power + 1e-10f)
                voiceBins++
            }
            val pitchClass = PitchClassOf[bin]
            if (pitchClass >= 0) {
                chroma[pitchClass] += magnitude
                if (bin >= HarmonyFromBin) frameChroma[pitchClass] += magnitude
            }
        }

        var onset = 0f
        for (band in BandWeights.indices) onset += BandWeights[band] * bandRise[band] / BandBins[band]
        flux.add(onset)
        // the kick's attack, and the snare, clap and hat attack, kept apart for finding the bar
        kick.add(bandRise[0] / BandBins[0])
        snap.add(0.5f * (bandRise[2] / BandBins[2] + bandRise[3] / BandBins[3]))
        for (value in frameChroma) harmony.add(value)
        rms.add(sqrt(energy / FrameSize).toFloat())
        low.add(if (totalEnergy > 0f) lowEnergy / totalEnergy else 0f)
        centroid.add(if (totalEnergy > 0f) (weighted / totalEnergy) * SampleRate / FrameSize else 0f)
        lead.add(voiceEnergy)
        leadShare.add(if (totalEnergy > 0f) voiceEnergy / totalEnergy else 0f)
        // spectral flatness of the voice band: near 1 for noise (hats, breath), low for sung notes
        val flatness = if (voiceBins > 0 && voiceEnergy > 0f) {
            (exp(voiceLogSum / voiceBins) / (voiceEnergy / voiceBins)).coerceIn(0f, 1f)
        } else {
            1f
        }
        tonality.add(1f - flatness)
    }

    /** spectral flux with its local average taken away, so only the jumps remain */
    private fun onsetEnvelope(raw: FloatArray): FloatArray {
        val half = (FramesPerSecondF / 2).roundToInt()
        val prefix = DoubleArray(raw.size + 1)
        for (i in raw.indices) prefix[i + 1] = prefix[i] + raw[i]
        val out = FloatArray(raw.size)
        var peak = 0f
        for (i in raw.indices) {
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(raw.size)
            val mean = ((prefix[to] - prefix[from]) / (to - from)).toFloat()
            val value = max(0f, raw[i] - mean)
            out[i] = value
            if (value > peak) peak = value
        }
        if (peak > 0f) for (i in out.indices) out[i] /= peak
        return out
    }

    /** beat period from the onset autocorrelation, biased toward tempos people dance at (around 120), gives bpm and a 0..1 confidence */
    private fun estimateTempo(onset: FloatArray): Pair<Float, Float>? {
        val minLag = (60f / 200f * FramesPerSecondF).roundToInt()
        val maxLag = min((60f / 60f * FramesPerSecondF).roundToInt(), onset.size / 3)
        if (maxLag <= minLag + 2) return null
        val acf = FloatArray(maxLag + 2)
        val n = onset.size
        for (lag in minLag..maxLag + 1) {
            var sum = 0.0
            var i = 0
            while (i + lag < n) { sum += onset[i] * onset[i + lag]; i++ }
            acf[lag] = (sum / (n - lag)).toFloat()
        }
        var bestLag = -1
        var bestScore = 0f
        var total = 0f
        for (lag in minLag..maxLag) {
            val bpm = 60f * FramesPerSecondF / lag
            val prior = exp(-0.5f * (ln(bpm / 120f) / ln(2f) / 0.9f).let { it * it })
            val score = acf[lag] * prior
            total += acf[lag]
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        if (bestLag <= 0 || bestScore <= 0f) return null
        // sub-frame peak, so 128 and 129 bpm don't round to the same lag
        val y0 = acf[bestLag - 1]; val y1 = acf[bestLag]; val y2 = acf[bestLag + 1]
        val curvature = y0 - 2 * y1 + y2
        val shift = if (curvature < 0f) (0.5f * (y0 - y2) / curvature).coerceIn(-0.5f, 0.5f) else 0f
        var bpm = 60f * FramesPerSecondF / (bestLag + shift)
        while (bpm < 78f) bpm *= 2f
        while (bpm > 168f) bpm /= 2f
        val mean = total / (maxLag - minLag + 1)
        val confidence = if (mean > 0f) ((acf[bestLag] / mean - 1.2f) / 2.5f).coerceIn(0f, 1f) else 0f
        return bpm to confidence
    }

    /** ellis dynamic-programming beat tracker: the best chain of beats that lands on onsets with even spacing, then each is nudged to its onset peak */
    private fun trackBeats(onset: FloatArray, bpm: Float): LongArray {
        val period = 60f * FramesPerSecondF / bpm
        val n = onset.size
        val score = FloatArray(n)
        val back = IntArray(n) { -1 }
        val tightness = 400f
        val longest = (2f * period).roundToInt()
        val shortest = (period / 2f).roundToInt().coerceAtLeast(1)
        // the cost of a gap of d frames, worked out once instead of a log per comparison
        val penalty = FloatArray(longest + 1) { d ->
            if (d == 0) 0f else -tightness * ln(d / period).let { it * it }
        }
        for (t in onset.indices) {
            var best = 0f
            var bestFrom = -1
            val from = (t - longest).coerceAtLeast(0)
            val to = t - shortest
            var tau = from
            while (tau <= to) {
                val candidate = score[tau] + penalty[t - tau]
                if (bestFrom < 0 || candidate > best) { best = candidate; bestFrom = tau }
                tau++
            }
            score[t] = onset[t] + if (bestFrom >= 0) max(0f, best) else 0f
            back[t] = if (bestFrom >= 0 && best > 0f) bestFrom else -1
        }
        var t = (n - period.roundToInt()).coerceAtLeast(0)
        var end = t
        var endScore = -1f
        while (t < n) { if (score[t] > endScore) { endScore = score[t]; end = t }; t++ }
        val frames = ArrayList<Int>()
        var cursor = end
        while (cursor >= 0) { frames += cursor; cursor = back[cursor] }
        frames.reverse()
        return LongArray(frames.size) { index ->
            val f = frames[index]
            val y0 = onset.getOrElse(f - 1) { 0f }; val y1 = onset[f]; val y2 = onset.getOrElse(f + 1) { 0f }
            val curvature = y0 - 2 * y1 + y2
            val shift = if (curvature < 0f) (0.5f * (y0 - y2) / curvature).coerceIn(-0.5f, 0.5f) else 0f
            frameToMs(f + shift)
        }
    }

    private fun pulseStrength(beats: LongArray, onset: FloatArray): Float {
        if (beats.isEmpty()) return 0f
        var sum = 0f
        for (beat in beats) sum += onset[msToFrame(beat).coerceIn(0, onset.lastIndex)]
        val onBeat = sum / beats.size
        val overall = onset.average().toFloat().coerceAtLeast(1e-4f)
        return ((onBeat / overall - 1f) / 3f).coerceIn(0f, 1f)
    }

    private fun estimateKey(chroma: FloatArray): Pair<String?, Float> {
        if (chroma.sum() <= 0f) return null to 0f
        var bestLabel: String? = null
        var bestScore = -1f
        for (root in 0 until 12) {
            for ((suffix, template) in listOf(" major" to MajorProfile, " minor" to MinorProfile)) {
                val score = correlation(chroma, template, root)
                if (score > bestScore) { bestScore = score; bestLabel = NoteNames[root] + suffix }
            }
        }
        return bestLabel to bestScore.coerceIn(0f, 1f)
    }

    private fun correlation(chroma: FloatArray, template: FloatArray, rotation: Int): Float {
        val xMean = chroma.average().toFloat()
        val yMean = template.average().toFloat()
        var numerator = 0f; var xEnergy = 0f; var yEnergy = 0f
        for (i in 0 until 12) {
            val x = chroma[(i + rotation) % 12] - xMean
            val y = template[i] - yMean
            numerator += x * y; xEnergy += x * x; yEnergy += y * y
        }
        return if (xEnergy == 0f || yEnergy == 0f) 0f else numerator / sqrt(xEnergy * yEnergy)
    }

    /** where sound starts and stops: 45 db under the loudest frame */
    private fun silenceBounds(rms: FloatArray): Pair<Long, Long> {
        val threshold = (rms.maxOrNull() ?: 0f) * 0.0056f
        val first = rms.indexOfFirst { it > threshold }.coerceAtLeast(0)
        val last = rms.indexOfLast { it > threshold }.coerceAtLeast(first)
        return frameToMs(first.toFloat()) to min(frameToMs(last + 1f), analyzedMs)
    }

    /** per second: loudness relative to the track's peak (0..1), bass share, brightness */
    private fun perSecondCurves(rms: FloatArray, lowShare: FloatArray, centroidHz: FloatArray): Triple<FloatArray, FloatArray, FloatArray> {
        val seconds = (rms.size / FramesPerSecondF).toInt().coerceAtLeast(1)
        val db = FloatArray(seconds)
        val bass = FloatArray(seconds)
        val bright = FloatArray(seconds)
        for (s in 0 until seconds) {
            var energy = 0.0; var lowSum = 0f; var centroidSum = 0f
            val from = (s * FramesPerSecondF).toInt().coerceAtMost(rms.size)
            val to = min(rms.size, ((s + 1) * FramesPerSecondF).toInt())
            for (f in from until to) { energy += rms[f] * rms[f]; lowSum += lowShare[f]; centroidSum += centroidHz[f] }
            val count = max(1, to - from)
            db[s] = (10.0 * log10(energy / count + 1e-12)).toFloat()
            bass[s] = (lowSum / count * 2.5f).coerceIn(0f, 1f)
            bright[s] = ((centroidSum / count - 600f) / 3_400f).coerceIn(0f, 1f)
        }
        val peak = db.maxOrNull() ?: 0f
        val loudness = FloatArray(seconds) { ((db[it] - (peak - 24f)) / 24f).coerceIn(0f, 1f) }
        return Triple(loudness, bass, bright)
    }

    private fun overallEnergy(rms: FloatArray, onset: FloatArray): Float {
        val sorted = rms.sortedArray()
        val loud = sorted.copyOfRange((sorted.size * 0.4f).toInt(), sorted.size)
        val meanSquare = loud.fold(0.0) { acc, v -> acc + v * v } / max(1, loud.size)
        val db = 10.0 * log10(meanSquare + 1e-12)
        val loudness = ((db + 30.0) / 22.0).toFloat().coerceIn(0f, 1f)
        val density = (onset.count { it > 0.3f } / (onset.size / FramesPerSecondF) / 6f).coerceIn(0f, 1f)
        return (loudness * 0.65f + density * 0.35f).coerceIn(0f, 1f)
    }

    private fun FloatArray.averageOfLoud(loudness: FloatArray): Float {
        var sum = 0f; var count = 0
        for (i in indices) if (loudness.getOrElse(i) { 0f } > 0.5f) { sum += this[i]; count++ }
        return if (count == 0) (if (isEmpty()) 0.5f else average().toFloat()) else sum / count
    }

    // flux peaks when a hit is about 3/4 along the analysis window, not at its centre (measured on clean kicks)
    private val onsetLagSamples = FrameSize * 0.27f

    private fun frameToMs(frame: Float): Long = ((frame * HopSize + FrameSize / 2f + onsetLagSamples) * 1_000f / SampleRate).toLong()

    private fun msToFrame(ms: Long): Int = ((ms * SampleRate / 1_000f - FrameSize / 2f - onsetLagSamples) / HopSize).roundToInt()

    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until re.size) {
            var bit = re.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var length = 2
        while (length <= re.size) {
            val half = length / 2
            for (start in re.indices step length) {
                for (k in 0 until half) {
                    val twiddle = k * (FrameSize / length)
                    val wr = CosTable[twiddle]; val wi = -SinTable[twiddle]
                    val even = start + k; val odd = even + half
                    val oddReal = re[odd] * wr - im[odd] * wi
                    val oddImaginary = re[odd] * wi + im[odd] * wr
                    re[odd] = re[even] - oddReal; im[odd] = im[even] - oddImaginary
                    re[even] += oddReal; im[even] += oddImaginary
                }
            }
            length = length shl 1
        }
    }

    private class GrowingFloats {
        private var values = FloatArray(4_096)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): FloatArray = values.copyOf(size)
    }

    companion object {
        const val SampleRate = 22_050
        private const val FrameSize = 1_024
        private const val HopSize = 256
        private const val Bins = FrameSize / 2 + 1
        private const val FramesPerSecondF = 22_050f / 256f
        // bins up to ~150 hz: the kick and the bass line
        private val LowBins = (150f * FrameSize / SampleRate).roundToInt()
        /** where a singing voice's formants and consonants sit */
        private val VoiceFromBin = (1_000f * FrameSize / SampleRate).roundToInt()
        private val VoiceToBin = (4_000f * FrameSize / SampleRate).roundToInt()
        // the pitch class of each bin between 55 hz and 5 khz (-1 outside), worked out once instead of a log per bin per frame
        private val PitchClassOf = IntArray(Bins) { bin ->
            val frequency = bin * SampleRate.toFloat() / FrameSize
            if (bin == 0 || frequency !in 55f..5_000f) -1 else ((69 + 12 * (ln(frequency / 440f) / ln(2f))).roundToInt() % 12 + 12) % 12
        }
        // under ~300 hz the bins are wider than a semitone, so chord changes are only read from above it
        private val HarmonyFromBin = (300f * FrameSize / SampleRate).roundToInt()
        // onset bands: kick and bass, low mids, mids (snare body, vocals), highs (hats, air)
        private val BandEdgesHz = floatArrayOf(160f, 600f, 3_000f)
        private val BandWeights = floatArrayOf(1f, 0.8f, 0.55f, 0.2f)
        private val BandOf = IntArray(Bins) { bin ->
            val hz = bin * SampleRate.toFloat() / FrameSize
            BandEdgesHz.indexOfFirst { hz < it }.let { if (it < 0) BandEdgesHz.size else it }
        }
        private val BandBins = FloatArray(BandWeights.size) { band -> (1 until Bins).count { BandOf[it] == band }.coerceAtLeast(1).toFloat() }
        private val CosTable = FloatArray(FrameSize / 2) { cos(2.0 * PI * it / FrameSize).toFloat() }
        private val SinTable = FloatArray(FrameSize / 2) { sin(2.0 * PI * it / FrameSize).toFloat() }
        private val MajorProfile = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
        private val MinorProfile = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)
        private val NoteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
}

/** key names to pitch classes and the distances djs mix by */
object Harmony {
    private val Names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun pitchClass(key: String?): Int? = key?.substringBefore(' ')?.let(Names::indexOf)?.takeIf { it >= 0 }

    /** distance on the camelot wheel: 0 same key, 1 a neighbour or relative major/minor, more means they clash */
    fun camelotDistance(a: String?, b: String?): Int? {
        val ca = camelot(a) ?: return null
        val cb = camelot(b) ?: return null
        val ring = abs(ca.first - cb.first).let { min(it, 12 - it) }
        return if (ca.second == cb.second) ring else if (ring == 0) 1 else ring + 1
    }

    private fun camelot(key: String?): Pair<Int, Boolean>? {
        val pc = pitchClass(key) ?: return null
        val minor = key!!.endsWith("minor")
        // the relative major shares a number: A minor (8A) with C major (8B)
        val majorRoot = if (minor) (pc + 3) % 12 else pc
        val number = (majorRoot * 7) % 12
        return number to minor
    }
}
