#include "reverb/space.h"
#include "reverb/progenitor.h"
#include <cstdio>
#include <vector>
using namespace fx;
// rt60 from schroeder backward integration, fitting -5 to -25 db (t20 x3)
static double rt60(const std::vector<float>& ir, double rate) {
    std::vector<double> e(ir.size());
    double acc = 0;
    for (int i = (int)ir.size() - 1; i >= 0; i--) { acc += (double)ir[i] * ir[i]; e[i] = acc; }
    double t5 = -1, t25 = -1;
    for (size_t i = 0; i < e.size(); i++) {
        double db = 10 * log10(e[i] / e[0] + 1e-30);
        if (t5 < 0 && db <= -5) t5 = i / rate;
        if (t25 < 0 && db <= -25) { t25 = i / rate; break; }
    }
    return (t25 - t5) * 3.0;
}
int main() {
    const double rate = 48000;
    for (float decay : {0.6f, 1.5f, 3.0f, 6.0f}) {
        SpaceReverb rv;
        SpaceSettings s; s.decaySec = decay; s.bassMul = 1; s.trebleMul = 1; s.preDelayMs = 0; s.mix = 1; s.modDepth = 0.3f; s.lowCutHz=20; s.highCutHz=20000;
        rv.prepare(rate); rv.apply(s, true); rv.reset();
        int n = (int)(rate * (decay * 2.5 + 0.5));
        std::vector<float> L(n, 0), R(n, 0); L[0] = 1; R[0] = 1;
        for (int i = 0; i < n; i += 512) rv.process(&L[i], &R[i], std::min(512, n - i), true);
        double peak = 0; for (float v : L) peak = std::max(peak, (double)fabs(v));
        bool ok = true; for (float v : L) if (!(v == v)) ok = false;
        printf("space decay %.1fs -> measured rt60 %.2fs  peak %.3f finite %d\n", decay, rt60(L, rate), peak, ok);
    }
    {
        SpaceReverb rv; SpaceSettings s; s.freeze = true; s.mix = 1; rv.prepare(rate); rv.apply(s, true);
        std::vector<float> L(48000 * 20, 0), R(48000 * 20, 0); for (int i = 0; i < 4800; i++) { L[i] = R[i] = 0.3f * sinf(i * 0.05f); }
        SpaceSettings live = s; live.freeze = false; rv.apply(live, true);
        rv.process(L.data(), R.data(), 4800, true); rv.apply(s, false);
        for (size_t i = 4800; i < L.size(); i += 512) rv.process(&L[i], &R[i], std::min<size_t>(512, L.size() - i), true);
        double a = 0, b = 0; for (int i = 48000; i < 96000; i++) a += L[i]*L[i]; for (size_t i = L.size()-48000; i < L.size(); i++) b += L[i]*L[i];
        printf("freeze: energy 1s %.4f vs 19s %.4f (should stay close, not grow)\n", a, b);
    }
    for (auto& p : progenitor::presets()) {
        progenitor::Reverb rv; rv.configure(48000, p.second);
        int n = 48000 * 8; std::vector<float> ir(n);
        for (int i = 0; i < n; i++) { float l, r; rv.process(i == 0 ? 1.f : 0.f, i == 0 ? 1.f : 0.f, &l, &r); ir[i] = l; }
        bool ok = true; for (float v : ir) if (!(v == v)) ok = false;
        printf("progenitor %-16s rt60 setting %5.1f finite %d\n", p.first, p.second.rt60, ok);
    }
    for (int rateTest : {32000, 96000, 192000}) {
        progenitor::Reverb rv; rv.configure(rateTest, progenitor::presets()[18].second);
        float l, r; for (int i = 0; i < rateTest; i++) rv.process(i == 0, i == 0, &l, &r);
        printf("progenitor at %d hz ok, last %g\n", rateTest, l);
    }
}
