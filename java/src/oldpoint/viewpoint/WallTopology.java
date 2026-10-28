package oldpoint.viewpoint;

import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import oldpoint.viewpoint.OcclusionSnapshot.Fading;
import oldpoint.viewpoint.OcclusionSnapshot.Wall;
import zombie.iso.IsoCell;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Captures physical wall/group inputs without changing projection-based selection. */
final class WallTopology {
    private WallTopology() {}

    static OcclusionSnapshot.Fading capture(OcclusionSnapshot.Wall wall, float amount, IsoCell cell) {
        return capture(wall, amount, cell, Set.of());
    }

    static OcclusionSnapshot.Fading capture(
            OcclusionSnapshot.Wall wall, float amount, IsoCell cell, Set<OcclusionSnapshot.Wall> lowered) {
        return capture(wall, amount, other -> capable(cell, other), lowered::contains);
    }

    static OcclusionSnapshot.Fading capture(
            OcclusionSnapshot.Wall wall, float amount, Predicate<OcclusionSnapshot.Wall> exists) {
        return capture(wall, amount, exists, candidate -> false);
    }

    static OcclusionSnapshot.Fading capture(
            OcclusionSnapshot.Wall wall,
            float amount,
            Predicate<OcclusionSnapshot.Wall> exists,
            Predicate<OcclusionSnapshot.Wall> lowered) {
        return capture(
                wall,
                amount,
                exists,
                lowered,
                CutawayProjection.Camera.orthographic(3, 3, CutawayProjection.STOREY, wall.floor()));
    }

    static Fading capture(
            Wall wall, float amount, Predicate<Wall> exists, Predicate<Wall> lowered, CutawayProjection.Camera camera) {
        var profile = WallContour.selected(wall, exists, lowered, camera);
        return new Fading(wall, amount, profile.startArc(), profile.endArc());
    }

    static ArrayList<Fading> capture(List<Fading> selected, IsoCell cell, CutawayProjection.Camera camera, int limit) {
        HashMap<Wall, Boolean> physical = new HashMap<>();
        Predicate<Wall> exists = wall -> physical.computeIfAbsent(wall, candidate -> capable(cell, candidate));
        HashMap<Wall, Fading> primary = new HashMap<>();
        HashSet<Wall> lowered = new HashSet<>();
        ArrayList<Fading> masks = new ArrayList<>();
        for (var f : selected) {
            primary.put(f.wall(), f);
            if (f.amount() > 0) {
                lowered.add(f.wall());
            }
        }
        for (var f : selected) {
            masks.add(capture(f.wall(), f.amount(), exists, lowered::contains, camera));
        }
        HashSet<Wall> added = new HashSet<>();
        for (var f : selected) {
            if (masks.size() >= limit) {
                break;
            }
            Wall wall = f.wall();
            Wall peer = new Wall(wall.x(), wall.y(), wall.floor(), 3 - wall.kind());
            if (f.amount() <= 0
                    || wall.floor() != camera.floor()
                    || primary.containsKey(peer)
                    || added.contains(peer)
                    || !exists.test(peer)) {
                continue;
            }
            var square = cell == null ? null : cell.getGridSquare(wall.x(), wall.y(), wall.floor());
            // Identity is essential: two independent walls at a corner are not
            // automatically a combined NW object with shared mask dispatch.
            if (square == null || square.getWall(true) == null || square.getWall(true) != square.getWall(false)) {
                continue;
            }
            var companion = WallContour.companion(peer, exists, lowered::contains, camera);
            if (companion == null) {
                continue;
            }
            var continuation = primary.get(companion.continuation());
            float amount = Math.min(f.amount(), continuation.amount());
            var p = companion.profile();
            masks.add(new Fading(peer, amount, p.startArc(), p.endArc(), true));
            added.add(peer);
        }
        return masks;
    }

    private static boolean capable(IsoCell cell, OcclusionSnapshot.Wall w) {
        var square = cell == null ? null : cell.getGridSquare(w.x(), w.y(), w.floor());
        if (square == null) {
            return false;
        }
        var p = square.getProperties();
        if (p.has(IsoFlagType.WallSE)) {
            return false;
        }
        boolean north = w.kind() == 1;
        boolean window = p.has(north ? IsoFlagType.WindowN : IsoFlagType.WindowW);
        boolean wallLike =
                (square.getWall(north) != null || window)
                        && (p.has(north ? IsoFlagType.WallN : IsoFlagType.WallW)
                                || p.has(IsoFlagType.WallNW)
                                || p.has(north ? IsoFlagType.DoorWallN : IsoFlagType.DoorWallW)
                                || window);
        return wallLike || square.getGarageDoor(north) != null;
    }
}
