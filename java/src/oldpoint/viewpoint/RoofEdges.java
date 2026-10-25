package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import viewpoint.world.WorldMesher;
import viewpoint.packs.ModelPacks;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;

/** Bounds of roof art that hangs below the next level or outside its footprint. */
final class RoofEdges {
    record Bounds(
            float x,
            float y,
            float up,
            float x2,
            float y2,
            float up2,
            float r0,
            float r1,
            float r2,
            float r3,
            int atlas) {}

    private record Cached(
            Object cell,
            Object player,
            int x,
            int y,
            int floor,
            int top,
            List<Layout.Rect> regions,
            boolean outdoor,
            long time,
            List<Bounds> result) {}

    private static Cached cached;
    private static Cached request;
    private static CaptureScan scan;
    private static ArrayList<Bounds> pending;

    static void clear() {
        cached = null;
        request = null;
        scan = null;
        pending = null;
    }

    static List<Bounds> capture(
            IsoGameCharacter player, List<Layout.Rect> regions, int top, OutdoorCutaway.Selection outdoor) {

        if (player.getCurrentSquare() == null || regions.isEmpty()) {
            clear();
            return List.of();
        }
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        int floor = (int) Math.floor(player.getZ());
        var cell = IsoWorld.instance.getCell();
        long now = System.nanoTime();
        Cached old = cached;
        boolean valid =
                old != null
                        && old.cell == cell
                        && old.player == player
                        && old.x == cx
                        && old.y == cy
                        && old.floor == floor
                        && old.top == top
                        && old.outdoor == (outdoor != null)
                        && old.regions.equals(regions);
        if (valid && now - old.time < 200_000_000L) {
            return old.result;
        }
        boolean same =
                request != null
                        && request.cell == cell
                        && request.player == player
                        && request.x == cx
                        && request.y == cy
                        && request.floor == floor
                        && request.top == top
                        && request.outdoor == (outdoor != null)
                        && request.regions.equals(regions);
        if (!same) {
            int minX = cx - 12, maxX = cx + 12, minY = cy - 12, maxY = cy + 12;
            for (var r : regions) {
                minX = Math.max(cx - 48, Math.min(minX, r.x() - 1));
                maxX = Math.min(cx + 48, Math.max(maxX, r.x() + r.width()));
                minY = Math.max(cy - 48, Math.min(minY, r.y() - 1));
                maxY = Math.min(cy + 48, Math.max(maxY, r.y() + r.height()));
            }
            scan = new CaptureScan(minX, maxX, minY, maxY, floor - 1, Math.min(31, top));
            pending = new ArrayList<>();
            request =
                    new Cached(cell, player, cx, cy, floor, top, List.copyOf(regions), outdoor != null, now, List.of());
        }
        ArrayList<Bounds> result = pending;
        scan.begin();
        while (scan.next()) {
            int x = scan.x, y = scan.y, z = scan.z;
            var square = cell.getGridSquare(x, y, z);
            if (square == null) {
                continue;
            }
            if (outdoor != null && !outdoor.buildings().isEmpty() && !outdoor.roofOwner(square, x, y)) {
                continue;
            }
            var objects = square.getObjects();
            for (int i = scan.objectIndex; i < objects.size(); i++) {
                if (!scan.allowObject(i)) {
                    break;
                }
                var object = objects.get(i);
                var sprite = object.getSprite();
                if (sprite == null) {
                    continue;
                }
                if (!RoofModels.roofObject(object, sprite, ModelPacks.bind(sprite))) {
                    continue;
                }
                float ax = Float.POSITIVE_INFINITY, ay = ax, az = ax;
                float bx = Float.NEGATIVE_INFINITY, by = bx, bz = bx;
                // A resident pack does not prove every distance/render pass
                // uses it. Retain already-built tile-art bounds for fallback
                // geometry, alongside RoofModels' packed-instance mask.
                var texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
                if (texture == null || !texture.isReady()) {
                    continue;
                }
                boolean generated = sprite.name != null && viewpoint.world.MapRoofs.vanilla(sprite);
                float[] meshBounds = ReadyMeshes.bounds(sprite, texture, null);
                // Only observe existing geometry. Viewpoint's peek() also
                // creates on a miss, so it must not run on this capture path.
                if (meshBounds == null && !generated) {
                    continue;
                }
                if (meshBounds != null) {
                    float[] b = meshBounds;
                    ax = -b[3];
                    bx = -b[0];
                    ay = b[1];
                    by = b[4];
                    az = -b[5];
                    bz = -b[2];
                }
                float base = z * 2.4494896f + WorldMesher.rise(object);
                if (generated) {
                    // Generated roof faces are seated by RoofBuilder; their
                    // final height need not equal the source sprite mesh.
                    ax = Math.min(ax, -0.3f);
                    az = Math.min(az, -0.3f);
                    bx = Math.max(bx, 1.3f);
                    bz = Math.max(bz, 1.3f);
                    ay = Math.min(ay, floor * 2.4494896f + 1.9f - base);
                    by = Math.max(by, (floor + 1) * 2.4494896f + 0.8f - base);
                }
                // Generic edge quads cover a whole wall although roof art
                // occupies only its top. Preserve lower walls and furniture.
                float low = base + ay - 0.025f;
                if (base + by < low) {
                    continue;
                }
                float[] rect = WorldMesher.textureMapping(texture);
                int atlas = texture.getTextureId().getID();
                if (atlas < 0) {
                    continue;
                }
                addJoined(
                        result,
                        new Bounds(
                                x + ax - 0.025f,
                                y + az - 0.025f,
                                low,
                                x + bx + 0.025f,
                                y + bz + 0.025f,
                                base + by + 0.025f,
                                rect[0],
                                rect[1],
                                rect[2],
                                rect[3],
                                atlas));
            }
        }
        scan.finish();
        boolean scanComplete = scan.done();
        if (!scanComplete && valid) {
            return old.result;
        }
        result.sort(
                java.util.Comparator.comparingDouble(
                        b ->
                                Math.pow((b.x + b.x2) * 0.5 - player.getX(), 2)
                                        + Math.pow((b.y + b.y2) * 0.5 - player.getY(), 2)));

        List<Bounds> immutable = List.copyOf(result);
        if (scanComplete) {
            cached =
                    new Cached(
                            cell,
                            player,
                            cx,
                            cy,
                            floor,
                            top,
                            List.copyOf(regions),
                            outdoor != null,
                            System.nanoTime(),
                            immutable);
            request = null;
            scan = null;
            pending = null;
        }
        return immutable;
    }

