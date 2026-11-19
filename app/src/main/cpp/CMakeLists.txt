# the native halves of audio: pass (the direct usb driver and the aaudio exclusive output) and 3d sims (the room), each its own small library with no third-party code
cmake_minimum_required(VERSION 3.22.1)
project(mochypass LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(mochypass SHARED usb_pass.cpp exclusive_pass.cpp)
target_compile_options(mochypass PRIVATE -O2 -Wall -fvisibility=hidden)
# aaudio is opened at runtime, so only the log and the loader are linked; that keeps the library loadable on android 7
target_link_libraries(mochypass log dl)
# 16 kb pages on android 15 phones need the segments aligned to them
target_link_options(mochypass PRIVATE "-Wl,-z,max-page-size=16384")

add_library(mochystage SHARED stage/splitter.cpp stage/hrtf.cpp stage/tail.cpp stage/engine.cpp stage/stage_jni.cpp)
# o3 lets the compiler vectorise the ear filters, the biggest cost of the room
target_compile_options(mochystage PRIVATE -O3 -ffp-contract=fast -Wall -fvisibility=hidden)
target_link_options(mochystage PRIVATE "-Wl,-z,max-page-size=16384")
