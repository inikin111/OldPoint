package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import viewpoint.packs.ModelPacks;
import viewpoint.packs.PackBind;
import viewpoint.render.PackModel;
import viewpoint.world.WorldMesher;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoThumpable;

/** Cached openings and attached decorations sharing selected near-wall rules. */
public final class OpeningFade {
    private static final ThreadLocal<viewpoint.platform.GlProgram> programs = new ThreadLocal<>();

    static void program(viewpoint.platform.GlProgram program) {
        if (program.has("uOPAtlasIdentity")) {
            programs.set(program);
        } else {
            programs.remove();
        }
    }

    public static void atlasBound(Object[] arguments) {
        var program = programs.get();
        if (program == null || !CameraDrawing.drawTopDown() || arguments.length == 0) {
            return;
        }
        if (arguments[arguments.length - 1] instanceof zombie.core.textures.TextureID page) {
            program.setInt("uOPAtlasIdentity", page.getID());
        }
    }

    record Art(float x, float y, float z, float w) {}

    record Instance(
            int model,
            float x,
            float y,
            float up,
            ObjectBounds bounds,
            Art art,
            float fade,
            zombie.iso.IsoObject owner) {
        Instance(int model, float x, float y, float up, ObjectBounds bounds, Art art, float fade) {
            this(model, x, y, up, bounds, art, fade, null);
        }

        Instance withFade(float amount) {
            return new Instance(model, x, y, up, bounds, art, amount, owner);
        }
    }

    record Capture(List<Instance> instances, Map<zombie.iso.IsoObject, Float> furniture) {}

    private record Cached(
            Object cell,
            Object player,
            Object room,
            int x,
            int y,
            int floor,
            int faces,
            List<Layout.Rect> occupied,
            long time,
            Capture capture) {}

    private static Cached cached;
    private static Cached request;
    private static CaptureScan scan;
    private static ArrayList<Instance> pending;
    private static HashMap<zombie.iso.IsoObject, Float> pendingFurniture;
    private static FurnitureOcclusion.Context pendingContext;

    static void clear() {
        cached = null;
        request = null;
        scan = null;
        pending = null;
        pendingFurniture = null;
        pendingContext = null;
    }

    private OpeningFade() {}

    private static boolean border(List<Layout.Rect> regions, int x, int y) {
        for (var r : regions) {
            if (x >= r.x() - 1 && x <= r.x() + r.width() && y >= r.y() - 1 && y <= r.y() + r.height()) {
                return true;
            }
        }
        return false;
    }

