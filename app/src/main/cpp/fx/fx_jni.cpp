// the jni half of the mixer; engine handles are only ever used on their player's playback thread (MixerNative.kt)
#include <jni.h>

#include "engine.h"
#include "liveprog.h"
#include "model.h"
#include "native_plugin.h"
#include "tail.h"

#include <string>
#include <vector>

using fx::Engine;
using fx::Model;

namespace {

Engine* engineOf(jlong handle) { return reinterpret_cast<Engine*>(handle); }

std::string str(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::vector<std::string> strings(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> out;
    if (array == nullptr) return out;
    const jsize count = env->GetArrayLength(array);
    for (jsize i = 0; i < count; i++) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        out.push_back(str(env, item));
        env->DeleteLocalRef(item);
    }
    return out;
}

std::vector<float> floats(JNIEnv* env, jfloatArray array) {
    std::vector<float> out;
    if (array == nullptr) return out;
    out.resize(static_cast<size_t>(env->GetArrayLength(array)));
    if (!out.empty()) env->GetFloatArrayRegion(array, 0, static_cast<jsize>(out.size()), out.data());
    return out;
}

// java's modified utf-8 can't hold every byte a script prints, so anything odd becomes '?'
jstring text(JNIEnv* env, const std::string& s) {
    std::string safe;
    safe.reserve(s.size());
    for (unsigned char c : s) safe += (c >= 0x20 && c < 0x7f) || c == '\n' || c == '\t' ? static_cast<char>(c) : '?';
    return env->NewStringUTF(safe.c_str());
}

void appendJson(std::string& out, const std::string& s) {
    out += '"';
    for (char c : s) {
        if (c == '"' || c == '\\') {
            out += '\\';
            out += c;
        } else if (static_cast<unsigned char>(c) < 0x20) {
            out += ' ';
        } else {
            out += c;
        }
    }
    out += '"';
}

void appendNumber(std::string& out, float v) {
    char buffer[32];
    snprintf(buffer, sizeof(buffer), "%.6g", static_cast<double>(v));
    out += buffer;
}

// one effect kind as the kotlin catalog reads it
void appendInfo(std::string& out, const std::string& key, const fx::PluginInfo& info) {
    out += "{\"key\":";
    appendJson(out, key);
    out += ",\"id\":";
    appendJson(out, info.id);
    out += ",\"name\":";
    appendJson(out, info.name);
    out += ",\"category\":";
    appendJson(out, info.category);
    out += ",\"description\":";
    appendJson(out, info.description);
    out += ",\"showsTail\":";
    out += info.showsTail ? "true" : "false";
    out += ",\"stereoOnly\":";
    out += info.stereoOnly ? "true" : "false";
    out += ",\"params\":[";
    for (size_t i = 0; i < info.params.size(); i++) {
        const fx::ParamInfo& p = info.params[i];
        if (i) out += ',';
        out += "{\"id\":";
        appendJson(out, p.id);
        out += ",\"name\":";
        appendJson(out, p.name);
        out += ",\"unit\":";
        appendJson(out, p.unit);
        out += ",\"min\":";
        appendNumber(out, p.min);
        out += ",\"max\":";
        appendNumber(out, p.max);
        out += ",\"def\":";
        appendNumber(out, p.def);
        out += ",\"scale\":";
        out += std::to_string(static_cast<int>(p.scale));
        out += ",\"choices\":";
        appendJson(out, p.choices);
        out += '}';
    }
    out += "],\"meters\":[";
    for (size_t i = 0; i < info.meters.size(); i++) {
        const fx::MeterInfo& m = info.meters[i];
        if (i) out += ',';
        out += "{\"id\":";
        appendJson(out, m.id);
        out += ",\"name\":";
        appendJson(out, m.name);
        out += ",\"unit\":";
        appendJson(out, m.unit);
        out += ",\"min\":";
        appendNumber(out, m.min);
        out += ",\"max\":";
        appendNumber(out, m.max);
        out += '}';
    }
    out += "],\"presets\":[";
    for (size_t i = 0; i < info.presets.size(); i++) {
        if (i) out += ',';
        out += "{\"name\":";
        appendJson(out, info.presets[i].name);
        out += ",\"values\":[";
        for (size_t v = 0; v < info.presets[i].values.size(); v++) {
            if (v) out += ',';
            appendNumber(out, info.presets[i].values[v]);
        }
        out += "]}";
    }
    out += "]}";
}

}  // namespace

