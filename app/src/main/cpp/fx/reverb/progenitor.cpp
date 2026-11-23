/*
  Progenitor 2 reverb, from sndfilter by Sean Connelly (@voidqk), MIT License,
  with James34602's changes from JamesDSP. The full notice is in progenitor.h.

  Modified for Mochy (2026): ported to c++ with rate-sized buffers.
*/
#include "progenitor.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace fx::progenitor {

namespace {

constexpr float PiF = 3.14159265358979323846f;

float db2lin(float db) { return std::pow(10.0f, 0.05f * db); }

float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

bool isPrime(int v) {
    if (v < 0) return isPrime(-v);
    if (v < 2) return false;
    if (v <= 3) return true;
    if (v % 2 == 0 || v % 3 == 0) return false;
    const int max = static_cast<int>(std::sqrt(static_cast<double>(v)));
    for (int i = 5; i <= max; i += 6) {
        if (v % i == 0 || v % (i + 2) == 0) return false;
    }
    return true;
}

int nextPrime(int v) {
    while (!isPrime(v)) v++;
    return v;
}

}  // namespace

const std::vector<std::pair<const char*, Settings>>& presets() {
    // OSF, ERtoLt, ERWet, Dry, ERFac, ERWdth, Wdth, Wet, Wander, BassB, Spin, InpLP, BasLP, DmpLP, OutLP, RT60, Delay
    static const std::vector<std::pair<const char*, Settings>> list = {
        {"Default", {1, 0.40f, -9.0f, -7, 1.6f, 0.7f, 1.0f, -0, 0.25f, 0.15f, 0.7f, 17000, 500, 7000, 10000, 3.2f, 0.020f}},
        {"Small hall 1", {1, 0.30f, -9.0f, -7, 1.0f, 0.7f, 1.0f, -8, 0.3f, 0.25f, 0.7f, 18000, 600, 9000, 17000, 2.1f, 0.010f}},
        {"Small hall 2", {1, 0.30f, -9.0f, -7, 1.0f, 0.7f, 1.0f, -8, 0.25f, 0.20f, 0.5f, 18000, 600, 7000, 9000, 2.3f, 0.010f}},
        {"Medium hall 1", {1, 0.30f, -9.0f, -7, 1.2f, 0.7f, 1.0f, -8, 0.25f, 0.20f, 0.7f, 18000, 500, 8000, 16000, 2.8f, 0.010f}},
        {"Medium hall 2", {1, 0.30f, -9.0f, -7, 1.2f, 0.7f, 1.0f, -8, 0.2f, 0.15f, 0.5f, 18000, 500, 6000, 8000, 2.9f, 0.010f}},
        {"Large hall 1", {1, 0.20f, -9.0f, -7, 1.4f, 0.7f, 1.0f, -8, 0.15f, 0.20f, 1.0f, 18000, 400, 9000, 14000, 3.8f, 0.018f}},
        {"Large hall 2", {1, 0.20f, -9.0f, -7, 1.5f, 0.7f, 1.0f, -8, 0.20f, 0.20f, 0.5f, 18000, 400, 5000, 7000, 4.2f, 0.018f}},
        {"Small room 1", {1, 0.70f, -8.0f, -7, 0.7f, -0.4f, 0.8f, -8, 0.20f, 0.30f, 1.6f, 18000, 1000, 18000, 18000, 0.5f, 0.005f}},
        {"Small room 2", {1, 0.70f, -8.0f, -7, 0.8f, 0.6f, 0.9f, -8, 0.30f, 0.30f, 0.4f, 18000, 300, 10000, 18000, 0.5f, 0.005f}},
        {"Medium room 1", {1, 0.50f, -8.0f, -7, 1.2f, -0.4f, 0.8f, -8, 0.20f, 0.10f, 1.6f, 18000, 1000, 18000, 18000, 0.8f, 0.008f}},
        {"Medium room 2", {1, 0.50f, -8.0f, -7, 1.2f, 0.6f, 0.9f, -8, 0.30f, 0.10f, 0.4f, 18000, 300, 10000, 18000, 1.2f, 0.016f}},
        {"Large room 1", {1, 0.20f, -8.0f, -7, 2.2f, -0.4f, 0.9f, -8, 0.20f, 0.10f, 1.6f, 18000, 1000, 16000, 18000, 1.8f, 0.010f}},
        {"Large room 2", {1, 0.20f, -8.0f, -7, 2.2f, 0.6f, 0.9f, -8, 0.30f, 0.10f, 0.4f, 18000, 500, 9000, 18000, 1.9f, 0.020f}},
        {"Medium early 1", {1, 0.50f, -7.0f, -6, 1.2f, -0.4f, 0.8f, -70, 0.20f, 0.10f, 1.6f, 18000, 1000, 18000, 18000, 0.8f, 0.008f}},
        {"Medium early 2", {1, 0.50f, -7.0f, -6, 1.2f, 0.6f, 0.9f, -70, 0.30f, 0.10f, 0.4f, 18000, 300, 10000, 18000, 1.2f, 0.016f}},
        {"Plate high", {2, 0.00f, -30.0f, -12, 1.0f, 1.0f, 1.0f, -8, 0.20f, 0.10f, 1.6f, 18000, 1000, 16000, 18000, 1.8f, 0.000f}},
        {"Plate low", {2, 0.00f, -30.0f, -12, 1.0f, 1.0f, 1.0f, -8, 0.30f, 0.20f, 0.4f, 18000, 500, 9000, 18000, 1.9f, 0.000f}},
        {"Long reverb 1", {2, 0.10f, -16.0f, -14, 1.0f, 0.1f, 1.0f, -5, 0.35f, 0.05f, 1.0f, 18000, 100, 10000, 18000, 12.0f, 0.000f}},
        {"Long reverb 2", {2, 0.10f, -16.0f, -14, 1.0f, 0.1f, 1.0f, -5, 0.40f, 0.05f, 1.0f, 18000, 100, 9000, 18000, 30.0f, 0.000f}},
    };
    return list;
}

