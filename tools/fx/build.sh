#!/bin/sh
# builds the mixer's host tools and tests with the mac's own compiler; run from anywhere, results land in tools/fx/build
set -e
here=$(cd "$(dirname "$0")" && pwd)
fx="$here/../../app/src/main/cpp/fx"
eel="$here/../../app/src/main/cpp/third_party/jamesdsp_eel2"
api="$here/../../modules/api/native"
out="$here/build"
mkdir -p "$out/eel"
for f in $(cd "$eel" && find . -name '*.c'); do
    o="$out/eel/$(echo "$f" | tr '/.' '__').o"
    [ -f "$o" ] || cc -std=gnu11 -O2 -w -DCUSTOM_CMD -DMOCHY_EEL_SANDBOX -I"$eel" -c "$eel/$f" -o "$o"
done
cxx="c++ -std=c++17 -O2 -w -I$fx -I$eel -I$api"
core="$fx/engine.cpp $fx/engine_build.cpp $fx/model.cpp $fx/native_plugin.cpp $fx/liveprog.cpp $fx/tail.cpp $fx/plugins/builtin.cpp $fx/reverb/progenitor.cpp"
$cxx "$here/dump_builtins.cpp" "$fx/plugins/builtin.cpp" "$fx/reverb/progenitor.cpp" -o "$out/dump_builtins"
$cxx "$here/reverb_test.cpp" "$fx/reverb/progenitor.cpp" -o "$out/reverb_test"
$cxx "$here/loudness_test.cpp" -o "$out/loudness_test"
$cxx "$here/engine_test.cpp" $core "$out"/eel/*.o -o "$out/engine_test"
$cxx "$here/liveprog_test.cpp" "$fx/liveprog.cpp" "$out"/eel/*.o -o "$out/liveprog_test"
echo "built: dump_builtins reverb_test loudness_test engine_test liveprog_test (liveprog_test takes .eel files)"
