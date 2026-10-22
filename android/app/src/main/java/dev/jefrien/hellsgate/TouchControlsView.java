package dev.jefrien.hellsgate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/** A compact Xbox 360 overlay. A physical SDL/Android gamepad hides it. */
public final class TouchControlsView extends View {
    private static final int UP = 0x0001, DOWN = 0x0002, LEFT = 0x0004, RIGHT = 0x0008;
    private static final int START = 0x0010, BACK = 0x0020, LTHUMB = 0x0040, RTHUMB = 0x0080;
    private static final int LB = 0x0100, RB = 0x0200;
    private static final int A = 0x1000, B = 0x2000, X = 0x4000, Y = 0x8000;

    private static native void nativeSetTouchState(int buttons, int leftX, int leftY,
                                                    int rightX, int rightY,
                                                    int leftTrigger, int rightTrigger);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Integer> buttonsByPointer = new HashMap<>();
    private final Map<Integer, Integer> triggersByPointer = new HashMap<>();
    private int buttons;
    private int leftPointer = -1, rightPointer = -1;
    private float leftX, leftY, rightX, rightY;
    private int leftTrigger, rightTrigger;
    private boolean gamepadActive;

    public TouchControlsView(Context context) {
        super(context);
        setFocusable(false);
        setBackgroundColor(Color.TRANSPARENT);
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        nativeSetTouchState(0, 0, 0, 0, 0, 0, 0);
    }

    public void onGamepadActive() {
        gamepadActive = true;
        setVisibility(INVISIBLE);
        resetState();
    }

    public void onGamepadGone() {
        gamepadActive = false;
        setVisibility(VISIBLE);
    }

    private void resetState() {
        buttonsByPointer.clear();
        triggersByPointer.clear();
        buttons = 0;
        leftPointer = rightPointer = -1;
        leftX = leftY = rightX = rightY = 0;
        leftTrigger = rightTrigger = 0;
        publish();
    }

    private float sx() { return getWidth() / 1920.0f; }
    private float sy() { return getHeight() / 1080.0f; }
    private float x(float value) { return value * sx(); }
    private float y(float value) { return value * sy(); }

    private static final float DPAD_X = 270, DPAD_Y = 520;
    private static final float FACE_X = 1650, FACE_Y = 510;
    private static final float FACE_TOP_Y = 420, FACE_BOTTOM_Y = 600;

