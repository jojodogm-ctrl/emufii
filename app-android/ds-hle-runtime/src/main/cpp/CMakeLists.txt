cmake_minimum_required(VERSION 3.22.1)
project(emufii_hle_broker LANGUAGES CXX)

# Standalone transport broker only. The pinned emulator .so is never rebuilt
# or linked to this helper; it remains the owner of its original SDK runtime.
add_library(emufii_hle_broker SHARED
    host_broker_jni.cpp
    upstream/src/frontend/wasm/BrowserBroker.cpp)
target_compile_features(emufii_hle_broker PRIVATE cxx_std_17)
target_compile_definitions(emufii_hle_broker PRIVATE NIFI2_PRIVATE_WMSDK_WAN_WINDOW=1)
target_compile_options(emufii_hle_broker PRIVATE -O2 -fexceptions -frtti
    -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_options(emufii_hle_broker PRIVATE "-Wl,--exclude-libs,ALL")
target_include_directories(emufii_hle_broker PRIVATE upstream/src)
