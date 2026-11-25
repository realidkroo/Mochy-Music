// what every mixer effect is: built in, a liveprog script or a module's native code all look the same to the engine and the ui
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace fx {

// how a knob moves between its ends, the ui draws and steps it the same way
enum class Scale : int { Linear = 0, Log = 1, Int = 2, Choice = 3, Toggle = 4 };

struct ParamInfo {
    std::string id;
    std::string name;
    std::string unit;
    float min = 0.0f;
    float max = 1.0f;
    float def = 0.0f;
    Scale scale = Scale::Linear;
    // choice names split by '|'
    std::string choices;

    float clamp(float v) const {
        if (!(v == v)) return def;
        if (v < min) v = min;
        if (v > max) v = max;
        if (scale == Scale::Int || scale == Scale::Choice || scale == Scale::Toggle) v = static_cast<float>(static_cast<int>(v + 0.5f));
        return v;
    }
};

// a value an effect reports back while it plays, like how much a compressor is pulling down
struct MeterInfo {
    std::string id;
    std::string name;
    std::string unit;
    float min = 0.0f;
    float max = 1.0f;
};

// a starting point that sets every knob at once; values follow the params' order
struct PresetInfo {
    std::string name;
    std::vector<float> values;
};

struct PluginInfo {
    std::string id;
    std::string name;
    std::string category;
    std::string description;
    std::vector<ParamInfo> params;
    std::vector<MeterInfo> meters;
    std::vector<PresetInfo> presets;
    // the ui shows a decay picture for effects with a tail worth looking at
    bool showsTail = false;
    // reshapes left and right against each other, so it means nothing on a mono lane (centre, lfe)
    bool stereoOnly = false;

    std::vector<float> defaults() const {
        std::vector<float> out;
        out.reserve(params.size());
        for (const ParamInfo& p : params) out.push_back(p.def);
        return out;
    }
};

class Plugin {
public:
    virtual ~Plugin() = default;

    // called off the playback thread before the first block, and again for a new rate; may allocate
    virtual void prepare(double rate, int maxBlock) = 0;

    // forgets everything it was holding: delay lines, envelopes, filter memory
    virtual void reset() = 0;

    // every knob's value in the info's order, already clamped; only called when one of them moved
    virtual void update(const float* values) = 0;

    virtual void process(float* left, float* right, int frames) = 0;

    // samples the output trails the input by, the engine lines every other path up with it
    virtual int latency() const { return 0; }

    // fills one value per MeterInfo
    virtual void readMeters(float* out) const { (void)out; }

    // effects that need the whole layout at once (an object decoder) take every channel in one call
    virtual bool wantsAllChannels() const { return false; }
    virtual void processAll(float** channels, int count, int frames) { (void)channels; (void)count; (void)frames; }

    // effects that read the stream before decoding get each encoded frame, in order, before its audio arrives
    virtual bool wantsBitstream() const { return false; }
    virtual void bitstream(const uint8_t* data, int size, int64_t timeUs) { (void)data; (void)size; (void)timeUs; }
};

// a kind of effect the add sheet can offer, and how to make one
struct PluginType {
    PluginInfo info;
    std::unique_ptr<Plugin> (*create)();
};

// every built-in kind, in the order the add sheet lists them
const std::vector<PluginType>& builtinTypes();

const PluginType* findBuiltin(const std::string& id);

}  // namespace fx
