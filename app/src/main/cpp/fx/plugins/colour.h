// effects that add movement and grit: saturation, delay, chorus and flanger
#pragma once

#include "../dsp.h"
#include "../plugin.h"
#include "info.h"

namespace fx {

// curves are run through their antiderivative (adaa), which keeps the new harmonics from folding back as aliasing without oversampling
class SaturationPlugin : public Plugin {
public:
    enum { Drive, Type, Tone, Mix, Output, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.saturation", "Saturation", "Colour", "Tape, tube or hard clipping, kept clean of aliasing", {}, {}, {}};
        i.params = {
            param("drive", "Drive", "dB", 0, 36, 6),
            choice("type", "Type", "Tape|Tube|Clip", 0),
            param("tone", "Tone", "", -100, 100, 0),
            param("mix", "Mix", "%", 0, 100, 100),
            param("output", "Output", "dB", -18, 18, 0),
        };
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        drive_.prepare(rate, 20);
        mix_.prepare(rate, 20);
        out_.prepare(rate, 20);
        tiltL_.setCutoff(rate, 800.0f);
        tiltR_.setCutoff(rate, 800.0f);
        reset();
    }
    void reset() override {
        prevL_ = prevR_ = 0.0f;
        tiltL_.reset();
        tiltR_.reset();
    }
    void update(const float* v) override {
        drive_.set(dbToGain(v[Drive]));
        type_ = static_cast<int>(v[Type]);
        const float tilt = v[Tone] / 100.0f;
        lowGain_ = dbToGain(-6.0f * tilt);
        highGain_ = dbToGain(6.0f * tilt);
        tilting_ = std::fabs(tilt) > 0.001f;
        mix_.set(v[Mix] / 100.0f);
        out_.set(dbToGain(v[Output]));
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            const float d = drive_.next();
            // a harder drive is pulled back down, so the knob changes the colour more than the level
            const float level = 1.0f / std::pow(d, 0.6f);
            const float mix = mix_.next(), out = out_.next();
            float wl = shape(l[i] * d, prevL_) * level;
            float wr = shape(r[i] * d, prevR_) * level;
            if (tilting_) {
                const float lowL = tiltL_.lowpass(wl), lowR = tiltR_.lowpass(wr);
                wl = lowL * lowGain_ + (wl - lowL) * highGain_;
                wr = lowR * lowGain_ + (wr - lowR) * highGain_;
            }
            l[i] = (l[i] * (1.0f - mix) + wl * mix) * out;
            r[i] = (r[i] * (1.0f - mix) + wr * mix) * out;
        }
    }

private:
    static constexpr float TubeBias = 0.3f;

    static float logCosh(float x) {
        const float a = std::fabs(x);
        return a + std::log1p(std::exp(-2.0f * a)) - 0.69314718f;
    }

    float curve(float x) const {
        switch (type_) {
            case 1: return std::tanh(x + TubeBias) - std::tanh(TubeBias);
            case 2: return clampf(x, -1.0f, 1.0f);
            default: return std::tanh(x);
        }
    }

    float antiderivative(float x) const {
        switch (type_) {
            case 1: return logCosh(x + TubeBias) - x * std::tanh(TubeBias);
            case 2: return std::fabs(x) <= 1.0f ? x * x * 0.5f : std::fabs(x) - 0.5f;
            default: return logCosh(x);
        }
    }

    float shape(float x, float& prev) {
        const float dx = x - prev;
        const float y = std::fabs(dx) > 1e-4f ? (antiderivative(x) - antiderivative(prev)) / dx : curve((x + prev) * 0.5f);
        prev = x;
        return y;
    }

    double rate_ = 48000;
    Smoothed drive_, mix_, out_;
    OnePole tiltL_, tiltR_;
    float prevL_ = 0, prevR_ = 0, lowGain_ = 1, highGain_ = 1;
    bool tilting_ = false;
    int type_ = 0;
};

class DelayPlugin : public Plugin {
public:
    enum { Time, Feedback, PingPong, LowCut, HighCut, Wow, Duck, Mix, Count };
    static constexpr float MaxMs = 2000.0f;