void Reverb::Delay::make(int size) {
    buf.assign(static_cast<size_t>(std::max(1, size)), 0.0f);
    pos = 0;
}

float Reverb::Delay::step(float v) {
    const float out = buf[pos];
    buf[pos] = v;
    pos = (pos + 1) % static_cast<int>(buf.size());
    return out;
}

float Reverb::Delay::get(int offset) const {
    const int size = static_cast<int>(buf.size());
    if (offset > size) return buf[pos];
    if (offset <= 0) offset = 1;
    int p = pos - offset;
    if (p < 0) p += size;
    return buf[p];
}

void Reverb::Iir1::lpf(int rate, float freq) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float t = std::tan(PiF * freq / static_cast<float>(rate));
    b1 = b2 = t / (1.0f + t);
    a2 = (1.0f - t) / (1.0f + t);
    y1 = 0;
}

void Reverb::Iir1::hpf(int rate, float freq) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float t = std::tan(PiF * freq / static_cast<float>(rate));
    b1 = 1.0f / (1.0f + t);
    b2 = -b1;
    a2 = (1.0f - t) / (1.0f + t);
    y1 = 0;
}

void Reverb::Iir1::retuneLpf(int rate, float freq) {
    const float keep = y1;
    lpf(rate, freq);
    y1 = keep;
}

float Reverb::Iir1::step(float v) {
    const float out = v * b1 + y1;
    y1 = out * a2 + v * b2;
    return out;
}

void Reverb::Bq::lpf(int rate, float freq, float bw) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float omega = 2.0f * PiF * freq / static_cast<float>(rate);
    const float cs = std::cos(omega), sn = std::sin(omega);
    const float alpha = sn * std::sinh(0.693147180559945f * 0.5f * bw * omega / sn);
    const float a0inv = 1.0f / (1.0f + alpha);
    b0 = a0inv * (1.0f - cs) * 0.5f;
    b1 = 2.0f * b0;
    b2 = b0;
    a1 = a0inv * -2.0f * cs;
    a2 = a0inv * (1.0f - alpha);
    clear();
}

void Reverb::Bq::lpfq(int rate, float freq, float bw) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float omega = 2.0f * PiF * freq / static_cast<float>(rate);
    const float cs = std::cos(omega);
    const float alpha = std::sin(omega) * 2.0f * bw;
    const float a0inv = 1.0f / (1.0f + alpha);
    b0 = a0inv * (1.0f - cs) * 0.5f;
    b1 = 2.0f * b0;
    b2 = b0;
    a1 = a0inv * -2.0f * cs;
    a2 = a0inv * (1.0f - alpha);
    clear();
}

