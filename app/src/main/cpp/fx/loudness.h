// ebu r128 loudness as itu bs.1770-4 measures it: momentary, short-term, gated integrated, loudness range and true peak
#pragma once

#include <algorithm>
#include <cmath>
#include <cstring>

namespace fx {

class LoudnessMeter {
public:
    static constexpr int MaxChannels = 16;
    // "nothing measured yet", far under anything a song reaches
    static constexpr float None = -200.0f;

    struct Reading {
        float momentary = None;
        float shortTerm = None;
        float integrated = None;
        float range = None;
        float truePeakDb = None;
    };

    void configure(int rate, int channels) {
        rate_ = rate;
        channels_ = channels < 1 ? 1 : (channels > MaxChannels ? MaxChannels : channels);
        buildKWeighting();
        buildTruePeak();
        subBlock_ = rate / 10;
        for (int c = 0; c < MaxChannels; c++) {
            double w = 1.0;
            // android's order: fl fr fc lfe then surrounds; the lfe isn't heard as loudness, surrounds count a bit more
            if (channels_ >= 5) {
                if (c == 3) w = 0.0;
                else if (c >= 4) w = 1.41;
            }
            weight_[c] = w;
        }
        reset();
    }

    void reset() {
        std::memset(z_, 0, sizeof(z_));
        std::memset(subs_, 0, sizeof(subs_));
        std::memset(gateCount_, 0, sizeof(gateCount_));
        std::memset(gateEnergy_, 0, sizeof(gateEnergy_));
        std::memset(rangeCount_, 0, sizeof(rangeCount_));
        std::memset(tpHistory_, 0, sizeof(tpHistory_));
        subSum_ = 0.0;
        subFrames_ = 0;
        subFilled_ = 0;
        subHead_ = 0;
        std::memset(tpHead_, 0, sizeof(tpHead_));
        peak_ = 0.0f;
        reading_ = Reading{};
    }

    // [channels] holds one plane per channel, [frames] long
    void process(float* const* channels, int frames) {
        for (int f = 0; f < frames; f++) {
            double sum = 0.0;
            for (int c = 0; c < channels_; c++) {
                const float x = channels[c][f];
                truePeak(c, x);
                if (weight_[c] == 0.0) continue;
                const double y = kWeight(c, x);
                sum += weight_[c] * y * y;
            }
            subSum_ += sum;
            if (++subFrames_ >= subBlock_) closeSubBlock();
        }
        reading_.truePeakDb = peak_ > 1e-9f ? 20.0f * std::log10(peak_) : None;
    }

    const Reading& reading() const { return reading_; }

private:
    static constexpr int MomentarySubs = 4;   // 400 ms
    static constexpr int ShortSubs = 30;      // 3 s
    // gating blocks and short-term values are kept as a histogram of 0.1 lu steps, so a whole album fits in a fixed array
    static constexpr float HistLow = -70.0f;
    static constexpr float HistStep = 0.1f;
    static constexpr int HistBins = 800;
    static constexpr int TpTaps = 12;
    static constexpr int TpPhases = 4;

    static float lufsOf(double meanSquare) {
        if (!(meanSquare > 1e-20)) return None;
        return static_cast<float>(-0.691 + 10.0 * std::log10(meanSquare));
    }

    static int binOf(float lufs) {
        const int bin = static_cast<int>((lufs - HistLow) / HistStep);
        return bin < 0 ? -1 : (bin >= HistBins ? HistBins - 1 : bin);
    }

    // the two k-weighting stages, worked out for any rate from bs.1770's analog prototype
    void buildKWeighting() {
        const double fs = rate_;
        {
            const double f0 = 1681.974450955533, gain = 3.999843853973347, q = 0.7071752369554196;
            const double k = std::tan(M_PI * f0 / fs);
            const double vh = std::pow(10.0, gain / 20.0);
            const double vb = std::pow(vh, 0.4996667741545416);
            const double a0 = 1.0 + k / q + k * k;
            sb_[0] = (vh + vb * k / q + k * k) / a0;
            sb_[1] = 2.0 * (k * k - vh) / a0;
            sb_[2] = (vh - vb * k / q + k * k) / a0;
            sa_[0] = 2.0 * (k * k - 1.0) / a0;
            sa_[1] = (1.0 - k / q + k * k) / a0;
        }
        {
            const double f0 = 38.13547087602444, q = 0.5003270373238773;
            const double k = std::tan(M_PI * f0 / fs);
            const double a0 = 1.0 + k / q + k * k;
            ha_[0] = 2.0 * (k * k - 1.0) / a0;
            ha_[1] = (1.0 - k / q + k * k) / a0;
        }
    }

    double kWeight(int c, float x) {
        double* z = z_[c];
        const double y1 = sb_[0] * x + z[0];
        z[0] = sb_[1] * x - sa_[0] * y1 + z[1];
        z[1] = sb_[2] * x - sa_[1] * y1;
        const double y2 = y1 + z[2];
        z[2] = -2.0 * y1 - ha_[0] * y2 + z[3];
        z[3] = y1 - ha_[1] * y2;
        return y2;
    }

