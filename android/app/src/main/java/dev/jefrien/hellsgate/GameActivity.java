package dev.jefrien.hellsgate;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.graphics.Color;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.hardware.input.InputManager;
import android.content.SharedPreferences;

import org.libsdl.app.SDLActivity;

import java.io.File;

/** SDL-owned game screen. SDLActivity supplies the main thread, surface and event loop. */
public final class GameActivity extends SDLActivity implements InputManager.InputDeviceListener {
    private static final String TAG = "Hellsgate";
    // Keep the SurfaceView full-screen, but render the game into a smaller buffer.
    // 1280x720 preserves the game's 16:9 content while minimizing the Adreno workload.
    // Stable working resolution for the Android Vulkan path.
    private static final int INTERNAL_WIDTH = 1280;
    private static final int INTERNAL_HEIGHT = 720;
    private String gameRoot;
    private String userRoot;
    private String cacheRoot;
    // android-dante is the working native Vulkan renderer on Android. The
    // SDK graphics-system path remains selectable through the launch intent.
    private String renderer = "android-dante";
    private TouchControlsView touchControls;
    private PerformanceOverlayView performanceOverlay;
    private InputManager inputManager;
    private boolean settingVsync;
    private boolean settingAsyncShaders;
    private boolean settingFastCores;
    private int settingAnisotropic;
    private int settingAudioFrames;
    private int settingPipelineThreads;

