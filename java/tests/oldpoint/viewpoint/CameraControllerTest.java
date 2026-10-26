package oldpoint.viewpoint;

import viewpoint.FP;
import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.platform.Keys;
import zombie.characters.IsoPlayer;

/** Lifecycle contracts run against the simulated external runtime in the runner. */
public final class CameraControllerTest {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void close(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.0001f, message + ": " + actual + " != " + expected);
    }

    private static void cycle() {
        Keys.THIRD_PERSON.pressed = true;
        require(CameraController.handleViewKey(), "OldPoint must consume the cycle key");
    }

    private static void overhead() {
        CameraController.enterOverheadRemembered();
        require(CameraController.topDownActive(), "Expected overhead mode");
        require(View.thirdPerson, "Overhead must use Viewpoint's third-person base");
    }

    public static void main(String[] args) {
        IsoPlayer player = new IsoPlayer();
        IsoPlayer.players[0] = player;
        View.enabled = true;
        switch (args[0]) {
            case "cycle" -> {
                cycle();
                require(View.thirdPerson && !CameraController.topDownActive(), "First -> third");
                cycle();
                require(CameraController.topDownActive(), "Third -> overhead");
                cycle();
                require(!CameraController.topDownActive() && !View.thirdPerson, "Overhead -> first");
                require(!CameraController.handleViewKey(), "Do not consume inactive first-person polling");
            }
            case "remember-first" -> {
                Look.yaw = 1.2f;
                Look.pitch = 0.3f;
                FP.cursorVisible(true);
                overhead();
                CameraController.enterOverheadRemembered();
                require(View.enabled && !View.thirdPerson && !CameraController.topDownActive(), "Return to first");
                close(Look.yaw, 1.2f, "Restore yaw");
                close(Look.pitch, 0.3f, "Restore pitch");
                require(FP.cursorVisible(), "Restore cursor");
                require(Zoom.remembered == 1 && Zoom.restored == 1, "Remember and restore zoom once");
            }
            case "remember-third" -> {
                View.thirdPerson = true;
                overhead();
                CameraController.enterOverheadRemembered();
                require(View.enabled && View.thirdPerson && !CameraController.topDownActive(), "Return to third");
            }
            case "remember-original" -> {
                View.enabled = false;
                CameraController.enterOverheadRemembered();
                require(
                        FP.cycleAsked && !View.enabled && !CameraController.topDownActive(),
                        "Activation is asynchronous");
                CameraController.afterToggle();
                require(!CameraController.topDownActive(), "Do not enter before confirmation");
                FP.completeActivation();
                CameraController.afterToggle();
                require(CameraController.topDownActive(), "Enter after confirmation");
                CameraController.enterOverheadRemembered();
                require(!View.enabled && !CameraController.topDownActive(), "Return to original");
            }
            case "automatic-entry" -> {
                View.enabled = false;
                require(CameraController.requestCameraMode(2), "Accept third-person request");
                require(!View.enabled && FP.cycleAsked, "Request must not force activation");
                FP.completeActivation();
                CameraController.afterToggle();
                require(View.thirdPerson && !CameraController.topDownActive(), "Apply pending third-person target");
                require(CameraController.requestCameraMode(3), "Accept overhead request");
                require(CameraController.topDownActive(), "Apply overhead target");
            }
            case "automatic-overhead" -> {
                View.enabled = false;
                require(CameraController.requestCameraMode(3), "Queue overhead target");
                CameraController.afterToggle();
                require(!CameraController.topDownActive(), "Wait for activation confirmation");
                FP.completeActivation();
                CameraController.afterToggle();
                require(CameraController.topDownActive(), "Apply pending overhead target");
                CameraController.enterOverheadRemembered();
                require(!View.enabled, "Automatic overhead from original remembers original");
            }
            case "pending-facing" -> {
                View.enabled = false;
                require(CameraController.requestAimCameraMode(2, 1.1f), "Queue aimed third person");
                require(!View.enabled && !CameraController.topDownActive(), "Facing request must wait");
                FP.completeActivation();
                CameraController.afterToggle();
                require(View.thirdPerson, "Apply pending aimed third person");
                close(Look.yaw, 1.1f, "Apply deferred camera facing");
                close(player.facingX, (float) Math.cos(1.1), "Apply deferred character facing");
            }
            case "explicit-exit" -> {
                overhead();
                require(CameraController.requestCameraMode(2), "Explicit third-person exit");
                require(View.thirdPerson && !CameraController.topDownActive(), "Third-person target");
                overhead();
                require(CameraController.requestCameraMode(4), "Explicit original exit");
                require(!View.enabled && !CameraController.topDownActive(), "Original target");
            }
            case "guards" -> {
                require(!CameraController.requestCameraMode(0), "Reject unchanged target");
                require(!CameraController.requestCameraMode(5), "Reject invalid target");
                zombie.GameTime.paused = true;
                CameraController.enterOverheadRemembered();
                require(!CameraController.topDownActive(), "Paused remembered entry blocked");
                require(!CameraController.requestCameraMode(3), "Paused automatic entry blocked");
                zombie.GameTime.paused = false;
                player.dead = true;
                require(!CameraController.requestCameraMode(3), "Dead player blocked");
                player.dead = false;
                zombie.ui.UIManager.modal = true;
                require(!CameraController.requestCameraMode(3), "Modal UI blocked");
            }
            case "free-camera" -> {
                overhead();
                FreeCam.active = true;
                require(!CameraController.topDownActive(), "Free camera suspends overhead");
                require(!CameraController.handleViewKey(), "Free camera retains native input");
                FreeCam.active = false;
                require(CameraController.topDownActive(), "Free camera must not erase overhead latch");
            }
            case "disable" -> {
                overhead();
                CameraController.setEnabled(false);
                require(!CameraController.topDownActive() && View.thirdPerson, "Disable preserves third-person base");
                CameraController.setEnabled(true);
                require(!CameraController.topDownActive(), "Re-enable must not resurrect exited overhead");
            }
            case "toggle-off" -> {
                overhead();
                View.enabled = false;
                CameraController.afterToggle();
                require(
                        !CameraController.topDownActive() && !View.thirdPerson,
                        "External Viewpoint shutdown clears overhead");
                View.enabled = true;
                require(!CameraController.topDownActive(), "Shutdown must clear latch");
            }
            case "cursor-orbit" -> {
                overhead();
                require(FP.cursorVisible(), "Idle overhead exposes cursor");
                org.lwjglx.input.Mouse.held[2] = true;
                CameraController.syncCursor();
                require(Look.wantCapture && !FP.cursorVisible(), "Orbit captures cursor");
                float before = CameraController.orbitYaw();
                OrbitInput.delta = 0.5f;
                CameraController.finishOrbitInput();
                close(CameraController.orbitYaw(), before + 0.5f, "Apply drag delta");
                org.lwjglx.input.Mouse.held[2] = false;
                CameraController.syncCursor();
                require(!Look.wantCapture && FP.cursorVisible(), "Release orbit cursor");
            }
            case "fixed-angle" -> {
                overhead();
                org.lwjglx.input.Mouse.held[2] = true;
                CameraController.syncCursor();
                OrbitInput.delta = 0.5f;
                CameraController.finishOrbitInput();
                float freeYaw = CameraController.orbitYaw();
                CameraController.setNativeFixedView(true);
                close(CameraController.orbitYaw(), Layout.YAW, "Fixed angle");
                require(FP.cursorVisible(), "Fixed angle disallows drag capture");
                org.lwjglx.input.Mouse.held[2] = false;
                CameraController.setNativeFixedView(false);
                close(CameraController.orbitYaw(), freeYaw, "Restore free orbit");
            }
            case "step-rotation" -> {
                overhead();
                CameraController.configureStepRotation(true, 90, false, 20, 21);
                float before = CameraController.orbitYaw();
                zombie.input.GameKeyboard.held.add(21);
                CameraController.updateStepRotation(true);
                close(CameraController.orbitYaw(), before + (float) Math.PI / 2, "One right step");
                CameraController.updateStepRotation(true);
                close(CameraController.orbitYaw(), before + (float) Math.PI / 2, "Held key must not repeat step");
            }
            case "facing" -> {
                require(CameraController.requestCameraModeFacing(2, 1.1f), "Request facing");
                close(Look.yaw, 1.1f, "Camera facing");
                close(player.facingX, (float) Math.cos(1.1), "Character facing X");
                overhead();
                require(CameraController.requestAimCameraMode(1, 0.7f), "Aim switch");
                close(Look.yaw, 0.7f, "Aim camera facing");
                require(!CameraController.topDownActive() && !View.thirdPerson, "Aim target is first person");
            }
            case "cancel-pending" -> {
                View.enabled = false;
                CameraController.enterOverheadRemembered();
                CameraController.setEnabled(false);
                FP.completeActivation();
                CameraController.afterToggle();
                CameraController.setEnabled(true);
                require(!CameraController.topDownActive(), "Disabled pending entry stays cancelled");
            }
            case "seated-mode" -> {
                player.vehicle = new Object();
                CameraController.setThirdPersonMode(true);
                require(viewpoint.camera.CameraSettings.SEATED.value, "Update seated setting");
                require(!viewpoint.camera.CameraSettings.ON_FOOT.value, "Do not change on-foot setting");
                require(viewpoint.input.CrosshairAim.switches == 1, "Reset aim once on mode change");
                CameraController.setThirdPersonMode(true);
                require(viewpoint.input.CrosshairAim.switches == 1, "Do not reset unchanged mode");
                player.vehicle = null;
                CameraController.setThirdPersonMode(true);
                require(viewpoint.camera.CameraSettings.ON_FOOT.value, "Update on-foot setting");
                require(viewpoint.input.CrosshairAim.switches == 1, "Saved-setting update is not a view transition");
            }
            case "repeat-activation" -> {
                View.enabled = false;
                CameraController.enterOverheadRemembered();
                FP.completeActivation();
                CameraController.afterToggle();
                CameraController.enterOverheadRemembered();
                require(!View.enabled, "First return to original");
                FP.cycleAsked = false;
                CameraController.enterOverheadRemembered();
                require(FP.cycleAsked && !View.enabled, "Cached activation still queues a request");
                FP.completeActivation();
                CameraController.afterToggle();
                require(CameraController.topDownActive(), "Re-enter through cached bindings");
                CameraController.enterOverheadRemembered();
                require(!View.enabled, "Cached shutdown still returns to original");
            }
            default -> throw new AssertionError("Unknown scenario " + args[0]);
        }
    }
}
