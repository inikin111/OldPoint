package oldpoint.viewpoint;

import java.util.function.Predicate;
import oldpoint.viewpoint.OcclusionSnapshot.Wall;

/** Camera-relative endpoint rules. Selection bits and final surface profiles are separate inputs/outputs. */
final class WallContour {
    private static final double EPSILON = 1e-9;

    record Profile(boolean startArc, boolean endArc) {}

    record Companion(Profile profile, Wall continuation) {}

    private WallContour() {}

    static Profile selected(
            Wall wall, Predicate<Wall> exists, Predicate<Wall> lowered, CutawayProjection.Camera camera) {
        return new Profile(
                endpoint(wall, false, exists, lowered, camera), endpoint(wall, true, exists, lowered, camera));
    }

    private static boolean endpoint(
            Wall wall, boolean end, Predicate<Wall> exists, Predicate<Wall> lowered, CutawayProjection.Camera camera) {
        int vx = wall.x() + (end && wall.kind() == 1 ? 1 : 0);
        int vy = wall.y() + (end && wall.kind() == 2 ? 1 : 0);
        int ax = wall.kind() == 1 ? (end ? -1 : 1) : 0;
        int ay = wall.kind() == 2 ? (end ? -1 : 1) : 0;
        Wall straight = arm(vx, vy, -ax, -ay, wall.floor());
        if (exists.test(straight) && !lowered.test(straight)) {
            return true;
        }
        double hx = camera.x() - camera.w() * vx;
        double hy = camera.y() - camera.w() * vy;
        double length = Math.hypot(hx, hy);
        if (!Double.isFinite(length) || length <= EPSILON) {
            return false;
        }
        hx /= length;
        hy /= length;
        double ua = hy * ax - hx * ay;
        double da = hx * ax + hy * ay;
        // Continue out of the opposite endpoint, rather than counting an
        // arbitrary selected wall at this joint as a straight continuation.
        Wall continuation = arm(vx + ax, vy + ay, ax, ay, wall.floor());
        boolean continues = exists.test(continuation) && lowered.test(continuation);
        for (int side = -1; side <= 1; side += 2) {
            int bx = -ay * side, by = ax * side;
            Wall branch = arm(vx, vy, bx, by, wall.floor());
            if (!exists.test(branch) || lowered.test(branch)) {
                continue;
            }
            double ub = hy * bx - hx * by;
            double db = hx * bx + hy * by;
            // Compare at equal projected side displacement, not at wall midpoints.
            boolean behind = ua * ub > EPSILON && db * Math.abs(ua) < da * Math.abs(ub) - EPSILON;
            // Preserve vanilla's handed foreground-corner policy explicitly.
            boolean leftCorner = continues && ua < -EPSILON && ub > EPSILON && da > EPSILON && db > EPSILON;
            if (behind || leftCorner) {
                return true;
            }
        }
        return false;
    }

    /**
     * An unselected face of a selected multi-face object can inherit a mask from a selected straight continuation. It
     * never becomes a selection bit.
     */
    static Companion companion(
            Wall wall, Predicate<Wall> exists, Predicate<Wall> lowered, CutawayProjection.Camera camera) {
        for (int end = 0; end <= 1; end++) {
            int dx = wall.kind() == 1 ? (end == 1 ? 1 : -1) : 0;
            int dy = wall.kind() == 2 ? (end == 1 ? 1 : -1) : 0;
            int vx = wall.x() + (end == 1 && wall.kind() == 1 ? 1 : 0);
            int vy = wall.y() + (end == 1 && wall.kind() == 2 ? 1 : 0);
            Wall next = arm(vx, vy, dx, dy, wall.floor());
            if (!exists.test(next) || !lowered.test(next)) {
                continue;
            }
            double hx = camera.x() - camera.w() * vx;
            double hy = camera.y() - camera.w() * vy;
            double length = Math.hypot(hx, hy);
            if (!Double.isFinite(length) || length <= EPSILON) {
                continue;
            }
            hx /= length;
            hy /= length;
            double toward = hx * dx + hy * dy;
            double right = hy * dx - hx * dy;
            if (toward <= EPSILON || Math.abs(right) <= EPSILON) {
                continue;
            }
            Profile profile = selected(wall, exists, lowered, camera);
            if (right > 0) {
                // A right-leading handoff retains the far endpoint. Left-leading
                // handoffs use the ordinary profile (often just the low base).
                profile = new Profile(profile.startArc() || end == 1, profile.endArc() || end == 0);
            }
            return new Companion(profile, next);
        }
        return null;
    }

    private static Wall arm(int vx, int vy, int dx, int dy, int floor) {
        return new Wall(vx + Math.min(0, dx), vy + Math.min(0, dy), floor, dx == 0 ? 2 : 1);
    }
}
