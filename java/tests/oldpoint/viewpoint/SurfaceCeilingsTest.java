package oldpoint.viewpoint;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import static net.bytebuddy.matcher.ElementMatchers.named;

/** Replays Surface Fix's Vp.emit boundary, using actual inlined production advice. */
public final class SurfaceCeilingsTest {
    private static int emitted;

    public static void record(int flags) {
        emitted = flags;
    }

    private static Class<?> compatibility() throws Exception {
        try {
            return Class.forName("oldpoint.viewpoint.SurfaceCeilings");
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static Class<?> target() throws Exception {
        net.bytebuddy.dynamic.DynamicType.Builder<?> builder =
                new ByteBuddy()
                        .subclass(Object.class)
                        .name("vpinteriors.CeilingEmitFixture")
                        .defineMethod("emit", void.class, Visibility.PUBLIC, Ownership.STATIC)
                        .withParameters(
                                Object.class,
                                Object.class,
                                Object.class,
                                float.class,
                                float.class,
                                float.class,
                                int.class)
                        .intercept(
                                MethodCall.invoke(SurfaceCeilingsTest.class.getMethod("record", int.class))
                                        .withArgument(6));
        Class<?> helper = compatibility();
        if (helper != null) {
            String name = helper.getName() + "$EmitPatch";
            byte[] source;
            try (var in = helper.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                source = in.readAllBytes();
            }
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(source)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9, writer) {
                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String method,
                                        String descriptor,
                                        String signature,
                                        String[] errors) {
                                    return new MethodVisitor(
                                            Opcodes.ASM9,
                                            super.visitMethod(access, method, descriptor, signature, errors)) {
                                        @Override
                                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                            String mapped =
                                                    descriptor
                                                            .replace(
                                                                    "Lme/zed_0xff/zombie_buddy/Patch$OnEnter;",
                                                                    "Lnet/bytebuddy/asm/Advice$OnMethodEnter;")
                                                            .replace(
                                                                    "Lme/zed_0xff/zombie_buddy/Patch$OnExit;",
                                                                    "Lnet/bytebuddy/asm/Advice$OnMethodExit;");
                                            return super.visitAnnotation(mapped, visible);
                                        }

                                        @Override
                                        public AnnotationVisitor visitParameterAnnotation(
                                                int parameter, String descriptor, boolean visible) {
                                            String mapped =
                                                    descriptor.replace(
                                                            "Lme/zed_0xff/zombie_buddy/Patch$Argument;",
                                                            "Lnet/bytebuddy/asm/Advice$Argument;");
                                            return super.visitParameterAnnotation(parameter, mapped, visible);
                                        }
                                    };
                                }
                            },
                            0);
            byte[] adviceBytes = writer.toByteArray();
            Class<?> advice =
                    new ClassLoader(helper.getClassLoader()) {
                        Class<?> loadAdvice() {
                            return defineClass(name, adviceBytes, 0, adviceBytes.length);
                        }
                    }.loadAdvice();
            builder = builder.visit(Advice.to(advice, ClassFileLocator.Simple.of(name, adviceBytes)).on(named("emit")));
        }
        return builder.make()
                .load(SurfaceCeilingsTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

    private static void scope(String name) throws Exception {
        Class<?> helper = compatibility();
        if (helper != null) {
            helper.getMethod(name).invoke(null);
        }
    }

    private static void emit(Class<?> target, int flags) throws Exception {
        target.getMethod(
                        "emit",
                        Object.class,
                        Object.class,
                        Object.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class)
                .invoke(null, null, null, null, 0f, 0f, 2.4494896f, flags);
    }

    public static void main(String[] args) throws Exception {
        Class<?> target = target();
        emit(target, 2);
        if (emitted != 2) {
            throw new AssertionError("unrelated Surface Fix geometry changed");
        }
        scope("begin");
        try {
            // RoomCeilings emits an ordinary mesh with flags=0, placed at the
            // next ceiling height but owned by the occupied floor's recipe.
            emit(target, 0);
            if ((emitted & GeneratedRoofs.TAG) == 0) {
                throw new AssertionError(
                        "Surface Fix ceiling has no roof provenance; same-floor ceiling stays visible");
            }
            emit(target, 2);
            if (emitted != (2 | GeneratedRoofs.TAG)) {
                throw new AssertionError("existing mesh flags lost");
            }
            int rooted = viewpoint.platform.VertexFormat.ROOTED;
            emit(target, rooted);
            if (emitted != rooted) {
                throw new AssertionError("reserved root-position bits changed");
            }
            scope("begin");
            scope("end");
            emit(target, 0);
            if ((emitted & GeneratedRoofs.TAG) == 0) {
                throw new AssertionError("nested scope ended the parent ceiling");
            }
        } finally {
            scope("end");
        }
        emit(target, 0);
        if (emitted != 0) {
            throw new AssertionError("ceiling scope leaked onto later geometry");
        }
        if (args.length > 0) {
            java.net.URL url = java.nio.file.Path.of(args[0]).toUri().toURL();
            try (var loader =
                    new java.net.URLClassLoader(new java.net.URL[] {url}, SurfaceCeilingsTest.class.getClassLoader())) {
                Class<?> ceilings = Class.forName("vpinteriors.RoomCeilings", false, loader);
                ceilings.getDeclaredMethod(
                        "capture", Object.class, float.class, float.class, float.class, boolean[].class);
                ceilings.getDeclaredMethod(
                        "captureRoof", Object.class, float.class, float.class, float.class, boolean[].class);
                Class<?> bridge = Class.forName("vpinteriors.Vp", false, loader);
                bridge.getDeclaredMethod(
                        "emit",
                        Object.class,
                        Object.class,
                        Object.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class);
                float[] mapping = new float[11];
                mapping[8] = mapping[9] = mapping[10] = 1;
                float[] vertices =
                        (float[])
                                ceilings.getMethod(
                                                "mappedVertices",
                                                float[].class,
                                                float.class,
                                                float.class,
                                                float.class,
                                                float.class)
                                        .invoke(null, mapping, 0f, 0f, 1f, 1f);
                for (int i = 0; i < vertices.length; i += 8) {
                    if (Math.abs(vertices[i + 1] + 0.008f) > 0.00001f || vertices[i + 6] != -1f) {
                        throw new AssertionError("installed Surface Fix ceiling layout changed");
                    }
                }
                System.out.println("PASS installed Surface Fix signatures and real inset ceiling geometry");
            }
        }
        System.out.println(
                "PASS Surface Fix ceiling provenance, preserved flags, nested scope and foreign-package inlining");
    }
}
