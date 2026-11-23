// effects that shape level, stereo and tone: utility, stereo, parametric eq, filter
#pragma once

#include "../dsp.h"
#include "../plugin.h"
#include "info.h"

namespace fx {

class UtilityPlugin : public Plugin {
public:
    enum { Gain, Pan, Width, InvertLeft, InvertRight, Swap, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.utility", "Utility", "Utility", "Gain, pan, width, polarity and channel swap", {}, {}, {}};
        i.params = {
            param("gain", "Gain", "dB", -36, 24, 0),
            param("pan", "Pan", "", -100, 100, 0),
            param("width", "Width", "%", 0, 200, 100),
            toggle("invertL", "Flip left", false),
            toggle("invertR", "Flip right", false),
            toggle("swap", "Swap sides", false),
        };
        return i;
    }

    void prepare(double rate, int) override {
        gainL_.prepare(rate, 20);
        gainR_.prepare(rate, 20);
        width_.prepare(rate, 20);
    }
    void reset() override {
        gainL_.snap(gainL_.target);
        gainR_.snap(gainR_.target);
        width_.snap(width_.target);
    }
    void update(const float* v) override {
        const float gain = dbToGain(v[Gain]);
        const float pan = v[Pan] / 100.0f;
        // constant power, so a centred pan sits where it was before panning
        const float angle = (pan + 1.0f) * Pi * 0.25f;
        gainL_.set(gain * std::cos(angle) * 1.41421356f * (v[InvertLeft] > 0.5f ? -1.0f : 1.0f));
        gainR_.set(gain * std::sin(angle) * 1.41421356f * (v[InvertRight] > 0.5f ? -1.0f : 1.0f));
        width_.set(v[Width] / 100.0f);
        swap_ = v[Swap] > 0.5f;
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            float a = swap_ ? r[i] : l[i];
            float b = swap_ ? l[i] : r[i];
            const float w = width_.next();
            const float mid = (a + b) * 0.5f, side = (a - b) * 0.5f * w;
            l[i] = (mid + side) * gainL_.next();
            r[i] = (mid - side) * gainR_.next();
        }
    }

private:
    Smoothed gainL_, gainR_, width_;
    bool swap_ = false;
};

class StereoPlugin : public Plugin {
public:
    enum { Width, MidDb, SideDb, BassMonoHz, HaasMs, Balance, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.stereo", "Stereo", "Utility", "Width, mid and side levels, mono bass and a haas widener", {}, {}, {}};
        i.params = {
            param("width", "Width", "%", 0, 200, 100),
            param("mid", "Mid", "dB", -18, 12, 0),
            param("side", "Side", "dB", -18, 12, 0),
            logParam("bassMono", "Mono bass", "Hz", 20, 400, 20),
            param("haas", "Haas", "ms", 0, 30, 0),
            param("balance", "Balance", "", -100, 100, 0),
        };
        i.stereoOnly = true;
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        haas_.prepare(static_cast<int>(0.032 * rate) + 4);
        mid_.prepare(rate, 20);
        side_.prepare(rate, 20);
    }
    void reset() override {
        haas_.reset();
        bassL_.reset();
        bassR_.reset();
    }
    void update(const float* v) override {
        const float width = v[Width] / 100.0f;
        mid_.set(dbToGain(v[MidDb]));
        side_.set(dbToGain(v[SideDb]) * width);
        // 20 hz is the knob's off position
        monoBass_ = v[BassMonoHz] > 21.0f;
        bassL_.setCutoff(rate_, v[BassMonoHz]);
        bassR_.setCutoff(rate_, v[BassMonoHz]);
        haasSamples_ = static_cast<int>(v[HaasMs] * 0.001f * static_cast<float>(rate_));
        const float bal = v[Balance] / 100.0f;
        balL_ = bal > 0 ? 1.0f - bal : 1.0f;
        balR_ = bal < 0 ? 1.0f + bal : 1.0f;
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            float a = l[i], b = r[i];
            if (monoBass_) {
                // the lows of each side go to the middle and the highs keep their place
                const float lowA = bassL_.lowpass(a), lowB = bassR_.lowpass(b);
                const float lowMid = (lowA + lowB) * 0.5f;
                a = a - lowA + lowMid;
                b = b - lowB + lowMid;
            }
            const float mid = (a + b) * 0.5f * mid_.next();
            const float side = (a - b) * 0.5f * side_.next();
            a = mid + side;
            b = mid - side;
            haas_.write(b);
            if (haasSamples_ > 0) b = haas_.read(haasSamples_);
            l[i] = a * balL_;
            r[i] = b * balR_;
        }
    }

