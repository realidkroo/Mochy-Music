// mochy's own room: early reflections, then a 16-line feedback network whose bass, middle and treble each fade over their own time
#pragma once

#include "../dsp.h"

#include <array>

namespace fx {

struct SpaceSettings {
    float preDelayMs = 20.0f;
    float decaySec = 2.2f;
    float bassMul = 1.2f;
    float trebleMul = 0.55f;
    float bassHz = 300.0f;
    float trebleHz = 4500.0f;
    float size = 0.55f;
    float diffusion = 0.75f;
    float early = 0.35f;
    float modHz = 0.6f;
    float modDepth = 0.3f;
    float lowCutHz = 120.0f;
    float highCutHz = 11000.0f;
    float width = 1.0f;
    float duck = 0.0f;
    bool freeze = false;
    float mix = 0.3f;
};

class SpaceReverb {
public:
    static constexpr int Lines = 16;

    void prepare(double rate) {
        rate_ = rate;
        const float maxLineMs = MaxBaseMs * MaxScale + 4.0f;
        for (auto& line : lines_) line.prepare(static_cast<int>(maxLineMs * 0.001 * rate) + 8);
        sideL_.prepare(static_cast<int>(0.26 * rate) + 8);
        sideR_.prepare(static_cast<int>(0.26 * rate) + 8);
        earlyLine_.prepare(static_cast<int>(0.12 * rate) + 8);
        for (int i = 0; i < 4; i++) {
            diffL_[i].prepare(static_cast<int>(0.03 * rate) + 8);
            diffR_[i].prepare(static_cast<int>(0.03 * rate) + 8);
        }
        for (int i = 0; i < 2; i++) {
            erDiffL_[i].prepare(static_cast<int>(0.01 * rate) + 8);
            erDiffR_[i].prepare(static_cast<int>(0.01 * rate) + 8);
        }
        for (int i = 0; i < Lines; i++) {
            lfo_[i].phase = static_cast<float>(i) / Lines;
            current_[i] = target_[i] = 0.0f;
        }
        glide_.prepare(rate, 120.0f);
        duckEnv_ = 0.0f;
        apply(settings_, true);
        reset();
    }

    void reset() {
        for (auto& line : lines_) line.reset();
        sideL_.reset();
        sideR_.reset();
        earlyLine_.reset();
        for (int i = 0; i < 4; i++) {
            diffL_[i].reset();
            diffR_[i].reset();
        }
        for (int i = 0; i < 2; i++) {
            erDiffL_[i].reset();
            erDiffR_[i].reset();
        }
        for (int i = 0; i < Lines; i++) {
            lowSplit_[i].reset();
            highSplit_[i].reset();
            current_[i] = target_[i];
        }
        for (Biquad* f : {&lowCutL_, &lowCutR_, &highCutL_, &highCutR_}) f->reset();
        duckEnv_ = 0.0f;
    }

