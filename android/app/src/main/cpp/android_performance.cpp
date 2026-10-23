// Android scheduling policy shared with the NFSMW port.
//
// The guest, renderer and audio threads are frame-coupled. On heterogeneous
// phones the scheduler can place them on little cores because each thread
// spends part of every frame waiting. Restricting the process to the fast
// cluster avoids that oscillation and is reversible through the cvar.
#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

#include <rex/cvar.h>
#include <rex/logging.h>

REXCVAR_DEFINE_BOOL(hellsgate_android_fast_cores, true, "Android",
                    "Use Android performance CPU cores while avoiding the hottest prime core")
    .lifecycle(rex::cvar::Lifecycle::kInitOnly);

namespace {

long MaxClockKhz(int cpu) {
  std::ifstream file("/sys/devices/system/cpu/cpu" + std::to_string(cpu) +
                     "/cpufreq/cpuinfo_max_freq");
  long khz = 0;
  return (file >> khz) ? khz : 0;
}
}  // namespace

extern "C" void hellsgate_android_apply_fast_cores() {
  if (!REXCVAR_GET(hellsgate_android_fast_cores)) return;

  const int total = static_cast<int>(sysconf(_SC_NPROCESSORS_CONF));
  if (total <= 1 || total > CPU_SETSIZE) return;

  std::vector<long> clocks(static_cast<size_t>(total));
  long fastest = 0;
  for (int cpu = 0; cpu < total; ++cpu) {
    clocks[static_cast<size_t>(cpu)] = MaxClockKhz(cpu);
    fastest = std::max(fastest, clocks[static_cast<size_t>(cpu)]);
  }
  if (fastest <= 0) {
    REXLOG_INFO("[android] no CPU max clocks found; affinity unchanged");
    return;
  }

  std::vector<long> clusters;
  for (long clock : clocks) {
    if (clock > 0 && std::find(clusters.begin(), clusters.end(), clock) == clusters.end()) {
      clusters.push_back(clock);
    }
  }
  std::sort(clusters.rbegin(), clusters.rend());
  const bool use_second_cluster = clusters.size() >= 3;
  const long selected_clock = use_second_cluster ? clusters[1] : 0;

  cpu_set_t mask;
  CPU_ZERO(&mask);
  int selected = 0;
  std::string cpu_list;
  for (int cpu = 0; cpu < total; ++cpu) {
    const long clock = clocks[static_cast<size_t>(cpu)];
    const bool is_fast = use_second_cluster ? clock == selected_clock
                                            : clock * 10 >= fastest * 7;
    if (is_fast) {
      CPU_SET(cpu, &mask);
      ++selected;
      cpu_list += (cpu_list.empty() ? "" : ",") + std::to_string(cpu);
    }
  }
  if (selected == total || selected < 2) {
    REXLOG_INFO("[android] {} CPUs without a slow cluster; affinity unchanged", total);
    return;
  }

  int moved = 0;
  std::error_code error;
  for (const auto& task : std::filesystem::directory_iterator("/proc/self/task", error)) {
    const pid_t tid = static_cast<pid_t>(std::strtol(task.path().filename().c_str(), nullptr, 10));
    if (tid > 0 && sched_setaffinity(tid, sizeof(mask), &mask) == 0) ++moved;
  }
  REXLOG_INFO("[android] performance CPUs {} of {} ({}; fastest {} MHz); moved {} threads",
              cpu_list, total, use_second_cluster ? "second clock cluster" : ">=70% fastest",
              fastest / 1000, moved);
}
