package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import oldpoint.viewpoint.OcclusionSnapshot.Fading;
import oldpoint.viewpoint.OcclusionSnapshot.Wall;
import viewpoint.core.Frame;
import viewpoint.render.SceneData;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Room ceilings and individually detected obstruction walls. */
public final class Interior {
    private static final float STOREY = 2.4494896f;
    private static final int MAX_ROOMS = OcclusionRules.MAX_ROOMS, MAX_WALLS = OcclusionRules.MAX_WALLS;

    private static final Map<SceneData, OcclusionSnapshot> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Wall, Float> fades = new HashMap<>();
    private static volatile OcclusionSnapshot latest;
    private static long previousTime;
    private static boolean reported;
    private static int reportedRoom;

    private record Contour(Object cell, int floor, List<Layout.Rect> rooms, long time, List<Wall> walls) {}

    private static Contour contour;

    private Interior() {}

    public static void reset(SceneData scene) {
        snapshots.remove(scene);
        RoofModels.reset(scene);
    }

    public static void clear() {
        snapshots.clear();
        fades.clear();
        latest = null;
        previousTime = 0;
        contour = null;
        NativeCutaway.clear();
        NativeRooms.clear();
        OutdoorCutaway.clear();
        FreeOutdoorCutaway.clear();
        RoofModels.clear();
        OpeningFade.clear();
        RoofEdges.clear();
        RoomWallOcclusion.clear();
        RoomBoundary.clear();
        BuiltUpperFloors.clear();
        VehiclePicking.clear();
    }

    public static void begin(SceneData scene) {
        OcclusionRendering.begin(snapshots.get(scene));
    }

    public static void end() {
        OcclusionRendering.end();
    }

