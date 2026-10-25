package oldpoint.viewpoint;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Matrix4f;
import viewpoint.core.Frame;
import zombie.characters.IsoPlayer;

public final class CameraDrawingTest {
    private static final class Drawer {
        private final Frame frame;
        private final float[] eye = new float[3];
        private final float[] dir = new float[3];
        private static final Matrix4f view = new Matrix4f();

        private Drawer(Frame frame) {
            this.frame = frame;
        }
    }

    public static void main(String[] args) throws Exception {
        Frame frame = new Frame();
        IsoPlayer player = new IsoPlayer();
        player.x = 10;
        player.y = 20;
        CameraController.yaw = 0;
        CameraController.pitch = 0;
        CameraCapture.capture(frame, player);
        Drawer drawer = new Drawer(frame);
        CaptureRuntime.events.clear();
        float[] result = new float[3];
        switch (args[0]) {
            case "captured-view" -> {
                CameraDrawing.beginDrawing(drawer);
                int advances = CameraController.advances;
                CameraController.yaw = 2;
                CameraController.active = false;
                require(CameraDrawing.direction(result), "Drawing retains captured overhead mode");
                near(result[0], -1);
                near(result[1], 0);
                near(result[2], 0);
                near(CameraDrawing.weatherActive(), 1);
                near(CameraDrawing.weatherAnchor(0), -10);
                near(CameraDrawing.weatherAnchor(2), -20);
                require(CameraController.advances == advances, "Drawing must not advance rotation");
                CameraDrawing.endDrawing();
                require(!CameraDrawing.drawTopDown(), "End restores fallback mode");
                near(CameraDrawing.weatherActive(), 0);
            }
            case "normal-frame" -> {
                CameraDrawing.beginDrawing(new Drawer(new Frame()));
                require(!CameraDrawing.drawTopDown(), "Uncaptured frame overrides live overhead mode");
                require(!CameraDrawing.direction(result), "Normal frame leaves backend direction alone");
                CameraDrawing.endDrawing();
                require(CameraDrawing.drawTopDown(), "Unbind returns to live fallback");
            }
            case "fallback" -> {
                int advances = CameraController.advances;
                CameraController.yaw = (float) (Math.PI / 2);
                require(CameraDrawing.direction(result), "Fallback direction available");
                near(result[2], -1);
                require(CameraController.advances == advances, "Fallback is read-only");
                near(CameraDrawing.fieldOfView(1), Layout.FOV);
                CameraController.active = false;
                near(CameraDrawing.fieldOfView(1), 1);
            }
            case "position" -> {
                require(CameraDrawing.position(frame, result), "Captured position available");
                for (int i = 0; i < 3; i++) {
                    near(result[i], CaptureRuntime.interiorEye[i]);
                }
                require(CameraDrawing.frameTopDown(frame), "Captured frame marked overhead");
                require(!CameraDrawing.position(new Frame(), result), "Missing snapshot leaves backend position alone");
            }
            case "center" -> {
                CameraDrawing.position(frame, drawer.eye);
                CameraDrawing.beginDrawing(drawer);
                require(CameraDrawing.centerDrawing(drawer), "Center captured frame");
                near(drawer.dir[0], -1);
                near(drawer.dir[1], 0);
                near(drawer.dir[2], 0);
                near(Drawer.view.lookAt[3], frame.eyeX);
                near(Drawer.view.lookAt[4], frame.eyeY);
                near(Drawer.view.lookAt[5], frame.eyeZ);
                CameraDrawing.endDrawing();
            }
            case "invalid-center" -> {
                drawer.eye[0] = frame.eyeX;
                drawer.eye[1] = frame.eyeY;
                drawer.eye[2] = frame.eyeZ;
                CameraDrawing.beginDrawing(drawer);
                require(!CameraDrawing.centerDrawing(drawer), "Reject zero-length look vector");
                drawer.eye[0] = Float.NaN;
                require(!CameraDrawing.centerDrawing(drawer), "Reject non-finite look vector");
                CameraDrawing.endDrawing();
            }
            case "cleanup-trees", "cleanup-interior", "cleanup-roofs" -> {
                CameraDrawing.beginDrawing(drawer);
                CaptureRuntime.events.clear();
                TreeClearing.failEnd = args[0].equals("cleanup-trees");
                Interior.failEnd = args[0].equals("cleanup-interior");
                RoofModels.failEnd = args[0].equals("cleanup-roofs");
                boolean threw = false;
                try {
                    CameraDrawing.endDrawing();
                } catch (IllegalStateException expected) {
                    threw = true;
                }
                require(threw, "Cleanup error remains visible");
                require(
                        CaptureRuntime.events.equals(List.of("trees-end", "interior-end", "roofs-end")),
                        "Every cleanup stage runs after a failure");
                CameraController.active = false;
                require(!CameraDrawing.drawTopDown(), "Failed cleanup must still unbind camera snapshot");
                near(CameraDrawing.weatherActive(), 0);
            }
            case "reflection-error" -> {
                CameraDrawing.beginDrawing(new Object());
                require(!CameraDrawing.drawTopDown(), "Failed binding uses normal drawing state");
                require(CompatibilityErrors.reports == 1, "Report incompatible drawer");
                CameraDrawing.endDrawing();
            }
            case "thread-isolation" -> {
                CameraDrawing.beginDrawing(drawer);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread worker =
                        new Thread(
                                () -> {
                                    try {
                                        CameraDrawing.beginDrawing(new Drawer(new Frame()));
                                        require(!CameraDrawing.drawTopDown(), "Other thread binds its own frame");
                                        CameraDrawing.endDrawing();
                                    } catch (Throwable error) {
                                        failure.set(error);
                                    }
                                });
                worker.start();
                worker.join();
                require(failure.get() == null, "Worker failed: " + failure.get());
                require(CameraDrawing.drawTopDown(), "Other thread cannot replace drawing snapshot");
                CameraDrawing.endDrawing();
            }
            default -> throw new AssertionError("Unknown scenario: " + args[0]);
        }
        System.out.println("PASS " + args[0]);
    }

    private static void near(float actual, float expected) {
        require(
                Float.isFinite(actual) && Math.abs(actual - expected) < 0.0001f,
                "Expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
