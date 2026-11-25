// small dsp pieces every mixer effect shares: level maths, smoothing, filters, delay lines
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

namespace fx {

constexpr float Pi = 3.14159265358979f;
constexpr float TwoPi = 6.28318530717959f;

inline float dbToGain(float db) { return std::pow(10.0f, db * 0.05f); }

inline float gainToDb(float gain) { return gain > 1e-9f ? 20.0f * std::log10(gain) : -180.0f; }

inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

// a nan or infinity from one effect would ride the feedback loops of every effect after it forever
inline bool finite(float v) { return (v - v) == 0.0f; }

// tiny numbers left in a decaying filter make some arm cores run many times slower
inline float undenormal(float v) { return std::fabs(v) < 1e-20f ? 0.0f : v; }

// glides a value to its target over a fixed time, so a knob never zips
struct Smoothed {
    float value = 0.0f;
    float target = 0.0f;
    float coeff = 0.0f;
    bool primed = false;

    void prepare(double rate, float ms) { coeff = 1.0f - std::exp(-1.0f / (static_cast<float>(rate) * ms * 0.001f)); }
    void snap(float v) {
        value = target = v;
        primed = true;
    }
    // the first value lands at once, so a new effect doesn't fade up from silence
    void set(float v) {
        if (!primed) snap(v);
        target = v;
    }
    float next() {
        value += (target - value) * coeff;
        return value;
    }
    bool settled() const { return std::fabs(target - value) < 1e-6f; }
};

// one-pole low pass, used for envelopes, damping and tone
struct OnePole {
    float a = 0.0f;
    float z = 0.0f;

    void setCutoff(double rate, float hz) { a = 1.0f - std::exp(-TwoPi * hz / static_cast<float>(rate)); }
    float lowpass(float x) {
        z += a * (x - z);
        z = undenormal(z);
        return z;
    }
    float highpass(float x) { return x - lowpass(x); }
    void reset() { z = 0.0f; }
};

enum class FilterShape { LowPass, HighPass, BandPass, Notch, Peak, LowShelf, HighShelf, AllPass };

// rbj cookbook biquad in transposed direct form 2, which keeps its precision in float
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    void set(FilterShape shape, double rate, float hz, float q, float gainDb = 0.0f) {
        const float freq = clampf(hz, 10.0f, static_cast<float>(rate) * 0.49f);
        const float w = TwoPi * freq / static_cast<float>(rate);
        const float cw = std::cos(w), sw = std::sin(w);
        const float alpha = sw / (2.0f * std::max(q, 0.025f));
        const float amp = std::pow(10.0f, gainDb / 40.0f);
        float nb0, nb1, nb2, na0, na1, na2;
        switch (shape) {
            case FilterShape::LowPass:
                nb0 = (1 - cw) * 0.5f; nb1 = 1 - cw; nb2 = nb0; na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                break;
            case FilterShape::HighPass:
                nb0 = (1 + cw) * 0.5f; nb1 = -(1 + cw); nb2 = nb0; na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                break;
            case FilterShape::BandPass:
                nb0 = alpha; nb1 = 0; nb2 = -alpha; na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                break;
            case FilterShape::Notch:
                nb0 = 1; nb1 = -2 * cw; nb2 = 1; na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                break;
            case FilterShape::Peak:
                nb0 = 1 + alpha * amp; nb1 = -2 * cw; nb2 = 1 - alpha * amp;
                na0 = 1 + alpha / amp; na1 = -2 * cw; na2 = 1 - alpha / amp;
                break;
            case FilterShape::LowShelf: {
                const float s = 2 * std::sqrt(amp) * alpha;
                nb0 = amp * ((amp + 1) - (amp - 1) * cw + s);
                nb1 = 2 * amp * ((amp - 1) - (amp + 1) * cw);
                nb2 = amp * ((amp + 1) - (amp - 1) * cw - s);
                na0 = (amp + 1) + (amp - 1) * cw + s;
                na1 = -2 * ((amp - 1) + (amp + 1) * cw);
                na2 = (amp + 1) + (amp - 1) * cw - s;
                break;
            }
            case FilterShape::HighShelf: {
                const float s = 2 * std::sqrt(amp) * alpha;
                nb0 = amp * ((amp + 1) + (amp - 1) * cw + s);
                nb1 = -2 * amp * ((amp - 1) + (amp + 1) * cw);
                nb2 = amp * ((amp + 1) + (amp - 1) * cw - s);
                na0 = (amp + 1) - (amp - 1) * cw + s;
                na1 = 2 * ((amp - 1) - (amp + 1) * cw);
                na2 = (amp + 1) - (amp - 1) * cw - s;
                break;
            }
            case FilterShape::AllPass:
            default:
                nb0 = 1 - alpha; nb1 = -2 * cw; nb2 = 1 + alpha; na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha;
                break;
        }
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0;
    }

