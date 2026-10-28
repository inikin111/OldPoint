package oldpoint.viewpoint;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import viewpoint.core.Frame;
import viewpoint.core.View;
import zombie.characters.IsoGameCharacter;
import zombie.core.Core;

/** Captures overhead camera geometry and retains immutable snapshots for reused Viewpoint frames. */
final class CameraCapture {
    private static final Map<Frame, Snapshot> cameraStates = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Offsets use Viewpoint's camera axes: subtract X/Z from the frame pivot, add Up. Direction and weather anchor
     * coordinates are frozen at capture time.
     */
    record Snapshot(
            boolean topDown,
            float offsetX,
            float offsetUp,
            float offsetZ,
            float directionX,
            float directionY,
            float directionZ,
            float anchorX,
            float anchorY,
            float anchorZ) {}

    static final Snapshot NORMAL = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    static boolean capture(Frame frame, Object character) {
        PlayerOutline.reset(frame.scene);
        IndoorWeather.snapshot(frame, character);
        boolean overhead = CameraController.topDownActive() && !frame.onCamera;
        boolean depthCut = ProjectionDepth.capture(frame, overhead);
        if (!overhead) {
            TreeClearing.reset(frame.scene);
            Interior.reset(frame.scene);
            cameraStates.put(frame, NORMAL);

            return false;
        }
        IsoGameCharacter player = (IsoGameCharacter) character;
        float orbitYaw = CameraController.captureYaw();
        frame.viewYaw = orbitYaw;
        frame.viewPitch = CameraController.overheadPitch();
        frame.eyeX = -(player.getX() - frame.camX);
        frame.eyeZ = -(player.getY() - frame.camY);
        float terrainHeight = TerrainCamera.height(player.getX(), player.getY());
        frame.eyeY = 0.8f + (player.getZ() - frame.camZ) * 2.4494896f + terrainHeight;
        frame.eyeLean = 0;
        float distance = Layout.distance(Core.getInstance().getScreenHeight(), Core.getTileScale(), Zoom.factor());
        float dx = (float) (-Math.cos(orbitYaw) * Math.cos(CameraController.overheadPitch()));
        float dy = (float) Math.sin(CameraController.overheadPitch());
        float dz = (float) (-Math.sin(orbitYaw) * Math.cos(CameraController.overheadPitch()));
        if (player instanceof zombie.characters.IsoPlayer local) {
            AimCamera.pan(frame, local, distance, dx, dy, dz);
        }
        // World X/Y point toward the camera along this horizontal direction.
        TreeClearing.capture(
                frame,
                player,
                (float) (-frame.eyeX + dx * distance - frame.scene.originX - player.getX()),
                (float) (-frame.eyeZ + dz * distance - frame.scene.originZ - player.getY()));
        Snapshot state =
                new Snapshot(
                        true,
                        dx * distance,
                        -dy * distance,
                        dz * distance,
                        dx,
                        dy,
                        dz,
                        (float) (-player.getX() - frame.scene.originX),
                        (float) (player.getZ() * 2.4494896 + 0.8 + terrainHeight - frame.scene.originY),
                        (float) (-player.getY() - frame.scene.originZ));
        Snapshot previous = cameraStates.put(frame, state);
        Interior.capture(
                frame,
                player,
                frame.eyeX - state.offsetX,
                frame.eyeY + state.offsetUp,
                frame.eyeZ - state.offsetZ,
                dx,
                dy,
                dz);
        // 0.1.6 stores camera effects in each frame instead of global offsets.
        // Clear the reused shot so shoulder sway, recoil and roll cannot leak in.
        frame.cameraShot.clear();
        frame.cameraShot.on = true;
        frame.cameraShot.seated = player.getVehicle() != null;
        frame.cameraShot.fovY = Layout.FOV;
        frame.cameraShot.handYaw = player.getDirectionAngleRadians();
        frame.cameraShot.handPitch = 0;
        View.cameraX = state.offsetX;
        View.cameraY = state.offsetZ;
        View.cameraUp = state.offsetUp;
        View.frameFovY = Layout.FOV;
        frame.scene.cameraCut = previous == null || !previous.topDown || depthCut;
        frame.scene.cameraFocus = frame.scene.cameraAim = frame.scene.cameraTrauma = 0;
        frame.cameraSquares.on = true;
        frame.cameraSquares.x = (int) Math.floor(player.getX());
        frame.cameraSquares.y = (int) Math.floor(player.getY());
        frame.cameraSquares.level = (int) Math.floor(player.getZ());
        frame.cameraSquares.side = 0;
        Pointer.snapshot(
                frame,
                frame.eyeX - state.offsetX,
                frame.eyeY + state.offsetUp,
                frame.eyeZ - state.offsetZ,
                dx,
                dy,
                dz,
                Core.getInstance().getScreenWidth(),
                Core.getInstance().getScreenHeight(),
                terrainHeight);
        if (player instanceof zombie.characters.IsoPlayer local) {
            VisionSystem.capture(frame, local);
        }
        CameraController.syncCursor();
        return true;
    }

    /** Read the latest published snapshot without advancing camera rotation or touching frame state. */
    static Snapshot snapshot(Frame frame) {
        return cameraStates.getOrDefault(frame, NORMAL);
    }

    private CameraCapture() {}
}
