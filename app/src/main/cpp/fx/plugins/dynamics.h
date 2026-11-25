// effects that follow the level: compressor, limiter, gate, transient shaper
#pragma once

#include "../dsp.h"
#include "../plugin.h"
#include "info.h"

#include <vector>

namespace fx {

inline float envCoeff(double rate, float ms) { return 1.0f - std::exp(-1.0f / (static_cast<float>(rate) * std::max(ms, 0.01f) * 0.001f)); }

class CompressorPlugin : public Plugin {
public:
    enum { Threshold, Ratio, Knee, Attack, Release, Makeup, AutoMakeup, Detector, Link, SidechainHz, Mix, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.compressor", "Compressor", "Dynamics", "Evens out the level, with a soft knee and a parallel mix", {}, {}, {}};
        i.params = {
            param("threshold", "Threshold", "dB", -60, 0, -18),
            logParam("ratio", "Ratio", ":1", 1, 20, 3),
            param("knee", "Knee", "dB", 0, 24, 6),
            logParam("attack", "Attack", "ms", 0.1f, 100, 10),
            logParam("release", "Release", "ms", 10, 2000, 120),
            param("makeup", "Makeup", "dB", 0, 24, 0),
            toggle("autoMakeup", "Auto makeup", false),
            choice("detector", "Detector", "Peak|RMS", 1),
            param("link", "Stereo link", "%", 0, 100, 100),
            logParam("sidechain", "Sidechain cut", "Hz", 20, 300, 20),
            param("mix", "Mix", "%", 0, 100, 100),
        };
        i.meters = {MeterInfo{"gr", "Reduction", "dB", -24, 0}};
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        rms_ = envCoeff(rate, 10);
        makeup_.prepare(rate, 20);
        mix_.prepare(rate, 20);
        reset();
    }
    void reset() override {
        grL_ = grR_ = 0.0f;
        msL_ = msR_ = 0.0f;
        scL_.reset();
        scR_.reset();
    }
    void update(const float* v) override {
        threshold_ = v[Threshold];
        slope_ = 1.0f - 1.0f / std::max(1.0f, v[Ratio]);
        knee_ = v[Knee];
        attack_ = envCoeff(rate_, v[Attack]);
        release_ = envCoeff(rate_, v[Release]);
        const float autoGain = v[AutoMakeup] > 0.5f ? -threshold_ * slope_ * 0.5f : 0.0f;
        makeup_.set(dbToGain(v[Makeup] + autoGain));
        rmsMode_ = v[Detector] > 0.5f;
        link_ = v[Link] / 100.0f;
        sidechain_ = v[SidechainHz] > 21.0f;
        scL_.setCutoff(rate_, v[SidechainHz]);
        scR_.setCutoff(rate_, v[SidechainHz]);
        mix_.set(v[Mix] / 100.0f);
    }
    void process(float* l, float* r, int n) override {
        float deepest = 0.0f;
        for (int i = 0; i < n; i++) {
            float dl = sidechain_ ? scL_.highpass(l[i]) : l[i];
            float dr = sidechain_ ? scR_.highpass(r[i]) : r[i];
            float levelL, levelR;
            if (rmsMode_) {
                msL_ += (dl * dl - msL_) * rms_;
                msR_ += (dr * dr - msR_) * rms_;
                levelL = std::sqrt(msL_);
                levelR = std::sqrt(msR_);
            } else {
                levelL = std::fabs(dl);
                levelR = std::fabs(dr);
            }
            const float linked = std::max(levelL, levelR);
            levelL = linked * link_ + levelL * (1.0f - link_);
            levelR = linked * link_ + levelR * (1.0f - link_);
            grL_ = follow(grL_, reduction(gainToDb(levelL)));
            grR_ = follow(grR_, reduction(gainToDb(levelR)));
            const float makeup = makeup_.next(), mix = mix_.next();
            l[i] = l[i] * (1.0f - mix) + l[i] * dbToGain(-grL_) * makeup * mix;
            r[i] = r[i] * (1.0f - mix) + r[i] * dbToGain(-grR_) * makeup * mix;
            deepest = std::max(deepest, std::max(grL_, grR_));
        }
        meter_ = -deepest;
    }
    void readMeters(float* out) const override { out[0] = meter_; }

private:
    // how many db over the knee curve the level is, soft around the threshold
    float reduction(float db) const {
        const float over = db - threshold_;
        if (knee_ > 0.0f && 2.0f * std::fabs(over) <= knee_) {
            const float k = over + knee_ * 0.5f;
            return slope_ * k * k / (2.0f * knee_);
        }
        return over > 0.0f ? over * slope_ : 0.0f;
    }