    // [snap] jumps the delay lengths instead of gliding, for a fresh start
    void apply(const SpaceSettings& s, bool snap = false) {
        settings_ = s;
        if (rate_ <= 0) return;
        const float scale = MinScale + (MaxScale - MinScale) * clampf(s.size, 0.0f, 1.0f);
        for (int i = 0; i < Lines; i++) {
            // log-spaced, then nudged to a prime so no two lines ring at the same pitch
            const float ms = MinBaseMs * std::pow(MaxBaseMs / MinBaseMs, static_cast<float>(i) / (Lines - 1)) * scale;
            target_[i] = static_cast<float>(nextPrime(static_cast<int>(ms * 0.001 * rate_)));
            if (snap) current_[i] = target_[i];
            lfo_[i].set(rate_, s.modHz * (0.75f + 0.5f * static_cast<float>(i) / (Lines - 1)));
            lowSplit_[i].setCutoff(rate_, s.bassHz);
            highSplit_[i].setCutoff(rate_, s.trebleHz);
        }
        const float decay = std::max(0.05f, s.decaySec);
        // the moving read loses a little treble on every pass, so a modulated room is given that back or it fades early
        const bool moving = s.modDepth > 0.0f;
        const float midFix = moving ? MidModFix : 1.0f;
        const float trebleFix = moving ? TrebleModFix : 1.0f;
        for (int i = 0; i < Lines; i++) {
            // jot's rule: each pass through a line loses its share of 60 db over the band's own decay time
            const float seconds = target_[i] / static_cast<float>(rate_);
            gainLow_[i] = s.freeze ? FreezeGain : std::pow(10.0f, -3.0f * seconds / (decay * s.bassMul));
            gainMid_[i] = s.freeze ? FreezeGain : std::pow(10.0f, -3.0f * seconds / (decay * midFix));
            gainHigh_[i] = s.freeze ? FreezeGain : std::pow(10.0f, -3.0f * seconds / (decay * s.trebleMul * trebleFix));
        }
        const float diffScale = 0.5f + 0.5f * clampf(s.size, 0.0f, 1.0f);
        static const float diffMs[4] = {4.7f, 3.6f, 12.7f, 9.3f};
        for (int i = 0; i < 4; i++) diffLen_[i] = std::max(1, static_cast<int>(diffMs[i] * diffScale * 0.001 * rate_));
        diffGain_[0] = diffGain_[1] = 0.75f * s.diffusion;
        diffGain_[2] = diffGain_[3] = 0.625f * s.diffusion;
        static const float erDiffMs[2] = {1.3f, 2.9f};
        for (int i = 0; i < 2; i++) erDiffLen_[i] = std::max(1, static_cast<int>(erDiffMs[i] * 0.001 * rate_));
        const float erScale = 0.35f + 0.9f * clampf(s.size, 0.0f, 1.0f);
        for (int t = 0; t < EarlyTaps; t++) {
            earlyTapL_[t] = std::max(1, static_cast<int>(EarlyMsL[t] * erScale * 0.001 * rate_));
            earlyTapR_[t] = std::max(1, static_cast<int>(EarlyMsR[t] * erScale * 0.001 * rate_));
        }
        preDelaySamples_ = static_cast<int>(clampf(s.preDelayMs, 0.0f, 250.0f) * 0.001f * static_cast<float>(rate_));
        modSamples_ = clampf(s.modDepth, 0.0f, 1.0f) * 0.0015f * static_cast<float>(rate_);
        lowCutL_.set(FilterShape::HighPass, rate_, s.lowCutHz, 0.7071f);
        lowCutR_ = lowCutL_;
        highCutL_.set(FilterShape::LowPass, rate_, s.highCutHz, 0.7071f);
        highCutR_ = highCutL_;
        // a longer tail carries more energy, so it's leveled to sound about as loud as a medium room
        wetNorm_ = clampf(std::sqrt(1.5f / decay), 0.3f, 1.4f);
        const float mix = clampf(s.mix, 0.0f, 1.0f);
        dryGain_ = std::min(1.0f, 2.0f * (1.0f - mix));
        wetGain_ = std::min(1.0f, 2.0f * mix);
        duckRelease_ = 1.0f - std::exp(-1.0f / (0.3f * static_cast<float>(rate_)));
        duckAttack_ = 1.0f - std::exp(-1.0f / (0.005f * static_cast<float>(rate_)));
    }

