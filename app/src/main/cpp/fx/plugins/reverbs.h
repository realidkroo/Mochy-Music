// the two reverbs: mochy's own space, and progenitor 2 from jamesdsp
#pragma once

#include "../plugin.h"
#include "../reverb/progenitor.h"
#include "../reverb/space.h"
#include "info.h"

namespace fx {

class SpacePlugin : public Plugin {
public:
    enum {
        PreDelay, Decay, BassMul, TrebleMul, BassHz, TrebleHz, Size, Diffusion, Early,
        ModRate, ModDepth, LowCut, HighCut, Width, Duck, Freeze, Mix, Count
    };

    static PluginInfo info() {
        PluginInfo i{"mochy.space", "Space", "Reverb", "A room whose bass, middle and treble each fade over their own time", {}, {}, {}};
        i.params = {
            param("preDelay", "Pre-delay", "ms", 0, 250, 20),
            logParam("decay", "Decay", "s", 0.2f, 20, 2.2f),
            param("bassMul", "Bass decay", "×", 0.3f, 2.5f, 1.2f),
            param("trebleMul", "Treble decay", "×", 0.1f, 1.2f, 0.55f),
            logParam("bassHz", "Bass split", "Hz", 80, 800, 300),
            logParam("trebleHz", "Treble split", "Hz", 1500, 12000, 4500),
            param("size", "Size", "%", 0, 100, 55),
            param("diffusion", "Diffusion", "%", 0, 100, 75),
            param("early", "Early", "%", 0, 100, 35),
            logParam("modRate", "Drift rate", "Hz", 0.05f, 4, 0.6f),
            param("modDepth", "Drift", "%", 0, 100, 30),
            logParam("lowCut", "Low cut", "Hz", 20, 1000, 120),
            logParam("highCut", "High cut", "Hz", 1000, 20000, 11000),
            param("width", "Width", "%", 0, 200, 100),
            param("duck", "Duck", "%", 0, 100, 0),
            toggle("freeze", "Freeze", false),
            param("mix", "Mix", "%", 0, 100, 30),
        };
        i.presets = {
            {"Room", {8, 0.7f, 1.1f, 0.6f, 250, 5000, 30, 70, 45, 0.5f, 20, 100, 12000, 100, 0, 0, 25}},
            {"Studio booth", {2, 0.35f, 1.0f, 0.5f, 300, 6000, 15, 60, 60, 0.4f, 10, 150, 14000, 80, 0, 0, 20}},
            {"Chamber", {12, 1.4f, 1.2f, 0.55f, 300, 4500, 45, 80, 40, 0.6f, 25, 120, 11000, 100, 0, 0, 28}},
            {"Concert hall", {25, 2.4f, 1.3f, 0.5f, 300, 4000, 70, 80, 30, 0.5f, 30, 100, 10000, 110, 0, 0, 30}},
            {"Cathedral", {40, 6.0f, 1.4f, 0.45f, 250, 3500, 100, 90, 20, 0.3f, 35, 80, 9000, 120, 0, 0, 32}},
            {"Plate", {0, 1.8f, 0.8f, 0.9f, 400, 7000, 40, 95, 0, 0.9f, 25, 200, 16000, 120, 0, 0, 25}},
            {"Vocal plate", {15, 1.6f, 0.7f, 0.8f, 500, 6500, 35, 90, 5, 0.8f, 30, 250, 14000, 110, 40, 0, 22}},
            {"Ambience", {5, 0.9f, 1.0f, 0.7f, 300, 6000, 50, 60, 70, 0.4f, 15, 120, 12000, 140, 0, 0, 18}},
            {"Dreamy", {35, 8.0f, 1.2f, 0.6f, 250, 4000, 90, 95, 10, 0.25f, 70, 150, 8000, 150, 50, 0, 40}},
            {"Infinite", {0, 20, 1, 1, 300, 5000, 80, 90, 0, 0.3f, 40, 100, 10000, 130, 0, 1, 35}},
        };
        i.showsTail = true;
        return i;
    }

