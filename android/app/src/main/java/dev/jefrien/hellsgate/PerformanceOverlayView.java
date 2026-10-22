package dev.jefrien.hellsgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Small diagnostic overlay kept outside the SDL/Vulkan render path. */
public final class PerformanceOverlayView extends View {
    private static final long UPDATE_MS = 500;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = this::updateStats;
    private final int cpuCount = Math.max(1, Runtime.getRuntime().availableProcessors());

    private long lastCpuNanos;
    private long lastWallNanos;
    private long lastPresentedFrames;
    private long lastFrameSampleNanos;
    private float fps;
    private float processCpuPercent;
    private float cpuTemperature = Float.NaN;
    private int batteryPercent = -1;
    private float batteryTemperature = Float.NaN;

    private static native long nativeGetPresentedFrames();

    public PerformanceOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        backgroundPaint.setColor(Color.argb(145, 0, 0, 0));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(10.0f * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastCpuNanos = android.os.Process.getElapsedCpuTime() * 1_000_000L;
        lastWallNanos = System.nanoTime();
        lastFrameSampleNanos = lastWallNanos;
        lastPresentedFrames = nativeGetPresentedFrames();
        handler.post(updateRunnable);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(updateRunnable);
        super.onDetachedFromWindow();
    }

    private void updateStats() {
        long nowCpu = android.os.Process.getElapsedCpuTime() * 1_000_000L;
        long nowWall = System.nanoTime();
        long cpuDelta = nowCpu - lastCpuNanos;
        long wallDelta = nowWall - lastWallNanos;
        if (wallDelta > 0) {
            // Normalize process CPU time to the whole device: 100% means all
            // logical cores are busy with this process.
            processCpuPercent = 100.0f * cpuDelta / wallDelta / cpuCount;
        }
        lastCpuNanos = nowCpu;
        lastWallNanos = nowWall;
        long presentedFrames = nativeGetPresentedFrames();
        long frameWallDelta = nowWall - lastFrameSampleNanos;
        if (frameWallDelta > 0) {
            fps = (presentedFrames - lastPresentedFrames) * 1_000_000_000.0f / frameWallDelta;
        }
        lastPresentedFrames = presentedFrames;
        lastFrameSampleNanos = nowWall;
        cpuTemperature = readCpuTemperature();
        readBattery();
        postInvalidate();
        handler.postDelayed(updateRunnable, UPDATE_MS);
    }

    private void readBattery() {
        Intent battery = getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;
        int level = battery.getIntExtra("level", -1);
        int scale = battery.getIntExtra("scale", -1);
        batteryPercent = level >= 0 && scale > 0 ? Math.round(level * 100.0f / scale) : -1;
        int temp = battery.getIntExtra("temperature", Integer.MIN_VALUE);
        batteryTemperature = temp == Integer.MIN_VALUE ? Float.NaN : temp / 10.0f;
    }

    private float readCpuTemperature() {
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) return Float.NaN;
        float fallback = Float.NaN;
        for (File zone : zones) {
            String type = readFirstLine(new File(zone, "type"));
            String value = readFirstLine(new File(zone, "temp"));
            if (value == null) continue;
            try {
                float temperature = Float.parseFloat(value.trim()) / 1000.0f;
                if (temperature < -20.0f || temperature > 150.0f) continue;
                if (!Float.isNaN(fallback)) fallback = Math.max(fallback, temperature);
                else fallback = temperature;
                String lowerType = type == null ? "" : type.toLowerCase(Locale.ROOT);
                if (lowerType.contains("cpu") || lowerType.contains("soc") ||
                        lowerType.contains("tsens") || lowerType.contains("ap")) {
                    return temperature;
                }
            } catch (NumberFormatException ignored) {
                // Some vendor thermal zones are not numeric.
            }
        }
        return fallback;
    }

    private String readFirstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 8.0f * density;
        float top = 8.0f * density;
        float line = 13.0f * density;
        float width = 170.0f * density;
        float height = 3.0f * line + 8.0f * density;
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                6.0f * density, 6.0f * density, backgroundPaint);
        String temperature = Float.isNaN(cpuTemperature) ? "--" : String.format(Locale.US, "%.1fC", cpuTemperature);
        String batteryTemp = Float.isNaN(batteryTemperature) ? "--" : String.format(Locale.US, "%.1fC", batteryTemperature);
        String battery = batteryPercent < 0 ? "--" : Integer.toString(batteryPercent);
        canvas.drawText(String.format(Locale.US, "FPS %.1f  CPU %.1f%%", fps, processCpuPercent),
                left + 5.0f * density, top + line, textPaint);
        canvas.drawText("SoC " + temperature + "  BAT " + battery + "%", left + 7.0f * density,
                top + 2.0f * line, textPaint);
        canvas.drawText("BatTemp " + batteryTemp, left + 7.0f * density,
                top + 3.0f * line, textPaint);
    }
}
