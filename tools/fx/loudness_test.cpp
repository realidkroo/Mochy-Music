#include "loudness.h"
#include <cstdio>
#include <vector>
#include <cmath>
using namespace fx;
static LoudnessMeter::Reading run(int rate, int ch, float dbfs, float hz, float seconds, int silentCh = -1, float phase = 0) {
    LoudnessMeter m; m.configure(rate, ch);
    int n = (int)(rate * seconds);
    std::vector<std::vector<float>> planes(ch, std::vector<float>(n));
    float amp = std::pow(10.f, dbfs / 20.f);
    for (int c = 0; c < ch; c++) for (int i = 0; i < n; i++) planes[c][i] = c == silentCh ? 0 : amp * std::sin(2 * M_PI * hz * i / rate + phase);
    std::vector<float*> ptr(ch); for (int c = 0; c < ch; c++) ptr[c] = planes[c].data();
    for (int i = 0; i < n; i += 1024) { std::vector<float*> p(ch); for (int c = 0; c < ch; c++) p[c] = ptr[c] + i; m.process(p.data(), std::min(1024, n - i)); }
    return m.reading();
}
int main() {
    auto r = run(48000, 2, -20, 997, 20);
    printf("stereo -20 dbfs 997hz: M %.2f S %.2f I %.2f LRA %.2f TP %.2f (expect ~ -20, -20, -20, 0, -20)\n", r.momentary, r.shortTerm, r.integrated, r.range, r.truePeakDb);
    r = run(44100, 2, 0, 997, 10, 1);
    printf("one channel full scale: I %.2f (expect -3.01)\n", r.integrated);
    // a sine at fs/4 with 45 degree phase peaks between samples: sample peak -3 db below its true peak
    r = run(48000, 1, 0, 12000, 2, -1, M_PI / 4);
    printf("fs/4 sine at 45 deg: true peak %.2f dBTP (expect ~0.0, sample peak would read -3.01)\n", r.truePeakDb);
    r = run(48000, 6, -20, 997, 10);
    printf("5.1 all channels -20: I %.2f (expect fl+fr+fc+1.41*2 surround = 10*log10(5.82) - 20 = -12.35)\n", r.integrated);
    // ebu tech 3341 case: -23 lufs then -33 lufs then -23, 20s each: integrated about -23 (gated), short-term stays
    LoudnessMeter m; m.configure(48000, 2); int n = 48000 * 20;
    for (float lvl : {-23.f, -33.f, -23.f}) { std::vector<float> a(n), b(n); float amp = pow(10.f, lvl / 20.f); for (int i = 0; i < n; i++) a[i] = b[i] = amp * sin(2 * M_PI * 1000 * i / 48000.0); float* p[2] = {a.data(), b.data()}; for (int i = 0; i < n; i += 4096) { float* q[2] = {p[0] + i, p[1] + i}; m.process(q, std::min(4096, n - i)); } }
    printf("tech 3341 case 3-ish (-23/-33/-23 at 1 kHz): I %.2f (expect -23.0 +-0.1), LRA %.2f (expect ~10)\n", m.reading().integrated, m.reading().range);
}
