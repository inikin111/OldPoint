package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import viewpoint.core.Frame;
import viewpoint.platform.Gl;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/** Full player coverage and occluded coverage are kept separate, avoiding cut-edge outlines. */
public final class PlayerOutline {
    private record Range(int first, int end, float x, float y, float z) {}

    private record Depth(SceneData scene, int texture, Matrix4f vp) {}

    private static final Map<SceneData, Range> ranges = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Integer> firstModel = new ThreadLocal<>();
    private static volatile Depth irisDepth;
    private static GlProgram mask, edge;
    private static int fbo, colour, depth, width, height;
    private static org.lwjgl.opengl.GLCapabilities context;
    private static boolean failed;
    private static volatile boolean enabled;

    private PlayerOutline() {}

    public static void enabled(boolean value) {
        enabled = value;
        if (!value) {
            ranges.clear();
            irisDepth = null;
        }
    }

    public static void reset(SceneData scene) {
        ranges.remove(scene);
    }

    public static void begin(Frame frame, IsoGameCharacter character) {
        firstModel.remove();
        if (enabled && CameraController.topDownActive() && character == IsoPlayer.getInstance()) {
            firstModel.set(frame.scene.models.count());
        }
    }

    public static void end(Frame frame, IsoGameCharacter character) {
        Integer first = firstModel.get();
        firstModel.remove();
        if (enabled && first != null && CameraController.topDownActive() && character == IsoPlayer.getInstance()) {
            ranges.put(
                    frame.scene,
                    new Range(
                            first,
                            frame.scene.models.count(),
                            (float) (-character.getX() - frame.scene.originX),
                            (float) (character.getZ() * 2.4494896 - frame.scene.originY),
                            (float) (-character.getY() - frame.scene.originZ)));
        }
    }

    public static void iris(Object pipeline, FrameContext frame) {
        if (!enabled || failed || !CameraDrawing.drawTopDown()) {
            return;
        }
        try {
            Object targets = field(pipeline.getClass(), "targets").get(pipeline);
            Object uniforms = field(pipeline.getClass(), "uniforms").get(pipeline);
            Matrix4f projection = (Matrix4f) field(uniforms.getClass(), "projection").get(uniforms);
            irisDepth =
                    new Depth(
                            frame.scene,
                            field(targets.getClass(), "depth").getInt(targets),
                            new Matrix4f(projection).mul(frame.view));
        } catch (ReflectiveOperationException error) {
            fail(error);
        }
    }

    public static void draw(Object models) {
        if (!enabled || failed || !CameraDrawing.drawTopDown()) {
            return;
        }
        try {
            FrameContext frame = (FrameContext) field(WorldRenderer.class, "frame").get(null);
            Range range = ranges.get(frame.scene);
            if (range == null || range.end <= range.first) {
                return;
            }
            boolean iris = field(WorldRenderer.class, "irisDrawn").getBoolean(null);
            Depth scene;
            if (iris) {
                Depth captured = irisDepth;
                if (captured == null || captured.scene != frame.scene) {
                    return;
                }
                scene = captured;
            } else {
                Object targets = field(WorldRenderer.class, "targets").get(null);
                scene =
                        new Depth(
                                frame.scene,
                                field(targets.getClass(), "depth").getInt(targets),
                                new Matrix4f(frame.viewProjection));
            }
            if (scene.texture != 0) {
                render(models, frame, range, scene, iris);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error);
        }
    }

