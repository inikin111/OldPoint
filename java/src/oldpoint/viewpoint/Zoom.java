package oldpoint.viewpoint;

import zombie.input.GameKeyboard;
import zombie.core.Core;
import zombie.input.Mouse;
import zombie.ui.UIManager;
import viewpoint.platform.SettingsWindow;

/** Continuous camera zoom with independent limits and time-based easing. */
public final class Zoom {
    private static double minimum = 10;
    private static double maximum = 600;
    private static double nativeMinimum = 10;
    private static double current = 100;
    private static double target = 100;
    private static double remembered = 100;
    private static long lastUpdate;
    private static volatile float renderedFactor = 1f;
    private static final WheelCycle wheelCycle = new WheelCycle();
    private static volatile int reservedModifier;
    private static boolean reportedWheelFallback;

    private Zoom() {}

    private static double clamp(double value) {
        double lower = Math.max(minimum, nativeMinimum);
        return Math.max(lower, Math.min(Math.max(lower, maximum), value));
    }

    /** Native zoom and Layout's cameraZoom express the same tile scale at the camera target plane. */
    public static synchronized double nativeMinimumDistance() {
        double nativeZoom = Core.getInstance().getMinZoom();
        if (Double.isFinite(nativeZoom) && nativeZoom > 0) {
            nativeMinimum = Math.max(10, Math.min(600, nativeZoom * 100.0 / Layout.CAMERA_ZOOM_SCALE));
        }
        target = clamp(target);
        current = clamp(current);
        remembered = clamp(remembered);
        return nativeMinimum;
    }

    public static synchronized void limits(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return;
        }
        minimum = Math.max(10, Math.min(600, min));
        maximum = Math.max(minimum, Math.max(10, Math.min(600, max)));
        target = clamp(target);
        current = clamp(current);
        remembered = clamp(remembered);
    }

    public static synchronized void reset() {
        restoreValue(100);
    }

    /** Keep the last captured view distance, without advancing its easing. */
    public static synchronized void remember() {
        remembered = clamp(current);
    }

    public static synchronized void restore() {
        restoreValue(remembered);
    }

    private static void restoreValue(double value) {
        wheelCycle.clear();
        current = target = clamp(value);
        renderedFactor = (float) (current / 100);
        lastUpdate = System.nanoTime();
    }

    public static synchronized float factor() {
        nativeMinimumDistance();
        long now = System.nanoTime();
        double seconds = lastUpdate == 0 ? 0 : Math.min(0.1, (now - lastUpdate) * 1e-9);
        lastUpdate = now;
        current += (target - current) * (1 - Math.exp(-seconds / 0.12));
        if (Math.abs(target - current) < 0.001) {
            current = target;
        }
        renderedFactor = (float) (clamp(current) / 100);
        return renderedFactor;
    }

    /** The last camera sample; UI sizing must not advance the zoom animation again. */
    public static float renderedFactor() {
        return renderedFactor;
    }

    public static synchronized boolean scroll(int player, int direction) {
        if (!CameraController.topDownActive() || player != 0) {
            return false;
        }
        // The generic event/native-input route owns the wheel. Core may be
        // skipped, called twice, or called by another camera mod independently.
        // None of those calls may apply a second zoom to this input dispatch.
        return true;
    }

    /** Kept for older Lua scripts; wheel ownership is now independent of QOL. */
    public static void qolWheelRouting(boolean value) {}

    public static void reserveModifier(boolean enabled, int key) {
        reservedModifier = enabled && key > 0 && key < 512 ? key : 0;
    }

    private static boolean permitted() {
        if (!CameraController.topDownActive() || UIManager.isModalVisible() || SettingsWindow.shown()) {
            return false;
        }
        int key = reservedModifier;
        if (key == 0) {
            return true;
        }
        if (GameKeyboard.isKeyDown(key)) {
            return false;
        }
        int other =
                switch (key) {
                    case 56 -> 184;
                    case 184 -> 56;
                    case 29 -> 157;
                    case 157 -> 29;
                    case 42 -> 54;
                    case 54 -> 42;
                    default -> 0;
                };
        return other == 0 || !GameKeyboard.isKeyDown(other);
    }

    public static synchronized void beginWheelCycle() {
        wheelCycle.begin(CameraController.topDownActive() ? Mouse.getWheelState() : 0);
    }

    public static synchronized void consumeUIWheel() {
        wheelCycle.consumed();
    }

    public static synchronized void endWheelCycle() {
        int direction = wheelCycle.finish(permitted());
        if (direction == 0) {
            return;
        }
        adjust(-direction);
        if (!reportedWheelFallback) {
            reportedWheelFallback = true;
            System.out.println("[OldPoint] wheel input recovered through native UI fallback");
        }
    }

    public static synchronized void wheel(double delta) {
        wheel(delta, true);
    }

    public static synchronized void wheel(double delta, boolean allowed) {
        if (!CameraController.topDownActive()) {
            return;
        }
        // Inside native UI dispatch, actual wheel consumption decides whether
        // UI owns the input. A transparent full-screen overlay alone must not
        // suppress world zoom. Manual calls outside it keep the hover guard.
        int direction = wheelCycle.event(delta, allowed && permitted() && (wheelCycle.active() || !Pointer.overUI()));
        if (direction == 0) {
            return;
        }
        // UIManager sends raw wheel sign to Lua, and its opposite to doZoomScroll.
        adjust(-direction);
    }

    private static void adjust(int direction) {
        // Adjust a continuous target instead of selecting a preset percentage.
        target = clamp(target * Math.exp(Math.signum(direction) * 0.08));
    }
}
