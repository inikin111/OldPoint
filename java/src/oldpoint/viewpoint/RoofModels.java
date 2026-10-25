package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL20;
import viewpoint.core.Frame;
import viewpoint.packs.ModelPacks;
import viewpoint.packs.PackBind;
import viewpoint.platform.GlProgram;
import viewpoint.render.PackModel;
import viewpoint.render.SceneData;
import viewpoint.world.WorldMesher;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;

/** Mask selected roof instances, without cutting wall vertices in the same space. */
public final class RoofModels {
    private record Chunk(int x, int y, int floor) {}

    // A weak-key cache value must never hold its own SceneData key.
    private record State(
            Map<PackModel, Map<Chunk, Long>> models,
            long captured,
            int regions,
            int floor,
            int x,
            int y,
            boolean outdoor) {}

    private record Drawing(SceneData scene, State state) {}

    private static final Map<SceneData, State> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Drawing> drawing = new ThreadLocal<>();
    private static Field runsField;
    private static boolean reported;
    private static boolean reportedActive;
    private static final Map<PackModel, Boolean> roofAssets = new WeakHashMap<>();

    private RoofModels() {}

    static void reset(SceneData scene) {
        snapshots.remove(scene);
    }

    private record Key(
            Object cell, Object player, List<Layout.Rect> regions, int floor, int x, int y, int top, boolean outdoor) {}

    private static Key request, completed;
    private static CaptureScan scan;
    private static Map<PackModel, Map<Chunk, Long>> pending;
    private static Map<PackModel, Map<Chunk, Long>> completedModels = Map.of();
    private static long completedAt;

    static void clear() {
        snapshots.clear();
        roofAssets.clear();
        request = null;
        completed = null;
        scan = null;
        pending = null;
        completedModels = Map.of();
        completedAt = 0;
    }

    static final int NEARBY_RADIUS = 12;

    static boolean roofSprite(zombie.iso.sprite.IsoSprite sprite) {
        if (sprite == null) {
            return false;
        }
        var type = sprite.getType();
        if (type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofB
                || type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofM
                || type == zombie.iso.SpriteDetails.IsoObjectType.WestRoofT) {
            return true;
        }
        if (roofName(sprite.name)) {
            return true;
        }
        var props = sprite.getProperties();
        if (props.has(zombie.iso.SpriteDetails.IsoFlagType.isEave)) {
            return true;
        }
        String name = props.get("CustomName");
        return name != null
                && (name.equalsIgnoreCase("Canopy")
                        || name.equalsIgnoreCase("Awning")
                        || name.equalsIgnoreCase("Roof"));
    }

    static boolean roofName(String name) {
        return RoofIdentity.name(name);
    }

    static boolean roofObject(zombie.iso.IsoObject object, zombie.iso.sprite.IsoSprite sprite, PackBind bind) {
        if (roofSprite(sprite)) {
            return true;
        }
        if (bind != null
                && bind.model.obj != null
                && roofAssets.computeIfAbsent(bind.model, model -> RoofIdentity.asset(model.obj.getPath()))) {
            return true;
        }
        if (sprite != null
                && sprite.name != null
                && sprite.name.startsWith("tilefix_markers_")
                && object.hasModData()) {
            Object original = object.getModData().rawget("tilefixFrom");
            if (original instanceof String from) {
                return roofName(from);
            }
        }
        return false;
    }

