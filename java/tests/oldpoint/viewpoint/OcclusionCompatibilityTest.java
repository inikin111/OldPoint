package oldpoint.viewpoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;

/** Compare packed rules and transformed shader text against an actual pre-refactor JAR; no GL calls. */
public final class OcclusionCompatibilityTest {
    public static void main(String[] args) throws Exception {
        URL[] baselineUrls = urls(args, 0);
        URL[] currentUrls = urls(args, 1);
        try (URLClassLoader baseline = new URLClassLoader(baselineUrls, ClassLoader.getPlatformClassLoader());
                URLClassLoader current = new URLClassLoader(currentUrls, ClassLoader.getPlatformClassLoader())) {
            for (String scenario : List.of("empty", "mixed", "limits", "negative", "large-extents")) {
                Object oldState = fixture(baseline, false, scenario, false);
                Object newState = fixture(current, true, scenario, false);
                Object expected = call(type(baseline, "Interior"), "packRules", oldState);
                Object actual = call(type(current, "OcclusionRules"), "forSnapshot", newState);
                compareData(expected, actual);
                Object repeated = call(type(current, "OcclusionRules"), "forSnapshot", newState);
                require(actual == repeated, "Same snapshot must reuse packed rules");
                Object moved = fixture(current, true, scenario, true);
                Object reused = call(type(current, "OcclusionRules"), "forSnapshot", moved);
                require(actual == reused, "Camera-only movement must reuse packed rules");
                System.out.println("PASS packed-rules " + scenario);
            }
            Class<?> oldShaders = type(baseline, "Interior");
            Class<?> newShaders = type(current, "OcclusionShaders");
            int shaders = 0;
            try (ZipFile viewpoint = new ZipFile(args[2])) {
                var entries = viewpoint.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    int start = name.indexOf("shaders/");
                    if (entry.isDirectory() || start < 0) {
                        continue;
                    }
                    String path = name.substring(start + "shaders/".length());
                    String source;
                    try (var input = viewpoint.getInputStream(entry)) {
                        source = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    Object expected = call(oldShaders, "shader", path, source);
                    Object actual = call(newShaders, "shader", path, source);
                    require(Objects.equals(expected, actual), "Shader transformation changed: " + path);
                    shaders++;
                }
            }
            require(shaders > 0, "No installed shader sources found");
            for (String source : new String[] {null, "", "void oldPointInterior()", "void main() {}"}) {
                require(
                        Objects.equals(
                                call(oldShaders, "shader", "gbuffer.frag", source),
                                call(newShaders, "shader", "gbuffer.frag", source)),
                        "Shader guard changed");
            }
            System.out.println("PASS " + shaders + " installed shader transformations and 4 guard cases");
        }
    }

    private static URL[] urls(String[] args, int selected) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(Path.of(args[selected]).toUri().toURL());
        for (int i = 2; i < args.length; i++) {
            urls.add(Path.of(args[i]).toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    private static Object fixture(ClassLoader loader, boolean current, String scenario, boolean moved)
            throws Exception {
        String wallType = current ? "OcclusionSnapshot$Wall" : "Interior$Wall";
        String fadingType = current ? "OcclusionSnapshot$Fading" : "Interior$Fading";
        List<Object> rooms = new ArrayList<>();
        List<Object> walls = new ArrayList<>();
        List<Object> roofs = new ArrayList<>();
        List<Object> boundary = new ArrayList<>();
        List<Object> openings = new ArrayList<>();
        List<Object> shades = new ArrayList<>();
        int base = scenario.equals("negative") ? -24 : 10;
        if (!scenario.equals("empty")) {
            int count = scenario.equals("limits") ? 140 : 2;
            for (int i = 0; i < count; i++) {
                rooms.add(make(loader, "Layout$Rect", base + i, 20, 4, 5));
                Object wall = make(loader, wallType, base + i, 20, 2, i % 2 + 1);
                walls.add(make(loader, fadingType, wall, 0.25f + i / 200f));
            }
            float x2 = scenario.equals("large-extents") ? 100f : base + 5f;
            roofs.add(
                    make(loader, "RoofEdges$Bounds", (float) base, 20f, 6f, x2, 120f, 8f, 0.1f, 0.2f, 0.7f, 0.8f, 13));
            boundary.add(make(loader, "RoomBoundary$Run", base, 20, 5, 1, -1));
            Object bounds = make(loader, "ObjectBounds", (float) base, 20f, 5f, base + 1f, 21f, 6f);
            Object art = make(loader, "OpeningFade$Art", 0.1f, 0.2f, 0.3f, 0.4f);
            openings.add(make(loader, "OpeningFade$Instance", -4, (float) base, 20f, 5f, bounds, art, 0.7f));
            openings.add(make(loader, "OpeningFade$Instance", 7, (float) base + 1, 20f, 5f, bounds, art, 0.2f));
            shades.add(make(loader, "NativeRooms$Shade", rooms.get(0), 0.45f));
        }
        float[] cells = scenario.equals("empty") ? new float[0] : new float[] {0.2f, 1f, 0.3f, 0.6f, 0.9f};
        Object trees = make(loader, "TreeClearing$Selection", base, 20, 5, 1, 2, cells, Map.of());
        Object capture = make(loader, "OpeningFade$Capture", List.copyOf(openings), Map.of());
        Object nearby = make(loader, "NearbyFade$Area", 12f, 22f);
        return make(
                loader,
                current ? "OcclusionSnapshot" : "Interior$State",
                moved ? 42d : 10d,
                20d,
                30d,
                List.copyOf(rooms),
                List.copyOf(rooms),
                false,
                true,
                List.copyOf(walls),
                List.copyOf(roofs),
                List.copyOf(boundary),
                capture,
                trees,
                List.copyOf(shades),
                2,
                1,
                moved ? 4f : 1f,
                2f,
                3f,
                0.5f,
                -0.5f,
                0.7f,
                10f,
                20f,
                30f,
                nearby);
    }

    private static Object make(ClassLoader loader, String name, Object... args) throws Exception {
        Class<?> type = type(loader, name);
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        throw new AssertionError("No fixture constructor: " + name);
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName("oldpoint.viewpoint." + name, true, loader);
    }

    private static Object call(Class<?> owner, String name, Object... args) throws Exception {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(null, args);
            }
        }
        throw new AssertionError("No test operation: " + owner + "." + name);
    }

    private static void compareData(Object expected, Object actual) throws Exception {
        for (var component : expected.getClass().getRecordComponents()) {
            Method oldAccessor = component.getAccessor();
            Method newAccessor = actual.getClass().getDeclaredMethod(component.getName());
            oldAccessor.setAccessible(true);
            newAccessor.setAccessible(true);
            Object a = oldAccessor.invoke(expected);
            Object b = newAccessor.invoke(actual);
            boolean equal = a instanceof float[] floats ? Arrays.equals(floats, (float[]) b) : a.equals(b);
            require(equal, "Packed data changed: " + component.getName());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
