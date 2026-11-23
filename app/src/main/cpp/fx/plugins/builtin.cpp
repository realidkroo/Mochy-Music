// every effect mochy ships, in the order the add sheet lists them
#include "colour.h"
#include "dynamics.h"
#include "reverbs.h"
#include "tone.h"

namespace fx {

namespace {

template <typename T>
std::unique_ptr<Plugin> make() {
    return std::make_unique<T>();
}

template <typename T>
PluginType entry() {
    return PluginType{T::info(), &make<T>};
}

}  // namespace

const std::vector<PluginType>& builtinTypes() {
    static const std::vector<PluginType> types = {
        entry<SpacePlugin>(),
        entry<ProgenitorPlugin>(),
        entry<EqPlugin>(),
        entry<FilterPlugin>(),
        entry<CompressorPlugin>(),
        entry<LimiterPlugin>(),
        entry<GatePlugin>(),
        entry<TransientPlugin>(),
        entry<SaturationPlugin>(),
        entry<DelayPlugin>(),
        entry<ChorusPlugin>(),
        entry<StereoPlugin>(),
        entry<UtilityPlugin>(),
    };
    return types;
}

const PluginType* findBuiltin(const std::string& id) {
    for (const PluginType& type : builtinTypes()) {
        if (type.info.id == id) return &type;
    }
    return nullptr;
}

}  // namespace fx
