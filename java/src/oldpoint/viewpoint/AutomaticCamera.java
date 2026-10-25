package oldpoint.viewpoint;

import viewpoint.input.FreeCam;
import viewpoint.core.View;
import viewpoint.platform.SettingsWindow;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.ui.UIManager;

/** Transition-triggered camera choices; never enforce a mode continuously. */
final class AutomaticCamera {
    private static int enter, leave, driver, leaveDriver;
    private static IsoPlayer tracked;
    private static boolean initialized, inside, candidateInside;
    private static long candidateSince;
    private static Object previousDriverVehicle;
    private static float outsideX, outsideY, entryYaw;
    private static final org.joml.Vector3f vehicleForward = new org.joml.Vector3f();

    private AutomaticCamera() {}

    static void configure(double enterValue, double leaveValue, double driverValue, double leaveDriverValue) {
        int e = mode(enterValue), l = mode(leaveValue), d = mode(driverValue), ld = mode(leaveDriverValue);
        if (e == enter && l == leave && d == driver && ld == leaveDriver) {
            return;
        }
        enter = e;
        leave = l;
        driver = d;
        leaveDriver = ld;
        reset();
    }

    private static int mode(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(4, (int) value)) : 0;
    }

    static void reset() {
        tracked = null;
        initialized = false;
        candidateSince = 0;
        previousDriverVehicle = null;
    }

    static void update() {
        if ((enter | leave | driver | leaveDriver) == 0 && !View.enabled) {
            return;
        }
        IsoPlayer player = IsoPlayer.players[0];
        if (player == null || player.isDead()) {
            reset();
            return;
        }
        if (player.getCurrentSquare() == null
                || GameTime.isGamePaused()
                || FreeCam.active
                || SettingsWindow.shown()
                || UIManager.isModalVisible()) {
            candidateSince = 0;
            return;
        }
        boolean nowInside = player.getCurrentSquare().getBuilding() != null;
        var vehicle = player.getVehicle();
        Object driverVehicle = vehicle != null && vehicle.getDriver() == player ? vehicle : null;
        long now = System.nanoTime();
        if (!initialized || tracked != player) {
            tracked = player;
            inside = candidateInside = nowInside;
            candidateSince = now;
            outsideX = player.getX();
            outsideY = player.getY();
            previousDriverVehicle = driverVehicle;
            initialized = true;
            return;
        }
        int target = 0;
        String reason = null;
        boolean faceInside = false;
        float facingYaw = entryYaw;
        if (!nowInside) {
            outsideX = player.getX();
            outsideY = player.getY();
        }
        if (nowInside != candidateInside || candidateSince == 0) {
            if (nowInside && !inside) {
                float dx = player.getX() - outsideX, dy = player.getY() - outsideY;
                entryYaw =
                        dx * dx + dy * dy > 0.000001f ? (float) Math.atan2(dy, dx) : player.getDirectionAngleRadians();
            }
            candidateInside = nowInside;
            candidateSince = now;
        }
        if (candidateInside != inside && now - candidateSince >= 300_000_000L) {
            inside = candidateInside;
            target = inside ? enter : leave;
            reason = inside ? "entered building" : "left building";
            faceInside = inside && (target == 1 || target == 2);
            facingYaw = entryYaw;
        }
        // Driver seat only: passenger entry is ignored. Moving from a passenger
        // seat into the driver's seat counts as becoming the driver.
        if (driverVehicle != previousDriverVehicle) {
            boolean wasDriver = previousDriverVehicle != null;
            previousDriverVehicle = driverVehicle;
            if (driverVehicle != null) {
                target = driver;
                if (target == 0 && View.enabled && !Bridge.topDownActive()) {
                    target = View.thirdPerson ? 2 : 1;
                }
                reason = "entered driver seat";
                faceInside = target == 1 || target == 2;
                if (faceInside) {
                    vehicle.getForwardVector(vehicleForward);
                    facingYaw =
                            vehicleForward.isFinite()
                                            && vehicleForward.x * vehicleForward.x + vehicleForward.z * vehicleForward.z
                                                    > 0.000001f
                                    ? (float) Math.atan2(vehicleForward.z, vehicleForward.x)
                                    : Float.NaN;
                }
            } else if (wasDriver && driverVehicle == null && leaveDriver != 0) {
                target = leaveDriver;
                reason = "left driver seat";
                faceInside = false;
            }
        }
        if (target != 0
                && (faceInside
                        ? Bridge.requestCameraModeFacing(target, facingYaw)
                        : Bridge.requestCameraMode(target))) {
            System.out.println("[OldPoint] automatic camera (experimental): " + reason + ", target=" + target);
        }
    }
}
