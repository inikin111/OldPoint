package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Matrix4f;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;
import viewpoint.upscale.UpscaleFrame;

/** Keeps overhead projection depth, reconstruction uniforms and temporal inputs on the same near plane. */
public final class ProjectionDepth {
    static final float NEAR = 2.0f;
    private static final float FAR = 400.0f;
    private static final Map<SceneData, Float> overheadScenes = Collections.synchronizedMap(new WeakHashMap<>());
    private static Field irisProjection;
    private static Field irisFar;
    private static Field irisValues;

    private ProjectionDepth() {}

    static boolean capture(Frame frame, boolean overhead) {
        if (overhead) {
            float near = NEAR;
            depthRange(frame.scene.projection, near, FAR);
            Float previous = overheadScenes.put(frame.scene, near);
            return previous == null || previous.floatValue() != near;
        } else {
            // Viewpoint has already rebuilt this reused frame's native projection before camera capture.
            overheadScenes.remove(frame.scene);
            return false;
        }
    }

    private static Float near(FrameContext frame) {
        return frame == null ? null : overheadScenes.get(frame.scene);
    }

    static void depthRange(Matrix4f projection, float near, float far) {
        // Preserve FOV, aspect, camera distance and all screen-space projection coefficients exactly.
        projection.m22(-(far + near) / (far - near));
        projection.m32(-(2.0f * far * near) / (far - near));
    }

    public static void cameraUniforms(GlProgram program, FrameContext frame) {
        Float near = near(frame);
        if (near != null && program.has("uCamera")) {
            program.set("uCamera", near, FAR, 1.0f / frame.scene.projection.m00(),
                    1.0f / frame.scene.projection.m11());
        }
    }

    private static void irisAccess(Object uniforms) throws ReflectiveOperationException {
        if (irisProjection == null) {
            Class<?> type = uniforms.getClass();
            Field projection = type.getDeclaredField("projection");
            Field far = type.getDeclaredField("far");
            Field values = type.getDeclaredField("values");
            projection.setAccessible(true);
            far.setAccessible(true);
            values.setAccessible(true);
            irisFar = far;
            irisValues = values;
            irisProjection = projection;
        }
    }

    public static void irisProjection(Object uniforms, FrameContext frame) {
        Float near = near(frame);
        if (near == null) {
            return;
        }
        try {
            irisAccess(uniforms);
            // Iris uses a longer scene far plane than the built-in path. Retain its original calculation.
            float extra = WorldRenderer.freeCamera
                    ? (float) Math.sqrt(frame.eyeX * frame.eyeX + frame.eyeY * frame.eyeY + frame.eyeZ * frame.eyeZ)
                    : 0;
            float far = irisFar.getFloat(uniforms) * 4.0f + extra;
            depthRange((Matrix4f) irisProjection.get(uniforms), near, far);
        } catch (ReflectiveOperationException error) {
            CompatibilityErrors.report(error);
        }
    }

    public static void irisCamera(Object uniforms, FrameContext frame) {
        Float selected = near(frame);
        if (selected == null) {
            return;
        }
        try {
            irisAccess(uniforms);
            Map<?, ?> values = (Map<?, ?>) irisValues.get(uniforms);
            float[] near = (float[]) values.get("near");
            if (near != null && near.length > 0) {
                near[0] = selected;
            }
        } catch (ReflectiveOperationException error) {
            CompatibilityErrors.report(error);
        }
    }

    public static UpscaleFrame upscale(FrameContext frame, UpscaleFrame original) {
        Float near = near(frame);
        if (near == null || original == null) {
            return original;
        }
        return new UpscaleFrame(original.jitterX(), original.jitterY(), original.motionScaleX(),
                original.motionScaleY(), original.reset(), original.frameMs(), near, original.far(),
                original.fovY(), original.metresPerUnit(), original.matrices(), original.camera());
    }

    @Patch(className = "viewpoint.render.FrameContext", methodName = "camera", strictMatch = true)
    public static final class CameraPatch {
        @Patch.OnExit
        public static void exit(@Patch.This FrameContext frame, @Patch.Argument(0) GlProgram program) {
            ProjectionDepth.cameraUniforms(program, frame);
        }
    }

    @Patch(className = "viewpoint.render.PackStages", methodName = "uniforms", strictMatch = true)
    public static final class PackPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) GlProgram program, @Patch.Argument(1) FrameContext frame) {
            ProjectionDepth.cameraUniforms(program, frame);
        }
    }

    @Patch(className = "viewpoint.render.IrisUniforms", methodName = "matrices", strictMatch = true)
    public static final class IrisProjectionPatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object uniforms, @Patch.Argument(0) FrameContext frame) {
            ProjectionDepth.irisProjection(uniforms, frame);
        }
    }

    @Patch(className = "viewpoint.render.IrisUniforms", methodName = "camera", strictMatch = true)
    public static final class IrisCameraPatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object uniforms, @Patch.Argument(0) FrameContext frame) {
            ProjectionDepth.irisCamera(uniforms, frame);
        }
    }

    @Patch(className = "viewpoint.render.VendorInputs", methodName = "describe", strictMatch = true)
    public static final class UpscalePatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) FrameContext frame,
                @Patch.Return(readOnly = false) UpscaleFrame result) {
            result = ProjectionDepth.upscale(frame, result);
        }
    }
}
