// an effect's impulse response, measured for the decay picture
#pragma once

#include <string>
#include <vector>

namespace fx {

// [points] slices of (whole, bass, middle, treble) level in db, then the four decay times in seconds (-1 when it never fades enough to tell)
std::vector<float> renderTail(const std::string& type, const std::string& source, const std::vector<std::string>& vars,
                              const std::vector<float>& values, int rate, float seconds, int points);

}  // namespace fx
