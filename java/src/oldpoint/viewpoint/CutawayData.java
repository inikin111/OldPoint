package oldpoint.viewpoint;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import viewpoint.platform.GlProgram;

/** Room, wall, roof and independent-opening data; no general object rule table. */
public final class CutawayData {
    record Data(
            float[] values,
            int occupied,
            int rooms,
            int walls,
            int roofs,
            int wallCount,
            int lookup,
            int trees,
            int spatial,
            int shades) {}

    private static int buffer, texture;
    private static Object uploaded;
    private static Data uploadedData;

    private static final class Samplers {
        final int programId;
        final Map<String, Integer> values = new HashMap<>();
        final Map<String, Integer> locations = new HashMap<>();
        final Map<String, String> names = new HashMap<>();
        int unit = -1;

        Samplers(int id) {
            programId = id;
        }
    }

    private static final Map<GlProgram, Samplers> samplers = new WeakHashMap<>();
    private static int unitLimit;
    private static final Map<Integer, Integer> previousBindings = new HashMap<>();

    private CutawayData() {}

    static void upload(Object key, Data data) {
        ensure();
        if (uploaded == key) {
            return;
        }
        uploaded = key;
        // Sampler assignment caching is independent of scene snapshots.
        if (uploadedData == data) {
            return;
        }
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, data.values, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
        uploadedData = data;
    }

