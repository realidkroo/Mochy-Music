/*
  Progenitor 2 reverb, from sndfilter by Sean Connelly (@voidqk), http://syntheti.cc
  https://github.com/voidqk/sndfilter, with James34602's changes from JamesDSP.

  MIT License

  Copyright (c) 2016 Sean Connelly

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

  Modified for Mochy (2026): ported to c++ with buffers sized from the sample
  rate instead of fixed arrays, so large rooms and high rates no longer clip
  their delay lines; the noise generator keeps its state per reverb.
*/
#pragma once

#include <vector>

namespace fx::progenitor {

// the knobs of the original, in its own units
struct Settings {
    int oversample = 1;
    float erToLate = 0.4f;    // how much of the early reflections feed the tail
    float erWetDb = -9.0f;
    float dryDb = -7.0f;
    float erFactor = 1.6f;    // early reflection room size
    float erWidth = 0.7f;
    float width = 1.0f;
    float wetDb = 0.0f;
    float wander = 0.25f;     // how far the tail's delays drift
    float bassBoost = 0.15f;
    float spin = 0.7f;        // drift speed, hz
    float inputLpf = 17000.0f;
    float bassLpf = 500.0f;
    float dampLpf = 7000.0f;
    float outputLpf = 10000.0f;
    float rt60 = 3.2f;
    float delay = 0.02f;      // seconds the tail waits, negative delays the dry instead
};

// the nineteen rooms the original ships with, in its order
const std::vector<std::pair<const char*, Settings>>& presets();

class Reverb {
public:
    // also how it's emptied: configuring again starts every line silent
    void configure(int rate, const Settings& s);
    // changes everything but the room's dimensions in place, so the tail rings on while a knob moves
    void retune(int rate, const Settings& s);
    // the knobs only configure can change: oversampling, early reflection size and pre-delay
    static bool sameShape(const Settings& a, const Settings& b) {
        return a.oversample == b.oversample && a.erFactor == b.erFactor && a.delay == b.delay;
    }
    void process(float inL, float inR, float* outL, float* outR);

private:
    struct Delay {
        std::vector<float> buf;
        int pos = 0;
        void make(int size);
        float step(float v);
        float get(int offset) const;
        float last() const { return buf[pos]; }
    };
    struct Iir1 {
        float a2 = 0, b1 = 0, b2 = 0, y1 = 0;
        void lpf(int rate, float freq);
        void hpf(int rate, float freq);
        void retuneLpf(int rate, float freq);
        float step(float v);
    };
    struct Bq {
        float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, xn1 = 0, xn2 = 0, yn1 = 0, yn2 = 0;
        void lpf(int rate, float freq, float bw);
        void lpfq(int rate, float freq, float bw);
        void apf(int rate, float freq, float bw);
        void retuneLpf(int rate, float freq, float bw);
        float step(float v);
        void clear() { xn1 = xn2 = yn1 = yn2 = 0; }
    };
    struct Early {
        int tblL[18] = {}, tblR[18] = {};
        Delay pwL, pwR, rl, lr;
        Bq apXL, apXR, apL, apR;
        Iir1 lpL, lpR, hpL, hpR;
        float wet1 = 0, wet2 = 0;
        void make(int rate, float factor, float width);
        void step(float inL, float inR, float* outL, float* outR);
    };
    struct Oversample {
        int factor = 1;
        Bq up, down;
        void make(int f);
        void stepUp(float in, float* out);
        float stepDown(float* in);
    };
    struct DcCut {
        float gain = 0, y1 = 0, y2 = 0;
        void make(int rate, float freq);
        float step(float v);
    };
    struct FractalNoise {
        std::vector<float> buf;
        int pos = 0;
        unsigned seed = 123, counter = 456;
        void make();
        float step();
        float random();
    };
    struct Osc {
        float re = 1, im = 0, sn = 0, co = 1;
        int count = 0;
        void make(int rate, float freq);
        void retune(int rate, float freq);
        float step();
    };
    struct Allpass {
        std::vector<float> buf;
        int pos = 0;
        float feedback = 0, decay = 0;
        void make(int size, float fb, float dc);
        float step(float v);
    };
    struct Allpass2 {
        std::vector<float> buf1, buf2;
        int pos1 = 0, pos2 = 0;
        float fb1 = 0, fb2 = 0, dc1 = 0, dc2 = 0;
        void make(int size1, int size2, float f1, float f2, float d1, float d2);
        float step(float v);
        float get1(int offset) const;
        float get2(int offset) const;
    };
    struct Allpass3 {
        std::vector<float> buf1, buf2, buf3;
        int rpos1 = 0, wpos1 = 0, pos2 = 0, pos3 = 0, msize1 = 1;
        float fb1 = 0, fb2 = 0, fb3 = 0, dc1 = 0, dc2 = 0, dc3 = 0;
        void make(int size1, int msize, int size2, int size3, float f1, float f2, float f3, float d1, float d2, float d3);
        float step(float v, float mod);
        float get1(int offset) const;
        float get2(int offset) const;
        float get3(int offset) const;
    };
    struct AllpassM {
        std::vector<float> buf;
        int rpos = 0, wpos = 0, msize = 1;
        float feedback = 0, decay = 0, z1 = 0;
        void make(int size, int msize, float fb, float dc);
        float step(float v, float mod, float fbmod);
    };
    struct Comb {
        std::vector<float> buf;
        int pos = 0;
        void make(int size);
        float step(float v, float feedback);
    };

    Early early_;
    Oversample osL_, osR_;
    DcCut dcL_, dcR_;
    FractalNoise noise_;
    Osc lfo1_, lfo2_;
    Iir1 lfo1Lpf_, lfo2Lpf_;
    AllpassM diffL_[10], diffR_[10];
    Allpass crossL_[4], crossR_[4];
    Iir1 clpfL_, clpfR_;
    Delay cdelayL_, cdelayR_;
    Bq bassApL_, bassApR_, bassLpL_, bassLpR_;
    Iir1 dampLpL_, dampLpR_;
    AllpassM dampAp1L_, dampAp1R_, dampAp2L_, dampAp2R_;
    Delay dampDL_, dampDR_;
    Delay cbassD1L_, cbassD1R_, cbassD2L_, cbassD2R_;
    Allpass2 cbassAp1L_, cbassAp1R_;
    Allpass3 cbassAp2L_, cbassAp2R_;
    Comb combL_, combR_;
    Bq lastLpfL_, lastLpfR_;
    Delay lastDelayL_, lastDelayR_, inDelayL_, inDelayR_;
    int outco_[32] = {};
    float loopDecay_ = 0, wet1_ = 0, wet2_ = 0, wander_ = 0, bassB_ = 0, erToLate_ = 0, erWet_ = 0, dry_ = 0;
};

}  // namespace fx::progenitor
