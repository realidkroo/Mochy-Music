// runs jamesdsp's eel2 engine (third_party/jamesdsp_eel2) as a mixer effect
#include "liveprog.h"

#include <chrono>
#include <cmath>
#include <cstring>
#include <regex>

extern "C" {
#include "ns-eel.h"
}

namespace {

std::once_flag started;
// compiling touches the engine's shared tables, so only one script builds at a time
std::recursive_mutex compileLock;
// eel's printf has no idea which script called it, so whoever runs a script says so first
thread_local fx::ScriptReport* speaking = nullptr;

constexpr size_t ConsoleLimit = 16 * 1024;

void start() {
    std::call_once(started, [] { NSEEL_start(); });
}

// a jamesdsp script sets its knobs' defaults in @init ("dB = -8;"), so the knob's value is written there instead
std::string withValues(std::string init, const std::vector<std::string>& vars, const std::vector<float>& values) {
    for (size_t i = 0; i < vars.size() && i < values.size(); i++) {
        if (vars[i].empty()) continue;
        const std::regex assignment("(^|[^A-Za-z0-9_.])" + vars[i] + "\\s*=\\s*-?[0-9]+(\\.[0-9]*)?\\s*;");
        char value[64];
        snprintf(value, sizeof(value), "%.9g", static_cast<double>(values[i]));
        init = std::regex_replace(init, assignment, "$1" + vars[i] + " = " + value + ";", std::regex_constants::format_first_only);
    }
    return init;
}

}  // namespace

// eel2's printf lands here
extern "C" void writeCircularStringBuf(char* text) {
    if (speaking != nullptr && text != nullptr) speaking->print(text);
}

