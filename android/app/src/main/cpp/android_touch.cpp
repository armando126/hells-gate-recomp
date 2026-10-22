#include <jni.h>

#include <cstdint>

#include "android_perf_trace.h"

extern "C" void rex_sdl_set_touch_gamepad_state(uint16_t buttons, int16_t left_x, int16_t left_y,
                                                 int16_t right_x, int16_t right_y,
                                                 uint8_t left_trigger, uint8_t right_trigger);
extern "C" uint64_t hellsgate_android_guest_frame_count();

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_TouchControlsView_nativeSetTouchState(
    JNIEnv*, jclass, jint buttons, jint left_x, jint left_y, jint right_x, jint right_y,
    jint left_trigger, jint right_trigger) {
  rex_sdl_set_touch_gamepad_state(
      static_cast<uint16_t>(buttons), static_cast<int16_t>(left_x), static_cast<int16_t>(left_y),
      static_cast<int16_t>(right_x), static_cast<int16_t>(right_y), static_cast<uint8_t>(left_trigger),
      static_cast<uint8_t>(right_trigger));
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_jefrien_hellsgate_PerformanceOverlayView_nativeGetPresentedFrames(JNIEnv*, jclass) {
  return static_cast<jlong>(hellsgate::android_perf::PresentedFrames());
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_jefrien_hellsgate_PerformanceOverlayView_nativeGetGuestFrames(JNIEnv*, jclass) {
  return static_cast<jlong>(hellsgate_android_guest_frame_count());
}
