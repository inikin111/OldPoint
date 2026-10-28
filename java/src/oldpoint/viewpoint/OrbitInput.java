package oldpoint.viewpoint;

import java.lang.reflect.Field;
import org.lwjgl.glfw.GLFW;
import org.lwjglx.opengl.Display;
import viewpoint.input.Look;
import viewpoint.platform.LiveSettings;

/** Own the overhead drag delta independently of FPS look/keyboard modifiers. */
final class OrbitInput {
    private static final double SMOOTH_SECONDS = 0.008;
    private static final float MAX_LAG = 0.006f;
    private static final double[] x = new double[1], y = new double[1];
    private static boolean sampled, reported;
    private static long previousWindow;
    private static double previousX;
    private static double cursorX, cursorY;
    private static long cursorWindow, readAt;
    private static boolean restoring;
    private static float targetYaw;
    private static LiveSettings.Number sensitivity;

    private OrbitInput() {}

    static void reset() {
        sampled = false;
        previousWindow = 0;
        readAt = 0;
    }

    /** Runs before Viewpoint can capture or recenter the cursor. */
    static void beforeUpdate(boolean dragging) {
        long window = Display.getWindow();
        if (window == 0) {
            return;
        }
        if (dragging && cursorWindow == 0 && !Look.captured) {
            GLFW.glfwGetCursorPos(window, x, y);
            cursorX = x[0];
            cursorY = y[0];
            cursorWindow = window;
            restoring = false;
            reset();
        } else if (!dragging && cursorWindow != 0) {
            restoring = true;
        }
    }

    private static double sampleCursor(long window) {
        GLFW.glfwGetCursorPos(window, x, y);
        double delta = sampled && previousWindow == window ? x[0] - previousX : 0;
        previousX = x[0];
        previousWindow = window;
        sampled = true;
        return delta;
    }

    static float read(float yaw, boolean dragging) {
        return read(yaw, dragging, System.nanoTime());
    }

    static float read(float yaw, boolean dragging, long now) {
        long window = Display.getWindow();
        if (restoring && !Look.captured) {
            if (cursorWindow == window && window != 0) {
                GLFW.glfwSetCursorPos(
                        window,
                        Math.max(0, Math.min(Display.getWidth() - 1, cursorX)),
                        Math.max(0, Math.min(Display.getHeight() - 1, cursorY)));
            }
            cursorWindow = 0;
            restoring = false;
        }
        if (!dragging
                || !Look.captured
                || window == 0
                || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
                || GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
            reset();
            return yaw;
        }
        double delta = sampleCursor(window);
        float speed = 1;
        try {
            if (sensitivity == null) {
                Field field = Look.class.getDeclaredField("SPEED");
                field.setAccessible(true);
                sensitivity = (LiveSettings.Number) field.get(null);
            }
            speed = sensitivity.get();
        } catch (ReflectiveOperationException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] orbit sensitivity fallback: " + error);
            }
        }
        if (!Double.isFinite(delta) || !Float.isFinite(speed)) {
            return yaw;
        }
        float scale = Float.isFinite(Look.zoom) && Look.zoom > 0 ? Look.zoom : 1;
        if (readAt == 0) {
            targetYaw = yaw;
        }
        targetYaw = Look.wrap(targetYaw + (float) delta * speed * 0.0022f * scale);
        double seconds = readAt == 0 ? 1.0 / 60 : Math.max(0, Math.min(0.1, (now - readAt) * 1e-9));
        readAt = now;
        float error = Look.wrap(targetYaw - yaw);
        // Short interpolation with a hard lag bound: fast drags stay attached to the hand.
        float remaining = (float) (error * Math.exp(-seconds / SMOOTH_SECONDS));
        remaining = Math.max(-MAX_LAG, Math.min(MAX_LAG, remaining));
        return Look.wrap(targetYaw - remaining);
    }
}
