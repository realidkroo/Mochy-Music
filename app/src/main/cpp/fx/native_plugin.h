// effects a code module brings as a native library (modules/api/native/mochy_fx.h)
#pragma once

#include "plugin.h"
#include "mochy_fx.h"

#include <memory>
#include <string>
#include <vector>

namespace fx {

// one processor a library offers, with the info the ui draws it from
struct NativeType {
    std::string key;  // "<library path>|<processor id>", what a saved mix points at
    PluginInfo info;
    const mochy_fx_processor* api = nullptr;
};

// opens the library once and lists its processors; empty with [error] filled when it isn't a mochy one
std::vector<NativeType> loadNativeLibrary(const std::string& path, std::string* error);

const NativeType* findNative(const std::string& key);

class NativePlugin : public Plugin {
public:
    static std::unique_ptr<NativePlugin> create(const NativeType& type, double rate, int maxBlock, int channels);
    ~NativePlugin() override;

    void prepare(double rate, int maxBlock) override;
    void reset() override;
    void update(const float* values) override;
    void process(float* left, float* right, int frames) override;
    int latency() const override;
    void readMeters(float* out) const override;
    bool wantsAllChannels() const override { return (api_->flags & MOCHY_FX_ALL_CHANNELS) != 0 && api_->process_all; }
    void processAll(float** channels, int count, int frames) override;
    bool wantsBitstream() const override { return (api_->flags & MOCHY_FX_BITSTREAM) != 0 && api_->bitstream; }
    void bitstream(const uint8_t* data, int size, int64_t timeUs) override;

private:
    NativePlugin() = default;

    const mochy_fx_processor* api_ = nullptr;
    void* self_ = nullptr;
    int channels_ = 2;
};

}  // namespace fx