    // wet only when [wetOnly], for drawing the decay; otherwise the blended signal
    void process(float* left, float* right, int frames, bool wetOnly = false) {
        const SpaceSettings& s = settings_;
        const float inputGain = s.freeze ? 0.0f : 0.5f;
        const float earlyLevel = clampf(s.early, 0.0f, 1.0f);
        const float width = clampf(s.width, 0.0f, 2.0f);
        const bool modulate = modSamples_ > 0.05f;
        for (int n = 0; n < frames; n++) {
            const float dryL = left[n], dryR = right[n];

            // the pre-delay holds each side apart, so the room keeps the song's stereo
            sideL_.write(dryL);
            sideR_.write(dryR);
            const float inL = preDelaySamples_ > 0 ? sideL_.read(preDelaySamples_) : dryL;
            const float inR = preDelaySamples_ > 0 ? sideR_.read(preDelaySamples_) : dryR;

            // early reflections: a sparse pattern off nearby walls, then a little smearing
            earlyLine_.write(inL + inR);
            float erL = 0.0f, erR = 0.0f;
            for (int t = 0; t < EarlyTaps; t++) {
                erL += EarlyGain[t] * earlyLine_.read(earlyTapL_[t]);
                erR += EarlyGain[t] * earlyLine_.read(earlyTapR_[t]);
            }
            for (int i = 0; i < 2; i++) {
                erL = allpass(erDiffL_[i], erL, erDiffLen_[i], 0.6f);
                erR = allpass(erDiffR_[i], erR, erDiffLen_[i], 0.6f);
            }
            erL *= 0.35f;
            erR *= 0.35f;

            // input diffusion turns each hit into a cloud before it reaches the tail
            float dL = inL + erL * 0.5f, dR = inR + erR * 0.5f;
            for (int i = 0; i < 4; i++) {
                dL = allpass(diffL_[i], dL, diffLen_[i], diffGain_[i]);
                dR = allpass(diffR_[i], dR, diffLen_[i] + 7, diffGain_[i]);
            }

            float reads[Lines];
            for (int i = 0; i < Lines; i++) {
                current_[i] += (target_[i] - current_[i]) * glide_.coeff;
                const float wobble = modulate ? lfo_[i].next() * modSamples_ : 0.0f;
                reads[i] = lines_[i].readCubic(std::max(2.0f, current_[i] + wobble));
            }

            float lateL = 0.0f, lateR = 0.0f;
            for (int i = 0; i < Lines; i++) {
                lateL += OutL[i] * reads[i];
                lateR += OutR[i] * reads[i];
            }

            // each line fades its bass, middle and treble at their own rates, and the split adds back to the line exactly
            for (int i = 0; i < Lines; i++) {
                const float x = reads[i];
                if (s.freeze) {
                    reads[i] = x * FreezeGain;
                    continue;
                }
                const float low = lowSplit_[i].lowpass(x);
                const float rest = x - low;
                const float mid = highSplit_[i].lowpass(rest);
                const float high = rest - mid;
                reads[i] = low * gainLow_[i] + mid * gainMid_[i] + high * gainHigh_[i];
            }
            hadamard16(reads);
            for (int i = 0; i < Lines; i++) {
                const float in = (i & 1 ? dR : dL) * InSign[i] * inputGain;
                lines_[i].write(undenormal(reads[i] + in));
            }

            float wetL = (lateL * LateScale + erL * earlyLevel * 2.0f) * wetNorm_;
            float wetR = (lateR * LateScale + erR * earlyLevel * 2.0f) * wetNorm_;
            wetL = highCutL_.process(lowCutL_.process(wetL));
            wetR = highCutR_.process(lowCutR_.process(wetR));
            const float mid = (wetL + wetR) * 0.5f;
            const float side = (wetL - wetR) * 0.5f * width;
            wetL = mid + side;
            wetR = mid - side;

            float duckGain = 1.0f;
            if (s.duck > 0.0f) {
                const float level = std::max(std::fabs(dryL), std::fabs(dryR));
                duckEnv_ += (level - duckEnv_) * (level > duckEnv_ ? duckAttack_ : duckRelease_);
                duckGain = 1.0f / (1.0f + s.duck * 6.0f * duckEnv_);
            }

            if (wetOnly) {
                left[n] = wetL;
                right[n] = wetR;
            } else {
                left[n] = dryL * dryGain_ + wetL * wetGain_ * duckGain;
                right[n] = dryR * dryGain_ + wetR * wetGain_ * duckGain;
            }
        }
    }

private:
    static constexpr float MinBaseMs = 9.0f;
    static constexpr float MaxBaseMs = 63.0f;
    static constexpr float MinScale = 0.35f;
    static constexpr float MaxScale = 1.6f;
    // a frozen room keeps ringing; a hair under one keeps the interpolation from slowly building up
    static constexpr float FreezeGain = 0.99995f;
    // tuned until the measured decay matches the knob within 5% (tools/fx/reverb_test.cpp)
    static constexpr float MidModFix = 1.05f;
    static constexpr float TrebleModFix = 1.25f;
    static constexpr int EarlyTaps = 12;
    static constexpr float LateScale = 0.35f;
    static constexpr float EarlyMsL[EarlyTaps] = {3.1f, 7.9f, 11.3f, 15.7f, 19.1f, 23.9f, 29.3f, 33.1f, 41.3f, 47.9f, 55.3f, 63.7f};
    static constexpr float EarlyMsR[EarlyTaps] = {4.3f, 6.7f, 12.9f, 14.1f, 21.7f, 25.3f, 27.1f, 36.7f, 39.9f, 51.1f, 53.9f, 67.3f};
    static constexpr float EarlyGain[EarlyTaps] = {0.84f, -0.63f, 0.55f, -0.47f, 0.41f, -0.36f, 0.31f, -0.27f, 0.23f, -0.2f, 0.17f, -0.14f};
    static constexpr float InSign[Lines] = {1, 1, -1, -1, 1, -1, 1, -1, -1, 1, 1, -1, -1, -1, 1, 1};
    static constexpr float OutL[Lines] = {1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, 1, 0, -1, 0};
    static constexpr float OutR[Lines] = {0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1};

