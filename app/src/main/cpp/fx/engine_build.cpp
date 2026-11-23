// how an engine turns the model's mix into a graph on the worker, and swaps it in on the playback thread without waiting
#include "engine.h"

#include <algorithm>
#include <cstring>

namespace fx {

namespace {

// a script whose @init ran again fades from its old copy over this long, so a knob never clicks
constexpr float FadeMs = 30.0f;

GraphSlot* findSlot(Graph* g, uint64_t uid) {
    if (g == nullptr) return nullptr;
    for (GraphBus& bus : g->buses) {
        for (GraphSlot& slot : bus.slots) {
            if (slot.uid == uid) return &slot;
        }
    }
    for (GraphSlot& slot : g->master.slots) {
        if (slot.uid == uid) return &slot;
    }
    return nullptr;
}

GraphBus* findBus(Graph* g, int id, bool master) {
    if (g == nullptr) return nullptr;
    if (master) return &g->master;
    const int index = id >= 0 && id < MaxBuses ? g->indexOf[id] : -1;
    return index >= 0 ? &g->buses[index] : nullptr;
}

template <typename F>
void forEachSlot(Graph* g, F&& f) {
    for (GraphBus& bus : g->buses) {
        for (GraphSlot& slot : bus.slots) f(slot);
    }
    for (GraphSlot& slot : g->master.slots) f(slot);
}

}  // namespace

std::vector<Lane> lanesFor(int channels) {
    if (channels <= 1) return {Lane{0, 1}};
    if (channels == 2) return {Lane{0, 2}};
    if (channels == 4) return {Lane{0, 2}, Lane{2, 2}};
    // android's order: front pair, centre, lfe, then the surrounds and heights in pairs
    std::vector<Lane> lanes = {Lane{0, 2}, Lane{2, 1}};
    int next = 3;
    if (channels >= 6) lanes.push_back(Lane{next++, 1});
    if ((channels - next) % 2 == 1) lanes.push_back(Lane{next++, 1});
    while (next + 1 < channels && static_cast<int>(lanes.size()) < MaxLanes) {
        lanes.push_back(Lane{next, 2});
        next += 2;
    }
    return lanes;
}

Engine::Engine(int rate, int channels) : rate(rate), channels(channels), lanes_(lanesFor(channels)) {
    song_.assign(static_cast<size_t>(channels) * Chunk, 0.0f);
    dry_.assign(static_cast<size_t>(channels) * Chunk, 0.0f);
    fade_.assign(static_cast<size_t>(channels) * Chunk, 0.0f);
    mono_.assign(Chunk, 0.0f);
    monoDry_.assign(Chunk, 0.0f);
    planes_.assign(static_cast<size_t>(channels), nullptr);
    loudness_.configure(rate, channels);
    for (auto& r : reading_) r.store(LoudnessMeter::None, std::memory_order_relaxed);
    // the first graph is built right here, so the song starts with its effects instead of a dry moment
    current_ = build(*Model::get().spec(), true);
    remember(current_);
    Model::get().attach(this);
}

Engine::~Engine() {
    Model::get().detach(this);
    delete pending_.exchange(nullptr);
    delete current_;
    collect();
}

uint32_t Engine::laneMaskFor(int focus) const {
    if (focus == FocusAll) return (1u << lanes_.size()) - 1u;
    if (focus >= FocusLane) {
        const int lane = focus - FocusLane;
        return lane < static_cast<int>(lanes_.size()) ? 1u << lane : 0u;
    }
    return 1u;
}

GraphSlot Engine::buildSlot(const SlotSpec& spec, uint32_t laneMask) {
    GraphSlot slot;
    slot.uid = spec.uid;
    slot.revision = spec.revision;
    slot.type = spec.type;
    slot.control = spec.control;
    slot.info = spec.type == "mochy.native" ? infoOf(spec.source) : infoOf(spec.type);
    slot.stereoOnly = slot.info != nullptr && slot.info->stereoOnly;
    slot.meterCount = slot.info != nullptr ? std::min<int>(static_cast<int>(slot.info->meters.size()), MaxMeters) : 0;
    slot.scratch.assign(static_cast<size_t>(spec.control->count), 0.0f);

    auto known = std::find_if(live_.begin(), live_.end(), [&](const LiveSlot& l) {
        return l.uid == spec.uid && l.revision == spec.revision && l.laneMask == laneMask;
    });
    if (known != live_.end()) {
        slot.reuse = true;
        slot.latency = known->latency;
        slot.allChannels = known->allChannels;
    } else {
        std::string problem;
        for (int lane = 0; lane < static_cast<int>(lanes_.size()); lane++) {
            if ((laneMask & (1u << lane)) == 0) continue;
            std::unique_ptr<Plugin> plugin = makePlugin(spec, rate, Chunk, channels, &problem);
            if (!plugin) {
                spec.control->setProblem(problem);
                break;
            }
            slot.latency = plugin->latency();
            slot.allChannels = plugin->wantsAllChannels();
            slot.instances[lane] = std::move(plugin);
            // one copy takes every channel at once
            if (slot.allChannels) break;
        }
    }
    if (slot.latency > 0) {
        slot.dryDelay.resize(static_cast<size_t>(channels));
        for (DelayLine& line : slot.dryDelay) line.prepare(slot.latency + Chunk);
    }
    return slot;
}

GraphBus Engine::buildBus(const BusSpec& spec, bool master) {
    GraphBus bus;
    bus.id = master ? Master : spec.id;
    bus.focus = master ? FocusAll : spec.focus;
    bus.sends = spec.sends;
    bus.buffer.assign(static_cast<size_t>(channels) * Chunk, 0.0f);
    const uint32_t mask = laneMaskFor(bus.focus);
    for (const SlotSpec& slot : spec.slots) {
        bus.slots.push_back(buildSlot(slot, mask));
        bus.latency += bus.slots.back().latency;
    }
    return bus;
}

Graph* Engine::build(const Spec& spec, bool first) {
    auto* g = new Graph();
    g->enabled = spec.enabled;
    g->indexOf.fill(-1);

    // every bus after all the buses that send to it; a loop the ui should have refused is cut where it closes
    std::vector<const BusSpec*> pending;
    for (const BusSpec& b : spec.buses) {
        if (b.id >= 0 && b.id < MaxBuses) pending.push_back(&b);
    }
    std::vector<const BusSpec*> ordered;
    while (!pending.empty()) {
        auto ready = std::find_if(pending.begin(), pending.end(), [&](const BusSpec* b) {
            return std::none_of(pending.begin(), pending.end(), [&](const BusSpec* s) { return s != b && (s->sends & (1u << b->id)); });
        });
        if (ready == pending.end()) ready = pending.begin();
        ordered.push_back(*ready);
        pending.erase(ready);
    }
    for (const BusSpec* b : ordered) {
        g->indexOf[b->id] = static_cast<int>(g->buses.size());
        g->buses.push_back(buildBus(*b, false));
    }
    g->master = buildBus(spec.master, true);

    // inputs, each held back so everything meeting at a bus arrives together (delay compensation)
    auto connect = [&](GraphBus& to, bool takesSong, int toIndex) {
        int arrival = 0;
        for (const BusSpec* s : ordered) {
            const GraphBus& from = g->buses[g->indexOf[s->id]];
            const int fromIndex = g->indexOf[s->id];
            if ((from.sends & (1u << to.id)) && (toIndex < 0 || fromIndex < toIndex)) arrival = std::max(arrival, from.arrival + from.latency);
        }
        to.arrival = arrival;
        auto edge = [&](int from, int ready) {
            GraphEdge e;
            e.from = from;
            // a fresh engine starts at the mix's levels; a send added later fades in from nothing
            if (first) e.applied = from < 0 ? 1.0f : Model::get().send[from][to.id].load(std::memory_order_relaxed);
            e.delay = arrival - ready;
            if (e.delay > 0) {
                e.lines.resize(static_cast<size_t>(channels));
                for (DelayLine& line : e.lines) line.prepare(e.delay + Chunk);
            }
            to.inputs.push_back(std::move(e));
        };
        if (takesSong) edge(-1, 0);
        for (const BusSpec* s : ordered) {
            const GraphBus& from = g->buses[g->indexOf[s->id]];
            const int fromIndex = g->indexOf[s->id];
            if ((from.sends & (1u << to.id)) && (toIndex < 0 || fromIndex < toIndex)) edge(s->id, from.arrival + from.latency);
        }
    };
    for (size_t i = 0; i < g->buses.size(); i++) {
        const BusSpec* s = ordered[i];
        connect(g->buses[i], s->songIn, static_cast<int>(i));
    }
    connect(g->master, false, -1);
    g->latency = g->master.arrival + g->master.latency;

    g->transparent = g->buses.size() == 1 && g->buses[0].slots.empty() && g->master.slots.empty() && ordered[0]->songIn &&
                     g->buses[0].focus == FocusAll && g->buses[0].sends == (1u << Master);
    return g;
}

void Engine::rebuild(const std::shared_ptr<const Spec>& spec) {
    Graph* fresh = build(*spec, false);
    // a graph handed over earlier but not yet taken comes back, and the copies it made move into the new one
    if (Graph* stale = pending_.exchange(nullptr, std::memory_order_acq_rel)) {
        forEachSlot(fresh, [&](GraphSlot& slot) {
            if (!slot.reuse) return;
            GraphSlot* old = findSlot(stale, slot.uid);
            if (old == nullptr || old->reuse || old->revision != slot.revision) return;
            slot.instances = std::move(old->instances);
            slot.seen = old->seen;
            slot.reuse = false;
        });
        delete stale;
    }
    remember(fresh);
    pending_.store(fresh, std::memory_order_release);
}

void Engine::remember(const Graph* g) {
    live_.clear();
    const auto record = [&](const GraphBus& bus) {
        for (const GraphSlot& slot : bus.slots) {
            live_.push_back(LiveSlot{slot.uid, slot.revision, laneMaskFor(bus.focus), slot.latency, slot.allChannels});
        }
    };
    for (const GraphBus& bus : g->buses) record(bus);
    record(g->master);
}

void Engine::adopt(Graph* fresh) {
    const int fadeSamples = static_cast<int>(FadeMs * 0.001f * static_cast<float>(rate));
    forEachSlot(fresh, [&](GraphSlot& slot) {
        GraphSlot* old = findSlot(current_, slot.uid);
        // an effect just added crossfades in from the dry signal
        if (old == nullptr) {
            slot.wet = 0.0f;
            return;
        }
        if (slot.reuse) {
            if (old->revision == slot.revision) {
                slot.instances = std::move(old->instances);
                slot.seen = old->seen;
                slot.fading = std::move(old->fading);
                slot.fadeLeft = old->fadeLeft;
                slot.fadeTotal = old->fadeTotal;
                slot.wet = old->wet;
            }
            slot.reuse = false;
            return;
        }
        // the same effect built again (a script's @init with new knobs): the old copy plays on while it fades
        if (old->revision != slot.revision) {
            slot.fading = std::move(old->instances);
            slot.fadeLeft = fadeSamples;
            slot.fadeTotal = fadeSamples;
        }
        slot.wet = old->wet;
    });
    // faders, pans and sends carry on from where they were instead of jumping
    for (GraphBus& bus : fresh->buses) {
        if (GraphBus* old = findBus(current_, bus.id, false)) {
            bus.gainApplied = old->gainApplied;
            bus.panApplied = old->panApplied;
            for (GraphEdge& e : bus.inputs) {
                for (const GraphEdge& o : old->inputs) {
                    if (o.from == e.from) e.applied = o.applied;
                }
            }
        }
    }
    if (current_ != nullptr) {
        fresh->master.gainApplied = current_->master.gainApplied;
        fresh->master.panApplied = current_->master.panApplied;
        for (GraphEdge& e : fresh->master.inputs) {
            for (const GraphEdge& o : current_->master.inputs) {
                if (o.from == e.from) e.applied = o.applied;
            }
        }
    }
    Graph* old = current_;
    current_ = fresh;
    if (old != nullptr && !trash(old, nullptr)) {
        // the worker is far behind; freeing here once is better than holding the old graph forever
        delete old;
    }
}

bool Engine::trash(Graph* graph, Plugin* plugin) {
    const int head = trashHead_.load(std::memory_order_relaxed);
    const int next = (head + 1) % TrashSize;
    if (next == trashTail_.load(std::memory_order_acquire)) return false;
    trash_[head] = Garbage{graph, plugin};
    trashHead_.store(next, std::memory_order_release);
    return true;
}

void Engine::collect() {
    int tail = trashTail_.load(std::memory_order_relaxed);
    while (tail != trashHead_.load(std::memory_order_acquire)) {
        delete trash_[tail].graph;
        delete trash_[tail].plugin;
        trash_[tail] = Garbage{};
        tail = (tail + 1) % TrashSize;
        trashTail_.store(tail, std::memory_order_release);
    }
}

void Engine::reset() {
    if (Graph* fresh = pending_.exchange(nullptr, std::memory_order_acq_rel)) adopt(fresh);
    if (current_ == nullptr) return;
    auto clear = [](GraphBus& bus) {
        for (GraphSlot& slot : bus.slots) {
            for (auto& p : slot.instances) {
                if (p) p->reset();
            }
            for (DelayLine& line : slot.dryDelay) line.reset();
        }
        for (GraphEdge& e : bus.inputs) {
            for (DelayLine& line : e.lines) line.reset();
        }
    };
    for (GraphBus& bus : current_->buses) clear(bus);
    clear(current_->master);
}

void Engine::bitstream(const uint8_t* data, int size, int64_t timeUs) {
    if (current_ == nullptr) return;
    forEachSlot(current_, [&](GraphSlot& slot) {
        for (auto& p : slot.instances) {
            if (p && p->wantsBitstream()) p->bitstream(data, size, timeUs);
        }
    });
}

}  // namespace fx
