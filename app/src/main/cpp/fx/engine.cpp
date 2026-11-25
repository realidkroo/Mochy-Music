// the playback side of an engine: pull the song through every bus, then the master, a chunk at a time
#include "engine.h"

#include <chrono>
#include <cstring>

namespace fx {

namespace {

void atomicMax(std::atomic<float>& slot, float value) {
    float seen = slot.load(std::memory_order_relaxed);
    while (value > seen && !slot.compare_exchange_weak(seen, value, std::memory_order_relaxed)) {
    }
}

int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

}  // namespace

bool Engine::process(const int16_t* in, int16_t* out, int frames) {
    if (Graph* fresh = pending_.exchange(nullptr, std::memory_order_acq_rel)) adopt(fresh);
    Model& model = Model::get();
    if (current_ == nullptr || !current_->enabled) {
        if (in != out) std::memcpy(out, in, sizeof(int16_t) * static_cast<size_t>(frames) * channels);
        return false;
    }
    lastActive_.store(nowMs(), std::memory_order_relaxed);
    if (loudnessReset_.exchange(false, std::memory_order_relaxed)) loudness_.reset();

    // one bus straight to the master at unity is a wire: the samples go out exactly as they came in
    Graph& g = *current_;
    if (g.transparent && !model.metering.load(std::memory_order_relaxed)) {
        const GraphBus& bus = g.buses[0];
        const int id = bus.id;
        const bool unity = model.bus[id].gainDb.load(std::memory_order_relaxed) == 0.0f && model.bus[id].pan.load(std::memory_order_relaxed) == 0.0f &&
                           !model.bus[id].mute.load(std::memory_order_relaxed) && model.bus[Master].gainDb.load(std::memory_order_relaxed) == 0.0f &&
                           model.bus[Master].pan.load(std::memory_order_relaxed) == 0.0f && !model.bus[Master].mute.load(std::memory_order_relaxed) &&
                           model.send[id][Master].load(std::memory_order_relaxed) == 1.0f;
        const bool settled = bus.gainApplied == 1.0f && bus.panApplied == 0.0f && g.master.gainApplied == 1.0f && g.master.panApplied == 0.0f &&
                             !g.master.inputs.empty() && g.master.inputs[0].applied == 1.0f;
        if (unity && settled) {
            if (in != out) std::memcpy(out, in, sizeof(int16_t) * static_cast<size_t>(frames) * channels);
            return false;
        }
    }
    for (int done = 0; done < frames; done += Chunk) {
        const int n = std::min(Chunk, frames - done);
        processChunk(g, in + static_cast<size_t>(done) * channels, out + static_cast<size_t>(done) * channels, n);
    }
    return true;
}

void Engine::processChunk(Graph& g, const int16_t* in, int16_t* out, int n) {
    Model& model = Model::get();
    for (int c = 0; c < channels; c++) {
        float* plane = song_.data() + static_cast<size_t>(c) * Chunk;
        for (int i = 0; i < n; i++) plane[i] = static_cast<float>(in[i * channels + c]) * (1.0f / 32768.0f);
    }

    // solo in place: a soloed bus, and every bus feeding one, stay heard
    bool anySolo = false;
    for (const GraphBus& bus : g.buses) anySolo |= model.bus[bus.id].solo.load(std::memory_order_relaxed);
    bool feeds[MaxBuses] = {};
    if (anySolo) {
        for (int i = static_cast<int>(g.buses.size()) - 1; i >= 0; i--) {
            const GraphBus& bus = g.buses[i];
            for (int d = 0; d < MaxBuses; d++) {
                if ((bus.sends & (1u << d)) && (model.bus[d].solo.load(std::memory_order_relaxed) || feeds[d])) feeds[bus.id] = true;
            }
        }
    }
    for (GraphBus& bus : g.buses) {
        const BusControl& c = model.bus[bus.id];
        const bool audible = !c.mute.load(std::memory_order_relaxed) && (!anySolo || c.solo.load(std::memory_order_relaxed) || feeds[bus.id]);
        gather(g, bus, n);
        runBus(bus, n, audible, c.gainDb.load(std::memory_order_relaxed), c.pan.load(std::memory_order_relaxed));
        publishPeaks(bus.id, bus, n);
    }
    const BusControl& m = model.bus[Master];
    gather(g, g.master, n);
    runBus(g.master, n, !m.mute.load(std::memory_order_relaxed), m.gainDb.load(std::memory_order_relaxed), m.pan.load(std::memory_order_relaxed));
    publishPeaks(Master, g.master, n);

    if (model.metering.load(std::memory_order_relaxed)) {
        for (int c = 0; c < channels; c++) planes_[c] = g.master.buffer.data() + static_cast<size_t>(c) * Chunk;
        loudness_.process(planes_.data(), n);
        const LoudnessMeter::Reading& r = loudness_.reading();
        reading_[0].store(r.momentary, std::memory_order_relaxed);
        reading_[1].store(r.shortTerm, std::memory_order_relaxed);
        reading_[2].store(r.integrated, std::memory_order_relaxed);
        reading_[3].store(r.range, std::memory_order_relaxed);
        reading_[4].store(r.truePeakDb, std::memory_order_relaxed);
    }

    // back to 16 bits with triangular dither, so quiet tails fade into noise instead of crunching
    bool clipped = false;
    for (int c = 0; c < channels; c++) {
        const float* plane = g.master.buffer.data() + static_cast<size_t>(c) * Chunk;
        for (int i = 0; i < n; i++) {
            const float x = plane[i];
            clipped |= x > 1.0f || x < -1.0f;
            dither_ ^= dither_ << 13;
            dither_ ^= dither_ >> 17;
            dither_ ^= dither_ << 5;
            const float r1 = static_cast<float>(dither_ & 0xffff) * (1.0f / 65536.0f);
            const float r2 = static_cast<float>(dither_ >> 16) * (1.0f / 65536.0f);
            float v = x * 32767.0f + (r1 - r2);
            v = v > 32767.0f ? 32767.0f : (v < -32768.0f ? -32768.0f : v);
            out[i * channels + c] = static_cast<int16_t>(std::lrintf(finite(v) ? v : 0.0f));
        }
    }
    if (clipped) clipped_.store(true, std::memory_order_relaxed);
}

void Engine::gather(Graph& g, GraphBus& bus, int n) {
    Model& model = Model::get();
    std::fill(bus.buffer.begin(), bus.buffer.end(), 0.0f);
    for (GraphEdge& e : bus.inputs) {
        const float* source;
        float target;
        if (e.from < 0) {
            source = song_.data();
            target = 1.0f;
        } else {
            const int index = g.indexOf[e.from];
            if (index < 0) continue;
            source = g.buses[index].buffer.data();
            target = model.send[e.from][bus.id].load(std::memory_order_relaxed);
        }
        const float start = e.applied, step = (target - start) / static_cast<float>(n);
        if (start == 0.0f && target == 0.0f && e.delay == 0) continue;
        for (int c = 0; c < channels; c++) {
            const float* src = source + static_cast<size_t>(c) * Chunk;
            float* dst = bus.buffer.data() + static_cast<size_t>(c) * Chunk;
            DelayLine* line = e.delay > 0 ? &e.lines[c] : nullptr;
            float level = start;
            for (int i = 0; i < n; i++) {
                level += step;
                float x = src[i];
                if (line) {
                    // read before writing, so the delay is exactly e.delay samples
                    const float held = line->read(e.delay);
                    line->write(x);
                    x = held;
                }
                dst[i] += x * level;
            }
        }
        e.applied = target;
    }
}

void Engine::runBus(GraphBus& bus, int n, bool audible, float gainDb, float pan) {
    float* ch[MaxChannels];
    for (int c = 0; c < channels; c++) ch[c] = bus.buffer.data() + static_cast<size_t>(c) * Chunk;
    const int laneCount = static_cast<int>(lanes_.size());
    int lanes[MaxLanes];
    int count = 0;
    const int focus = bus.focus;
    const Lane& front = lanes_[0];

    auto silenceExcept = [&](int keep) {
        for (int l = 0; l < laneCount; l++) {
            if (l == keep) continue;
            for (int c = lanes_[l].first; c < lanes_[l].first + lanes_[l].count; c++) std::memset(ch[c], 0, sizeof(float) * n);
        }
    };

    if (focus == FocusAll) {
        for (int l = 0; l < laneCount; l++) lanes[count++] = l;
    } else if (focus >= FocusLane) {
        const int lane = focus - FocusLane;
        silenceExcept(lane < laneCount ? lane : -1);
        if (lane < laneCount) lanes[count++] = lane;
    } else {
        // mid, side, left or right of the front pair, carried through the effects as the same signal on both sides
        silenceExcept(0);
        if (front.count == 2) {
            float* l = ch[0];
            float* r = ch[1];
            for (int i = 0; i < n; i++) {
                const float a = l[i], b = r[i];
                const float s = focus == FocusMid ? (a + b) * 0.5f : focus == FocusSide ? (a - b) * 0.5f : focus == FocusLeft ? a : b;
                l[i] = r[i] = s;
            }
        } else if (focus == FocusSide) {
            std::memset(ch[0], 0, sizeof(float) * n);
        }
        lanes[count++] = 0;
    }

    for (GraphSlot& slot : bus.slots) runSlot(slot, ch, lanes, count, n, focus);

    if (focus != FocusAll && focus < FocusLane && front.count == 2) {
        float* l = ch[0];
        float* r = ch[1];
        for (int i = 0; i < n; i++) {
            const float y = (l[i] + r[i]) * 0.5f;
            switch (focus) {
                case FocusMid: l[i] = y; r[i] = y; break;
                case FocusSide: l[i] = y; r[i] = -y; break;
                case FocusLeft: l[i] = y; r[i] = 0.0f; break;
                default: l[i] = 0.0f; r[i] = y; break;
            }
        }
    }

    // the fader and pan glide across the chunk; pan on a stereo bus is a balance, mono lanes only take the fader
    const float gainTarget = audible ? dbToGain(gainDb) : 0.0f;
    const float gainStep = (gainTarget - bus.gainApplied) / static_cast<float>(n);
    const float panStep = (pan - bus.panApplied) / static_cast<float>(n);
    for (int l = 0; l < laneCount; l++) {
        const Lane& lane = lanes_[l];
        float g = bus.gainApplied, p = bus.panApplied;
        if (lane.count == 2) {
            float* a = ch[lane.first];
            float* b = ch[lane.first + 1];
            for (int i = 0; i < n; i++) {
                g += gainStep;
                p += panStep;
                a[i] *= g * (p > 0.0f ? 1.0f - p : 1.0f);
                b[i] *= g * (p < 0.0f ? 1.0f + p : 1.0f);
            }
        } else {
            float* a = ch[lane.first];
            for (int i = 0; i < n; i++) {
                g += gainStep;
                a[i] *= g;
            }
        }
    }
    bus.gainApplied = gainTarget;
    bus.panApplied = pan;
}

void Engine::runSlot(GraphSlot& slot, float* const* ch, const int* lanes, int laneCount, int n, int focus) {
    SlotControl& control = *slot.control;
    Plugin* any = nullptr;
    for (auto& p : slot.instances) {
        if (p) {
            any = p.get();
            break;
        }
    }
    // a copy that takes every channel only makes sense where the bus carries every channel
    if (slot.allChannels && focus != FocusAll) any = nullptr;
    const float target = (control.bypass.load(std::memory_order_relaxed) || any == nullptr) ? 0.0f : 1.0f;

    // knobs that moved since each copy last looked
    const uint32_t version = control.version.load(std::memory_order_acquire);
    for (int l = 0; l < MaxLanes; l++) {
        Plugin* p = slot.instances[l].get();
        if (p == nullptr || slot.seen[l] == version) continue;
        for (int i = 0; i < control.count; i++) {
            float v = control.values[i].load(std::memory_order_relaxed);
            if (slot.info != nullptr && i < static_cast<int>(slot.info->params.size())) v = slot.info->params[i].clamp(v);
            slot.scratch[i] = v;
        }
        p->update(slot.scratch.data());
        slot.seen[l] = version;
    }

    int covered[MaxChannels];
    int coveredCount = 0;
    for (int k = 0; k < laneCount; k++) {
        const Lane& lane = lanes_[lanes[k]];
        if (!slot.allChannels && slot.stereoOnly && lane.count == 1) continue;
        for (int c = lane.first; c < lane.first + lane.count; c++) covered[coveredCount++] = c;
    }

    // the input held back by the slot's latency: the dry half of a blend, and the whole signal while it's off
    for (int k = 0; k < coveredCount; k++) {
        const int c = covered[k];
        float* dry = dry_.data() + static_cast<size_t>(c) * Chunk;
        std::memcpy(dry, ch[c], sizeof(float) * n);
        if (slot.latency > 0) {
            DelayLine& line = slot.dryDelay[c];
            for (int i = 0; i < n; i++) {
                const float held = line.read(slot.latency);
                line.write(dry[i]);
                dry[i] = held;
            }
        }
    }
    if (slot.wet == 0.0f && target == 0.0f && slot.fadeLeft == 0) {
        if (slot.latency > 0) {
            for (int k = 0; k < coveredCount; k++) std::memcpy(ch[covered[k]], dry_.data() + static_cast<size_t>(covered[k]) * Chunk, sizeof(float) * n);
        }
        return;
    }

    const bool fading = slot.fadeLeft > 0;
    if (fading) {
        for (int k = 0; k < coveredCount; k++) std::memcpy(fade_.data() + static_cast<size_t>(covered[k]) * Chunk, ch[covered[k]], sizeof(float) * n);
    }

    if (slot.allChannels) {
        if (any != nullptr) any->processAll(const_cast<float**>(ch), channels, n);
        if (fading && slot.fading[0]) {
            for (int c = 0; c < channels; c++) planes_[c] = fade_.data() + static_cast<size_t>(c) * Chunk;
            slot.fading[0]->processAll(planes_.data(), channels, n);
        }
    } else {
        for (int k = 0; k < laneCount; k++) {
            const int index = lanes[k];
            const Lane& lane = lanes_[index];
            if (slot.stereoOnly && lane.count == 1) continue;
            auto run = [&](Plugin* p, float* l) {
                float* r = lane.count == 2 ? l + Chunk : mono_.data();
                if (lane.count == 1) std::memcpy(r, l, sizeof(float) * n);
                p->process(l, r, n);
                if (lane.count == 1) {
                    for (int i = 0; i < n; i++) l[i] = (l[i] + r[i]) * 0.5f;
                }
            };
            if (Plugin* p = slot.instances[index].get()) run(p, ch[lane.first]);
            if (fading && slot.fading[index]) run(slot.fading[index].get(), fade_.data() + static_cast<size_t>(lane.first) * Chunk);
        }
    }

    // the old copy fades out under the new one
    if (fading) {
        const float total = static_cast<float>(std::max(1, slot.fadeTotal));
        for (int k = 0; k < coveredCount; k++) {
            float* out = ch[covered[k]];
            const float* old = fade_.data() + static_cast<size_t>(covered[k]) * Chunk;
            for (int i = 0; i < n; i++) {
                const float f = clampf(static_cast<float>(slot.fadeLeft - i) / total, 0.0f, 1.0f);
                out[i] = out[i] * (1.0f - f) + old[i] * f;
            }
        }
        slot.fadeLeft = std::max(0, slot.fadeLeft - n);
        if (slot.fadeLeft == 0) {
            for (auto& old : slot.fading) {
                if (old && trash(nullptr, old.get())) (void)old.release();
            }
        }
    }

    // bypass crossfades instead of cutting, so switching an effect never clicks
    if (slot.wet != 1.0f || target != 1.0f) {
        const float step = (target - slot.wet) / static_cast<float>(n);
        for (int k = 0; k < coveredCount; k++) {
            float* out = ch[covered[k]];
            const float* dry = dry_.data() + static_cast<size_t>(covered[k]) * Chunk;
            float w = slot.wet;
            for (int i = 0; i < n; i++) {
                w += step;
                out[i] = dry[i] * (1.0f - w) + out[i] * w;
            }
        }
    }
    slot.wet = target;

    if (Model::get().metering.load(std::memory_order_relaxed) && any != nullptr) {
        float peak = 0.0f;
        for (int k = 0; k < coveredCount; k++) {
            const float* out = ch[covered[k]];
            for (int i = 0; i < n; i++) peak = std::max(peak, std::fabs(out[i]));
        }
        atomicMax(control.peak, peak);
        if (slot.meterCount > 0) {
            float values[MaxMeters] = {};
            any->readMeters(values);
            for (int i = 0; i < slot.meterCount; i++) control.meters[i].store(values[i], std::memory_order_relaxed);
        }
    }
}

void Engine::publishPeaks(int index, const GraphBus& bus, int n) {
    if (!Model::get().metering.load(std::memory_order_relaxed)) return;
    float left = 0.0f, right = 0.0f;
    for (const Lane& lane : lanes_) {
        const float* a = bus.buffer.data() + static_cast<size_t>(lane.first) * Chunk;
        const float* b = lane.count == 2 ? a + Chunk : a;
        for (int i = 0; i < n; i++) {
            left = std::max(left, std::fabs(a[i]));
            right = std::max(right, std::fabs(b[i]));
        }
    }
    atomicMax(peaks_[index * 2], left);
    atomicMax(peaks_[index * 2 + 1], right);
}

void Engine::takePeaks(float* out, bool* clipped) {
    for (size_t i = 0; i < peaks_.size(); i++) out[i] = std::max(out[i], peaks_[i].exchange(0.0f, std::memory_order_relaxed));
    if (clipped_.exchange(false, std::memory_order_relaxed)) *clipped = true;
}

void Engine::readLoudness(float* out) const {
    for (size_t i = 0; i < reading_.size(); i++) out[i] = reading_[i].load(std::memory_order_relaxed);
}

}  // namespace fx
