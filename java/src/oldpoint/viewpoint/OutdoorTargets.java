package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoWorld;

/** Capture native-style outdoor points of interest in the current camera's orientation. */
final class OutdoorTargets {
    private OutdoorTargets() {}

    static List<CutawayGeometry.Target> capture(IsoPlayer player, Layout.Sight eye) {
        var targets = new ArrayList<CutawayGeometry.Target>();
        // Protect the full body footprint as a region, not a bundle of sample rays.
        targets.add(CutawayGeometry.Target.body(player.getX(), player.getY()));
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        var cell = IsoWorld.instance.getCell();
        int playerIndex = player.getIndex();
        long peekRoom = cell.playerWindowPeekingRoomId[playerIndex];
        for (int y = cy - 4; y <= cy + 4; y++) {
            for (int x = cx - 4; x <= cx + 4; x++) {
                var square = cell.getGridSquare(x, y, eye.floor());
                if (square == null || !square.isCouldSee(playerIndex)) {
                    continue;
                }
                boolean peeked = peekRoom != -1 && square.getRoomID() == peekRoom;
                if (!peeked
                        && (square.getBuilding() != null || !CutawayGeometry.protectsSpace(eye, x + 0.5f, y + 0.5f))) {
                    continue;
                }
                targets.add(CutawayGeometry.Target.tile(x, y));
            }
        }
        return List.copyOf(targets);
    }
}
