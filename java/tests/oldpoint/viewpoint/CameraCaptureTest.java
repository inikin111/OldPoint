package oldpoint.viewpoint;

import java.util.List;
import viewpoint.core.Frame;
import zombie.characters.IsoPlayer;

public final class CameraCaptureTest {
    public static void main(String[] args) {
        Frame frame = new Frame();
        frame.camX = 10;
        frame.camY = 20;
        frame.camZ = 1;
        frame.scene.originX = -100;
        frame.scene.originY = 3;
        frame.scene.originZ = -200;
        IsoPlayer player = new IsoPlayer();
        player.x = 12.75f;
        player.y = 24.25f;
        player.z = 2.5f;
        player.yaw = 0.7f;
        CameraController.yaw = 0;
        CameraController.pitch = 0;

        switch (args[0]) {
            case "geometry" -> {
                require(CameraCapture.capture(frame, player), "Overhead capture replaces backend capture");
                CameraCapture.Snapshot state = CameraCapture.snapshot(frame);
                near(frame.eyeX, -2.75f);
                near(frame.eyeZ, -4.25f);
                near(frame.eyeY, 4.9742344f);
                near(state.directionX(), -1);
                near(state.directionY(), 0);
                near(state.directionZ(), 0);
                near(state.anchorX(), 87.25f);
                near(state.anchorY(), 4.423724f);
                near(state.anchorZ(), 175.75f);
                // Fixed viewport, scale and zoom give this legacy camera distance.
                near(state.offsetX(), -14.501786f);
                require(CaptureRuntime.interiorSawSnapshot, "Publish before interior capture");
            }
            case "disabled", "on-camera" -> {
                CameraCapture.capture(frame, player);
                CaptureRuntime.events.clear();
                int advances = CameraController.advances;
                if (args[0].equals("disabled")) {
                    CameraController.active = false;
                } else {
                    frame.onCamera = true;
                }
                require(!CameraCapture.capture(frame, new Object()), "Normal path does not cast character");
                require(!CameraCapture.snapshot(frame).topDown(), "Discard stale overhead snapshot");
                require(CameraController.advances == advances, "Normal path does not advance rotation");
                require(
                        CaptureRuntime.events.equals(List.of("outline", "weather", "trees-reset", "interior-reset")),
                        "Reset effects on normal path");
            }
            case "frame-reuse" -> {
                CameraCapture.capture(frame, player);
                CameraCapture.Snapshot first = CameraCapture.snapshot(frame);
                player.x += 2;
                CameraController.yaw = (float) (Math.PI / 2);
                CameraCapture.capture(frame, player);
                CameraCapture.Snapshot second = CameraCapture.snapshot(frame);
                require(first != second, "Reused frame receives new immutable snapshot");
                near(first.directionX(), -1);
                near(second.directionZ(), -1);
                near(first.anchorX() - second.anchorX(), 2);
                require(!frame.scene.cameraCut, "Continuous overhead frame does not cut");
                frame.onCamera = true;
                CameraCapture.capture(frame, player);
                frame.onCamera = false;
                CameraCapture.capture(frame, player);
                require(frame.scene.cameraCut, "Returning from normal view cuts");
            }
            case "independent-frames" -> {
                Frame other = new Frame();
                require(!CameraCapture.snapshot(other).topDown(), "Uncaptured frame is normal");
                CameraCapture.capture(frame, player);
                CameraCapture.Snapshot first = CameraCapture.snapshot(frame);
                CameraController.yaw = (float) Math.PI;
                CameraCapture.capture(other, player);
                require(CameraCapture.snapshot(frame) == first, "Other frame does not replace first snapshot");
                near(CameraCapture.snapshot(other).directionX(), 1);
            }
            case "read-only-snapshot" -> {
                CameraCapture.capture(frame, player);
                CameraCapture.Snapshot state = CameraCapture.snapshot(frame);
                int advances = CameraController.advances;
                CameraController.active = false;
                CameraController.yaw = 2;
                for (int i = 0; i < 10; i++) {
                    require(CameraCapture.snapshot(frame) == state, "Reads retain captured state");
                    near(CameraCapture.snapshot(frame).directionX(), -1);
                }
                require(CameraController.advances == advances, "Snapshot reads never advance rotation");
            }
            case "aim-pan" -> {
                CameraCapture.capture(frame, player);
                float[] original = CaptureRuntime.pointerEye;
                AimCamera.shifted = true;
                CameraCapture.capture(frame, player);
                float[] shifted = CaptureRuntime.pointerEye;
                near(shifted[0] - original[0], 4);
                near(shifted[1] - original[1], 2);
                near(shifted[2] - original[2], -3);
                for (int axis = 0; axis < 3; axis++) {
                    near(shifted[axis], CaptureRuntime.interiorEye[axis]);
                }
            }
            default -> throw new AssertionError("Unknown scenario: " + args[0]);
        }
        System.out.println("PASS " + args[0]);
    }

    private static void near(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.0001f) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
