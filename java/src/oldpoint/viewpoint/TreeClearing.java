package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import viewpoint.core.Frame;
import viewpoint.render.PackModel;
import viewpoint.render.SceneData;
import viewpoint.render.FrameContext;
import viewpoint.platform.GlProgram;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoTree;

/** Per-frame tree instance masks. Never alters world objects or baked chunks. */
public final class TreeClearing {
    private record Chunk(int x, int y, int level) { }
    record Selection(int x,int y,int width,int height,int floor,float[] cells,Map<Chunk,Long> hidden) { }
    private static final Selection EMPTY=new Selection(0,0,0,0,0,new float[0],Map.of());
    private record State(SceneData scene, Selection selection) {
        Map<Chunk,Long> hidden() { return selection.hidden; }
    }
    private static volatile boolean enabled = true;
    private static volatile float radius = 10;
    private static final Map<SceneData, Selection> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<State> drawing = new ThreadLocal<>();
    private static final Map<PackModel, Boolean> treeModels = new WeakHashMap<>();
    private static Field runsField;
    private static boolean reported;
    private static boolean reportedActive;
    private static boolean outlinePass, failed;
    private static Boolean savedTargeted;
    private static Field outlineMask;
    private static Field targetedField, projectionField;
    private static Method drawTarget;
    private static Method drawNative;
    private static GlProgram nativeMask;
    private TreeClearing() { }

    public static void configure(boolean value, double distance) {
        if (Double.isFinite(distance)) radius = (float) Math.max(0, Math.min(36, distance));
        enabled = value && radius > 0;
        if (!enabled) clear();
    }
    public static void clear() { snapshots.clear(); }
    public static void reset(SceneData scene) { snapshots.remove(scene); }
    static boolean active() { return enabled && !failed; }
    static Selection selection(SceneData scene) { return enabled && !failed?snapshots.getOrDefault(scene,EMPTY):EMPTY; }
    public static void begin(SceneData scene) {
        Selection selected = snapshots.get(scene);
        drawing.set(selected == null ? null : new State(scene, selected));
    }
    public static void end() { drawing.remove(); }