    float follow(float current, float target) const {
        return current + (target - current) * (target > current ? attack_ : release_);
    }

    double rate_ = 48000;
    float threshold_ = -18, slope_ = 0.66f, knee_ = 6, attack_ = 0, release_ = 0, rms_ = 0, link_ = 1;
    bool rmsMode_ = true, sidechain_ = false;
    float grL_ = 0, grR_ = 0, msL_ = 0, msR_ = 0, meter_ = 0;
    OnePole scL_, scR_;
    Smoothed makeup_, mix_;
};

// looks ahead so the gain is already down when a peak arrives: nothing ever passes the ceiling
class LimiterPlugin : public Plugin {
public:
    enum { Input, Ceiling, Release, Count };
    static constexpr float LookaheadMs = 1.5f;

    static PluginInfo info() {
        PluginInfo i{"mochy.limiter", "Limiter", "Dynamics", "A brickwall that looks ahead, so no sample goes past the ceiling", {}, {}, {}};
        i.params = {
            param("input", "Input", "dB", 0, 24, 0),
            param("ceiling", "Ceiling", "dB", -12, 0, -1),
            logParam("release", "Release", "ms", 1, 1000, 80),
        };
        i.meters = {MeterInfo{"gr", "Reduction", "dB", -24, 0}};
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        look_ = std::max(1, static_cast<int>(LookaheadMs * 0.001 * rate));
        gains_.assign(static_cast<size_t>(look_ + 1), 1.0f);
        mins_.assign(static_cast<size_t>(look_), 1.0f);
        delayL_.prepare(look_ + 2);
        delayR_.prepare(look_ + 2);
        input_.prepare(rate, 20);
        reset();
    }
    void reset() override {
        std::fill(gains_.begin(), gains_.end(), 1.0f);
        std::fill(mins_.begin(), mins_.end(), 1.0f);
        sum_ = static_cast<float>(look_);
        gainPos_ = minPos_ = 0;
        out_ = 1.0f;
        delayL_.reset();
        delayR_.reset();
    }
    void update(const float* v) override {
        input_.set(dbToGain(v[Input]));
        ceiling_ = dbToGain(v[Ceiling]);
        release_ = envCoeff(rate_, v[Release]);
    }
    int latency() const override { return look_; }
    void process(float* l, float* r, int n) override {
        float deepest = 1.0f;
        for (int i = 0; i < n; i++) {
            const float in = input_.next();
            const float a = l[i] * in, b = r[i] * in;
            const float peak = std::max(std::fabs(a), std::fabs(b));
            gains_[gainPos_] = peak > ceiling_ ? ceiling_ / peak : 1.0f;
            gainPos_ = (gainPos_ + 1) % static_cast<int>(gains_.size());
            float lowest = 1.0f;
            for (float g : gains_) lowest = std::min(lowest, g);
            // a running average of the held minimum ramps the gain down over exactly the lookahead
            sum_ += lowest - mins_[minPos_];
            mins_[minPos_] = lowest;
            minPos_ = (minPos_ + 1) % look_;
            const float ramp = sum_ / static_cast<float>(look_);
            out_ = ramp < out_ ? ramp : out_ + (ramp - out_) * release_;
            const float gain = std::min(out_, ramp);
            const float heldL = delayL_.read(look_), heldR = delayR_.read(look_);
            delayL_.write(a);
            delayR_.write(b);
            l[i] = heldL * gain;
            r[i] = heldR * gain;
            deepest = std::min(deepest, gain);
        }
        meter_ = gainToDb(deepest);
    }
    void readMeters(float* out) const override { out[0] = meter_; }

private:
    double rate_ = 48000;
    int look_ = 72;
    std::vector<float> gains_, mins_;
    float sum_ = 0, out_ = 1, ceiling_ = 0.89f, release_ = 0, meter_ = 0;
    int gainPos_ = 0, minPos_ = 0;
    DelayLine delayL_, delayR_;
    Smoothed input_;
};

class GatePlugin : public Plugin {
public:
    enum { Threshold, Range, Attack, Hold, Release, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.gate", "Gate", "Dynamics", "Turns down whatever falls under the threshold", {}, {}, {}};
        i.params = {
            param("threshold", "Threshold", "dB", -80, 0, -50),
            param("range", "Range", "dB", -80, 0, -40),
            logParam("attack", "Attack", "ms", 0.1f, 50, 1),
            param("hold", "Hold", "ms", 0, 500, 50),
            logParam("release", "Release", "ms", 5, 2000, 150),
        };
        i.meters = {MeterInfo{"gr", "Reduction", "dB", -80, 0}};
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        reset();
    }
    void reset() override {
        env_ = 0.0f;
        gain_ = 1.0f;
        holdLeft_ = 0;
        open_ = false;
    }
    void update(const float* v) override {
        open_at_ = dbToGain(v[Threshold]);
        // three db of hysteresis, so a level hovering at the threshold doesn't chatter
        close_at_ = dbToGain(v[Threshold] - 3.0f);
        floor_ = dbToGain(v[Range]);
        attack_ = envCoeff(rate_, v[Attack]);
        release_ = envCoeff(rate_, v[Release]);
        hold_ = static_cast<int>(v[Hold] * 0.001f * static_cast<float>(rate_));
        follow_ = envCoeff(rate_, 0.5f);
        fall_ = envCoeff(rate_, 30.0f);
    }
    void process(float* l, float* r, int n) override {
        float lowest = 1.0f;
        for (int i = 0; i < n; i++) {
            const float level = std::max(std::fabs(l[i]), std::fabs(r[i]));
            env_ += (level - env_) * (level > env_ ? follow_ : fall_);
            if (env_ >= open_at_) {
                open_ = true;
                holdLeft_ = hold_;
            } else if (env_ < close_at_) {
                if (holdLeft_ > 0) holdLeft_--;
                else open_ = false;
            }
            const float target = open_ ? 1.0f : floor_;
            gain_ += (target - gain_) * (target > gain_ ? attack_ : release_);
            l[i] *= gain_;
            r[i] *= gain_;
            lowest = std::min(lowest, gain_);
        }
        meter_ = gainToDb(lowest);
    }
    void readMeters(float* out) const override { out[0] = meter_; }

private:
    double rate_ = 48000;
    float env_ = 0, gain_ = 1, open_at_ = 0.003f, close_at_ = 0.002f, floor_ = 0.01f;
    float attack_ = 0, release_ = 0, follow_ = 0, fall_ = 0, meter_ = 0;
    int hold_ = 0, holdLeft_ = 0;
    bool open_ = false;
};

// a fast and a slow follower: where the fast one leads it's an attack, where it trails it's the sustain
class TransientPlugin : public Plugin {
public:
    enum { AttackAmount, SustainAmount, Output, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.transient", "Transient shaper", "Dynamics", "Brings hits forward or tucks them in, and stretches or shortens their tail", {}, {}, {}};
        i.params = {
            param("attack", "Attack", "%", -100, 100, 0),
            param("sustain", "Sustain", "%", -100, 100, 0),
            param("output", "Output", "dB", -18, 18, 0),
        };
        return i;
    }

