// one player's mixer: runs the model's buses and effects on that player's channels, at its rate
#pragma once

#include "dsp.h"
#include "loudness.h"
#include "model.h"

#include <array>
#include <atomic>
#include <memory>
#include <vector>

namespace fx {

// the engine works in pieces this long, so every buffer it owns has a fixed size
constexpr int Chunk = 512;
constexpr int MaxLanes = 8;

// a group of the song's channels processed together: a stereo pair, or one channel (centre, lfe)
struct Lane {
    int first = 0;
    int count = 2;
};

std::vector<Lane> lanesFor(int channels);

struct GraphSlot {
    uint64_t uid = 0;
    uint32_t revision = 0;
    std::string type;
    std::shared_ptr<SlotControl> control;
    const PluginInfo* info = nullptr;
    // one copy per lane it runs on; empty ones are filled from the engine's current graph when this graph goes live
    std::array<std::unique_ptr<Plugin>, MaxLanes> instances;
    std::array<uint32_t, MaxLanes> seen{};
    bool reuse = false;
    bool stereoOnly = false;
    bool allChannels = false;
    int latency = 0;
    int meterCount = 0;
    // the copies being replaced after @init ran again, faded out over a few milliseconds
    std::array<std::unique_ptr<Plugin>, MaxLanes> fading;
    int fadeLeft = 0;
    int fadeTotal = 1;
    float wet = 1.0f;
    // the signal that skips a bypassed effect is held back by its latency, so the bus timing never jumps
    std::vector<DelayLine> dryDelay;
    std::vector<float> scratch;
};

struct GraphEdge {
    int from = -1;  // -1 is the song itself
    int delay = 0;
    float applied = 0.0f;
    std::vector<DelayLine> lines;
};

struct GraphBus {
    int id = 0;
    int focus = FocusAll;
    std::vector<GraphSlot> slots;
    std::vector<GraphEdge> inputs;
    uint32_t sends = 0;
    std::vector<float> buffer;  // channels x Chunk
    int latency = 0;
    int arrival = 0;
    float gainApplied = 1.0f;
    float panApplied = 0.0f;
};

struct Graph {
    std::vector<GraphBus> buses;  // every mix bus after the buses that send to it
    GraphBus master;
    bool enabled = false;
    // nothing on it changes the sound: one bus taking the song straight to the master, no effects
    bool transparent = false;
    int latency = 0;
    // a mix bus's id to its place in [buses], -1 when it isn't in this mix
    std::array<int, MaxBuses> indexOf{};
};

// a slot the worker already made copies for, so the next graph can take them over instead of starting cold
struct LiveSlot {
    uint64_t uid = 0;
    uint32_t revision = 0;
    uint32_t laneMask = 0;
    int latency = 0;
    bool allChannels = false;
};

class Engine {
public:
    Engine(int rate, int channels);
    ~Engine();

    // false when the audio came through untouched, so the caller knows nothing was changed
    bool process(const int16_t* in, int16_t* out, int frames);
    void reset();
    void bitstream(const uint8_t* data, int size, int64_t timeUs);

    // called on the worker: builds a graph for [spec] and hands it to the playback thread
    void rebuild(const std::shared_ptr<const Spec>& spec);
    // the worker frees what the playback thread put down
    void collect();

    void takePeaks(float* out, bool* clipped);
    void readLoudness(float* out) const;
    int64_t lastActive() const { return lastActive_.load(std::memory_order_relaxed); }
    void resetLoudness() { loudnessReset_.store(true, std::memory_order_relaxed); }

    const int rate;
    const int channels;

private:
    Graph* build(const Spec& spec, bool first);
    GraphBus buildBus(const BusSpec& spec, bool master);
    GraphSlot buildSlot(const SlotSpec& spec, uint32_t laneMask);
    uint32_t laneMaskFor(int focus) const;
    void adopt(Graph* fresh);
    void remember(const Graph* g);
    bool trash(Graph* graph, Plugin* plugin);

    void processChunk(Graph& g, const int16_t* in, int16_t* out, int frames);
    void gather(Graph& g, GraphBus& bus, int frames);
    void runBus(GraphBus& bus, int frames, bool audible, float gainDb, float pan);
    void runSlot(GraphSlot& slot, float* const* ch, const int* lanes, int laneCount, int frames, int focus);
    void publishPeaks(int index, const GraphBus& bus, int frames);

    std::vector<Lane> lanes_;
    Graph* current_ = nullptr;
    std::atomic<Graph*> pending_{nullptr};
    // what the worker last handed over, by uid and revision, so it knows which copies already exist
    std::vector<LiveSlot> live_;

    // single producer (playback thread), single consumer (worker): things to free off the playback thread
    struct Garbage {
        Graph* graph = nullptr;
        Plugin* plugin = nullptr;
    };
    static constexpr int TrashSize = 256;
    std::array<Garbage, TrashSize> trash_{};
    std::atomic<int> trashHead_{0};
    std::atomic<int> trashTail_{0};

    std::vector<float> song_;   // channels x Chunk
    std::vector<float> dry_;    // a slot's untouched copy, channels x Chunk
    std::vector<float> fade_;   // the fading copy's output, channels x Chunk
    std::vector<float> mono_;   // the second half of a mono lane, Chunk
    std::vector<float> monoDry_;
    std::vector<float*> planes_;
    std::array<std::atomic<float>, (MaxBuses + 1) * 2> peaks_{};
    std::atomic<bool> clipped_{false};
    LoudnessMeter loudness_;
    std::array<std::atomic<float>, 5> reading_{};
    std::atomic<bool> loudnessReset_{false};
    std::atomic<int64_t> lastActive_{0};
    uint32_t dither_ = 0x1234567u;
};

}  // namespace fx