    static PluginInfo info() {
        PluginInfo i{"mochy.delay", "Delay", "Time", "Echoes that darken as they repeat, with ping-pong and ducking", {}, {}, {}};
        i.params = {
            logParam("time", "Time", "ms", 1, MaxMs, 350),
            param("feedback", "Feedback", "%", 0, 95, 35),
            toggle("pingPong", "Ping-pong", false),
            logParam("lowCut", "Low cut", "Hz", 20, 2000, 150),
            logParam("highCut", "High cut", "Hz", 1000, 20000, 6000),
            param("wow", "Wow", "%", 0, 100, 10),
            param("duck", "Duck", "%", 0, 100, 0),
            param("mix", "Mix", "%", 0, 100, 25),
        };
        i.showsTail = true;
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        lineL_.prepare(static_cast<int>((MaxMs + 20.0f) * 0.001 * rate));
        lineR_.prepare(static_cast<int>((MaxMs + 20.0f) * 0.001 * rate));
        time_.prepare(rate, 150);
        mix_.prepare(rate, 20);
        wow_.set(rate, 0.4f);
        duckAttack_ = 1.0f - std::exp(-1.0f / (0.005f * static_cast<float>(rate)));
        duckRelease_ = 1.0f - std::exp(-1.0f / (0.25f * static_cast<float>(rate)));
        reset();
    }
    void reset() override {
        lineL_.reset();
        lineR_.reset();
        lowL_.reset();
        lowR_.reset();
        highL_.reset();
        highR_.reset();
        duckEnv_ = 0.0f;
        time_.snap(time_.target);
    }
    void update(const float* v) override {
        time_.set(v[Time] * 0.001f * static_cast<float>(rate_));
        if (time_.value <= 0) time_.snap(time_.target);
        feedback_ = v[Feedback] / 100.0f;
        pingPong_ = v[PingPong] > 0.5f;
        lowL_.setCutoff(rate_, v[LowCut]);
        lowR_.setCutoff(rate_, v[LowCut]);
        highL_.setCutoff(rate_, v[HighCut]);
        highR_.setCutoff(rate_, v[HighCut]);
        wowDepth_ = v[Wow] / 100.0f * 0.002f * static_cast<float>(rate_);
        duck_ = v[Duck] / 100.0f;
        mix_.set(v[Mix] / 100.0f);
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            const float wobble = wow_.next() * wowDepth_;
            const float delay = clampf(time_.next() + wobble, 1.0f, static_cast<float>(lineL_.capacity() - 4));
            float echoL = lineL_.readCubic(delay);
            float echoR = lineR_.readCubic(delay);
            // each repeat loses its lows and highs, the way tape and old units did
            float fbL = highL_.lowpass(lowL_.highpass(echoL)) * feedback_;
            float fbR = highR_.lowpass(lowR_.highpass(echoR)) * feedback_;
            if (pingPong_) {
                lineL_.write(undenormal((l[i] + r[i]) * 0.5f + fbR));
                lineR_.write(undenormal(fbL));
            } else {
                lineL_.write(undenormal(l[i] + fbL));
                lineR_.write(undenormal(r[i] + fbR));
            }
            float duckGain = 1.0f;
            if (duck_ > 0.0f) {
                const float level = std::max(std::fabs(l[i]), std::fabs(r[i]));
                duckEnv_ += (level - duckEnv_) * (level > duckEnv_ ? duckAttack_ : duckRelease_);
                duckGain = 1.0f / (1.0f + duck_ * 8.0f * duckEnv_);
            }
            const float mix = mix_.next();
            const float dry = std::min(1.0f, 2.0f * (1.0f - mix)), wet = std::min(1.0f, 2.0f * mix) * duckGain;
            l[i] = l[i] * dry + echoL * wet;
            r[i] = r[i] * dry + echoR * wet;
        }
    }

private:
    double rate_ = 48000;
    DelayLine lineL_, lineR_;
    OnePole lowL_, lowR_, highL_, highR_;
    Smoothed time_, mix_;
    Lfo wow_;
    float feedback_ = 0.35f, wowDepth_ = 0, duck_ = 0, duckEnv_ = 0, duckAttack_ = 0, duckRelease_ = 0;
    bool pingPong_ = false;
};

class ChorusPlugin : public Plugin {
public:
    enum { Mode, Rate, Depth, DelayMs, Feedback, Phase, Mix, Count };

    static PluginInfo info() {
        PluginInfo i{"mochy.chorus", "Chorus", "Time", "Chorus, flanger or vibrato from one moving delay per side", {}, {}, {}};
        i.params = {
            choice("mode", "Mode", "Chorus|Flanger|Vibrato", 0),
            logParam("rate", "Rate", "Hz", 0.05f, 10, 0.8f),
            param("depth", "Depth", "%", 0, 100, 40),
            logParam("delay", "Delay", "ms", 0.5f, 30, 12),
            param("feedback", "Feedback", "%", -95, 95, 0),
            param("phase", "Stereo", "°", 0, 180, 90),
            param("mix", "Mix", "%", 0, 100, 50),
        };
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = rate;
        lineL_.prepare(static_cast<int>(0.05 * rate) + 8);
        lineR_.prepare(static_cast<int>(0.05 * rate) + 8);
        mix_.prepare(rate, 20);
        base_.prepare(rate, 80);
        reset();
    }
    void reset() override {
        lineL_.reset();
        lineR_.reset();
        fbL_ = fbR_ = 0.0f;
    }
    void update(const float* v) override {
        mode_ = static_cast<int>(v[Mode]);
        lfoL_.set(rate_, v[Rate]);
        lfoR_.set(rate_, v[Rate]);
        const float phase = v[Phase] / 360.0f;
        if (phase != phase_) {
            lfoR_.phase = lfoL_.phase + phase;
            if (lfoR_.phase >= 1.0f) lfoR_.phase -= 1.0f;
            phase_ = phase;
        }
        const float ms = mode_ == 1 ? std::min(v[DelayMs], 5.0f) : v[DelayMs];
        base_.set(ms * 0.001f * static_cast<float>(rate_));
        if (base_.value <= 0) base_.snap(base_.target);
        depth_ = v[Depth] / 100.0f;
        feedback_ = mode_ == 1 ? v[Feedback] / 100.0f : v[Feedback] / 200.0f;
        mix_.set(mode_ == 2 ? 1.0f : v[Mix] / 100.0f);
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) {
            const float base = base_.next();
            const float swing = base * 0.9f * depth_;
            const float dl = std::max(1.0f, base + lfoL_.next() * swing);
            const float dr = std::max(1.0f, base + lfoR_.next() * swing);
            const float wetL = lineL_.readCubic(dl), wetR = lineR_.readCubic(dr);
            lineL_.write(undenormal(l[i] + wetL * feedback_));
            lineR_.write(undenormal(r[i] + wetR * feedback_));
            const float mix = mix_.next();
            l[i] = l[i] * (1.0f - mix) + wetL * mix;
            r[i] = r[i] * (1.0f - mix) + wetR * mix;
        }
    }

private:
    double rate_ = 48000;
    DelayLine lineL_, lineR_;
    Lfo lfoL_, lfoR_;
    Smoothed mix_, base_;
    float depth_ = 0.4f, feedback_ = 0, phase_ = -1, fbL_ = 0, fbR_ = 0;
    int mode_ = 0;
};

}  // namespace fx
