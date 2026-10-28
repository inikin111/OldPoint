package oldpoint.viewpoint;

import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.areas.IsoRoom;

/** Runs real world-capture selectors against the fixture world in run_cutaway_tests.py. */
public final class CutawaySelectionTest {
    private static final Layout.Sight NORTH = new Layout.Sight(0.5f, 1, 0.8f, 0.5f, -4, 3, 0);
    private static final List<Layout.Rect> ROOM = List.of(new Layout.Rect(0, 0, 4, 4));

    private static IsoGridSquare square(int x, int y) {
        var cell = IsoWorld.instance.getCell();
        var square = cell.getGridSquare(x, y, 0);
        if (square == null) {
            square = new IsoGridSquare();
            square.x = x;
            square.y = y;
            cell.put(square);
        }
        return square;
    }

    private static IsoPlayer reset() {
        IsoWorld.instance = new IsoWorld();
        RoomWallOcclusion.clear();
        OutdoorCutaway.clear();
        NativeCutaway.clear();
        var player = new IsoPlayer();
        player.square = square(0, 1);
        return player;
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError(name);
        }
        System.out.println("PASS " + name);
    }

    private static RoomWallOcclusion.Selection indoor(IsoPlayer player, Layout.Sight eye) {
        return indoor(player, ROOM, eye);
    }

    private static RoomWallOcclusion.Selection indoor(IsoPlayer player, List<Layout.Rect> regions, Layout.Sight eye) {
        RoomWallOcclusion.Selection selection = RoomWallOcclusion.Selection.EMPTY;
        // Complete a budgeted production scan without sleeping or disabling its budget.
        for (int frame = 0; frame < 128; frame++) {
            CaptureScan.startFrame();
            try {
                selection = RoomWallOcclusion.capture(player, regions, eye);
            } finally {
                CaptureScan.endFrame();
            }
        }
        return selection;
    }

    private static boolean wall(RoomWallOcclusion.Selection selection, int x, int y) {
        return selection.hidden().contains(new RoomWallOcclusion.Segment(x, y, 0, 1));
    }

    private static IsoPlayer room() {
        var player = reset();
        var room = new IsoRoom();
        room.def.rects.add(new RoomDef.RoomRect(0, 0, 4, 4));
        var building = new IsoBuilding();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                square(x, y).room = room;
                square(x, y).building = building;
            }
        }
        for (int x = 0; x < 4; x++) {
            square(x, 0).props.flags.add("WallN");
            square(x, 4).props.flags.add("WallN");
        }
        return player;
    }

    public static void main(String[] args) {
        var player = room();
        IsoWorld.instance.getCell().tempPlayerCutawayRoomIds.get(0).add(1L);
        zombie.iso.IsoCamera.frameState.playerIndex = 3;
        try (var nativeRooms = new NativeCutaway(IsoWorld.instance.getCell(), player)) {
            nativeRooms.prepare(player);
            check("native selected room captured", nativeRooms.rooms.equals(ROOM));
            check("native room selection uses correct player", zombie.iso.IsoCamera.frameState.playerIndex == 0);
        }
        check("native room selection restores player", zombie.iso.IsoCamera.frameState.playerIndex == 3);
        NativeCutaway.clear();
        IsoWorld.instance.getCell().failSelection = true;
        try (var nativeRooms = new NativeCutaway(IsoWorld.instance.getCell(), player)) {
            nativeRooms.prepare(player);
            throw new AssertionError("fixture should fail");
        } catch (IllegalStateException expected) {
            check("native room failure restores player", zombie.iso.IsoCamera.frameState.playerIndex == 3);
        }
        IsoWorld.instance.getCell().failSelection = false;
        var selected = indoor(player, NORTH);
        check("room foreground wall selected", wall(selected, 0, 0));
        check("room rear wall retained", !wall(selected, 0, 4));
        check("visible tile edge reaches final wall segment", wall(selected, 3, 0));
        var opposite = new Layout.Sight(0.5f, 1, 0.8f, 0.5f, 8, 3, 0);
        selected = indoor(player, opposite);
        check("room selection follows camera", wall(selected, 0, 4) && !wall(selected, 0, 0));

        player = room();
        for (var square : IsoWorld.instance.getCell().squares.values()) {
            square.visible = square.x == 0 && square.y == 1;
        }
        selected = indoor(player, NORTH);
        check("partial visibility does not cut whole contour", wall(selected, 0, 0) && !wall(selected, 3, 0));
        check("no automatic adjacent-wall padding", !wall(selected, 1, 0));
        check("no propagation beyond actual coverage", !wall(selected, 2, 0));
        var narrowRoom = List.of(new Layout.Rect(0, 0, 1, 4));
        square(1, 0).room = new IsoRoom();
        square(1, 0).room.id = 2;
        selected = indoor(player, narrowRoom, NORTH);
        check("same building does not bypass projection", !wall(selected, 1, 0));
        RoomWallOcclusion.clear();
        square(0, -1).props.flags.add("WallN");
        square(0, -1).building = new IsoBuilding();
        selected = indoor(player, narrowRoom, NORTH);
        check("another building remains outside indoor candidates", !wall(selected, 0, -1));
        RoomWallOcclusion.clear();
        square(1, 0).props.flags.clear();
        selected = indoor(player, NORTH);
        check("wall gap does not cause extra selection", !wall(selected, 2, 0));
        square(1, 0).props.flags.add("WallN");
        for (var square : IsoWorld.instance.getCell().squares.values()) {
            square.building = null;
        }
        RoomWallOcclusion.clear();
        selected = indoor(player, narrowRoom, NORTH);
        check("unknown building uses the same projection", wall(selected, 0, 0) && !wall(selected, 1, 0));
        square(0, 1).objects.clear();
        RoomWallOcclusion.clear();
        check("empty target squares cannot select a wall", indoor(player, narrowRoom, NORTH).hidden().isEmpty());

        player = room();
        square(0, 2).props.flags.add("WallN");
        check("visible room partition selected", wall(indoor(player, NORTH), 0, 2));
        RoomWallOcclusion.clear();
        for (int y = 2; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                square(x, y).visible = false;
            }
        }
        check("partition with no visible space retained", !wall(indoor(player, NORTH), 0, 2));

        player = reset();
        square(1, 0).props.flags.add("WallN");
        square(0, 3).props.flags.add("WallN");
        var outdoor = OutdoorCutaway.capture(player, NORTH);
        check("outdoor nearby visible space protected", outdoor.opening(1, 0, 1));
        check("outdoor rear wall retained", !outdoor.opening(0, 3, 1));
        square(1, 0).visible = false;
        OutdoorCutaway.clear();
        check("unseen outdoor space ignored", !OutdoorCutaway.capture(player, NORTH).opening(1, 0, 1));

        var peeked = square(0, 3);
        peeked.room = new IsoRoom();
        peeked.building = new IsoBuilding();
        check(
                "unselected room excluded outdoors",
                OutdoorTargets.capture(player, NORTH).stream().noneMatch(t -> t.x() == 0.5f && t.y() == 3.5f));
        IsoWorld.instance.getCell().playerWindowPeekingRoomId[0] = peeked.getRoomID();
        check(
                "native peeked room protected",
                OutdoorTargets.capture(player, NORTH).stream().anyMatch(t -> t.x() == 0.5f && t.y() == 3.5f));

        player = reset();
        square(2, 0).visible = true;
        var building = new BuildingDef();
        building.x = 2;
        building.y = -2;
        building.w = 1;
        building.h = 2;
        var room = new RoomDef();
        room.rects.add(new RoomDef.RoomRect(2, -2, 1, 2));
        building.rooms.add(room);
        IsoWorld.instance.metaGrid.buildings.add(building);
        outdoor = OutdoorCutaway.capture(player, NORTH);
        check("building uses nearby protected space", outdoor.buildings().contains(building));
        square(2, 0).visible = false;
        OutdoorCutaway.clear();
        check("building not hidden by unseen space", OutdoorCutaway.capture(player, NORTH).buildings().isEmpty());
    }
}
