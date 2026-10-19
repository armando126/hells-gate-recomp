cmake_minimum_required(VERSION 3.22)
project(hells_gate_android LANGUAGES CXX)

set(SDL_SHARED ON CACHE BOOL "Build SDL3 as a shared Android library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Do not build static SDL3" FORCE)
set(SDL_TESTS OFF CACHE BOOL "Do not build SDL3 tests" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "Do not build SDL3 test library" FORCE)
set(SDL_ANDROID_JAR OFF CACHE BOOL "Use the app's own Android Activity" FORCE)

set(HELLS_GATE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../..")
add_subdirectory(
    "${HELLS_GATE_ROOT}/thirdparty/rexglue-sdk/thirdparty/sdl3"
    "${CMAKE_CURRENT_BINARY_DIR}/sdl3")
if(TARGET SDL3-shared)
    set_target_properties(SDL3-shared PROPERTIES
        OUTPUT_NAME "SDL3"
        DEBUG_POSTFIX ""
        RELEASE_POSTFIX ""
        RELWITHDEBINFO_POSTFIX ""
        MINSIZEREL_POSTFIX "")
endif()

add_library(hells_gate_android SHARED android_bridge.cpp)

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