    void prepare(double rate, int) override { reverb_.prepare(rate); }
    void reset() override { reverb_.reset(); }
    void update(const float* v) override {
        SpaceSettings s;
        s.preDelayMs = v[PreDelay];
        s.decaySec = v[Decay];
        s.bassMul = v[BassMul];
        s.trebleMul = v[TrebleMul];
        s.bassHz = v[BassHz];
        s.trebleHz = v[TrebleHz];
        s.size = v[Size] / 100.0f;
        s.diffusion = v[Diffusion] / 100.0f;
        s.early = v[Early] / 100.0f;
        s.modHz = v[ModRate];
        s.modDepth = v[ModDepth] / 100.0f;
        s.lowCutHz = v[LowCut];
        s.highCutHz = v[HighCut];
        s.width = v[Width] / 100.0f;
        s.duck = v[Duck] / 100.0f;
        s.freeze = v[Freeze] > 0.5f;
        s.mix = v[Mix] / 100.0f;
        reverb_.apply(s, !started_);
        started_ = true;
    }
    void process(float* l, float* r, int n) override { reverb_.process(l, r, n); }

private:
    SpaceReverb reverb_;
    bool started_ = false;
};

class ProgenitorPlugin : public Plugin {
public:
    enum {
        Oversample, ErToLate, ErWet, Dry, ErFactor, ErWidth, Width, Wet, Wander, BassBoost, Spin,
        InputLpf, BassLpf, DampLpf, OutputLpf, Rt60, DelayMs, Count
    };

    static PluginInfo info() {
        PluginInfo i{"mochy.progenitor", "Progenitor", "Reverb", "Progenitor 2, the dense hall from JamesDSP, with every knob open", {}, {}, {}};
        i.params = {
            choice("oversample", "Oversample", "1x|2x", 0),
            param("erToLate", "Early to tail", "%", 0, 100, 40),
            param("erWet", "Early level", "dB", -70, 0, -9),
            param("dry", "Dry", "dB", -70, 6, -7),
            param("erFactor", "Early size", "", 0.5f, 2.5f, 1.6f),
            param("erWidth", "Early width", "", -1, 1, 0.7f),
            param("width", "Width", "", 0, 1, 1),
            param("wet", "Wet", "dB", -70, 6, 0),
            param("wander", "Wander", "", 0.05f, 0.6f, 0.25f),
            param("bassBoost", "Bass boost", "", 0, 0.6f, 0.15f),
            logParam("spin", "Spin", "Hz", 0.1f, 5, 0.7f),
            logParam("inputLpf", "Input cut", "Hz", 1000, 20000, 17000),
            logParam("bassLpf", "Bass cut", "Hz", 50, 1500, 500),
            logParam("dampLpf", "Damping", "Hz", 1000, 20000, 7000),
            logParam("outputLpf", "Output cut", "Hz", 1000, 20000, 10000),
            logParam("rt60", "Decay", "s", 0.1f, 30, 3.2f),
            param("delay", "Delay", "ms", -100, 100, 20),
        };
        for (const auto& preset : progenitor::presets()) {
            const progenitor::Settings& s = preset.second;
            i.presets.push_back({preset.first, {static_cast<float>(s.oversample - 1), s.erToLate * 100, s.erWetDb, s.dryDb, s.erFactor,
                                                s.erWidth, s.width, s.wetDb, s.wander, s.bassBoost, s.spin, s.inputLpf, s.bassLpf,
                                                s.dampLpf, s.outputLpf, s.rt60, s.delay * 1000}});
        }
        i.showsTail = true;
        return i;
    }

    void prepare(double rate, int) override {
        rate_ = static_cast<int>(rate);
        reverb_.configure(rate_, settings_);
        configured_ = true;
    }
    void reset() override { reverb_.configure(rate_, settings_); }
    void update(const float* v) override {
        progenitor::Settings s;
        s.oversample = static_cast<int>(v[Oversample]) + 1;
        s.erToLate = v[ErToLate] / 100.0f;
        s.erWetDb = v[ErWet];
        s.dryDb = v[Dry];
        s.erFactor = v[ErFactor];
        s.erWidth = v[ErWidth];
        s.width = v[Width];
        s.wetDb = v[Wet];
        s.wander = v[Wander];
        s.bassBoost = v[BassBoost];
        s.spin = v[Spin];
        s.inputLpf = v[InputLpf];
        s.bassLpf = v[BassLpf];
        s.dampLpf = v[DampLpf];
        s.outputLpf = v[OutputLpf];
        s.rt60 = v[Rt60];
        s.delay = v[DelayMs] / 1000.0f;
        const bool reshape = !configured_ || !progenitor::Reverb::sameShape(s, settings_);
        settings_ = s;
        if (reshape) reverb_.configure(rate_, settings_);
        else reverb_.retune(rate_, settings_);
        configured_ = true;
    }
    void process(float* l, float* r, int n) override {
        for (int i = 0; i < n; i++) reverb_.process(l[i], r[i], &l[i], &r[i]);
    }

private:
    progenitor::Reverb reverb_;
    progenitor::Settings settings_;
    int rate_ = 48000;
    bool configured_ = false;
};

}  // namespace fx
