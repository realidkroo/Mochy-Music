// renders an effect's real impulse response for the decay picture: the level over time in three bands, and how long each takes to fade
#include "tail.h"

#include "dsp.h"
#include "engine.h"
#include "model.h"

#include <algorithm>
#include <cmath>

namespace fx {

namespace {

// rt60 from schroeder's backward energy sum: the time from -5 to -25 db, times three
float decayTime(const std::vector<float>& ir, double rate) {
    std::vector<double> energy(ir.size());
    double sum = 0.0;
    for (size_t i = ir.size(); i-- > 0;) {
        sum += static_cast<double>(ir[i]) * ir[i];
        energy[i] = sum;
    }
    if (sum <= 1e-20) return -1.0f;
    double t5 = -1.0, t25 = -1.0, t15 = -1.0;
    for (size_t i = 0; i < energy.size(); i++) {
        const double db = 10.0 * std::log10(energy[i] / sum + 1e-30);
        if (t5 < 0 && db <= -5.0) t5 = i / rate;
        if (t15 < 0 && db <= -15.0) t15 = i / rate;
        if (t25 < 0 && db <= -25.0) {
            t25 = i / rate;
            break;
        }
    }
    if (t5 >= 0 && t25 > t5) return static_cast<float>((t25 - t5) * 3.0);
    // a tail longer than what was rendered: guess from the first 10 db
    if (t5 >= 0 && t15 > t5) return static_cast<float>((t15 - t5) * 6.0);
    return -1.0f;
}

}  // namespace

std::vector<float> renderTail(const std::string& type, const std::string& source, const std::vector<std::string>& vars,
                              const std::vector<float>& values, int rate, float seconds, int points) {
    const int total = std::max(Chunk, static_cast<int>(seconds * static_cast<float>(rate)));
    points = std::max(8, points);
    std::vector<float> out(static_cast<size_t>(points) * 4 + 4, -120.0f);

    // the picture shows the effect alone, so a reverb's dry signal is turned down for it
    std::vector<float> knobs = values;
    if (const PluginInfo* info = infoOf(type == "mochy.native" ? source : type)) {
        for (size_t i = 0; i < info->params.size() && i < knobs.size(); i++) {
            const ParamInfo& p = info->params[i];
            if (p.id == "mix") knobs[i] = p.max;
            if (p.id == "dry") knobs[i] = p.min;
        }
    }
    SlotSpec slot;
    slot.type = type;
    slot.source = source;
    slot.vars = vars;
    slot.control = std::make_shared<SlotControl>(0, static_cast<int>(knobs.size()));
    slot.control->set(knobs.data(), static_cast<int>(knobs.size()));
    if (type == "mochy.liveprog") slot.control->report = std::make_shared<ScriptReport>();
    std::string problem;
    std::unique_ptr<Plugin> plugin = makePlugin(slot, rate, Chunk, 2, &problem);
    if (!plugin) return out;
    plugin->update(knobs.data());
    plugin->reset();
    plugin->update(knobs.data());

    std::vector<float> left(static_cast<size_t>(total), 0.0f), right(static_cast<size_t>(total), 0.0f);
    left[0] = right[0] = 1.0f;
    for (int i = 0; i < total; i += Chunk) plugin->process(&left[i], &right[i], std::min(Chunk, total - i));

    std::vector<float> bands[4];
    for (auto& b : bands) b.resize(static_cast<size_t>(total));
    Biquad low, midHigh, midLow, high;
    low.set(FilterShape::LowPass, rate, 250.0f, 0.7071f);
    midHigh.set(FilterShape::HighPass, rate, 250.0f, 0.7071f);
    midLow.set(FilterShape::LowPass, rate, 4000.0f, 0.7071f);
    high.set(FilterShape::HighPass, rate, 4000.0f, 0.7071f);
    for (int i = 0; i < total; i++) {
        float x = (left[i] + right[i]) * 0.5f;
        if (!finite(x)) x = 0.0f;
        bands[0][i] = x;
        bands[1][i] = low.process(x);
        bands[2][i] = midLow.process(midHigh.process(x));
        bands[3][i] = high.process(x);
    }

    // the level in each slice of time, in db under the loudest slice of the whole response
    const int window = std::max(1, total / points);
    double loudest = 1e-30;
    std::vector<double> energy(static_cast<size_t>(points) * 4, 0.0);
    for (int b = 0; b < 4; b++) {
        for (int p = 0; p < points; p++) {
            double e = 0.0;
            const int from = p * window, to = std::min(total, from + window);
            for (int i = from; i < to; i++) e += static_cast<double>(bands[b][i]) * bands[b][i];
            e /= std::max(1, to - from);
            energy[static_cast<size_t>(p) * 4 + b] = e;
            if (b == 0) loudest = std::max(loudest, e);
        }
    }
    for (size_t i = 0; i < energy.size(); i++) out[i] = std::max(-120.0f, static_cast<float>(10.0 * std::log10(energy[i] / loudest + 1e-30)));
    for (int b = 0; b < 4; b++) out[static_cast<size_t>(points) * 4 + b] = decayTime(bands[b], rate);
    return out;
}

}  // namespace fx