    private static void addJoined(ArrayList<Bounds> boxes, Bounds value) {
        // Merge straight continuous eaves before applying the uniform budget.
        // Matching cross sections avoids filling gaps between perpendicular runs.
        for (int i = 0; i < boxes.size(); i++) {
            Bounds b = boxes.get(i);
            if (b.atlas != value.atlas
                    || b.r0 != value.r0
                    || b.r1 != value.r1
                    || b.r2 != value.r2
                    || b.r3 != value.r3) {
                continue;
            }
            if (Math.abs(b.up - value.up) > 0.01f || Math.abs(b.up2 - value.up2) > 0.01f) {
                continue;
            }
            boolean alongX =
                    Math.abs(b.y - value.y) < 0.01f
                            && Math.abs(b.y2 - value.y2) < 0.01f
                            && b.x <= value.x2 + 0.01f
                            && value.x <= b.x2 + 0.01f;
            boolean alongY =
                    Math.abs(b.x - value.x) < 0.01f
                            && Math.abs(b.x2 - value.x2) < 0.01f
                            && b.y <= value.y2 + 0.01f
                            && value.y <= b.y2 + 0.01f;
            if (!alongX && !alongY) {
                continue;
            }
            boxes.remove(i);
            addJoined(
                    boxes,
                    new Bounds(
                            Math.min(b.x, value.x),
                            Math.min(b.y, value.y),
                            value.up,
                            Math.max(b.x2, value.x2),
                            Math.max(b.y2, value.y2),
                            value.up2,
                            value.r0,
                            value.r1,
                            value.r2,
                            value.r3,
                            value.atlas));
            return;
        }
        boxes.add(value);
    }
}
