package oldpoint.viewpoint;

import static oldpoint.viewpoint.CompatibilityErrors.report;

import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.platform.Keys;
import viewpoint.platform.SettingsWindow;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.core.Core;
import zombie.iso.Vector2;
import zombie.ui.UIManager;

/**
 * Owns OldPoint's camera modes, pending activation, return view and orbit input. Viewpoint still owns its actual
 * enabled/third-person state. A cycleView request is consumed only after Viewpoint's toggle callback confirms
 * activation. Capture and drawing must use this module's queries rather than mutate its state.
 */
final class CameraController {
    private static volatile boolean enabled = true;
    private static volatile boolean topDown;
    private static float savedYaw;
    private static float savedPitch;
    private static boolean savedCursor;
    private static volatile float orbitYaw = Layout.YAW;
    private static volatile boolean nativeFixedView;
    private static float freeOrbitYaw = Layout.YAW;
    private static volatile float rememberedOrbitYaw = Layout.YAW;
    private static volatile boolean directEntryRequested;
    private static volatile long directEntryRequestedAt;
    private static int automaticEntryMode;
    private static long automaticEntryAt;
    private static float automaticEntryYaw = Float.NaN;

    private enum EntryMode {
        ORIGINAL,
        FIRST_PERSON,
        THIRD_PERSON
    }

    private static EntryMode directReturnMode;
    private static EntryMode overheadOrigin = EntryMode.THIRD_PERSON;
    private static boolean directReturnThirdPerson;
    private static boolean rotating;
    private static volatile int rotationKey = zombie.input.Mouse.MMB;
    private static volatile boolean stepRotation, smoothStepRotation = true;
    private static volatile int stepDegrees = 90, leftRotationKey, rightRotationKey;
    private static boolean leftRotationHeld, rightRotationHeld;

    private CameraController() {}

    static void setEnabled(boolean value) {
        if (!value) {
            directEntryRequested = false;
            directReturnMode = null;
        }
        if (!value && topDown) {
            leaveTopDown();
        }
        enabled = value;
    }

    static boolean topDownActive() {
        return enabled && topDown && View.enabled && !FreeCam.active;
    }

    static void setAutomaticCameraModes(double enter, double leave, double driver, double leaveDriver) {
        automaticEntryMode = 0;
        AutomaticCamera.configure(enter, leave, driver, leaveDriver);
    }

    static void resetAutomaticCamera() {
        AutomaticCamera.reset();
        automaticEntryMode = 0;
        automaticEntryYaw = Float.NaN;
    }

    static boolean requestCameraMode(int mode) {
        var player = zombie.characters.IsoPlayer.players[0];
        if (mode < 1
                || mode > 4
                || player == null
                || player.isDead()
                || FreeCam.active
                || mode == 3 && !enabled
                || UIManager.isModalVisible()
                || SettingsWindow.shown()
                || GameTime.isGamePaused()) {
            return false;
        }
        automaticEntryMode = 0;
        automaticEntryYaw = Float.NaN;
        if (mode == 3 && topDownActive()) {
            return true;
        }
        try {
            if (mode == 4) {
                directEntryRequested = false;
                if (topDown) {
                    float yaw = rotating ? Look.yaw : orbitYaw;
                    if (!nativeFixedView && Float.isFinite(yaw)) {
                        rememberedOrbitYaw = yaw;
                    }
                    leaveTopDown();
                }
                if (View.enabled) {
                    ViewpointCameraAdapter.disable(player);
                }
                return true;
            }
            if (!View.enabled) {
                directEntryRequested = false;
                automaticEntryMode = mode;
                automaticEntryAt = System.nanoTime();
                // Preserve Viewpoint's normal permission, onboarding and activation checks.
                ViewpointCameraAdapter.requestActivation();
                return true;
            }
            if (mode == 3) {
                directReturnMode = currentMode();
                directReturnThirdPerson = View.thirdPerson;
                enterOverhead(true);
            } else {
                directEntryRequested = false;
                if (topDown) {
                    float yaw = rotating ? Look.yaw : orbitYaw;
                    if (!nativeFixedView && Float.isFinite(yaw)) {
                        rememberedOrbitYaw = yaw;
                    }
                    leaveTopDown();
                }
                setThirdPersonMode(mode == 2);
            }
            return true;
        } catch (ReflectiveOperationException error) {
            automaticEntryMode = 0;
            report(error);
            return false;
        }
    }

    static boolean requestCameraModeFacing(int mode, float yaw) {
        if (mode != 1 && mode != 2) {
            return false;
        }
        if (!requestCameraMode(mode)) {
            return false;
        }
        if (!Float.isFinite(yaw)) {
            return true;
        }
        if (View.enabled && View.thirdPerson == (mode == 2) && !topDownActive()) {
            faceEntryCamera(yaw);
        } else {
            automaticEntryYaw = yaw;
        }
        return true;
    }

