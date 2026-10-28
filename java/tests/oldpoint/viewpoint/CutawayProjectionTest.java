package oldpoint.viewpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Cross-check the actual production projection against native bytecode and independent screen-space fixtures. */
public final class CutawayProjectionTest {
    private record Cell(int x, int y) {}

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError(name);
        }
        System.out.println("PASS " + name);
    }

    private static void nativeMasks() throws Exception {
        Class<?> type = Class.forName("zombie.iso.fboRenderChunk.FBORenderCutaways$CutawayWall");
        int cases = 0;
        for (int kind = 1; kind <= 2; kind++) {
            var field = type.getDeclaredField(kind == 1 ? "NORTH_WALL_DXY" : "WEST_WALL_DXY");
            field.setAccessible(true);
            int[] offsets = (int[]) field.get(null);
            for (int floor : new int[] {-1, 0, 2}) {
                for (int[] origin : new int[][] {{0, 0}, {8411, 12028}, {-8123, -415}, {100000, 100000}}) {
                    var camera = CutawayProjection.Camera.orthographic(3, 3, CutawayProjection.STOREY, floor);
                    var wall = new OcclusionSnapshot.Wall(origin[0], origin[1], floor, kind);
                    var projection = CutawayProjection.wall(wall, camera);
                    Set<Cell> expected = new HashSet<>(), actual = new HashSet<>();
                    for (int i = 0; i < offsets.length; i += 2) {
                        expected.add(new Cell(origin[0] + offsets[i], origin[1] + offsets[i + 1]));
                    }
                    var bounds = projection.bounds(origin[0] - 8, origin[1] - 8, origin[0] + 8, origin[1] + 8);
                    if (bounds == null) {
                        throw new AssertionError("Missing native footprint");
                    }
                    for (int y = origin[1] - 8; y <= origin[1] + 8; y++) {
                        for (int x = origin[0] - 8; x <= origin[0] + 8; x++) {
                            if (projection.overlaps(x, y, x + 1, y + 1)) {
                                actual.add(new Cell(x, y));
                                if (!bounds.contains(x, y)) {
                                    throw new AssertionError("Bounds omit a projected cell");
                                }
                            }
                        }
                    }
                    if (!actual.equals(expected)) {
                        throw new AssertionError("Native mask mismatch: " + kind + " " + actual + " != " + expected);
                    }
                    cases++;
                }
            }
        }
        System.out.println("PASS " + cases + " production/native-bytecode footprint cases");
    }

    private static void independentFixtures(Path file) throws Exception {
        int count = 0;
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t");
            int floor = Integer.parseInt(p[3]);
            var wall =
                    new OcclusionSnapshot.Wall(
                            Integer.parseInt(p[1]), Integer.parseInt(p[2]), floor, Integer.parseInt(p[0]));
            var camera =
                    new CutawayProjection.Camera(
                            Double.parseDouble(p[5]),
                            Double.parseDouble(p[6]),
                            Double.parseDouble(p[7]),
                            Double.parseDouble(p[4]),
                            floor);
            boolean expected = p[12].equals("1");
            boolean actual =
                    CutawayProjection.wall(wall, camera)
                            .overlaps(
                                    Double.parseDouble(p[8]),
                                    Double.parseDouble(p[9]),
                                    Double.parseDouble(p[10]),
                                    Double.parseDouble(p[11]));
            if (actual != expected) {
                throw new AssertionError("Independent screen projection mismatch at fixture " + count + ": " + line);
            }
            count++;
        }
        if (count == 0) {
            throw new AssertionError("No projection fixtures");
        }
        System.out.println("PASS " + count + " production vs independent screen-overlap queries");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            nativeMasks();
            independentFixtures(Path.of(args[0]));
        }
        var wall = new OcclusionSnapshot.Wall(0, 0, 0, 1);
        var camera = new CutawayProjection.Camera(0.5, -4, CutawayProjection.STOREY, 1, 0);
        var horizon = CutawayProjection.wall(wall, camera);
        check("wall-top horizon has bounded room intersection", horizon.bounds(-3, -10, 3, 10) != null);
        check("wall-top horizon reaches far ground", horizon.overlaps(0.1, 100, 0.9, 101));
        var low = CutawayProjection.wall(wall, new CutawayProjection.Camera(0.5, -4, 1, 1, 0));
        check("camera below wall top remains defined", low.overlaps(0.1, 50, 0.9, 51));
        check(
                "view parallel to wall is empty",
                !CutawayProjection.wall(wall, CutawayProjection.Camera.orthographic(3, 0, 1, 0))
                        .overlaps(-10, -10, 10, 10));
        check(
                "camera on wall plane is empty",
                !CutawayProjection.wall(wall, new CutawayProjection.Camera(0.5, 0, 3, 1, 0))
                        .overlaps(-10, -10, 10, 10));
        check(
                "camera at ground does not produce invalid footprint",
                !CutawayProjection.wall(wall, new CutawayProjection.Camera(0.5, -4, 0, 1, 0))
                        .overlaps(-10, -10, 10, 10));
        check(
                "constant-plane rejection for solids above eye",
                !CutawayProjection.prism(new CutawayProjection.Camera(0.5, -4, 3, 1, 0), 0, -2, 3, 1, 0, 5)
                        .overlaps(CutawayGeometry.Target.point(0.5, 1)));
        var narrow =
                CutawayProjection.prism(
                        new CutawayProjection.Camera(0.5, -4, 10, 1, 0), 0.21, 0.2, 0.1, 0.23, 0.22, 0.15);
        check(
                "continuous target catches narrow solid between sample points",
                narrow.overlaps(CutawayGeometry.Target.tile(0, 0)));
    }
}
