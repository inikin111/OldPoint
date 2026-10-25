package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderCutaways.CutawayWall;

/** Native fixed-projection decisions only; never run the native 2D draw pipeline. */
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

    private record Cached(
            Object cell, Object player, Object square, int floor, boolean aiming, long time, Selection result) {}

    private static Cached cached;
    private static Field level, seenMasks;
    private static Method calculateRooms, calculateSeen, isCutaway;
    private static boolean reported;

    static void clear() {
        cached = null;
    }

    private OutdoorCutaway() {}

    static Selection capture(IsoPlayer player) {
        var cell = IsoWorld.instance.getCell();
        var square = player.getCurrentSquare();
        int floor = (int) Math.floor(player.getZ());
        long time = System.nanoTime();
        var old = cached;
        if (old != null
                && old.cell == cell
                && old.player == player
                && old.square == square
                && old.floor == floor
                && old.aiming == player.isAiming()
                && time - old.time < 150_000_000L) {
            return old.result;
        }
        var state = IsoCamera.frameState;
        int oldIndex = state.playerIndex;
        float oldX = state.camCharacterX, oldY = state.camCharacterY, oldZ = state.camCharacterZ;
        var oldCharacter = state.camCharacter;
        var oldSquare = state.camCharacterSquare;
        var oldRoom = state.camCharacterRoom;
        var nativeCut = FBORenderCutaways.getInstance();
        var oldCell = nativeCut.cell;
        boolean oldOrphan = cell.occludedByOrphanStructureFlag;
        try {
            int index = player.getPlayerNum();
            state.playerIndex = index;
            state.camCharacter = player;
            state.camCharacterSquare = square;
            state.camCharacterRoom = square.getRoom();
            state.camCharacterX = player.getX();
            state.camCharacterY = player.getY();
            state.camCharacterZ = player.getZ();
            nativeCut.cell = cell;
            // Native POIs include the player and visible surroundings. Native
            // collapse selection itself excludes the mouse-only point.
            nativeCut.CalculatePointsOfInterest();
            nativeCut.CalculateBuildingsToCollapse();
            List<BuildingDef> buildings = List.copyOf(nativeCut.getCollapsedBuildings());
            ArrayList<Layout.Rect> upper = new ArrayList<>();
            for (var b : buildings) {
                upper.add(new Layout.Rect(b.getX(), b.getY(), b.getW(), b.getH()));
            }
            if (buildings.isEmpty() && cell.tempPlayerCutawayRoomIds.get(index).isEmpty()) {
                var result = new Selection(List.of(), List.of(), List.of());
                cached = new Cached(cell, player, square, floor, player.isAiming(), time, result);
                return result;
            }
            init();
            ArrayList<Wall> walls = new ArrayList<>();
            int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
            long now = System.currentTimeMillis();
            // Native wall predicates use topology and seen-room masks. Adapt
            // individual wall spans to avoid native FBO isOnScreen dependencies.
            for (int y = cy - 12; y <= cy + 12; y++) {
                for (int x = cx - 12; x <= cx + 12; x++) {
                    var tile = cell.getGridSquare(x, y, floor);
                    if (tile == null || tile.chunk == null) {
                        continue;
                    }
                    var props = tile.getProperties();
                    for (int kind = 1; kind <= 2; kind++) {
                        boolean exists =
                                props.has(kind == 1 ? IsoFlagType.WallN : IsoFlagType.WallW)
                                        || props.has(kind == 1 ? IsoFlagType.cutN : IsoFlagType.cutW)
                                        || props.has(kind == 1 ? "WallN" : "WallW")
                                        || props.has(kind == 1 ? "WallNW" : "WallSE");
                        if (!exists) {
                            continue;
                        }
                        var wall = new CutawayWall();
                        wall.x1 = x;
                        wall.y1 = y;
                        wall.x2 = x + (kind == 1 ? 1 : 0);
                        wall.y2 = y + (kind == 2 ? 1 : 0);
                        level.set(wall, tile.chunk.getCutawayDataForLevel(floor));
                        calculateRooms.invoke(wall);
                        ((int[]) seenMasks.get(wall))[index] = (int) calculateSeen.invoke(wall, index);
                        if ((boolean) isCutaway.invoke(nativeCut, wall, square, tile, now)) {
                            walls.add(new Wall(x, y, kind));
                        }
                    }
                }
            }
            var result = new Selection(buildings, List.copyOf(upper), List.copyOf(walls));
            if (old == null || !old.result.buildings.equals(buildings) || old.result.walls.size() != walls.size()) {
                System.out.println(
                        "[OldPoint] fixed outdoor 0.6.144: buildings="
                                + buildings.size()
                                + ", walls="
                                + walls.size()
                                + ", floor="
                                + floor);
            }
            cached = new Cached(cell, player, square, floor, player.isAiming(), time, result);
            return result;
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] native outdoor cutaway unavailable: " + error);
            }
            // Avoid repeating a failed reflective path every frame.
            var result = new Selection(List.of(), List.of(), List.of());
            cached = new Cached(cell, player, square, floor, player.isAiming(), time, result);
            return result;
        } finally {
            nativeCut.cell = oldCell;
            cell.occludedByOrphanStructureFlag = oldOrphan;
            state.playerIndex = oldIndex;
            state.camCharacterX = oldX;
            state.camCharacterY = oldY;
            state.camCharacterZ = oldZ;
            state.camCharacter = oldCharacter;
            state.camCharacterSquare = oldSquare;
            state.camCharacterRoom = oldRoom;
        }
    }

    private static void init() throws ReflectiveOperationException {
        if (isCutaway != null) {
            return;
        }
        var type = CutawayWall.class;
        level = type.getDeclaredField("chunkLevelData");
        level.setAccessible(true);
        seenMasks = type.getDeclaredField("occludedSquaresMaskForSeenRooms");
        seenMasks.setAccessible(true);
        calculateRooms = type.getDeclaredMethod("calculateOccludedRooms");
        calculateRooms.setAccessible(true);
        calculateSeen = type.getDeclaredMethod("calculateOccludedSquaresMaskForSeenRooms", int.class);
        calculateSeen.setAccessible(true);
        var method =
                FBORenderCutaways.class.getDeclaredMethod(
                        "IsCutawaySquare", type, IsoGridSquare.class, IsoGridSquare.class, long.class);
        method.setAccessible(true);
        isCutaway = method;
    }
}
