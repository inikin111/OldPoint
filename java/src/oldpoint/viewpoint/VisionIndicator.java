package oldpoint.viewpoint;

import java.lang.reflect.Field;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;
import viewpoint.platform.Gl;
import viewpoint.platform.GlProgram;
import viewpoint.platform.SettingsWindow;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/** A ground-plane guide composited against existing scene depth, never over foreground bodies. */
public final class VisionIndicator {
    private record Depth(SceneData scene, int texture, Matrix4f vp) {}

    private static volatile Depth irisDepth;
    private static GlProgram program;
    private static boolean failed;

    private VisionIndicator() {}

    public static void iris(Object pipeline, FrameContext frame) {
        if (!VisionSystem.active() || failed) {
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

    public static void draw() {
        if (!VisionSystem.active() || !CameraDrawing.drawTopDown() || failed || SettingsWindow.shown()) {
            return;
        }
        try {
            FrameContext frame = (FrameContext) field(WorldRenderer.class, "frame").get(null);
            var v = VisionSystem.indicatorState(frame.scene);
            if (v == null) {
                return;
            }
            Depth depth;
            if (field(WorldRenderer.class, "irisDrawn").getBoolean(null)) {
                depth = irisDepth;
                if (depth == null || depth.scene != frame.scene) {
                    return;
                }
            } else {
                Object targets = field(WorldRenderer.class, "targets").get(null);
                depth =
                        new Depth(
                                frame.scene,
                                field(targets.getClass(), "depth").getInt(targets),
                                new Matrix4f(frame.viewProjection));
            }
            if (depth.texture != 0) {
                render(frame, depth, v);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error);
        }
    }

    private static void render(FrameContext frame, Depth depth, VisionSystem.Visual v) {
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM),
                active = glGetInteger(GL_ACTIVE_TEXTURE),
                vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int[] viewport = new int[4], scissorBox = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST),
                blend = glIsEnabled(GL_BLEND),
                test = glIsEnabled(GL_DEPTH_TEST),
                cull = glIsEnabled(GL_CULL_FACE);
        boolean write = glGetBoolean(GL_DEPTH_WRITEMASK);
        int srcRgb = glGetInteger(GL_BLEND_SRC_RGB),
                dstRgb = glGetInteger(GL_BLEND_DST_RGB),
                srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA),
                dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glActiveTexture(GL_TEXTURE0 + 63);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D), oldSampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            if (program == null) {
                program =
                        GlProgram.create(
                                "OldPoint vision guide", "oldpoint_vision_screen.vert", "oldpoint_vision_ground.frag");
                program.sampler("uVisionDepth", 63);
            }
            float x = (float) (-v.x() - frame.scene.originX),
                    y = (float) (v.z() * 2.4494896 - frame.scene.originY),
                    z = (float) (-v.y() - frame.scene.originZ);
            // Limit fragment work to a small projected box around the nearby guide.
            if (!scissor(depth.vp, x, y, z, viewport)) {
                return;
            }
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            program.use();
            program.set("uVisionVP", depth.vp);
            program.set("uVisionInverse", new Matrix4f(depth.vp).invert());
            program.set("uVisionCentre", x, y, z);
            program.set("uVisionHeading", v.heading(), (float) Math.toRadians(v.degrees()) * 0.5f);
            Gl.bind(63, depth.texture);
            glBindSampler(63, 0);
            Gl.screenTriangle();
        } finally {
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
            glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
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
            glDepthMask(write);
            org.lwjgl.opengl.GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    private static boolean scissor(Matrix4f vp, float x, float y, float z, int[] viewport) {
        float minX = 1, minY = 1, maxX = -1, maxY = -1;
        for (int i = 0; i < 8; i++) {
            Vector4f p =
                    new Vector4f(
                            x + ((i & 1) == 0 ? -0.95f : 0.95f),
                            y + ((i & 2) == 0 ? -0.15f : 0.15f),
                            z + ((i & 4) == 0 ? -0.95f : 0.95f),
                            1);
            vp.transform(p);
            if (p.w <= 0) {
                return false;
            }
            minX = Math.min(minX, p.x / p.w);
            maxX = Math.max(maxX, p.x / p.w);
            minY = Math.min(minY, p.y / p.w);
            maxY = Math.max(maxY, p.y / p.w);
        }
        int left = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * viewport[2]) - 3);
        int bottom = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * viewport[3]) - 3);
        int right = Math.min(viewport[2], (int) Math.ceil((maxX * 0.5f + 0.5f) * viewport[2]) + 3);
        int top = Math.min(viewport[3], (int) Math.ceil((maxY * 0.5f + 0.5f) * viewport[3]) + 3);
        if (right <= left || top <= bottom) {
            return false;
        }
        glEnable(GL_SCISSOR_TEST);
        glScissor(viewport[0] + left, viewport[1] + bottom, right - left, top - bottom);
        return true;
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
        System.out.println("[OldPoint] vision indicator disabled: " + error);
        error.printStackTrace(System.out);
    }

    public static String shader(String path, String source) {
        return switch (path) {
            case "oldpoint_vision_screen.vert" -> SCREEN;
            case "oldpoint_vision_ground.frag" -> FRAGMENT;
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
    private static final String FRAGMENT =
            """
        #version 330
        in vec2 vUv;
        out vec4 colour;
        uniform sampler2D uVisionDepth;
        uniform mat4 uVisionVP;
        uniform mat4 uVisionInverse;
        uniform vec3 uVisionCentre;
        uniform vec2 uVisionHeading;
        vec3 unproject(float depth) {
          vec4 p=uVisionInverse*vec4(vUv*2.0-1.0,depth,1.0);
          return p.xyz/p.w;
        }
        float edgeDistance(vec2 p,float angle) {
          vec2 direction=vec2(cos(angle),sin(angle));
          float along=clamp(dot(p,direction),0.5,0.8);
          return length(p-direction*along);
        }
        void main() {
          float depth=texture(uVisionDepth,vUv).r;
          if (depth>=0.9999999) discard;
          vec3 a=unproject(-1.0),b=unproject(0.0),ray=b-a;
          if (abs(ray.y)<0.00000001) discard;
          float t=(uVisionCentre.y-a.y)/ray.y;
          if (t<0.0) discard;
          vec3 point=a+ray*t;
          vec4 clip=uVisionVP*vec4(point,1.0);
          float guideDepth=clip.z/clip.w*0.5+0.5;
          // Reject any visible foreground surface (player, bodies, furniture).
          if (depth+0.0000001<guideDepth) discard;
          vec3 surface=unproject(depth*2.0-1.0);
          if (abs(surface.y-uVisionCentre.y)>0.15) discard;
          vec2 local=-(point-uVisionCentre).xz;
          float radius=length(local);
          if (radius>0.9) discard;
          float angle=atan(local.y,local.x)-uVisionHeading.x;
          angle=atan(sin(angle),cos(angle));
          float radial=radius-0.5;
          float distanceToLine=abs(angle)<=uVisionHeading.y?abs(radial):1000.0;
          if (uVisionHeading.y<3.1415) {
            distanceToLine=min(distanceToLine,min(edgeDistance(local,uVisionHeading.x-uVisionHeading.y),
                edgeDistance(local,uVisionHeading.x+uVisionHeading.y)));
          }
          float aa=max(fwidth(distanceToLine),0.0015);
          float stroke=1.0-smoothstep(aa*0.35,aa*1.25,distanceToLine);
          // Stacked outward white shadows: smaller layers are denser, outer layers softer.
          float outside=max(radial-aa,0.0);
          float glow=0.0;
          if (radial>=0.0) {
            glow+=0.10*(1.0-smoothstep(0.015,0.030,outside));
            glow+=0.06*(1.0-smoothstep(0.030,0.050,outside));
            glow+=0.035*(1.0-smoothstep(0.050,0.075,outside));
            glow+=0.015*(1.0-smoothstep(0.075,0.100,outside));
          }
          if (abs(angle)>uVisionHeading.y) glow=0.0;
          float alpha=max(stroke*0.8,glow);
          if (alpha<0.003) discard;
          colour=vec4(1.0,1.0,1.0,alpha);
        }
        """;
}
