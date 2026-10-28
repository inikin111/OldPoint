package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.RoomDef;
import zombie.iso.areas.IsoBuilding;

/**
 * Native isometric decisions, evaluated only on the game capture thread. No native stencil, GL state, textures or
 * shader-pack programs are changed.
 */
final class NativeCutaway implements AutoCloseable {
    private record Cached(
            Object cell,
            Object player,
            Object square,
            int floor,
            long time,
            List<Layout.Rect> rooms,
            List<Layout.Rect> upper,
            IsoBuilding[] buildings,
            boolean orphan) {}

    private static Cached cached;

    static void clear() {
        cached = null;
    }

    private final IsoCell cell;
    private final IsoCamera.FrameState state = IsoCamera.frameState;
    private final int oldIndex;
    private final IsoBuilding[] oldBuildings;
    private final boolean oldOrphan;
    private final int index;
    final List<Layout.Rect> rooms = new ArrayList<>();
    final List<Layout.Rect> upper = new ArrayList<>();

    NativeCutaway(IsoCell cell, IsoPlayer player) {
        this.cell = cell;
        index = player.getIndex();
        oldIndex = state.playerIndex;
        oldBuildings = cell.playerOccluderBuildingsArr[index];
        oldOrphan = cell.occludedByOrphanStructureFlag;
        state.playerIndex = index;
    }

    void prepare(IsoPlayer player) {
        long now = System.nanoTime();
        int floor = (int) Math.floor(player.getZ());
        Cached old = cached;
        if (old != null
                && old.cell == cell
                && old.player == player
                && old.square == player.getCurrentSquare()
                && old.floor == floor
                && now - old.time < 120_000_000L) {
            rooms.addAll(old.rooms);
            upper.addAll(old.upper);
            cell.playerOccluderBuildingsArr[index] = old.buildings;
            cell.occludedByOrphanStructureFlag = old.orphan;
            return;
        }
        // Viewpoint can bypass RenderTiles, so refresh native room selection
        // explicitly instead of consuming stale square cutaway flags.
        cell.SetCutawayRoomsForPlayer();
        var selected = cell.tempPlayerCutawayRoomIds.get(index);
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        var seen = new java.util.HashSet<RoomDef>();
        for (int y = cy - 12; y <= cy + 12; y++) {
            for (int x = cx - 12; x <= cx + 12; x++) {
                var square = cell.getGridSquare(x, y, floor);
                if (square == null || square.getRoom() == null || !selected.contains(square.getRoomID())) {
                    continue;
                }
                var def = square.getRoom().getRoomDef();
                if (def != null && seen.add(def)) {
                    for (var r : def.getRects()) {
                        rooms.add(new Layout.Rect(r.x, r.y, r.w, r.h));
                    }
                }
            }
        }
        var buildings = new ArrayList<IsoBuilding>();
        cell.GetBuildingsInFrontOfCharacter(buildings, player.getCurrentSquare(), false);
        if (buildings.isEmpty()) {
            cell.GetBuildingsInFrontOfCharacter(buildings, player.getCurrentSquare(), true);
        }
        var own = player.getCurrentSquare().getBuilding();
        if (own != null && !buildings.contains(own)) {
            buildings.add(own);
        }
        cell.playerOccluderBuildingsArr[index] = buildings.toArray(new IsoBuilding[500]);
        // Native collapsible buildings lose levels above the walking floor.
        // Keep this distinct from the room targets used for current-floor walls.
        for (var building : buildings) {
            var d = building.getDef();
            if (d != null) {
                upper.add(new Layout.Rect(d.getX(), d.getY(), d.getX2() - d.getX(), d.getY2() - d.getY()));
            }
        }
        cached =
                new Cached(
                        cell,
                        player,
                        player.getCurrentSquare(),
                        floor,
                        now,
                        List.copyOf(rooms),
                        List.copyOf(upper),
                        cell.playerOccluderBuildingsArr[index],
                        cell.occludedByOrphanStructureFlag);
    }

    boolean cuts(IsoGridSquare square, long now) {
        return cell.IsCutawaySquare(square, now);
    }

    @Override
    public void close() {
        cell.playerOccluderBuildingsArr[index] = oldBuildings;
        cell.occludedByOrphanStructureFlag = oldOrphan;
        state.playerIndex = oldIndex;
    }
}
