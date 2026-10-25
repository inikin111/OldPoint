package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Check installed backend contracts without initializing Viewpoint or starting the game. */
public final class ViewpointCameraBindingsTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        ClassLoader loader = ViewpointCameraBindingsTest.class.getClassLoader();
        Class<?> fp = Class.forName("viewpoint.FP", false, loader);
        Class<?> character = Class.forName("zombie.characters.IsoGameCharacter", false, loader);
        Class<?> settings = Class.forName("viewpoint.camera.CameraSettings", false, loader);
        Class<?> toggle = Class.forName("viewpoint.platform.LiveSettings$Toggle", false, loader);

        requireStaticMethod(fp.getDeclaredMethod("cycleView"), void.class);
        requireStaticMethod(fp.getDeclaredMethod("switchView", character, boolean.class), void.class);
        requireStaticField(fp.getDeclaredField("cursorMode"), boolean.class);
        requireStaticField(settings.getDeclaredField("ON_FOOT"), toggle);
        requireStaticField(settings.getDeclaredField("SEATED"), toggle);
        System.out.println("PASS 5 installed Viewpoint camera bindings (classes not initialized)");
    }

    private static void requireStaticMethod(Method method, Class<?> result) {
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) {
            throw new AssertionError("Unexpected method contract: " + method);
        }
        method.setAccessible(true);
    }

    private static void requireStaticField(Field field, Class<?> type) {
        if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
            throw new AssertionError("Unexpected field contract: " + field);
        }
        field.setAccessible(true);
    }
}
