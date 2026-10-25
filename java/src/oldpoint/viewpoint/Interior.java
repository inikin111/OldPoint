package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Room ceilings and individually detected obstruction walls. */
public final class Interior {
    private static final float STOREY = 2.4494896f;
    private static final int MAX_ROOMS = 32, MAX_WALLS = 128;

    record Wall(int x, int y, int floor, int kind) {}

    private record Fading(Wall wall, float amount) {}

    private record State(
            double ox,
            double oy,
            double oz,
            List<Layout.Rect> rooms,
            List<Layout.Rect> upper,
            boolean nativeMode,
            boolean outdoor,
            List<Fading> walls,
            List<RoofEdges.Bounds> roofs,
            List<RoomBoundary.Run> boundary,
            OpeningFade.Capture openings,
            TreeClearing.Selection trees,
            List<NativeRooms.Shade> roomShades,
            int floor,
            int foreignUpperCount,
            float px,
            float py,
            float pz,
            float dx,
            float dy,
            float dz,
            float ex,
            float ey,
            float ez,
            NearbyFade.Area nearby) {}

    private static final Map<SceneData, State> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Wall, Float> fades = new HashMap<>();
    private static final ThreadLocal<State> drawing = new ThreadLocal<>();
    private static volatile State latest;
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
        drawing.set(snapshots.get(scene));
    }

    public static void end() {
        CutawayData.end();
        drawing.remove();
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
                    && (!occupiedRoom.isEmpty() || Bridge.nativeFixedView())) {
                try (NativeCutaway nativeCut = new NativeCutaway(IsoWorld.instance.getCell(), local)) {
                    nativeCut.prepare(local);
                    int nx = (int) Math.floor(player.getX()), ny = (int) Math.floor(player.getY());
                    long now = System.currentTimeMillis();
                    if (Bridge.nativeFixedView()) {
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
                            ? (Bridge.nativeFixedView()
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
                if (roomFace(targets, wall.x, wall.y, wall.kind, wall.kind == 1 ? sight.cy() : sight.cx()) > 0) {
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
            walls.sort(
                    java.util.Comparator.<Fading>comparingInt(
                                    f ->
                                            roomFace(
                                                                    occupiedRoom,
                                                                    f.wall.x,
                                                                    f.wall.y,
                                                                    f.wall.kind,
                                                                    f.wall.kind == 1 ? sight.cy() : sight.cx())
                                                            > 0
                                                    ? 0
                                                    : 1)
                            .thenComparingDouble(
                                    f ->
                                            Math.pow(f.wall.x - player.getX(), 2)
                                                    + Math.pow(f.wall.y - player.getY(), 2)));
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
            State state =
                    new State(
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
                                + state.roofs.size()
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
        if (!Bridge.topDownActive()) {
            return;
        }
        try {
            // Portal culling is designed for an eye inside a room. Anchoring that
            // eye to the player while drawing an overhead exterior view omits
            // interior walls but leaves their doors/models visible. OldPoint's
            // local structural cutaway now owns indoor visibility in this mode.
            field(Class.forName("viewpoint.visibility.Rooms"), "hiding").setBoolean(null, false);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    public static boolean hideModel(float x, float y, float x2, float y2, int floor) {
        State state = latest;
        if (!Bridge.topDownActive() || state == null || floor <= state.floor) {
            return false;
        }
        if (state.floor < 0) {
            return true;
        }
        // Both modes use the same selected building/room footprints and eave margin.
        float edge = 0.75f;
        for (Layout.Rect r : state.upper) {
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
        State state = scene == null ? latest : snapshots.get(scene);
        if (!Bridge.topDownActive() || state == null || floor <= state.floor) {
            return false;
        }
        if (state.floor < 0) {
            return true;
        }
        for (Layout.Rect r : state.upper) {
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
        if (s == null || floor != s.floor) {
            return false;
        }
        if (s.outdoor) {
            for (var f : s.walls) {
                if (f.wall.x == x && f.wall.y == y && f.wall.kind == kind && f.amount > 0) {
                    return true;
                }
            }
            return false;
        }
        float cameraAxis = kind == 1 ? (float) (-s.ez - s.oz) : (float) (-s.ex - s.ox);
        for (var r : s.boundary) {
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
        if (s == null || floor != s.floor) {
            return false;
        }
        for (var f : s.walls) {
            if (f.wall.x == x && f.wall.y == y && f.wall.kind == kind && f.amount > 0) {
                return true;
            }
        }
        return false;
    }

    static float nearbyObjectFade(SceneData scene, zombie.iso.IsoGridSquare square, zombie.iso.IsoObject object) {
        var s = snapshots.get(scene);
        if (s == null
                || !s.outdoor
                || square.getZ() != s.floor
                || !NearbyFade.eligible(object, object == null ? null : object.getSprite())) {
            return 0;
        }
        return s.nearby.contains(square.getX() + 0.5f, square.getY() + 0.5f) ? NearbyFade.amount(object) : 0;
    }

    static float furnitureObjectFade(SceneData scene, zombie.iso.IsoObject object) {
        var s = snapshots.get(scene);
        return s == null || object == null ? 0 : s.openings.furniture().getOrDefault(object, 0f);
    }

    public static void uniforms(Object value) {
        GlProgram program = (GlProgram) value;
        OpeningFade.program(program);
        if (program.has("uOPNoCameraClear")) {
            program.setInt("uOPNoCameraClear", drawing.get() != null && Bridge.drawTopDown() ? 1 : 0);
        }
        if (!program.has("uOPInterior") && !program.has("uOPTreeEnabled")) {
            return;
        }
        upload(program, drawing.get(), false);
    }

    public static void irisUniforms(Object value, Object context, boolean shadow) {
        try {
            GlProgram program = (GlProgram) field(value.getClass(), "gl").get(value);
            FrameContext frame = (FrameContext) context;
            if (program.has("uOPNoCameraClear")) {
                program.setInt(
                        "uOPNoCameraClear",
                        snapshots.get(frame.scene) != null && Bridge.drawTopDown() && !shadow ? 1 : 0);
            }
            if (program.has("uOPKeepNearGeometry")) {
                program.setInt(
                        "uOPKeepNearGeometry",
                        snapshots.get(frame.scene) != null && Bridge.drawTopDown() && !shadow ? 1 : 0);
            }
            if (program.has("uOPInterior") || program.has("uOPTreeEnabled")) {
                upload(program, snapshots.get(frame.scene), shadow);
            }
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    private static void upload(GlProgram p, State s, boolean shadow) {
        boolean enabled = s != null && Bridge.drawTopDown() && !shadow;
        if (p.has("uOPInterior")) {
            p.setInt("uOPInterior", enabled ? 1 : 0);
        }
        if (p.has("uOPTreeEnabled")) {
            p.setInt("uOPTreeEnabled", enabled && TreeClearing.active() && s.trees.cells().length > 0 ? 1 : 0);
        }
        if (!enabled) {
            CutawayData.bind(p);
            return;
        }
        p.set("uOPOrigin", (float) s.ox, (float) s.oy, (float) s.oz);
        p.set("uOPFloor", (s.floor + 1) * STOREY);
        p.set("uOPPlayer", s.px, s.py, s.pz);
        p.set("uOPDirection", s.dx, s.dy, s.dz);
        p.set("uOPEye", s.ex, s.ey, s.ez);
        p.setInt("uOPBoundaryCount", s.boundary.size());
        p.setInt("uOPRoomCount", Math.min(MAX_ROOMS, s.upper.size()));
        p.setInt("uOPForeignUpperCount", s.foreignUpperCount);
        p.setInt("uOPRoomShadeCount", s.roomShades.size());
        if (packedState != s) {
            if (!sameRules(packedState, s)) {
                packedRules = packRules(s);
            }
            packedState = s;
        }
        CutawayData.upload(s, packedRules);
        CutawayData.bind(p);
        p.setInt("uOPOccupiedOffset", packedRules.occupied());
        p.setInt("uOPRoomOffset", packedRules.rooms());
        p.setInt("uOPWallOffset", packedRules.walls());
        p.setInt("uOPRoofOffset", packedRules.roofs());
        p.setInt("uOPWallCount", packedRules.wallCount());
        p.setInt("uOPStructureWallCount", Math.min(s.walls.size(), MAX_WALLS));
        p.setInt("uOPSpatialOffset", packedRules.spatial());
        int grid = packedRules.spatial() * 4;
        float[] data = packedRules.values();
        p.set("uOPSpatialGrid", data[grid], data[grid + 1], data[grid + 2], data[grid + 3]);
        p.setInt("uOPOpeningLookup", packedRules.lookup());
        p.setInt("uOPRoomShadeOffset", packedRules.shades());
        if (p.has("uOPTreeEnabled")) {
            p.setInt("uOPTreeOffset", packedRules.trees());
            p.set("uOPTreeGrid", s.trees.x(), s.trees.y(), s.trees.width(), s.trees.height());
            p.set("uOPTreeFloor", s.trees.floor() * STOREY);
            p.set("uOPTreeOrigin", (float) s.ox, (float) s.oy, (float) s.oz);
        }
    }

    private static State packedState;
    private static CutawayData.Data packedRules;

    private static boolean sameRules(State a, State b) {
        if (a == null || packedRules == null) {
            return false;
        }
        var x = a.trees;
        var y = b.trees;
        return a.upper.equals(b.upper)
                && a.boundary.equals(b.boundary)
                && a.walls.equals(b.walls)
                && a.roofs.equals(b.roofs)
                && a.openings.instances().equals(b.openings.instances())
                && a.roomShades.equals(b.roomShades)
                && x.x() == y.x()
                && x.y() == y.y()
                && x.width() == y.width()
                && x.height() == y.height()
                && x.floor() == y.floor()
                && java.util.Arrays.equals(x.cells(), y.cells());
    }

    private static void put(java.nio.FloatBuffer out, float x, float y, float z, float w) {
        out.put(x).put(y).put(z).put(w);
    }

    private static CutawayData.Data packRules(State s) {
        int rooms = Math.min(MAX_ROOMS, s.upper.size());
        int walls = Math.min(s.walls.size(), MAX_WALLS);
        int totalWalls = walls + s.roofs.size() * 2;
        SpatialRules index = new SpatialRules();
        for (int i = 0; i < walls; i++) {
            var w = s.walls.get(i).wall;
            index.wall(i, w.x, w.y, w.kind);
        }
        for (int i = 0; i < s.roofs.size(); i++) {
            var r = s.roofs.get(i);
            index.roof(i, r.x(), r.y(), r.x2(), r.y2());
        }
        float[] spatial = index.pack();
        int rows =
                s.openings.instances().size() * 4
                        + s.boundary.size()
                        + rooms
                        + totalWalls * 2
                        + s.roofs.size()
                        + OpeningLookup.rows(s.openings.instances().size())
                        + (s.trees.cells().length + 3) / 4
                        + spatial.length / 4
                        + s.roomShades.size() * 2;
        float[] values = new float[Math.max(4, rows * 4)];
        var out = java.nio.FloatBuffer.wrap(values);
        for (var o : s.openings.instances()) {
            var b = o.bounds();
            var a = o.art();
            put(out, o.x(), o.y(), o.up(), o.model());
            put(out, b.x(), b.y(), b.z(), o.fade());
            put(out, b.x2(), b.y2(), b.z2(), 0);
            put(out, a.x(), a.y(), a.z(), a.w());
        }
        int occupiedOffset = out.position() / 4;
        for (var r : s.boundary) {
            put(out, r.x(), r.y(), r.length(), r.kind() * r.side());
        }
        int roomOffset = out.position() / 4;
        for (int i = 0; i < rooms; i++) {
            var r = s.upper.get(i);
            put(out, r.x() - 0.75f, r.y() - 0.75f, r.x() + r.width() + 0.75f, r.y() + r.height() + 0.75f);
        }
        int wallOffset = out.position() / 4;
        for (int i = 0; i < walls; i++) {
            var f = s.walls.get(i);
            var w = f.wall;
            put(out, w.x, w.y, w.floor * STOREY, w.kind);
            put(out, f.amount, 0, 0, 0);
        }
        for (var r : s.roofs) {
            put(out, r.x(), r.y(), r.up(), 3);
            put(out, r.atlas(), 0, 0, 0);
            put(out, r.x2(), r.y2(), r.up2(), 4);
            put(out, 0, 0, 0, 0);
        }
        int roofOffset = out.position() / 4;
        for (var r : s.roofs) {
            put(out, r.r0(), r.r1(), r.r2(), r.r3());
        }
        int lookupOffset = out.position() / 4;
        OpeningLookup.write(out, s.openings.instances());
        int treeOffset = out.position() / 4;
        out.put(s.trees.cells());
        while (out.position() % 4 != 0) {
            out.put(0);
        }
        int spatialOffset = out.position() / 4;
        out.put(spatial);
        int shadeOffset = out.position() / 4;
        for (var shade : s.roomShades) {
            var r = shade.rect();
            put(out, r.x(), r.y(), r.x() + r.width(), r.y() + r.height());
            put(out, shade.ratio(), 0, 0, 0);
        }
        return new CutawayData.Data(
                values,
                occupiedOffset,
                roomOffset,
                wallOffset,
                roofOffset,
                totalWalls,
                lookupOffset,
                treeOffset,
                spatialOffset,
                shadeOffset);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void report(Throwable error) {
        if (reported) {
            return;
        }
        reported = true;
        System.out.println("[OldPoint] interior rendering error: " + error);
        error.printStackTrace(System.out);
    }

    private static final String TERRAIN =
            CutawayData.SHADER
                    + """
        flat in float vOPSourceBase;
        flat in int vOPGeneratedRoof;
        flat in int vOPObjectKind;
        uniform int uOPAtlasIdentity;
        in vec3 vOPStructure;
        in vec3 vOPLift;
        flat in int vOPCanopy;
        flat in vec3 vOPPlantBase;
        uniform int uOPInterior;
        uniform vec3 uOPOrigin;
        uniform float uOPFloor;
        uniform int uOPForeignUpperCount;
        uniform int uOPRoomShadeCount;
        uniform int uOPRoomShadeOffset;
        float oldPointRoomLight() {
          if (uOPInterior==0 || uOPRoomShadeCount==0) return 1.0;
          vec3 world=vOPStructure+uOPOrigin;
          if (world.y<uOPFloor-2.4494896-0.05 || world.y>=uOPFloor-0.015) return 1.0;
          vec2 tile=-world.xz;
          for (int i=0;i<uOPRoomShadeCount;i++) {
            vec4 r=texelFetch(uOPData,uOPRoomShadeOffset+i*2);
            if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw)))
              return texelFetch(uOPData,uOPRoomShadeOffset+i*2+1).x;
          }
          return 1.0;
        }
        uniform vec3 uOPPlayer;
        uniform vec3 uOPDirection;
        uniform vec3 uOPEye;
        uniform int uOPRoomCount;
        uniform int uOPWallCount;
        uniform int uOPStructureWallCount;
        uniform int uOPBoundaryCount;
        float oldPointWallNoise(vec3 world) {
          ivec3 cell=ivec3(floor(world*24.0));
          uint hash=uint(cell.x)*73856093u ^ uint(cell.y)*19349663u ^ uint(cell.z)*83492791u;
          hash^=hash>>13;
          hash*=1274126177u;
          return float(hash&0x00ffffffu)*(1.0/16777216.0);
        }
        bool oldPointRoom(vec2 tile) {
          vec2 playerTile=-(uOPPlayer+uOPOrigin).xz;
          // Bound large rooms by a local exploration area, independent of zoom.
          if (any(greaterThan(abs(tile-playerTile),vec2(10.75)))) return false;
          for (int i=0;i<uOPRoomCount;i++) {
            vec4 r=opRoom(i);
            if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) return true;
          }
          return false;
        }
        uniform vec3 uOPViewRight;
        uniform vec3 uOPViewUp;
        uniform vec3 uOPViewForward;
        int oldPointOwnFace(vec2 tile) {
          if (abs(vNormal.y)>0.5) return 0;
          int kind=abs(vNormal.z)>=abs(vNormal.x)?1:2;
          vec2 eye=-(uOPEye+uOPOrigin).xz;
          for (int i=0;i<uOPBoundaryCount;i++) {
            vec4 r=opOccupied(i);
            if (int(abs(r.w))!=kind) continue;
            float plane=kind==1?tile.y-r.y:tile.x-r.x;
            float along=kind==1?tile.x-r.x:tile.y-r.y;
            if (abs(plane)>=0.06 || along < -0.01 || along>r.z+0.01) continue;
            float cameraAxis=kind==1?eye.y-r.y:eye.x-r.x;
            return cameraAxis*sign(r.w)<0.0?1:-1;
          }
          return 0;
        }

        void oldPointInterior() {
          if (uOPInterior == 0) return;
          // Native underground rendering caps the displayed levels globally,
          // including streets and vegetation outside the occupied room.
          if (uOPFloor<=0.0 && (vOPSourceBase>=uOPFloor-0.015
              || vOPSourceBase < -99999.0 && (vOPStructure+uOPOrigin).y>=uOPFloor-0.015)) discard;
          if (vOPOpening>=0) {
            float noise=fract(52.9829189*fract(dot(floor(gl_FragCoord.xy),vec2(0.06711056,0.00583715))));
            if (noise<opOpeningMin(vOPOpening).w) discard;
            return;
          }
          vec3 world = vOPStructure + uOPOrigin;
          vec2 tile = -world.xz;
          // Never cut terrain at or below the player's walking floor, including
          // roads and lawns. Ignore vegetation and water altogether.
          if (world.y<=uOPFloor-2.4494896+0.05) return;
          ivec4 candidates=opSpatial(tile);
          for (int n=0;n<candidates.w;n++) {
            int roofIndex=opSpatialRule(candidates.z+n);
            int i=uOPStructureWallCount+roofIndex*2;
            if (uOPAtlasIdentity!=int(opFade(i))) continue;
            vec4 rect=opRoofRect(roofIndex);
            if (any(greaterThan(abs(vRect-rect),vec4(0.000002)))) continue;
            vec4 a=opWall(i);
            vec4 b=opWall(i+1);
            if (world.y>=a.z && world.y<=b.z
                && all(greaterThanEqual(tile,a.xy)) && all(lessThanEqual(tile,b.xy))) discard;
          }
          // Provenance identifies roof faces, hanging eaves and vertical gables
          // even when their material was borrowed from an ordinary wall.
          // The walking-floor guard above still preserves ground surfaces.
          bool generatedRoof=vOPGeneratedRoof!=0;
          // Far shells do not retain source floor. Clip upper fragments of
          // selected neighbouring buildings by height, preserving their base.
          if (vOPSourceBase < -99999.0 && world.y>=uOPFloor-0.015) {
            for (int i=0;i<uOPForeignUpperCount;i++) {
              vec4 r=opRoom(i);
              if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) discard;
            }
          }
          if ((generatedRoof || vOPSourceBase>=uOPFloor-0.015 || vOPSourceBase < -99999.0 && abs(vNormal.y)>0.5 && world.y>=uOPFloor-0.015)) {
            for (int i=0;i<uOPRoomCount;i++) {
              vec4 r=opRoom(i);
              if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) discard;
            }
          }
          // Water/decal surfaces on hidden upper floors must be removed before
          // the current-floor terrain protection is applied.
          if ((vFlags & (FLAG_WATER | FLAG_GRASS))!=0) return;
          // These paths previously returned after the boundary scan anyway.
          if (abs(vNormal.y)>0.5) return;
          if (vOPObjectKind!=0 || candidates.y==0) return;
          int ownFace=oldPointOwnFace(tile);
          if (ownFace<0) return;
          // FLAG_GLASS also labels window-hole walls and transparent wall art.
          // Only a source matched by vOPOpening may bypass wall removal above.
          if ((vFlags & (FLAG_BILLBOARD | FLAG_FACING))!=0) return;
          // CPU selection already checks camera obstruction against room sight
          // lines. Hide the whole selected wall segment; a second per-fragment
          // player/room test leaves strips above and beside the explored space.
          if (abs(vNormal.y)>0.5) return;
          if ((vFlags & FLAG_SINGLE_SIDED)==0) return;
          float fade=0.0;
          for (int n=0;n<candidates.y;n++) {
            int i=opSpatialRule(candidates.x+n);
            vec4 w=opWall(i);
            bool north=w.w<1.5;
            float plane=north?abs(tile.y-w.y):abs(tile.x-w.x);
            float along=north?tile.x-w.x:tile.y-w.y;
            if (plane<0.06 && along>=-0.01 && along<=1.01
                && world.y>=w.z) {
              float amount=opFade(i);
              fade=max(fade,amount);
            }
          }
          // Only structural wall fading needs camera-stable coverage. Opening,
          // furniture and decoration art keeps its original fine screen dither.
          if (fade>0.0 && oldPointWallNoise(world)<fade) discard;
        }
        """;

    public static String shader(String path, String source) {
        if (source == null || source.contains("void oldPointInterior()")) {
            return source;
        }
        boolean terrainFragment =
                path.equals("gbuffer.frag")
                        || path.equals("forward.frag")
                        || path.equals("gbuffer_shell.frag")
                        || path.equals("far_shell.frag")
                        || path.equals("iris/mesh_fragment.glsl")
                        || path.equals("iris/far_shell_fragment.glsl")
                        || path.equals("puddle.frag");
        if (terrainFragment) {
            // Preserve native room blackness only when that native feature is enabled.
            source =
                    source.replaceAll("(gAlbedo = vec4\\([^;]+;)", "$1\n  gAlbedo.rgb *= oldPointRoomLight();")
                            .replace("vec3 color = shade(", "s.albedo *= oldPointRoomLight();\n  vec3 color = shade(")
                            .replace(
                                    "vec3 color = wetGround(",
                                    "s.albedo *= oldPointRoomLight();\n  vec3 color = wetGround(");
            // Declare before the existing surface helpers as well as our cutaway.
            source =
                    source.replace("flat in vec4 vRect;", "flat in vec4 vRect;\nflat in int vOPOpening;")
                            .replace(
                                    "glassSolid(vFlags, vPos, vNormal)",
                                    "(vOPOpening>=0 ? false : glassSolid(vFlags, vPos, vNormal))")
                            .replace("glassFar(vPos, vNormal)", "(vOPOpening>=0 ? 0.0 : glassFar(vPos, vNormal))");
        }
        if (path.equals("lib/camera_clear.glsl") && !source.contains("uOPNoCameraClear")) {
            return source.replace("uniform vec4 uClearEye;", "uniform int uOPNoCameraClear;\nuniform vec4 uClearEye;")
                    .replace(
                            "if (uClearEye.w < 0.5 || kind == 2)",
                            "if (uOPNoCameraClear != 0 || uClearEye.w < 0.5 || kind == 2)");
        }
        // Geometry's depth bias is camera-distance dependent. Preserve the
        // unmodified position for structural classification in every path.
        if (path.equals("mesh.vert") || path.equals("iris/mesh_vertex.glsl")) {
            if (source.contains("out vec3 vOPStructure;")) {
                return source;
            }
            return source.replace(
                            "out vec3 vPos;",
                            "out vec3 vPos;\nout vec3 vOPStructure;\nout vec3 vOPLift;\nflat out int vOPCanopy;\nflat out vec3 vOPPlantBase;\nflat out vec3 vOPObjectRoot;\nflat out int vOPObjectKind;\nflat out int vOPModelIndex;\nflat out float vOPSourceBase;\nflat out int vOPGeneratedRoof;"
                                    + OpeningLookup.SHADER)
                    .replace("vPos = pulled(v, uEye);", plantVaryings() + "\n  vPos = pulled(v, uEye);")
                    .replace(
                            "vPos = uIrisShadow == 0 ? pulled(v, uEye) : v.pos;",
                            plantVaryings() + "\n  vPos = uIrisShadow == 0 ? pulled(v, uEye) : v.pos;");
        }
        if (path.equals("far_shell.vert") || path.equals("iris/far_shell_vertex.glsl")) {
            if (source.contains("out vec3 vOPStructure;")) {
                return source;
            }
            return source.replace(
                            "out vec3 vPos;",
                            "out vec3 vPos;\nout vec3 vOPStructure;\nout vec3 vOPLift;\nflat out int vOPCanopy;\nflat out vec3 vOPPlantBase;\nflat out vec3 vOPObjectRoot;\nflat out int vOPObjectKind;\nflat out int vOPModelIndex;\nflat out float vOPSourceBase;\nflat out int vOPGeneratedRoof;"
                                    + OpeningLookup.SHADER)
                    // 0.1.6 batches shell origins in records: the built-in and
                    // Iris shaders now use origin/shellOrigin instead of aOrigin.
                    .replaceAll(
                            "vPos = aPos \\+ (aOrigin|origin|shellOrigin)\\.xyz;",
                            "vOPGeneratedRoof = int((aFlags & 1073741824)!=0 && (aFlags & (FLAG_ROOTED | FLAG_STANDING))==0);\n  vOPSourceBase = -100000.0;\n  vOPCanopy = 0;\n  vOPPlantBase = vec3(0.0);\n  vOPObjectRoot = vec3(0.0);\n  vOPObjectKind = 0;\n  vOPModelIndex = -1;\n  vOPStructure = aPos + $1.xyz;\n  vOPLift = vec3(0.0);\n  vPos = vOPStructure;")
                    .replace(
                            "vNormal =",
                            "vOPOpening = vOPGeneratedRoof!=0 ? -1 : oldPointOpeningIndex(vOPStructure,vOPObjectRoot,0,-1,aRect,aFlags);\n  vOPLift = vPos - vOPStructure;\n  vNormal =");
        }
        if (path.equals("gbuffer.frag")
                || path.equals("forward.frag")
                || path.equals("gbuffer_shell.frag")
                || path.equals("far_shell.frag")
                || path.equals("puddle.frag")) {
            return source.replace("void main() {", TERRAIN + "\nvoid main() {\n  oldPointInterior();");
        }
        if (path.equals("iris/mesh_fragment.glsl")) {
            return source.replace("void prologue() {", TERRAIN + "\nvoid prologue() {")
                    .replace("  unpackMesh();", "  unpackMesh();\n  oldPointInterior();")
                    .replace(
                            "return surface(uv);",
                            "vec4 opSurface=surface(uv); opSurface.rgb*=oldPointRoomLight(); return opSurface;");
        }
        if (path.equals("iris/far_shell_fragment.glsl")) {
            return source.replace("void prologue() {", TERRAIN + "\nvoid prologue() {\n  oldPointInterior();")
                    .replace("return shellTexel;", "return vec4(shellTexel.rgb*oldPointRoomLight(),shellTexel.a);")
                    .replace("return c * shellTexel.rgb;", "return c * shellTexel.rgb * oldPointRoomLight();")
                    // A built near chunk still supplies detailed geometry in
                    // overhead mode even when the eye is outside the near radius.
                    .replace(
                            "length(vPos - uIrisEye) < uIrisNearKept &&",
                            "(uOPInterior != 0 || length(vPos - uIrisEye) < uIrisNearKept) &&");
        }
        return source;
    }

    private static String plantVaryings() {
        return """
            vOPStructure = vec3(-v.local.x, v.local.y + v.mesh.w, -v.local.z) + v.mesh.xyz;
            vOPLift = v.pos - vOPStructure;
            vOPSourceBase = v.mesh.w;
            vOPGeneratedRoof = int(uPlants==0 && uModels==0 && uCards==0 && (v.flags & 1073741824)!=0 && (v.flags & (FLAG_ROOTED | FLAG_STANDING))==0);
            vOPObjectRoot = v.opObjectRoot;
            vOPObjectKind = v.opObjectKind;
            vOPModelIndex = v.opModelIndex;
            vOPOpening = vOPGeneratedRoof!=0 ? -1 : oldPointOpeningIndex(vOPStructure,vOPObjectRoot,vOPObjectKind,vOPModelIndex,v.rect,v.flags);
            vOPCanopy = int(uPlants != 0 && (v.flags & FLAG_GRASS) == 0);
            vOPPlantBase = v.mesh.xyz;
            if (uPlants != 0) {
              int opSlot = (gl_VertexID / PLANT_VERTICES) * PLANT_TEXELS;
              vec2 opFoot0 = texelFetch(uPlantSlots, opSlot).xy;
              vec2 opFoot1 = texelFetch(uPlantSlots, opSlot + 1).xy;
              vOPPlantBase += vec3(opFoot0.x, opFoot0.y, opFoot1.x);
            }
            """;
    }
}
