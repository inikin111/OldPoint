package oldpoint.viewpoint;

import viewpoint.input.FreeCam;
import viewpoint.platform.SettingsWindow;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;
import zombie.ui.UIManager;

/** Change cameras only on actual aim edges; keep the last world-space facing. */
final class AimModeCamera {
    private static int onAim,onRelease;
    private static IsoPlayer tracked;
    private static boolean initialized,wasAiming;
    private static float lastAimYaw=Float.NaN;
    private AimModeCamera() { }

    static void configure(double aim,double release) {
        int a=mode(aim),r=mode(release);
        if (a==onAim && r==onRelease) return;
        onAim=a; onRelease=r; reset();
    }
    private static int mode(double value) {
        return Double.isFinite(value)?Math.max(0,Math.min(4,(int)value)):0;
    }
    static void reset() {
        tracked=null; initialized=false; wasAiming=false; lastAimYaw=Float.NaN;
    }
    static void update() {
        IsoPlayer player=IsoPlayer.players[0];
        if (player==null || player.isDead()) { reset(); return; }
        boolean aiming=player.isAiming();
        if (!initialized || tracked!=player) {
            tracked=player; wasAiming=aiming; initialized=true;
            lastAimYaw=aiming?player.getDirectionAngleRadians():Float.NaN;
            return;
        }
        if (GameTime.isGamePaused() || FreeCam.active || SettingsWindow.shown()
                || UIManager.isModalVisible()) {
            wasAiming=aiming;
            if (aiming) lastAimYaw=player.getDirectionAngleRadians();
            return;
        }
        // Viewpoint can briefly clear IsoPlayer.isAiming while switching modes.
        // A held mouse aim must not be interpreted as a release.
        if (wasAiming && !aiming && Mouse.isRightDown()) return;
        if (aiming && !wasAiming) {
            float yaw=player.getDirectionAngleRadians();
            wasAiming=true;
            lastAimYaw=yaw;
            if (onAim!=0 && Bridge.requestAimCameraMode(onAim,yaw)) {
                // The transition was initiated while aiming; keep the stance
                // even if Viewpoint's camera toggle reset it during this frame.
                player.setIsAiming(true);
                System.out.println("[OldPoint] aim camera: aiming, target="+onAim);
            }
        } else if (!aiming && wasAiming) {
            wasAiming=false;
            float yaw=Float.isFinite(lastAimYaw)?lastAimYaw:player.getDirectionAngleRadians();
            if (onRelease!=0 && Bridge.requestAimCameraMode(onRelease,yaw))
                System.out.println("[OldPoint] aim camera: released, target="+onRelease);
            lastAimYaw=Float.NaN;
        } else if (aiming) {
            lastAimYaw=player.getDirectionAngleRadians();
        }
    }
}