private:
    double rate_ = 48000;
    DelayLine haas_;
    OnePole bassL_, bassR_;
    Smoothed mid_, side_;
    bool monoBass_ = false;
    int haasSamples_ = 0;
    float balL_ = 1, balR_ = 1;
};

class EqPlugin : public Plugin {
public:
    enum {
        LowCut, LowShelfHz, LowShelfDb, P1Hz, P1Db, P1Q, P2Hz, P2Db, P2Q, P3Hz, P3Db, P3Q,
        HighShelfHz, HighShelfDb, HighCut, Output, Count
    };
    static constexpr int Bands = 7;

    static PluginInfo info() {
        PluginInfo i{"mochy.eq", "Parametric EQ", "EQ & filter", "Low and high cut, two shelves and three bells", {}, {}, {}};
        i.params = {
            logParam("lowCut", "Low cut", "Hz", 10, 1000, 10),
            logParam("lowShelfHz", "Low shelf", "Hz", 30, 500, 100),
            param("lowShelfDb", "Low gain", "dB", -18, 18, 0),
            logParam("p1Hz", "Bell 1", "Hz", 40, 2000, 250),
            param("p1Db", "Bell 1 gain", "dB", -18, 18, 0),
            logParam("p1Q", "Bell 1 Q", "", 0.2f, 10, 1),
            logParam("p2Hz", "Bell 2", "Hz", 150, 8000, 1000),
            param("p2Db", "Bell 2 gain", "dB", -18, 18, 0),
            logParam("p2Q", "Bell 2 Q", "", 0.2f, 10, 1),
            logParam("p3Hz", "Bell 3", "Hz", 600, 18000, 4000),
            param("p3Db", "Bell 3 gain", "dB", -18, 18, 0),
            logParam("p3Q", "Bell 3 Q", "", 0.2f, 10, 1),
            logParam("highShelfHz", "High shelf", "Hz", 1500, 18000, 8000),
            param("highShelfDb", "High gain", "dB", -18, 18, 0),
            logParam("highCut", "High cut", "Hz", 1000, 22000, 22000),
            param("output", "Output", "dB", -18, 18, 0),
        };
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        out_.prepare(rate, 20);
    }
    void reset() override {
        for (auto& b : left_) b.reset();
        for (auto& b : right_) b.reset();
    }
    void update(const float* v) override {
        // 10 hz and 22 khz are the cuts' off positions; a flat band costs nothing
        active_[0] = v[LowCut] > 10.5f;
        active_[1] = std::fabs(v[LowShelfDb]) > 0.01f;
        active_[2] = std::fabs(v[P1Db]) > 0.01f;
        active_[3] = std::fabs(v[P2Db]) > 0.01f;
        active_[4] = std::fabs(v[P3Db]) > 0.01f;
        active_[5] = std::fabs(v[HighShelfDb]) > 0.01f;
        active_[6] = v[HighCut] < 21900.0f;
        configure(0, FilterShape::HighPass, v[LowCut], 0.7071f, 0);
        configure(1, FilterShape::LowShelf, v[LowShelfHz], 0.7071f, v[LowShelfDb]);
        configure(2, FilterShape::Peak, v[P1Hz], v[P1Q], v[P1Db]);
        configure(3, FilterShape::Peak, v[P2Hz], v[P2Q], v[P2Db]);
        configure(4, FilterShape::Peak, v[P3Hz], v[P3Q], v[P3Db]);
        configure(5, FilterShape::HighShelf, v[HighShelfHz], 0.7071f, v[HighShelfDb]);
        configure(6, FilterShape::LowPass, v[HighCut], 0.7071f, 0);
        out_.set(dbToGain(v[Output]));
    }
    void process(float* l, float* r, int n) override {
        for (int b = 0; b < Bands; b++) {
            if (!active_[b]) continue;
            Biquad& fl = left_[b];
            Biquad& fr = right_[b];
            for (int i = 0; i < n; i++) {
                l[i] = fl.process(l[i]);
                r[i] = fr.process(r[i]);
            }
        }
        for (int i = 0; i < n; i++) {
            const float g = out_.next();
            l[i] *= g;
            r[i] *= g;
        }
    }

private:
    void configure(int band, FilterShape shape, float hz, float q, float db) {
        // only the coefficients change, so a moving knob keeps the filter's memory and never clicks
        const float z1l = left_[band].z1, z2l = left_[band].z2, z1r = right_[band].z1, z2r = right_[band].z2;
        left_[band].set(shape, rate_, hz, q, db);
        right_[band] = left_[band];
        left_[band].z1 = z1l;
        left_[band].z2 = z2l;
        right_[band].z1 = z1r;
        right_[band].z2 = z2r;
    }

