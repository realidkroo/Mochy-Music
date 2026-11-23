// opens a code module's native library and runs its processors through the mochy_fx.h table
#include "native_plugin.h"

#include <dlfcn.h>

#include <deque>
#include <mutex>

namespace fx {

namespace {

std::mutex registryLock;
// libraries stay open for the life of the app: an instance may still be running when its module is turned off; a deque keeps every entry where it is
std::deque<NativeType>& registry() {
    static std::deque<NativeType> types;
    return types;
}

std::string text(const char* s) { return s ? std::string(s) : std::string(); }

PluginInfo infoOf(const mochy_fx_processor& p) {
    PluginInfo info;
    info.id = text(p.id);
    info.name = text(p.name);
    info.category = text(p.category);
    info.description = text(p.description);
    for (int i = 0; i < p.param_count; i++) {
        const mochy_fx_param& q = p.params[i];
        ParamInfo param{text(q.id), text(q.name), text(q.unit), q.min, q.max, q.def, static_cast<Scale>(q.scale), text(q.choices)};
        if (!(param.max > param.min)) param.max = param.min + 1.0f;
        param.def = param.clamp(param.def);
        info.params.push_back(param);
    }
    for (int i = 0; i < p.meter_count; i++) {
        const mochy_fx_meter& m = p.meters[i];
        info.meters.push_back(MeterInfo{text(m.id), text(m.name), text(m.unit), m.min, m.max});
    }
    info.stereoOnly = (p.flags & MOCHY_FX_STEREO_ONLY) != 0;
    info.showsTail = (p.flags & MOCHY_FX_SHOWS_TAIL) != 0;
    return info;
}

}  // namespace

std::vector<NativeType> loadNativeLibrary(const std::string& path, std::string* error) {
    std::lock_guard<std::mutex> guard(registryLock);
    std::vector<NativeType> found;
    for (const NativeType& t : registry()) {
        if (t.key.compare(0, path.size() + 1, path + "|") == 0) found.push_back(t);
    }
    if (!found.empty()) return found;
    void* library = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) {
        if (error) *error = text(dlerror());
        return found;
    }
    auto entry = reinterpret_cast<mochy_fx_processors_fn>(dlsym(library, MOCHY_FX_ENTRY));
    int count = 0;
    const mochy_fx_processor* const* list = entry ? entry(&count) : nullptr;
    for (int i = 0; list && i < count; i++) {
        const mochy_fx_processor* p = list[i];
        // a processor built for a newer table than this mochy knows could read past what's there
        if (p == nullptr || p->abi != MOCHY_FX_ABI || p->id == nullptr || p->create == nullptr || p->destroy == nullptr || p->process == nullptr) continue;
        NativeType type{path + "|" + p->id, infoOf(*p), p};
        registry().push_back(type);
        found.push_back(type);
    }
    if (found.empty()) {
        if (error) *error = "no_processors";
        dlclose(library);
    }
    return found;
}

const NativeType* findNative(const std::string& key) {
    std::lock_guard<std::mutex> guard(registryLock);
    for (const NativeType& t : registry()) {
        if (t.key == key) return &t;
    }
    return nullptr;
}

std::unique_ptr<NativePlugin> NativePlugin::create(const NativeType& type, double rate, int maxBlock, int channels) {
    void* self = type.api->create(rate, maxBlock, channels);
    if (self == nullptr) return nullptr;
    std::unique_ptr<NativePlugin> plugin(new NativePlugin());
    plugin->api_ = type.api;
    plugin->self_ = self;
    plugin->channels_ = channels;
    return plugin;
}

NativePlugin::~NativePlugin() {
    if (self_) api_->destroy(self_);
}

void NativePlugin::prepare(double rate, int maxBlock) {
    // the module made its instance for one rate; a new rate means a new instance
    if (self_) api_->destroy(self_);
    self_ = api_->create(rate, maxBlock, channels_);
}

void NativePlugin::reset() {
    if (self_ && api_->reset) api_->reset(self_);
}

void NativePlugin::update(const float* values) {
    if (self_ && api_->set_params) api_->set_params(self_, values);
}

void NativePlugin::process(float* left, float* right, int frames) {
    if (self_) api_->process(self_, left, right, frames);
}

int NativePlugin::latency() const { return self_ && api_->latency ? api_->latency(self_) : 0; }

void NativePlugin::readMeters(float* out) const {
    if (self_ && api_->read_meters) api_->read_meters(self_, out);
}

void NativePlugin::processAll(float** channels, int count, int frames) {
    if (self_ && api_->process_all) api_->process_all(self_, channels, count, frames);
}

void NativePlugin::bitstream(const uint8_t* data, int size, int64_t timeUs) {
    if (self_ && api_->bitstream) api_->bitstream(self_, data, size, timeUs);
}

}  // namespace fx