    // a windowed-sinc for 4x interpolation; peaks between samples are what clip a dac
    void buildTruePeak() {
        const int taps = TpTaps * TpPhases;
        for (int i = 0; i < taps; i++) {
            const double n = i - (taps - 1) / 2.0;
            const double x = n / TpPhases;
            const double sinc = std::fabs(x) < 1e-9 ? 1.0 : std::sin(M_PI * x) / (M_PI * x);
            const double window = 0.5 - 0.5 * std::cos(2.0 * M_PI * (i + 0.5) / taps);
            tpCoeff_[i % TpPhases][i / TpPhases] = static_cast<float>(sinc * window);
        }
    }

    void truePeak(int c, float x) {
        float* h = tpHistory_[c];
        int& head = tpHead_[c];
        h[head] = x;
        head = (head + 1) % TpTaps;
        for (int p = 0; p < TpPhases; p++) {
            float acc = 0.0f;
            for (int t = 0; t < TpTaps; t++) acc += tpCoeff_[p][t] * h[(head + TpTaps - 1 - t) % TpTaps];
            acc = std::fabs(acc);
            if (acc > peak_) peak_ = acc;
        }
    }

    void closeSubBlock() {
        // a nan from upstream would sit in the filters for good, and silence decays into slow denormals
        if (!std::isfinite(subSum_)) subSum_ = 0.0;
        for (int c = 0; c < channels_; c++) {
            for (double& v : z_[c]) {
                if (!std::isfinite(v) || std::fabs(v) < 1e-30) v = 0.0;
            }
        }
        subs_[subHead_] = subSum_ / subFrames_;
        subHead_ = (subHead_ + 1) % ShortSubs;
        if (subFilled_ < ShortSubs) subFilled_++;
        subSum_ = 0.0;
        subFrames_ = 0;

        if (subFilled_ >= MomentarySubs) {
            const double m = meanOfLast(MomentarySubs);
            reading_.momentary = lufsOf(m);
            const int bin = binOf(reading_.momentary);
            if (bin >= 0) {
                gateCount_[bin]++;
                gateEnergy_[bin] += m;
            }
            reading_.integrated = integrated();
        }
        if (subFilled_ >= ShortSubs) {
            const double s = meanOfLast(ShortSubs);
            reading_.shortTerm = lufsOf(s);
            const int bin = binOf(reading_.shortTerm);
            if (bin >= 0) rangeCount_[bin]++;
            reading_.range = range();
        }
    }

    double meanOfLast(int count) const {
        double sum = 0.0;
        for (int i = 1; i <= count; i++) sum += subs_[(subHead_ - i + ShortSubs) % ShortSubs];
        return sum / count;
    }

    // blocks above -70 lufs, then only those within 10 lu of their own average
    float integrated() const {
        double energy = 0.0;
        long count = 0;
        for (int b = 0; b < HistBins; b++) {
            energy += gateEnergy_[b];
            count += gateCount_[b];
        }
        if (count == 0) return None;
        const float relative = lufsOf(energy / count) - 10.0f;
        const int from = binOf(relative) < 0 ? 0 : binOf(relative);
        energy = 0.0;
        count = 0;
        for (int b = from; b < HistBins; b++) {
            energy += gateEnergy_[b];
            count += gateCount_[b];
        }
        return count == 0 ? None : lufsOf(energy / count);
    }

    // ebu tech 3342: short-term values within 20 lu of their average, from the 10th to the 95th percentile
    float range() const {
        double energy = 0.0;
        long count = 0;
        for (int b = 0; b < HistBins; b++) {
            if (rangeCount_[b] == 0) continue;
            const float lufs = HistLow + (b + 0.5f) * HistStep;
            energy += rangeCount_[b] * std::pow(10.0, (lufs + 0.691) / 10.0);
            count += rangeCount_[b];
        }
        if (count == 0) return None;
        const int from = std::max(0, binOf(lufsOf(energy / count) - 20.0f));
        long total = 0;
        for (int b = from; b < HistBins; b++) total += rangeCount_[b];
        if (total == 0) return None;
        const long lowRank = static_cast<long>(total * 0.10), highRank = static_cast<long>(total * 0.95);
        long seen = 0;
        float low = None, high = None;
        for (int b = from; b < HistBins; b++) {
            seen += rangeCount_[b];
            if (low == None && seen > lowRank) low = HistLow + b * HistStep;
            if (high == None && seen > highRank) {
                high = HistLow + b * HistStep;
                break;
            }
        }
        return (low == None || high == None) ? None : high - low;
    }

    int rate_ = 48000;
    int channels_ = 2;
    int subBlock_ = 4800;
    double sb_[3] = {}, sa_[2] = {}, ha_[2] = {};
    double z_[MaxChannels][4] = {};
    double weight_[MaxChannels] = {};
    double subs_[ShortSubs] = {};
    double subSum_ = 0.0;
    int subFrames_ = 0, subFilled_ = 0, subHead_ = 0;
    unsigned gateCount_[HistBins] = {};
    double gateEnergy_[HistBins] = {};
    unsigned rangeCount_[HistBins] = {};
    float tpCoeff_[TpPhases][TpTaps] = {};
    float tpHistory_[MaxChannels][TpTaps] = {};
    int tpHead_[MaxChannels] = {};
    float peak_ = 0.0f;
    Reading reading_;
};

}  // namespace fx
