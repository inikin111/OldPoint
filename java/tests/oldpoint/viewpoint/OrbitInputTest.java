package oldpoint.viewpoint;

import org.lwjgl.glfw.GLFW;
import viewpoint.input.Look;

/** Real input module with a virtual GLFW cursor and explicit sample times. */
public final class OrbitInputTest {
    private static long now = 1_000_000_000L;

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " != " + expected);
    }

    private static float begin(float yaw) {
        OrbitInput.beforeUpdate(true);
        Look.captured = true;
        GLFW.mode = GLFW.GLFW_CURSOR_DISABLED;
        return OrbitInput.read(yaw, true, now);
    }

    private static float move(float yaw, double dx, double dy, int milliseconds) {
        GLFW.x += dx;
        GLFW.y += dy;
        now += milliseconds * 1_000_000L;
        return OrbitInput.read(yaw, true, now);
    }

    private static float release(float yaw) {
        OrbitInput.beforeUpdate(false);
        Look.captured = false;
        GLFW.mode = 0;
        // Viewpoint recentres before the OldPoint exit callback.
        GLFW.glfwSetCursorPos(1, 960, 540);
        now += 16_000_000L;
        return OrbitInput.read(yaw, false, now);
    }

    public static void main(String[] args) {
        float yaw = begin(args[0].equals("wrap") ? (float) Math.PI - 0.01f : 0);
        switch (args[0]) {
            case "smooth" -> {
                yaw = move(yaw, 10, 0, 8);
                require(yaw > 0 && yaw < 0.022f, "Interpolate and respond on the first motion sample");
                require(0.022f - yaw <= 0.00601f, "Cap angular lag");
                float fast = move(yaw, 500, 0, 8);
                require(Math.abs(Look.wrap(1.122f - fast)) <= 0.00601f, "Fast drag stays near the target");
                float settled = move(fast, 0, 0, 16);
                require(settled > fast && settled <= 1.122f, "Held cursor settles without overshoot");
            }
            case "release" -> {
                yaw = move(yaw, 100, 70, 8);
                GLFW.x += 12;
                GLFW.y -= 9;
                float stopped = release(yaw);
                close(stopped, yaw, "Release must neither coast nor snap to target");
                close(GLFW.x, 300, "Release keeps the horizontal press position");
                close(GLFW.y, 200, "Release keeps the vertical press position");
                close(OrbitInput.read(stopped, false, now + 1_000_000_000L), stopped, "No release inertia");
                close(begin(stopped), stopped, "Next drag cannot inherit interpolation debt");
            }
            case "edge" -> {
                yaw = move(yaw, 5000, -5000, 16);
                release(yaw);
                close(GLFW.x, 300, "Offscreen drag cannot move the locked cursor horizontally");
                close(GLFW.y, 200, "Offscreen drag cannot move the locked cursor vertically");
            }
            case "focus" -> {
                yaw = move(yaw, 20, 30, 8);
                GLFW.focused = false;
                close(OrbitInput.read(yaw, true, now + 16_000_000L), yaw, "Focus loss freezes rotation");
                release(yaw);
                close(GLFW.x, 300, "Focus loss preserves the locked press position");
            }
            case "wrap" -> {
                float previous = yaw;
                yaw = move(yaw, 20, 0, 8);
                float turned = Look.wrap(yaw - previous);
                require(turned > 0 && turned < 0.044f, "Take the short path across pi");
            }
            case "rates" -> {
                for (int milliseconds : new int[] {4, 8, 16, 33}) {
                    float before = yaw;
                    yaw = move(yaw, milliseconds, 0, milliseconds);
                    float target = before + milliseconds * 0.0022f;
                    require(yaw > before && yaw <= target, "Responsive monotonic drag at each sample interval");
                    release(yaw);
                    yaw = begin(yaw);
                }
            }
            default -> throw new AssertionError(args[0]);
        }
        System.out.println("PASS orbit " + args[0]);
    }
}
