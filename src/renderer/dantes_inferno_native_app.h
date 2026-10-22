
#pragma once

#include <atomic>
#include <cstdint>

#include "dantes_inferno_app.h"
#include "renderer_config.h"

class DantesInfernoNativeApp : public DantesInfernoApp {
 public:
  using DantesInfernoApp::DantesInfernoApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(rex::ui::WindowedAppContext& ctx) {
    auto app = std::unique_ptr<DantesInfernoNativeApp>(
        new DantesInfernoNativeApp(ctx, "dantes_inferno", PPCImageConfig));
    instance_.store(app.get(), std::memory_order_release);
    return app;
  }

  ~DantesInfernoNativeApp() override {
    instance_.store(nullptr, std::memory_order_release);
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    DantesInfernoApp::OnPreSetup(config);
    renderer::ApplyNativeRenderScale();
    if (auto graphics = renderer::CreateGraphicsSystem()) {
      config.graphics = std::move(graphics);
    }
  }

  static DantesInfernoNativeApp* Instance() {
    return instance_.load(std::memory_order_acquire);
  }

 private:
  static inline std::atomic<DantesInfernoNativeApp*> instance_{nullptr};
};
