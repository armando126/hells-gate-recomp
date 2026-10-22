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

import org.libsdl.app.SDLActivity;

import java.io.File;

/** SDL-owned game screen. SDLActivity supplies the main thread, surface and event loop. */
public final class GameActivity extends SDLActivity implements InputManager.InputDeviceListener {
    private static final String TAG = "Hellsgate";
    // Keep the SurfaceView full-screen, but render the game into a smaller buffer.
    // 1280x720 preserves the game's 16:9 content while minimizing the Adreno workload.
    private static final int INTERNAL_WIDTH = 1280;
    private static final int INTERNAL_HEIGHT = 720;
    private String gameRoot;
    private String userRoot;
    private String cacheRoot;
    private TouchControlsView touchControls;
    private PerformanceOverlayView performanceOverlay;
    private InputManager inputManager;

    @Override
    protected void onCreate(Bundle state) {
        File appRoot = new File(getFilesDir(), "hellsgate");
        gameRoot = new File(getFilesDir(), "game").getAbsolutePath();
        userRoot = new File(appRoot, "user").getAbsolutePath();
        cacheRoot = new File(appRoot, "cache").getAbsolutePath();
        new File(userRoot).mkdirs();
        new File(cacheRoot).mkdirs();
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
        performanceOverlay = new PerformanceOverlayView(this);
        root.addView(performanceOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                "--renderer=native",
                // Match the Vulkan/Xenos compatibility profile used by the
                // working NFSMW Android port on Adreno devices.
                "--vulkan_native_shader_features=false",
                "--render_target_path_vulkan=fbo",
                "--vulkan_require_geometry_shader=false",
                "--vulkan_require_fill_mode_non_solid=false",
                // FIFO lets the 60 Hz display pace presentation instead of keeping the
                // Presentation thread busy in immediate/tearing mode.
                "--vsync=true",
                "--vulkan_allow_present_mode_immediate=false",
                "--host_present_from_non_ui_thread=false",
                // Reduce texture sampling cost on the Adreno during the
                // shader/pipeline warm-up phase.
                "--anisotropic_override=0",
                // The base XEX emits a very large number of intentionally
                // padded vertex fetches during the intro. Keep the diagnostic
                // scan off on Android so it cannot flood the render thread.
                "--vfetch_oob_trace=false",
                // Keep a small audio cushion for the game's 256-sample bursts.
                // Ten frames is still low-latency (~53 ms at 48 kHz) but avoids
                // starving the stereo Android output during shader/video bursts.
                "--audio_maxqframes=10",
                // Synchronous creation is slower during the first load, but
                // is required by this Adreno path to present the first frame.
                "--async_shader_compilation=false",
                "--vulkan_async_skip_incomplete_frames=false",
        };
    }
}
