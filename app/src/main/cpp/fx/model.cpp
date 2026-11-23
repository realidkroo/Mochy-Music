// the shared mix and the worker thread that builds each player's graph from it
#include "model.h"

#include "engine.h"
#include "native_plugin.h"

#include <algorithm>
#include <chrono>

namespace fx {

SlotControl::SlotControl(uint64_t uid, int count) : uid(uid), count(count), values(new std::atomic<float>[static_cast<size_t>(count > 0 ? count : 1)]) {
    for (int i = 0; i < count; i++) values[i].store(0.0f, std::memory_order_relaxed);
    for (auto& m : meters) m.store(0.0f, std::memory_order_relaxed);
}

void SlotControl::set(const float* v, int n) {
    for (int i = 0; i < n && i < count; i++) values[i].store(v[i], std::memory_order_relaxed);
    version.fetch_add(1, std::memory_order_release);
}

void SlotControl::setProblem(const std::string& why) {
    std::lock_guard<std::mutex> guard(problemLock);
    problem = why;
}

std::string SlotControl::takeProblem() {
    std::string out;
    {
        std::lock_guard<std::mutex> guard(problemLock);
        out = problem;
    }
    if (out.empty() && report) {
        std::lock_guard<std::mutex> guard(report->lock);
        out = report->problem;
    }
    return out;
}

Model& Model::get() {
    // never torn down: a worker thread joined at exit could hang the process on its way out
    static Model* model = new Model();
    return *model;
}

Model::Model() : current_(std::make_shared<Spec>()) {
    for (auto& row : send) {
        for (auto& s : row) s.store(0.0f, std::memory_order_relaxed);
    }
    worker_ = std::thread([this] { workerLoop(); });
    worker_.detach();
}

void Model::begin() {
    std::lock_guard<std::mutex> guard(lock_);
    building_ = std::make_shared<Spec>();
}

void Model::addBus(int id, bool songIn, int focus, uint32_t sends, bool master) {
    std::lock_guard<std::mutex> guard(lock_);
    if (!building_) return;
    BusSpec bus;
    bus.id = master ? Master : id;
    bus.songIn = songIn;
    bus.focus = master ? FocusAll : focus;
    bus.sends = master ? 0 : sends;
    if (master) building_->master = bus;
    else if (id >= 0 && id < MaxBuses) building_->buses.push_back(bus);
}

void Model::addSlot(int bus, uint64_t uid, const std::string& type, const std::string& source, const std::vector<std::string>& vars,
                    uint32_t revision, const float* values, int count) {
    std::lock_guard<std::mutex> guard(lock_);
    if (!building_) return;
    BusSpec* target = nullptr;
    if (bus == Master) target = &building_->master;
    for (BusSpec& b : building_->buses) {
        if (b.id == bus) target = &b;
    }
    if (target == nullptr) return;
    std::shared_ptr<SlotControl>& control = controls_[uid];
    // a script edited into a different number of knobs gets fresh controls
    if (!control || control->count != count) {
        control = std::make_shared<SlotControl>(uid, count);
        control->set(values, count);
    } else {
        bool changed = false;
        for (int i = 0; i < count; i++) changed |= control->values[i].load(std::memory_order_relaxed) != values[i];
        if (changed) control->set(values, count);
    }
    if (type == "mochy.liveprog" && !control->report) control->report = std::make_shared<ScriptReport>();
    target->slots.push_back(SlotSpec{uid, type, source, vars, revision, control});
}

void Model::commit(bool enabled) {
    std::lock_guard<std::mutex> guard(lock_);
    if (!building_) return;
    building_->enabled = enabled;
    current_ = building_;
    building_.reset();
    // controls for slots that left the mix go once nothing points at them
    for (auto it = controls_.begin(); it != controls_.end();) {
        bool used = false;
        auto check = [&](const BusSpec& b) {
            for (const SlotSpec& s : b.slots) used |= s.uid == it->first;
        };
        for (const BusSpec& b : current_->buses) check(b);
        check(current_->master);
        it = used ? std::next(it) : controls_.erase(it);
    }
    requested_ = engines_;
    wake_.notify_one();
}

std::shared_ptr<const Spec> Model::spec() {
    std::lock_guard<std::mutex> guard(lock_);
    return current_;
}

std::shared_ptr<SlotControl> Model::control(uint64_t uid) {
    std::lock_guard<std::mutex> guard(lock_);
    auto it = controls_.find(uid);
    return it == controls_.end() ? nullptr : it->second;
}

void Model::attach(Engine* engine) {
    std::lock_guard<std::mutex> guard(lock_);
    engines_.push_back(engine);
    // a commit may have landed between this engine's first build and now
    requested_.push_back(engine);
    wake_.notify_one();
}

void Model::detach(Engine* engine) {
    std::lock_guard<std::mutex> building(buildLock_);
    std::lock_guard<std::mutex> guard(lock_);
    engines_.erase(std::remove(engines_.begin(), engines_.end(), engine), engines_.end());
    requested_.erase(std::remove(requested_.begin(), requested_.end(), engine), requested_.end());
}

void Model::readPeaks(float* out, int count) {
    std::lock_guard<std::mutex> guard(lock_);
    const int peaks = (MaxBuses + 1) * 2;
    if (count < peaks + 1) return;
    std::fill(out, out + peaks + 1, 0.0f);
    bool clipped = false;
    for (Engine* e : engines_) e->takePeaks(out, &clipped);
    out[peaks] = clipped ? 1.0f : 0.0f;
}

void Model::readLoudness(float* out) {
    std::lock_guard<std::mutex> guard(lock_);
    Engine* latest = nullptr;
    for (Engine* e : engines_) {
        if (latest == nullptr || e->lastActive() > latest->lastActive()) latest = e;
    }
    if (latest != nullptr) latest->readLoudness(out);
    else std::fill(out, out + 5, LoudnessMeter::None);
}

void Model::resetLoudness() {
    std::lock_guard<std::mutex> guard(lock_);
    for (Engine* e : engines_) e->resetLoudness();
}

void Model::workerLoop() {
    for (;;) {
        {
            std::unique_lock<std::mutex> guard(lock_);
            wake_.wait_for(guard, std::chrono::milliseconds(250), [this] { return !requested_.empty(); });
        }
        // held across the whole pass, so an engine can't be torn down while it's being built for
        std::lock_guard<std::mutex> building(buildLock_);
        std::vector<Engine*> todo, all;
        std::shared_ptr<const Spec> spec;
        {
            std::lock_guard<std::mutex> guard(lock_);
            todo.swap(requested_);
            all = engines_;
            spec = current_;
        }
        std::sort(todo.begin(), todo.end());
        todo.erase(std::unique(todo.begin(), todo.end()), todo.end());
        for (Engine* e : todo) e->rebuild(spec);
        for (Engine* e : all) e->collect();
    }
}

std::unique_ptr<Plugin> makePlugin(const SlotSpec& slot, double rate, int maxBlock, int channels, std::string* problem) {
    if (slot.type == "mochy.liveprog") {
        std::vector<float> values(static_cast<size_t>(slot.control->count));
        for (int i = 0; i < slot.control->count; i++) values[i] = slot.control->values[i].load(std::memory_order_relaxed);
        return LiveprogPlugin::compile(slot.source, slot.vars, values, rate, slot.control->report, problem);
    }
    if (slot.type == "mochy.native") {
        const NativeType* type = findNative(slot.source);
        if (type == nullptr) {
            if (problem) *problem = "missing_module";
            return nullptr;
        }
        std::unique_ptr<Plugin> plugin = NativePlugin::create(*type, rate, maxBlock, channels);
        if (!plugin && problem) *problem = "module_failed";
        return plugin;
    }
    const PluginType* type = findBuiltin(slot.type);
    if (type == nullptr) {
        if (problem) *problem = "unknown_type";
        return nullptr;
    }
    std::unique_ptr<Plugin> plugin = type->create();
    plugin->prepare(rate, maxBlock);
    return plugin;
}

const PluginInfo* infoOf(const std::string& type) {
    if (const PluginType* builtin = findBuiltin(type)) return &builtin->info;
    if (const NativeType* native = findNative(type)) return &native->info;
    return nullptr;
}

}  // namespace fx
