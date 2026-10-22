// Android startup compatibility hooks for the TU2 image.
// The Xbox title entrypoint performs console privilege/XConfig checks before
// continuing. Those checks are not meaningful on Android and otherwise make
// xstart call XamLoaderTerminateTitle immediately.

#include <rex/hook.h>
#include <rex/logging/macros.h>
#include <rex/platform.h>

#if REX_PLATFORM_ANDROID

REX_EXTERN(__imp__sub_8281D8E0);

REX_HOOK_RAW(sub_8281D8E0) {
  REXLOG_INFO("Android startup compatibility hook entered");
  __imp__sub_8281D8E0(ctx, base);
  const uint32_t original_status = ctx.r3.u32;
  REXLOG_INFO("Android startup compatibility: original status={:#x}", original_status);
  if (original_status != 0) {
    REXLOG_WARN("Android startup compatibility: forcing success");
    ctx.r3.u64 = 0;
  }
}

#endif