    static boolean requestAimCameraMode(int mode, float yaw) {
        if (mode < 1 || mode > 4 || !requestCameraMode(mode)) {
            return false;
        }
        if (!Float.isFinite(yaw)) {
            return true;
        }
        if (mode == 1 || mode == 2) {
            if (View.enabled && !topDownActive()) {
                faceEntryCamera(yaw);
            } else {
                automaticEntryYaw = yaw;
            }
        } else {
            faceCharacter(yaw);
            if (mode == 3 && !View.enabled) {
                automaticEntryYaw = yaw;
            }
        }
        return true;
    }

    private static void faceCharacter(float yaw) {
        var player = zombie.characters.IsoPlayer.players[0];
        if (player != null && player.getVehicle() == null) {
            player.setTargetAndCurrentDirection((float) Math.cos(yaw), (float) Math.sin(yaw));
        }
    }

    private static void faceEntryCamera(float yaw) {
        yaw = Look.wrap(yaw);
        Look.yaw = yaw;
        Look.pitch = 0;
        Look.hold(yaw, 0);
        Look.turnTo(Float.NaN);
        Look.behind(Float.NaN, Float.NaN, false);
        faceCharacter(yaw);
        automaticEntryYaw = Float.NaN;
    }

    static boolean nativeFixedView() {
        return nativeFixedView;
    }

    static void setRotationKey(double value) {
        int key = Double.isFinite(value) ? (int) value : zombie.input.Mouse.MMB;
        rotationKey =
                key >= 0 && key < 512 || key >= zombie.input.Mouse.BTN_OFFSET && key < zombie.input.Mouse.BTN_OFFSET + 8
                        ? key
                        : zombie.input.Mouse.MMB;
    }

    private static boolean rotationHeld() {
        return keyHeld(rotationKey);
    }

    private static boolean keyHeld(int key) {
        if (key >= zombie.input.Mouse.BTN_OFFSET) {
            int button = key - zombie.input.Mouse.BTN_OFFSET;
            return button < org.lwjglx.input.Mouse.getButtonCount() && org.lwjglx.input.Mouse.isButtonDown(button);
        }
        return key > 0 && zombie.input.GameKeyboard.isKeyDown(key);
    }

    static void configureStepRotation(boolean enabled, double degrees, boolean smooth, double left, double right) {
        stepRotation = enabled;
        stepDegrees = degrees == 45 ? 45 : degrees == 180 ? 180 : 90;
        smoothStepRotation = smooth;
        leftRotationKey = validStepKey(left);
        rightRotationKey = validStepKey(right);
    }

    private static int validStepKey(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        int key = (int) value;
        return key > 0 && (key < 512 || key >= zombie.input.Mouse.BTN_OFFSET && key < zombie.input.Mouse.BTN_OFFSET + 8)
                ? key
                : 0;
    }

    static void updateStepRotation(boolean inputAllowed) {
        boolean left = keyHeld(leftRotationKey), right = keyHeld(rightRotationKey);
        boolean pressLeft = left && !leftRotationHeld, pressRight = right && !rightRotationHeld;
        leftRotationHeld = left;
        rightRotationHeld = right;
        if (!inputAllowed
                || !stepRotation
                || !topDownActive()
                || nativeFixedView
                || GameTime.isGamePaused()
                || Core.getInstance().isDoingTextEntry()
                || UIManager.isModalVisible()
                || SettingsWindow.shown()
                || pressLeft == pressRight) {
            return;
        }
        float amount = (float) Math.toRadians(stepDegrees) * (pressLeft ? -1 : 1);
        if (rotating && Float.isFinite(Look.yaw)) {
            orbitYaw = Look.yaw;
        }
        rotating = false;
        OrbitInput.reset();
        if (smoothStepRotation) {
            ResetMotion.step(orbitYaw, amount);
        } else {
            orbitYaw = ResetMotion.targetOr(orbitYaw) + amount;
            ResetMotion.cancel();
            rememberedOrbitYaw = orbitYaw;
            freeOrbitYaw = orbitYaw;
        }
        Look.yaw = orbitYaw;
    }

    static boolean improvedAimVisible() {
        return !rotating && ImprovedAim.showing();
    }

    static float overheadPitch() {
        return nativeFixedView ? Layout.NATIVE_PITCH : Layout.PITCH;
    }

    static void setNativeFixedView(boolean value) {
        if (value == nativeFixedView) {
            return;
        }
        ResetMotion.cancel();
        if (value) {
            freeOrbitYaw = rotating ? Look.yaw : orbitYaw;
        }
        nativeFixedView = value;
        orbitYaw = value ? Layout.YAW : freeOrbitYaw;
        rotating = false;
        Interior.clear();
        if (topDownActive()) {
            syncCursor();
        }
    }

