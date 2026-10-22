#include <jni.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>
#define SDL_MAIN_HANDLED
#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/memory/utils.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>

#include <cstdio>
#include <dlfcn.h>
#include <filesystem>
#include <memory>
#include <string>

namespace {
ANativeWindow* g_window = nullptr;
bool g_sdl_initialized = false;
bool g_runtime_started = false;
std::string g_runtime_start_error;
std::unique_ptr<rex::ui::SDLWindowedAppContext> g_app_context;
std::unique_ptr<rex::ui::WindowedApp> g_app;
std::filesystem::path g_app_data_path;

extern "C" bool hells_gate_rexglue_start(ANativeWindow*) {
  if (g_runtime_started) return true;
  g_runtime_start_error.clear();
  if (!g_sdl_initialized) {
    g_runtime_start_error = "SDL no está inicializado";
    return false;
  }

  // CLI11 expects a valid argv array even when Android has no command-line
  // arguments. Passing (0, nullptr) reaches CLI::App::parse() with a null
  // argv and crashes while starting the game.
  char arg0[] = "hells_gate_android";
  char* argv[] = {arg0, nullptr};
  rex::cvar::Init(1, argv);
  rex::cvar::ApplyEnvironment();
  if (!g_app_data_path.empty()) {
    rex::cvar::SetFlagByName("user_data_root", g_app_data_path.string());
    rex::cvar::SetFlagByName("game_data_root", (g_app_data_path / "game").string());
    const auto default_xex = g_app_data_path / "game" / "default.xex";
    if (!std::filesystem::is_regular_file(default_xex)) {
      g_runtime_start_error = "No existe " + default_xex.string();
      return false;
    }
  }
  // API 26+ provides ASharedMemory_create, while /dev/ashmem is blocked for
  // modern targetSdk apps. Initialize ReXGlue's Android memory helper before
  // Runtime::Setup creates the guest memory arena.
  rex::memory::AndroidInitialize();
  rex::InitLoggingEarly();

  g_app_context = std::make_unique<rex::ui::SDLWindowedAppContext>();
  if (!g_app_context->Initialize()) {
    g_runtime_start_error = "SDLWindowedAppContext falló: " + std::string(SDL_GetError());
    g_app_context.reset();
    return false;
  }

  auto creator = rex::ui::WindowedApp::GetCreator("dantes_inferno");
  if (!creator) {
    g_runtime_start_error = "No se registró el creador dantes_inferno";
    g_app_context.reset();
    return false;
  }

  g_app = creator(*g_app_context);
  if (!g_app || !g_app->OnInitialize()) {
    g_runtime_start_error =
        "DantesInfernoApp::OnInitialize falló (revisa default.xex y los logs)";
    // WindowedApp requires InvokeOnDestroy() when initialization fails. It
    // drains pending UI callbacks and joins ReXApp's module thread before the
    // object is destroyed; resetting directly leaves a joinable std::thread
    // behind and std::thread::~thread() calls std::terminate().
    if (g_app) {
      g_app->InvokeOnDestroy();
    }
    g_app.reset();
    g_app_context.reset();
    return false;
  }

  // ReXApp::LaunchModule() intentionally queues the actual guest launch on
  // the SDL UI context. Android uses this bridge instead of
  // RunMainMessageLoop(), so there is no SDL event loop to drain that queue.
  // Execute it now on the same UI thread that called OnInitialize(); without
  // this, the launcher reports success while the guest module never starts.
  g_app_context->ExecutePendingFunctionsFromUIThread();

  g_runtime_started = true;
  return true;
}

bool InitializeSdl() {
  if (g_sdl_initialized) return true;
  // Android enters through Activity.onCreate(), not an SDL-provided main().
  SDL_SetMainReady();
  // Start with video only. SDL's Android audio hotplug path calls into
  // SDLAudioManager during SDL_InitSubSystem; our custom Compose Activity does
  // not use SDL's audio manager and that callback can receive a null jclass.
  // Audio/gamepad can be enabled after the native bootstrap is fully wired.
  g_sdl_initialized = SDL_Init(SDL_INIT_VIDEO);
  return g_sdl_initialized;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeStatus(JNIEnv* env, jobject) {
  if (g_runtime_started) {
    return env->NewStringUTF("ReXGlue runtime started");
  }
  if (g_window != nullptr && g_sdl_initialized) {
    return env->NewStringUTF("SDL3 Android surface ready");
  }
  return env->NewStringUTF("Android surface pending");
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeSetAppDataPath(JNIEnv* env, jobject,
                                                              jstring path) {
  const char* utf_path = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
  g_app_data_path = utf_path ? std::filesystem::path(utf_path) : std::filesystem::path{};
  if (utf_path) env->ReleaseStringUTFChars(path, utf_path);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeStartGame(JNIEnv* env, jobject) {
  if (g_window == nullptr || !g_sdl_initialized) {
    return env->NewStringUTF("Renderer not ready");
  }
  g_runtime_started = hells_gate_rexglue_start(g_window);
  if (g_runtime_started) return env->NewStringUTF("ReXGlue runtime started");
  return env->NewStringUTF(g_runtime_start_error.empty() ? "ReXGlue runtime failed to start"
                                                          : g_runtime_start_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativePumpEvents(JNIEnv*, jobject) {
  if (!g_app_context) {
    return;
  }

  // ReXGlue schedules guest-launch and presentation work here. The Android
  // host does not run SDLWindowedAppContext::RunMainMessageLoop(), so drive
  // the context from the Choreographer-driven Kotlin loop.
  g_app_context->PumpEvents();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeSetSurface(JNIEnv* env, jobject, jobject surface) {
  if (g_window) {
    ANativeWindow_release(g_window);
    g_window = nullptr;
  }
  g_window = ANativeWindow_fromSurface(env, surface);
  if (g_window) {
    InitializeSdl();
  }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_jefrien_hellsgate_MainActivity_nativeClearSurface(JNIEnv*, jobject) {
  g_runtime_started = false;
  if (g_window) {
    ANativeWindow_release(g_window);
    g_window = nullptr;
  }
  if (g_sdl_initialized) {
    SDL_Quit();
    g_sdl_initialized = false;
  }
}