    @Override protected void onDraw(Canvas canvas) {
        if (gamepadActive) return;
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(62, 255, 255, 255));
        circle(canvas, 270, 820, 145);
        circle(canvas, 1650, 820, 145);
        circle(canvas, 1650, 820, 72);
        paint.setColor(Color.argb(85, 20, 20, 20));
        circle(canvas, 270 + leftX * 85, 820 - leftY * 85, 48);
        circle(canvas, 1650 + rightX * 85, 820 - rightY * 85, 48);
        circle(canvas, FACE_X, FACE_TOP_Y, 44); label(canvas, "Y", FACE_X, FACE_TOP_Y + 7);
        circle(canvas, 1560, FACE_Y, 44); label(canvas, "X", 1560, FACE_Y + 7);
        circle(canvas, 1740, FACE_Y, 44); label(canvas, "B", 1740, FACE_Y + 7);
        circle(canvas, FACE_X, FACE_BOTTOM_Y, 44);
        label(canvas, "A", FACE_X, FACE_BOTTOM_Y + 7);
        dpad(canvas);
        rect(canvas, 685, 90, 980, 155); label(canvas, "LB", 832, 135);
        rect(canvas, 940, 90, 1235, 155); label(canvas, "RB", 1088, 135);
        rect(canvas, 620, 930, 820, 1020); label(canvas, "LT", 720, 985);
        rect(canvas, 1100, 930, 1300, 1020); label(canvas, "RT", 1200, 985);
        rect(canvas, 780, 510, 900, 585); label(canvas, "BACK", 840, 565);
        rect(canvas, 1020, 510, 1140, 585); label(canvas, "START", 1080, 565);
    }

    private void circle(Canvas c, float px, float py, float radius) {
        c.drawCircle(x(px), y(py), Math.min(sx(), sy()) * radius, paint);
    }

    private void rect(Canvas c, float l, float t, float r, float b) {
        c.drawRoundRect(new RectF(x(l), y(t), x(r), y(b)), 18, 18, paint);
    }

    private void dpad(Canvas c) {
        rect(c, DPAD_X - 35, DPAD_Y - 105, DPAD_X + 35, DPAD_Y + 105);
        rect(c, DPAD_X - 105, DPAD_Y - 35, DPAD_X + 105, DPAD_Y + 35);
        label(c, "▲", DPAD_X, DPAD_Y - 55);
        label(c, "▼", DPAD_X, DPAD_Y + 78);
        label(c, "◀", DPAD_X - 58, DPAD_Y + 9);
        label(c, "▶", DPAD_X + 58, DPAD_Y + 9);
    }

    private void label(Canvas c, String text, float px, float py) {
        paint.setColor(Color.argb(155, 255, 255, 255));
        paint.setTextSize(Math.min(sx(), sy()) * 25);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, x(px), y(py), paint);
        paint.setColor(Color.argb(62, 255, 255, 255));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (gamepadActive) return false;
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointer = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float px = event.getX(index) / sx();
            float py = event.getY(index) / sy();
            if (distance(px, py, 270, 820) < 155 && leftPointer < 0) {
                leftPointer = pointer;
                updateStick(event.getX(index), event.getY(index), true);
            } else if (distance(px, py, 1650, 820) < 155 && rightPointer < 0) {
                rightPointer = pointer;
                updateStick(event.getX(index), event.getY(index), false);
            } else {
                int trigger = triggerAt(px, py);
                if (trigger != 0) {
                    triggersByPointer.put(pointer, trigger);
                    if (trigger == 1) leftTrigger = 255; else rightTrigger = 255;
                } else {
                    int mask = buttonAt(px, py);
                    if (mask != 0) {
                    buttonsByPointer.put(pointer, mask);
                    buttons |= mask;
                    }
                }
            }
            publish(); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (leftPointer >= 0) updateStickByPointer(event, leftPointer, true);
            if (rightPointer >= 0) updateStickByPointer(event, rightPointer, false);
            publish(); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            if (pointer == leftPointer) { leftPointer = -1; leftX = leftY = 0; }
            if (pointer == rightPointer) { rightPointer = -1; rightX = rightY = 0; }
            Integer mask = buttonsByPointer.remove(pointer);
            if (mask != null) buttons &= ~mask;
            Integer trigger = triggersByPointer.remove(pointer);
            if (trigger != null) {
                if (trigger == 1) leftTrigger = 0; else rightTrigger = 0;
            }
            publish(); invalidate(); return true;
        }
        return true;
    }

    private void updateStickByPointer(MotionEvent e, int id, boolean left) {
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (e.getPointerId(i) == id) { updateStick(e.getX(i), e.getY(i), left); return; }
        }
    }

    private void updateStick(float rawX, float rawY, boolean left) {
        float cx = x(left ? 270 : 1650), cy = y(820), radius = Math.min(sx(), sy()) * 110;
        float dx = (rawX - cx) / radius, dy = (rawY - cy) / radius;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length > 1) { dx /= length; dy /= length; }
        if (left) { leftX = dx; leftY = -dy; } else { rightX = dx; rightY = -dy; }
    }

    private int buttonAt(float px, float py) {
        if (distance(px, py, FACE_X, FACE_TOP_Y) <= 65) return Y;
        if (distance(px, py, 1560, FACE_Y) <= 65) return X;
        if (distance(px, py, 1740, FACE_Y) <= 65) return B;
        if (distance(px, py, FACE_X, FACE_BOTTOM_Y) <= 65) return A;
        if (px > 680 && px < 990 && py < 190) return LB;
        if (px >= 940 && px < 1260 && py < 190) return RB;
        if (px > 760 && px < 920 && py > 480 && py < 620) return BACK;
        if (px >= 1000 && px < 1170 && py > 480 && py < 620) return START;
        if (px >= DPAD_X - 70 && px <= DPAD_X + 70 && py >= DPAD_Y - 105 && py < DPAD_Y - 15) return UP;
        if (px >= DPAD_X - 70 && px <= DPAD_X + 70 && py > DPAD_Y + 15 && py <= DPAD_Y + 105) return DOWN;
        if (px >= DPAD_X - 105 && px < DPAD_X - 15 && py >= DPAD_Y - 70 && py <= DPAD_Y + 70) return LEFT;
        if (px > DPAD_X + 15 && px <= DPAD_X + 105 && py >= DPAD_Y - 70 && py <= DPAD_Y + 70) return RIGHT;
        return 0;
    }

    private int triggerAt(float px, float py) {
        if (px > 600 && px < 850 && py > 900) return 1;
        if (px > 1080 && px < 1320 && py > 900) return 2;
        return 0;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    private void publish() {
        nativeSetTouchState(buttons, Math.round(leftX * 32767), Math.round(leftY * 32767),
                Math.round(rightX * 32767), Math.round(rightY * 32767), leftTrigger, rightTrigger);
    }
}
