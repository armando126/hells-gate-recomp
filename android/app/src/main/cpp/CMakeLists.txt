cmake_minimum_required(VERSION 3.22)
project(hells_gate_android LANGUAGES CXX ASM)

set(SDL_SHARED ON CACHE BOOL "Build SDL3 as a shared Android library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Do not build static SDL3" FORCE)
set(SDL_TESTS OFF CACHE BOOL "Do not build SDL3 tests" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "Do not build SDL3 test library" FORCE)
set(SDL_ANDROID_JAR OFF CACHE BOOL "Use the app's own Android Activity" FORCE)

set(HELLS_GATE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../..")

# Build the same SDK/runtime that powers the desktop recompilation. The SDK's
# Android path supplies SDL3, Vulkan UI support, kernel services, and the
# runtime target consumed by the generated guest code below.
set(REXGLUE_USE_VULKAN ON CACHE BOOL "Build ReXGlue's Vulkan backend" FORCE)
set(REXGLUE_USE_D3D12 OFF CACHE BOOL "Android uses Vulkan" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "Disable Tracy on Android" FORCE)
set(REXGLUE_ENABLE_PERF_COUNTERS OFF CACHE BOOL "Disable perf counters on Android" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "Do not build ReXGlue tests" FORCE)
add_subdirectory(
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk"
    "${CMAKE_CURRENT_BINARY_DIR}/rexglue-sdk")

# The temporary Android performance probes live with the app sources but are
# also included by the SDK's presentation and audio translation units.
foreach(HELLSGATE_PERF_TARGET rexui rexaudio)
    if(TARGET ${HELLSGATE_PERF_TARGET})
        target_include_directories(${HELLSGATE_PERF_TARGET} PRIVATE
            "${HELLS_GATE_ROOT}/src")
    endif()
endforeach()

# The Android app consumes rexgpu-xenos' graphics implementation directly.
# ReXGlue normally hides all plugin symbols because the desktop runtime loads
# the plugin through its C ABI, but these C++ symbols are needed by the app's
# renderer and must remain visible in this shared library.
set_target_properties(rexgpu-xenos PROPERTIES
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF)

set(DILIGENT_CORE_DIR "${HELLS_GATE_ROOT}/thirdparty/diligent-core")
set(DILIGENT_NO_HLSL ON CACHE BOOL "Disable HLSL support in DiligentCore" FORCE)
set(DILIGENT_NO_GLSLANG ON CACHE BOOL "Disable DiligentCore glslang" FORCE)
set(DILIGENT_NO_ARCHIVER ON CACHE BOOL "Do not build DiligentCore Archiver" FORCE)
set(DILIGENT_INSTALL_CORE OFF CACHE BOOL "Do not install DiligentCore" FORCE)
set(DILIGENT_BUILD_TESTS OFF CACHE BOOL "Do not build DiligentCore tests" FORCE)
add_subdirectory(
    "${DILIGENT_CORE_DIR}"
    "${CMAKE_CURRENT_BINARY_DIR}/diligent-core"
    EXCLUDE_FROM_ALL)

if(NOT EXISTS "${HELLS_GATE_ROOT}/generated/default/sources.cmake")
    message(FATAL_ERROR "Generated game sources are missing; run ReXGlue codegen first")
endif()
include("${HELLS_GATE_ROOT}/generated/default/sources.cmake")

# ReXGlue disables glslang's standalone binaries, which also prevents its
# default-resource-limits library from being created. The runtime shader
# compiler still needs DefaultTBuiltInResource, so provide that small library
# explicitly for the Android consumer.
if(NOT TARGET glslang-default-resource-limits)
    add_library(glslang-default-resource-limits STATIC
        "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/glslang/StandAlone/ResourceLimits.cpp"
        "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/glslang/StandAlone/resource_limits_c.cpp")
    target_include_directories(glslang-default-resource-limits PUBLIC
        "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/glslang/StandAlone"
        "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/glslang")
    set_property(TARGET glslang-default-resource-limits PROPERTY POSITION_INDEPENDENT_CODE ON)
endif()

file(READ "${HELLS_GATE_ROOT}/thirdparty/smaa/SMAA.hlsl" SMAA_SHADER_TEXT)
configure_file(
    "${CMAKE_CURRENT_LIST_DIR}/smaa_shader.h.in"
    "${CMAKE_CURRENT_BINARY_DIR}/generated/smaa_shader.h"
    @ONLY)

# Dante's renderer is driven by the TU2 D3D hooks. Keep these enabled for the
# Android target; without them the guest and audio run, but the custom renderer
# never receives Clear/Resolve/Draw/Swap callbacks and the surface stays black.
option(HELLSGATE_TU2_HOOKS "Build hooks for the TU2 function map" ON)

set(HELLS_GATE_APP_SOURCES
    "${CMAKE_CURRENT_LIST_DIR}/android_performance.cpp"
    "${HELLS_GATE_ROOT}/src/main.cpp"
    "${HELLS_GATE_ROOT}/src/timer_resolution.cpp"
    "${HELLS_GATE_ROOT}/src/user_paths.cpp"
    "${HELLS_GATE_ROOT}/src/overlay/achievement_sound.cpp"
    "${HELLS_GATE_ROOT}/src/overlay/achievement_ui.cpp"
    "${HELLS_GATE_ROOT}/src/overlay/fps_overlay.cpp"
    "${HELLS_GATE_ROOT}/src/overlay/overlay_style.cpp"
    "${HELLS_GATE_ROOT}/src/overlay/shader_progress.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/renderer.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/draw_renderer.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/pipeline_storage.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/png_dump.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/button_glyphs_android.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/shader_cache.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/smaa_pass.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/texture_cache.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/renderer_config.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/ui_overlay.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/vulkan_device.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/renderdoc_capture.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/state_verifier.cpp"
    "${HELLS_GATE_ROOT}/src/renderer/guest_gpu_state.cpp"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/src/ui/rex_app.cpp"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/src/ui/windowed_app_main_sdl.cpp"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/src/graphics/register_file.cpp")

if(HELLSGATE_TU2_HOOKS)
    list(APPEND HELLS_GATE_APP_SOURCES
        "${HELLS_GATE_ROOT}/src/startup_hooks.cpp"
        "${HELLS_GATE_ROOT}/src/renderer/d3d_hooks.cpp")
endif()

add_library(hells_gate_recompiled OBJECT ${GENERATED_SOURCES} ${HELLS_GATE_APP_SOURCES})
target_include_directories(hells_gate_recompiled PRIVATE
    "${HELLS_GATE_ROOT}"
    "${HELLS_GATE_ROOT}/src"
    "${HELLS_GATE_ROOT}/generated/default"
    "${CMAKE_CURRENT_BINARY_DIR}/generated"
    "${DILIGENT_CORE_DIR}"
    "${DILIGENT_CORE_DIR}/Graphics"
    "${DILIGENT_CORE_DIR}/Graphics/GraphicsEngine/interface"
    "${DILIGENT_CORE_DIR}/Graphics/GraphicsEngineVulkan/interface"
    "${DILIGENT_CORE_DIR}/Platforms/interface"
    "${DILIGENT_CORE_DIR}/Platforms/Android/interface"
    "${DILIGENT_CORE_DIR}/Primitives/interface"
    "${DILIGENT_CORE_DIR}/Common/interface"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/imgui"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/xxHash"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/glslang"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/renderdoc"
    "${CMAKE_CURRENT_BINARY_DIR}/rexglue-sdk/thirdparty/glslang/include")
target_link_libraries(hells_gate_recompiled PRIVATE
    rex::runtime
    rex::gpu-xenos
    glslang::SPIRV
    glslang
    glslang-default-resource-limits
    MachineIndependent
    GenericCodeGen
    OSDependent
    OGLCompiler
    Diligent-GraphicsEngineVk-static
    Diligent-GraphicsTools
    Diligent-Common
    volk::volk
    Vulkan::Headers)
target_compile_features(hells_gate_recompiled PRIVATE cxx_std_23)
target_compile_definitions(hells_gate_recompiled PRIVATE
    REXGLUE_BUILD_CONFIG="$<CONFIG>"
    # The Android toolchain exposes ANDROID, but this SDK's platform header
    # selects its Android branches from REX_PLATFORM_ANDROID.
    REX_PLATFORM_ANDROID=1)
target_compile_options(hells_gate_recompiled PRIVATE -g0)

add_library(hells_gate_android SHARED android_bridge.cpp)
target_sources(hells_gate_android PRIVATE android_touch.cpp)
target_include_directories(hells_gate_android PRIVATE
    "${HELLS_GATE_ROOT}"
    "${HELLS_GATE_ROOT}/src"
    "${HELLS_GATE_ROOT}/generated/default"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/include"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/simde"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/imgui"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/fmt/include"
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/spdlog/include")
target_link_libraries(hells_gate_android PRIVATE hells_gate_recompiled)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)
target_link_libraries(hells_gate_android PRIVATE
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
    SDL3::SDL3)
target_compile_features(hells_gate_android PRIVATE cxx_std_23)
target_compile_options(hells_gate_android PRIVATE -march=armv8-a)
target_link_options(hells_gate_android PRIVATE "-Wl,-z,max-page-size=16384")

# Match NFSMW's Android Release build. ThinLTO is particularly valuable here:
# the generated guest code and the native renderer otherwise remain separate
# compilation units at the exact boundary where most frame time is spent.
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    cmake_host_system_information(RESULT HELLS_GATE_HOST_CORES QUERY NUMBER_OF_LOGICAL_CORES)
    target_compile_options(hells_gate_recompiled PRIVATE -flto=thin)
    target_compile_options(hells_gate_android PRIVATE -flto=thin)
    target_link_options(hells_gate_android PRIVATE
        -flto=thin
        "-Wl,--thinlto-jobs=${HELLS_GATE_HOST_CORES}"
        "-Wl,--thinlto-cache-dir=${CMAKE_CURRENT_BINARY_DIR}/thinlto-cache"
        -Wl,--gc-sections -Wl,--icf=safe)
endif()

# SDLActivity loads the application entry point as libmain.so and invokes
# SDL_main on SDL's own Android thread. Keep the name stable in every build
# variant; the game must not be started through a custom JNI pump.
set_target_properties(hells_gate_android PROPERTIES
    OUTPUT_NAME "main"
    DEBUG_POSTFIX ""
    RELEASE_POSTFIX ""
    RELWITHDEBINFO_POSTFIX ""
    MINSIZEREL_POSTFIX "")

if(TARGET SDL3-shared)
    set_target_properties(SDL3-shared PROPERTIES
        OUTPUT_NAME "SDL3"
        DEBUG_POSTFIX ""
        RELEASE_POSTFIX ""
        RELWITHDEBINFO_POSTFIX ""
        MINSIZEREL_POSTFIX "")
endif()
