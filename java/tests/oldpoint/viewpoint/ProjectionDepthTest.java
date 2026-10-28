package oldpoint.viewpoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.joml.Matrix4f;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import viewpoint.core.Frame;
import viewpoint.render.FrameContext;
import viewpoint.render.WorldRenderer;
import viewpoint.upscale.UpscaleFrame;

/** Real JOML and Viewpoint data classes; no GL context. Checks projection/reconstruction consistency. */
public final class ProjectionDepthTest {
    public static void main(String[] args) throws Exception {
        Frame frame = new Frame();
        frame.scene.projection.setPerspective(Layout.FOV, 16.0f / 9.0f, 0.05f, 400.0f);
        Matrix4f original = new Matrix4f(frame.scene.projection);
        FrameContext context = new FrameContext();
        context.scene = frame.scene;
        ProjectionDepth.capture(frame, true);
        Matrix4f adjusted = frame.scene.projection;
        close(adjusted.perspectiveNear(), 2.0f, 0.00001f);
        close(adjusted.perspectiveFar(), 400.0f, 0.02f);
        float[] before = original.get(new float[16]);
        float[] after = adjusted.get(new float[16]);
        for (int i = 0; i < 16; i++) {
            if (i != 10 && i != 14 && Float.floatToRawIntBits(before[i]) != Float.floatToRawIntBits(after[i])) {
                throw new AssertionError("Screen-space projection coefficient changed at " + i);
            }
        }
        Vector3f point = new Vector3f(1.4f, -2.0f, -57.0f);
        Vector3f ndc = adjusted.transformProject(new Vector3f(point));
        Vector3f recovered = new Matrix4f(adjusted).invert().transformProject(new Vector3f(ndc));
        if (recovered.distance(point) > 0.002f) {
            throw new AssertionError("Inverse projection does not recover the surface: " + recovered);
        }
        double oldGap = depthGap(original);
        double newGap = depthGap(adjusted);
        if (newGap / oldGap < 36.0 || newGap / oldGap > 44.0) {
            throw new AssertionError("Expected about 40x depth separation, got " + newGap / oldGap);
        }
        System.out.println("PASS: unchanged screen projection, inverse reconstruction and about 40x depth separation");

        float[] matrices = new float[64];
        float[] camera = new float[12];
        UpscaleFrame nativeInput = new UpscaleFrame(0.2f, -0.1f, 2560, 1440, true, 16, 0.05f, 400,
                Layout.FOV, 1.2247449f, matrices, camera);
        UpscaleFrame input = ProjectionDepth.upscale(context, nativeInput);
        close(input.near(), 2.0f, 0);
        if (input.matrices() != matrices || input.camera() != camera || input.reset() != nativeInput.reset()
                || input.fovY() != nativeInput.fovY() || input.jitterX() != nativeInput.jitterX()) {
            throw new AssertionError("Upscaler metadata changed beyond near plane");
        }
        iris(context);
        ProjectionDepth.capture(frame, false);
        frame.scene.projection.set(original);
        if (ProjectionDepth.upscale(context, nativeInput) != nativeInput) {
            throw new AssertionError("Reused normal frame retained overhead state");
        }
        Frame other = new Frame();
        FrameContext normal = new FrameContext();
        normal.scene = other.scene;
        if (ProjectionDepth.upscale(normal, nativeInput) != nativeInput) {
            throw new AssertionError("Independent normal scene affected");
        }
        System.out.println("PASS: upscaler near, reused/independent normal scene isolation and Iris projection/history inputs");
        inline("CameraPatch", "viewpoint.render.FrameContext", "camera");
        inline("PackPatch", "viewpoint.render.PackStages", "uniforms");
        inline("IrisProjectionPatch", "viewpoint.render.IrisUniforms", "matrices");
        inline("IrisCameraPatch", "viewpoint.render.IrisUniforms", "camera");
        inline("UpscalePatch", "viewpoint.render.VendorInputs", "describe");
        System.out.println("PASS: all 5 production patches inline into the actual installed Viewpoint methods");
    }

    private static double depthGap(Matrix4f projection) {
        // Compare the ideal separation of the actual float matrix coefficients, avoiding CPU float cancellation.
        // GPU rounding/rasterization and the visible flicker still require an in-game check.
        Matrix4d precise = new Matrix4d(projection);
        Vector4d a = precise.transform(new Vector4d(0, 0, -100, 1));
        Vector4d b = precise.transform(new Vector4d(0, 0, -100.02, 1));
        return Math.abs(a.z / a.w - b.z / b.w);
    }

    private static void iris(FrameContext context) throws Exception {
        Class<?> type = Class.forName("viewpoint.render.IrisUniforms");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object uniforms = constructor.newInstance();
        var projectionField = type.getDeclaredField("projection");
        projectionField.setAccessible(true);
        Matrix4f projection = (Matrix4f) projectionField.get(uniforms);
        projection.setPerspective(Layout.FOV, 16.0f / 9.0f, 0.05f, 1024.0f);
        float xScale = projection.m00(), yScale = projection.m11();
        ProjectionDepth.irisProjection(uniforms, context);
        close(projection.perspectiveNear(), 2.0f, 0.00001f);
        close(projection.perspectiveFar(), 1024.0f, 0.25f);
        close(projection.m00(), xScale, 0);
        close(projection.m11(), yScale, 0);
        var valuesField = type.getDeclaredField("values");
        valuesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, float[]> values = (Map<String, float[]>) valuesField.get(uniforms);
        values.put("near", new float[] {0.05f});
        ProjectionDepth.irisCamera(uniforms, context);
        close(values.get("near")[0], 2.0f, 0);
        WorldRenderer.freeCamera = true;
        context.eyeX = 30;
        context.eyeY = 40;
        try {
            ProjectionDepth.irisProjection(uniforms, context);
            close(projection.perspectiveFar(), 1074.0f, 0.25f);
        } finally {
            WorldRenderer.freeCamera = false;
        }
        FrameContext normal = new FrameContext();
        normal.scene = new Frame().scene;
        projection.setPerspective(Layout.FOV, 16.0f / 9.0f, 0.05f, 1024.0f);
        values.put("near", new float[] {0.05f});
        ProjectionDepth.irisProjection(uniforms, normal);
        ProjectionDepth.irisCamera(uniforms, normal);
        close(projection.perspectiveNear(), 0.05f, 0.00001f);
        close(values.get("near")[0], 0.05f, 0);
    }

    private static void close(float actual, float expected, float tolerance) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void inline(String suffix, String targetName, String method) throws Exception {
        String name = "oldpoint.viewpoint.ProjectionDepth$" + suffix;
        byte[] source;
        try (var in = ProjectionDepthTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            source = in.readAllBytes();
        }
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        return super.visitAnnotation(descriptor.replace("Lme/zed_0xff/zombie_buddy/Patch$OnExit;",
                                "Lnet/bytebuddy/asm/Advice$OnMethodExit;"), visible);
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        return super.visitParameterAnnotation(parameter,
                                descriptor.replace("Lme/zed_0xff/zombie_buddy/Patch$", "Lnet/bytebuddy/asm/Advice$"),
                                visible);
                    }
                };
            }
        }, 0);
        byte[] bytes = writer.toByteArray();
        Class<?> advice = new ClassLoader(ProjectionDepthTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define();
        Class<?> target = Class.forName(targetName, false, ProjectionDepthTest.class.getClassLoader());
        new ByteBuddy().redefine(target)
                .visit(Advice.to(advice, ClassFileLocator.Simple.of(name, bytes)).on(named(method))).make();
    }
}