void Reverb::Bq::apf(int rate, float freq, float bw) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float omega = 2.0f * PiF * freq / static_cast<float>(rate);
    const float sn = std::sin(omega);
    const float alpha = sn * std::sinh(0.693147180559945f * 0.5f * bw * omega / sn);
    const float a0inv = 1.0f / (1.0f + alpha);
    b0 = a0inv * (1.0f - alpha);
    b1 = a0inv * -2.0f * std::cos(omega);
    b2 = a0inv * (1.0f + alpha);
    a1 = b1;
    a2 = b0;
    clear();
}

void Reverb::Bq::retuneLpf(int rate, float freq, float bw) {
    const float k1 = xn1, k2 = xn2, k3 = yn1, k4 = yn2;
    lpf(rate, freq, bw);
    xn1 = k1;
    xn2 = k2;
    yn1 = k3;
    yn2 = k4;
}

float Reverb::Bq::step(float v) {
    const float out = v * b0 + xn1 * b1 + xn2 * b2 - yn1 * a1 - yn2 * a2;
    xn2 = xn1;
    xn1 = v;
    yn2 = yn1;
    yn1 = out;
    return out;
}

void Reverb::Early::make(int rate, float factor, float width) {
    static const float delL[18] = {0.0043f, 0.0215f, 0.0225f, 0.0268f, 0.0270f, 0.0298f, 0.0458f, 0.0485f, 0.0572f,
                                   0.0587f, 0.0595f, 0.0612f, 0.0707f, 0.0708f, 0.0726f, 0.0741f, 0.0753f, 0.0797f};
    static const float delR[18] = {0.0053f, 0.0225f, 0.0235f, 0.0278f, 0.0290f, 0.0288f, 0.0468f, 0.0475f, 0.0582f,
                                   0.0577f, 0.0575f, 0.0622f, 0.0697f, 0.0718f, 0.0736f, 0.0751f, 0.0763f, 0.0817f};
    wet1 = width * 0.5f + 0.5f;
    wet2 = (1.0f - width) * 0.5f;
    const int lrDelay = static_cast<int>(0.0002f * static_cast<float>(rate));
    rl.make(lrDelay);
    lr.make(lrDelay);
    apXL.apf(rate, 740.0f, 4.0f);
    apXR = apXL;
    apL.apf(rate, 150.0f, 4.0f);
    apR = apL;
    factor *= static_cast<float>(rate);
    for (int i = 0; i < 18; i++) {
        tblL[i] = static_cast<int>(delL[i] * factor);
        tblR[i] = static_cast<int>(delR[i] * factor);
    }
    pwL.make(tblL[17] + 10);
    pwR.make(tblR[17] + 10);
    lpL.lpf(rate, 20000.0f);
    lpR = lpL;
    hpL.hpf(rate, 4.0f);
    hpR = hpL;
}

void Reverb::Early::step(float inL, float inR, float* outL, float* outR) {
    static const float gainL[18] = {0.841f, 0.504f, 0.491f, 0.379f, 0.380f, 0.346f, 0.289f, 0.272f, 0.192f,
                                    0.193f, 0.217f, 0.181f, 0.180f, 0.181f, 0.176f, 0.142f, 0.167f, 0.134f};
    static const float gainR[18] = {0.842f, 0.506f, 0.489f, 0.382f, 0.300f, 0.346f, 0.290f, 0.271f, 0.193f,
                                    0.192f, 0.217f, 0.195f, 0.192f, 0.166f, 0.186f, 0.131f, 0.168f, 0.133f};
    float wetL = 0, wetR = 0;
    pwL.step(inL);
    pwR.step(inR);
    for (int i = 0; i < 18; i++) {
        wetL += gainL[i] * pwL.get(tblL[i]);
        wetR += gainR[i] * pwR.get(tblR[i]);
    }
    float l = rl.step(inR + wetR);
    l = apXL.step(l);
    l = apL.step(wet1 * wetL + wet2 * l);
    l = lpL.step(hpL.step(l));
    float r = lr.step(inL + wetL);
    r = apXR.step(r);
    r = apR.step(wet1 * wetR + wet2 * r);
    r = lpR.step(hpR.step(r));
    *outL = l;
    *outR = r;
}

void Reverb::Oversample::make(int f) {
    factor = std::max(1, std::min(2, f));
    up.lpfq(2 * factor, 1.0f, 0.5773502691896258f);
    down = up;
}

void Reverb::Oversample::stepUp(float in, float* out) {
    if (factor == 1) {
        out[0] = in;
        return;
    }
    out[0] = up.step(in * static_cast<float>(factor));
    for (int i = 1; i < factor; i++) out[i] = up.step(0);
}