namespace fx {

void ScriptReport::print(const char* text) {
    // the playback thread never waits on the ui: a line that would have to wait is dropped
    std::unique_lock<std::mutex> guard(lock, std::try_to_lock);
    if (!guard.owns_lock()) return;
    console += text;
    if (console.size() > ConsoleLimit) console.erase(0, console.size() - ConsoleLimit / 2);
}

std::string ScriptReport::takeConsole() {
    std::lock_guard<std::mutex> guard(lock);
    std::string out;
    out.swap(console);
    return out;
}

ScriptSource ScriptSource::split(const std::string& text) {
    ScriptSource out;
    std::string* current = nullptr;
    int line = 0;
    size_t pos = 0;
    while (pos <= text.size()) {
        size_t end = text.find('\n', pos);
        if (end == std::string::npos) end = text.size();
        const std::string row = text.substr(pos, end - pos);
        const size_t first = row.find_first_not_of(" \t\r");
        if (first != std::string::npos && row[first] == '@') {
            const std::string name = row.substr(first, row.find_first_of(" \t\r", first) - first);
            // @gfx, @serialize and anything else has no meaning here, so it's skipped
            if (name == "@init") { current = &out.init; out.initLine = line + 1; }
            else if (name == "@sample") { current = &out.sample; out.sampleLine = line + 1; }
            else if (name == "@slider") { current = &out.slider; out.sliderLine = line + 1; }
            else if (name == "@block") { current = &out.block; out.blockLine = line + 1; }
            else current = nullptr;
        } else if (current != nullptr) {
            *current += row;
            *current += '\n';
        }
        line++;
        pos = end + 1;
    }
    return out;
}

std::unique_ptr<LiveprogPlugin> LiveprogPlugin::compile(const std::string& source, const std::vector<std::string>& vars,
                                                        const std::vector<float>& values, double rate,
                                                        std::shared_ptr<ScriptReport> report, std::string* error) {
    start();
    const ScriptSource parts = ScriptSource::split(source);
    if (parts.sample.find_first_not_of(" \t\r\n") == std::string::npos) {
        if (error) *error = "missing_sample";
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> guard(compileLock);
    std::unique_ptr<LiveprogPlugin> plugin(new LiveprogPlugin());
    plugin->report_ = std::move(report);
    plugin->rate_ = rate;
    NSEEL_VMCTX vm = NSEEL_VM_alloc();
    if (vm == nullptr) {
        if (error) *error = "no_memory";
        return nullptr;
    }
    plugin->vm_ = vm;
    plugin->srate_ = NSEEL_VM_regvar(vm, "srate");
    plugin->spl0_ = NSEEL_VM_regvar(vm, "spl0");
    plugin->spl1_ = NSEEL_VM_regvar(vm, "spl1");
    plugin->samplesblock_ = NSEEL_VM_regvar(vm, "samplesblock");
    *plugin->srate_ = static_cast<float>(rate);
    *NSEEL_VM_regvar(vm, "num_ch") = 2.0f;
    for (size_t i = 0; i < vars.size(); i++) {
        float* var = vars[i].empty() ? nullptr : NSEEL_VM_regvar(vm, vars[i].c_str());
        if (var != nullptr && i < values.size()) *var = values[i];
        plugin->vars_.push_back(var);
    }
    auto build = [&](const std::string& code, int line, bool common) -> void* {
        if (code.find_first_not_of(" \t\r\n") == std::string::npos) return nullptr;
        void* handle = NSEEL_code_compile_ex(vm, code.c_str(), line, common ? NSEEL_CODE_COMPILE_FLAG_COMMONFUNCS : 0);
        if (handle == nullptr && error) {
            const char* message = NSEEL_code_getcodeerror(vm);
            *error = std::string("compile:") + (message ? message : "?");
        }
        return handle;
    };
    // functions written in @init are usable everywhere, as in jamesdsp
    const std::string init = withValues(parts.init, vars, values);
    plugin->init_ = build(init, parts.initLine, true);
    if (plugin->init_ == nullptr && init.find_first_not_of(" \t\r\n") != std::string::npos) return nullptr;
    plugin->sample_ = build(parts.sample, parts.sampleLine, false);
    if (plugin->sample_ == nullptr) return nullptr;
    plugin->slider_ = build(parts.slider, parts.sliderLine, false);
    if (plugin->slider_ == nullptr && !parts.slider.empty() && parts.slider.find_first_not_of(" \t\r\n") != std::string::npos) return nullptr;
    plugin->block_ = build(parts.block, parts.blockLine, false);
    if (plugin->block_ == nullptr && !parts.block.empty() && parts.block.find_first_not_of(" \t\r\n") != std::string::npos) return nullptr;

    speaking = plugin->report_.get();
    if (plugin->init_) NSEEL_code_execute(plugin->init_);
    // knobs win over whatever @init left behind, then @slider works out anything that follows from them
    for (size_t i = 0; i < plugin->vars_.size() && i < values.size(); i++) {
        if (plugin->vars_[i]) *plugin->vars_[i] = values[i];
    }
    if (plugin->slider_) NSEEL_code_execute(plugin->slider_);
    speaking = nullptr;
    return plugin;
}

LiveprogPlugin::~LiveprogPlugin() {
    std::lock_guard<std::recursive_mutex> guard(compileLock);
    for (void* code : {init_, sample_, slider_, block_}) {
        if (code) NSEEL_code_free(code);
    }
    if (vm_) NSEEL_VM_free(vm_);
}

void LiveprogPlugin::prepare(double rate, int) {
    rate_ = rate;
    if (srate_) *srate_ = static_cast<float>(rate);
}

void LiveprogPlugin::update(const float* values) {
    for (size_t i = 0; i < vars_.size(); i++) {
        if (vars_[i]) *vars_[i] = values[i];
    }
    if (slider_) {
        speaking = report_.get();
        NSEEL_code_execute(slider_);
        speaking = nullptr;
    }
}

void LiveprogPlugin::process(float* left, float* right, int frames) {
    if (report_ && report_->paused.load(std::memory_order_relaxed)) return;
    const auto began = std::chrono::steady_clock::now();
    speaking = report_.get();
    if (block_) {
        *samplesblock_ = static_cast<float>(frames);
        NSEEL_code_execute(block_);
    }
    for (int i = 0; i < frames; i++) {
        *spl0_ = left[i];
        *spl1_ = right[i];
        NSEEL_code_execute(sample_);
        const float l = *spl0_, r = *spl1_;
        // a script that divides by zero once would otherwise silence everything after it for good
        const bool good = std::isfinite(l) && std::isfinite(r);
        left[i] = good ? l : 0.0f;
        right[i] = good ? r : 0.0f;
        if (!good) {
            *spl0_ = *spl1_ = 0.0f;
            badSamples_++;
        }
    }
    speaking = nullptr;
    if (!report_) return;
    if (badSamples_ > static_cast<int>(rate_)) {
        std::lock_guard<std::mutex> guard(report_->lock);
        report_->problem = "bad_numbers";
        badSamples_ = 0;
    }
    // more than 60% of the block's own time for a second's worth of blocks means it can't keep up, so it's paused before the song stutters
    const double spent = std::chrono::duration<double>(std::chrono::steady_clock::now() - began).count();
    const double budget = frames / rate_;
    slowBlocks_ = spent > budget * 0.6 ? slowBlocks_ + frames : 0;
    if (slowBlocks_ > static_cast<int>(rate_)) {
        report_->paused.store(true, std::memory_order_relaxed);
        std::lock_guard<std::mutex> guard(report_->lock);
        report_->problem = "too_slow";
    }
}

std::string checkScript(const std::string& source) {
    std::string error;
    auto report = std::make_shared<ScriptReport>();
    auto plugin = LiveprogPlugin::compile(source, {}, {}, 48000, report, &error);
    return plugin ? std::string() : (error.empty() ? std::string("compile:?") : error);
}

}  // namespace fx
