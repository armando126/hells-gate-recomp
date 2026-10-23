#include "renderer_config.h"

#include <rex/cvar.h>
#include <rex/logging/macros.h>

#include <algorithm>
#include <cstdlib>
#include <string>

#if REX_PLATFORM_WIN32
#include <windows.h>
#endif

#if REX_HAS_VULKAN
#include <rex/graphics/vulkan/graphics_system.h>
#endif

REXCVAR_DEFINE_STRING(renderer, "xenos", "Graphics",
                      "Graphics system: 'dante' renders natively and keeps the rexgpu-xenos "
                      "plugin for sync only; 'xenos' renders with the plugin; 'native' uses "
                      "the SDK's Vulkan backend linked into dantes_inferno_native");

REXCVAR_DEFINE_STRING(native_render_scale, "off", "Graphics",
                      "Guest draw resolution scale, applied via the SDK "
                      "'resolution_scale' cvar before GPU init. 'off' leaves "
                      "the SDK default, 'auto' derives the scale from the "
                      "display height (display_height / 720, rounded), "
                      "'1'-'7' forces a fixed integer scale");

REXCVAR_DEFINE_BOOL(dante_packet_inventory, false, "Diagnostics",
                    "renderer=dante: turn on the plugin's gpu_packet_inventory (PM4 packets "
                    "the sync-only command processor executes, logged every 10 s)");

namespace renderer {

bool IsDanteRenderer(std::string_view renderer_name) {
  return renderer_name == "dante" || renderer_name == "android-dante";
}

bool IsSdkVulkanRenderer(std::string_view renderer_name) {
  return renderer_name == "native" || renderer_name == "android-dante";
}

std::unique_ptr<rex::system::IGraphicsSystem> CreateGraphicsSystem() {
  const std::string renderer = rex::cvar::Query<std::string>("renderer");
  if (renderer == "xenos") return nullptr;
  if (IsSdkVulkanRenderer(renderer)) {
#if REX_HAS_VULKAN
    REXLOG_INFO("renderer={}: SDK Vulkan backend", renderer);
    return std::make_unique<rex::graphics::vulkan::VulkanGraphicsSystem>();
#else
    REXLOG_ERROR("renderer={} requires a Vulkan-enabled SDK build (REXGLUE_USE_VULKAN)", renderer);
    return nullptr;
#endif
  }
  REXLOG_WARN("Unknown renderer='{}'; using the xenos plugin", renderer);
  return nullptr;
}

void ConfigureSyncOnlyGpu() {
  const std::string renderer = rex::cvar::Query<std::string>("renderer");
  if (renderer != "dante") {
    return;
  }
  bool host_off = rex::cvar::SetFlagByName("gpu_host_rendering", "false");
  // Without host draws there are no real sample counts; the CP then writes
  // query_occlusion_fake_sample_count ("visible").
  bool queries_off = rex::cvar::SetFlagByName("occlusion_query_enable", "false");
  REXLOG_INFO("renderer=dante: gpu_host_rendering=false ({}), occlusion_query_enable=false ({})",
              host_off ? "ok" : "FAILED", queries_off ? "ok" : "FAILED");
  // The CP's D3D12 shader storage is unused here, and loading it blocks the UI
  // thread (the precompile progress card) for seconds after driver updates.
  rex::cvar::SetFlagByName("store_shaders", "false");
  if (REXCVAR_GET(dante_packet_inventory)) {
    bool ok = rex::cvar::SetFlagByName("gpu_packet_inventory", "true");
    REXLOG_INFO("renderer=dante: gpu_packet_inventory ({})", ok ? "ok" : "FAILED");
  }
}

void ApplyNativeRenderScale() {
  const std::string mode = rex::cvar::Query<std::string>("native_render_scale");
  if (mode.empty() || mode == "off") {
    return;
  }

  int scale = 0;
  if (mode == "auto") {
    // The guest always renders a 720p-class frame; pick the integer scale
    // that best matches the target display height, rounding up so
    // non-integer ratios supersample rather than undersample.
    const int window_height = rex::cvar::Query<int32_t>("window_height");
    int target_height = window_height > 0 ? window_height : 720;
#if REX_PLATFORM_WIN32
    if (rex::cvar::Query<bool>("fullscreen")) {
      target_height = GetSystemMetrics(SM_CYSCREEN);
    }
#endif
    scale = (target_height + 359) / 720;
  } else {
    scale = std::atoi(mode.c_str());
  }

  scale = std::clamp(scale, 1, 7);
  if (scale <= 1) {
    return;
  }
  REXLOG_INFO("native_render_scale={} -> resolution_scale={}", mode, scale);
  rex::cvar::SetFlagByName("resolution_scale", std::to_string(scale));
}

}  // namespace renderer