    private static void render(Object models, FrameContext frame, Range range, Depth scene, boolean iris)
            throws ReflectiveOperationException {
        if (context != org.lwjgl.opengl.GL.getCapabilities()
                && glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) <= 63) {
            throw new IllegalStateException("Player mask texture unit is unavailable");
        }
        Object draws = frame.scene.models;
        int[] flags = (int[]) field(draws.getClass(), "flags").get(draws), savedFlags = flags.clone();
        Field targeted = field(draws.getClass(), "targeted"), programField = field(models.getClass(), "outline");
        boolean oldTargeted = targeted.getBoolean(draws);
        Object oldOutline = programField.get(models);
        Matrix4f plain = (Matrix4f) field(FrameContext.class, "plainViewProjection").get(frame),
                oldPlain = new Matrix4f(plain);
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM),
                active = glGetInteger(GL_ACTIVE_TEXTURE),
                vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int[] oldScissor = new int[4];
        glGetIntegerv(GL_SCISSOR_BOX, oldScissor);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST),
                blend = glIsEnabled(GL_BLEND),
                test = glIsEnabled(GL_DEPTH_TEST),
                cull = glIsEnabled(GL_CULL_FACE);
        boolean writeDepth = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        int srcRgb = glGetInteger(GL_BLEND_SRC_RGB),
                dstRgb = glGetInteger(GL_BLEND_DST_RGB),
                srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA),
                dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glActiveTexture(GL_TEXTURE0 + 63);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D), oldSampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            ensure(frame.outputWidth, frame.outputHeight);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_BLEND);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, width, height);
            glClearColor(0, 0, 0, 0);
            glClearDepth(1);
            glDepthMask(true);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            mask.use();
            mask.set("uOPSceneVP", scene.vp);
            mask.set("uOPSceneInverse", new Matrix4f(scene.vp).invert());
            mask.set("uOPEye", frame.eyeX, frame.eyeY, frame.eyeZ);
            Gl.bind(63, scene.texture);
            glBindSampler(63, 0);
            for (int i = 0; i < frame.scene.models.count(); i++) {
                flags[i] = (flags[i] & ~16) | (i >= range.first && i < range.end ? 16 : 0);
            }
            targeted.setBoolean(draws, true);
            programField.set(models, mask);
            if (iris) {
                plain.set(scene.vp);
            }
            Method target = models.getClass().getDeclaredMethod("targets", FrameContext.class);
            target.setAccessible(true);
            target.invoke(models, frame);
            System.arraycopy(savedFlags, 0, flags, 0, flags.length);
            targeted.setBoolean(draws, oldTargeted);
            programField.set(models, oldOutline);
            plain.set(oldPlain);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            scissor(range, iris ? scene.vp : oldPlain, viewport);
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            edge.use();
            Gl.bind(63, colour);
            glBindSampler(63, 0);
            Gl.screenTriangle();
        } finally {
            System.arraycopy(savedFlags, 0, flags, 0, flags.length);
            targeted.setBoolean(draws, oldTargeted);
            programField.set(models, oldOutline);
            plain.set(oldPlain);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glActiveTexture(GL_TEXTURE0 + 63);
            glBindTexture(GL_TEXTURE_2D, oldTexture);
            glBindSampler(63, oldSampler);
            glActiveTexture(active);
            glUseProgram(oldProgram);
            glBindVertexArray(vao);
            if (scissor) {
                glEnable(GL_SCISSOR_TEST);
            } else {
                glDisable(GL_SCISSOR_TEST);
            }
            glScissor(oldScissor[0], oldScissor[1], oldScissor[2], oldScissor[3]);
            if (blend) {
                glEnable(GL_BLEND);
            } else {
                glDisable(GL_BLEND);
            }
            if (test) {
                glEnable(GL_DEPTH_TEST);
            } else {
                glDisable(GL_DEPTH_TEST);
            }
            if (cull) {
                glEnable(GL_CULL_FACE);
            } else {
                glDisable(GL_CULL_FACE);
            }
            glDepthMask(writeDepth);
            glDepthFunc(depthFunc);
            org.lwjgl.opengl.GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    private static void scissor(Range range, Matrix4f vp, int[] viewport) {
        float minX = 1, minY = 1, maxX = -1, maxY = -1;
        for (int i = 0; i < 8; i++) {
            Vector4f p =
                    new Vector4f(
                            range.x + ((i & 1) == 0 ? -1.5f : 1.5f),
                            range.y + ((i & 2) == 0 ? -0.5f : 3f),
                            range.z + ((i & 4) == 0 ? -1.5f : 1.5f),
                            1);
            vp.transform(p);
            if (p.w <= 0) {
                return;
            }
            minX = Math.min(minX, p.x / p.w);
            maxX = Math.max(maxX, p.x / p.w);
            minY = Math.min(minY, p.y / p.w);
            maxY = Math.max(maxY, p.y / p.w);
        }
        int x = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * viewport[2]) - 4);
        int y = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * viewport[3]) - 4);
        int x2 = Math.min(viewport[2], (int) Math.ceil((maxX * 0.5f + 0.5f) * viewport[2]) + 4);
        int y2 = Math.min(viewport[3], (int) Math.ceil((maxY * 0.5f + 0.5f) * viewport[3]) + 4);
        glEnable(GL_SCISSOR_TEST);
        glScissor(viewport[0] + x, viewport[1] + y, Math.max(0, x2 - x), Math.max(0, y2 - y));
    }

    private static void ensure(int w, int h) throws ReflectiveOperationException {
        var current = org.lwjgl.opengl.GL.getCapabilities();
        if (context != current) {
            // Names belong to the old context: deleting them in a new context
            // could delete unrelated resources with recycled numeric IDs.
            context = current;
            fbo = colour = depth = width = height = 0;
            mask = edge = null;
        }
        if (mask == null) {
            mask = GlProgram.create("OldPoint player mask", "model.vert", "oldpoint_player_mask.frag");
            // ModelPass.samplers also binds uTexture, which this mask does not
            // declare. Bind only the vertex-shader buffers it actually uses.
            mask.sampler("uPalettes", 31);
            mask.sampler("uDraws", 40);
            mask.sampler("uOPSceneDepth", 63);
            edge = GlProgram.create("OldPoint player edge", "oldpoint_player_screen.vert", "oldpoint_player_edge.frag");
            edge.sampler("uOPMask", 63);
        }
        if (fbo != 0 && width == w && height == h) {
            return;
        }
        int maxSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (w <= 0 || h <= 0 || w > maxSize || h > maxSize) {
            throw new IllegalStateException("Invalid player mask dimensions: " + w + "x" + h);
        }
        int nextColour = 0, nextDepth = 0, nextFbo = 0;
        try {
            nextColour = Gl.texture(GL_RGBA8, w, h, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
            nextDepth = Gl.texture(GL_DEPTH_COMPONENT24, w, h, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
            nextFbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, nextFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, nextColour, 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, nextDepth, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if (nextColour == 0
                    || nextDepth == 0
                    || nextFbo == 0
                    || glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete player mask framebuffer");
            }
            int oldFbo = fbo, oldColour = colour, oldDepth = depth;
            fbo = nextFbo;
            colour = nextColour;
            depth = nextDepth;
            width = w;
            height = h;
            nextFbo = nextColour = nextDepth = 0;
            if (oldFbo != 0) {
                glDeleteFramebuffers(oldFbo);
            }
            if (oldColour != 0) {
                glDeleteTextures(oldColour);
            }
            if (oldDepth != 0) {
                glDeleteTextures(oldDepth);
            }
        } finally {
            if (nextFbo != 0) {
                glDeleteFramebuffers(nextFbo);
            }
            if (nextColour != 0) {
                glDeleteTextures(nextColour);
            }
            if (nextDepth != 0) {
                glDeleteTextures(nextDepth);
            }
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void fail(Exception error) {
        if (failed) {
            return;
        }
        failed = true;
        System.out.println("[OldPoint] occluded player outline disabled: " + error);
        error.printStackTrace(System.out);
    }

    public static String shader(String path, String source) {
        return switch (path) {
            case "oldpoint_player_mask.frag" -> MASK;
            case "oldpoint_player_edge.frag" -> EDGE;
            case "oldpoint_player_screen.vert" -> SCREEN;
            default -> source;
        };
    }

    private static final String SCREEN =
            """
        #version 330
        out vec2 vUv;
        void main() {
          vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
          vUv=p; gl_Position=vec4(p*2.0-1.0,0.0,1.0);
        }
        """;
    private static final String MASK =
            """
        #version 330
        #extension GL_ARB_gpu_shader5 : enable
        in vec2 vUv;
        in vec3 vPos;
        #include "constants.glsl"
        uniform sampler2D uTextures[MODEL_TEXTURES];
        flat in int vTexture;
        uniform sampler2D uOPSceneDepth;
        uniform mat4 uOPSceneVP;
        uniform mat4 uOPSceneInverse;
        uniform vec3 uOPEye;
        out vec4 fragColor;
        void main() {
          if (texture(uTextures[vTexture],vUv).a<0.5) discard;
          vec4 clip=uOPSceneVP*vec4(vPos,1.0);
          vec2 uv=clip.xy/clip.w*0.5+0.5;
          float hidden=0.0;
          if (clip.w>0.0 && all(greaterThanEqual(uv,vec2(0.0))) && all(lessThan(uv,vec2(1.0)))) {
            ivec2 size=textureSize(uOPSceneDepth,0);
            ivec2 pixel=clamp(ivec2(uv*vec2(size)),ivec2(0),size-1);
            float z=texelFetch(uOPSceneDepth,pixel,0).r;
            vec2 sampleUv=(vec2(pixel)+0.5)/vec2(size);
            vec4 world=uOPSceneInverse*vec4(sampleUv*2.0-1.0,z*2.0-1.0,1.0);
            if (z<1.0 && abs(world.w)>0.00001)
              hidden=float(length(vPos-uOPEye)>length(world.xyz/world.w-uOPEye)+0.20);
          }
          fragColor=vec4(1.0,hidden,0.0,1.0);
        }
        """;
    private static final String EDGE =
            """
        #version 330
        in vec2 vUv;
        uniform sampler2D uOPMask;
        out vec4 fragColor;
        void main() {
          ivec2 size=textureSize(uOPMask,0),pixel=ivec2(vUv*vec2(size));
          // The complete body masks the interior, so occlusion boundaries add no line.
          if (texelFetch(uOPMask,clamp(pixel,ivec2(0),size-1),0).r>0.5) discard;
          float alpha=0.0;
          for (int y=-2;y<=2;y++) for (int x=-2;x<=2;x++) {
            if (x*x+y*y>5) continue;
            vec2 m=texelFetch(uOPMask,clamp(pixel+ivec2(x,y),ivec2(0),size-1),0).rg;
            alpha=max(alpha,m.r*m.g);
          }
          if (alpha<=0.0) discard;
          fragColor=vec4(1.0,1.0,1.0,alpha);
        }
        """;
}
