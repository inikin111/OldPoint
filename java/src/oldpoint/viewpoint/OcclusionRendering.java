package oldpoint.viewpoint;

import java.lang.reflect.Field;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;

/** Drawing-thread state and shader uniforms; selection and packing stay outside this module. */
final class OcclusionRendering {
    private static final float STOREY = 2.4494896f;
    private static final int MAX_ROOMS = OcclusionRules.MAX_ROOMS, MAX_WALLS = OcclusionRules.MAX_WALLS;
    private static final ThreadLocal<OcclusionSnapshot> drawing = new ThreadLocal<>();

    private OcclusionRendering() {}

    static void begin(OcclusionSnapshot snapshot) {
        drawing.set(snapshot);
    }

    static void end() {
        try {
            CutawayData.end();
        } finally {
            drawing.remove();
        }
    }

    static void uniforms(Object value) {
        GlProgram program = (GlProgram) value;
        OpeningFade.program(program);
        if (program.has("uOPNoCameraClear")) {
            program.setInt("uOPNoCameraClear", drawing.get() != null && CameraDrawing.drawTopDown() ? 1 : 0);
        }
        if (!program.has("uOPInterior") && !program.has("uOPTreeEnabled")) {
            return;
        }
        upload(program, drawing.get(), false);
    }

    static void irisUniforms(Object value, Object context, boolean shadow) {
        try {
            GlProgram program = (GlProgram) field(value.getClass(), "gl").get(value);
            FrameContext frame = (FrameContext) context;
            if (program.has("uOPNoCameraClear")) {
                program.setInt(
                        "uOPNoCameraClear",
                        Interior.snapshot(frame.scene) != null && CameraDrawing.drawTopDown() && !shadow ? 1 : 0);
            }
            if (program.has("uOPKeepNearGeometry")) {
                program.setInt(
                        "uOPKeepNearGeometry",
                        Interior.snapshot(frame.scene) != null && CameraDrawing.drawTopDown() && !shadow ? 1 : 0);
            }
            if (program.has("uOPInterior") || program.has("uOPTreeEnabled")) {
                upload(program, Interior.snapshot(frame.scene), shadow);
            }
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    private static void upload(GlProgram p, OcclusionSnapshot s, boolean shadow) {
        boolean enabled = s != null && CameraDrawing.drawTopDown() && !shadow;
        if (p.has("uOPInterior")) {
            p.setInt("uOPInterior", enabled ? 1 : 0);
        }
        if (p.has("uOPTreeEnabled")) {
            p.setInt("uOPTreeEnabled", enabled && TreeClearing.active() && s.trees().cells().length > 0 ? 1 : 0);
        }
        if (!enabled) {
            CutawayData.bind(p);
            return;
        }
        p.set("uOPOrigin", (float) s.originX(), (float) s.originY(), (float) s.originZ());
        p.set("uOPFloor", (s.floor() + 1) * STOREY);
        p.set("uOPPlayer", s.playerX(), s.playerY(), s.playerZ());
        p.set("uOPDirection", s.directionX(), s.directionY(), s.directionZ());
        p.set("uOPEye", s.eyeX(), s.eyeY(), s.eyeZ());
        p.setInt("uOPBoundaryCount", s.boundary().size());
        p.setInt("uOPRoomCount", Math.min(MAX_ROOMS, s.upperFloors().size()));
        p.setInt("uOPForeignUpperCount", s.foreignUpperCount());
        p.setInt("uOPRoomShadeCount", s.roomShades().size());
        CutawayData.Data packedRules = OcclusionRules.forSnapshot(s);
        CutawayData.upload(s, packedRules);
        CutawayData.bind(p);
        p.setInt("uOPOccupiedOffset", packedRules.occupied());
        p.setInt("uOPRoomOffset", packedRules.rooms());
        p.setInt("uOPWallOffset", packedRules.walls());
        p.setInt("uOPRoofOffset", packedRules.roofs());
        p.setInt("uOPWallCount", packedRules.wallCount());
        p.setInt("uOPStructureWallCount", Math.min(s.walls().size(), MAX_WALLS));
        p.setInt("uOPSpatialOffset", packedRules.spatial());
        int grid = packedRules.spatial() * 4;
        float[] data = packedRules.values();
        p.set("uOPSpatialGrid", data[grid], data[grid + 1], data[grid + 2], data[grid + 3]);
        p.setInt("uOPOpeningLookup", packedRules.lookup());
        p.setInt("uOPRoomShadeOffset", packedRules.shades());
        if (p.has("uOPTreeEnabled")) {
            p.setInt("uOPTreeOffset", packedRules.trees());
            p.set("uOPTreeGrid", s.trees().x(), s.trees().y(), s.trees().width(), s.trees().height());
            p.set("uOPTreeFloor", s.trees().floor() * STOREY);
            p.set("uOPTreeOrigin", (float) s.originX(), (float) s.originY(), (float) s.originZ());
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void report(Throwable error) {
        Interior.report(error);
    }
}
