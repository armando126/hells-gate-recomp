#pragma once

#if defined(__ANDROID__)

#include <algorithm>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdint>

namespace hellsgate::android_perf {

inline std::atomic<uint64_t> g_presented_frames{0};

inline uint64_t PresentedFrames() {
  return g_presented_frames.load(std::memory_order_relaxed);
}

inline void CountPresentedFrame() {
  g_presented_frames.fetch_add(1, std::memory_order_relaxed);
}

struct Stats {
  const char* name;
  std::atomic<uint64_t> count{0};
  std::atomic<uint64_t> total_ns{0};
  std::atomic<uint64_t> max_ns{0};
};

class Scope final {
 public:
  explicit Scope(Stats& stats) : stats_(stats), start_(std::chrono::steady_clock::now()) {}

  ~Scope() {
    const auto elapsed = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start_)
            .count());
    const uint64_t count = stats_.count.fetch_add(1, std::memory_order_relaxed) + 1;
    stats_.total_ns.fetch_add(elapsed, std::memory_order_relaxed);
    uint64_t previous_max = stats_.max_ns.load(std::memory_order_relaxed);
    while (elapsed > previous_max &&
           !stats_.max_ns.compare_exchange_weak(previous_max, elapsed,
                                                 std::memory_order_relaxed)) {
    }
    if (count % 60 == 0) {
      const uint64_t total = stats_.total_ns.load(std::memory_order_relaxed);
      const uint64_t maximum = stats_.max_ns.load(std::memory_order_relaxed);
      __android_log_print(ANDROID_LOG_INFO, "HellsgatePerf",
                          "[android-perf] %s count=%llu avg_us=%llu max_us=%llu", stats_.name,
                          static_cast<unsigned long long>(count),
                          static_cast<unsigned long long>(total / count / 1000),
                          static_cast<unsigned long long>(maximum / 1000));
    }
  }

 private:
  Stats& stats_;
  std::chrono::steady_clock::time_point start_;
};

}  // namespace hellsgate::android_perf

#define HELLSGATE_ANDROID_PERF_SCOPE(name) \
  static ::hellsgate::android_perf::Stats hellsgate_perf_stats{name}; \
  ::hellsgate::android_perf::Scope hellsgate_perf_scope(hellsgate_perf_stats)

#else

#define HELLSGATE_ANDROID_PERF_SCOPE(name)

#endif