    public static void capture(
            Frame frame, IsoGameCharacter player, float ex, float ey, float ez, float dx, float dy, float dz) {
        CaptureScan.startFrame();
        try {
            long costStart = System.nanoTime();
            ArrayList<Layout.Rect> rooms = new ArrayList<>();
            if (player.getCurrentSquare() != null && player.getCurrentSquare().getRoom() != null) {
                RoomDef def = player.getCurrentSquare().getRoom().getRoomDef();
                if (def != null) {
                    for (RoomDef.RoomRect r : def.getRects()) {
                        rooms.add(new Layout.Rect(r.x, r.y, r.w, r.h));
                    }
                }
            }
            List<Layout.Rect> occupiedRoom = List.copyOf(rooms);
            NativeRooms.Selection visibleRooms =
                    player instanceof zombie.characters.IsoPlayer local
                            ? NativeRooms.capture(local, occupiedRoom)
                            : new NativeRooms.Selection(occupiedRoom, List.of());
            List<Layout.Rect> targets = visibleRooms.targets();
            int floor = (int) Math.floor(player.getZ());
            ArrayList<Layout.Rect> upper = new ArrayList<>(rooms);
            HashSet<Long> nativeSquares = new HashSet<>();
            boolean nativeMode = false;
            if (player instanceof zombie.characters.IsoPlayer local
                    && player.getCurrentSquare() != null
                    && (!occupiedRoom.isEmpty() || CameraController.nativeFixedView())) {
                try (NativeCutaway nativeCut = new NativeCutaway(IsoWorld.instance.getCell(), local)) {
                    nativeCut.prepare(local);
                    int nx = (int) Math.floor(player.getX()), ny = (int) Math.floor(player.getY());
                    long now = System.currentTimeMillis();
                    if (CameraController.nativeFixedView()) {
                        // Outdoors the cached, directional native adapter below
                        // replaces this legacy per-frame square scan.
                        if (!occupiedRoom.isEmpty()) {
                            for (int y = ny - 12; y <= ny + 12; y++) {
                                for (int x = nx - 12; x <= nx + 12; x++) {
                                    var square = IsoWorld.instance.getCell().getGridSquare(x, y, floor);
                                    if (square != null && nativeCut.cuts(square, now)) {
                                        nativeSquares.add(tileKey(x, y));
                                    }
                                }
                            }
                        }
                        rooms.clear();
                        rooms.addAll(nativeCut.rooms);
                        nativeMode = true;
                    }
                    upper.clear();
                    upper.addAll(nativeCut.upper);
                    // Roof rooms without a conventional building still use their room footprint.
                    if (!nativeMode) {
                        upper.addAll(nativeCut.rooms);
                    }
                    upper.addAll(rooms);
                } catch (RuntimeException error) {
                    report(error);
                }
            }
            Layout.Sight sight =
                    new Layout.Sight(
                            player.getX(),
                            player.getY(),
                            player.getZ() * STOREY + 0.8f,
                            (float) (-ex - frame.scene.originX),
                            (float) (-ez - frame.scene.originZ),
                            (float) (ey + frame.scene.originY),
                            floor);
            OutdoorCutaway.Selection outdoor =
                    occupiedRoom.isEmpty()
                                    && player.getCurrentSquare() != null
                                    && player instanceof zombie.characters.IsoPlayer local
                            ? (CameraController.nativeFixedView()
                                    ? OutdoorCutaway.capture(local)
                                    : FreeOutdoorCutaway.capture(local, sight))
                            : null;
            if (outdoor != null) {
                upper.clear();
                upper.addAll(outdoor.upper());
            }
            int foreignUpperCount = 0;
            if (!occupiedRoom.isEmpty() && player instanceof zombie.characters.IsoPlayer local) {
                // The original isometric candidates cannot track a rotated camera.
                // Reuse cached local building prisms indoors, without scanning walls.
                var neighbours = FreeOutdoorCutaway.captureUpper(local, sight);
                var own = player.getCurrentSquare().getBuilding();
                ArrayList<Layout.Rect> selected = new ArrayList<>();
                for (var b : neighbours.buildings()) {
                    if (own != null && b == own.getDef()) {
                        continue;
                    }
                    var rect = new Layout.Rect(b.getX(), b.getY(), b.getW(), b.getH());
                    if (!selected.contains(rect)) {
                        selected.add(rect);
                    }
                }
                // Prioritize real camera obstructions within the existing GPU limit.
                foreignUpperCount = Math.min(MAX_ROOMS, selected.size());
                for (var rect : upper) {
                    if (!selected.contains(rect)) {
                        selected.add(rect);
                    }
                }
                upper = selected;
            }
            long costNative = System.nanoTime();
            var foreign = RoomWallOcclusion.capture(player, targets, sight);
            var boundary = RoomBoundary.capture(targets);
            HashSet<Wall> blocked = new HashSet<>();
            // Enumerate the real contour, including short concave runs and boundaries
            // outside the local foreign-wall scan. Rectangle seams are absent.
            for (var wall : contourWalls(targets, boundary, floor)) {
                if (roomFace(targets, wall.x(), wall.y(), wall.kind(), wall.kind() == 1 ? sight.cy() : sight.cx())
                        > 0) {
                    blocked.add(wall);
                }
            }
            int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
            // Indoor selection is already complete: actual contour + cached foreign
            // relations. The legacy 21x21 targets and 25x25 scan were unused here.
            if (outdoor != null) {
                ArrayList<OutdoorCutaway.Wall> visibleWalls = new ArrayList<>();
                for (var wall : outdoor.walls()) {
                    // The surrounding protection samples may cross a nearby wall.
                    // Only planes actually between the character and eye can hide.
                    float plane = wall.kind() == 1 ? wall.y() : wall.x();
                    float axis = wall.kind() == 1 ? player.getY() : player.getX();
                    float eyeAxis = wall.kind() == 1 ? sight.cy() : sight.cx();
                    if ((plane - axis) * Math.signum(eyeAxis - axis) <= 0.06f) {
                        continue;
                    }
                    blocked.add(new Wall(wall.x(), wall.y(), floor, wall.kind()));
                    visibleWalls.add(wall);
                }
                outdoor = new OutdoorCutaway.Selection(outdoor.buildings(), outdoor.upper(), List.copyOf(visibleWalls));
            } else if (occupiedRoom.isEmpty()) {
                ArrayList<Layout.Sight> roomSight = new ArrayList<>();
                // Sample all locally selected rooms, including native adjacent rooms.
                // This world-space extent does not grow when the camera zooms out.
                for (int y = cy - 10; y <= cy + 10; y++) {
                    for (int x = cx - 10; x <= cx + 10; x++) {
                        if (!Layout.contains(occupiedRoom.isEmpty() ? rooms : occupiedRoom, x, y)) {
                            continue;
                        }
                        var target = IsoWorld.instance.getCell().getGridSquare(x, y, floor);
                        if (target == null || target.getRoom() == null) {
                            continue;
                        }
                        roomSight.add(
                                new Layout.Sight(
                                        x + 0.5f,
                                        y + 0.5f,
                                        floor * STOREY + 0.8f,
                                        sight.cx(),
                                        sight.cy(),
                                        sight.cup(),
                                        floor));
                    }
                }
                int radius = roomSight.isEmpty() ? 6 : 12;
                for (int y = cy - radius; y <= cy + radius; y++) {
                    for (int x = cx - radius; x <= cx + radius; x++) {
                        var square = IsoWorld.instance.getCell().getGridSquare(x, y, floor);
                        if (square == null) {
                            continue;
                        }
                        for (int kind = 1; kind <= 2; kind++) {
                            // Only wall properties: furniture, doors and floor contents are not
                            // candidates.
                            boolean wall =
                                    square.getProperties().has(kind == 1 ? "WallN" : "WallW")
                                            || square.getProperties()
                                                    .has(kind == 1 ? IsoFlagType.WallN : IsoFlagType.WallW)
                                            || square.getProperties().has(kind == 1 ? "WallNW" : "WallSE")
                                            || square.getProperties()
                                                    .has(
                                                            kind == 1
                                                                    ? zombie.iso.SpriteDetails.IsoFlagType.cutN
                                                                    : zombie.iso.SpriteDetails.IsoFlagType.cutW);
                            if (!wall) {
                                continue;
                            }
                            if (nativeMode) {
                                // Sampling adjacent rooms must not cut through their shared
                                // wall behind the player. The wall plane must lie toward
                                // the camera along its own normal axis. A center-point dot
                                // test incorrectly treats diagonal/long rear walls as front.
                                float plane = kind == 1 ? y : x;
                                float playerAxis = kind == 1 ? player.getY() : player.getX();
                                float cameraAxis = kind == 1 ? sight.cy() : sight.cx();
                                float toward = cameraAxis - playerAxis;
                                if (Math.abs(toward) < 0.0001f || (plane - playerAxis) * Math.signum(toward) < -0.02f) {
                                    continue;
                                }
                            }
                            // North/west walls belong to one tile but bound two tiles.
                            // An exterior tile can own a wall of a selected indoor room.
                            int otherX = x - (kind == 2 ? 1 : 0), otherY = y - (kind == 1 ? 1 : 0);
                            boolean roomBoundary =
                                    Layout.contains(rooms, x, y) || Layout.contains(rooms, otherX, otherY);
                            if (nativeMode
                                    && !nativeSquares.contains(tileKey(x, y))
                                    && !nativeSquares.contains(tileKey(otherX, otherY))
                                    && !roomBoundary) {
                                continue;
                            }
                            int chunkX = Math.floorDiv(x, 8), chunkY = Math.floorDiv(y, 8);
                            int owner = (Math.floorMod(y, 8) * 8 + Math.floorMod(x, 8)) * 4 + kind;
                            boolean obscures = sight.hidesOwner(owner, floor, chunkX, chunkY);
                            if (!obscures) {
                                for (Layout.Sight target : roomSight) {
                                    if (target.hidesOwner(owner, floor, chunkX, chunkY, 12f)) {
                                        obscures = true;
                                        break;
                                    }
                                }
                            }
                            if (obscures) {
                                blocked.add(new Wall(x, y, floor, kind));
                            }
                        }
                    }
                }
            }
            for (var wall : foreign.hidden()) {
                blocked.add(new Wall(wall.x(), wall.y(), wall.floor(), wall.kind()));
            }
            long now = System.nanoTime();
            float step = previousTime == 0 ? 0 : Math.min(0.1f, (now - previousTime) / 1_000_000_000f) / 0.22f;
            previousTime = now;
            for (Wall wall : blocked) {
                fades.putIfAbsent(wall, 0f);
            }
            fades.replaceAll(
                    (wall, value) -> Math.max(0, Math.min(1, value + (blocked.contains(wall) ? step : -step))));
            fades.entrySet().removeIf(entry -> entry.getValue() <= 0 && !blocked.contains(entry.getKey()));
            ArrayList<Fading> walls = new ArrayList<>();
            for (var entry : fades.entrySet()) {
                walls.add(new Fading(entry.getKey(), entry.getValue()));
            }
            java.util.Comparator<Fading> roomFacingFirst =
                    java.util.Comparator.comparingInt(
                            fading -> {
                                Wall wall = fading.wall();
                                float cameraAxis = wall.kind() == 1 ? sight.cy() : sight.cx();
                                int face = roomFace(occupiedRoom, wall.x(), wall.y(), wall.kind(), cameraAxis);
                                return face > 0 ? 0 : 1;
                            });
            walls.sort(
                    roomFacingFirst.thenComparingDouble(
                            fading -> {
                                Wall wall = fading.wall();
                                return Math.pow(wall.x() - player.getX(), 2) + Math.pow(wall.y() - player.getY(), 2);
                            }));
            if (walls.size() > MAX_WALLS) {
                walls.subList(MAX_WALLS, walls.size()).clear();
            }
            long costSelection = System.nanoTime();
            var openings =
                    occupiedRoom.isEmpty() && outdoor == null
                            ? new OpeningFade.Capture(List.of(), Map.of())
                            : OpeningFade.capture(player, outdoor == null ? targets : upper, sight, outdoor, blocked);
            long costOpenings = System.nanoTime();
            if (occupiedRoom.isEmpty() && outdoor == null) {
                upper.clear();
            }
            // Self-built rooms may have no map RoomDef/BuildingDef at all.
            upper.addAll(BuiltUpperFloors.capture(player));
            int roofTop = floor + 1;
            for (int i = 0; i < frame.scene.meshCount; i++) {
                roofTop = Math.max(roofTop, frame.scene.meshes[i].level);
            }
            OcclusionSnapshot state =
                    new OcclusionSnapshot(
                            frame.scene.originX,
                            frame.scene.originY,
                            frame.scene.originZ,
                            List.copyOf(rooms),
                            List.copyOf(upper),
                            nativeMode,
                            outdoor != null,
                            List.copyOf(walls),
                            RoofEdges.capture(player, upper, roofTop, outdoor),
                            boundary,
                            openings,
                            TreeClearing.selection(frame.scene),
                            visibleRooms.shades(),
                            floor,
                            foreignUpperCount,
                            (float) (-player.getX() - frame.scene.originX),
                            (float) (player.getZ() * STOREY + 0.8f - frame.scene.originY),
                            (float) (-player.getY() - frame.scene.originZ),
                            dx,
                            dy,
                            dz,
                            ex,
                            ey,
                            ez,
                            NearbyFade.area(player.getX(), player.getY(), sight.cx(), sight.cy()));
            snapshots.put(frame.scene, state);
            latest = state;
            int roomKey = occupiedRoom.hashCode() * 31 + floor;
            if (!occupiedRoom.isEmpty() && reportedRoom != roomKey) {
                reportedRoom = roomKey;
                System.out.println(
                        "[OldPoint] cutaway 0.6.144: room="
                                + occupiedRoom
                                + ", floor="
                                + floor
                                + ", boundary runs="
                                + boundary.size()
                                + ", walls="
                                + walls.size()
                                + ", roof art="
                                + state.roofs().size()
                                + ", near openings="
                                + openings.instances().size()
                                + ", foreign wall segments="
                                + foreign.hidden().size()
                                + ", eye="
                                + (ex + frame.scene.originX)
                                + ","
                                + (ey + frame.scene.originY)
                                + ","
                                + (ez + frame.scene.originZ));
            }
            RoofModels.capture(frame, player, upper, outdoor);
            InteriorCost.record(
                    !occupiedRoom.isEmpty(), costStart, costNative, costSelection, costOpenings, System.nanoTime());
        } finally {
            CaptureScan.endFrame();
        }
    }

