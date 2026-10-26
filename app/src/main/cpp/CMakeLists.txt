cmake_minimum_required(VERSION 3.22.1)

project("switchpadsr_engine")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for ARMv8/v9 (Snapdragon 8 Elite / Oryon)
add_compile_options(-O3 -ffast-math -Wall -Wextra)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/engine
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# Core Native Engine sources
add_library(switchpadsr_engine SHARED
    jni/jni_bridge.cpp
    engine/vulkan_renderer.cpp
    engine/sr_pipeline.cpp
    engine/uvc_capture.cpp
    engine/audio_engine.cpp
)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)
find_library(aaudio-lib aaudio)

# Link native libraries
target_link_libraries(switchpadsr_engine
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
    ${aaudio-lib}
)