float Reverb::Oversample::stepDown(float* in) {
    if (factor == 1) return in[0];
    for (int i = 0; i < factor; i++) down.step(in[i]);
    return in[0];
}

void Reverb::DcCut::make(int rate, float freq) {
    freq = clampf(freq, 0, static_cast<float>(rate) * 0.5f);
    const float ang = 2.0f * PiF * freq / static_cast<float>(rate);
    const float sn = std::sin(ang);
    const float sqrt3 = 1.7320508075688772f;
    gain = (sqrt3 - 2.0f * sn) / (sn + sqrt3 * std::cos(ang));
    y1 = y2 = 0;
}

float Reverb::DcCut::step(float v) {
    const float out = v - y1 + gain * y2;
    y1 = v;
    y2 = out;
    return out;
}

void Reverb::FractalNoise::make() {
    buf.assign(1 << 11, 0.0f);
    pos = static_cast<int>(buf.size());
}

float Reverb::FractalNoise::random() {
    const unsigned m = 0x5bd1e995u;
    const unsigned k = counter++ * m;
    seed = (k ^ (k >> 24) ^ (seed * m)) * m;
    const unsigned bits = (seed ^ (seed >> 13)) & 0x007FFFFFu;
    union {
        unsigned i;
        float f;
    } u;
    u.i = 0x3F800000u | bits;
    return u.f - 1.0f;
}

float Reverb::FractalNoise::step() {
    const int size = static_cast<int>(buf.size());
    if (pos >= size) {
        // midpoint displacement, so the drift wanders instead of hissing
        pos = 0;
        int len = size, tot = 1;
        float r = 0.8f;
        buf[0] = 0;
        while (len > 1) {
            float left = 0;
            for (int i = tot - 1; i >= 0; i--) {
                const float right = left;
                left = buf[i * len];
                const float mid = (left + right) * 0.5f;
                buf[i * len + len / 2] = clampf(mid + r * (2.0f * random() - 1.0f), -1.0f, 1.0f);
            }
            len /= 2;
            tot *= 2;
            r *= 0.7071067811865475f;
        }
    }
    return buf[pos++];
}

void Reverb::Osc::make(int rate, float freq) {
    count = 0;
    re = 1.0f;
    im = 0.0f;
    const float theta = 2.0f * PiF * freq / static_cast<float>(rate);
    sn = std::sin(theta);
    co = std::cos(theta);
}

void Reverb::Osc::retune(int rate, float freq) {
    const float theta = 2.0f * PiF * freq / static_cast<float>(rate);
    sn = std::sin(theta);
    co = std::cos(theta);
}

float Reverb::Osc::step() {
    const float v = im;
    float nre = re * co - im * sn;
    float nim = re * sn + im * co;
    if (count++ > 100000) {
        // a long run of rotations drifts off the unit circle, so pull it back now and then
        count = 0;
        const float inv = 1.0f / std::sqrt(nre * nre + nim * nim);
        nre *= inv;
        nim *= inv;
    }
    re = nre;
    im = nim;
    return v;
}

void Reverb::Allpass::make(int size, float fb, float dc) {
    buf.assign(static_cast<size_t>(std::max(1, size)), 0.0f);
    pos = 0;
    feedback = fb;
    decay = dc;
}

float Reverb::Allpass::step(float v) {
    v += feedback * buf[pos];
    const float out = decay * buf[pos] - feedback * v;
    buf[pos] = v;
    pos = (pos + 1) % static_cast<int>(buf.size());
    return out;
}

void Reverb::Allpass2::make(int size1, int size2, float f1, float f2, float d1, float d2) {
    buf1.assign(static_cast<size_t>(std::max(1, size1)), 0.0f);
    buf2.assign(static_cast<size_t>(std::max(1, size2)), 0.0f);
    pos1 = pos2 = 0;
    fb1 = f1;
    fb2 = f2;
    dc1 = d1;
    dc2 = d2;
}

float Reverb::Allpass2::step(float v) {
    v += fb2 * buf2[pos2];
    const float out = dc2 * buf2[pos2] - v * fb2;
    v += fb1 * buf1[pos1];
    buf2[pos2] = dc1 * buf1[pos1] - v * fb1;
    buf1[pos1] = v;
    pos1 = (pos1 + 1) % static_cast<int>(buf1.size());
    pos2 = (pos2 + 1) % static_cast<int>(buf2.size());
    return out;
}

