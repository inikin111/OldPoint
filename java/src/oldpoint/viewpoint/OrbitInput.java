package oldpoint.viewpoint;

import java.lang.reflect.Field;
import org.lwjgl.glfw.GLFW;
import org.lwjglx.opengl.Display;
import viewpoint.input.Look;
import viewpoint.platform.LiveSettings;

/** Own the overhead drag delta independently of FPS look/keyboard modifiers. */
final class OrbitInput {
    private static final double[] x = new double[1], y = new double[1];
    private static boolean sampled, reported;
    private static long previousWindow;
    private static double previousX;
    private static LiveSettings.Number sensitivity;

    private OrbitInput() {}

    static void reset() {
        sampled = false;
        previousWindow = 0;
    }

    static float read(float yaw, boolean dragging) {
        long window = Display.getWindow();
        if (!dragging
                || !Look.captured
                || window == 0
                || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
                || GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
            reset();
            return yaw;
        }
        GLFW.glfwGetCursorPos(window, x, y);
        double delta = sampled && previousWindow == window ? x[0] - previousX : 0;
        previousX = x[0];
        previousWindow = window;
        sampled = true;
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
        return Look.wrap(yaw + (float) delta * speed * 0.0022f * scale);
    }
}
