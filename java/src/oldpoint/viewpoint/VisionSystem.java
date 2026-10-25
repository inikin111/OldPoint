package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.Vector2;
import zombie.iso.IsoCamera;
import viewpoint.core.Frame;
import viewpoint.render.SceneData;

/** Native visibility decisions, with a small angular guide rather than a darkness mask. */
public final class VisionSystem {
    private static volatile boolean enabled = true;
    private static volatile int indicator = 1;
    private static volatile float nativeCone = -0.2f;
    private static volatile Visual visual;

    record Visual(float x, float y, float z, float heading, float degrees, boolean aiming) {}

    private static final java.util.Map<SceneData, Visual> snapshots =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Vector2 look = new Vector2();
    private static final ThreadLocal<Boolean> nativeQuery = ThreadLocal.withInitial(() -> false);
    private static final java.util.Map<IsoZombie, Sight> sightCache = new java.util.WeakHashMap<>();

    private static final class Sight {
        int frame, index;
        float px, py, pz, zx, zy, zz, hx, hy;
        long at;
        boolean visible;
    }

    public static synchronized void clearSight() {
        sightCache.clear();
    }

    private VisionSystem() {}

    public static void configure(boolean value, int mode) {
        enabled = value;
        indicator = Math.max(0, Math.min(2, mode));
        if (!value) {
            visual = null;
            snapshots.clear();
            VisionFade.clear();
            clearSight();
        }
    }

    public static boolean active() {
        return enabled && Bridge.topDownActive();
    }

    // Advice is inlined into Viewpoint; cross-package calls require public entries.
    public static float coneResult(IsoGameCharacter character, float original, float current) {
        if (active() && character == IsoPlayer.getInstance()) {
            nativeCone = original;
            return original;
        }
        return current;
    }

    public static synchronized boolean visible(IsoZombie zombie, IsoPlayer player) {
        int index = player.getIndex();
        if (index < 0 || index >= 4) {
            return false;
        }
        int frame = IsoCamera.frameState.frameCount;
        long now = System.nanoTime();
        Sight s = sightCache.get(zombie);
        if (s != null
                && s.frame == frame
                && s.index == index
                && now - s.at < 16_000_000L
                && s.px == player.getX()
                && s.py == player.getY()
                && s.pz == player.getZ()
                && s.zx == zombie.getX()
                && s.zy == zombie.getY()
                && s.zz == zombie.getZ()
                && s.hx == player.getForwardDirectionX()
                && s.hy == player.getForwardDirectionY()) {
            return s.visible;
        }
        if (s == null) {
            s = new Sight();
            sightCache.put(zombie, s);
        }
        var square = zombie.getCurrentSquare();
        // Native canSee includes cone, obstacles, lighting and close awareness.
        boolean result = square != null && square.isCanSee(index);
        if (!result) {
            nativeQuery.set(true);
            try {
                result = zombie.canSeeHeadSquare(player);
            } finally {
                nativeQuery.set(false);
            }
        }
        s.frame = frame;
        s.index = index;
        s.at = now;
        s.px = player.getX();
        s.py = player.getY();
        s.pz = player.getZ();
        s.zx = zombie.getX();
        s.zy = zombie.getY();
        s.zz = zombie.getZ();
        s.hx = player.getForwardDirectionX();
        s.hy = player.getForwardDirectionY();
        s.visible = result;
        return result;
    }

    public static boolean fadingHead(IsoZombie zombie, boolean original) {
        // A retained model must never feed back into the native sight target,
        // otherwise fade-out would make itself visible indefinitely.
        return original || !nativeQuery.get() && VisionFade.retained(zombie);
    }

    public static void capture(Frame frame, IsoPlayer player) {
        if (!active() || player.isDead()) {
            visual = null;
            snapshots.remove(frame.scene);
            return;
        }
        player.getLookVector(look);
        float degrees = Math.max(18, Math.min(360, 180 * (1 + nativeCone)));
        visual =
                new Visual(
                        player.getX(),
                        player.getY(),
                        player.getZ() + 0.015f,
                        (float) Math.atan2(look.y, look.x),
                        degrees,
                        player.isAiming());
        snapshots.put(frame.scene, visual);
    }

    static Visual indicatorState(SceneData scene) {
        if (!active() || indicator == 2) {
            return null;
        }
        Visual v = snapshots.get(scene);
        return v != null && (indicator == 0 || v.aiming) ? v : null;
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "visionCone", strictMatch = true)
    public static final class NativeConePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) IsoGameCharacter character) {
            return active() && character == IsoPlayer.getInstance();
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) IsoGameCharacter character,
                @Patch.Argument(1) float original,
                @Patch.Return(readOnly = false) float result) {
            result = coneResult(character, original, result);
        }
    }

    @Patch(className = "viewpoint.models.ModelCull", methodName = "headSquareSeen", strictMatch = true)
    public static final class NativeHeadPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(1) IsoPlayer player) {
            return active() && player == IsoPlayer.getInstance();
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) IsoZombie zombie,
                @Patch.Argument(1) IsoPlayer player,
                @Patch.Argument(2) boolean original,
                @Patch.Return(readOnly = false) boolean result) {
            if (active() && player == IsoPlayer.getInstance()) {
                result = VisionSystem.fadingHead(zombie, original);
            }
        }
    }
}
