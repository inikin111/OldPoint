package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import viewpoint.platform.SettingsWindow;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.iso.IsoCamera;
import zombie.iso.PlayerCamera;
import zombie.ui.UIManager;

/** Adapts vanilla camera pan to the overhead view. */
public final class AimCamera {
    private AimCamera() {}

    public static boolean hidePointer() {
        var player = IsoPlayer.getInstance();
        return CameraController.topDownActive()
                && player != null
                && player.isAiming()
                && !player.isDead()
                && !GameTime.isGamePaused()
                && !SettingsWindow.shown()
                && !Pointer.overUI();
    }

    static void pan(Frame frame, IsoPlayer player, float distance, float dx, float dy, float dz) {
        Core core = Core.getInstance();
        boolean drivingPan = player.getVehicle() != null && core.getOptionPanCameraWhileDriving();
        if (player.isDead() || (!drivingPan && (!core.getOptionPanCameraWhileAiming()
                || SettingsWindow.shown() || UIManager.isModalVisible() || GameTime.isGamePaused()))) {
            return;
        }
        int index = player.getIndex();
        if (index < 0 || index >= IsoCamera.cameras.length) {
            return;
        }
        var camera = IsoCamera.cameras[index];
        if (camera == null) {
            return;
        }
        if (drivingPan) {
            drive(frame, player, camera);
            return;
        }
        float pixels =
                2
                        * distance
                        * (float) Math.tan(Layout.FOV * 0.5)
                        / Math.max(1, IsoCamera.getScreenHeight(index))
                        / Math.max(0.01f, core.getZoom(index));
        float px = camera.rightClickX * pixels, py = camera.rightClickY * pixels;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        if (!Float.isFinite(px) || !Float.isFinite(py) || horizontal < 0.001f) {
            return;
        }
        // Translate target and eye together, preserving yaw/pitch and all picking rays.
        frame.eyeX += (-dz * px - dx * py / Math.max(0.1f, Math.abs(dy))) / horizontal;
        frame.eyeZ += (dx * px - dz * py / Math.max(0.1f, Math.abs(dy))) / horizontal;
    }

    static void drive(Frame frame, IsoPlayer player, PlayerCamera camera) {
        float screenX = camera.rightClickX;
        float screenY = camera.rightClickY;
        frame.eyeX = frame.camX - player.getX() - (screenX + 2 * screenY) / 128;
        frame.eyeZ = frame.camY - player.getY() - (2 * screenY - screenX) / 128;
    }

    @Patch(className = "viewpoint.input.PadPan", methodName = "updated", strictMatch = false)
    public static final class NativePanPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            // Viewpoint clears the native rightClick offsets here. Keep vanilla's
            // already updated result in overhead; its own option still applies.
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.Hooks", methodName = "skipMouseCursorUpdate", strictMatch = false)
    public static final class NativeCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.keepAimCursor()) {
                result = false;
            }
        }
    }

    @Patch(className = "zombie.input.Mouse", methodName = "isCursorVisible", strictMatch = false)
    public static final class SoftwareCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (AimCamera.hidePointer()) {
                result = false;
            }
        }
    }

    @Patch(className = "zombie.core.opengl.RenderThread", methodName = "isCursorVisible", strictMatch = false)
    public static final class HardwareCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (AimCamera.hidePointer()) {
                result = false;
            }
        }
    }
}
