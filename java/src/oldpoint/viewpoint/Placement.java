package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.building.BuildAim;
import viewpoint.render.MousePick;
import zombie.characters.IsoGameCharacter;
import zombie.core.Core;
import zombie.input.Mouse;

/** Supplies the overhead mouse ray to Viewpoint's existing placement pipeline. */
public final class Placement {
    private static java.lang.reflect.Method previewHit;
    private static double lastSurface = Double.NaN;
    private static int lastFloor = Integer.MIN_VALUE;
    private Placement() {}

    public static boolean active() {
        return CameraController.topDownActive();
    }

    public static boolean placementLine(double[] out, boolean original) {
        var player = zombie.characters.IsoPlayer.getInstance();
        var cell = zombie.iso.IsoWorld.instance.getCell();
        if (!active() || player == null || cell == null || cell.getDrag(player.getPlayerNum()) == null) {
            return original;
        }
        float[] ray = new float[6];
        if (!Pointer.interactionRay(ray)) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            out[i] = ray[i];
        }
        return true;
    }

    @Patch(className = "viewpoint.input.CrosshairAim", methodName = "line", strictMatch = true)
    public static final class LinePatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) double[] line,
                @Patch.Return(readOnly = false) boolean result) {
            result = Placement.placementLine(line, result);
        }
    }

    public static BuildAim.Target target(IsoGameCharacter player) {
        float[] ray = new float[6];
        if (Pointer.overUI() || !Pointer.interactionRay(ray)) {
            return null;
        }
        int width = Core.getInstance().getScreenWidth();
        int height = Core.getInstance().getScreenHeight();
        var ask = MousePick.ask;
        int x = Mouse.getXA(), y = Mouse.getYA();
        if (ask == null || ask.x() != x || ask.y() != y
                || ask.screenWidth() != width || ask.screenHeight() != height) {
            MousePick.ask = new MousePick.Ask(x, y, width, height);
        }
        var hit = MousePick.hit;
        var line = new BuildAim.Line(ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
        int floor = (int) Math.floor(player.getZ());
        if (floor != lastFloor) {
            lastSurface = Double.NaN;
            lastFloor = floor;
        }
        BuildAim.Target target = null;
        if (hit != null && hit.ask().x() == x && hit.ask().y() == y
                && hit.ask().screenWidth() == width && hit.ask().screenHeight() == height) {
            // A readback belongs to an older camera frame. Reject geometry that
            // no longer lies on the current ray instead of snapping to its tile.
            double hx = hit.x() - ray[0];
            double hy = hit.y() - ray[1];
            double hz = hit.z() * 2.4494896
                    + TerrainCamera.height((float) hit.x(), (float) hit.y()) - ray[2];
            double distance = hx * ray[3] + hy * ray[4] + hz * ray[5];
            double errorX = hx - distance * ray[3];
            double errorY = hy - distance * ray[4];
            double errorZ = hz - distance * ray[5];
            if (distance >= 0 && errorX * errorX + errorY * errorY + errorZ * errorZ < 0.0025) {
                target = BuildAim.at(hit, line, floor,
                        (px, py) -> TerrainCamera.height((float) px, (float) py),
                        isPreview(hit) ? lastSurface : Double.NaN);
                if (target != null) {
                    target = BuildAim.at(hit, line, floor,
                            (px, py) -> TerrainCamera.height((float) px, (float) py), target.up());
                }
            }
        }
        // GPU picking arrives asynchronously and other consumers replace its request.
        // Keep the placement live on the player's floor while that result is pending.
        if (target == null && ray[5] < -0.00001f) {
            double up = floor * 2.4494896 + TerrainCamera.height(player.getX(), player.getY());
            double along = (up - ray[2]) / ray[5];
            double px = ray[0] + along * ray[3];
            double py = ray[1] + along * ray[4];
            if (Double.isFinite(along) && along >= 0) {
                target = new BuildAim.Target((int) Math.floor(px), (int) Math.floor(py),
                        floor, px, py, up, along);
            }
        }
        if (target == null || Math.hypot(target.pointX() - player.getX(),
                target.pointY() - player.getY()) > viewpoint.platform.Tuning.buildReach) {
            return null;
        }
        lastSurface = target.up();
        return target;
    }

    private static boolean isPreview(MousePick.Hit hit) {
        try {
            if (previewHit == null) {
                previewHit = Class.forName("viewpoint.building.BuildSight")
                        .getDeclaredMethod("onPreview", MousePick.Hit.class);
                previewHit.setAccessible(true);
            }
            return Boolean.TRUE.equals(previewHit.invoke(null, hit));
        } catch (ReflectiveOperationException error) {
            CompatibilityErrors.report(error);
            return false;
        }
    }

    @Patch(className = "viewpoint.building.BuildSight", methodName = "target", strictMatch = true)
    public static final class TargetPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Placement.active();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoGameCharacter player,
                @Patch.Return(readOnly = false) BuildAim.Target result) {
            if (Placement.active()) {
                result = Placement.target(player);
            }
        }
    }
}