    static void capture(
            Frame frame, IsoGameCharacter player, List<Layout.Rect> regions, OutdoorCutaway.Selection outdoor) {
        if (player.getCurrentSquare() == null || regions.isEmpty()) {
            reset(frame.scene);
            request = null;
            scan = null;
            pending = null;
            return;
        }
        int cx = (int) Math.floor(player.getX()), cy = (int) Math.floor(player.getY());
        int floor = (int) Math.floor(player.getZ());
        long captured = System.nanoTime();
        var cell = IsoWorld.instance.getCell();
        int top = floor + 1;
        for (int i = 0; i < frame.scene.meshCount; i++) {
            top = Math.max(top, frame.scene.meshes[i].level);
        }
        Key key = new Key(cell, player, List.copyOf(regions), floor, cx, cy, top, outdoor != null);
        if (key.equals(completed) && captured - completedAt < 250_000_000L) {
            publish(frame, key, completedModels, completedAt);
            return;
        }
        if (!key.equals(request)) {
            int scanMinX = cx - 12, scanMaxX = cx + 12, scanMinY = cy - 12, scanMaxY = cy + 12;
            for (var r : regions) {
                scanMinX = Math.min(scanMinX, r.x() - 2);
                scanMaxX = Math.max(scanMaxX, r.x() + r.width() + 1);
                scanMinY = Math.min(scanMinY, r.y() - 2);
                scanMaxY = Math.max(scanMaxY, r.y() + r.height() + 1);
            }
            scanMinX = Math.max(cx - 48, scanMinX);
            scanMaxX = Math.min(cx + 48, scanMaxX);
            scanMinY = Math.max(cy - 48, scanMinY);
            scanMaxY = Math.min(cy + 48, scanMaxY);
            scan = new CaptureScan(scanMinX, scanMaxX, scanMinY, scanMaxY, floor - 1, Math.min(31, top));
            pending = new HashMap<>();
            request = key;
        }
        Map<PackModel, Map<Chunk, Long>> models = pending;
        scan.begin();
        while (scan.next()) {
            int x = scan.x, y = scan.y, z = scan.z;
            boolean nearby = Math.abs(x - cx) <= NEARBY_RADIUS && Math.abs(y - cy) <= NEARBY_RADIUS;
            boolean selected = outdoor == null && nearby;
            for (var r : regions) {
                if (x >= r.x() - 1 && y >= r.y() - 1 && x < r.x() + r.width() + 1 && y < r.y() + r.height() + 1) {
                    selected = true;
                    break;
                }
            }
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
                if (sprite.getType() != null && sprite.getType().name().startsWith("stairs")
                        || sprite.getProperties().has(zombie.iso.SpriteDetails.IsoFlagType.attachtostairs)) {
                    continue;
                }
                String name = sprite.name == null ? "" : sprite.name.toLowerCase(java.util.Locale.ROOT);
                boolean marker = name.startsWith("tilefix_markers_");
                var bind = ModelPacks.bind(sprite);
                if (bind == null || bind.model.state() != PackModel.RESIDENT) {
                    continue;
                }
                boolean roof = roofObject(object, sprite, bind);
                if (!roof && !marker) {
                    continue;
                }
                if (!roof
                        && marker
                        && object.hasModData()
                        && object.getModData().rawget("tilefixFrom") instanceof String) {
                    continue;
                }
                // Never hide a complete packed roof that lies at/below the
                // player's walking floor. Read existing bounds; do not build.
                float[] bounds = bind.model.bounds();
                if (bounds != null && bounds.length >= 6) {
                    float high =
                            z * 2.4494896f
                                    + WorldMesher.rise(object)
                                    + bind.z
                                    + Math.max(bounds[1] * bind.scale, bounds[4] * bind.scale);
                    if (!Float.isFinite(high) || high <= floor * 2.4494896f + 0.05f) {
                        continue;
                    }
                } else if (z <= floor) {
                    continue;
                }
                // An eave can be anchored outside the room while its mesh
                // projects into the selected room. Use its actual footprint.
                if (!selected) {
                    if (bounds == null || bounds.length < 6) {
                        continue;
                    }
                    float minX = Float.POSITIVE_INFINITY, minY = minX;
                    float maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
                    float[] placed = new float[3];
                    for (int corner = 0; corner < 8; corner++) {
                        bind.place(
                                bounds[(corner & 1) == 0 ? 0 : 3],
                                bounds[(corner & 2) == 0 ? 1 : 4],
                                bounds[(corner & 4) == 0 ? 2 : 5],
                                PackBind.turns(sprite),
                                placed);
                        float px = x + 0.5f + bind.x - placed[0], py = y + 0.5f + bind.y - placed[2];
                        minX = Math.min(minX, px);
                        maxX = Math.max(maxX, px);
                        minY = Math.min(minY, py);
                        maxY = Math.max(maxY, py);
                    }
                    boolean overlap = false;
                    for (var r : regions) {
                        if (maxX > r.x() && maxY > r.y() && minX < r.x() + r.width() && minY < r.y() + r.height()) {
                            overlap = true;
                            break;
                        }
                    }
                    if (!overlap) {
                        continue;
                    }
                }
                // A high marker can be a wall cap. Height alone is not evidence of a roof.
                if (!roof && z <= floor) {
                    continue;
                }
                Chunk chunk = new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8), z);
                long bit = 1L << (Math.floorMod(y, 8) * 8 + Math.floorMod(x, 8));
                models.computeIfAbsent(bind.model, k -> new HashMap<>()).merge(chunk, bit, (a, b) -> a | b);
            }
        }
        scan.finish();
        boolean done = scan.done();
        if (!done && key.equals(completed)) {
            publish(frame, key, completedModels, captured);
            return;
        }
        Map<PackModel, Map<Chunk, Long>> frozen = new HashMap<>();
        models.forEach((model, masks) -> frozen.put(model, Map.copyOf(masks)));
        Map<PackModel, Map<Chunk, Long>> result = Map.copyOf(frozen);
        if (done) {
            completed = key;
            completedModels = result;
            completedAt = System.nanoTime();
            request = null;
            scan = null;
            pending = null;
        }
        publish(frame, key, result, captured);
    }

    private static void publish(Frame frame, Key key, Map<PackModel, Map<Chunk, Long>> models, long time) {
        snapshots.put(
                frame.scene, new State(models, time, key.regions.hashCode(), key.floor, key.x, key.y, key.outdoor));
    }

    public static void begin(SceneData scene, GlProgram program) {
        drawing.remove();
        // Default shadow shaders lack uOPInterior; Iris shadow draws set it to
        // zero. Retain roof shadow casters to avoid changing room illumination.
        if (!CameraDrawing.drawTopDown()
                || !program.has("uOPInterior")
                || GL20.glGetUniformi(program.id(), program.location("uOPInterior")) == 0) {
            return;
        }
        State state = snapshots.get(scene);
        if (state != null && !state.models.isEmpty()) {
            drawing.set(new Drawing(scene, state));
        }
    }

    public static void end() {
        drawing.remove();
    }

    public static void mask(Object value, int run) {
        Drawing context = drawing.get();
        if (context == null) {
            return;
        }
        State state = context.state;
        Map<Chunk, Long> masks = state.models.get((PackModel) value);
        if (masks == null) {
            return;
        }
        try {
            if (runsField == null) {
                runsField = Class.forName("viewpoint.render.PackDraws").getDeclaredField("runs");
                runsField.setAccessible(true);
            }
            int[] runs = (int[]) runsField.get(null);
            int at = run * 4;
            if (at < 0 || at + 3 >= runs.length) {
                return;
            }
            int mesh = runs[at];
            SceneData scene = context.scene;
            if (mesh < 0 || mesh >= scene.meshCount) {
                return;
            }
            int x = (int) Math.round(-(scene.meshOffsets[mesh * 3] + scene.originX));
            int y = (int) Math.round(-(scene.meshOffsets[mesh * 3 + 2] + scene.originZ));
            Long hidden = masks.get(new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8), scene.meshes[mesh].level));
            if (hidden == null) {
                return;
            }
            long visible = Integer.toUnsignedLong(runs[at + 2]) | ((long) runs[at + 3] << 32);
            if (!reportedActive && (visible & hidden) != 0) {
                reportedActive = true;
                PackModel model = (PackModel) value;
                System.out.println(
                        "[OldPoint] roof instance mask active: "
                                + (model.obj == null ? model.index : model.obj.getName()));
            }
            visible &= ~hidden;
            runs[at + 2] = (int) visible;
            runs[at + 3] = (int) (visible >>> 32);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] roof model cutaway unavailable: " + error);
            }
        }
    }
}
