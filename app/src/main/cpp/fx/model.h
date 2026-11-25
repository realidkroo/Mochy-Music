// the mix as the ui last set it, shared by every player's engine: buses, sends, effects and every knob
#pragma once

#include "liveprog.h"
#include "plugin.h"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace fx {

constexpr int MaxBuses = 16;
// the master's place in every per-bus array, after the mix buses
constexpr int Master = MaxBuses;
constexpr int MaxMeters = 8;
constexpr int MaxChannels = 16;

// what part of its input a bus works on; FocusLane + k is the song's k-th channel group alone
enum Focus : int { FocusAll = 0, FocusMid = 1, FocusSide = 2, FocusLeft = 3, FocusRight = 4, FocusLane = 10 };

// one effect slot's knobs and readings, shared by its copies in every lane of every player
struct SlotControl {
    SlotControl(uint64_t uid, int count);

    const uint64_t uid;
    const int count;
    std::unique_ptr<std::atomic<float>[]> values;
    std::atomic<uint32_t> version{1};
    std::atomic<bool> bypass{false};
    std::atomic<float> meters[MaxMeters];
    std::atomic<float> peak{0.0f};
    // why the effect isn't running: empty when it is
    std::mutex problemLock;
    std::string problem;
    std::shared_ptr<ScriptReport> report;

    void set(const float* v, int n);
    void setProblem(const std::string& why);
    std::string takeProblem();
};

struct SlotSpec {
    uint64_t uid = 0;
    std::string type;
    // a liveprog script, or the key of a module's native processor
    std::string source;
    std::vector<std::string> vars;
    // bumped when a script without @slider needs @init run again with new knobs: the old copy fades out
    uint32_t revision = 0;
    std::shared_ptr<SlotControl> control;
};

struct BusSpec {
    int id = 0;
    bool songIn = false;
    int focus = FocusAll;
    // a bit per destination bus id, bit Master for the master
    uint32_t sends = 0;
    std::vector<SlotSpec> slots;
};

struct Spec {
    bool enabled = false;
    std::vector<BusSpec> buses;
    BusSpec master;
};

struct BusControl {
    std::atomic<float> gainDb{0.0f};
    std::atomic<float> pan{0.0f};
    std::atomic<bool> mute{false};
    std::atomic<bool> solo{false};
};

class Engine;

class Model {
public:
    static Model& get();

    // the ui describes the whole mix between begin and commit; nothing plays until commit
    void begin();
    void addBus(int id, bool songIn, int focus, uint32_t sends, bool master);
    void addSlot(int bus, uint64_t uid, const std::string& type, const std::string& source, const std::vector<std::string>& vars,
                 uint32_t revision, const float* values, int count);
    void commit(bool enabled);

    std::shared_ptr<const Spec> spec();
    std::shared_ptr<SlotControl> control(uint64_t uid);

    BusControl bus[MaxBuses + 1];
    // post-fader send levels, 0..1, from a mix bus to a bus or the master
    std::atomic<float> send[MaxBuses][MaxBuses + 1];
    // loudness and peaks are only worked out while someone is looking at them
    std::atomic<bool> metering{false};

    void attach(Engine* engine);
    void detach(Engine* engine);

    // [peakL, peakR] per bus (master last) since the last read, then a clip flag
    void readPeaks(float* out, int count);
    // momentary, short-term, integrated, range, true peak, of the player heard last
    void readLoudness(float* out);
    void resetLoudness();

private:
    Model();
    void workerLoop();

    std::mutex lock_;
    // held while the worker builds for an engine, so one going away waits for its build to end
    std::mutex buildLock_;
    std::condition_variable wake_;
    std::shared_ptr<Spec> building_;
    std::shared_ptr<const Spec> current_;
    std::unordered_map<uint64_t, std::shared_ptr<SlotControl>> controls_;
    std::vector<Engine*> engines_;
    std::vector<Engine*> requested_;
    std::thread worker_;
};

// makes a ready effect for a slot: built in, a compiled script or a module's processor; null with [problem] filled when it can't
std::unique_ptr<Plugin> makePlugin(const SlotSpec& slot, double rate, int maxBlock, int channels, std::string* problem);

// the knob list of a built-in or native type, null for scripts
const PluginInfo* infoOf(const std::string& type);

}  // namespace fx
