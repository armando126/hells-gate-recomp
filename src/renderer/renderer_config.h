#pragma once

#include <memory>
#include <string_view>

#include <rex/system/interfaces/graphics.h>

namespace renderer {

// Returns true for the custom Dante renderer and its Android compatibility
// path. The SDK-backed Android baseline is selected separately below.
bool IsDanteRenderer(std::string_view renderer_name);

// Returns true for renderers that use the SDK Vulkan graphics system directly.
// android-dante starts from this path; the custom renderer remains available
// as renderer=dante while it is migrated incrementally.
bool IsSdkVulkanRenderer(std::string_view renderer_name);

// The graphics system selected by the `renderer` cvar: the SDK's Vulkan
// backend linked into the exe for "native", nullptr (ReXApp loads the
// rexgpu-xenos plugin) otherwise.
std::unique_ptr<rex::system::IGraphicsSystem> CreateGraphicsSystem();

// Applies the `native_render_scale` cvar to the SDK `resolution_scale` cvar.
// Must run before SetupGuestGpu (which captures the scale).
void ApplyNativeRenderScale();

// renderer=dante: the Xenos command processor only keeps the game's sync
// (fences, interrupts, swaps); rendering is native. Call after the GPU plugin
// is loaded (its cvars register on load).
void ConfigureSyncOnlyGpu();

}  // namespace renderer
