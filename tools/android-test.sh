#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_dir="$repo_root/android"
log_dir="$repo_root/logs"
stamp="$(date +%Y%m%d-%H%M%S)"
log_file="$log_dir/android-test-$stamp.log"

mkdir -p "$log_dir"
cd "$android_dir"

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

adb logcat -c
echo "Capturando log en: $log_file"
adb logcat -v time > "$log_file" &
logcat_pid=$!

cleanup() {
  kill "$logcat_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

adb shell am force-stop dev.jefrien.hellsgate
adb shell am start -W -n dev.jefrien.hellsgate/.GameActivity

sleep 30
grep -E "HellsgateNative|SDL|REX|Diligent|Vulkan|Audio|XThread|RtlLeaveCriticalSection|RtlEnterCriticalSection|FATAL|DEBUG|AndroidRuntime" \
  "$log_file" > "$log_file.filtered" || true
echo "Prueba terminada. Log: $log_file"
echo "Log filtrado: $log_file.filtered"
