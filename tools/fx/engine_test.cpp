#include "engine.h"
#include "model.h"
#include "plugins/reverbs.h"
#include <cstdio>
#include <thread>
#include <vector>
#include <cmath>
using namespace fx;
static void settle() { std::this_thread::sleep_for(std::chrono::milliseconds(400)); }
static std::vector<int16_t> sine(int frames, int ch, float a = 0.3f) {
    std::vector<int16_t> v(frames * ch);
    for (int i = 0; i < frames; i++) for (int c = 0; c < ch; c++) v[i * ch + c] = (int16_t)lrintf(32767 * a * sinf(i * (0.031f + 0.017f * c)));
    return v;
}
static double maxDiff(const std::vector<int16_t>& a, const std::vector<int16_t>& b, int skip = 0) {
    double m = 0; for (size_t i = skip; i < a.size(); i++) m = std::max(m, (double)abs(a[i] - b[i])); return m;
}
static double rms(const std::vector<int16_t>& a, int skip = 0) { double e = 0; for (size_t i = skip; i < a.size(); i++) e += (double)a[i] * a[i]; return sqrt(e / (a.size() - skip)); }
static void plainMix(bool enabled) {
    Model& m = Model::get(); m.begin();
    m.addBus(0, true, FocusAll, 1u << Master, false); m.addBus(0, false, 0, 0, true); m.commit(enabled);
    m.send[0][Master] = 1; m.bus[0].gainDb = 0; m.bus[Master].gainDb = 0;
}
int main() {
    const int rate = 48000, ch = 2, n = 48000;
    Model& m = Model::get();
    plainMix(true);
    Engine e(rate, ch); settle();
    auto in = sine(n, ch), out = in;
    bool changed = e.process(in.data(), out.data(), n);
    printf("1 transparent: changed=%d maxdiff=%g (expect 0, 0)\n", changed, maxDiff(in, out));

    // mid and side on two buses add back to the song
    m.begin(); m.addBus(0, true, FocusMid, 1u << Master, false); m.addBus(1, true, FocusSide, 1u << Master, false); m.addBus(0, false, 0, 0, true); m.commit(true);
    m.send[1][Master] = 1; settle();
    e.process(in.data(), out.data(), n);
    printf("2 mid+side buses rebuild the song: maxdiff=%g lsb (expect <= 2, dither)\n", maxDiff(in, out, 2000));

    // a send doubles the song
    m.begin(); m.addBus(0, true, FocusAll, (1u << Master) | (1u << 1), false); m.addBus(1, false, FocusAll, 1u << Master, false); m.addBus(0, false, 0, 0, true); m.commit(true);
    m.send[0][1] = 1; settle();
    e.process(in.data(), out.data(), n);
    printf("3 send: rms ratio %.3f (expect 2.000)\n", rms(out, 4000) / rms(in, 4000));

    // a limiter bus (72 samples late) and a dry bus meet in step: no comb filtering, just twice the level
    m.send[0][1] = 0;
    std::vector<float> lim = {0, 0, 80};
    m.begin(); m.addBus(0, true, FocusAll, 1u << Master, false); m.addBus(1, true, FocusAll, 1u << Master, false); m.addSlot(0, 101, "mochy.limiter", "", {}, 0, lim.data(), 3); m.addBus(0, false, 0, 0, true); m.commit(true);
    settle();
    auto quiet = sine(n, ch, 0.2f); out = quiet;
    e.process(quiet.data(), out.data(), n);
    std::vector<int16_t> shifted(quiet.size(), 0); for (int i = 72; i < n; i++) for (int c = 0; c < ch; c++) shifted[i * ch + c] = (int16_t)std::clamp(2 * quiet[(i - 72) * ch + c], -32768, 32767);
    for (int i = 4000; i < n; i += 4000) printf("   i=%d out=%d expect=%d in=%d\n", i, out[i*ch], shifted[i*ch], quiet[i*ch]);
    { int best = 0; double bd = 1e18; for (int d = 0; d < 300; d++) { double e = 0; for (int i = 4000; i < n - 300; i++) { double x = out[i*ch] - 2.0 * quiet[(i - d)*ch]; e += x*x; } if (e < bd) { bd = e; best = d; } } printf("   best matching delay %d (rms err %.1f)\n", best, sqrt(bd / (n - 4300))); }
    printf("4 delay compensation: maxdiff vs 2x input delayed 72 = %g lsb (expect small)\n", maxDiff(shifted, out, 4000));

    // liveprog in a bus
    std::vector<float> db = {-6};
    m.begin(); m.addBus(0, true, FocusAll, 1u << Master, false); m.addSlot(0, 202, "mochy.liveprog", "@init\ndB=0;\ng=exp(dB*0.1151292546);\n@sample\nspl0*=g;spl1*=g;\n", {"dB"}, 0, db.data(), 1); m.addBus(0, false, 0, 0, true); m.commit(true);
    m.send[1][Master] = 0; settle();
    e.process(in.data(), out.data(), n);
    printf("5 liveprog -6 dB: rms ratio %.4f (expect 0.5012)\n", rms(out, 4000) / rms(in, 4000));
    auto problem = m.control(202)->takeProblem(); printf("   problem='%s'\n", problem.c_str());

    // reverb keeps ringing across a commit that only adds a bus
    std::vector<float> rv = SpacePlugin::info().defaults(); rv[16] = 100; rv[1] = 3;
    m.begin(); m.addBus(0, true, FocusAll, 1u << Master, false); m.addSlot(0, 303, "mochy.space", "", {}, 0, rv.data(), (int)rv.size()); m.addBus(0, false, 0, 0, true); m.commit(true);
    settle();
    std::vector<int16_t> burst(n * ch, 0); for (int i = 0; i < 2000; i++) for (int c = 0; c < ch; c++) burst[i * ch + c] = 10000 * sinf(i * 0.05f);
    out = burst; e.process(burst.data(), out.data(), n / 2);
    double before = rms(std::vector<int16_t>(out.begin() + (n / 2 - 2000) * ch, out.begin() + (n / 2) * ch));
    m.begin(); m.addBus(0, true, FocusAll, 1u << Master, false); m.addSlot(0, 303, "mochy.space", "", {}, 0, rv.data(), (int)rv.size()); m.addBus(1, false, FocusAll, 1u << Master, false); m.addBus(0, false, 0, 0, true); m.commit(true);
    settle();
    std::vector<int16_t> silence(4000 * ch, 0), tail = silence; e.process(silence.data(), tail.data(), 4000);
    printf("6 tail across a commit: before %.1f, after %.1f (after should be close, not 0)\n", before, rms(tail));
    // mono and 5.1 run without trouble
    for (int channels : {1, 6, 8, 12}) { Engine e2(rate, channels); settle(); auto s = sine(n, channels), o = s; e2.process(s.data(), o.data(), n); bool fin = true; double r = rms(o); printf("7 %d channels with reverb: rms %.0f\n", channels, r); }
    plainMix(false);
    printf("done\n");
}