    private static long tileKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static List<Wall> contourWalls(List<Layout.Rect> rooms, List<RoomBoundary.Run> boundary, int floor) {
        Object cell = IsoWorld.instance.getCell();
        long now = System.nanoTime();
        Contour old = contour;
        if (old != null
                && old.cell == cell
                && old.floor == floor
                && old.rooms.equals(rooms)
                && now - old.time < 150_000_000L) {
            return old.walls;
        }
        ArrayList<Wall> walls = new ArrayList<>();
        for (var run : boundary) {
            for (int offset = 0; offset < run.length(); offset++) {
                int x = run.x() + (run.kind() == 1 ? offset : 0), y = run.y() + (run.kind() == 2 ? offset : 0);
                if (wallAt(x, y, floor, run.kind())) {
                    walls.add(new Wall(x, y, floor, run.kind()));
                }
            }
        }
        contour = new Contour(cell, floor, List.copyOf(rooms), now, List.copyOf(walls));
        return contour.walls;
    }

    /** +1 camera-side room boundary, -1 far boundary, 0 not a boundary. */
    static int roomFace(List<Layout.Rect> rooms, int x, int y, int kind, float cameraAxis) {
        boolean inside = Layout.contains(rooms, x, y);
        boolean across = Layout.contains(rooms, x - (kind == 2 ? 1 : 0), y - (kind == 1 ? 1 : 0));
        if (inside == across) {
            return 0;
        }
        float plane = kind == 1 ? y : x;
        // An owner tile lies on the positive side of a north/west wall plane.
        float interiorSide = inside ? 1f : -1f;
        return (cameraAxis - plane) * interiorSide < 0 ? 1 : -1;
    }