    static Capture capture(
            IsoGameCharacter player,
            List<Layout.Rect> occupied,
            Layout.Sight sight,
            OutdoorCutaway.Selection outdoor,
            java.util.Set<OcclusionSnapshot.Wall> hiddenWalls) {
        var cell = IsoWorld.instance.getCell();
        var own = player.getCurrentSquare().getRoom();
        int floor = (int) Math.floor(player.getZ());
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        var nearbyArea = NearbyFade.area(player.getX(), player.getY(), sight.cx(), sight.cy());
        if (outdoor == null && (own == null || occupied.isEmpty())) {
            clear();
            return new Capture(List.of(), Map.of());
        }
        int faces = outdoor == null ? 1 : outdoor.walls().hashCode();
        if (outdoor != null) {
            faces = 31 * (31 * faces + Math.round(nearbyArea.x() * 4)) + Math.round(nearbyArea.y() * 4);
        }
        var boundary = outdoor == null ? RoomBoundary.capture(occupied) : List.<RoomBoundary.Run>of();
        if (outdoor == null) {
            for (var r : boundary) {
                faces = faces * 31 + r.face(r.kind() == 1 ? sight.cy() : sight.cx());
            }
        }
        faces = 31 * faces + hiddenWalls.hashCode();
        long now = System.nanoTime();
        var old = cached;
        boolean valid =
                old != null
                        && old.cell == cell
                        && old.player == player
                        && old.room == own
                        && old.x == cx
                        && old.y == cy
                        && old.floor == floor
                        && old.faces == faces
                        && old.occupied.equals(occupied);
        if (valid && now - old.time < 150_000_000L) {
            return old.capture;
        }
        boolean same =
                request != null
                        && request.cell == cell
                        && request.player == player
                        && request.room == own
                        && request.x == cx
                        && request.y == cy
                        && request.floor == floor
                        && request.faces == faces
                        && request.occupied.equals(occupied);
        if (!same) {
            var furniture = outdoor == null ? FurnitureOcclusion.context(player, sight) : null;
            int minX = cx + 48, maxX = cx - 48, minY = cy + 48, maxY = cy - 48;
            for (var r : occupied) {
                minX = Math.min(minX, Math.max(cx - 48, r.x() - 1));
                maxX = Math.max(maxX, Math.min(cx + 48, r.x() + r.width()));
                minY = Math.min(minY, Math.max(cy - 48, r.y() - 1));
                maxY = Math.max(maxY, Math.min(cy + 48, r.y() + r.height()));
            }
            if (furniture != null) {
                for (var r : furniture.occupied()) {
                    minX = Math.min(minX, Math.max(cx - 48, r.x() - 4));
                    maxX = Math.max(maxX, Math.min(cx + 48, r.x() + r.width() + 3));
                    minY = Math.min(minY, Math.max(cy - 48, r.y() - 4));
                    maxY = Math.max(maxY, Math.min(cy + 48, r.y() + r.height() + 3));
                }
            }
            if (outdoor != null) {
                minX = Math.min(minX, (int) Math.floor(nearbyArea.x() - NearbyFade.RADIUS));
                maxX = Math.max(maxX, (int) Math.ceil(nearbyArea.x() + NearbyFade.RADIUS));
                minY = Math.min(minY, (int) Math.floor(nearbyArea.y() - NearbyFade.RADIUS));
                maxY = Math.max(maxY, (int) Math.ceil(nearbyArea.y() + NearbyFade.RADIUS));
            }
            scan = new CaptureScan(minX, maxX, minY, maxY, floor, floor);
            pending = new ArrayList<>();
            pendingFurniture = new HashMap<>();
            pendingContext = furniture;
            request =
                    new Cached(
                            cell,
                            player,
                            own,
                            cx,
                            cy,
                            floor,
                            faces,
                            List.copyOf(occupied),
                            now,
                            new Capture(List.of(), Map.of()));
        }
        var furniture = pendingContext;
        ArrayList<Instance> result = pending;
        scan.begin();
        while (scan.next()) {
            int x = scan.x, y = scan.y;
            if (!border(occupied, x, y)
                    && !(furniture != null && furniture.nearCurrent(x, y))
                    && !(outdoor != null && nearbyArea.contains(x + 0.5f, y + 0.5f))) {
                continue;
            }
            var square = cell.getGridSquare(x, y, floor);
            if (square == null) {
                continue;
            }
            var objects = square.getObjects();
            for (int i = scan.objectIndex; i < objects.size(); i++) {
                if (!scan.allowObject(i)) {
                    break;
                }
                var object = objects.get(i);
                var main = object.getSprite();
                if (main == null || main.name == null) {
                    continue;
                }
                var mainEdge = OpeningEdge.of(object, x, y);
                var mainDecoration = mainEdge == null ? OpeningEdge.decoration(main, x, y) : null;
                boolean nearby = outdoor != null && nearbyArea.contains(x + 0.5f, y + 0.5f);
                boolean doorObject = object instanceof IsoDoor || object instanceof IsoThumpable t && t.isDoor();
                boolean furnitureCandidate = furniture != null && furniture.candidate(square, object, main);
                boolean doorCandidate =
                        furniture != null
                                && doorObject
                                && furniture.doorCandidate(square, mainEdge)
                                && !furniture.nearWall(mainEdge);
                boolean wholeObject =
                        (furnitureCandidate || doorCandidate || nearby || mainEdge != null || mainDecoration != null)
                                && (NearbyFade.eligible(object, main)
                                        || doorObject
                                        || object instanceof IsoWindow
                                        || object instanceof IsoCurtain);
                if (wholeObject) {
                    boolean near =
                            mainEdge != null
                                    && (outdoor != null
                                            ? outdoor.opening(mainEdge.x(), mainEdge.y(), mainEdge.kind())
                                            : near(boundary, mainEdge, sight));
                    boolean decorationHidden =
                            mainDecoration != null
                                    && hiddenWalls.contains(
                                            new OcclusionSnapshot.Wall(
                                                    mainDecoration.x(),
                                                    mainDecoration.y(),
                                                    floor,
                                                    mainDecoration.kind()));
                    float target = mainDecoration != null && (near || decorationHidden) ? 1f : near ? 0.5f : 0;
                    if (nearby && NearbyFade.eligible(object, main)) {
                        target = Math.max(target, NearbyFade.amount(object));
                    }
                    if (target > 0) {
                        pendingFurniture.merge(object, target, Math::max);
                    }
                }
                var overlay = object.getOverlaySprite();
                var attached = object.getAttachedAnimSprite();
                if (!nearby
                        && !furnitureCandidate
                        && !doorCandidate
                        && mainEdge == null
                        && mainDecoration == null
                        && overlay == null
                        && (attached == null || attached.isEmpty())) {
                    continue;
                }
                ArrayList<zombie.iso.sprite.IsoSprite> parts = new ArrayList<>();
                parts.add(main);
                if (overlay != null) {
                    parts.add(overlay);
                }
                if (attached != null) {
                    for (var part : attached) {
                        if (part != null && part.parentSprite != null) {
                            parts.add(part.parentSprite);
                        }
                    }
                }
                for (var sprite : parts) {
                    if (sprite == null || sprite.name == null) {
                        continue;
                    }
                    // An independent frame may be attached to an ordinary wall object.
                    // Classify each part, never promote that wall's main art to an opening.
                    var edge = sprite == main ? mainEdge : OpeningEdge.ofSprite(sprite, x, y);
                    if (edge == null && sprite != main) {
                        edge = mainEdge;
                    }
                    var decoration = edge == null ? OpeningEdge.decoration(sprite, x, y) : null;
                    if (edge == null) {
                        edge = decoration;
                    }
                    boolean nearObject = nearby && NearbyFade.eligible(object, sprite);
                    boolean opening =
                            edge != null
                                    && (outdoor != null
                                            ? outdoor.opening(edge.x(), edge.y(), edge.kind())
                                            : hiddenWalls.contains(
                                                    new OcclusionSnapshot.Wall(
                                                            edge.x(), edge.y(), floor, edge.kind())));
                    if (decoration != null) {
                        opening =
                                hiddenWalls.contains(
                                        new OcclusionSnapshot.Wall(
                                                decoration.x(), decoration.y(), floor, decoration.kind()));
                    }
                    if (!opening && !nearObject && !furnitureCandidate && !doorCandidate && !wholeObject) {
                        continue;
                    }
                    // Combined wall/window artwork stays in structural cutaway; never fade the wall
                    // with its window.
                    if (sprite.name.toLowerCase(java.util.Locale.ROOT).startsWith("walls_")
                            && !(doorObject && sprite == main)
                            && !OpeningEdge.doorFrame(sprite)
                            && sprite.getType() != zombie.iso.SpriteDetails.IsoObjectType.windowFN
                            && sprite.getType() != zombie.iso.SpriteDetails.IsoObjectType.windowFW) {
                        continue;
                    }
                    var bind = ModelPacks.bind(sprite);
                    boolean packed =
                            bind != null
                                    && bind.model.state() == PackModel.RESIDENT
                                    && !viewpoint.world.MapRoofs.vanilla(sprite);
                    // A resident pack does not prove every pass uses it: also
                    // retain native art identity for detailed/shell transitions.
                    for (int representation = 0; representation < (packed ? 2 : 1); representation++) {
                        boolean usePacked = packed && representation == 0;
                        float rx = x + 0.5f + (usePacked ? bind.x : 0), ry = y + 0.5f + (usePacked ? bind.y : 0);
                        float base = floor * 2.4494896f + WorldMesher.rise(object),
                                up = base + (usePacked ? bind.z : 0);
                        float lx = Float.POSITIVE_INFINITY,
                                ly = lx,
                                lz = lx,
                                hx = Float.NEGATIVE_INFINITY,
                                hy = hx,
                                hz = hx;
                        Art art = new Art(0, 0, 0, 0);
                        int model;
                        if (usePacked) {
                            model = bind.model.index;
                            float[] b = bind.model.bounds();
                            if (b == null || b.length < 6) {
                                continue;
                            }
                            float[] p = new float[3];
                            for (int c = 0; c < 8; c++) {
                                bind.place(
                                        b[(c & 1) == 0 ? 0 : 3],
                                        b[(c & 2) == 0 ? 1 : 4],
                                        b[(c & 4) == 0 ? 2 : 5],
                                        PackBind.turns(sprite),
                                        p);
                                float px = rx - p[0], py = ry - p[2], pz = up + p[1];
                                lx = Math.min(lx, px);
                                ly = Math.min(ly, py);
                                lz = Math.min(lz, pz);
                                hx = Math.max(hx, px);
                                hy = Math.max(hy, py);
                                hz = Math.max(hz, pz);
                            }
                        } else {
                            var texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
                            if (texture == null || !texture.isReady()) {
                                continue;
                            }
                            float[] b = ReadyMeshes.bounds(sprite, texture, sprite == main ? null : main);
                            if (b == null) {
                                continue;
                            }
                            lx = x + 0.5f + b[0];
                            ly = y + 0.5f + b[2];
                            lz = base + b[1];
                            hx = x + 0.5f + b[3];
                            hy = y + 0.5f + b[5];
                            hz = base + b[4];
                            float[] r = WorldMesher.textureMapping(texture);
                            art = new Art(r[0], r[1], r[2], r[3]);
                            int atlas = texture.getTextureId().getID();
                            if (atlas < 0) {
                                continue;
                            }
                            model = -atlas - 2;
                        }
                        var bounds = new ObjectBounds(lx, ly, lz, hx, hy, hz);
                        float furnitureFade = furnitureCandidate ? furniture.fade(square, bounds) : 0;
                        float doorFade = doorCandidate ? furniture.doorFade(square, bounds) : 0;
                        if (!opening && !nearObject && furnitureFade <= 0 && doorFade <= 0 && !wholeObject) {
                            continue;
                        }
                        float fade =
                                Math.max(
                                        Math.max(furnitureFade, doorFade),
                                        (opening && decoration != null
                                                        || nearObject
                                                                && NearbyFade.upper(sprite, lz - floor * 2.4494896f))
                                                ? 1f
                                                : opening || nearObject ? 0.5f : 0);
                        if (wholeObject) {
                            pendingFurniture.merge(object, fade, Math::max);
                        }
                        result.add(new Instance(model, rx, ry, up, bounds, art, fade, wholeObject ? object : null));
                    }
                }
            }
        }
        scan.finish();
        boolean scanComplete = scan.done();
        if (!scanComplete) {
            // Never expose a partially scanned set of wall attachments. A
            // tile move changes the cache key even though the room is the same.
            if (old != null && old.cell == cell && old.player == player && old.room == own && old.floor == floor) {
                return old.capture;
            }
            return new Capture(List.of(), Map.of());
        }
        Capture capture = new Capture(List.copyOf(result), Map.copyOf(pendingFurniture));
        if (scanComplete) {
            cached =
                    new Cached(
                            cell, player, own, cx, cy, floor, faces, List.copyOf(occupied), System.nanoTime(), capture);
            request = null;
            scan = null;
            pending = null;
            pendingFurniture = null;
            pendingContext = null;
        }
        return capture;
    }

    private static boolean near(List<RoomBoundary.Run> boundary, OpeningEdge.Edge edge, Layout.Sight sight) {
        for (var run : boundary) {
            if (run.kind() == edge.kind()
                    && run.face(edge.kind() == 1 ? sight.cy() : sight.cx()) > 0
                    && (edge.kind() == 1
                            ? run.y() == edge.y() && edge.x() >= run.x() && edge.x() < run.x() + run.length()
                            : run.x() == edge.x() && edge.y() >= run.y() && edge.y() < run.y() + run.length())) {
                return true;
            }
        }
        return false;
    }
}