    public static void capture(Frame frame, IsoGameCharacter player, float cameraX, float cameraY) {
        if (!enabled || radius<=0 || IsoWorld.instance == null || IsoWorld.instance.getCell() == null) {
            reset(frame.scene);
            return;
        }
        float range = radius;
        int level = (int) Math.floor(player.getZ());
        float px = player.getX(), py = player.getY();
        // Player is the point on the circle farthest from the camera: shift the
        // centre one full radius toward the actual camera, including aim pan.
        float horizontal = (float) Math.sqrt(cameraX * cameraX + cameraY * cameraY);
        if (Float.isFinite(horizontal) && horizontal > 0.0001f) {
            px += cameraX / horizontal * range;
            py += cameraY / horizontal * range;
        }
        int minX=(int)Math.floor(px-range),minY=(int)Math.floor(py-range);
        int width=(int)Math.ceil(px+range)-minX+1,height=(int)Math.ceil(py+range)-minY+1;
        float[] cells=new float[width*height];
        HashMap<Chunk, Long> masks = new HashMap<>();
        var cell = IsoWorld.instance.getCell();
        for (int y = (int) Math.floor(py - range); y <= Math.ceil(py + range); y++) {
            for (int x = (int) Math.floor(px - range); x <= Math.ceil(px + range); x++) {
                float dx = x + 0.5f - px, dy = y + 0.5f - py;
                if (dx * dx + dy * dy > range * range) continue;
                var square = cell.getGridSquare(x, y, level);
                if (square == null) continue;
                boolean tree = false;
                // PZArrayList deliberately does not implement iterator().
                var objects = square.getObjects();
                for (int i = 0, count = objects.size(); i < count; i++) {
                    if (objects.get(i) instanceof IsoTree) { tree = true; break; }
                }
                if (!tree) continue;
                cells[(y-minY)*width+x-minX]=1;
                Chunk chunk = new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8), level);
                long bit = 1L << (Math.floorMod(y, 8) * 8 + Math.floorMod(x, 8));
                masks.merge(chunk, bit, (a, b) -> a | b);
            }
        }
        snapshots.put(frame.scene,new Selection(minX,minY,width,height,level,cells,Map.copyOf(masks)));
    }

    public static void mask(Object value, int runIndex) {
        State state = drawing.get();
        if (!enabled || failed || state == null || state.hidden().isEmpty()) return;
        PackModel model = (PackModel) value;
        // Match the installed 3D tree packs by asset directory, then require
        // an actual IsoTree tile below. Other packs/furniture stay untouched.
        boolean isTree = treeModels.computeIfAbsent(model, m -> {
            if (m.obj == null) return false;
            String path = m.obj.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
            return path.contains("/nearvegetation/") || path.contains("/pzt3d_trees/");
        });
        if (!isTree && !outlinePass) return;
        try {
            if (runsField == null) {
                runsField = Class.forName("viewpoint.render.PackDraws").getDeclaredField("runs");
                runsField.setAccessible(true);
            }
            int[] runs = (int[]) runsField.get(null);
            int at = runIndex * 4;
            if (at < 0 || at + 3 >= runs.length) return;
            if (outlinePass && !isTree) {
                runs[at + 2] = runs[at + 3] = 0;
                return;
            }
            int mesh = runs[at];
            SceneData scene = state.scene;
            if (mesh < 0 || mesh >= scene.meshCount) return;
            // Mesh origins are negative world X/Y, relative to the scene origin.
            int x = (int) Math.round(-(scene.meshOffsets[mesh * 3] + scene.originX));
            int y = (int) Math.round(-(scene.meshOffsets[mesh * 3 + 2] + scene.originZ));
            Long hidden = state.hidden().get(new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8),
                    scene.meshes[mesh].level));
            if (hidden == null) return;
            long visible = Integer.toUnsignedLong(runs[at + 2]) | ((long) runs[at + 3] << 32);
            visible = outlinePass ? visible & hidden : visible & ~hidden;
            runs[at + 2] = (int) visible;
            runs[at + 3] = (int) (visible >>> 32);
            if (!reportedActive) {
                reportedActive = true;
                System.out.println("[OldPoint] nearby 3D tree clearing active: " + model.obj.getName());
            }
        } catch (ReflectiveOperationException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] 3D tree clearing error: " + error);
            }
        }
    }

    /** Ensure the existing mask pass also runs when no mouse target is selected. */
    public static void beforeOutline(FrameContext frame) {
        State state = drawing.get();
        if (!enabled || failed || state == null || state.hidden().isEmpty()) return;
        try {
            if (drawTarget == null) {
                Field mask = Class.forName("viewpoint.render.TargetOutline").getDeclaredField("mask");
                Method draw = Class.forName("viewpoint.render.PackDraws").getDeclaredMethod(
                        "drawTarget", SceneData.class, GlProgram.class, int.class, long.class);
                mask.setAccessible(true);
                draw.setAccessible(true);
                targetedField = frame.scene.models.getClass().getDeclaredField("targeted");
                projectionField = FrameContext.class.getDeclaredField("plainViewProjection");
                targetedField.setAccessible(true);
                projectionField.setAccessible(true);
                outlineMask = mask;
                drawTarget = draw;
                drawNative=Class.forName("viewpoint.render.Meshes").getDeclaredMethod("draw",
                    SceneData.class,GlProgram.class,byte.class,boolean[].class);
                drawNative.setAccessible(true);
            }
            savedTargeted = targetedField.getBoolean(frame.scene.models);
            targetedField.setBoolean(frame.scene.models, true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            failOutline(error);
        }
    }

    public static void afterOutlineWorld(Object renderer, FrameContext frame) {
        restoreTargeted(frame);
        State state = drawing.get();
        if (!enabled || failed || state == null || state.hidden().isEmpty() || drawTarget == null) return;
        try {
            GlProgram program = (GlProgram) outlineMask.get(renderer);
            program.use();
            program.set("uViewProjection", (org.joml.Matrix4f) projectionField.get(frame));
            outlinePass = true;
            SceneData scene = frame.scene;
            for (int mesh = 0; mesh < scene.meshCount; mesh++) {
                if (scene.meshes[mesh].arenaFirst < 0 || !scene.meshes[mesh].hasModels()) continue;
                int x = (int) Math.round(-(scene.meshOffsets[mesh * 3] + scene.originX));
                int y = (int) Math.round(-(scene.meshOffsets[mesh * 3 + 2] + scene.originZ));
                Long trees = state.hidden().get(new Chunk(Math.floorDiv(x, 8), Math.floorDiv(y, 8),
                        scene.meshes[mesh].level));
                if (trees != null) drawTarget.invoke(null, scene, program, mesh, trees.longValue());
            }
            // Draw folded cards into the same silhouette mask. Restrict the
            // batch to selected tree chunks; the vertex mask rejects all other
            // surfaces, packed models, shrubs and grass within those chunks.
            if (nativeMask==null) {
                nativeMask=GlProgram.create("OldPoint native tree mask","shadow.vert","target_mask.frag");
                nativeMask.sampler("uTexture",0);
                nativeMask.sampler("uPlantSlots",48);
            }
            boolean[] chosen=new boolean[scene.meshCount];
            for (int mesh=0;mesh<scene.meshCount;mesh++) {
                int x=(int)Math.round(-(scene.meshOffsets[mesh*3]+scene.originX));
                int y=(int)Math.round(-(scene.meshOffsets[mesh*3+2]+scene.originZ));
                chosen[mesh]=state.hidden().containsKey(new Chunk(Math.floorDiv(x,8),Math.floorDiv(y,8),scene.meshes[mesh].level));
            }
            nativeMask.use();
            nativeMask.set("uViewProjection",(org.joml.Matrix4f)projectionField.get(frame));
            nativeMask.set("uEye",frame.eyeX,frame.eyeY,frame.eyeZ);
            nativeMask.setInt("uOPTreeMaskPass",1);
            try { drawNative.invoke(null,scene,nativeMask,SceneData.VISIBLE,chosen); }
            finally { nativeMask.setInt("uOPTreeMaskPass",0); }
        } catch (ReflectiveOperationException | RuntimeException error) {
            failOutline(error);
        } finally {
            outlinePass = false;
        }
    }

    public static void restoreTargeted(FrameContext frame) {
        if (savedTargeted != null) {
            try {
                targetedField.setBoolean(frame.scene.models, savedTargeted);
            } catch (IllegalAccessException | RuntimeException error) {
                failOutline(error);
            } finally {
                savedTargeted = null;
            }
        }
    }

    private static void failOutline(Exception error) {
        failed = true;
        System.out.println("[OldPoint] tree outline unavailable; solid trees retained: " + error);
    }
}