float Reverb::Allpass2::get1(int offset) const {
    const int size = static_cast<int>(buf1.size());
    if (offset > size) return buf1[pos1];
    if (offset <= 0) offset = 1;
    int p = pos1 - offset;
    if (p < 0) p += size;
    return buf1[p];
}

float Reverb::Allpass2::get2(int offset) const {
    const int size = static_cast<int>(buf2.size());
    if (offset > size) return buf2[pos2];
    if (offset <= 0) offset = 1;
    int p = pos2 - offset;
    if (p < 0) p += size;
    return buf2[p];
}

void Reverb::Allpass3::make(int size1, int msize, int size2, int size3, float f1, float f2, float f3, float d1, float d2, float d3) {
    size1 = std::max(1, size1);
    msize = std::max(1, std::min(msize, size1));
    const int total = size1 + msize;
    rpos1 = (msize * 2) % total;
    wpos1 = pos2 = pos3 = 0;
    msize1 = msize;
    buf1.assign(static_cast<size_t>(total), 0.0f);
    buf2.assign(static_cast<size_t>(std::max(1, size2)), 0.0f);
    buf3.assign(static_cast<size_t>(std::max(1, size3)), 0.0f);
    fb1 = f1;
    fb2 = f2;
    fb3 = f3;
    dc1 = d1;
    dc2 = d2;
    dc3 = d3;
}

float Reverb::Allpass3::step(float v, float mod) {
    const int size1 = static_cast<int>(buf1.size());
    mod = (mod + 1.0f) * static_cast<float>(msize1);
    const float floorMod = std::floor(mod);
    const float frac = mod - floorMod;
    int r1 = rpos1 - static_cast<int>(floorMod);
    if (r1 < 0) r1 += size1;
    int r2 = r1 - 1;
    if (r2 < 0) r2 += size1;
    v += fb3 * buf3[pos3];
    const float out = dc3 * buf3[pos3] - fb3 * v;
    v += fb2 * buf2[pos2];
    buf3[pos3] = dc2 * buf2[pos2] - fb2 * v;
    const float tmp = buf1[r2] * frac + buf1[r1] * (1.0f - frac);
    v += fb1 * tmp;
    buf2[pos2] = dc1 * tmp - fb1 * v;
    buf1[wpos1] = v;
    wpos1 = (wpos1 + 1) % size1;
    rpos1 = (rpos1 + 1) % size1;
    pos2 = (pos2 + 1) % static_cast<int>(buf2.size());
    pos3 = (pos3 + 1) % static_cast<int>(buf3.size());
    return out;
}

float Reverb::Allpass3::get1(int offset) const {
    const int size = static_cast<int>(buf1.size());
    if (offset > size) return buf1[rpos1];
    if (offset <= 0) offset = 1;
    int p = rpos1 - offset;
    if (p < 0) p += size;
    return buf1[p];
}

float Reverb::Allpass3::get2(int offset) const {
    const int size = static_cast<int>(buf2.size());
    if (offset > size) return buf2[pos2];
    if (offset <= 0) offset = 1;
    int p = pos2 - offset;
    if (p < 0) p += size;
    return buf2[p];
}

float Reverb::Allpass3::get3(int offset) const {
    const int size = static_cast<int>(buf3.size());
    if (offset > size) return buf3[pos3];
    if (offset <= 0) offset = 1;
    int p = pos3 - offset;
    if (p < 0) p += size;
    return buf3[p];
}

void Reverb::AllpassM::make(int size, int ms, float fb, float dc) {
    size = std::max(1, size);
    ms = std::max(1, std::min(ms, size));
    const int total = size + ms;
    rpos = (ms * 2) % total;
    wpos = 0;
    msize = ms;
    feedback = fb;
    decay = dc;
    z1 = 0;
    buf.assign(static_cast<size_t>(total), 0.0f);
}

float Reverb::AllpassM::step(float v, float mod, float fbmod) {
    const int size = static_cast<int>(buf.size());
    const float mfb = feedback + fbmod;
    mod = (mod + 1.0f) * static_cast<float>(msize);
    const float floorMod = std::floor(mod);
    const float frac = 1.0f - mod + floorMod;
    int r1 = rpos - static_cast<int>(floorMod);
    if (r1 < 0) r1 += size;
    int r2 = r1 - 1;
    if (r2 < 0) r2 += size;
    z1 = buf[r2] + frac * (buf[r1] - z1);
    rpos = (rpos + 1) % size;
    buf[wpos] = v + z1 * mfb;
    v = decay * z1 - buf[wpos] * mfb;
    wpos = (wpos + 1) % size;
    return v;
}

