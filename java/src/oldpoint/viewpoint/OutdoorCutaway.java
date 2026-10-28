package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zombie.characters.IsoPlayer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoWorld;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Camera-dependent outdoor selection over cached local building topology. */
final class OutdoorCutaway {
    record Wall(int x, int y, int kind) {}

    record Selection(List<BuildingDef> buildings, List<Layout.Rect> upper, List<Wall> walls) {
        boolean opening(int x, int y, int kind) {
            return walls.contains(new Wall(x, y, kind));
        }

        boolean roofOwner(IsoGridSquare square, int x, int y) {
            if (square != null) {
                if (square.associatedBuilding != null) {
                    return buildings.contains(square.associatedBuilding);
                }
                if (square.getBuilding() != null) {
                    return buildings.contains(square.getBuilding().getDef());
                }
                if (square.getRoofHideBuilding() != null) {
                    return buildings.contains(square.getRoofHideBuilding().getDef());
                }
            }
            // Unassigned eaves may sit one tile beyond their building's footprint.
            for (var r : upper) {
                if (x >= r.x() - 1 && y >= r.y() - 1 && x < r.x() + r.width() + 1 && y < r.y() + r.height() + 1) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final float STOREY = 2.4494896f;
    private static final int RADIUS = 32;

    private record Candidates(Object cell, Object grid, int x, int y, long time, List<BuildingDef> buildings) {}

    private record Cached(
            Object cell,
            Object player,
            int floor,
            boolean includeWalls,
            Layout.Sight eye,
            long time,
            OutdoorCutaway.Selection result) {}

    private static Candidates candidates;
    private static Cached cached;
    private static final Map<BuildingDef, Long> held = new HashMap<>();
    private static long lastReport;

    private OutdoorCutaway() {}

    static void clear() {
        candidates = null;
        cached = null;
        held.clear();
    }

    static OutdoorCutaway.Selection capture(IsoPlayer player, Layout.Sight eye) {
        return capture(player, eye, true);
    }

    /** Indoor views need building collapse only; retain their own wall rules. */
    static OutdoorCutaway.Selection captureUpper(IsoPlayer player, Layout.Sight eye) {
        return capture(player, eye, false);
    }

    private static OutdoorCutaway.Selection capture(IsoPlayer player, Layout.Sight eye, boolean includeWalls) {
        var cell = IsoWorld.instance.getCell();
        int floor = (int) Math.floor(player.getZ());
        long now = System.nanoTime();
        var old = cached;
        boolean same =
                old != null
                        && old.cell == cell
                        && old.player == player
                        && old.floor == floor
                        && old.includeWalls == includeWalls;
        if (!same) {
            held.clear();
            candidates = null;
        }
        // At most 13 evaluations/sec while moving; refresh stationary topology
        // periodically too, for native room/structure/visibility changes.
        if (same && (now - old.time < 75_000_000L || near(old.eye, eye) && now - old.time < 200_000_000L)) {
            return old.result;
        }
        var camera = CutawayProjection.Camera.perspective(eye);
        var targets = OutdoorTargets.capture(player, eye);
        var buildings = local(player, now);
        for (var building : buildings) {
            if (obstructs(building, eye, targets)) {
                held.put(building, now);
            }
        }
        held.entrySet().removeIf(e -> now - e.getValue() > 250_000_000L || !buildings.contains(e.getKey()));
        ArrayList<BuildingDef> selected = new ArrayList<>();
        ArrayList<Layout.Rect> upper = new ArrayList<>();
        // Keep stable candidate order so small camera changes do not invalidate
        // roof caches merely by reordering the same selected buildings.
        for (var b : buildings) {
            if (held.containsKey(b)) {
                selected.add(b);
                upper.add(new Layout.Rect(b.getX(), b.getY(), b.getW(), b.getH()));
            }
        }
        ArrayList<OutdoorCutaway.Wall> walls = new ArrayList<>();
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        if (includeWalls) {
            for (int y = cy - 12; y <= cy + 12; y++) {
                for (int x = cx - 12; x <= cx + 12; x++) {
                    var square = cell.getGridSquare(x, y, floor);
                    if (square == null) {
                        continue;
                    }
                    var p = square.getProperties();
                    for (int kind = 1; kind <= 2; kind++) {
                        if (!p.has(kind == 1 ? IsoFlagType.WallN : IsoFlagType.WallW)
                                && !p.has(kind == 1 ? "WallN" : "WallW")
                                && !p.has(kind == 1 ? IsoFlagType.cutN : IsoFlagType.cutW)
                                && !p.has(kind == 1 ? "WallNW" : "WallSE")) {
                            continue;
                        }
                        var footprint = CutawayProjection.wall(new OcclusionSnapshot.Wall(x, y, floor, kind), camera);
                        for (var target : targets) {
                            if (footprint.overlaps(target)) {
                                walls.add(new OutdoorCutaway.Wall(x, y, kind));
                                break;
                            }
                        }
                    }
                }
            }
        }
        var result = new OutdoorCutaway.Selection(List.copyOf(selected), List.copyOf(upper), List.copyOf(walls));
        if (now - lastReport > 5_000_000_000L
                && (!selected.isEmpty() || old != null && !old.result.buildings().isEmpty())) {
            lastReport = now;
            System.out.println(
                    "[OldPoint] camera cutaway: candidates="
                            + buildings.size()
                            + ", buildings="
                            + selected.size()
                            + ", walls="
                            + walls.size()
                            + ", floor="
                            + floor);
        }
        cached = new Cached(cell, player, floor, includeWalls, eye, now, result);
        return result;
    }

    private static List<BuildingDef> local(IsoPlayer player, long now) {
        var cell = IsoWorld.instance.getCell();
        var grid = IsoWorld.instance.metaGrid;
        int x = (int) Math.floor(player.getX()), y = (int) Math.floor(player.getY());
        var old = candidates;
        if (old != null
                && old.cell == cell
                && old.grid == grid
                && Math.abs(x - old.x) <= 4
                && Math.abs(y - old.y) <= 4
                && now - old.time < 1_000_000_000L) {
            return old.buildings;
        }
        ArrayList<BuildingDef> found = new ArrayList<>();
        // Query only intersecting 8x8 metadata chunks, never the full map's
        // buildings list or the global query's wide meta-cell iteration.
        if (grid != null) {
            for (int cy = Math.floorDiv(y - RADIUS, 8); cy <= Math.floorDiv(y + RADIUS, 8); cy++) {
                for (int cx = Math.floorDiv(x - RADIUS, 8); cx <= Math.floorDiv(x + RADIUS, 8); cx++) {
                    var chunk = grid.getChunkData(cx, cy);
                    if (chunk != null) {
                        chunk.getBuildingsIntersecting(x - RADIUS, y - RADIUS, RADIUS * 2 + 1, RADIUS * 2 + 1, found);
                    }
                }
            }
        }
        found.removeIf(b -> b == null || b.getW() <= 0 || b.getH() <= 0);
        candidates = new Candidates(cell, grid, x, y, now, List.copyOf(found));
        return candidates.buildings;
    }

    private static boolean near(Layout.Sight a, Layout.Sight b) {
        return Math.abs(a.px() - b.px()) < 0.08f
                && Math.abs(a.py() - b.py()) < 0.08f
                && Math.abs(a.up() - b.up()) < 0.08f
                && Math.abs(a.cx() - b.cx()) < 0.08f
                && Math.abs(a.cy() - b.cy()) < 0.08f
                && Math.abs(a.cup() - b.cup()) < 0.08f;
    }

    private static boolean obstructs(BuildingDef b, Layout.Sight eye, List<CutawayGeometry.Target> targets) {
        float low = eye.floor() * STOREY + 0.12f;
        float high = (Math.max(eye.floor(), b.getMaxLevel()) + 2) * STOREY;
        var camera = CutawayProjection.Camera.perspective(eye);
        var broad =
                CutawayProjection.prism(
                        camera, b.getX() - 0.3, b.getY() - 0.3, low, b.getX2() + 0.3, b.getY2() + 0.3, high);
        var rooms = new ArrayList<CutawayProjection.Volume>();
        for (var room : b.getRooms()) {
            if (room.level < eye.floor()) {
                continue;
            }
            double bottom = Math.max(low, room.level * STOREY);
            double top = (room.level + 1) * STOREY + (room.level == b.getMaxLevel() ? STOREY : 0);
            for (var r : room.getRects()) {
                rooms.add(
                        CutawayProjection.prism(
                                camera, r.x - 0.25, r.y - 0.25, bottom, r.x + r.w + 0.25, r.y + r.h + 0.25, top));
            }
        }
        for (var target : targets) {
            if (!broad.overlaps(target)) {
                continue;
            }
            if (b.getRooms().isEmpty()) {
                return true;
            }
            for (var room : rooms) {
                if (room.overlaps(target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
