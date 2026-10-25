package oldpoint.viewpoint;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Exposer;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import viewpoint.core.View;
import viewpoint.input.Look;

import zombie.characters.IsoGameCharacter;
import zombie.core.Core;

import zombie.iso.Vector2;
import zombie.ui.UIManager;

/** A playable, player-following isometric camera inside Viewpoint's renderer. */
@Exposer.LuaClass(name = "OldPointBridge")
public final class Bridge {
    static {
        System.out.println("[OldPoint] loaded build 0.6.151: selected-wall decoration hiding");
    }

    private static Field drawerFrame;
    private static Field drawerEye;
    private static Field drawerDirection;
    private static Field drawerView;
    private static boolean reportedCenterMismatch;
    private static Field roomsView;
    private static Field[] roomViewFields;
    private static final Map<Frame, CameraState> cameraStates = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<CameraState> drawing = new ThreadLocal<>();

    private record CameraState(
            boolean topDown,
            float x,
            float up,
            float y,
            float dx,
            float dy,
            float dz,
            float playerX,
            float playerY,
            float playerZ) {}

    private static final CameraState NORMAL = new CameraState(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private Bridge() {}

    public static void setEnabled(boolean value) {
        CameraController.setEnabled(value);
    }

    public static boolean topDownActive() {
        return CameraController.topDownActive();
    }

    public static void setZoomLimits(double minimum, double maximum) {
        Zoom.limits(minimum, maximum);
    }

    public static void setQolWheelRouting(boolean value) {
        Zoom.qolWheelRouting(value);
    }

    public static void qolMouseWheel(double delta) {
        Zoom.wheel(delta);
    }

    public static void mouseWheel(double delta, boolean allowed) {
        Zoom.wheel(delta, allowed);
    }

    public static void configureWheelModifier(boolean enabled, int key) {
        Zoom.reserveModifier(enabled, key);
    }

    public static void setPlayerOutline(boolean enabled) {
        PlayerOutline.enabled(enabled);
    }

    public static void setTreeClearing(boolean enabled, double radius) {
        TreeClearing.configure(enabled, radius);
    }

    public static void setAutomaticCameraModes(double enter, double leave, double driver, double leaveDriver) {
        CameraController.setAutomaticCameraModes(enter, leave, driver, leaveDriver);
    }

    public static void resetAutomaticCamera() {
        CameraController.resetAutomaticCamera();
    }

    public static void updateAutomaticCamera() {
        AutomaticCamera.update();
    }

    public static void setAimCameraModes(double aiming, double released) {
        AimModeCamera.configure(aiming, released);
    }

    public static void resetAimCamera() {
        AimModeCamera.reset();
    }

    public static void updateAimCamera() {
        AimModeCamera.update();
    }

    /** 0 unchanged, 1 first person, 2 third person, 3 remembered overhead, 4 vanilla. */
    public static boolean requestCameraMode(int mode) {
        return CameraController.requestCameraMode(mode);
    }

    static boolean requestCameraModeFacing(int mode, float yaw) {
        return CameraController.requestCameraModeFacing(mode, yaw);
    }

    static boolean requestAimCameraMode(int mode, float yaw) {
        return CameraController.requestAimCameraMode(mode, yaw);
    }

    public static boolean nativeFixedView() {
        return CameraController.nativeFixedView();
    }

    public static void setRotationKey(double value) {
        CameraController.setRotationKey(value);
    }

    public static void configureStepRotation(
            boolean enabled, double degrees, boolean smooth, double left, double right) {
        CameraController.configureStepRotation(enabled, degrees, smooth, left, right);
    }

    public static void updateStepRotation(boolean inputAllowed) {
        CameraController.updateStepRotation(inputAllowed);
    }

    public static void setVisionRange(boolean enabled, double indicator) {
        VisionSystem.configure(enabled, (int) indicator);
    }

    public static void setMemoryPolicy(double value) {
        ResourceBudget.configure((int) value);
    }

    public static void setAimingSystem(double mode) {
        ImprovedAim.configure((int) mode);
    }

    public static void updateImprovedAim() {
        ResourceBudget.tick();
        Pointer.refreshUI();
        ImprovedAim.update();
    }

    public static void drawImprovedAim() {
        ImprovedAim.draw();
    }

    public static void improvedAimHit(Object target, Object attacker, Object bodyPart, Object weapon) {
        ImprovedAim.hit(target, attacker, bodyPart, weapon);
    }

    public static boolean improvedAimVisible() {
        return CameraController.improvedAimVisible();
    }

    public static float overheadPitch() {
        return CameraController.overheadPitch();
    }

    public static void setNativeFixedView(boolean value) {
        CameraController.setNativeFixedView(value);
    }

    public static boolean keepAimCursor() {
        return CameraController.keepAimCursor();
    }

    public static void pressWorldContext() {
        WorldContext.press();
    }

    public static void releaseWorldContext() {
        WorldContext.refreshPick();
    }

    public static Object beginWorldContext(Object object) {
        return WorldContext.begin((zombie.iso.IsoObject) object);
    }

    public static void endWorldContext() {
        WorldContext.clear();
    }

    public static void expireWorldContext() {
        WorldContext.expire();
    }

    public static Object walkGroundSquare() {
        return WorldContext.groundSquare();
    }

    public static void drawWalkCursor(Object square, boolean valid) {
        WorldContext.draw((zombie.iso.IsoGridSquare) square, valid);
    }

    private static void report(Throwable error) {
        CompatibilityErrors.report(error);
    }

    public static boolean handleViewKey() {
        return CameraController.handleViewKey();
    }

    /** Toggle remembered overhead and return to the entry view on the next press. */
    public static void enterOverheadRemembered() {
        CameraController.enterOverheadRemembered();
    }

    public static void resetOverheadOrientation() {
        CameraController.resetOverheadOrientation();
    }

    public static void setThirdPersonMode(boolean value) {
        CameraController.setThirdPersonMode(value);
    }

    public static void afterToggle() {
        CameraController.afterToggle();
    }

    public static void syncCursor() {
        CameraController.syncCursor();
    }

    public static void finishOrbitInput() {
        CameraController.finishOrbitInput();
    }

    public static boolean nativeMove(Object value) {
        return CameraController.nativeMove(value);
    }

    public static boolean nativeAim(Object value) {
        return Pointer.aimCharacter(value);
    }

    public static boolean captureCamera(Object value, Object character) {
        Frame frame = (Frame) value;
        PlayerOutline.reset(frame.scene);
        IndoorWeather.snapshot(frame, character);
        if (!topDownActive() || frame.onCamera) {
            TreeClearing.reset(frame.scene);
            Interior.reset(frame.scene);
            cameraStates.put(frame, NORMAL);

            return false;
        }
        IsoGameCharacter player = (IsoGameCharacter) character;
        float orbitYaw = CameraController.captureYaw();
        frame.viewYaw = orbitYaw;
        frame.viewPitch = overheadPitch();
        frame.eyeX = -(player.getX() - frame.camX);
        frame.eyeZ = -(player.getY() - frame.camY);
        float terrainHeight = TerrainCamera.height(player.getX(), player.getY());
        frame.eyeY = 0.8f + (player.getZ() - frame.camZ) * 2.4494896f + terrainHeight;
        frame.eyeLean = 0;
        float distance = Layout.distance(Core.getInstance().getScreenHeight(), Core.getTileScale(), Zoom.factor());
        float dx = (float) (-Math.cos(orbitYaw) * Math.cos(overheadPitch()));
        float dy = (float) Math.sin(overheadPitch());
        float dz = (float) (-Math.sin(orbitYaw) * Math.cos(overheadPitch()));
        if (player instanceof zombie.characters.IsoPlayer local) {
            AimCamera.pan(frame, local, distance, dx, dy, dz);
        }
        // World X/Y point toward the camera along this horizontal direction.
        TreeClearing.capture(
                frame,
                player,
                (float) (-frame.eyeX + dx * distance - frame.scene.originX - player.getX()),
                (float) (-frame.eyeZ + dz * distance - frame.scene.originZ - player.getY()));
        CameraState state =
                new CameraState(
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
        CameraState previous = cameraStates.put(frame, state);
        Interior.capture(frame, player, frame.eyeX - state.x, frame.eyeY + state.up, frame.eyeZ - state.y, dx, dy, dz);
        // 0.1.6 stores camera effects in each frame instead of global offsets.
        // Clear the reused shot so shoulder sway, recoil and roll cannot leak in.
        frame.cameraShot.clear();
        frame.cameraShot.on = true;
        frame.cameraShot.seated = player.getVehicle() != null;
        frame.cameraShot.fovY = Layout.FOV;
        frame.cameraShot.handYaw = player.getDirectionAngleRadians();
        frame.cameraShot.handPitch = 0;
        View.cameraX = state.x;
        View.cameraY = state.y;
        View.cameraUp = state.up;
        View.frameFovY = Layout.FOV;
        frame.scene.cameraCut = previous == null || !previous.topDown;
        frame.scene.cameraFocus = frame.scene.cameraAim = frame.scene.cameraTrauma = 0;
        frame.cameraSquares.on = true;
        frame.cameraSquares.x = (int) Math.floor(player.getX());
        frame.cameraSquares.y = (int) Math.floor(player.getY());
        frame.cameraSquares.level = (int) Math.floor(player.getZ());
        frame.cameraSquares.side = 0;
        Pointer.snapshot(
                frame,
                frame.eyeX - state.x,
                frame.eyeY + state.up,
                frame.eyeZ - state.y,
                dx,
                dy,
                dz,
                Core.getInstance().getScreenWidth(),
                Core.getInstance().getScreenHeight(),
                terrainHeight);
        if (player instanceof zombie.characters.IsoPlayer local) {
            VisionSystem.capture(frame, local);
        }
        syncCursor();
        return true;
    }

    public static void beginDrawing(Object drawer) {
        try {
            if (drawerFrame == null) {
                drawerFrame = drawer.getClass().getDeclaredField("frame");
                drawerFrame.setAccessible(true);
            }
            Frame frame = (Frame) drawerFrame.get(drawer);
            drawing.set(cameraStates.getOrDefault(frame, NORMAL));
            Interior.begin(frame.scene);
            TreeClearing.begin(frame.scene);
        } catch (ReflectiveOperationException error) {
            report(error);
            drawing.set(NORMAL);
        }
    }

    public static void endDrawing() {
        try {
            TreeClearing.end();
            Interior.end();
        } finally {
            RoofModels.end();
            drawing.remove();
        }
    }

    public static boolean centerDrawing(Object drawer) {
        CameraState state = drawing.get();
        if (state == null || !state.topDown) {
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

    public static boolean drawTopDown() {
        CameraState state = drawing.get();
        return state != null ? state.topDown : topDownActive();
    }

    public static float weatherActive() {
        CameraState state = drawing.get();
        return state != null && state.topDown ? 1 : 0;
    }

    public static float weatherAnchor(int axis) {
        CameraState state = drawing.get();
        return state == null ? 0 : axis == 0 ? state.playerX : axis == 1 ? state.playerY : state.playerZ;
    }

    public static boolean direction(float[] result) {
        if (!drawTopDown()) {
            return false;
        }
        CameraState state = drawing.get();
        result[0] =
                state == null ? (float) (-Math.cos(CameraController.orbitYaw()) * Math.cos(overheadPitch())) : state.dx;
        result[1] = state == null ? (float) Math.sin(overheadPitch()) : state.dy;
        result[2] =
                state == null ? (float) (-Math.sin(CameraController.orbitYaw()) * Math.cos(overheadPitch())) : state.dz;
        return true;
    }

    public static boolean position(Object value, float[] result) {
        Frame frame = (Frame) value;
        CameraState state = cameraStates.get(frame);
        if (state == null || !state.topDown) {
            return false;
        }
        result[0] = frame.eyeX - state.x;
        result[1] = frame.eyeY + state.up;
        result[2] = frame.eyeZ - state.y;
        return true;
    }

    public static boolean frameTopDown(Object value) {
        CameraState state = cameraStates.get((Frame) value);
        return state != null && state.topDown;
    }

    public static float fieldOfView(float normal) {
        return topDownActive() ? Layout.FOV : normal;
    }

    public static void roomView(Object value) {
        if (!topDownActive()) {
            return;
        }
        Frame frame = (Frame) value;
        try {
            if (roomsView == null) {
                roomsView = Class.forName("viewpoint.visibility.Rooms").getDeclaredField("view");
                roomsView.setAccessible(true);
                Object view = roomsView.get(null);
                String[] names = {"x", "y", "h", "tanX", "tanY"};
                roomViewFields = new Field[names.length];
                for (int i = 0; i < names.length; i++) {
                    roomViewFields[i] = view.getClass().getDeclaredField(names[i]);
                    roomViewFields[i].setAccessible(true);
                }
            }
            Object view = roomsView.get(null);
            // The overhead eye can be tens of storeys above the room graph. That makes
            // Rooms.choose return "eye in no sector" and disables all room culling.
            // Anchor visibility to the player's valid floor, independently of camera zoom.
            roomViewFields[0].setFloat(view, frame.camX);
            roomViewFields[1].setFloat(view, frame.camY);
            roomViewFields[2].setFloat(view, frame.camZ * 2.4494896f + 0.8f);
            float wide = (float) Math.tan(Math.toRadians(85));
            roomViewFields[3].setFloat(view, wide);
            roomViewFields[4].setFloat(view, wide);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "calculateAimVector", strictMatch = false)
    public static final class ProjectedAimPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.This Object player, @Patch.Argument(0) Object vector) {
            return Pointer.aim(player, vector);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Vector2 vector, @Patch.Return(readOnly = false) Vector2 result) {
            if (Bridge.topDownActive()) {
                result = vector;
            }
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "setAngleFromAim", strictMatch = true)
    public static final class MeleeAttackAnglePatch {
        @Patch.OnExit
        public static void exit(@Patch.This zombie.characters.IsoPlayer player) {
            Pointer.alignMeleeAttack(player);
        }
    }

    @Patch(className = "zombie.core.Core", methodName = "doZoomScroll", strictMatch = false)
    public static final class ZoomPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) int player, @Patch.Argument(1) int direction) {
            return Zoom.scroll(player, direction);
        }
    }

    @Patch(className = "zombie.ui.UIManager", methodName = "update", strictMatch = true)
    public static final class WheelInputDispatchPatch {
        @Patch.OnEnter
        public static void enter() {
            Zoom.beginWheelCycle();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            Zoom.endWheelCycle();
        }
    }

    @Patch(className = "zombie.ui.UIElement", methodName = "onConsumeMouseWheel", strictMatch = false)
    public static final class WheelUIConsumptionPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return Boolean consumed) {
            if (Boolean.TRUE.equals(consumed)) {
                Zoom.consumeUIWheel();
            }
        }
    }

    @Patch(className = "viewpoint.interact.LootMenu", methodName = "snapshot", strictMatch = false)
    public static final class HoverOnlyPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object frame) {
            return MouseInteraction.begin((Frame) frame);
        }
    }

    @Patch(className = "viewpoint.interact.MouseTargets", methodName = "object", strictMatch = false)
    public static final class PreciseWallControlPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) zombie.iso.IsoObject result) {
            result = PickTargets.resolve(result);
        }
    }

    // Keep draw() consuming panelDue, but omit the visual panel in overhead mode.
    @Patch(className = "viewpoint.interact.LootPanel", methodName = "render", strictMatch = false)
    public static final class InteractionPanelPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    // A hidden menu must not consume keys or execute its selected action.
    @Patch(className = "viewpoint.interact.LootMenu", methodName = "keyDown", strictMatch = false)
    public static final class InteractionKeyPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) boolean original, @Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = original;
            }
        }
    }

    @Patch(className = "viewpoint.interact.LootMenu", methodName = "mouse", strictMatch = false)
    public static final class InteractionWheelPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.interact.LootMenu", methodName = "freeCursor", strictMatch = false)
    public static final class InteractionCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = false;
            }
        }
    }

    @Patch(className = "viewpoint.interact.LootMenu", methodName = "lookAgain", strictMatch = false)
    public static final class InteractionLookPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = false;
            }
        }
    }

    // Advice is inlined into the target class. Keep callbacks limited to public helpers.
    // ZombieBuddy strictMatch compares the number of mapped @Argument parameters to
    // the target arity. Return-only and sparse-argument callbacks must use false.
    // These target method names are unique in the supported Viewpoint/game versions.
    @Patch(className = "viewpoint.camera.ThirdPerson", methodName = "poll", strictMatch = false)
    public static final class CyclePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.handleViewKey();
        }
    }

    @Patch(className = "viewpoint.FP", methodName = "toggle", strictMatch = false)
    public static final class TogglePatch {
        @Patch.OnExit
        public static void exit() {
            Bridge.afterToggle();
        }
    }

    @Patch(className = "viewpoint.FP", methodName = "cursorMode", strictMatch = false)
    public static final class CursorPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit() {
            Bridge.syncCursor();
        }
    }

    @Patch(className = "viewpoint.input.Look", methodName = "onUpdateMouseCursor", strictMatch = false)
    public static final class MousePatch {
        @Patch.OnEnter
        public static void enter() {
            Bridge.syncCursor();
        }

        @Patch.OnExit
        public static void exit() {
            Bridge.finishOrbitInput();
        }
    }

    @Patch(className = "viewpoint.input.MouseButtons", methodName = "rightHeld", strictMatch = true)
    public static final class NativeRightButtonPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) boolean original) {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) boolean original, @Patch.Return(readOnly = false) boolean result) {
            // Native mouse state already respects UI capture. Released-cursor
            // overhead aiming must not require Viewpoint's captured FPS mouse.
            if (Bridge.topDownActive()) {
                result = original;
            }
        }
    }

    @Patch(className = "viewpoint.input.Look", methodName = "readBeforeDrawing", strictMatch = false)
    public static final class OrbitSnapshotPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.Hooks", methodName = "skipIsoCursor", strictMatch = false)
    public static final class AimCursorDrawPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = !Pointer.showNativeAim();
            }
        }
    }

    @Patch(className = "viewpoint.CombatHooks", methodName = "skipIsoReticle", strictMatch = false)
    public static final class AimReticleDrawPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = !Pointer.showNativeAim();
            }
        }
    }

    @Patch(className = "viewpoint.gunplay.Gunplay", methodName = "aimsFirearm", strictMatch = false)
    public static final class NativeFirearmRulesPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            // 0.1.6's gunplay uses a screen-centred FPS ray. Overhead retains
            // the native skeleton picking and hit chance through BallisticsAim.
            if (Bridge.topDownActive()) {
                result = false;
            }
        }
    }

    @Patch(className = "viewpoint.CombatHooks", methodName = "reticleArm", strictMatch = false)
    public static final class NativeReticleArmPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) float red,
                @Patch.Argument(1) float green,
                @Patch.Argument(2) float blue,
                @Patch.Argument(3) float alpha,
                @Patch.Return(readOnly = false) float[] result) {
            // Viewpoint's SpriteRenderer advice always reads all four entries.
            // Skipping its gunplay color calculation must return the native RGBA,
            // not the null return value produced by skipping a reference method.
            if (Bridge.topDownActive()) {
                result = new float[] {red, green, blue, alpha};
            }
        }
    }

    @Patch(className = "zombie.core.opengl.RenderThread", methodName = "isCursorVisible", strictMatch = false)
    public static final class AimHardwareCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.improvedAimVisible()) {
                result = false;
            } else if (Bridge.keepAimCursor()) {
                result = true;
            }
        }
    }

    @Patch(className = "zombie.input.Mouse", methodName = "isCursorVisible", strictMatch = false)
    public static final class AimSoftwareCursorPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.improvedAimVisible()) {
                result = false;
            } else if (Bridge.keepAimCursor()) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "reticle", strictMatch = false)
    public static final class NativeReticlePositionPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) int original, @Patch.Return(readOnly = false) int result) {
            if (Bridge.topDownActive()) {
                result = original;
            }
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "owns", strictMatch = false)
    public static final class NativeControlsPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) {
                result = false;
            }
        }
    }

    // Stop camera-relative steering at the actual input entry points as well.
    // Native player movement and reticle aiming run before Viewpoint's callbacks.
    @Patch(className = "viewpoint.input.Controls", methodName = "beforeMovement", strictMatch = false)
    public static final class NativeMovementStartPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "afterMovement", strictMatch = false)
    public static final class NativeMovementEndPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "moveVector", strictMatch = false)
    public static final class NativeMoveVectorPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(1) Object vector) {
            return Bridge.nativeMove(vector);
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "rootMotion", strictMatch = false)
    public static final class NativeRootMotionPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "aim", strictMatch = false)
    public static final class NativeAimPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object player) {
            return Bridge.nativeAim(player);
        }
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "lookAngle", strictMatch = false)
    public static final class NativeLookAnglePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) float original, @Patch.Return(readOnly = false) float result) {
            if (Bridge.topDownActive()) {
                result = original;
            }
        }
    }

    @Patch(className = "viewpoint.camera.ThirdPerson", methodName = "capture", strictMatch = false)
    public static final class CapturePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object frame, @Patch.Argument(2) Object player) {
            return Bridge.captureCamera(frame, player);
        }
    }

    @Patch(className = "viewpoint.core.View", methodName = "fovY", strictMatch = false)
    public static final class FovPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float result) {
            result = Bridge.fieldOfView(result);
        }
    }

    @Patch(className = "viewpoint.SceneDrawer", methodName = "drawFrame", strictMatch = false)
    public static final class DrawPatch {
        @Patch.OnEnter
        public static void enter(@Patch.This Object drawer) {
            Bridge.beginDrawing(drawer);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            Bridge.endDrawing();
        }
    }

    @Patch(className = "viewpoint.SceneDrawer", methodName = "lookAlong", strictMatch = true)
    public static final class CenterViewPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.This Object drawer) {
            return Bridge.centerDrawing(drawer);
        }
    }

    @Patch(className = "viewpoint.render.TargetOutline", methodName = "mask", strictMatch = true)
    public static final class TreeOutlineMaskPatch {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) viewpoint.render.FrameContext frame, @Patch.Argument(1) Object models) {
            TreeClearing.beforeOutline(frame);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.Argument(0) viewpoint.render.FrameContext frame) {
            TreeClearing.restoreTargeted(frame);
        }
    }

    @Patch(className = "viewpoint.models.ModelCapture", methodName = "capture", strictMatch = false)
    public static final class PlayerOutlinePatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGameCharacter character) {
            PlayerOutline.begin(frame, character);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGameCharacter character) {
            PlayerOutline.end(frame, character);
        }
    }

    @Patch(className = "viewpoint.render.IrisFrame", methodName = "draw", strictMatch = false)
    public static final class PlayerOutlineIrisDepthPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Object pipeline, @Patch.Argument(1) viewpoint.render.FrameContext frame) {
            PlayerOutline.iris(pipeline, frame);
            VisionIndicator.iris(pipeline, frame);
        }
    }

    @Patch(className = "viewpoint.render.ModelPass", methodName = "endFrame", strictMatch = true)
    public static final class PlayerOutlineCompositePatch {
        @Patch.OnEnter
        public static void enter(@Patch.This Object models) {
            PlayerOutline.draw(models);
            VisionIndicator.draw();
        }
    }

    @Patch(className = "viewpoint.render.TargetOutline", methodName = "world", strictMatch = true)
    public static final class TreeOutlineWorldPatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object renderer, @Patch.Argument(0) viewpoint.render.FrameContext frame) {
            TreeClearing.afterOutlineWorld(renderer, frame);
        }
    }

    @Patch(className = "viewpoint.render.PackDraws$Bucket", methodName = "add", strictMatch = true)
    public static final class NearbyTreeMaskPatch {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) Object model, @Patch.Argument(1) int count, @Patch.Argument(2) int run) {
            TreeClearing.mask(model, run);
            RoofModels.mask(model, run);
        }
    }

    @Patch(className = "viewpoint.render.PackDraws", methodName = "draw", strictMatch = true)
    public static final class RoofModelPassPatch {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) viewpoint.render.SceneData scene,
                @Patch.Argument(1) viewpoint.platform.GlProgram program) {
            RoofModels.begin(scene, program);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            RoofModels.end();
        }
    }

    @Patch(className = "viewpoint.input.Camera", methodName = "direction", strictMatch = false)
    public static final class DirectionPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(2) float[] result) {
            return Bridge.direction(result);
        }
    }

    @Patch(className = "viewpoint.input.Camera", methodName = "eye", strictMatch = false)
    public static final class EyePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object frame, @Patch.Argument(2) float[] result) {
            return Bridge.position(frame, result);
        }
    }

    @Patch(className = "viewpoint.camera.CameraView", methodName = "place", strictMatch = false)
    public static final class CameraPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object frame, @Patch.Argument(3) float[] eye) {
            return Bridge.position(frame, eye);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object frame, @Patch.Return(readOnly = false) boolean result) {
            if (Bridge.frameTopDown(frame)) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.visibility.Rooms", methodName = "select", strictMatch = false)
    public static final class RoomSelectionPatch {
        @Patch.OnExit
        public static void exit() {
            Interior.roomCulling();
        }
    }

    @Patch(className = "viewpoint.iris.IrisAssembler", methodName = "fragment", strictMatch = false)
    public static final class EclipseFarAlbedoPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) viewpoint.iris.IrisTransform.Stage stage,
                @Patch.Argument(2) Object kind,
                @Patch.Return(readOnly = false) String source) {
            source = EclipseAlbedo.restore(stage, kind, source);
            source = EclipseLightmap.restore(stage, kind, source);
            source = NearGeometry.shader(kind, source);
        }
    }

    @Patch(className = "viewpoint.platform.Shaders", methodName = "read", strictMatch = false)
    public static final class InteriorShaderPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) String path) {
            return PlayerOutline.shader(path, null) != null || VisionIndicator.shader(path, null) != null;
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) String path, @Patch.Return(readOnly = false) String source) {
            source = PlayerOutline.shader(path, source);
            source = VisionIndicator.shader(path, source);
            source = OpeningSource.library(path, source);
            source = TreeCards.library(path, source);
            source = Interior.shader(path, source);
            source = EclipseLightmap.library(path, source);
            source = NearGeometry.library(path, source);
            source = OverheadWeather.library(path, source);
        }
    }

    @Patch(className = "viewpoint.platform.GlProgram", methodName = "use", strictMatch = false)
    public static final class InteriorProgramPatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object program) {
            Interior.uniforms(program);
            OverheadWeather.uniforms(program);
        }
    }

    @Patch(className = "viewpoint.render.Meshes", methodName = "bindPage", strictMatch = false)
    public static final class CutawayAtlasIdentityPatch {
        @Patch.OnExit
        public static void exit(@Patch.AllArguments Object[] arguments) {
            OpeningFade.atlasBound(arguments);
        }
    }

    @Patch(className = "viewpoint.render.IrisGeometry", methodName = "begin", strictMatch = false)
    public static final class InteriorIrisPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(1) Object program, @Patch.Argument(6) Object frame, @Patch.Argument(7) boolean shadow) {
            Interior.irisUniforms(program, frame, shadow);
        }
    }

    @Patch(className = "viewpoint.visibility.Rooms", methodName = "hides", strictMatch = false)
    public static final class InteriorUpperModelsPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) float x,
                @Patch.Argument(1) float y,
                @Patch.Argument(2) float x2,
                @Patch.Argument(3) float y2,
                @Patch.Argument(4) int floor,
                @Patch.Return(readOnly = false) boolean result) {
            if (Interior.hideModel(x, y, x2, y2, floor)) {
                result = true;
            }
        }
    }
}