    private static boolean wallAt(int x, int y, int floor, int kind) {
        var square = IsoWorld.instance.getCell().getGridSquare(x, y, floor);
        if (square == null) {
            return false;
        }
        var props = square.getProperties();
        return props.has(kind == 1 ? "WallN" : "WallW")
                || props.has(kind == 1 ? "WallNW" : "WallSE")
                || props.has(kind == 1 ? IsoFlagType.WallN : IsoFlagType.WallW)
                || props.has(
                        kind == 1
                                ? zombie.iso.SpriteDetails.IsoFlagType.cutN
                                : zombie.iso.SpriteDetails.IsoFlagType.cutW);
    }

    public static void roomCulling() {
        ViewpointVisibilityAdapter.roomCulling();
    }

    public static boolean hideModel(float x, float y, float x2, float y2, int floor) {
        OcclusionSnapshot state = latest;
        if (!CameraController.topDownActive() || state == null || floor <= state.floor()) {
            return false;
        }
        if (state.floor() < 0) {
            return true;
        }
        // Both modes use the same selected building/room footprints and eave margin.
        float edge = 0.75f;
        for (Layout.Rect r : state.upperFloors()) {
            if (x >= r.x() - edge
                    && y >= r.y() - edge
                    && x2 <= r.x() + r.width() + edge
                    && y2 <= r.y() + r.height() + edge) {
                return true;
            }
        }
        return false;
    }

