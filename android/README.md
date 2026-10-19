# Android build

This is the initial Android shell for Hell's Gate. It targets `arm64-v8a`,
receives an Android `Surface`, and creates/destroys a Vulkan `VkSurfaceKHR` as
the Activity enters and leaves the foreground. The app build remains separate
from the desktop CMake project until the SDL3/Vulkan platform layer is ready.

Requirements: Android Studio or Gradle, Android SDK 35, NDK, CMake 3.25+, and
JDK 17+. From this directory, run:

```bash
./gradlew assembleDebug
```

The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. The native library now links SDL3
and initializes its video, audio, and gamepad subsystems alongside the Vulkan
surface. The next step is to connect the SDL/ReXGlue runtime and replace the
launcher placeholder with the recompiled game.
