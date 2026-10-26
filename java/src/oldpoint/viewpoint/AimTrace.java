package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;
import zombie.iso.Vector2;

/** Observe shove direction without steering the player or changing attack input. */
public final class AimTrace {
    private static boolean held;
    private static long next;
    private static int remaining;
    private static int events;
    private static long lastEvent;

    private AimTrace() {}

    // Advice is inlined into IsoPlayer, which is outside this package.
    public static void observe(IsoPlayer player) {
        if (!CameraController.topDownActive() || player != IsoPlayer.getInstance()) {
            held = false;
            remaining = 0;
            return;
        }
        boolean active = player.isDoShove();
        long now = System.nanoTime();
        if (active && !held && now - lastEvent >= 1_000_000_000L) {
            remaining = 3;
            next = now;
            events++;
            lastEvent = now;
        }
        held = active;
        if (remaining == 0 || now < next) {
            return;
        }
        next = now + 120_000_000L;
        remaining--;
        Vector2 wanted = new Vector2();
        Pointer.aim(player, wanted);
        float x = player.getForwardDirectionX(), y = player.getForwardDirectionY();
        double delta = Math.toDegrees(Math.atan2(wanted.x * y - wanted.y * x, wanted.x * x + wanted.y * y));
        Vector2 look = player.getLookVector(new Vector2());
        double lookDelta =
                Math.toDegrees(
                        Math.atan2(wanted.x * look.y - wanted.y * look.x, wanted.x * look.x + wanted.y * look.y));
        System.out.println(
                "[OldPoint] shove aim trace: event="
                        + events
                        + ", sample="
                        + (3 - remaining)
                        + ", aiming="
                        + player.isAiming()
                        + ", shove="
                        + active
                        + ", mouse="
                        + Mouse.getXA()
                        + ","
                        + Mouse.getYA()
                        + ", wanted="
                        + wanted.x
                        + ","
                        + wanted.y
                        + ", forward="
                        + x
                        + ","
                        + y
                        + ", deltaDegrees="
                        + delta
                        + ", lookDeltaDegrees="
                        + lookDelta
                        + ", origin="
                        + player.getAimOriginPosX()
                        + ","
                        + player.getAimOriginPosY()
                        + ","
                        + player.getAimOriginPosZ()
                        + Pointer.aimScreenTrace(player));
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "update", strictMatch = false)
    public static final class PlayerUpdatePatch {
        @Patch.OnExit
        public static void exit(@Patch.This IsoPlayer player) {
            observe(player);
        }
    }
}
