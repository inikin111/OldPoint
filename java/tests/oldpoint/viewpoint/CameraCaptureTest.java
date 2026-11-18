package oldpoint.viewpoint;

import java.util.List;
import viewpoint.core.Frame;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.iso.IsoCamera;

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
                Core.getInstance().panWhileAiming = true;
                IsoCamera.cameras[0].rightClickX = 100;
                CameraCapture.capture(frame, player);
                float[] shifted = CaptureRuntime.pointerEye;
                float expected = 2 * 14.501786f * (float) Math.tan(Layout.FOV * 0.5) * 100 / 1080;
                near(shifted[0] - original[0], 0);
                near(shifted[1] - original[1], 0);
                near(shifted[2] - original[2], -expected);
                for (int axis = 0; axis < 3; axis++) {
                    near(shifted[axis], CaptureRuntime.interiorEye[axis]);
                }
            }
            case "driving-pan" -> {
                Core.getInstance().panWhileDriving = true;
                player.vehicle = new Object();
                IsoCamera.cameras[0].rightClickX = 192;
                IsoCamera.cameras[0].rightClickY = 96;
                CameraCapture.capture(frame, player);
                near(frame.eyeX, -5.75f);
                near(frame.eyeZ, -4.25f);

                CameraController.yaw = (float) (Math.PI / 2);
                CameraCapture.capture(frame, player);
                near(frame.eyeX, -5.75f);
                near(frame.eyeZ, -4.25f);
                near(CaptureRuntime.pointerEye[0], CaptureRuntime.interiorEye[0]);
                near(CaptureRuntime.pointerEye[2], CaptureRuntime.interiorEye[2]);

                IsoCamera.cameras[0].rightClickX = -64;
                IsoCamera.cameras[0].rightClickY = 32;
                CameraCapture.capture(frame, player);
                near(frame.eyeX, -2.75f);
                near(frame.eyeZ, -5.25f);

                IsoCamera.cameras[0].rightClickX = 0;
                IsoCamera.cameras[0].rightClickY = 0;
                CameraCapture.capture(frame, player);
                near(frame.eyeZ, -4.25f);

                IsoCamera.cameras[0].rightClickX = 192;
                IsoCamera.cameras[0].rightClickY = 96;
                Core.getInstance().panWhileDriving = false;
                CameraCapture.capture(frame, player);
                near(frame.eyeX, -2.75f);

                Core.getInstance().panWhileDriving = true;
                player.vehicle = null;
                CameraCapture.capture(frame, player);
                near(frame.eyeX, -2.75f);
            }
            case "driving-pan-after-capture" -> {
                Core.getInstance().panWhileDriving = true;
                player.vehicle = new Object();
                CameraCapture.capture(frame, player);
                IsoCamera.cameras[0].rightClickX = 530;
                IsoCamera.cameras[0].rightClickY = -257;
                DrivingCameraFrame.FillPatch.exit(frame, player);
                near(frame.eyeX, -2.875f);
                near(frame.eyeZ, 3.90625f);
                near(frame.cameraShot.pivotX, frame.eyeX);
                near(frame.cameraShot.pivotZ, frame.eyeZ);
                near(CaptureRuntime.interiorEye[2], 3.90625f);
                near(CaptureRuntime.pointerEye[2], 3.90625f);
                DrivingCameraFrame.FillPatch.exit(frame, player);
                near(frame.eyeZ, 3.90625f);

                IsoCamera.cameras[0].rightClickX = 0;
                IsoCamera.cameras[0].rightClickY = 0;
                DrivingCameraFrame.FillPatch.exit(frame, player);
                near(frame.eyeX, -2.75f);
                near(frame.eyeZ, -4.25f);
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
