package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.iso.IsoCamera;

/** Reconciles the overhead pivot with native vehicle pan before Viewpoint submits the frame. */
public final class DrivingCameraFrame {
    private DrivingCameraFrame() {}

    public static void finish(Object value, Object character) {
        if (!(character instanceof IsoPlayer player) || player.getVehicle() == null || player.isDead()) {
            return;
        }
        Core core = Core.getInstance();
        Frame frame = (Frame) value;
        CameraCapture.Snapshot snapshot = CameraCapture.snapshot(frame);
        if (!snapshot.topDown() || !core.getOptionPanCameraWhileDriving()) {
            return;
        }
        int index = player.getIndex();
        if (index < 0 || index >= IsoCamera.cameras.length || IsoCamera.cameras[index] == null) {
            return;
        }
        float previousX = frame.eyeX;
        float previousZ = frame.eyeZ;
        AimCamera.drive(frame, player, IsoCamera.cameras[index]);
        frame.cameraShot.pivotX = frame.eyeX;
        frame.cameraShot.pivotZ = frame.eyeZ;
        if (frame.eyeX == previousX && frame.eyeZ == previousZ) {
            return;
        }
        float cameraX = frame.eyeX - snapshot.offsetX();
        float cameraY = frame.eyeY + snapshot.offsetUp();
        float cameraZ = frame.eyeZ - snapshot.offsetZ();
        TreeClearing.capture(frame, player,
                (float) (-cameraX - frame.scene.originX - player.getX()),
                (float) (-cameraZ - frame.scene.originZ - player.getY()));
        Interior.capture(frame, player, cameraX, cameraY, cameraZ,
                snapshot.directionX(), snapshot.directionY(), snapshot.directionZ());
        Pointer.snapshot(frame, cameraX, cameraY, cameraZ,
                snapshot.directionX(), snapshot.directionY(), snapshot.directionZ(),
                core.getScreenWidth(), core.getScreenHeight(), TerrainCamera.height(player.getX(), player.getY()));
    }

    @Patch(className = "viewpoint.FrameFill", methodName = "fill", strictMatch = false)
    public static final class FillPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object frame, @Patch.Argument(4) Object player) {
            DrivingCameraFrame.finish(frame, player);
        }
    }
}
