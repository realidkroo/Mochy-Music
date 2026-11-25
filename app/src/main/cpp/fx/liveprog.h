// liveprog: a jamesdsp eel2 script run as a mixer effect, with live knobs and its own console
#pragma once

#include "plugin.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace fx {

// what the console and the editor see of one script, shared by every copy of it in every player
struct ScriptReport {
    std::mutex lock;
    std::string console;
    std::string problem;
    std::atomic<bool> paused{false};

    void print(const char* text);
    std::string takeConsole();
};

// a script's sections, split out of its source the way jamesdsp and jsfx write them
struct ScriptSource {
    std::string init, sample, slider, block;
    // where each section starts in the original, so errors point at the right line
    int initLine = 0, sampleLine = 0, sliderLine = 0, blockLine = 0;

    static ScriptSource split(const std::string& text);
};

class LiveprogPlugin : public Plugin {
public:
    // compiles off the playback thread; null with [error] filled when the script doesn't build
    static std::unique_ptr<LiveprogPlugin> compile(const std::string& source, const std::vector<std::string>& vars,
                                                   const std::vector<float>& values, double rate,
                                                   std::shared_ptr<ScriptReport> report, std::string* error);

    ~LiveprogPlugin() override;

    void prepare(double rate, int maxBlock) override;
    void reset() override {}
    void update(const float* values) override;
    void process(float* left, float* right, int frames) override;

private:
    LiveprogPlugin() = default;

    void* vm_ = nullptr;
    void* init_ = nullptr;
    void* sample_ = nullptr;
    void* slider_ = nullptr;
    void* block_ = nullptr;
    float* spl0_ = nullptr;
    float* spl1_ = nullptr;
    float* srate_ = nullptr;
    float* samplesblock_ = nullptr;
    std::vector<float*> vars_;
    std::shared_ptr<ScriptReport> report_;
    double rate_ = 48000;
    int slowBlocks_ = 0;
    int badSamples_ = 0;
};

// checks a script without running it, for the editor; empty when it builds
std::string checkScript(const std::string& source);

}  // namespace fx