void Reverb::Comb::make(int size) {
    buf.assign(static_cast<size_t>(std::max(1, size)), 0.0f);
    pos = 0;
}

float Reverb::Comb::step(float v, float feedback) {
    v = buf[pos] * feedback + v;
    buf[pos] = v;
    pos = (pos + 1) % static_cast<int>(buf.size());
    return v;
}

void Reverb::configure(int rate, const Settings& s) {
    erToLate_ = s.erToLate;
    erWet_ = db2lin(s.erWetDb);
    dry_ = db2lin(s.dryDb);
    const float wet = db2lin(s.wetDb);
    wet1_ = wet * (s.width * 0.5f + 0.5f);
    wet2_ = wet * ((1.0f - s.width) * 0.5f);
    wander_ = s.wander;
    bassB_ = s.bassBoost;
    early_.make(rate, s.erFactor, s.erWidth);
    osL_.make(s.oversample);
    osR_ = osL_;
    const int osRate = rate * osL_.factor;
    dcL_.make(osRate, 5.0f);
    dcR_ = dcL_;
    noise_.make();
    lfo1_.make(osRate, s.spin);
    lfo1Lpf_.lpf(osRate, 20.0f);
    lfo2_.make(osRate, std::sqrt(std::max(0.0f, 100.0f - (10.0f - s.spin) * (10.0f - s.spin))) * 0.5f);
    lfo2Lpf_.lpf(osRate, 12.0f);
    static const int diffLc[10] = {617, 535, 434, 347, 218, 162, 144, 122, 109, 74};
    static const int diffRc[10] = {603, 547, 416, 364, 236, 162, 140, 131, 111, 79};
    // the original's lengths were tuned at 34125 hz; below that its whole-number factor rounded to zero
    const int f = std::max(1, osRate / 34125);
    int msize = nextPrime(std::max(1, 10 * osRate / 34125));
    for (int i = 0; i < 10; i++) {
        diffL_[i].make(nextPrime(diffLc[i] * f), msize, -0.78f, 1);
        diffR_[i].make(nextPrime(diffRc[i] * f), msize, -0.78f, 1);
    }
    static const int crossLc[4] = {430, 341, 264, 174};
    static const int crossRc[4] = {447, 324, 247, 191};
    for (int i = 0; i < 4; i++) {
        crossL_[i].make(nextPrime(crossLc[i] * f), 0.78f, 1);
        crossR_[i].make(nextPrime(crossRc[i] * f), 0.78f, 1);
    }
    clpfL_.lpf(osRate, s.inputLpf);
    clpfR_ = clpfL_;
    cdelayL_.make(nextPrime(1572 * f));
    cdelayR_.make(nextPrime(16 * f));
    dampDL_.make(nextPrime(2 * f));
    dampDR_.make(nextPrime(f));
    cbassD1L_.make(nextPrime(1055 * f));
    cbassD1R_.make(nextPrime(1460 * f));
    cbassD2L_.make(nextPrime(344 * f));
    cbassD2R_.make(nextPrime(500 * f));
    bassApL_.apf(osRate, 150.0f, 4.0f);
    bassApR_ = bassApL_;
    bassLpL_.lpf(osRate, s.bassLpf, 2.0f);
    bassLpR_ = bassLpL_;
    dampLpL_.lpf(osRate, s.dampLpf);
    dampLpR_ = dampLpL_;
    const float rt60 = std::max(0.05f, s.rt60);
    const float decay0 = std::pow(10.0f, std::log10(0.237f) / rt60);
    const float decay1 = std::pow(10.0f, std::log10(0.938f) / rt60);
    const float decay2 = std::pow(10.0f, std::log10(0.844f) / rt60);
    const float decay3 = std::pow(10.0f, std::log10(0.906f) / rt60);
    loopDecay_ = decay0;
    msize = nextPrime(32 * f);
    dampAp1L_.make(nextPrime(239 * f), msize, 0.375f, decay2);
    dampAp1R_.make(nextPrime(205 * f), msize, 0.375f, decay2);
    dampAp2L_.make(nextPrime(392 * f), msize, 0.312f, decay3);
    dampAp2R_.make(nextPrime(329 * f), msize, 0.312f, decay3);
    cbassAp1L_.make(nextPrime(1944 * f), nextPrime(612 * f), 0.250f, 0.406f, decay1, decay2);
    cbassAp1R_.make(nextPrime(2032 * f), nextPrime(368 * f), 0.250f, 0.406f, decay1, decay2);
    cbassAp2L_.make(nextPrime(1212 * f), nextPrime(121 * f), nextPrime(816 * f), nextPrime(1264 * f), 0.250f, 0.250f, 0.406f, decay1, decay1, decay2);
    cbassAp2R_.make(nextPrime(1452 * f), nextPrime(5 * f), nextPrime(688 * f), nextPrime(1340 * f), 0.250f, 0.250f, 0.406f, decay1, decay1, decay2);
    static const int outco[32] = {1, 40, 192, 276, 321, 110, 468, 1572, 121, 480, 103, 26, 780, 1200, 310, 780,
                                  625, 468, 312, 24, 36, 790, 189, 8, 10, 359, 30, 10, 109, 1310, 800, 10};
    for (int i = 0; i < 32; i++) outco_[i] = outco[i] * f;
    combL_.make(nextPrime(22 * osRate / 1000));
    combR_ = combL_;
    lastLpfL_.lpf(osRate, s.outputLpf, 1.0f);
    lastLpfR_ = lastLpfL_;
    const int delaySamples = static_cast<int>(static_cast<float>(osRate) * s.delay);
    if (delaySamples >= 0) {
        inDelayL_.make(0);
        inDelayR_.make(0);
        lastDelayL_.make(delaySamples);
        lastDelayR_.make(delaySamples);
    } else {
        inDelayL_.make(-delaySamples);
        inDelayR_.make(-delaySamples);
        lastDelayL_.make(0);
        lastDelayR_.make(0);
    }
}