    static boolean keepAimCursor() {
        return topDownActive() && !rotating && !ImprovedAim.showing();
    }

    private static void leaveTopDown() {
        if (topDown) {
            Zoom.remember();
            float yaw = rotating ? Look.yaw : orbitYaw;
            if (!nativeFixedView && Float.isFinite(yaw)) {
                rememberedOrbitYaw = yaw;
            }
        }
        ResetMotion.cancel();
        directReturnMode = null;
        OrbitInput.reset();
        TreeClearing.clear();
        VisionFade.clear();
        VisionSystem.clearSight();
        topDown = false;
        rotating = false;
        Look.yaw = savedYaw;
        Look.pitch = savedPitch;
        try {
            ViewpointCameraAdapter.setCursorVisible(savedCursor);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
        Interior.clear();

        Pointer.clear();
    }

    static boolean handleViewKey() {
        if (!enabled || FreeCam.active) {
            return false;
        }
        if (!Keys.THIRD_PERSON.pressed()) {
            return topDownActive();
        }
        automaticEntryMode = 0;
        automaticEntryYaw = Float.NaN;
        if (topDown) {
            leaveTopDown();
            setThirdPersonMode(false);
            System.out.println("[OldPoint] camera: first person");
        } else if (View.thirdPerson) {
            enterOverhead(true);
        } else {
            setThirdPersonMode(true);
            System.out.println("[OldPoint] camera: third person");
        }
        // pressed() calls eatKeyPress, but another pressed() in this same frame
        // can still see the event. Skip poll on EVERY handled transition so
        // Viewpoint cannot toggle the selected setting a second time.
        return true;
    }

    private static void enterOverhead(boolean restore) {
        if (!topDown) {
            try {
                overheadOrigin = currentMode();
                ShootToThrillCompat.enteringOverhead();
                savedCursor = ViewpointCameraAdapter.cursorVisible();
                savedYaw = Look.yaw;
                savedPitch = Look.pitch;
                setThirdPersonMode(true);
                topDown = true;
                if (restore) {
                    Zoom.restore();
                } else {
                    Zoom.reset();
                }
                Pointer.closeInteraction();
            } catch (ReflectiveOperationException error) {
                report(error);
                return;
            }
        }
        ResetMotion.cancel();
        OrbitInput.reset();
        orbitYaw = restore && !nativeFixedView ? rememberedOrbitYaw : Layout.YAW;
        rotating = false;
        Look.yaw = orbitYaw;
        Look.pitch = Layout.NATIVE_PITCH;
        Interior.clear();
        syncCursor();
        System.out.println(
                "[OldPoint] overhead entry: "
                        + (restore ? "restore user orbit" : "native orientation")
                        + ", yaw="
                        + orbitYaw
                        + ", pitch="
                        + Layout.NATIVE_PITCH);
    }

    static void enterOverheadRemembered() {
        var player = zombie.characters.IsoPlayer.players[0];
        if (!enabled
                || FreeCam.active
                || player == null
                || player.isDead()
                || UIManager.isModalVisible()
                || SettingsWindow.shown()
                || GameTime.isGamePaused()) {
            return;
        }
        automaticEntryMode = 0;
        automaticEntryYaw = Float.NaN;
        if (topDownActive()) {
            returnFromOverhead(player);
            return;
        }
        directReturnMode = currentMode();
        directReturnThirdPerson = View.thirdPerson;
        if (View.enabled) {
            enterOverhead(true);
            return;
        }
        try {
            directEntryRequested = true;
            directEntryRequestedAt = System.nanoTime();
            // Ask Viewpoint to enter through its normal onboarding/build/split-screen checks.
            ViewpointCameraAdapter.requestActivation();
        } catch (ReflectiveOperationException error) {
            directEntryRequested = false;
            directReturnMode = null;
            report(error);
        }
    }

    private static EntryMode currentMode() {
        return !View.enabled ? EntryMode.ORIGINAL : View.thirdPerson ? EntryMode.THIRD_PERSON : EntryMode.FIRST_PERSON;
    }

    private static void returnFromOverhead(IsoGameCharacter player) {
        float exitYaw = rotating ? Look.yaw : orbitYaw;
        if (!nativeFixedView && Float.isFinite(exitYaw)) {
            rememberedOrbitYaw = exitYaw;
        }
        EntryMode target = directReturnMode != null ? directReturnMode : overheadOrigin;
        boolean originalThird = directReturnThirdPerson;
        if (target == EntryMode.ORIGINAL) {
            try {
                ViewpointCameraAdapter.disable(player);
            } catch (ReflectiveOperationException error) {
                report(error);
                return;
            }
        }
        leaveTopDown();
        setThirdPersonMode(target == EntryMode.THIRD_PERSON || target == EntryMode.ORIGINAL && originalThird);
        System.out.println("[OldPoint] direct overhead return: " + target);
    }

    static void resetOverheadOrientation() {
        if (!topDownActive() || UIManager.isModalVisible() || SettingsWindow.shown() || GameTime.isGamePaused()) {
            return;
        }
        rememberedOrbitYaw = Layout.YAW;
        freeOrbitYaw = Layout.YAW;
        if (!nativeFixedView) {
            if (rotating && Float.isFinite(Look.yaw)) {
                orbitYaw = Look.yaw;
            }
            rotating = false;
            OrbitInput.reset();
            ResetMotion.start(orbitYaw);
            Interior.clear();
        } else {
            enterOverhead(false);
        }
    }

    static void setThirdPersonMode(boolean value) {
        try {
            ViewpointCameraAdapter.setThirdPerson(value);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    static void afterToggle() {
        if (automaticEntryMode != 0) {
            if (System.nanoTime() - automaticEntryAt > 10_000_000_000L) {
                automaticEntryMode = 0;
                automaticEntryYaw = Float.NaN;
            } else if (View.enabled) {
                int mode = automaticEntryMode;
                automaticEntryMode = 0;
                if (mode == 3) {
                    directReturnMode = EntryMode.ORIGINAL;
                    directReturnThirdPerson = false;
                    enterOverhead(true);
                    if (Float.isFinite(automaticEntryYaw)) {
                        faceCharacter(automaticEntryYaw);
                        automaticEntryYaw = Float.NaN;
                    }
                } else {
                    setThirdPersonMode(mode == 2);
                    if ((mode == 1 || mode == 2) && Float.isFinite(automaticEntryYaw)) {
                        faceEntryCamera(automaticEntryYaw);
                    }
                }
            }
        }
        if (directEntryRequested && System.nanoTime() - directEntryRequestedAt > 10_000_000_000L) {
            directEntryRequested = false;
            directReturnMode = null;
        }
        if (directEntryRequested && View.enabled) {
            directEntryRequested = false;
            enterOverhead(true);
        }
        if (topDown && !View.enabled) {
            leaveTopDown();
            setThirdPersonMode(false);
            View.thirdPerson = false;
        }
    }

    static void syncCursor() {
        if (!topDownActive()) {
            return;
        }
        boolean held =
                !nativeFixedView
                        && !stepRotation
                        && rotationHeld()
                        && !UIManager.isModalVisible()
                        && !GameTime.isGamePaused()
                        && !SettingsWindow.shown();
        if (held && ResetMotion.active()) {
            ResetMotion.cancel();
            OrbitInput.reset();
        }
        if (nativeFixedView) {
            orbitYaw = Layout.YAW;
        } else if (rotating) {
            orbitYaw = Look.yaw;
            if (Float.isFinite(orbitYaw)) {
                rememberedOrbitYaw = orbitYaw;
            }
        }
        rotating = held;
        Look.yaw = orbitYaw;
        Look.pitch = overheadPitch();
        Look.wantCapture = held;
        try {
            ViewpointCameraAdapter.setCursorVisible(!held);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    static void finishOrbitInput() {
        orbitYaw = OrbitInput.read(orbitYaw, topDownActive() && rotating && !nativeFixedView);
        if (!topDownActive()) {
            return;
        }
        if (rotating && !nativeFixedView && Float.isFinite(orbitYaw)) {
            rememberedOrbitYaw = orbitYaw;
        }
        Look.yaw = orbitYaw;
        Look.pitch = overheadPitch();
    }

    static void prepareOrbitInput() {
        OrbitInput.beforeUpdate(topDownActive() && rotating && !nativeFixedView);
    }

    static boolean nativeMove(Object value) {
        if (!topDownActive()) {
            return false;
        }
        Vector2 vector = (Vector2) value;
        vector.rotate(orbitYaw - Layout.YAW);
        return true;
    }

    /** Commit orbit animation on capture, never on the render thread. */
    static float captureYaw() {
        if (ResetMotion.active()) {
            orbitYaw = ResetMotion.advance(GameTime.isGamePaused());
        }
        if (nativeFixedView) {
            orbitYaw = Layout.YAW;
        } else if (rotating) {
            orbitYaw = Look.yaw;
            if (Float.isFinite(orbitYaw)) {
                rememberedOrbitYaw = orbitYaw;
            }
        }
        Look.yaw = orbitYaw;
        Look.pitch = overheadPitch();
        return orbitYaw;
    }

    static float orbitYaw() {
        return orbitYaw;
    }
}
