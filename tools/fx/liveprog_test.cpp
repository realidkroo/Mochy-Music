#include "liveprog.h"
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <vector>
#include <cmath>
#include <chrono>
using namespace fx;
static std::string read(const char* p) { std::ifstream f(p); std::stringstream s; s << f.rdbuf(); return s.str(); }
int main(int argc, char** argv) {
    int ok = 0, bad = 0;
    for (int a = 1; a < argc; a++) {
        std::string src = read(argv[a]); std::string err;
        auto report = std::make_shared<ScriptReport>();
        auto t0 = std::chrono::steady_clock::now();
        auto p = LiveprogPlugin::compile(src, {}, {}, 48000, report, &err);
        double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        const char* slash = strrchr(argv[a], '/');
        const char* name = slash ? slash + 1 : argv[a];
        if (!p) { printf("%-45s FAILED %s\n", name, err.c_str()); bad++; continue; }
        p->prepare(48000, 1024);
        std::vector<float> l(48000), r(48000); for (int i = 0; i < 48000; i++) { l[i] = 0.3f * sinf(i * 0.0571f); r[i] = 0.3f * sinf(i * 0.0391f); }
        auto t1 = std::chrono::steady_clock::now();
        for (int i = 0; i < 48000; i += 1024) p->process(&l[i], &r[i], std::min(1024, 48000 - i));
        double run = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t1).count();
        double e = 0; for (float v : l) e += v * v;
        std::string con = report->takeConsole(); for (auto& c : con) if (c == '\n') c = ' ';
        printf("%-45s ok  compile %6.1f ms  1s audio in %7.1f ms  rms %.3f  %s%s\n", name, ms, run, sqrt(e / 48000), report->problem.c_str(), con.substr(0, 40).c_str());
        ok++;
    }
    printf("built %d, failed %d\n", ok, bad);
    printf("check(empty) = '%s'\n", checkScript("@init\nx=1;\n").c_str());
    printf("check(syntax) = '%s'\n", checkScript("@sample\nspl0 = spl0 * ;\n").c_str());
    printf("check(sandboxed ls) = '%s'\n", checkScript("@sample\nls(\"\");\n").c_str());
    // knobs: jamesdsp style default rewrite
    auto rep = std::make_shared<ScriptReport>(); std::string err;
    auto g = LiveprogPlugin::compile("@init\ndB = -8;\ng = exp(dB * 0.1151292546);\n@sample\nspl0 *= g; spl1 *= g;\n", {"dB"}, {-20}, 48000, rep, &err);
    float L = 1, R = 1; g->process(&L, &R, 1); printf("gain knob -20 dB -> %.4f (expect 0.1)\n", L);
    auto s = LiveprogPlugin::compile("@init\ng=1;\n@slider\ng = exp(dB * 0.1151292546);\n@sample\nspl0 *= g; spl1 *= g;\n", {"dB"}, {-6}, 48000, rep, &err);
    L = 1; R = 1; s->process(&L, &R, 1); float v = -20; s->update(&v); float L2 = 1, R2 = 1; s->process(&L2, &R2, 1);
    printf("@slider knob -6 -> %.4f, live -20 -> %.4f (expect 0.5012, 0.1)\n", L, L2);
}