void Reverb::retune(int rate, const Settings& s) {
    erToLate_ = s.erToLate;
    erWet_ = db2lin(s.erWetDb);
    dry_ = db2lin(s.dryDb);
    const float wet = db2lin(s.wetDb);
    wet1_ = wet * (s.width * 0.5f + 0.5f);
    wet2_ = wet * ((1.0f - s.width) * 0.5f);
    wander_ = s.wander;
    bassB_ = s.bassBoost;
    early_.wet1 = s.erWidth * 0.5f + 0.5f;
    early_.wet2 = (1.0f - s.erWidth) * 0.5f;
    const int osRate = rate * osL_.factor;
    lfo1_.retune(osRate, s.spin);
    lfo2_.retune(osRate, std::sqrt(std::max(0.0f, 100.0f - (10.0f - s.spin) * (10.0f - s.spin))) * 0.5f);
    clpfL_.retuneLpf(osRate, s.inputLpf);
    clpfR_.retuneLpf(osRate, s.inputLpf);
    bassLpL_.retuneLpf(osRate, s.bassLpf, 2.0f);
    bassLpR_.retuneLpf(osRate, s.bassLpf, 2.0f);
    dampLpL_.retuneLpf(osRate, s.dampLpf);
    dampLpR_.retuneLpf(osRate, s.dampLpf);
    lastLpfL_.retuneLpf(osRate, s.outputLpf, 1.0f);
    lastLpfR_.retuneLpf(osRate, s.outputLpf, 1.0f);
    const float rt60 = std::max(0.05f, s.rt60);
    const float decay1 = std::pow(10.0f, std::log10(0.938f) / rt60);
    const float decay2 = std::pow(10.0f, std::log10(0.844f) / rt60);
    const float decay3 = std::pow(10.0f, std::log10(0.906f) / rt60);
    loopDecay_ = std::pow(10.0f, std::log10(0.237f) / rt60);
    dampAp1L_.decay = dampAp1R_.decay = decay2;
    dampAp2L_.decay = dampAp2R_.decay = decay3;
    for (Allpass2* a : {&cbassAp1L_, &cbassAp1R_}) {
        a->dc1 = decay1;
        a->dc2 = decay2;
    }
    for (Allpass3* a : {&cbassAp2L_, &cbassAp2R_}) {
        a->dc1 = decay1;
        a->dc2 = decay1;
        a->dc3 = decay2;
    }
}