    static float allpass(DelayLine& line, float x, int delay, float g) {
        const float delayed = line.read(delay);
        const float v = x + g * delayed;
        line.write(undenormal(v));
        return delayed - g * v;
    }

    // walsh-hadamard mixing: every line feeds every other with no loss, in 64 additions
    static void hadamard16(float* x) {
        for (int span = 1; span < Lines; span <<= 1) {
            for (int i = 0; i < Lines; i += span << 1) {
                for (int j = i; j < i + span; j++) {
                    const float a = x[j], b = x[j + span];
                    x[j] = a + b;
                    x[j + span] = a - b;
                }
            }
        }
        for (int i = 0; i < Lines; i++) x[i] *= 0.25f;
    }

    static bool isPrime(int v) {
        if (v < 2) return false;
        if (v % 2 == 0) return v == 2;
        for (int d = 3; d * d <= v; d += 2) {
            if (v % d == 0) return false;
        }
        return true;
    }

    static int nextPrime(int v) {
        if (v < 3) v = 3;
        while (!isPrime(v)) v++;
        return v;
    }

    double rate_ = 0.0;
    SpaceSettings settings_;
    std::array<DelayLine, Lines> lines_;
    DelayLine sideL_, sideR_, earlyLine_;
    DelayLine diffL_[4], diffR_[4], erDiffL_[2], erDiffR_[2];
    int diffLen_[4] = {1, 1, 1, 1};
    float diffGain_[4] = {};
    int erDiffLen_[2] = {1, 1};
    int earlyTapL_[EarlyTaps] = {};
    int earlyTapR_[EarlyTaps] = {};
    std::array<Lfo, Lines> lfo_;
    std::array<OnePole, Lines> lowSplit_, highSplit_;
    float current_[Lines] = {};
    float target_[Lines] = {};
    float gainLow_[Lines] = {}, gainMid_[Lines] = {}, gainHigh_[Lines] = {};
    Smoothed glide_;
    int preDelaySamples_ = 0;
    float modSamples_ = 0.0f;
    Biquad lowCutL_, lowCutR_, highCutL_, highCutR_;
    float wetNorm_ = 1.0f, dryGain_ = 1.0f, wetGain_ = 0.6f;
    float duckEnv_ = 0.0f, duckAttack_ = 0.0f, duckRelease_ = 0.0f;
};

}  // namespace fx
