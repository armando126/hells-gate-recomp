
#include "generated/default/dantes_inferno_init.h"

#if REX_PLATFORM_ANDROID
#include "renderer/dantes_inferno_native_app.h"

extern "C" uint64_t hellsgate_android_guest_frame_count() {
  auto* app = DantesInfernoNativeApp::Instance();
  return app ? app->GuestFrameCountForAndroid() : 0;
}
#else
#include "dantes_inferno_app.h"
#endif

#if REX_PLATFORM_ANDROID
// Android uses the same native Vulkan graphics-system path as the upstream
// dantes_inferno_native desktop target. Keep the creator name stable because
// the SDL bridge looks it up by this name.
REX_DEFINE_APP(dantes_inferno, DantesInfernoNativeApp::Create)
#else
REX_DEFINE_APP(dantes_inferno, DantesInfernoApp::Create)
#endif