    private static void ensure() {
        if (texture != 0) {
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        int oldBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        buffer = GL15.glGenBuffers();
        texture = GL11.glGenTextures();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, new float[4], GL15.GL_STREAM_DRAW);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, oldTexture);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, oldBuffer);
        GL13.glActiveTexture(active);
    }

    static void bind(GlProgram program) {
        if (!program.has("uOPData")) {
            return;
        }
        ensure();
        Samplers cached = samplers.get(program);
        if (cached == null || cached.programId != program.id()) {
            cached = samplerLocations(program);
            samplers.put(program, cached);
        }
        if (cached.unit < 0) {
            if (unitLimit == 0) {
                unitLimit = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            }
            boolean[] used = new boolean[unitLimit];
            for (var entry : cached.locations.entrySet()) {
                int occupied = GL20.glGetUniformi(program.id(), entry.getValue());
                cached.values.put(entry.getKey(), occupied);
                if (occupied >= 0 && occupied < unitLimit) {
                    used[occupied] = true;
                }
            }
            int unit = unitLimit - 1;
            while (unit >= 0 && used[unit]) {
                unit--;
            }
            if (unit < 0) {
                throw new IllegalStateException("OldPoint rule data: no free texture sampler unit");
            }
            cached.unit = unit;
        }
        int unit = cached.unit;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        if (!previousBindings.containsKey(unit)) {
            previousBindings.put(unit, GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER));
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        program.setInt("uOPData", unit);
        GL13.glActiveTexture(active);
    }

    private static Samplers samplerLocations(GlProgram program) {
        Samplers result = new Samplers(program.id());
        int count = GL20.glGetProgrami(program.id(), GL20.GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < count; i++) {
            int type = GL31.glGetActiveUniformsi(program.id(), i, GL31.GL_UNIFORM_TYPE);
            boolean sampler =
                    type >= 0x8B5D && type <= 0x8B64
                            || type >= 0x8DC0 && type <= 0x8DD8
                            || type >= 0x9108 && type <= 0x910D
                            || type >= 0x900C && type <= 0x900F;
            if (!sampler) {
                continue;
            }
            String name = GL31.glGetActiveUniformName(program.id(), i);
            if (name.equals("uOPData")) {
                continue;
            }
            int size = GL31.glGetActiveUniformsi(program.id(), i, GL31.GL_UNIFORM_SIZE);
            for (int n = 0; n < size; n++) {
                String element = size == 1 ? name : name.replace("[0]", "[" + n + "]");
                int location = GL20.glGetUniformLocation(program.id(), element);
                if (location >= 0) {
                    result.locations.put(element, location);
                    result.names.put(element, element);
                    if (element.endsWith("[0]")) {
                        result.names.put(element.substring(0, element.length() - 3), element);
                    }
                }
            }
        }
        return result;
    }

    /** Called after Viewpoint's integer uniform setters; ordinary uniforms do no work. */
    public static void samplerChanged(GlProgram program, String name, int value) {
        if ("uOPData".equals(name)) {
            return;
        }
        Samplers state = samplers.get(program);
        if (state == null) {
            return;
        }
        String key = state.names.get(name);
        if (key == null) {
            return;
        }
        Integer previous = state.values.put(key, value);
        if (previous == null || previous != value) {
            state.unit = -1;
        }
    }

    public static void invalidateSamplers(GlProgram program) {
        samplers.remove(program);
    }

    @Patch(className = "viewpoint.platform.GlProgram", methodName = "setInt", strictMatch = true)
    public static final class IntegerPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.This GlProgram program, @Patch.Argument(0) String name, @Patch.Argument(1) int value) {
            CutawayData.samplerChanged(program, name, value);
        }
    }

    @Patch(className = "viewpoint.platform.GlProgram", methodName = "sampler", strictMatch = true)
    public static final class SamplerPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.This GlProgram program, @Patch.Argument(0) String name, @Patch.Argument(1) int value) {
            CutawayData.samplerChanged(program, name, value);
        }
    }

    @Patch(className = "viewpoint.platform.GlProgram", methodName = "adopt", strictMatch = true)
    public static final class RelinkPatch {
        @Patch.OnExit
        public static void exit(@Patch.This GlProgram program) {
            CutawayData.invalidateSamplers(program);
        }
    }

    @Patch(className = "viewpoint.platform.GlProgram", methodName = "delete", strictMatch = true)
    public static final class DeletePatch {
        @Patch.OnExit
        public static void exit(@Patch.This GlProgram program) {
            CutawayData.invalidateSamplers(program);
        }
    }

    static void end() {
        if (previousBindings.isEmpty()) {
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        previousBindings.forEach(
                (unit, value) -> {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                    GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, value);
                });
        GL13.glActiveTexture(active);
        previousBindings.clear();
    }

    // This block is also appended immediately after a varying declaration.
    // Preprocessor directives must start on a fresh line in every caller.
    static final String SHADER =
            "\n"
                    + """
        #ifndef OLDPOINT_CUTAWAY_DATA
        #define OLDPOINT_CUTAWAY_DATA
        uniform samplerBuffer uOPData;
        uniform int uOPOccupiedOffset;
        uniform int uOPRoomOffset;
        uniform int uOPWallOffset;
        uniform int uOPRoofOffset;
        uniform int uOPSpatialOffset;
        uniform vec4 uOPSpatialGrid;
        ivec4 opSpatial(vec2 tile) {
          ivec2 c=ivec2(floor(tile));
          if (uOPSpatialGrid.z>0.0) {
            ivec2 local=c-ivec2(uOPSpatialGrid.xy);
            int width=int(uOPSpatialGrid.z);
            int height=int(uOPSpatialGrid.w)/width;
            if (any(lessThan(local,ivec2(0))) || local.x>=width || local.y>=height) return ivec4(0);
            return ivec4(texelFetch(uOPData,uOPSpatialOffset+1+local.y*width+local.x));
          }
          // Conservative fallback for unusually large extents.
          uint h=(uint(c.x)*73856093u)^(uint(c.y)*19349663u);
          return ivec4(texelFetch(uOPData,uOPSpatialOffset+1+int(h&255u)));
        }
        int opSpatialRule(int index) {
          vec4 ids=texelFetch(uOPData,uOPSpatialOffset+1+int(uOPSpatialGrid.w)+index/4);
          return int(ids[index%4]);
        }
        vec4 opOpeningRoot(int i) { return texelFetch(uOPData,i*4); }
        vec4 opOpeningMin(int i) { return texelFetch(uOPData,i*4+1); }
        vec4 opOpeningMax(int i) { return texelFetch(uOPData,i*4+2); }
        vec4 opOpeningArt(int i) { return texelFetch(uOPData,i*4+3); }
        vec4 opOccupied(int i) { return texelFetch(uOPData,uOPOccupiedOffset+i); }
        vec4 opRoom(int i) { return texelFetch(uOPData,uOPRoomOffset+i); }
        vec4 opWall(int i) { return texelFetch(uOPData,uOPWallOffset+i*2); }
        float opFade(int i) { return texelFetch(uOPData,uOPWallOffset+i*2+1).x; }
        vec2 opWallEnds(int i) { return texelFetch(uOPData,uOPWallOffset+i*2+1).yz; }
        vec4 opRoofRect(int i) { return texelFetch(uOPData,uOPRoofOffset+i); }
        #endif
        """;
}