    public static boolean hidePoint(SceneData scene, float x, float y, int floor) {
        OcclusionSnapshot state = scene == null ? latest : snapshots.get(scene);
        if (!CameraController.topDownActive() || state == null || floor <= state.floor()) {
            return false;
        }
        if (state.floor() < 0) {
            return true;
        }
        for (Layout.Rect r : state.upperFloors()) {
            if (x >= r.x() - 0.75f
                    && y >= r.y() - 0.75f
                    && x < r.x() + r.width() + 0.75f
                    && y < r.y() + r.height() + 0.75f) {
                return true;
            }
        }
        return false;
    }

    static boolean nearOpening(SceneData scene, int x, int y, int floor, int kind) {
        var s = snapshots.get(scene);
        if (s == null || floor != s.floor()) {
            return false;
        }
        if (s.outdoor()) {
            for (var f : s.walls()) {
                if (f.wall().x() == x && f.wall().y() == y && f.wall().kind() == kind && f.amount() > 0) {
                    return true;
                }
            }
            return false;
        }
        float cameraAxis = kind == 1 ? (float) (-s.eyeZ() - s.originZ()) : (float) (-s.eyeX() - s.originX());
        for (var r : s.boundary()) {
            if (r.kind() != kind) {
                continue;
            }
            boolean matches =
                    kind == 1
                            ? y == r.y() && x >= r.x() && x < r.x() + r.length()
                            : x == r.x() && y >= r.y() && y < r.y() + r.length();
            if (matches) {
                return r.face(cameraAxis) > 0;
            }
        }
        return false;
    }

    static boolean selectedWall(SceneData scene, int x, int y, int floor, int kind) {
        var s = snapshots.get(scene);
        if (s == null || floor != s.floor()) {
            return false;
        }
        for (var f : s.walls()) {
            if (f.wall().x() == x && f.wall().y() == y && f.wall().kind() == kind && f.amount() > 0) {
                return true;
            }
        }
        return false;
    }

    static float nearbyObjectFade(SceneData scene, zombie.iso.IsoGridSquare square, zombie.iso.IsoObject object) {
        var s = snapshots.get(scene);
        if (s == null
                || !s.outdoor()
                || square.getZ() != s.floor()
                || !NearbyFade.eligible(object, object == null ? null : object.getSprite())) {
            return 0;
        }
        return s.nearbyArea().contains(square.getX() + 0.5f, square.getY() + 0.5f) ? NearbyFade.amount(object) : 0;
    }

    static float furnitureObjectFade(SceneData scene, zombie.iso.IsoObject object) {
        var s = snapshots.get(scene);
        return s == null || object == null ? 0 : s.openings().furniture().getOrDefault(object, 0f);
    }

    public static void uniforms(Object value) {
        OcclusionRendering.uniforms(value);
    }

    public static void irisUniforms(Object value, Object context, boolean shadow) {
        OcclusionRendering.irisUniforms(value, context, shadow);
    }

    public static String shader(String path, String source) {
        return OcclusionShaders.shader(path, source);
    }

    static OcclusionSnapshot snapshot(SceneData scene) {
        return snapshots.get(scene);
    }

    static void report(Throwable error) {
        if (reported) {
            return;
        }
        reported = true;
        System.out.println("[OldPoint] interior rendering error: " + error);
        error.printStackTrace(System.out);
    }
}
