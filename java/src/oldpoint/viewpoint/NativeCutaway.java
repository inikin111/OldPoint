package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.RoomDef;

/**
 * Native room selection on the capture thread. Building and wall occlusion use the actual 3D camera independently of
 * the original fixed projection.
 */
final class NativeCutaway implements AutoCloseable {
    private record Cached(Object cell, Object player, Object square, int floor, long time, List<Layout.Rect> rooms) {}

    private static Cached cached;

    static void clear() {
        cached = null;
    }

    private final IsoCell cell;
    private final IsoCamera.FrameState state = IsoCamera.frameState;
    private final int oldIndex;
    private final int index;
    final List<Layout.Rect> rooms = new ArrayList<>();

    NativeCutaway(IsoCell cell, IsoPlayer player) {
        this.cell = cell;
        index = player.getIndex();
        oldIndex = state.playerIndex;
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
        cached = new Cached(cell, player, player.getCurrentSquare(), floor, now, List.copyOf(rooms));
    }

    @Override
    public void close() {
        state.playerIndex = oldIndex;
    }
}
