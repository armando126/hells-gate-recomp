# Hell's Gate Android

An experimental Android ARM64 port of **Dante's Inferno** (Xbox 360), based
on the original [Hell's Gate PC port](https://github.com/florinp93/hells-gate-recomp).
The project uses the [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) to
statically recompile the Xbox 360 PowerPC executable into native C++ and uses
SDL3 and Vulkan on Android. It is not an emulator and does not use a JIT at
runtime.

## Current status

This is my first porting project. I am a programmer, but I had no previous
experience porting a game to Android, and this project has been far beyond my
normal comfort zone. After a lot of experimentation, the game now boots and
is playable on Android ARM64 at roughly 30 FPS on my test device. The current
build is functional, but performance, compatibility and graphics quality still
need substantial work.

If you have experience with Android graphics, Vulkan, SDL3, ReXGlue, game
recompilation or Adreno performance tuning, help is very welcome. The project
is open source and contributions, issue reports, profiling data, code review
and documentation improvements are all appreciated.

## Features currently working

- Android ARM64 build and launcher.
- Vulkan rendering through the Android/ReXGlue runtime.
- Game audio, FMV playback and touch controls.
- Xbox-compatible controller input, including Bluetooth controllers.
- Performance overlay with FPS, CPU usage, SoC temperature and battery data.
- Runtime options for VSync, shader compilation, CPU affinity, anisotropic
  filtering up to 16x, audio buffering and pipeline creation threads.

The port is still experimental. Performance varies by device and scene, and
some settings may trade latency, image quality, heat or stability for speed.

## Requirements

- Android SDK 35
- Android NDK `28.2.13676358` or a compatible NDK
- CMake 3.22+
- JDK 17+
- Git, Ninja and Clang for preparing dependencies

The repository does not contain game files. You must provide your own legally
obtained copy of the game, including `default.xex` and the required
`bigfile*.viv` files, in the local `game/` directory. Game files, generated
code and cloned third-party SDKs are intentionally excluded from Git.

## Setup

Clone the repository and prepare the local SDK dependencies:

```bash
./setup.sh
```

Generate the ReXGlue game sources using the SDK workflow. The generated files
must be available under `generated/default/` before building the Android app.

## Build and install

From the `android/` directory:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

For a debug build:

```bash
./gradlew :app:assembleDebug
```

The APK is built for `arm64-v8a`. Release builds currently use the Android
debug signing key for development and testing; use your own release keystore
before distributing a production build.

## Contributing

1. Fork the repository and create a focused branch.
2. Make one measurable change at a time.
3. Build and test on a real Android ARM64 device when possible.
4. Include the device model, Android version, renderer/settings, FPS, thermal
   behavior and relevant logcat output in performance or compatibility reports.
5. Open a pull request describing the change and how it was tested.

Useful contributions include profiling, Vulkan synchronization and pipeline
work, shader and texture compatibility, audio scheduling, controller support,
thermal/power optimization, launcher improvements and documentation.

Please do not commit game files, generated output, private keys, APK signing
keys or third-party build directories.

## Documentation

- [`android/README.md`](android/README.md): Android build and architecture.
- [`docs/NATIVE_RENDERER_DESIGN.md`](docs/NATIVE_RENDERER_DESIGN.md): renderer
  design and milestones.
- [`docs/NATIVE_D3D_MAP.md`](docs/NATIVE_D3D_MAP.md): graphics entry points.
- [`docs/frame_rate.md`](docs/frame_rate.md): frame pacing and refresh rate.
- [`docs/vp6_fmv_corruption_fix.md`](docs/vp6_fmv_corruption_fix.md): VP6 FMV.
- [`docs/rexglue_notes.md`](docs/rexglue_notes.md): SDK notes and patches.

## License and legal notice

Hell's Gate is an unofficial fan project and is not affiliated with or
endorsed by Electronic Arts, Visceral Games or Microsoft. *Dante's Inferno* is
a trademark of its respective owners. This repository contains no proprietary
game code or game assets; provide and use only a legally obtained copy of the
game.
