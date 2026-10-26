package oldpoint.viewpoint;

import java.lang.reflect.Field;
import viewpoint.core.Frame;

/** Binds captured camera state to the drawing thread; reads never advance camera animation. */
final class CameraDrawing {
    private static Field drawerFrame;
    private static Field drawerEye;
    private static Field drawerDirection;
    private static Field drawerView;
    private static boolean reportedCenterMismatch;
    private static final ThreadLocal<CameraCapture.Snapshot> drawing = new ThreadLocal<>();

    private CameraDrawing() {}

    static void beginDrawing(Object drawer) {
        try {
            if (drawerFrame == null) {
                drawerFrame = drawer.getClass().getDeclaredField("frame");
                drawerFrame.setAccessible(true);
            }
            Frame frame = (Frame) drawerFrame.get(drawer);
            drawing.set(CameraCapture.snapshot(frame));
            Interior.begin(frame.scene);
            TreeClearing.begin(frame.scene);
        } catch (ReflectiveOperationException error) {
            report(error);
            drawing.set(CameraCapture.NORMAL);
        }
    }

    static void endDrawing() {
        try {
            TreeClearing.end();
        } finally {
            try {
                Interior.end();
            } finally {
                try {
                    RoofModels.end();
                } finally {
                    drawing.remove();
                }
            }
        }
    }

    static boolean centerDrawing(Object drawer) {
        CameraCapture.Snapshot state = drawing.get();
        if (state == null || !state.topDown()) {
            return false;
        }
        try {
            if (drawerEye == null) {
                Class<?> type = drawer.getClass();
                drawerEye = type.getDeclaredField("eye");
                drawerDirection = type.getDeclaredField("dir");
                drawerView = type.getDeclaredField("view");
                drawerEye.setAccessible(true);
                drawerDirection.setAccessible(true);
                drawerView.setAccessible(true);
            }
            Frame frame = (Frame) drawerFrame.get(drawer);
            float[] eye = (float[]) drawerEye.get(drawer);
            float[] direction = (float[]) drawerDirection.get(drawer);
            float dx = frame.eyeX - eye[0];
            float dy = frame.eyeY - eye[1];
            float dz = frame.eyeZ - eye[2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Float.isFinite(length) || length < 0.0001f) {
                return false;
            }
            dx /= length;
            dy /= length;
            dz /= length;
            float error = Math.abs(dx - direction[0]) + Math.abs(dy - direction[1]) + Math.abs(dz - direction[2]);
            if (!reportedCenterMismatch && error > 0.001f) {
                reportedCenterMismatch = true;
                System.out.println("[OldPoint] camera center direction mismatch: " + error + ", frame=" + frame.number);
            }
            direction[0] = dx;
            direction[1] = dy;
            direction[2] = dz;
            // Aim directly at the captured player pivot, not eye + a direction
            // sampled elsewhere. This also avoids subtractive precision loss
            // from constructing a target only one unit from a distant eye.
            ((org.joml.Matrix4f) drawerView.get(null))
                    .setLookAt(eye[0], eye[1], eye[2], frame.eyeX, frame.eyeY, frame.eyeZ, 0, 1, 0);
            return true;
        } catch (ReflectiveOperationException error) {
            report(error);
            return false;
        }
    }

    static boolean drawTopDown() {
        CameraCapture.Snapshot state = drawing.get();
        return state != null ? state.topDown() : CameraController.topDownActive();
    }

    static float weatherActive() {
        CameraCapture.Snapshot state = drawing.get();
        return state != null && state.topDown() ? 1 : 0;
    }

    static float weatherAnchor(int axis) {
        CameraCapture.Snapshot state = drawing.get();
        return state == null ? 0 : axis == 0 ? state.anchorX() : axis == 1 ? state.anchorY() : state.anchorZ();
    }

    static boolean direction(float[] result) {
        if (!drawTopDown()) {
            return false;
        }
        CameraCapture.Snapshot state = drawing.get();
        if (state != null) {
            result[0] = state.directionX();
            result[1] = state.directionY();
            result[2] = state.directionZ();
        } else {
            result[0] = (float) (-Math.cos(CameraController.orbitYaw()) * Math.cos(CameraController.overheadPitch()));
            result[1] = (float) Math.sin(CameraController.overheadPitch());
            result[2] = (float) (-Math.sin(CameraController.orbitYaw()) * Math.cos(CameraController.overheadPitch()));
        }
        return true;
    }

    static boolean position(Object value, float[] result) {
        Frame frame = (Frame) value;
        CameraCapture.Snapshot state = CameraCapture.snapshot(frame);
        if (!state.topDown()) {
            return false;
        }
        result[0] = frame.eyeX - state.offsetX();
        result[1] = frame.eyeY + state.offsetUp();
        result[2] = frame.eyeZ - state.offsetZ();
        return true;
    }

    static boolean frameTopDown(Object value) {
        CameraCapture.Snapshot state = CameraCapture.snapshot((Frame) value);
        return state.topDown();
    }

    static float fieldOfView(float normal) {
        return CameraController.topDownActive() ? Layout.FOV : normal;
    }

    private static void report(Throwable error) {
        CompatibilityErrors.report(error);
    }
}
