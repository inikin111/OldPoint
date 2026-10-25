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
        Class<?> rooms = Class.forName("viewpoint.visibility.Rooms", false, loader);
        Field roomView = rooms.getDeclaredField("view");
        requireStaticField(roomView, roomView.getType());
        requireStaticField(rooms.getDeclaredField("hiding"), boolean.class);
        for (String name : new String[] {"x", "y", "h", "tanX", "tanY"}) {
            requireInstanceField(roomView.getType().getDeclaredField(name), float.class);
        }
        Class<?> drawer = Class.forName("viewpoint.SceneDrawer", false, loader);
        requireInstanceField(drawer.getDeclaredField("frame"), Class.forName("viewpoint.core.Frame", false, loader));
        requireInstanceField(drawer.getDeclaredField("eye"), float[].class);
        requireInstanceField(drawer.getDeclaredField("dir"), float[].class);
        requireStaticField(drawer.getDeclaredField("view"), Class.forName("org.joml.Matrix4f", false, loader));
        System.out.println("PASS 16 installed Viewpoint camera/visibility/drawing bindings (classes not initialized)");
    }

    private static void requireStaticMethod(Method method, Class<?> result) {
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) {
            throw new AssertionError("Unexpected method contract: " + method);
        }
        method.setAccessible(true);
    }

    private static void requireInstanceField(Field field, Class<?> type) {
        if (Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
            throw new AssertionError("Unexpected instance field contract: " + field);
        }
        field.setAccessible(true);
    }

    private static void requireStaticField(Field field, Class<?> type) {
        if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
            throw new AssertionError("Unexpected field contract: " + field);
        }
        field.setAccessible(true);
    }
}