void Reverb::process(float inL, float inR, float* outputL, float* outputR) {
    const float modNoise1 = 0.09f, modNoise2 = 0.06f, crossfeed = 0.4f;
    float osL[2], osR[2];
    float erL, erR;
    early_.step(inL, inR, &erL, &erR);
    osL_.stepUp(erL * erToLate_ + inL, osL);
    osR_.stepUp(erR * erToLate_ + inR, osR);
    for (int k = 0; k < osL_.factor; k++) {
        float outL = dcL_.step(osL[k]);
        float outR = dcR_.step(osR[k]);
        float mnoise = noise_.step();
        float lfo = (lfo1_.step() + modNoise1 * mnoise) * wander_;
        lfo = lfo1Lpf_.step(lfo);
        mnoise *= modNoise2;
        for (int i = 0, s = -1; i < 10; i++, s = -s) {
            outL = diffL_[i].step(outL, lfo * static_cast<float>(s), mnoise);
            outR = diffR_[i].step(outR, lfo, mnoise * static_cast<float>(s));
        }
        float crossL = outL, crossR = outR;
        for (int i = 0; i < 4; i++) {
            crossL = crossL_[i].step(crossL);
            crossR = crossR_[i].step(crossR);
        }
        outL = clpfL_.step(outL + crossfeed * crossR);
        outR = clpfR_.step(outR + crossfeed * crossL);
        crossL = cdelayL_.last();
        crossR = cdelayR_.last();
        outL += loopDecay_ * (crossR + bassB_ * bassLpL_.step(bassApL_.step(crossR)));
        outR += loopDecay_ * (crossL + bassB_ * bassLpR_.step(bassApR_.step(crossL)));
        outL = dampAp2L_.step(dampDL_.step(dampAp1L_.step(dampLpL_.step(outL), lfo, mnoise)), -lfo, -mnoise);
        outR = dampAp2R_.step(dampDR_.step(dampAp1R_.step(dampLpR_.step(outR), -lfo, -mnoise)), lfo, mnoise);
        cdelayL_.step(cbassAp2L_.step(cbassD2L_.step(cbassAp1L_.step(cbassD1L_.step(outL))), lfo));
        cdelayR_.step(cbassAp2R_.step(cbassD2R_.step(cbassAp1R_.step(cbassD1R_.step(outR))), -lfo));
        const float d1 = cbassD1L_.get(outco_[0]);
        const float d2 = cbassD2L_.get(outco_[1]) - cbassD2R_.get(outco_[2]) + cbassD2L_.get(outco_[3]) - cdelayR_.get(outco_[4]) -
                         cbassD1R_.get(outco_[5]) - cbassD2R_.get(outco_[6]);
        const float d3 = cdelayL_.get(outco_[7]) + cbassAp1L_.get1(outco_[8]) + cbassAp1L_.get2(outco_[9]) - cbassAp1R_.get2(outco_[10]) +
                         cbassAp2L_.get1(outco_[11]) + cbassAp2L_.get2(outco_[12]) + cbassAp2L_.get3(outco_[13]) - cbassAp2R_.get2(outco_[14]);
        const float d4 = cdelayL_.get(outco_[15]);
        const float b1 = cbassD1R_.get(outco_[16]);
        const float b2 = cbassD2R_.get(outco_[17]) - cbassD2L_.get(outco_[18]) + cbassD2R_.get(outco_[19]) - cdelayL_.get(outco_[20]) -
                         cbassD1L_.get(outco_[21]) - cbassD2L_.get(outco_[22]);
        const float b3 = cdelayR_.get(outco_[23]) + cbassAp1R_.get1(outco_[24]) + cbassAp1R_.get2(outco_[25]) - cbassAp1L_.get2(outco_[26]) +
                         cbassAp2R_.get1(outco_[27]) + cbassAp2R_.get2(outco_[28]) + cbassAp2R_.get3(outco_[29]) - cbassAp2L_.get2(outco_[30]);
        const float b4 = cdelayR_.get(outco_[31]);
        const float d = d1 * 0.469f + d2 * 0.219f + d3 * 0.064f + d4 * 0.045f;
        const float b = b1 * 0.469f + b2 * 0.219f + b3 * 0.064f + b4 * 0.045f;
        lfo = lfo2Lpf_.step(lfo2_.step() * wander_);
        outL = combL_.step(d, lfo);
        outR = combR_.step(b, -lfo);
        outL = lastDelayL_.step(lastLpfL_.step(outL));
        outR = lastDelayR_.step(lastLpfR_.step(outR));
        osL[k] = outL * wet1_ + outR * wet2_ + inDelayL_.step(osL[k]) * dry_;
        osR[k] = outR * wet1_ + outL * wet2_ + inDelayR_.step(osR[k]) * dry_;
    }
    *outputL = osL_.stepDown(osL) + erL * erWet_ + inL * dry_;
    *outputR = osR_.stepDown(osR) + erR * erWet_ + inR * dry_;
}

}  // namespace fx::progenitor
