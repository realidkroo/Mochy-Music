// short builders for the knob lists the effects describe themselves with
#pragma once

#include "../plugin.h"

namespace fx {

inline ParamInfo param(const char* id, const char* name, const char* unit, float min, float max, float def) {
    return ParamInfo{id, name, unit, min, max, def, Scale::Linear, ""};
}

inline ParamInfo logParam(const char* id, const char* name, const char* unit, float min, float max, float def) {
    return ParamInfo{id, name, unit, min, max, def, Scale::Log, ""};
}

inline ParamInfo intParam(const char* id, const char* name, const char* unit, float min, float max, float def) {
    return ParamInfo{id, name, unit, min, max, def, Scale::Int, ""};
}

inline ParamInfo toggle(const char* id, const char* name, bool def) {
    return ParamInfo{id, name, "", 0, 1, def ? 1.0f : 0.0f, Scale::Toggle, ""};
}

// [choices] split by '|', the value is the picked one's index
inline ParamInfo choice(const char* id, const char* name, const char* choices, int def) {
    int count = 1;
    for (const char* c = choices; *c; c++) {
        if (*c == '|') count++;
    }
    return ParamInfo{id, name, "", 0, static_cast<float>(count - 1), static_cast<float>(def), Scale::Choice, choices};
}

}  // namespace fx
