package oldpoint.viewpoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/** Executes production Hook bodies inlined into another package, as ZombieBuddy does at runtime. */
public final class HookInliningTest {
    private static final String PATCH = "Lme/zed_0xff/zombie_buddy/Patch$";
    private static final String ADVICE = "Lnet/bytebuddy/asm/Advice$";

    public static void main(String[] args) throws Exception {
        check("AimCamera$NativeCursorPatch", false);
        check("AimCamera$NativePanPatch", false);
        check("TerrainCamera$ThirdPatch", true);
        System.out.println("PASS 3 production Hooks inlined into a foreign package");
    }

    private static void check(String nestedName, boolean hasFrame) throws Exception {
        String name = "oldpoint.viewpoint." + nestedName;
        byte[] source;
        try (var input =
                HookInliningTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) {
                throw new AssertionError("Missing production Hook: " + name);
            }
            source = input.readAllBytes();
        }
        ClassWriter output = new ClassWriter(0);
        new ClassReader(source)
                .accept(
                        new ClassVisitor(Opcodes.ASM9, output) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access, String name, String descriptor, String signature, String[] exceptions) {
                                return new MethodVisitor(
                                        Opcodes.ASM9,
                                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                        String mapped =
                                                switch (descriptor) {
                                                    case PATCH + "OnEnter;" -> ADVICE + "OnMethodEnter;";
                                                    case PATCH + "OnExit;" -> ADVICE + "OnMethodExit;";
                                                    default -> descriptor;
                                                };
                                        return new AnnotationVisitor(
                                                Opcodes.ASM9, super.visitAnnotation(mapped, visible)) {
                                            @Override
                                            public void visit(String name, Object value) {
                                                if (descriptor.equals(PATCH + "OnEnter;") && name.equals("skipOn")) {
                                                    if (Boolean.TRUE.equals(value)) {
                                                        super.visit(
                                                                "skipOn", Type.getType(Advice.OnNonDefaultValue.class));
                                                    }
                                                } else {
                                                    super.visit(name, value);
                                                }
                                            }
                                        };
                                    }

                                    @Override
                                    public AnnotationVisitor visitParameterAnnotation(
                                            int parameter, String descriptor, boolean visible) {
                                        String mapped =
                                                descriptor.equals(PATCH + "Return;")
                                                        ? ADVICE + "Return;"
                                                        : descriptor.equals(PATCH + "Argument;")
                                                                ? ADVICE + "Argument;"
                                                                : descriptor;
                                        return super.visitParameterAnnotation(parameter, mapped, visible);
                                    }
                                };
                            }
                        },
                        0);
        byte[] bytes = output.toByteArray();
        Class<?> advice =
                new ClassLoader(HookInliningTest.class.getClassLoader()) {
                    Class<?> define() {
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                }.define();
        Class<?>[] parameters = hasFrame ? new Class<?>[] {Object.class} : new Class<?>[0];
        Class<?> target =
                new ByteBuddy()
                        .subclass(Object.class)
                        .name("viewpoint.HookAccessFixture" + nestedName.replace('$', '_'))
                        .defineMethod("probe", boolean.class, Visibility.PUBLIC, Ownership.STATIC)
                        .withParameters(parameters)
                        .intercept(FixedValue.value(true))
                        .visit(Advice.to(advice, ClassFileLocator.Simple.of(name, bytes)).on(named("probe")))
                        .make()
                        .load(HookInliningTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                        .getLoaded();
        Object result =
                target.getMethod("probe", parameters).invoke(null, hasFrame ? new Object[] {null} : new Object[0]);
        if (!Boolean.TRUE.equals(result)) {
            throw new AssertionError("Unexpected inactive Hook result: " + name);
        }
    }
}