    @Override
    protected void onCreate(Bundle state) {
        File appRoot = new File(getFilesDir(), "hellsgate");
        gameRoot = new File(getFilesDir(), "game").getAbsolutePath();
        userRoot = new File(appRoot, "user").getAbsolutePath();
        cacheRoot = new File(appRoot, "cache").getAbsolutePath();
        String requestedRenderer = getIntent().getStringExtra("renderer");
        if ("dante".equalsIgnoreCase(requestedRenderer)
                || "android-dante".equalsIgnoreCase(requestedRenderer)) {
            renderer = "android-dante".equalsIgnoreCase(requestedRenderer)
                    ? "android-dante" : "dante";
        } else if ("xenos".equalsIgnoreCase(requestedRenderer)) {
            renderer = "xenos";
        } else if ("native".equalsIgnoreCase(requestedRenderer)) {
            renderer = "native";
        }
        new File(userRoot).mkdirs();
        new File(cacheRoot).mkdirs();
        SharedPreferences settings = getSharedPreferences("performance", MODE_PRIVATE);
        settingVsync = settings.getBoolean("vsync", true);
        settingAsyncShaders = settings.getBoolean("async_shaders", false);
        settingFastCores = settings.getBoolean("fast_cores", true);
        settingAnisotropic = settings.getInt("anisotropic", 0);
        settingAudioFrames = settings.getInt("audio_frames", 10);
        settingPipelineThreads = settings.getInt("pipeline_threads", -1);
        try {
            // ReXGlue uses this folder for logs, settings and the Vulkan cache.
            Os.setenv("REX_APP_FOLDER", userRoot, true);
        } catch (ErrnoException e) {
            Log.w(TAG, "REX_APP_FOLDER", e);
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(state);
        useWholeScreenAtGameRefresh();
        applyInternalSurfaceSize();
        ViewGroup root = findViewById(android.R.id.content);
        root.setBackgroundColor(Color.BLACK);
        touchControls = new TouchControlsView(this);
        root.addView(touchControls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        performanceOverlay = new PerformanceOverlayView(this, renderer);
        root.addView(performanceOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Log.i(TAG, "Renderer seleccionado: " + renderer);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        refreshGamepadVisibility();
    }

    @Override protected void onResume() {
        super.onResume();
        applyInternalSurfaceSize();
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(this, null);
            refreshGamepadVisibility();
        }
    }

    private void applyInternalSurfaceSize() {
        if (mSurface != null) {
            View parent = (View) mSurface.getParent();
            int availableWidth = parent != null ? parent.getWidth() : 0;
            int availableHeight = parent != null ? parent.getHeight() : 0;
            if (availableWidth <= 0 || availableHeight <= 0) {
                availableWidth = getResources().getDisplayMetrics().widthPixels;
                availableHeight = getResources().getDisplayMetrics().heightPixels;
            }

            // The game is authored for 16:9. Keep the SurfaceView's viewport
            // proportional on the phone's wider landscape panel and let the
            // black background form pillarbox bars instead of stretching it.
            int viewportWidth = availableWidth;
            int viewportHeight = Math.round(viewportWidth * 9.0f / 16.0f);
            if (viewportHeight > availableHeight) {
                viewportHeight = availableHeight;
                viewportWidth = Math.round(viewportHeight * 16.0f / 9.0f);
            }

            RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(
                    viewportWidth, viewportHeight);
            layout.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            mSurface.setLayoutParams(layout);
            mSurface.getHolder().setFixedSize(INTERNAL_WIDTH, INTERNAL_HEIGHT);
            Log.i(TAG, "Game viewport: " + viewportWidth + "x" + viewportHeight
                    + " buffer=" + INTERNAL_WIDTH + "x" + INTERNAL_HEIGHT);
        }
    }

    @Override protected void onPause() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        super.onPause();
    }

    private void refreshGamepadVisibility() {
        boolean found = false;
        if (inputManager != null) {
            for (int id : inputManager.getInputDeviceIds()) {
                if (isGamepad(inputManager.getInputDevice(id))) { found = true; break; }
            }
        }
        if (touchControls != null) {
            if (found) touchControls.onGamepadActive(); else touchControls.onGamepadGone();
        }
    }

    private static boolean isGamepad(InputDevice device) {
        if (device == null) return false;
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    @Override public void onInputDeviceAdded(int id) { refreshGamepadVisibility(); }
    @Override public void onInputDeviceRemoved(int id) { refreshGamepadVisibility(); }
    @Override public void onInputDeviceChanged(int id) { refreshGamepadVisibility(); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isGamepad(event.getDevice()) && touchControls != null) touchControls.onGamepadActive();
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (isGamepad(event.getDevice()) && touchControls != null) touchControls.onGamepadActive();
        return super.onGenericMotionEvent(event);
    }

    // SDL chooses an orientation from the window dimensions when it creates
    // the window. Keep that request constrained to landscape as NFSMW does.
    @Override
    public void setOrientationBis(int width, int height, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    /** Prefer a 60 Hz physical mode: the game is paced at 60 Hz, not at the panel's 120 Hz. */
    private void useWholeScreenAtGameRefresh() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode current = display.getMode();
            Display.Mode best = null;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() != current.getPhysicalWidth() ||
                    mode.getPhysicalHeight() != current.getPhysicalHeight()) {
                    continue;
                }
                if (mode.getRefreshRate() + 0.5f >= 60.0f &&
                    (best == null || mode.getRefreshRate() < best.getRefreshRate())) {
                    best = mode;
                }
            }
            if (best != null) {
                attrs.preferredDisplayModeId = best.getModeId();
            }
        }
        getWindow().setAttributes(attrs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
                "--game_data_root=" + gameRoot,
                "--user_data_root=" + userRoot,
                "--cache_root=" + cacheRoot,
                // Match the PC launcher's Spanish Xbox locale. The runtime's
                // XGetLanguage implementation consumes these CVARs and the
                // game then selects the corresponding text/audio tables.
                "--user_language=5",
                "--user_country=31",
                // The sidecar default.xexp applies TU2 0.0.2.1 to the base XEX.
                // Use the upstream native Vulkan graphics-system path rather
                // than the D3D-hook compatibility renderer on Android.
                "--renderer=" + renderer,
                // Do not render at the phone's 2780px physical width. The
                // Stable working resolution for the Android Dante path.
                "--dante_resolution=1280x720",
                // Keep shadow maps at the game resolution instead of 2x; this
                // is a large GPU/bandwidth reduction on high-density phones.
                "--dante_shadow_scale=1",
                // Keep the guest/renderer/audio threads on the fast CPU cluster.
                // This is the first isolated performance experiment; compare
                // frame pacing and SoC temperature against the unpinned build.
                "--hellsgate_android_fast_cores=" + settingFastCores,
                // Always present the resolved front buffer on Android. A previous
                // diagnostic value could remain in the runtime configuration and
                // select an intermediate render target, which produces audio with
                // a black screen even though NATIVE-PRESENT reports success.
                "--dante_debug_view=0",
                // FXAA/SMAA's compute shader uses textureLod, which this
                // Android glslang path rejects; disable it until that shader
                // variant is translated for GLES/Vulkan Android.
                "--dante_smaa=false",
                // Native Vulkan path: do not allocate/use host 2x MSAA. The
                // game already performs its own Xenos resolve and this
                // duplicate sample cost is especially expensive on Adreno.
                "--native_2x_msaa=false",
                // Explicitly keep the swap post-process disabled. This is
                // the native equivalent of removing FXAA/bloom-like screen
                // effects; the default is none, but make the Android profile
                // independent of persisted runtime cvars.
                "--swap_post_effect=none",
                // Keep the diagnostic tracer off during normal runtime; enable
                // it only for a focused capture because it adds per-draw work.
                "--d3d_trace_frames=0",
                // Match the Vulkan/Xenos compatibility profile used by the
                // working NFSMW Android port on Adreno devices.
                "--vulkan_native_shader_features=false",
                "--render_target_path_vulkan=fbo",
                // Adreno comparison: traditional render passes can have lower
                // driver overhead than dynamic rendering for this workload.
                "--vulkan_dynamic_rendering=false",
                // Let Vulkan submit at the primary-buffer boundary instead of
                // forcing extra intermediate submissions on this workload.
                "--vulkan_submit_on_primary_buffer_end=false",
                "--vulkan_require_geometry_shader=false",
                "--vulkan_require_fill_mode_non_solid=false",
                // FIFO lets the 60 Hz display pace presentation instead of keeping the
                // Presentation thread busy in immediate/tearing mode. When the launcher
                // disables VSync, allow the backend to select IMMEDIATE if available.
                "--vsync=" + settingVsync,
                "--vulkan_allow_present_mode_immediate=" + !settingVsync,
                "--host_present_from_non_ui_thread=false",
                // Reduce texture sampling cost on the Adreno during the
                // shader/pipeline warm-up phase.
                "--anisotropic_override=" + settingAnisotropic,
                // The base XEX emits a very large number of intentionally
                // padded vertex fetches during the intro. Keep the diagnostic
                // scan off on Android so it cannot flood the render thread.
                "--vfetch_oob_trace=false",
                // Keep a small audio cushion for the game's 256-sample bursts.
                // Ten frames is still low-latency (~53 ms at 48 kHz) but avoids
                // starving the stereo Android output during shader/video bursts.
                "--audio_maxqframes=" + settingAudioFrames,
                // Synchronous creation is the verified stable path for this
                // Adreno profile; async pipeline misses did not improve FPS.
                "--async_shader_compilation=" + settingAsyncShaders,
                "--vulkan_pipeline_creation_threads=" + settingPipelineThreads,
                "--vulkan_async_skip_incomplete_frames=" + settingAsyncShaders,
        };
    }
}