    float process(float x) {
        const float y = b0 * x + z1;
        z1 = undenormal(b1 * x - a1 * y + z2);
        z2 = undenormal(b2 * x - a2 * y);
        return y;
    }

    void reset() { z1 = z2 = 0.0f; }

    // the filter's gain at [hz], for drawing response curves
    float magnitude(double rate, float hz) const {
        const double w = 2.0 * 3.14159265358979 * hz / rate;
        const double cr = std::cos(w), ci = -std::sin(w), c2r = std::cos(2 * w), c2i = -std::sin(2 * w);
        const double nr = b0 + b1 * cr + b2 * c2r, ni = b1 * ci + b2 * c2i;
        const double dr = 1 + a1 * cr + a2 * c2r, di = a1 * ci + a2 * c2i;
        return static_cast<float>(std::sqrt((nr * nr + ni * ni) / (dr * dr + di * di)));
    }
};

// a power-of-two ring read at whole or fractional delays
struct DelayLine {
    std::vector<float> buffer;
    uint32_t mask = 0;
    uint32_t pos = 0;

    void prepare(int maxSamples) {
        uint32_t size = 4;
        while (size < static_cast<uint32_t>(maxSamples) + 4) size <<= 1;
        buffer.assign(size, 0.0f);
        mask = size - 1;
        pos = 0;
    }

    void reset() {
        std::fill(buffer.begin(), buffer.end(), 0.0f);
        pos = 0;
    }

    void write(float x) {
        buffer[pos] = x;
        pos = (pos + 1) & mask;
    }

    // the sample written [delay] writes ago; read before the next write and that's exactly [delay] samples back
    float read(int delay) const { return buffer[(pos - static_cast<uint32_t>(delay)) & mask]; }

    // hermite between samples, so a moving delay doesn't buzz
    float readCubic(float delay) const {
        const int whole = static_cast<int>(delay);
        const float frac = delay - static_cast<float>(whole);
        const float xm1 = read(whole - 1 < 1 ? 1 : whole - 1);
        const float x0 = read(whole < 1 ? 1 : whole);
        const float x1 = read(whole + 1);
        const float x2 = read(whole + 2);
        const float c1 = 0.5f * (x1 - xm1);
        const float c2 = xm1 - 2.5f * x0 + 2.0f * x1 - 0.5f * x2;
        const float c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1);
        return ((c3 * frac + c2) * frac + c1) * frac + x0;
    }

    int capacity() const { return static_cast<int>(mask) - 3; }
};

// a free-running sine, phase kept in 0..1 so it never loses precision over a long song
struct Lfo {
    float phase = 0.0f;
    float step = 0.0f;

    void set(double rate, float hz) { step = hz / static_cast<float>(rate); }
    float next() {
        const float v = std::sin(TwoPi * phase);
        phase += step;
        if (phase >= 1.0f) phase -= 1.0f;
        return v;
    }
};

// cheap white noise that's the same on every run, for reverb modulation and dither
struct Noise {
    uint32_t state = 0x9e3779b9u;
    float next() {
        state ^= state << 13;
        state ^= state >> 17;
        state ^= state << 5;
        return static_cast<float>(static_cast<int32_t>(state)) * (1.0f / 2147483648.0f);
    }
};

}  // namespace fx
