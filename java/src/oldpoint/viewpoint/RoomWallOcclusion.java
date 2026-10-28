package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Native-inspired, same-floor wall/room relations, computed before drawing. */
final class RoomWallOcclusion {

    record Segment(int x, int y, int floor, int kind) {}

    record Selection(Set<Segment> hidden, Map<Segment, Set<Long>> rooms) {
        static final Selection EMPTY = new Selection(Set.of(), Map.of());
    }

    private record Candidates(
            Object cell,
            Object player,
            Object room,
            int x,
            int y,
            int floor,
            List<Layout.Rect> regions,
            long time,
            List<Segment> walls) {}

    private record Result(Candidates candidates, int eyeX, int eyeY, int eyeUp, long time, Selection selection) {}

    private static Candidates candidates;
    private static Candidates request;
    private static CaptureScan scan;
    private static ArrayList<Segment> pending;
    private static Result result;

    private RoomWallOcclusion() {}

    static void clear() {
        candidates = null;
        request = null;
        scan = null;
        pending = null;
        result = null;
    }

    private static boolean matches(
            Candidates c, Object cell, Object player, Object room, int x, int y, int floor, List<Layout.Rect> regions) {
        return c != null
                && c.cell == cell
                && c.player == player
                && c.room == room
                && c.x == x
                && c.y == y
                && c.floor == floor
                && c.regions.equals(regions);
    }

    static Selection capture(IsoGameCharacter player, List<Layout.Rect> occupied, Layout.Sight eye) {
        var current = player.getCurrentSquare();
        if (occupied.isEmpty() || current == null || current.getRoom() == null) {
            clear();
            return Selection.EMPTY;
        }
        var cell = IsoWorld.instance.getCell();
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        int floor = (int) Math.floor(player.getZ());
        long now = System.nanoTime();
        Candidates cache = candidates;
        boolean valid = matches(cache, cell, player, current.getRoom(), cx, cy, floor, occupied);
        if (!valid || now - cache.time >= 250_000_000L) {
            if (!matches(request, cell, player, current.getRoom(), cx, cy, floor, occupied)) {
                int left = cx - 12, right = cx + 12, top = cy - 12, bottom = cy + 12;
                for (var r : occupied) {
                    left = Math.min(left, r.x() - 4);
                    right = Math.max(right, r.x() + r.width() + 3);
                    top = Math.min(top, r.y() - 4);
                    bottom = Math.max(bottom, r.y() + r.height() + 3);
                }
                left = Math.max(left, cx - 32);
                right = Math.min(right, cx + 32);
                top = Math.max(top, cy - 32);
                bottom = Math.min(bottom, cy + 32);
                scan = new CaptureScan(left, right, top, bottom, floor, floor);
                pending = new ArrayList<>();
                request =
                        new Candidates(
                                cell, player, current.getRoom(), cx, cy, floor, List.copyOf(occupied), now, List.of());
            }
            var building = current.getBuilding();
            scan.begin();
            while (scan.next()) {
                int x = scan.x, y = scan.y;
                var square = cell.getGridSquare(x, y, floor);
                if (square == null) {
                    continue;
                }
                for (int kind = 1; kind <= 2; kind++) {
                    if (!hasWall(square, kind)) {
                        continue;
                    }
                    // Keep shared partitions as candidates: two visible rooms
                    // can lie on opposite sides of the same wall. Their union
                    // boundary alone deliberately has no edge at that partition.
                    var neighbour = cell.getGridSquare(x - (kind == 2 ? 1 : 0), y - (kind == 1 ? 1 : 0), floor);
                    // Do not collapse an unrelated nearby house just because it projects over this
                    // one.
                    if (building != null
                            && square.getBuilding() != building
                            && (neighbour == null || neighbour.getBuilding() != building)) {
                        continue;
                    }
                    pending.add(new Segment(x, y, floor, kind));
                }
            }
            scan.finish();
            if (scan.done()) {
                pending.sort(
                        java.util.Comparator.comparingDouble(
                                w -> (w.x - cx) * (double) (w.x - cx) + (w.y - cy) * (double) (w.y - cy)));
                if (pending.size() > 256) {
                    pending.subList(256, pending.size()).clear();
                }
                cache =
                        new Candidates(
                                cell,
                                player,
                                current.getRoom(),
                                cx,
                                cy,
                                floor,
                                List.copyOf(occupied),
                                now,
                                List.copyOf(pending));
                candidates = cache;
                request = null;
                scan = null;
                pending = null;
            } else if (!valid
                    && (cache == null
                            || cache.cell != cell
                            || cache.player != player
                            || cache.room != current.getRoom()
                            || cache.floor != floor)) {
                // Moving one tile within the same room often outlasts the
                // per-frame scan budget. Keep evaluating the last complete
                // wall list until its replacement is ready, rather than
                // flashing every foreign wall back into view.
                return Selection.EMPTY;
            }
        }
        // Changes in orbit, pitch, zoom or player position update the relation cache.
        int ex = Math.round(eye.cx() * 8), ey = Math.round(eye.cy() * 8), ez = Math.round(eye.cup() * 8);
        Result previous = result;
        if (previous != null
                && previous.candidates == cache
                && previous.eyeX == ex
                && previous.eyeY == ey
                && previous.eyeUp == ez
                && now - previous.time < 120_000_000L) {
            return previous.selection;
        }
        int playerIndex = player instanceof IsoPlayer local ? local.getIndex() : 0;
        var camera = CutawayProjection.Camera.perspective(eye);
        HashSet<Segment> hidden = new HashSet<>();
        HashMap<Segment, Set<Long>> relations = new HashMap<>();
        for (var wall : cache.walls) {
            var footprint =
                    CutawayProjection.wall(new OcclusionSnapshot.Wall(wall.x, wall.y, wall.floor, wall.kind), camera);
            HashSet<Long> occluded = new HashSet<>();
            for (var region : occupied) {
                // Clip to existing target rooms and the candidate scan's local
                // extent before enumeration, including near-horizon projections.
                var bounds =
                        footprint.bounds(
                                Math.max(region.x(), cx - 32),
                                Math.max(region.y(), cy - 32),
                                Math.min(region.x() + region.width(), cx + 33),
                                Math.min(region.y() + region.height(), cy + 33));
                if (bounds == null) {
                    continue;
                }
                for (int ty = bounds.y(); ty < bounds.y() + bounds.height(); ty++) {
                    for (int tx = bounds.x(); tx < bounds.x() + bounds.width(); tx++) {
                        if (!footprint.overlaps(tx, ty, tx + 1, ty + 1)) {
                            continue;
                        }
                        var target = cell.getGridSquare(tx, ty, floor);
                        if (target != null
                                && target.getRoom() != null
                                && !target.getObjects().isEmpty()
                                && target.isCouldSee(playerIndex)) {
                            occluded.add(target.getRoomID());
                        }
                    }
                }
            }
            if (!occluded.isEmpty()) {
                hidden.add(wall);
                relations.put(wall, Set.copyOf(occluded));
            }
        }
        Selection selection = new Selection(Set.copyOf(hidden), Map.copyOf(relations));
        result = new Result(cache, ex, ey, ez, now, selection);
        return selection;
    }

    private static boolean hasWall(IsoGridSquare square, int kind) {
        var p = square.getProperties();
        return p.has(kind == 1 ? "WallN" : "WallW")
                || p.has(kind == 1 ? "WallNW" : "WallSE")
                || p.has(kind == 1 ? IsoFlagType.WallN : IsoFlagType.WallW)
                || p.has(kind == 1 ? IsoFlagType.cutN : IsoFlagType.cutW);
    }
}
