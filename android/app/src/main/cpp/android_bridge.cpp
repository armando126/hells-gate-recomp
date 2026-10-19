#include <jni.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>
#define SDL_MAIN_HANDLED
#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>

#include <cstdio>

namespace {
ANativeWindow* g_window = nullptr;
VkInstance g_instance = VK_NULL_HANDLE;
VkSurfaceKHR g_surface = VK_NULL_HANDLE;
bool g_sdl_initialized = false;
bool g_runtime_started = false;

// ReXGlue's Android entry point is supplied when the generated game runtime is
// linked. Keeping it weak lets the launcher build before game code generation
// and gives the UI a useful diagnostic instead of crashing or silently doing
// nothing.
extern "C" bool hells_gate_rexglue_start(ANativeWindow*) __attribute__((weak));

void DestroyVulkan() {
  if (g_surface != VK_NULL_HANDLE && g_instance != VK_NULL_HANDLE) {
    auto destroy_surface = reinterpret_cast<PFN_vkDestroySurfaceKHR>(
        vkGetInstanceProcAddr(g_instance, "vkDestroySurfaceKHR"));
    if (destroy_surface) destroy_surface(g_instance, g_surface, nullptr);
  }
  if (g_instance != VK_NULL_HANDLE) vkDestroyInstance(g_instance, nullptr);
  g_surface = VK_NULL_HANDLE;
  g_instance = VK_NULL_HANDLE;
}

bool CreateVulkanSurface() {
  const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME,
                              VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
  VkApplicationInfo app_info{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app_info.pApplicationName = "Hell's Gate";
  app_info.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
  app_info.pEngineName = "Hell's Gate";
  app_info.engineVersion = VK_MAKE_VERSION(0, 1, 0);
  app_info.apiVersion = VK_API_VERSION_1_1;

  VkInstanceCreateInfo instance_info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  instance_info.pApplicationInfo = &app_info;
  instance_info.enabledExtensionCount = 2;
  instance_info.ppEnabledExtensionNames = extensions;
  if (vkCreateInstance(&instance_info, nullptr, &g_instance) != VK_SUCCESS) {
    g_instance = VK_NULL_HANDLE;
    return false;
  }

  auto create_surface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
      vkGetInstanceProcAddr(g_instance, "vkCreateAndroidSurfaceKHR"));
  if (!create_surface) {
    DestroyVulkan();
    return false;
  }

  VkAndroidSurfaceCreateInfoKHR surface_info{
      VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
  surface_info.window = g_window;
  if (create_surface(g_instance, &surface_info, nullptr, &g_surface) != VK_SUCCESS) {
    DestroyVulkan();
    return false;
  }
  return true;
}

bool InitializeSdl() {
  if (g_sdl_initialized) return true;
  // Android enters through Activity.onCreate(), not an SDL-provided main().
  SDL_SetMainReady();
  g_sdl_initialized = SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO | SDL_INIT_GAMEPAD);
  return g_sdl_initialized;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeStatus(JNIEnv* env, jobject) {
  if (g_runtime_started) {
    return env->NewStringUTF("ReXGlue runtime started");
  }
  if (g_surface != VK_NULL_HANDLE && g_sdl_initialized) {
    return env->NewStringUTF("SDL3 + Android Vulkan surface ready");
  }
  if (g_surface != VK_NULL_HANDLE) {
    char status[256];
    std::snprintf(status, sizeof(status), "Android Vulkan ready; SDL3 pending: %s",
                  SDL_GetError());
    return env->NewStringUTF(status);
  }
  return env->NewStringUTF("Android surface pending");
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeStartGame(JNIEnv* env, jobject) {
  if (g_window == nullptr || g_surface == VK_NULL_HANDLE || !g_sdl_initialized) {
    return env->NewStringUTF("Renderer not ready");
  }
  if (!hells_gate_rexglue_start) {
    return env->NewStringUTF(
        "ReXGlue runtime not linked; add game/default.xex and generated/default");
  }
  g_runtime_started = hells_gate_rexglue_start(g_window);
  return env->NewStringUTF(g_runtime_started ? "ReXGlue runtime started"
                                             : "ReXGlue runtime failed to start");
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeSetSurface(JNIEnv* env, jobject, jobject surface) {
  DestroyVulkan();
  if (g_window) {
    ANativeWindow_release(g_window);
    g_window = nullptr;
  }
  g_window = ANativeWindow_fromSurface(env, surface);
  if (g_window) {
    InitializeSdl();
    CreateVulkanSurface();
  }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeClearSurface(JNIEnv*, jobject) {
  g_runtime_started = false;
  DestroyVulkan();
  if (g_window) {
    ANativeWindow_release(g_window);
    g_window = nullptr;
  }
  if (g_sdl_initialized) {
    SDL_Quit();
    g_sdl_initialized = false;
  }
}