    void prepare(double rate, int) override {
        fastUp_ = envCoeff(rate, 0.5f);
        fastDown_ = envCoeff(rate, 30.0f);
        slowUp_ = envCoeff(rate, 25.0f);
        slowDown_ = envCoeff(rate, 250.0f);
        smooth_ = envCoeff(rate, 2.0f);
        output_.prepare(rate, 20);
        reset();
    }
    void reset() override {
        fast_ = slow_ = 0.0f;
        gainDb_ = 0.0f;
    }
    void update(const float* v) override {
        attack_ = v[AttackAmount] / 100.0f;
        sustain_ = v[SustainAmount] / 100.0f;
        output_.set(dbToGain(v[Output]));
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            const float level = std::max(std::fabs(l[i]), std::fabs(r[i]));
            fast_ += (level - fast_) * (level > fast_ ? fastUp_ : fastDown_);
            slow_ += (level - slow_) * (level > slow_ ? slowUp_ : slowDown_);
            const float diff = gainToDb(fast_ + 1e-9f) - gainToDb(slow_ + 1e-9f);
            const float target = diff > 0 ? attack_ * std::min(diff, 12.0f) * 1.5f : sustain_ * std::min(-diff, 12.0f) * 1.5f;
            gainDb_ += (target - gainDb_) * smooth_;
            const float g = dbToGain(gainDb_) * output_.next();
            l[i] *= g;
            r[i] *= g;
        }
    }

private:
    float fast_ = 0, slow_ = 0, gainDb_ = 0, attack_ = 0, sustain_ = 0;
    float fastUp_ = 0, fastDown_ = 0, slowUp_ = 0, slowDown_ = 0, smooth_ = 0;
    Smoothed output_;
};

}  // namespace fx
