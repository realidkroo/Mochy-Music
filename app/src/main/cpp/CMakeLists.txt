# the native halves of audio: pass (the direct usb driver and the aaudio exclusive output), 3d sims (the room) and the mixer (with jamesdsp's eel2 for liveprog)
cmake_minimum_required(VERSION 3.22.1)
project(mochypass LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(mochypass SHARED usb_pass.cpp exclusive_pass.cpp store_guard.cpp)
if(DEFINED STORE_GUARD_DIR)
    target_include_directories(mochypass PRIVATE "${STORE_GUARD_DIR}")
    target_compile_definitions(mochypass PRIVATE HAS_STORE_GUARD_DATA=1)
endif()
target_compile_options(mochypass PRIVATE -O2 -Wall -fvisibility=hidden)
# aaudio is opened at runtime, so only the log and the loader are linked; that keeps the library loadable on android 7
target_link_libraries(mochypass log dl)
# 16 kb pages on android 15 phones need the segments aligned to them
target_link_options(mochypass PRIVATE "-Wl,-z,max-page-size=16384")

add_library(mochystage SHARED stage/splitter.cpp stage/hrtf.cpp stage/tail.cpp stage/engine.cpp stage/stage_jni.cpp)
# o3 lets the compiler vectorise the ear filters, the biggest cost of the room
target_compile_options(mochystage PRIVATE -O3 -ffp-contract=fast -Wall -fvisibility=hidden)
target_link_options(mochystage PRIVATE "-Wl,-z,max-page-size=16384")

# the mixer: buses, effects, loudness and liveprog, built as libmochyfx (docs/notes/mixer.md)
file(GLOB_RECURSE EEL2_SOURCES CONFIGURE_DEPENDS third_party/jamesdsp_eel2/*.c)
add_library(jamesdsp_eel2 STATIC ${EEL2_SOURCES})
target_include_directories(jamesdsp_eel2 PUBLIC third_party/jamesdsp_eel2)
# the sandbox leaves out eel's file, folder, thread and sleep builtins, so a downloaded script can only work on audio
target_compile_definitions(jamesdsp_eel2 PUBLIC CUSTOM_CMD MOCHY_EEL_SANDBOX)
# it's jamesdsp's c as it ships, old enough that clang's newer errors need turning back into warnings
target_compile_options(jamesdsp_eel2 PRIVATE -std=gnu11 -O2 -w -fPIC -fvisibility=hidden
    -Wno-error=implicit-function-declaration -Wno-error=int-conversion -Wno-error=incompatible-pointer-types -Wno-error=implicit-int)

add_library(mochyfx SHARED
    fx/engine.cpp fx/engine_build.cpp fx/model.cpp fx/liveprog.cpp fx/native_plugin.cpp fx/tail.cpp fx/fx_jni.cpp
    fx/plugins/builtin.cpp fx/reverb/progenitor.cpp)
# the header a module's own native processor is built against lives with the module api
target_include_directories(mochyfx PRIVATE fx ../../../../modules/api/native)
target_compile_options(mochyfx PRIVATE -O3 -ffp-contract=fast -Wall -fvisibility=hidden)
target_link_libraries(mochyfx jamesdsp_eel2 log dl)
target_link_options(mochyfx PRIVATE "-Wl,-z,max-page-size=16384")