#define FX(name) Java_com_mochyy_mochymusic_eq_mixer_MixerNative_##name

extern "C" {

JNIEXPORT jlong JNICALL FX(create)(JNIEnv*, jobject, jint rate, jint channels) {
    if (rate <= 0 || channels < 1 || channels > 12) return 0;
    return reinterpret_cast<jlong>(new Engine(rate, channels));
}

JNIEXPORT void JNICALL FX(destroy)(JNIEnv*, jobject, jlong handle) { delete engineOf(handle); }

JNIEXPORT jboolean JNICALL FX(process)(JNIEnv* env, jobject, jlong handle, jobject input, jint offset, jobject output, jint frames) {
    Engine* engine = engineOf(handle);
    auto* in = static_cast<uint8_t*>(env->GetDirectBufferAddress(input));
    auto* out = static_cast<int16_t*>(env->GetDirectBufferAddress(output));
    if (engine == nullptr || in == nullptr || out == nullptr || frames <= 0) return JNI_FALSE;
    return engine->process(reinterpret_cast<const int16_t*>(in + offset), out, frames) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL FX(reset)(JNIEnv*, jobject, jlong handle) {
    if (Engine* engine = engineOf(handle)) engine->reset();
}

JNIEXPORT void JNICALL FX(bitstream)(JNIEnv* env, jobject, jlong handle, jobject data, jint offset, jint size, jlong timeUs) {
    Engine* engine = engineOf(handle);
    auto* bytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(data));
    if (engine != nullptr && bytes != nullptr && size > 0) engine->bitstream(bytes + offset, size, timeUs);
}

JNIEXPORT void JNICALL FX(begin)(JNIEnv*, jobject) { Model::get().begin(); }

JNIEXPORT void JNICALL FX(bus)(JNIEnv*, jobject, jint id, jboolean songIn, jint focus, jint sends, jboolean master) {
    Model::get().addBus(id, songIn, focus, static_cast<uint32_t>(sends), master);
}

JNIEXPORT void JNICALL FX(slot)(JNIEnv* env, jobject, jint bus, jlong uid, jstring type, jstring source, jobjectArray vars, jint revision,
                                jfloatArray values) {
    const std::vector<float> v = floats(env, values);
    Model::get().addSlot(bus, static_cast<uint64_t>(uid), str(env, type), str(env, source), strings(env, vars), static_cast<uint32_t>(revision),
                         v.data(), static_cast<int>(v.size()));
}

JNIEXPORT void JNICALL FX(commit)(JNIEnv*, jobject, jboolean enabled) { Model::get().commit(enabled); }

JNIEXPORT void JNICALL FX(busControl)(JNIEnv*, jobject, jint id, jfloat gainDb, jfloat pan, jboolean mute, jboolean solo) {
    if (id < 0 || id > fx::Master) return;
    fx::BusControl& c = Model::get().bus[id];
    c.gainDb.store(gainDb, std::memory_order_relaxed);
    c.pan.store(pan, std::memory_order_relaxed);
    c.mute.store(mute, std::memory_order_relaxed);
    c.solo.store(solo, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL FX(sendLevel)(JNIEnv*, jobject, jint from, jint to, jfloat level) {
    if (from < 0 || from >= fx::MaxBuses || to < 0 || to > fx::Master) return;
    Model::get().send[from][to].store(level < 0 ? 0 : (level > 1 ? 1 : level), std::memory_order_relaxed);
}

JNIEXPORT void JNICALL FX(params)(JNIEnv* env, jobject, jlong uid, jfloatArray values) {
    if (auto control = Model::get().control(static_cast<uint64_t>(uid))) {
        const std::vector<float> v = floats(env, values);
        control->set(v.data(), static_cast<int>(v.size()));
    }
}

JNIEXPORT void JNICALL FX(bypass)(JNIEnv*, jobject, jlong uid, jboolean bypassed) {
    if (auto control = Model::get().control(static_cast<uint64_t>(uid))) control->bypass.store(bypassed, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL FX(metering)(JNIEnv*, jobject, jboolean on) { Model::get().metering.store(on, std::memory_order_relaxed); }

JNIEXPORT void JNICALL FX(peaks)(JNIEnv* env, jobject, jfloatArray out) {
    const int count = (fx::MaxBuses + 1) * 2 + 1;
    if (env->GetArrayLength(out) < count) return;
    float values[(fx::MaxBuses + 1) * 2 + 1];
    Model::get().readPeaks(values, count);
    env->SetFloatArrayRegion(out, 0, count, values);
}

JNIEXPORT void JNICALL FX(loudness)(JNIEnv* env, jobject, jfloatArray out) {
    if (env->GetArrayLength(out) < 5) return;
    float values[5];
    Model::get().readLoudness(values);
    env->SetFloatArrayRegion(out, 0, 5, values);
}

JNIEXPORT void JNICALL FX(resetLoudness)(JNIEnv*, jobject) { Model::get().resetLoudness(); }

JNIEXPORT jboolean JNICALL FX(slotReadings)(JNIEnv* env, jobject, jlong uid, jfloatArray out) {
    auto control = Model::get().control(static_cast<uint64_t>(uid));
    const jsize size = env->GetArrayLength(out);
    if (!control || size < 1) return JNI_FALSE;
    float values[1 + fx::MaxMeters] = {};
    values[0] = control->peak.exchange(0.0f, std::memory_order_relaxed);
    for (int i = 0; i < fx::MaxMeters; i++) values[1 + i] = control->meters[i].load(std::memory_order_relaxed);
    env->SetFloatArrayRegion(out, 0, std::min<jsize>(size, 1 + fx::MaxMeters), values);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL FX(slotProblem)(JNIEnv* env, jobject, jlong uid) {
    auto control = Model::get().control(static_cast<uint64_t>(uid));
    if (!control) return nullptr;
    const std::string problem = control->takeProblem();
    return problem.empty() ? nullptr : text(env, problem);
}

JNIEXPORT jstring JNICALL FX(console)(JNIEnv* env, jobject, jlong uid) {
    auto control = Model::get().control(static_cast<uint64_t>(uid));
    if (!control || !control->report) return text(env, "");
    return text(env, control->report->takeConsole());
}

JNIEXPORT void JNICALL FX(resume)(JNIEnv*, jobject, jlong uid) {
    auto control = Model::get().control(static_cast<uint64_t>(uid));
    if (!control || !control->report) return;
    std::lock_guard<std::mutex> guard(control->report->lock);
    control->report->problem.clear();
    control->report->paused.store(false, std::memory_order_relaxed);
}

JNIEXPORT jstring JNICALL FX(builtins)(JNIEnv* env, jobject) {
    std::string out = "[";
    bool first = true;
    for (const fx::PluginType& type : fx::builtinTypes()) {
        if (!first) out += ',';
        first = false;
        appendInfo(out, type.info.id, type.info);
    }
    out += ']';
    return text(env, out);
}

JNIEXPORT jstring JNICALL FX(loadLibrary)(JNIEnv* env, jobject, jstring path) {
    std::string error;
    const std::vector<fx::NativeType> types = fx::loadNativeLibrary(str(env, path), &error);
    if (types.empty()) return nullptr;
    std::string out = "[";
    for (size_t i = 0; i < types.size(); i++) {
        if (i) out += ',';
        appendInfo(out, types[i].key, types[i].info);
    }
    out += ']';
    return text(env, out);
}

JNIEXPORT jstring JNICALL FX(checkScript)(JNIEnv* env, jobject, jstring source) { return text(env, fx::checkScript(str(env, source))); }

JNIEXPORT jfloatArray JNICALL FX(renderTail)(JNIEnv* env, jobject, jstring type, jstring source, jobjectArray vars, jfloatArray values, jint rate,
                                             jfloat seconds, jint points) {
    const std::vector<float> out = fx::renderTail(str(env, type), str(env, source), strings(env, vars), floats(env, values), rate, seconds, points);
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(out.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(out.size()), out.data());
    return array;
}

}