    double rate_ = 48000;
    Biquad left_[Bands], right_[Bands];
    bool active_[Bands] = {};
    Smoothed out_;
};

// a state-variable filter in zavalishin's trapezoidal form, which stays stable while its cutoff sweeps
class FilterPlugin : public Plugin {
public:
    enum { Type, Cutoff, Resonance, Slope, Drive, Mix, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.filter", "Filter", "EQ & filter", "A resonant low, high, band or notch filter that sweeps smoothly", {}, {}, {}};
        i.params = {
            choice("type", "Type", "Low pass|High pass|Band pass|Notch", 0),
            logParam("cutoff", "Cutoff", "Hz", 20, 20000, 2000),
            param("resonance", "Resonance", "%", 0, 100, 20),
            choice("slope", "Slope", "12 dB|24 dB", 0),
            param("drive", "Drive", "dB", 0, 24, 0),
            param("mix", "Mix", "%", 0, 100, 100),
        };
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        cutoff_.prepare(rate, 30);
        mix_.prepare(rate, 20);
    }
    void reset() override {
        for (auto& s : state_) s = {};
    }
    void update(const float* v) override {
        type_ = static_cast<int>(v[Type]);
        cutoff_.set(v[Cutoff]);
        if (cutoff_.value <= 0) cutoff_.snap(v[Cutoff]);
        k_ = 2.0f - 1.95f * (v[Resonance] / 100.0f);
        stages_ = v[Slope] > 0.5f ? 2 : 1;
        drive_ = dbToGain(v[Drive]);
        mix_.set(v[Mix] / 100.0f);
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            const float hz = clampf(cutoff_.next(), 20.0f, static_cast<float>(rate_) * 0.45f);
            const float g = std::tan(Pi * hz / static_cast<float>(rate_));
            const float a1 = 1.0f / (1.0f + g * (g + k_));
            const float mix = mix_.next();
            float x[2] = {l[i], r[i]};
            for (int c = 0; c < 2; c++) {
                float y = drive_ > 1.001f ? std::tanh(x[c] * drive_) / std::tanh(drive_) : x[c];
                for (int s = 0; s < stages_; s++) y = tick(state_[c * 2 + s], y, g, a1);
                x[c] = x[c] * (1.0f - mix) + y * mix;
            }
            l[i] = x[0];
            r[i] = x[1];
        }
    }

private:
    struct Svf {
        float ic1 = 0, ic2 = 0;
    };

    float tick(Svf& s, float x, float g, float a1) {
        const float v1 = a1 * (s.ic1 + g * (x - s.ic2));
        const float v2 = s.ic2 + g * v1;
        s.ic1 = undenormal(2.0f * v1 - s.ic1);
        s.ic2 = undenormal(2.0f * v2 - s.ic2);
        const float low = v2, band = v1, high = x - k_ * v1 - v2;
        switch (type_) {
            case 1: return high;
            case 2: return band * k_;
            case 3: return low + high;
            default: return low;
        }
    }

    double rate_ = 48000;
    Svf state_[4];
    Smoothed cutoff_, mix_;
    float k_ = 1.4f, drive_ = 1.0f;
    int type_ = 0, stages_ = 1;
};

}  // namespace fx
