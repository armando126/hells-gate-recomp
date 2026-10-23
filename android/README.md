# Android build

This is the Android ARM64 port of Hell's Gate. It targets `arm64-v8a`,
receives an Android `Surface`, and uses SDL3, ReXGlue and Vulkan through the
Android Activity.

Requirements: Android Studio or Gradle, Android SDK 35, NDK, CMake 3.25+, and
JDK 17+. From this directory, run:

```bash
./gradlew assembleDebug
```

The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. The native library links SDL3 and
initializes the video, audio, gamepad and Vulkan subsystems alongside the
recompiled game.
